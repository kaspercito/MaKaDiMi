package com.kaspercito.domain.data.model.home.chart

import com.kaspercito.domain.data.model.browse.artist.ResultPlaylist

data class ChartItemPlaylist(
    val title: String,
    val playlists: List<ResultPlaylist>,
)