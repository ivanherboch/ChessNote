package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.PieceLexiconFr
import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_VALUES
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.isWhite
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.engine.mateFor
import com.ivanherboch.chessduck.engine.winPercent
import com.ivanherboch.chessduck.ui.Tone

/**
 * Commentaires de revue en langage naturel.
 *
 * Chaque commentaire nomme explicitement le joueur concerné, puis s'adresse à lui : « vous » pour un joueur
 * humain, « il » pour l'ordinateur. Aucune évaluation chiffrée : on explique ce qui se passe sur l'échiquier.
 * Tout est calculé instantanément à partir des analyses déjà stockées (aucune recherche supplémentaire).
 */
object ReviewComments {
    /** Le joueur dont on commente le coup. */
    class Voice(val name: String, val human: Boolean, val white: Boolean) {
        val label: String get() = "$name (${if (white) "Blancs" else "Noirs"})"
        fun f(vous: String, il: String) = if (human) vous else il
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

    // Formes grammaticales des pièces : lexique français partagé par toute l'application.
    private fun the(t: Int) = PieceLexiconFr.definite(t)
    private fun it(t: Int) = PieceLexiconFr.pronoun(t)
    private fun poss(v: Voice, t: Int) = v.f("votre " + PieceLexiconFr.name(t), PieceLexiconFr.possessive(t))

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

    /**
     * La pièce en [sq] peut être prise avec profit : attaquée et non défendue, attaquée par une pièce moins forte, ou
     * attaquée plus de fois qu'elle n'est défendue.
     */
    private fun underAttack(p: Position, sq: Int): Boolean {
        val piece = p[sq]
        if (piece == 0) return false
        val attackers = p.attackers(sq, !isWhite(piece))
        if (attackers.isEmpty()) return false
        val defenders = p.attackers(sq, isWhite(piece))
        return defenders.isEmpty() || attackers.size > defenders.size ||
            attackers.any { PIECE_VALUES[pieceType(p[it])] < PIECE_VALUES[pieceType(piece)] }
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
            m.promo != 0 -> "mène son pion à promotion et obtient ${PieceLexiconFr.indefinite(m.promo)}"
            p.isCapture(m) -> {
                val victim = p.capturedType(m)
                (if (prev != null && prev.to == m.to) "reprend en capturant " else "capture ") + the(victim) + " en ${sqName(m.to)}"
            }
            p.fullmove <= 12 && (t == KNIGHT || t == BISHOP) && rankOf(m.from) == (if (v.white) 0 else 7) -> "développe ${PieceLexiconFr.possessive(t)}"
            t == PAWN && fileOf(m.to) in 3..4 && rankOf(m.to) in 3..4 -> "avance un pion au centre"
            t == PAWN -> "avance son pion en ${sqName(m.to)}"
            else -> "joue ${PieceLexiconFr.possessive(t)} en ${sqName(m.to)}"
        }
        val tail = when {
            san.endsWith("#") -> " et fait échec et mat !"
            san.endsWith("+") -> ", avec échec."
            else -> "."
        }
        return "${v.label} $base$tail"
    }

    /** Pourquoi le coup joué est fautif : la première explication concrète qui s'applique. */
    private fun explain(v: Voice, p: Position, m: Move, san: String, a0: Analysis, a1: Analysis): String =
        diagnose(v, p, m, san, a0, a1).first

