package com.kaspercito.data.dj

import android.content.Context
import android.util.Base64
import com.kaspercito.domain.manager.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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
 * La capa gratuita de Google es MUY corta (unas 3 peticiones por minuto y unas 10 al día por modelo),
 * así que esta clase la cuida para que dure lo máximo posible:
 *  - Separa las peticiones al menos [MIN_GAP_MS] para no pasar del límite por minuto.
 *  - Si Google dice "espera N segundos" (límite por minuto), espera y reintenta una vez.
 *  - Si dice que se acabó la cuota del DÍA, apaga ese modelo hasta que se renueve (medianoche del
 *    Pacífico) y prueba el siguiente. Si no queda ninguno, deja de intentarlo hasta la renovación:
 *    el DJ sigue con la voz del teléfono sin perder tiempo ni repetir avisos. Ese estado se guarda
 *    en disco, así que sobrevive a cerrar la app.
 *  - Al cambiar de clave se olvida todo lo anterior (otra clave = otra cuota).
 *
 * Sin clave, o ante cualquier fallo, [synthesize] devuelve null y el DJ usa la voz del teléfono.
 */
internal class GeminiVoice(
    context: Context,
    private val dataStoreManager: DataStoreManager,
) {
    private val prefs = context.applicationContext.getSharedPreferences("dj_voice_quota", Context.MODE_PRIVATE)
    private val gate = Mutex()

    @Volatile
    private var lastRequestAt = 0L

    /** Último motivo de fallo (para avisar en pantalla); null si no hubo fallo. */
    @Volatile
    var lastError: String? = null
        private set

    /** true si la cuota diaria está agotada: la voz natural vuelve a estar disponible tras la renovación. */
    @Volatile
    var dailyQuotaExhausted = false
        private set

    @Volatile
    private var workingModel: String? = null

    private class Outcome(
        val clip: SpeechClip?,
        val code: Int,
        val message: String?,
        val perDay: Boolean = false,
        val retryAfterMs: Long = 0L,
    )

    suspend fun synthesize(text: String): SpeechClip? =
        withContext(Dispatchers.IO) {
            val key = dataStoreManager.djVoiceApiKey.first().trim()
            if (key.isEmpty() || text.isBlank()) return@withContext null
            forgetStateIfKeyChanged(key)
            if (System.currentTimeMillis() < prefs.getLong(PAUSED_UNTIL, 0L)) {
                dailyQuotaExhausted = true
                lastError = null
                return@withContext null
            }
            dailyQuotaExhausted = false
            lastError = null
            gate.withLock { runRequests(key, text) }
        }

    private suspend fun runRequests(
        key: String,
        text: String,
    ): SpeechClip? {
        val candidates = (listOfNotNull(workingModel) + MODELS.filter { it != workingModel }).filter { !modelBlocked(it) }
        if (candidates.isEmpty()) {
            pauseUntilReset()
            return null
        }
        for (model in candidates) {
            var outcome = attempt(key, model, text)
            // Límite por minuto: Google dice cuánto esperar; si es razonable se espera y se reintenta una vez.
            if (outcome.code == 429 && !isDailyLimit(outcome) && outcome.retryAfterMs in 1..MAX_WAIT_MS) {
                delay(outcome.retryAfterMs + 500)
                outcome = attempt(key, model, text)
            }
            outcome.clip?.let {
                workingModel = model
                prefs.edit().putInt(USED_TODAY, usedToday() + 1).apply()
                return it
            }
            lastError = "${outcome.code} ${outcome.message.orEmpty()}".trim().take(160)
            when {
                // Modelo retirado: se prueba el siguiente.
                outcome.code == 404 -> continue
                // Cuota del día agotada en este modelo: se apaga hasta la renovación y se prueba el siguiente.
                outcome.code == 429 && isDailyLimit(outcome) -> {
                    prefs.edit().putLong(BLOCKED_PREFIX + model, nextResetMs()).apply()
                    continue
                }
                // Límite por minuto que no se pudo esperar: otro modelo tiene su propia cuota.
                outcome.code == 429 -> continue
                else -> return null
            }
        }
        if (MODELS.all { modelBlocked(it) }) pauseUntilReset()
        return null
    }

    /** Google casi siempre indica si el límite es "por día"; si no lo dice, se deduce de cuánto se ha usado hoy. */
    private fun isDailyLimit(outcome: Outcome): Boolean = outcome.perDay || (outcome.retryAfterMs == 0L && usedToday() >= GUESS_DAILY_USED)

    private fun pauseUntilReset() {
        prefs.edit().putLong(PAUSED_UNTIL, nextResetMs()).apply()
        dailyQuotaExhausted = true
    }

    private fun modelBlocked(model: String): Boolean = System.currentTimeMillis() < prefs.getLong(BLOCKED_PREFIX + model, 0L)

    private fun forgetStateIfKeyChanged(key: String) {
        val hash = key.hashCode()
        if (prefs.getInt(KEY_HASH, 0) != hash) {
            prefs.edit().clear().putInt(KEY_HASH, hash).apply()
            workingModel = null
        }
    }

    /** Peticiones correctas hoy (día del Pacífico, que es cuando Google renueva la cuota). */
    private fun usedToday(): Int {
        val today = LocalDate.now(PACIFIC).toString()
        if (prefs.getString(USED_DAY, null) != today) {
            prefs.edit().putString(USED_DAY, today).putInt(USED_TODAY, 0).apply()
            return 0
        }
        return prefs.getInt(USED_TODAY, 0)
    }

    /** Próxima medianoche del Pacífico, más un margen. */
    private fun nextResetMs(): Long =
        ZonedDateTime
            .now(PACIFIC)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(PACIFIC)
            .toInstant()
            .toEpochMilli() + 60_000L

    private suspend fun attempt(
        key: String,
        model: String,
        text: String,
    ): Outcome {
        // 3 peticiones por minuto: se espacian.
        val wait = lastRequestAt + MIN_GAP_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait.coerceAtMost(MIN_GAP_MS))
        lastRequestAt = System.currentTimeMillis()
        return request(key, model, text)
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
                val perDay = raw.contains("PerDay", ignoreCase = true)
                val retryMs =
                    Regex("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"")
                        .find(raw)
                        ?.groupValues
                        ?.get(1)
                        ?.toDoubleOrNull()
                        ?.let { (it * 1000).toLong() } ?: 0L
                return Outcome(null, code, message, perDay, retryMs)
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
        /** Primero el modelo actual; si Google lo renombra, lo retira o se le acaba la cuota, se prueba el siguiente. */
        val MODELS = listOf("gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts")

        /** Google renueva la cuota diaria a medianoche, hora del Pacífico. */
        val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

        /** Separación mínima entre peticiones (límite gratuito: 3 por minuto). */
        const val MIN_GAP_MS = 22_000L

        /** Lo máximo que se espera a que Google libere el límite por minuto. */
        const val MAX_WAIT_MS = 45_000L

        /** Si Google no dice qué límite es, con tantas peticiones hechas hoy se asume que es el diario. */
        const val GUESS_DAILY_USED = 5

        const val PAUSED_UNTIL = "paused_until"
        const val BLOCKED_PREFIX = "blocked_"
        const val USED_DAY = "used_day"
        const val USED_TODAY = "used_today"
        const val KEY_HASH = "key_hash"

        /** Voz alegre y con energía, a juego con un locutor de radio. */
        const val VOICE_NAME = "Puck"

        /** Indicación de estilo; el texto del DJ va detrás, tal cual. */
        const val STYLE_PROMPT = "Dilo con energía de locutor de radio, cercano y natural: "
    }
}
