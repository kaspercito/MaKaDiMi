package com.kaspercito.data.dj

import com.kaspercito.domain.manager.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Voz neuronal del DJ con Deepgram Aura-2 (voces en español e inglés).
 *
 * Deepgram regala 200 USD de crédito al registrarse (sin tarjeta y sin caducidad), lo que da para
 * millones de caracteres: miles de comentarios del DJ. Es la voz principal; Gemini y la voz del
 * teléfono quedan de respaldo.
 *
 * Sin clave, con un idioma sin voz, o ante cualquier fallo, [synthesize] devuelve null.
 */
internal class DeepgramVoice(
    private val dataStoreManager: DataStoreManager,
) {
    /** Último motivo de fallo (para avisar en pantalla); null si no hubo fallo. */
    @Volatile
    var lastError: String? = null
        private set

    /** true si hay una clave de Deepgram guardada. */
    @Volatile
    var configured = false
        private set

    /** Tras un error de clave o de crédito no se insiste durante un rato. */
    @Volatile
    private var pausedUntilMs = 0L

    suspend fun synthesize(
        text: String,
        languageCode: String,
    ): SpeechClip? =
        withContext(Dispatchers.IO) {
            val key = dataStoreManager.djVoiceDeepgramKey.first().trim()
            configured = key.isNotEmpty()
            lastError = null
            if (!configured || text.isBlank()) return@withContext null
            val model =
                when (languageCode) {
                    "es" -> ES_MODEL
                    "en" -> EN_MODEL
                    else -> return@withContext null
                }
            if (System.currentTimeMillis() < pausedUntilMs) {
                lastError = "pausado tras un error de clave o crédito"
                return@withContext null
            }
            request(key, model, text)
        }

    private fun request(
        key: String,
        model: String,
        text: String,
    ): SpeechClip? {
        val connection =
            URL("https://api.deepgram.com/v1/speak?model=$model&encoding=linear16&sample_rate=$SAMPLE_RATE&container=none")
                .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Token $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(JSONObject().put("text", text.take(MAX_CHARS)).toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val raw = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message =
                    runCatching {
                        val json = JSONObject(raw)
                        json.optString("err_msg").ifBlank { json.optString("message") }.ifBlank { json.optString("reason") }
                    }.getOrNull().orEmpty()
                lastError = "Deepgram $code $message".trim().take(160)
                // 401/402/403: clave inválida o sin crédito; no tiene sentido reintentar en cada comentario.
                if (code == 401 || code == 402 || code == 403) pausedUntilMs = System.currentTimeMillis() + PAUSE_MS
                return null
            }
            var pcm = connection.inputStream.use { it.readBytes() }
            // Por si el servidor devuelve un WAV con cabecera en vez de PCM puro.
            if (pcm.size > 44 && pcm[0] == 'R'.code.toByte() && pcm[1] == 'I'.code.toByte() && pcm[2] == 'F'.code.toByte() && pcm[3] == 'F'.code.toByte()) {
                pcm = pcm.copyOfRange(44, pcm.size)
            }
            if (pcm.size % 2 != 0) pcm = pcm.copyOf(pcm.size - 1)
            if (pcm.size < 2) {
                lastError = "Deepgram devolvió audio vacío"
                null
            } else {
                SpeechClip(pcm, SAMPLE_RATE)
            }
        } catch (e: Exception) {
            lastError = "Deepgram: ${e.message ?: e.javaClass.simpleName}".take(160)
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        /** Voz colombiana, enérgica y amigable: encaja con un locutor de radio. Alternativas: aura-2-nestor-es, aura-2-sirio-es. */
        const val ES_MODEL = "aura-2-celeste-es"
        const val EN_MODEL = "aura-2-thalia-en"
        const val SAMPLE_RATE = 24_000
        const val MAX_CHARS = 1_900
        const val PAUSE_MS = 30 * 60 * 1000L
    }
}