    /** Explication d'une faute et, s'il y a lieu, le point faible qu'elle révèle (pont vers l'Académie). */
    /**
     * Fourchette : après [m], la pièce jouée attaque au moins deux pièces adverses (hors pions) qu'elle gagne à prendre
     * (le roi, une pièce plus forte qu'elle, ou une pièce non défendue), sans pouvoir être prise elle-même par une pièce
     * plus faible ni sans protection.
     */
    /**
     * Filet de sécurité du Jeu Libre : pourquoi le coup [m] du joueur humain serait une gaffe (phrase complète) et le
     * point faible qu'il révèle (null s'il ne relève d'aucun thème de l'Académie).
     */
    fun netReason(p: Position, m: Move, a0: Analysis, a1: Analysis): NetReason {
        val v = Voice("", true, p.whiteToMove)
        val (text, topic) = diagnose(v, p, m, p.san(m), a0, a1)
        // La pièce jouée était attaquée : le joueur croit souvent devoir la sauver. On dit si elle l'était vraiment.
        val moved = pieceType(p[m.from])
        val e = if (PieceLexiconFr.isFeminine(moved)) "e" else ""
        val pre = when {
            moved == KING || moved == PAWN || p.attackers(m.from, !p.whiteToMove).isEmpty() -> ""
            underAttack(p, m.from) -> "Mettre ${poss(v, moved)} à l'abri était nécessaire, mais "
            else -> "${poss(v, moved).replaceFirstChar { it.uppercase() }} en ${sqName(m.from)} était attaqué$e, mais pas en danger : " +
                "${if (e.isEmpty()) "il" else "elle"} est défendu$e, et un échange ne vous ferait rien perdre. Le vrai problème : "
        }
        // Majuscule en début de phrase, sauf si la phrase commence par un coup (« b4 », « f5 » : la notation ne change pas).
        val body = if (pre.isNotEmpty()) pre + text else if (text.startsWith(p.san(m))) text else text.replaceFirstChar { it.uppercase() }
        // Le coup conseillé : il évite la perte (souvent, il règle les deux problèmes à la fois).
        val best = a0.best.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }?.takeIf { p.isLegal(it) && it != m }
        return NetReason(body + (best?.let { "\n\nCoup conseillé : " + bestNote(p, it) } ?: ""), topic, best)
    }

    /**
     * Ce que fait le coup conseillé, en clair : un échange (on prend une pièce de même valeur, l'adversaire reprend :
     * rien n'est perdu), un gain de matériel, une prise gratuite, ou un coup tranquille qui ne perd rien.
     */
    internal fun bestNote(p: Position, best: Move): String {
        val bs = p.san(best)
        if (!p.isCapture(best)) return "$bs, qui ne perd rien."
        val mover = pieceType(p[best.from])
        val taken = p.capturedType(best)
        val after = p.play(best)
        val retaken = after.attackers(best.to, !p.whiteToMove).isNotEmpty()
        return when {
            !retaken -> "$bs, qui prend ${the(taken)} adverse gratuitement."
            PIECE_VALUES[taken] > PIECE_VALUES[mover] ->
                "$bs, qui gagne du matériel : ${the(taken)} adverse vaut plus que ${the(mover)} qui ${if (PieceLexiconFr.isFeminine(taken)) "la" else "le"} prend."
            PIECE_VALUES[taken] == PIECE_VALUES[mover] && taken == QUEEN ->
                "$bs, qui échange les dames : vous prenez sa dame, l'adversaire reprend la vôtre, et vous ne perdez rien."
            PIECE_VALUES[taken] == PIECE_VALUES[mover] ->
                "$bs, un échange : vous prenez ${the(taken)} adverse, l'adversaire reprend, et vous ne perdez rien."
            else -> "$bs, qui ne perd rien au bout des échanges."
        }
    }

    /** Explication du filet de sécurité : texte, point faible révélé, coup conseillé (flèche verte). */
    data class NetReason(val text: String, val topic: ReviewTopic?, val best: Move?, val stop: Boolean = topic != null)

    internal fun isFork(p: Position, m: Move): Boolean {
        val white = p.whiteToMove
        val after = p.play(m)
        val sq = m.to
        val value = PIECE_VALUES[pieceType(after[sq])]
        val takers = after.attackers(sq, !white)
        if (takers.isNotEmpty() && (after.attackers(sq, white).isEmpty() || takers.any { PIECE_VALUES[pieceType(after[it])] < value })) return false
        var targets = 0
        for (s in 0 until 64) {
            val pc = after[s]
            if (pc == 0 || isWhite(pc) == white || pieceType(pc) == PAWN) continue
            if (sq !in after.attackers(s, white)) continue
            val t = pieceType(pc)
            if (t == KING || PIECE_VALUES[t] > value || after.attackers(s, !white).isEmpty()) targets++
        }
        return targets >= 2
    }

    private fun diagnose(v: Voice, p: Position, m: Move, san: String, a0: Analysis, a1: Analysis): Pair<String, ReviewTopic?> {
        val after = p.play(m)
        val reply = a1.best.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }?.takeIf { after.isLegal(it) }
        val best = a0.best.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }?.takeIf { p.isLegal(it) && it != m }

        // 1. Le coup autorise un mat rapide.
        val n = mateFor(a1.eval, !p.whiteToMove)
        if (n != null && reply != null) {
            return (if (n <= 2) "en jouant $san, ${v.f("vous permettez", "il permet")} à l'adversaire de faire échec et mat en $n (${after.san(reply)})."
            else "$san laisse l'adversaire lancer une attaque décisive contre ${v.f("votre", "son")} roi.") to ReviewTopic.ALLOWED_MATE
        }
        // 2. Une pièce est laissée en prise.
        if (reply != null && after.isCapture(reply)) {
            val victim = after.capturedType(reply)
            val gained = if (p.isCapture(m)) PIECE_VALUES[p.capturedType(m)] else 0
            if (PIECE_VALUES[victim] > gained) {
                val rs = after.san(reply)
                return (if (reply.to == m.to) "en jouant $san, ${v.f("vous exposez", "il expose")} ${poss(v, victim)} : $rs peut ${it(victim)} capturer."
                else "$san laisse ${poss(v, victim)} en ${sqName(reply.to)} sans protection suffisante : $rs ${it(victim)} capture.") to ReviewTopic.HANGING_PIECE
            }
        }
        // 2 bis. Le coup permet une fourchette : la réponse attaque deux pièces à la fois.
        if (reply != null && !after.isCapture(reply) && isFork(after, reply)) {
            return "en jouant $san, ${v.f("vous permettez", "il permet")} la fourchette ${after.san(reply)}, qui attaque deux pièces à la fois." to ReviewTopic.FORK_ALLOWED
        }
        // 2 ter. La réponse ajoute un attaquant : une pièce n'est plus assez défendue et va tomber au coup suivant.
        if (reply != null && !after.isCapture(reply)) {
            val next = after.play(reply)
            val mine = p.whiteToMove
            for (sq in 0 until 64) {
                val pc = next[sq]
                if (pc == 0 || isWhite(pc) != mine || pieceType(pc) == PAWN || pieceType(pc) == KING) continue
                if (!underAttack(next, sq) || underAttack(after, sq)) continue
                val t = pieceType(pc)
                val e = if (PieceLexiconFr.isFeminine(t)) "e" else ""
                return ("en jouant $san, ${v.f("vous laissez", "il laisse")} ${poss(v, t)} en ${sqName(sq)} trop peu défendu$e : après " +
                    "${after.san(reply)}, ${if (e.isEmpty()) "il" else "elle"} est attaqué$e plus de fois qu'${if (e.isEmpty()) "il" else "elle"} n'est défendu$e et va tomber.") to
                    ReviewTopic.HANGING_PIECE
            }
        }
        if (best != null) {
            val bs = p.san(best)
            val bt = pieceType(p[best.from])
            // 3. Une capture avantageuse a été manquée.
            if (p.isCapture(best)) {
                val victim = p.capturedType(best)
                val free = p.attackers(best.to, !p.whiteToMove).isEmpty()
                if (free || PIECE_VALUES[victim] >= PIECE_VALUES[bt]) {
                    return "${v.f("vous avez", "il a")} manqué l'occasion de capturer ${the(victim)} en ${sqName(best.to)} avec $bs : cela ${v.f("vous ", "lui ")}aurait donné l'avantage." to ReviewTopic.MISSED_CAPTURE
                }
            }
            // 3 bis. Une fourchette a été manquée.
            if (!p.isCapture(best) && isFork(p, best)) {
                return "${v.f("vous avez", "il a")} manqué la fourchette $bs, qui attaquait deux pièces à la fois." to ReviewTopic.MISSED_FORK
            }
            // 4. Une pièce attaquée n'a pas été mise à l'abri.
            if (attackedCheaply(p, best.from) && bt != PAWN && bt != KING && best.from != m.from) {
                return "${poss(v, bt)} en ${sqName(best.from)} était attaqué${if (PieceLexiconFr.isFeminine(bt)) "e" else ""} : il fallait ${it(bt)} mettre à l'abri avec $bs." to ReviewTopic.HANGING_PIECE
            }
            // 5. Un échec fort a été manqué.
            if (bs.endsWith("+")) return "${v.f("vous auriez dû", "il aurait dû")} donner échec avec $bs pour garder l'initiative." to null
            // 6. Principes d'ouverture.
            if (p.isCastle(best)) return "${v.f("vous auriez dû", "il aurait dû")} mettre ${v.f("votre", "son")} roi à l'abri en roquant ($bs)." to ReviewTopic.CASTLING
            if (p.fullmove <= 14) {
                if ((bt == KNIGHT || bt == BISHOP) && rankOf(best.from) == (if (p.whiteToMove) 0 else 7)) {
                    return "${v.f("vous auriez dû", "il aurait dû")} développer ${poss(v, bt)} avec $bs pour prendre part à la bataille du centre." to ReviewTopic.DEVELOPMENT
                }
                if (bt == PAWN && fileOf(best.to) in 3..4 && rankOf(best.to) in 2..5) {
                    return "${v.f("vous auriez dû", "il aurait dû")} occuper le centre avec $bs." to ReviewTopic.DEVELOPMENT
                }
            }
            return "$bs était plus fort que $san." to null
        }
        return "$san affaiblit ${v.f("votre", "sa")} position." to null
    }

    /**
     * Point faible révélé par le coup n° [i] (indice 0), seulement pour un joueur humain et une faute nette :
     * mat manqué, gaffe, erreur ou imprécision dont l'explication relève d'un thème de l'Académie.
     */
    fun topic(g: GameRecord, line: Line, i: Int, analysis: List<Analysis?>): ReviewTopic? {
        if (i !in 0 until line.size) return null
        val p = line.positions[i]
        val v = voice(g, p.whiteToMove)
        if (!v.human) return null
        val a0 = analysis.getOrNull(i) ?: return null
        val a1 = analysis.getOrNull(i + 1) ?: return null
        val m = line.moves[i]
        val san = line.sans[i]
        if (san.endsWith("#")) return null
        if (GameLogic.missedMate(p.whiteToMove, a0, a1) != null) return ReviewTopic.MISSED_MATE
        val note = GameLogic.annotate(p, m, a0, a1, if (i > 0) line.moves[i - 1] else null)
        if (note != "??" && note != "?" && note != "?!") return null
        return diagnose(v, p, m, san, a0, a1).second
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
            // La jauge montre la position après le coup : on dit aussi où en est le mat, pour qu'elle et le texte concordent.
            val left = mateFor(a1.eval, p.whiteToMove)
            val now = when (left) {
                null -> " Après $san, le mat n'est plus forcé."
                else -> " Après $san, ${v.f("il vous faut", "il lui faut")} encore $left ${if (left > 1) "coups" else "coup"} pour mater."
            }
            return "⚠️ Mat en $k manqué · ${v.label} : ${v.f("vous pouviez", "il pouvait")} gagner tout de suite" + (if (best != null) ", $best $how." else ".") + now + trend to Tone.BAD
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
