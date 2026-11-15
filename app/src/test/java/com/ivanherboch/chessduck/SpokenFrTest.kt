package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.SpokenFr
import com.ivanherboch.chessduck.content.Lessons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpokenFrTest {
    @Test
    fun movesAreSpokenInFrench() {
        assertEquals("cavalier prend e5, échec", SpokenFr.move("Nxe5+"))
        assertEquals("e prend d5", SpokenFr.move("exd5"))
        assertEquals("e4", SpokenFr.move("e4"))
        assertEquals("cavalier g e7", SpokenFr.move("Nge7"))
        assertEquals("dame h4, mat", SpokenFr.move("Qh4#"))
        assertEquals("petit roque", SpokenFr.move("O-O"))
        assertEquals("grand roque, échec", SpokenFr.move("O-O-O+"))
        assertEquals("b prend a8 promotion en dame", SpokenFr.move("bxa8=Q"))
    }

    @Test
    fun notationInsideSentencesIsReplaced() {
        assertEquals(
            "Les Blancs roquent et menacent maintenant réellement cavalier prend e5.",
            SpokenFr.text("Les Blancs roquent et menacent maintenant réellement Nxe5."),
        )
        assertEquals("Après fou c4, le pion e5 tombe !", SpokenFr.text("Après 3. Bc4, le **pion e5** tombe !"))
    }

    @Test
    fun noLessonTextKeepsEnglishNotation() {
        val english = Regex("""(?<!\w)[KQRBN][a-h1-8]?x?[a-h][1-8]""")
        for (l in Lessons.all) for (t in l.texts + l.summary + l.title) assertFalse(t, english.containsMatchIn(SpokenFr.text(t)))
    }

    @Test
    fun chessTermsArePronounced() {
        assertEquals("C'est patte : partie nulle.", SpokenFr.text("C'est pat : partie nulle."))
        assertEquals("Le gambitte Évanse.", SpokenFr.text("Le gambit Evans."))
        assertEquals("Fou h2, échec et matte.", SpokenFr.text("Fou h2, échec et mat."))
        assertEquals("dame h4, matte", SpokenFr.text(SpokenFr.move("Qh4#")))
        // Les mots qui ne font que ressembler restent intacts.
        val same = "Il faut mater sans perdre de matériel, avec patience."
        assertEquals(same, SpokenFr.text(same))
    }

    @Test
    fun noLessonKeepsAHardWordForTheVoice() {
        val hard = Regex("(?<![\\p{L}-])(pat|mat|gambit|fianchetto|zugzwang|Giuoco|Kieseritzky|Najdorf|Winawer|Tarrasch|Grünfeld)(?![\\p{L}-])", RegexOption.IGNORE_CASE)
        for (l in Lessons.all) for (t in l.texts + l.summary + l.title) assertFalse(t, hard.containsMatchIn(SpokenFr.text(t)))
    }
}
