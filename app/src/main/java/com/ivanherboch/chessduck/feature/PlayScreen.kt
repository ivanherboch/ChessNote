package com.ivanherboch.chessduck.feature

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.Badge
import com.ivanherboch.chessduck.ui.BoardRow
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.ConfirmDialog
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.MoveStrip
import com.ivanherboch.chessduck.ui.PlayerBar
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.Tone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Couleurs des badges d'annotation (texte blanc ≥ 4,5:1). */
fun annotationColor(a: String): Color = when (a) {
    "!!" -> Color(0xFF0F766E)
    "!" -> Color(0xFF047857)
    "?!" -> Color(0xFFA16207)
    "?" -> Color(0xFFC2410C)
    else -> Color(0xFFB91C1C)
}

/** Les deux joueurs affichés autour de l'échiquier : (nom, détail) des Blancs puis des Noirs. */
private data class Players(val white: Pair<String, String?>, val black: Pair<String, String?>)

@Composable
fun PlayScreen(vm: PlayViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val review = s.review
    // Entrée et sortie de la revue en fondu ; le bouton Retour Android quitte la revue.
    AnimatedContent(review != null, label = "revue", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }) { inReview ->
        if (inReview) s.review?.let { ReviewLayout(vm, it) } else LiveGame(vm)
    }
}

