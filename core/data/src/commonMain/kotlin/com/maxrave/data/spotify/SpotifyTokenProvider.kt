@file:OptIn(ExperimentalTime::class)

package com.maxrave.data.spotify

import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.logger.Logger
import com.maxrave.spotify.Spotify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class SpotifyNotLoggedInException : Exception("No Spotify account is linked")

internal class SpotifySessionExpiredException : Exception("The Spotify session (sp_dc) is no longer valid")

/**
 * One place that turns the saved `sp_dc` cookie into Spotify tokens.
 *
 * Lyrics, Canvas and the playlist library each used to carry their own copy of this logic. Besides
 * being triplicated, the copies refreshed without any lock — two simultaneous requests (a lyrics
 * fetch and a canvas fetch start together on every new track) both hit Spotify's token endpoint —
 * and neither noticed that a refresh can succeed and still hand back an anonymous guest token.
 */
internal class SpotifyTokenProvider(
    private val spotify: Spotify,
    private val dataStoreManager: DataStoreManager,
) {
    private val personalLock = Mutex()
    private val clientLock = Mutex()

    /**
     * A signed-in token for api.spotify.com and the web player's endpoints.
     *
     * @param forceRefresh skip the saved token — for the retry after Spotify refused one that still
     * looked valid by its timestamp.
     */
    suspend fun personalToken(forceRefresh: Boolean = false): Result<String> =
        personalLock.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            val saved = dataStoreManager.spotifyPersonalToken.first()
            val expires = dataStoreManager.spotifyPersonalTokenExpires.first()
            if (!forceRefresh && saved.isNotEmpty() && expires - EXPIRY_MARGIN_MS > now) {
                return@withLock Result.success(saved)
            }
            val spdc = dataStoreManager.spdc.first()
            if (spdc.isEmpty()) return@withLock Result.failure(SpotifyNotLoggedInException())
            spotify
                .getPersonalTokenWithTotp(spdc)
                .mapCatching { response ->
                    if (response.isAnonymous || response.accessToken.isEmpty()) {
                        // Spotify answers an expired cookie with a guest token. Keeping it would make
                        // every later call fail with a 401 until it expires.
                        throw SpotifySessionExpiredException()
                    }
                    dataStoreManager.setSpotifyPersonalToken(response.accessToken)
                    dataStoreManager.setSpotifyPersonalTokenExpires(response.accessTokenExpirationTimestampMs)
                    response.accessToken
                }.onFailure { Logger.w(TAG, "personal token refresh failed: ${it.message}") }
        }

    /** The web player's client token, which the lyrics and Canvas endpoints ask for next to the bearer. */
    suspend fun clientToken(): Result<String> =
        clientLock.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            val saved = dataStoreManager.spotifyClientToken.first()
            val expires = dataStoreManager.spotifyClientTokenExpires.first()
            if (saved.isNotEmpty() && expires - EXPIRY_MARGIN_MS > now) {
                return@withLock Result.success(saved)
            }
            spotify
                .getClientToken()
                .mapCatching { response ->
                    dataStoreManager.setSpotifyClientToken(response.grantedToken.token)
                    dataStoreManager.setSpotifyClientTokenExpires(
                        response.grantedToken.expiresAfterSeconds * 1000L + now,
                    )
                    response.grantedToken.token
                }.onFailure { Logger.w(TAG, "client token refresh failed: ${it.message}") }
        }

    /** Both tokens, or the first failure. */
    suspend fun bothTokens(): Result<Pair<String, String>> =
        personalToken().mapCatching { personal -> personal to clientToken().getOrThrow() }

    private companion object {
        const val TAG = "SpotifyToken"

        /** Refresh a minute early so a token never expires between the check and the request. */
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
