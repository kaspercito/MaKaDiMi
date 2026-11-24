package com.my.kizzy

import com.kaspercito.domain.data.entities.SongEntity
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DiscordRPC(
    token: String,
) : KizzyRPC(token) {
    @OptIn(ExperimentalTime::class)
    suspend fun updateSong(
        currentPlaybackTimeMillis: Long,
        durationMillis: Long,
        playbackSpeed: Float = 1.0f,
        song: SongEntity,
    ) = runCatching {
        val currentTime = Clock.System.now().toEpochMilliseconds()

        val adjustedPlaybackTime = (currentPlaybackTimeMillis / playbackSpeed).toLong()
        val calculatedStartTime = currentTime - adjustedPlaybackTime

        val remainingDuration = durationMillis - currentPlaybackTimeMillis
        val adjustedRemainingDuration = (remainingDuration / playbackSpeed).toLong()

        // Enlace al tema en YouTube Music: al tocar el título en Discord se abre la canción.
        // Reemplaza al antiguo botón "Visit MaKaDiMi", que ya no se muestra.
        val songUrl = "https://music.youtube.com/watch?v=${song.videoId}"
        val artists = song.artistName?.filter { it.isNotBlank() }?.joinToString(", ")

        val delivered = setActivity(
            name = APP_NAME,
            details = song.title,
            detailsUrl = songUrl,
            state = artists,
            largeImage = song.thumbnails?.let { RpcImage.ExternalImage(it) },
            smallImage = RpcImage.ExternalImage(APP_ICON),
            // Al pasar el cursor sobre la carátula se ve el álbum (o el título si no hay álbum).
            largeText = song.albumName?.takeIf { it.isNotBlank() } ?: song.title,
            smallText = "Escuchando en $APP_NAME",
            // Sin botones: la tarjeta queda limpia, solo carátula, título, artista y la barra de tiempo.
            buttons = null,
            type = Type.LISTENING,
            // En la lista de miembros de Discord se lee "Escuchando <artista>" en vez de solo "MaKaDiMi".
            statusDisplayType = StatusDisplayType.STATE,
            since = currentTime,
            startTime = calculatedStartTime,
            endTime = currentTime + adjustedRemainingDuration,
            applicationId = APPLICATION_ID,
        )
        // Surface a dropped update as a failure so the caller retries instead of silently showing nothing.
        check(delivered) { "Discord gateway not ready, presence not delivered" }
    }

    companion object {
        private const val APPLICATION_ID = "1530805316894331113"
        private const val APP_NAME: String = "MaKaDiMi"
        // Use a versioned URL so Discord's image proxy gets a new cache key when the artwork changes.
        private const val APP_ICON: String =
            "https://raw.githubusercontent.com/kaspercito/MaKaDiMi/112995e54930555e3dad509e914fb1d515f5c916/composeApp/src/commonMain/composeResources/drawable/app_icon.png"
    }
}
