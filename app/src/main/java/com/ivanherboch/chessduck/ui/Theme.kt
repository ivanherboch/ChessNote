package com.ivanherboch.chessduck.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ivanherboch.chessduck.R

/**
 * Rôle d'un accent :
 * - [text] : texte, icône ou bordure posés sur une surface du thème (≥ 4,5:1) ;
 * - [fill] : couleur de remplissage d'un bouton principal ;
 * - [onFill] : texte posé sur ce remplissage (≥ 4,5:1).
 */
data class AccentRole(val text: Color, val fill: Color, val onFill: Color)

/**
 * Palette sémantique ChessDuck « Ardoise & Or ».
 * Les couleurs de base [gold], [green], [blue], [red] servent aux éléments graphiques (flèches, surlignages,
 * jauges) ; tout texte d'accent passe par [goldText], [greenText]… ou par [role], qui garantissent le contraste.
 */
data class DuckColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val surfaceHi: Color,
    val text: Color,
    val muted: Color,
    /** Séparateurs décoratifs. */
    val outline: Color,
    /** Bordures de composants interactifs (≥ 3:1 sur les surfaces). */
    val outlineStrong: Color,
    val gold: Color,
    val green: Color,
    val blue: Color,
    val red: Color,
    val goldRole: AccentRole,
    val greenRole: AccentRole,
    val blueRole: AccentRole,
    val redRole: AccentRole,
    val squareLight: Color = Color(0xFFF0D9B5),
    val squareDark: Color = Color(0xFFB58863),
    /** Coordonnées de l'échiquier : texte et pastille de fond (lisibles même sous une pièce). */
    val coordInk: Color = Color(0xFF5C3D24),
    val coordPill: Color = Color(0xD9F0D9B5),
    /** Couleur de sélection et des destinations légales. */
    val select: Color = Color(0xFF10B981),
    val kid: Boolean = false,
) {
    val goldText get() = goldRole.text
    val greenText get() = greenRole.text
    val blueText get() = blueRole.text
    val redText get() = redRole.text

    /** Texte posé sur un remplissage or (badge ELO, bouton principal or). */
    val onAccent get() = goldRole.onFill

    val neutralRole get() = AccentRole(text, surfaceHi, text)

    /** Rôle correspondant à une couleur d'accent de base (or, vert, bleu, rouge) ; neutre si null. */
    fun role(accent: Color?): AccentRole = when (accent) {
        null -> neutralRole
        gold -> goldRole
        green -> greenRole
        blue -> blueRole
        red -> redRole
        else -> AccentRole(accent, accent, if (accent.luminance() > 0.4f) Color(0xFF0F172A) else Color.White)
    }
}

/** Rapport de contraste WCAG entre deux couleurs opaques (luminance relative sRGB). */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private val Ink = Color(0xFF0F172A)

val NightColors = DuckColors(
    dark = true,
    bg = Color(0xFF0F172A), surface = Color(0xFF1E293B), surfaceSubtle = Color(0xFF172033), surfaceHi = Color(0xFF334155),
    text = Color(0xFFF8FAFC), muted = Color(0xFFCBD5E1), outline = Color(0xFF475569), outlineStrong = Color(0xFF94A3B8),
    gold = Color(0xFFF59E0B), green = Color(0xFF10B981), blue = Color(0xFF38BDF8), red = Color(0xFFF87171),
    goldRole = AccentRole(Color(0xFFF59E0B), Color(0xFFF59E0B), Ink),
    greenRole = AccentRole(Color(0xFF34D399), Color(0xFF10B981), Ink),
    blueRole = AccentRole(Color(0xFF38BDF8), Color(0xFF38BDF8), Ink),
    redRole = AccentRole(Color(0xFFFCA5A5), Color(0xFFF87171), Ink),
)

val DayColors = DuckColors(
    dark = false,
    bg = Color(0xFFF8FAFC), surface = Color(0xFFFFFFFF), surfaceSubtle = Color(0xFFEEF2F6), surfaceHi = Color(0xFFE2E8F0),
    text = Color(0xFF0F172A), muted = Color(0xFF475569), outline = Color(0xFFCBD5E1), outlineStrong = Color(0xFF64748B),
    gold = Color(0xFFF59E0B), green = Color(0xFF10B981), blue = Color(0xFF0284C7), red = Color(0xFFDC2626),
    // Accents foncés pour le texte sur fond clair ; remplissages choisis pour un texte ≥ 4,5:1.
    goldRole = AccentRole(Color(0xFF92400E), Color(0xFFF59E0B), Ink),
    greenRole = AccentRole(Color(0xFF065F46), Color(0xFF047857), Color.White),
    blueRole = AccentRole(Color(0xFF075985), Color(0xFF0369A1), Color.White),
    redRole = AccentRole(Color(0xFFB91C1C), Color(0xFFDC2626), Color.White),
)

