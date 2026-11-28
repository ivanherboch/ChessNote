package com.ivanherboch.chessduck

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ivanherboch.chessduck.feature.KID_LEGEND
import com.ivanherboch.chessduck.feature.KidBlue
import com.ivanherboch.chessduck.feature.KidCoral
import com.ivanherboch.chessduck.feature.KidGreen
import com.ivanherboch.chessduck.feature.KidOrange
import com.ivanherboch.chessduck.feature.annotationColor
import com.ivanherboch.chessduck.ui.DayColors
import com.ivanherboch.chessduck.ui.DuckColors
import com.ivanherboch.chessduck.ui.KidColors
import com.ivanherboch.chessduck.ui.NightColors
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrastes WCAG 2.1 AA des palettes Nuit, Jour et Enfant : 4,5:1 pour le texte, 3:1 pour les bordures interactives. */
class ContrastTest {
    private val failures = ArrayList<String>()

    /** Rapport de contraste WCAG entre deux couleurs opaques (luminance relative sRGB). */
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    private fun Color.over(bg: Color): Color = Color(red * alpha + bg.red * (1 - alpha), green * alpha + bg.green * (1 - alpha), blue * alpha + bg.blue * (1 - alpha), 1f)

    private fun check(name: String, fg: Color, bg: Color, min: Float = 4.5f) {
        val r = contrast(fg, bg)
        if (r < min) failures.add("$name : %.2f < %.1f".format(r, min))
    }

    private fun palette(tag: String, c: DuckColors) {
        val surfaces = mapOf("bg" to c.bg, "surface" to c.surface, "surfaceSubtle" to c.surfaceSubtle, "surfaceHi" to c.surfaceHi)
        for ((sn, s) in surfaces) {
            check("$tag texte/$sn", c.text, s)
            check("$tag secondaire/$sn", c.muted, s)
            check("$tag bordure interactive/$sn", c.outlineStrong, s, 3f)
        }
        val roles = mapOf("or" to c.goldRole, "vert" to c.greenRole, "bleu" to c.blueRole, "rouge" to c.redRole)
        for ((rn, r) in roles) {
            for ((sn, s) in surfaces) check("$tag accent $rn/$sn", r.text, s)
            check("$tag $rn texte sur remplissage", r.onFill, r.fill)
            // Bouton tonal : remplissage à 14–16 % posé sur le fond ou une carte.
            for ((sn, s) in surfaces.filterKeys { it != "surfaceHi" }) check("$tag tonal $rn/$sn", r.text, r.fill.copy(alpha = if (c.dark) 0.16f else 0.14f).over(s))
        }
        // Coordonnées de l'échiquier : pastille posée sur une case claire ou foncée.
        check("$tag coordonnées/case claire", c.coordInk, c.coordPill.over(c.squareLight))
        check("$tag coordonnées/case foncée", c.coordInk, c.coordPill.over(c.squareDark))
    }

    /**
     * Case sélectionnée (sélection à 50 % sur la case) : nettement distincte de la case nue sur les 4 échiquiers et
     * toutes les palettes, et bleue (pas verte) sur le Vert tournoi, dont les cases foncées sont olive.
     */
    @Test
    fun selectionStandsOutOnEveryBoard() {
        fun dist(a: Color, b: Color) = kotlin.math.sqrt(((a.red - b.red) * (a.red - b.red) + (a.green - b.green) * (a.green - b.green) + (a.blue - b.blue) * (a.blue - b.blue)).toDouble()) * 255
        for (t in com.ivanherboch.chessduck.ui.BoardTheme.entries) for (p in com.ivanherboch.chessduck.ui.DuckPalette.entries) for (dark in listOf(true, false)) {
            val c = t.applyTo(p.colors(dark))
            for (sq in listOf(c.squareLight, c.squareDark)) {
                val d = dist(c.select.copy(alpha = 0.5f).over(sq), sq)
                assertTrue("${t.label} / ${p.label} : sélection trop proche de la case (${d.toInt()})", d >= 55)
            }
        }
        val tour = com.ivanherboch.chessduck.ui.BoardTheme.TOURNAMENT.applyTo(NightColors)
        assertTrue(tour.select.blue > tour.select.green)
        assertTrue("sélection sur case olive", contrast(tour.select.copy(alpha = 0.5f).over(tour.squareDark), tour.squareDark) >= 1.5f)
    }

    @Test
    fun allPalettesMeetWcagAA() {
        palette("Nuit", NightColors)
        palette("Jour", DayColors)
        for (p in com.ivanherboch.chessduck.ui.DuckPalette.entries) {
            palette("${p.label} Nuit", p.night)
            palette("${p.label} Jour", p.day)
        }
        // Matières d'échiquier : coordonnées lisibles sur les deux couleurs de cases.
        for (t in com.ivanherboch.chessduck.ui.BoardTheme.entries) {
            val c = t.applyTo(NightColors)
            check("${t.label} coordonnées/case claire", c.coordInk, c.coordPill.over(c.squareLight))
            check("${t.label} coordonnées/case foncée", c.coordInk, c.coordPill.over(c.squareDark))
        }
        palette("Enfant", KidColors)
        for ((n, pair) in mapOf("bleu" to KidBlue, "orange" to KidOrange, "corail" to KidCoral, "vert" to KidGreen)) {
            check("Enfant bouton $n", Color.White, pair.first)
        }
        check("Enfant bouton désactivé", Color(0xFF4A6785), Color(0xFFDCEAF6))
        for (p in KID_LEGEND) {
            check("Enfant leçon ${p.name}", p.ink, Color.White)
            check("Enfant carte ${p.name}", Color(0xFF1F3B57), p.bg)
        }
        for (n in listOf("!!", "!", "?!", "?", "??")) check("annotation $n", Color.White, annotationColor(n))
        // Palette Bleu-Orange : mêmes contrastes, bons coups bleus et erreurs vermillon (jamais du vert contre du rouge).
        for (n in listOf("!!", "!", "?!", "?", "??")) check("annotation daltonisme $n", Color.White, annotationColor(n, colorBlind = true))
        for (n in listOf("!!", "!")) assertTrue(n, annotationColor(n, true).blue > annotationColor(n, true).red)
        for (n in listOf("?", "??")) assertTrue(n, annotationColor(n, true).red > annotationColor(n, true).blue)
        assertTrue(com.ivanherboch.chessduck.ui.DuckPalette.BLUE_ORANGE.night.colorBlind && com.ivanherboch.chessduck.ui.DuckPalette.BLUE_ORANGE.day.colorBlind)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
