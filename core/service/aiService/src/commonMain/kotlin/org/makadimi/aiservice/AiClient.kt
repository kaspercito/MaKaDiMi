package org.makadimi.aiservice

import com.aallam.openai.api.exception.OpenAIAPIException
import com.kaspercito.domain.data.model.metadata.Lyrics
import com.kaspercito.domain.data.model.taste.TasteException
import com.kaspercito.domain.data.model.taste.TasteInput
import com.kaspercito.domain.data.model.taste.TasteReading
import kotlin.coroutines.cancellation.CancellationException

class AiClient {
    private var aiService: AiService? = null
    var host = AIHost.GEMINI
        set(value) {
            field = value
            rebuildAiService()
        }
    var apiKey: String? = null
        set(value) {
            field = value
            rebuildAiService()
        }
    var customModelId: String? = null
        set(value) {
            field = value
            rebuildAiService()
        }
    var customBaseUrl: String? = null
        set(value) {
            field = value
            rebuildAiService()
        }
    var customHeaders: Map<String, String>? = null
        set(value) {
            field = value
            rebuildAiService()
        }

    private fun rebuildAiService() {
        aiService =
            if (apiKey != null) {
                AiService(
                    aiHost = host,
                    apiKey = apiKey!!,
                    customModelId = customModelId,
                    customBaseUrl = customBaseUrl,
                    customHeaders = customHeaders,
                )
            } else {
                null
            }
    }

    suspend fun translateLyrics(
        inputLyrics: Lyrics,
        targetLanguage: String,
    ): Result<Lyrics> =
        runCatching {
            val result =
                aiService?.translateLyrics(inputLyrics, targetLanguage)
                    ?: throw IllegalStateException("AI service is not initialized. Please set host and apiKey.")

            // Validate: check that at least some lines were actually translated
            val originalWords = inputLyrics.lines?.map { it.words } ?: emptyList()
            val translatedWords = result.lines?.map { it.words } ?: emptyList()
            val unchangedCount = originalWords.zip(translatedWords).count { (orig, trans) -> orig == trans }
            val translatableCount = originalWords.count { it.trim().isNotEmpty() && it.trim() != "♫" }

            // Reject if >80% of translatable lines are unchanged (likely same language or translation failed)
            if (translatableCount > 0 && unchangedCount.toFloat() / translatableCount > 0.8f) {
                throw IllegalStateException("Translation failed or returned empty lyrics or same language.")
            }

            result
        }

    /**
     * A reading of the listener's taste.
     *
     * Every failure comes back as a [TasteException] sorted by what the user can do about it: a
     * refused key or model is fixed in Settings, everything else is worth another try. The layers
     * above never see this library's own exception types.
     */
    suspend fun describeTaste(input: TasteInput): Result<TasteReading> {
        val service = aiService ?: return Result.failure(TasteException(TasteException.Kind.NOT_CONFIGURED))
        return try {
            Result.success(service.describeTaste(input))
        } catch (e: CancellationException) {
            throw e
        } catch (e: TasteException) {
            Result.failure(e)
        } catch (e: OpenAIAPIException) {
            val kind = if (e.statusCode in REJECTED_STATUS) TasteException.Kind.REJECTED else TasteException.Kind.TEMPORARY
            Result.failure(TasteException(kind, e.statusCode, e.error.detail?.message ?: e.message, e))
        } catch (e: Exception) {
            Result.failure(TasteException(TasteException.Kind.TEMPORARY, message = e.message, cause = e))
        }
    }

    /**
     * Frase de locutor DJ para presentar [next] tras [previous]. Falla en silencio: el DJ es opcional.
     *
     * [taste] resume los gustos del oyente para personalizar el comentario; [avoid] son las últimas
     * frases que ya dijo el DJ, para que no se repita.
     */
    suspend fun djLine(
        previous: String?,
        next: String,
        language: String,
        localTime: String? = null,
        taste: String? = null,
        avoid: List<String> = emptyList(),
    ): Result<String> {
        val service = aiService ?: return Result.failure(IllegalStateException("AI service is not initialized."))
        return try {
            val prompt =
                buildString {
                    append("Idioma de la respuesta: ").append(language).append('\n')
                    if (!taste.isNullOrBlank()) {
                        append("Gustos del oyente: ").append(taste).append('\n')
                    }
                    if (avoid.isNotEmpty()) {
                        append("Frases que ya dijiste (no las repitas): ")
                        append(avoid.joinToString(" | ")).append('\n')
                    }
                    if (!previous.isNullOrBlank()) {
                        append("Acaba de sonar: ").append(previous).append('\n')
                    } else {
                        // Sin canción anterior = arranque de la sesión: intro de DJ, un poco más larga.
                        append("INTRO DE LA SESIÓN (máximo 55 palabras): saluda según la hora local")
                        if (!localTime.isNullOrBlank()) append(" (").append(localTime).append(')')
                        append(", preséntate como DJ MaKa, di que hoy pones música pensada para el oyente y ")
                        append("presenta la primera canción con entusiasmo.\n")
                    }
                    append("Va a sonar ahora: ").append(next)
                }
            val line = service.djLine(prompt)
            if (line == null) Result.failure(IllegalStateException("Empty DJ line")) else Result.success(line)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Canciones sugeridas ("Artista - Título") para la cola del DJ, ancladas a los gustos del
     * oyente y a la canción de arranque [seed]. [avoid] son canciones que no deben repetirse.
     */
    suspend fun suggestSongs(
        taste: String,
        seed: String,
        avoid: List<String>,
        count: Int = 24,
    ): Result<List<String>> {
        val service = aiService ?: return Result.failure(IllegalStateException("AI service is not initialized."))
        return try {
            val prompt =
                buildString {
                    append("Gustos del oyente: ").append(taste).append('\n')
                    append("Canción con la que arranca la sesión: ").append(seed).append('\n')
                    if (avoid.isNotEmpty()) {
                        append("NO repetir estas canciones: ").append(avoid.joinToString(" | ")).append('\n')
                    }
                    append("Dame exactamente ").append(count).append(" canciones.")
                }
            val raw = service.suggestSongs(prompt) ?: return Result.failure(IllegalStateException("Empty suggestions"))
            val parsed = parseSuggestions(raw)
            if (parsed.isEmpty()) Result.failure(IllegalStateException("Unreadable suggestions")) else Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        /** Bad request, bad key, no access, unknown model: retrying cannot help, Settings can. */
        val REJECTED_STATUS = setOf(400, 401, 403, 404)
    }
}

/**
 * Lee la respuesta del modelo con tolerancia: un arreglo JSON de cadenas (con o sin cercas ```),
 * o, si el modelo ignoró el formato, una canción por línea.
 */
internal fun parseSuggestions(raw: String): List<String> {
    val cleaned = raw.replace("```json", "").replace("```", "").trim()
    val fromJson =
        runCatching {
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            if (start < 0 || end <= start) {
                emptyList()
            } else {
                kotlinx.serialization.json.Json
                    .parseToJsonElement(cleaned.substring(start, end + 1))
                    .let { it as kotlinx.serialization.json.JsonArray }
                    .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }
        }.getOrDefault(emptyList())
    val lines =
        fromJson.ifEmpty {
            cleaned
                .lines()
                .map { it.trim().trimStart('-', '*', '•').replace(Regex("^\\d+[.)]\\s*"), "").trim().trim('"', ',') }
        }
    return lines
        .map { it.trim() }
        .filter { it.contains(" - ") && it.length in 5..160 }
        .distinct()
}
