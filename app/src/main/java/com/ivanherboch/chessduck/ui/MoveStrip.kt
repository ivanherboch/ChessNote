package com.ivanherboch.chessduck.ui

import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.chess.Line

/** Numéro de tour du demi-coup [i], la ligne commençant au tour [first] (par défaut, le vrai numéro de la partie). */
fun Line.turnOf(i: Int, first: Int = positions[0].fullmove): Int = positions[i].fullmove - positions[0].fullmove + first

/** Libellé d'un coup : « 6. O-O » pour les Blancs, « 12… Qe5+ » pour les Noirs. */
fun moveLabel(turn: Int, white: Boolean, san: String) = if (white) "$turn. $san" else "$turn… $san"

/** Titre de carte pour la position après [cursor] demi-coups : « 12… Qe5+ · coup 12 sur 14 ». */
fun Line.stepTitle(cursor: Int, first: Int = positions[0].fullmove, note: String? = null): String {
    val i = cursor - 1
    val turn = turnOf(i, first)
    return moveLabel(turn, positions[i].whiteToMove, sans[i]) + (if (note != null) " $note" else "") + " · coup $turn sur ${turnOf(size - 1, first)}"
}

/**
 * Bande horizontale des coups joués, regroupés par tour : chaque capsule réunit le numéro du tour (en bleu),
 * le coup des Blancs et la réponse des Noirs. Seul le coup affiché est surligné. Flèches précédent / suivant.
 * Chaque coup est cliquable ([onSelect] reçoit le nombre de demi-coups à afficher). [firstTurn] : numéro du
 * premier tour (le vrai numéro de la partie par défaut ; 1 pour une leçon).
 */
@Composable
fun MoveStrip(
    line: Line,
    cursor: Int,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    prevEnabled: Boolean = cursor > 0,
    nextEnabled: Boolean = cursor < line.size,
    emptyText: String = "Les coups de la partie s'afficheront ici.",
    firstTurn: Int = line.positions[0].fullmove,
) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val listState = rememberLazyListState()
    // Demi-coups regroupés par tour : (numéro, indices des coups).
    val turns = remember(line, line.size, firstTurn) { (0 until line.size).groupBy { line.turnOf(it, firstTurn) }.toList() }
    LaunchedEffect(cursor, turns) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.indexOfFirst { (cursor - 1).coerceAtLeast(0) in it.second }.coerceAtLeast(0))
    }
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onPrev != null) DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Coup précédent", onPrev, enabled = prevEnabled)
        LazyRow(
            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface).padding(horizontal = 4.dp),
            state = listState, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (line.size == 0) item { Text(emptyText, color = c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp)) }
            items(turns, key = { it.first }) { (turn, plies) ->
                // Capsule du tour, dessinée derrière : les coups gardent toute la hauteur de la bande comme cible tactile.
                Row(
                    Modifier.fillMaxHeight().drawBehind {
                        val inset = 4.dp.toPx()
                        drawRoundRect(c.surfaceHi, Offset(0f, inset), Size(size.width, size.height - 2 * inset), CornerRadius(9.dp.toPx()))
                    }.padding(start = 8.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val blackFirst = !line.positions[plies.first()].whiteToMove
                    Text(
                        if (blackFirst) "$turn…" else "$turn", color = c.blueText, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 2.dp).clearAndSetSemantics {},
                    )
                    for (i in plies) {
                        val white = line.positions[i].whiteToMove
                        val current = i == cursor - 1
                        val label = moveLabel(turn, white, line.sans[i])
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .then(
                                    if (onSelect != null) Modifier.clickable(role = Role.Button, onClickLabel = "Aller au coup $label") { fb?.key(); onSelect(i + 1) }
                                    else Modifier,
                                )
                                .semantics { selected = current },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.clip(RoundedCornerShape(DuckRadius.S)).background(if (current) c.goldRole.fill else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    line.sans[i], color = if (current) c.goldRole.onFill else if (white) c.text else c.muted,
                                    style = if (current) MaterialTheme.typography.chessMove else MaterialTheme.typography.labelMedium,
                                    // TalkBack annonce le coup avec son numéro : « 6. O-O ».
                                    modifier = Modifier.clearAndSetSemantics { contentDescription = label },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (onNext != null) DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Coup suivant", onNext, enabled = nextEnabled)
    }
}
