package com.kaspercito.kotlinytmusicscraper.models.body

import com.kaspercito.kotlinytmusicscraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class LikeBody(
    val context: Context,
    val target: Target,
) {
    @Serializable
    data class Target(
        val videoId: String,
    )
}