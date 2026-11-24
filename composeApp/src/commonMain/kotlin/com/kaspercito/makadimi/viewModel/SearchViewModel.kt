package com.kaspercito.makadimi.viewModel

import androidx.lifecycle.viewModelScope
import com.kaspercito.domain.data.entities.SearchHistory
import com.kaspercito.domain.data.model.searchResult.albums.AlbumsResult
import com.kaspercito.domain.data.model.searchResult.artists.ArtistsResult
import com.kaspercito.domain.data.model.searchResult.playlists.PlaylistsResult
import com.kaspercito.domain.data.model.searchResult.songs.SongsResult
import com.kaspercito.domain.data.model.mood.Mood
import com.kaspercito.domain.data.model.searchResult.videos.VideosResult
import com.kaspercito.domain.data.type.SearchResultType
import com.kaspercito.domain.repository.HomeRepository
import com.kaspercito.domain.repository.SearchRepository
import com.kaspercito.domain.utils.Resource
import com.kaspercito.domain.utils.toQueryList
import com.kaspercito.logger.LogLevel
import com.kaspercito.logger.Logger
import com.kaspercito.makadimi.viewModel.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.albums
import makadimi.composeapp.generated.resources.all
import makadimi.composeapp.generated.resources.artists
import makadimi.composeapp.generated.resources.featured_playlists
import makadimi.composeapp.generated.resources.playlists
import makadimi.composeapp.generated.resources.podcasts
import makadimi.composeapp.generated.resources.songs
import makadimi.composeapp.generated.resources.videos

// State cho tìm kiếm
data class SearchScreenState(
    val searchType: SearchType = SearchType.ALL,
    val searchAllResult: List<SearchResultType> = emptyList(),
    val searchSongsResult: List<SongsResult> = emptyList(),
    val searchVideosResult: List<VideosResult> = emptyList(),
    val searchAlbumsResult: List<AlbumsResult> = emptyList(),
    val searchArtistsResult: List<ArtistsResult> = emptyList(),
    val searchPlaylistsResult: List<PlaylistsResult> = emptyList(),
    val searchFeaturedPlaylistsResult: List<PlaylistsResult> = emptyList(),
    val searchPodcastsResult: List<PlaylistsResult> = emptyList(),
    val suggestQueries: List<String> = emptyList(),
    val suggestYTItems: List<SearchResultType> = emptyList(),
)

// Loại tìm kiếm
enum class SearchType {
    ALL,
    SONGS,
    VIDEOS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
    FEATURED_PLAYLISTS,
    PODCASTS,
}

fun SearchType.toStringRes(): StringResource =
    when (this) {
        SearchType.ALL -> Res.string.all
        SearchType.SONGS -> Res.string.songs
        SearchType.VIDEOS -> Res.string.videos
        SearchType.ALBUMS -> Res.string.albums
        SearchType.ARTISTS -> Res.string.artists
        SearchType.PLAYLISTS -> Res.string.playlists
        SearchType.FEATURED_PLAYLISTS -> Res.string.featured_playlists
        SearchType.PODCASTS -> Res.string.podcasts
    }

// UI state cho tìm kiếm
sealed class SearchScreenUIState {
    object Empty : SearchScreenUIState()

    object Loading : SearchScreenUIState()

    object Success : SearchScreenUIState()

