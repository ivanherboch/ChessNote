package com.ivanherboch.chessduck

import androidx.compose.ui.graphics.Color
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
import com.ivanherboch.chessduck.ui.contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrastes WCAG 2.1 AA des palettes Nuit, Jour et Enfant : 4,5:1 pour le texte, 3:1 pour les bordures interactives. */
class ContrastTest {
    private val failures = ArrayList<String>()

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

    @Test
    fun allPalettesMeetWcagAA() {
        palette("Nuit", NightColors)
        palette("Jour", DayColors)
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
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
