package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.isWhite
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.engine.Stockfish
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.feature.EndgameFamily
import com.ivanherboch.chessduck.feature.EndgameGoal
import com.ivanherboch.chessduck.feature.EndgamePositions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** Générateurs de finales jouables : positions légales, au bon camp, au bon matériel ; vérifiées par Stockfish (REVIEW_SF). */
class EndgamePracticeTest {
    private fun material(p: com.ivanherboch.chessduck.chess.Position): String =
        (0 until 64).filter { p[it] != 0 }.map { q -> "WB"[if (isWhite(p[q])) 0 else 1] + "?PNBRQK"[pieceType(p[q])].toString() }.sorted().joinToString(",")

    private val expected = mapOf(
        EndgameFamily.KQK to "BK,WK,WQ", EndgameFamily.KRK to "BK,WK,WR", EndgameFamily.KRRK to "BK,WK,WR,WR",
        EndgameFamily.KBBK to "BK,WB,WB,WK", EndgameFamily.KPK_WIN to "BK,WK,WP", EndgameFamily.LUCENA to "BK,BR,WK,WP,WR",
        EndgameFamily.PHILIDOR_DRAW to "BK,BR,WK,WP,WR", EndgameFamily.ROOK_PAWN_DRAW to "BK,WK,WP",
        EndgameFamily.KQKP to "BK,BP,WK,WQ", EndgameFamily.QUEEN_BISHOP_PAWN_DRAW to "BK,BP,WK,WQ",
        EndgameFamily.VANCURA_DRAW to "BK,BR,WK,WP,WR",
    )

    @Test
    fun generatedPositionsAreLegalAndVaried() {
        for (f in EndgameFamily.entries) {
            val seen = HashSet<String>()
            repeat(40) { seed ->
                val p = EndgamePositions.generate(f, Random(seed))
                assertEquals(f.name, f.heroWhite, p.whiteToMove)
                assertFalse(f.name, p.inCheck)
                assertTrue(f.name, p.legalMoves.isNotEmpty())
                assertEquals(f.name, expected.getValue(f), material(p))
                val wk = p.kingSquare(true); val bk = p.kingSquare(false)
                assertTrue(f.name, max(abs(fileOf(wk) - fileOf(bk)), abs(rankOf(wk) - rankOf(bk))) >= 2)
                if (f == EndgameFamily.KBBK) {
                    val bishops = (0 until 64).filter { pieceType(p[it]) == BISHOP }
                    assertEquals(f.name, 2, bishops.map { (fileOf(it) + rankOf(it)) % 2 }.toSet().size) // fous de couleurs opposées
                }
                seen.add(p.toFen())
            }
            assertTrue("${f.name} : positions trop répétitives", seen.size >= 10)
        }
        // Les constantes de pièces restent celles du moteur de règles.
        assertEquals(listOf(1, 2, 3, 4, 5, 6), listOf(PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING))
    }

    /** Outil (inactif sans REVIEW_SF) : chaque famille gagne ou fait nulle selon la théorie, d'après Stockfish. */
    @Test
    fun theoryCheckedByStockfish() = runBlocking {
        val sf = System.getenv("REVIEW_SF") ?: return@runBlocking
        val engine = Stockfish(File(sf))
        val problems = ArrayList<String>()
        for (f in EndgameFamily.entries) repeat(12) { seed ->
            val p = EndgamePositions.generate(f, Random(1000 + seed))
            val score = engine.search(p.toFen(), 700).score ?: return@repeat
            val ok = when (f.goal) {
                EndgameGoal.WIN -> (isMateScore(score) && score > 0) || score >= 250
                EndgameGoal.DRAW -> score >= -150
            }
            if (!ok) problems.add("${f.name} ${p.toFen()} : $score")
        }
        engine.close()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
