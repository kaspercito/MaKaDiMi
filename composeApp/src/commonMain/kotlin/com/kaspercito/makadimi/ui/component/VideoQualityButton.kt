package com.kaspercito.makadimi.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaspercito.common.VIDEO_QUALITY
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.mediaservice.handler.MediaPlayerHandler
import com.kaspercito.makadimi.ui.icon.Check
import com.kaspercito.makadimi.ui.icon.SimpIcons
import com.kaspercito.makadimi.ui.icon.Tune
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.quick_video_quality
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Mientras el menú de calidad está abierto, los controles que flotan sobre el video no deben
 * esconderse solos (su temporizador de 3 s cerraría el menú justo cuando se está eligiendo).
 * Los reproductores de Now Playing y el de pantalla completa miran este valor.
 */
object VideoMenuState {
    var open by mutableStateOf(false)
}

/**
 * El cambio de calidad recarga la pista, y eso no debe cancelarse si los controles se ocultan
 * (y con ellos este composable) a mitad de la recarga: por eso vive en su propio ámbito.
 */
private val qualityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * Botón de ajustes del video: abre un menú con las calidades disponibles (1080p / 720p / 360p).
 * Al elegir una se guarda y la canción actual se vuelve a cargar desde el mismo segundo para que
 * el cambio se vea ya, sin esperar a la siguiente.
 */
@Composable
fun VideoQualityButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    iconSize: Dp = 24.dp,
    dataStoreManager: DataStoreManager = koinInject<DataStoreManager>(),
    handler: MediaPlayerHandler = koinInject<MediaPlayerHandler>(),
) {
    val saved by dataStoreManager.videoQuality.collectAsState(VIDEO_QUALITY.items[1].toString())
    var expanded by remember { mutableStateOf(false) }

    // Si el botón desaparece con el menú abierto, los controles vuelven a poder esconderse.
    DisposableEffect(Unit) { onDispose { VideoMenuState.open = false } }

    Box(modifier) {
        IconButton(
            onClick = {
                expanded = true
                VideoMenuState.open = true
            },
        ) {
            Icon(
                imageVector = SimpIcons.Tune,
                contentDescription = stringResource(Res.string.quick_video_quality),
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                VideoMenuState.open = false
            },
        ) {
            Text(
                text = stringResource(Res.string.quick_video_quality),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            VIDEO_QUALITY.items.forEach { option ->
                val label = option.toString()
                val selected = label == saved
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = SimpIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Spacer(Modifier.size(20.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        VideoMenuState.open = false
                        if (!selected) {
                            qualityScope.launch {
                                dataStoreManager.setVideoQuality(label)
                                reloadCurrentTrack(handler)
                            }
                        }
                    },
                )
            }
        }
    }
}