/** Mode enfant : bleu ciel très doux, cases menthe et vanille, boutons denses à texte blanc lisible. */
val KidColors = DuckColors(
    dark = false,
    bg = Color(0xFFEBF6FF), surface = Color(0xFFFFFFFF), surfaceSubtle = Color(0xFFF4FAFF), surfaceHi = Color(0xFFD6ECFF),
    text = Color(0xFF1F3B57), muted = Color(0xFF4A6785), outline = Color(0xFF9CC9F0), outlineStrong = Color(0xFF4A6785),
    gold = Color(0xFFF59E0B), green = Color(0xFF237A36), blue = Color(0xFF0369A1), red = Color(0xFFD93829),
    goldRole = AccentRole(Color(0xFF8A5A00), Color(0xFFF59E0B), Color(0xFF1F3B57)),
    greenRole = AccentRole(Color(0xFF17602A), Color(0xFF237A36), Color.White),
    blueRole = AccentRole(Color(0xFF075985), Color(0xFF0369A1), Color.White),
    redRole = AccentRole(Color(0xFFA12A1E), Color(0xFFD93829), Color.White),
    squareLight = Color(0xFFFFFDF2), squareDark = Color(0xFF98DDCA),
    coordInk = Color(0xFF17594B), coordPill = Color(0xCCFFFFFF),
    select = Color(0xFFFF9F1C),
    kid = true,
)

val LocalDuck = staticCompositionLocalOf { NightColors }

/** Police imposée à tous les textes (Fredoka en mode enfant, police système sinon). */
val LocalDuckFont = staticCompositionLocalOf<FontFamily?> { null }

/** Affichage des coordonnées a–h / 1–8 sur l'échiquier (réglage utilisateur). */
val LocalShowCoords = staticCompositionLocalOf { true }

/** Police Fredoka (Google Fonts, licence OFL), en graisses variables. */
@OptIn(ExperimentalTextApi::class)
val Fredoka = FontFamily(
    Font(R.font.fredoka, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.fredoka, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.fredoka, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.fredoka, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.fredoka, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Chiffres tabulaires : scores, ELO, numéros de coups et compteurs restent alignés. */
const val TNUM = "tnum"

/** Échelle typographique M3 de ChessDuck ; aucun texte sous 11 sp. */
fun duckTypography(font: FontFamily?): Typography {
    val f = font ?: FontFamily.Default
    fun s(size: Int, weight: FontWeight, line: Int) =
        TextStyle(fontFamily = f, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, fontFeatureSettings = TNUM)
    return Typography(
        displaySmall = s(28, FontWeight.Bold, 34),
        headlineSmall = s(22, FontWeight.Bold, 28),
        titleLarge = s(20, FontWeight.Bold, 26),
        titleMedium = s(16, FontWeight.SemiBold, 22),
        titleSmall = s(14, FontWeight.SemiBold, 20),
        bodyLarge = s(16, FontWeight.Normal, 24),
        bodyMedium = s(14, FontWeight.Normal, 20),
        bodySmall = s(12, FontWeight.Normal, 16),
        labelLarge = s(13, FontWeight.SemiBold, 18),
        labelMedium = s(12, FontWeight.Medium, 16),
        labelSmall = s(11, FontWeight.Medium, 14),
    )
}

private fun schemeOf(c: DuckColors): ColorScheme {
    val base = if (c.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.goldRole.fill, onPrimary = c.goldRole.onFill,
        primaryContainer = c.goldRole.fill.copy(alpha = 0.18f).compositeOver(c.surface), onPrimaryContainer = c.goldRole.text,
        secondary = c.blueRole.fill, onSecondary = c.blueRole.onFill,
        secondaryContainer = c.surfaceHi, onSecondaryContainer = c.text,
        tertiary = c.greenRole.fill, onTertiary = c.greenRole.onFill,
        error = c.redRole.fill, onError = c.redRole.onFill,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceHi, onSurfaceVariant = c.muted, outline = c.outlineStrong, outlineVariant = c.outline,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surfaceSubtle, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceHi, surfaceContainerHighest = c.surfaceHi,
        inverseSurface = c.text, inverseOnSurface = c.bg,
    )
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

/** Palette dérivée des couleurs dynamiques Material You (Android 12+) ; accents et échiquier ChessDuck conservés. */
private fun dynamicDuck(base: DuckColors, s: ColorScheme) = base.copy(
    bg = s.background, surface = s.surfaceContainer, surfaceSubtle = s.surfaceContainerLow, surfaceHi = s.surfaceContainerHighest,
    text = s.onSurface, muted = s.onSurfaceVariant, outline = s.outlineVariant, outlineStrong = s.outline,
)

/**
 * Thème ChessDuck : [dark] Nuit/Jour, [kid] mode enfant (palette dédiée et police Fredoka),
 * [dynamic] couleurs Material You sur Android 12+ (désactivées par défaut pour préserver la charte).
 */
@Composable
fun ChessDuckTheme(dark: Boolean, kid: Boolean = false, dynamic: Boolean = false, content: @Composable () -> Unit) {
    var c = when {
        kid -> KidColors
        dark -> NightColors
        else -> DayColors
    }
    var scheme = schemeOf(c)
    if (dynamic && !kid && Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        val dyn = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        c = dynamicDuck(c, dyn)
        scheme = schemeOf(c)
    }
    val font = if (kid) Fredoka else null
    CompositionLocalProvider(LocalDuck provides c, LocalDuckFont provides font) {
        MaterialTheme(colorScheme = scheme, typography = duckTypography(font), content = content)
    }
}
