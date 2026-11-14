package com.kaspercito.data.dj

import android.content.Context
import com.kaspercito.domain.manager.DataStoreManager

/**
 * Voz natural del DJ. Prueba en orden:
 *  1. Deepgram (si hay clave): crédito gratuito enorme, es la principal.
 *  2. Gemini (si hay clave): respaldo, con cuota gratuita pequeña.
 * Si ninguna da audio devuelve null y el DJ usa la voz del teléfono.
 */
internal class NeuralVoice(
    context: Context,
    dataStoreManager: DataStoreManager,
) {
    private val deepgram = DeepgramVoice(dataStoreManager)
    private val gemini = GeminiVoice(context, dataStoreManager)

    /** Motivo del último fallo de la voz natural (para avisar en pantalla); null si no hubo. */
    @Volatile
    var lastError: String? = null
        private set

    /** true si lo único que falla es la cuota diaria de Gemini (y no hay Deepgram que lo cubra). */
    @Volatile
    var dailyQuotaExhausted = false
        private set

    suspend fun synthesize(
        text: String,
        languageCode: String,
    ): SpeechClip? {
        lastError = null
        dailyQuotaExhausted = false
        deepgram.synthesize(text, languageCode)?.let { return it }
        val deepgramError = deepgram.lastError
        gemini.synthesize(text)?.let { return it }
        dailyQuotaExhausted = gemini.dailyQuotaExhausted && !deepgram.configured
        lastError = listOfNotNull(deepgramError, gemini.lastError).joinToString(" | ").ifBlank { null }
        return null
    }
}
