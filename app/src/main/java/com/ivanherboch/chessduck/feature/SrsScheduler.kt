package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.data.LessonProgress

/**
 * Répétition espacée de l'Académie. Une leçon maîtrisée revient en révision après 1, 7, 30 puis 90 jours si on la
 * réussit sans hésiter ; une hésitation garde l'intervalle ; une révision ratée la fait revenir le lendemain. [clock] est injectable pour les tests.
 */
class SrsScheduler(private val clock: () -> Long = System::currentTimeMillis) {
    fun now(): Long = clock()

    /** Première maîtrise : première révision le lendemain (sans changer un calendrier déjà en cours). */
    fun mastered(p: LessonProgress): LessonProgress =
        if (p.srsStage > 0) p else p.copy(srsStage = 1, nextReviewAt = now() + DAY, lastReviewedAt = now())

    /**
     * Révision faite. Réussie sans hésiter ([fluent] : sans erreur ni indice), l'intervalle s'allonge ; réussie avec
     * une hésitation, il reste le même ; ratée, on repart à un jour.
     */
    fun reviewed(p: LessonProgress, success: Boolean, fluent: Boolean = true): LessonProgress {
        val stage = when {
            !success -> 1
            fluent -> minOf(p.srsStage + 1, INTERVAL_DAYS.size)
            else -> p.srsStage.coerceIn(1, INTERVAL_DAYS.size)
        }
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
     * paliers inférieurs (les bases du palier 1 seulement au palier 1), sinon des paliers suivants.
     */
    fun recommended(lessons: List<Lesson>, progress: Map<Int, LessonProgress>, level: Int): Lesson? {
        val open = lessons.filter { progress[it.id]?.mastered != true }
        // Les bases (palier 1) ne sont conseillées qu'au palier 1 : au-delà, on rattrape les paliers 2 et plus.
        return open.firstOrNull { it.level == level } ?: open.firstOrNull { it.level < level && (it.level > 1 || level == 1) }
            ?: open.filter { it.level > level }.minByOrNull { it.level } ?: open.minByOrNull { it.level }
    }

    /**
     * Leçon commencée mais pas encore maîtrisée, autre que [current] : Comprendre terminé ([understood]) ou Pratiquer
     * déjà tenté (voir [started]).
     */
    fun continueLesson(lessons: List<Lesson>, progress: Map<Int, LessonProgress>, current: Int, understood: Set<Int> = emptySet()): Lesson? =
        lessons.firstOrNull { l -> l.id != current && started(progress[l.id], l.id in understood) }

    companion object {
        /**
         * Leçon « En cours » : pas encore maîtrisée, et Comprendre terminé ou Pratiquer tenté (étoiles, exercices ou
         * erreurs). Même règle partout : en-tête de leçon, catalogue et « Continuer » de l'accueil et de la séance du jour.
         */
        fun started(p: LessonProgress?, understood: Boolean): Boolean =
            p?.mastered != true && (understood || (p != null && (p.stars > 0 || p.practiceTotal > 0 || p.errors > 0)))

        const val DAY = 86_400_000L
        val INTERVAL_DAYS = listOf(1, 7, 30, 90)
    }
}
