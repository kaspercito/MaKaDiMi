package com.kaspercito.data.repository

import com.kaspercito.common.ITAG
import com.kaspercito.common.MERGING_DATA_TYPE
import com.kaspercito.common.OfflineMode
import com.kaspercito.common.QUALITY
import com.kaspercito.common.VIDEO_QUALITY
import com.kaspercito.data.db.datasource.LocalDataSource
import com.kaspercito.data.mapping.toSponsorSkipSegments
import com.kaspercito.data.mapping.toTrack
import com.kaspercito.domain.data.entities.NewFormatEntity
import com.kaspercito.domain.data.model.browse.album.Track
import com.kaspercito.domain.data.model.mediaService.SponsorSkipSegments
import com.kaspercito.domain.data.player.LiveStreamRegistry
import com.kaspercito.domain.extension.isBefore
import com.kaspercito.domain.extension.now
import com.kaspercito.domain.extension.plusSeconds
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.repository.StreamRepository
import com.kaspercito.domain.utils.Resource
import com.kaspercito.kotlinytmusicscraper.YouTube
import com.kaspercito.kotlinytmusicscraper.models.MediaType
import com.kaspercito.kotlinytmusicscraper.models.response.PlayerResponse
import com.kaspercito.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

internal class StreamRepositoryImpl(
    private val localDataSource: LocalDataSource,
    private val youTube: YouTube,
) : StreamRepository {
    override suspend fun insertNewFormat(newFormat: NewFormatEntity) =
        withContext(Dispatchers.IO) {
            localDataSource.insertNewFormat(newFormat)
        }

    override fun getNewFormat(videoId: String): Flow<NewFormatEntity?> = flow { emit(localDataSource.getNewFormat(videoId)) }.flowOn(Dispatchers.IO)

    override suspend fun getFormatFlow(videoId: String) = localDataSource.getNewFormatAsFlow(videoId)

    override fun getExtractSource(videoId: String): String? = youTube.getExtractSource(videoId)

    override suspend fun updateFormat(videoId: String) {
        if (OfflineMode.enabled) return
        localDataSource.getNewFormat(videoId)?.let { oldFormat ->
            Logger.d("Stream", "oldFormatExpired: ${oldFormat.expiredTime}")
            Logger.d("Stream", "now: ${now()}")
            Logger.d("Stream", "isExpired: ${oldFormat.expiredTime.isBefore(now())}")
            if (oldFormat.expiredTime.isBefore(now())) {
                youTube
                    .player(videoId)
                    .onSuccess { triple ->
                        val response = triple.second
                        localDataSource.updateNewFormat(
                            oldFormat.copy(
                                expiredTime = now().plusSeconds(response.streamingData?.expiresInSeconds?.toLong() ?: 0L),
                                playbackTrackingVideostatsPlaybackUrl =
                                    response.playbackTracking?.videostatsPlaybackUrl?.baseUrl?.replace(
                                        "https://s.youtube.com",
                                        "https://music.youtube.com",
                                    ),
                                playbackTrackingAtrUrl =
                                    response.playbackTracking?.atrUrl?.baseUrl?.replace(
                                        "https://s.youtube.com",
                                        "https://music.youtube.com",
                                    ),
                                playbackTrackingVideostatsWatchtimeUrl =
                                    response.playbackTracking?.videostatsWatchtimeUrl?.baseUrl?.replace(
                                        "https://s.youtube.com",
                                        "https://music.youtube.com",
                                    ),
                                cpn = triple.first,
                            ),
                        )
                        Logger.w("UpdateFormat", "Updated format for $videoId")
                    }.onFailure { throwable ->
                        Logger.e("UpdateFormat", "Error: ${throwable.message}")
                    }
            }
        }
    }

    override fun getStream(
        dataStoreManager: DataStoreManager,
        videoId: String,
        isDownloading: Boolean,
        isVideo: Boolean,
        muxed: Boolean,
    ): Flow<String?> =
        flow {
            if (OfflineMode.enabled) {
                // Nothing can be fetched: answer at once instead of running every client through its
                // timeouts, which kept the player buffering before it failed anyway.
                Logger.w("Stream", "Offline mode: not resolving a stream URL for $videoId")
                emit(null)
                return@flow
            }
            val itag =
                if (isDownloading) {
                    QUALITY.itagOf(dataStoreManager.downloadQuality.first())
                } else {
                    QUALITY.itagOf(dataStoreManager.quality.first())
                }
            val videoItag =
                if (!muxed) {
                    VIDEO_QUALITY.itags.getOrNull(
                        VIDEO_QUALITY.items.indexOf(
                            if (isDownloading) {
                                dataStoreManager.videoDownloadQuality.first()
                            } else {
                                dataStoreManager.videoQuality.first()
                            },
                        ),
                    )
                        ?: ITAG.VIDEO_360P
                } else {
                    ITAG.MUXED_360P
                }
            youTube
                .player(
                    videoId,
                    noLogIn = muxed,
                    preferredAudioLanguage = dataStoreManager.preferredAudioLanguage.first(),
                )
                .onSuccess { data ->
                    val response = data.second
                    val isLive = response.videoDetails?.isLive == true
                    LiveStreamRegistry.recordLiveStatus(videoId, isLive)
                    if (isLive) {
                        // A live broadcast plays from its HLS playlist alone, so none of the itag
                        // choice below applies. Nothing about it goes into the format table either:
                        // the playlist URL is good for a few hours and the broadcast may be over by
                        // the next run, so every play resolves it afresh — and the players learn
                        // that it is live from LiveStreamRegistry, recorded just above.
                        val liveHlsUrl = response.streamingData?.hlsManifestUrl
                        Logger.i("Stream", "$videoId: live stream, HLS URL ${if (liveHlsUrl == null) "missing" else "found"}")
                        emit(liveHlsUrl)
                        return@onSuccess
                    }
                    Logger.d(
                        "Stream",
                        response.streamingData
                            ?.formats
                            ?.map { it.itag }
                            .toString() + " " +
                            response.streamingData
                                ?.adaptiveFormats
                                ?.map { it.itag }
                                .toString(),
                    )
                    val formatList = mutableListOf<PlayerResponse.StreamingData.Format>()
                    formatList.addAll(
                        response.streamingData?.formats?.filter { it.url.isNullOrEmpty().not() } ?: emptyList(),
                    )
                    formatList.addAll(
                        response.streamingData?.adaptiveFormats?.filter { it.url.isNullOrEmpty().not() }
                            ?: emptyList(),
                    )
                    val videoFormat =
                        formatList.find { it.itag == videoItag }
                            ?: formatList.find { it.itag == ITAG.VIDEO_720P }
                            ?: formatList.find { it.itag == ITAG.VIDEO_360P }
                            ?: formatList.find { !it.isAudio && it.url.isNullOrEmpty().not() }
                    val audioTwinItag = ITAG.highQualityTwinOf(itag)
                    val audioFormat =
                        formatList.find { it.itag == itag } ?: if (audioTwinItag != null) {
                            formatList.find { it.itag == audioTwinItag }
                        } else {
                            formatList.find { it.isAudio && it.url.isNullOrEmpty().not() }
                        }
                    var format =
                        if (isVideo) {
                            videoFormat
                        } else {
                            audioFormat
                        }
                    if (format == null) {
                        format = formatList.lastOrNull { it.url.isNullOrEmpty().not() }
                    }
                    if (muxed) {
                        format = formatList
                            .filter {
                                val url = it.url
                                url != null && youTube.isManifestUrl(url)
                            }.maxByOrNull { it.width ?: 0 } ?: formatList.find { it.itag == videoItag }
                    }
                    // One line per resolved stream and never the URL: a googlevideo URL carries the
                    // listener's IP address.
                    Logger.i(
                        "Stream",
                        "$videoId: ${data.third}, itag ${format?.itag} ${format?.mimeType?.substringBefore(';')}" +
                            (if (muxed) ", HLS" else "") +
                            ", expires in ${response.streamingData?.expiresInSeconds}s",
                    )
                    val durationSecond = response.videoDetails?.lengthSeconds?.toIntOrNull()
                    // AutoMix metadata from Tidal official API
                    var tidalBpm: Int? = null
                    var tidalMusicKey: String? = null
                    var tidalKeyScale: String? = null
                    if (!isVideo && durationSecond != null && data.third == MediaType.Song) {
                        val title = response.videoDetails?.title ?: ""
                        val author = response.videoDetails?.author ?: ""
                        val q =
                            "$title $author"
                                .replace(
                                    Regex("\\((feat\\.|ft.|cùng với|con|mukana|com|avec|合作音乐人: ) "),
                                    " ",
                                ).replace(
                                    Regex("( và | & | и | e | und |, |和| dan)"),
                                    " ",
                                ).replace("  ", " ")
                                .replace(Regex("([()])"), "")
                                .replace(".", " ")
                                .replace("  ", " ")
                        Logger.d("Stream", "Search Tidal metadata for: $q")
                        youTube
                            .searchTidalMetadata(q, durationSecond)
                            .onSuccess { metadata ->
                                Logger.d("Stream", "Tidal metadata: $metadata")
                                tidalBpm = metadata.bpm
                                tidalMusicKey = metadata.musicKey
                                tidalKeyScale = metadata.keyScale
                            }.onFailure {
                                Logger.e("Stream", "Tidal metadata error: ${it.message}", it)
                            }
                    }
                    insertNewFormat(
                        NewFormatEntity(
                            videoId = if (VIDEO_QUALITY.itags.contains(format?.itag)) "${MERGING_DATA_TYPE.VIDEO}$videoId" else videoId,
                            itag = format?.itag ?: itag,
                            mimeType =
                                Regex("""([^;]+);\s*codecs=["']([^"']+)["']""")
                                    .find(
                                        format?.mimeType ?: "",
                                    )?.groupValues
                                    ?.getOrNull(1) ?: format?.mimeType ?: "",
                            codecs =
                                Regex("""([^;]+);\s*codecs=["']([^"']+)["']""")
                                    .find(
                                        format?.mimeType ?: "",
                                    )?.groupValues
                                    ?.getOrNull(2) ?: format?.mimeType ?: "",
                            bitrate = format?.bitrate,
                            sampleRate = format?.audioSampleRate,
                            contentLength = format?.contentLength,
                            loudnessDb =
                                response.playerConfig
                                    ?.audioConfig
                                    ?.loudnessDb
                                    ?.toFloat(),
                            lengthSeconds = response.videoDetails?.lengthSeconds?.toInt(),
                            playbackTrackingVideostatsPlaybackUrl =
                                response.playbackTracking?.videostatsPlaybackUrl?.baseUrl?.replace(
                                    "https://s.youtube.com",
                                    "https://music.youtube.com",
                                ),
                            playbackTrackingAtrUrl =
                                response.playbackTracking?.atrUrl?.baseUrl?.replace(
                                    "https://s.youtube.com",
                                    "https://music.youtube.com",
                                ),
                            playbackTrackingVideostatsWatchtimeUrl =
                                response.playbackTracking?.videostatsWatchtimeUrl?.baseUrl?.replace(
                                    "https://s.youtube.com",
                                    "https://music.youtube.com",
                                ),
                            cpn = data.first,
                            expiredTime = now().plusSeconds(response.streamingData?.expiresInSeconds?.toLong() ?: 0L),
                            audioUrl = if (muxed) response.streamingData?.hlsManifestUrl else format?.url,
                            videoUrl = if (muxed) response.streamingData?.hlsManifestUrl else videoFormat?.url,
                            bpm = tidalBpm,
                            musicKey = tidalMusicKey,
                            keyScale = tidalKeyScale,
                        ),
                    )
                    if (data.first != null) {
                        emit(
                            if (muxed) {
                                response.streamingData?.hlsManifestUrl
                            } else {
                                format?.url?.let { url ->
                                    if (youTube.isManifestUrl(url)) {
                                        url.plus("&cpn=${data.first}")
                                    } else {
                                        url.plus("&cpn=${data.first}&range=0-${format.contentLength ?: 10000000}")
                                    }
                                }
                            },
                        )
                    } else {
                        emit(
                            if (muxed) {
                                response.streamingData?.hlsManifestUrl
                            } else {
                                format?.url?.let { url ->
                                    if (youTube.isManifestUrl(url)) {
                                        url
                                    } else {
                                        url.plus("&range=0-${format.contentLength ?: 10000000}")
                                    }
                                }
                            },
                        )
                    }
                }.onFailure {
                    Logger.e("Stream", "Error: ${it::class.simpleName}: ${it.message ?: it.cause?.message}")
                    emit(null)
                }
        }.flowOn(Dispatchers.IO)

    override fun initPlayback(
        playback: String,
        atr: String,
        watchTime: String,
        cpn: String,
        playlistId: String?,
    ): Flow<Pair<Int, Float>> =
        flow {
            youTube
                .initPlayback(playback, atr, watchTime, cpn, playlistId)
                .onSuccess { response ->
                    emit(response)
                }.onFailure {
                    Logger.e("InitPlayback", "Error: ${it.message}")
                    emit(Pair(0, 0f))
                }
        }.flowOn(Dispatchers.IO)

    override fun updateWatchTimeFull(
        watchTime: String,
        cpn: String,
        playlistId: String?,
    ): Flow<Int> =
        flow {
            runCatching {
                youTube
                    .updateWatchTimeFull(watchTime, cpn, playlistId)
                    .onSuccess { response ->
                        emit(response)
                    }.onFailure {
                        it.printStackTrace()
                        emit(0)
                    }
            }
        }.flowOn(Dispatchers.IO)

    override fun updateWatchTime(
        playbackTrackingVideostatsWatchtimeUrl: String,
        watchTimeList: ArrayList<Float>,
        cpn: String,
        playlistId: String?,
    ): Flow<Int> =
        flow {
            runCatching {
                youTube
                    .updateWatchTime(
                        playbackTrackingVideostatsWatchtimeUrl,
                        watchTimeList,
                        cpn,
                        playlistId,
                    ).onSuccess { response ->
                        emit(response)
                    }.onFailure {
                        it.printStackTrace()
                        emit(0)
                    }
            }
        }.flowOn(Dispatchers.IO)

    override fun getSkipSegments(videoId: String): Flow<Resource<List<SponsorSkipSegments>>> =
        flow {
            youTube
                .getSkipSegments(videoId)
                .onSuccess { data ->
                    emit(Resource.Success(data.map { it.toSponsorSkipSegments() }))
                }.onFailure {
                    emit(Resource.Error(it.message ?: "Unknown error"))
                }
        }.flowOn(Dispatchers.IO)

    override fun getFullMetadata(videoId: String): Flow<Resource<Track>> =
        flow {
            Logger.w("getFullMetadata", "videoId: $videoId")
            youTube
                .getFullMetadata(videoId)
                .onSuccess {
                    emit(Resource.Success(it.toTrack()))
                }.onFailure {
                    Logger.e("getFullMetadata", "Error: ${it.message}")
                    emit(Resource.Error(it.message ?: "Unknown error"))
                }
        }.flowOn(Dispatchers.IO)

    override fun is403Url(url: String) = flow { emit(youTube.is403Url(url)) }.flowOn(Dispatchers.IO)

    override suspend fun invalidateFormat(videoId: String) {
        withContext(Dispatchers.IO) {
            localDataSource.getNewFormat(videoId)?.let { format ->
                Logger.d("Stream", "Invalidating cached format for $videoId")
                localDataSource.updateNewFormat(
                    format.copy(expiredTime = now().plusSeconds(-1)),
                )
            }
        }
    }
}