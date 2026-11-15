package org.makadimi.aiservice

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.JsonSchema
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import com.aallam.openai.client.OpenAIHost.Companion.Gemini
import com.kaspercito.domain.data.model.metadata.Line
import com.kaspercito.domain.data.model.metadata.Lyrics
import com.kaspercito.domain.data.model.taste.TasteException
import com.kaspercito.domain.data.model.taste.TasteInput
import com.kaspercito.domain.data.model.taste.TasteReading
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration.Companion.minutes

class AiService(
    private val aiHost: AIHost = AIHost.GEMINI,
    private val apiKey: String,
    private val customModelId: String? = null,
    private val customBaseUrl: String? = null,
    private val customHeaders: Map<String, String>? = null,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    private val openAI: OpenAI by lazy {
        when (aiHost) {
            AIHost.GEMINI -> {
                OpenAI(host = Gemini, token = apiKey, timeout = REQUEST_TIMEOUT)
            }

            AIHost.OPENAI -> {
                OpenAI(token = apiKey, timeout = REQUEST_TIMEOUT)
            }

            AIHost.CUSTOM_OPENAI -> {
                val baseUrl = customBaseUrl ?: "https://api.openai.com/v1/"
                val config =
                    OpenAIConfig(
                        token = apiKey,
                        timeout = REQUEST_TIMEOUT,
                        host = OpenAIHost(baseUrl = baseUrl),
                        headers = customHeaders ?: emptyMap(),
                    )
                OpenAI(config)
            }
        }
    }

    private val model by lazy {
        if (!customModelId.isNullOrEmpty()) {
            ModelId(customModelId)
        } else {
            when (aiHost) {
                AIHost.GEMINI -> ModelId("gemini-3.5-flash")
                AIHost.OPENAI -> ModelId("gpt-4o")
                AIHost.CUSTOM_OPENAI -> ModelId("gpt-4o")
            }
        }
    }

    /**
     * One reading of the listener's taste from [input].
     *
     * Sent without a `responseFormat`, unlike [translateLyrics]: providers reached through the
     * custom OpenAI-compatible path do not all accept `json_schema` (issue #2297 traced DeepSeek's
     * chat endpoint to `json_object` only), and a refused format fails the whole request. The
     * prompt asks for a bare JSON object instead, and [parseTasteReading] reads it leniently.
     */
    suspend fun describeTaste(input: TasteInput): TasteReading {
        val request =
            chatCompletionRequest {
                this.model = this@AiService.model
                messages {
                    system {
                        content = TASTE_SYSTEM_PROMPT
                    }
                    user {
                        content {
                            text(input.toPromptData())
                        }
                    }
                }
            }
        val raw =
            openAI
                .chatCompletion(request)
                .choices
                .firstOrNull()
                ?.message
                ?.content
                ?: throw TasteException(TasteException.Kind.TEMPORARY, message = "Empty response from AI")
        return parseTasteReading(raw)
            ?: throw TasteException(TasteException.Kind.TEMPORARY, message = "Unreadable response from AI")
    }

    /**
     * Una intervención corta de "locutor DJ" para presentar la siguiente canción.
     *
     * Texto libre, sin `responseFormat` (igual que [describeTaste]): no todos los proveedores
     * compatibles con OpenAI aceptan formatos estructurados. Devuelve null si la respuesta viene vacía.
     */
    suspend fun djLine(prompt: String): String? {
        val request =
            chatCompletionRequest {
                this.model = this@AiService.model
                messages {
                    system {
                        content = DJ_SYSTEM_PROMPT
                    }
                    user {
                        content {
                            text(prompt)
                        }
                    }
                }
            }
        return openAI
            .chatCompletion(request)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Sugerencias de canciones para la cola del DJ, como texto libre (un JSON de cadenas
     * "Artista - Título"). Sin `responseFormat` por la misma razón que [describeTaste].
     */
    suspend fun suggestSongs(prompt: String): String? {
        val request =
            chatCompletionRequest {
                this.model = this@AiService.model
                messages {
                    system {
                        content = SUGGEST_SYSTEM_PROMPT
                    }
                    user {
                        content {
                            text(prompt)
                        }
                    }
                }
            }
        return openAI
            .chatCompletion(request)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    suspend fun translateLyrics(
        inputLyrics: Lyrics,
        targetLanguage: String,
    ): Lyrics {
        val lines = inputLyrics.lines ?: throw IllegalStateException("No lyrics lines to translate")

        // Build key-value map: index -> words (only non-empty lines)
        val indexToWords = mutableMapOf<String, String>()
        lines.forEachIndexed { index, line ->
            val words = line.words.trim()
            if (words.isNotEmpty() && words != "♫") {
                indexToWords[index.toString()] = words
            }
        }

        if (indexToWords.isEmpty()) {
            throw IllegalStateException("No translatable lyrics lines found")
        }

        val inputJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), indexToWords)

        val request =
            chatCompletionRequest {
                this.model = this@AiService.model
                responseFormat = ChatResponseFormat.jsonSchema(aiResponseJsonSchema)
                messages {
                    system {
                        content =
                            "You are a song lyrics translation assistant.\n" +
                            "\n" +
                            "TASK:\n" +
                            "- You will receive a JSON object where keys are line indices and values are lyrics text.\n" +
                            "- FIRST, detect the dominant language of the input lyrics.\n" +
                            "- If the detected language is the SAME as the target language code, return an EMPTY \"translations\" object. Do NOT translate. Do NOT paraphrase.\n" +
                            "- Otherwise, translate ONLY the values to the target language.\n" +
                            "- When translating: keep ALL keys exactly the same, output MUST have the EXACT same number of entries as the input, do NOT merge/split/add/remove any entries, and preserve the song's meaning, tone, and emotion.\n" +
                            "\n" +
                            "OUTPUT:\n" +
                            "- A JSON object with the \"translations\" field containing the same keys mapped to translated values (or an empty object when the input is already in the target language)."
                    }
                    user {
                        content {
                            text("Target language: $targetLanguage")
                        }
                        content {
                            text("Input lyrics: $inputJson")
                        }
                    }
                }
            }
        val completion: ChatCompletion = openAI.chatCompletion(request)
        val jsonContent =
            completion.choices
                .firstOrNull()
                ?.message
                ?.content ?: throw IllegalStateException("No response from AI")
        val jsonData =
            Regex(
                "```json\\s*([\\s\\S]*?)```",
            ).find(jsonContent)
                ?.groups
                ?.firstOrNull()
                ?.value ?: jsonContent
        val cleanedJson = jsonData.replace("```json", "").replace("```", "")
        val translationResponse = json.decodeFromString<TranslationResponse>(cleanedJson)
        val translatedMap = translationResponse.translations
        if (translatedMap.isEmpty()) {
            throw IllegalStateException(
                "Input lyrics are already in the target language ($targetLanguage). Translation aborted.",
            )
        }

        // Map translated text back to original lines, preserving all timestamps
        val translatedLines = lines.mapIndexed { index, originalLine ->
            val translatedWords = translatedMap[index.toString()]
            if (translatedWords != null) {
                Line(
                    startTimeMs = originalLine.startTimeMs,
                    endTimeMs = originalLine.endTimeMs,
                    words = translatedWords,
                    syllables = null,
                )
            } else {
                // Non-translatable line (empty or ♫): keep original
                Line(
                    startTimeMs = originalLine.startTimeMs,
                    endTimeMs = originalLine.endTimeMs,
                    words = originalLine.words,
                    syllables = originalLine.syllables,
                )
            }
        }

        return Lyrics(
            error = false,
            lines = translatedLines,
            syncType = inputLyrics.syncType,
        )
    }

    companion object {
        /**
         * How long a request may go without a byte coming back. The library's default is 30 s,
         * which a model that thinks before answering — or a router in front of one — exceeds on an
         * open-ended prompt like the taste reading, and the request then dies with
         * "Socket timeout has expired" while the provider is still working on it.
         */
        private val REQUEST_TIMEOUT = Timeout(socket = 2.minutes)

        private const val DJ_SYSTEM_PROMPT =
            "Eres DJ MaKa, un locutor de radio con mucha energía, carisma y buena onda, como un DJ de verdad. " +
                "Tu texto NO se lee: se pronuncia en voz alta con un sintetizador, así que escribe para el oído. " +
                "Recibirás la canción que acaba de sonar (si la hay), la que va a sonar y, a veces, los gustos del " +
                "oyente. Si NO hay canción anterior, es el arranque de la sesión: saluda con entusiasmo y presenta " +
                "la primera. Responde SOLO con lo que dirías al micrófono: una o dos frases cortas (máximo 35 palabras; " +
                "55 en la intro de la sesión). Reglas: frases cortas y naturales, con comas donde tomarías aire; " +
                "sin emojis, sin comillas, sin paréntesis, sin guiones, sin hashtags, sin listas, sin markdown, sin " +
                "abreviaturas ni símbolos (escribe 'y' en vez de '&' y los números con letras). Di el nombre del " +
                "artista y de la canción tal cual los recibes. Si te dan los gustos del oyente, de vez en cuando " +
                "(no siempre) haz un guiño personal: que la canción encaja con lo que suele escuchar, o con su " +
                "artista favorito, pero sin inventar nada que no esté en esos datos. Varía el estilo: a veces hype, " +
                "a veces relajado, a veces un guiño al estado de ánimo que crea la música. Si te dicen frases que ya " +
                "dijiste, no repitas ni su estructura ni sus muletillas. No inventes datos que no conozcas con " +
                "seguridad (años, premios, anécdotas). Responde en el idioma que se te pida."

        private const val SUGGEST_SYSTEM_PROMPT =
            "Eres un curador musical experto que arma la cola de una sesión de radio personal. " +
                "Recibirás los gustos reales del oyente (artistas y canciones que ya escucha), la canción con la que " +
                "arranca la sesión y una lista de canciones que NO debes repetir. " +
                "Tu trabajo: proponer canciones que ENCAJEN con esos gustos y que el oyente disfrutaría de verdad. " +
                "REGLAS ESTRICTAS: " +
                "1) Mantente en el MISMO género, idioma, época y nivel de energía que la canción inicial y los gustos. " +
                "Nunca saltes a un género distinto (si escucha trap latino, no propongas rock, baladas ni pop de otro estilo). " +
                "2) Mezcla: más o menos la mitad de la lista debe ser de artistas que ya escucha o muy cercanos a ellos " +
                "(colaboradores habituales, mismo movimiento o escena) y la otra mitad, éxitos del mismo estilo. " +
                "3) Solo canciones reales, populares y fáciles de encontrar en YouTube Music; no inventes títulos. " +
                "4) No repitas canciones de la lista prohibida ni la inicial; evita repetir el mismo artista más de dos veces. " +
                "5) Ordena la lista como una buena sesión: alterna energías sin romper el estilo. " +
                "FORMATO: responde SOLO con un arreglo JSON de cadenas con el formato \"Artista - Título\", " +
                "sin texto extra, sin markdown, sin numeración."

        private val translationJsonSchema: JsonObject =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("translations") {
                        put("type", "object")
                        putJsonObject("additionalProperties") {
                            put("type", "string")
                        }
                    }
                }
                putJsonArray("required") {
                    add("translations")
                }
            }
        private val aiResponseJsonSchema =
            JsonSchema(
                name = "ai_translation_schema",
                schema = translationJsonSchema,
                strict = false,
            )
    }
}

@kotlinx.serialization.Serializable
data class TranslationResponse(
    val translations: Map<String, String> = emptyMap(),
)

enum class AIHost {
    GEMINI,
    OPENAI,
    CUSTOM_OPENAI,
}