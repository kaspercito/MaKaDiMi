package com.kaspercito.data.mediaservice

import com.kaspercito.domain.repository.AnalyticsRepository

actual fun createMediaServiceHandler(
    dataStoreManager: com.kaspercito.domain.manager.DataStoreManager,
    songRepository: com.kaspercito.domain.repository.SongRepository,
    streamRepository: com.kaspercito.domain.repository.StreamRepository,
    localPlaylistRepository: com.kaspercito.domain.repository.LocalPlaylistRepository,
    analyticsRepository: AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.kaspercito.domain.mediaservice.handler.MediaPlayerHandler =
    MediaServiceHandlerImpl(
        dataStoreManager,
        songRepository,
        streamRepository,
        localPlaylistRepository,
        analyticsRepository,
        coroutineScope,
    )