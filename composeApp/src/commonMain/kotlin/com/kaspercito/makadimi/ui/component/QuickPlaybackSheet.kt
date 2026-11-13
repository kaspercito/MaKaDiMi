package com.kaspercito.makadimi.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaspercito.common.QUALITY
import com.kaspercito.common.VIDEO_QUALITY
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.mediaservice.handler.MediaPlayerHandler
import com.kaspercito.makadimi.ui.icon.Headphones
import com.kaspercito.makadimi.ui.icon.Movie
import com.kaspercito.makadimi.ui.icon.MovieOff
import com.kaspercito.makadimi.ui.icon.RestartAlt
import com.kaspercito.makadimi.ui.icon.SimpIcons
import com.kaspercito.makadimi.ui.theme.LeafShape
import com.kaspercito.makadimi.ui.theme.LeafShapeSmall
import com.kaspercito.makadimi.ui.theme.glassPanel
import com.kaspercito.makadimi.ui.theme.monoFontFamily
import com.kaspercito.makadimi.ui.theme.neonBrush
import com.kaspercito.makadimi.ui.theme.typo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.quick_apply_now
import makadimi.composeapp.generated.resources.quick_apply_now_desc
import makadimi.composeapp.generated.resources.quick_audio_quality
import makadimi.composeapp.generated.resources.quick_playback_subtitle
import makadimi.composeapp.generated.resources.quick_playback_title
import makadimi.composeapp.generated.resources.quick_video_mode
import makadimi.composeapp.generated.resources.quick_video_mode_desc
import makadimi.composeapp.generated.resources.quick_video_quality
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Panel rápido del reproductor: modo vídeo, calidad de vídeo y de audio y locutor DJ, todo en un
 * mismo sitio y con efecto inmediato (el botón "Aplicar ahora" recarga la pista actual desde el
 * mismo segundo para que el cambio de calidad se oiga/vea sin esperar a la siguiente canción).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPlaybackSheet(
    onDismiss: () -> Unit,
    dataStoreManager: DataStoreManager = koinInject<DataStoreManager>(),
    handler: MediaPlayerHandler = koinInject<MediaPlayerHandler>(),
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val watchVideo by dataStoreManager.watchVideoInsteadOfPlayingAudio.collectAsState(DataStoreManager.FALSE)
    val videoQuality by dataStoreManager.videoQuality.collectAsState("720p")
    val audioQuality by dataStoreManager.quality.collectAsState(QUALITY.items.first().toString())

    var dirty by remember { mutableStateOf(false) }
    var reloading by remember { mutableStateOf(false) }
    val videoOn = watchVideo == DataStoreManager.TRUE

    val c = rememberSurfaceDarkColors()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color(0xFF05060E).copy(alpha = .72f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(c.container)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
        ) {
            // Asa + hilo de luz aurora
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(50)).background(c.handle))
            }
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(neonBrush()),
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(Res.string.quick_playback_subtitle).uppercase(),
                    style = typo().bodySmall.copy(fontFamily = monoFontFamily(), fontSize = 11.sp, letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.quick_playback_title),
                    style = typo().titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = c.content,
                )
            }

            // --- Modo vídeo ---
            QuickCard {
                QuickSwitchRow(
                    icon = if (videoOn) SimpIcons.Movie else SimpIcons.MovieOff,
                    title = stringResource(Res.string.quick_video_mode),
                    subtitle = stringResource(Res.string.quick_video_mode_desc),
                    checked = videoOn,
                    onChange = {
                        dirty = true
                        scope.launch { dataStoreManager.setWatchVideoInsteadOfPlayingAudio(it) }
                    },
                )
                if (videoOn) {
                    QuickLabel(stringResource(Res.string.quick_video_quality))
                    QuickChips(
                        options = VIDEO_QUALITY.items.map { it.toString() },
                        selected = videoQuality,
                        onSelect = {
                            dirty = true
                            scope.launch { dataStoreManager.setVideoQuality(it) }
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Calidad de audio ---
            QuickCard {
                QuickLabel(stringResource(Res.string.quick_audio_quality), icon = SimpIcons.Headphones)
                val normalized = QUALITY.normalize(audioQuality)
                QuickChips(
                    options = QUALITY.items.map { it.toString() },
                    selected = normalized,
                    display = { it.substringBefore(" - ").substringBefore(" (") },
                    onSelect = {
                        dirty = true
                        scope.launch { dataStoreManager.setQuality(it) }
                    },
                )
            }


            // --- Aplicar ahora ---
            if (dirty) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .clip(LeafShape)
                            .background(neonBrush())
                            .clickable(enabled = !reloading) {
                                reloading = true
                                scope.launch {
                                    reloadCurrentTrack(handler)
                                    dirty = false
                                    reloading = false
                                    sheetState.hide()
                                    onDismiss()
                                }
                            }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(SimpIcons.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(Res.string.quick_apply_now),
                            style = typo().labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = stringResource(Res.string.quick_apply_now_desc),
                            style = typo().bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}

/** Recarga la pista en curso (se vuelve a resolver el stream con los ajustes nuevos) sin perder el punto. */
internal suspend fun reloadCurrentTrack(handler: MediaPlayerHandler) {
    val player = handler.player
    val index = player.currentMediaItemIndex
    val item = player.getMediaItemAt(index) ?: return
    val position = player.currentPosition
    player.replaceMediaItem(index, item)
    // El reproductor carga la pista desde 0; se espera a que esté lista y se vuelve al punto anterior.
    if (position > 1_500L) {
        var waited = 0
        while (waited < 6_000) {
            delay(250)
            waited += 250
            if (player.isPlaying && waited >= 750) break
        }
        player.seekTo(position)
    }
}

@Composable
private fun QuickCard(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .glassPanel(LeafShape, alpha = 0.55f)
                .padding(vertical = 6.dp),
    ) { content() }
}

@Composable
private fun QuickLabel(
    text: String,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(),
            style = typo().bodySmall.copy(fontFamily = monoFontFamily(), fontSize = 11.sp, letterSpacing = 1.5.sp),
            color = rememberSurfaceDarkColors().subtitle,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickChips(
    options: List<String>,
    selected: String?,
    display: (String) -> String = { it },
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                label = "chipBg",
            )
            Box(
                modifier =
                    Modifier
                        .clip(LeafShapeSmall)
                        .background(bg)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(
                    text = display(option),
                    style = typo().labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else rememberSurfaceDarkColors().content,
                )
            }
        }
    }
}

@Composable
private fun QuickSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = rememberSurfaceDarkColors()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(LeafShapeSmall)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (checked) 0.22f else 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (checked) MaterialTheme.colorScheme.primary else c.content, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = typo().labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.content)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = typo().bodySmall, color = c.subtitle)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = c.subtitle,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
        )
    }
}
