package com.kaspercito.domain.data.model.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Lyrics(
    val error: Boolean = false,
    val lines: List<Line>?,
    val syncType: String?,
    @SerialName("simpMusicLyrics")
    val makadimiLyrics: MakadimiLyrics? = null,
)

@Serializable
data class MakadimiLyrics(
    val id: String,
    val vote: Int,
)