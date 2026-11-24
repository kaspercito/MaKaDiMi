package com.kaspercito.makadimi.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaspercito.makadimi.ui.icon.Close
import com.kaspercito.makadimi.ui.icon.Search
import com.kaspercito.makadimi.ui.icon.SimpIcons
import com.kaspercito.makadimi.ui.theme.LeafShape
import com.kaspercito.makadimi.ui.theme.LeafShapeSmall
import com.kaspercito.makadimi.extension.getScreenSizeInfo
import com.kaspercito.makadimi.ui.theme.neonBrush
import com.kaspercito.makadimi.ui.theme.typo
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.clear
import makadimi.composeapp.generated.resources.hub_categories
import makadimi.composeapp.generated.resources.hub_no_results
import makadimi.composeapp.generated.resources.hub_search
import org.jetbrains.compose.resources.stringResource

/** Una categoría del hub: [key] es la clave del item de la lista al que salta. */
data class SettingsHubEntry(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val keywords: List<String> = emptyList(),
)

/**
 * Cabecera "centro de mando" de Ajustes: buscador y cuadrícula de categorías. Tocar una categoría
 * lleva directo a su sección, así no hay que recorrer toda la lista para encontrar algo.
 */
@Composable
fun SettingsHub(
    entries: List<SettingsHubEntry>,
    onJump: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, entries) {
        val terms = query.split(' ').filter { it.isNotBlank() }
        entries.filter { entry ->
            terms.all { term ->
                entry.title.contains(term, ignoreCase = true) ||
                    entry.keywords.any { it.contains(term, ignoreCase = true) }
            }
        }
    }
    val cs = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val focusManager = LocalFocusManager.current
    val screenWidth = getScreenSizeInfo().wDP
    val iconGradient = remember(cs.primary, cs.secondary) {
        Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.24f), cs.secondary.copy(alpha = 0.12f)))
    }

    Column(modifier.fillMaxWidth().padding(top = 20.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(Res.string.hub_search)) },
            leadingIcon = { Icon(SimpIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(SimpIcons.Close, contentDescription = stringResource(Res.string.clear))
                    }
                }
            },
            shape = LeafShape,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(Res.string.hub_categories),
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
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

        val columns = when {
            screenWidth < 410 || fontScale >= 1.3f -> 2
            screenWidth >= 680 -> 4
            else -> 3
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            filtered.chunked(columns).forEach { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowEntries.forEach { entry ->
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(LeafShapeSmall)
                                    .background(cs.surfaceContainerLow)
                                    .border(1.dp, cs.outlineVariant.copy(alpha = 0.65f), LeafShapeSmall)
                                    .clickable(role = Role.Button) {
                                        focusManager.clearFocus()
                                        onJump(entry.key)
                                    }
                                    .padding(top = 14.dp, bottom = 12.dp, start = 6.dp, end = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(LeafShapeSmall)
                                        .background(iconGradient),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(entry.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = entry.title,
                                style = typo().labelSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 14.sp),
                                color = cs.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = if (fontScale >= 1.3f) 3 else 2,
                                softWrap = true,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                    // Relleno para que la última fila no estire sus tarjetas.
                    repeat(columns - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // Línea de luz bajo el hub
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(neonBrush()))
    }
}
