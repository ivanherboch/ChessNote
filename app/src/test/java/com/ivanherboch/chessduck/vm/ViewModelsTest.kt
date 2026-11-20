package com.ivanherboch.chessduck.vm

import androidx.lifecycle.SavedStateHandle
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.chess.SpokenFr
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.feature.ASK
import com.ivanherboch.chessduck.feature.AcademyViewModel
import com.ivanherboch.chessduck.feature.GameAnalysisService
import com.ivanherboch.chessduck.feature.GameReviewViewModel
import com.ivanherboch.chessduck.feature.KidViewModel
import com.ivanherboch.chessduck.feature.NotationViewModel
import com.ivanherboch.chessduck.feature.PlayViewModel
import com.ivanherboch.chessduck.feature.PuzzlesViewModel
import com.ivanherboch.chessduck.feature.ShareFormat
import com.ivanherboch.chessduck.feature.ShareStyle
import com.ivanherboch.chessduck.engine.MATE
import com.ivanherboch.chessduck.ui.evalShort
import com.ivanherboch.chessduck.ui.DuckPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import com.ivanherboch.chessduck.engine.SearchResult
import com.ivanherboch.chessduck.engine.PvLine
import com.ivanherboch.chessduck.chess.Position

/**
 * Tests unitaires purs (JVM, sans Android ni Robolectric) des ViewModels, avec des faux moteur et dépôts
 * et un temps virtuel : délais d'animation, réponses de l'adversaire et de l'ordinateur sont déterministes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {
    private val main = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(main) { block() }

    @Test
    fun globalSettingsArePersistedThroughTheRepository() = test {
        val prefs = FakeSettings()
        prefs.setThemeMode(1)
        prefs.setPalette(4)
        prefs.setCoords(false)
        prefs.setConfirmActions(false)
        prefs.setHaptics(false)
        prefs.setSound(false)
        prefs.setKidMode(true)
        prefs.setPseudo("Alice")
        prefs.setPseudo2("Bob")
        prefs.setNarration(true)
        val st = prefs.settings.first()
        assertEquals(1, st.themeMode)
        assertEquals(4, st.palette)
        assertFalse(st.coords || st.confirmActions || st.haptics || st.sound)
        assertTrue(st.kidMode && st.narration)
        assertEquals("Alice" to "Bob", st.pseudo to st.pseudo2)
    }

    @Test
    fun shareStyleIsReadAndSavedByPuzzlesViewModel() = test {
        val prefs = FakeSettings()
        val vm = PuzzlesViewModel(prefs, FakePuzzles(listOf(PUZZLE_0000D)), FakeEngine())
        assertEquals(ShareStyle(), vm.shareStyle())
        val square = ShareStyle(ShareFormat.SQUARE, light = true)
        vm.saveShareStyle(square)
        advanceUntilIdle()
        assertEquals(ShareFormat.SQUARE.name, prefs.settings.first().shareFormat)
        assertEquals(square, vm.shareStyle())
        prefs.setPalette(DuckPalette.BLUE_ORANGE.ordinal)
        assertEquals(DuckPalette.BLUE_ORANGE, vm.shareStyle().palette)
    }

    @Test
    fun evalBarShowsMateOnceTheGameIsOver() = test {
        val games = FakeGames()
        val engine = FakeEngine()
        val vm = PlayViewModel(FakeSettings(Settings(twoPlayers = true)), games, engine, GameAnalysisService(engine, games, backgroundScope))
        advanceUntilIdle()
        for (uci in listOf("f2f3", "e7e5", "g2g4", "d8h4")) { vm.playerMove(Move.fromUci(uci)); advanceUntilIdle() }
        val s = vm.state.value
        assertNotNull(s.result)
        // Position finale évaluée comme un mat des Noirs, et non par la dernière évaluation connue.
        assertEquals(-MATE, s.analysis[s.line.size]?.eval)
        assertEquals("#", evalShort(-MATE))
    }

    @Test
    fun kidPieceStepsAreSpokenInSyncOnlyWhenParentsAllowIt() = test {
        val prefs = FakeSettings()
        val voice = FakeNarrator()
        var now = 0L
        val vm = KidViewModel(prefs, voice) { now }
        runCurrent()
        // Voix désactivée (par défaut) : rien n'est dit et l'animation n'attend jamais.
        vm.pieceShown(5)
        vm.say(5, "La **Dame** va **tout droit**.")
        vm.awaitVoice(5)
        assertTrue(voice.spoken.isEmpty())
        prefs.setKidNarration(true); runCurrent()
        // Voix activée : chaque étape est annoncée quand elle commence, et l'étape suivante attend la fin de la phrase.
        vm.pieceShown(5)
        vm.say(5, "La **Dame** va **tout droit**.")
        assertEquals(listOf("La Dame va tout droit."), voice.spoken)
        val waiting = async { vm.awaitVoice(5) }
        runCurrent()
        assertFalse(waiting.isCompleted)
        voice.finish(); runCurrent()
        assertTrue(waiting.isCompleted)
        // Premier tour terminé : les tours suivants sont silencieux.
        vm.voiceDone(5)
        vm.say(5, "La **Dame** va **tout droit**.")
        assertEquals(1, voice.spoken.size)
        // Même pièce rouverte tout de suite : pas de commentaire ; fermer la fiche coupe la voix.
        vm.pieceClosed()
        now += 10_000
        vm.pieceShown(5)
        vm.say(5, "La **Dame** va **tout droit**.")
        assertEquals(1, voice.spoken.size)
        // Une autre pièce est commentée ; après le temps de calme, la Dame peut l'être de nouveau.
        vm.pieceShown(1)
        vm.say(1, "Le **Pion** avance.")
        assertEquals(2, voice.spoken.size)
        now += 30_000
        vm.pieceShown(5)
        vm.say(5, "La **Dame** va **tout droit**.")
        assertEquals(3, voice.spoken.size)
    }

    @Test
    fun kidStalemateIsExplained() = test {
        val vm = KidViewModel(FakeSettings(), FakeNarrator())
        // Pat le plus rapide connu (Sam Loyd, 10 coups), joué depuis la position de départ.
        val loyd = Line.fromSan(START_FEN, "e3 a5 Qh5 Ra6 Qxa5 h5 h4 Rah6 Qxc7 f6 Qxd7+ Kf7 Qxb7 Qd3 Qxb8 Qh7 Qxc8 Kg6 Qe6".split(" "))
        for (m in loyd.moves) vm.move(m)
        assertTrue(vm.state.value.result!!, vm.state.value.result!!.startsWith("🤝 Pat !"))
    }

    @Test
    fun kidMovesBackAndForward() = test {
        val vm = KidViewModel(FakeSettings(), FakeNarrator())
        vm.move(Move.fromUci("e2e4"))
        assertEquals(1, vm.state.value.cursor)
        vm.move(Move.fromUci("e2e4")) // illégal pour les Noirs : ignoré
        assertEquals(1, vm.state.value.cursor)
        vm.back(); assertEquals(0, vm.state.value.cursor)
        vm.forward(); assertEquals(1, vm.state.value.cursor)
        vm.reset(); assertEquals(0, vm.state.value.line.size)
    }

    private fun academy(
        lessons: FakeLessons = FakeLessons(), voice: FakeNarrator = FakeNarrator(), engine: FakeEngine = FakeEngine(),
        puzzles: FakePuzzles = FakePuzzles(listOf(PUZZLE_0000D)), prefs: FakeSettings = FakeSettings(),
        saved: SavedStateHandle = SavedStateHandle(),
    ) = AcademyViewModel(prefs, lessons, voice, engine, puzzles, saved, Random(1))

    /** Réponse imposée au faux moteur : la position obtenue après [m] vaut [score] pour le camp au trait. */
    private fun FakeEngine.after(pos: Position, m: Move, score: Int) {
        val after = pos.play(m)
        scripted[after.toFen().split(' ').take(4).joinToString(" ")] = SearchResult(after.legalMoves.first(), listOf(PvLine(after.legalMoves.first(), score)))
    }

    @Test
    fun academyGuidedChallengeStarsAndMastery() = test {
        val lessons = FakeLessons()
        val engine = FakeEngine()
        val vm = academy(lessons, engine = engine)
        advanceUntilIdle()
        val l = Lessons.byId(1)
        assertEquals(1, vm.state.value.lessonId)
        vm.goTo(3); assertEquals(3, vm.state.value.cursor)
        vm.rewind()
        vm.togglePlay()
        advanceTimeBy(701); runCurrent()
        assertEquals(1, vm.state.value.cursor) // premier coup après 700 ms
        vm.pause()
        // Mode Défi : un mauvais coup (jugé nettement moins bon) est réfuté puis annulé ; il coûte une étoile.
        vm.setChallenge(true)
        advanceUntilIdle()
        val wrong = Move.fromUci("a2a3")
        engine.after(l.line.positions[0], wrong, 400)
        assertFalse(vm.challengeMove(wrong))
        runCurrent()
        assertNotNull(vm.state.value.refute)
        advanceUntilIdle() // réponse adverse, compte à rebours, puis retour à la position d'avant l'erreur
        assertNull(vm.state.value.refute)
        assertEquals(0, vm.state.value.cursor)
        assertEquals(1, vm.state.value.errors)
        while (!vm.state.value.done) {
            val c = vm.state.value.cursor
            assertTrue(vm.challengeMove(l.line.moves[c]))
            advanceUntilIdle()
        }
        // Le dernier commentaire et sa règle « À retenir » restent affichés, avec le bilan.
        assertTrue(vm.state.value.message, vm.state.value.message.contains("À retenir"))
        assertEquals(2, vm.state.value.stars)
        assertEquals(listOf(1), lessons.masteredLessons().first()) // leçon sans thème : le Défi suffit (au moins 2 étoiles)
        assertEquals(2, lessons.progress(1)?.stars)
        // Retour au Guidé : la position atteinte est conservée.
        val end = vm.state.value.cursor
        vm.setChallenge(false)
        assertEquals(end, vm.state.value.cursor)
    }

    @Test
    fun academyAcceptsEquivalentMovesAndShowsReplies() = test {
        val vm = academy() // faux moteur : toutes les positions se valent
        advanceUntilIdle()
        val l = Lessons.byId(1)
        vm.setChallenge(true)
        advanceUntilIdle()
        assertFalse(vm.challengeMove(Move.fromUci("d2d4"))) // pas le coup de la leçon…
        advanceUntilIdle()
        // … mais aussi bon : accepté, la leçon poursuit avec son propre coup, puis la réponse adverse est commentée.
        assertEquals(0, vm.state.value.errors)
        assertTrue(vm.state.value.message.startsWith("Bon coup aussi ! La leçon poursuit avec ${l.line.sans[0]}"))
        assertTrue(vm.state.value.message.contains("Réponse ${l.line.sans[1]} : ${l.texts[1]}"))
        assertEquals(2, vm.state.value.cursor)
    }

    @Test
    fun academyGuidedPausesAskForTheMove() = test {
        val engine = FakeEngine()
        val vm = academy(engine = engine)
        advanceUntilIdle()
        val l = Lessons.byId(1)
        val p = l.pauseSteps.first()
        // La lecture automatique s'arrête à l'arrêt « À vous ».
        vm.togglePlay()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(p, vm.state.value.cursor)
        assertFalse(vm.state.value.playing)
        assertTrue(vm.asking())
        // Un coup moins bon : on peut réessayer ; le bon coup : bravo, et la leçon avance.
        val pos = l.line.positions[p]
        val wrong = pos.legalMoves.first { it != l.line.moves[p] }
        engine.after(pos, wrong, 500)
        vm.guideMove(wrong); advanceUntilIdle()
        assertTrue(vm.asking())
        assertTrue(vm.state.value.note!!.contains("n'est pas le coup de la leçon"))
        vm.guideMove(l.line.moves[p]); advanceUntilIdle()
        assertEquals(p + 1, vm.state.value.cursor)
        assertFalse(vm.asking())
        assertTrue(vm.state.value.note!!.startsWith("Bravo"))
        // Revenir sur l'arrêt déjà résolu ne redemande rien.
        vm.goTo(p); assertFalse(vm.asking())
    }

    @Test
    fun academyPracticeValidatesOnFreshPuzzles() = test {
        val lessons = FakeLessons()
        val l = Lessons.all.first { it.theme.isNotEmpty() }
        // Cinq problèmes du thème (copies du #0000D sous d'autres identifiants).
        val set = (1..5).map { PUZZLE_0000D.copy(id = "P$it", primaryTheme = l.theme, rating = 1000) }
        val puzzles = FakePuzzles(set)
        lessons.save(com.ivanherboch.chessduck.data.LessonProgress(l.id, 0, stars = 3))
        val vm = academy(lessons, puzzles = puzzles)
        advanceUntilIdle()
        vm.open(l.id)
        vm.startPractice()
        advanceUntilIdle()
        repeat(5) { k ->
            val pr = vm.state.value.practice!!
            assertEquals(k, pr.index)
            val line = pr.line!!
            if (k == 0) {
                vm.practiceMove(line.positions[1].legalMoves.first { it != line.moves[1] && !line.positions[1].play(it).isCheckmate }) // raté : la solution est montrée
                assertTrue(vm.state.value.practice!!.failed)
            } else {
                vm.practiceMove(line.moves[1]); advanceUntilIdle()
                vm.practiceMove(line.moves[3]); advanceUntilIdle()
                assertTrue(vm.state.value.practice!!.solved)
            }
            vm.nextPractice(); advanceUntilIdle()
        }
        val pr = vm.state.value.practice!!
        assertTrue(pr.finished)
        assertEquals(4, pr.score)
        assertEquals(listOf(l.id), lessons.masteredLessons().first())
        assertEquals(4, lessons.progress(l.id)?.practicePassed)
        assertEquals(4, puzzles.solved.value.size)
        vm.closePractice()
        assertNull(vm.state.value.practice)
    }

    @Test
    fun academyThemedLessonNeedsPracticeToBeMastered() = test {
        val lessons = FakeLessons()
        val vm = academy(lessons)
        advanceUntilIdle()
        val l = Lessons.all.first { it.theme.isNotEmpty() }
        vm.open(l.id)
        vm.setChallenge(true)
        advanceUntilIdle()
        while (!vm.state.value.done) {
            assertTrue(vm.challengeMove(l.line.moves[vm.state.value.cursor]))
            advanceUntilIdle()
        }
        assertEquals(3, vm.state.value.stars)
        assertTrue(lessons.masteredLessons().first().isEmpty()) // Défi parfait, mais exercices à valider
        assertTrue(vm.state.value.message.contains("problèmes inédits"))
    }

    /** « Jouer cette position » : partie d'entraînement sans ELO, enregistrée avec sa position de départ et relue en revue. */
    @Test
    fun playFromLessonPositionIsUnratedAndReviewable() = test {
        val games = FakeGames()
        val engine = FakeEngine()
        val prefs = FakeSettings()
        val analysis = GameAnalysisService(engine, games, backgroundScope)
        val vm = PlayViewModel(prefs, games, engine, analysis)
        advanceUntilIdle()
        val fen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1"
        vm.startFromPosition(fen, playerWhite = true)
        advanceUntilIdle()
        assertTrue(vm.state.value.fromPosition)
        assertEquals(0, vm.state.value.line.size)
        vm.playerMove(Move.fromUci("d1d8")) // mat du couloir
        advanceUntilIdle()
        val record = games.stored.value.single()
        assertEquals(fen, record.startFen)
        assertEquals(record.eloBefore, record.eloAfter)
        assertEquals(1200, prefs.settings.first().elo)
        assertTrue(vm.state.value.result!!.text.contains("l'ELO ne change pas"))
        val rv = GameReviewViewModel(analysis)
        rv.openReview(record)
        assertEquals(fen, rv.review.value!!.line.startFen)
        assertEquals(1, rv.review.value!!.line.size)
        // « Nouvelle partie » revient à la position initiale, partie classée.
        vm.newGame(); advanceUntilIdle()
        assertFalse(vm.state.value.fromPosition)
    }

    @Test
    fun academyReadsGuidedCommentsAloudOnlyWhenEnabled() = test {
        val prefs = FakeSettings()
        val voice = FakeNarrator()
        val vm = academy(voice = voice, prefs = prefs)
        advanceUntilIdle()
        val l = Lessons.byId(1)
        vm.goTo(1)
        assertTrue(voice.spoken.isEmpty()) // réglage désactivé par défaut
        prefs.setNarration(true); runCurrent()
        vm.goTo(2)
        assertEquals(SpokenFr.text("${SpokenFr.move(l.line.sans[1])}. ${l.texts[1]}"), voice.spoken.single())
        // Lecture automatique : le coup suivant attend la fin du commentaire.
        vm.togglePlay()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, vm.state.value.cursor)
        voice.finish()
        advanceTimeBy(601); runCurrent()
        assertEquals(3, vm.state.value.cursor)
        // Arrêt « À vous » (leçon 1 : avant le 5e demi-coup) : la question est lue après le commentaire, et la lecture
        // automatique s'arrête pour laisser l'élève chercher.
        voice.finish()
        advanceTimeBy(601); runCurrent()
        assertEquals(4, vm.state.value.cursor)
        assertFalse(vm.state.value.playing)
        assertTrue(voice.spoken.last().endsWith(SpokenFr.text(ASK)))
        // Pause pendant un commentaire : la voix s'arrête, puis reprend (au début de sa phrase) avec la lecture.
        vm.goTo(6)
        voice.finish()
        vm.togglePlay()
        voice.spoken.clear()
        vm.goTo(7) // la lecture automatique est toujours en cours
        assertTrue(vm.state.value.playing)
        vm.togglePlay()
        assertFalse(voice.speaking.value)
        assertFalse(vm.state.value.playing)
        vm.togglePlay()
        assertTrue(voice.speaking.value)
        assertEquals(2, voice.spoken.size)
        assertEquals(7, vm.state.value.cursor) // le coup suivant attend toujours la fin du commentaire
        voice.finish()
        advanceTimeBy(601); runCurrent()
        assertEquals(8, vm.state.value.cursor)
        // Écran quitté : silence et pause.
        vm.silence()
        assertFalse(voice.speaking.value)
        assertFalse(vm.state.value.playing)
        // Mode Défi : aucune lecture.
        voice.spoken.clear()
        vm.setChallenge(true)
        advanceUntilIdle()
        vm.challengeMove(l.line.moves[vm.state.value.cursor])
        advanceUntilIdle()
        assertTrue(voice.spoken.isEmpty())
        // Réglage coupé pendant une lecture : la voix s'arrête.
        vm.setChallenge(false)
        assertTrue(voice.speaking.value)
        prefs.setNarration(false); runCurrent()
        assertFalse(voice.speaking.value)
    }

    @Test
    fun academyRestoresFromSavedState() = test {
        val saved = SavedStateHandle(mapOf("academy_lesson" to 5, "academy_challenge" to false, "academy_cursor" to 3))
        val vm = academy(saved = saved)
        runCurrent()
        assertEquals(5, vm.state.value.lessonId)
        assertEquals(3, vm.state.value.cursor)
        vm.goTo(4); runCurrent()
        assertEquals(4, saved.get<Int>("academy_cursor"))
    }

    @Test
    fun puzzleSolvedWithRefutationAndRetry() = test {
        val repo = FakePuzzles(listOf(PUZZLE_0000D))
        val vm = PuzzlesViewModel(FakeSettings(), repo, FakeEngine())
        runCurrent()
        assertEquals("0000D", vm.state.value.puzzle?.id)
        advanceTimeBy(601); runCurrent()
        assertEquals(1, vm.state.value.cursor) // le coup adverse a été joué
        // Mauvais coup : réfutation puis « Réessayer ».
        assertEquals(-1, vm.onMove(Move.fromUci("g8h8")))
        advanceTimeBy(700); runCurrent()
        assertNotNull(vm.state.value.refute)
        vm.retry()
        assertNull(vm.state.value.refute)
        assertEquals(1, vm.onMove(Move.fromUci("f8d8")))
        advanceTimeBy(601); runCurrent()
        assertEquals(3, vm.state.value.cursor)
        assertEquals(2, vm.onMove(Move.fromUci("f6d8")))
        runCurrent()
        assertTrue(vm.state.value.solved)
        assertEquals(setOf("0000D"), repo.solved.value)
    }

    @Test
    fun recentKeepsThemeWhenItWasVisible() = test {
        // Filtre sur un thème précis (ici celui du problème) : le thème était affiché, il le reste dans « Récents ».
        val theme = PUZZLE_0000D.primaryTheme
        val settings = FakeSettings(Settings(puzzleTheme = theme))
        val vm = PuzzlesViewModel(settings, FakePuzzles(listOf(PUZZLE_0000D)), FakeEngine())
        runCurrent()
        assertTrue(vm.state.value.themeVisible)
        assertTrue("0000D" in settings.flow.value.knownThemePuzzles)
        // Sans filtre : thème masqué jusqu'à ce qu'il soit révélé.
        val settings2 = FakeSettings()
        val vm2 = PuzzlesViewModel(settings2, FakePuzzles(listOf(PUZZLE_0000D)), FakeEngine())
        runCurrent()
        assertFalse(vm2.state.value.themeVisible)
        assertFalse("0000D" in settings2.flow.value.knownThemePuzzles)
        vm2.revealTheme(); runCurrent()
        assertTrue("0000D" in settings2.flow.value.knownThemePuzzles)
    }

    @Test
    fun notationExerciseFromFallbackAndValidation() = test {
        val settings = FakeSettings()
        val vm = NotationViewModel(settings, FakePuzzles(emptyList()))
        val ex = vm.state.first { it.exercise != null }.exercise!!
        val san = ex.san
        if (san.startsWith("O-O")) {
            vm.type(san.trimEnd('+', '#')); san.drop(san.trimEnd('+', '#').length).forEach { vm.type(it.toString()) }
        } else san.forEach { vm.type(it.toString()) }
        assertTrue(vm.validate())
        assertEquals(1, vm.state.value.streak)
        advanceUntilIdle()
        assertEquals(1, settings.flow.value.notationBest)
    }

    @Test
    fun playComputerRepliesAndStaleMoveIsIgnored() = test {
        val games = FakeGames()
        val engine = FakeEngine()
        val settings = FakeSettings(Settings(colorChoice = 0, aiElo = 1500))
        val vm = PlayViewModel(settings, games, engine, GameAnalysisService(engine, games, backgroundScope))
        advanceUntilIdle()
        vm.playerMove(Move.fromUci("e2e4"))
        assertTrue(vm.state.value.thinking)
        advanceTimeBy(600); runCurrent()
        assertEquals(2, vm.state.value.line.size) // l'ordinateur a répondu
        // Coup joué puis nouvelle partie pendant la réflexion : la réponse de l'ancienne partie est ignorée.
        vm.playerMove(vm.state.value.position.legalMoves.first())
        vm.newGame()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.line.size)
        // Abandon après un échange : partie enregistrée et ELO mis à jour.
        vm.playerMove(Move.fromUci("d2d4"))
        advanceUntilIdle()
        vm.resign()
        advanceUntilIdle()
        assertNotNull(vm.state.value.result)
        assertEquals(1, games.stored.value.size)
        assertTrue(settings.flow.value.elo < 1200)
    }

    @Test
    fun reviewAutoplayJumpAndClose() = test {
        val games = FakeGames()
        val engine = FakeEngine()
        val vm = GameReviewViewModel(GameAnalysisService(engine, games, backgroundScope))
        val g = GameRecord(
            id = 7, date = 1, opponent = "Ordinateur 1200", vsComputer = true, playerWhite = false, result = "0-1", outcome = 1,
            reason = "abandon", eloBefore = 1200, eloAfter = 1216, moves = "e2e4 e7e5 g1f3",
        )
        vm.openReview(g)
        assertFalse(vm.review.value!!.playing) // la revue s'ouvre en pause, sur le résumé
        assertTrue(vm.review.value!!.flipped) // le joueur avait les Noirs
        vm.toggle(); assertTrue(vm.review.value!!.playing)
        advanceTimeBy(1151); runCurrent()
        assertEquals(1, vm.review.value!!.cursor)
        vm.jump(3); assertEquals(3, vm.review.value!!.cursor); assertFalse(vm.review.value!!.playing)
        advanceUntilIdle()
        assertEquals(4, vm.review.value!!.analysis.count { it != null }) // analyse publiée au fil de l'eau
        assertTrue(games.analyses.containsKey(7L))
        vm.close(); assertNull(vm.review.value)
    }
}
