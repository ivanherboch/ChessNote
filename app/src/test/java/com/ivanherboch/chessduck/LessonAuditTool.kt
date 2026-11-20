package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.sqFromName
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.LessonKind
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.engine.Stockfish
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Audit des leçons de l'Académie.
 * - Toujours actif (rapide) : identifiants uniques, « À retenir » présent partout, thèmes connus, arrêts du Guidé
 *   sur un coup du joueur, et cohérence entre le texte et l'échiquier : chaque « cavalier g7 », « la dame b6 »…
 *   cité dans un commentaire désigne bien cette pièce sur cette case (avant ou après le coup commenté).
 * - Avec REVIEW_SF (chemin d'un Stockfish de la machine) : chaque coup du joueur est vérifié par le moteur.
 *   Un coup qui perd au moins 0,8 pion (1 pion en ouverture, où les gambits sont permis), ou qui laisse échapper un
 *   gain, est signalé ; deux gains écrasants se valent. Dans une partie modèle ou une ouverture, un coup historique ou
 *   risqué n'est accepté que si son commentaire le dit à l'élève (« L'Ordinateur préfère… »). Rapport : build/lesson-audit.txt.
 */
class LessonAuditTool {
    @Test
    fun idsTakeawaysThemesAndPauses() {
        val all = Lessons.all
        assertEquals("identifiants en double", all.size, all.map { it.id }.toSet().size)
        for (l in all) {
            assertTrue("« À retenir » manquant : ${l.title}", l.takeaway.length > 10 && l.texts.any { "À retenir" in it })
            assertTrue("thème inconnu : ${l.title}", l.theme.isEmpty() || PuzzleThemes.byKey.containsKey(l.theme))
            for (p in l.pauseSteps) {
                assertTrue("arrêt hors du camp du joueur : ${l.title}", p < l.line.size && l.line.positions[p].whiteToMove == l.heroWhite)
            }
        }
    }

    @Test
    fun textsMatchTheBoard() {
        val problems = all().flatMap { lexicalProblems(it) }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** L'ancienne erreur de la leçon « Calculer un mat en trois coups » (« le cavalier g7 » au lieu du pion) est détectée. */
    @Test
    fun wrongPieceIsReported() {
        val l = Lesson(
            0, Lessons.all.first().category, "test", "test", false, "8/3qbrpk/3p1n1p/p3pN2/Pp2P3/6RQ/1r1B1PPP/3R2K1 b - - 1 26",
            "f6e4 g3g7", listOf("Le cavalier prend le pion e4.", "La tour se sacrifie en prenant le cavalier g7, avec échec."),
        )
        assertEquals(1, lexicalProblems(l).size)
        assertTrue(lexicalProblems(Lesson(0, l.category, "t", "t", false, l.fen, l.uci, listOf("a", "La tour prend le pion g7."))).isEmpty())
    }

    @Test
    fun engine() = runBlocking {
        val sf = System.getenv("REVIEW_SF") ?: return@runBlocking
        val engine = Stockfish(File(sf))
        val report = StringBuilder()
        var heroMoves = 0
        val problems = ArrayList<String>()
        for (l in all()) for (v in 0..l.variants.size) {
            val line = l.lineFor(v)
            for (i in (if (v == 0) 0 else l.variants[v - 1].from) until line.size) {
                val pos = line.positions[i]
                if (pos.whiteToMove != l.heroWhite) continue
                heroMoves++
                val after = line.positions[i + 1]
                val maxLoss = if (l.category == LessonCategory.OPENING || l.category == LessonCategory.MODEL_GAMES) OPENING_LOSS else LOSS
                // Partie modèle ou gambit d'ouverture : un coup historique ou risqué est accepté s'il est signalé
                // à l'élève dans son commentaire (« L'Ordinateur préfère… », « L'Ordinateur le juge risqué… »).
                val flagged = (l.kind == LessonKind.MODEL_GAME || l.category == LessonCategory.OPENING) && "Ordinateur" in l.textsFor(v)[i]
                // Score du coup de la leçon, vu par le joueur : opposé du score de l'adversaire au trait.
                suspend fun verdict(ms: Int): Pair<Int, Int>? {
                    val best = engine.search(pos.toFen(), ms).score ?: return null
                    val played = when {
                        after.isCheckmate -> best
                        after.legalMoves.isEmpty() -> 0
                        else -> -(engine.search(after.toFen(), ms).score ?: return null)
                    }
                    return best to played
                }
                // Deux gains écrasants (mat forcé ou plus de 10 pions d'avance) se valent ; un gambit d'ouverture
                // peut céder jusqu'à un pion ; un gain n'est « gâché » que si l'avantage retombe nettement.
                fun dubious(v: Pair<Int, Int>): Boolean {
                    val (best, played) = v
                    val bothCrushing = best >= CRUSHING && played >= CRUSHING
                    return (best - played >= maxLoss && !bothCrushing) || (best >= WIN && played < WIN - 100)
                }
                var res = verdict(MOVETIME) ?: continue
                // Second avis : un coup signalé après une analyse courte est réexaminé plus longuement, pour ne pas
                // retenir les écarts d'évaluation dus au peu de temps de réflexion.
                if (!flagged && dubious(res)) res = verdict(MOVETIME * SECOND_OPINION) ?: res
                val (best, played) = res
                if (!flagged && dubious(res)) {
                    problems.add("${l.id}. ${l.title}${if (v > 0) " (variante $v)" else ""} — demi-coup ${i + 1} (${line.sans[i]}) : meilleur $best, leçon $played")
                }
            }
        }
        report.append("Leçons : ${all().size}, coups du joueur vérifiés : $heroMoves, coups douteux : ${problems.size}\n")
        problems.forEach { report.append(it).append('\n') }
        val out = File(System.getenv("REVIEW_OUT") ?: "build/lesson-audit.txt")
        out.parentFile?.mkdirs()
        out.writeText(report.toString())
        engine.close()
        assertTrue(report.toString(), problems.isEmpty())
    }

    private fun all(): List<Lesson> = Lessons.all

    private companion object {
        const val MOVETIME = 300
        /** Multiplicateur du temps de réflexion du second avis. */
        const val SECOND_OPINION = 5
        const val LOSS = 80
        const val OPENING_LOSS = 100
        const val CRUSHING = 1000
        const val WIN = 250
        val TYPES = mapOf("pion" to PAWN, "cavalier" to KNIGHT, "fou" to BISHOP, "tour" to ROOK, "dame" to QUEEN, "roi" to KING)
        /** « le cavalier g7 », « la dame noire en b6 », « le pion blanc, en e4 »… (pièce, couleur facultative, case). */
        val CITED = Regex("""\b(pion|cavalier|fou|tour|dame|roi)s? (?:(?:blanc|noir|blanche|noire)s?,? )?(?:en )?([a-h][1-8])\b""", RegexOption.IGNORE_CASE)

        val FUTURE = Regex("""\b(sortie|sortir|jouer|venir|aller|placer|installer|amener|sauter|suivie|prépare|pourra|ira|veut|veulent)\b""", RegexOption.IGNORE_CASE)

        /** Pièces citées avec leur case qui ne sont sur cette case ni avant ni après le coup commenté. */
        fun lexicalProblems(l: Lesson): List<String> = (0..l.variants.size).flatMap { v -> lexicalProblems(l, v) }

        private fun lexicalProblems(l: Lesson, v: Int): List<String> {
            val line = l.lineFor(v)
            val out = ArrayList<String>()
            l.textsFor(v).forEachIndexed { i, text ->
                if (v > 0 && i < l.variants[v - 1].from) return@forEachIndexed
                for (m in CITED.findAll(text)) {
                    // « prépare la sortie du cavalier en f6 », « jouer leur fou en h6 » : une intention, pas un constat.
                    if (" en " in m.value && FUTURE.containsMatchIn(text.substring(maxOf(0, m.range.first - 30), m.range.first))) continue
                    val type = TYPES.getValue(m.groupValues[1].lowercase())
                    val sq = sqFromName(m.groupValues[2])
                    val ok = listOf(line.positions[i], line.positions[i + 1]).any { pieceType(it[sq]) == type && it[sq] != 0 }
                    if (!ok) out.add("${l.id}. ${l.title} — commentaire ${i + 1} : « ${m.value} » ne correspond pas à l'échiquier")
                }
            }
            return out
        }
    }
}
