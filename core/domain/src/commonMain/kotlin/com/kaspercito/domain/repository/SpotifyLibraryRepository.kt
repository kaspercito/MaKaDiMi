package com.kaspercito.domain.repository

import com.kaspercito.domain.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * A playlist of the signed-in Spotify account. [isLikedSongs] marks the pseudo-playlist made of the
 * saved tracks, whose [name] is empty because the app localizes it.
 */
data class SpotifyPlaylistInfo(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int,
    val ownerName: String?,
) {
    val isLikedSongs: Boolean get() = id == LIKED_SONGS_ID

    companion object {
        const val LIKED_SONGS_ID = "liked_songs"
    }
}

/** Why a Spotify request could not be completed, so the UI can say something useful in the user's language. */
enum class SpotifyFailure {
    /** No Spotify account is linked. */
    NOT_LOGGED_IN,

    /** The saved session (sp_dc cookie) was refused: the user has to log in to Spotify again. */
    SESSION_EXPIRED,

    /** Anything else — most often the network. */
    UNKNOWN,
}

sealed interface SpotifyImportProgress {
    /** Reading the playlist from Spotify. [total] is 0 until the first page lands. */
    data class ReadingTracks(
        val read: Int,
        val total: Int,
    ) : SpotifyImportProgress

    /** Looking each track up on YouTube Music. */
    data class Matching(
        val processed: Int,
        val total: Int,
        val matched: Int,
    ) : SpotifyImportProgress

    /**
     * Finished. [notFound] holds "Title — Artist" for every track that had no confident match, so
     * the user can see what is missing instead of a silently shorter playlist.
     */
    data class Success(
        val playlistTitle: String,
        val matched: Int,
        val total: Int,
        val notFound: List<String>,
    ) : SpotifyImportProgress

    data class Error(
        val reason: SpotifyFailure,
    ) : SpotifyImportProgress
}

interface SpotifyLibraryRepository {
    /** The account's playlists, saved tracks first. */
    fun getPlaylists(): Flow<Resource<List<SpotifyPlaylistInfo>>>

    /**
     * Copies [playlist] into a new local playlist named [title]: reads its tracks from Spotify,
     * finds each one on YouTube Music (the only place this app can play from) and keeps the
     * original order. Tracks that cannot be matched with confidence are skipped and reported.
     */
    fun importPlaylist(
        playlist: SpotifyPlaylistInfo,
        title: String,
    ): Flow<SpotifyImportProgress>
}

/** Carries a [SpotifyFailure] through [Resource.Error], whose payload is only a message. */
fun SpotifyFailure.asMessage(): String = name

fun String?.toSpotifyFailure(): SpotifyFailure = SpotifyFailure.entries.firstOrNull { it.name == this } ?: SpotifyFailure.UNKNOWN
