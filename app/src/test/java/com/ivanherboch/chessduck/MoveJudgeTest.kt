package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.engine.MATE
import com.ivanherboch.chessduck.engine.PvLine
import com.ivanherboch.chessduck.engine.SearchResult
import com.ivanherboch.chessduck.feature.MoveJudge
import com.ivanherboch.chessduck.feature.Verdict
import com.ivanherboch.chessduck.vm.FakeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Juge de coups de l'Académie, avec un faux moteur dont on impose les évaluations. */
class MoveJudgeTest {
    private val start = Position.fromFen(START_FEN)
    private val e4 = Move.fromUci("e2e4")
    private val d4 = Move.fromUci("d2d4")
    private val a3 = Move.fromUci("a2a3")

    /** Évaluation imposée de la position obtenue après [m], du point de vue de l'adversaire (camp au trait). */
    private fun FakeEngine.score(from: Position, m: Move, forOpponent: Int?) {
        val after = from.play(m)
        scripted[after.toFen().split(' ').take(4).joinToString(" ")] =
            forOpponent?.let { SearchResult(after.legalMoves.first(), listOf(PvLine(after.legalMoves.first(), it))) }
    }

    @Test
    fun exactEquivalentWorseAndUnavailable() = runBlocking {
        val engine = FakeEngine()
        val judge = MoveJudge(engine)
        assertEquals(Verdict.EXACT, judge.judge(start, e4, e4, endgame = false))
        assertEquals(0, engine.searches) // le coup attendu ne demande aucune recherche
        engine.score(start, e4, -30)
        engine.score(start, d4, -25)
        engine.score(start, a3, 60)
        assertEquals(Verdict.EQUIVALENT, judge.judge(start, d4, e4, endgame = false))
        assertEquals(Verdict.WORSE, judge.judge(start, a3, e4, endgame = false))
        // Coup illégal : jamais accepté.
        assertEquals(Verdict.WORSE, judge.judge(start, Move.fromUci("e2e5"), e4, endgame = false))
        // Ordinateur absent : seul le coup attendu passe.
        engine.score(start, d4, null)
        assertEquals(Verdict.UNAVAILABLE, judge.judge(start, d4, e4, endgame = false))
    }

    @Test
    fun multiPvLinesAreUsedFirst() = runBlocking {
        val engine = FakeEngine()
        // Les 4 meilleures suites de la position contiennent les deux coups : une seule recherche suffit.
        engine.scripted[START_FEN.split(' ').take(4).joinToString(" ")] = SearchResult(e4, listOf(PvLine(e4, 30), PvLine(d4, 25), PvLine(a3, -60)))
        val judge = MoveJudge(engine)
        assertEquals(Verdict.EQUIVALENT, judge.judge(start, d4, e4, endgame = false))
        assertEquals(1, engine.searches)
        assertEquals(Verdict.WORSE, judge.judge(start, a3, e4, endgame = false))
        assertEquals(2, engine.searches)
    }

    /** Cas signalé : dans le mat avec deux tours, Rh2 garde le mat mais ne suit pas l'escalier (Ra5+). */
    @Test
    fun slowerMateIsNotTheLessonMove() = runBlocking {
        val pos = Position.fromFen("8/8/8/3k4/8/8/8/R3K2R w - - 0 1")
        val ra5 = Move.fromUci("a1a5")
        val rh2 = Move.fromUci("h1h2")
        val engine = FakeEngine()
        engine.scripted[pos.toFen().split(' ').take(4).joinToString(" ")] = SearchResult(ra5, listOf(PvLine(ra5, MATE - 9), PvLine(rh2, MATE - 13)))
        assertEquals(Verdict.SLOWER, MoveJudge(engine).judge(pos, rh2, ra5, endgame = true))
    }

    @Test
    fun matesAreAlwaysGoodMoves() = runBlocking {
        // Mat du berger : tout mat immédiat est accepté, même si la leçon montrait un autre coup.
        val pos = Position.fromFen("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 4 4")
        val mate = Move.fromUci("f3f7")
        assertTrue(pos.play(mate).isCheckmate)
        assertEquals(Verdict.EQUIVALENT, MoveJudge(FakeEngine()).judge(pos, mate, Move.fromUci("b1c3"), endgame = false))
    }

    @Test
    fun endgameRulesKeepTheTheoreticalResult() {
        // Mat forcé : un mat aussi rapide est équivalent, un mat plus lent ne suit pas la méthode, un simple gain ne suffit pas.
        assertEquals(Verdict.EQUIVALENT, MoveJudge.compare(MATE - 9, MATE - 9, endgame = true))
        assertEquals(Verdict.SLOWER, MoveJudge.compare(MATE - 11, MATE - 9, endgame = true))
        assertEquals(Verdict.WORSE, MoveJudge.compare(900, MATE - 9, endgame = true))
        // Hors finale aussi : gagner une dame au lieu de mater n'est pas « aussi bon ».
        assertEquals(Verdict.WORSE, MoveJudge.compare(900, MATE - 3, endgame = false))
        // Gain décisif (≥ 95 %) : on ne doit pas retomber sous 90 % (pat, gain gâché).
        assertTrue(MoveJudge.acceptable(700, 900, endgame = true))
        assertFalse(MoveJudge.acceptable(0, 900, endgame = true))
        // Nulle théorique : au plus 12 points de pourcentage perdus.
        assertTrue(MoveJudge.acceptable(-40, 0, endgame = true))
        assertFalse(MoveJudge.acceptable(-300, 0, endgame = true))
        // Hors finale : au plus 6 points perdus.
        assertTrue(MoveJudge.acceptable(-20, 20, endgame = false))
        assertFalse(MoveJudge.acceptable(-150, 20, endgame = false))
    }
}
