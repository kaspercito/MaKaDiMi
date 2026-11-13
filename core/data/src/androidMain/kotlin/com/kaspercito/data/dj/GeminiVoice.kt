package com.kaspercito.data.dj

import android.util.Base64
import com.kaspercito.domain.manager.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Audio ya generado por la voz neuronal: PCM de 16 bits, mono. */
internal class SpeechClip(
    val pcm: ByteArray,
    val sampleRate: Int,
) {
    val durationMs: Long get() = (pcm.size / 2) * 1000L / sampleRate
}

/**
 * Voz neuronal del DJ con la API oficial de Gemini (texto a voz). Suena mucho más natural que el
 * motor de voz del teléfono.
 *
 *  - Necesita una clave de Gemini (gratis en aistudio.google.com) guardada en Ajustes. Sin clave
 *    [synthesize] devuelve null y el DJ usa la voz del teléfono, como siempre.
 *  - Si algo falla (sin internet, cuota, clave mala) devuelve null y deja el motivo en [lastError];
 *    el DJ sigue con la voz del teléfono para no quedarse mudo.
 */
internal class GeminiVoice(
    private val dataStoreManager: DataStoreManager,
) {
    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var workingModel: String? = null

    private class Outcome(
        val clip: SpeechClip?,
        val code: Int,
        val message: String?,
    )

    suspend fun synthesize(text: String): SpeechClip? =
        withContext(Dispatchers.IO) {
            val key = dataStoreManager.djVoiceApiKey.first().trim()
            if (key.isEmpty() || text.isBlank()) return@withContext null
            lastError = null
            val candidates = listOfNotNull(workingModel) + MODELS.filter { it != workingModel }
            for (model in candidates) {
                val outcome = request(key, model, text)
                if (outcome.clip != null) {
                    workingModel = model
                    return@withContext outcome.clip
                }
                // 404: ese modelo ya no existe; se prueba el siguiente.
                if (outcome.code == 404) {
                    lastError = "modelo $model no disponible"
                    continue
                }
                lastError = "${outcome.code} ${outcome.message.orEmpty()}".trim().take(120)
                return@withContext null
            }
            null
        }

    private fun request(
        key: String,
        model: String,
        text: String,
    ): Outcome {
        val body =
            JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", "$STYLE_PROMPT$text")),
                        ),
                    ),
                ).put(
                    "generationConfig",
                    JSONObject()
                        .put("responseModalities", JSONArray().put("AUDIO"))
                        .put(
                            "speechConfig",
                            JSONObject().put(
                                "voiceConfig",
                                JSONObject().put(
                                    "prebuiltVoiceConfig",
                                    JSONObject().put("voiceName", VOICE_NAME),
                                ),
                            ),
                        ),
                ).toString()
        val connection = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", key)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrNull()
                return Outcome(null, code, message)
            }
            val inline =
                JSONObject(raw)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getJSONObject("inlineData")
            val pcm = Base64.decode(inline.getString("data"), Base64.DEFAULT)
            val rate = Regex("rate=(\\d+)").find(inline.optString("mimeType"))?.groupValues?.get(1)?.toIntOrNull() ?: 24_000
            if (pcm.size < 2) {
                Outcome(null, 200, "audio vacío")
            } else {
                Outcome(SpeechClip(if (pcm.size % 2 == 0) pcm else pcm.copyOf(pcm.size - 1), rate), 200, null)
            }
        } catch (e: Exception) {
            Outcome(null, -1, e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        /** Primero el modelo actual; si Google lo renombra o retira, se prueba el anterior. */
        val MODELS = listOf("gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts")

        /** Voz alegre y con energía, a juego con un locutor de radio. */
        const val VOICE_NAME = "Puck"

        /** Indicación de estilo; el texto del DJ va detrás, tal cual. */
        const val STYLE_PROMPT = "Dilo con energía de locutor de radio, cercano y natural: "
    }
}
