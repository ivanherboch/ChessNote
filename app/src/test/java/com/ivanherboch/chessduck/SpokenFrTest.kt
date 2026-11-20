package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.SpokenFr
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.Narrator
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
        for (l in Lessons.all) for (t in spoken(l)) assertFalse(t, english.containsMatchIn(SpokenFr.text(t)))
    }

    /** Tout ce que la voix peut lire d'une leçon : titre, résumé, règle à retenir, conditions, commentaires, question. */
    private fun spoken(l: com.ivanherboch.chessduck.content.Lesson) =
        l.texts + l.summary + l.title + l.takeaway + l.conditions + com.ivanherboch.chessduck.feature.ASK +
            com.ivanherboch.chessduck.feature.objectiveText(l) + l.ideas + l.traps + l.variants.flatMap { it.texts + it.title } +
            listOfNotNull(l.quiz?.question, l.quiz?.answer, l.quiz?.explanation) + l.quiz?.wrong.orEmpty()

    @Test
    fun chessTermsArePronounced() {
        assertEquals("C'est patte : partie nulle.", SpokenFr.text("C'est pat : partie nulle."))
        assertEquals("Le gambitte Évanse.", SpokenFr.text("Le gambit Evans."))
        assertEquals("Fou h2, échec et matte.", SpokenFr.text("Fou h2, échec et mat."))
        assertEquals("dame h4, matte", SpokenFr.text(SpokenFr.move("Qh4#")))
        assertEquals("L'étude de Rétti et la défense Filidor.", SpokenFr.text("L'étude de Réti et la défense Philidor."))
        assertEquals("Paul Morfi contre le duc de Brunesvik et le comte Izouar.", SpokenFr.text("Paul Morphy contre le duc de Brunswick et le comte Isouard."))
        assertEquals("La défense Nimmzo-indienne, la Skandinave, le matte d'Anastazie.", SpokenFr.text("La défense nimzo-indienne, la scandinave, le mat d'Anastasie."))
        assertEquals("L'étude de Saavédra, la défense Vantsoura et le gambitte Bènnko.", SpokenFr.text("L'étude de Saavedra, la défense Vancura et le gambit Benko."))
        assertEquals("La sicilienne Kanne et la Caro-Kane.", SpokenFr.text("La sicilienne Kan et la Caro-Kann."))
        // Les mots qui ne font que ressembler restent intacts.
        val same = "Il faut mater sans perdre de matériel, avec patience."
        assertEquals(same, SpokenFr.text(same))
    }

    @Test
    fun noLessonKeepsAHardWordForTheVoice() {
        val hard = Regex("(?<![\\p{L}-])(pat|mat|gambit|fianchetto|zugzwang|Giuoco|Kieseritzky|Najdorf|Winawer|Tarrasch|Grünfeld|Réti|Philidor|Nimzo|Morphy|Brunswick|Isouard|Vancura|Saavedra|Anderssen|Nimzowitsch|Botvinnik|Lasker|Bauer|Pillsbury|Greco|Taimanov|Trompowsky|Marshall|Benko|desperado|Smith|Kan|Panov|Budapest|Tartakower|Rotlewi|Lodz|Sämisch)(?![\\p{L}])", RegexOption.IGNORE_CASE)
        for (l in Lessons.all) for (t in spoken(l)) assertFalse(t, hard.containsMatchIn(SpokenFr.text(t)))
    }

    @Test
    fun pausedSpeechResumesAtTheStartOfItsSentence() {
        val t = "Le fou sort. Il vise la case f7 : seul le roi la protège."
        assertEquals(0, Narrator.resumePoint(t, 7)) // interrompu dans « sort » : on reprend tout le début
        assertEquals(t.indexOf("Il vise"), Narrator.resumePoint(t, t.indexOf("case")))
        assertEquals(t.indexOf("seul"), Narrator.resumePoint(t, t.indexOf("protège") + 3))
    }
}
