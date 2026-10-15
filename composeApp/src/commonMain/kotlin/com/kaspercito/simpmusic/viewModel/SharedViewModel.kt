package com.kaspercito.simpmusic.viewModel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import com.kaspercito.common.AppIdentity
import com.kaspercito.common.Config
import com.kaspercito.common.Config.ALBUM_CLICK
import com.kaspercito.common.Config.DOWNLOAD_CACHE
import com.kaspercito.common.Config.PLAYLIST_CLICK
import com.kaspercito.common.Config.RECOVER_TRACK_QUEUE
import com.kaspercito.common.Config.SHARE
import com.kaspercito.common.Config.SONG_CLICK
import com.kaspercito.common.Config.VIDEO_CLICK
import com.kaspercito.common.LibraryChipType
import com.kaspercito.common.SELECTED_LANGUAGE
import com.kaspercito.common.STATUS_DONE
import com.kaspercito.domain.data.entities.AlbumEntity
import com.kaspercito.domain.data.entities.DownloadState
import com.kaspercito.domain.data.entities.LocalPlaylistEntity
import com.kaspercito.domain.data.entities.LyricsEntity
import com.kaspercito.domain.data.entities.NewFormatEntity
import com.kaspercito.domain.data.entities.PlaylistEntity
import com.kaspercito.domain.data.entities.SongEntity
import com.kaspercito.domain.data.entities.SongInfoEntity
import com.kaspercito.domain.data.entities.TranslatedLyricsEntity
import com.kaspercito.domain.data.model.browse.album.Track
import com.kaspercito.domain.data.model.canvas.CanvasResult
import com.kaspercito.domain.data.model.download.DownloadProgress
import com.kaspercito.domain.data.model.intent.GenericIntent
import com.kaspercito.domain.data.model.metadata.Lyrics
import com.kaspercito.domain.data.model.promo.Promo
import com.kaspercito.domain.data.model.streams.TimeLine
import com.kaspercito.domain.data.model.update.UpdateData
import com.kaspercito.domain.data.player.GenericCastState
import com.kaspercito.domain.data.player.LiveStreamRegistry
import com.kaspercito.domain.extension.decodeHtmlEntities
import com.kaspercito.domain.extension.isSong
import com.kaspercito.domain.extension.isVideo
import com.kaspercito.domain.extension.toGenericMediaItem
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.data.model.lyrics.RomanizationLanguage
import com.kaspercito.domain.manager.DataStoreManager.Values.FALSE
import com.kaspercito.domain.manager.DataStoreManager.Values.TRUE
import com.kaspercito.domain.mediaservice.handler.ControlState
import com.kaspercito.domain.mediaservice.handler.DownloadHandler
import com.kaspercito.domain.mediaservice.handler.NowPlayingTrackState
import com.kaspercito.domain.mediaservice.handler.PlayerEvent
import com.kaspercito.domain.mediaservice.handler.PlaylistType
import com.kaspercito.domain.mediaservice.handler.QueueData
import com.kaspercito.domain.mediaservice.handler.RepeatState
import com.kaspercito.domain.mediaservice.handler.SimpleMediaState
import com.kaspercito.domain.mediaservice.handler.SleepTimerState
import com.kaspercito.domain.mediaservice.handler.addToQueueBlock
import com.kaspercito.domain.repository.AlbumRepository
import com.kaspercito.domain.repository.CacheRepository
import com.kaspercito.domain.repository.LocalPlaylistRepository
import com.kaspercito.domain.repository.LyricsCanvasRepository
import com.kaspercito.domain.repository.PlaylistRepository
import com.kaspercito.domain.repository.SongRepository
import com.kaspercito.domain.repository.StreamRepository
import com.kaspercito.domain.repository.UpdateRepository
import com.kaspercito.domain.utils.Resource
import com.kaspercito.domain.utils.toListName
import com.kaspercito.domain.utils.toLyrics
import com.kaspercito.domain.utils.toLyricsEntity
import com.kaspercito.domain.utils.toSongEntity
import com.kaspercito.domain.utils.toSyncedLyrics
import com.kaspercito.domain.utils.toTrack
import com.kaspercito.logger.LogLevel
import com.kaspercito.logger.Logger
import com.kaspercito.simpmusic.Platform
import com.kaspercito.simpmusic.expect.getDownloadFolderPath
import com.kaspercito.simpmusic.expect.ui.toByteArray
import com.kaspercito.simpmusic.getPlatform
import com.kaspercito.simpmusic.utils.VersionManager
import com.kaspercito.simpmusic.utils.decodePromos
import com.kaspercito.simpmusic.utils.shownPromoIds
import com.kaspercito.simpmusic.utils.withShownPromo
import com.kaspercito.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.core.component.inject
import org.simpmusic.lastfm.completeLogin
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.added_to_queue
import simpmusic.composeapp.generated.resources.added_to_youtube_liked
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.lastfm_login_failed
import simpmusic.composeapp.generated.resources.login_success
import simpmusic.composeapp.generated.resources.play_next
import simpmusic.composeapp.generated.resources.removed_from_youtube_liked
import simpmusic.composeapp.generated.resources.shared
import simpmusic.composeapp.generated.resources.updated
import simpmusic.composeapp.generated.resources.vote_submitted
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.reflect.KClass

// DataStore key for the kotlin-footguns star prompt. Cleared on every version bump,
// so the ask comes back after an update - same policy as OPEN_APP_TIME.
const val FOOTGUNS_STAR_KEY = "footguns_starred"

/** Spotify has no [DataStoreManager] provider constant (it is a fallback, never the chosen provider), so the chain names it here. */
private const val SPOTIFY_LYRICS_KEY = "spotify"

/** Past this a provider is skipped, so one that hangs cannot hold back the others. */
private const val LYRICS_PROVIDER_TIMEOUT_MS = 15_000L

