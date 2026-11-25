package com.kaspercito.makadimi.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.kaspercito.domain.manager.DataStoreManager
import kotlin.math.max
import org.jetbrains.compose.resources.Font
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.space_mono_regular

/**
 * Lenguaje visual MaKaDiMi "Neón": base obsidiana casi negra con brillos verde neón / cian / lima,
 * paneles de cristal translúcido con borde de luz, geometría "hoja" (esquinas asimétricas) y
 * etiquetas HUD en tipografía mono. Todo sale del ColorScheme, así que respeta el color elegido
 * por el usuario y el modo claro/oscuro.
 */

/** Geometría de marca: esquinas opuestas grandes y pequeñas, como una hoja / pastilla cortada. */
val LeafShape = RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
val LeafShapeLarge = RoundedCornerShape(topStart = 30.dp, topEnd = 8.dp, bottomEnd = 30.dp, bottomStart = 8.dp)
val LeafShapeSmall = RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 4.dp)

/** Degradado de marca: del acento principal al terciario (verde neón -> lima). */
@Composable
fun neonBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.horizontalGradient(listOf(cs.primary, cs.tertiary))
}

/** Degradado frío: verde neón -> cian. Para indicadores, progreso y botones de acción. */
@Composable
fun auroraBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(cs.primary, cs.secondary))
}

/** Tipografía mono para etiquetas HUD pequeñas (eyebrows, cabeceras de grupo, métricas). */
@Composable
fun monoFontFamily(): FontFamily = FontFamily(Font(Res.font.space_mono_regular))

/** Borde de luz: brillo en la esquina superior, se apaga en el medio y vuelve en el opuesto. */
@Composable
fun Modifier.neonBorder(
    shape: Shape,
    width: Dp = 1.dp,
    alpha: Float = 0.5f,
): Modifier {
    val cs = MaterialTheme.colorScheme
    val brush =
        Brush.linearGradient(
            listOf(
                cs.primary.copy(alpha = alpha),
                cs.outlineVariant.copy(alpha = 0.35f),
                cs.secondary.copy(alpha = alpha * 0.7f),
            ),
        )
    return this.border(width, brush, shape)
}

/** Brillo difuso detrás del elemento (se dibuja debajo, sin cambiar su tamaño ni su layout). */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 24.dp,
): Modifier =
    drawBehind {
        val r = radius.toPx() + size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = r),
            radius = r,
            center = center,
        )
    }

/**
 * Panel de cristal: relleno translúcido con velo de luz en la parte alta y borde de luz fino.
 * Es la superficie estándar de tarjetas, grupos de ajustes, menús y diálogos de la app.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape = LeafShape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    alpha: Float = 0.62f,
    borderAlpha: Float = 0.55f,
): Modifier {
    val cs = MaterialTheme.colorScheme
    val sheen = androidx.compose.runtime.remember(tint, alpha, cs.primary, cs.tertiary) {
        Brush.linearGradient(
            listOf(
                androidx.compose.ui.graphics.lerp(tint, cs.primary, 0.16f).copy(alpha = alpha),
                tint.copy(alpha = (alpha + 0.18f).coerceAtMost(1f)),
                androidx.compose.ui.graphics.lerp(tint, cs.tertiary, 0.08f).copy(alpha = alpha),
            ),
        )
    }
    return this
        .background(sheen, shape)
        .neonBorder(shape, alpha = borderAlpha * 0.6f)
}

/**
 * Fondo ambiental de toda la app, configurable desde Ajustes > Fondo. Se dibuja una sola vez detrás
 * del Scaffold y es ESTÁTICO a propósito (sin animaciones infinitas): un fondo que se redibuja a
 * 60 fps en todas las pantallas calienta el celular. Los pinceles se calculan una vez por tamaño
 * ([drawWithCache]) y no se vuelven a crear en cada cuadro.
 *
 * @param style uno de `DataStoreManager.BACKGROUND_STYLE_*`.
 * @param glowColor color del brillo; null = el del tema (primario / terciario / secundario).
 * @param intensity 0..100 (60 = aspecto original). En "Tu imagen" es cuánto se ve la foto.
 * @param imageUri imagen elegida (solo para el estilo imagen); vacío = cae a Aurora.
 */
