package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_VALUES
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.isWhite
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.engine.mateIn
import com.ivanherboch.chessduck.engine.winPercent
import com.ivanherboch.chessduck.ui.Tone

/**
 * Commentaires de revue en langage naturel.
 *
 * Chaque commentaire nomme explicitement le joueur concerné, puis s'adresse à lui : « tu » pour un joueur
 * humain, « il » pour l'ordinateur. Aucune évaluation chiffrée : on explique ce qui se passe sur l'échiquier.
 * Tout est calculé instantanément à partir des analyses déjà stockées (aucune recherche supplémentaire).
 */
object ReviewComments {
    /** Le joueur dont on commente le coup. */
    class Voice(val name: String, val human: Boolean, val white: Boolean) {
        val label: String get() = "$name (${if (white) "Blancs" else "Noirs"})"
        fun f(tu: String, il: String) = if (human) tu else il
    }

    fun voice(g: GameRecord, white: Boolean): Voice {
        val human = !g.vsComputer || g.playerWhite == white
        val stored = if (white) g.whiteName else g.blackName
        val name = when {
            !human -> "L'ordinateur"
            stored.isNotEmpty() -> stored
            g.vsComputer -> "Joueur"
            else -> if (white) "Joueur 1" else "Joueur 2"
        }
        return Voice(name, human, white)
    }

    private val NAME = arrayOf("", "pion", "cavalier", "fou", "tour", "dame", "roi")
    private fun fem(t: Int) = t == ROOK || t == QUEEN
    private fun the(t: Int) = (if (fem(t)) "la " else "le ") + NAME[t]
    private fun it(t: Int) = if (fem(t)) "la" else "le"
    private fun poss(v: Voice, t: Int) = v.f(if (fem(t)) "ta " else "ton ", if (fem(t)) "sa " else "son ") + NAME[t]

    /** Tendance de la partie, du point de vue des Blancs : -2 … +2. */
    private fun bucket(evalWhite: Int): Int {
        val w = winPercent(evalWhite)
        return when {
            w >= 86 -> 2
            w >= 63 -> 1
            w > 37 -> 0
            w > 14 -> -1
            else -> -2
        }
    }

    private fun attackedCheaply(p: Position, sq: Int): Boolean {
        val piece = p[sq]
        if (piece == 0) return false
        val attackers = p.attackers(sq, !isWhite(piece))
        if (attackers.isEmpty()) return false
        val defended = p.attackers(sq, isWhite(piece)).isNotEmpty()
        return !defended || attackers.any { PIECE_VALUES[pieceType(p[it])] < PIECE_VALUES[pieceType(piece)] }
    }

    /** Description neutre d'un coup, à la troisième personne. */
    private fun describe(v: Voice, p: Position, m: Move, san: String, prev: Move?): String {
        val t = pieceType(p[m.from])
        val base = when {
            p.isCastle(m) -> "roque pour mettre son roi à l'abri"
            m.promo != 0 -> "mène son pion à promotion et obtient une ${NAME[m.promo]}".replace("une cavalier", "un cavalier").replace("une fou", "un fou")
            p.isCapture(m) -> {
                val victim = if (p.isEnPassant(m)) PAWN else pieceType(p[m.to])
                (if (prev != null && prev.to == m.to) "reprend en capturant " else "capture ") + the(victim) + " en ${sqName(m.to)}"
            }
            p.fullmove <= 12 && (t == KNIGHT || t == BISHOP) && rankOf(m.from) == (if (v.white) 0 else 7) -> "développe son ${NAME[t]}"
            t == PAWN && fileOf(m.to) in 3..4 && rankOf(m.to) in 3..4 -> "avance un pion au centre"
            t == PAWN -> "avance son pion en ${sqName(m.to)}"
            else -> "joue ${if (fem(t)) "sa" else "son"} ${NAME[t]} en ${sqName(m.to)}"
        }
        val tail = when {
            san.endsWith("#") -> " et fait échec et mat !"
            san.endsWith("+") -> ", avec échec."
            else -> "."
        }
        return "${v.label} $base$tail"
    }

