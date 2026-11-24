package com.kaspercito.media3.di

import android.app.Activity
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.kaspercito.common.Config.CANVAS_CACHE
import com.kaspercito.common.Config.DOWNLOAD_CACHE
import com.kaspercito.common.Config.MAIN_PLAYER
import com.kaspercito.common.Config.PLAYER_CACHE
import com.kaspercito.common.Config.SERVICE_SCOPE
import com.kaspercito.common.MERGING_DATA_TYPE
import com.kaspercito.common.OfflineMode
import com.kaspercito.domain.data.player.LiveStreamRegistry
import com.kaspercito.domain.extension.now
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.mediaservice.handler.DownloadHandler
import com.kaspercito.domain.mediaservice.handler.MediaPlayerHandler
import com.kaspercito.domain.mediaservice.player.MediaPlayerInterface
import com.kaspercito.domain.repository.CacheRepository
import com.kaspercito.domain.repository.HomeRepository
import com.kaspercito.domain.repository.LocalPlaylistRepository
import com.kaspercito.domain.repository.PlaylistRepository
import com.kaspercito.domain.repository.SearchRepository
import com.kaspercito.domain.repository.SongRepository
import com.kaspercito.domain.repository.StreamRepository
import com.kaspercito.logger.Logger
import com.kaspercito.media3.service.OfflineBlockInterceptor
import com.kaspercito.media3.service.PlaybackSource
import com.kaspercito.media3.service.playbackSource
import com.kaspercito.media3.cast.CastHandoffManager
import com.kaspercito.media3.cast.CastStreamResolver
import com.kaspercito.media3.exoplayer.CrossfadeExoPlayerAdapter
import com.kaspercito.media3.extension.isFullyCached
import com.kaspercito.media3.repository.CacheRepositoryImpl
import com.kaspercito.media3.service.SimpleMediaService
import com.kaspercito.media3.service.callback.SimpleMediaSessionCallback
import com.kaspercito.media3.service.download.DownloadUtils
import com.kaspercito.media3.service.mediasourcefactory.LiveStreamAwareLoadErrorHandlingPolicy
import com.kaspercito.media3.service.mediasourcefactory.LiveStreamDetectedException
import com.kaspercito.media3.service.mediasourcefactory.MergingMediaSourceFactory
import com.kaspercito.media3.service.mediasourcefactory.liveStreamDataSourceFactory
import com.kaspercito.media3.utils.CoilBitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.makadimi.cast.initCast
import org.makadimi.cast.wrapWithCastPlayer
import java.io.IOException
import java.net.Proxy
import kotlin.time.Duration.Companion.seconds

/**
 * Required repository first initialization
 */
