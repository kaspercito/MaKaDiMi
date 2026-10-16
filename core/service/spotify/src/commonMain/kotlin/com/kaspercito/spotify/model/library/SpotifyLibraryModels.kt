package com.kaspercito.spotify.model.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the app needs to know about a Spotify playlist, whichever shape of the Web API produced it.
 * [id] is [LIKED_SONGS_ID] for the pseudo-playlist made of the user's saved tracks.
 */
data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int,
    val ownerName: String?,
) {
    companion object {
        const val LIKED_SONGS_ID = "liked_songs"
    }
}

/** One track of a Spotify playlist. Only what is needed to find the same song on YouTube Music. */
data class SpotifyTrack(
    val id: String?,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationMs: Long,
    val imageUrl: String?,
)

/** Spotify answered a library request with a non-2xx [code]. 401/403 mean the token is not accepted. */
class SpotifyHttpException(
    val code: Int,
    what: String,
    /** Seconds Spotify asked us to wait (Retry-After header) when it answered 429, if it said. */
    val retryAfterSeconds: Long? = null,
) : Exception("Spotify $what request answered $code")

// ---- Wire models. Every field is optional on purpose: the web player's token is answered by
// ---- endpoints Spotify changes without notice, and one renamed field must not sink the page.

@Serializable
internal data class SpotifyPage<T>(
    @SerialName("items") val items: List<T?> = emptyList(),
    @SerialName("total") val total: Int = 0,
    @SerialName("next") val next: String? = null,
)

@Serializable
internal data class SpotifyImageDto(
    @SerialName("url") val url: String? = null,
    @SerialName("width") val width: Int? = null,
)

@Serializable
internal data class SpotifyOwnerDto(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("id") val id: String? = null,
)

@Serializable
internal data class SpotifyCountDto(
    @SerialName("total") val total: Int = 0,
)

@Serializable
internal data class SpotifyPlaylistDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("images") val images: List<SpotifyImageDto>? = null,
    @SerialName("owner") val owner: SpotifyOwnerDto? = null,
    // Spotify has called the count `tracks` and, in newer API versions, `items`.
    @SerialName("tracks") val tracks: SpotifyCountDto? = null,
    @SerialName("items") val items: SpotifyCountDto? = null,
)

@Serializable
internal data class SpotifyArtistDto(
    @SerialName("name") val name: String? = null,
)

@Serializable
internal data class SpotifyAlbumDto(
    @SerialName("name") val name: String? = null,
    @SerialName("images") val images: List<SpotifyImageDto>? = null,
)

@Serializable
internal data class SpotifyTrackDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("is_local") val isLocal: Boolean? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("artists") val artists: List<SpotifyArtistDto>? = null,
    @SerialName("album") val album: SpotifyAlbumDto? = null,
)

/** A playlist row (`track`, or `item` in newer API versions) or a saved-track row (`track`). */
@Serializable
internal data class SpotifyTrackEntryDto(
    @SerialName("track") val track: SpotifyTrackDto? = null,
    @SerialName("item") val item: SpotifyTrackDto? = null,
)
