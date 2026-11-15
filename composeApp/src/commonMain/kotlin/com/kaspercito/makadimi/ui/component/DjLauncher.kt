package com.kaspercito.makadimi.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
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
import com.kaspercito.domain.data.entities.SongEntity
import com.kaspercito.domain.dj.DjSession
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.domain.repository.SongRepository
import com.kaspercito.domain.utils.toTrack
import com.kaspercito.data.dj.DjQueueBuilder
import com.kaspercito.makadimi.ui.theme.LeafShapeLarge
import com.kaspercito.makadimi.ui.theme.LeafShapeSmall
import com.kaspercito.makadimi.ui.theme.neonBorder
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kaspercito.makadimi.ui.theme.auroraBrush
import com.kaspercito.makadimi.ui.theme.monoFontFamily
import com.kaspercito.makadimi.ui.theme.neonGlow
import com.kaspercito.makadimi.viewModel.SharedViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.dj_active
import makadimi.composeapp.generated.resources.dj_cta
import makadimi.composeapp.generated.resources.dj_eyebrow_off
import makadimi.composeapp.generated.resources.dj_eyebrow_on
import makadimi.composeapp.generated.resources.dj_feature_fresh
import makadimi.composeapp.generated.resources.dj_feature_taste
import makadimi.composeapp.generated.resources.dj_feature_voice
import makadimi.composeapp.generated.resources.dj_preparing
import makadimi.composeapp.generated.resources.dj_restart
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
    // Cuánto espacio extra se reserva alrededor para las ondas: 1.5 es el original (Inicio y hoja);
    // el dock usa menos para no estirar la barra.
    halo: Float = 1.5f,
    onClick: (() -> Unit)? = null,
) {
    // El orbe vive en la barra inferior, visible en TODAS las pantallas: si respirara siempre, la app
    // redibujaría un brillo a 60 fps de forma permanente (y el celular se calienta). Quieto cuando el
    // DJ está apagado; solo se anima mientras está en el aire.
    val transition = rememberInfiniteTransition(label = "djOrb")
    val idle = remember { mutableStateOf(1f) }
    val breathe by if (active) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "djBreathe",
        )
    } else {
        idle
    }
    val wave by if (active) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
            label = "djWave",
        )
    } else {
        idle
    }
    Box(modifier = modifier.size(size * halo), contentAlignment = Alignment.Center) {
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

/** Barras de ecualizador: bailan mientras el DJ está en el aire y se quedan quietas y bajas si no. */
@Composable
private fun DjEqualizer(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "djEq")
    val periods = remember { listOf(620, 810, 540, 730, 900, 660) }
    val still = remember { mutableStateOf(0.2f) }
    // Si no está en el aire no se crea ninguna animación: las barras se quedan quietas y bajas.
    val bars =
        periods.mapIndexed { i, ms ->
            if (active) {
                transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            tween(ms, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse,
                            initialStartOffset = androidx.compose.animation.core.StartOffset(i * 90),
                        ),
                    label = "djEqBar$i",
                )
            } else {
                still
            }
        }
    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        // Se escala en la capa gráfica: no relayout por cuadro.
                        scaleY = if (active) bar.value else 0.2f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }.clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (active) 0.95f else 0.45f)),
            )
        }
    }
}

