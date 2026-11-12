package com.kaspercito.makadimi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import makadimi.composeapp.generated.resources.Res
import makadimi.composeapp.generated.resources.inter_bold
import makadimi.composeapp.generated.resources.inter_display_bold
import makadimi.composeapp.generated.resources.inter_display_extrabold
import makadimi.composeapp.generated.resources.inter_medium
import makadimi.composeapp.generated.resources.inter_regular
import makadimi.composeapp.generated.resources.inter_semibold

@Composable
fun fontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.inter_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.inter_bold, FontWeight.Bold, FontStyle.Normal),
    )

/** Familia de titulares: Inter Display, más cerrada y con más carácter en tamaños grandes. */
@Composable
fun displayFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_display_bold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.inter_display_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.inter_display_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
    )

/**
 * When true, [typo] keeps the original always-light text colors (pure white titles, #A8A8A8 body)
 * regardless of theme. Immersive screens drawn over dark artwork provide `true` so their text stays
 * readable at light theme. Everything else leaves it false and gets theme-aware colors.
 */
val LocalForceDarkText = staticCompositionLocalOf { false }

@Composable
fun typo(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    forceDark: Boolean = LocalForceDarkText.current,
): Typography {
    val fontFamily = fontFamily()
    val display = displayFontFamily()

    // Titles were pure white, everything else muted gray (#A8A8A8) in the old dark-only palette.
    // forceDark keeps that; otherwise both come from the scheme (theme-aware light/dark).
    val titleColor = if (forceDark) Color.White else colorScheme.onBackground
    val bodyColor = if (forceDark) Color(0xFFA8A8A8) else colorScheme.onSurfaceVariant

    // typo() se llama desde cientos de composables; sin remember se construía un Typography nuevo
    // en cada recomposición (y cambiaba la identidad del tema entero). Ahora se reutiliza.
    val typo =
        remember(colorScheme, forceDark, fontFamily, display) {
        Typography(
            /***
             * This typo().is use for the title of the Playlist, Artist, Song, Album, etc. in Home, Mood, Genre, Playlist, etc.
             */
            titleSmall =
                TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = fontFamily,
                    color = titleColor,
                ),
            titleMedium =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    fontFamily = display,
                    color = titleColor,
                ),
            titleLarge =
                TextStyle(
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.9).sp,
                    fontFamily = display,
                    color = titleColor,
                ),
            bodySmall =
                TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = bodyColor,
                ),
            bodyMedium =
                TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = bodyColor,
                ),
            bodyLarge =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = bodyColor,
                ),
            displayLarge =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = bodyColor,
                ),
            headlineMedium =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    fontFamily = display,
                    color = bodyColor,
                ),
            headlineLarge =
                TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.7).sp,
                    fontFamily = display,
                    color = bodyColor,
                ),
            labelMedium =
                TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    fontFamily = fontFamily,
                    color = bodyColor,
                ),
            labelSmall =
                TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = fontFamily,
                    color = bodyColor,
                ),
            // Botones y chips: antes caían a la fuente por defecto del sistema. Sin color a propósito:
            // el color lo pone el contenedor (onPrimary, etc.).
            labelLarge =
                TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                    fontFamily = fontFamily,
                ),
            headlineSmall =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    fontFamily = display,
                    color = titleColor,
                ),
        )
        }
    return typo
}
