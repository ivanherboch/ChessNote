package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.engine.Stockfish
import com.ivanherboch.chessduck.feature.Analysis
import com.ivanherboch.chessduck.feature.SafetyNet
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Banc d'essai du filet de sécurité avec le vrai Stockfish (outil, inactif sans SAFETY_AUDIT=1) : mêmes durées d'analyse
 * que l'application (450 ms pour la position, 250 ms après le coup), puis contre-vérification longue.
 * A : premier coup des problèmes (une faute) → doit être arrêté ; B : solution des problèmes → jamais arrêtée ;
 * C : parties de Stockfish bridé (≈ débutant) → chaque arrêt doit être une vraie gaffe à l'analyse longue.
 * Rapport écrit dans SAFETY_REPORT (par défaut build/safety_report.txt).
 */
class SafetyNetEngineTool {
    private val sf = File("/usr/games/stockfish")

    private suspend fun analyse(e: Stockfish, pos: Position, ms: Int): Analysis? {
        if (pos.legalMoves.isEmpty()) return Analysis.decided(pos, if (pos.inCheck) com.ivanherboch.chessduck.chess.GameEnd.CHECKMATE else com.ivanherboch.chessduck.chess.GameEnd.STALEMATE)
        val r = e.search(pos.toFen(), ms, multiPv = 2)
        val first = r.lines.firstOrNull() ?: return null
        val sign = if (pos.whiteToMove) 1 else -1
        return Analysis(sign * first.score, r.lines.getOrNull(1)?.let { sign * it.score }, first.move.uci)
    }

    /** Perte réelle du coup (centipions, côté du joueur), à l'analyse longue. */
    private suspend fun deepDrop(e: Stockfish, pos: Position, m: Move): Int {
        val b = analyse(e, pos, 1500) ?: return 0
        val a = analyse(e, pos.play(m), 1500) ?: return 0
        val sign = if (pos.whiteToMove) 1 else -1
        return sign * (b.eval - a.eval)
    }

    /** Détail des gaffes manquées listées dans SAFETY_IDS : explication et point faible trouvés (ou non). */
    @Test
    fun explainMisses() = runBlocking {
        val ids = System.getenv("SAFETY_IDS")?.split(',')?.toSet() ?: return@runBlocking
        assumeTrue(sf.canExecute())
        val e = Stockfish(sf)
        val out = StringBuilder()
        File(System.getenv("PUZZLE_DUMP") ?: "/opt/work/puzzles_dump.txt").forEachLine { row ->
            val (id, fen, moves) = row.split('|')
            if (id !in ids) return@forEachLine
            runBlocking {
                val p0 = Line(fen).last
                val ms = moves.split(' ').map { Move.fromUci(it) }
                val b = analyse(e, p0, 450)!!; val a = analyse(e, p0.play(ms[0]), 250)!!
                val r = runCatching { com.ivanherboch.chessduck.feature.ReviewComments.netReason(p0, ms[0], b, a) }.getOrNull()
                val reply = a.best.takeIf { it.isNotEmpty() }?.let { p0.play(ms[0]).san(Move.fromUci(it)) }
                out.append("$id ${p0.toFen()} ${p0.san(ms[0])} réponse=$reply suite=${moves} topic=${r?.topic} : ${r?.text?.replace("\n", " ")}\n")
            }
        }
        e.close()
        File(System.getenv("SAFETY_REPORT") ?: "build/safety_misses.txt").writeText(out.toString())
    }

