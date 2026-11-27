package com.ivanherboch.chessduck.vm

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.feature.PuzzlesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
                    a < 97 -> { vm.solution(); "solution" }
                    a < 98 -> { vm.restart(); "recommencer" }
                    a < 99 -> { vm.goTo(r.nextInt(0, (s.progress + 1).coerceAtLeast(1))); "bandeau" }
                    else -> { vm.setFilter(r.nextInt(5), listOf("", "mateIn1", "hangingPiece").random(r)); "filtre" }
                }
                if (r.nextInt(3) == 0) { advanceTimeBy(r.nextLong(0, 700)); runCurrent() } else advanceUntilIdle()
                val t = vm.state.value
                val l = t.line ?: return@repeat
                if (t.cursor > t.progress) problems += "seed $seed pas $step : curseur après la progression (${log.takeLast(4)})"
                if (t.progress > l.size) problems += "seed $seed pas $step : progression hors ligne (${log.takeLast(4)})"
                if (t.progress == l.size && !t.solved && !t.revealed && t.refute == null) problems += "seed $seed pas $step : ligne finie mais pas résolu (${log.takeLast(4)})"
                if (t.revealed && t.solved) problems += "seed $seed pas $step : solution dévoilée comptée comme résolue (${log.takeLast(4)})"
                if (t.revealed && vm.canPlay(t)) problems += "seed $seed pas $step : jouable après la solution (${log.takeLast(4)})"
                                if (t.solved && t.progress != l.size) problems += "seed $seed pas $step : résolu avant la fin de la ligne (${log.takeLast(4)})"
                if (t.cursor < t.progress && vm.canPlay(t)) problems += "seed $seed pas $step : jouable depuis un coup précédent (${log.takeLast(4)})"
            }
            advanceUntilIdle()
            // Quel que soit l'état laissé, le problème affiché se termine en jouant la solution.
            vm.retry(); advanceUntilIdle()
            if (vm.state.value.revealed) { vm.restart(); advanceUntilIdle() }
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

    /** « Solution » après une erreur : toute la ligne se déroule, navigable, sans compter le problème ; « Recommencer » remet tout à zéro. */
    @Test
    fun solutionRevealsTheLineWithoutCountingAndRestartResets() = runTest(main) {
        val repo = FakePuzzles(listOf(PUZZLE_0000D))
        val vm = PuzzlesViewModel(FakeSettings(), repo, FakeEngine())
        advanceUntilIdle()
        val l = vm.state.value.line!!
        vm.solution() // pas encore d'erreur : sans effet
        assertTrue(!vm.state.value.revealed)
        val wrong = l.positions[1].legalMoves.first { it != l.moves[1] && !l.positions[1].play(it).isCheckmate }
        vm.onMove(wrong); vm.retry(); advanceUntilIdle()
        assertTrue(vm.state.value.hadError)
        vm.hint(); vm.hint()
        vm.solution(); advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.revealed && !s.solved && s.progress == l.size && s.cursor == l.size && s.themeVisible)
        assertTrue(s.message, s.message.contains("ne compte pas"))
        assertTrue(repo.solvedIds().first().isEmpty() && !vm.canPlay(s))
        vm.goTo(1); assertTrue(vm.state.value.cursor == 1)
        vm.forward(); vm.forward(); assertTrue(vm.state.value.cursor == 3)
        vm.restart(); advanceUntilIdle()
        val r = vm.state.value
        assertTrue(!r.revealed && !r.hadError && !r.arrowUsed && r.hintStage == 0 && r.progress == 1)
        vm.onMove(l.moves[1]); advanceUntilIdle(); vm.onMove(l.moves[3]); advanceUntilIdle()
        assertTrue(vm.state.value.solved && repo.solvedIds().first() == listOf(PUZZLE_0000D.id))
    }

    /** Tous les problèmes du filtre déjà résolus : le tirage en repropose un, et le dit. */
    @Test
    fun drawingWhenEverythingIsSolvedSaysSo() = runTest(main) {
        val other = PUZZLE_0000D.copy(id = "0000E")
        val repo = FakePuzzles(listOf(PUZZLE_0000D, other))
        val vm = PuzzlesViewModel(FakeSettings(), repo, FakeEngine())
        advanceUntilIdle()
        assertTrue(!vm.state.value.allSolvedInFilter)
        repo.markSolved(PUZZLE_0000D.id); repo.markSolved(other.id)
        vm.random(); advanceUntilIdle()
        assertTrue(vm.state.value.allSolvedInFilter && vm.state.value.message.contains("résolu tous"))
    }

    // ---------------------------------------------------------------- Étape 3 : progression adaptative

    /** ELO tactique : formule Elo (K = 32), bornée. */
    @Test
    fun tacticEloFollowsTheEloFormula() {
        assertTrue(PuzzlesViewModel.tacticElo(1200, 1200, 1.0) == 1216)
        assertTrue(PuzzlesViewModel.tacticElo(1200, 1200, 0.0) == 1184)
        assertTrue(PuzzlesViewModel.tacticElo(1200, 1600, 1.0) > 1225) // problème difficile réussi : gros gain
        assertTrue(PuzzlesViewModel.tacticElo(PuzzlesViewModel.ELO_MIN, 3000, 0.0) == PuzzlesViewModel.ELO_MIN)
    }

    /** Suivi : résolu net (compté, rien à refaire), après une erreur (compté, à refaire demain), abandon (non compté, à refaire). */
    @Test
    fun attemptsArePlannedForRedo() {
        val at = 1_000_000L
        val d = com.ivanherboch.chessduck.data.PuzzleProgress.REDO_DELAY
        val o = com.ivanherboch.chessduck.data.PuzzleOutcome
        val empty = com.ivanherboch.chessduck.data.PuzzleProgress("X", 0, bookmarked = true)
        val gaveUp = com.ivanherboch.chessduck.data.PuzzleProgress.after(empty, o.GAVE_UP, at)
        assertTrue(gaveUp.solvedAt == 0L && gaveUp.nextReviewAt == at + d && gaveUp.attempts == 1 && gaveUp.bookmarked)
        val err = com.ivanherboch.chessduck.data.PuzzleProgress.after(gaveUp, o.WITH_ERROR, at + 5)
        assertTrue(err.solvedAt == at + 5 && err.nextReviewAt == at + 5 + d && err.attempts == 2)
        // Résolu net alors qu'il était à refaire : intervalle suivant (7 jours), puis 30, 90, et retiré de la liste.
        val clean = com.ivanherboch.chessduck.data.PuzzleProgress.after(err, o.CLEAN, at + 9)
        assertTrue(clean.solvedAt == at + 5 && clean.nextReviewAt == at + 9 + 7 * d && clean.status == o.CLEAN && clean.bookmarked)
        var p = clean
        var t = at + 9
        for (days in listOf(30, 90)) { t += 1; p = com.ivanherboch.chessduck.data.PuzzleProgress.after(p, o.CLEAN, t); assertTrue(p.nextReviewAt == t + days * d) }
        p = com.ivanherboch.chessduck.data.PuzzleProgress.after(p, o.CLEAN, t + 1)
        assertTrue(p.nextReviewAt == 0L)
        // Une erreur garde l'intervalle en cours ; un abandon le remet à un jour.
        val at7 = com.ivanherboch.chessduck.data.PuzzleProgress.after(clean, o.WITH_ERROR, at + 20)
        assertTrue(at7.nextReviewAt == at + 20 + 7 * d)
        assertTrue(com.ivanherboch.chessduck.data.PuzzleProgress.after(at7, o.GAVE_UP, at + 30).nextReviewAt == at + 30 + d)
        // Résolu net du premier coup : rien à refaire.
        assertTrue(com.ivanherboch.chessduck.data.PuzzleProgress.after(empty, o.CLEAN, at).nextReviewAt == 0L)
    }

    /** Problèmes : ELO tactique compté une fois, erreurs et abandons à refaire, résolution nette qui les retire, favoris. */
    @Test
    fun outcomesUpdateEloRedoAndBookmarks() = runTest(main) {
        val repo = FakePuzzles(listOf(PUZZLE_0000D))
        val prefs = FakeSettings(com.ivanherboch.chessduck.data.Settings(elo = 1300))
        val vm = PuzzlesViewModel(prefs, repo, FakeEngine())
        advanceUntilIdle()
        assertTrue(vm.state.value.puzzleElo == 1300 && prefs.flow.value.puzzleElo == 1300) // parti de l'ELO du Jeu Libre
        val l = vm.state.value.line!!
        val wrong = l.positions[1].legalMoves.first { it != l.moves[1] && !l.positions[1].play(it).isCheckmate }
        vm.onMove(wrong); vm.retry(); advanceUntilIdle()
        val afterError = vm.state.value.puzzleElo
        assertTrue(afterError < 1300 && prefs.flow.value.puzzleElo == afterError)
        vm.onMove(l.moves[1]); advanceUntilIdle(); vm.onMove(l.moves[3]); advanceUntilIdle()
        assertTrue(vm.state.value.puzzleElo == afterError) // déjà compté : pas de second calcul
        val p1 = repo.progress.value[PUZZLE_0000D.id]!!
        assertTrue(p1.status == com.ivanherboch.chessduck.data.PuzzleOutcome.WITH_ERROR && p1.solvedAt > 0 && p1.nextReviewAt > 0)
        assertTrue(repo.toRedo().first().single().first.id == PUZZLE_0000D.id)
        // Recommencer et résoudre net : la révision s'espace (7 jours, comme dans l'Académie), sans nouveau calcul d'ELO.
        vm.restart(); advanceUntilIdle()
        vm.onMove(l.moves[1]); advanceUntilIdle(); vm.onMove(l.moves[3]); advanceUntilIdle()
        val p2 = repo.progress.value[PUZZLE_0000D.id]!!
        assertTrue(p2.nextReviewAt - p2.lastTriedAt == 7 * com.ivanherboch.chessduck.data.PuzzleProgress.REDO_DELAY && vm.state.value.puzzleElo == afterError)
        // Favori.
        val bm = backgroundScope.launch { vm.bookmarked.collect {} }
        vm.toggleBookmark(); advanceUntilIdle()
        assertTrue(vm.bookmarked.value && repo.progress.value[PUZZLE_0000D.id]!!.bookmarked)
        vm.toggleBookmark(); advanceUntilIdle()
        assertTrue(!vm.bookmarked.value)
        bm.cancel()
    }

    /** Partition stricte par défaut ; « thèmes secondaires » : le thème choisi peut être secondaire (tirage, compteurs, affichage). */
    @Test
    fun secondaryThemesWidenTheFilter() = runTest(main) {
        val secondaryFork = PUZZLE_0000D.copy(id = "SF", themes = " crushing fork middlegame pin short ", primaryTheme = "pin")
        val repo = FakePuzzles(listOf(secondaryFork))
        val vm = PuzzlesViewModel(FakeSettings(), repo, FakeEngine())
        advanceUntilIdle()
        vm.setFilter(0, "fork"); advanceUntilIdle()
        assertTrue(vm.state.value.empty) // thème principal seulement : aucun problème « fourchette »
        vm.setSecondary(true); vm.random(); advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.puzzle?.id == "SF" && s.filterMatches && s.themeVisible)
        assertTrue(repo.themeCounts(0..4000, secondary = true).any { it.theme == "fork" && it.total == 1 })
        assertTrue(repo.themeCounts(0..4000, secondary = false).none { it.theme == "fork" })
        // « Tous les thèmes » et les familles comptent les problèmes distincts, même avec plusieurs thèmes chacun.
        vm.loadCounts(0); advanceUntilIdle()
        assertTrue(vm.counts.value[""]?.total == 1)
        assertTrue(vm.counts.value.filterKeys { it != "" && it !in com.ivanherboch.chessduck.content.PuzzleThemes.byKey }.values.all { it.total <= 1 })
    }

    /** Longueur : un coup, court, long ; « Mon niveau » : autour de l'ELO tactique. */
    @Test
    fun lengthAndMyLevelFilters() = runTest(main) {
        // La longueur se compte sur les coups réels : « LON » porte à tort « oneMove » (comme #Cex55), il reste long.
        val one = PUZZLE_0000D.copy(id = "ONE", fen = "6k1/5ppp/8/8/8/8/5PPP/R5K1 b - - 0 1", moves = "g8f8 a1a8", themes = " mateIn1 oneMove ", primaryTheme = "mateIn1", rating = 900)
        val short = PUZZLE_0000D.copy(id = "SHO", themes = " fork short ", primaryTheme = "fork", rating = 1500)
        val long = PUZZLE_0000D.copy(
            id = "LON", fen = "8/5k2/p1p3p1/PpPpP2p/1P4p1/2P1K1P1/7P/8 b - - 0 53",
            moves = "f7e6 e3f4 g6g5 f4g5 e6e5 g5h5 e5f5 h5h4 f5e4 h4g4 e4d3 h2h4 d3c3 h4h5 d5d4 h5h6 d4d3 h6h7 d3d2 h7h8q",
            themes = " pin oneMove veryLong ", primaryTheme = "pin", rating = 2100,
        )
        val vm = PuzzlesViewModel(FakeSettings(com.ivanherboch.chessduck.data.Settings(elo = 1520)), FakePuzzles(listOf(one, short, long)), FakeEngine())
        advanceUntilIdle()
        for ((len, id) in listOf(1 to "ONE", 2 to "SHO", 3 to "LON")) {
            vm.setLength(len); vm.random(); advanceUntilIdle()
            assertTrue("longueur $len", vm.state.value.puzzle?.id == id)
        }
        // Compteurs du catalogue avec la longueur « Long » : le mat en 1 n'y figure plus.
        vm.setLength(3); vm.loadCounts(0); advanceUntilIdle()
        assertTrue(vm.counts.value["mateIn1"]?.total == 0 && vm.counts.value["pin"]?.total == 1 && vm.counts.value[""]?.total == 1)
        vm.setLength(0); vm.setBand(com.ivanherboch.chessduck.content.PuzzleThemes.MY_LEVEL)
        repeat(5) { vm.random(); advanceUntilIdle(); assertTrue(vm.state.value.puzzle?.id == "SHO") } // 1520 ± 100
    }

    /** Bilan tactique : précision, série en cours, thèmes les plus manqués. */
    @Test
    fun tacticStatsSummarizeAttempts() {
        val o = com.ivanherboch.chessduck.data.PuzzleOutcome
        val rows = listOf(
            com.ivanherboch.chessduck.data.AttemptRow(o.CLEAN, 1, 50, "fork"), com.ivanherboch.chessduck.data.AttemptRow(o.CLEAN, 1, 40, "pin"),
            com.ivanherboch.chessduck.data.AttemptRow(o.GAVE_UP, 2, 30, "fork"), com.ivanherboch.chessduck.data.AttemptRow(o.WITH_ERROR, 1, 20, "fork"),
            com.ivanherboch.chessduck.data.AttemptRow(o.WITH_ERROR, 1, 10, "skewer"),
        )
        val st = com.ivanherboch.chessduck.feature.TacticStats.of(rows, 1400)
        assertTrue(st.tried == 5 && st.clean == 2 && st.precision == 40 && st.streak == 2 && st.elo == 1400)
        assertTrue(st.missed.first() == ("fork" to 2))
    }

    private fun com.ivanherboch.chessduck.feature.PuzzleState.position() = line?.positions?.getOrNull(cursor)
}
