package com.kaspercito.makadimi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaspercito.domain.manager.DataStoreManager
import com.kaspercito.makadimi.expect.ui.SystemBarAppearanceEffect
import com.kaspercito.makadimi.expect.ui.platformDynamicColorScheme
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

/**
 * Semantic colors that sit outside the Material 3 ColorScheme.
 * Read them via [LocalAppColors] inside composables.
 */
@Immutable
data class AppColors(
    val favorite: Color,
    val lyricActive: Color,
    val shimmerBackground: Color,
    val shimmerLine: Color,
    val overlay: Color,
    val overlayHeavy: Color,
)

private val DarkAppColors =
    AppColors(
        favorite = favoriteColor,
        lyricActive = lyricActiveColor,
        shimmerBackground = shimmerBackground,
        shimmerLine = shimmerLine,
        overlay = overlay,
        overlayHeavy = blackMoreOverlay,
    )

// Overlays stay dark in both themes: they cover artwork, where content is always light.
private val LightAppColors =
    DarkAppColors.copy(
        shimmerBackground = shimmerBackgroundLight,
        shimmerLine = shimmerLineLight,
    )

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

/** True in dark theme. Provided by [AppTheme] so platform glass (blur/tint) can adapt reliably. */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * Whether liquid-glass surfaces may actually draw glass.
 *
 * Provided by [AppTheme] from the user's setting; the default is true so previews and anything
 * composed outside [AppTheme] keep today's look. The setting row is Android-only, so Desktop always
 * provides true — its capsule player and detail-screen buttons are glass by design, with no switch.
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }

/**
 * The dark scheme to use for immersive screens while the app itself is on the light theme.
 * Provided by [AppTheme], consumed by [ForceDarkContent]; null only outside of [AppTheme].
 */
val LocalForcedDarkColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

/** Parses "RRGGBB" or "AARRGGBB" (optionally "#"-prefixed) into a [Color]; null if malformed. */
fun parseThemeColorHex(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    val argb =
        when (clean.length) {
            6 -> "FF$clean"
            8 -> clean
            else -> return null
        }
    return argb.toLongOrNull(16)?.let { Color(it) }
}

/**
 * Superficies claras "Menta": blanco-menta con una rampa verde fría muy sutil. Con el color de marca
 * por defecto, el acento pasa a un verde profundo legible sobre fondo claro; con color propio o
 * de fondo de pantalla se respeta el del usuario.
 */
private fun ColorScheme.withPearlLightSurfaces(brand: Boolean): ColorScheme {
    val base =
        copy(
            background = Color(0xFFF1FBF6),
            onBackground = Color(0xFF0B1F17),
            surface = Color(0xFFF1FBF6),
            onSurface = Color(0xFF0B1F17),
            surfaceVariant = Color(0xFFDDEFE5),
            onSurfaceVariant = Color(0xFF3C5A4C),
            surfaceTint = primary,
            surfaceBright = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFD3E6DB),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF6FDF9),
            surfaceContainer = Color(0xFFEAF6EF),
            surfaceContainerHigh = Color(0xFFE3F1E9),
            surfaceContainerHighest = Color(0xFFDCECE2),
            outline = Color(0xFF6D8E7D),
            outlineVariant = Color(0xFFBFD8CB),
            inverseSurface = Color(0xFF16302A),
            inverseOnSurface = Color(0xFFE9FFF4),
        )
    return if (brand) {
        base.copy(
            primary = Color(0xFF00875A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFB5F5D2),
            onPrimaryContainer = Color(0xFF00210F),
            secondary = Color(0xFF00727F),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFB3F1F8),
            onSecondaryContainer = Color(0xFF001F24),
            tertiary = Color(0xFF557300),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFD7F27F),
            onTertiaryContainer = Color(0xFF161F00),
        )
    } else {
        base
    }
}

/**
 * Superficies oscuras "Obsidiana": negro-verdoso profundo con una rampa verde fría para tarjetas,
 * hojas y diálogos. Con el color de marca por defecto el acento es verde neón, el secundario cian
 * y el terciario lima; con color propio las superficies pasan a un grafito neutro (no tiñen).
 */
