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
import kotlinx.coroutines.launch
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
        saved: SavedStateHandle = SavedStateHandle(), clock: () -> Long = System::currentTimeMillis,
    ) = AcademyViewModel(prefs, lessons, voice, engine, puzzles, saved, Random(1), clock)

    /** Joue tout le Défi (ou « Devinez le coup ») avec les coups de la leçon. */
    private suspend fun TestScope.playChallenge(vm: AcademyViewModel) {
        advanceUntilIdle()
        while (!vm.state.value.done) {
            val c = vm.state.value.cursor
            vm.challengeMove(vm.line().moves[c])
            advanceUntilIdle()
        }
    }

    /** Résout les problèmes d'une série (2e et 4e coups de chaque problème). */
    private suspend fun TestScope.solveSeries(vm: AcademyViewModel) {
        while (vm.state.value.practice?.finished == false) {
            val pr = vm.state.value.practice!!
            val line = pr.line!!
            vm.practiceMove(line.moves[1]); advanceUntilIdle()
            if (line.size > 3) { vm.practiceMove(line.moves[3]); advanceUntilIdle() }
            vm.nextPractice(); advanceUntilIdle()
        }
    }

    private val puzzleSet = (1..12).map { PUZZLE_0000D.copy(id = "Q$it", rating = 500 + it * 150) }

    @Test
    fun academyVariantsAreShownAndReadAloud() = test {
        val prefs = FakeSettings()
        val voice = FakeNarrator()
        val vm = academy(voice = voice, prefs = prefs)
        advanceUntilIdle()
        val italian = Lessons.all.first { it.variants.isNotEmpty() }
        prefs.setNarration(true); runCurrent()
        vm.open(italian.id)
        // La fiche de la leçon (idées et pièges compris) est lue à l'ouverture.
        assertTrue(voice.spoken.last().contains(SpokenFr.text(italian.ideas.first())))
        vm.setVariant(1)
        val v = italian.variants[0]
        assertEquals(v.from, vm.state.value.cursor)
        vm.step(1)
        assertEquals(italian.lineFor(1).sans[v.from], vm.line().sans[v.from])
        assertEquals(SpokenFr.text("${SpokenFr.move(vm.line().sans[v.from])}. ${v.texts[0]}"), voice.spoken.last())
        // Défi sur la variante : l'adversaire joue l'écart, l'élève répond.
        vm.setChallenge(true)
        voice.spoken.clear()
        playChallenge(vm)
        assertTrue(voice.spoken.isEmpty()) // aucune lecture en Défi
    }

    @Test
    fun academyQuizAtTheEndOfTheLesson() = test {
        val vm = academy()
        advanceUntilIdle()
        val l = Lessons.all.first { it.quiz != null }
        vm.open(l.id)
        vm.last()
        vm.startQuiz()
        val q = vm.state.value.quiz!!
        assertEquals(1 + l.quiz!!.wrong.size, q.choices.size)
        assertEquals(l.quiz!!.answer, q.choices[q.correct])
        vm.answerQuiz((q.correct + 1) % q.choices.size)
        vm.answerQuiz(q.correct) // une seule réponse
        assertEquals((q.correct + 1) % q.choices.size, vm.state.value.quiz!!.picked)
        vm.closeQuiz()
        assertNull(vm.state.value.quiz)
    }

    @Test
    fun academyModelGameGuessesTheMove() = test {
        val voice = FakeNarrator()
        val prefs = FakeSettings()
        val vm = academy(voice = voice, prefs = prefs)
        advanceUntilIdle()
        prefs.setNarration(true); runCurrent()
        val game = Lessons.all.first { it.kind == com.ivanherboch.chessduck.content.LessonKind.MODEL_GAME }
        vm.open(game.id)
        vm.setChallenge(true)
        voice.spoken.clear()
        advanceUntilIdle()
        assertTrue(vm.challengeMove(game.line.moves[0])) // le coup du maître : 3 points
        advanceUntilIdle()
        assertEquals(3, vm.state.value.guessScore)
        val c = vm.state.value.cursor
        val other = game.line.positions[c].legalMoves.first { it != game.line.moves[c] }
        assertFalse(vm.challengeMove(other)) // faux moteur : coup jugé aussi bon, 2 points, puis le coup du maître
        advanceUntilIdle()
        assertEquals(5, vm.state.value.guessScore)
        assertEquals(c + 2, vm.state.value.cursor)
        vm.skipGuess(); advanceUntilIdle()
        assertEquals(9, vm.state.value.guessMax)
        assertTrue(voice.spoken.isEmpty()) // voix muette pendant la recherche
    }

    @Test
    fun academyEndgameLessonValidatesAgainstTheComputer() = test {
        val lessons = FakeLessons()
        val vm = academy(lessons)
        advanceUntilIdle()
        val kqk = Lessons.all.first { it.drill == "KQK" }
        vm.open(kqk.id)
        vm.setChallenge(true)
        playChallenge(vm)
        assertEquals(3, vm.state.value.stars)
        assertTrue(lessons.masteredLessons().first().isEmpty())
        assertTrue(vm.state.value.message.contains("contre l'Ordinateur"))
        // Faux moteur (évaluation nulle) : le gain « s'envole » au premier coup, la position est perdue.
        vm.startEndgame(com.ivanherboch.chessduck.feature.EndgameFamily.KQK, fromLesson = true)
        val eg = vm.endgame.state.value!!
        vm.endgame.move(eg.position!!.legalMoves.first { !eg.position!!.play(it).isCheckmate && eg.position!!.play(it).legalMoves.isNotEmpty() })
        advanceUntilIdle()
        assertTrue(vm.endgame.state.value!!.over)
        assertEquals(listOf(false), vm.endgame.state.value!!.results)
        vm.closeEndgame()
        assertNull(vm.endgame.state.value)
    }

    @Test
    fun endgameDrawIsHeldForFifteenMoves() = test {
        val engine = FakeEngine()
        val trainer = com.ivanherboch.chessduck.feature.EndgameTrainer(this, engine, Random(3))
        trainer.start(com.ivanherboch.chessduck.feature.EndgameFamily.PHILIDOR_DRAW, total = 1)
        var guard = 0
        while (trainer.state.value?.over == false && guard++ < 40) {
            val s = trainer.state.value!!
            if (s.heroToMove) trainer.move(s.position!!.legalMoves.first())
            advanceUntilIdle()
        }
        val s = trainer.state.value!!
        assertTrue(s.message, s.over && s.results.single())
        trainer.next()
        assertTrue(trainer.state.value!!.finished)
    }

    @Test
    fun academyReviewsFollowTheSchedule() = test {
        var now = 10 * com.ivanherboch.chessduck.feature.SrsScheduler.DAY
        val lessons = FakeLessons()
        // Leçon 1 maîtrisée, révision prévue hier.
        lessons.save(com.ivanherboch.chessduck.data.LessonProgress(1, masteredAt = 1, stars = 3, srsStage = 1, nextReviewAt = now - 1))
        val vm = academy(lessons, clock = { now })
        backgroundScope.launch { vm.home.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(1), vm.home.value.due)
        vm.startReviews()
        advanceUntilIdle()
        assertTrue(vm.state.value.challenge && vm.state.value.review != null)
        playChallenge(vm)
        val p = lessons.progress(1)!!
        assertEquals(2, p.srsStage)
        assertEquals(now + 7 * com.ivanherboch.chessduck.feature.SrsScheduler.DAY, p.nextReviewAt)
        assertTrue(vm.state.value.message.contains("Révision réussie"))
        vm.nextReview(); vm.closeReviews()
        assertNull(vm.state.value.review)
        advanceUntilIdle()
        assertTrue(vm.home.value.due.isEmpty())
        now += 8 * com.ivanherboch.chessduck.feature.SrsScheduler.DAY
        assertTrue(vm.srs.isDue(lessons.progress(1)!!))
    }

    @Test
    fun academyRepertoireReplaysOpenings() = test {
        val vm = academy()
        advanceUntilIdle()
        vm.startRepertoire(white = true)
        advanceUntilIdle()
        val rep = vm.state.value.repertoire!!
        assertEquals(5, rep.items.size)
        assertTrue(rep.items.all { (id, _) -> Lessons.byId(id).let { it.heroWhite && it.category == com.ivanherboch.chessduck.content.LessonCategory.OPENING } })
        assertEquals(rep.items[0].first, vm.state.value.lessonId)
        assertEquals(rep.items[0].second, vm.state.value.variant)
        playChallenge(vm)
        assertEquals(listOf(true), vm.state.value.repertoire!!.results)
        vm.nextRepertoire(); advanceUntilIdle()
        assertEquals(rep.items[1].first, vm.state.value.lessonId)
        vm.closeRepertoire()
        assertNull(vm.state.value.repertoire)
    }

    @Test
    fun academyPlacementAndExamSetTheLevel() = test {
        val prefs = FakeSettings()
        val lessons = FakeLessons()
        val vm = academy(lessons, puzzles = FakePuzzles(puzzleSet), prefs = prefs)
        advanceUntilIdle()
        vm.startPlacement(); advanceUntilIdle()
        assertEquals(12, vm.state.value.practice!!.ids.size)
        solveSeries(vm)
        assertEquals(5, prefs.settings.first().academyLevel)
        assertTrue(prefs.settings.first().placementDone)
        prefs.setAcademyLevel(3)
        // Examen : 6 positions tactiques, 2 défensives, 2 finales, sans annonce du thème.
        val examSet = (1..8).map { PUZZLE_0000D.copy(id = "T$it", primaryTheme = "fork", rating = 1200) } +
            (1..3).map { PUZZLE_0000D.copy(id = "D$it", primaryTheme = "defensiveMove", rating = 1200) } +
            (1..3).map { PUZZLE_0000D.copy(id = "E$it", primaryTheme = "pawnEndgame", rating = 1200) }
        val vm2 = academy(lessons, puzzles = FakePuzzles(examSet), prefs = prefs)
        advanceUntilIdle()
        vm2.startExam(); advanceUntilIdle()
        val examVm = vm2
        val exam = examVm.state.value.practice!!
        assertEquals(10, exam.ids.size)
        assertEquals(3, exam.level)
        assertEquals(2, exam.ids.count { it.startsWith("D") })
        solveSeries(examVm)
        assertEquals(4, prefs.settings.first().academyLevel) // examen réussi : palier suivant
        assertEquals(listOf("placement", "exam"), lessons.tests.value.map { it.kind })
        assertTrue(lessons.tests.value.last().passed)
    }

    @Test
    fun academyReflexesAndOnlyMove() = test {
        val fork = com.ivanherboch.chessduck.data.Puzzle("FORK1", "4k3/8/8/8/3n4/1p6/4K3/R7 w - - 0 1", "e2e1 d4c2 e1d2 c2a1", 600, 90, 100, " fork ", "fork")
        val vm = academy(puzzles = FakePuzzles(listOf(fork) + puzzleSet.map { it.copy(primaryTheme = "defensiveMove") }))
        advanceUntilIdle()
        vm.startReflex(com.ivanherboch.chessduck.feature.ReflexKind.THREAT)
        advanceUntilIdle()
        val rx = vm.reflex.state.value!!
        assertEquals(1, rx.total)
        // L'en-tête annonce l'entraînement à la place de la leçon ouverte.
        assertEquals("Quelle est la menace ?", vm.trainingTitle(vm.state.value, vm.endgame.state.value, rx))
        vm.reflex.tap(com.ivanherboch.chessduck.chess.sqFromName("a1"))
        assertEquals(listOf(true), vm.reflex.state.value!!.results)
        vm.reflex.next()
        assertTrue(vm.reflex.state.value!!.finished)
        vm.closeReflex()
        assertNull(vm.trainingTitle(vm.state.value, vm.endgame.state.value, vm.reflex.state.value))
        vm.startOnlyMove(); advanceUntilIdle()
        assertEquals("Le seul coup", vm.trainingTitle(vm.state.value, vm.endgame.state.value, vm.reflex.state.value))
        assertEquals(com.ivanherboch.chessduck.feature.PracticeKind.ONLY_MOVE, vm.state.value.practice!!.kind)
        solveSeries(vm)
        assertEquals(5, vm.state.value.practice!!.score)
    }



    /** Pont Revue → Académie et bilan : la leçon du point faible s'ouvre et 5 exercices de son thème suivent. */
    @Test
    fun academyTrainTopicOpensLessonAndExercises() = test {
        val vm = academy(puzzles = FakePuzzles(puzzleSet.map { it.copy(primaryTheme = "hangingPiece") }))
        advanceUntilIdle()
        val t = com.ivanherboch.chessduck.feature.ReviewTopic.HANGING_PIECE
        vm.trainTopic(t.lessonId, t.theme); advanceUntilIdle()
        assertEquals(136, vm.state.value.lessonId)
        val p = vm.state.value.practice!!
        assertEquals(com.ivanherboch.chessduck.feature.PracticeKind.LESSON, p.kind)
        assertEquals(5, p.ids.size)
        solveSeries(vm)
        assertEquals(5, vm.state.value.practice!!.score)
        // Sans thème (développement : leçon des principes), seule la leçon s'ouvre.
        vm.closePractice()
        vm.trainTopic(1, ""); advanceUntilIdle()
        assertEquals(1, vm.state.value.lessonId)
        assertNull(vm.state.value.practice)
        assertTrue(vm.priorities.value.isEmpty())
    }

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

    /** Un coup qui garde le mat mais plus lentement : montré, compté comme une erreur, puis la position revient (pas de coup de la leçon joué). */
    @Test
    fun academySlowerMateIsRejectedWithoutPlayingTheLessonMove() = test {
        val engine = FakeEngine()
        val vm = academy(engine = engine)
        advanceUntilIdle()
        val l = Lessons.all.first { it.drill == "KRRK" }
        vm.open(l.id)
        vm.setChallenge(true)
        advanceUntilIdle()
        val pos = l.line.positions[0]
        val rh2 = Move.fromUci("h1h2")
        engine.scripted[pos.toFen().split(' ').take(4).joinToString(" ")] =
            SearchResult(l.line.moves[0], listOf(PvLine(l.line.moves[0], com.ivanherboch.chessduck.engine.MATE - 9), PvLine(rh2, com.ivanherboch.chessduck.engine.MATE - 13)))
        assertFalse(vm.challengeMove(rh2))
        assertEquals(rh2, vm.state.value.tentative?.last) // la tour va d'abord en h2
        advanceUntilIdle()
        assertNull(vm.state.value.tentative)
        assertEquals(0, vm.state.value.cursor) // la tour attendue n'a pas bougé
        assertEquals(1, vm.state.value.errors)
        assertTrue(vm.state.value.message.contains("plus lentement"))
    }

    @Test
    fun academyAcceptsEquivalentMovesAndShowsReplies() = test {
        val vm = academy() // faux moteur : toutes les positions se valent
        advanceUntilIdle()
        val l = Lessons.byId(1)
        vm.setChallenge(true)
        advanceUntilIdle()
        assertFalse(vm.challengeMove(Move.fromUci("d2d4"))) // pas le coup de la leçon…
        // La pièce va d'abord là où l'élève l'a jouée, pendant que l'Ordinateur juge le coup.
        assertEquals(Move.fromUci("d2d4"), vm.state.value.tentative?.last)
        assertEquals(com.ivanherboch.chessduck.chess.PAWN, com.ivanherboch.chessduck.chess.pieceType(vm.state.value.tentative!!.position[com.ivanherboch.chessduck.chess.sqFromName("d4")]))
        advanceUntilIdle()
        assertNull(vm.state.value.tentative)
        // … mais aussi bon : pas d'erreur, et rien n'est joué à la place de l'élève. La position revient et une flèche
        // verte montre le coup de la leçon, que l'élève joue lui-même.
        assertEquals(0, vm.state.value.errors)
        assertEquals(0, vm.state.value.cursor)
        assertEquals(l.line.moves[0], vm.state.value.suggest)
        assertTrue(vm.state.value.message.contains("aussi bon"))
        assertTrue(vm.challengeMove(l.line.moves[0]))
        advanceUntilIdle()
        assertNull(vm.state.value.suggest)
        assertTrue(vm.state.value.message.contains("Réponse ${l.line.sans[1]} : ${l.texts[1]}"))
        assertEquals(2, vm.state.value.cursor)
    }

    /** Cas signalé : Rh5+ au lieu de Ra5+ (même escalier, de l'autre côté) ; la tour ne doit pas revenir pendant que l'autre joue. */
    @Test
    fun academyMirroredMoveIsPraisedThenTheLessonMoveIsAsked() = test {
        val engine = FakeEngine()
        val vm = academy(engine = engine)
        advanceUntilIdle()
        val l = Lessons.all.first { it.drill == "KRRK" }
        vm.open(l.id)
        vm.setChallenge(true)
        advanceUntilIdle()
        val pos = l.line.positions[0]
        val rh5 = Move.fromUci("h1h5")
        engine.scripted[pos.toFen().split(' ').take(4).joinToString(" ")] =
            SearchResult(l.line.moves[0], listOf(PvLine(l.line.moves[0], com.ivanherboch.chessduck.engine.MATE - 9), PvLine(rh5, com.ivanherboch.chessduck.engine.MATE - 9)))
        assertFalse(vm.challengeMove(rh5))
        advanceUntilIdle()
        assertEquals(0, vm.state.value.cursor) // la tour a1 n'a pas été jouée à la place de l'élève
        assertEquals(0, vm.state.value.errors)
        assertEquals(l.line.moves[0], vm.state.value.suggest)
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

    /** Vision des cases : couleur (échiquier masqué), toucher la case, chemin du cavalier ; aucun chronomètre. */
    @Test
    fun notationSquareVision() = test {
        val vm = NotationViewModel(FakeSettings(), FakePuzzles(emptyList()))
        vm.startVision(com.ivanherboch.chessduck.feature.VisionKind.COLOR)
        val sq = vm.vision.value!!.question.square
        vm.answerColor(com.ivanherboch.chessduck.feature.SquareVision.isLight(sq))
        assertTrue(vm.vision.value!!.correct)
        assertEquals(1, vm.vision.value!!.score)
        vm.nextVision()
        assertEquals(1, vm.vision.value!!.total)
        assertTrue(!vm.vision.value!!.answered)
        vm.startVision(com.ivanherboch.chessduck.feature.VisionKind.TOUCH)
        val target = vm.vision.value!!.question.square
        vm.tapVision((target + 1) % 64)
        assertTrue(!vm.vision.value!!.correct && vm.vision.value!!.message.contains("marquée en vert"))
        vm.startVision(com.ivanherboch.chessduck.feature.VisionKind.KNIGHT)
        val q = vm.vision.value!!.question
        val n = com.ivanherboch.chessduck.feature.SquareVision.knightDistance(q.square, q.target)
        assertTrue(n in 1..4)
        assertEquals(com.ivanherboch.chessduck.chess.KNIGHT, com.ivanherboch.chessduck.chess.pieceType(vm.vision.value!!.position[q.square]))
        vm.answerKnight(n)
        assertTrue(vm.vision.value!!.correct)
        vm.closeVision()
        assertNull(vm.vision.value)
    }

    /** Calcul à l'aveugle (palier 5) : coups lus en français par la voix, écrits dans la carte, échiquier réaffichable. */
    @Test
    fun academyBlindCalculationIsSpokenAndShown() = test {
        val prefs = FakeSettings()
        prefs.setNarration(true)
        val voice = FakeNarrator()
        // Exercice du palier 5 : problèmes classés de 1800 à 2500.
        val vm = academy(voice = voice, prefs = prefs, puzzles = FakePuzzles(listOf(PUZZLE_0000D.copy(rating = 2000))))
        advanceUntilIdle()
        vm.startReflex(com.ivanherboch.chessduck.feature.ReflexKind.BLIND); advanceUntilIdle()
        val rx = vm.reflex.state.value!!
        val b = rx.blind!!
        assertEquals(listOf("Rd8", "Qxd8+"), b.announced)
        assertEquals("Calcul à l'aveugle", vm.trainingTitle(vm.state.value, vm.endgame.state.value, rx))
        // La carte écrit les coups (voix coupée ou TalkBack) ; la voix les lit en français, sans notation anglaise.
        assertTrue(rx.message, "Coups annoncés : 27… Rd8 28. Qxd8+" in rx.message)
        val said = voice.spoken.last()
        assertTrue(said, "tour" in said && "dame" in said && "Rd8" !in said && "Qxd8" !in said)
        // L'échiquier affiché reste la position de départ, puis peut être réaffiché après les coups annoncés.
        assertEquals(b.start, vm.reflex.state.value!!.position)
        vm.reflex.announce(); assertEquals(said, voice.spoken.last())
        vm.reflex.tap(com.ivanherboch.chessduck.chess.sqFromName("f6"))
        vm.reflex.tap(com.ivanherboch.chessduck.chess.sqFromName("d8"))
        assertEquals(listOf(true), vm.reflex.state.value!!.results)
        assertEquals(b.after, vm.reflex.state.value!!.position)
        // Sans voix : rien n'est lu, le texte suffit.
        prefs.setNarration(false); advanceUntilIdle()
        val before = voice.spoken.size
        vm.startReflex(com.ivanherboch.chessduck.feature.ReflexKind.BLIND); advanceUntilIdle()
        assertEquals(before, voice.spoken.size)
        vm.reflex.showBoard()
        assertTrue(vm.reflex.state.value!!.shown)
        vm.reflex.tap(com.ivanherboch.chessduck.chess.sqFromName("f6"))
        vm.reflex.tap(com.ivanherboch.chessduck.chess.sqFromName("d8"))
        assertEquals(listOf(false), vm.reflex.state.value!!.results) // juste, mais avec l'échiquier réaffiché
        vm.closeReflex()
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
