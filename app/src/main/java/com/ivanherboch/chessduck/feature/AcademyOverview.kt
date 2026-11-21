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
 * leçon conseillée, palier), bilan des points faibles et nombre de suggestions du tiroir « Entraînements ».
 * [currentLesson] : la leçon ouverte (elle n'est ni « à reprendre » ni « conseillée »).
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
            continueId = srs.continueLesson(Lessons.all, byId, current)?.id,
            examsPassed = tests.filter { it.kind == "exam" && it.passed }.map { it.level }.toSet(),
            endgamesDone = Lessons.all.filter { it.drill.isNotEmpty() && byId[it.id]?.mastered == true }.mapNotNull { EndgameFamily.of(it.drill) }.toSet(),
            levelMastered = Lessons.all.count { it.level == st.academyLevel && byId[it.id]?.mastered == true },
            levelTotal = Lessons.all.count { it.level == st.academyLevel },
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AcademyHome())

    /** Bilan : les 3 priorités du moment, d'après les parties analysées et la progression dans l'Académie. */
    val priorities: StateFlow<List<Priority>> = combine(games?.games() ?: flowOf(emptyList()), lessons.progress()) { g, p ->
        WeakTopics.compute(g, p)
    }.let { f -> if (work != null) f.flowOn(work) else f }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Suggestions « À faire maintenant » du tiroir (révisions du jour, leçon à reprendre, leçon conseillée autre que
     * celle ouverte) : leur nombre s'affiche sur le bouton « Entraînements » de l'en-tête.
     */
    val suggestions: StateFlow<Int> = combine(home, currentLesson, priorities) { h, current, prio ->
        val rec = h.recommended?.takeIf { it != current && it != h.continueId }
        // Les priorités du bilan (points faibles à travailler) comptent aussi, sauf la leçon déjà ouverte.
        (if (h.due.isNotEmpty()) 1 else 0) + (if (h.continueId != null) 1 else 0) + (if (rec != null) 1 else 0) +
            prio.count { it.lessonId != current }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)
}
