package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.chess.GameEnd
import com.ivanherboch.chessduck.feature.Analysis
import com.ivanherboch.chessduck.feature.GameLogic
import com.ivanherboch.chessduck.feature.NotationExercise
import com.ivanherboch.chessduck.feature.NotationLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChessTest {
    private fun perft(p: Position, d: Int): Long {
        if (d == 0) return 1
        var n = 0L
        for (m in p.legalMoves) n += perft(p.play(m), d - 1)
        return n
    }

    @Test
    fun perftReferencePositions() {
        assertEquals(197281, perft(Position.fromFen(START_FEN), 4))
        assertEquals(97862, perft(Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"), 3))
        assertEquals(43238, perft(Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"), 4))
        assertEquals(9467, perft(Position.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"), 3))
        assertEquals(62379, perft(Position.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"), 3))
    }

    @Test
    fun sanAndFen() {
        val l = Line.fromSan(START_FEN, "e4 e5 Nf3 Nc6 Bb5 a6 Bxc6 dxc6 O-O f6 d4 exd4 Nxd4 c5 Nb3 Qxd1 Rxd1".split(" "))
        assertEquals("r1b1kbnr/1pp3pp/p4p2/2p5/4P3/1N6/PPP2PPP/RNBR2K1 b kq - 0 9", l.last.toFen())
        val p = Position.fromFen("3r2k1/5ppp/8/8/8/8/5PPP/R2R2K1 w - - 0 1")
        assertEquals("Rxd8#", p.san(Move.fromUci("d1d8")))
        assertEquals("Rab1", p.san(Move.fromUci("a1b1")))
        val promo = Position.fromFen("8/4P1k1/8/8/8/8/1p4K1/8 w - - 0 1")
        assertEquals("e8=N+", promo.san(Move.fromUci("e7e8n")))
        assertEquals(Move.fromUci("e7e8q"), promo.parseSan("e8=Q"))
        val ep = Line.fromSan(START_FEN, "e4 a6 e5 d5 exd6".split(" "))
        assertEquals(0, ep.last[35])
        val fool = Line.fromSan(START_FEN, "f3 e5 g4 Qh4#".split(" "))
        assertEquals(GameEnd.CHECKMATE, fool.end())
        assertEquals("N5f3", Position.fromFen("4k3/8/8/6N1/8/8/8/4K1N1 w - - 0 1").san(Move.fromUci("g5f3")))
        assertEquals("Qh4e1+", Position.fromFen("4k3/8/8/8/4Q2Q/8/8/K6Q b - - 0 1".replace(" b ", " w ")).san(Move.fromUci("h4e1")))
    }

    @Test
    fun notationDiagnostics() {
        val p = Position.fromFen("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3")
        val m = Move.fromUci("f3e5")
        val ex = NotationExercise(p, m, p.san(m), 2)
        assertEquals("Nxe5", ex.san)
        assertTrue("x" in NotationLogic.diagnose("Ne5", ex))
        assertTrue("Knight" in NotationLogic.diagnose("Kxe5", ex))
        assertTrue("échec" in NotationLogic.diagnose("Nxe5+", ex))
        val pawn = NotationExercise(p, Move.fromUci("d2d4"), "d4", 1)
        assertTrue("Pe4" in NotationLogic.diagnose("Pd4", pawn))
        assertEquals(1, NotationLogic.levelOf(p, pawn.move, "d4"))
        assertEquals(2, NotationLogic.levelOf(p, m, "Nxe5"))
    }

    @Test
    fun eloAndAnnotations() {
        assertEquals(1216, GameLogic.newElo(1200, 1200, 1.0))
        assertEquals(1184, GameLogic.newElo(1200, 1200, 0.0))
        assertEquals(1200, GameLogic.newElo(1200, 1200, 0.5))
        val p = Position.fromFen(START_FEN)
        val m = Move.fromUci("e2e4")
        // Coup normal : aucune annotation.
        assertNull(GameLogic.annotate(p, m, Analysis(30, 25, "e2e4"), Analysis(28, 20, "e7e5"), null))
        // Grosse perte d'évaluation : gaffe.
        assertEquals("??", GameLogic.annotate(p, m, Analysis(30, 25, "d2d4"), Analysis(-600, null, "e7e5"), null))
        // Seul bon coup : « ! ».
        assertEquals("!", GameLogic.annotate(p, m, Analysis(150, -200, "e2e4"), Analysis(150, 0, "e7e5"), null))
        val round = Analysis.decodeAll(Analysis.encodeAll(listOf(Analysis(12, null, "e2e4"), null, Analysis(-3, 4, ""))), 3)
        assertEquals(Analysis(12, null, "e2e4"), round[0]); assertNull(round[1]); assertNotNull(round[2])
    }

    @Test
    fun capturedMaterial() {
        val start = com.ivanherboch.chessduck.ui.materialOf(Position.fromFen(START_FEN))
        assertEquals(0, start.diff); assertTrue(start.blackLost.isEmpty() && start.whiteLost.isEmpty())
        // Les Noirs ont perdu deux pions, un cavalier et la dame ; les Blancs une tour.
        val m = com.ivanherboch.chessduck.ui.materialOf(Position.fromFen("r1b1kb1r/pppppp2/8/8/8/8/PPPPPPPP/RNBQKBN1 w Qkq - 0 1"))
        assertEquals(listOf(9, 9, 10, 10, 13), m.blackLost)
        assertEquals(listOf(4), m.whiteLost)
        assertEquals(1 + 1 + 3 + 3 + 9 - 5, m.diff)
    }

    @Test
    fun missedMates() {
        val mate1 = Analysis(100000 - 1, null, "d1d8")        // les Blancs ont un mat en 1
        assertEquals(1, GameLogic.missedMate(true, mate1, Analysis(300, null, "a7a6")))
        assertNull(GameLogic.missedMate(true, mate1, Analysis(100000, null, "")))           // mat joué
        val mate2 = Analysis(-(100000 - 3), null, "h4h2")     // les Noirs ont un mat en 2
        assertEquals(2, GameLogic.missedMate(false, mate2, Analysis(-250, null, "g1h1")))
        assertNull(GameLogic.missedMate(false, mate2, Analysis(-(100000 - 2), null, "g1h1"))) // mat en 1 conservé
        assertNull(GameLogic.missedMate(true, Analysis(100000 - 5, null, "x"), Analysis(0, null, "y"))) // mat en 3 : non signalé
    }

    /** Validation croisée du générateur de coups sur les 50 000 problèmes embarqués (si l'export est disponible). */
    @Test
    fun allEmbeddedPuzzlesAreLegal() {
        val f = File(System.getenv("PUZZLE_DUMP") ?: "/opt/work/puzzles_dump.txt")
        if (!f.exists()) return
        var n = 0
        f.forEachLine { row ->
            val (id, fen, moves) = row.split('|')
            val line = Line(fen)
            for (u in moves.split(' ')) {
                val m = Move.fromUci(u)
                assertTrue("Problème $id : $u illégal", line.last.isLegal(m))
                line.push(m)
            }
            assertTrue("Problème $id : position initiale déjà terminée", line.positions[0].legalMoves.isNotEmpty())
            n++
        }
        assertEquals(50_000, n)
    }
}
