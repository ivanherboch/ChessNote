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
        val t = ReflexDrills.threatFrom(fork)
        assertNotNull(t)
        assertEquals(sqFromName("a1"), t!!.target)
        assertEquals(sqFromName("c2"), t.attacker)
        assertTrue(t.position.whiteToMove)
        assertEquals("Le cavalier c2 menace de prendre votre tour en a1.", t.explanation)
        // Le 4e coup reprend sur la case où l'élève vient de jouer : ce n'est pas une menace, le problème est écarté.
        assertNull(ReflexDrills.threatFrom(PUZZLE_0000D))
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
