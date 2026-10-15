package com.kaspercito.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** Idioma y región del teléfono -> idioma / región que usa la app. */
class LocaleMappingTest {
    @Test
    fun regionalVariantFallsBackToTheLanguage() {
        assertEquals("es-ES", SUPPORTED_LANGUAGE.fromSystem(language = "es", country = "EC"))
        assertEquals("es-ES", SUPPORTED_LANGUAGE.fromSystem(language = "es"))
        assertEquals("pt-PT", SUPPORTED_LANGUAGE.fromSystem(language = "pt", country = "BR"))
    }

    @Test
    fun legacyLanguageCodesAreMapped() {
        assertEquals("iw-IL", SUPPORTED_LANGUAGE.fromSystem(language = "he"))
        assertEquals("id-ID", SUPPORTED_LANGUAGE.fromSystem(language = "in"))
    }

    @Test
    fun chineseUsesScriptOrRegion() {
        assertEquals("zh-Hant-TW", SUPPORTED_LANGUAGE.fromSystem(language = "zh", script = "Hant"))
        assertEquals("zh-Hant-TW", SUPPORTED_LANGUAGE.fromSystem(language = "zh", country = "HK"))
        assertEquals("zh-CN", SUPPORTED_LANGUAGE.fromSystem(language = "zh", country = "CN"))
    }

    @Test
    fun unknownLanguageFallsBackToEnglish() {
        assertEquals("en-US", SUPPORTED_LANGUAGE.fromSystem(language = "xx"))
    }

    @Test
    fun supportedCountryIsKeptAndUnknownBecomesUs() {
        assertEquals("EC", SUPPORTED_LOCATION.fromCountry("EC"))
        assertEquals("EC", SUPPORTED_LOCATION.fromCountry("ec"))
        assertEquals("EC", SUPPORTED_LOCATION.fromCountry("XX"))
        assertEquals("EC", SUPPORTED_LOCATION.fromCountry(null))
        assertEquals("EC", SUPPORTED_LOCATION.fromCountry(""))
    }
}