@UnstableApi
private val mediaServiceModule =
    module {
        // Service
        // CoroutineScope for service
        single<CoroutineScope>(
            createdAtStart = true,
            qualifier = named(SERVICE_SCOPE),
        ) {
            CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
        // Cache
        single<DatabaseProvider>(
            createdAtStart = true,
        ) {
            provideDatabaseProvider(androidContext())
        }
        // Player Cache
        single<SimpleCache>(qualifier = named(PLAYER_CACHE), createdAtStart = true) {
            provideSimpleCache(
                context = androidContext(),
                cacheName = "exoplayer",
                cacheSize = runBlocking { get<DataStoreManager>().maxSongCacheSize.first() },
                databaseProvider = get<DatabaseProvider>(),
            )
        }
        // Download Cache
        single<SimpleCache>(qualifier = named(DOWNLOAD_CACHE), createdAtStart = true) {
            provideSimpleCache(
                context = androidContext(),
                cacheName = "download",
                cacheSize = -1,
                databaseProvider = get<DatabaseProvider>(),
            )
        }
        // Spotify Canvas Cache
        single<SimpleCache>(qualifier = named(CANVAS_CACHE), createdAtStart = true) {
            provideSimpleCache(
                context = androidContext(),
                cacheName = "spotifyCanvas",
                cacheSize = -1,
                databaseProvider = get<DatabaseProvider>(),
            )
        }
        // DownloadUtils
        single<DownloadHandler>(createdAtStart = true) {
            DownloadUtils(
                context = androidContext(),
                playerCache = get(named(PLAYER_CACHE)),
                downloadCache = get(named(DOWNLOAD_CACHE)),
                dataStoreManager = get(),
                databaseProvider = get(),
                streamRepository = get(),
                songRepository = get(),
            )
        }

        // AudioAttributes
        single<AudioAttributes>(createdAtStart = true) {
            provideAudioAttributes()
        }

        single<MergingMediaSourceFactory>(createdAtStart = true) {
            provideMergingMediaSource(
                androidContext(),
                get(named(DOWNLOAD_CACHE)),
                get(named(PLAYER_CACHE)),
                get(),
                get(named(SERVICE_SCOPE)),
                get(),
            )
        }

        single<DefaultRenderersFactory>(createdAtStart = true) {
            provideRendererFactory(androidContext())
        }

        // Player exposed for MediaSession + UI (video rendering via PlayerView/PlayerSurface).
        // The adapter's ForwardingPlayer (delegating to the active ExoPlayer) is wrapped with
        // Cast support in the full build; org.makadimi.cast no-ops back to the same instance
        // in the FOSS build, so this stays the stable session-level player either way.
        single<Player>(qualifier = named(MAIN_PLAYER)) {
            val adapter = get<MediaPlayerInterface>() as CrossfadeExoPlayerAdapter
            initCast(androidContext())
            wrapWithCastPlayer(androidContext(), adapter.forwardingPlayer)
        }

        // CoilBitmapLoader
        single<CoilBitmapLoader>(createdAtStart = true) {
            provideCoilBitmapLoader(androidContext(), get(named(SERVICE_SCOPE)))
        }

        single<MediaPlayerInterface>(createdAtStart = true) {
            CrossfadeExoPlayerAdapter(
                context = androidContext(),
                coroutineScope = get(named(SERVICE_SCOPE)),
                dataStoreManager = get(),
                mediaSourceFactory = get(),
                audioAttributes = get(),
                streamRepository = get(),
            )
        }

        // Local ↔ Cast receiver handoff. No-op when wrapWithCastPlayer returned the plain
        // ForwardingPlayer (FOSS build or no GMS on the device).
        single<CastHandoffManager>(createdAtStart = true) {
            CastHandoffManager(
                adapter = get<MediaPlayerInterface>() as CrossfadeExoPlayerAdapter,
                sessionPlayer = get(qualifier = named(MAIN_PLAYER)),
                resolver = CastStreamResolver(get(), get()),
                coroutineScope = get(qualifier = named(SERVICE_SCOPE)),
            ).also { it.start() }
        }

        // MediaSession Callback for main player
        single<MediaLibrarySession.Callback>(createdAtStart = true) {
            SimpleMediaSessionCallback(
                androidApplication(),
                get<CoroutineScope>(named(SERVICE_SCOPE)),
                get<MediaPlayerHandler>(),
                get<SearchRepository>(),
                get<SongRepository>(),
                get<LocalPlaylistRepository>(),
                get<PlaylistRepository>(),
                get<HomeRepository>(),
                get<StreamRepository>(),
            )
        }

        single<CacheRepository>(createdAtStart = true) {
            CacheRepositoryImpl(
                playerCache = get(named(PLAYER_CACHE)),
                downloadCache = get(named(DOWNLOAD_CACHE)),
                canvasCache = get(named(CANVAS_CACHE)),
            )
        }
    }

@UnstableApi
private fun provideResolvingDataSourceFactory(
    cacheDataSourceFactory: CacheDataSource.Factory,
    downloadCache: SimpleCache,
    playerCache: SimpleCache,
    dataStoreManager: DataStoreManager,
    streamRepository: StreamRepository,
    coroutineScope: CoroutineScope,
): DataSource.Factory {
    return ResolvingDataSource.Factory(cacheDataSourceFactory) { dataSpec ->
        val mediaId = dataSpec.key ?: throw IOException("Missing playback cache key")
        val videoId = mediaId.removePrefix(MERGING_DATA_TYPE.VIDEO)
        // A live broadcast reached a progressive source that was built before it was known to be
        // live; failing it is what gets the track rebuilt as HLS — see LiveStreamDetectedException.
        val isCached = downloadCache.isFullyCached(mediaId, 0L) || playerCache.isFullyCached(mediaId, 0L)
        if (playbackSource(isCached, OfflineMode.enabled) == PlaybackSource.CACHE) {
            if (dataSpec.position == 0L && !OfflineMode.enabled) {
                coroutineScope.launch(Dispatchers.IO) {
                    if (dataStoreManager.sendBackToGoogle.first() == DataStoreManager.TRUE && !OfflineMode.enabled) {
                        streamRepository.updateFormat(videoId)
                    }
                }
            }
            return@Factory dataSpec
        }
        if (LiveStreamRegistry.isLive(videoId)) throw LiveStreamDetectedException(videoId)
        var dataSpecReturn = dataSpec
        var resolved = false
        runBlocking(Dispatchers.IO) {
            if (mediaId.startsWith(MERGING_DATA_TYPE.VIDEO)) {
                streamRepository.getNewFormat(videoId).lastOrNull()?.let {
                    val videoUrl = it.videoUrl
                    if (videoUrl != null && it.expiredTime > now()) {
                        Logger.d("Stream", "Video from format")
                        val is403Url = streamRepository.is403Url(videoUrl).firstOrNull() != false
                        Logger.d("Stream", "is 403 $is403Url")
                        if (!is403Url) {
                            dataSpecReturn = dataSpec.withUri(videoUrl.toUri())
                            resolved = true
                            return@runBlocking
                        }
                    }
                }
                streamRepository
                    .getStream(
                        dataStoreManager,
                        videoId,
                        isDownloading = false,
                        isVideo = true,
                    ).lastOrNull()
                    ?.let {
                        Logger.d("Stream", "Video")
                        dataSpecReturn = dataSpec.withUri(it.toUri())
                        resolved = true
                    }
            } else {
                streamRepository.getNewFormat(mediaId).lastOrNull()?.let {
                    val audioUrl = it.audioUrl
                    if (audioUrl != null && it.expiredTime > now()) {
                        Logger.d("Stream", "Audio from format")
                        val is403Url = streamRepository.is403Url(audioUrl).firstOrNull() != false
                        Logger.d("Stream", "is 403 $is403Url")
                        if (!is403Url) {
                            dataSpecReturn = dataSpec.withUri(audioUrl.toUri())
                            resolved = true
                            return@runBlocking
                        }
                    }
                }
                streamRepository
                    .getStream(
                        dataStoreManager,
                        mediaId,
                        isDownloading = false,
                        isVideo = false,
                    ).lastOrNull()
                    ?.let {
                        Logger.d("Stream", "Audio")
                        dataSpecReturn = dataSpec.withUri(it.toUri())
                        resolved = true
                    }
            }
        }
        // getStream above is where a live broadcast is first recognised. Its HLS playlist URL must
        // not be read as a file, so this source fails and the track is rebuilt as HLS.
        if (LiveStreamRegistry.isLive(videoId)) throw LiveStreamDetectedException(videoId)
        if (!resolved) {
            Logger.e("Stream", "Failed to resolve stream URL for $mediaId")
            throw IOException("Failed to resolve stream URL for $mediaId")
        }
        return@Factory dataSpecReturn
    }
}

@UnstableApi
private fun provideExtractorFactory(): ExtractorsFactory =
    ExtractorsFactory {
        arrayOf(
            FlacExtractor(
                FlacExtractor.FLAG_DISABLE_ID3_METADATA,
            ),
            MatroskaExtractor(
                DefaultSubtitleParserFactory(),
            ),
            FragmentedMp4Extractor(
                DefaultSubtitleParserFactory(),
            ),
            Mp4Extractor(
                DefaultSubtitleParserFactory(),
            ),
        )
    }

@UnstableApi
private fun provideMediaSourceFactory(
    context: Context,
    downloadCache: SimpleCache,
    playerCache: SimpleCache,
    streamRepository: StreamRepository,
    dataStoreManager: DataStoreManager,
    coroutineScope: CoroutineScope,
): DefaultMediaSourceFactory =
    DefaultMediaSourceFactory(
        provideResolvingDataSourceFactory(
            provideCacheDataSource(
                downloadCache,
                playerCache,
                context,
                provideProxy(dataStoreManager),
            ),
            downloadCache,
            playerCache,
            dataStoreManager,
            streamRepository,
            coroutineScope,
        ),
        provideExtractorFactory(),
    ).setLoadErrorHandlingPolicy(LiveStreamAwareLoadErrorHandlingPolicy())

/** The proxy the user configured, for every HTTP source the player opens. */
private fun provideProxy(dataStoreManager: DataStoreManager): Proxy? =
    dataStoreManager.getJVMProxy()?.let {
        Proxy(
            when (it.type) {
                DataStoreManager.ProxyType.PROXY_TYPE_HTTP -> Proxy.Type.HTTP
                DataStoreManager.ProxyType.PROXY_TYPE_SOCKS -> Proxy.Type.SOCKS
            },
            java.net.InetSocketAddress(it.host, it.port),
        )
    }

/**
 * HLS for live broadcasts. The playlist placeholder is resolved through the same getStream every
 * other track uses, which answers with the broadcast's HLS playlist once it knows the video is live.
 */
@UnstableApi
private fun provideLiveStreamMediaSourceFactory(
    context: Context,
    streamRepository: StreamRepository,
    dataStoreManager: DataStoreManager,
): HlsMediaSource.Factory =
    HlsMediaSource.Factory(
        liveStreamDataSourceFactory(provideHttpDataSourceFactory(context, provideProxy(dataStoreManager))) { videoId ->
            runBlocking(Dispatchers.IO) {
                streamRepository
                    .getStream(
                        dataStoreManager,
                        videoId,
                        isDownloading = false,
                        isVideo = false,
                    ).lastOrNull()
            }
        },
    ).setLoadErrorHandlingPolicy(LiveStreamAwareLoadErrorHandlingPolicy())

@OptIn(UnstableApi::class)
private fun provideMergingMediaSource(
    context: Context,
    downloadCache: SimpleCache,
    playerCache: SimpleCache,
    streamRepository: StreamRepository,
    coroutineScope: CoroutineScope,
    dataStoreManager: DataStoreManager,
): MergingMediaSourceFactory =
    MergingMediaSourceFactory(
        provideMediaSourceFactory(
            context,
            downloadCache,
            playerCache,
            streamRepository,
            dataStoreManager,
            coroutineScope,
        ),
        provideLiveStreamMediaSourceFactory(context, streamRepository, dataStoreManager),
        dataStoreManager,
        context,
        downloadCache,
        playerCache,
    )

@UnstableApi
private fun provideRendererFactory(context: Context): DefaultRenderersFactory =
    object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink =
            DefaultAudioSink
                .Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        emptyArray(),
                        SilenceSkippingAudioProcessor(
                            2_000_000,
                            (20_000 / 2_000_000).toFloat(),
                            2_000_000,
                            0,
                            256,
                        ),
                        SonicAudioProcessor(),
                    ),
                ).build()
    }

