@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kaspercito.spotify.auth

import com.kaspercito.logger.Logger
import com.kaspercito.spotify.SpotifyClient
import com.kaspercito.spotify.model.response.spotify.PersonalTokenResponse
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/**
 * Handles advanced Spotify authentication with TOTP.
 *
 * Spotify rotates the TOTP secret behind open.spotify.com/api/token without notice. A single
 * cached (or hard-coded, long-expired) secret therefore turns into "playlists could not be
 * loaded" the day it rotates. This class keeps the whole list of known secrets (remote, newest
 * first, then the ones bundled with the app) and tries them in order until one is accepted.
 */
class SpotifyAuth(
    private val spotifyClient: SpotifyClient,
) {
    /** The secret that worked last time, tried first. */
    var totpSecret: Pair<Int, List<Int>>? = null

    private var remoteSecrets: List<Pair<Int, List<Int>>> = emptyList()
    private var remoteFetchedAt: Long = 0L

    /** Downloads the current secret list (newest version first). Failing keeps the old list. */
    suspend fun getTotpSecret(): Result<Boolean> =
        runCatching {
            val response = spotifyClient.getSpotifyLastestTotpSecret()
            if (!response.status.isSuccess()) error("TOTP secret list answered ${response.status.value}")
            val dict = response.body<Map<String, List<Int>>>()
            val parsed =
                dict.entries
                    .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }
                    .filter { it.second.isNotEmpty() }
                    .sortedByDescending { it.first }
            if (parsed.isEmpty()) return@runCatching false
            remoteSecrets = parsed
            remoteFetchedAt = Clock.System.now().toEpochMilliseconds()
            true
        }.onFailure { Logger.w("Auth", "Spotify: could not fetch TOTP secrets: ${it.message}") }

    private fun candidateSecrets(): List<Pair<Int, List<Int>>> {
        val all = remoteSecrets + SpotifyTotp.BUNDLED_SECRETS + listOf(SpotifyTotp.TOTP_SECRET_V22)
        val ordered = listOfNotNull(totpSecret) + all
        return ordered.distinctBy { it.first }
    }

    /** One attempt with one secret. Returns the token, or throws with the HTTP status in the message. */
    private suspend fun tryWith(
        spDc: String,
        secret: Pair<Int, List<Int>>,
    ): PersonalTokenResponse {
        val serverTimeResponse = spotifyClient.getSpotifyServerTime(spDc)
        if (!serverTimeResponse.status.isSuccess()) error("server-time answered ${serverTimeResponse.status.value}")
        val serverTimeJson = Json.parseToJsonElement(serverTimeResponse.body<String>()).jsonObject
        val serverTime =
            serverTimeJson["serverTime"]?.jsonPrimitive?.longOrNull
                ?: error("server-time had no serverTime")

        val otpValue = SpotifyTotp.at(serverTime * 1000L, secret)
        var lastProblem = "no response"
        for (reason in listOf("transport", "init")) {
            val response =
                spotifyClient.getSpotifyAccessToken(
                    spdc = spDc,
                    otpValue = otpValue,
                    reason = reason,
                    sTime = "$serverTime",
                    cTime = "$serverTime",
                    totpVersion = secret.first,
                )
            if (!response.status.isSuccess()) {
                lastProblem = "api/token ($reason, totpVer ${secret.first}) answered ${response.status.value}"
                continue
            }
            val data = runCatching { response.body<PersonalTokenResponse>() }.getOrNull()
            if (data != null && data.accessToken.isNotEmpty()) return data
            lastProblem = "api/token ($reason, totpVer ${secret.first}) returned no token"
        }
        error(lastProblem)
    }

    /**
     * Refresh the Spotify web-player token using the TOTP authentication.
     * Tries the known secrets (newest first) until Spotify accepts one.
     */
    suspend fun refreshToken(spDc: String): Result<PersonalTokenResponse> =
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            if (remoteSecrets.isEmpty() || now - remoteFetchedAt > SECRET_TTL_MS) getTotpSecret()

            var lastError: Throwable? = null
            for (secret in candidateSecrets()) {
                val result = runCatching { tryWith(spDc, secret) }
                val token = result.getOrNull()
                if (token != null) {
                    totpSecret = secret
                    if (token.isAnonymous) {
                        // An expired or revoked sp_dc is still answered, with a guest token.
                        Logger.e("Auth", "Spotify: sp_dc is no longer valid (anonymous token) — the user must log in to Spotify again")
                    } else {
                        val minutesLeft = (token.accessTokenExpirationTimestampMs - Clock.System.now().toEpochMilliseconds()) / 60_000
                        Logger.i("Auth", "Spotify: token refreshed with totpVer ${secret.first}, valid for $minutesLeft min")
                    }
                    return@runCatching token
                }
                lastError = result.exceptionOrNull()
                Logger.w("Auth", "Spotify: totpVer ${secret.first} failed: ${lastError?.message}")
            }
            // Every secret failed: re-download the list next time instead of trusting a stale one.
            remoteFetchedAt = 0L
            totpSecret = null
            throw lastError ?: Exception("Unsuccessful token request")
        }.onFailure { Logger.e("Auth", "Spotify: token refresh failed: ${it.message}") }

    private companion object {
        const val SECRET_TTL_MS = 60 * 60 * 1000L
    }
}
