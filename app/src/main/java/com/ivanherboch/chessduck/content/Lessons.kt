package com.ivanherboch.chessduck.content

import com.ivanherboch.chessduck.chess.Line

enum class LessonCategory(val label: String) {
    OPENING("Ouvertures"), MIDDLEGAME("Milieu de partie"), ENDGAME("Finales")
}

/** Données d'une leçon telles que générées (coups en UCI, déjà validés par le moteur de l'application). */
internal class LessonData(
    val title: String,
    val summary: String,
    val heroWhite: Boolean,
    val fen: String,
    val uci: String,
    val texts: List<String>,
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
) {
    /** Construite à la première ouverture : les coups sont rejoués directement, sans analyse de notation SAN. */
    val line: Line by lazy { Line.fromUci(fen, uci) }
}

/**
 * Catalogue des 113 leçons (37 ouvertures, 38 milieux de partie, 38 finales).
 * Source : les fichiers .txt de scripts/lessons, compilés en structures Kotlin typées par scripts/gen_lessons.py
 * et LessonCompilerTool (voir LessonsOpenings.kt, LessonsMiddlegames.kt, LessonsEndgames.kt).
 */
object Lessons {
    val all: List<Lesson> by lazy {
        listOf(
            LessonCategory.OPENING to OPENING_LESSONS,
            LessonCategory.MIDDLEGAME to MIDDLEGAME_LESSONS,
            LessonCategory.ENDGAME to ENDGAME_LESSONS,
        ).flatMap { (cat, data) -> data.map { cat to it } }
            .mapIndexed { i, (cat, d) -> Lesson(i + 1, cat, d.title, d.summary, d.heroWhite, d.fen, d.uci, d.texts) }
    }

    fun byId(id: Int): Lesson = all.firstOrNull { it.id == id } ?: all.first()
}
