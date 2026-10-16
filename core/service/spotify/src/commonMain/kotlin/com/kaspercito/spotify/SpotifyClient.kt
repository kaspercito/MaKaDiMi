package com.kaspercito.spotify

import com.kaspercito.ktorext.curl.CurlLogger
import com.kaspercito.ktorext.encoding.brotli
import com.kaspercito.ktorext.getEngine
import com.kaspercito.spotify.model.body.CanvasBody
import com.kaspercito.spotify.model.body.SpotifyClientBody
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.random.Random

class SpotifyClient {
    private var spotifyClient = createSpotifyClient()
    private var jsonClient =
        createSpotifyClient(
            onlyJson = true,
        )

    var proxy: ProxyConfig? = null
        set(value) {
            if (field == value) return
            field = value
            spotifyClient.close()
            spotifyClient = createSpotifyClient()
        }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createSpotifyClient(onlyJson: Boolean = false) =
        HttpClient(getEngine()) {
            followRedirects = true
            expectSuccess = false
            // Every rejected Spotify token lands in the app log in one place. Host and path only: the
            // headers carry the tokens. Ktor's own Logger is imported here, hence the full name.
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                        val url = response.request.url
                        com.kaspercito.logger.Logger.w("Auth", "Spotify: ${url.host}${url.encodedPath} answered ${response.status.value}")
                    }
                }
            }
            install(HttpCache)
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
            install(CurlLogger) {
                logger = { Logger.DEFAULT.log(it) }
            }
            install(HttpSend) {
                maxSendCount = 100
            }
            install(HttpCookies) {
                storage = AcceptAllCookiesStorage()
            }
            install(ContentNegotiation) {
                if (!onlyJson) {
                    register(
                        ContentType.Text.Plain,
                        KotlinxSerializationConverter(
                            Json {
                                prettyPrint = true
                                isLenient = true
                                ignoreUnknownKeys = true
                                explicitNulls = false
                                encodeDefaults = true
                            },
                        ),
                    )
                    protobuf(
                        ProtoBuf {
                            encodeDefaults = true
                        },
                    )
                }
                json(
                    Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    },
                )
            }
            install(ContentEncoding) {
                brotli(1.0F)
                gzip(0.9F)
                deflate(0.8F)
            }
            defaultRequest {
                url("https://api.spotify.com")
            }
            if (proxy != null) {
                engine {
                    proxy = this@SpotifyClient.proxy
                }
            }
        }

    /**
     * Thanks to @Thereallo1026 and @misiektoja for providing the latest TOTP secret
     */
    suspend fun getSpotifyLastestTotpSecret() =
        spotifyClient.get("https://raw.githubusercontent.com/xyloflake/spot-secrets-go/refs/heads/main/secrets/secretDict.json") {
            userAgent(USER_AGENT)
            contentType(ContentType.Application.Json)
        }

    suspend fun getSpotifyServerTime(spdc: String) =
        spotifyClient.get("https://open.spotify.com/api/server-time") {
            userAgent(USER_AGENT)
            header(
                "Cookie",
                "sp_dc=$spdc",
            )
            header("App-platform", "WebPlayer")
            header("Spotify-App-Version", "1.2.61.20.g3b4cd5b2")
            header("Accept", "application/json")
            header("Origin", "https://open.spotify.com")
            header("Referer", "https://open.spotify.com/")
        }

    suspend fun getSpotifyAccessToken(
        spdc: String,
        otpValue: String,
        reason: String = "init",
        sTime: String,
        cTime: String,
        totpVersion: Int,
    ) = spotifyClient.get("https://open.spotify.com/api/token") {
        userAgent(USER_AGENT)
        contentType(ContentType.Application.Json)
        header(
            "Cookie",
            "sp_dc=$spdc",
        )
        parameter("reason", reason)
        parameter("productType", "mobile-web-player")
        parameter("totp", otpValue)
        parameter("totpServer", otpValue)
        parameter("totpVer", totpVersion)
        header("Cookie", "sp_dc=$spdc")
        header("Accept", "application/json")
        header("Origin", "https://open.spotify.com")
        header("Referer", "https://open.spotify.com/")
    }

    suspend fun getSpotifyLyricsToken(spdc: String) =
        spotifyClient.get("https://open.spotify.com/get_access_token?reason=transport&productType=web_player") {
            userAgent(USER_AGENT)
            contentType(ContentType.Application.Json)
            header("Cookie", "sp_dc=$spdc")
        }

    suspend fun getSpotifyLyrics(
        token: String,
        clientToken: String,
        trackId: String,
    ) = spotifyClient.get("https://spclient.wg.spotify.com/color-lyrics/v2/track/$trackId?format=json&vocalRemoval=false&market=from_token") {
        userAgent(USER_AGENT)
        contentType(ContentType.Application.Json)
        header("Authorization", "Bearer $token")
        header("Client-Token", clientToken)
        header("App-platform", "WebPlayer")
    }

    suspend fun searchSpotifyTrack(
        q: String,
        authToken: String,
        clientToken: String,
    ) = spotifyClient.get("https://api-partner.spotify.com/pathfinder/v1/query?operationName=searchTracks") {
        userAgent(USER_AGENT)
        contentType(ContentType.Application.Json)
        header("Authorization", "Bearer $authToken")
        header("Client-Token", clientToken)
        header(
            HttpHeaders
                .AcceptEncoding,
            "gzip, deflate, br",
        )
        val variable =
            "{\"searchTerm\":\"${q}\",\"offset\":0,\"limit\":3,\"numberOfTopResults\":3,\"includeAudiobooks\":true,\"includePreReleases\":false}"
        val sha = "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"
        parameter(
            "variables",
            variable,
        )
        parameter(
            "extensions",
            "{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"${sha}\"}}",
        )
    }
    // {"searchTerm":"trình+hieuthuhai","offset":0,"limit":20,"numberOfTopResults":20,"includeAudiobooks":true,"includePreReleases":false}
    // {"persistedQuery":{"version":1,"sha256Hash":"e4ed1f91a2cc5415befedb85acf8671dc1a4bf3ca1a5b945a6386101a22e28a6"}}

    suspend fun getSpotifyCanvas(
        trackId: String,
        token: String,
        clientToken: String,
    ) = spotifyClient.post("https://spclient.wg.spotify.com/canvaz-cache/v0/canvases") {
        headers {
            append(HttpHeaders.Accept, "application/protobuf")
            append(HttpHeaders.ContentType, "application/protobuf")
            append(
                HttpHeaders
                    .AcceptEncoding,
                "gzip, deflate, br",
            )
            append(HttpHeaders.Authorization, "Bearer $token")
            append("Client-Token", clientToken)
            append(HttpHeaders.UserAgent, "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)")
        }
        setBody(
            CanvasBody(
                tracks =
                    listOf(
                        CanvasBody.Track(
                            track_uri = "spotify:track:$trackId",
                        ),
                    ),
            ),
        )
    }

    /**
     * One page of the user's library, asked the way the web player asks for it (pathfinder
     * `libraryV3`, flattened so playlists inside folders are included).
     *
     * api.spotify.com/v1/me/playlists answers the web player's token with 429, which is why this
     * exists. The hash names the query on Spotify's side and rotates when the web player is rebuilt:
     * if this stops answering, capture a fresh one from open.spotify.com (DevTools > Network).
     */
    suspend fun getLibraryV3(
        token: String,
        clientToken: String?,
        offset: Int,
        limit: Int,
    ): HttpResponse {
        val hash = "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455"
        val variables =
            "{\"order\":null,\"textFilter\":\"\",\"features\":[\"LIKED_SONGS\",\"YOUR_EPISODES_V2\"," +
                "\"PRERELEASES\",\"PRERELEASES_V2\",\"CLIPS\",\"EVENTS\"],\"limit\":$limit,\"offset\":$offset," +
                "\"flatten\":true,\"expandedFolders\":[],\"folderUri\":null,\"includeFoldersWhenFlattening\":true}"
        val extensions = "{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"$hash\"}}"
        val posted =
            spotifyClient.post("https://api-partner.spotify.com/pathfinder/v2/query") {
                userAgent(USER_AGENT)
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                if (clientToken != null) header("Client-Token", clientToken)
                header("App-platform", "WebPlayer")
                header("Accept", "application/json")
                setBody("{\"variables\":$variables,\"operationName\":\"libraryV3\",\"extensions\":$extensions}")
            }
        // 401 (expired token) and 429 (rate limit) mean the endpoint is right; anything else may
        // just be the older GET form of the same query, which the web player also still answers.
        val code = posted.status.value
        if (code in 200..299 || code == 401 || code == 429) return posted
        return spotifyClient.get("https://api-partner.spotify.com/pathfinder/v1/query?operationName=libraryV3") {
            userAgent(USER_AGENT)
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            if (clientToken != null) header("Client-Token", clientToken)
            header("App-platform", "WebPlayer")
            header("Accept", "application/json")
            parameter("variables", variables)
            parameter("extensions", extensions)
        }
    }

    /** One page of the playlists the signed-in user owns or follows. */
    suspend fun getMyPlaylists(
        token: String,
        offset: Int,
        limit: Int,
    ) = spotifyClient.get("/v1/me/playlists") {
        userAgent(USER_AGENT)
        header("Authorization", "Bearer $token")
        header("Accept", "application/json")
        parameter("offset", offset)
        parameter("limit", limit)
    }

    /** One page of the tracks a playlist holds. [path] is `tracks`, or `items` in newer API versions. */
    suspend fun getPlaylistTracks(
        token: String,
        playlistId: String,
        path: String,
        offset: Int,
        limit: Int,
    ) = spotifyClient.get("/v1/playlists/$playlistId/$path") {
        userAgent(USER_AGENT)
        header("Authorization", "Bearer $token")
        header("Accept", "application/json")
        parameter("offset", offset)
        parameter("limit", limit)
        parameter("market", "from_token")
        parameter("additional_types", "track")
    }

    /** One page of the user's saved ("liked") tracks. */
    suspend fun getSavedTracks(
        token: String,
        offset: Int,
        limit: Int,
    ) = spotifyClient.get("/v1/me/tracks") {
        userAgent(USER_AGENT)
        header("Authorization", "Bearer $token")
        header("Accept", "application/json")
        parameter("offset", offset)
        parameter("limit", limit)
        parameter("market", "from_token")
    }

    suspend fun getSpotifyClientToken() =
        jsonClient.post("https://clienttoken.spotify.com/v1/clienttoken") {
            headers {
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.AcceptEncoding, "gzip, deflate, br")
                append(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0",
                )
            }
            setBody(
                SpotifyClientBody(
                    client_data = SpotifyClientBody.ClientData(),
                ),
            )
        }

    fun getRandomUserAgent(): String {
        val macOSVersion = "${Random.nextInt(11, 15)}_${Random.nextInt(4, 9)}"
        val webKitVersion = "${Random.nextInt(530, 537)}.${Random.nextInt(30, 37)}"
        val chromeVersion = "${Random.nextInt(80, 105)}.0.${Random.nextInt(3000, 4500)}.${Random.nextInt(60, 125)}"
        val safariVersion = "${Random.nextInt(530, 537)}.${Random.nextInt(30, 36)}"

        return "Mozilla/5.0 (Macintosh; Intel Mac OS X $macOSVersion) AppleWebKit/$webKitVersion (KHTML, like Gecko) Chrome/$chromeVersion Safari/$safariVersion"
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
    }
}