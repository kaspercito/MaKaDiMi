package com.kaspercito.data.dj

import com.kaspercito.domain.data.player.GenericMediaItem
import com.kaspercito.domain.dj.DjSession
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.mediaservice.player.MediaPlayerInterface
import com.kaspercito.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.makadimi.aiservice.AiClient
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

private const val TAG = "DjAnnouncer"

/** Un comentario cada tantas canciones: pocas peticiones a la IA y la voz no cansa. */
private const val TRACKS_PER_COMMENT = 3

/** Volumen de la música (0..1) mientras habla el locutor. */
private const val DUCKED_LEVEL = 0.28f

/**
 * Locutor DJ con IA, al estilo del DJ de Spotify, solo para Windows.
 *
 * Diseñado para no notarse en el rendimiento:
 *  - Todo el trabajo (petición a la IA, síntesis de voz) se hace **por adelantado**, mientras
 *    suena la canción anterior, en hilos de IO y sin tocar el hilo de la interfaz.
 *  - Cuando empieza la canción anunciada, solo hay que reproducir un WAV ya listo: sin esperas.
 *  - La voz sale por Java Sound (sesión de audio propia) y la música se atenúa con
 *    [MediaPlayerInterface.sleepFadeFactor], que es por-manejador de mpv. Así bajar la música no
 *    baja también la voz (el `ao-volume` de mpv es compartido por toda la sesión de Windows).
 *  - Si la IA no está configurada, falla o no hay voz, no pasa nada: se salta el comentario.
 */
internal class DjAnnouncer(
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
        val wav: File,
    )

    @Volatile
    private var prepared: Prepared? = null
    private var prepareJob: Job? = null
    private var speakJob: Job? = null
    private var sinceLastComment = 0
    private var lastDescription: String? = null

    /** Verdadero hasta que el DJ salude: así la sesión arranca con voz y no en silencio. */
    private var introPending = false

    /** Últimas frases dichas, para pedirle a la IA que no se repita. */
    private val recentLines = ArrayDeque<String>()

    fun start() {
        if (!WindowsSpeech.isSupported) return
        // El DJ solo suena si TÚ lo enciendes con el botón DJ. El interruptor se guarda en disco,
        // así que al abrir la app se deja apagado (una sola vez por proceso).
        if (DjSession.consumeFreshProcess()) {
            scope.launch { dataStoreManager.setAiDj(false) }
        }
        // Al ENCENDER el DJ, la siguiente canción llega con saludo; al APAGARLO se calla en el acto.
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
        prepared?.wav?.delete()
        prepared = null
        sinceLastComment = 0
        speakJob?.cancel()
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
            ready?.wav?.delete()
            sinceLastComment = 0
            lastDescription = describe(item)
            return
        }

        if (ready != null && ready.forMediaId == item.mediaId) {
            sinceLastComment = 0
            introPending = false
            speak(ready)
        } else if (introPending) {
            // Primera canción con el DJ encendido: saluda y la presenta (no hay comentario preparado).
            ready?.wav?.delete()
            introPending = false
            sinceLastComment = 0
            prepareJob = scope.launch(Dispatchers.IO) { introFor(item) }
        } else {
            ready?.wav?.delete()
            sinceLastComment++
        }
        val justPlayed = describe(item)
        lastDescription = justPlayed

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
        val title = next.metadata.title?.takeIf { it.isNotBlank() } ?: return
        val artist = next.metadata.artist?.takeIf { it.isNotBlank() }
        val nextDescription = if (artist != null) "$title - $artist" else title

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
        val wav = File.createTempFile("makadimi-dj", ".wav").apply { deleteOnExit() }
        val ok = WindowsSpeech.synthesizeToWav(spoken, wav, languageCode)
        // Si mientras tanto se cambió de canción, esta preparación ya no vale.
        currentCoroutineContext().ensureActive()
        if (ok) {
            prepared?.wav?.delete()
            prepared = Prepared(next.mediaId, wav)
        } else {
            wav.delete()
        }
    }

    /** Saludo de arranque de la sesión: se escribe, se sintetiza y se dice sobre la primera canción. */
    private suspend fun introFor(item: GenericMediaItem) {
        val description = describe(item) ?: return
        val languageCode = runCatching { dataStoreManager.language.first().take(2) }.getOrDefault("es").ifBlank { "es" }
        val languageName = Locale.forLanguageTag(languageCode).getDisplayLanguage(Locale.ENGLISH)
        val line =
            aiClient
                .djLine(
                    null,
                    description,
                    languageName,
                    localTime = localTimeLabel(),
                    taste = DjSession.tasteProfile,
                    avoid = recentSnapshot(),
                ).getOrElse {
                    Logger.w(TAG, "La IA no pudo escribir la intro: ${it.message}")
                    return
                }
        val spoken = sanitize(line, max = 420)
        if (spoken.isEmpty()) return
        remember(spoken)
        val wav = File.createTempFile("makadimi-dj", ".wav").apply { deleteOnExit() }
        val ok = WindowsSpeech.synthesizeToWav(spoken, wav, languageCode)
        currentCoroutineContext().ensureActive()
        if (ok && dataStoreManager.aiDj.first() == DataStoreManager.TRUE) {
            speak(Prepared(item.mediaId, wav))
        } else {
            wav.delete()
        }
    }

    /** Hora local, para que el saludo sea "buenos días / tardes / noches" de verdad. */
    private fun localTimeLabel(): String {
        val cal = java.util.Calendar.getInstance()
        return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }

    private fun speak(ready: Prepared) {
        speakJob?.cancel()
        speakJob =
            scope.launch(Dispatchers.IO) {
                // La intro puede estar lista antes de que la canción arranque del todo: se espera un poco.
                val playing = kotlinx.coroutines.withTimeoutOrNull(4_000L) {
                    while (!isPlaying()) delay(150)
                    true
                } ?: false
                if (!playing || isSleepTimerActive()) {
                    ready.wav.delete()
                    return@launch
                }
                var ducked = false
                try {
                    player.sleepFadeFactor = DUCKED_LEVEL
                    ducked = true
                    delay(150)
                    runInterruptible { playBlocking(ready.wav) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Logger.w(TAG, "No se pudo reproducir la voz del DJ: ${e.message}")
                } finally {
                    withContext(NonCancellable) {
                        if (ducked && !isSleepTimerActive()) restoreMusic()
                        ready.wav.delete()
                    }
                }
            }
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

    private fun playBlocking(file: File) {
        AudioSystem.getAudioInputStream(file).use { stream ->
            val clip = AudioSystem.getClip()
            val finished = CountDownLatch(1)
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP || event.type == LineEvent.Type.CLOSE) finished.countDown()
            }
            clip.open(stream)
            try {
                clip.start()
                finished.await(clip.microsecondLength / 1000 + 3_000, TimeUnit.MILLISECONDS)
            } finally {
                clip.stop()
                clip.close()
            }
        }
    }

    private fun describe(item: GenericMediaItem): String? {
        val title = item.metadata.title?.takeIf { it.isNotBlank() } ?: return null
        val artist = item.metadata.artist?.takeIf { it.isNotBlank() }
        return if (artist != null) "$title - $artist" else title
    }

    /** Lo que se le pasa al sintetizador: una línea, sin formato ni símbolos que se lean en voz alta. */
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
