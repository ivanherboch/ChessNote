package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.sqFromName
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.feature.AcademyAssessments
import com.ivanherboch.chessduck.feature.ReflexDrills
import com.ivanherboch.chessduck.feature.SrsScheduler
import com.ivanherboch.chessduck.vm.PUZZLE_0000D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Exercices de réflexes, répétition espacée et tests de l'Académie (fonctions pures). */
class ReflexDrillsTest {
    /** Fourchette de cavalier : après Ke1?? Nc2+, la tour a1 est menacée (Kd2 ne la sauve pas). */
    val fork = Puzzle("FORK1", "4k3/8/8/8/3n4/1p6/4K3/R7 w - - 0 1", "e2e1 d4c2 e1d2 c2a1", 900, 90, 100, " fork ", "fork")

    @Test
    fun threatIsThePieceTheSolutionWins() {
        // Fourchette avec échec : l'élève doit d'abord parer l'échec, la question n'est pas posée.
        assertNull(ReflexDrills.threatFrom(fork))
        // Fourchette sans échec : le cavalier c2 attaque la dame e1 et la tour a1 ; la cible principale est la dame.
        val quiet = Puzzle("FORK2", "6k1/8/8/8/3n4/8/8/R3Q1K1 w - - 0 1", "g1h1 d4c2", 900, 90, 100, " fork ", "fork")
        val t = ReflexDrills.threatFrom(quiet)
        assertNotNull(t)
        assertEquals(sqFromName("e1"), t!!.target)
        assertEquals(sqFromName("c2"), t.attacker)
        assertEquals(setOf(sqFromName("e1"), sqFromName("a1")), t.targets.toSet())
        assertTrue(t.position.whiteToMove)
        // #0000D : après Qd6 Rd8, la dame d6 est attaquée par la tour d8 ; protégée par la tour d1, elle vaut plus qu'elle.
        val q = ReflexDrills.threatFrom(PUZZLE_0000D)!!
        assertEquals(sqFromName("d6"), q.target)
        assertEquals(sqFromName("d8"), q.attacker)
        assertEquals("La tour d8 menace de prendre votre dame en d6, qui vaut plus que la tour.", q.explanation)
    }

    @Test
    fun threatIsReadOnTheShownPositionNotLaterInTheSolution() {
        // Position signalée : le fou g5 attaque la dame f6 ; la tour d8, derrière elle, n'est pas menacée tout de suite.
        val pos = Position.fromFen("3rr1k1/pp3ppp/3b1q2/2p1p1B1/8/8/PPPQ1PPP/3R1RK1 b - - 0 20")
        assertEquals(listOf(sqFromName("f6") to sqFromName("g5")), ReflexDrills.threatsIn(pos))
        // Pièce protégée par une pièce qui ne vaut pas plus qu'elle : pas de menace (le fou d6 attaqué par la dame).
        assertTrue(ReflexDrills.threatsIn(Position.fromFen("3r2k1/8/3b4/8/8/8/3Q4/6K1 b - - 0 1")).isEmpty())
        // Fourchette : deux pièces menacées, l'une ou l'autre est une bonne réponse.
        val fork = ReflexDrills.threatsIn(Position.fromFen("4k3/8/8/1q3r2/3N4/8/8/4K3 b - - 0 1"))
        assertEquals(setOf(sqFromName("b5"), sqFromName("f5")), fork.map { it.first }.toSet())
        assertTrue(fork.all { it.second == sqFromName("d4") })
    }

