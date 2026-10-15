package com.kaspercito.data.mediaservice

import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.mediaservice.handler.MediaPlayerHandler
import com.kaspercito.domain.repository.AnalyticsRepository
import com.kaspercito.domain.repository.LocalPlaylistRepository
import com.kaspercito.domain.repository.SongRepository
import com.kaspercito.domain.repository.StreamRepository
import kotlinx.coroutines.CoroutineScope

expect fun createMediaServiceHandler(
    dataStoreManager: DataStoreManager,
    songRepository: SongRepository,
    streamRepository: StreamRepository,
    localPlaylistRepository: LocalPlaylistRepository,
    analyticsRepository: AnalyticsRepository,
    coroutineScope: CoroutineScope,
): MediaPlayerHandler