package com.kaspercito.data.dj

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.kaspercito.domain.data.player.GenericMediaItem
import com.kaspercito.domain.dj.DjSession
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.mediaservice.player.MediaPlayerInterface
import com.kaspercito.logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.makadimi.aiservice.AiClient
import java.util.Locale

private const val TAG = "AndroidDjAnnouncer"

/** Un comentario cada tantas canciones: pocas peticiones a la IA y la voz no cansa. */
private const val TRACKS_PER_COMMENT = 3

/** Volumen de la música (0..1) mientras habla el locutor. */
private const val DUCKED_LEVEL = 0.25f

/** Pausa entre frases, para que no suene a lector de pantalla. */
private const val PAUSE_BETWEEN_SENTENCES_MS = 300L

private const val SPEECH_RATE = 1.05f
private const val SPEECH_PITCH = 1.0f

/** Tiempo máximo que se espera a que termine de hablar, por si el motor TTS no avisa. */
private const val MAX_SPEECH_MS = 25_000L

/** Tiempo máximo que se espera a que la canción empiece a sonar antes de hablar. */
private const val PLAYBACK_WAIT_MS = 20_000L

/**
 * Locutor DJ con IA para Android (equivalente móvil del de escritorio).
 *
 *  - La línea de texto se pide a la IA **por adelantado**, mientras suena la canción anterior.
 *  - Al empezar la canción anunciada se lee con el motor TTS del teléfono (sin datos extra) y la
 *    música se atenúa con [MediaPlayerInterface.sleepFadeFactor], subiendo de nuevo con suavidad.
 *  - Si la IA no está configurada, falla o el teléfono no tiene voz, simplemente se omite.
 */
