package com.kaspercito.makadimi.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.Font
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.space_mono_regular

/**
 * Lenguaje visual MaKaDiMi "Aurora": base obsidiana casi negra con auroras violeta / aqua / magenta,
 * paneles de cristal translúcido con borde de luz, geometría "hoja" (esquinas asimétricas) y
 * etiquetas HUD en tipografía mono. Todo sale del ColorScheme, así que respeta el color elegido
 * por el usuario y el modo claro/oscuro.
 */

/** Geometría de marca: esquinas opuestas grandes y pequeñas, como una hoja / pastilla cortada. */
val LeafShape = RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
val LeafShapeLarge = RoundedCornerShape(topStart = 30.dp, topEnd = 8.dp, bottomEnd = 30.dp, bottomStart = 8.dp)
val LeafShapeSmall = RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 4.dp)

/** Degradado de marca: del acento principal al terciario (violeta -> magenta). */
@Composable
fun neonBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.horizontalGradient(listOf(cs.primary, cs.tertiary))
}

/** Degradado "aurora" frío: violeta -> aqua. Para indicadores, progreso y botones de acción. */
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
    val sheen =
        Brush.verticalGradient(
            listOf(cs.onSurface.copy(alpha = 0.07f), Color.Transparent),
        )
    return this
        .background(tint.copy(alpha = alpha), shape)
        .background(sheen, shape)
        .neonBorder(shape, alpha = borderAlpha * 0.6f)
}

/**
 * Fondo ambiental de toda la app: base sólida + tres auroras difusas (violeta arriba a la
 * izquierda, magenta a la derecha, aqua abajo). Se dibuja una sola vez detrás del Scaffold.
 */
@Composable
fun Modifier.auroraBackground(base: Color = MaterialTheme.colorScheme.background): Modifier {
    val cs = MaterialTheme.colorScheme
    val isDark = base.red * 0.299f + base.green * 0.587f + base.blue * 0.114f < 0.5f
    val k = if (isDark) 1f else 0.55f
    val c1 = cs.primary.copy(alpha = 0.26f * k)
    val c2 = cs.tertiary.copy(alpha = 0.15f * k)
    val c3 = cs.secondary.copy(alpha = 0.11f * k)
    return this.drawBehind {
        drawRect(base)
        val w = size.width
        val h = size.height
        val r1 = w * 0.95f
        drawCircle(Brush.radialGradient(listOf(c1, Color.Transparent), Offset(w * 0.05f, h * 0.02f), r1), r1, Offset(w * 0.05f, h * 0.02f))
        val r2 = w * 0.8f
        drawCircle(Brush.radialGradient(listOf(c2, Color.Transparent), Offset(w * 1.0f, h * 0.38f), r2), r2, Offset(w * 1.0f, h * 0.38f))
        val r3 = w * 0.9f
        drawCircle(Brush.radialGradient(listOf(c3, Color.Transparent), Offset(w * 0.1f, h * 1.0f), r3), r3, Offset(w * 0.1f, h * 1.0f))
    }
}
