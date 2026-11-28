package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.SessionHeader
import com.ivanherboch.chessduck.ui.WholeOrNothing
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.ui.chessMove
import com.ivanherboch.chessduck.ui.AlignedLabelRows
import com.ivanherboch.chessduck.ui.LabeledRow
import androidx.compose.foundation.lazy.items
import com.ivanherboch.chessduck.ui.LocalIsTwoPane
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.ivanherboch.chessduck.ui.icons.Insights
import com.ivanherboch.chessduck.ui.icons.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import com.ivanherboch.chessduck.ui.icons.SkipNext
import com.ivanherboch.chessduck.ui.icons.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.ui.Badge
import com.ivanherboch.chessduck.ui.BoardRow
import com.ivanherboch.chessduck.ui.evalBarSpace
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.icons.School
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.MoveStrip
import com.ivanherboch.chessduck.ui.stepTitle
import com.ivanherboch.chessduck.ui.PlayerBar

/** Compteurs d'annotations d'un camp pour le résumé de partie. */
private class SideStats(val counts: Map<String, Int>, val missed: Int)

private val NOTES = listOf("!!", "!", "?!", "?", "??")

private val NOTE_NAMES = mapOf("!!" to "brillant", "!" to "très bon", "?!" to "imprécision", "?" to "erreur", "??" to "gaffe")