private fun ColorScheme.withObsidianDarkSurfaces(brand: Boolean): ColorScheme {
    val base =
        copy(
            background = Color(0xFF050606),
            surface = Color(0xFF050606),
            onBackground = Color(0xFFEDF1EF),
            onSurface = Color(0xFFEDF1EF),
            surfaceDim = Color(0xFF030404),
            surfaceBright = Color(0xFF2A2F2D),
            surfaceContainerLowest = Color(0xFF030404),
            surfaceContainerLow = Color(0xFF0A0C0B),
            surfaceContainer = Color(0xFF101312),
            surfaceContainerHigh = Color(0xFF171B19),
            surfaceContainerHighest = Color(0xFF1F2422),
            surfaceVariant = Color(0xFF1F2422),
            onSurfaceVariant = Color(0xFFA5ADAA),
            outline = Color(0xFF626B67),
            outlineVariant = Color(0xFF2A302D),
        )
    return if (brand) {
        base.copy(
            primary = Color(0xFF3DFFA2),
            onPrimary = Color(0xFF00391F),
            primaryContainer = Color(0xFF00522F),
            onPrimaryContainer = Color(0xFFB6FFD6),
            secondary = Color(0xFF2BE4F0),
            onSecondary = Color(0xFF00363B),
            secondaryContainer = Color(0xFF004F56),
            onSecondaryContainer = Color(0xFFB3F6FF),
            tertiary = Color(0xFFC8FF3D),
            onTertiary = Color(0xFF273600),
            tertiaryContainer = Color(0xFF3B5000),
            onTertiaryContainer = Color(0xFFE3FF9A),
            surfaceTint = Color(0xFF3DFFA2),
            background = Color(0xFF030807),
            surface = Color(0xFF030807),
            onBackground = Color(0xFFE6FFF3),
            onSurface = Color(0xFFE6FFF3),
            surfaceDim = Color(0xFF020605),
            surfaceBright = Color(0xFF1B2D26),
            surfaceContainerLowest = Color(0xFF020605),
            surfaceContainerLow = Color(0xFF07100D),
            surfaceContainer = Color(0xFF0B1713),
            surfaceContainerHigh = Color(0xFF10201A),
            surfaceContainerHighest = Color(0xFF152A22),
            surfaceVariant = Color(0xFF152A22),
            onSurfaceVariant = Color(0xFF9BBFAF),
            outline = Color(0xFF4F7A68),
            outlineVariant = Color(0xFF1D3A2E),
        )
    } else {
        base
    }
}

/**
 * Formas MaKaDiMi: geometría "hoja" (esquinas opuestas grandes/pequeñas) en tarjetas y paneles
 * medianos y grandes; esquinas totalmente redondas en hojas inferiores y diálogos.
 */
private val MakadimiShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(16.dp),
        medium = LeafShape,
        large = LeafShapeLarge,
        extraLarge = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppTheme(
    themeMode: String = DataStoreManager.THEME_MODE_DARK,
    themeColorSource: String = DataStoreManager.THEME_COLOR_DEFAULT,
    customThemeColor: Color? = null,
    liquidGlassEnabled: Boolean = true,
    content:
        @Composable()
        () -> Unit,
) {
    val isDark = isDarkTheme(themeMode)
    val wallpaperScheme =
        if (themeColorSource == DataStoreManager.THEME_COLOR_WALLPAPER) {
            platformDynamicColorScheme(isDark)
        } else {
            null
        }
    val brand = themeColorSource != DataStoreManager.THEME_COLOR_CUSTOM
    val seedColor =
        if (themeColorSource == DataStoreManager.THEME_COLOR_CUSTOM) {
            customThemeColor ?: seed
        } else {
            seed
        }
    // Symmetric base: dark pins background/surface to pure black via isAmoled; light pins them to
    // pure white with a neutral-grey ramp (the seed otherwise tints the light neutrals warm/cream).
    val colorScheme =
        wallpaperScheme
            ?: rememberDynamicColorScheme(
                seedColor = seedColor,
                isDark = isDark,
                isAmoled = isDark,
                style = PaletteStyle.Vibrant,
                modifyColorScheme = { cs -> if (isDark) cs.withObsidianDarkSurfaces(brand) else cs.withPearlLightSurfaces(brand) },
            )
    // Immersive screens stay dark even at light theme (see [ForceDarkContent]). Resolve their scheme
    // once here instead of letting every such subtree build a palette of its own.
    val forcedDarkScheme =
        if (isDark) {
            colorScheme
        } else {
            rememberDynamicColorScheme(
                seedColor = seedColor,
                isDark = true,
                isAmoled = true,
                style = PaletteStyle.Vibrant,
                modifyColorScheme = { cs -> cs.withObsidianDarkSurfaces(brand) },
            )
        }
    SystemBarAppearanceEffect(isDark)
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        content = {
            CompositionLocalProvider(
                LocalRippleConfiguration provides SoftRippleConfiguration,
                LocalContentColor provides colorScheme.onSurfaceVariant,
                LocalAppColors provides if (isDark) DarkAppColors else LightAppColors,
                LocalIsDarkTheme provides isDark,
                LocalForcedDarkColorScheme provides forcedDarkScheme,
                LocalLiquidGlassEnabled provides liquidGlassEnabled,
                content = content,
            )
        },
        shapes = MakadimiShapes,
        typography = typo(colorScheme),
    )
}

