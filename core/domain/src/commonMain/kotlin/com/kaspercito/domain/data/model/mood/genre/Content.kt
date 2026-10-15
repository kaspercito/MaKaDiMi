package com.kaspercito.domain.data.model.mood.genre

import com.kaspercito.domain.data.model.searchResult.songs.Thumbnail
import com.kaspercito.domain.data.type.HomeContentType

data class Content(
    val playlistBrowseId: String,
    val thumbnail: List<Thumbnail>?,
    val title: Title,
) : HomeContentType