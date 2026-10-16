package com.kaspercito.spotify

import com.kaspercito.spotify.auth.SpotifyAuth
import com.kaspercito.spotify.model.library.SpotifyHttpException
import com.kaspercito.spotify.model.library.SpotifyPage
import com.kaspercito.spotify.model.library.SpotifyPlaylistDto
import com.kaspercito.spotify.model.library.SpotifyPlaylistSummary
import com.kaspercito.spotify.model.library.SpotifyTrack
import com.kaspercito.spotify.model.library.SpotifyTrackDto
import com.kaspercito.spotify.model.library.SpotifyTrackEntryDto
import com.kaspercito.spotify.model.response.spotify.CanvasResponse
import com.kaspercito.spotify.model.response.spotify.ClientTokenResponse
import com.kaspercito.spotify.model.response.spotify.PersonalTokenResponse
import com.kaspercito.spotify.model.response.spotify.SpotifyLyricsResponse
import com.kaspercito.spotify.model.response.spotify.search.SpotifySearchResponse
import io.ktor.client.call.body
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.http
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import com.kaspercito.logger.Logger
import io.ktor.http.isSuccess

class Spotify {
    private val spotifyClient = SpotifyClient()
    private val spotifyAuth = SpotifyAuth(spotifyClient)

    /**
     * Remove proxy for client
     */
    fun removeProxy() {
        spotifyClient.proxy = null
    }

    /**
     * Set the proxy for client
     */
    fun setProxy(
        isHttp: Boolean,
        host: String,
        port: Int,
    ) {
        val verifiedHost =
            if (!host.contains("http")) {
                "http://$host"
            } else {
                host
            }
        runCatching {
            if (isHttp) ProxyBuilder.http("$verifiedHost:$port") else ProxyBuilder.socks(verifiedHost, port)
        }.onSuccess {
            spotifyClient.proxy = it
        }.onFailure {
            it.printStackTrace()
        }
    }

    /**
     * Get personal token using the standard method
     */
    suspend fun getPersonalToken(spdc: String) =
        runCatching {
            spotifyClient.getSpotifyLyricsToken(spdc).body<PersonalTokenResponse>()
        }

    /**
     * Get personal token using the more reliable TOTP-based method
     * This should be used when the standard method fails
     */
    suspend fun getPersonalTokenWithTotp(spdc: String) = spotifyAuth.refreshToken(spdc)

    suspend fun getClientToken() =
        runCatching {
            spotifyClient
                .getSpotifyClientToken()
                .body<ClientTokenResponse>()
        }

    suspend fun searchSpotifyTrack(
        query: String,
        authToken: String,
        clientToken: String,
    ) = runCatching {
        spotifyClient
            .searchSpotifyTrack(query, authToken, clientToken)
            .body<SpotifySearchResponse>()
    }

    suspend fun getSpotifyLyrics(
        trackId: String,
        token: String,
        clientToken: String,
    ) = runCatching {
        spotifyClient
            .getSpotifyLyrics(
                token = token,
                clientToken = clientToken,
                trackId,
            ).body<SpotifyLyricsResponse>()
    }

    suspend fun getSpotifyCanvas(
        trackId: String,
        token: String,
        clientToken: String,
    ) = runCatching {
        spotifyClient.getSpotifyCanvas(trackId, token, clientToken).body<CanvasResponse>()
    }

    // ---------------------------------------------------------------- Library (playlists)

