package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.GameEnd
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.engine.Stockfish
import com.ivanherboch.chessduck.feature.GameAnalysisService
import com.ivanherboch.chessduck.feature.ReviewComments
import com.ivanherboch.chessduck.ui.evalShort
import com.ivanherboch.chessduck.vm.FakeGames
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Outil (inactif sans REVIEW_SF = chemin d'un Stockfish de la machine) : simule des parties, les analyse
 * exactement comme l'application (GameAnalysisService, 110 ms par position) et vérifie que la jauge et les
 * commentaires de la revue disent la même chose. REVIEW_GAMES : nombre de parties (30 par défaut).
 * Le rapport détaillé est écrit dans REVIEW_OUT (sinon build/review-consistency.txt).
 */
class ReviewConsistencyTool {
    /** Distance de mat lue sur la jauge pour le camp [white] (null : pas de mat, ou position non analysée). */
    private fun mateFor(evalWhite: Int?, white: Boolean): Int? = evalWhite?.let { com.ivanherboch.chessduck.engine.mateFor(it, white) }

    @Test
    fun run() = runBlocking {
        val sf = System.getenv("REVIEW_SF") ?: return@runBlocking
        val games = System.getenv("REVIEW_GAMES")?.toIntOrNull() ?: 30
        val engine = Stockfish(File(sf))
        val analyzer = GameAnalysisService(engine, FakeGames(), this)
        val problems = ArrayList<String>()
        var plies = 0
        var mates = 0
        var noise = 0
        for (k in 0 until games) {
            val rnd = Random(k)
            val line = Line(START_FEN)
            // Parties volontairement imparfaites : ordinateur bridé et 20 % de coups au hasard, pour faire
            // apparaître gaffes, mats manqués et mats concédés.
            while (line.end() == GameEnd.NONE && line.size < 200) {
                val pos = line.last
                val engineMove = if (rnd.nextDouble() < 0.2) null else engine.search(pos.toFen(), 30, elo = 1350).best
                line.push(engineMove?.takeIf { pos.isLegal(it) } ?: pos.legalMoves.random(rnd))
            }
            val analysis = (0..line.size).map { analyzer.analyse(line.positions[it], 110) }
            val g = GameRecord(
                date = 0, opponent = "Ordinateur", vsComputer = true, playerWhite = true, result = "*", outcome = 0,
                reason = "", eloBefore = 0, eloAfter = 0, moves = line.uciMoves(), whiteName = "Joueur", blackName = "Ordinateur",
            )
            if (line.end() == GameEnd.CHECKMATE) {
                mates++
                val bar = evalShort(analysis.last()?.eval ?: 0)
                if (bar != "#") problems.add("partie $k : mat final affiché « $bar » au lieu de « # »")
            }
            for (c in 1..line.size) {
                plies++
                val i = c - 1
                val p = line.positions[i]
                val san = line.sans[i]
                val white = p.whiteToMove
                val (text, _) = ReviewComments.comment(g, line, i, analysis)
                val before = mateFor(analysis[i]?.eval, white)
                val after = mateFor(analysis[c]?.eval, white)
                val against = mateFor(analysis[c]?.eval, !white)
                val where = "partie $k, coup $c ($san), jauge avant ${analysis[i]?.eval?.let(::evalShort)}, après ${analysis[c]?.eval?.let(::evalShort)}"
                // 1. La jauge montrait un mat en 1 pour le joueur, qui ne l'a pas joué : le commentaire doit le dire.
                if (before == 1 && !san.endsWith("#") && !text.startsWith("⚠️ Mat en 1 manqué")) problems.add("$where : mat en 1 manqué non signalé — $text")
                // 2. Un mat manqué annoncé doit correspondre à la jauge d'avant le coup.
                Regex("Mat en (\\d) manqué").find(text)?.let { m ->
                    if (m.groupValues[1].toInt() != before) problems.add("$where : « ${m.value} » mais la jauge d'avant indiquait ${before ?: "aucun mat"} — $text")
                    if (before == 1 && !Regex("[^ ]+# matait").containsMatchIn(text)) problems.add("$where : le coup qui matait n'est pas un mat — $text")
                }
                // 3. Mat concédé : même distance que la jauge d'après, et le coup cité mate bien en 1.
                Regex("mat en (\\d) \\(([^)]+)\\)").find(text)?.let { m ->
                    if (m.groupValues[1].toInt() != against) problems.add("$where : « mat en ${m.groupValues[1]} » concédé mais la jauge indique ${against ?: "aucun mat"} — $text")
                    if ((m.groupValues[1] == "1") != m.groupValues[2].endsWith("#")) problems.add("$where : coup de mat incohérent — $text")
                }
                // 4. Distance de mat restante annoncée après un mat manqué : celle de la jauge.
                Regex("encore (\\d+) coups? pour mater").find(text)?.let { m ->
                    if (m.groupValues[1].toInt() != after) problems.add("$where : « ${m.value} » mais la jauge indique ${after ?: "aucun mat"} — $text")
                }
                // 5. Bruit du moteur (statistique) : le meilleur coup joué ne rapproche pas le mat d'un coup.
                if (before != null && before > 1 && line.moves[i].uci == analysis[i]?.best && after != before - 1) noise++
            }
        }
        engine.close()
        val report = buildString {
            appendLine("$games parties, $plies coups commentés, $mates mats, $noise distances de mat imprécises (bruit du moteur).")
            appendLine("${problems.size} incohérences :")
            problems.forEach { appendLine("- $it") }
        }
        File(System.getenv("REVIEW_OUT") ?: "build/review-consistency.txt").apply { parentFile?.mkdirs() }.writeText(report)
        println(report)
        assertTrue(report, problems.isEmpty())
    }
}
