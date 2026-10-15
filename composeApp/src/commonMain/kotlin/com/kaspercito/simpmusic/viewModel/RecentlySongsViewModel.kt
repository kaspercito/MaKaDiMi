package com.kaspercito.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.kaspercito.domain.repository.SongRepository
import com.kaspercito.simpmusic.pagination.RecentPagingSource
import com.kaspercito.simpmusic.viewModel.base.BaseViewModel

class RecentlySongsViewModel(
    private val songRepository: SongRepository,
) : BaseViewModel() {
    val recentlySongs =
        Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20,
            ),
        ) {
            RecentPagingSource(songRepository)
        }.flow.cachedIn(viewModelScope)
}