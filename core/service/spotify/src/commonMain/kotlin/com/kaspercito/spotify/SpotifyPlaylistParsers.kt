package com.kaspercito.spotify

import com.kaspercito.logger.Logger
import com.kaspercito.spotify.model.library.SpotifyTrack
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val TAG = "SpotifyLibrary"
private const val TRACK_URI = "spotify:track:"

/**
 * One page of tracks read from the web player's own GraphQL answer.
 *
 * [rawCount] is how many entries the page held (playable or not), which is what the next offset
 * advances by; [total] is the playlist size Spotify reports, when it does.
 */
internal data class PathfinderTracksPage(
    val tracks: List<SpotifyTrack>,
    val rawCount: Int,
    val total: Int?,
)

/**
 * Reads a pathfinder answer (`fetchPlaylistContents`, `fetchPlaylist`, `fetchLibraryTracks`).
 *
 * The reply is GraphQL JSON whose wrappers Spotify renames from one web-player build to the next
 * (`itemV2.data`, `track.data`, `item`…), so this walks the tree looking for track objects instead
 * of binding it to a fixed class: a rename costs a detail (an image), not the whole playlist.
 *
 * @throws IllegalStateException when Spotify answered with GraphQL errors and no data (for example
 * a persisted-query hash it no longer knows).
 */