@OptIn(ExperimentalCoroutinesApi::class)
class SharedViewModel(
    private val dataStoreManager: DataStoreManager,
    private val streamRepository: StreamRepository,
    private val updateRepository: UpdateRepository,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val playlistRepository: PlaylistRepository,
    private val lyricsCanvasRepository: LyricsCanvasRepository,
    private val cacheRepository: CacheRepository,
) : BaseViewModel() {
    var isFirstLiked: Boolean = false
    var isFirstMiniplayer: Boolean = false
    var isFirstSuggestions: Boolean = false
    var showedUpdateDialog: Boolean = false

    private val appIdentity: AppIdentity by inject()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate

    private var _liked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val liked: SharedFlow<Boolean> = _liked.asSharedFlow()

    var isServiceRunning: Boolean = false

    private var _sleepTimerState = MutableStateFlow(SleepTimerState(false, 0))
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState

    private var regionCode: String? = null
    private var language: String? = null

    private var _format: MutableStateFlow<NewFormatEntity?> = MutableStateFlow(null)
    val format: SharedFlow<NewFormatEntity?> = _format.asSharedFlow()

    /**
     * Which extractor and cipher decoder produced the current track's URLs, shown in the info sheet.
     *
     * Read alongside the format rather than stored with it: the format row is cached and reused,
     * while this describes the extraction that happened in this run of the app.
     */
    private val _extractSource: MutableStateFlow<String?> = MutableStateFlow(null)
    val extractSource: StateFlow<String?> = _extractSource.asStateFlow()

    private var _canvas: MutableStateFlow<CanvasResult?> = MutableStateFlow(null)
    val canvas: StateFlow<CanvasResult?> = _canvas

    private var canvasJob: Job? = null

    private val _intent: MutableStateFlow<GenericIntent?> = MutableStateFlow(null)
    val intent: StateFlow<GenericIntent?> = _intent

    private val _showNotificationPermissionDialog = MutableStateFlow(false)
    val showNotificationPermissionDialog: StateFlow<Boolean> = _showNotificationPermissionDialog

    private val _isOfficialBuild = MutableStateFlow(true)
    val isOfficialBuild: StateFlow<Boolean> = _isOfficialBuild

    // One-shot: the Desktop capsule asks the Now Playing panel, which hosts the page, to open
    // full-screen lyrics. The panel consumes it once shown.
    private val _fullscreenLyricsRequest = MutableStateFlow(false)
    val fullscreenLyricsRequest: StateFlow<Boolean> = _fullscreenLyricsRequest

    fun requestFullscreenLyrics() {
        _fullscreenLyricsRequest.value = true
    }

    fun consumeFullscreenLyricsRequest() {
        _fullscreenLyricsRequest.value = false
    }

    private var getFormatFlowJob: Job? = null

    var playlistId: MutableStateFlow<String?> = MutableStateFlow(null)

    var isFullScreen: Boolean = false

    private var _nowPlayingState = MutableStateFlow<NowPlayingTrackState?>(null)
    val nowPlayingState: StateFlow<NowPlayingTrackState?> = _nowPlayingState

    fun getQueueDataState() = mediaPlayerHandler.queueData

    val castState: StateFlow<GenericCastState> get() = mediaPlayerHandler.castState

    private var _controllerState =
        MutableStateFlow<ControlState>(
            ControlState(
                isPlaying = false,
                isShuffle = false,
                repeatState = RepeatState.None,
                isLiked = false,
                isNextAvailable = false,
                isPreviousAvailable = false,
                isCrossfading = false,
                volume = 1f,
            ),
        )
    val controllerState: StateFlow<ControlState> = _controllerState
    private val _getVideo: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val getVideo: StateFlow<Boolean> = _getVideo

    private var _timeline =
        MutableStateFlow<TimeLine>(
            TimeLine(
                current = -1L,
                total = -1L,
                bufferedPercent = 0,
                loading = true,
            ),
        )
    val timeline: StateFlow<TimeLine> = _timeline

    private var _nowPlayingScreenData =
        MutableStateFlow<NowPlayingScreenData>(
            NowPlayingScreenData.initial(),
        )
    val nowPlayingScreenData: StateFlow<NowPlayingScreenData> = _nowPlayingScreenData

    private var _likeStatus = MutableStateFlow<Boolean>(false)
    val likeStatus: StateFlow<Boolean> = _likeStatus

    /**
     * Which body of the Apple Music player was open last — held by ENUM NAME so this class stays
     * ignorant of the UI enum, which is internal to the player package.
     *
     * It cannot live in the composable: that player is inside a ModalBottomSheet, so dismissing
     * the sheet disposes the whole tree and takes any rememberSaveable with it — the tab snapped
     * back to the artwork every single time it was reopened. This class is a Koin `single`, so it
     * outlives the sheet while still resetting on app restart, which is the right lifetime for
     * "where I was a moment ago".
     */
    private val _lastPlayerViewTab = MutableStateFlow<String?>(null)
    val lastPlayerViewTab: StateFlow<String?> = _lastPlayerViewTab

    fun setLastPlayerViewTab(tabName: String) {
        _lastPlayerViewTab.value = tabName
    }

    val openAppTime: StateFlow<Int> = dataStoreManager.openAppTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)
    private val _shareSavedLyrics: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val shareSavedLyrics: StateFlow<Boolean> get() = _shareSavedLyrics

    init {
        viewModelScope.launch {
            log("SharedViewModel init")
            if (dataStoreManager.appVersion.first() != VersionManager.getVersionName()) {
                dataStoreManager.resetOpenAppTime()
                dataStoreManager.putString(FOOTGUNS_STAR_KEY, "false")
                dataStoreManager.setAppVersion(
                    VersionManager.getVersionName(),
                )
            }
            dataStoreManager.openApp()
            val timeLineJob =
                launch {
                    nowPlayingState
                        .filterNotNull()
                        .flatMapLatest { nowPlayingState ->
                            timeline.map { timeLine ->
                                Pair(timeLine, nowPlayingState)
                            }
                        }.distinctUntilChanged { old, new ->
                            // A live stream's "total" is the seek window mpv reports, and it grows with
                            // every new segment — not a new length, so it must not re-run what a new
                            // length triggers (this fired every few seconds for as long as one played).
                            val sameLiveStream = old.first.isLive && new.first.isLive
                            (old.first.total.toString() + old.second.songEntity?.videoId).hashCode() ==
                                (new.first.total.toString() + new.second.songEntity?.videoId).hashCode() ||
                                (sameLiveStream && old.second.songEntity?.videoId == new.second.songEntity?.videoId)
                        }.collectLatest {
                            val nowPlaying = it.second
                            val timeline = it.first
                            // Lyrics and canvas both key off the track's length, which a live stream has none of.
                            if (timeline.isLive) return@collectLatest
                            log("Timeline job ${(it.first.total.toString() + it.second.songEntity?.videoId).hashCode()}")
                            if (timeline.total > 0 && nowPlaying.songEntity != null) {
                                if (nowPlaying.mediaItem.isSong() && nowPlayingScreenData.value.canvasData == null) {
                                    Logger.w(tag, "Duration is ${timeline.total}")
                                    Logger.w(tag, "MediaId is ${nowPlaying.mediaItem.mediaId}")
                                    getCanvas(nowPlaying.mediaItem.mediaId, (timeline.total / 1000).toInt())
                                }
                                nowPlaying.songEntity?.let { song ->
                                    if (nowPlayingScreenData.value.lyricsData == null) {
                                        Logger.w(tag, "Get lyrics from format")
                                        getLyricsFromFormat(nowPlaying.mediaItem.isVideo(), song, (timeline.total / 1000).toInt())
                                    }
                                }
                            }
                        }
                }
            val checkGetVideoJob =
                launch {
                    dataStoreManager.watchVideoInsteadOfPlayingAudio.collectLatest {
                        _getVideo.value = it == TRUE
                    }
                }
            val lyricsProviderJob =
                launch {
                    dataStoreManager.lyricsProvider.distinctUntilChanged().collectLatest {
                        setLyricsProvider()
                    }
                }
            val shareSavedLyricsJob =
                launch {
                    dataStoreManager.helpBuildLyricsDatabase.distinctUntilChanged().collectLatest {
                        _shareSavedLyrics.value = it == TRUE
                    }
                }
//            val controllerStateJob =
//                launch {
//                    controllerState.map { it.isLiked }.distinctUntilChanged().collectLatest {
//                        if (dataStoreManager.combineLocalAndYouTubeLiked.first() == TRUE) {
//                            nowPlayingState.value?.mediaItem?.mediaId?.let {
//                                getLikeStatus(it)
//                            }
//                        }
//                    }
//                }
            timeLineJob.join()
            checkGetVideoJob.join()
            lyricsProviderJob.join()
            shareSavedLyricsJob.join()
//            controllerStateJob.join()
        }

        runBlocking {
            dataStoreManager.getString("miniplayer_guide").first().let {
                isFirstMiniplayer = it != STATUS_DONE
            }
            dataStoreManager.getString("suggest_guide").first().let {
                isFirstSuggestions = it != STATUS_DONE
            }
            dataStoreManager.getString("liked_guide").first().let {
                isFirstLiked = it != STATUS_DONE
            }
        }
        viewModelScope.launch {
            mediaPlayerHandler.nowPlayingState
                .distinctUntilChangedBy {
                    it.songEntity?.videoId
                }.collectLatest { state ->
                    canvasJob?.cancel()
                    _nowPlayingState.value = state

                    // Seed the timeline from METADATA as soon as the track is known, instead of
                    // waiting for the player. SimpleMediaState.Ready carries a duration and only
                    // fires once the container is parsed; after a queue restore nothing plays, the
                    // position poll never starts (it runs only while isPlaying), and so nothing
                    // ever reports a length — which is why a restored queue showed no times at all.
                    // SongEntity has known the length since the song was first seen.
                    //
                    // Written on EVERY track change, not just when missing: leaving the old value
                    // in place would show the previous track's length over the new one.
                    // Order matters: metadata first because it is available immediately, then the
                    // player's own duration, and only -1 ("not known yet") when neither has one.
                    //
                    // Writing -1 whenever metadata is missing — which this did at first — makes
                    // every radio track and every first-time track flash NA:NA on the clock until
                    // the container is parsed, because those rows have no durationSeconds stored.
                    // Asking the player before giving up covers exactly that case: on a normal
                    // track change it usually already knows.
                    val metadataDurationMs = (state.songEntity?.durationSeconds ?: 0).toLong() * 1000L
                    val seededTotal =
                        metadataDurationMs.takeIf { it > 0L }
                            ?: mediaPlayerHandler.getPlayerDuration().takeIf { it > 0L }
                            ?: -1L
                    _timeline.update { it.copy(total = seededTotal) }
                    state.songEntity?.let { track ->
                        _nowPlayingScreenData.value =
                            NowPlayingScreenData(
                                nowPlayingTitle = track.title,
                                artistName =
                                    track
                                        .artistName
                                        ?.joinToString(", ") ?: "",
                                isVideo = false,
                                thumbnailURL = null,
                                canvasData = null,
                                lyricsData = null,
                                songInfoData = null,
                                playlistName =
                                    mediaPlayerHandler.queueData.value
                                        ?.data
                                        ?.playlistName ?: "",
                            )
                    }
                    state.mediaItem.let { now ->
                        _canvas.value = null
                        getLikeStatus(now.mediaId)
                        getSongInfo(now.mediaId)
                        getFormat(now.mediaId)
                        _nowPlayingScreenData.update {
                            it.copy(
                                thumbnailURL = now.metadata.artworkUri,
                                isVideo = now.isVideo(),
                            )
                        }
                    }
                    state.songEntity?.let { song ->
                        _liked.value = song.liked == true
                        _nowPlayingScreenData.update {
                            it.copy(
                                isExplicit = song.isExplicit,
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            val job1 =
                launch {
                    mediaPlayerHandler.simpleMediaState.collect { mediaState ->
                        when (mediaState) {
                            is SimpleMediaState.Buffering -> {
                                _timeline.update {
                                    it.copy(
                                        loading = true,
                                    )
                                }
                            }

                            SimpleMediaState.Initial -> {
                                _timeline.update { it.copy(loading = true) }
                            }

                            SimpleMediaState.Ended -> {
                                // Park at the end of the track rather than at -1. The only formatter
                                // for these fields renders any negative as "NA:NA", and nothing here
                                // is guaranteed to follow: at the end of the queue the player simply
                                // stays ended, so a -1 written here stays on screen. Worse, the
                                // Progress branch below ignores negative values and the Loading
                                // branch restores `total` without touching `current`, which is how
                                // the player ends up showing a correct duration next to "NA:NA".
                                _timeline.update {
                                    it.copy(
                                        current = it.total.coerceAtLeast(0L),
                                        bufferedPercent = 0,
                                        loading = false,
                                    )
                                }
                            }

                            is SimpleMediaState.Progress -> {
                                if (mediaState.progress >= 0L && mediaState.progress != _timeline.value.current) {
                                    // A live stream never reports a length (ExoPlayer: C.TIME_UNSET), so
                                    // a missing one means "loading" for everything except a live stream.
                                    if (_timeline.value.total > 0L || _timeline.value.isLive) {
                                        _timeline.update {
                                            it.copy(
                                                total = mediaPlayerHandler.getPlayerDuration().takeIf { d -> d > 0L } ?: it.total,
                                                current = mediaState.progress,
                                                loading = false,
                                            )
                                        }
                                    } else {
                                        _timeline.update {
                                            it.copy(
                                                current = mediaState.progress,
                                                loading = true,
                                                total = mediaPlayerHandler.getPlayerDuration().takeIf { d -> d > 0L } ?: it.total,
                                            )
                                        }
                                    }
                                }
                                // When progress hasn't changed (same value polled again) or is negative,
                                // don't modify loading state. The loading flag is already managed by
                                // Buffering/Ready/Loading state events. Setting loading=true here would
                                // cause rapid flickering whenever the same value arrives twice,
                                // which it can: the handler's ticker and the adapter's position
                                // poll both run at 50ms and are not in step, so a poll is sometimes
                                // read twice. A repeat is not evidence of a stall, and the loading
                                // flag belongs to the Buffering/Ready events rather than to a
                                // guess made here.
                            }

                            is SimpleMediaState.Loading -> {
                                _timeline.update {
                                    it.copy(
                                        bufferedPercent = mediaState.bufferedPercentage,
                                        total = mediaState.duration,
                                        loading = true,
                                    )
                                }
                            }

                            is SimpleMediaState.Ready -> {
                                _timeline.update {
                                    it.copy(
                                        current = mediaPlayerHandler.getProgress(),
                                        loading = false,
                                        // The player's own duration wins, but ONLY when it has one.
                                        // ExoPlayer answers C.TIME_UNSET (a large negative, not
                                        // null) until it has parsed the container, and Ready is
                                        // also published from onIsLoadingChanged — which fires
                                        // before STATE_READY. Writing that would throw away the
                                        // metadata duration seeded on the track change above.
                                        total = mediaState.duration.takeIf { d -> d > 0L } ?: it.total,
                                    )
                                }
                            }
                        }
                    }
                }
            val controllerJob =
                launch {
                    Logger.w(tag, "ControllerJob is running")
                    mediaPlayerHandler.controlState.collectLatest {
                        Logger.w(tag, "ControlState is $it")
                        _controllerState.value = it
                        // Propagate crossfade state to timeline so UI can react
                        _timeline.update { timeline ->
                            timeline.copy(isCrossfading = it.isCrossfading)
                        }
                    }
                }
            val sleepTimerJob =
                launch {
                    mediaPlayerHandler.sleepTimerState.collectLatest {
                        _sleepTimerState.value = it
                    }
                }
            val playlistNameJob =
                launch {
                    mediaPlayerHandler.queueData.collectLatest {
                        _nowPlayingScreenData.update {
                            it.copy(playlistName = it.playlistName)
                        }
                    }
                }
            val liveStreamJob =
                launch {
                    // A track is only known to be live once its stream has been resolved, so this
                    // follows the registry as well as the track itself.
                    combine(mediaPlayerHandler.nowPlayingState, LiveStreamRegistry.liveVideoIds) { state, liveVideoIds ->
                        state.mediaItem.mediaId in liveVideoIds
                    }.distinctUntilChanged()
                        .collect { isLive -> _timeline.update { it.copy(isLive = isLive) } }
                }
            job1.join()
            controllerJob.join()
            sleepTimerJob.join()
            playlistNameJob.join()
            liveStreamJob.join()
        }
        // Reset downloading songs & playlists to not downloaded
        checkAllDownloadingSongs()
        checkAllDownloadingPlaylists()
        checkAllDownloadingLocalPlaylists()
    }

    fun setIntent(intent: GenericIntent?) {
        _intent.value = intent
    }

    /**
     * Finishes a Last.fm login from the `wordbyword://lastfm-auth` callback.
     *
     * It lands here, and not on the login screen, because the callback arrives at the app rather
     * than at any one screen: the user left for their browser, and the screen they left from may
     * not even exist any more if the process was killed. Routing the token onwards through
     * navigation would push a second copy of the login screen on top of the one already open.
     *
     * The screen learns it succeeded by watching the stored session key, not by being told.
     */
    fun completeLastfmLogin(token: String) {
        if (token.isEmpty()) return
        viewModelScope.launch {
            val session = completeLogin(token)
            if (session != null) {
                dataStoreManager.setLastfmSession(
                    sessionKey = session.sessionKey,
                    username = session.username,
                )
                makeToast(getString(Res.string.login_success))
            } else {
                makeToast(getString(Res.string.lastfm_login_failed))
            }
        }
    }

    fun showNotificationPermissionDialog() {
        _showNotificationPermissionDialog.value = true
    }

    fun dismissNotificationPermissionDialog(doNotShowAgain: Boolean) {
        _showNotificationPermissionDialog.value = false
        if (doNotShowAgain) {
            putString("notification_permission_do_not_ask", "true")
        }
    }

    private fun getLikeStatus(videoId: String?) {
        viewModelScope.launch {
            if (videoId != null) {
                _likeStatus.value = false
                songRepository.getLikeStatus(videoId).collectLatest { status ->
                    _likeStatus.value = status
                }
            }
        }
    }

    private fun getCanvas(
        videoId: String,
        duration: Int,
    ) {
        Logger.w(tag, "Start getCanvas: $videoId $duration")
//        canvasJob?.cancel()
        viewModelScope.launch {
            // Both sources fill the same slot, so they are tried in order rather than raced:
            // animated artwork first, and a Spotify canvas only if that found nothing AND the user
            // has that switch on too. Neither switch touches the other — a track with no animated
            // artwork still gets its canvas, and turning Spotify off still means no canvas at all.
            val sources =
                buildList {
                    if (dataStoreManager.amAnimatedArtwork.first() == TRUE) {
                        add(lyricsCanvasRepository.getAMAnimatedArtwork(videoId))
                    }
                    if (dataStoreManager.spotifyCanvas.first() == TRUE) {
                        add(lyricsCanvasRepository.getCanvas(dataStoreManager, videoId, duration))
                    }
                }
            if (sources.isEmpty()) return@launch

            var resolved = false
            for (source in sources) {
                if (resolved) break
                source.cancellable().collect { response ->
                    val data = response.data
                    if (response is Resource.Success && data != null && nowPlayingState.value?.mediaItem?.mediaId == videoId) {
                        resolved = true
                        _canvas.value = data
                        _nowPlayingScreenData.update {
                            it.copy(
                                canvasData =
                                    NowPlayingScreenData.CanvasData(
                                        isVideo = data.isVideo,
                                        url = data.canvasUrl,
                                        thumbUrl = data.canvasThumbUrl,
                                    ),
                            )
                        }
                        // Save canvas video url
                        if (data.isVideo) lyricsCanvasRepository.updateCanvasUrl(videoId, data.canvasUrl)
                        // Save canvas thumb url
                        data.canvasThumbUrl?.let { lyricsCanvasRepository.updateCanvasThumbUrl(videoId, it) }
                    } else {
                        log("Get canvas miss from a source: ${response.message}")
                    }
                }
            }

            if (!resolved) {
                nowPlayingState.value?.songEntity?.canvasUrl?.let { url ->
                    _nowPlayingScreenData.update {
                        it.copy(
                            canvasData =
                                NowPlayingScreenData.CanvasData(
                                    isVideo = url.isCanvasVideoUrl(),
                                    url = url,
                                    thumbUrl = nowPlayingState.value?.songEntity?.canvasThumbUrl,
                                ),
                        )
                    }
                }
            }
        }
    }

    fun getString(key: String): String? = runBlocking { dataStoreManager.getString(key).first() }

    /**
     * The `promos` block the last config fetch saved, as it changes. Every DataStore write re-emits
     * the whole settings file (the playback position is saved every few seconds), so only a change
     * to this block's own text gets through.
     */
    fun cachedPromos(): Flow<List<Promo>> =
        dataStoreManager
            .getString(Promo.cacheKey(appIdentity.isDevBuild))
            .distinctUntilChanged()
            .map(::decodePromos)

    /** The ids of the launch banners this app version has already shown. */
    suspend fun shownPromos(): Set<String> =
        shownPromoIds(
            stored = dataStoreManager.getString(Promo.shownKey(appIdentity.isDevBuild)).first(),
            appVersion = VersionManager.getVersionName(),
        )

    /** Each banner once per app version, however it was closed. */
    fun markPromoShown(id: String) {
        viewModelScope.launch {
            val key = Promo.shownKey(appIdentity.isDevBuild)
            dataStoreManager.putString(
                key,
                withShownPromo(dataStoreManager.getString(key).first(), VersionManager.getVersionName(), id),
            )
        }
    }

    // A Library tab asked for from outside the Library screen (a deep link); the screen selects it
    // the next time it composes and clears the request.
    private val _libraryTabRequest = MutableStateFlow<LibraryChipType?>(null)
    val libraryTabRequest: StateFlow<LibraryChipType?> = _libraryTabRequest.asStateFlow()

    fun requestLibraryTab(tab: LibraryChipType?) {
        _libraryTabRequest.value = tab
    }

    fun putString(
        key: String,
        value: String,
    ) {
        runBlocking { dataStoreManager.putString(key, value) }
    }

    fun setSleepTimer(minutes: Int) {
        mediaPlayerHandler.sleepStart(minutes)
    }

    fun stopSleepTimer() {
        mediaPlayerHandler.sleepStop()
    }

    private var _downloadState: MutableStateFlow<DownloadHandler.Download?> = MutableStateFlow(null)
    var downloadState: StateFlow<DownloadHandler.Download?> = _downloadState.asStateFlow()

    fun checkIsRestoring() {
        viewModelScope.launch {
            val downloadedCacheKeys = cacheRepository.getAllCacheKeys(DOWNLOAD_CACHE)
            songRepository.getDownloadedSongs().first().let { songs ->
                songs?.forEach { song ->
                    if (!downloadedCacheKeys.contains(song.videoId)) {
                        songRepository.updateDownloadState(
                            song.videoId,
                            DownloadState.STATE_NOT_DOWNLOADED,
                        )
                    }
                }
            }
            playlistRepository.getAllDownloadedPlaylist().first().let { list ->
                for (data in list) {
                    when (data) {
                        is AlbumEntity -> {
                            val tracks = data.tracks ?: emptyList()
                            if (tracks.isEmpty() ||
                                (
                                    !downloadedCacheKeys.containsAll(
                                        tracks,
                                    )
                                )
                            ) {
                                albumRepository.updateAlbumDownloadState(
                                    data.browseId,
                                    DownloadState.STATE_NOT_DOWNLOADED,
                                )
                            }
                        }

                        is PlaylistEntity -> {
                            val tracks = data.tracks ?: emptyList()
                            if (tracks.isEmpty() ||
                                (
                                    !downloadedCacheKeys.containsAll(
                                        tracks,
                                    )
                                )
                            ) {
                                playlistRepository.updatePlaylistDownloadState(
                                    data.id,
                                    DownloadState.STATE_NOT_DOWNLOADED,
                                )
                            }
                        }

                        is LocalPlaylistEntity -> {
                            val tracks = data.tracks ?: emptyList()
                            if (tracks.isEmpty() ||
                                (
                                    !downloadedCacheKeys.containsAll(
                                        tracks,
                                    )
                                )
                            ) {
                                localPlaylistRepository.updateLocalPlaylistDownloadState(
                                    DownloadState.STATE_NOT_DOWNLOADED,
                                    data.id,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun insertLyrics(lyrics: LyricsEntity) {
        viewModelScope.launch {
            lyricsCanvasRepository.insertLyrics(lyrics)
        }
    }

    private fun getSavedLyrics(track: Track) {
        viewModelScope.launch {
            lyricsCanvasRepository.getSavedLyrics(track.videoId).cancellable().collectLatest { lyrics ->
                if (lyrics != null) {
                    val lyricsData = lyrics.toLyrics()
                    Logger.d(tag, "Saved Lyrics $lyricsData")
                    updateLyrics(
                        track.videoId,
                        track.durationSeconds ?: 0,
                        lyricsData,
                        false,
                        LyricsProvider.OFFLINE,
                    )
                    getAITranslationLyrics(
                        track.videoId,
                        lyricsData,
                    )
                }
            }
        }
    }

    fun loadSharedMediaItem(videoId: String) {
        viewModelScope.launch {
            val localSong = songRepository.getSongById(videoId).firstOrNull()
            if (localSong != null) {
                val track = localSong.toTrack()
                mediaPlayerHandler.setQueueData(
                    QueueData.Data(
                        listTracks = arrayListOf(track),
                        firstPlayedTrack = track,
                        playlistId = "RDAMVM$videoId",
                        playlistName = getString(Res.string.shared),
                        playlistType = PlaylistType.RADIO,
                        continuation = null,
                    ),
                )
                loadMediaItemFromTrack(track, SONG_CLICK)
            } else {
                streamRepository.getFullMetadata(videoId).collectLatest { response ->
                    val track = response.data
                    when (response) {
                        is Resource.Success if (track != null) -> {
                            mediaPlayerHandler.setQueueData(
                                QueueData.Data(
                                    listTracks = arrayListOf(track),
                                    firstPlayedTrack = track,
                                    playlistId = "RDAMVM$videoId",
                                    playlistName = getString(Res.string.shared),
                                    playlistType = PlaylistType.RADIO,
                                    continuation = null,
                                ),
                            )
                            loadMediaItemFromTrack(track, SONG_CLICK)
                        }

                        else -> {
                            log("Load shared media item error: ${response.message}", LogLevel.WARN)
                            makeToast("${getString(Res.string.error)}: ${response.message}")
                        }
                    }
                }
            }
        }
    }

    fun loadMediaItemFromTrack(
        track: Track,
        type: String,
        index: Int? = null,
    ) {
        viewModelScope.launch {
            mediaPlayerHandler.clearMediaItems()
            songRepository.insertSong(track.toSongEntity()).lastOrNull()?.let {
                println("insertSong: $it")
                songRepository
                    .getSongById(track.videoId)
                    .collect { songEntity ->
                        if (songEntity != null) {
                            Logger.w("Check like", "loadMediaItemFromTrack ${songEntity.liked}")
                            _liked.value = songEntity.liked
                        }
                    }
            }
            track.durationSeconds?.let {
                songRepository.updateDurationSeconds(
                    it,
                    track.videoId,
                )
            }
            withContext(Dispatchers.Main) {
                mediaPlayerHandler.addMediaItem(track.toGenericMediaItem(), playWhenReady = type != RECOVER_TRACK_QUEUE)
            }

            when (type) {
                SONG_CLICK -> {
                    mediaPlayerHandler.getRelated(track.videoId)
                }

                VIDEO_CLICK -> {
                    mediaPlayerHandler.getRelated(track.videoId)
                }

                SHARE -> {
                    mediaPlayerHandler.getRelated(track.videoId)
                }

                PLAYLIST_CLICK -> {
                    if (index == null) {
//                                        fetchSourceFromQueue(downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = 0)
                    } else {
//                                        fetchSourceFromQueue(index!!, downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = index)
                    }
                }

                ALBUM_CLICK -> {
                    if (index == null) {
//                                        fetchSourceFromQueue(downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = 0)
                    } else {
//                                        fetchSourceFromQueue(index!!, downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = index)
                    }
                }
            }
        }
    }

    fun onUIEvent(uiEvent: UIEvent) =
        viewModelScope.launch {
            when (uiEvent) {
                UIEvent.Backward -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.Backward,
                    )
                }

                UIEvent.Forward -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Forward)
                }

                UIEvent.PlayPause -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.PlayPause,
                    )
                }

                UIEvent.Next -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                }

                UIEvent.Previous -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.Previous,
                    )
                }

                UIEvent.SkipToPrevious -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.SkipToPrevious,
                    )
                }

                UIEvent.Stop -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Stop)
                }

                is UIEvent.UpdateProgress -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.UpdateProgress(
                            uiEvent.newProgress,
                        ),
                    )
                }

                UIEvent.Repeat -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Repeat)
                }

                UIEvent.Shuffle -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Shuffle)
                }

                UIEvent.ToggleLike -> {
                    Logger.w(tag, "ToggleLike")
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.ToggleLike)
                }

                is UIEvent.UpdateVolume -> {
                    val newVolume = uiEvent.newVolume
                    // Apply to the player first: persisting to DataStore is a suspending disk write
                    // and must not sit between the user's gesture and the audible change.
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.UpdateVolume(newVolume))
                    dataStoreManager.setPlayerVolume(newVolume)
                }
            }
        }

    override fun onCleared() {
        Logger.w("Check onCleared", "onCleared")
    }

    fun getLocation() {
        regionCode = runBlocking { dataStoreManager.location.first() }
        language = runBlocking { dataStoreManager.getString(SELECTED_LANGUAGE).first() }
    }

    private fun checkAllDownloadingLocalPlaylists() {
        viewModelScope.launch {
            localPlaylistRepository.getAllDownloadingLocalPlaylists().collectLatest { playlists ->
                playlists.forEach { playlist ->
                    localPlaylistRepository.updateDownloadState(playlist.id, 0, successMessage = getString(Res.string.updated)).lastOrNull()
                }
            }
        }
    }

    private fun checkAllDownloadingPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllDownloadingPlaylist().collectLatest { list ->
                list.forEach { data ->
                    when (data) {
                        is AlbumEntity -> {
                            albumRepository.updateAlbumDownloadState(data.browseId, 0)
                        }

                        is PlaylistEntity -> {
                            playlistRepository.updatePlaylistDownloadState(data.id, 0)
                        }

                        else -> {
                            // Skip
                        }
                    }
                }
            }
        }
    }

    /**
     * Limpieza de arranque: lo que quedó "descargando" o "preparando" de la sesión anterior ya no
     * lo está. Debe mirar UNA vez (`first`). Antes usaba `collect` sobre un flujo de Room, que no
     * termina nunca: el primer `collect` se quedaba escuchando para siempre (y el segundo no llegaba a
     * ejecutarse), y cada vez que el servicio de descargas marcaba una canción como "descargando" este
     * observador la devolvía al instante a "no descargada". La barra avanzaba pero las canciones
     * aparecían sin descargar. Las que Media3 sí retoma las vuelve a marcar `DownloadUtils`.
     */
    private fun checkAllDownloadingSongs() {
        viewModelScope.launch {
            songRepository.getDownloadingSongs().first()?.forEach { song ->
                songRepository.updateDownloadState(
                    song.videoId,
                    DownloadState.STATE_NOT_DOWNLOADED,
                )
            }
            songRepository.getPreparingSongs().first().forEach { song ->
                songRepository.updateDownloadState(
                    song.videoId,
                    DownloadState.STATE_NOT_DOWNLOADED,
                )
            }
        }
    }

    private fun getFormat(mediaId: String?) {
        if (mediaId != _format.value?.videoId && !mediaId.isNullOrEmpty()) {
            _format.value = null
            _extractSource.value = streamRepository.getExtractSource(mediaId)
            getFormatFlowJob?.cancel()
            getFormatFlowJob =
                viewModelScope.launch {
                    streamRepository.getFormatFlow(mediaId).cancellable().collectLatest { f ->
                        Logger.d(tag, "Get format for $mediaId: itag ${f?.itag}, expires ${f?.expiredTime}")
                        if (f != null) {
                            _format.emit(f)
                        } else {
                            _format.emit(null)
                        }
                        // Re-read on every emission: the first one usually lands before the
                        // extractor has finished, so the source is only known on a later pass.
                        _extractSource.value = streamRepository.getExtractSource(mediaId)
                    }
                }
        }
    }

    private var songInfoJob: Job? = null

    fun getSongInfo(mediaId: String?) {
        songInfoJob?.cancel()
        songInfoJob =
            viewModelScope.launch {
                if (mediaId != null) {
                    songRepository.getSongInfo(mediaId).collect { song ->
                        _nowPlayingScreenData.update {
                            it.copy(
                                songInfoData = song,
                            )
                        }
                    }
                }
            }
    }

    private var _updateResponse = MutableStateFlow<UpdateData?>(null)
    val updateResponse: StateFlow<UpdateData?> = _updateResponse

    fun checkForUpdate() {
        // Raised before the coroutine starts, so Home's launch banner, which reads it right after
        // startup, can never miss a check that is already under way but not yet marked.
        _isCheckingUpdate.value = true
        viewModelScope.launch {
            @Suppress("UNUSED_VARIABLE") val storedUpdateChannel = dataStoreManager.updateChannel.first()
            val updateChannel = DataStoreManager.GITHUB
            dataStoreManager.putString(
                "CheckForUpdateAt",
                System.currentTimeMillis().toString(),
            )
            if (updateChannel == DataStoreManager.GITHUB) {
                updateRepository.checkForGithubReleaseUpdate().collectLatest { response ->
                    val data = response.data
                    when (response) {
                        is Resource.Success if (data != null) -> {
                            _updateResponse.value = data
                            showedUpdateDialog = true
                        }

                        else -> {
                            log("Check for update error: ${response.message}", LogLevel.WARN)
                        }
                    }
                    _isCheckingUpdate.value = false
                }
            } else if (updateChannel == DataStoreManager.FDROID) {
                updateRepository.checkForFdroidUpdate().collectLatest { response ->
                    val data = response.data
                    when (response) {
                        is Resource.Success if (data != null) -> {
                            _updateResponse.value = data
                            showedUpdateDialog = true
                        }

                        else -> {
                            log("Check for update error: ${response.message}", LogLevel.WARN)
                        }
                    }
                    _isCheckingUpdate.value = false
                }
            }
        }.invokeOnCompletion {
            // Also for a channel neither branch handles, and for a check that throws: the launch
            // banner waits for this flag, so it must never stay raised.
            _isCheckingUpdate.value = false
        }
    }

    /**
     * [signingCerts]: SHA-256 hex of each certificate this APK is signed with. A failed fetch leaves
     * the app usable — it plays offline, and an unknown answer must not lock out our own users.
     */
    /**
     * MaKaDiMi es una app propia: no hay "build oficial" de otro autor contra el que comparar,
     * así que este chequeo está desactivado y la app nunca se bloquea por firma o nombre de paquete.
     */
    @Suppress("UNUSED_PARAMETER")
    fun checkOfficialBuild(
        packageName: String,
        signingCerts: List<String>,
    ) {
        _isOfficialBuild.value = true
    }

    fun stopPlayer() {
        _nowPlayingScreenData.value = NowPlayingScreenData.initial()
        _nowPlayingState.value = null
        mediaPlayerHandler.resetSongAndQueue()
        onUIEvent(UIEvent.Stop)
    }

    private fun loadPlaylistOrAlbum(index: Int? = null) {
        mediaPlayerHandler.loadPlaylistOrAlbum(index)
    }

    private fun updateLyrics(
        videoId: String,
        duration: Int, // 0 if translated lyrics
        inputLyrics: Lyrics?,
        isTranslatedLyrics: Boolean,
        lyricsProvider: LyricsProvider = LyricsProvider.SIMPMUSIC,
    ) {
        if (inputLyrics == null) {
            _nowPlayingScreenData.update {
                it.copy(
                    lyricsData = null,
                )
            }
            return
        }

        val lyrics =
            inputLyrics.copy(
                lines =
                    inputLyrics.lines?.map { line ->
                        line.copy(
                            words = decodeHtmlEntities(line.words),
                        )
                    },
            )

        if (isTranslatedLyrics && lyricsProvider != LyricsProvider.AI) {
            // Skip sync validation for AI translations — timestamps are copied programmatically
            val originalLyrics = _nowPlayingScreenData.value.lyricsData?.lyrics
            val originalLines = originalLyrics?.lines
            val lyricsLines = lyrics.lines
            if (originalLyrics != null && originalLines != null && lyricsLines != null) {
                var timeSyncErrorCount = 0
                val totalLines = originalLines.size

                if (originalLines.size == lyricsLines.size) {
                    // Line counts match: compare by index (1:1 mapping)
                    originalLines.forEachIndexed { index, originalLine ->
                        val originalTime = originalLine.startTimeMs.toLongOrNull() ?: 0L
                        val translatedLine = lyricsLines[index]
                        val translatedTime = translatedLine.startTimeMs.toLongOrNull() ?: 0L
                        val timeDiff = abs(originalTime - translatedTime)

                        if (timeDiff > 1000L) {
                            timeSyncErrorCount++
                        }
                    }
                } else {
                    // Line count mismatch: use timestamp-based matching with used-line tracking
                    val usedIndices = mutableSetOf<Int>()
                    originalLines.forEach { originalLine ->
                        val originalTime = originalLine.startTimeMs.toLongOrNull() ?: 0L
                        var bestIndex = -1
                        var bestDiff = Long.MAX_VALUE
                        lyricsLines.forEachIndexed { index, line ->
                            if (index !in usedIndices) {
                                val diff = abs((line.startTimeMs.toLongOrNull() ?: 0L) - originalTime)
                                if (diff < bestDiff) {
                                    bestDiff = diff
                                    bestIndex = index
                                }
                            }
                        }
                        if (bestIndex >= 0) {
                            usedIndices.add(bestIndex)
                            if (bestDiff > 1000L) {
                                timeSyncErrorCount++
                            }
                        } else {
                            timeSyncErrorCount++
                        }
                    }
                }

                // Use percentage-based threshold: reject if >25% of lines are out of sync
                val syncErrorRatio = if (totalLines > 0) timeSyncErrorCount.toFloat() / totalLines else 0f
                if (syncErrorRatio > 0.25f || (totalLines > 0 && timeSyncErrorCount > totalLines / 2)) {
                    Logger.w(
                        tag,
                        "Translated lyrics out of sync: $timeSyncErrorCount/$totalLines lines with time diff > 1s (${(syncErrorRatio * 100).toInt()}%)",
                    )

                    _nowPlayingScreenData.update {
                        it.copy(
                            lyricsData =
                                it.lyricsData?.copy(
                                    translatedLyrics = null,
                                ),
                        )
                    }

                    viewModelScope.launch {
                        lyricsCanvasRepository.removeTranslatedLyrics(
                            videoId,
                            dataStoreManager.translationLanguage.first(),
                        )
                        log("Removed out-of-sync translated lyrics for $videoId")
                        val simpMusicLyricsId = lyrics.simpMusicLyrics?.id
                        if (lyricsProvider == LyricsProvider.SIMPMUSIC && !simpMusicLyricsId.isNullOrEmpty()) {
                            viewModelScope.launch {
                                lyricsCanvasRepository
                                    .voteSimpMusicTranslatedLyrics(
                                        translatedLyricsId = simpMusicLyricsId,
                                        false,
                                    ).collectLatest {
                                        when (it) {
                                            is Resource.Error -> {
                                                Logger.w(tag, "Vote SimpMusic Translated Lyrics Error ${it.message}")
                                            }

                                            is Resource.Success -> {
                                                Logger.d(tag, "Vote SimpMusic Translated Lyrics Success")
                                            }
                                        }
                                    }
                            }
                        }
                        nowPlayingScreenData.value.lyricsData?.lyrics?.let {
                            getAITranslationLyrics(
                                videoId,
                                it,
                            )
                        }
                    }
                    return
                }
            }
        }

        val shouldSendLyricsToSimpMusic =
            runBlocking {
                dataStoreManager.helpBuildLyricsDatabase.first() == TRUE
            } &&
                lyricsProvider != LyricsProvider.SIMPMUSIC
        if (_nowPlayingState.value?.songEntity?.videoId == videoId) {
            val track = _nowPlayingState.value?.track
            when (isTranslatedLyrics) {
                true -> {
                    if (lyricsProvider == LyricsProvider.SIMPMUSIC) {
                        _translatedVoteState.value =
                            VoteData(
                                id = lyrics.simpMusicLyrics?.id ?: "",
                                vote = lyrics.simpMusicLyrics?.vote ?: 0,
                                state = VoteState.Idle,
                            )
                    }
                    _nowPlayingScreenData.update {
                        it.copy(
                            lyricsData =
                                it.lyricsData?.copy(
                                    translatedLyrics = lyrics to lyricsProvider,
                                ),
                        )
                    }
                    if (shouldSendLyricsToSimpMusic && track != null) {
                        viewModelScope.launch {
                            lyricsCanvasRepository
                                .insertSimpMusicTranslatedLyrics(
                                    dataStoreManager,
                                    track,
                                    lyrics,
                                    dataStoreManager.translationLanguage.first(),
                                ).collect {
                                    when (it) {
                                        is Resource.Error -> {
                                            log("Insert SimpMusic Translated Lyrics Error ${it.message}", LogLevel.WARN)
                                        }

                                        is Resource.Success -> {
                                            log("Insert SimpMusic Translated Lyrics Success")
                                        }
                                    }
                                }
                        }
                    }
                }

                false -> {
                    if (lyricsProvider == LyricsProvider.SIMPMUSIC) {
                        _lyricsVoteState.value =
                            VoteData(
                                id = lyrics.simpMusicLyrics?.id ?: "",
                                vote = lyrics.simpMusicLyrics?.vote ?: 0,
                                state = VoteState.Idle,
                            )
                    }
                    _nowPlayingScreenData.update {
                        it.copy(
                            lyricsData =
                                NowPlayingScreenData.LyricsData(
                                    lyrics = lyrics,
                                    lyricsProvider = lyricsProvider,
                                ),
                        )
                    }
                    // Save lyrics to database
                    viewModelScope.launch {
                        lyricsCanvasRepository.insertLyrics(
                            LyricsEntity(
                                videoId = videoId,
                                error = false,
                                lines = lyrics.lines,
                                syncType = lyrics.syncType,
                            ),
                        )
                    }
                    if (shouldSendLyricsToSimpMusic && track != null) {
                        viewModelScope.launch {
                            lyricsCanvasRepository
                                .insertSimpMusicLyrics(
                                    dataStoreManager,
                                    track,
                                    duration,
                                    lyrics,
                                ).collect {
                                    when (it) {
                                        is Resource.Error -> {
                                            Logger.w(tag, "Insert MaKaDiMi Lyrics Error ${it.message}")
                                        }

                                        is Resource.Success -> {
                                            Logger.d(tag, "Insert MaKaDiMi Lyrics Success")
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    /** The lyrics request running for [lyricsJobVideoId], so a second trigger for the same track does not restart it. */
    private var lyricsJob: Job? = null
    private var lyricsJobVideoId: String? = null

    private fun getLyricsFromFormat(
        isVideo: Boolean,
        song: SongEntity,
        duration: Int,
        force: Boolean = false,
    ) {
        // A live broadcast has nothing to sync lyrics to — the "duration" mpv reports for it is only
        // its seek window — so no provider is asked. Every caller comes through here.
        if (LiveStreamRegistry.isLive(song.videoId)) return
        // The timeline fires again for the same track while buffering settles; the search already
        // running answers for it. Only a deliberate change (the user picked another provider) restarts it.
        if (!force && lyricsJob?.isActive == true && lyricsJobVideoId == song.videoId) return
        lyricsJob?.cancel()
        lyricsJobVideoId = song.videoId
        lyricsJob =
            viewModelScope.launch {
                val videoId = song.videoId
                log("Get Lyrics From Format for $videoId", LogLevel.WARN)
                val artistName = song.artistName
                val artist =
                    if (artistName?.firstOrNull() != null &&
                        artistName
                            .firstOrNull()
                            ?.contains("Various Artists") == false
                    ) {
                        artistName.firstOrNull()
                    } else {
                        mediaPlayerHandler.nowPlaying
                            .first()
                            ?.metadata
                            ?.artist
                            ?: ""
                    }
                resetLyricsVoteState()
                resolveLyrics(song, artist ?: "", duration, dataStoreManager.lyricsProvider.first())
            }
    }

    /** One provider's answer, with the translation the provider itself supplied (YouTube captions do). */
    private class FetchedLyrics(
        val lyrics: Lyrics,
        val provider: LyricsProvider,
        val translated: Lyrics? = null,
    )

    private fun Lyrics.isSynced() = syncType == "LINE_SYNCED" || syncType == "RICH_SYNCED"

    /**
     * Finds lyrics for [song] by asking the providers in turn, the user's choice first.
     *
     * Each provider used to fall back to a different, shorter list of the others, and several of the
     * paths ended in nothing at all — a miss on LRCLIB, for instance, never tried BetterLyrics,
     * Spotify or the YouTube captions. Now every provider is tried until one answers with synced
     * lyrics. Unsynced lyrics are shown as soon as they are found (better than a blank screen) but
     * do not stop the search: a synced version from a later provider replaces them. When nothing
     * answers, the copy saved on the device is used.
     */
    private suspend fun resolveLyrics(
        song: SongEntity,
        artist: String,
        duration: Int,
        preferred: String,
    ) {
        val videoId = song.videoId
        val spotifyUsable =
            dataStoreManager.spotifyLyrics.first() == TRUE && dataStoreManager.spdc.first().isNotEmpty()
        val order =
            buildList {
                add(preferred)
                listOf(DataStoreManager.SIMPMUSIC, DataStoreManager.BETTER_LYRICS).forEach { if (it !in this) add(it) }
                if (spotifyUsable) add(SPOTIFY_LYRICS_KEY)
                listOf(DataStoreManager.LRCLIB, DataStoreManager.YOUTUBE).forEach { if (it !in this) add(it) }
            }
        Logger.d(tag, "Lyrics order for $videoId: $order")

        var shown: FetchedLyrics? = null
        for (key in order) {
            currentCoroutineContext().ensureActive()
            val fetched =
                withTimeoutOrNull(LYRICS_PROVIDER_TIMEOUT_MS) { fetchLyrics(key, song, artist, duration) }
            if (fetched == null || fetched.lyrics.lines.isNullOrEmpty()) {
                Logger.d(tag, "Lyrics: $key has nothing for $videoId")
                continue
            }
            val synced = fetched.lyrics.isSynced()
            // Already showing something, and this is no better.
            if (shown != null && !synced) continue
            if (shown != null) resetLyricsVoteState()
            Logger.d(tag, "Lyrics: $key answered for $videoId (${fetched.lyrics.syncType})")
            updateLyrics(videoId, duration, fetched.lyrics, false, fetched.provider)
            insertLyrics(fetched.lyrics.toLyricsEntity(videoId))
            shown = fetched
            if (synced) break
        }

        val result = shown
        if (result == null) {
            // Nothing online. A copy saved by an earlier play is better than an empty screen.
            getSavedLyrics(song.toTrack().copy(durationSeconds = duration))
            return
        }
        // Translated once, for the lyrics that stayed on screen — not for a draft that was replaced.
        when {
            result.provider == LyricsProvider.SIMPMUSIC -> getSimpMusicTranslatedLyrics(videoId, result.lyrics)
            result.translated != null -> updateLyrics(videoId, duration, result.translated, true, result.provider)
            else -> getAITranslationLyrics(videoId, result.lyrics)
        }
    }

    private suspend fun fetchLyrics(
        key: String,
        song: SongEntity,
        artist: String,
        duration: Int,
    ): FetchedLyrics? =
        when (key) {
            DataStoreManager.SIMPMUSIC ->
                lyricsCanvasRepository
                    .getSimpMusicLyrics(song.videoId)
                    .firstOrNull()
                    ?.data
                    ?.let { FetchedLyrics(it, LyricsProvider.SIMPMUSIC) }

            DataStoreManager.BETTER_LYRICS ->
                lyricsCanvasRepository
                    .getBetterLyrics(artist, song.title, duration)
                    .firstOrNull()
                    ?.data
                    ?.let { FetchedLyrics(it, LyricsProvider.BETTER_LYRICS) }

            DataStoreManager.LRCLIB ->
                lyricsCanvasRepository
                    .getLrclibLyricsData(artist, song.title, duration)
                    .firstOrNull()
                    ?.data
                    ?.let { FetchedLyrics(it, LyricsProvider.LRCLIB) }

            SPOTIFY_LYRICS_KEY ->
                lyricsCanvasRepository
                    .getSpotifyLyrics(dataStoreManager, "${song.title} $artist", duration)
                    .firstOrNull()
                    ?.data
                    ?.let { FetchedLyrics(it, LyricsProvider.SPOTIFY) }

            DataStoreManager.YOUTUBE ->
                lyricsCanvasRepository
                    .getYouTubeCaption(dataStoreManager.youtubeSubtitleLanguage.first(), song.videoId)
                    .firstOrNull()
                    ?.data
                    ?.let { FetchedLyrics(it.first, LyricsProvider.YOUTUBE, it.second) }

            else -> null
        }

    private suspend fun getSimpMusicTranslatedLyrics(
        videoId: String,
        lyrics: Lyrics,
    ) {
        val translationLanguage =
            dataStoreManager.translationLanguage.first()
        lyricsCanvasRepository.getSimpMusicTranslatedLyrics(videoId, translationLanguage).collectLatest { response ->
            val data = response.data
            when (response) {
                is Resource.Success if (data != null) -> {
                    // If SimpMusic translated lyrics are RICH_SYNCED (word-by-word),
                    // convert to LINE_SYNCED, downvote, and fallback to AI translation
                    if (data.syncType == "RICH_SYNCED") {
                        Logger.w(tag, "SimpMusic translated lyrics are RICH_SYNCED, downvoting and falling back to AI")
                        val simpMusicLyricsId = data.simpMusicLyrics?.id
                        if (!simpMusicLyricsId.isNullOrEmpty()) {
                            viewModelScope.launch {
                                lyricsCanvasRepository
                                    .voteSimpMusicTranslatedLyrics(simpMusicLyricsId, false)
                                    .collectLatest { voteResult ->
                                        when (voteResult) {
                                            is Resource.Error -> Logger.w(tag, "Downvote RICH_SYNCED translated lyrics error: ${voteResult.message}")
                                            is Resource.Success -> Logger.d(tag, "Downvote RICH_SYNCED translated lyrics success")
                                        }
                                    }
                            }
                        }
                        // Fallback to AI translation
                        getAITranslationLyrics(videoId, lyrics)
                    } else {
                        Logger.d(tag, "Get SimpMusic Translated Lyrics Success")
                        updateLyrics(
                            videoId,
                            0,
                            data,
                            true,
                            LyricsProvider.SIMPMUSIC,
                        )
                    }
                }

                else -> {
                    Logger.d(tag, "Get SimpMusic Translated Lyrics Error: ${response.message}")
                    getAITranslationLyrics(
                        videoId,
                        lyrics,
                    )
                }
            }
        }
    }

    private suspend fun getAITranslationLyrics(
        videoId: String,
        lyrics: Lyrics,
    ) {
        Logger.d(tag, "Get AI Translation Lyrics for $videoId")
        if (dataStoreManager.useAITranslation.first() == TRUE &&
            dataStoreManager.aiApiKey.first().isNotEmpty() &&
            dataStoreManager.enableTranslateLyric.first() == FALSE
        ) {
            val savedTranslatedLyrics =
                lyricsCanvasRepository
                    .getSavedTranslatedLyrics(
                        videoId,
                        dataStoreManager.translationLanguage.first(),
                    ).firstOrNull()
            if (savedTranslatedLyrics != null) {
                Logger.d(tag, "Get Saved Translated Lyrics")
                updateLyrics(
                    videoId,
                    0,
                    savedTranslatedLyrics.toLyrics(),
                    true,
                    LyricsProvider.AI,
                )
            } else {
                // Convert RICH_SYNCED to LINE_SYNCED before sending to AI
                // AI should only work with line-level or plain lyrics
                val lyricsForAi =
                    if (lyrics.syncType == "RICH_SYNCED") {
                        lyrics.toSyncedLyrics()
                    } else {
                        lyrics
                    }
                lyricsCanvasRepository
                    .getAITranslationLyrics(
                        lyricsForAi,
                        dataStoreManager.translationLanguage.first(),
                    ).cancellable()
                    .collectLatest {
                        val data = it.data
                        when (it) {
                            is Resource.Success if (data != null) -> {
                                Logger.d(tag, "Get AI Translate Lyrics Success")
                                lyricsCanvasRepository.insertTranslatedLyrics(
                                    TranslatedLyricsEntity(
                                        videoId = videoId,
                                        language = dataStoreManager.translationLanguage.first(),
                                        error = false,
                                        lines = data.lines,
                                        syncType = data.syncType,
                                    ),
                                )
                                updateLyrics(
                                    videoId,
                                    0,
                                    data,
                                    true,
                                    LyricsProvider.AI,
                                )
                            }

                            else -> {
                                Logger.w(tag, "Get AI Translate Lyrics Error: ${it.message}")
                            }
                        }
                    }
            }
        }
    }

    private fun setLyricsProvider() {
        viewModelScope.launch {
            val songEntity = nowPlayingState.value?.songEntity ?: return@launch
            val isVideo = nowPlayingState.value?.mediaItem?.isVideo() ?: false
            getLyricsFromFormat(isVideo, songEntity, timeline.value.total.toInt() / 1000, force = true)
        }
    }

    private var _recreateActivity: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val recreateActivity: StateFlow<Boolean> = _recreateActivity

    fun activityRecreate() {
        _recreateActivity.value = true
    }

    fun activityRecreateDone() {
        _recreateActivity.value = false
    }

    fun addListToQueue(listTrack: ArrayList<Track>) {
        viewModelScope.launch {
            // A radio, a mix or an Endless queue has no end to append to, so a single song (the swipe
            // on a song row) goes right after the current one instead.
            val endlessQueue = dataStoreManager.endlessQueue.first() == TRUE
            if (listTrack.size == 1 && mediaPlayerHandler.queueData.value.addToQueueBlock(endlessQueue) != null) {
                mediaPlayerHandler.playNext(listTrack.first())
                makeToast(getString(Res.string.play_next))
            } else {
                mediaPlayerHandler.loadMoreCatalog(listTrack)
                makeToast(getString(Res.string.added_to_queue))
            }
        }
    }

    fun addToYouTubeLiked() {
        viewModelScope.launch {
            val videoId = mediaPlayerHandler.nowPlaying.first()?.mediaId
            if (videoId != null) {
                val like = likeStatus.value
                if (!like) {
                    songRepository
                        .addToYouTubeLiked(
                            mediaPlayerHandler.nowPlaying.first()?.mediaId,
                        ).collect { response ->
                            if (response == 200) {
                                makeToast(getString(Res.string.added_to_youtube_liked))
                                getLikeStatus(videoId)
                            } else {
                                makeToast("${getString(Res.string.error)}: HTTP $response")
                            }
                        }
                } else {
                    songRepository
                        .removeFromYouTubeLiked(
                            mediaPlayerHandler.nowPlaying.first()?.mediaId,
                        ).collect {
                            if (it == 200) {
                                makeToast(getString(Res.string.removed_from_youtube_liked))
                                getLikeStatus(videoId)
                            } else {
                                makeToast("${getString(Res.string.error)}: HTTP $it")
                            }
                        }
                }
            }
        }
    }

    fun getEnableLiquidGlass() = dataStoreManager.enableLiquidGlass

    fun getLocalTrackingEnabled() = dataStoreManager.localTrackingEnabled

    // Drives the Mix for you tab: YouTube hands an anonymous session no mixes at all.
    fun getYouTubeLoggedIn() = dataStoreManager.loggedIn

    fun getThemeMode() = dataStoreManager.themeMode

    fun getThemeColorSource() = dataStoreManager.themeColorSource

    fun getCustomThemeColor() = dataStoreManager.customThemeColor

    fun getNowPlayingStyle() = dataStoreManager.nowPlayingStyle

    fun getLyricsStyle() = dataStoreManager.lyricsStyle

    fun getLyricsOffsetMs() = dataStoreManager.lyricsOffsetMs

    fun setLyricsOffsetMs(offsetMs: Int) {
        viewModelScope.launch {
            dataStoreManager.setLyricsOffsetMs(offsetMs)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            dataStoreManager.setThemeMode(mode)
        }
    }

    fun setThemeColorSource(source: String) {
        viewModelScope.launch {
            dataStoreManager.setThemeColorSource(source)
        }
    }

    fun setCustomThemeColor(argbHex: String) {
        viewModelScope.launch {
            dataStoreManager.setCustomThemeColor(argbHex)
        }
    }

    fun setNowPlayingStyle(style: String) {
        viewModelScope.launch {
            dataStoreManager.setNowPlayingStyle(style)
        }
    }

    fun setLyricsStyle(style: String) {
        viewModelScope.launch {
            dataStoreManager.setLyricsStyle(style)
        }
    }

    fun getRomanizationLanguages() = dataStoreManager.romanizationLanguages

    fun setRomanizationLanguages(languages: Set<RomanizationLanguage>) {
        viewModelScope.launch {
            // Sorted by name so the stored string is stable: an unsorted Set writes a different
            // value for the same selection depending on iteration order, which makes the DataStore
            // flow emit on a change that did not happen.
            dataStoreManager.setRomanizationLanguages(languages.map { it.name }.sorted().joinToString(","))
        }
    }

    private val _reloadDestination: MutableStateFlow<KClass<*>?> = MutableStateFlow(null)
    val reloadDestination: StateFlow<KClass<*>?> = _reloadDestination.asStateFlow()

    fun reloadDestination(destination: KClass<*>) {
        _reloadDestination.value = destination
    }

    fun reloadDestinationDone() {
        _reloadDestination.value = null
    }

    fun shouldCheckForUpdate(): Boolean = runBlocking { dataStoreManager.autoCheckForUpdates.first() == TRUE }

    private var _downloadFileProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.INIT)
    val downloadFileProgress: StateFlow<DownloadProgress> get() = _downloadFileProgress

    fun downloadFile(bitmap: ImageBitmap) {
        val fileName =
            "${nowPlayingScreenData.value.nowPlayingTitle} - ${nowPlayingScreenData.value.artistName}"
                .replace(Regex("""[|\\?*<":>]"""), "")
                .replace(" ", "_")
        val path =
            "${getDownloadFolderPath()}/$fileName"
        viewModelScope.launch {
            nowPlayingState.value?.track?.let { track ->
                val bytesArray = bitmap.toByteArray()
                try {
                    val fileOutputStream = FileOutputStream("$path.jpg")
                    fileOutputStream.write(bytesArray)
                    fileOutputStream.close()
                    Logger.d(tag, "Thumbnail saved to $path.jpg")
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
                songRepository
                    .downloadToFile(
                        track = track,
                        videoId = track.videoId,
                        path = path,
                        isVideo = nowPlayingScreenData.value.isVideo && getVideo.value,
                    ).collectLatest {
                        _downloadFileProgress.value = it
                    }
            }
        }
    }

    fun downloadFileDone() {
        _downloadFileProgress.value = DownloadProgress.INIT
    }

    fun onDoneReview(isDismissOnly: Boolean = true) {
        viewModelScope.launch {
            if (!isDismissOnly) {
                dataStoreManager.doneOpenAppTime()
            } else {
                dataStoreManager.openApp()
            }
        }
    }

    fun onDoneRequestingShareLyrics(contributor: Pair<String, String>? = null) {
        viewModelScope.launch {
            dataStoreManager.setHelpBuildLyricsDatabase(true)
            dataStoreManager.setContributorLyricsDatabase(
                contributor,
            )
        }
    }

    fun setBitmap(bitmap: ImageBitmap?) {
        _nowPlayingScreenData.update {
            it.copy(bitmap = bitmap)
        }
    }

    // Vote state for translated lyrics
    private val _translatedVoteState = MutableStateFlow<VoteData?>(null)
    val translatedVoteState: StateFlow<VoteData?> = _translatedVoteState.asStateFlow()

    // Vote state for original lyrics
    private val _lyricsVoteState = MutableStateFlow<VoteData?>(null)
    val lyricsVoteState: StateFlow<VoteData?> = _lyricsVoteState.asStateFlow()

    /**
     * Vote for SimpMusic original lyrics (upvote or downvote)
     * @param upvote true for upvote, false for downvote
     */
    fun voteLyrics(upvote: Boolean) {
        val lyricsData = _nowPlayingScreenData.value.lyricsData
        val lyricsProvider = lyricsData?.lyricsProvider
        val simpMusicLyricsId = lyricsData?.lyrics?.simpMusicLyrics?.id ?: return

        if (lyricsProvider != LyricsProvider.SIMPMUSIC || simpMusicLyricsId.isEmpty()) {
            Logger.w(tag, "Cannot vote: not a SimpMusic lyrics or missing ID")
            return
        }

        viewModelScope.launch {
            _lyricsVoteState.update {
                it?.copy(
                    state = VoteState.Loading,
                )
            }
            lyricsCanvasRepository
                .voteSimpMusicLyrics(
                    lyricsId = simpMusicLyricsId,
                    upvote = upvote,
                ).collectLatest { result ->
                    when (result) {
                        is Resource.Error -> {
                            Logger.w(tag, "Vote MaKaDiMi Lyrics Error ${result.message}")
                            _lyricsVoteState.update {
                                it?.copy(
                                    state = VoteState.Error(result.message ?: "Unknown error"),
                                )
                            }
                        }

                        is Resource.Success -> {
                            Logger.d(tag, "Vote MaKaDiMi Lyrics Success")
                            _lyricsVoteState.update {
                                it?.copy(
                                    state = VoteState.Success(upvote),
                                    vote = it.vote + if (upvote) 1 else -1,
                                )
                            }
                            makeToast(getString(Res.string.vote_submitted))
                        }
                    }
                }
        }
    }

    private fun resetLyricsVoteState() {
        _lyricsVoteState.value = null
        _translatedVoteState.value = null
    }

    /**
     * Vote for SimpMusic translated lyrics (upvote or downvote)
     * @param upvote true for upvote, false for downvote
     */
    fun voteTranslatedLyrics(upvote: Boolean) {
        val translatedLyrics = _nowPlayingScreenData.value.lyricsData?.translatedLyrics
        val lyricsProvider = translatedLyrics?.second
        val simpMusicLyricsId = translatedLyrics?.first?.simpMusicLyrics?.id ?: return

        if (lyricsProvider != LyricsProvider.SIMPMUSIC || simpMusicLyricsId.isEmpty()) {
            Logger.w(tag, "Cannot vote: not a SimpMusic translated lyrics or missing ID")
            return
        }

        viewModelScope.launch {
            _translatedVoteState.update {
                it?.copy(
                    state = VoteState.Loading,
                )
            }
            lyricsCanvasRepository
                .voteSimpMusicTranslatedLyrics(
                    translatedLyricsId = simpMusicLyricsId,
                    upvote = upvote,
                ).collectLatest { result ->
                    when (result) {
                        is Resource.Error -> {
                            Logger.w(tag, "Vote SimpMusic Translated Lyrics Error ${result.message}")
                            _translatedVoteState.update {
                                it?.copy(
                                    state = VoteState.Error(result.message ?: "Unknown error"),
                                )
                            }
                        }

                        is Resource.Success -> {
                            Logger.d(tag, "Vote SimpMusic Translated Lyrics Success")
                            _translatedVoteState.update {
                                it?.copy(
                                    state = VoteState.Success(upvote),
                                    vote = it.vote + if (upvote) 1 else -1,
                                )
                            }
                            makeToast(getString(Res.string.vote_submitted))
                        }
                    }
                }
        }
    }

    fun shouldStopMusicService(): Boolean = runBlocking { dataStoreManager.killServiceOnExit.first() == TRUE }

    fun isUserLoggedIn(): Boolean = runBlocking { dataStoreManager.cookie.first().isNotEmpty() }

    // Flow-based variant of [isUserLoggedIn] so composables can collect login state once
    // instead of calling runBlocking inside composition (used by NowPlayingScreenContent).
    fun isUserLoggedInFlow(): Flow<Boolean> = dataStoreManager.cookie.map { it.isNotEmpty() }

    fun isCombineFavoriteAndYTLiked(): Boolean = runBlocking { dataStoreManager.combineLocalAndYouTubeLiked.first() == TRUE }
}

sealed class UIEvent {
    data object PlayPause : UIEvent()

    data object Backward : UIEvent()

    data object Forward : UIEvent()

    data object Next : UIEvent()

    data object Previous : UIEvent()

    /**
     * Always advances to the previous track — bypasses the 3-second
     * "seek to start of current track" rule used by [Previous]. Used by the
     * NowPlaying artwork pager swipe.
     */
    data object SkipToPrevious : UIEvent()

    data object Stop : UIEvent()

    data object Shuffle : UIEvent()

    data object Repeat : UIEvent()

    data class UpdateProgress(
        val newProgress: Float,
    ) : UIEvent()

    data class UpdateVolume(
        val newVolume: Float,
    ) : UIEvent()

    data object ToggleLike : UIEvent()
}

enum class LyricsProvider {
    SIMPMUSIC,
    YOUTUBE,
    SPOTIFY,
    LRCLIB,
    BETTER_LYRICS,
    AI,
    OFFLINE,
}

data class NowPlayingScreenData(
    val playlistName: String,
    val nowPlayingTitle: String,
    val artistName: String,
    val isVideo: Boolean,
    val isExplicit: Boolean = false,
    val thumbnailURL: String?,
    val canvasData: CanvasData? = null,
    val lyricsData: LyricsData? = null,
    val songInfoData: SongInfoEntity? = null,
    val bitmap: ImageBitmap? = null,
) {
    data class CanvasData(
        val isVideo: Boolean,
        val url: String,
        // A still of the clip at the clip's own size — Apple Music's animated artwork sends one.
        // The Apple Music player shows it under the clip while that loads, and reads the clip's
        // proportions off it, so the frame is the right shape before the first video frame arrives.
        val thumbUrl: String? = null,
    )

    data class LyricsData(
        val lyrics: Lyrics,
        val translatedLyrics: Pair<Lyrics, LyricsProvider>? = null,
        val lyricsProvider: LyricsProvider,
    )

    companion object {
        fun initial(): NowPlayingScreenData =
            NowPlayingScreenData(
                nowPlayingTitle = "",
                artistName = "",
                isVideo = false,
                thumbnailURL = null,
                canvasData = null,
                lyricsData = null,
                songInfoData = null,
                playlistName = "",
            )
    }
}

data class VoteData(
    val id: String,
    val vote: Int,
    val state: VoteState,
)

sealed class VoteState {
    data object Idle : VoteState()

    data object Loading : VoteState()

    data class Success(
        val upvote: Boolean,
    ) : VoteState()

    data class Error(
        val message: String,
    ) : VoteState()
}

/**
 * Whether a stored canvas url points at something a player should open rather than an image.
 *
 * The column holds whatever the active source wrote: a Spotify canvas is an `.mp4`, while AM
 * animated artwork is an HLS `.m3u8` master playlist. Testing only for `.mp4` — as this did before
 * AM existed — sends every AM artwork down the still-image branch, and because the branch that
 * reads this is the one that restores a *cached* url, the failure only appears from the second play
 * of a track onwards.
 */
private fun String.isCanvasVideoUrl(): Boolean = contains(".mp4") || contains(".m3u8")
