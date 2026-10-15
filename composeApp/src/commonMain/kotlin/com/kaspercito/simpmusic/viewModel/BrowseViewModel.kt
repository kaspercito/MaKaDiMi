package com.kaspercito.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.kaspercito.domain.data.model.home.BrowsePage
import com.kaspercito.domain.repository.HomeRepository
import com.kaspercito.domain.utils.Resource
import com.kaspercito.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.error

class BrowseViewModel(
    private val homeRepository: HomeRepository,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow<BrowseUIState>(BrowseUIState.Loading)
    val uiState: StateFlow<BrowseUIState> get() = _uiState

    fun getBrowsePage(
        browseId: String,
        params: String?,
    ) {
        viewModelScope.launch {
            _uiState.value = BrowseUIState.Loading
            homeRepository.getBrowsePage(browseId, params).collect { resource ->
                val page = resource.data
                _uiState.value =
                    if (resource is Resource.Success && page != null && (page.contents.isNotEmpty() || page.moods.isNotEmpty())) {
                        BrowseUIState.Success(page)
                    } else {
                        BrowseUIState.Error(resource.message ?: getString(Res.string.error))
                    }
            }
        }
    }
}

sealed class BrowseUIState {
    data class Success(
        val page: BrowsePage,
    ) : BrowseUIState()

    data class Error(
        val message: String,
    ) : BrowseUIState()

    object Loading : BrowseUIState()
}
