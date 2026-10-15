package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.SpotifyFailure
import com.maxrave.domain.repository.SpotifyImportProgress
import com.maxrave.domain.repository.SpotifyLibraryRepository
import com.maxrave.domain.repository.SpotifyPlaylistInfo
import com.maxrave.domain.repository.toSpotifyFailure
import com.maxrave.domain.utils.Resource
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface SpotifyPlaylistsState {
    data object Loading : SpotifyPlaylistsState

    data class Loaded(
        val playlists: List<SpotifyPlaylistInfo>,
    ) : SpotifyPlaylistsState

    data class Failed(
        val reason: SpotifyFailure,
    ) : SpotifyPlaylistsState
}

/** An import in flight or just finished, for the progress dialog. */
data class SpotifyImportUi(
    val playlist: SpotifyPlaylistInfo,
    val title: String,
    val progress: SpotifyImportProgress,
)

class SpotifyLibraryViewModel(
    dataStoreManager: DataStoreManager,
    private val repository: SpotifyLibraryRepository,
) : BaseViewModel() {
    /** A Spotify account is linked (the saved sp_dc cookie is what the login screen stores). */
    val spotifyLoggedIn: kotlinx.coroutines.flow.Flow<Boolean> = dataStoreManager.spdc.map { it.isNotEmpty() }

    private val _state: MutableStateFlow<SpotifyPlaylistsState> = MutableStateFlow(SpotifyPlaylistsState.Loading)
    val state: StateFlow<SpotifyPlaylistsState> = _state.asStateFlow()

    private val _import: MutableStateFlow<SpotifyImportUi?> = MutableStateFlow(null)
    val importState: StateFlow<SpotifyImportUi?> = _import.asStateFlow()

    private var loadJob: Job? = null
    private var importJob: Job? = null

    fun load(force: Boolean = false) {
        if (!force && _state.value is SpotifyPlaylistsState.Loaded) return
        loadJob?.cancel()
        _state.value = SpotifyPlaylistsState.Loading
        loadJob =
            viewModelScope.launch {
                repository.getPlaylists().collect { result ->
                    _state.value =
                        when (result) {
                            is Resource.Success -> SpotifyPlaylistsState.Loaded(result.data ?: emptyList())
                            is Resource.Error -> SpotifyPlaylistsState.Failed(result.message.toSpotifyFailure())
                        }
                }
            }
    }

    fun import(
        playlist: SpotifyPlaylistInfo,
        title: String,
    ) {
        if (importJob?.isActive == true) return
        _import.value = SpotifyImportUi(playlist, title, SpotifyImportProgress.ReadingTracks(0, playlist.trackCount))
        importJob =
            viewModelScope.launch {
                repository.importPlaylist(playlist, title).collect { progress ->
                    _import.value = SpotifyImportUi(playlist, title, progress)
                }
            }
    }

    /** Closes the dialog; while an import is still running this also cancels it. */
    fun dismissImport() {
        importJob?.cancel()
        importJob = null
        _import.value = null
    }
}
