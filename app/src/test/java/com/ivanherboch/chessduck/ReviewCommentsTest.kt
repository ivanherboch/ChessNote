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

    /** Fourchettes : détection géométrique, fourchette manquée dans une partie, leçon de la fourchette proposée. */
    @Test
    fun forksAreDetectedAndPointToForkLessons() {
        val p = com.ivanherboch.chessduck.chess.Position.fromFen("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1")
        assertTrue(ReviewComments.isFork(p, com.ivanherboch.chessduck.chess.Move.fromUci("b5c7"))) // roi et tour
        assertTrue(!ReviewComments.isFork(p, com.ivanherboch.chessduck.chess.Move.fromUci("b5d6"))) // le roi seul
        val l = Line.fromSan("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", listOf("Kd2"))
        val t = ReviewComments.topic(record(false, true, "Ivan", "Léa"), l, 0, listOf(Analysis(600, 0, "b5c7"), Analysis(0, null, "a8a1")))
        assertEquals(com.ivanherboch.chessduck.feature.ReviewTopic.MISSED_FORK, t)
        assertEquals("fork", com.ivanherboch.chessduck.content.Lessons.byId(t!!.lessonId).theme)
    }

    /** Bilan : récence, point travaillé (recul puis disparition temporaire) et rotation des leçons d'un même point faible. */
    @Test
    fun weakTopicsFollowRecencyAndWork() {
        val day = 86_400_000L
        val now = 100 * day
        val game = record(true, true, "Ivan", "").copy(moves = "e2e4 e7e5 d1h5 b8c6 h5f7 e8f7", analysis = Analysis.encodeAll(analysis), date = now - 5 * day)
        val WT = com.ivanherboch.chessduck.feature.WeakTopics
        val hanging = com.ivanherboch.chessduck.feature.ReviewTopic.HANGING_PIECE
        // Récence : la même erreur pèse plus dans une partie récente.
        val recent = WT.compute(listOf(game.copy(id = 1, date = now - day)), emptyList(), now = now)[0].score
        val older = WT.compute(listOf(game.copy(id = 2, date = now - day), game.copy(id = 3, date = now - 2 * day, analysis = "")), emptyList(), now = now)
        assertEquals(recent, older[0].score, 0.001)
        assertTrue(WT.compute(listOf(game.copy(id = 4), game.copy(id = 5)), emptyList(), now = now)[0].score < 2 * recent)
        // Travaillé hier, pas réapparu depuis : le point disparaît quelques jours.
        assertTrue(WT.compute(listOf(game), emptyList(), mapOf(hanging.lessonId to now - day), now).isEmpty())
        // Réapparu après le travail : il revient, en le disant.
        val again = WT.compute(listOf(game, game.copy(id = 6, date = now - 1000)), emptyList(), mapOf(hanging.lessonId to now - day), now)
        assertTrue(again[0].reason, "1 depuis votre entraînement" in again[0].reason)
        assertTrue(again[0].reason, "2 dernières parties" in again[0].reason)
        // Leçon de référence maîtrisée : une autre leçon du même thème est proposée.
        val mastered = listOf(com.ivanherboch.chessduck.data.LessonProgress(hanging.lessonId, masteredAt = 1))
        val next = WT.lessonFor(hanging, mastered)!!
        assertTrue(next.id != hanging.lessonId && next.theme == "hangingPiece")
    }

    /** Séance du jour : étapes cochées le jour même, série qui avance de jour en jour et repart à 1 après un trou. */
    @Test
    fun dailyLogCountsConsecutiveDays() {
        val req = setOf("lesson", "puzzles")
        var log = com.ivanherboch.chessduck.data.DailyLog()
        log = log.mark("lesson", 10, req)
        assertEquals(0, log.streakOn(10))
        log = log.mark("puzzles", 10, req)
        assertEquals(1, log.streakOn(10))
        assertEquals(1, log.streakOn(11)) // tient jusqu'au lendemain
        log = log.mark("lesson", 11, req).mark("puzzles", 11, req)
        assertEquals(2, log.streakOn(11))
        assertEquals(setOf("lesson", "puzzles"), log.doneOn(11))
        assertTrue(log.doneOn(12).isEmpty())
        assertEquals(0, log.streakOn(13)) // un jour manqué : la série est perdue
        log = log.mark("lesson", 14, req).mark("puzzles", 14, req)
        assertEquals(1, log.streakOn(14))
        assertEquals(log, com.ivanherboch.chessduck.data.DailyLog.decode(log.encode()))
    }

    /**
     * Cas signalé : dame attaquée par la dame adverse (mais défendue) et cavalier attaqué par un pion. Qe2 « sauve » la
     * dame mais perd le cavalier : le filet dit que la dame n'était pas en danger et conseille Qxd8+, qui ne perd rien.
     */
    @Test
    fun netReasonExplainsTheRealDangerAndTheBestMove() {
        // La partie de la capture : 1.e4 e5 2.Nf3 d5 3.d4 Nf6 4.Nc3 dxe4 5.dxe5 Be6.
        val line = Line.fromSan(START_FEN, "e4 e5 Nf3 d5 d4 Nf6 Nc3 dxe4 dxe5 Be6".split(" "))
        val p = line.last
        val qe2 = com.ivanherboch.chessduck.chess.Move.fromUci("d1e2")
        val r = ReviewComments.netReason(p, qe2, Analysis(0, 0, "d1d8"), Analysis(-300, null, "e4f3"))
        assertTrue(r.text, "était attaquée, mais pas en danger" in r.text)
        assertTrue(r.text, "exf3" in r.text)
        // Le coup conseillé est un échange de dames : dit tel quel (pas « qui ne perd rien », trompeur ici).
        assertTrue(r.text, "Coup conseillé : Qxd8+, qui échange les dames" in r.text)
        assertEquals(com.ivanherboch.chessduck.chess.Move.fromUci("d1d8"), r.best)
        assertEquals(com.ivanherboch.chessduck.feature.ReviewTopic.HANGING_PIECE, r.topic)
    }

    /**
     * Cas signalé : le cavalier c6, attaqué par la dame c7 et défendu une fois par la dame b5, tombera après f4 Bd7
     * (deux attaquants pour un défenseur) : le filet l'explique au lieu de « Bb2 était plus fort ».
     */
    @Test
    fun netReasonSeesAPieceThatWillFallAfterTheReply() {
        val p = com.ivanherboch.chessduck.chess.Position.fromFen("2b2k1r/2q2p1p/n1N1pnp1/pQ1p4/N7/P7/2PP1PPP/R1B1K2R w KQ - 0 15")
        val r = ReviewComments.netReason(p, com.ivanherboch.chessduck.chess.Move.fromUci("f2f4"), Analysis(500, 0, "c1b2"), Analysis(200, null, "c8d7"))
        assertEquals(com.ivanherboch.chessduck.feature.ReviewTopic.HANGING_PIECE, r.topic)
        assertTrue(r.text, "votre cavalier en c6 trop peu défendu" in r.text && "Bd7" in r.text)
    }

    /** Décision du filet : pat quand on gagne → arrêt ; pas d'arrêt sans danger concret ni quand on reste nettement gagnant. */
    @Test
    fun safetyNetDecisionRules() {
        val SN = com.ivanherboch.chessduck.feature.SafetyNet
        val mv = { u: String -> com.ivanherboch.chessduck.chess.Move.fromUci(u) }
        // Qf7 ?? pat (le roi h8 n'a plus de case), alors que Qg7# matait.
        val pat = com.ivanherboch.chessduck.chess.Position.fromFen("7k/4Q3/6K1/8/8/8/8/8 w - - 0 1")
        val d = SN.decide(pat, mv("e7f7"), Analysis(MATE - 1, null, "e7g7"), Analysis(0, null, ""))!!
        assertTrue(d.stop && "pat" in d.text)
        assertEquals(mv("e7g7"), d.best)
        // Dame en prise (Qg4 ?? Bxg4) : arrêt, avec le point faible.
        val l = Line.fromSan(START_FEN, "e4 d5".split(" "))
        val g = SN.decide(l.last, mv("d1g4"), Analysis(30, 0, "e4d5"), Analysis(-900, null, "c8g4"))!!
        assertEquals(com.ivanherboch.chessduck.feature.ReviewTopic.HANGING_PIECE, g.topic)
        // Même perte d'évaluation, mais aucun danger concret (coup tranquille) : pas d'arrêt.
        assertEquals(null, SN.decide(l.last, mv("a2a3"), Analysis(30, 0, "e4d5"), Analysis(-300, null, "g8f6")))
        // Toujours nettement gagnant après le coup (+9 → +6) : pas d'arrêt.
        assertEquals(null, SN.decide(l.last, mv("d1g4"), Analysis(900, 0, "e4d5"), Analysis(600, null, "c8g4")))
        // Partie déjà perdue : pas d'arrêt.
        assertEquals(null, SN.decide(l.last, mv("d1g4"), Analysis(-900, 0, "e4d5"), Analysis(-1800, null, "c8g4")))
    }

    /** Filet de sécurité : une gaffe arrêtée compte dans « À travailler », même sans partie enregistrée. */
    @Test
    fun netEventsFeedWeakTopics() {
        val now = 100 * 86_400_000L
        val p = com.ivanherboch.chessduck.feature.WeakTopics.compute(
            emptyList(), emptyList(), now = now,
            events = listOf(com.ivanherboch.chessduck.data.NetEvent("FORK_ALLOWED", now - 3_600_000), com.ivanherboch.chessduck.data.NetEvent("FORK_ALLOWED", now - 7_200_000)),
        )
        assertEquals("Fourchette subie", p.single().title)
        assertTrue(p[0].reason, "2 gaffes arrêtées par le filet" in p[0].reason)
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
        assertTrue(p[0].reason, "3 erreurs et 3 exercices manqués en pratique" in p[0].reason)
        assertEquals("Pièce laissée en prise", p[1].title)
        assertTrue(p[1].reason, "2 fois" in p[1].reason)
    }
}
