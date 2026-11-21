package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.ui.BoardRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.refuteArrow
import com.ivanherboch.chessduck.ui.Tone


// Échiquiers des séances de l'Académie : finale contre l'Ordinateur, réflexes, série de problèmes.

/** Finale contre l'Ordinateur : jauge à la fin de la position, flèches de punition, du bon coup et de l'indice. */
@Composable
internal fun EndgameBoard(vm: AcademyViewModel, egs: EndgameSession, sessionFlipped: Boolean, onStuck: (String?) -> Unit) {
    val c = LocalDuck.current
    val pos = egs.position ?: return
    val flip = !egs.family.heroWhite != sessionFlipped
    val hint = egs.hint.takeIf { egs.heroToMove }
    // La jauge n'apparaît qu'une fois la position terminée : elle montre l'issue sans aider pendant la recherche.
    BoardRow(egs.evalWhite.takeIf { egs.over || egs.finished }, flip) { mod ->
        ChessBoard(
            position = pos, modifier = mod, flipped = flip, interactive = egs.heroToMove,
            lastMove = egs.last, anim = egs.anim,
            arrows = listOfNotNull(
                egs.punish?.let { Arrow(it.from, it.to, c.red) },
                // Coup repris après une erreur : le bon coup en vert.
                egs.better?.takeIf { egs.replay && egs.heroToMove && egs.before == null && egs.moves == egs.beforeMoves }
                    ?.let { Arrow(it.from, it.to, c.green) },
                hint?.takeIf { egs.hintStage >= 2 }?.let { Arrow(it.from, it.to, c.green) },
            ),
            marks = listOfNotNull(hint?.takeIf { egs.hintStage == 1 }?.let { it.from to c.green }),
            onIllegalTouch = { onStuck(GameLogic.whyStuck(pos, it)) },
            onMove = vm.endgame::move,
        )
    }
}

/** Réflexes (menace, prise, calcul à l'aveugle) : flèches et cases de la réponse. */
@Composable
internal fun ReflexBoard(vm: AcademyViewModel, rxs: ReflexSession, sessionFlipped: Boolean) {
    val c = LocalDuck.current
    val pos = rxs.position ?: return
    val t = rxs.threat
    val cp = rxs.capture
    val b = rxs.blind
    ChessBoard(
        // Calcul à l'aveugle : l'échiquier reste orienté comme au départ, même réaffiché.
        position = pos, modifier = Modifier.fillMaxSize(), flipped = !(b?.start ?: pos).whiteToMove != sessionFlipped,
        lastMove = t?.lastMove,
        arrows = when {
            cp != null -> listOf(Arrow(cp.capture.from, cp.capture.to, c.blue))
            t != null && rxs.answered -> t.threats.map { (to, from) -> Arrow(from, to, c.red) }
            b != null && rxs.answered -> listOf(Arrow(b.expected.from, b.expected.to, c.green))
            else -> emptyList()
        },
        marks = when {
            t != null && rxs.answered -> t.targets.map { it to c.green } + listOfNotNull(rxs.tapped.takeIf { it >= 0 && it !in t.targets }?.let { it to c.red })
            b != null && !rxs.answered && rxs.from >= 0 -> listOf(rxs.from to c.select)
            // Calcul à l'aveugle, réponse fausse : la case d'arrivée jouée en rouge, la flèche verte montre le bon coup.
            b != null && rxs.answered && rxs.tapped >= 0 && rxs.tone == Tone.BAD &&
                !(rxs.from == b.expected.from && rxs.tapped == b.expected.to) ->
                listOf(rxs.tapped to c.red)
            else -> emptyList()
        },
        onSquareTap = if ((t != null || b != null) && !rxs.answered) vm.reflex::tap else null,
    )
}

/** Série de problèmes : réfutation, indice en deux temps, solution ; [fallback] avant le chargement du problème. */
@Composable
internal fun PracticeBoard(vm: AcademyViewModel, pr: Practice, fallback: Position, sessionFlipped: Boolean, onStuck: (String?) -> Unit) {
    val c = LocalDuck.current
    val pl = pr.line
    val pc = pr.cursor.coerceIn(0, pl?.size ?: 0)
    val r = pr.refute
    val ppos = r?.position ?: pl?.positions?.get(pc) ?: fallback
    val pnext = if (pl != null && pc < pl.size) pl.moves[pc] else null
    val pTurn = pl != null && r == null && !pr.failed && !pr.solved && !pr.finished && pc == pr.progress && pc < pl.size &&
        ppos.whiteToMove == pr.heroWhite
    val refArrow = refuteArrow(r)
    ChessBoard(
        position = ppos,
        modifier = Modifier.fillMaxSize(),
        flipped = !pr.heroWhite != sessionFlipped,
        interactive = pTurn,
        lastMove = r?.last ?: if (pl != null && pc > 0) pl.moves[pc - 1] else null,
        anim = pr.anim,
        arrows = when {
            refArrow != null -> listOf(refArrow)
            pnext != null && (pr.failed || (pTurn && pr.hintStage >= 2)) -> listOf(Arrow(pnext.from, pnext.to, c.green))
            else -> emptyList()
        },
        marks = if (pTurn && pr.hintStage >= 1 && pnext != null) listOf(pnext.from to c.green) else emptyList(),
        onIllegalTouch = { onStuck(GameLogic.whyStuck(ppos, it)) },
        // Réfutation affichée : un toucher sur l'échiquier remet la position pour rejouer aussitôt.
        onSquareTap = if (r != null) { _ -> vm.retryPractice() } else null,
        onMove = vm::practiceMove,
    )
}
