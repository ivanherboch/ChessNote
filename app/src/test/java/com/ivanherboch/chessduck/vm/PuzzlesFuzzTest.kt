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
                if (t.solved && t.progress != l.size) problems += "seed $seed pas $step : résolu avant la fin de la ligne (${log.takeLast(4)})"
                if (t.cursor < t.progress && vm.canPlay(t)) problems += "seed $seed pas $step : jouable depuis un coup précédent (${log.takeLast(4)})"
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

    /** « S'entraîner sur ce thème » arrivé pendant le chargement du problème de départ : le filtre et le tirage gagnent. */
    @Test
    fun trainDuringStartupIsNotOverwritten() = runTest(main) {
        val mate = PUZZLE_0000D.copy(id = "M1", fen = "6k1/5ppp/8/8/8/8/5PPP/R5K1 b - - 0 1", moves = "g8f8 a1a8", rating = 700, primaryTheme = "mateIn1")
        val prefs = FakeSettings(com.ivanherboch.chessduck.data.Settings(puzzleId = PUZZLE_0000D.id, puzzleTheme = "", puzzleBand = 0))
        val vm = PuzzlesViewModel(prefs, FakePuzzles(listOf(PUZZLE_0000D, mate)), FakeEngine())
        vm.train("mateIn1", 1) // avant même que la coroutine de départ ait lu les réglages
        advanceUntilIdle()
        assertTrue(vm.state.value.theme == "mateIn1" && vm.state.value.band == 1)
        assertTrue(vm.state.value.puzzle?.id == "M1")
    }

    /** Thème visible seulement si le filtre est précisément celui du problème ; sinon masqué jusqu'à la résolution. */
    @Test
    fun themeIsNotRevealedOutsideItsFilter() = runTest(main) {
        val vm = PuzzlesViewModel(FakeSettings(com.ivanherboch.chessduck.data.Settings(puzzleTheme = "fork")), FakePuzzles(listOf(PUZZLE_0000D)), FakeEngine())
        advanceUntilIdle()
        // Le filtre « fourchette » ne trouve rien : on ouvre 0000D (thème principal différent) depuis les récents.
        vm.openPuzzle(PUZZLE_0000D); advanceUntilIdle()
        assertTrue(vm.state.value.theme == "fork" && !vm.state.value.themeVisible)
        vm.setFilter(0, PUZZLE_0000D.primaryTheme); advanceUntilIdle()
        assertTrue(vm.state.value.themeVisible)
    }

    /** Recherche : une saisie qui n'est pas un identifiant n'interroge pas la base ; la frappe rapide ne fait qu'une requête. */
    @Test
    fun searchIsDebouncedAndSkipsThemeWords() = runTest(main) {
        val repo = FakePuzzles(listOf(PUZZLE_0000D))
        val vm = PuzzlesViewModel(FakeSettings(), repo, FakeEngine())
        advanceUntilIdle()
        assertTrue(!PuzzlesViewModel.looksLikeId("fourchette") && PuzzlesViewModel.looksLikeId("#00sHx") && !PuzzlesViewModel.looksLikeId("0"))
        vm.search("fourchette"); advanceUntilIdle()
        assertTrue(repo.searches == 0 && vm.suggestions.value.items.isEmpty())
        vm.search("00"); vm.search("000"); vm.search("0000"); advanceUntilIdle()
        assertTrue("${repo.searches} requêtes", repo.searches == 1)
        assertTrue(vm.suggestions.value.query == "0000" && vm.suggestions.value.items.single().id == "0000D")
    }

    /** Fin avec la flèche de l'indice : ton doré, code 3 (pas de vibration de réussite), problème non compté. */
    @Test
    fun arrowFinishIsGoldAndNotCounted() = runTest(main) {
        val repo = FakePuzzles(listOf(PUZZLE_0000D))
        val vm = PuzzlesViewModel(FakeSettings(), repo, FakeEngine())
        advanceUntilIdle()
        val l = vm.state.value.line!!
        vm.onMove(l.moves[1]); advanceUntilIdle()
        vm.hint(); vm.hint()
        assertTrue(vm.onMove(l.moves[3]) == 3)
        advanceUntilIdle()
        assertTrue(vm.state.value.tone == com.ivanherboch.chessduck.ui.Tone.GOLD && repo.solvedIds().first().isEmpty())
        // Déjà résolu auparavant : le message le dit, il reste compté.
        repo.markSolved(PUZZLE_0000D.id)
        vm.openPuzzle(PUZZLE_0000D); advanceUntilIdle()
        assertTrue(vm.state.value.alreadySolved)
        vm.onMove(l.moves[1]); advanceUntilIdle(); vm.hint(); vm.hint(); vm.onMove(l.moves[3]); advanceUntilIdle()
        assertTrue(vm.state.value.message, vm.state.value.message.contains("déjà résolu"))
    }

    /** Coup illégal ou rejoué depuis un coup précédent : refusé ; une erreur ne raccourcit pas la ligne déjà jouée. */
    @Test
    fun movesOnlyCountAtTheEndOfThePlayedLine() = runTest(main) {
        val vm = PuzzlesViewModel(FakeSettings(), FakePuzzles(listOf(PUZZLE_0000D)), FakeEngine())
        advanceUntilIdle()
        val l = vm.state.value.line!!
        assertTrue(vm.onMove(com.ivanherboch.chessduck.chess.Move.fromUci("a1a8")) == 0) // illégal
        vm.onMove(l.moves[1]); advanceUntilIdle()
        assertTrue(vm.state.value.progress == 3)
        vm.back(); vm.back()
        assertTrue(!vm.canPlay(vm.state.value) && vm.onMove(l.moves[1]) == 0)
        vm.forward(); vm.forward()
        val wrong = l.positions[3].legalMoves.first { it != l.moves[3] && !l.positions[3].play(it).isCheckmate }
        assertTrue(vm.onMove(wrong) == -1)
        assertTrue(vm.state.value.progress == 3)
    }

    /** Réfutation : prise en passant nommée comme une prise de pion ; moteur muet : aucune réponse inventée. */
    @Test
    fun refutationNamesEnPassantAndNeverInventsAReply() = runTest(main) {
        val engine = FakeEngine()
        // Les Blancs jouent d4?? : exd3 e.p. (scripté) répond.
        val pos = com.ivanherboch.chessduck.chess.Position.fromFen("4k3/8/8/8/4p3/8/3P4/4K3 w - - 0 1")
        val m = com.ivanherboch.chessduck.chess.Move.fromUci("d2d4")
        val after = pos.play(m)
        fun key(p: com.ivanherboch.chessduck.chess.Position) = p.toFen().split(' ').take(4).joinToString(" ")
        val ep = com.ivanherboch.chessduck.chess.Move.fromUci("e4d3")
        engine.scripted[key(after)] = com.ivanherboch.chessduck.engine.SearchResult(ep, listOf(com.ivanherboch.chessduck.engine.PvLine(ep, 100)))
        val r = com.ivanherboch.chessduck.feature.RefutationLogic.reply(engine, pos, m, minShow = 0)
        assertTrue(r.text, r.reply == ep && r.text.contains("gagnent un pion"))
        engine.scripted[key(after)] = null
        val none = com.ivanherboch.chessduck.feature.RefutationLogic.reply(engine, pos, m, minShow = 0)
        assertTrue(none.text, none.reply == null && none.text.contains("à temps"))
        // Le coup du joueur matait aussi, plus lentement : pas de « pièce capturée ».
        val slow = com.ivanherboch.chessduck.chess.Move.fromUci("e1e2")
        val a2 = pos.play(slow)
        val k = a2.legalMoves.first()
        engine.scripted[key(a2)] = com.ivanherboch.chessduck.engine.SearchResult(k, listOf(com.ivanherboch.chessduck.engine.PvLine(k, -(com.ivanherboch.chessduck.engine.MATE - 5))))
        val r2 = com.ivanherboch.chessduck.feature.RefutationLogic.reply(engine, pos, slow, minShow = 0)
        assertTrue(r2.text, r2.text.contains("mate aussi"))
    }

    private fun com.ivanherboch.chessduck.feature.PuzzleState.position() = line?.positions?.getOrNull(cursor)
}
