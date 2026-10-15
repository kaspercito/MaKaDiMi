package com.kaspercito.data.spotify

import com.kaspercito.domain.data.model.searchResult.songs.SongsResult
import com.kaspercito.spotify.model.library.SpotifyTrack
import kotlin.math.abs

/**
 * Decides which YouTube Music result is the Spotify track — or that none is.
 *
 * Picking the first search hit, which is what a naive importer does, puts covers, live takes and
 * karaoke versions into the playlist. Here every candidate is scored on title, artist and length
 * and the best one is taken only if it clears a bar; a track with no confident match is reported
 * as missing rather than replaced by a wrong song.
 */
internal object SpotifyTrackMatcher {
    private const val MIN_SCORE = 0.62
    private const val MIN_TITLE_SIMILARITY = 0.5

    /** Words that make a candidate a different recording unless the Spotify title has them too. */
    private val VARIANT_WORDS =
        setOf("live", "remix", "cover", "karaoke", "instrumental", "acoustic", "sped", "slowed", "reverb", "nightcore", "8d")

    fun best(
        track: SpotifyTrack,
        candidates: List<SongsResult>,
    ): SongsResult? {
        val title = normalizeTitle(track.title)
        if (title.isEmpty()) return null
        val titleTokens = tokens(title)
        val artists = track.artists.map { normalize(it) }.filter { it.isNotEmpty() }
        val spotifySeconds = (track.durationMs / 1000).toInt()
        val wanted = VARIANT_WORDS.filter { it in tokens(normalize(track.title)) }.toSet()

        var best: SongsResult? = null
        var bestScore = 0.0
        candidates.forEach { candidate ->
            val candidateTitle = normalizeTitle(candidate.title.orEmpty())
            val titleSim = similarity(titleTokens, tokens(candidateTitle))
            if (titleSim < MIN_TITLE_SIMILARITY) return@forEach

            val candidateArtists = candidate.artists.orEmpty().map { normalize(it.name) }
            val artistHit =
                artists.any { wantedArtist ->
                    candidateArtists.any { it.contains(wantedArtist) || wantedArtist.contains(it) && it.isNotEmpty() }
                }
            val durationDiff =
                if (spotifySeconds > 0 && (candidate.durationSeconds ?: 0) > 0) {
                    abs(spotifySeconds - (candidate.durationSeconds ?: 0))
                } else {
                    null
                }
            val durationScore =
                when {
                    durationDiff == null -> 0.4
                    durationDiff <= 3 -> 1.0
                    durationDiff <= 8 -> 0.6
                    durationDiff <= 15 -> 0.2
                    else -> -0.4
                }
            val candidateVariants = VARIANT_WORDS.filter { it in tokens(normalize(candidate.title.orEmpty())) }.toSet()
            val variantPenalty = if ((candidateVariants - wanted).isNotEmpty()) 0.3 else 0.0

            // With neither the artist nor the length agreeing, a matching title alone is a different song
            // that happens to share a name.
            if (!artistHit && (durationDiff == null || durationDiff > 3)) return@forEach

            val score = 0.5 * titleSim + 0.3 * (if (artistHit) 1.0 else 0.0) + 0.2 * durationScore - variantPenalty
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best.takeIf { bestScore >= MIN_SCORE }
    }

    /** Title with the decoration streaming services add: "(feat. X)", "- Remastered 2011", "[Official]". */
    fun normalizeTitle(raw: String): String {
        var s = raw
        s = s.replace(Regex("""\((?:feat|ft|with|prod)\.?[^)]*\)""", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("""\[(?:feat|ft|with|prod)\.?[^\]]*\]""", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("""\s[-–—]\s.*(?:remaster|remastered|mono|stereo|version|edit|single|radio)\b.*""", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("""\((?:official|lyrics?|audio|video|hd|hq|remaster(?:ed)?)[^)]*\)""", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("""\s(?:feat|ft)\.?\s.*""", RegexOption.IGNORE_CASE), " ")
        return normalize(s)
    }

    /** Lowercase, accents folded, punctuation gone, single spaces. */
    fun normalize(raw: String): String {
        val folded = StringBuilder(raw.length)
        raw.lowercase().forEach { c -> folded.append(FOLD[c] ?: c) }
        return folded
            .toString()
            .replace(Regex("""[^\p{L}\p{N} ]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun tokens(normalized: String): Set<String> = normalized.split(' ').filter { it.isNotEmpty() }.toSet()

    /** Share of the words the two titles have in common, measured against the shorter one. */
    private fun similarity(
        a: Set<String>,
        b: Set<String>,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.intersect(b).size.toDouble()
        val jaccard = common / a.union(b).size
        val containment = common / minOf(a.size, b.size)
        // Containment alone would let "Love" match "Love Story"; the Jaccard term pulls that back down.
        return 0.6 * jaccard + 0.4 * containment
    }

    private val FOLD: Map<Char, Char> =
        buildMap {
            "àáâãäåāă" .forEach { put(it, 'a') }
            "çćč".forEach { put(it, 'c') }
            "èéêëēėę".forEach { put(it, 'e') }
            "ìíîïī".forEach { put(it, 'i') }
            "ñń".forEach { put(it, 'n') }
            "òóôõöøō".forEach { put(it, 'o') }
            "ùúûüū".forEach { put(it, 'u') }
            "ýÿ".forEach { put(it, 'y') }
            "šś".forEach { put(it, 's') }
            "žźż".forEach { put(it, 'z') }
        }
}
