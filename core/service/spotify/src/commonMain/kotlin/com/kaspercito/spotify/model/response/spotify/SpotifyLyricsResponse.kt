package com.kaspercito.spotify.model.response.spotify

import kotlinx.serialization.Serializable

// Every field has a default: Spotify leaves out the end time of unsynced lyrics and sometimes whole
// objects, and a missing field must not turn a usable answer into a decoding error.
@Serializable
data class SpotifyLyricsResponse(
    val lyrics: Lyrics = Lyrics(),
) {
    @Serializable
    data class Lyrics(
        val syncType: String = "UNSYNCED",
        val lines: List<Line> = emptyList(),
    ) {
        @Serializable
        data class Line(
            val startTimeMs: String = "0",
            val endTimeMs: String = "0",
            val words: String = "",
        )
    }
}