/**
 * A quieter ripple than the Material default, applied app-wide.
 *
 * Material 3's own alphas are tuned for light surfaces; on the near-black background this app
 * uses they read as a grey flash rather than a touch response. Each value is roughly 40% of
 * the default — enough to register the touch, not enough to wash the surface.
 *
 * The colour is left unspecified on purpose, so the ripple keeps deriving from LocalContentColor
 * and stays correct inside [ForceDarkContent] subtrees, which run a different scheme.
 *
 * This reaches bare `Modifier.clickable` too, not just Material components: MaterialTheme
 * already provides `material3.ripple()` as LocalIndication, and that is the one indication that
 * reads this configuration. Nothing extra has to be provided for it.
 *
 * The (color, rippleAlpha) constructor is deprecated in favour of one that also takes a `focus`,
 * but that three-argument version is internal to material3, and the two public replacements —
 * (color) and (focus, color) — drop rippleAlpha entirely. Adjusting ripple alpha therefore has
 * no non-deprecated path in this version, so the warning is suppressed rather than designed
 * around. Revisit once material3 opens up the full constructor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
private val SoftRippleConfiguration =
    RippleConfiguration(
        // Colour is deliberately left alone. Ripple derives it from LocalContentColor, which is
        // light on the dark theme and dark on the light one — always contrasting with what it
        // covers. Pinning a dark grey here instead made the ripple paint *darker* than the black
        // surface underneath, so a tap read as a sooty smudge rather than a highlight.
        // Alpha is the only lever that softens without changing which way the contrast runs.
        rippleAlpha =
            RippleAlpha(
                draggedAlpha = 0.06f,
                focusedAlpha = 0.04f,
                hoveredAlpha = 0.03f,
                pressedAlpha = 0.04f,
            ),
    )

/**
 * Wraps immersive screens — artist, album, playlist, local playlist, podcast and the players — which
 * always draw over dark artwork and must therefore render dark even on the light theme.
 *
 * [LocalForceDarkText] alone is not enough: it only recolors text through [typo]. Icons, buttons and
 * every other Material default read [LocalContentColor] and [MaterialTheme.colorScheme], which at
 * light theme resolve to dark-on-light and turn grey and unreadable over the artwork. Providing the
 * dark scheme here fixes the whole subtree in one place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ForceDarkContent(content: @Composable () -> Unit) {
    val darkScheme = LocalForcedDarkColorScheme.current ?: MaterialTheme.colorScheme
    MaterialExpressiveTheme(
        colorScheme = darkScheme,
        content = {
            CompositionLocalProvider(
                LocalForceDarkText provides true,
                LocalIsDarkTheme provides true,
                LocalContentColor provides darkScheme.onSurfaceVariant,
                LocalAppColors provides DarkAppColors,
                content = content,
            )
        },
        shapes = MakadimiShapes,
        typography = typo(darkScheme, forceDark = true),
    )
}

/**
 * Resolves the stored theme mode to a plain boolean.
 *
 * Pulled out of [AppTheme] so chrome living *outside* it can ask the same question — the desktop
 * title bar is drawn before AppTheme is entered, so it has no MaterialTheme to read and would
 * otherwise need its own copy of this `when`.
 */
@Composable
fun isDarkTheme(themeMode: String): Boolean =
    when (themeMode) {
        DataStoreManager.THEME_MODE_LIGHT -> false
        DataStoreManager.THEME_MODE_SYSTEM -> isSystemInDarkTheme()
        else -> true
    }
