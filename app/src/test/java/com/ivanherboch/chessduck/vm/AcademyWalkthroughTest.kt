package com.ivanherboch.chessduck.vm

import androidx.lifecycle.SavedStateHandle
import com.ivanherboch.chessduck.content.LessonKind
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.feature.AcademyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Parcours complet de chaque leçon, comme un élève : Comprendre jusqu'au bout, puis Pratiquer en jouant les coups de la
 * leçon (ligne principale et chaque variante). Toute leçon qui bloque, se termine sans rien demander ou ne se maîtrise
 * pas est signalée.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcademyWalkthroughTest {
    private val main = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun everyLessonCanBeUnderstoodAndPractised() = runTest(main) {
        val problems = ArrayList<String>()
        val lessons = FakeLessons()
        val prefs = FakeSettings()
        val vm = AcademyViewModel(prefs, lessons, FakeNarrator(), FakeEngine(), FakePuzzles(listOf(PUZZLE_0000D)), SavedStateHandle(), Random(1), { 1_000_000L })
        advanceUntilIdle()
        for (l in Lessons.all) {
            vm.open(l.id, guided = true)
            advanceUntilIdle()
            vm.last()
            advanceUntilIdle()
            if (l.id !in vm.understood.value) problems += "${l.id} ${l.title} : Comprendre fini mais pas marqué compris"
            if (vm.practiceMode(l) != AcademyViewModel.PracticeMode.MOVES) continue
            for (v in 0..l.variants.size) {
                if (v == 0) vm.setChallenge(true) else vm.setVariant(v)
                advanceUntilIdle()
                var played = 0
                var guard = 0
                while (!vm.state.value.done && guard++ < 400) {
                    val s = vm.state.value
                    if (s.judging || s.refute != null) { advanceUntilIdle(); continue }
                    val c = s.cursor
                    if (c >= vm.line().size) break
                    val ok = vm.challengeMove(vm.line().moves[c])
                    if (ok) played++
                    advanceUntilIdle()
                    if (!ok && vm.state.value.cursor == c) { problems += "${l.id} v$v : coup ${vm.line().sans[c]} (demi-coup $c) refusé"; break }
                }
                val s = vm.state.value
                if (!s.done) problems += "${l.id} ${l.title} v$v : Pratiquer ne se termine pas (curseur ${s.cursor}/${vm.line().size})"
                else {
                    if (played == 0) problems += "${l.id} ${l.title} v$v : Pratiquer terminé sans aucun coup demandé"
                    if (l.kind != LessonKind.MODEL_GAME && s.stars != 3) problems += "${l.id} v$v : ${s.stars} étoiles sans erreur"
                    if (l.kind == LessonKind.MODEL_GAME && s.guessMax == 0) problems += "${l.id} v$v : partie modèle sans coup à deviner"
                    if (Regex("\\bnull\\b").containsMatchIn(s.message)) problems += "${l.id} v$v : « null » dans le message : ${s.message.lines().filter { "null" in it }}"
                    // Bilan concis : dernier commentaire, règle, score et maîtrise, sans l'historique des coups.
                    val paragraphs = s.message.split("\n\n").size
                    if (paragraphs > 5) problems += "${l.id} v$v : bilan de $paragraphs paragraphes"
                    if (!s.message.contains("À retenir")) problems += "${l.id} v$v : bilan sans « À retenir »"
                }
            }
            if (lessons.progress(l.id)?.mastered != true) problems += "${l.id} ${l.title} : pas maîtrisée après un Pratiquer parfait"
            vm.setChallenge(false)
            advanceUntilIdle()
        }
        println(problems.joinToString("\n"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Quitter une révision au moment où l'adversaire répond : rien ne doit être compté comme une leçon pratiquée. */
    @Test
    fun leavingAReviewMidReplyCountsNothing() = runTest(main) {
        val now = 10 * com.ivanherboch.chessduck.feature.SrsScheduler.DAY
        val lessons = FakeLessons()
        val start = com.ivanherboch.chessduck.data.LessonProgress(1, masteredAt = 1, stars = 1, srsStage = 1, nextReviewAt = now - 1)
        lessons.save(start)
        val prefs = FakeSettings()
        val vm = AcademyViewModel(prefs, lessons, FakeNarrator(), FakeEngine(), FakePuzzles(listOf(PUZZLE_0000D)), SavedStateHandle(), Random(1), { now })
        advanceUntilIdle()
        vm.startReviews(); advanceUntilIdle()
        assertTrue(vm.state.value.review != null)
        // Joue les coups jusqu'au dernier coup du joueur, puis quitte aussitôt (avant la fin de la séquence).
        val heroIdx = vm.line().moves.indices.filter { vm.line().positions[it].whiteToMove == Lessons.byId(1).heroWhite }
        while (vm.state.value.cursor < heroIdx.last()) { vm.challengeMove(vm.line().moves[vm.state.value.cursor]); advanceUntilIdle() }
        vm.challengeMove(vm.line().moves[vm.state.value.cursor])
        vm.back()
        advanceUntilIdle()
        val s = vm.state.value
        println("after back: route=${s.route} challenge=${s.challenge} done=${s.done} review=${s.review} progress=${lessons.progress(1)} daily=${prefs.settings.first().daily}")
        assertEquals(start, lessons.progress(1))
        assertTrue(prefs.settings.first().daily.doneOn(vm.srs.now() / com.ivanherboch.chessduck.feature.SrsScheduler.DAY).isEmpty())
        assertFalse(s.challenge)
    }

    /** Révisions finies depuis la séance du jour : retour à l'accueil, et le bandeau « Étape terminée » ne resurgit pas ensuite. */
    @Test
    fun dailyBannerDoesNotReappearAfterReturningHome() = runTest(main) {
        val now = 20_000 * com.ivanherboch.chessduck.feature.SrsScheduler.DAY + 12 * 3_600_000L
        val lessons = FakeLessons()
        lessons.save(com.ivanherboch.chessduck.data.LessonProgress(1, masteredAt = 1, stars = 3, srsStage = 1, nextReviewAt = 1))
        val vm = AcademyViewModel(FakeSettings(), lessons, FakeNarrator(), FakeEngine(), FakePuzzles(listOf(PUZZLE_0000D)), SavedStateHandle(), Random(1), { now })
        val sub = backgroundScope.launch { vm.daily.collect {} }
        advanceUntilIdle()
        vm.startDaily(); advanceUntilIdle()
        while (!vm.state.value.done) { vm.challengeMove(vm.line().moves[vm.state.value.cursor]); advanceUntilIdle() }
        // « Terminer les révisions » : étape suivante, puis fermeture de la séance (retour à l'accueil).
        vm.nextReview(); vm.closeReviews(); advanceUntilIdle()
        assertEquals(com.ivanherboch.chessduck.feature.AcademyRoute.HOME, vm.state.value.route)
        assertFalse(vm.state.value.dailyPrompt)
        vm.open(2, guided = true); advanceUntilIdle()
        assertFalse(vm.state.value.dailyPrompt)
        sub.cancel()
    }
}