@Composable
fun Modifier.appBackground(
    style: String,
    glowColor: Color?,
    intensity: Int,
    imageUri: String,
    base: Color = MaterialTheme.colorScheme.background,
): Modifier {
    val cs = MaterialTheme.colorScheme
    val isDark = base.luminance() < 0.5f
    val strength = intensity.coerceIn(0, 100)
    val k = (if (isDark) 1f else 0.55f) * (strength / 60f)
    val g1 = glowColor ?: cs.primary
    val g2 = glowColor ?: cs.tertiary
    val g3 = glowColor ?: cs.secondary
    val effective =
        if (style == DataStoreManager.BACKGROUND_STYLE_IMAGE && imageUri.isBlank()) {
            DataStoreManager.BACKGROUND_STYLE_AURORA
        } else {
            style
        }
    val painter: Painter? =
        if (effective == DataStoreManager.BACKGROUND_STYLE_IMAGE) {
            // Tamaño acotado: decodificar una foto de 12 MP completa gasta memoria y calienta.
            rememberAsyncImagePainter(
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(imageUri)
                    .size(1080, 2400)
                    .build(),
            )
        } else {
            null
        }
    val imageDim = (0.88f - 0.7f * strength / 100f).coerceIn(0.1f, 0.9f)

    return this.drawWithCache {
        val w = size.width
        val h = size.height
        val cell = 36.dp.toPx()

        fun blob(color: Color, alpha: Float, cx: Float, cy: Float, radius: Float): Triple<Brush, Float, Offset> {
            val center = Offset(cx, cy)
            return Triple(
                Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent), center, radius),
                radius,
                center,
            )
        }

        val blobs =
            when (effective) {
                DataStoreManager.BACKGROUND_STYLE_GRID ->
                    listOf(
                        blob(g1, 0.22f * k, w * 0.5f, -h * 0.04f, w * 1.15f),
                        blob(g3, 0.09f * k, w * 0.95f, h, w * 0.85f),
                    )
                DataStoreManager.BACKGROUND_STYLE_DOTS ->
                    listOf(
                        blob(g1, 0.16f * k, w * 0.05f, h * 0.02f, w * 0.95f),
                        blob(g3, 0.07f * k, w * 0.9f, h * 0.95f, w * 0.8f),
                    )
                DataStoreManager.BACKGROUND_STYLE_BLACK -> emptyList()
                DataStoreManager.BACKGROUND_STYLE_IMAGE -> emptyList()
                else ->
                    listOf(
                        blob(g1, 0.26f * k, w * 0.05f, h * 0.02f, w * 0.95f),
                        blob(g2, 0.15f * k, w * 1.0f, h * 0.38f, w * 0.8f),
                        blob(g3, 0.11f * k, w * 0.1f, h * 1.0f, w * 0.9f),
                    )
            }

        val lineAlpha = (0.075f * k).coerceIn(0f, 0.5f)
        val verticalLine = Brush.verticalGradient(listOf(g1.copy(alpha = lineAlpha), Color.Transparent), 0f, h * 0.85f)
        val dotBrush =
            Brush.verticalGradient(
                listOf(g1.copy(alpha = (0.30f * k).coerceIn(0f, 0.8f)), g1.copy(alpha = (0.05f * k).coerceIn(0f, 0.3f))),
                0f,
                h,
            )
        val dots =
            if (effective == DataStoreManager.BACKGROUND_STYLE_DOTS) {
                val step = 22.dp.toPx()
                buildList {
                    var y = step / 2f
                    while (y < h) {
                        var x = step / 2f
                        while (x < w) {
                            add(Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                }
            } else {
                emptyList()
            }

        onDrawBehind {
            when (effective) {
                DataStoreManager.BACKGROUND_STYLE_BLACK -> drawRect(if (isDark) Color.Black else base)
                DataStoreManager.BACKGROUND_STYLE_IMAGE -> {
                    drawRect(base)
                    val p = painter
                    if (p != null) {
                        val iw = p.intrinsicSize.width
                        val ih = p.intrinsicSize.height
                        if (iw.isFinite() && ih.isFinite() && iw > 0f && ih > 0f) {
                            val scale = max(w / iw, h / ih)
                            val dw = iw * scale
                            val dh = ih * scale
                            translate((w - dw) / 2f, (h - dh) / 2f) {
                                with(p) { draw(Size(dw, dh)) }
                            }
                        }
                    }
                    // Velo para que el texto siga leyéndose, y más oscuro abajo (barra y reproductor).
                    drawRect(base.copy(alpha = imageDim))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = 0.55f))))
                }
                else -> {
                    drawRect(base)
                    blobs.forEach { (brush, radius, center) -> drawCircle(brush, radius, center) }
                    if (effective == DataStoreManager.BACKGROUND_STYLE_GRID) {
                        var i = 0
                        var x = 0f
                        while (x <= w) {
                            val major = i % 4 == 0
                            drawLine(
                                brush = verticalLine,
                                start = Offset(x, 0f),
                                end = Offset(x, h * 0.85f),
                                strokeWidth = if (major) 1.6f else 1f,
                                alpha = if (major) 1f else 0.6f,
                            )
                            x += cell
                            i++
                        }
                        var j = 0
                        var y = 0f
                        while (y <= h * 0.85f) {
                            val fade = 1f - y / (h * 0.85f)
                            val major = j % 4 == 0
                            drawLine(
                                color = g1.copy(alpha = (lineAlpha * fade * (if (major) 1f else 0.6f)).coerceIn(0f, 1f)),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = if (major) 1.6f else 1f,
                            )
                            y += cell
                            j++
                        }
                    }
                    if (dots.isNotEmpty()) {
                        drawPoints(
                            points = dots,
                            pointMode = PointMode.Points,
                            brush = dotBrush,
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