@UnstableApi
private fun provideCacheDataSource(
    downloadCache: SimpleCache,
    playerCache: SimpleCache,
    context: Context,
    proxy: Proxy? = null,
): CacheDataSource.Factory =
    CacheDataSource
        .Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(
            CacheDataSource
                .Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(provideHttpDataSourceFactory(context, proxy)),
        ).setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

@UnstableApi
private fun provideHttpDataSourceFactory(
    context: Context,
    proxy: Proxy?,
): DataSource.Factory =
    DefaultDataSource.Factory(
        context,
        OkHttpDataSource.Factory(
            OkHttpClient
                .Builder()
                .connectTimeout(30.seconds)
                .readTimeout(30.seconds)
                .proxy(
                    proxy,
                ).addInterceptor(OfflineBlockInterceptor())
                .build(),
        ),
    )

@UnstableApi
private fun provideAudioAttributes(): AudioAttributes =
    AudioAttributes
        .Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

@UnstableApi
private fun provideDatabaseProvider(context: Context) = StandaloneDatabaseProvider(context)

@UnstableApi
private fun provideSimpleCache(
    context: Context,
    cacheName: String,
    cacheSize: Int = -1,
    databaseProvider: DatabaseProvider,
) = SimpleCache(
    context.filesDir.resolve(cacheName),
    when (cacheSize) {
        -1 -> NoOpCacheEvictor()
        else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
    },
    databaseProvider,
)

@UnstableApi
private fun provideCoilBitmapLoader(
    context: Context,
    coroutineScope: CoroutineScope,
): CoilBitmapLoader = CoilBitmapLoader(context, coroutineScope)

@OptIn(UnstableApi::class)
fun loadMediaService() {
    loadKoinModules(mediaServiceModule)
}

@OptIn(UnstableApi::class)
fun startService(
    context: Context,
    serviceConnection: ServiceConnection,
): Boolean {
    val intent = Intent(context, SimpleMediaService::class.java)
    try {
        context.startService(intent)
    } catch (e: IllegalStateException) {
        Logger.w("Service", "Unable to start service before binding: ${e.message}")
    }
    return context.bindService(intent, serviceConnection, BIND_AUTO_CREATE)
}

@OptIn(UnstableApi::class)
fun stopService(context: Context) {
    context.stopService(Intent(context, SimpleMediaService::class.java))
}

@OptIn(UnstableApi::class)
fun setServiceActivitySession(
    context: Context,
    cls: Class<out Activity>,
    musicService: IBinder?,
) {
    (musicService as? SimpleMediaService.MusicBinder)?.setActivitySession(context, cls)
}