@Composable
internal fun ReviewLayout(vm: GameReviewViewModel, r: Review, onTrainTopic: ((ReviewTopic) -> Unit)? = null) {
    val c = LocalDuck.current
    BackHandler { vm.close() }
    val line = r.line
    val cur = r.cursor.coerceIn(0, line.size)
    val g = r.record
    val last = if (cur > 0) line.moves[cur - 1] else null
    // Annotations mémorisées par coup : pendant l'analyse en tâche de fond, seuls les coups dont
    // l'évaluation vient de changer sont réannotés (et non toute la partie à chaque position analysée).
    val noteCache = remember(line) { arrayOfNulls<Pair<Pair<Analysis?, Analysis?>, String?>>(line.size) }
    val notes = remember(r.analysis, line) {
        List(line.size) { i ->
            val key = r.analysis.getOrNull(i) to r.analysis.getOrNull(i + 1)
            val cached = noteCache[i]
            if (cached != null && cached.first == key) cached.second
            else GameLogic.annotate(line.positions[i], line.moves[i], key.first, key.second, if (i > 0) line.moves[i - 1] else null)
                .also { noteCache[i] = key to it }
        }
    }
    val note = if (last != null) notes.getOrNull(cur - 1) else null
    val wide = LocalIsTwoPane.current
    val names = Players(
        g.whiteName.ifEmpty { if (g.vsComputer && !g.playerWhite) COMPUTER else "Blancs" } to null,
        g.blackName.ifEmpty { if (g.vsComputer && g.playerWhite) COMPUTER else "Noirs" } to null,
    )
    val pos = line.positions[cur]
    val bar: @Composable (Boolean) -> Unit = { white ->
        val (name, _) = if (white) names.white else names.black
        PlayerBar(name, null, white, pos, toMove = cur < line.size && pos.whiteToMove == white)
    }
    ModuleLayout(
        bottomMin = 170.dp,
        top = {
            SessionHeader(
                "Revue : " + if (g.whiteName.isEmpty()) g.opponent.replace("Stockfish", "Ordinateur") else "${g.whiteName} – ${g.blackName}",
                vm::close, backLabel = "Quitter la revue", subtitle = "${formatDate(g.date)} · ${g.result} (${g.reason})", onFlip = vm::flip,
            ) {
                DuckIconButton(Icons.Rounded.Insights, "Jauge d'évaluation", vm::toggleEval, accent = c.blue, selected = r.evalOn)
            }
        },
        overBoard = { Box(Modifier.padding(start = if (r.evalOn) evalBarSpace() else 0.dp)) { bar(r.flipped) } },
        board = {
            val known = r.analysis.getOrNull(cur) ?: r.analysis.take(cur + 1).lastOrNull { it != null }
            BoardRow(if (r.evalOn) (known?.eval ?: 0) else null, r.flipped) { m ->
                // Badges réservés aux annotations de revue (!!, !, ?!, ?, ??).
                val badges = if (last != null && note != null) listOf(Badge(last.to, note, annotationColor(note, LocalDuck.current.colorBlind))) else emptyList()
                ChessBoard(position = pos, modifier = m, flipped = r.flipped, lastMove = last, anim = r.anim, badges = badges)
            }
        },
        underBoard = { Box(Modifier.padding(start = if (r.evalOn) evalBarSpace() else 0.dp)) { bar(!r.flipped) } },
        bottom = {
            if (!wide) MoveStrip(line, cur, Modifier.padding(top = 2.dp), onSelect = vm::jump, onPrev = { vm.step(-1) }, onNext = { vm.step(1) })
            val ready = r.analysis.count { it != null }
            if (cur == 0) {
                // Résumé à la taille de son contenu ; la place restante reste libre, les boutons ne bougent pas.
                Box(Modifier.padding(top = 4.dp).weight(1f).fillMaxWidth()) { ReviewSummary(g, line, r.analysis, notes, ready, Modifier) }
            } else {
                // Commentaire en langage naturel : aucune évaluation chiffrée, le joueur concerné est toujours nommé.
                val (c0, t0) = ReviewComments.comment(g, line, cur - 1, r.analysis)
                val text = c0 + if (cur == line.size) " " + ReviewComments.ending(g) else ""
                val title = line.stepTitle(cur, note = note)
                // Pont vers l'Académie : la faute du joueur relève d'un thème enseigné (pièce en prise, mat manqué…).
                val topic = onTrainTopic?.let { ReviewComments.topic(g, line, cur - 1, r.analysis) }
                val action: (@Composable () -> Unit)? = topic?.let { t ->
                    {
                        DuckButton(
                            "Travailler ce point dans l'Académie", { onTrainTopic(t) }, Modifier.fillMaxWidth(),
                            accent = c.green, icon = Icons.Rounded.School,
                        )
                    }
                }
                if (wide) {
                    // Tablette : commentaire puis tableau complet des coups sur deux colonnes.
                    FeedbackCard(text, t0, Modifier.padding(top = 4.dp).heightIn(max = 220.dp), title = title, action = action)
                } else {
                    FeedbackCard(text, t0, Modifier.padding(top = 4.dp).weight(1f), title = title, action = action)
                }
            }
            if (wide) MoveTable(line, cur, notes, vm::jump, Modifier.padding(top = 6.dp).weight(1f))
            EqualRow(Modifier.padding(vertical = 6.dp)) {
                DuckButton("Début", vm::first, Modifier.weight(1f), enabled = cur > 0, icon = Icons.Rounded.SkipPrevious)
                DuckButton(
                    if (r.playing) "Pause" else "Lecture", vm::toggle, Modifier.weight(1.4f), accent = c.blue, filled = true,
                    icon = if (r.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                )
                DuckButton("Fin", vm::last, Modifier.weight(1f), enabled = cur < line.size, icon = Icons.Rounded.SkipNext)
            }
        },
    )
}

/** Résumé de la partie (au coup 0) : annotations par camp, mats manqués et progression de l'analyse. */
@Composable
private fun ReviewSummary(g: GameRecord, line: Line, analysis: List<Analysis?>, notes: List<String?>, ready: Int, modifier: Modifier) {
    val c = LocalDuck.current
    val stats = remember(analysis, notes) {
        listOf(true, false).associateWith { white ->
            val idx = line.positions.indices.filter { it < line.size && line.positions[it].whiteToMove == white }
            SideStats(
                NOTES.associateWith { n -> idx.count { notes[it] == n } },
                idx.count { GameLogic.missedMate(white, analysis.getOrNull(it), analysis.getOrNull(it + 1)) != null },
            )
        }
    }
    val total = line.size + 1
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(DuckRadius.M)).background(c.surface).padding(12.dp)) {
        Text("Résumé de la partie", color = c.text, style = MaterialTheme.typography.titleSmall)
        if (ready < total) {
            Text("Analyse : ${fr(ready)} position${if (ready > 1) "s" else ""} sur ${fr(total)}…", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            LinearProgressIndicator(
                progress = { ready.toFloat() / total }, color = c.blueRole.fill, trackColor = c.surfaceHi,
                // Barre pleine et continue : ni écart entre la partie remplie et le reste, ni point final (style Material 3 par défaut).
                gapSize = 0.dp, drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
        // Nom (couleur) toujours en entier. Tout sur une ligne si les deux joueurs tiennent, sinon pour les deux :
        // nom puis, à la ligne, les compteurs alignés à gauche.
        AlignedLabelRows(
            rows = listOf(true, false).map { white ->
                val v = ReviewComments.voice(g, white)
                val st = stats.getValue(white)
                LabeledRow(
                    v.label,
                    v.label + " : " + NOTES.joinToString(", ") { "${st.counts[it]} ${NOTE_NAMES[it]}" } + ", ${st.missed} mat manqué",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (n in NOTES) NoteCount(n, st.counts[n] ?: 0)
                        if (st.missed > 0) Text("${st.missed} mat${if (st.missed > 1) "s" else ""} manqué${if (st.missed > 1) "s" else ""}", color = c.redText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            },
            style = MaterialTheme.typography.labelLarge, color = c.text,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        // Aide facultative : affichée en entier quand la place le permet, sinon masquée (jamais tronquée).
        WholeOrNothing(Modifier.padding(top = 8.dp).weight(1f, fill = false)) {
            Text("Touchez Lecture ou un coup pour lire les commentaires.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NoteCount(note: String, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(DuckRadius.S)).background(annotationColor(note, LocalDuck.current.colorBlind)).padding(horizontal = 6.dp, vertical = 1.dp)) {
            Text(note, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Text(" $n", color = LocalDuck.current.text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Tableau algébrique complet sur deux colonnes (Blancs | Noirs), cliquable — affiché sur tablette (revue et partie). */
@Composable
internal fun MoveTable(line: Line, cursor: Int, notes: List<String?>, onSelect: (Int) -> Unit, modifier: Modifier) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val state = rememberLazyListState()
    val first = if (line.size > 0 && !line.positions[0].whiteToMove) 1 else 0
    val rows = (line.size + first + 1) / 2
    LaunchedEffect(cursor) { if (cursor > 0) state.animateScrollToItem(((cursor - 1 + first) / 2 - 2).coerceAtLeast(0)) }
    LazyColumn(modifier.fillMaxWidth().clip(RoundedCornerShape(DuckRadius.M)).background(c.surface), state = state) {
        items(rows) { row ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                val start = line.positions[0].fullmove
                Text("${start + row}.", color = c.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp).padding(start = 10.dp))
                for (col in 0..1) {
                    val i = row * 2 + col - first
                    Box(Modifier.weight(1f)) {
                        if (i in 0 until line.size) {
                            val current = i == cursor - 1
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(2.dp).clip(RoundedCornerShape(DuckRadius.S))
                                    .background(if (current) c.goldRole.fill else Color.Transparent)
                                    .clickable(role = Role.Button, onClickLabel = "Aller au coup ${line.sans[i]}") { fb?.key(); onSelect(i + 1) }
                                    .semantics { selected = current }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(line.sans[i], color = if (current) c.goldRole.onFill else c.text, style = MaterialTheme.typography.chessMove)
                                notes.getOrNull(i)?.let { n ->
                                    Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(DuckRadius.S)).background(annotationColor(n, LocalDuck.current.colorBlind)).padding(horizontal = 4.dp)) {
                                        Text(n, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
