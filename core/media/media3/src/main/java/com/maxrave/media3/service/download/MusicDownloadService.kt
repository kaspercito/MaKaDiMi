package com.maxrave.media3.service.download

import android.app.Notification
import android.content.Context
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.maxrave.common.MERGING_DATA_TYPE
import com.maxrave.media3.R
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import org.koin.android.ext.android.inject

@UnstableApi
internal class MusicDownloadService :
    DownloadService(
        NOTIFICATION_ID,
        1000L,
        CHANNEL_ID,
        R.string.download,
        0,
    ) {
    private val downloadUtil: DownloadHandler by inject<DownloadHandler>()

    override fun getDownloadManager() = (downloadUtil as DownloadUtils).downloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification =
        (downloadUtil as DownloadUtils).downloadNotificationHelper.buildProgressNotification(
            this,
            R.drawable.mono,
            null,
            if (downloads.size == 1) {
                Util.fromUtf8Bytes(downloads[0].request.data)
            } else {
                resources.getQuantityString(R.plurals.n_song, downloads.size, downloads.size)
            },
            downloads,
            notMetRequirements,
        )

    /**
     * Avisa del resultado de las descargas UNA sola vez por tanda.
     *
     * Antes cada canción publicaba su propia notificación ("descarga completada" o "falló") con un id
     * nuevo cada vez: bajar una lista de 50 canciones dejaba 50 avisos apilados en la barra, y como la
     * canción y su video son dos descargas distintas, las que traían video avisaban dos veces. Ahora se
     * van contando y, cuando ya no queda nada activo (`onIdle`), se publica un resumen de completadas y
     * otro de fallidas, con id fijo para que una tanda nueva reemplace al aviso de la anterior en vez de
     * sumarse a él.
     *
     * Los callbacks de [DownloadManager] llegan todos por el mismo hilo (el de la aplicación), así que
     * los contadores no necesitan sincronización.
     */
    class TerminalStateNotificationHelper(
        private val context: Context,
        private val notificationHelper: DownloadNotificationHelper,
    ) : DownloadManager.Listener {
        private var completedCount = 0
        private var lastCompletedTitle: String? = null
        private var failedCount = 0
        private var lastFailedTitle: String? = null

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            // La parte de video de una canción es una descarga aparte; el aviso es por canción, así que
            // solo cuenta la de audio. (Si falla solo el video, el audio sigue sirviendo.)
            if (download.request.id.startsWith(MERGING_DATA_TYPE.VIDEO)) return
            val title = Util.fromUtf8Bytes(download.request.data)
            when (download.state) {
                Download.STATE_COMPLETED -> {
                    completedCount++
                    lastCompletedTitle = title
                }

                Download.STATE_FAILED -> {
                    failedCount++
                    lastFailedTitle = title
                }
            }
        }

        override fun onIdle(downloadManager: DownloadManager) {
            if (completedCount > 0) {
                val text =
                    if (completedCount == 1 && !lastCompletedTitle.isNullOrBlank()) {
                        lastCompletedTitle
                    } else {
                        context.resources.getQuantityString(R.plurals.n_song, completedCount, completedCount)
                    }
                NotificationUtil.setNotification(
                    context,
                    COMPLETED_NOTIFICATION_ID,
                    notificationHelper.buildDownloadCompletedNotification(
                        context,
                        R.drawable.baseline_downloaded,
                        null,
                        text,
                    ),
                )
            }
            if (failedCount > 0) {
                val text =
                    if (failedCount == 1 && !lastFailedTitle.isNullOrBlank()) {
                        lastFailedTitle
                    } else {
                        context.resources.getQuantityString(R.plurals.n_song, failedCount, failedCount)
                    }
                NotificationUtil.setNotification(
                    context,
                    FAILED_NOTIFICATION_ID,
                    notificationHelper.buildDownloadFailedNotification(
                        context,
                        R.drawable.baseline_error_outline_24,
                        null,
                        text,
                    ),
                )
            }
            completedCount = 0
            lastCompletedTitle = null
            failedCount = 0
            lastFailedTitle = null
        }

        override fun onDownloadsPausedChanged(
            downloadManager: DownloadManager,
            downloadsPaused: Boolean,
        ) {
            if (downloadsPaused) {
                downloadManager.resumeDownloads()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "download"
        const val NOTIFICATION_ID = 1000
        const val COMPLETED_NOTIFICATION_ID = NOTIFICATION_ID + 1
        const val FAILED_NOTIFICATION_ID = NOTIFICATION_ID + 2
        const val JOB_ID = 1000
    }
}