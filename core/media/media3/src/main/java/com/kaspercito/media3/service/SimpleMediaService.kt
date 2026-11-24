package com.kaspercito.media3.service

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.kaspercito.common.MEDIA_NOTIFICATION
import com.kaspercito.domain.mediaservice.handler.MediaPlayerHandler
import com.kaspercito.logger.Logger
import com.kaspercito.media3.R
import com.kaspercito.media3.extension.toCommandButton
import com.kaspercito.media3.utils.CoilBitmapLoader
import com.kaspercito.media3.utils.sizeLimitedForSession
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

@UnstableApi
internal class SimpleMediaService :
    MediaLibraryService(),
    KoinComponent {
    // Session-level player from DI: the ForwardingPlayer wrapped with Cast support in the
    // full build (plain ForwardingPlayer in the FOSS build).
    private val player: Player by inject<Player>(qualifier = named(com.kaspercito.common.Config.MAIN_PLAYER))
    private val coilBitmapLoader: CoilBitmapLoader by inject<CoilBitmapLoader>()

    private var mediaSession: MediaLibrarySession? = null

    private val simpleMediaSessionCallback: MediaLibrarySession.Callback by inject<MediaLibrarySession.Callback>()

    private val simpleMediaServiceHandler: MediaPlayerHandler by inject<MediaPlayerHandler>()

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: SimpleMediaService
            get() = this@SimpleMediaService

        fun setActivitySession(
            context: Context,
            activity: Class<out Activity>,
        ) {
            mediaSession?.setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, activity),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Logger.w("Service", "Simple Media Service Bound")
        return super.onBind(intent) ?: binder
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        Logger.w("Service", "Simple Media Service Created")

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { MEDIA_NOTIFICATION.NOTIFICATION_ID },
                MEDIA_NOTIFICATION.NOTIFICATION_CHANNEL_ID,
                R.string.notification_channel_name,
            ).apply {
                setSmallIcon(R.drawable.mono)
            },
        )

        mediaSession = provideMediaLibrarySession(this, player, simpleMediaSessionCallback).also(::addSession)

        simpleMediaServiceHandler.onUpdateNotification = { list ->
            val commandButtonList = list.map { it.toCommandButton(this) }
            mediaSession?.setMediaButtonPreferences(
                commandButtonList,
            )
        }
    }

    @UnstableApi
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Logger.w("Service", "Simple Media Service Received Action: ${intent?.action}")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    private fun releaseSession() {
        val session = mediaSession ?: return
        mediaSession = null
        try {
            removeSession(session)
        } finally {
            session.release()
        }
    }

    @UnstableApi
    override fun onDestroy() {
        simpleMediaServiceHandler.onUpdateNotification = {}
        try {
            releaseSession()
        } finally {
            super.onDestroy()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Logger.w("Service", "Simple Media Service Trim Memory Level: $level")
        simpleMediaServiceHandler.mayBeSaveRecentSong()
    }

    @UnstableApi
    override fun onTaskRemoved(rootIntent: Intent?) {
        val canContinuePlayback =
            isPlaybackOngoing() &&
                player.playWhenReady &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
        if (simpleMediaServiceHandler.shouldReleaseOnTaskRemoved() || !canContinuePlayback) {
            pauseAllPlayersAndStopSelf()
            releaseSession()
        }
    }

    // Can't inject by Koin because it depend on service
    @UnstableApi
    private fun provideMediaLibrarySession(
        service: MediaLibraryService,
        player: Player,
        callback: MediaLibrarySession.Callback,
    ): MediaLibrarySession =
        MediaLibrarySession
            .Builder(
                service,
                player,
                callback,
            ).setId(this.javaClass.name)
            // Capped at the platform's own artwork limit so the framework never rescales the shared
            // bitmap in setMetadata — see sizeLimitedForSession (#2500). Falls back to the plain
            // loader if the limit cannot be read.
            .setBitmapLoader(sizeLimitedForSession(service, coilBitmapLoader))
            // Tapping the media notification opens whatever the session activity points at, and the
            // only other place that sets it is MainActivity's bind. A service started without that
            // bind — a headset/Bluetooth play, Android Auto, a widget, a restart after the process
            // was killed — would otherwise post a notification that opens nothing. The launcher
            // activity is resolved at runtime, so this module never has to name MainActivity.
            .apply {
                service.packageManager.getLaunchIntentForPackage(service.packageName)?.let { launch ->
                    setSessionActivity(
                        PendingIntent.getActivity(service, 0, launch, PendingIntent.FLAG_IMMUTABLE),
                    )
                }
            }
            .build()
}