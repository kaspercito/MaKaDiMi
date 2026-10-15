package com.kaspercito.kotlinytmusicscraper.models.body

import com.kaspercito.kotlinytmusicscraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)