package com.matheo.sonnet.ui.theme

import androidx.compose.ui.graphics.Color

// Sonnet palette — noir profond + violet (hérité d'Opus)
val Violet50  = Color(0xFFEEEDFE)
val Violet100 = Color(0xFFCECBF6)
val Violet200 = Color(0xFFAFA9EC)
val Violet400 = Color(0xFF7F77DD)
val Violet600 = Color(0xFF534AB7)
val Violet800 = Color(0xFF3C3489)
val Violet900 = Color(0xFF26215C)

val BgBlack    = Color(0xFF050208)
val BgSurface1 = Color(0xFF0A0612)
val BgSurface2 = Color(0xFF150C26)

val TextPrimary   = Violet50
val TextSecondary = Violet200
val TextMuted     = Violet400
val Divider       = Violet900

// Thème clair
val LightBg            = Color(0xFFF7F5FF)
val LightSurface1      = Color(0xFFFFFFFF)
val LightSurface2      = Color(0xFFECE8FA)
val LightText          = Color(0xFF1B1530)
val LightTextSecondary = Color(0xFF4A4370)
val LightDivider       = Color(0xFFD8D2F0)

// Accent palette the user can switch between in Settings
data class AccentPalette(val name: String, val primary: Color, val onPrimary: Color)

val Accents = listOf(
    AccentPalette("Violet",   Violet400, BgBlack),
    AccentPalette("Rose",     Color(0xFFD4537E), BgBlack),
    AccentPalette("Émeraude", Color(0xFF1D9E75), BgBlack),
    AccentPalette("Azur",     Color(0xFF378ADD), BgBlack),
    AccentPalette("Ambre",    Color(0xFFEF9F27), BgBlack),
    AccentPalette("Corail",   Color(0xFFD85A30), BgBlack),
)