@Composable
private fun LiveGame(vm: PlayViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val st by vm.settings.collectAsStateWithLifecycle()
    val tally by vm.tally.collectAsStateWithLifecycle()
    val games by vm.games.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    var history by remember { mutableStateOf(false) }
    var gameSettings by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val players = if (s.twoPlayers) Players(st.pseudo to null, st.pseudo2 to null)
    else {
        val me = st.pseudo to "${st.elo}"
        val ai = COMPUTER to "${s.aiElo}"
        if (s.playerWhite) Players(me, ai) else Players(ai, me)
    }
    val pos = s.position
    val bar: @Composable (Boolean) -> Unit = { white ->
        val (name, detail) = if (white) players.white else players.black
        val toMove = s.result == null && pos.whiteToMove == white && s.cursor == s.line.size
        PlayerBar(name, detail, white, pos, toMove, thinking = s.thinking && pos.whiteToMove == white)
    }
    val undo = vm.undoTarget(s)

    ModuleLayout(
        bottomMin = 170.dp,
        top = {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (s.twoPlayers) Icons.Rounded.People else Icons.Rounded.SmartToy, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(
                        if (s.twoPlayers) "Partie à 2 joueurs" else "Ordinateur · ${s.aiElo}",
                        color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val w = tally.firstOrNull { it.outcome == 1 }?.n ?: 0
                    val d = tally.firstOrNull { it.outcome == 0 }?.n ?: 0
                    val l = tally.firstOrNull { it.outcome == -1 }?.n ?: 0
                    Text(
                        if (s.twoPlayers) "Hors classement ELO" else "Bilan $w V · $d N · $l D",
                        color = c.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    )
                }
                DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
                DuckIconButton(Icons.Rounded.Insights, "Jauge d'évaluation", vm::toggleEval, accent = c.blue, selected = s.evalOn)
                DuckIconButton(Icons.Rounded.History, "Historique des parties", { history = true })
                DuckIconButton(Icons.Rounded.Tune, "Paramètres de partie", { gameSettings = true })
            }
        },
        overBoard = { Box(Modifier.padding(start = if (s.evalOn) 30.dp else 0.dp)) { bar(s.flipped) } },
        board = {
            val cur = s.cursor
            val last = if (cur > 0) s.line.moves[cur - 1] else null
            val hint = if (s.canPlay) s.hint else null
            val known = s.analysis[cur] ?: s.analysis.filterKeys { it <= cur }.maxByOrNull { it.key }?.value
            BoardRow(if (s.evalOn) (known?.eval ?: 0) else null, s.flipped) { m ->
                ChessBoard(
                    position = pos,
                    modifier = m,
                    flipped = s.flipped,
                    interactive = s.canPlay,
                    lastMove = last,
                    anim = s.anim,
                    arrows = if (hint != null && s.hintStage >= 2) listOf(Arrow(hint.from, hint.to, c.green)) else emptyList(),
                    marks = if (hint != null && s.hintStage >= 1) listOf(hint.from to c.green) else emptyList(),
                    onMove = vm::playerMove,
                )
            }
        },
        underBoard = { Box(Modifier.padding(start = if (s.evalOn) 30.dp else 0.dp)) { bar(!s.flipped) } },
        bottom = {
            MoveStrip(
                s.line, s.cursor, Modifier.padding(top = 2.dp), onSelect = vm::goTo, onPrev = vm::back, onNext = vm::forward,
                prevEnabled = s.cursor > 0 && !s.thinking, nextEnabled = s.cursor < s.line.size && !s.thinking,
            )
            val (msg, tone) = liveStatus(s, st.pseudo, st.pseudo2)
            FeedbackCard(msg, tone, Modifier.padding(top = 4.dp).weight(1f))
            EqualRow(Modifier.padding(vertical = 6.dp), gap = 6.dp) {
                if (s.result != null) {
                    // Fin de partie : l'analyse est à un geste.
                    DuckButton(
                        "Analyser la partie", { s.lastRecord?.let(vm::openReview) }, Modifier.weight(1f), enabled = s.lastRecord != null,
                        accent = c.blue, filled = true, icon = Icons.Rounded.Analytics,
                    )
                    DuckButton("Nouvelle partie", vm::newGame, Modifier.weight(1f), accent = c.gold, filled = true, icon = Icons.Rounded.Add)
                } else {
                    DuckButton(
                        "Indice ${(s.hintStage + 1).coerceAtMost(2)}/2", vm::hint, Modifier.weight(1f), enabled = s.canPlay,
                        accent = c.green, icon = Icons.Rounded.Lightbulb,
                    )
                    DuckButton(
                        if (s.twoPlayers) "Annuler le coup" else "Annuler mon coup", vm::undoMove, Modifier.weight(1f),
                        enabled = undo >= 0, icon = Icons.AutoMirrored.Rounded.Undo,
                    )
                    DuckIconButton(
                        Icons.Rounded.Flag, "Abandonner", { if (st.confirmActions && s.inProgress) confirm = "resign" else vm.resign() },
                        enabled = s.line.size > 0, accent = c.red,
                    )
                    DuckIconButton(
                        Icons.Rounded.Add, "Nouvelle partie", { if (st.confirmActions && s.inProgress) confirm = "new" else vm.newGame() },
                        accent = c.gold,
                    )
                }
            }
        },
    )

    when (confirm) {
        "resign" -> ConfirmDialog(
            "Abandonner la partie ?",
            if (s.twoPlayers) "Le camp au trait perd la partie." else "La partie sera comptée comme une défaite et votre ELO sera mis à jour.",
            "Abandonner", { confirm = null; vm.resign() }, { confirm = null },
        )
        "new" -> ConfirmDialog(
            "Commencer une nouvelle partie ?", "La partie en cours sera perdue (elle n'est pas enregistrée).",
            "Nouvelle partie", { confirm = null; vm.newGame() }, { confirm = null },
        )
    }
    if (history) HistorySheet(games, onClose = { history = false }, onDelete = vm::deleteGame) { g ->
        // Fermeture instantanée du tiroir, puis lecture automatique sur l'échiquier.
        history = false
        vm.openReview(g)
    }
    if (gameSettings) GameSettingsSheet(st.twoPlayers, st.colorChoice, st.aiElo, onClose = { gameSettings = false }) { two, color, elo ->
        gameSettings = false
        vm.applySettings(two, color, elo)
    }
}

private fun liveStatus(s: PlayState, p1: String, p2: String): Pair<String, Tone> {
    val pos = s.position
    val side = if (pos.whiteToMove) "Blancs" else "Noirs"
    s.result?.let { r ->
        return (if (s.cursor < s.line.size) "Coup ${s.cursor} / ${s.line.size}. " else "") + r.text +
            (if (s.lastRecord != null) " Touchez « Analyser la partie » pour la revue commentée." else "") to r.tone
    }
    if (s.thinking) return "L'ordinateur (${s.aiElo} ELO) réfléchit…" to Tone.NEUTRAL
    if (s.canPlay && s.hintStage > 0) {
        val h = s.hint ?: return "Calcul en cours, l'indice s'affiche dans un instant…" to Tone.GOLD
        return if (s.hintStage == 1) "Indice 1/2 : la pièce à jouer est surlignée en vert. Touchez encore pour voir le coup." to Tone.GOLD
        else "Indice 2/2 : coup suggéré ${pos.san(h)}." to Tone.GOLD
    }
    if (s.cursor < s.line.size) {
        return if (s.canPlay) "Coup ${s.cursor} / ${s.line.size}, trait aux $side. Jouez un autre coup ici pour reprendre la partie depuis cette position." to Tone.INFO
        else "Coup ${s.cursor} / ${s.line.size}. Reculez ou avancez jusqu'à votre tour pour rejouer depuis ce moment." to Tone.INFO
    }
    val check = if (pos.inCheck) "Échec ! " else ""
    return if (s.twoPlayers) "${check}À ${if (pos.whiteToMove) p1 else p2} de jouer (les $side)." to Tone.INFO
    else if (s.line.size == 0) "Nouvelle partie contre l'ordinateur (${s.aiElo} ELO). Vous jouez les $side : à vous de commencer !" to Tone.INFO
    else "${check}À vous de jouer (les $side)." to Tone.INFO
}