    @Test
    fun audit() = runBlocking {
        assumeTrue(System.getenv("SAFETY_AUDIT") == "1" && sf.canExecute())
        val dump = File(System.getenv("PUZZLE_DUMP") ?: "/opt/work/puzzles_dump.txt")
        assumeTrue(dump.exists())
        val n = (System.getenv("SAFETY_N") ?: "150").toInt()
        val out = StringBuilder()
        val e = Stockfish(sf)
        val rows = dump.readLines().let { all -> List(n) { all[(it.toLong() * all.size / n).toInt()] } }
        var aStop = 0; var aTrue = 0; var aTrueStop = 0
        var bStop = 0
        val misses = ArrayList<String>()
        for (row in rows) {
            val (id, fen, moves) = row.split('|')
            val line = Line(fen)
            val ms = moves.split(' ').map { Move.fromUci(it) }
            val p0 = line.last
            // A : la faute qui ouvre le problème.
            val b0 = analyse(e, p0, 450) ?: continue
            val a0 = analyse(e, p0.play(ms[0]), 250) ?: continue
            val d0 = SafetyNet.decide(p0, ms[0], b0, a0)
            val real = deepDrop(e, p0, ms[0])
            val sign = if (p0.whiteToMove) 1 else -1
            val gameOn = sign * b0.eval > -SafetyNet.LOST_CP
            if (real >= 250 && gameOn) aTrue++
            if (d0 != null) { aStop++; if (real >= 250) aTrueStop++ else out.append("A-FAUX $id ${p0.san(ms[0])} réel=$real : ${d0.text}\n") }
            else if (real >= 250 && gameOn) misses.add("A-MANQUÉ $id ${p0.san(ms[0])} réel=$real avant=${sign * b0.eval} après=${sign * a0.eval}")
            if (d0 != null && aStop <= 25) out.append("A-ARRÊT $id ${p0.san(ms[0])} réel=$real : ${d0.text.replace("\n", " ")}\n")
            // B : la solution (meilleur coup) ne doit jamais être arrêtée.
            val p1 = p0.play(ms[0])
            val b1 = analyse(e, p1, 450) ?: continue
            val a1 = analyse(e, p1.play(ms[1]), 250) ?: continue
            val d1 = SafetyNet.decide(p1, ms[1], b1, a1)
            if (d1 != null) { bStop++; out.append("B-FAUX $id ${p1.san(ms[1])} réel=${deepDrop(e, p1, ms[1])} : ${d1.text.replace("\n", " ")}\n") }
        }
        out.append("\nA : $aStop arrêts sur ${rows.size} fautes, dont $aTrueStop vraies gaffes ; gaffes réelles (≥ 2,5 pions, partie non perdue) : $aTrue, arrêtées : ${aTrueStop}\n")
        out.append("B : $bStop arrêts sur ${rows.size} bons coups (doit être 0)\n")
        misses.take(40).forEach { out.append(it).append('\n') }
        // C : parties réalistes, Stockfish bridé contre lui-même.
        var cMoves = 0; var cStops = 0; var cFalse = 0
        for (g in 0 until (System.getenv("SAFETY_GAMES") ?: "6").toInt()) {
            val gl = Line(com.ivanherboch.chessduck.chess.START_FEN)
            // Ouverture variée : premiers coups tirés parmi les plausibles.
            val rnd = kotlin.random.Random(g)
            while (gl.size < 60 && gl.last.legalMoves.isNotEmpty()) {
                val pos = gl.last
                val res = e.search(pos.toFen(), 60, multiPv = 4, elo = 1350)
                val m = (if (gl.size < 8) res.lines.map { it.move }.randomOrNull(rnd) else res.best) ?: pos.legalMoves.random(rnd)
                val b = analyse(e, pos, 450); val a = analyse(e, pos.play(m), 250)
                if (b != null && a != null) {
                    cMoves++
                    SafetyNet.decide(pos, m, b, a)?.let { d ->
                        cStops++
                        val real = deepDrop(e, pos, m)
                        if (real < 150) cFalse++
                        out.append("C${if (real < 150) "-FAUX" else ""} partie $g coup ${gl.size} ${pos.san(m)} réel=$real : ${d.text.replace("\n", " ")}\n")
                    }
                }
                gl.push(m)
            }
        }
        out.append("C : $cStops arrêts sur $cMoves coups, dont $cFalse faux (perte réelle < 1,5 pion)\n")
        e.close()
        val f = File(System.getenv("SAFETY_REPORT") ?: "build/safety_report.txt")
        f.parentFile?.mkdirs(); f.writeText(out.toString())
        println(out)
    }
}
