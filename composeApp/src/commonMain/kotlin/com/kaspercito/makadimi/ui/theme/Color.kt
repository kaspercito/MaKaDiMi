package com.kaspercito.makadimi.ui.theme

import androidx.compose.ui.graphics.Color

// ===== Brand =====

/**
 * Brand seed color. The whole Material 3 ColorScheme is generated from this
 * color at runtime — see [AppTheme].
 */
val seed = Color(0xFF1FE58F)

// ===== Semantic colors (not derivable from the color scheme) =====

/** Liked/favorite state (heart buttons, favorite tiles). */
val favoriteColor = Color(0xFF5CFF9D)

/** Currently playing lyric line. */
val lyricActiveColor = Color(0xFFC8FFE3)

val shimmerBackground = Color(0x7E10241C)
val shimmerLine = Color(0xFF1E4034)

// Light-theme counterparts of the shimmer tokens.
val shimmerBackgroundLight = Color(0x7EDCD8D8)
val shimmerLineLight = Color(0xFFCFC8C8)

val overlay = Color(0x32030807)
val blackMoreOverlay = Color(0x8F030807)

// ===== Desktop shell =====
// Spotify-style layering for the desktop window: the window itself takes the extreme, panels
// step one shade back towards the middle so they read as raised. The light panel is not here —
// it comes from colorScheme.surfaceContainer, which already sits at the right distance.

val desktopWindowDark = Color(0xFF030807)
val desktopWindowLight = Color(0xFFFFFFFF)
val desktopPanelDark = Color(0xFF0A1511)

// ===== Desktop window controls =====
// The macOS traffic lights. Fixed by convention rather than by theme — users read these by
// colour, so they stay the same in light and dark.

val windowCloseButton = Color(0xFFFF605C)
val windowCloseButtonHover = Color(0xFFE54942)
val windowMinimiseButton = Color(0xFFFFBD44)
val windowMinimiseButtonHover = Color(0xFFE5A93D)
val windowMaximiseButton = Color(0xFF00CA4E)
val windowMaximiseButtonHover = Color(0xFF00B344)

// ===== Legacy — do not add new usages =====

/**
 * Old M3 primary (lavender). Kept only for the SettingScreen storage bar,
 * which stays untouched by owner's decision.
 */
@Deprecated("Legacy storage bar color only — use MaterialTheme.colorScheme.primary in new code")
val md_theme_dark_primary = Color(0xFFB2C5FF)


// ===== Paleta "Neón verde" (identidad MaKaDiMi) =====
// Los nombres Aurora* se conservan por compatibilidad; ahora son verde neón / cian / lima.
val ObsidianBase = Color(0xFF030807)
val AuroraViolet = Color(0xFF3DFFA2) // acento principal: verde neón
val AuroraAqua = Color(0xFF2BE4F0) // secundario: cian
val AuroraMagenta = Color(0xFFC8FF3D) // terciario: lima eléctrico
val PearlBase = Color(0xFFF1FBF6)
