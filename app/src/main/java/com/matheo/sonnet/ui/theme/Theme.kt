package com.matheo.sonnet.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Couleur d'accent finale : teinte libre si [hue] >= 0, sinon la pastille [accentIndex].
 * En thème clair on fonce un peu la couleur pour garder un bon contraste.
 */
fun resolveAccent(accentIndex: Int, hue: Int, light: Boolean): Color {
    if (hue in 0..359) {
        return if (light) Color.hsv(hue.toFloat(), 0.72f, 0.62f) else Color.hsv(hue.toFloat(), 0.52f, 0.92f)
    }
    val base = Accents.getOrElse(accentIndex) { Accents.first() }.primary
    return if (light) {
        Color(
            red = base.red * 0.72f,
            green = base.green * 0.72f,
            blue = base.blue * 0.72f,
            alpha = 1f,
        )
    } else base
}

fun accentHex(color: Color): String = String.format("#%06X", 0xFFFFFF and color.toArgb())

@Composable
fun SonnetTheme(
    accentIndex: Int = 0,
    immersive: Boolean = false,
    themeMode: Int = 0,          // 0 = sombre, 1 = AMOLED, 2 = clair
    accentHue: Int = -1,         // -1 = pastille ; 0..359 = teinte libre
    content: @Composable () -> Unit
) {
    val light = themeMode == 2
    val amoled = themeMode == 1
    val accent = resolveAccent(accentIndex, accentHue, light)
    val onAccent = if (light) Color.White else BgBlack

    val colors = when {
        light -> lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = Violet100,
            onPrimaryContainer = Violet900,
            secondary = Violet600,
            onSecondary = Color.White,
            secondaryContainer = Violet50,
            onSecondaryContainer = Violet900,
            background = LightBg,
            onBackground = LightText,
            surface = LightSurface1,
            onSurface = LightText,
            surfaceVariant = LightSurface2,
            onSurfaceVariant = LightTextSecondary,
            outline = LightDivider,
            outlineVariant = Violet100,
        )
        else -> darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = Violet800,
            onPrimaryContainer = Violet50,
            secondary = Violet600,
            onSecondary = Violet50,
            secondaryContainer = Violet900,
            onSecondaryContainer = Violet100,
            background = if (amoled) Color.Black else BgBlack,
            onBackground = TextPrimary,
            surface = if (amoled) Color(0xFF050505) else BgSurface1,
            onSurface = TextPrimary,
            surfaceVariant = if (amoled) Color(0xFF111111) else BgSurface2,
            onSurfaceVariant = TextSecondary,
            outline = if (amoled) Color(0xFF222222) else Divider,
            outlineVariant = Violet900,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode && !immersive) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
