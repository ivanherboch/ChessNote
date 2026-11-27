package com.ivanherboch.chessduck.vm

import com.ivanherboch.chessduck.feature.AcademyRoute
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.cancelAndJoin
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.chess.SpokenFr
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
import com.ivanherboch.chessduck.engine.MATE
import com.ivanherboch.chessduck.ui.evalShort
import com.ivanherboch.chessduck.ui.DuckPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emitAll
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

    /**
     * Dans l'application, le fil principal exécute la coroutine de démarrage sans attendre (Main.immediate) : la leçon
     * s'ouvre pendant la construction même du ViewModel, qui doit donc être entièrement initialisé à ce moment-là.
     */
    @Test
    fun academyOpensItsLessonDuringConstructionOnAnImmediateDispatcher() {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        try {
            val vm = AcademyViewModel(FakeSettings(), FakeLessons(), FakeNarrator(), FakeEngine(), FakePuzzles(listOf(PUZZLE_0000D)))
            assertEquals(1, vm.state.value.lessonId)
            vm.viewModelScope.cancel()
        } finally {
            Dispatchers.setMain(main)
        }
    }

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
        // Le chargement du problème au démarrage tourne hors du temps virtuel : on l'arrête et on attend sa fin avant la fin
        // du test, sinon un travail encore en cours sur Dispatchers.IO reprend sur Dispatchers.Main après sa
        // réinitialisation et fait échouer le test suivant.
        vm.viewModelScope.coroutineContext.job.cancelAndJoin()
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

    /** « Voir la leçon » depuis un problème : la leçon du thème, non maîtrisée et proche du palier, s'ouvre dans Comprendre. */
    @Test
    fun academyOpensTheLessonOfAPuzzleTheme() = test {
        val lessons = FakeLessons()
        val forks = Lessons.all.filter { it.theme == "fork" }
        assertTrue(forks.size >= 2)
        val first = forks.minByOrNull { kotlin.math.abs(it.level - 2) }!!
        lessons.save(com.ivanherboch.chessduck.data.LessonProgress(first.id, masteredAt = 1, stars = 3))
        val vm = academy(lessons)
        advanceUntilIdle()
        vm.openLessonForTheme("fork"); advanceUntilIdle()
        val s = vm.state.value
        assertEquals("fork", Lessons.byId(s.lessonId).theme)
        assertTrue(s.lessonId != first.id)
        assertEquals(AcademyRoute.LESSON, s.route)
        assertFalse(s.challenge)
        // Thème sans leçon : rien ne change.
        vm.openLessonForTheme("aucunTheme"); advanceUntilIdle()
        assertEquals(s.lessonId, vm.state.value.lessonId)
    }

    @Test
    fun academyNextLessonAfterChallengeOpensGuided() = test {
        val vm = academy()
        advanceUntilIdle()
        val first = Lessons.all[0]
        vm.open(first.id)
        vm.setChallenge(true)
        playChallenge(vm)
        // « Leçon suivante » à la fin du Défi : la suivante du catalogue, en mode Guidé.
        vm.nextLesson(guided = true); advanceUntilIdle()
        assertEquals(Lessons.all[1].id, vm.state.value.lessonId)
        assertFalse(vm.state.value.challenge)
        // La flèche de l'en-tête garde le mode en cours.
        vm.setChallenge(true); advanceUntilIdle()
        vm.nextLesson(); advanceUntilIdle()
        assertEquals(Lessons.all[2].id, vm.state.value.lessonId)
        assertTrue(vm.state.value.challenge)
    }

    @Test
    fun newStudentStartsWithTheBasics() = test {
        // Premier lancement : la première leçon du cursus (« L'échiquier et la notation ») et le palier 1.
        val fresh = com.ivanherboch.chessduck.data.Settings()
        assertEquals(Lessons.all[0].id, fresh.lessonId)
        assertEquals(1, fresh.academyLevel)
        val vm = academy(prefs = FakeSettings(fresh))
        advanceUntilIdle()
        assertEquals(Lessons.all[0].id, vm.state.value.lessonId)
        // Le palier choisi à la main est enregistré.
        vm.setLevel(3); advanceUntilIdle()
    }

    /** Comprendre suivi jusqu'au bout : la leçon est notée « comprise » (coche, statut « En cours »), pas avant. */
    @Test
    fun understandIsMarkedDoneAtTheEnd() = test {
        val prefs = FakeSettings()
        val vm = academy(prefs = prefs)
        advanceUntilIdle()
        val active = backgroundScope.launch { vm.understood.collect {} }
        vm.open(5, guided = true); advanceUntilIdle()
        vm.step(1); advanceUntilIdle()
        assertFalse(5 in prefs.settings.first().understoodIds)
        vm.last(); advanceUntilIdle()
        assertTrue(5 in prefs.settings.first().understoodIds)
        assertTrue(5 in vm.understood.value)
        assertEquals("En cours", com.ivanherboch.chessduck.feature.lessonStatus(null, understood = true))
        assertEquals("À faire", com.ivanherboch.chessduck.feature.lessonStatus(null))
        active.cancel()
    }

    /** Retour à l'accueil depuis une leçon lue à voix haute : la voix se tait aussitôt. */
    @Test
    fun goingHomeSilencesTheVoice() = test {
        val voice = FakeNarrator()
        val prefs = FakeSettings()
        val vm = academy(voice = voice, prefs = prefs)
        advanceUntilIdle()
        prefs.setNarration(true); runCurrent()
        vm.open(5, guided = true); runCurrent()
        assertTrue(voice.speaking.value)
        vm.goHome(); runCurrent()
        assertFalse(voice.speaking.value)
        assertEquals(AcademyRoute.HOME, vm.state.value.route)
    }

    @Test
    fun academyRoutesBetweenHomeWorkshopAndSessions() = test {
        val vm = academy()
        advanceUntilIdle()
        // À l'ouverture, l'accueil ; le retour Android n'y fait rien (il quitte l'onglet).
        assertEquals(AcademyRoute.HOME, vm.state.value.route)
        assertFalse(vm.back())
        // Une séance lancée depuis une sous-liste de l'Atelier y ramène en la quittant (← ou retour Android).
        vm.openRoute(AcademyRoute.WORKSHOP_REFLEX)
        vm.startReflex(com.ivanherboch.chessduck.feature.ReflexKind.THREAT); advanceUntilIdle()
        assertEquals(AcademyRoute.LESSON, vm.state.value.route)
        assertEquals(AcademyRoute.WORKSHOP_REFLEX, vm.state.value.sessionFrom)
        assertTrue(vm.back())
        assertNull(vm.reflex.state.value)
        assertEquals(AcademyRoute.WORKSHOP_REFLEX, vm.state.value.route)
        assertNull(vm.state.value.sessionFrom)
        // Sous-liste → accueil.
        assertTrue(vm.back())
        assertEquals(AcademyRoute.HOME, vm.state.value.route)
        // Finale lancée depuis l'Atelier, puis retour à l'Atelier ; une leçon ouverte revient à l'accueil.
        vm.openRoute(AcademyRoute.WORKSHOP_ENDGAMES)
        vm.startEndgame(com.ivanherboch.chessduck.feature.EndgameFamily.KQK); advanceUntilIdle()
        vm.closeTraining()
        assertEquals(AcademyRoute.WORKSHOP_ENDGAMES, vm.state.value.route)
        vm.open(5, guided = true)
        assertEquals(AcademyRoute.LESSON, vm.state.value.route)
        assertTrue(vm.back())
        assertEquals(AcademyRoute.HOME, vm.state.value.route)
        // Une leçon choisie dans le catalogue commence par Comprendre, même si l'on était en Pratiquer.
        vm.setChallenge(true)
        vm.open(Lessons.all[3].id, guided = true)
        assertFalse(vm.state.value.challenge)
        // Les flèches gardent le mode en cours.
        vm.setChallenge(true); vm.nextLesson()
        assertTrue(vm.state.value.challenge)
        // Une séance lancée depuis la leçon y reste ; « Accueil » arrête tout, voix comprise.
        vm.startReflex(com.ivanherboch.chessduck.feature.ReflexKind.THREAT); advanceUntilIdle()
        assertNull(vm.state.value.sessionFrom)
        vm.closeTraining()
        assertEquals(AcademyRoute.LESSON, vm.state.value.route)
        vm.goHome()
        assertEquals(AcademyRoute.HOME, vm.state.value.route)
        assertFalse(vm.state.value.challenge)
        assertNull(vm.endgame.state.value)
    }

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
        // Variante réussie en Défi : badge, sans toucher à la maîtrise (qui suit la ligne principale).
        assertEquals(1, vm.state.value.variant)
        assertTrue("${italian.id}:1" in prefs.flow.value.variantsDone)
        assertTrue(vm.state.value.message.contains("Variante validée"))
        // Le menu Variantes reste actif en Défi : la variante 2 (ou la principale) se rejoue depuis le début.
        vm.setVariant(if (italian.variants.size > 1) 2 else 0)
        assertTrue(vm.state.value.challenge)
        assertFalse(vm.state.value.done)
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
        vm.setKeyMoments(false) // « Tous les coups » : chaque coup des Blancs est à deviner
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
        val engine = FakeEngine()
        val vm = academy(lessons, engine = engine)
        advanceUntilIdle()
        val kqk = Lessons.all.first { it.drill == "KQK" }
        vm.open(kqk.id)
        // Pratiquer : les positions de la finale contre l'Ordinateur, sous l'en-tête de la leçon.
        vm.setChallenge(true)
        advanceUntilIdle()
        assertNotNull(vm.endgame.state.value)
        assertTrue(vm.state.value.lessonPractice)
        assertNull(vm.sessionTitle(vm.state.value, vm.endgame.state.value, null))
        assertTrue(lessons.masteredLessons().first().isEmpty())
        // Faux moteur : la position de départ est évaluée gagnante (mat), et le coup joué la rend nulle (évaluation 0) :
        // le gain « s'envole », la position est perdue.
        vm.startEndgame(com.ivanherboch.chessduck.feature.EndgameFamily.KQK, fromLesson = true)
        val eg = vm.endgame.state.value!!
        val start = eg.position!!
        engine.scripted[start.toFen().split(' ').take(4).joinToString(" ")] =
            SearchResult(start.legalMoves.first(), listOf(PvLine(start.legalMoves.first(), com.ivanherboch.chessduck.engine.MATE - 9)))
        vm.endgame.move(eg.position!!.legalMoves.first { !eg.position!!.play(it).isCheckmate && eg.position!!.play(it).legalMoves.isNotEmpty() })
        advanceUntilIdle()
        val lost = vm.endgame.state.value!!
        assertTrue(lost.over)
        assertEquals(listOf(false), lost.results)
        // Le retournement de l'échiquier vaut pour la séance, sans toucher à celui de la leçon.
        val lessonFlip = vm.state.value.flipped
        vm.flip()
        assertTrue(vm.state.value.sessionFlipped)
        assertEquals(lessonFlip, vm.state.value.flipped)
        // L'Ordinateur joue sa réponse (flèche rouge), la jauge reçoit l'évaluation finale, l'erreur vibre.
        assertNotNull(lost.punish)
        assertEquals(lost.punish, lost.last)
        assertEquals(0, lost.evalWhite)
        assertEquals(false, lost.pulse?.good)
        // L'explication rappelle la méthode de la finale ; « Reprendre le coup » revient juste avant l'erreur.
        assertTrue(lost.message, "Méthode : ${com.ivanherboch.chessduck.feature.EndgameFamily.KQK.method}" in lost.message)
        assertEquals(eg.position!!.toFen(), lost.before?.toFen())
        // « Rejouer » : la même position, hors score.
        vm.endgame.retry()
        val again = vm.endgame.state.value!!
        assertFalse(again.over)
        assertTrue(again.replay)
        assertEquals(eg.position!!.toFen(), again.position!!.toFen())
        assertNull(again.evalWhite)
        // Indice en deux temps : la pièce (recherche), puis la flèche sans nouvelle recherche.
        vm.endgame.hint(); advanceUntilIdle()
        assertEquals(1, vm.endgame.state.value!!.hintStage)
        assertNotNull(vm.endgame.state.value!!.hint)
        vm.endgame.hint(); advanceUntilIdle()
        assertEquals(2, vm.endgame.state.value!!.hintStage)
        val p2 = vm.endgame.state.value!!.position!!
        vm.endgame.move(p2.legalMoves.first { !p2.play(it).isCheckmate && p2.play(it).legalMoves.isNotEmpty() })
        advanceUntilIdle()
        assertEquals(listOf(false), vm.endgame.state.value!!.results) // la position rejouée ne compte pas
        // Fermer la pratique ramène à Comprendre.
        vm.closeEndgame()
        assertNull(vm.endgame.state.value)
        assertFalse(vm.state.value.challenge)
    }

    @Test
    fun endgameDoesNotFailWhenTheEngineIsSilentOrUnderestimates() = test {
        val engine = FakeEngine()
        val trainer = com.ivanherboch.chessduck.feature.EndgameTrainer(this, engine, Random(5))
        trainer.start(com.ivanherboch.chessduck.feature.EndgameFamily.KPK_WIN, total = 1)
        advanceUntilIdle()
        val s0 = trainer.state.value!!
        val pos = s0.position!!
        // Le moteur évalue toute la position à 0 (il ne voit pas le gain) : un coup ne fait rien baisser, il est accepté.
        val m = pos.legalMoves.first { !pos.play(it).isCheckmate && pos.play(it).legalMoves.isNotEmpty() }
        trainer.move(m); advanceUntilIdle()
        assertFalse(trainer.state.value!!.message, trainer.state.value!!.over)
        assertTrue(trainer.state.value!!.results.isEmpty())
        // Le moteur ne répond pas : le coup est annulé, la position reste jouable, rien n'est compté.
        val p1 = trainer.state.value!!.position!!
        val m1 = p1.legalMoves.first { !p1.play(it).isCheckmate && p1.play(it).legalMoves.isNotEmpty() }
        engine.scripted[p1.play(m1).toFen().split(' ').take(4).joinToString(" ")] = null
        trainer.move(m1); advanceUntilIdle()
        val s1 = trainer.state.value!!
        assertFalse(s1.over)
        assertEquals(p1.toFen(), s1.position!!.toFen())
        assertTrue(s1.heroToMove)
        assertTrue(s1.message, "rejouez votre coup" in s1.message)
        assertTrue(s1.results.isEmpty())
        trainer.stop()
    }

    @Test
    fun endgameTripleRepetitionEndsThePosition() = test {
        val engine = FakeEngine()
        val trainer = com.ivanherboch.chessduck.feature.EndgameTrainer(this, engine, Random(5))
        trainer.start(com.ivanherboch.chessduck.feature.EndgameFamily.KQK, total = 1)
        advanceUntilIdle()
        val p0 = trainer.state.value!!.position!!
        fun key(p: com.ivanherboch.chessduck.chess.Position) = p.toFen().split(' ').take(4).joinToString(" ")
        fun quiet(p: com.ivanherboch.chessduck.chess.Position, m: Move) = p.play(m).let { !it.inCheck && it.legalMoves.isNotEmpty() }
        // Un aller-retour de la dame, auquel le roi noir répond par un aller-retour : la position de départ revient 3 fois.
        val (m, back, r) = p0.legalMoves.asSequence().filter { quiet(p0, it) }.mapNotNull { m ->
            val a = p0.play(m)
            a.legalMoves.firstOrNull { r -> val n = a.play(r); val b = Move(m.to, m.from); n.isLegal(b) && quiet(n, b) && n.play(b).isLegal(Move(r.to, r.from)) }
                ?.let { r -> Triple(m, Move(m.to, m.from), r) }
        }.first()
        val a1 = p0.play(m); val n1 = a1.play(r); val a2 = n1.play(back)
        engine.scripted[key(a1)] = SearchResult(r, listOf(PvLine(r, 0)))
        engine.scripted[key(a2)] = SearchResult(Move(r.to, r.from), listOf(PvLine(Move(r.to, r.from), 0)))
        repeat(2) {
            trainer.move(m); advanceUntilIdle()
            trainer.move(back); advanceUntilIdle()
        }
        val s = trainer.state.value!!
        assertTrue(s.message, s.over && "Triple répétition" in s.message)
        assertEquals(listOf(false), s.results) // il fallait gagner : la nulle est un échec
        trainer.stop()
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

    /** Révisions du jour : une seule épreuve par leçon (1 problème du thème, 1 position de finale), puis le calendrier. */
    @Test
    fun academyReviewsAreOneQuickTestPerLesson() = test {
        val now = 10 * com.ivanherboch.chessduck.feature.SrsScheduler.DAY
        val lessons = FakeLessons()
        val tactic = Lessons.all.first { it.theme.isNotEmpty() && it.drill.isEmpty() && it.category != com.ivanherboch.chessduck.content.LessonCategory.OPENING }
        val ending = Lessons.all.first { it.drill == "KQK" }
        for (l in listOf(tactic, ending)) lessons.save(com.ivanherboch.chessduck.data.LessonProgress(l.id, masteredAt = 1, stars = 3, srsStage = 1, nextReviewAt = now - 1))
        val vm = academy(lessons, puzzles = FakePuzzles(puzzleSet.map { it.copy(primaryTheme = tactic.theme) }), clock = { now })
        advanceUntilIdle()
        vm.startReviews(); advanceUntilIdle()
        val queue = vm.state.value.review!!.queue
        assertEquals(setOf(tactic.id, ending.id), queue.toSet())
        for (i in queue.indices) {
            val l = Lessons.byId(queue[i])
            if (l.id == tactic.id) {
                // Tactique : un seul problème inédit du thème, sans rejouer la leçon avant.
                val pr = vm.state.value.practice!!
                assertEquals(com.ivanherboch.chessduck.feature.PracticeKind.REVIEW, pr.kind)
                assertEquals(1, pr.ids.size)
                assertFalse(vm.state.value.challenge)
                assertTrue(vm.sessionTitle(vm.state.value, null, null)!!.label.startsWith("Révision ${i + 1} sur 2"))
                solveSeries(vm)
                assertEquals(2, lessons.progress(l.id)!!.srsStage) // mis à jour dès la fin de l'épreuve
            } else {
                // Finale : une seule position contre l'Ordinateur.
                assertEquals(1, vm.endgame.state.value!!.total)
            }
            vm.nextReview(); advanceUntilIdle()
        }
        assertTrue(vm.state.value.review!!.finished)
        vm.closeReviews()
        assertNull(vm.state.value.review)
    }

    @Test
    fun academyRepertoireReplaysTheChosenOpenings() = test {
        val prefs = FakeSettings()
        val lessons = FakeLessons()
        val vm = academy(lessons, prefs = prefs)
        advanceUntilIdle()
        // Rien de choisi ni de travaillé : aucune révision tirée au hasard parmi toutes les ouvertures.
        vm.startRepertoire(white = true); advanceUntilIdle()
        assertNull(vm.state.value.repertoire)
        // Ouvertures déjà travaillées : elles servent tant que rien n'est choisi.
        val whites = Lessons.all.filter { it.heroWhite && it.category == com.ivanherboch.chessduck.content.LessonCategory.OPENING }
        lessons.save(com.ivanherboch.chessduck.data.LessonProgress(whites[2].id, masteredAt = 0, stars = 1))
        assertEquals(listOf(whites[2].id), vm.repertoirePool(true, emptySet(), setOf(whites[2].id)).map { it.id })
        // Choix de l'élève : seules ces ouvertures sont révisées.
        vm.toggleRepertoire(whites[0].id); vm.toggleRepertoire(whites[1].id); advanceUntilIdle()
        assertEquals(setOf(whites[0].id, whites[1].id), prefs.settings.first().repertoireIds)
        vm.toggleRepertoire(whites[1].id); vm.toggleRepertoire(whites[1].id); advanceUntilIdle() // retirer puis remettre
        vm.startRepertoire(white = true)
        advanceUntilIdle()
        val rep = vm.state.value.repertoire!!
        assertEquals(2, rep.items.size)
        assertEquals(setOf(whites[0].id, whites[1].id), rep.items.map { it.first }.toSet())
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
        // Fourchette sans échec : le cavalier c2 attaque la dame e1 et la tour a1.
        val fork = com.ivanherboch.chessduck.data.Puzzle("FORK2", "6k1/8/8/8/3n4/8/8/R3Q1K1 w - - 0 1", "g1h1 d4c2", 600, 90, 100, " fork ", "fork")
        // L'Ordinateur confirme la menace : s'il rejouait, il prendrait la dame e1.
        val engine = FakeEngine()
        engine.scripted["6k1/8/8/8/8/8/2n5/R3Q2K b - -"] = SearchResult(Move.fromUci("c2e1"), listOf(PvLine(Move.fromUci("c2e1"), 900)))
        val vm = academy(puzzles = FakePuzzles(listOf(fork) + puzzleSet.map { it.copy(primaryTheme = "defensiveMove") }), engine = engine)
        advanceUntilIdle()
        vm.startReflex(com.ivanherboch.chessduck.feature.ReflexKind.THREAT)
        advanceUntilIdle()
        val rx = vm.reflex.state.value!!
        assertEquals(1, rx.total)
        // L'en-tête annonce l'entraînement à la place de la leçon ouverte.
        assertEquals("Quelle est la menace ?", vm.trainingTitle(vm.state.value, vm.endgame.state.value, rx))
        vm.reflex.tap(com.ivanherboch.chessduck.chess.sqFromName("e1"))
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

    /** En-têtes de séance, indicateur « séance en cours » et filtre du catalogue qui n'ouvre aucune leçon. */
    @Test
    fun academySessionHeadersAndFilter() = test {
        val vm = academy(puzzles = FakePuzzles(puzzleSet.map { it.copy(primaryTheme = "hangingPiece") }))
        advanceUntilIdle()
        assertNull(vm.sessionTitle(vm.state.value, null, null))
        // Pont depuis la revue de partie : la séance s'ouvre directement, sans passer par l'accueil.
        vm.trainTopic(136, "hangingPiece"); advanceUntilIdle()
        val t = vm.sessionTitle(vm.state.value, vm.endgame.state.value, vm.reflex.state.value)!!
        assertEquals("Pratique · 5 problèmes", t.label)
        assertEquals("Initiation : la pièce gratuite", t.title)
        assertEquals(AcademyRoute.LESSON, vm.state.value.route)
        vm.closeTraining(); advanceUntilIdle()
        assertNull(vm.state.value.practice)
        assertEquals(AcademyRoute.LESSON, vm.state.value.route)
        // Le QCM de fin de leçon a lui aussi son en-tête, et « Revenir à la leçon » le ferme.
        vm.open(2); vm.startQuiz()
        assertEquals("Question de fin de leçon", vm.sessionTitle(vm.state.value, null, null)!!.label)
        vm.closeTraining()
        assertNull(vm.state.value.quiz)
        // Accueil : la leçon conseillée (la leçon 1) est la « Prochaine leçon » ; une leçon commencée devient « Continuer ».
        val home = backgroundScope.launch { vm.home.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.home.value.recommended)
        assertNull(vm.home.value.continueId)
        home.cancel()
        // Filtrer une catégorie ne change pas la leçon ouverte.
        vm.setFilter(com.ivanherboch.chessduck.content.LessonCategory.ENDGAME)
        assertEquals(2, vm.state.value.lessonId)
        assertEquals("2 · Débutant", com.ivanherboch.chessduck.feature.levelName(2))
        assertEquals("2, Débutant", com.ivanherboch.chessduck.feature.levelSpoken(2))
    }

    /** Calcul à l'aveugle : la case de départ est écrite, « Réécouter » explique la voix coupée. */
    @Test
    fun academyBlindFeedbackWithoutVoice() = test {
        val voice = FakeNarrator()
        val vm = academy(voice = voice, puzzles = FakePuzzles(listOf(PUZZLE_0000D.copy(rating = 2000))))
        advanceUntilIdle()
        vm.startReflex(com.ivanherboch.chessduck.feature.ReflexKind.BLIND); advanceUntilIdle()
        val before = voice.spoken.size
        vm.replayBlind()
        assertEquals(before, voice.spoken.size)
        assertTrue(vm.reflex.state.value!!.message, "lecture vocale est désactivée" in vm.reflex.state.value!!.message)
        assertTrue("Coups annoncés" in vm.reflex.state.value!!.message)
        vm.reflex.tap(com.ivanherboch.chessduck.chess.sqFromName("f6"))
        assertTrue(vm.reflex.state.value!!.message, "Départ : f6" in vm.reflex.state.value!!.message)
        vm.reflex.tap(com.ivanherboch.chessduck.chess.sqFromName("f6")) // second toucher : la case est désélectionnée
        assertEquals(-1, vm.reflex.state.value!!.from)
        assertTrue("Départ" !in vm.reflex.state.value!!.message)
        // « Voir la position » : la position après les coups annoncés, avec la marche à suivre ; puis « Solution » :
        // la question est manquée et l'on peut passer à la suivante (jamais bloqué).
        vm.reflex.showBoard()
        assertTrue(vm.reflex.state.value!!.shown)
        assertTrue(vm.reflex.state.value!!.message, "position après les coups annoncés" in vm.reflex.state.value!!.message)
        assertEquals(vm.reflex.state.value!!.blind!!.after.toFen(), vm.reflex.state.value!!.position!!.toFen())
        vm.reflex.giveUp()
        assertTrue(vm.reflex.state.value!!.answered)
        assertEquals(listOf(false), vm.reflex.state.value!!.results)
        assertTrue(vm.reflex.state.value!!.message, "Solution : ${vm.reflex.state.value!!.blind!!.answer}" in vm.reflex.state.value!!.message)
        vm.reflex.next()
        assertTrue(vm.reflex.state.value!!.finished || !vm.reflex.state.value!!.answered)
        vm.closeReflex()
    }

    /** Calcul à l'aveugle, voix active : les deux coups sont annoncés en français, « Réécouter » les relit, et ils restent écrits. */
    @Test
    fun academyBlindAnnouncesTheMovesAloud() = test {
        val voice = FakeNarrator()
        val prefs = FakeSettings()
        val vm = academy(voice = voice, prefs = prefs, puzzles = FakePuzzles(listOf(PUZZLE_0000D.copy(rating = 2000))))
        advanceUntilIdle()
        prefs.setNarration(true); runCurrent()
        vm.startReflex(com.ivanherboch.chessduck.feature.ReflexKind.BLIND); advanceUntilIdle()
        val said = voice.spoken.lastOrNull { "Coups annoncés" in it }
        assertNotNull(said)
        val b = vm.reflex.state.value!!.blind!!
        // Coups prononcés en français (« cavalier », « prend »…), jamais en notation anglaise.
        assertEquals(com.ivanherboch.chessduck.chess.SpokenFr.text(said!!), said)
        assertTrue(said, com.ivanherboch.chessduck.chess.SpokenFr.move(b.announced[0]) in said)
        assertTrue(vm.reflex.state.value!!.message, "Coups annoncés" in vm.reflex.state.value!!.message) // écrits aussi
        val before = voice.spoken.size
        vm.replayBlind()
        assertEquals(before + 1, voice.spoken.size)
        assertEquals(said, voice.spoken.last())
        vm.closeReflex()
        assertFalse(voice.speaking.value)
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
        advanceTimeBy(1_000); runCurrent() // réponse adverse affichée (flèche rouge), avec la conséquence de la faute
        assertNotNull(vm.state.value.refute?.arrow)
        assertTrue(vm.state.value.message, vm.state.value.message.startsWith("Réfutation : après a3"))
        // Pause : la réfutation reste affichée et le message invite à reprendre.
        vm.holdRefutation()
        assertTrue(vm.state.value.message, "Réessayer" in vm.state.value.message)
        assertEquals(0L, vm.state.value.refuteUntil)
        vm.resumeAtMiss()
        assertNull(vm.state.value.refute)
        assertNotNull(vm.state.value.anim) // le coup glisse en arrière
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
        // … mais aussi bon : pas d'erreur, rien n'est joué à la place de l'élève, et la flèche verte montre aussitôt
        // le coup de la leçon.
        assertEquals(0, vm.state.value.errors)
        assertEquals(0, vm.state.value.cursor)
        assertEquals(l.line.moves[0], vm.state.value.suggest)
        assertTrue(vm.state.value.message, vm.state.value.message.contains("Bon coup aussi"))
        assertTrue(vm.challengeMove(l.line.moves[0]))
        advanceUntilIdle()
        assertNull(vm.state.value.suggest)
        assertTrue(vm.state.value.message.contains("Réponse ${l.line.sans[1]} : ${l.texts[1]}"))
        assertEquals(2, vm.state.value.cursor)
    }

    /** L'Ordinateur ne répond pas (démarrage lent, occupé) : le coup est à rejouer, sans erreur comptée. */
    @Test
    fun challengeMoveIsNotCountedWrongWhenTheEngineCannotJudge() = test {
        val engine = FakeEngine()
        val vm = academy(engine = engine)
        advanceUntilIdle()
        val l = Lessons.byId(1)
        vm.open(l.id)
        vm.setChallenge(true)
        advanceUntilIdle()
        val pos = l.line.positions[0]
        val other = pos.legalMoves.first { it != l.line.moves[0] }
        fun key(p: com.ivanherboch.chessduck.chess.Position) = p.toFen().split(' ').take(4).joinToString(" ")
        for (p in listOf(pos, pos.play(other), pos.play(l.line.moves[0]))) engine.scripted[key(p)] = null
        assertFalse(vm.challengeMove(other))
        advanceUntilIdle()
        assertEquals(0, vm.state.value.errors)
        assertFalse(vm.state.value.judging)
        assertTrue(vm.state.value.message, vm.state.value.message.contains("pas pu juger"))
        // Le coup de la leçon reste jouable aussitôt.
        assertTrue(vm.challengeMove(l.line.moves[0]))
    }

    /** Comprendre : la lecture automatique déroule toute la leçon, sans s'arrêter aux anciens arrêts « À vous ». */
    @Test
    fun understandPlaysTheWholeLessonWithoutStops() = test {
        val vm = academy()
        advanceUntilIdle()
        val l = Lessons.byId(1)
        assertTrue(l.pauseSteps.isNotEmpty())
        assertFalse(vm.state.value.challenge)
        vm.togglePlay()
        advanceTimeBy(600_000); runCurrent()
        assertEquals(l.line.size, vm.state.value.cursor)
        assertFalse(vm.state.value.playing)
        // ⏮ et ⏭ restent les mêmes commandes, à toute position.
        vm.rewind(); assertEquals(0, vm.state.value.cursor)
        vm.step(1); vm.step(1); assertEquals(2, vm.state.value.cursor)
        vm.last(); assertEquals(l.line.size, vm.state.value.cursor)
    }

    /** Pratiquer lance l'exercice adapté à la leçon : série de problèmes, finale, ou jeu des coups. */
    @Test
    fun practiseStartsTheRightActivityForEachLesson() = test {
        val tactic = Lessons.all.first { it.theme.isNotEmpty() && it.drill.isEmpty() && it.category != com.ivanherboch.chessduck.content.LessonCategory.OPENING }
        val set = (1..5).map { PUZZLE_0000D.copy(id = "Q$it", primaryTheme = tactic.theme, rating = 1000) }
        val vm = academy(puzzles = FakePuzzles(set))
        advanceUntilIdle()
        // Tactique : la série de 5 problèmes, sous l'en-tête de la leçon (pas d'en-tête de séance).
        vm.open(tactic.id)
        vm.setChallenge(true)
        advanceUntilIdle()
        assertEquals(com.ivanherboch.chessduck.feature.PracticeKind.LESSON, vm.state.value.practice?.kind)
        assertTrue(vm.state.value.lessonPractice)
        assertNull(vm.sessionTitle(vm.state.value, vm.endgame.state.value, vm.reflex.state.value))
        // « Revoir les coups » (fermer la série) : retour à Comprendre, sur la leçon.
        vm.closePractice()
        assertFalse(vm.state.value.challenge)
        assertNull(vm.state.value.practice)
        // Finale avec générateur : 5 positions contre l'Ordinateur.
        val kqk = Lessons.all.first { it.drill == "KQK" }
        vm.open(kqk.id)
        vm.setChallenge(true)
        advanceUntilIdle()
        assertEquals(com.ivanherboch.chessduck.feature.EndgameFamily.KQK, vm.endgame.state.value?.family)
        assertEquals(5, vm.endgame.state.value?.total)
        // Revenir à Comprendre arrête la finale.
        vm.setChallenge(false)
        assertNull(vm.endgame.state.value)
        // Ouverture : la ligne se rejoue sur l'échiquier.
        val opening = Lessons.byId(1)
        vm.open(opening.id)
        vm.setChallenge(true)
        advanceUntilIdle()
        assertNull(vm.state.value.practice)
        assertNull(vm.endgame.state.value)
        assertFalse(vm.state.value.lessonPractice)
        // Bases : seuls les coups clés (arrêts de la leçon) sont à jouer.
        val basics = Lessons.all.first { it.category == com.ivanherboch.chessduck.content.LessonCategory.BASICS && it.pauseSteps.isNotEmpty() && it.theme.isEmpty() && it.drill.isEmpty() }
        vm.open(basics.id)
        vm.setChallenge(true)
        advanceUntilIdle()
        assertTrue(vm.state.value.message, vm.state.value.message.contains("coups clés"))
        assertTrue(vm.state.value.cursor in basics.pauseSteps || vm.state.value.done)
    }

    /** Fin de Comprendre : la question de fin, puis la pratique. */
    @Test
    fun endOfLessonOffersTheQuizThenPractice() = test {
        val vm = academy()
        advanceUntilIdle()
        val l = Lessons.all.first { it.quiz != null }
        vm.open(l.id)
        vm.last()
        vm.startQuiz()
        val q = vm.state.value.quiz!!
        assertFalse(vm.state.value.quizPassed)
        vm.answerQuiz(q.correct)
        assertTrue(vm.state.value.quizPassed)
        vm.closeQuiz()
        vm.setChallenge(true)
        advanceUntilIdle()
        assertTrue(vm.state.value.challenge)
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
                // L'énoncé dit quoi chercher (le thème) et rappelle la règle de la leçon.
                val msg = vm.state.value.practice!!.message
                assertTrue(msg, "Rappel de la leçon « ${l.title} » : ${l.takeaway}" in msg)
                com.ivanherboch.chessduck.content.PuzzleThemes.byKey[l.theme]?.let { assertTrue(msg, "À chercher : ${it.name.lowercase()}" in msg) }
                // Mauvais coup : réfuté (comme dans les Problèmes), puis « Réessayer » ; le problème compte encore s'il est
                // résolu ensuite sans la flèche de l'indice.
                vm.practiceMove(line.positions[1].legalMoves.first { it != line.moves[1] && !line.positions[1].play(it).isCheckmate })
                assertNotNull(vm.state.value.practice!!.refute)
                assertTrue(vm.state.value.practice!!.hadError)
                assertFalse(vm.state.value.practice!!.failed)
                advanceTimeBy(1_000); runCurrent()
                assertNotNull(vm.state.value.practice!!.refute?.arrow)
                vm.retryPractice()
                assertNull(vm.state.value.practice!!.refute)
                vm.practiceMove(line.moves[1]); advanceUntilIdle()
                // ‹ et › : revoir le coup joué, puis y revenir (l'échiquier n'accepte un coup qu'au bout de la ligne).
                val reached = vm.state.value.practice!!.progress
                vm.practiceBack(); assertEquals(reached - 1, vm.state.value.practice!!.cursor)
                vm.practiceForward(); assertEquals(reached, vm.state.value.practice!!.cursor)
                vm.practiceMove(line.moves[3]); advanceUntilIdle()
                assertTrue(vm.state.value.practice!!.solved)
                assertEquals(listOf(true), vm.state.value.practice!!.results)
                assertTrue(vm.state.value.practice!!.message.contains("après une erreur"))
            } else if (k == 1) {
                // « Solution » après une erreur : le problème est compté manqué, la flèche montre le coup.
                vm.practiceMove(line.positions[1].legalMoves.first { it != line.moves[1] && !line.positions[1].play(it).isCheckmate })
                vm.retryPractice()
                vm.practiceSolution()
                assertTrue(vm.state.value.practice!!.failed)
                assertEquals(listOf(true, false), vm.state.value.practice!!.results)
            } else if (k == 2) {
                // Plusieurs erreurs, puis la flèche de l'indice 2/2 : ce problème ne compte pas.
                repeat(3) {
                    vm.practiceMove(line.positions[1].legalMoves.first { it != line.moves[1] && !line.positions[1].play(it).isCheckmate })
                    vm.retryPractice()
                }
                vm.practiceHint(); vm.practiceHint()
                vm.practiceMove(line.moves[1]); advanceUntilIdle()
                vm.practiceMove(line.moves[3]); advanceUntilIdle()
                assertTrue(vm.state.value.practice!!.solved)
                assertEquals(listOf(true, false, false), vm.state.value.practice!!.results)
            } else {
                vm.practiceMove(line.moves[1]); advanceUntilIdle()
                vm.practiceMove(line.moves[3]); advanceUntilIdle()
                assertTrue(vm.state.value.practice!!.solved)
            }
            vm.nextPractice(); advanceUntilIdle()
        }
        val pr = vm.state.value.practice!!
        assertTrue(pr.finished)
        assertEquals(3, pr.score) // la solution montrée et la flèche de l'indice comptent : le seuil de 4 sur 5 n'est pas atteint
        assertTrue(lessons.masteredLessons().first().isEmpty())
        assertEquals(3, lessons.progress(l.id)?.practicePassed)
        assertEquals(3, puzzles.solved.value.size)
        vm.closePractice()
        assertNull(vm.state.value.practice)
    }

    @Test
    fun academyOpeningPracticeMastersWithoutAnExtraValidation() = test {
        val lessons = FakeLessons()
        val vm = academy(lessons)
        advanceUntilIdle()
        // Leçon de stratégie : Pratiquer ne demande que les coups clés, puis la réussir maîtrise la leçon (pas d'étape
        // « À valider » en plus).
        val l = Lessons.all.first {
            it.category == com.ivanherboch.chessduck.content.LessonCategory.STRATEGY && it.theme.isEmpty() && it.pauseSteps.isNotEmpty()
        }
        vm.open(l.id)
        vm.setChallenge(true)
        advanceUntilIdle()
        assertNull(vm.state.value.practice)
        assertTrue(vm.state.value.message, vm.state.value.message.contains("coups clés"))
        while (!vm.state.value.done) {
            assertTrue(vm.state.value.cursor in l.pauseSteps)
            assertTrue(vm.challengeMove(l.line.moves[vm.state.value.cursor]))
            advanceUntilIdle()
        }
        assertEquals(3, vm.state.value.stars)
        assertEquals(listOf(l.id), lessons.masteredLessons().first())
        assertTrue(vm.state.value.message, vm.state.value.message.contains("Leçon maîtrisée"))
    }

    /** « Jouer cette position » : partie d'entraînement sans ELO, enregistrée avec sa position de départ et relue en revue. */
    @Test
    fun coachedGameStopsABlunderBeforeItIsPlayed() = test {
        val games = FakeGames()
        val engine = FakeEngine()
        val vm = PlayViewModel(FakeSettings(), games, engine, GameAnalysisService(engine, games, backgroundScope))
        advanceUntilIdle()
        // Le moteur (scripté) juge que Td4 perd 6 pions : le coup doit être arrêté.
        // Td4 se jette sous le cavalier e6 : Nxd4 la gagne.
        val fen = "6k1/5ppp/4n3/8/8/8/5PPP/3R2K1 w - - 0 1"
        vm.startFromPosition(fen, playerWhite = true)
        advanceUntilIdle()
        assertTrue(vm.state.value.coached)
        val blunder = Move.fromUci("d1d4")
        val after = com.ivanherboch.chessduck.chess.Position.fromFen(fen).play(blunder)
        val take = Move.fromUci("e6d4")
        engine.scripted[after.toFen().split(' ').take(4).joinToString(" ")] = SearchResult(take, listOf(PvLine(take, 600)))
        vm.playerMove(blunder)
        advanceUntilIdle()
        val w = vm.state.value.warning
        assertTrue("le coup doit être arrêté", w != null)
        assertEquals(0, vm.state.value.line.size) // rien n'est joué
        assertFalse(vm.state.value.canPlay)
        assertEquals(com.ivanherboch.chessduck.feature.COACH_STOPS - 1, vm.state.value.coachLeft)
        // Changer de coup : la position reste jouable ; jouer quand même : le coup part.
        vm.dismissWarning()
        assertTrue(vm.state.value.canPlay)
        vm.playerMove(blunder); advanceUntilIdle()
        vm.playAnyway(); advanceUntilIdle()
        assertTrue(vm.state.value.line.size >= 1)
        assertEquals(blunder, vm.state.value.line.moves[0])
    }

    /** Filet de sécurité (partie normale) : la gaffe est arrêtée, « Pourquoi ? » l'explique, et la partie ne compte plus pour l'ELO. */
    @Test
    fun safetyNetStopsABlunderAndMakesTheGameUnrated() = test {
        val games = FakeGames()
        val engine = FakeEngine()
        val prefs = FakeSettings(com.ivanherboch.chessduck.data.Settings(lessonId = 1, academyLevel = 2, safetyNet = true))
        val vm = PlayViewModel(prefs, games, engine, GameAnalysisService(engine, games, backgroundScope))
        advanceUntilIdle()
        assertTrue(vm.state.value.coached) // réglage mémorisé : actif dès la nouvelle partie
        fun key(p: com.ivanherboch.chessduck.chess.Position) = p.toFen().split(' ').take(4).joinToString(" ")
        // 1.e4 d5 : l'Ordinateur répond d5 (scripté), ce qui ouvre la diagonale du fou c8.
        val start = vm.state.value.position
        val afterE4 = start.play(Move.fromUci("e2e4"))
        engine.scripted[key(afterE4)] = SearchResult(Move.fromUci("d7d5"), listOf(PvLine(Move.fromUci("d7d5"), 0)))
        vm.playerMove(Move.fromUci("e2e4")); advanceUntilIdle()
        assertNull(vm.state.value.warning) // coup sans danger : joué, l'Ordinateur répond
        assertEquals(2, vm.state.value.line.size)
        // 2.Qg4 ?? : la dame se jette sous le fou c8.
        val blunder = Move.fromUci("d1g4")
        val after = vm.state.value.position.play(blunder)
        engine.scripted[key(after)] = SearchResult(Move.fromUci("c8g4"), listOf(PvLine(Move.fromUci("c8g4"), 900)))
        vm.playerMove(blunder)
        // Fluidité : le coup s'affiche aussitôt, pendant la vérification…
        assertEquals(3, vm.state.value.line.size)
        assertTrue(vm.state.value.checking)
        advanceUntilIdle()
        // … puis la gaffe est retirée de l'échiquier, avec l'arrêt.
        assertEquals(2, vm.state.value.line.size)
        val w = vm.state.value.warning!!
        assertTrue(w.text, w.text.startsWith("Filet de sécurité"))
        assertTrue(w.why!!, "dame" in w.why!!)
        assertTrue(vm.state.value.netUsed)
        vm.showWhy()
        assertTrue(vm.state.value.warning!!.showWhy)
        vm.playAnyway(); advanceUntilIdle()
        assertEquals(blunder, vm.state.value.line.moves[2])
        // Un bon coup ensuite : affiché aussitôt, et l'Ordinateur répond sans arrêt.
        val ply = vm.state.value.line.size
        val good = vm.state.value.position.legalMoves.first()
        vm.playerMove(good)
        assertEquals(ply + 1, vm.state.value.line.size)
        advanceUntilIdle()
        assertNull(vm.state.value.warning)
        assertTrue(vm.state.value.line.size >= ply + 2)
        val elo = prefs.settings.first().elo
        vm.resign(); advanceUntilIdle()
        assertEquals(elo, prefs.settings.first().elo)
        assertTrue(vm.state.value.result!!.text, "filet de sécurité" in vm.state.value.result!!.text)
        // Le couper est mémorisé pour les parties suivantes.
        vm.newGame(); advanceUntilIdle()
        vm.toggleCoach(); advanceUntilIdle()
        assertFalse(prefs.settings.first().safetyNet)
        assertFalse(vm.state.value.coached)
    }

    /** Filet : un coup rejoué depuis l'historique est vérifié aussi ; une gaffe rend la partie d'origine intacte. */
    @Test
    fun safetyNetChecksMovesReplayedFromHistory() = test {
        val games = FakeGames()
        val engine = FakeEngine()
        val prefs = FakeSettings(com.ivanherboch.chessduck.data.Settings(lessonId = 1, academyLevel = 2, safetyNet = true))
        val vm = PlayViewModel(prefs, games, engine, GameAnalysisService(engine, games, backgroundScope))
        advanceUntilIdle()
        fun key(p: com.ivanherboch.chessduck.chess.Position) = p.toFen().split(' ').take(4).joinToString(" ")
        val start = vm.state.value.position
        val afterE4 = start.play(Move.fromUci("e2e4"))
        engine.scripted[key(afterE4)] = SearchResult(Move.fromUci("d7d5"), listOf(PvLine(Move.fromUci("d7d5"), 0)))
        vm.playerMove(Move.fromUci("e2e4")); advanceUntilIdle()
        vm.playerMove(Move.fromUci("g1f3")); advanceUntilIdle()
        val full = vm.state.value.line
        assertEquals(4, full.size)
        // Retour après 1.e4 d5, puis Qg4 ?? (le fou c8 la prend).
        vm.goTo(2)
        val blunder = Move.fromUci("d1g4")
        val after = vm.state.value.position.play(blunder)
        engine.scripted[key(after)] = SearchResult(Move.fromUci("c8g4"), listOf(PvLine(Move.fromUci("c8g4"), 900)))
        vm.playerMove(blunder)
        // Pendant la vérification, la navigation est bloquée.
        vm.goTo(0)
        assertEquals(3, vm.state.value.cursor)
        advanceUntilIdle()
        assertNotNull(vm.state.value.warning)
        assertTrue(vm.state.value.line === full) // la partie d'origine est rendue intacte…
        assertEquals(2, vm.state.value.cursor) // … à la position où l'on rejouait
    }

    /** Séance du jour : les étapes du jour, cochées une à une, puis la série de jours. */
    @Test
    fun dailySessionPlansStepsAndCountsTheStreak() = test {
        val day = 86_400_000L
        var now = 20_000 * day + 12 * 3_600_000L
        val lessons = FakeLessons()
        lessons.save(com.ivanherboch.chessduck.data.LessonProgress(5, masteredAt = 1, stars = 3, srsStage = 1, nextReviewAt = 1))
        val prefs = FakeSettings()
        val vm = academy(lessons, prefs = prefs, clock = { now })
        val sub = backgroundScope.launch { vm.daily.collect {} }
        advanceUntilIdle()
        val plan = vm.daily.value
        assertEquals(listOf(com.ivanherboch.chessduck.feature.DailyStep.REVIEWS, com.ivanherboch.chessduck.feature.DailyStep.LESSON), plan.steps)
        assertEquals("Commencer la séance : révisions (1)", plan.buttonLabel())
        // Le bouton lance la première étape : les révisions.
        vm.startDaily(); advanceUntilIdle()
        assertNotNull(vm.state.value.review)
        assertTrue(vm.state.value.dailyRunning)
        playChallenge(vm)
        vm.nextReview(); advanceUntilIdle()
        assertTrue(vm.state.value.dailyPrompt) // bandeau « Étape terminée · Suite »
        assertEquals(setOf(com.ivanherboch.chessduck.feature.DailyStep.REVIEWS), vm.daily.value.done)
        assertEquals(0, vm.daily.value.streak)
        // Suite : la leçon ; Pratiquer réussi termine la séance et ouvre la série.
        vm.closeReviews()
        vm.startDaily(); advanceUntilIdle()
        vm.setChallenge(true); advanceUntilIdle()
        playChallenge(vm)
        advanceUntilIdle()
        assertTrue(vm.daily.value.finished)
        assertEquals(1, vm.daily.value.streak)
        assertTrue(vm.daily.value.buttonLabel()!!.startsWith("Séance terminée ✓"))
        sub.cancel()
    }

    /** « Jouer cette position » à la première ouverture du Jeu Libre : la partie de départ ne doit pas l'écraser. */
    @Test
    fun lessonPositionIsNotOverwrittenByTheStartingGame() = test {
        val games = FakeGames()
        val engine = FakeEngine()
        val base = FakeSettings()
        var reads = 0
        // La première lecture des réglages (celle de la partie de départ) est lente, les suivantes immédiates.
        val slow = object : com.ivanherboch.chessduck.data.SettingsRepository by base {
            override val settings = kotlinx.coroutines.flow.flow {
                if (reads++ == 0) kotlinx.coroutines.delay(500)
                emitAll(base.flow)
            }
        }
        val vm = PlayViewModel(slow, games, engine, GameAnalysisService(engine, games, backgroundScope))
        val fen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1"
        vm.startFromPosition(fen, playerWhite = true)
        advanceUntilIdle()
        assertTrue(vm.state.value.fromPosition)
        assertEquals(fen, vm.state.value.line.startFen)
    }

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
        assertEquals(com.ivanherboch.chessduck.feature.positionElo(2), vm.state.value.aiElo) // force du palier 2
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
        // Comprendre ne s'arrête plus aux anciens arrêts « À vous » : la lecture continue, coup après coup, et la
        // voix ne lit que le commentaire.
        voice.finish()
        advanceTimeBy(601); runCurrent()
        assertEquals(4, vm.state.value.cursor)
        assertTrue(vm.state.value.playing)
        assertEquals(SpokenFr.text("${SpokenFr.move(l.line.sans[3])}. ${l.texts[3]}"), voice.spoken.last())
        vm.pause()
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
        // Pratiquer : aucune lecture.
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
        val saved = SavedStateHandle(mapOf("academy_lesson" to 5, "academy_challenge" to false, "academy_cursor" to 3, "academy_route" to "LESSON"))
        val vm = academy(saved = saved)
        runCurrent()
        assertEquals(5, vm.state.value.lessonId)
        assertEquals(3, vm.state.value.cursor)
        vm.goTo(4); runCurrent()
        assertEquals(4, saved.get<Int>("academy_cursor"))
        // La leçon en cours est restaurée à l'écran (pas l'accueil), et l'écran affiché est mémorisé.
        assertEquals(AcademyRoute.LESSON, vm.state.value.route)
        vm.goHome(); runCurrent()
        assertEquals("HOME", saved.get<String>("academy_route"))
    }

    @Test
    fun modelGameKeyMomentsAreTheOnlyGuesses() = test {
        val vm = academy()
        advanceUntilIdle()
        val l = Lessons.all.first { it.kind == com.ivanherboch.chessduck.content.LessonKind.MODEL_GAME }
        assertTrue(l.pauseSteps.size >= 5)
        vm.open(l.id)
        vm.setChallenge(true)
        assertTrue(vm.state.value.keyMoments) // coups clés par défaut
        assertTrue(vm.state.value.message, vm.state.value.message.contains("coups clés"))
        playChallenge(vm)
        // Seuls les moments clés sont devinés (3 points chacun) ; les autres coups se sont joués seuls.
        assertEquals(3 * l.pauseSteps.size, vm.state.value.guessMax)
        assertEquals(vm.state.value.guessMax, vm.state.value.guessScore)
    }

    @Test
    fun academyResumesAnInterruptedChallenge() = test {
        // Défi de la leçon 1 interrompu par Android au 3e demi-coup, avec une erreur déjà commise.
        val saved = SavedStateHandle(mapOf("academy_lesson" to 1, "academy_challenge" to true, "academy_cursor" to 2, "academy_errors" to 1, "academy_route" to "LESSON"))
        val vm = academy(saved = saved)
        advanceUntilIdle()
        assertTrue(vm.state.value.challenge)
        assertTrue(vm.state.value.cursor >= 2)
        assertEquals(1, vm.state.value.errors)
        assertTrue(vm.state.value.message, vm.state.value.message.contains("Exercice repris"))
    }

    @Test
    fun reviewsLengthenOnlyWhenFluentAndErrorsAccumulate() = test {
        val srs = com.ivanherboch.chessduck.feature.SrsScheduler { 0L }
        val p = com.ivanherboch.chessduck.data.LessonProgress(1, masteredAt = 1, srsStage = 2)
        assertEquals(3, srs.reviewed(p, success = true, fluent = true).srsStage)
        assertEquals(2, srs.reviewed(p, success = true, fluent = false).srsStage) // hésitation : même intervalle
        assertEquals(1, srs.reviewed(p, success = false).srsStage)
        // Les erreurs du Défi s'additionnent d'une tentative à l'autre (bilan des points faibles).
        val lessons = FakeLessons()
        lessons.save(com.ivanherboch.chessduck.data.LessonProgress(1, masteredAt = 0, errors = 2, hintsUsed = 1))
        val vm = academy(lessons)
        advanceUntilIdle()
        vm.open(1)
        vm.setChallenge(true)
        playChallenge(vm)
        assertEquals(2, lessons.progress(1)!!.errors)
        assertEquals(1, lessons.progress(1)!!.hintsUsed)
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
        // Exercice suivant généré sur Dispatchers.Default : on attend sa fin avant celle du test.
        vm.viewModelScope.coroutineContext.job.cancelAndJoin()
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
        // La génération de l'exercice de notation tourne sur Dispatchers.Default : on attend sa fin avant celle du test.
        vm.viewModelScope.coroutineContext.job.cancelAndJoin()
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
        // Coup joué puis nouvelle partie pendant la réflexion : la réponse de l'ancienne partie est ignorée, et la partie
        // quittée en cours compte comme un abandon.
        vm.playerMove(vm.state.value.position.legalMoves.first())
        vm.newGame()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.line.size)
        assertEquals(1, games.stored.value.size)
        // Abandon après un échange : partie enregistrée et ELO mis à jour.
        vm.playerMove(Move.fromUci("d2d4"))
        advanceUntilIdle()
        vm.resign()
        advanceUntilIdle()
        assertNotNull(vm.state.value.result)
        assertEquals(2, games.stored.value.size)
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
