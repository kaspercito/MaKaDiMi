package com.kaspercito.data.spotify

import com.kaspercito.data.db.datasource.LocalDataSource
import com.kaspercito.data.parser.search.parseSearchSong
import com.kaspercito.domain.data.entities.LocalPlaylistEntity
import com.kaspercito.domain.data.model.searchResult.songs.SongsResult
import com.kaspercito.domain.repository.SpotifyFailure
import com.kaspercito.domain.repository.SpotifyImportProgress
import com.kaspercito.domain.repository.SpotifyLibraryRepository
import com.kaspercito.domain.repository.SpotifyPlaylistInfo
import com.kaspercito.domain.repository.asMessage
import com.kaspercito.domain.utils.Resource
import com.kaspercito.domain.utils.toSongEntity
import com.kaspercito.domain.utils.toTrack
import com.kaspercito.kotlinytmusicscraper.YouTube
import com.kaspercito.logger.Logger
import com.kaspercito.spotify.Spotify
import com.kaspercito.spotify.model.library.SpotifyHttpException
import com.kaspercito.spotify.model.library.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val TAG = "SpotifyLibrary"

/** YouTube Music is searched this many tracks at a time: fast enough for 500 songs, gentle enough not to be throttled. */
private const val MATCH_CONCURRENCY = 4
private const val SONG_BATCH_SIZE = 500

private const val MAX_RATE_LIMIT_RETRIES = 3
private const val MAX_RATE_LIMIT_WAIT_SECONDS = 20L
private val CACHE_TTL = 5.minutes

