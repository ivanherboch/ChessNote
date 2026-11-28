package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.data.GameRepository
import com.ivanherboch.chessduck.data.LessonProgressRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Vue d'ensemble de l'Académie, sans état propre : progression par leçon, accueil (révisions dues, leçon à reprendre,
 * leçon conseillée, palier) et bilan des points faibles. [currentLesson] : la dernière leçon ouverte, proposée en premier
 * à « Continuer » si elle est commencée sans être maîtrisée.
 */
internal class AcademyOverview(
    scope: CoroutineScope,
    prefs: SettingsRepository,
    lessons: LessonProgressRepository,
    games: GameRepository?,
    srs: SrsScheduler,
    currentLesson: Flow<Int>,
    /** Fil de calcul du bilan (relecture des parties) ; null : celui de [scope] (tests). */
    work: CoroutineDispatcher?,
) {
    val mastered = lessons.masteredLessons().map { it.toSet() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())
    /** Progression détaillée (étoiles, exercices, révisions) par leçon. */
    val progress = lessons.progress().map { l -> l.associateBy { it.lessonId } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    /** Accueil : leçon à reprendre, révisions dues, leçon conseillée selon le palier. */
    val home: StateFlow<AcademyHome> = combine(
        lessons.progress(), prefs.settings, currentLesson, lessons.assessments(),
    ) { list, st, current, tests ->
        val byId = list.associateBy { it.lessonId }
        AcademyHome(
            level = st.academyLevel, placementDone = st.placementDone, due = srs.due(list),
            recommended = srs.recommended(Lessons.all, byId, st.academyLevel)?.id,
            // Leçon commencée (Comprendre terminé compris) : la dernière ouverte d'abord, sinon la première du cursus.
            continueId = current.takeIf { id -> SrsScheduler.started(byId[id], id in st.understoodIds) }
                ?: srs.continueLesson(Lessons.all, byId, current, st.understoodIds)?.id,
            examsPassed = tests.filter { it.kind == "exam" && it.passed }.map { it.level }.toSet(),
            endgamesDone = Lessons.all.filter { it.drill.isNotEmpty() && byId[it.id]?.mastered == true }.mapNotNull { EndgameFamily.of(it.drill) }.toSet(),
            levelMastered = Lessons.all.count { it.level == st.academyLevel && byId[it.id]?.mastered == true },
            levelTotal = Lessons.all.count { it.level == st.academyLevel },
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AcademyHome())

    /** Bilan : les 3 priorités du moment, d'après les parties analysées et la progression dans l'Académie. */
    val priorities: StateFlow<List<Priority>> = combine(
        games?.games() ?: flowOf(emptyList()), lessons.progress(), prefs.settings.map { it.lessonWorkedAt to it.netEvents }.distinctUntilChanged(),
    ) { g, p, (worked, events) ->
        WeakTopics.compute(g, p, worked, srs.now(), events)
    }.let { f -> if (work != null) f.flowOn(work) else f }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
