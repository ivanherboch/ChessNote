package com.ivanherboch.chessduck.vm

import androidx.lifecycle.SavedStateHandle
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.feature.AcademyViewModel
import com.ivanherboch.chessduck.feature.GameAnalysisService
import com.ivanherboch.chessduck.feature.GameReviewViewModel
import com.ivanherboch.chessduck.feature.KidViewModel
import com.ivanherboch.chessduck.feature.NotationViewModel
import com.ivanherboch.chessduck.feature.PlayViewModel
import com.ivanherboch.chessduck.feature.PuzzlesViewModel
import com.ivanherboch.chessduck.feature.ShareFormat
import com.ivanherboch.chessduck.feature.ShareStyle
import kotlinx.coroutines.Dispatchers
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
        val st = prefs.settings.first()
        assertEquals(1, st.themeMode)
        assertEquals(4, st.palette)
        assertFalse(st.coords || st.confirmActions || st.haptics || st.sound)
        assertTrue(st.kidMode)
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
    }

    @Test
    fun kidMovesBackAndForward() = test {
        val vm = KidViewModel()
        vm.move(Move.fromUci("e2e4"))
        assertEquals(1, vm.state.value.cursor)
        vm.move(Move.fromUci("e2e4")) // illégal pour les Noirs : ignoré
        assertEquals(1, vm.state.value.cursor)
        vm.back(); assertEquals(0, vm.state.value.cursor)
        vm.forward(); assertEquals(1, vm.state.value.cursor)
        vm.reset(); assertEquals(0, vm.state.value.line.size)
    }

    @Test
    fun academyGuidedChallengeAndMastery() = test {
        val lessons = FakeLessons()
        val vm = AcademyViewModel(FakeSettings(), lessons)
        advanceUntilIdle()
        val l = Lessons.byId(1)
        assertEquals(1, vm.state.value.lessonId)
        vm.goTo(3); assertEquals(3, vm.state.value.cursor)
        vm.rewind()
        vm.togglePlay()
        advanceTimeBy(701); runCurrent()
        assertEquals(1, vm.state.value.cursor) // premier coup après 700 ms
        vm.pause()
        // Mode Défi : un mauvais coup est refusé, la ligne exacte maîtrise la leçon.
        vm.setChallenge(true)
        advanceUntilIdle()
        assertFalse(vm.challengeMove(Move.fromUci("a2a3")))
        while (!vm.state.value.done) {
            val c = vm.state.value.cursor
            assertTrue(vm.challengeMove(l.line.moves[c]))
            advanceUntilIdle()
        }
        assertEquals(listOf(1), lessons.mastered.value)
    }

    @Test
    fun academyRestoresFromSavedState() = test {
        val saved = SavedStateHandle(mapOf("academy_lesson" to 5, "academy_challenge" to false, "academy_cursor" to 3))
        val vm = AcademyViewModel(FakeSettings(), FakeLessons(), saved)
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
        assertTrue(vm.review.value!!.playing)
        assertTrue(vm.review.value!!.flipped) // le joueur avait les Noirs
        advanceTimeBy(1151); runCurrent()
        assertEquals(1, vm.review.value!!.cursor)
        vm.jump(3); assertEquals(3, vm.review.value!!.cursor); assertFalse(vm.review.value!!.playing)
        advanceUntilIdle()
        assertEquals(4, vm.review.value!!.analysis.count { it != null }) // analyse publiée au fil de l'eau
        assertTrue(games.analyses.containsKey(7L))
        vm.close(); assertNull(vm.review.value)
    }
}
