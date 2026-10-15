package com.kaspercito.kotlinytmusicscraper.pages

import com.kaspercito.kotlinytmusicscraper.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)