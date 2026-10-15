package com.kaspercito.domain.data.model.browse.artist

import com.kaspercito.domain.data.model.searchResult.songs.Thumbnail

data class ResultRelated(
    val browseId: String,
    val subscribers: String,
    val thumbnails: List<Thumbnail>,
    val title: String,
)