package com.kaspercito.makadimi.viewModel

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.kaspercito.domain.repository.SongRepository
import com.kaspercito.makadimi.pagination.RecentPagingSource
import com.kaspercito.makadimi.viewModel.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class RecentlySongsViewModel(
    private val songRepository: SongRepository,
) : BaseViewModel() {
    private val _downloadedOnly = MutableStateFlow(false)
    val downloadedOnly = _downloadedOnly.asStateFlow()

    fun setDownloadedOnly(enabled: Boolean) {
        _downloadedOnly.value = enabled
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentlySongs =
        downloadedOnly.flatMapLatest { filter ->
            Pager(
                PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    initialLoadSize = 20,
                ),
            ) {
                RecentPagingSource(songRepository, filter)
            }.flow
        }.cachedIn(viewModelScope)
}
