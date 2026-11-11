package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.sqName
import org.junit.Test
import java.io.File

/**
 * Outil d'auteur (inactif sans LESSON_TOOL_IN) : convertit des brouillons de leçons en SAN
 * et contrôle chaque coup du joueur avec un Stockfish hôte (LESSON_TOOL_SF).
 * Entrées : label|w ou b|FEN|coups (UCI, SAN, ou « AUTO n »).
 */
class LessonToolTest {
    private class Sf(path: String) {
        val p: Process = ProcessBuilder(path).redirectErrorStream(true).start()
        val w = p.outputStream.bufferedWriter()
        val r = p.inputStream.bufferedReader()
        fun send(s: String) { w.write(s + "\n"); w.flush() }
        /** Renvoie (meilleur coup, score du camp au trait en cp ; mat = ±100000). */
        fun go(fen: String, depth: Int): Pair<String, Int> {
            send("position fen $fen"); send("go depth $depth")
            var score = 0
            while (true) {
                val l = r.readLine() ?: break
                if (l.startsWith("info") && " score " in l && "bound" !in l) {
                    val t = l.split(' ')
                    val i = t.indexOf("score")
                    val v = t[i + 2].toInt()
                    score = if (t[i + 1] == "mate") (if (v > 0) 100000 - v else -100000 - v) else v
                }
                if (l.startsWith("bestmove")) return l.split(' ')[1] to score
            }
            return "" to 0
        }
    }

    @Test
    fun run() {
        val input = File(System.getenv("LESSON_TOOL_IN") ?: return)
        val sf = System.getenv("LESSON_TOOL_SF")?.let { Sf(it) }
        val depth = (System.getenv("LESSON_TOOL_DEPTH") ?: "16").toInt()
        val out = StringBuilder()
        for (row in input.readLines()) {
            if (row.isBlank() || row.startsWith("//")) continue
            val f = row.split('|')
            val (label, hero, fen) = f
            val spec = f[3].trim()
            val line = Line(fen)
            try {
                if (spec.startsWith("AUTO")) {
                    val parts = spec.split(' ')
                    val n = parts[1].toInt()
                    for (s in parts.drop(2)) line.push(line.last.parseSan(s) ?: error("illégal $s"))
                    while (line.size < n && line.last.legalMoves.isNotEmpty()) line.push(Move.fromUci(sf!!.go(line.last.toFen(), depth + 4).first))
                } else for (tok in spec.split(' ').filter { it.isNotBlank() }) {
                    val p = line.last
                    val m = if (tok.matches(Regex("[a-h][1-8][a-h][1-8][qrbn]?")) && p.isLegal(Move.fromUci(tok))) Move.fromUci(tok) else p.parseSan(tok)
                    line.push(m ?: error("coup illégal « $tok » après ${line.sans.joinToString(" ")}"))
                }
            } catch (e: Exception) {
                out.append("### $label\n  ERREUR : ${e.message}\n"); continue
            }
            out.append("### $label | $hero | $fen\n")
            for (i in 0 until line.size) {
                val p = line.positions[i]
                val m = line.moves[i]
                val isHero = p.whiteToMove == (hero == "w")
                val after = line.positions[i + 1]
                val hits = (0 until 64).filter { q -> after[q] != 0 && (after[q] > 8) != (after[m.to] > 8) && m.to in after.attackers(q, !after.whiteToMove) }
                    .joinToString(",") { PIECE_NAMES_FR[pieceType(after[it])][0] + sqName(it) }
                var note = ""
                if (sf != null && isHero) {
                    val (best, sBest) = sf.go(p.toFen(), depth)
                    val sAfter = if (after.legalMoves.isEmpty()) (if (after.inCheck) 100000 else 0) else -sf.go(after.toFen(), depth).second
                    val bad = sBest - sAfter > 70 && !(sAfter > 600) && best != m.uci
                    note = "  [sf best=${p.san(Move.fromUci(best))} $sBest ; joué $sAfter${if (bad) "  <<<<< DOUTEUX" else ""}]"
                }
                out.append("  ${if (isHero) "*" else " "} ${line.sans[i].padEnd(8)} ${PIECE_NAMES_FR[pieceType(p[m.from])]} ${sqName(m.from)}-${sqName(m.to)}" +
                    (if (p[m.to] != 0) " x${PIECE_NAMES_FR[pieceType(p[m.to])]}" else "") + (if (hits.isNotEmpty()) " attaque:$hits" else "") + note + "\n")
            }
            val e = line.end()
            out.append("  fin: ${line.last.toFen()} ${if (e.name != "NONE") e.name else ""}\n")
        }
        File(System.getenv("LESSON_TOOL_OUT") ?: "/tmp/lesson_tool.txt").writeText(out.toString())
    }
}
