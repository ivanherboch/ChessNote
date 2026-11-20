package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.engine.MATE
import com.ivanherboch.chessduck.feature.Analysis
import com.ivanherboch.chessduck.feature.ReviewComments
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.evalShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCommentsTest {
    private fun record(vsComputer: Boolean, playerWhite: Boolean, white: String, black: String) = GameRecord(
        date = 0, opponent = "", vsComputer = vsComputer, playerWhite = playerWhite, result = "0-1", outcome = -1, reason = "abandon",
        eloBefore = 1200, eloAfter = 1184, moves = "", whiteName = white, blackName = black, opponentElo = 1200,
    )

    // 1.e4 e5 2.Qh5 Nc6 3.Qxf7+ : la dame blanche se jette dans la gueule du roi.
    private val line = Line.fromSan(START_FEN, "e4 e5 Qh5 Nc6 Qxf7+ Kxf7".split(" "))
    private val analysis = listOf(
        Analysis(30, 20, "e2e4"), Analysis(30, 20, "e7e5"), Analysis(30, 25, "g1f3"), Analysis(-20, -30, "b8c6"),
        Analysis(10, 0, "f1c4"), Analysis(-800, -900, "e8f7"), Analysis(-820, null, "d2d4"),
    )

    @Test
    fun humanBlunderIsExplainedInSecondPerson() {
        val (text, tone) = ReviewComments.comment(record(true, true, "Ivan", "🤖 Ordinateur"), line, 4, analysis)
        assertEquals(Tone.BAD, tone)
        assertTrue(text, text.startsWith("Gaffe · Ivan (Blancs)"))
        assertTrue(text, "vous exposez votre dame" in text && "Kxf7" in text)
        assertTrue(text, "L'ordinateur (Noirs) a maintenant un avantage décisif" in text)
        // Aucune évaluation chiffrée, aucune référence à ce que « l'ordinateur préférait ».
        assertFalse(text, "Évaluation" in text || "préférait" in text)
    }

    @Test
    fun computerBlunderIsExplainedInThirdPerson() {
        val (text, _) = ReviewComments.comment(record(true, false, "🤖 Ordinateur", "Ivan"), line, 4, analysis)
        assertTrue(text, text.startsWith("Gaffe · L'ordinateur (Blancs)"))
        assertTrue(text, "il expose sa dame" in text)
        assertFalse(text, " vous " in text)
    }

    @Test
    fun twoPlayersAreNamed() {
        val g = record(false, true, "Ivan", "Léa")
        val (reply, tone) = ReviewComments.comment(g, line, 5, analysis)
        assertEquals(Tone.INFO, tone)
        assertTrue(reply, reply.startsWith("Léa (Noirs) reprend en capturant la dame en f7"))
        val (plain, _) = ReviewComments.comment(g, line, 0, analysis)
        assertTrue(plain, plain.startsWith("Ivan (Blancs) avance un pion au centre"))
        assertTrue(ReviewComments.ending(g).contains("victoire de Léa (Noirs) par abandon"))
    }

    @Test
    fun missedCaptureAndMissedMate() {
        // Les Blancs peuvent prendre une dame non défendue en d5 et jouent autre chose.
        val l = Line.fromSan("4k3/8/8/3q4/8/8/8/R2R2K1 w - - 0 1", listOf("Kf1"))
        val (text, _) = ReviewComments.comment(record(false, true, "Ivan", "Léa"), l, 0, listOf(Analysis(900, 0, "d1d5"), Analysis(-100, null, "e8e7")))
        assertTrue(text, "manqué l'occasion de capturer la dame en d5 avec Rxd5" in text)
        // Mat en 1 manqué : signalé ; un mat en 3 manqué ne l'est jamais comme tel.
        val m = Line.fromSan("6k1/5ppp/8/8/8/8/8/3R2K1 w - - 0 1", listOf("Kf2"))
        val (mate, tone) = ReviewComments.comment(record(false, true, "Ivan", "Léa"), m, 0, listOf(Analysis(100000 - 1, null, "d1d8"), Analysis(500, null, "g7g6")))
        assertEquals(Tone.BAD, tone)
        assertTrue(mate, mate.startsWith("⚠️ Mat en 1 manqué · Ivan (Blancs) : vous pouviez") && "Rd8#" in mate)
        val (far, _) = ReviewComments.comment(record(false, true, "Ivan", "Léa"), m, 0, listOf(Analysis(100000 - 5, null, "d1d8"), Analysis(500, null, "g7g6")))
        assertFalse(far, "Mat en" in far)
    }

    /** Cas signalé : un mat en 1 manqué, après lequel la jauge indique encore un mat en 2. Texte et jauge concordent. */
    @Test
    fun missedMateAgreesWithTheEvalBar() {
        // 1. f3 e5 2. g4 : Dh4 mate, mais les Noirs jouent Fe7 ; la jauge d'après indique M2 (analyse simulée).
        val line = Line.fromUci(START_FEN, "f2f3 e7e5 g2g4 f8e7")
        val analysis = listOf<Analysis?>(null, null, null, Analysis(-MATE + 1, null, "d8h4"), Analysis(-MATE + 4, null, "e7h4"))
        val (text, _) = ReviewComments.comment(record(true, true, "Ivan", ""), line, 3, analysis)
        assertEquals("M2", evalShort(analysis[4]!!.eval))
        assertTrue(text, text.startsWith("⚠️ Mat en 1 manqué"))
        assertTrue(text, "Qh4# matait immédiatement" in text)
        assertTrue(text, "Après Be7, il lui faut encore 2 coups pour mater." in text)
    }

    /** Pont Revue → Académie : la faute du joueur humain désigne la leçon et le thème à travailler. */
    @Test
    fun blundersPointToAnAcademyTopic() {
        val human = record(true, true, "Ivan", "")
        // Dame laissée en prise : « Pièce laissée en prise », leçon d'initiation et exercices « hangingPiece ».
        val hanging = ReviewComments.topic(human, line, 4, analysis)
        assertEquals(com.ivanherboch.chessduck.feature.ReviewTopic.HANGING_PIECE, hanging)
        assertEquals("hangingPiece", hanging!!.theme)
        assertEquals("Initiation : la pièce gratuite", com.ivanherboch.chessduck.content.Lessons.byId(hanging.lessonId).title)
        // Les coups de l'ordinateur et les bons coups ne proposent rien.
        assertEquals(null, ReviewComments.topic(record(true, false, "", "Ivan"), line, 4, analysis))
        assertEquals(null, ReviewComments.topic(human, line, 0, analysis))
        // Mat en 1 manqué, prise gagnante manquée.
        val m = Line.fromSan("6k1/5ppp/8/8/8/8/8/3R2K1 w - - 0 1", listOf("Kf2"))
        assertEquals(com.ivanherboch.chessduck.feature.ReviewTopic.MISSED_MATE,
            ReviewComments.topic(record(false, true, "Ivan", "Léa"), m, 0, listOf(Analysis(100000 - 1, null, "d1d8"), Analysis(500, null, "g7g6"))))
        val l = Line.fromSan("4k3/8/8/3q4/8/8/8/R2R2K1 w - - 0 1", listOf("Kf1"))
        assertEquals(com.ivanherboch.chessduck.feature.ReviewTopic.MISSED_CAPTURE,
            ReviewComments.topic(record(false, true, "Ivan", "Léa"), l, 0, listOf(Analysis(900, 0, "d1d5"), Analysis(-100, null, "e8e7"))))
    }

    /** Bilan : les erreurs des parties et les difficultés de l'Académie donnent au plus 3 priorités, les plus fréquentes d'abord. */
    @Test
    fun weakTopicsBalanceGamesAndAcademy() {
        val game = record(true, true, "Ivan", "").copy(moves = "e2e4 e7e5 d1h5 b8c6 h5f7 e8f7", analysis = Analysis.encodeAll(analysis))
        assertEquals(mapOf(com.ivanherboch.chessduck.feature.ReviewTopic.HANGING_PIECE to 1), com.ivanherboch.chessduck.feature.WeakTopics.fromGame(game))
        val struggling = com.ivanherboch.chessduck.data.LessonProgress(lessonId = 41, masteredAt = 0, errors = 3, practiceTotal = 5, practicePassed = 2)
        val fine = com.ivanherboch.chessduck.data.LessonProgress(lessonId = 42, masteredAt = 0, errors = 1)
        val p = com.ivanherboch.chessduck.feature.WeakTopics.compute(listOf(game, game.copy(id = 2)), listOf(struggling, fine))
        assertEquals(2, p.size)
        // La leçon du clouage (3 erreurs + 3 exercices manqués) passe avant la pièce en prise vue 2 fois.
        assertEquals(41, p[0].lessonId)
        assertTrue(p[0].reason, "3 erreurs en Défi" in p[0].reason && "3 exercices manqués" in p[0].reason)
        assertEquals("Pièce laissée en prise", p[1].title)
        assertTrue(p[1].reason, "2 fois" in p[1].reason)
    }
}
