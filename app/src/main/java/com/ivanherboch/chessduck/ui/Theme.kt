package com.ivanherboch.chessduck.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Palette ChessDuck : ardoise + accents fonctionnels (or, émeraude, ciel, corail). */
data class DuckColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
) {
    val gold = Color(0xFFF59E0B)
    val green = Color(0xFF10B981)
    val blue = Color(0xFF38BDF8)
    val red = Color(0xFFEF4444)
    val onAccent = Color(0xFF0F172A)
    val squareLight = Color(0xFFF0D9B5)
    val squareDark = Color(0xFFB58863)
}

val NightColors = DuckColors(
    dark = true,
    bg = Color(0xFF0F172A), surface = Color(0xFF1E293B), surfaceHi = Color(0xFF334155),
    text = Color(0xFFF8FAFC), muted = Color(0xFF94A3B8), outline = Color(0xFF475569),
)
val DayColors = DuckColors(
    dark = false,
    bg = Color(0xFFF8FAFC), surface = Color(0xFFE2E8F0), surfaceHi = Color(0xFFCBD5E1),
    text = Color(0xFF0F172A), muted = Color(0xFF475569), outline = Color(0xFF94A3B8),
)

val LocalDuck = staticCompositionLocalOf { NightColors }

@Composable
fun ChessDuckTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) NightColors else DayColors
    val scheme = if (dark) darkColorScheme(
        primary = c.gold, onPrimary = c.onAccent, secondary = c.blue, tertiary = c.green, error = c.red,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceHi, onSurfaceVariant = c.muted, outline = c.outline,
        surfaceContainerLow = c.surface, surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHi,
    ) else lightColorScheme(
        primary = c.gold, onPrimary = c.onAccent, secondary = c.blue, tertiary = c.green, error = c.red,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceHi, onSurfaceVariant = c.muted, outline = c.outline,
        surfaceContainerLow = c.bg, surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHi,
    )
    CompositionLocalProvider(LocalDuck provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
