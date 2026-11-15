package com.kaspercito.data.dj

import com.kaspercito.domain.data.model.searchResult.songs.SongsResult
import com.kaspercito.domain.repository.SearchRepository
import com.kaspercito.domain.utils.toTrack
import com.kaspercito.domain.data.model.browse.album.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.makadimi.aiservice.AiClient

/**
 * Arma la cola del DJ a partir de TUS gustos, en vez de usar la "radio relacionada" de YouTube
 * Music (que se va derivando de género con cada canción).
 *
 * La IA propone canciones del mismo estilo que tu música ([AiClient.suggestSongs]) y cada una se
 * resuelve buscándola en YouTube Music. Las pistas llegan una a una por el [Flow], así la sesión
 * puede empezar a sonar mientras la cola se sigue llenando.
 */
class DjQueueBuilder internal constructor(
    private val aiClient: AiClient,
    private val searchRepository: SearchRepository,
) {
    /**
     * @param taste resumen de gustos (artistas y canciones favoritas) para el modelo.
     * @param seed la canción con la que arranca la sesión, como "Artista - Título".
     * @param avoidTitles canciones que no deben repetirse (la inicial y las de la cola propia).
     * @param avoidIds videoIds ya presentes, para no repetir la misma pista.
     */
    fun suggestions(
        taste: String,
        seed: String,
        avoidTitles: List<String>,
        avoidIds: Set<String>,
        count: Int = 24,
    ): Flow<Track> =
        channelFlow {
            val names = aiClient.suggestSongs(taste, seed, avoidTitles, count).getOrNull().orEmpty()
            if (names.isEmpty()) return@channelFlow
            // Pocas búsquedas a la vez: cada una hace varias peticiones y no queremos saturar la red.
            val gate = Semaphore(3)
            val seen = HashSet<String>(avoidIds)
            val lock = Mutex()
            for (name in names) {
                launch {
                    gate.withPermit {
                        val track = resolve(name) ?: return@withPermit
                        val fresh = lock.withLock { seen.add(track.videoId) }
                        if (fresh) send(track)
                    }
                }
            }
        }

    /** Busca "Artista - Título" y devuelve el primer resultado cuyo artista coincide de verdad. */
    private suspend fun resolve(name: String): Track? {
        val artistPart = name.substringBefore(" - ").trim()
        val result =
            runCatching { searchRepository.getSearchDataSong(name).firstOrNull() }.getOrNull()
        val songs: List<SongsResult> = result?.data.orEmpty()
        val wanted = normalize(artistPart)
        val match =
            songs.take(4).firstOrNull { song ->
                song.artists.orEmpty().any { artist ->
                    val got = normalize(artist.name)
                    got.isNotEmpty() && wanted.isNotEmpty() && (wanted.contains(got) || got.contains(wanted))
                } ||
                    // "Artista A, Artista B": basta con que alguno de los nombrados coincida.
                    artistPart.split(',', '&', ';').map { normalize(it) }.any { part ->
                        part.isNotEmpty() &&
                            song.artists.orEmpty().any { normalize(it.name).let { got -> got.contains(part) || part.contains(got) } }
                    }
            }
        return match?.toTrack()
    }

    private fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
}