internal class AndroidDjAnnouncer(
    private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val aiClient: AiClient,
    private val player: MediaPlayerInterface,
    private val nowPlaying: StateFlow<GenericMediaItem?>,
    private val isPlaying: () -> Boolean,
    private val isSleepTimerActive: () -> Boolean,
    private val scope: CoroutineScope,
) {
    private class Prepared(
        val forMediaId: String,
        val text: String,
        /** Audio de la voz neuronal ya generado (null si no hay clave o falló). */
        val clip: SpeechClip?,
    )

    private val neuralVoice = GeminiVoice(dataStoreManager)

    @Volatile
    private var voiceProblemShown = false

    @Volatile
    private var prepared: Prepared? = null
    private var prepareJob: Job? = null
    private var speakJob: Job? = null
    private var sinceLastComment = 0

    /** Verdadero hasta que el DJ salude: así la sesión arranca con voz y no en silencio. */
    private var introPending = false

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    @Volatile
    private var ttsLanguageCode: String? = null

    /** Se activa si una voz de red falla: desde entonces solo se usan voces instaladas. */
    @Volatile
    private var offlineVoicesOnly = false

    /** Últimas frases dichas, para pedirle a la IA que no se repita. */
    private val recentLines = ArrayDeque<String>()

    fun start() {
        // El DJ solo suena si TÚ lo enciendes con el botón DJ. El interruptor se guarda en disco,
        // así que al abrir la app se deja apagado (una sola vez por proceso).
        if (DjSession.consumeFreshProcess()) {
            scope.launch { dataStoreManager.setAiDj(false) }
        }
        // Cada vez que el DJ se ENCIENDE (botón "Empezar DJ"), la siguiente canción llega con
        // saludo. Al APAGARLO se calla en el acto y se descarta lo que tuviera preparado.
        scope.launch {
            dataStoreManager.aiDj
                .drop(1)
                .distinctUntilChanged()
                .collect { on ->
                    introPending = on == DataStoreManager.TRUE
                    if (!introPending) shutUp()
                }
        }
        scope.launch {
            nowPlaying
                .filterNotNull()
                .distinctUntilChangedBy { it.mediaId }
                .collect { onTrackStarted(it) }
        }
    }

    /** Corta la voz actual y descarta lo preparado (DJ apagado). */
    private fun shutUp() {
        prepareJob?.cancel()
        prepared = null
        sinceLastComment = 0
        speakJob?.cancel()
        runCatching { tts?.stop() }
    }

    private fun remember(line: String) {
        synchronized(recentLines) {
            recentLines.addLast(line)
            while (recentLines.size > 4) recentLines.removeFirst()
        }
    }

    private fun recentSnapshot(): List<String> = synchronized(recentLines) { recentLines.toList() }

    private suspend fun onTrackStarted(item: GenericMediaItem) {
        val ready = prepared
        prepared = null
        prepareJob?.cancel()
        val enabled = dataStoreManager.aiDj.first() == DataStoreManager.TRUE
        if (!enabled) {
            sinceLastComment = 0
            introPending = true
            return
        }

        if (ready != null && ready.forMediaId == item.mediaId) {
            sinceLastComment = 0
            introPending = false
            speak(ready.text, ready.clip, synthesize = false)
        } else if (introPending) {
            // Primera canción con el DJ encendido: saluda y la presenta (no hay comentario preparado).
            introPending = false
            sinceLastComment = 0
            scope.launch(Dispatchers.IO) { introFor(item) }
        } else {
            sinceLastComment++
        }
        val justPlayed = describe(item)

        // Si la SIGUIENTE canción es la que toca anunciar, se prepara ya, durante esta.
        if (sinceLastComment >= TRACKS_PER_COMMENT - 1) {
            prepareJob = scope.launch(Dispatchers.IO) { prepareFor(previous = justPlayed) }
        }
    }

    private suspend fun prepareFor(previous: String?) {
        val next =
            try {
                if (player.hasNextMediaItem()) player.getMediaItemAt(player.currentMediaItemIndex + 1) else null
            } catch (_: Exception) {
                null
            } ?: return
        val nextDescription = describe(next) ?: return
        val languageCode = runCatching { dataStoreManager.language.first().take(2) }.getOrDefault("es").ifBlank { "es" }
        val languageName = Locale.forLanguageTag(languageCode).getDisplayLanguage(Locale.ENGLISH)

        val line =
            aiClient.djLine(previous, nextDescription, languageName, taste = DjSession.tasteProfile, avoid = recentSnapshot()).getOrElse {
                Logger.w(TAG, "La IA no pudo escribir el comentario: ${it.message}")
                return
            }
        val spoken = sanitize(line)
        if (spoken.isEmpty()) return
        remember(spoken)
        // La voz neuronal se genera ya, durante la canción anterior, para que no haya espera al hablar.
        prepared = Prepared(next.mediaId, spoken, synthesizeWithNotice(spoken))
    }

    private suspend fun introFor(item: GenericMediaItem) {
        val languageCode = runCatching { dataStoreManager.language.first().take(2) }.getOrDefault("es").ifBlank { "es" }
        val description = describe(item)
        if (description == null) {
            // La canción aún no trae título: se saluda igual en vez de quedarse mudo.
            Logger.w(TAG, "Intro sin título de canción; se usa el saludo local")
            speak(fallbackIntro(item, languageCode))
            return
        }
        showToast("DJ MaKa: preparando el saludo…")
        val languageName = Locale.forLanguageTag(languageCode).getDisplayLanguage(Locale.ENGLISH)
        val line =
            aiClient.djLine(null, description, languageName, localTime = localTimeLabel(), taste = DjSession.tasteProfile, avoid = recentSnapshot()).getOrElse {
                Logger.w(TAG, "La IA no pudo escribir la intro: ${it.message}")
                // Antes se quedaba mudo sin decir nada. Ahora se avisa del motivo y se saluda igual.
                showToast("DJ: la IA falló (${it.message?.take(90) ?: "sin detalle"}). Revisa tu API key y modelo en Ajustes.")
                speak(fallbackIntro(item, languageCode))
                return
            }
        val spoken = sanitize(line, max = 420)
        if (spoken.isNotEmpty()) {
            remember(spoken)
            speak(spoken)
        }
    }

    private fun speak(
        text: String,
        clip: SpeechClip? = null,
        synthesize: Boolean = true,
    ) {
        speakJob?.cancel()
        speakJob =
            scope.launch(Dispatchers.IO) {
                // Voz neuronal: se genera mientras la canción carga y ANTES de bajar la música, para no
                // dejarla atenuada mientras se espera al audio.
                val voiceClip = clip ?: if (synthesize) synthesizeWithNotice(text) else null
                // La intro puede estar lista antes de que la canción arranque del todo: se espera un poco.
                // La canción llega por streaming y puede tardar en sonar: se espera hasta 20 s y, si aun así
                // no ha arrancado, se habla igualmente en vez de quedarse mudo.
                withTimeoutOrNull(PLAYBACK_WAIT_MS) {
                    while (!isPlaying()) delay(150)
                }
                if (isSleepTimerActive()) return@launch
                if (dataStoreManager.aiDj.first() != DataStoreManager.TRUE) return@launch
                var ducked = false
                try {
                    if (voiceClip != null) {
                        player.sleepFadeFactor = DUCKED_LEVEL
                        ducked = true
                        delay(150)
                        playClip(voiceClip)
                        return@launch
                    }
                    val engine =
                        ensureTts() ?: run {
                            showToast("DJ: el teléfono no tiene un motor de voz (TTS) disponible.")
                            return@launch
                        }
                    val languageCode =
                        runCatching { dataStoreManager.language.first().take(2) }.getOrDefault("es").ifBlank { "es" }
                    applyLanguage(engine, languageCode)
                    player.sleepFadeFactor = DUCKED_LEVEL
                    ducked = true
                    delay(150)

                    // Se habla por frases, con una pausa corta entre ellas: suena a persona, no a lector.
                    val chunks = splitSentences(text)
                    if (chunks.isEmpty()) return@launch
                    val stamp = "dj-${System.nanoTime()}"
                    val lastId = "$stamp-last"
                    val done = CompletableDeferred<Unit>()
                    engine.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                if (utteranceId == lastId) done.complete(Unit)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                if (utteranceId?.startsWith(stamp) == true) {
                                    Logger.w(TAG, "El motor de voz devolvió error en $utteranceId")
                                    // Puede ser una voz de red sin conexión: la próxima vez se eligen solo voces instaladas.
                                    offlineVoicesOnly = true
                                    ttsLanguageCode = null
                                    scope.launch { showToast("DJ: el motor de voz del teléfono dio un error al hablar.") }
                                    done.complete(Unit)
                                }
                            }

                            override fun onStop(
                                utteranceId: String?,
                                interrupted: Boolean,
                            ) {
                                if (utteranceId?.startsWith(stamp) == true) done.complete(Unit)
                            }
                        },
                    )
                    var ok = true
                    chunks.forEachIndexed { index, chunk ->
                        val isLast = index == chunks.lastIndex
                        val id = if (isLast) lastId else "$stamp-$index"
                        val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                        if (engine.speak(chunk, mode, Bundle(), id) != TextToSpeech.SUCCESS) ok = false
                        if (!isLast) engine.playSilentUtterance(PAUSE_BETWEEN_SENTENCES_MS, TextToSpeech.QUEUE_ADD, "$stamp-p$index")
                    }
                    if (ok) {
                        withTimeoutOrNull(MAX_SPEECH_MS) { done.await() }
                    } else {
                        showToast("DJ: la voz del teléfono rechazó el texto.")
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        runCatching { tts?.stop() }
                        throw e
                    }
                    Logger.w(TAG, "No se pudo reproducir la voz del DJ: ${e.message}")
                } finally {
                    withContext(NonCancellable) {
                        if (ducked && !isSleepTimerActive()) restoreMusic()
                    }
                }
            }
    }

    /** Genera la voz neuronal; si falla, avisa una vez por sesión y el DJ sigue con la voz del teléfono. */
    private suspend fun synthesizeWithNotice(text: String): SpeechClip? {
        val clip = neuralVoice.synthesize(text)
        val problem = neuralVoice.lastError
        if (clip == null && problem != null && !voiceProblemShown) {
            voiceProblemShown = true
            showToast("DJ: la voz de Gemini falló ($problem). Uso la voz del teléfono.")
        }
        return clip
    }

    /** Reproduce el audio de la voz neuronal (PCM) y espera a que termine. */
    private suspend fun playClip(clip: SpeechClip) {
        val format =
            AudioFormat
                .Builder()
                .setSampleRate(clip.sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        val attributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        val track =
            AudioTrack
                .Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(clip.pcm.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        try {
            track.write(clip.pcm, 0, clip.pcm.size)
            track.play()
            delay(clip.durationMs + 300)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Parte el texto en frases (por . ! ? …) para hablarlas una a una. */
    private fun splitSentences(text: String): List<String> =
        text
            .split(Regex("(?<=[.!?…])\\s+"))
            .map { it.trim() }
            .filter { it.any(Char::isLetterOrDigit) }

    private val ttsMutex = kotlinx.coroutines.sync.Mutex()

    /** Crea el motor TTS la primera vez que hace falta (y solo si el DJ está activado). */
    private suspend fun ensureTts(): TextToSpeech? =
        ttsMutex.withLock {
            if (ttsReady) return@withLock tts
            val ready = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
            }
            val ok = withTimeoutOrNull(5_000L) { ready.await() } ?: false
            if (!ok) {
                Logger.w(TAG, "El motor de voz del teléfono no está disponible")
                withContext(Dispatchers.Main) {
                    tts?.shutdown()
                    tts = null
                }
                return@withLock null
            }
            runCatching {
                tts?.setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
            ttsReady = true
            tts
        }

    private fun applyLanguage(
        engine: TextToSpeech,
        languageCode: String,
    ) {
        if (ttsLanguageCode == languageCode) return
        val locale = Locale.forLanguageTag(languageCode)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
        } else {
            // Entre las voces instaladas se elige la de mejor calidad (la voz por defecto suele ser la peor).
            pickBestVoice(engine, locale)?.let { voice ->
                if (engine.setVoice(voice) == TextToSpeech.ERROR) {
                    Logger.w(TAG, "No se pudo usar la voz ${voice.name}; se queda la de por defecto")
                }
            }
        }
        engine.setSpeechRate(SPEECH_RATE)
        engine.setPitch(SPEECH_PITCH)
        ttsLanguageCode = languageCode
    }

    /**
     * La mejor voz para el idioma: mayor calidad (las voces de red de Google, más naturales, entran;
     * el DJ ya necesita internet para pedir el texto a la IA) y, a igualdad, la de la misma región que
     * el teléfono. Si una voz de red falla, [offlineVoicesOnly] la descarta y se usan solo las instaladas.
     */
    private fun pickBestVoice(
        engine: TextToSpeech,
        locale: Locale,
    ): Voice? {
        val device = Locale.getDefault()
        val candidates =
            runCatching { engine.voices }
                .getOrNull()
                .orEmpty()
                .filter { it.locale.language == locale.language }
                .filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                .filter { !offlineVoicesOnly || !it.isNetworkConnectionRequired }
        return candidates.maxWithOrNull(
            compareBy<Voice>(
                { it.quality },
                { if (it.locale.country == device.country) 1 else 0 },
                { if (it.locale.country.isNotEmpty()) 1 else 0 },
            ),
        )
    }

    /** Sube la música de vuelta en ~0,6 s en vez de de golpe. */
    private suspend fun restoreMusic() {
        val steps = 12
        for (i in 1..steps) {
            player.sleepFadeFactor = DUCKED_LEVEL + (1f - DUCKED_LEVEL) * i / steps
            delay(50)
        }
        player.sleepFadeFactor = 1f
    }

    /** Saludo local (sin IA) para cuando la IA no responde: así el DJ nunca arranca en silencio. */
    private fun fallbackIntro(
        item: GenericMediaItem,
        languageCode: String,
    ): String {
        val title = item.metadata.title?.takeIf { it.isNotBlank() }
        val artist = item.metadata.artist?.takeIf { it.isNotBlank() }
        val song = listOfNotNull(title, artist).joinToString(", de ").ifBlank { null }
        return if (languageCode == "es") {
            "Hola, soy DJ MaKa y arrancamos la sesión" + (song?.let { " con $it" } ?: "") + ". Sube ese volumen."
        } else {
            "Hey, it's DJ MaKa and we're kicking off the session" + (song?.let { " with $it" } ?: "") + ". Turn it up."
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            runCatching { android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    private fun describe(item: GenericMediaItem): String? {
        val title = item.metadata.title?.takeIf { it.isNotBlank() } ?: return null
        val artist = item.metadata.artist?.takeIf { it.isNotBlank() }
        return if (artist != null) "$title - $artist" else title
    }

    /** Hora local del teléfono, para que el saludo sea "buenos días / tardes / noches" de verdad. */
    private fun localTimeLabel(): String {
        val cal = java.util.Calendar.getInstance()
        return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }

    private fun sanitize(
        raw: String,
        max: Int = 300,
    ): String =
        raw
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace("&", " y ")
            .replace(Regex("[*_#`>\"“”«»\\[\\]]"), "")
            .replace(Regex("\\s+[-–—]\\s+"), ", ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(max)
}
