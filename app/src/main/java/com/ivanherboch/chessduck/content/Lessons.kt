package com.ivanherboch.chessduck.content

import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.chess.Line

/** Catégories de l'Académie, dans l'ordre du cursus ; le catalogue n'affiche que celles qui ont des leçons. */
enum class LessonCategory(val label: String) {
    BASICS("Les bases"), OPENING("Ouvertures"), MIDDLEGAME("Milieu de partie"), STRATEGY("Stratégie"), ENDGAME("Finales"),
    MODEL_GAMES("Parties modèles"),
}

/** Format d'une leçon : suite de coups commentés, ou partie modèle (mode « Devinez le coup » à la place du Défi). */
enum class LessonKind { LINE, MODEL_GAME }

/** Variante d'une leçon : elle part de la position après [from] demi-coups de la ligne principale. */
@Immutable
data class LessonVariant(val from: Int, val title: String, val uci: String, val texts: List<String>)

/** QCM de fin de leçon : la bonne réponse, une ou deux mauvaises, et l'explication. */
@Immutable
data class LessonQuiz(val question: String, val answer: String, val wrong: List<String>, val explanation: String)

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
    val drill: String = "",
    val ideas: List<String> = emptyList(),
    val traps: List<String> = emptyList(),
    val quiz: LessonQuiz? = null,
    val variants: List<LessonVariant> = emptyList(),
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
    /** Finale jouable contre l'Ordinateur associée (nom d'une EndgameFamily), vide sinon. */
    val drill: String = "",
    /** Les idées clés (ouvertures). */
    val ideas: List<String> = emptyList(),
    /** Pièges : pour soi, puis contre soi. */
    val traps: List<String> = emptyList(),
    val quiz: LessonQuiz? = null,
    /** Écarts fréquents de l'adversaire (ouvertures). */
    val variants: List<LessonVariant> = emptyList(),
) {
    /** Construite à la première ouverture : les coups sont rejoués directement, sans analyse de notation SAN. */
    val line: Line by lazy { Line.fromUci(fen, uci) }

    private val variantLines: List<Line> by lazy {
        variants.map { v -> Line.fromUci(fen, (line.uciMoves(v.from) + " " + v.uci).trim()) }
    }

    /** Ligne affichée : principale (0) ou variante [v] (1 = première variante). */
    fun lineFor(v: Int): Line = if (v == 0) line else variantLines[v - 1]

    /** Commentaires de la ligne [v] : ceux de la ligne principale jusqu'au départ de la variante, puis les siens. */
    fun textsFor(v: Int): List<String> = if (v == 0) texts else variants[v - 1].let { texts.take(it.from) + it.texts }
}

/**
 * Catalogue des leçons, classées par catégorie (ordre du cursus) puis par [Lesson.order].
 * Source : les fichiers .txt de scripts/lessons, compilés en structures Kotlin typées par scripts/gen_lessons.py
 * et LessonCompilerTool (voir LessonsOpenings.kt, LessonsMiddlegames.kt, LessonsEndgames.kt, LessonsModelGames.kt).
 */
object Lessons {
    val all: List<Lesson> by lazy {
        listOf(
            LessonCategory.OPENING to OPENING_LESSONS,
            LessonCategory.MIDDLEGAME to MIDDLEGAME_LESSONS,
            LessonCategory.ENDGAME to ENDGAME_LESSONS,
            LessonCategory.MODEL_GAMES to MODEL_GAME_LESSONS,
        ).flatMap { (cat, data) ->
            data.map { d ->
                Lesson(
                    d.id, cat, d.title, d.summary, d.heroWhite, d.fen, d.uci, d.texts,
                    d.level, d.order, d.theme, d.kind, d.pauseSteps, d.takeaway, d.conditions, d.stepVisuals,
                    d.drill, d.ideas, d.traps, d.quiz, d.variants,
                )
            }
        }.sortedWith(compareBy({ it.category.ordinal }, { it.order }))
    }

    private val index: Map<Int, Lesson> by lazy { all.associateBy { it.id } }

    fun byId(id: Int): Lesson = index[id] ?: all.first()

    /** Catégories qui ont au moins une leçon (les catégories encore vides restent masquées). */
    val categories: List<LessonCategory> by lazy { LessonCategory.entries.filter { c -> all.any { it.category == c } } }
}