    /**
     * Every playlist the user owns or follows, plus their saved tracks as a pseudo-playlist
     * ([SpotifyPlaylistSummary.LIKED_SONGS_ID]) placed first, like the Spotify app does.
     */
    suspend fun getMyPlaylists(
        token: String,
        clientToken: String? = null,
    ): Result<List<SpotifyPlaylistSummary>> {
        // The web player's own library query first: the public /v1/me/playlists answers 429 to this
        // token. If it fails or comes back empty, the public route is still tried.
        try {
            val fromLibrary = fetchLibraryPlaylists(token, clientToken)
            if (fromLibrary.isNotEmpty()) return Result.success(fromLibrary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("SpotifyLibrary", "libraryV3 failed (${e.message}), trying the public API")
        }
        return getMyPlaylistsViaWebApi(token)
    }

    private suspend fun fetchLibraryPlaylists(
        token: String,
        clientToken: String?,
    ): List<SpotifyPlaylistSummary> {
        val found = LinkedHashMap<String, SpotifyPlaylistSummary>()
        var offset = 0
        var pages = 0
        while (pages < MAX_PAGES) {
            val response = spotifyClient.getLibraryV3(token, clientToken, offset, PLAYLIST_PAGE_SIZE)
            val body = response.requireSuccess("library").bodyAsText()
            val page = parseLibraryV3(body)
            if (pages == 0 && page.playlists.isEmpty()) {
                // Nothing recognised: keep the start of the reply in the log so the shape can be fixed.
                Logger.e("SpotifyLibrary", "libraryV3 gave no playlists: ${body.take(600)}")
            }
            page.playlists.forEach { found.putIfAbsent(it.id, it) }
            pages++
            offset += page.rawCount
            val total = page.total
            val done =
                page.rawCount == 0 ||
                    (total != null && offset >= total) ||
                    (total == null && page.rawCount < PLAYLIST_PAGE_SIZE)
            if (done) break
        }
        // Saved tracks come first, like the Spotify app lists them.
        val liked = found.remove(SpotifyPlaylistSummary.LIKED_SONGS_ID)
        return buildList {
            if (liked != null) add(liked)
            addAll(found.values)
        }
    }

    private suspend fun getMyPlaylistsViaWebApi(token: String): Result<List<SpotifyPlaylistSummary>> =
        runCatching {
            val playlists = mutableListOf<SpotifyPlaylistSummary>()
            var offset = 0
            var pages = 0
            while (pages < MAX_PAGES) {
                val response = spotifyClient.getMyPlaylists(token, offset, PLAYLIST_PAGE_SIZE)
                val page = response.requireSuccess("playlists").body<SpotifyPage<SpotifyPlaylistDto>>()
                page.items.filterNotNull().forEach { dto ->
                    val id = dto.id ?: return@forEach
                    playlists +=
                        SpotifyPlaylistSummary(
                            id = id,
                            name = dto.name?.takeIf { it.isNotBlank() } ?: "Playlist",
                            imageUrl = dto.images.bestImage(),
                            trackCount = dto.tracks?.total ?: dto.items?.total ?: 0,
                            ownerName = dto.owner?.displayName ?: dto.owner?.id,
                        )
                }
                pages++
                offset += PLAYLIST_PAGE_SIZE
                if (page.next == null || page.items.isEmpty()) break
            }
            // The saved-tracks count costs one tiny request; failing to get it must not hide the
            // playlists, so it falls back to "unknown" (0) instead of failing the whole call.
            val liked =
                runCatching {
                    spotifyClient
                        .getSavedTracks(token, 0, 1)
                        .requireSuccess("saved tracks")
                        .body<SpotifyPage<SpotifyTrackEntryDto>>()
                        .total
                }.getOrNull()
            if (liked != null && liked > 0) {
                playlists.add(
                    0,
                    SpotifyPlaylistSummary(
                        id = SpotifyPlaylistSummary.LIKED_SONGS_ID,
                        name = "",
                        imageUrl = null,
                        trackCount = liked,
                        ownerName = null,
                    ),
                )
            }
            playlists
        }

    /**
     * Every playable track of a playlist, in order. Podcast episodes and local files (which have
     * no Spotify id and nothing to look up) are left out.
     *
     * [onProgress] receives (tracks read so far, total) after each page.
     */
    suspend fun getPlaylistTracks(
        token: String,
        playlistId: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<List<SpotifyTrack>> =
        runCatching {
            val tracks = mutableListOf<SpotifyTrack>()
            val isLiked = playlistId == SpotifyPlaylistSummary.LIKED_SONGS_ID
            // `tracks` is the long-standing path; `items` is what newer API versions call it. Try
            // the first and only fall to the second when it is refused or missing.
            var path = "tracks"
            var offset = 0
            var pages = 0
            while (pages < MAX_PAGES) {
                val response: HttpResponse =
                    when {
                        isLiked -> spotifyClient.getSavedTracks(token, offset, TRACK_PAGE_SIZE)
                        else -> spotifyClient.getPlaylistTracks(token, playlistId, path, offset, TRACK_PAGE_SIZE)
                    }
                if (!isLiked && pages == 0 && !response.status.isSuccess() && response.status.value != 429 && path == "tracks") {
                    path = "items"
                    continue
                }
                val page = response.requireSuccess("playlist tracks").body<SpotifyPage<SpotifyTrackEntryDto>>()
                page.items.filterNotNull().forEach { entry ->
                    (entry.track ?: entry.item)?.toSpotifyTrack()?.let { tracks += it }
                }
                pages++
                offset += TRACK_PAGE_SIZE
                onProgress(tracks.size, page.total)
                if (page.next == null || page.items.isEmpty()) break
            }
            tracks
        }

    private fun HttpResponse.requireSuccess(what: String): HttpResponse {
        if (!status.isSuccess()) {
            throw SpotifyHttpException(status.value, what, headers["Retry-After"]?.trim()?.toLongOrNull())
        }
        return this
    }

    private fun List<com.kaspercito.spotify.model.library.SpotifyImageDto>?.bestImage(): String? =
        this
            ?.filter { !it.url.isNullOrEmpty() }
            // Large enough for a grid tile, without pulling the 640px original for every card.
            ?.sortedBy { kotlin.math.abs((it.width ?: 300) - 300) }
            ?.firstOrNull()
            ?.url

    private fun SpotifyTrackDto.toSpotifyTrack(): SpotifyTrack? {
        if (isLocal == true) return null
        if (type != null && type != "track") return null
        val title = name?.takeIf { it.isNotBlank() } ?: return null
        return SpotifyTrack(
            id = id,
            title = title,
            artists = artists.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } },
            album = album?.name,
            durationMs = durationMs ?: 0L,
            imageUrl = album?.images.bestImage(),
        )
    }

    private companion object {
        const val PLAYLIST_PAGE_SIZE = 50
        const val TRACK_PAGE_SIZE = 100

        /** 100 pages of 100 is 10,000 tracks — Spotify's own cap for a playlist. */
        const val MAX_PAGES = 100
    }
}