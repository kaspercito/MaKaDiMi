package com.kaspercito.kotlinytmusicscraper.models.body

import com.kaspercito.kotlinytmusicscraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)