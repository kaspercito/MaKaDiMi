package com.kaspercito.makadimi.pagination

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kaspercito.domain.repository.SongRepository
import kotlinx.coroutines.CancellationException

class RecentPagingSource(
    private val songRepository: SongRepository,
    private val downloadedOnly: Boolean,
) : PagingSource<Int, Any>() {
    override fun getRefreshKey(state: PagingState<Int, Any>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Any> {
        val page = params.key ?: 0

        return try {
            val entities = songRepository.getRecentSong(params.loadSize, page * params.loadSize, downloadedOnly)
            LoadResult.Page(
                data = entities,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (entities.size < params.loadSize) null else page + 1,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
