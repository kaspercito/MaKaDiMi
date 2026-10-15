package com.kaspercito.data.mediaservice

actual fun createMediaServiceHandler(
    dataStoreManager: com.kaspercito.domain.manager.DataStoreManager,
    songRepository: com.kaspercito.domain.repository.SongRepository,
    streamRepository: com.kaspercito.domain.repository.StreamRepository,
    localPlaylistRepository: com.kaspercito.domain.repository.LocalPlaylistRepository,
    analyticsRepository: com.kaspercito.domain.repository.AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.kaspercito.domain.mediaservice.handler.MediaPlayerHandler {
    TODO("Not yet implemented")
}