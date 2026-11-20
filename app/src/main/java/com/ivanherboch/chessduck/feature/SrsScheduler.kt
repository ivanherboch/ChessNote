package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.data.LessonProgress

/**
 * Répétition espacée de l'Académie. Une leçon maîtrisée revient en révision après 1, 7, 30 puis 90 jours ;
 * une révision ratée la fait revenir le lendemain. [clock] est injectable pour les tests.
 */
class SrsScheduler(private val clock: () -> Long = System::currentTimeMillis) {
    fun now(): Long = clock()

    /** Première maîtrise : première révision le lendemain (sans changer un calendrier déjà en cours). */
    fun mastered(p: LessonProgress): LessonProgress =
        if (p.srsStage > 0) p else p.copy(srsStage = 1, nextReviewAt = now() + DAY, lastReviewedAt = now())

    /** Révision faite : réussie, l'intervalle s'allonge ; ratée, on repart à un jour. */
    fun reviewed(p: LessonProgress, success: Boolean): LessonProgress {
        val stage = if (success) minOf(p.srsStage + 1, INTERVAL_DAYS.size) else 1
        return p.copy(srsStage = stage, nextReviewAt = now() + INTERVAL_DAYS[stage - 1] * DAY, lastReviewedAt = now())
    }

    fun isDue(p: LessonProgress): Boolean = p.mastered && p.srsStage > 0 && p.nextReviewAt in 1..now()

    /** Leçons à réviser aujourd'hui, les plus anciennes d'abord. */
    fun due(progress: Collection<LessonProgress>): List<Int> = progress.filter(::isDue).sortedBy { it.nextReviewAt }.map { it.lessonId }

    /** Jours avant la prochaine révision (0 : aujourd'hui), null si la leçon n'est pas planifiée. */
    fun daysUntil(p: LessonProgress?): Int? {
        if (p == null || !p.mastered || p.srsStage == 0) return null
        return ((p.nextReviewAt - now()).coerceAtLeast(0) + DAY - 1).div(DAY).toInt()
    }

    /**
     * Prochaine leçon conseillée : la première leçon non maîtrisée du palier [level] (ordre du cursus), sinon des
     * paliers inférieurs, sinon des paliers suivants.
     */
    fun recommended(lessons: List<Lesson>, progress: Map<Int, LessonProgress>, level: Int): Lesson? {
        val open = lessons.filter { progress[it.id]?.mastered != true }
        return open.firstOrNull { it.level == level } ?: open.firstOrNull { it.level < level } ?: open.minByOrNull { it.level }
    }

    /** Leçon commencée (Défi ou exercices déjà tentés) mais pas encore maîtrisée, autre que [current]. */
    fun continueLesson(lessons: List<Lesson>, progress: Map<Int, LessonProgress>, current: Int): Lesson? =
        lessons.firstOrNull { l -> l.id != current && progress[l.id]?.let { !it.mastered && (it.stars > 0 || it.practiceTotal > 0) } == true }

    companion object {
        const val DAY = 86_400_000L
        val INTERVAL_DAYS = listOf(1, 7, 30, 90)
    }
}
