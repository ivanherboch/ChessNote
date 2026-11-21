package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.feature.ReflexDrills
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Outil (pas un test) : génère les exercices de réflexes sur un échantillon de problèmes, avec le code de l'application,
 * pour les faire vérifier par Stockfish. REFLEX_AUDIT=entrée.tsv (type|id|fen|coups|thèmes) ; sortie : entrée.out.
 */
class ReflexAuditTool {
    @Test
    fun audit() {
        val input = System.getenv("REFLEX_AUDIT") ?: return
        val out = StringBuilder()
        for (l in File(input).readLines()) {
            val (kind, id, fen, moves, themes) = l.split('|')
            val p = Puzzle(id, fen, moves, 1500, 90, 100, " $themes ", "")
            when (kind) {
                "T" -> ReflexDrills.threatFrom(p)?.let { t ->
                    out.append("T|$id|${t.position.toFen()}|${t.threats.joinToString(",") { "${it.second}-${it.first}" }}|${t.explanation}\n")
                }
                "B" -> ReflexDrills.blindFrom(p)?.let { b -> out.append("B|$id|${b.after.toFen()}|${b.expected.uci}|${b.answer}\n") }
                "C" -> runCatching { Line.fromUci(fen, moves).positions[1] }.getOrNull()?.let { pos ->
                    ReflexDrills.captureFrom(pos, Random(id.hashCode()))?.let { c -> out.append("C|$id|${pos.toFen()}|${c.capture.uci}|${c.good}|${c.explanation}\n") }
                }
            }
        }
        File("$input.out").writeText(out.toString())
    }
}
