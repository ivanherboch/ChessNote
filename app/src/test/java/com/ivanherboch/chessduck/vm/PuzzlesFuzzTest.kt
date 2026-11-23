package com.ivanherboch.chessduck.vm

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.feature.PuzzlesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Onglet Problèmes soumis à des actions au hasard (bons et mauvais coups, indice, navigation, Réessayer, nouveau
 * problème, filtres). Après chaque pause : jamais bloqué en attente de la réponse adverse, un problème joué jusqu'au
 * bout est résolu (et enregistré comme tel), et un problème résolu correctement peut toujours l'être.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PuzzlesFuzzTest {
    private val main = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    /** Quelques vrais problèmes de longueurs variées (2 à 8 demi-coups). */
    private val set = listOf(
        PUZZLE_0000D,
        PUZZLE_0000D.copy(id = "M1", fen = "6k1/5ppp/8/8/8/8/5PPP/R5K1 b - - 0 1", moves = "g8f8 a1a8", rating = 700, primaryTheme = "mateIn1"),
        PUZZLE_0000D.copy(id = "X3", fen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", moves = "b1c3 f6h5 g2g4 h5f4", rating = 1100, primaryTheme = "hangingPiece"),
    )

    @Test
    fun randomSessionsNeverGetStuck() = runTest(main) {
        for (p in set) Line.fromUci(p.fen, p.moves) // les problèmes du test sont valides
        val problems = LinkedHashSet<String>()
        for (seed in 1..60) {
            val r = Random(seed)
            val puzzles = FakePuzzles(set)
            val vm = PuzzlesViewModel(FakeSettings(), puzzles, FakeEngine())
            advanceUntilIdle()
            val log = ArrayList<String>()
            repeat(120) { step ->
                val s = vm.state.value
                val line = s.line
                val a = r.nextInt(100)
                log += when {
                    a < 45 && line != null && s.cursor < line.size -> { vm.onMove(line.moves[s.cursor]); "bon coup" }
                    a < 60 && line != null && s.cursor < line.size -> { s.position()?.legalMoves?.randomOrNull(r)?.let { vm.onMove(it) }; "coup au hasard" }
                    a < 66 -> { vm.hint(); "indice" }
                    a < 74 -> { vm.back(); "back" }
                    a < 80 -> { vm.forward(); "forward" }
                    a < 86 -> { vm.retry(); "réessayer" }
                    a < 88 -> { vm.holdRefutation(); "garder" }
                    a < 93 -> { vm.random(); "aléatoire" }
                    a < 95 -> { vm.revealTheme(); "thème" }
                    else -> { vm.setFilter(r.nextInt(5), listOf("", "mateIn1", "hangingPiece").random(r)); "filtre" }
                }
                if (r.nextInt(3) == 0) { advanceTimeBy(r.nextLong(0, 700)); runCurrent() } else advanceUntilIdle()
                val t = vm.state.value
                val l = t.line ?: return@repeat
                if (t.cursor > t.progress) problems += "seed $seed pas $step : curseur après la progression (${log.takeLast(4)})"
                if (t.progress > l.size) problems += "seed $seed pas $step : progression hors ligne (${log.takeLast(4)})"
                if (t.progress == l.size && !t.solved && t.refute == null) problems += "seed $seed pas $step : ligne finie mais pas résolu (${log.takeLast(4)})"
            }
            advanceUntilIdle()
            // Quel que soit l'état laissé, le problème affiché se termine en jouant la solution.
            vm.retry(); advanceUntilIdle()
            while (vm.state.value.cursor < vm.state.value.progress) { vm.forward() }
            var guard = 0
            while (!vm.state.value.solved && guard++ < 20) {
                val s = vm.state.value
                val l = s.line!!
                if (s.cursor >= l.size) break
                if (l.positions[s.cursor].whiteToMove != s.playerWhite) { problems += "seed $seed : bloqué en attente de la réponse adverse (${log.takeLast(4)})"; break }
                vm.onMove(l.moves[s.cursor]); advanceUntilIdle()
            }
            val end = vm.state.value
            if (!end.solved) problems += "seed $seed : solution jouée mais problème non résolu (curseur ${end.cursor}/${end.line?.size}, ${log.takeLast(4)})"
            else if (!end.arrowUsed && end.puzzle!!.id !in puzzles.solvedIds().first()) problems += "seed $seed : résolu mais non enregistré"
        }
        println(problems.joinToString("\n"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Reculer juste après un bon coup : la réponse adverse s'ajoute sans faire sauter l'échiquier en avant. */
    @Test
    fun goingBackRightAfterAGoodMoveKeepsTheBoardStill() = runTest(main) {
        val vm = PuzzlesViewModel(FakeSettings(), FakePuzzles(listOf(PUZZLE_0000D)), FakeEngine())
        advanceUntilIdle()
        val l = vm.state.value.line!!
        vm.onMove(l.moves[1]) // Td8, la réponse arrive 600 ms plus tard
        vm.back()
        advanceUntilIdle()
        assertTrue(vm.state.value.cursor == 1 && vm.state.value.progress == 3)
        vm.forward(); vm.forward()
        assertTrue(vm.state.value.cursor == 3)
        vm.onMove(l.moves[3]); advanceUntilIdle()
        assertTrue(vm.state.value.solved)
    }

    /** Problème terminé avec la flèche de l'indice 2/2 : pas compté comme résolu ; après une simple erreur, il compte. */
    @Test
    fun arrowHintDoesNotCountAsSolved() = runTest(main) {
        val repo = FakePuzzles(listOf(PUZZLE_0000D))
        val vm = PuzzlesViewModel(FakeSettings(), repo, FakeEngine())
        advanceUntilIdle()
        val l = vm.state.value.line!!
        vm.hint(); vm.hint()
        vm.onMove(l.moves[1]); advanceUntilIdle()
        vm.onMove(l.moves[3]); advanceUntilIdle()
        assertTrue(vm.state.value.solved)
        assertTrue(vm.state.value.message, vm.state.value.message.contains("ne compte pas"))
        assertTrue(repo.solvedIds().first().isEmpty())
        // Le même problème rouvert, résolu après une erreur : il compte.
        vm.openPuzzle(PUZZLE_0000D); advanceUntilIdle()
        val wrong = l.positions[1].legalMoves.first { it != l.moves[1] && !l.positions[1].play(it).isCheckmate }
        vm.onMove(wrong); vm.retry(); advanceUntilIdle()
        vm.onMove(l.moves[1]); advanceUntilIdle()
        vm.onMove(l.moves[3]); advanceUntilIdle()
        assertTrue(repo.solvedIds().first() == listOf(PUZZLE_0000D.id))
    }

    private fun com.ivanherboch.chessduck.feature.PuzzleState.position() = line?.positions?.getOrNull(cursor)
}
