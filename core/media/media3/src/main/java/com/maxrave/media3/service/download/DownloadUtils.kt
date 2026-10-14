package com.maxrave.media3.service.download

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.maxrave.common.MERGING_DATA_TYPE
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.extension.now
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.repository.StreamRepository
import com.maxrave.logger.Logger
import com.maxrave.media3.service.OfflineBlockInterceptor
import com.maxrave.media3.extension.isFullyCached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

@UnstableApi
internal class DownloadUtils(
    private val context: Context,
    private val playerCache: SimpleCache,
    private val downloadCache: SimpleCache,
    private val dataStoreManager: DataStoreManager,
    private val streamRepository: StreamRepository,
    private val songRepository: SongRepository,
    databaseProvider: DatabaseProvider,
) : DownloadHandler {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataSourceFactory =
        ResolvingDataSource.Factory(
            CacheDataSource
                .Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(
                    OkHttpDataSource.Factory(
                        OkHttpClient
                            .Builder()
                            .addInterceptor(OfflineBlockInterceptor())
                            .build(),
                    ),
                ),
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            Logger.d("Stream", mediaId)
            Logger.d("Stream", mediaId.startsWith(MERGING_DATA_TYPE.VIDEO).toString())
            // Already downloaded in full: hand the DataSpec back untouched so the enclosing
            // CacheDataSource reads straight off disk. Safe precisely because the whole track
            // is there — it never needs the upstream, which is the only thing the bare media id
            // in the URI cannot survive. Re-resolving here would re-fetch a track the user
            // already has.
            if (downloadCache.isFullyCached(mediaId, dataSpec.position)) {
                Logger.d("Stream", "Already downloaded $mediaId")
                return@Factory dataSpec
            }
            // Anything short of a full copy must resolve a real URL, and must never hand back
            // the incoming DataSpec: its URI is the bare media id. Skipping the bytes already on
            // disk is not this resolver's job — CacheWriter does that via cache.getCachedLength(),
            // and the CacheDataSource wrapping this resolver still serves whatever playerCache
            // holds.
            //
            // The upstream here is OkHttpDataSource (not DefaultDataSource, as on the playback
            // side), so a scheme-less URI dies in HttpUrl.parse as
            // HttpDataSourceException("Malformed URL", ERROR_CODE_FAILED_RUNTIME_CHECK) — a
            // misleading error for what is really "we could not get a stream URL". Fail loudly
            // instead, and let DownloadManager mark the download failed for the true reason.
            var dataSpecReturn: DataSpec = dataSpec
            var resolved = false
            runBlocking(Dispatchers.IO) {
                if (mediaId.contains(MERGING_DATA_TYPE.VIDEO)) {
                    val id = mediaId.removePrefix(MERGING_DATA_TYPE.VIDEO)
                    streamRepository.getNewFormat(id).lastOrNull()?.let {
                        val videoUrl = it.videoUrl
                        if (videoUrl != null && it.expiredTime > now()) {
                            Logger.d("Stream", videoUrl)
                            Logger.d("Stream", "Video from format")
                            val is403Url = streamRepository.is403Url(videoUrl).firstOrNull() != false
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
                            id,
                            isDownloading = true,
                            true,
                        ).lastOrNull()
                        ?.let {
                            dataSpecReturn = dataSpec.withUri(it.toUri())
                            resolved = true
                        }
                } else {
                    streamRepository.getNewFormat(mediaId).lastOrNull()?.let {
                        val audioUrl = it.audioUrl
                        if (audioUrl != null && it.expiredTime > now()) {
                            Logger.d("Stream", audioUrl)
                            Logger.d("Stream", "Audio from format")
                            val is403Url = streamRepository.is403Url(audioUrl).firstOrNull() != false
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
                            isDownloading = true,
                            isVideo = false,
                        ).lastOrNull()
                        ?.let {
                            if (it.contains("MPD")) {
                                DashManifestParser().parse(
                                    it.toUri(),
                                    ByteArrayInputStream(it.toByteArray()),
                                )
                            }
                            dataSpecReturn = dataSpec.withUri(it.toUri())
                            resolved = true
                        }
                }
            }
            if (!resolved) {
                Logger.e("Stream", "Failed to resolve download stream URL for $mediaId")
                throw java.io.IOException("Failed to resolve stream URL for $mediaId")
            }
            return@Factory dataSpecReturn
        }
    val downloadNotificationHelper =
        DownloadNotificationHelper(
            context,
            MusicDownloadService.Companion.CHANNEL_ID,
        )
    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executor(Runnable::run),
        ).apply {
            maxParallelDownloads = 4
            minRetryCount = 3
            addListener(
                MusicDownloadService.TerminalStateNotificationHelper(
                    context = context,
                    notificationHelper = downloadNotificationHelper,
                ),
            )
        }

    /**
     * Último estado combinado que se escribió en la base de datos por canción. Lo lee el colector de
     * [downloads] para no volver a escribir lo que no cambió, y lo vacía [onDownloadRemoved] al quitar.
     * Concurrente porque el colector corre en IO y los callbacks de DownloadManager en otro hilo.
     */
    private val lastWrittenState = ConcurrentHashMap<String, Int>()

    /**
     * Audio + video -> un solo estado por canción.
     *
     * Quitar una descarga pasa por REMOVING, que antes caía en el `else` (= "descargando") y se
     * escribía así en la base de datos justo después de que la interfaz hubiera puesto "no descargada":
     * la canción se quedaba "descargando" para siempre tras cancelar o borrar una descarga.
     */
    private fun mergedState(
        audio: Int?,
        video: Int?,
    ): Int {
        if (audio == Download.STATE_REMOVING || video == Download.STATE_REMOVING) {
            return DownloadState.STATE_NOT_DOWNLOADED
        }
        return when (audio to video) {
            Download.STATE_COMPLETED to Download.STATE_COMPLETED -> DownloadState.STATE_DOWNLOADED
            Download.STATE_FAILED to Download.STATE_FAILED -> DownloadState.STATE_NOT_DOWNLOADED
            Download.STATE_QUEUED to Download.STATE_QUEUED -> DownloadState.STATE_PREPARING
            Download.STATE_COMPLETED to null -> DownloadState.STATE_DOWNLOADED
            Download.STATE_FAILED to null -> DownloadState.STATE_NOT_DOWNLOADED
            Download.STATE_QUEUED to null -> DownloadState.STATE_PREPARING
            null to Download.STATE_COMPLETED -> DownloadState.STATE_DOWNLOADING
            null to Download.STATE_QUEUED -> DownloadState.STATE_PREPARING
            null to Download.STATE_FAILED -> DownloadState.STATE_NOT_DOWNLOADED
            else -> DownloadState.STATE_DOWNLOADING
        }
    }

    private var _downloads = MutableStateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>(emptyMap())

    // Audio / Video
    override val downloads: StateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>
        get() = _downloads
    private val _downloadTask = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val downloadTask: StateFlow<Map<String, Int>> get() = _downloadTask
    val downloadingVideoIds = MutableStateFlow<MutableSet<String>>(mutableSetOf())

    /**
     * Use thumbnail to check video or audio. The video stream is fetched only while
     * "play video instead of audio" is on: playback merges it in for nothing else.
     */
    override suspend fun downloadTrack(
        videoId: String,
        title: String,
        thumbnail: String,
    ) {
        var isVideo = false
        if (dataStoreManager.watchVideoInsteadOfPlayingAudio.first() == DataStoreManager.Values.TRUE) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnail)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            val imageResult = ImageLoader(context).execute(request)
            if (imageResult.image?.height != imageResult.image?.width && imageResult.image != null) {
                isVideo = true
            }
        }
        val downloadRequest =
            DownloadRequest
                .Builder(videoId, videoId.toUri())
                .setData(title.toByteArray())
                .setCustomCacheKey(videoId)
                .build()
        DownloadService.sendAddDownload(
            context,
            MusicDownloadService::class.java,
            downloadRequest,
            false,
        )
        if (isVideo) {
            val id = MERGING_DATA_TYPE.VIDEO + videoId
            val downloadRequestVideo =
                DownloadRequest
                    .Builder(id, id.toUri())
                    .setData("Video $title".toByteArray())
                    .setCustomCacheKey(id)
                    .build()
            DownloadService.sendAddDownload(
                context,
                MusicDownloadService::class.java,
                downloadRequestVideo,
                false,
            )
        }
    }

    override fun removeDownload(videoId: String) {
        DownloadService.sendRemoveDownload(
            context,
            MusicDownloadService::class.java,
            videoId,
            false,
        )
        val id = MERGING_DATA_TYPE.VIDEO + videoId
        DownloadService.sendRemoveDownload(
            context,
            MusicDownloadService::class.java,
            id,
            false,
        )
    }

    override fun removeAllDownloads() {
        _downloads.value = emptyMap()
        _downloadTask.value = emptyMap()
        downloadingVideoIds.value = mutableSetOf()
        lastWrittenState.clear()
        downloadManager.removeAllDownloads()
    }

    init {
        coroutineScope.launch {
            downloads.collect { download ->
                download.forEach {
                    val videoId = it.key
                    val audio = it.value.first?.state
                    val video = it.value.second?.state
                    val combineState = mergedState(audio, video)
                    // Este flujo emite el mapa ENTERO en cada cambio de cualquier descarga: con una lista
                    // de 100 canciones eso eran 100 escrituras en la base de datos por cambio (y cada una
                    // refresca las pantallas que observan canciones). Solo se escribe lo que cambió.
                    if (lastWrittenState.put(videoId, combineState) == combineState) return@forEach
                    _downloadTask.update {
                        it.toMutableMap().apply {
                            set(videoId, combineState)
                        }
                    }
                    when (combineState) {
                        DownloadState.STATE_DOWNLOADED -> {
                            downloadingVideoIds.update {
                                it.apply {
                                    remove(videoId)
                                }
                            }
                            songRepository.updateDownloadState(videoId, DownloadState.STATE_DOWNLOADED)
                        }
                        DownloadState.STATE_DOWNLOADING -> {
                            songRepository.updateDownloadState(videoId, DownloadState.STATE_DOWNLOADING)
                        }
                        DownloadState.STATE_NOT_DOWNLOADED -> {
                            downloadingVideoIds.update {
                                it.apply {
                                    remove(videoId)
                                }
                            }
                            songRepository.updateDownloadState(videoId, DownloadState.STATE_NOT_DOWNLOADED)
                        }
                        DownloadState.STATE_PREPARING -> {
                            songRepository.updateDownloadState(videoId, DownloadState.STATE_PREPARING)
                        }
                    }
                }
            }
        }

        // Pair Audio and Video
        val result = mutableMapOf<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            val id = cursor.download.request.id
            val isVideo = id.contains(MERGING_DATA_TYPE.VIDEO)
            val songId =
                if (id.contains(MERGING_DATA_TYPE.VIDEO)) {
                    id.removePrefix(MERGING_DATA_TYPE.VIDEO)
                } else {
                    id
                }
            result[songId] =
                if (isVideo) {
                    result[songId]?.copy(second = DownloadHandler.Download(cursor.download.state))
                        ?: Pair(null, DownloadHandler.Download(cursor.download.state))
                } else {
                    result[songId]?.copy(first = DownloadHandler.Download(cursor.download.state))
                        ?: Pair(DownloadHandler.Download(cursor.download.state), null)
                }
        }
        _downloads.value = result
        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadRemoved(
                    downloadManager: DownloadManager,
                    download: Download,
                ) {
                    // Sin esto la descarga quitada se quedaba para siempre en el mapa con su último
                    // estado (REMOVING), y el colector seguía releyéndola.
                    val id = download.request.id
                    val isVideo = id.startsWith(MERGING_DATA_TYPE.VIDEO)
                    val songId = if (isVideo) id.removePrefix(MERGING_DATA_TYPE.VIDEO) else id
                    var gone = false
                    _downloads.update { map ->
                        // El bloque puede repetirse si otro hilo cambió el mapa a la vez.
                        gone = false
                        val current = map[songId] ?: return@update map
                        val updated = if (isVideo) current.copy(second = null) else current.copy(first = null)
                        if (updated.first == null && updated.second == null) {
                            gone = true
                            map - songId
                        } else {
                            map + (songId to updated)
                        }
                    }
                    if (gone) {
                        lastWrittenState.remove(songId)
                        _downloadTask.update { it - songId }
                        downloadingVideoIds.update { it.apply { remove(songId) } }
                    }
                }

                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    download.request.id.let { id ->
                        var isVideo = false
                        val songId =
                            if (id.contains(MERGING_DATA_TYPE.VIDEO)) {
                                isVideo = true
                                id.removePrefix(MERGING_DATA_TYPE.VIDEO)
                            } else {
                                id
                            }
                        _downloads.update { map ->
                            map.toMutableMap().apply {
                                val current = map.getOrDefault(songId, null)
                                if (isVideo) {
                                    set(
                                        songId,
                                        current?.copy(second = DownloadHandler.Download(download.state))
                                            ?: Pair(null, DownloadHandler.Download(download.state)),
                                    )
                                } else {
                                    set(
                                        songId,
                                        current?.copy(first = DownloadHandler.Download(download.state))
                                            ?: Pair(DownloadHandler.Download(download.state), null),
                                    )
                                }
                            }
                        }
                        when (download.state) {
                            Download.STATE_COMPLETED -> {
                                playerCache.removeResource(id)
                            }

                            Download.STATE_DOWNLOADING -> {
                                coroutineScope.launch {
                                    downloadingVideoIds.update {
                                        it.apply {
                                            add(songId)
                                        }
                                    }
                                    songRepository.updateDownloadState(songId, DownloadState.STATE_DOWNLOADING)
                                }
                            }
                            else -> {
                            }
                        }
                    }
                }
            },
        )
    }
}