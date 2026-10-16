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
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class Spotify {
    private val spotifyClient = SpotifyClient()
    private val spotifyAuth = SpotifyAuth(spotifyClient)
    private val webPlayerHashes = WebPlayerHashes(spotifyClient)

    // api.spotify.com/v1 answers the web player's token with a 429 that can last 24 hours, and every
    // further request while it lasts only keeps the block alive. After one, that route is left alone.
    private var publicApiBlockedUntil: TimeMark? = null

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
        val response =
            spotifyClient.getSpotifyLyrics(
                token = token,
                clientToken = clientToken,
                trackId,
            )
        // A track without lyrics answers 404 with a body that is not the lyrics shape: reading it
        // through content negotiation threw "Expected response body of the type ... but was ...".
        // Check the status first and decode the text ourselves, whatever Content-Type came back.
        if (!response.status.isSuccess()) {
            throw SpotifyHttpException(response.status.value, "lyrics", response.headers["Retry-After"]?.trim()?.toLongOrNull())
        }
        val decoded = lyricsJson.decodeFromString(SpotifyLyricsResponse.serializer(), response.bodyAsText())
        // An answer with no lines is "no lyrics", not an empty lyrics screen.
        if (decoded.lyrics.lines.isEmpty()) throw SpotifyHttpException(404, "lyrics")
        decoded
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
     * Three routes are tried, in this order, and the first that answers wins:
     *  1. the web player's own GraphQL query (what open.spotify.com uses to draw the playlist);
     *  2. the public Web API, unless it recently answered 429 (it can ask for 24 hours);
     *  3. the playlist's public embed page, which needs no token but lists only 100 tracks.
     *
     * [onProgress] receives (tracks read so far, total) after each page.
     */
    suspend fun getPlaylistTracks(
        token: String,
        playlistId: String,
        clientToken: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<List<SpotifyTrack>> {
        val isLiked = playlistId == SpotifyPlaylistSummary.LIKED_SONGS_ID
        var failure: Throwable? = null

        // 1. The web player's own route.
        try {
            fetchTracksViaPathfinder(token, clientToken, playlistId, isLiked, onProgress)?.let { return Result.success(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpotifyHttpException) {
            // An expired token is for the caller to refresh; anything else moves on to the next route.
            if (e.code == 401) return Result.failure(e)
            failure = e
        } catch (e: Exception) {
            Logger.w("SpotifyLibrary", "web player route failed: ${e.message}")
            failure = e
        }

        // 2. The public Web API, unless it is known to be refusing this token.
        val blocked = publicApiBlockedUntil
        if (blocked == null || !blocked.hasNotPassedNow()) {
            publicApiBlockedUntil = null
            try {
                return Result.success(fetchTracksViaPublicApi(token, playlistId, isLiked, onProgress))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpotifyHttpException) {
                if (e.code == 401) return Result.failure(e)
                if (e.code == 429) {
                    val wait = (e.retryAfterSeconds ?: 60L).coerceIn(10L, 3600L)
                    publicApiBlockedUntil = TimeSource.Monotonic.markNow() + wait.seconds
                }
                failure = e
            } catch (e: Exception) {
                failure = failure ?: e
            }
        } else {
            Logger.w("SpotifyLibrary", "public Web API is rate-limited, not asking it for the playlist")
        }

        // 3. The public embed page: no token, no rate limit of the account's, first 100 tracks only.
        if (!isLiked) {
            try {
                val embedded = fetchTracksViaEmbed(playlistId)
                if (embedded.isNotEmpty()) {
                    Logger.w("SpotifyLibrary", "playlist read from its embed page: at most 100 tracks (${embedded.size} found)")
                    onProgress(embedded.size, embedded.size)
                    return Result.success(embedded)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w("SpotifyLibrary", "embed route failed: ${e.message}")
                failure = failure ?: e
            }
        }
        return Result.failure(failure ?: IllegalStateException("No route could read the playlist"))
    }

    /** The playlist read through the web player's GraphQL query, or null when that route cannot be used. */
    private suspend fun fetchTracksViaPathfinder(
        token: String,
        clientToken: String?,
        playlistId: String,
        isLiked: Boolean,
        onProgress: (Int, Int) -> Unit,
    ): List<SpotifyTrack>? {
        val operations = if (isLiked) listOf("fetchLibraryTracks") else listOf("fetchPlaylistContents", "fetchPlaylist")
        for (operation in operations) {
            val hash = webPlayerHashes.hashFor(operation) ?: continue
            try {
                readPathfinderPages(token, clientToken, operation, hash, playlistId, isLiked, onProgress)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpotifyHttpException) {
                if (e.code == 401 || e.code == 429) throw e
                Logger.w("SpotifyLibrary", "$operation failed: ${e.message}")
            } catch (e: Exception) {
                Logger.w("SpotifyLibrary", "$operation failed: ${e.message}")
            }
        }
        return null
    }

    private suspend fun readPathfinderPages(
        token: String,
        clientToken: String?,
        operation: String,
        hash: String,
        playlistId: String,
        isLiked: Boolean,
        onProgress: (Int, Int) -> Unit,
    ): List<SpotifyTrack>? {
        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        var pages = 0
        while (pages < MAX_PAGES) {
            val variables =
                when {
                    isLiked -> "{\"offset\":$offset,\"limit\":$PATHFINDER_PAGE_SIZE}"
                    operation == "fetchPlaylist" ->
                        "{\"uri\":\"spotify:playlist:$playlistId\",\"offset\":$offset,\"limit\":$PATHFINDER_PAGE_SIZE," +
                            "\"enableWatchFeedEntrypoint\":false}"
                    else -> "{\"uri\":\"spotify:playlist:$playlistId\",\"offset\":$offset,\"limit\":$PATHFINDER_PAGE_SIZE}"
                }
            val response = spotifyClient.getPathfinder(token, clientToken, operation, hash, variables)
            val body = response.requireSuccess("playlist tracks (web player)").bodyAsText()
            val page = parsePathfinderTracks(body)
            if (pages == 0 && page.tracks.isEmpty()) {
                // Spotify says the playlist is empty: that is an answer. Otherwise the reply was not
                // understood, so let the next route try.
                if (page.total == 0) return emptyList()
                Logger.w("SpotifyLibrary", "$operation answered but no tracks were recognised: ${body.take(300)}")
                return null
            }
            tracks += page.tracks
            pages++
            offset += page.rawCount
            val total = page.total
            onProgress(tracks.size, total ?: tracks.size)
            val done =
                page.rawCount == 0 ||
                    (total != null && offset >= total) ||
                    (total == null && page.rawCount < PATHFINDER_PAGE_SIZE)
            if (done) break
        }
        return tracks
    }

    /** The playlist read through api.spotify.com/v1: complete, but its 429 can last a day. */
    private suspend fun fetchTracksViaPublicApi(
        token: String,
        playlistId: String,
        isLiked: Boolean,
        onProgress: (Int, Int) -> Unit,
    ): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()
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
        return tracks
    }

    /** The first 100 tracks of a public playlist, read from its embed page. */
    private suspend fun fetchTracksViaEmbed(playlistId: String): List<SpotifyTrack> {
        val response = spotifyClient.getWebText("https://open.spotify.com/embed/playlist/$playlistId")
        val html = response.requireSuccess("playlist embed").bodyAsText()
        return parseEmbedTracks(html)
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
        const val PATHFINDER_PAGE_SIZE = 100

        /** Lyrics replies come with whatever Content-Type Spotify picks, so they are decoded by hand. */
        val lyricsJson =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }

        /** 100 pages of 100 is 10,000 tracks — Spotify's own cap for a playlist. */
        const val MAX_PAGES = 100
    }
}