/** Etiqueta pequeña tipo HUD: "● EN EL AIRE" / "IA · TU RADIO PERSONAL". */
@Composable
private fun DjEyebrow(active: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (active) {
            Box(Modifier.size(7.dp).background(Color(0xFFFF4D6D), CircleShape))
        }
        Text(
            stringResource(if (active) Res.string.dj_eyebrow_on else Res.string.dj_eyebrow_off),
            color = if (active) Color(0xFFFF4D6D) else cs.primary,
            fontFamily = monoFontFamily(),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.8.sp,
        )
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
    val cs = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .neonGlow(cs.primary.copy(alpha = if (active) 0.22f else 0.12f), 22.dp)
                .clip(LeafShapeLarge)
                .background(
                    Brush.linearGradient(
                        listOf(
                            cs.primary.copy(alpha = 0.34f),
                            cs.secondary.copy(alpha = 0.18f),
                            cs.surfaceContainerHigh,
                        ),
                    ),
                ).neonBorder(LeafShapeLarge, alpha = 0.75f)
                .clickable { showSheet = true },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DjOrb(active = active, size = 58.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                DjEyebrow(active)
                Text(
                    stringResource(Res.string.dj_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(if (active) Res.string.dj_active else Res.string.dj_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DjEqualizer(active = active)
                Text(
                    stringResource(if (active) Res.string.dj_restart else Res.string.dj_cta),
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(auroraBrush())
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    color = Color.White,
                    fontFamily = monoFontFamily(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                )
            }
        }
    }
    if (showSheet) DjSheet(onDismiss = { showSheet = false })
}

/** Una línea de la lista "qué hace el DJ": punto de color + texto. */
@Composable
private fun DjFeatureRow(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(LeafShapeSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(auroraBrush()))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * La hoja del DJ. "Empezar" activa al locutor y arranca una sesión desde una canción tuya
 * (favoritas o más escuchadas). La cola se arma con TUS gustos: canciones tuyas intercaladas con
 * las que la IA propone del mismo estilo; ya no se usa la radio relacionada de YouTube Music, que
 * se iba derivando a otros géneros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjSheet(
    onDismiss: () -> Unit,
    dataStoreManager: DataStoreManager = koinInject<DataStoreManager>(),
    songRepository: SongRepository = koinInject<SongRepository>(),
    sharedViewModel: SharedViewModel = koinInject<SharedViewModel>(),
    djQueueBuilder: DjQueueBuilder = koinInject<DjQueueBuilder>(),
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiDj by dataStoreManager.aiDj.collectAsState(DataStoreManager.FALSE)
    val active = aiDj == DataStoreManager.TRUE
    var message by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    val needKeyText = stringResource(Res.string.dj_need_key)
    val noSongsText = stringResource(Res.string.dj_no_songs)
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = LeafShapeLarge) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DjOrb(active = active || starting, size = 96.dp)
            DjEyebrow(active)
            Text(
                stringResource(Res.string.dj_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(if (active) Res.string.dj_active else Res.string.dj_description),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            DjEqualizer(active = active || starting, modifier = Modifier.height(32.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DjFeatureRow(stringResource(Res.string.dj_feature_taste))
                DjFeatureRow(stringResource(Res.string.dj_feature_voice))
                DjFeatureRow(stringResource(Res.string.dj_feature_fresh))
            }
            message?.let {
                Text(it, color = cs.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !starting,
                onClick = {
                    scope.launch {
                        starting = true
                        try {
                            if (dataStoreManager.aiApiKey.first().isBlank()) {
                                message = needKeyText
                                return@launch
                            }
                            val liked = songRepository.getLikedSongs().first()
                            val played = songRepository.getMostPlayedSongs().first()
                            val seed = liked.ifEmpty { played }.randomOrNull()
                            if (seed == null) {
                                message = noSongsText
                                return@launch
                            }
                            message = null
                            // Tus gustos: el DJ los usa para que sus comentarios suenen a ti.
                            DjSession.tasteProfile = buildTasteProfile(liked, played)
                            // Tus canciones (favoritas y más escuchadas) ancladas a la cola.
                            val own = (liked + played).distinctBy { it.videoId }
                            val suggestions =
                                djQueueBuilder.suggestions(
                                    taste = buildSuggestionTaste(liked, played),
                                    seed = seed.displayName(),
                                    avoidTitles = own.shuffled().take(40).map { it.displayName() },
                                    avoidIds = own.map { it.videoId }.toSet(),
                                )
                            // Si ya estaba encendido, se apaga un instante para que vuelva a "encenderse" y saludar.
                            if (active) {
                                dataStoreManager.setAiDj(false)
                                kotlinx.coroutines.delay(400)
                            }
                            dataStoreManager.setAiDj(true)
                            kotlinx.coroutines.delay(200)
                            sharedViewModel.startDjSession(
                                seed = seed.toTrack(),
                                own = own.shuffled().map { it.toTrack() },
                                suggestions = suggestions,
                            )
                            onDismiss()
                        } finally {
                            starting = false
                        }
                    }
                },
            ) {
                if (starting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(Res.string.dj_preparing))
                } else {
                    Text(stringResource(Res.string.dj_start), fontWeight = FontWeight.Bold)
                }
            }
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

private fun SongEntity.displayName(): String {
    val artist = artistName?.firstOrNull { it.isNotBlank() }
    return if (artist != null) "$artist - $title" else title
}

/**
 * Gustos para el modelo que sugiere canciones: artistas más repetidos y una muestra de
 * favoritas con su artista, para que se mantenga en el mismo estilo.
 */
private fun buildSuggestionTaste(
    liked: List<SongEntity>,
    played: List<SongEntity>,
): String {
    val topArtists =
        (liked + played)
            .flatMap { it.artistName.orEmpty() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(12)
            .map { it.key }
    val sample = liked.ifEmpty { played }.shuffled().take(18).map { it.displayName() }
    return buildString {
        if (topArtists.isNotEmpty()) append("Artistas que más escucha: ").append(topArtists.joinToString(", ")).append(". ")
        if (sample.isNotEmpty()) append("Canciones que le gustan: ").append(sample.joinToString("; ")).append('.')
    }.trim().ifEmpty { "sin datos" }
}

/**
 * Resumen de los gustos del oyente para el locutor: sus artistas más repetidos y algunas de sus
 * canciones favoritas (o, si aún no tiene favoritas, las más escuchadas).
 */
private fun buildTasteProfile(
    liked: List<SongEntity>,
    played: List<SongEntity>,
): String? {
    val topArtists =
        (liked + played)
            .flatMap { it.artistName.orEmpty() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }
    val favoriteSongs =
        liked
            .ifEmpty { played }
            .shuffled()
            .take(5)
            .map { it.title }
    val text =
        buildString {
            if (topArtists.isNotEmpty()) append("artistas que más escucha: ").append(topArtists.joinToString(", ")).append(". ")
            if (favoriteSongs.isNotEmpty()) append("canciones que le gustan: ").append(favoriteSongs.joinToString(", ")).append('.')
        }.trim()
    return text.ifEmpty { null }
}
