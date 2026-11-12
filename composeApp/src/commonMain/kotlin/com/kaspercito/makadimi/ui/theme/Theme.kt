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
 * Superficies claras "Pearl": perla lavanda con una rampa fría muy sutil. Con el color de marca
 * por defecto, el acento pasa a un índigo profundo legible sobre fondo claro; con color propio o
 * de fondo de pantalla se respeta el del usuario.
 */
private fun ColorScheme.withPearlLightSurfaces(brand: Boolean): ColorScheme {
    val base =
        copy(
            background = Color(0xFFF4F3FF),
            onBackground = Color(0xFF14152B),
            surface = Color(0xFFF4F3FF),
            onSurface = Color(0xFF14152B),
            surfaceVariant = Color(0xFFE2E1F4),
            onSurfaceVariant = Color(0xFF4A4C73),
            surfaceTint = primary,
            surfaceBright = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFDAD9EC),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF8F7FF),
            surfaceContainer = Color(0xFFEFEEFC),
            surfaceContainerHigh = Color(0xFFE9E8F8),
            surfaceContainerHighest = Color(0xFFE2E1F4),
            outline = Color(0xFF787BA8),
            outlineVariant = Color(0xFFC9CAE6),
            inverseSurface = Color(0xFF23244A),
            inverseOnSurface = Color(0xFFF1F0FF),
        )
    return if (brand) {
        base.copy(
            primary = Color(0xFF5B47F0),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE2DDFF),
            onPrimaryContainer = Color(0xFF1B0F6B),
            secondary = Color(0xFF008C84),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFB6F5EE),
            onSecondaryContainer = Color(0xFF00201E),
            tertiary = Color(0xFFC21E92),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFD8F1),
            onTertiaryContainer = Color(0xFF3B002A),
        )
    } else {
        base
    }
}

/**
 * Superficies oscuras "Obsidian": negro-índigo profundo con una rampa azulada para tarjetas, hojas
 * y diálogos. Con el color de marca por defecto el acento es violeta eléctrico, el secundario aqua
 * y el terciario magenta; con color propio solo se tocan las superficies.
 */
private fun ColorScheme.withObsidianDarkSurfaces(brand: Boolean): ColorScheme {
    val base =
        copy(
            background = Color(0xFF05060E),
            surface = Color(0xFF05060E),
            onBackground = Color(0xFFEDEEFF),
            onSurface = Color(0xFFEDEEFF),
            surfaceDim = Color(0xFF03040A),
            surfaceBright = Color(0xFF252B52),
            surfaceContainerLowest = Color(0xFF03040A),
            surfaceContainerLow = Color(0xFF0A0C18),
            surfaceContainer = Color(0xFF0F1222),
            surfaceContainerHigh = Color(0xFF151934),
            surfaceContainerHighest = Color(0xFF1C2142),
            surfaceVariant = Color(0xFF1C2142),
            onSurfaceVariant = Color(0xFF9EA4CC),
            outline = Color(0xFF5B6296),
            outlineVariant = Color(0xFF262B52),
            tertiary = Color(0xFFFF6AD5),
            onTertiary = Color(0xFF4A0036),
            tertiaryContainer = Color(0xFF6B0F52),
            onTertiaryContainer = Color(0xFFFFD8F1),
        )
    return if (brand) {
        base.copy(
            primary = Color(0xFF9C8CFF),
            onPrimary = Color(0xFF1A0F57),
            primaryContainer = Color(0xFF3A2FA0),
            onPrimaryContainer = Color(0xFFE4DFFF),
            secondary = Color(0xFF42F0E0),
            onSecondary = Color(0xFF00322E),
            secondaryContainer = Color(0xFF0B4E49),
            onSecondaryContainer = Color(0xFFB8FFF6),
            surfaceTint = Color(0xFF9C8CFF),
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
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(16.dp),
        medium = LeafShape,
        large = LeafShapeLarge,
        extraLarge = RoundedCornerShape(34.dp),
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
