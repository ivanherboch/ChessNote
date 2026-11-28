package com.ivanherboch.chessduck.vm

import com.ivanherboch.chessduck.chess.GameEnd
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.engine.PvLine
import com.ivanherboch.chessduck.engine.SearchResult
import com.ivanherboch.chessduck.feature.GameAnalysisService
import com.ivanherboch.chessduck.feature.PlayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
 * Jeu Libre soumis à des centaines d'actions au hasard (coups, indice, annulation, navigation, abandon, filet, nouvelle
 * partie, réglages), avec un moteur aux évaluations variées. Après chaque pause, la partie ne doit jamais rester bloquée
 * (l'Ordinateur au trait sans réfléchir), une fin de partie doit être conclue, et chaque partie conclue enregistrée une fois.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayFuzzTest {
    private val main = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    /** Moteur déterministe : coup et score tirés de la position, avec un temps de réflexion simulé. */
    private class NoisyEngine : ChessEngine {
        override suspend fun search(fen: String, movetimeMs: Int, multiPv: Int, elo: Int?, depth: Int?): SearchResult {
            delay(movetimeMs.toLong().coerceAtMost(300))
            val p = Position.fromFen(fen)
            val moves = p.legalMoves
            if (moves.isEmpty()) return SearchResult(null, emptyList())
            val r = Random(fen.hashCode())
            val lines = moves.shuffled(r).take(multiPv.coerceAtLeast(1)).mapIndexed { i, m -> PvLine(m, r.nextInt(-700, 700) - i * 40) }
                .sortedByDescending { it.score }
            return SearchResult(lines.first().move, lines)
        }
    }

    private companion object {
        val FENS = listOf(
            "6k1/5ppp/4n3/8/8/8/5PPP/3R2K1 w - - 0 1", "8/8/8/4k3/8/8/4P3/4K3 w - - 0 1",
            "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3", "7k/8/6K1/8/8/8/8/5Q2 w - - 0 1",
        )
    }

    @Test
    fun randomSessionsNeverGetStuck() = runTest(main) {
        val problems = LinkedHashSet<String>()
        for (seed in (System.getenv("FUZZ_FROM")?.toInt() ?: 1)..(System.getenv("FUZZ_TO")?.toInt() ?: 80)) {
            val r = Random(seed)
            val games = FakeGames()
            val engine = NoisyEngine()
            val net = r.nextBoolean()
            val prefs = FakeSettings(Settings(colorChoice = r.nextInt(3), aiElo = listOf(800, 1200, 1600).random(r), safetyNet = net, ratedGame = !net && r.nextBoolean()))
            val vm = PlayViewModel(prefs, games, engine, GameAnalysisService(engine, games, backgroundScope), Random(seed * 7919))
            advanceUntilIdle()
            var finished = 0
            var eloBefore = prefs.flow.value.elo
            var lastResult: Any? = null
            val log = ArrayList<String>()
            repeat(160) { step ->
                val s = vm.state.value
                val a = r.nextInt(100)
                if (s.result == null) eloBefore = prefs.flow.value.elo
                // Quitter une partie en cours contre l'Ordinateur la compte comme un abandon (enregistré).
                val quit = (a in 77..78 || a in 94..95 || a >= 98) && s.inProgress && !s.twoPlayers
                if (quit) finished++
                val name = when {
                    a < 55 -> { val m = s.position.legalMoves.randomOrNull(r); if (m != null) vm.playerMove(m); "coup ${m?.let { s.position.san(it) }}" }
                    a < 60 -> { vm.hint(); "indice" }
                    a < 66 -> { vm.undoMove(); "annuler" }
                    a < 70 -> { vm.goTo(r.nextInt(s.line.size + 1)); "goTo" }
                    a < 73 -> { vm.back(); "back" }
                    a < 75 -> { vm.forward(); "forward" }
                    a < 77 -> { vm.resign(); "abandon" }
                    a < 79 -> { vm.newGame(); "nouvelle" }
                    a < 82 -> { vm.setSafetyNet(r.nextBoolean()); "filet" }
                    a < 84 -> { vm.toggleEval(); "jauge" }
                    a < 88 -> { vm.dismissWarning(); "changer" }
                    a < 92 -> { vm.playAnyway(); "quand même" }
                    a < 94 -> { vm.showWhy(); "pourquoi" }
                    a < 96 -> { vm.applySettings(r.nextInt(4) == 0, r.nextInt(3), listOf(800, 1400).random(r)); "réglages" }
                    a < 98 -> { vm.flip(); "flip" }
                    else -> { vm.startFromPosition(FENS.random(r), r.nextBoolean()); "position" }
                }
                log += name
                // Parfois on laisse tout se terminer, parfois on agit pendant la réflexion (courses).
                if (r.nextInt(3) == 0) { advanceTimeBy(r.nextLong(0, 400)); runCurrent() } else advanceUntilIdle()
                val t = vm.state.value
                if (System.getenv("FUZZ_DEBUG") != null) println("S$seed #$step ${log.last()} quit=$quit -> size=${t.line.size} res=${t.result?.text?.take(40)} stored=${games.stored.value.size} fin=$finished")
                // Une nouvelle fin de partie : tout nouveau résultat, sauf le texte définitif qui remplace « Partie terminée. ».
                if (t.result != null && t.result !== lastResult && (lastResult as? com.ivanherboch.chessduck.feature.GameResult)?.text != "Partie terminée.") {
                    lastResult = t.result
                    if (!t.result!!.text.startsWith("Partie interrompue")) finished++
                    advanceUntilIdle()
                    val elo = prefs.flow.value.elo
                    // Hors classement : 2 joueurs, partie libre, position de leçon, ou partie classée sortie par une aide.
                    val unrated = t.twoPlayers || !t.rated || t.fromPosition || t.netUsed
                    // Une partie classée quittée dans la même étape (abandon) a pu, elle, changer l'ELO.
                    if (unrated && !quit && elo != eloBefore) problems += "seed $seed pas $step : ELO changé par une partie hors classement (${t.result!!.text})"
                    eloBefore = elo
                }
                if (t.result == null) lastResult = null
                val idle = !t.thinking && !t.checking
                if (idle) {
                    val end = t.line.end()
                    if (end != GameEnd.NONE && t.result == null) problems += "seed $seed pas $step : fin $end non conclue (${log.takeLast(4)})"
                    if (end == GameEnd.NONE && t.result == null && !t.twoPlayers && t.line.last.whiteToMove != t.playerWhite)
                        problems += "seed $seed pas $step : l'Ordinateur est au trait mais ne joue pas (${log.takeLast(4)})"
                    if (t.warning != null && t.position.isLegal(t.warning!!.move).not())
                        problems += "seed $seed pas $step : arrêt du filet sur un coup illégal dans la position affichée (${log.takeLast(4)})"
                }
            }
            advanceUntilIdle()
            val stored = games.stored.value.size
            if (stored != finished) problems += "seed $seed : $finished parties conclues, $stored enregistrées (${games.stored.value.map { it.reason + "/" + it.moves.split(' ').size }})"
        }
        println(problems.joinToString("\n"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** « Nouvelle partie » en pleine partie contre l'Ordinateur : un abandon, enregistré, qui fait perdre de l'ELO. */
    @Test
    fun quittingAGameInProgressCountsAsResignation() = runTest(main) {
        val games = FakeGames()
        val engine = FakeEngine()
        val prefs = FakeSettings(Settings(colorChoice = 0, aiElo = 1200, ratedGame = true, safetyNet = false))
        val vm = PlayViewModel(prefs, games, engine, GameAnalysisService(engine, games, backgroundScope))
        advanceUntilIdle()
        // Pas encore d'échange : rien n'est enregistré.
        vm.newGame(); advanceUntilIdle()
        assertTrue(games.stored.value.isEmpty())
        vm.playerMove(com.ivanherboch.chessduck.chess.Move.fromUci("e2e4")); advanceUntilIdle()
        vm.newGame(); advanceUntilIdle()
        assertTrue(games.stored.value.size == 1 && games.stored.value[0].reason == "abandon" && games.stored.value[0].outcome == -1)
        assertTrue(prefs.flow.value.elo < 1200)
        assertTrue(vm.state.value.line.size == 0 && vm.state.value.result == null)
    }

    /** Fin de partie puis « Nouvelle partie » (ou un second abandon) aussitôt : la partie n'est enregistrée qu'une fois. */
    @Test
    fun aGameIsRecordedOnceEvenWhenQuitRightAfterItsEnd() = runTest(main) {
        val games = FakeGames()
        val engine = FakeEngine()
        val prefs = FakeSettings(Settings(colorChoice = 0, aiElo = 1200, ratedGame = true, safetyNet = false))
        val vm = PlayViewModel(prefs, games, engine, GameAnalysisService(engine, games, backgroundScope), Random(1))
        advanceUntilIdle()
        vm.playerMove(com.ivanherboch.chessduck.chess.Move.fromUci("e2e4")); advanceUntilIdle()
        vm.resign(); vm.resign(); vm.newGame()
        advanceUntilIdle()
        assertTrue("${games.stored.value.size} enregistrements", games.stored.value.size == 1)
        assertTrue(prefs.flow.value.elo == com.ivanherboch.chessduck.feature.GameLogic.newElo(1200, 1200, 0.0))
    }
}