internal class SpotifyLibraryRepositoryImpl(
    private val spotify: Spotify,
    private val youTube: YouTube,
    private val localDataSource: LocalDataSource,
    private val tokens: SpotifyTokenProvider,
) : SpotifyLibraryRepository {
    // Opening the tab again must not hit Spotify again: its rate limit on the web player's token is
    // tight and a 429 can last a long while. A fresh list is reused; a stale one still beats an error.
    private var cachedPlaylists: List<SpotifyPlaylistInfo>? = null
    private var cachedAt: TimeMark? = null

    override fun getPlaylists(): Flow<Resource<List<SpotifyPlaylistInfo>>> =
        flow {
            val cached = cachedPlaylists
            val age = cachedAt
            if (cached != null && age != null && age.elapsedNow() < CACHE_TTL) {
                emit(Resource.Success(cached))
                return@flow
            }
            withSpotifyToken { token ->
                spotify.getMyPlaylists(token, tokens.clientToken().getOrNull())
            }
                .onSuccess { list ->
                    val mapped =
                        list.map {
                            SpotifyPlaylistInfo(
                                id = it.id,
                                name = it.name,
                                imageUrl = it.imageUrl,
                                trackCount = it.trackCount,
                                ownerName = it.ownerName,
                            )
                        }
                    cachedPlaylists = mapped
                    cachedAt = TimeSource.Monotonic.markNow()
                    emit(Resource.Success(mapped))
                }.onFailure {
                    Logger.e(TAG, "loading playlists failed: ${it::class.simpleName}: ${it.message}")
                    val rateLimited = it is SpotifyHttpException && it.code == 429
                    if (rateLimited && cached != null) {
                        emit(Resource.Success(cached))
                    } else {
                        emit(Resource.Error(it.toFailure().asMessage()))
                    }
                }
        }.flowOn(Dispatchers.IO)

    override fun importPlaylist(
        playlist: SpotifyPlaylistInfo,
        title: String,
    ): Flow<SpotifyImportProgress> =
        channelFlow {
            send(SpotifyImportProgress.ReadingTracks(0, playlist.trackCount))
            val tracks =
                withSpotifyToken { token ->
                    spotify.getPlaylistTracks(token, playlist.id) { read, total ->
                        trySend(SpotifyImportProgress.ReadingTracks(read, total))
                    }
                }.getOrElse {
                    Logger.e(TAG, "reading '${playlist.name}' failed: ${it.message}")
                    send(SpotifyImportProgress.Error(it.toFailure()))
                    return@channelFlow
                }.distinctBy { it.id ?: "${it.title}|${it.artists}" }

            val total = tracks.size
            val matches = arrayOfNulls<SongsResult>(total)
            val semaphore = Semaphore(MATCH_CONCURRENCY)
            val counters = Mutex()
            var processed = 0
            var matched = 0
            send(SpotifyImportProgress.Matching(0, total, 0))
            coroutineScope {
                tracks
                    .mapIndexed { index, track ->
                        async {
                            val found = semaphore.withPermit { findOnYouTube(track) }
                            matches[index] = found
                            counters.withLock {
                                processed++
                                if (found != null) matched++
                                send(SpotifyImportProgress.Matching(processed, total, matched))
                            }
                        }
                    }.awaitAll()
            }

            // Playlist order, one entry per recording even if two Spotify tracks resolve to the same video.
            val found = matches.filterNotNull().distinctBy { it.videoId }
            val notFound =
                tracks
                    .filterIndexed { index, _ -> matches[index] == null }
                    .map { "${it.title} — ${it.artists.joinToString(", ")}" }

            if (found.isNotEmpty()) {
                runCatching {
                    val entities = found.map { it.toTrack().toSongEntity() }
                    entities.chunked(SONG_BATCH_SIZE).forEach { localDataSource.insertSongs(it) }
                    localDataSource.insertLocalPlaylistWithTracks(
                        localPlaylist =
                            LocalPlaylistEntity(
                                title = title,
                                thumbnail = playlist.imageUrl ?: entities.firstOrNull()?.thumbnails,
                                tracks = entities.map { it.videoId },
                            ),
                        videoIds = entities.map { it.videoId },
                    )
                }.onFailure {
                    Logger.e(TAG, "writing '$title' failed: ${it.message}")
                    send(SpotifyImportProgress.Error(SpotifyFailure.UNKNOWN))
                    return@channelFlow
                }
            }
            Logger.i(TAG, "'$title': ${found.size} of $total tracks matched")
            send(SpotifyImportProgress.Success(title, found.size, total, notFound))
        }.flowOn(Dispatchers.IO)

    /**
     * The YouTube Music song for [track], or null when nothing confident turns up.
     *
     * Two queries: the way people name the song ("title artist"), then a cleaned title with every
     * artist and the album, which rescues titles carrying "(feat. …)" or "- Remastered" decoration
     * that the first query lets drown the real match.
     */
    private suspend fun findOnYouTube(track: SpotifyTrack): SongsResult? {
        val mainArtist = track.artists.firstOrNull().orEmpty()
        val queries =
            listOf(
                "${track.title} $mainArtist",
                "${SpotifyTrackMatcher.normalizeTitle(track.title)} ${track.artists.joinToString(" ")} ${track.album.orEmpty()}",
            ).map { it.replace(Regex("\\s+"), " ").trim() }.distinct()
        for (query in queries) {
            val candidates = searchSongs(query)
            SpotifyTrackMatcher.best(track, candidates)?.let { return it }
        }
        return null
    }

    private suspend fun searchSongs(query: String): List<SongsResult> {
        repeat(2) { attempt ->
            val result = youTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            result.onSuccess { return parseSearchSong(it) }
            Logger.w(TAG, "search '$query' failed (attempt ${attempt + 1}): ${result.exceptionOrNull()?.message}")
            delay(500)
        }
        return emptyList()
    }

    /** Runs [block] with a token; if Spotify refuses it as expired, once more with a freshly minted one. */
    private suspend fun <T> withSpotifyToken(block: suspend (String) -> Result<T>): Result<T> {
        val first = tokens.personalToken().getOrElse { return Result.failure(it) }
        var result = block(first)
        var error = result.exceptionOrNull()
        if (error is SpotifyHttpException && error.code == 401) {
            val fresh = tokens.personalToken(forceRefresh = true).getOrElse { return Result.failure(it) }
            result = block(fresh)
            error = result.exceptionOrNull()
        }
        // 429 Too Many Requests: wait what Spotify asks (or back off 3s, 6s) and try again. When it
        // asks for longer than is reasonable to wait on a loading screen, give up right away.
        var attempt = 0
        while (error is SpotifyHttpException && error.code == 429 && attempt < MAX_RATE_LIMIT_RETRIES) {
            val waitSeconds = error.retryAfterSeconds ?: (3L * (attempt + 1))
            if (waitSeconds > MAX_RATE_LIMIT_WAIT_SECONDS) {
                Logger.w(TAG, "Spotify asked to wait ${waitSeconds}s (429), not waiting")
                break
            }
            Logger.w(TAG, "Spotify answered 429, retrying in ${waitSeconds}s")
            delay(waitSeconds.coerceAtLeast(1L) * 1000L)
            attempt++
            val token = tokens.personalToken().getOrElse { return Result.failure(it) }
            result = block(token)
            error = result.exceptionOrNull()
        }
        return result
    }

    private fun Throwable.toFailure(): SpotifyFailure =
        when {
            this is SpotifyNotLoggedInException -> SpotifyFailure.NOT_LOGGED_IN
            this is SpotifySessionExpiredException -> SpotifyFailure.SESSION_EXPIRED
            this is SpotifyHttpException && code == 401 -> SpotifyFailure.SESSION_EXPIRED
            else -> SpotifyFailure.UNKNOWN
        }
}