    object Error : SearchScreenUIState()
}

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val homeRepository: HomeRepository,
) : BaseViewModel() {
    private val _searchScreenUIState = MutableStateFlow<SearchScreenUIState>(SearchScreenUIState.Empty)
    val searchScreenUIState: StateFlow<SearchScreenUIState> get() = _searchScreenUIState.asStateFlow()

    private val _searchScreenState = MutableStateFlow(SearchScreenState())
    val searchScreenState: StateFlow<SearchScreenState> get() = _searchScreenState.asStateFlow()

    private val _searchHistory: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    val searchHistory: StateFlow<List<String>> get() = _searchHistory.asStateFlow()

    /** Browse categories shown on the empty search screen, the way YouTube Music itself does. */
    private val _moodAndGenres: MutableStateFlow<Mood?> = MutableStateFlow(null)
    val moodAndGenres: StateFlow<Mood?> get() = _moodAndGenres.asStateFlow()

    /** Cover art per category params, filled in as tiles scroll into view. */
    private val _moodArtwork: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    val moodArtwork: StateFlow<Map<String, String>> get() = _moodArtwork.asStateFlow()

    private var searchJob: Job? = null

    private val requestedArtwork = mutableSetOf<String>()

    init {
        getSearchHistory()
        getMoodAndGenres()
    }

    /**
     * Fetched once for the app's lifetime — this ViewModel is a Koin `single`, and the category
     * list changes about as often as YouTube ships a new mood, so re-fetching on every visit to
     * the search tab would be pure waste.
     */
    private fun getMoodAndGenres() {
        if (_moodAndGenres.value != null) return
        viewModelScope.launch {
            homeRepository.getMoodAndMomentsData().collect { resource ->
                if (resource is Resource.Success) {
                    _moodAndGenres.value = resource.data
                }
            }
        }
    }

    /**
     * Resolve the cover for one category. Called from the tile itself, so only categories the user
     * actually scrolls to cost a request — [requestedArtwork] keeps a tile scrolling in and out of
     * view from firing it again.
     */
    fun loadMoodArtwork(params: String) {
        if (!requestedArtwork.add(params)) return
        viewModelScope.launch {
            homeRepository.getMoodCategoryArtwork(params).collect { url ->
                if (url != null) {
                    _moodArtwork.update { it + (params to url) }
                }
            }
        }
    }

    private fun getSearchHistory() {
        viewModelScope.launch {
            searchRepository.getSearchHistory().collect { values ->
                if (values.isNotEmpty()) {
                    values.toQueryList().reversed().let { list ->
                        _searchHistory.value = list
                        log("Search history updated: $list")
                    }
                } else {
                    _searchHistory.value = emptyList()
                    log("Search history is empty")
                }
            }
        }
    }

    fun insertSearchHistory(query: String) {
        viewModelScope.launch {
            searchRepository.insertSearchHistory(SearchHistory(query = query)).collectLatest {
                Logger.d(tag, "Inserted search history: $query, $it")
                getSearchHistory()
            }
        }
    }

    fun deleteSearchHistory() {
        viewModelScope.launch {
            searchRepository.deleteSearchHistory()
            delay(1000)
            getSearchHistory()
        }
    }

    fun searchSongs(query: String) {
        launchSearch {
            searchRepository.getSearchDataSong(query).collect { values ->
                when (values) {
                    is Resource.Success -> {
                        values.data?.let { songsList ->
                            _searchScreenState.update { state ->
                                state.copy(searchSongsResult = songsList)
                            }
                        }
                        _searchScreenUIState.value = SearchScreenUIState.Success
                    }

                    is Resource.Error -> {
                        _searchScreenUIState.value = SearchScreenUIState.Error
                    }
                }
            }
        }
    }

    fun searchAll(query: String) = launchSearch {
        val songs = async { searchRepository.getSearchDataSong(query).searchResult() }
        val artists = async { searchRepository.getSearchDataArtist(query).searchResult() }
        val albums = async { searchRepository.getSearchDataAlbum(query).searchResult() }
        val playlists = async { searchRepository.getSearchDataPlaylist(query).searchResult() }
        val videos = async { searchRepository.getSearchDataVideo(query).searchResult() }
        val featured = async { searchRepository.getSearchDataFeaturedPlaylist(query).searchResult() }
        val podcasts = async { searchRepository.getSearchDataPodcast(query).searchResult() }

        val songResults = songs.await()
        val artistResults = artists.await()
        val albumResults = albums.await()
        val playlistResults = playlists.await()
        val videoResults = videos.await()
        val featuredResults = featured.await()
        val podcastResults = podcasts.await()

        if (listOf(songResults, artistResults, albumResults, playlistResults, videoResults, featuredResults, podcastResults).all { it == null }) {
            _searchScreenUIState.value = SearchScreenUIState.Error
            return@launchSearch
        }

        _searchScreenState.update { state ->
            state.copy(
                searchType = SearchType.ALL,
                searchAllResult = buildList {
                    addAll(artistResults.orEmpty().take(3))
                    addAll(songResults.orEmpty())
                    addAll(videoResults.orEmpty())
                    addAll(albumResults.orEmpty())
                    addAll(playlistResults.orEmpty())
                    addAll(featuredResults.orEmpty())
                    addAll(podcastResults.orEmpty())
                },
                searchSongsResult = songResults.orEmpty(),
                searchArtistsResult = artistResults.orEmpty(),
                searchAlbumsResult = albumResults.orEmpty(),
                searchPlaylistsResult = playlistResults.orEmpty(),
                searchVideosResult = videoResults.orEmpty(),
                searchFeaturedPlaylistsResult = featuredResults.orEmpty(),
                searchPodcastsResult = podcastResults.orEmpty(),
            )
        }
        _searchScreenUIState.value = SearchScreenUIState.Success
    }

    suspend fun suggestQuery(query: String) {
        _searchScreenState.update { it.copy(suggestQueries = emptyList(), suggestYTItems = emptyList()) }
        if (query.isBlank()) return
        delay(250)
        val suggestions = searchRepository.getSuggestQuery(query).searchResult() ?: return
        _searchScreenState.update {
            it.copy(suggestQueries = suggestions.queries, suggestYTItems = suggestions.recommendedItems)
        }
    }

    private suspend fun <T> Flow<Resource<T>>.searchResult(): T? =
        try {
            when (val result = firstOrNull()) {
                is Resource.Success -> result.data
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Search failed: ${e.message}", LogLevel.ERROR)
            null
        }

    private fun launchSearch(block: suspend CoroutineScope.() -> Unit) {
        searchJob?.cancel()
        _searchScreenUIState.value = SearchScreenUIState.Loading
        searchJob =
            viewModelScope.launch {
                try {
                    block()
                    if (_searchScreenUIState.value == SearchScreenUIState.Loading) {
                        _searchScreenUIState.value = SearchScreenUIState.Error
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("Search failed: ${e.message}", LogLevel.ERROR)
                    _searchScreenUIState.value = SearchScreenUIState.Error
                }
            }
    }

    fun searchAlbums(query: String) {
        launchSearch {
            searchRepository.getSearchDataAlbum(query).collect { values ->
                when (values) {
                    is Resource.Success -> {
                        values.data?.let { albumsList ->
                            _searchScreenState.update { state ->
                                state.copy(
                                    searchType = SearchType.ALBUMS,
                                    searchAlbumsResult = albumsList,
                                )
                            }
                        }
                        _searchScreenUIState.value = SearchScreenUIState.Success
                    }

                    is Resource.Error -> {
                        _searchScreenUIState.value = SearchScreenUIState.Error
                    }
                }
            }
        }
    }

    fun searchFeaturedPlaylist(query: String) {
        launchSearch {
            searchRepository.getSearchDataFeaturedPlaylist(query).collect { values ->
                when (values) {
                    is Resource.Success -> {
                        values.data?.let { featuredPlaylistList ->
                            _searchScreenState.update { state ->
                                state.copy(
                                    searchType = SearchType.FEATURED_PLAYLISTS,
                                    searchFeaturedPlaylistsResult = featuredPlaylistList,
                                )
                            }
                        }
                        _searchScreenUIState.value = SearchScreenUIState.Success
                    }

                    is Resource.Error -> {
                        _searchScreenUIState.value = SearchScreenUIState.Error
                    }
                }
            }
        }
    }

    fun searchPodcast(query: String) {
        launchSearch {
            searchRepository.getSearchDataPodcast(query).collect { values ->
                when (values) {
                    is Resource.Success -> {
                        values.data?.let { podcastList ->
                            _searchScreenState.update { state ->
                                state.copy(
                                    searchType = SearchType.PODCASTS,
                                    searchPodcastsResult = podcastList,
                                )
                            }
                        }
                        _searchScreenUIState.value = SearchScreenUIState.Success
                    }

                    is Resource.Error -> {
                        _searchScreenUIState.value = SearchScreenUIState.Error
                    }
                }
            }
        }
    }

    fun searchArtists(query: String) {
        launchSearch {
            searchRepository.getSearchDataArtist(query).collect { values ->
                when (values) {
                    is Resource.Success -> {
                        values.data?.let { artistsList ->
                            _searchScreenState.update { state ->
                                state.copy(
                                    searchType = SearchType.ARTISTS,
                                    searchArtistsResult = artistsList,
                                )
                            }
                        }
                        _searchScreenUIState.value = SearchScreenUIState.Success
                    }

                    is Resource.Error -> {
                        _searchScreenUIState.value = SearchScreenUIState.Error
                    }
                }
            }
        }
    }

    fun searchPlaylists(query: String) {
        launchSearch {
            searchRepository.getSearchDataPlaylist(query).collect { values ->
                when (values) {
                    is Resource.Success -> {
                        values.data?.let { playlistsList ->
                            _searchScreenState.update { state ->
                                state.copy(
                                    searchType = SearchType.PLAYLISTS,
                                    searchPlaylistsResult = playlistsList,
                                )
                            }
                        }
                        _searchScreenUIState.value = SearchScreenUIState.Success
                    }

                    is Resource.Error -> {
                        _searchScreenUIState.value = SearchScreenUIState.Error
                    }
                }
            }
        }
    }

    fun searchVideos(query: String) {
        launchSearch {
            searchRepository.getSearchDataVideo(query).collect { values ->
                when (values) {
                    is Resource.Success -> {
                        values.data?.let { videosList ->
                            _searchScreenState.update { state ->
                                state.copy(
                                    searchType = SearchType.VIDEOS,
                                    searchVideosResult = videosList,
                                )
                            }
                        }
                        _searchScreenUIState.value = SearchScreenUIState.Success
                    }

                    is Resource.Error -> {
                        _searchScreenUIState.value = SearchScreenUIState.Error
                    }
                }
            }
        }
    }

    fun setSearchType(searchType: SearchType) {
        _searchScreenState.update { state ->
            state.copy(searchType = searchType)
        }
    }
}