package com.kaspercito.data.dj

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.kaspercito.domain.data.player.GenericMediaItem
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
import kotlinx.coroutines.flow.distinctUntilChangedBy
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

/** Tiempo máximo que se espera a que termine de hablar, por si el motor TTS no avisa. */
private const val MAX_SPEECH_MS = 25_000L

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
    )

    @Volatile
    private var prepared: Prepared? = null
    private var prepareJob: Job? = null
    private var speakJob: Job? = null
    private var sinceLastComment = 0

    /** Verdadero hasta que el DJ salude: así la sesión arranca con voz y no en silencio. */
    private var introPending = true

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    @Volatile
    private var ttsLanguageCode: String? = null

    fun start() {
        scope.launch {
            nowPlaying
                .filterNotNull()
                .distinctUntilChangedBy { it.mediaId }
                .collect { onTrackStarted(it) }
        }
    }

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
            speak(ready.text)
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
            aiClient.djLine(previous, nextDescription, languageName).getOrElse {
                Logger.w(TAG, "La IA no pudo escribir el comentario: ${it.message}")
                return
            }
        val spoken = sanitize(line)
        if (spoken.isEmpty()) return
        prepared = Prepared(next.mediaId, spoken)
    }

    private suspend fun introFor(item: GenericMediaItem) {
        val description = describe(item) ?: return
        val languageCode = runCatching { dataStoreManager.language.first().take(2) }.getOrDefault("es").ifBlank { "es" }
        val languageName = Locale.forLanguageTag(languageCode).getDisplayLanguage(Locale.ENGLISH)
        val line =
            aiClient.djLine(null, description, languageName).getOrElse {
                Logger.w(TAG, "La IA no pudo escribir la intro: ${it.message}")
                return
            }
        val spoken = sanitize(line)
        if (spoken.isNotEmpty()) speak(spoken)
    }

    private fun speak(text: String) {
        speakJob?.cancel()
        speakJob =
            scope.launch(Dispatchers.IO) {
                if (!isPlaying() || isSleepTimerActive()) return@launch
                var ducked = false
                try {
                    val engine = ensureTts() ?: return@launch
                    val languageCode =
                        runCatching { dataStoreManager.language.first().take(2) }.getOrDefault("es").ifBlank { "es" }
                    applyLanguage(engine, languageCode)
                    player.sleepFadeFactor = DUCKED_LEVEL
                    ducked = true
                    delay(150)
                    val done = CompletableDeferred<Unit>()
                    val id = "dj-${System.nanoTime()}"
                    engine.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                if (utteranceId == id) done.complete(Unit)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                if (utteranceId == id) done.complete(Unit)
                            }

                            override fun onStop(
                                utteranceId: String?,
                                interrupted: Boolean,
                            ) {
                                if (utteranceId == id) done.complete(Unit)
                            }
                        },
                    )
                    val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
                    if (result == TextToSpeech.SUCCESS) {
                        withTimeoutOrNull(MAX_SPEECH_MS) { done.await() }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        tts?.stop()
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
            ttsReady = true
            tts
        }

    private fun applyLanguage(
        engine: TextToSpeech,
        languageCode: String,
    ) {
        if (ttsLanguageCode == languageCode) return
        val result = engine.setLanguage(Locale.forLanguageTag(languageCode))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
        }
        engine.setSpeechRate(1.05f)
        ttsLanguageCode = languageCode
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

    private fun describe(item: GenericMediaItem): String? {
        val title = item.metadata.title?.takeIf { it.isNotBlank() } ?: return null
        val artist = item.metadata.artist?.takeIf { it.isNotBlank() }
        return if (artist != null) "$title - $artist" else title
    }

    private fun sanitize(raw: String): String =
        raw
            .replace(Regex("[*_#`>\"“”]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(300)
}
