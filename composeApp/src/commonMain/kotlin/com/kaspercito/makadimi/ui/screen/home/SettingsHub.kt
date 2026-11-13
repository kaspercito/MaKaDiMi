package com.kaspercito.makadimi.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaspercito.makadimi.ui.icon.Close
import com.kaspercito.makadimi.ui.icon.Search
import com.kaspercito.makadimi.ui.icon.SimpIcons
import com.kaspercito.makadimi.ui.theme.LeafShape
import com.kaspercito.makadimi.ui.theme.LeafShapeSmall
import com.kaspercito.makadimi.ui.theme.glassPanel
import com.kaspercito.makadimi.ui.theme.monoFontFamily
import com.kaspercito.makadimi.ui.theme.neonBrush
import com.kaspercito.makadimi.ui.theme.typo
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.hub_categories
import makadimi.composeapp.generated.resources.hub_no_results
import makadimi.composeapp.generated.resources.hub_search
import org.jetbrains.compose.resources.stringResource

/** Una categoría del hub: [key] es la clave del item de la lista al que salta. */
data class SettingsHubEntry(
    val key: String,
    val title: String,
    val icon: ImageVector,
)

/**
 * Cabecera "centro de mando" de Ajustes: buscador y cuadrícula de categorías. Tocar una categoría
 * lleva directo a su sección, así no hay que recorrer toda la lista para encontrar algo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsHub(
    entries: List<SettingsHubEntry>,
    onJump: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, entries) { entries.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) } }
    val cs = MaterialTheme.colorScheme

    Column(modifier.fillMaxWidth().padding(top = 20.dp)) {
        // Buscador
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(LeafShape)
                    .glassPanel(LeafShape, alpha = 0.55f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SimpIcons.Search, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.hub_search),
                        style = typo().bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = typo().bodyMedium.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    SimpIcons.Close,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).clickable { query = "" },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(Res.string.hub_categories),
            style = typo().bodySmall.copy(fontFamily = monoFontFamily(), fontSize = 11.sp, letterSpacing = 2.sp),
            color = cs.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )

        if (filtered.isEmpty()) {
            Text(
                text = stringResource(Res.string.hub_no_results),
                style = typo().bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 3,
        ) {
            filtered.forEach { entry ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(LeafShapeSmall)
                            .glassPanel(LeafShapeSmall, alpha = 0.5f)
                            .clickable { onJump(entry.key) }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(LeafShapeSmall)
                                .background(Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.28f), cs.secondary.copy(alpha = 0.18f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(entry.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = entry.title,
                        style = typo().labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            // Relleno para que la última fila no estire sus tarjetas.
            repeat((3 - filtered.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
        }
        // Línea de luz bajo el hub
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(neonBrush()))
    }
}
