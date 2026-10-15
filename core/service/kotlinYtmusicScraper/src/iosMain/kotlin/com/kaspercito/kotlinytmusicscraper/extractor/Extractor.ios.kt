package com.kaspercito.kotlinytmusicscraper.extractor

import com.kaspercito.kotlinytmusicscraper.models.SongItem
import com.kaspercito.kotlinytmusicscraper.models.response.DownloadProgress

actual class Extractor {
    actual fun init() {
    }

    actual fun logIn(cookie: String?) {}

    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> = emptyList()

    actual fun liveHlsUrl(videoId: String): String? = null

    actual fun mergeAudioVideoDownload(filePath: String): DownloadProgress = DownloadProgress.failed("Not supported on iOS")

    actual fun saveAudioWithThumbnail(
        filePath: String,
        track: SongItem,
    ): DownloadProgress = DownloadProgress.failed("Not supported on iOS")
}