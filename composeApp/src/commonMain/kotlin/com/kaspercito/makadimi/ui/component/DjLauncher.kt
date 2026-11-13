package com.kaspercito.makadimi.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaspercito.common.Config.SONG_CLICK
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.repository.SongRepository
import com.kaspercito.domain.utils.toTrack
import com.kaspercito.makadimi.ui.theme.LeafShapeLarge
import com.kaspercito.makadimi.ui.theme.auroraBrush
import com.kaspercito.makadimi.ui.theme.monoFontFamily
import com.kaspercito.makadimi.ui.theme.neonGlow
import com.kaspercito.makadimi.viewModel.SharedViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.dj_active
import makadimi.composeapp.generated.resources.dj_card_subtitle
import makadimi.composeapp.generated.resources.dj_description
import makadimi.composeapp.generated.resources.dj_need_key
import makadimi.composeapp.generated.resources.dj_no_songs
import makadimi.composeapp.generated.resources.dj_start
import makadimi.composeapp.generated.resources.dj_stop
import makadimi.composeapp.generated.resources.dj_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * El orbe del DJ: un círculo con degradado aurora que "respira" y emite ondas mientras el DJ está
 * activo. Se reutiliza en la tarjeta de Inicio, en la hoja del DJ y en el dock de escritorio.
 */
@Composable
fun DjOrb(
    active: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "djOrb")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(tween(if (active) 900 else 2200), RepeatMode.Reverse),
        label = "djBreathe",
    )
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "djWave",
    )
    Box(modifier = modifier.size(size * 1.5f), contentAlignment = Alignment.Center) {
        if (active) {
            // Dos ondas desfasadas que se expanden y se desvanecen.
            listOf(0f, 0.5f).forEach { phase ->
                val p = (wave + phase) % 1f
                Box(
                    Modifier
                        .size(size)
                        .graphicsLayer {
                            scaleX = 1f + p * 0.5f
                            scaleY = 1f + p * 0.5f
                            alpha = (1f - p) * 0.45f
                        }.background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        Box(
            Modifier
                .size(size)
                .scale(breathe)
                .neonGlow(MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.7f else 0.4f), size / 3)
                .clip(CircleShape)
                .background(auroraBrush())
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "DJ",
                color = Color.White,
                fontFamily = monoFontFamily(),
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.36f).sp,
                letterSpacing = 1.sp,
            )
        }
    }
}

/** Tarjeta grande de Inicio: abre la hoja del DJ. */
@Composable
fun DjHeroCard(
    modifier: Modifier = Modifier,
    dataStoreManager: DataStoreManager = koinInject<DataStoreManager>(),
) {
    val aiDj by dataStoreManager.aiDj.collectAsState(DataStoreManager.FALSE)
    val active = aiDj == DataStoreManager.TRUE
    var showSheet by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(LeafShapeLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { showSheet = true }
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DjOrb(active = active, size = 52.dp)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.dj_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(if (active) Res.string.dj_active else Res.string.dj_card_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showSheet) DjSheet(onDismiss = { showSheet = false })
}

/**
 * La hoja del DJ. "Empezar" activa al locutor y arranca una sesión desde una canción tuya
 * (favoritas o más escuchadas); YouTube Music rellena la cola con canciones parecidas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjSheet(
    onDismiss: () -> Unit,
    dataStoreManager: DataStoreManager = koinInject<DataStoreManager>(),
    songRepository: SongRepository = koinInject<SongRepository>(),
    sharedViewModel: SharedViewModel = koinInject<SharedViewModel>(),
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiDj by dataStoreManager.aiDj.collectAsState(DataStoreManager.FALSE)
    val active = aiDj == DataStoreManager.TRUE
    var message by remember { mutableStateOf<String?>(null) }
    val needKeyText = stringResource(Res.string.dj_need_key)
    val noSongsText = stringResource(Res.string.dj_no_songs)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = LeafShapeLarge) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DjOrb(active = active, size = 96.dp)
            Text(
                stringResource(Res.string.dj_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(if (active) Res.string.dj_active else Res.string.dj_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        if (dataStoreManager.aiApiKey.first().isBlank()) {
                            message = needKeyText
                            return@launch
                        }
                        val seed =
                            (
                                songRepository.getLikedSongs().first().ifEmpty {
                                    songRepository.getMostPlayedSongs().first()
                                }
                            ).randomOrNull()
                        if (seed == null) {
                            message = noSongsText
                            return@launch
                        }
                        message = null
                        dataStoreManager.setAiDj(true)
                        sharedViewModel.loadMediaItemFromTrack(seed.toTrack(), SONG_CLICK)
                        onDismiss()
                    }
                },
            ) { Text(stringResource(Res.string.dj_start)) }
            if (active) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            dataStoreManager.setAiDj(false)
                            onDismiss()
                        }
                    },
                ) { Text(stringResource(Res.string.dj_stop)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
