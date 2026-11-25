package com.kaspercito.makadimi.ui.navigation.destination.library

import kotlinx.serialization.Serializable

/**
 * @param type Using LibraryDynamicPlaylistType.toStringParams
 */
@Serializable
data class LibraryDynamicPlaylistDestination(
    val type: String,
    val recentDownloads: Boolean = false,
    val favoriteDownloads: Boolean = false,
)