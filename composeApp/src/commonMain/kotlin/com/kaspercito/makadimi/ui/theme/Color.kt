package com.kaspercito.makadimi.ui.theme

import androidx.compose.ui.graphics.Color

// ===== Brand =====

/**
 * Brand seed color. The whole Material 3 ColorScheme is generated from this
 * color at runtime — see [AppTheme].
 */
val seed = Color(0xFF8B7BFF)

// ===== Semantic colors (not derivable from the color scheme) =====

/** Liked/favorite state (heart buttons, favorite tiles). */
val favoriteColor = Color(0xFFFF4FD8)

/** Currently playing lyric line. */
val lyricActiveColor = Color(0xFFB9FFF4)

val shimmerBackground = Color(0x7E1A1E3A)
val shimmerLine = Color(0xFF2E3566)

// Light-theme counterparts of the shimmer tokens.
val shimmerBackgroundLight = Color(0x7EDCD8D8)
val shimmerLineLight = Color(0xFFCFC8C8)

val overlay = Color(0x3205060E)
val blackMoreOverlay = Color(0x8F05060E)

// ===== Desktop shell =====
// Spotify-style layering for the desktop window: the window itself takes the extreme, panels
// step one shade back towards the middle so they read as raised. The light panel is not here —
// it comes from colorScheme.surfaceContainer, which already sits at the right distance.

val desktopWindowDark = Color(0xFF05060E)
val desktopWindowLight = Color(0xFFFFFFFF)
val desktopPanelDark = Color(0xFF0C0F20)

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


// ===== Aurora / Obsidian palette (identidad MaKaDiMi) =====
val ObsidianBase = Color(0xFF05060E)
val AuroraViolet = Color(0xFF9C8CFF)
val AuroraAqua = Color(0xFF42F0E0)
val AuroraMagenta = Color(0xFFFF6AD5)
val PearlBase = Color(0xFFF4F3FF)
