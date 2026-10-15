package com.kaspercito.simpmusic.expect.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import com.kaspercito.domain.data.model.metadata.Lyrics
import com.kaspercito.domain.data.model.streams.TimeLine
import com.kaspercito.media3.ui.MediaPlayerView
import com.kaspercito.media3.ui.MediaPlayerViewWithSubtitle
import com.kaspercito.simpmusic.extension.findActivity
import com.kaspercito.simpmusic.extension.getScreenSizeInfo
import com.kaspercito.simpmusic.ui.theme.typo

@Composable
actual fun MediaPlayerView(
    url: String,
    modifier: Modifier,
    cropToBounds: Boolean,
    onFirstFrame: () -> Unit,
) {
    MediaPlayerView(
        modifier = modifier,
        context = LocalContext.current,
        density = LocalDensity.current,
        url = url,
        screenSize = getScreenSizeInfo(),
        cropToBounds = cropToBounds,
        onFirstFrame = onFirstFrame,
    )
}

// Fully qualified: the media3-ui helper shares this function's name and signature.
@Composable
actual fun rememberVideoAspectRatio(playerName: String): Float? = com.kaspercito.media3.ui.rememberVideoAspectRatio(playerName)

@Composable
actual fun MediaPlayerViewWithSubtitle(
    modifier: Modifier,
    playerName: String,
    shouldPip: Boolean,
    shouldShowSubtitle: Boolean,
    shouldScaleDownSubtitle: Boolean,
    isInPipMode: Boolean,
    timelineState: TimeLine,
    lyricsData: Lyrics?,
    translatedLyricsData: Lyrics?,
    mainTextStyle: TextStyle,
    translatedTextStyle: TextStyle,
) {
    MediaPlayerViewWithSubtitle(
        playerName = playerName,
        modifier = modifier,
        shouldShowSubtitle = shouldShowSubtitle,
        shouldPip = shouldPip,
        shouldScaleDownSubtitle = shouldScaleDownSubtitle,
        timelineState = timelineState,
        lyricsData = lyricsData,
        translatedLyricsData = translatedLyricsData,
        context = LocalContext.current,
        activity = LocalActivity.current as? ComponentActivity ?: LocalContext.current.findActivity(),
        isInPipMode = isInPipMode,
        mainTextStyle = typo().bodyLarge,
        translatedTextStyle = typo().bodyMedium,
    )
}