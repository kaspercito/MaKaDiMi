package com.kaspercito.media3.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlaybackNetworkPolicyTest {
    @Test
    fun cachedAudioUsesDiskInBothModes() {
        assertEquals(PlaybackSource.CACHE, playbackSource(isCached = true, offline = true))
        assertEquals(PlaybackSource.CACHE, playbackSource(isCached = true, offline = false))
    }

    @Test
    fun uncachedAudioFailsImmediatelyWhileOffline() {
        val error = assertThrows(OfflinePlaybackException::class.java) {
            playbackSource(isCached = false, offline = true)
        }
        assertTrue(IOException(error).isOfflinePlaybackFailure())
        assertNull(playbackRetryDelay(offline = true, retryable = true, networkFailure = true, failedAttempts = 0, maxRetries = 3))
    }

    @Test
    fun transientConnectionFailureBacksOffAndStopsAfterTheBudget() {
        assertEquals(1_000L, playbackRetryDelay(false, true, true, 0, 3))
        assertEquals(2_000L, playbackRetryDelay(false, true, true, 1, 3))
        assertEquals(3_000L, playbackRetryDelay(false, true, true, 2, 3))
        assertNull(playbackRetryDelay(false, true, true, 3, 3))
        assertNull(playbackRetryDelay(false, false, false, 0, 3))
    }

    @Test
    fun uncachedAudioCanUseTheNetworkAgainAfterOfflineModeIsDisabled() {
        assertThrows(OfflinePlaybackException::class.java) {
            playbackSource(isCached = false, offline = true)
        }
        assertEquals(PlaybackSource.NETWORK, playbackSource(isCached = false, offline = false))
        assertEquals(1_000L, playbackRetryDelay(false, true, true, 0, 3))
    }
}
