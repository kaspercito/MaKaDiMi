package com.kaspercito.spotify

import com.kaspercito.spotify.model.library.SpotifyPlaylistSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * One page of the web player's `libraryV3` answer, read tolerantly.
 *
 * [rawCount] is how many entries the page held (playlists or not), which is what the next offset
 * advances by; [total] is the library size Spotify reports, when it does.
 */
internal data class LibraryV3Page(
    val playlists: List<SpotifyPlaylistSummary>,
    val rawCount: Int,
    val total: Int?,
)

private const val PLAYLIST_URI = "spotify:playlist:"
private const val LIKED_SONGS_URI = "spotify:collection:tracks"

/**
 * The reply is GraphQL JSON whose field names Spotify can reshuffle, so this walks it instead of
 * binding it to a fixed class: a rename costs one detail (an image, an owner name), not the list.
 */
internal fun parseLibraryV3(text: String): LibraryV3Page {
    val root = Json.parseToJsonElement(text)
    val library = root.findObject("libraryV3") ?: return LibraryV3Page(emptyList(), 0, null)
    val items = library["items"] as? JsonArray ?: return LibraryV3Page(emptyList(), 0, null)
    val total = (library["totalCount"] as? JsonPrimitive)?.intOrNull
    val playlists = mutableListOf<SpotifyPlaylistSummary>()
    for (entry in items) {
        val wrapper = entry as? JsonObject ?: continue
        val item = wrapper["item"] as? JsonObject ?: wrapper
        val data = item["data"] as? JsonObject
        val uri =
            item.stringOf("_uri") ?: item.stringOf("uri") ?: data?.stringOf("uri")
                ?: wrapper.stringOf("_uri") ?: wrapper.stringOf("uri") ?: continue
        when {
            uri == LIKED_SONGS_URI ->
                playlists +=
                    SpotifyPlaylistSummary(
                        id = SpotifyPlaylistSummary.LIKED_SONGS_ID,
                        name = "",
                        imageUrl = null,
                        trackCount = 0,
                        ownerName = null,
                    )
            uri.startsWith(PLAYLIST_URI) -> {
                val id = uri.removePrefix(PLAYLIST_URI).takeIf { it.isNotBlank() } ?: continue
                playlists +=
                    SpotifyPlaylistSummary(
                        id = id,
                        name = data?.stringOf("name")?.takeIf { it.isNotBlank() } ?: "Playlist",
                        imageUrl = data?.get("images")?.firstString("url"),
                        trackCount = 0,
                        ownerName = data?.get("ownerV2")?.firstString("name"),
                    )
            }
        }
    }
    return LibraryV3Page(playlists, items.size, total)
}

private fun JsonObject.stringOf(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

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

/**
 * A short description of a libraryV3 reply that yielded no playlists: its keys, how many items it
 * held and the start of the first one. The raw start of the body is mostly filter and sort names,
 * which hid the part that matters.
 */
internal fun describeLibraryV3(text: String): String =
    runCatching {
        val root = Json.parseToJsonElement(text)
        val library = root.findObject("libraryV3")
            ?: return@runCatching "no libraryV3 object; errors=${(root as? JsonObject)?.get("errors")?.toString()?.take(400)}"
        val items = library["items"] as? JsonArray
        "keys=${library.keys}, items=${items?.size}, totalCount=${library["totalCount"]}, " +
            "first=${items?.firstOrNull()?.toString()?.take(500)}"
    }.getOrElse { "unparseable: ${text.take(300)}" }