    @Test
    fun capturesAreJudgedByTheMaterialBalance() {
        val free = ReflexDrills.captureFrom(Position.fromFen("4k3/8/8/3n4/8/8/8/3QK3 w - - 0 1"), Random(0))!!
        assertTrue(free.good)
        assertEquals("Oui : le cavalier en d5 n'est pas protégé. Vous gagnez 3 points.", free.explanation)
        val defended = ReflexDrills.captureFrom(Position.fromFen("4k3/8/3p4/4p3/8/8/4Q3/4K3 w - - 0 1"), Random(0))!!
        assertFalse(defended.good)
        assertTrue(defended.explanation, defended.explanation.startsWith("Non : le pion en e5 est protégé. Vous donneriez votre dame pour un pion : vous perdriez 8 points."))
        // Une prise équilibrée n'est pas une bonne question ; en échec, on ne pose pas la question.
        assertNull(ReflexDrills.captureFrom(Position.fromFen("4k3/8/3p4/4n3/8/5N2/8/4K3 w - - 0 1"), Random(0)))
        assertNull(ReflexDrills.captureFrom(Position.fromFen("4k3/8/8/3n4/8/8/8/3QK2r w - - 0 1"), Random(0)))
        // La réponse de l'Ordinateur explique la suite : ici, la reprise du pion.
        val after = defended.position.play(defended.capture)
        val reply = ReflexDrills.replyText(after, defended.capture, com.ivanherboch.chessduck.chess.Move.fromUci("d6e5"), false)
        assertEquals("Après la prise, l'adversaire reprend par dxe5 : la prise ne tient pas.", reply)
    }

    @Test
    fun spacedRepetitionFollowsOneSevenThirtyNinetyDays() {
        var now = 1_000_000L
        val srs = SrsScheduler { now }
        val day = SrsScheduler.DAY
        var p = srs.mastered(LessonProgress(5, masteredAt = now, stars = 3))
        assertEquals(1, p.srsStage)
        assertEquals(now + day, p.nextReviewAt)
        assertFalse(srs.isDue(p))
        now += day
        assertTrue(srs.isDue(p))
        assertEquals(listOf(5), srs.due(listOf(p, LessonProgress(6, 0))))
        for ((stage, days) in listOf(2 to 7, 3 to 30, 4 to 90, 4 to 90)) {
            p = srs.reviewed(p, success = true)
            assertEquals(stage, p.srsStage)
            assertEquals(now + days * day, p.nextReviewAt)
            assertEquals(days, srs.daysUntil(p))
        }
        p = srs.reviewed(p, success = false)
        assertEquals(1, p.srsStage)
        assertEquals(now + day, p.nextReviewAt)
        // Une leçon non maîtrisée n'est jamais planifiée.
        assertNull(srs.daysUntil(LessonProgress(7, 0)))
    }

    @Test
    fun recommendedAndContinueFollowTheCurriculum() {
        val srs = SrsScheduler { 0L }
        val all = Lessons.all
        val first2 = all.first { it.level == 2 }
        assertEquals(first2.id, srs.recommended(all, emptyMap(), 2)?.id)
        val first3 = all.first { it.level == 3 }
        assertEquals(first3.id, srs.recommended(all, emptyMap(), 3)?.id)
        val done = all.filter { it.level == 2 }.associate { it.id to LessonProgress(it.id, masteredAt = 1, stars = 3) }
        assertEquals(all.first { it.level == 3 }.id, srs.recommended(all, done, 2)?.id)
        val started = mapOf(first3.id to LessonProgress(first3.id, 0, stars = 1))
        assertEquals(first3.id, srs.continueLesson(all, started, current = first2.id)?.id)
        assertNull(srs.continueLesson(all, started, current = first3.id))
    }

    @Test
    fun placementAndExamRules() {
        val t = true
        val f = false
        assertEquals(3, AcademyAssessments.placementLevel(listOf(t, t, f, t, f, t, f, f, f, t, t, t)))
        assertEquals(5, AcademyAssessments.placementLevel(List(12) { t }))
        assertEquals(1, AcademyAssessments.placementLevel(List(12) { f }))
        assertEquals(12, AcademyAssessments.placementSpecs().size)
        val exam = AcademyAssessments.examSpecs(3, Random(1))
        assertEquals(10, exam.size)
        assertEquals(2, exam.count { it.themes == listOf("defensiveMove") })
        assertEquals(2, exam.count { it.themes == AcademyAssessments.ENDGAMES })
        assertTrue(exam.all { it.range == AcademyAssessments.levelRange(3) })
        assertTrue(AcademyAssessments.examPassed(8))
        assertFalse(AcademyAssessments.examPassed(7))
    }
}