internal fun parsePathfinderTracks(text: String): PathfinderTracksPage {
    val root = Json.parseToJsonElement(text)
    if (root is JsonObject) {
        val errors = root["errors"] as? JsonArray
        val data = root["data"]
        if (errors != null && errors.isNotEmpty() && (data == null || data is kotlinx.serialization.json.JsonNull)) {
            val message = ((errors.firstOrNull() as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
            throw IllegalStateException("web player answered with an error: ${message ?: "unknown"}")
        }
    }
    val tracks = mutableListOf<SpotifyTrack>()
    collectTracks(root, tracks)

    val itemsHolder = root.findObject("content") ?: root.findObject("tracks")
    val rawCount = (itemsHolder?.get("items") as? JsonArray)?.size ?: tracks.size
    val total =
        itemsHolder?.numberOf("totalCount")
            ?: root.findObject("content")?.numberOf("totalCount")
            ?: root.firstNumber("totalCount")
    return PathfinderTracksPage(tracks, rawCount, total?.toInt())
}

private fun collectTracks(
    node: JsonElement,
    out: MutableList<SpotifyTrack>,
) {
    if (node is JsonObject) {
        val uri = node.stringOf("uri") ?: node.stringOf("_uri")
        if (uri != null && uri.startsWith(TRACK_URI)) {
            node.toSpotifyTrack(uri)?.let { out += it }
            // A track carries its album and artists, each with a uri of its own: do not descend.
            return
        }
        for (child in node.values) collectTracks(child, out)
    } else if (node is JsonArray) {
        for (child in node) collectTracks(child, out)
    }
}

private fun JsonObject.toSpotifyTrack(uri: String): SpotifyTrack? {
    val data = (this["data"] as? JsonObject) ?: this
    val title = (data.stringOf("name") ?: stringOf("name"))?.takeIf { it.isNotBlank() } ?: return null

    val artists = mutableListOf<String>()
    for (key in listOf("artists", "firstArtist", "otherArtists")) {
        when (val holder = data[key]) {
            is JsonObject -> (holder["items"] as? JsonArray)?.forEach { item -> item.artistName()?.let { name -> artists.add(name) } }
            is JsonArray -> holder.forEach { item -> item.artistName()?.let { name -> artists.add(name) } }
            else -> Unit
        }
    }

    val album = (data["albumOfTrack"] as? JsonObject) ?: (data["album"] as? JsonObject)
    val durationMs = data.firstNumber("totalMilliseconds") ?: data.numberOf("duration_ms") ?: 0L
    return SpotifyTrack(
        id = uri.removePrefix(TRACK_URI).takeIf { it.isNotBlank() },
        title = title,
        artists = artists.distinct(),
        album = album?.stringOf("name"),
        durationMs = durationMs,
        imageUrl = (album?.get("coverArt") ?: album?.get("images"))?.firstString("url"),
    )
}

private fun JsonElement.artistName(): String? {
    val obj = this as? JsonObject ?: return null
    val profile = obj["profile"] as? JsonObject
    return (profile?.stringOf("name") ?: obj.stringOf("name"))?.takeIf { it.isNotBlank() }
}

/**
 * Reads the public embed page of a playlist (`open.spotify.com/embed/playlist/<id>`). It needs no
 * token at all and so cannot be rate-limited with the account's, but it only lists the first 100
 * tracks, which is why it is the last resort.
 */
internal fun parseEmbedTracks(html: String): List<SpotifyTrack> {
    val match =
        Regex("<script[^>]*id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html) ?: return emptyList()
    val root = Json.parseToJsonElement(match.groupValues[1])
    val list = root.findArray("trackList") ?: return emptyList()
    val tracks = mutableListOf<SpotifyTrack>()
    for (entry in list) {
        val item = entry as? JsonObject ?: continue
        val uri = item.stringOf("uri") ?: continue
        if (!uri.startsWith(TRACK_URI)) continue
        val title = item.stringOf("title")?.takeIf { it.isNotBlank() } ?: continue
        val artists =
            (item.stringOf("subtitle") ?: "")
                .replace('\u00a0', ' ')
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        tracks +=
            SpotifyTrack(
                id = uri.removePrefix(TRACK_URI).takeIf { it.isNotBlank() },
                title = title,
                artists = artists,
                album = null,
                durationMs = item.numberOf("duration") ?: 0L,
                imageUrl = null,
            )
    }
    return tracks
}

/** The script URLs Spotify's CDN serves with the web player. The main bundle holds the query hashes. */
internal fun extractScriptUrls(html: String): List<String> =
    Regex("https://[A-Za-z0-9./_\\-]*spotifycdn\\.com/[A-Za-z0-9./_~%\\-]*\\.js")
        .findAll(html)
        .map { it.value }
        .distinct()
        // Vendor chunks hold libraries, not queries: look in the player's own bundle first.
        .sortedBy { if (it.contains("vendor")) 1 else 0 }
        .toList()

/**
 * The persisted-query hash the web player sends for [operation], read from its bundle. The player
 * declares each query as `("<operation>","query","<sha256>",…)`.
 */
internal fun extractPersistedQueryHash(
    script: String,
    operation: String,
): String? =
    Regex("\"" + Regex.escape(operation) + "\"\\s*,\\s*\"(?:query|mutation)\"\\s*,\\s*\"([0-9a-f]{64})\"")
        .find(script)
        ?.groupValues
        ?.get(1)

/**
 * Finds out, from Spotify's own web player, which hash currently names each GraphQL query.
 *
 * Those hashes change every time the web player is rebuilt, so a hash written into the app works
 * for a few weeks and then silently stops. Reading them from the live bundle means the playlist
 * import keeps working across Spotify's releases without a new version of this app.
 */
internal class WebPlayerHashes(
    private val client: SpotifyClient,
) {
    private val lock = Mutex()
    private val known = HashMap<String, String>()
    private var failedAt: TimeMark? = null

    /** The current hash for [operation], or null when it cannot be learned right now. */
    suspend fun hashFor(operation: String): String? =
        lock.withLock {
            known[operation]?.let { return@withLock it }
            // The bundle is several megabytes: after a miss, do not download it again for a while.
            val failed = failedAt
            if (failed != null && failed.elapsedNow() < RETRY_AFTER) return@withLock null
            try {
                discover(operation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "could not read the web player's query hashes: ${e.message}")
            }
            val found = known[operation]
            failedAt = if (found == null) TimeSource.Monotonic.markNow() else null
            found
        }

    private suspend fun discover(wanted: String) {
        val home = client.getWebText("https://open.spotify.com/")
        if (!home.status.isSuccess()) return
        for (url in extractScriptUrls(home.bodyAsText()).take(MAX_SCRIPTS)) {
            val response = client.getWebText(url)
            if (!response.status.isSuccess()) continue
            val script = response.bodyAsText()
            for (operation in OPERATIONS) {
                if (operation !in known) extractPersistedQueryHash(script, operation)?.let { known[operation] = it }
            }
            if (wanted in known) break
        }
        Logger.i(TAG, "web player query hashes found for: ${known.keys.joinToString()}")
    }

    private companion object {
        val OPERATIONS = listOf("fetchPlaylistContents", "fetchPlaylist", "fetchLibraryTracks")
        const val MAX_SCRIPTS = 6
        val RETRY_AFTER = 30.minutes
    }
}

// ---------------------------------------------------------------- tiny JSON helpers

private fun JsonObject.stringOf(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.numberOf(key: String): Long? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

/** The first object stored under [key] anywhere in this tree. */
private fun JsonElement.findObject(key: String): JsonObject? {
    val node: JsonElement = this
    if (node is JsonObject) {
        (node[key] as? JsonObject)?.let { return it }
        for (child in node.values) child.findObject(key)?.let { return it }
    } else if (node is JsonArray) {
        for (child in node) child.findObject(key)?.let { return it }
    }
    return null
}

/** The first array stored under [key] anywhere in this tree. */
private fun JsonElement.findArray(key: String): JsonArray? {
    val node: JsonElement = this
    if (node is JsonObject) {
        (node[key] as? JsonArray)?.let { return it }
        for (child in node.values) child.findArray(key)?.let { return it }
    } else if (node is JsonArray) {
        for (child in node) child.findArray(key)?.let { return it }
    }
    return null
}

/** The first number stored under [key] anywhere in this tree. */
private fun JsonElement.firstNumber(key: String): Long? {
    val node: JsonElement = this
    if (node is JsonObject) {
        (node[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.let { return it.toLong() }
        for (child in node.values) child.firstNumber(key)?.let { return it }
    } else if (node is JsonArray) {
        for (child in node) child.firstNumber(key)?.let { return it }
    }
    return null
}

/** The first string stored under [key] anywhere in this tree. */
private fun JsonElement.firstString(key: String): String? {
    val node: JsonElement = this
    if (node is JsonObject) {
        (node[key] as? JsonPrimitive)?.contentOrNull?.let { return it }
        for (child in node.values) child.firstString(key)?.let { return it }
    } else if (node is JsonArray) {
        for (child in node) child.firstString(key)?.let { return it }
    }
    return null
}