/** Compteurs d'annotations d'un camp pour le résumé de partie. */
private class SideStats(val counts: Map<String, Int>, val missed: Int)

private val NOTES = listOf("!!", "!", "?!", "?", "??")
private val NOTE_NAMES = mapOf("!!" to "brillant", "!" to "très bon", "?!" to "imprécision", "?" to "erreur", "??" to "gaffe")

@Composable
private fun ReviewLayout(vm: PlayViewModel, r: Review) {
    val c = LocalDuck.current
    BackHandler { vm.closeReview() }
    val line = r.line
    val cur = r.cursor.coerceIn(0, line.size)
    val g = r.record
    val last = if (cur > 0) line.moves[cur - 1] else null
    val note = if (last != null) GameLogic.annotate(
        line.positions[cur - 1], last, r.analysis.getOrNull(cur - 1), r.analysis.getOrNull(cur), if (cur > 1) line.moves[cur - 2] else null,
    ) else null
    val notes = remember(r.analysis, line) {
        List(line.size) { i -> GameLogic.annotate(line.positions[i], line.moves[i], r.analysis.getOrNull(i), r.analysis.getOrNull(i + 1), if (i > 0) line.moves[i - 1] else null) }
    }
    val wide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                DuckIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Quitter la revue", vm::closeReview)
                Column(Modifier.padding(start = 4.dp).weight(1f)) {
                    Text(
                        "Revue : " + if (g.whiteName.isEmpty()) g.opponent.replace("Stockfish", "Ordinateur") else "${g.whiteName} – ${g.blackName}",
                        color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("${formatDate(g.date)} · ${g.result} (${g.reason})", color = c.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
                DuckIconButton(Icons.Rounded.Insights, "Jauge d'évaluation", vm::toggleEval, accent = c.blue, selected = r.evalOn)
            }
        },
        overBoard = { Box(Modifier.padding(start = if (r.evalOn) 30.dp else 0.dp)) { bar(r.flipped) } },
        board = {
            val known = r.analysis.getOrNull(cur) ?: r.analysis.take(cur + 1).lastOrNull { it != null }
            BoardRow(if (r.evalOn) (known?.eval ?: 0) else null, r.flipped) { m ->
                // Badges réservés aux annotations de revue (!!, !, ?!, ?, ??).
                val badges = if (last != null && note != null) listOf(Badge(last.to, note, annotationColor(note))) else emptyList()
                ChessBoard(position = pos, modifier = m, flipped = r.flipped, lastMove = last, anim = r.anim, badges = badges)
            }
        },
        underBoard = { Box(Modifier.padding(start = if (r.evalOn) 30.dp else 0.dp)) { bar(!r.flipped) } },
        bottom = {
            if (!wide) MoveStrip(line, cur, Modifier.padding(top = 2.dp), onSelect = vm::reviewJump, onPrev = { vm.reviewStep(-1) }, onNext = { vm.reviewStep(1) })
            val ready = r.analysis.count { it != null }
            if (cur == 0) {
                ReviewSummary(g, line, r.analysis, notes, ready, Modifier.padding(top = 4.dp).weight(1f))
            } else {
                // Commentaire en langage naturel : aucune évaluation chiffrée, le joueur concerné est toujours nommé.
                val (c0, t0) = ReviewComments.comment(g, line, cur - 1, r.analysis)
                val text = c0 + if (cur == line.size) " " + ReviewComments.ending(g) else ""
                val before = line.positions[cur - 1]
                val title = "Coup $cur / ${line.size} · " + moveLabel(before.fullmove, before.whiteToMove, line.sans[cur - 1]) + (if (note != null) " $note" else "")
                if (wide) {
                    // Tablette : commentaire puis tableau complet des coups sur deux colonnes.
                    FeedbackCard(text, t0, Modifier.padding(top = 4.dp).heightIn(max = 180.dp), title = title)
                } else {
                    FeedbackCard(text, t0, Modifier.padding(top = 4.dp).weight(1f), title = title)
                }
            }
            if (wide) MoveTable(line, cur, notes, vm::reviewJump, Modifier.padding(top = 6.dp).weight(1f))
            EqualRow(Modifier.padding(vertical = 6.dp)) {
                DuckButton("Début", vm::reviewFirst, Modifier.weight(1f), enabled = cur > 0, icon = Icons.Rounded.SkipPrevious)
                DuckButton(
                    if (r.playing) "Pause" else "Lecture", vm::reviewToggle, Modifier.weight(1.4f), accent = c.blue, filled = true,
                    icon = if (r.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                )
                DuckButton("Fin", vm::reviewLast, Modifier.weight(1f), enabled = cur < line.size, icon = Icons.Rounded.SkipNext)
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
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).padding(12.dp)) {
        Text("Résumé de la partie", color = c.text, style = MaterialTheme.typography.titleSmall)
        if (ready < total) {
            Text("Analyse des coups : $ready / $total…", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            LinearProgressIndicator(
                progress = { ready.toFloat() / total }, color = c.blueRole.fill, trackColor = c.surfaceHi,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
        for (white in listOf(true, false)) {
            val v = ReviewComments.voice(g, white)
            val st = stats.getValue(white)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).semantics(mergeDescendants = true) {
                    contentDescription = v.label + " : " + NOTES.joinToString(", ") { "${st.counts[it]} ${NOTE_NAMES[it]}" } + ", ${st.missed} mat manqué"
                },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(v.label, color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                for (n in NOTES) NoteCount(n, st.counts[n] ?: 0)
                if (st.missed > 0) Text("${st.missed} mat${if (st.missed > 1) "s" else ""} manqué${if (st.missed > 1) "s" else ""}", color = c.redText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        Text(
            "Touchez Lecture ou un coup de la liste pour lire les commentaires.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).weight(1f, fill = false),
        )
    }
}

@Composable
private fun NoteCount(note: String, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(annotationColor(note)).padding(horizontal = 6.dp, vertical = 1.dp)) {
            Text(note, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Text(" $n", color = LocalDuck.current.text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Tableau algébrique complet sur deux colonnes (Blancs | Noirs), cliquable — affiché sur tablette. */
@Composable
private fun MoveTable(line: Line, cursor: Int, notes: List<String?>, onSelect: (Int) -> Unit, modifier: Modifier) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val state = rememberLazyListState()
    val first = if (line.size > 0 && !line.positions[0].whiteToMove) 1 else 0
    val rows = (line.size + first + 1) / 2
    LaunchedEffect(cursor) { if (cursor > 0) state.animateScrollToItem(((cursor - 1 + first) / 2 - 2).coerceAtLeast(0)) }
    LazyColumn(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface), state = state) {
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
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(2.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (current) c.goldRole.fill else Color.Transparent)
                                    .clickable(role = Role.Button, onClickLabel = "Aller au coup ${line.sans[i]}") { fb?.key(); onSelect(i + 1) }
                                    .semantics { selected = current }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(line.sans[i], color = if (current) c.goldRole.onFill else c.text, style = MaterialTheme.typography.labelLarge)
                                notes.getOrNull(i)?.let { n ->
                                    Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(annotationColor(n)).padding(horizontal = 4.dp)) {
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

private fun formatDate(t: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(t))

@Composable
private fun HistorySheet(games: List<GameRecord>, onClose: () -> Unit, onDelete: (Long) -> Unit, onReplay: (GameRecord) -> Unit) {
    val c = LocalDuck.current
    var pendingDelete by remember { mutableStateOf<GameRecord?>(null) }
    DuckSheet("Historique des parties", onClose) {
        if (games.isEmpty()) {
            EmptyState(
                Icons.Rounded.History, "Aucune partie pour l'instant",
                "Terminez une partie en Jeu Libre : elle apparaîtra ici avec sa revue commentée.",
                actionLabel = "Fermer", onAction = onClose,
            )
        }
        for (g in games) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (label, col) = when {
                        !g.vsComputer -> (if (g.outcome == 1) "Victoire de ${g.whiteName.ifEmpty { "Blancs" }}" else if (g.outcome == -1) "Victoire de ${g.blackName.ifEmpty { "Noirs" }}" else "Nulle") to c.goldText
                        g.outcome == 1 -> "Victoire" to c.greenText
                        g.outcome == -1 -> "Défaite" to c.redText
                        else -> "Nulle" to c.goldText
                    }
                    Text(label, color = col, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (g.vsComputer) {
                        val d = g.eloAfter - g.eloBefore
                        Text("ELO ${g.eloAfter} (${if (d >= 0) "+$d" else "−${-d}"})", color = if (d >= 0) c.greenText else c.redText, style = MaterialTheme.typography.labelLarge)
                    }
                    DuckIconButton(Icons.Rounded.Delete, "Supprimer cette partie", { pendingDelete = g })
                }
                val nbMoves = (g.moves.count { it == ' ' } + 2) / 2
                val players = when {
                    g.whiteName.isEmpty() -> "contre ${g.opponent.replace("Stockfish", "Ordinateur")}" + if (g.vsComputer) " · avec les ${if (g.playerWhite) "Blancs" else "Noirs"}" else ""
                    g.vsComputer -> {
                        val me = "(${g.eloBefore})"
                        val ai = "(${g.opponentElo})"
                        "Blancs : ${g.whiteName} ${if (g.playerWhite) me else ai}  –  Noirs : ${g.blackName} ${if (g.playerWhite) ai else me}"
                    }
                    else -> "Blancs : ${g.whiteName}  –  Noirs : ${g.blackName} · hors ELO"
                }
                Text(players, color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Text(
                    "${formatDate(g.date)} · ${g.result} par ${g.reason} · $nbMoves coups",
                    color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp),
                )
                DuckButton("Rejouer sur l’échiquier", { onReplay(g) }, Modifier.fillMaxWidth().padding(end = 8.dp), accent = c.blue, filled = true, icon = Icons.Rounded.PlayArrow)
            }
        }
    }
    pendingDelete?.let { g ->
        ConfirmDialog(
            "Supprimer cette partie ?", "Elle disparaîtra définitivement de l'historique (${formatDate(g.date)}).",
            "Supprimer", { pendingDelete = null; onDelete(g.id) }, { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameSettingsSheet(twoPlayers: Boolean, color: Int, aiElo: Int, onClose: () -> Unit, onApply: (Boolean, Int, Int) -> Unit) {
    val c = LocalDuck.current
    var two by remember { mutableStateOf(twoPlayers) }
    var col by remember { mutableIntStateOf(color) }
    var elo by remember { mutableFloatStateOf(aiElo.toFloat()) }
    DuckSheet("Paramètres de partie", onClose) {
        SectionTitle("Mode de jeu")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DuckChip("Contre l'ordinateur", !two, { two = false }, icon = Icons.Rounded.SmartToy)
            DuckChip("2 joueurs (Pass & Play)", two, { two = true }, icon = Icons.Rounded.People)
        }
        if (!two) {
            SectionTitle("Votre couleur")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DuckChip("Blancs", col == 0, { col = 0 })
                DuckChip("Aléatoire", col == 1, { col = 1 })
                DuckChip("Noirs", col == 2, { col = 2 })
            }
            SectionTitle("Niveau de l'ordinateur : ${elo.toInt()} ELO")
            Slider(
                value = elo, onValueChange = { elo = (it / 50).toInt() * 50f }, valueRange = 600f..2400f, steps = 35,
                colors = SliderDefaults.colors(thumbColor = c.goldRole.fill, activeTrackColor = c.goldRole.fill, inactiveTrackColor = c.surfaceHi, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                modifier = Modifier.semantics { contentDescription = "Niveau de l'ordinateur : ${elo.toInt()} ELO" },
            )
            Row(Modifier.fillMaxWidth()) {
                Text("600 · débutant", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("2400 · maître", color = c.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
        DuckButton("Lancer une nouvelle partie", { onApply(two, col, elo.toInt()) }, Modifier.fillMaxWidth().padding(top = 16.dp), accent = c.gold, filled = true, icon = Icons.Rounded.PlayArrow)
        Text("Le pseudo, le thème et les confirmations se règlent dans Paramètres, en haut à droite.", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}
