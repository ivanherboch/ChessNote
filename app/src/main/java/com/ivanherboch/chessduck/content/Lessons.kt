package com.ivanherboch.chessduck.content

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.START_FEN

enum class LessonCategory(val label: String, val emoji: String) {
    OPENING("Ouvertures", "🚀"), MIDDLEGAME("Milieu de partie", "⚔️"), ENDGAME("Finales", "🏁")
}

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
    val sans: List<String>,
    val texts: List<String>,
) {
    val line: Line by lazy { Line.fromMoves(fen, sans) }
}

/**
 * Catalogue des 113 leçons (37 ouvertures, 38 milieux de partie, 38 finales).
 *
 * Format source : un bloc par leçon.
 *   # Titre | Résumé | w ou b (camp du joueur en mode Défi)
 *   FEN: ... (facultatif, position initiale sinon)
 *   coup SAN : explication
 */
object Lessons {
    val all: List<Lesson> by lazy {
        val out = ArrayList<Lesson>()
        parse(OPENING_LESSONS, LessonCategory.OPENING, out)
        parse(MIDDLEGAME_LESSONS, LessonCategory.MIDDLEGAME, out)
        parse(ENDGAME_LESSONS, LessonCategory.ENDGAME, out)
        out
    }

    fun byId(id: Int): Lesson = all.firstOrNull { it.id == id } ?: all.first()

    private fun parse(src: String, cat: LessonCategory, out: MutableList<Lesson>) {
        var header: List<String>? = null
        var fen = START_FEN
        val sans = ArrayList<String>()
        val texts = ArrayList<String>()
        fun flush() {
            val h = header ?: return
            out.add(Lesson(out.size + 1, cat, h[0], h[1], h[2] == "w", fen, sans.toList(), texts.toList()))
            sans.clear(); texts.clear(); fen = START_FEN
        }
        for (raw in src.lines()) {
            val l = raw.trim()
            when {
                l.isEmpty() -> {}
                l.startsWith("#") -> { flush(); header = l.removePrefix("#").split("|").map { it.trim() } }
                l.startsWith("FEN:") -> fen = l.removePrefix("FEN:").trim()
                else -> {
                    val i = l.indexOf(" : ")
                    sans.add(l.substring(0, i).trim())
                    texts.add(l.substring(i + 3).trim())
                }
            }
        }
        flush()
    }
}
