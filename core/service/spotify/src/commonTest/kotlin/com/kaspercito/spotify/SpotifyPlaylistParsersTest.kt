package com.kaspercito.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotifyPlaylistParsersTest {
    private fun item(
        n: Int,
        name: String,
        artists: List<String>,
        ms: Int,
    ): String {
        val artistItems = artists.joinToString(",") { """{"uri":"spotify:artist:A$n","profile":{"name":"$it"}}""" }
        return """
            {"uid":"u$n","itemV2":{"__typename":"TrackResponseWrapper","data":{
              "__typename":"Track","uri":"spotify:track:ID$n","name":"$name",
              "artists":{"items":[$artistItems]},
              "albumOfTrack":{"uri":"spotify:album:AL","name":"Alb $name",
                "coverArt":{"sources":[{"url":"https://i.scdn.co/image/x$n","width":300}]}},
              "trackDuration":{"totalMilliseconds":$ms}}}}
            """.trimIndent()
    }

    @Test
    fun playlistPageKeepsTracksAndSkipsLocalFilesAndEpisodes() {
        val json =
            """
            {"data":{"playlistV2":{"__typename":"Playlist","uri":"spotify:playlist:P","name":"Momentos",
              "content":{"totalCount":4,"pagingInfo":{"offset":0,"limit":100},"items":[
                ${item(1, "nada mal", listOf("mafalda cardenal"), 212000)},
                ${item(2, "Lovesong", listOf("The Cure", "Otro"), 209000)},
                {"uid":"loc","itemV2":{"__typename":"LocalTrackResponseWrapper","data":{"uri":"spotify:local:::x:1"}}},
                {"uid":"ep","itemV2":{"__typename":"EpisodeResponseWrapper","data":{"uri":"spotify:episode:E1","name":"pod"}}}
              ]}}}}
            """.trimIndent()
        val page = parsePathfinderTracks(json)
        assertEquals(listOf("nada mal", "Lovesong"), page.tracks.map { it.title })
        assertEquals(listOf("The Cure", "Otro"), page.tracks[1].artists)
        assertEquals(212000L, page.tracks[0].durationMs)
        assertEquals("Alb nada mal", page.tracks[0].album)
        assertEquals("ID1", page.tracks[0].id)
        assertEquals(4, page.rawCount)
        assertEquals(4, page.total)
    }

    @Test
    fun savedTracksShapeWithUnderscoreUriIsRead() {
        val json =
            """
            {"data":{"me":{"library":{"tracks":{"totalCount":250,"items":[
              {"track":{"_uri":"spotify:track:L1","data":{"__typename":"Track","name":"Fav",
                "artists":{"items":[{"profile":{"name":"Me"}}]},
                "albumOfTrack":{"name":"A","coverArt":{"sources":[{"url":"u"}]}},
                "duration":{"totalMilliseconds":1000}}}}]}}}}}
            """.trimIndent()
        val page = parsePathfinderTracks(json)
        assertEquals("Fav", page.tracks.single().title)
        assertEquals("L1", page.tracks.single().id)
        assertEquals(250, page.total)
    }

    @Test
    fun emptyPlaylistReportsTotalZero() {
        val page = parsePathfinderTracks("""{"data":{"playlistV2":{"content":{"totalCount":0,"items":[]}}}}""")
        assertTrue(page.tracks.isEmpty())
        assertEquals(0, page.total)
    }

    @Test
    fun graphqlErrorWithoutDataIsAFailure() {
        assertFailsWith<IllegalStateException> {
            parsePathfinderTracks("""{"errors":[{"message":"PersistedQueryNotFound"}]}""")
        }
    }

    @Test
    fun embedPageIsReadAndEpisodesAreSkipped() {
        val html =
            """
            <html><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{"trackList":[
              {"uri":"spotify:track:E1","title":"Tu Fotografia","subtitle":"Artista A,${'\u00a0'}Artista B","duration":180000},
              {"uri":"spotify:episode:X","title":"no","subtitle":"x","duration":1}]}}}}}}
            </script></html>
            """.trimIndent()
        val tracks = parseEmbedTracks(html)
        assertEquals(1, tracks.size)
        assertEquals("Tu Fotografia", tracks[0].title)
        assertEquals(listOf("Artista A", "Artista B"), tracks[0].artists)
        assertEquals(180000L, tracks[0].durationMs)
    }

    @Test
    fun persistedQueryHashIsReadFromTheBundle() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        val bundle = """x=new u.Z("fetchPlaylist","query","$a",null),y=new u.Z("fetchPlaylistContents","query","$b",null)"""
        assertEquals(a, extractPersistedQueryHash(bundle, "fetchPlaylist"))
        assertEquals(b, extractPersistedQueryHash(bundle, "fetchPlaylistContents"))
        assertNull(extractPersistedQueryHash(bundle, "fetchLibraryTracks"))
    }

    @Test
    fun bundleUrlsPutTheVendorChunkLast() {
        val home =
            """<script src="https://open.spotifycdn.com/cdn/build/web-player/vendor~web-player.3f2a.js"></script>""" +
                """<script src="https://open.spotifycdn.com/cdn/build/web-player/web-player.9bc1d2.js"></script>"""
        val urls = extractScriptUrls(home)
        assertEquals(2, urls.size)
        assertTrue(urls.first().endsWith("web-player.9bc1d2.js"))
    }
}
