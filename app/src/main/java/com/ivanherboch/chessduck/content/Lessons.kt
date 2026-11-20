package com.ivanherboch.chessduck.content

import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.chess.Line

/** Catégories de l'Académie, dans l'ordre du cursus ; le catalogue n'affiche que celles qui ont des leçons. */
enum class LessonCategory(val label: String) {
    BASICS("Les bases"), OPENING("Ouvertures"), MIDDLEGAME("Milieu de partie"), STRATEGY("Stratégie"), ENDGAME("Finales")
}

/** Format d'une leçon : aujourd'hui, une suite de coups commentés (d'autres formats viendront s'y ajouter). */
enum class LessonKind { LINE }

/** Repères du mode Guidé pour une position : cases surlignées et flèches (cases 0–63). */
@Immutable
data class StepVisuals(val marks: List<Int> = emptyList(), val arrows: List<Pair<Int, Int>> = emptyList())

/** Données d'une leçon telles que générées (coups en UCI, déjà validés par le moteur de l'application). */
internal class LessonData(
    /** Identifiant fixe, défini dans les fichiers source : la progression enregistrée en dépend. */
    val id: Int,
    val title: String,
    val summary: String,
    val heroWhite: Boolean,
    val fen: String,
    val uci: String,
    val texts: List<String>,
    val level: Int = 2,
    val order: Int = id,
    val theme: String = "",
    val kind: LessonKind = LessonKind.LINE,
    val pauseSteps: Set<Int> = emptySet(),
    val takeaway: String = "",
    val conditions: String = "",
    val stepVisuals: Map<Int, StepVisuals> = emptyMap(),
)

/**
 * Une leçon interactive : une suite de coups commentés.
 * En mode Défi, le joueur joue les coups du camp [heroWhite] ; l'adversaire répond seul.
 */
class Lesson(
    val id: Int,
    val category: LessonCategory,
    val title: String,
    val summary: String,
    val heroWhite: Boolean,
    val fen: String,
    /** Coups de la leçon en UCI (« e2e4 e7e5 … »). */
    val uci: String,
    val texts: List<String>,
    /** Palier du cursus (1 Les bases … 5 Confirmé). */
    val level: Int = 2,
    /** Rang dans sa catégorie (ordre du catalogue). */
    val order: Int = id,
    /** Thème Lichess associé (clé de PuzzleThemes), vide si aucun : sert aux exercices et à « S'entraîner sur ce thème ». */
    val theme: String = "",
    val kind: LessonKind = LessonKind.LINE,
    /** Positions (nombre de demi-coups joués) où le mode Guidé s'arrête : « À vous : que jouez-vous ici ? ». */
    val pauseSteps: Set<Int> = emptySet(),
    /** La règle à retenir, en une phrase. */
    val takeaway: String = "",
    /** Comment reconnaître la position théorique (finales), vide sinon. */
    val conditions: String = "",
    /** Repères du mode Guidé par position (nombre de demi-coups joués). */
    val stepVisuals: Map<Int, StepVisuals> = emptyMap(),
) {
    /** Construite à la première ouverture : les coups sont rejoués directement, sans analyse de notation SAN. */
    val line: Line by lazy { Line.fromUci(fen, uci) }
}

/**
 * Catalogue des leçons, classées par catégorie (ordre du cursus) puis par [Lesson.order].
 * Source : les fichiers .txt de scripts/lessons, compilés en structures Kotlin typées par scripts/gen_lessons.py
 * et LessonCompilerTool (voir LessonsOpenings.kt, LessonsMiddlegames.kt, LessonsEndgames.kt).
 */
object Lessons {
    val all: List<Lesson> by lazy {
        listOf(
            LessonCategory.OPENING to OPENING_LESSONS,
            LessonCategory.MIDDLEGAME to MIDDLEGAME_LESSONS,
            LessonCategory.ENDGAME to ENDGAME_LESSONS,
        ).flatMap { (cat, data) ->
            data.map { d ->
                Lesson(
                    d.id, cat, d.title, d.summary, d.heroWhite, d.fen, d.uci, d.texts,
                    d.level, d.order, d.theme, d.kind, d.pauseSteps, d.takeaway, d.conditions, d.stepVisuals,
                )
            }
        }.sortedWith(compareBy({ it.category.ordinal }, { it.order }))
    }

    private val index: Map<Int, Lesson> by lazy { all.associateBy { it.id } }

    fun byId(id: Int): Lesson = index[id] ?: all.first()

    /** Catégories qui ont au moins une leçon (les catégories encore vides restent masquées). */
    val categories: List<LessonCategory> by lazy { LessonCategory.entries.filter { c -> all.any { it.category == c } } }
}
