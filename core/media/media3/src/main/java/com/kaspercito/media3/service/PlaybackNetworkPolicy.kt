package com.kaspercito.media3.service

import java.io.IOException

internal class OfflinePlaybackException(message: String) : IOException(message)

internal fun Throwable.isOfflinePlaybackFailure(): Boolean =
    generateSequence(this) { it.cause }.take(8).any { it is OfflinePlaybackException }

internal enum class PlaybackSource {
    CACHE,
    NETWORK,
}

internal fun playbackSource(isCached: Boolean, offline: Boolean): PlaybackSource =
    when {
        isCached -> PlaybackSource.CACHE
        offline -> throw OfflinePlaybackException("Track is unavailable offline")
        else -> PlaybackSource.NETWORK
    }

internal fun playbackRetryDelay(
    offline: Boolean,
    retryable: Boolean,
    networkFailure: Boolean,
    failedAttempts: Int,
    maxRetries: Int,
): Long? {
    if (offline || !retryable || failedAttempts >= maxRetries) return null
    return if (networkFailure) 1_000L * (failedAttempts + 1) else 0L
}