    /** Pourquoi le coup joué est fautif : la première explication concrète qui s'applique. */
    private fun explain(v: Voice, p: Position, m: Move, san: String, a0: Analysis, a1: Analysis): String {
        val after = p.play(m)
        val reply = a1.best.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }?.takeIf { after.isLegal(it) }
        val best = a0.best.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }?.takeIf { p.isLegal(it) && it != m }
        val sign = if (p.whiteToMove) 1 else -1

        // 1. Le coup autorise un mat rapide.
        val oppScore = -sign * a1.eval
        if (isMateScore(oppScore) && oppScore > 0 && reply != null) {
            val n = mateIn(oppScore)
            return if (n <= 2) "en jouant $san, ${v.f("tu permets", "il permet")} à l'adversaire de faire échec et mat en $n (${after.san(reply)})."
            else "$san laisse l'adversaire lancer une attaque décisive contre ${v.f("ton", "son")} roi."
        }
        // 2. Une pièce est laissée en prise.
        if (reply != null && after.isCapture(reply)) {
            val victim = if (after.isEnPassant(reply)) PAWN else pieceType(after[reply.to])
            val gained = if (p.isCapture(m)) PIECE_VALUES[if (p.isEnPassant(m)) PAWN else pieceType(p[m.to])] else 0
            if (PIECE_VALUES[victim] > gained) {
                val rs = after.san(reply)
                return if (reply.to == m.to) "en jouant $san, ${v.f("tu exposes", "il expose")} ${poss(v, victim)} : $rs peut ${it(victim)} capturer."
                else "$san laisse ${poss(v, victim)} en ${sqName(reply.to)} sans protection suffisante : $rs ${it(victim)} capture."
            }
        }
        if (best != null) {
            val bs = p.san(best)
            val bt = pieceType(p[best.from])
            // 3. Une capture avantageuse a été manquée.
            if (p.isCapture(best)) {
                val victim = if (p.isEnPassant(best)) PAWN else pieceType(p[best.to])
                val free = p.attackers(best.to, !p.whiteToMove).isEmpty()
                if (free || PIECE_VALUES[victim] >= PIECE_VALUES[bt]) {
                    return "${v.f("tu as", "il a")} manqué l'occasion de capturer ${the(victim)} en ${sqName(best.to)} avec $bs : cela ${v.f("t'", "lui ")}aurait donné l'avantage."
                }
            }
            // 4. Une pièce attaquée n'a pas été mise à l'abri.
            if (attackedCheaply(p, best.from) && bt != PAWN && bt != KING && best.from != m.from) {
                return "${poss(v, bt)} en ${sqName(best.from)} était attaqué${if (fem(bt)) "e" else ""} : il fallait ${it(bt)} mettre à l'abri avec $bs."
            }
            // 5. Un échec fort a été manqué.
            if (bs.endsWith("+")) return "${v.f("tu aurais dû", "il aurait dû")} donner échec avec $bs pour garder l'initiative."
            // 6. Principes d'ouverture.
            if (p.isCastle(best)) return "${v.f("tu aurais dû", "il aurait dû")} mettre ${v.f("ton", "son")} roi à l'abri en roquant ($bs)."
            if (p.fullmove <= 14) {
                if ((bt == KNIGHT || bt == BISHOP) && rankOf(best.from) == (if (p.whiteToMove) 0 else 7)) {
                    return "${v.f("tu aurais dû", "il aurait dû")} développer ${poss(v, bt)} avec $bs pour prendre part à la bataille du centre."
                }
                if (bt == PAWN && fileOf(best.to) in 3..4 && rankOf(best.to) in 2..5) {
                    return "${v.f("tu aurais dû", "il aurait dû")} occuper le centre avec $bs."
                }
            }
            return "$bs était plus fort que $san."
        }
        return "$san affaiblit ${v.f("ta", "sa")} position."
    }

    /**
     * Commentaire du coup n° [i] (indice 0) de la partie. [analysis] peut être incomplète :
     * on se contente alors de décrire le coup.
     */
    fun comment(g: GameRecord, line: Line, i: Int, analysis: List<Analysis?>): Pair<String, Tone> {
        val p = line.positions[i]
        val m = line.moves[i]
        val san = line.sans[i]
        val v = voice(g, p.whiteToMove)
        val prev = if (i > 0) line.moves[i - 1] else null
        val a0 = analysis.getOrNull(i)
        val a1 = analysis.getOrNull(i + 1)
        if (san.endsWith("#")) return "Échec et mat ! ${v.label} conclut la partie avec $san." to Tone.GOOD
        val plain = describe(v, p, m, san, prev)
        if (a0 == null || a1 == null) return plain to Tone.INFO

        // Dynamique de la partie : qui a l'avantage après ce coup ?
        val b0 = bucket(a0.eval)
        val b1 = bucket(a1.eval)
        val trend = if (b1 == b0) "" else {
            val w = voice(g, true)
            val b = voice(g, false)
            " ➜ " + when (b1) {
                2 -> "${w.label} a maintenant un avantage décisif."
                1 -> if (b0 > 1) "${w.label} garde l'avantage, mais il n'est plus décisif." else "${w.label} prend l'avantage."
                0 -> "La position est de nouveau équilibrée."
                -1 -> if (b0 < -1) "${b.label} garde l'avantage, mais il n'est plus décisif." else "${b.label} prend l'avantage."
                else -> "${b.label} a maintenant un avantage décisif."
            }
        }

        GameLogic.missedMate(p.whiteToMove, a0, a1)?.let { k ->
            val best = a0.best.takeIf { it.isNotEmpty() }?.let { p.san(Move.fromUci(it)) }
            val how = if (k == 1) "matait immédiatement" else "forçait le mat en 2 coups"
            return "⚠️ Mat en $k manqué · ${v.label} : ${v.f("tu pouvais", "il pouvait")} gagner tout de suite" + (if (best != null) ", $best $how." else ".") + trend to Tone.BAD
        }
        return when (GameLogic.annotate(p, m, a0, a1, prev)) {
            "??" -> "Gaffe · ${v.label} : ${explain(v, p, m, san, a0, a1)}$trend" to Tone.BAD
            "?" -> "Erreur · ${v.label} : ${explain(v, p, m, san, a0, a1)}$trend" to Tone.BAD
            "?!" -> "Imprécision · ${v.label} : ${explain(v, p, m, san, a0, a1)}$trend" to Tone.GOLD
            "!!" -> "Coup brillant · ${v.label} : $san sacrifie du matériel pour une attaque gagnante.$trend" to Tone.GOOD
            "!" -> "Très bon coup · ${v.label} : $san était le seul coup vraiment fort dans cette position.$trend" to Tone.GOOD
            else -> plain + trend to Tone.INFO
        }
    }

    /** Conclusion affichée sur la position finale. */
    fun ending(g: GameRecord): String {
        val w = voice(g, true)
        val b = voice(g, false)
        return when (g.result) {
            "1-0" -> "Fin de la partie : victoire de ${w.label} par ${g.reason}."
            "0-1" -> "Fin de la partie : victoire de ${b.label} par ${g.reason}."
            else -> "Fin de la partie : nulle (${g.reason})."
        }.replace("de L'ordinateur", "de l'ordinateur")
    }
}
