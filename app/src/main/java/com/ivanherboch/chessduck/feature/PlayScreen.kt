package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.engine.mateIn
import com.ivanherboch.chessduck.engine.winPercent
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.AutoText
import com.ivanherboch.chessduck.ui.Badge
import com.ivanherboch.chessduck.ui.BoardRow
import com.ivanherboch.chessduck.ui.CapturedBar
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.captureBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private fun evalLabel(eval: Int): String = when {
    isMateScore(eval) -> "Mat en ${abs(mateIn(eval)).coerceAtLeast(1)} pour les ${if (eval > 0) "Blancs" else "Noirs"}"
    else -> (if (eval >= 0) "+" else "−") + String.format(Locale.FRANCE, "%.1f", abs(eval) / 100.0)
}

fun annotationColor(a: String): Color = when (a) {
    "!!" -> Color(0xFF0EA5A4)
    "!" -> Color(0xFF10B981)
    "?!" -> Color(0xFFEAB308)
    "?" -> Color(0xFFF97316)
    else -> Color(0xFFEF4444)
}

@Composable
fun PlayScreen(vm: PlayViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val st by vm.settings.collectAsStateWithLifecycle()
    val tally by vm.tally.collectAsStateWithLifecycle()
    val games by vm.games.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    var history by remember { mutableStateOf(false) }
    var gameSettings by remember { mutableStateOf(false) }
    val review = s.review

    if (review != null) ReviewLayout(vm, review) else ModuleLayout(
        bottomMin = 150.dp,
        top = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(38.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1.5f)) {
                    AutoText(if (s.twoPlayers) "👥 ${st.pseudo} – ${st.pseudo2}" else "👤 ${st.pseudo} · ELO ${st.elo}", maxSize = 14.sp, weight = FontWeight.Bold)
                    val w = tally.firstOrNull { it.outcome == 1 }?.n ?: 0
                    val d = tally.firstOrNull { it.outcome == 0 }?.n ?: 0
                    val l = tally.firstOrNull { it.outcome == -1 }?.n ?: 0
                    AutoText(
                        if (s.twoPlayers) "⬜ ${st.pseudo} · ⬛ ${st.pseudo2} · hors ELO" else "Bilan ${w}V / ${d}N / ${l}D · 🤖 Ordinateur ${s.aiElo}",
                        color = c.muted, maxSize = 12.sp,
                    )
                }
                DuckButton("📜 Historique", { history = true }, Modifier.weight(1f), height = 38.dp)
                DuckButton("⚙️ Partie", { gameSettings = true }, Modifier.weight(0.8f), height = 38.dp)
            }
            EqualRow(Modifier.padding(vertical = 5.dp)) {
                DuckButton("⇅ Retourner", vm::flip, Modifier.weight(1f), height = 36.dp)
                DuckButton("💡 Indice ${(s.hintStage + 1).coerceAtMost(2)}/2", vm::hint, Modifier.weight(1f), enabled = s.canPlay, accent = c.green, height = 36.dp)
                DuckButton(if (s.evalOn) "📊 Évaluation ON" else "📊 Évaluation OFF", vm::toggleEval, Modifier.weight(1.2f), accent = c.blue, filled = s.evalOn, height = 36.dp)
            }
        },
        board = {
            val cur = s.cursor
            val pos = s.position
            val last = if (cur > 0) s.line.moves[cur - 1] else null
            val hint = if (s.canPlay) s.hint else null
            val known = s.analysis[cur] ?: s.analysis.filterKeys { it <= cur }.maxByOrNull { it.key }?.value
            BoardRow(if (s.evalOn) (winPercent(known?.eval ?: 0) / 100.0).toFloat() else null, s.flipped) { m ->
                ChessBoard(
                    position = pos,
                    modifier = m,
                    flipped = s.flipped,
                    interactive = s.canPlay,
                    lastMove = last,
                    anim = s.anim,
                    arrows = if (hint != null && s.hintStage >= 2) listOf(Arrow(hint.from, hint.to, c.green)) else emptyList(),
                    marks = if (hint != null && s.hintStage >= 1) listOf(hint.from to c.green) else emptyList(),
                    badges = if (last != null) listOfNotNull(captureBadge(s.line.positions[cur - 1], last)) else emptyList(),
                    onMove = vm::playerMove,
                )
            }
        },
        underBoard = {
            // Pièces capturées et avantage matériel, alignés sur le plateau (décalé par la jauge si elle est affichée).
            CapturedBar(s.position, Modifier.padding(top = 3.dp, start = if (s.evalOn) 15.dp else 0.dp))
        },
        bottom = {
            MoveStrip(s.line, s.cursor, Modifier.padding(top = 5.dp))
            val (msg, tone) = liveStatus(s, st.pseudo, st.pseudo2)
            FeedbackCard(msg, tone, Modifier.padding(top = 5.dp).weight(1f), maxSize = 15.sp)
            EqualRow(Modifier.padding(vertical = 5.dp), gap = 5.dp) {
                DuckButton("◀ Arrière", vm::back, Modifier.weight(1f), enabled = s.cursor > 0 && !s.thinking, height = 44.dp)
                if (s.result == null) DuckButton("🏳️ Abandon", vm::resign, Modifier.weight(1.1f), enabled = s.line.size > 0, accent = c.red, height = 44.dp)
                DuckButton("🆕 Nouvelle", vm::newGame, Modifier.weight(1.3f), accent = c.gold, filled = s.result != null, height = 44.dp)
                DuckButton("Avant ▶", vm::forward, Modifier.weight(1f), enabled = s.cursor < s.line.size && !s.thinking, height = 44.dp)
            }
        },
    )

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
        return (if (s.cursor < s.line.size) "⏪ Coup ${s.cursor} / ${s.line.size}. " else "") + r.text to r.tone
    }
    if (s.thinking) return "🤖 L'ordinateur (${s.aiElo} ELO) réfléchit…" to Tone.NEUTRAL
    val evalNote = if (s.evalOn) s.analysis[s.cursor]?.let { " Évaluation : ${evalLabel(it.eval)}." } ?: "" else ""
    if (s.canPlay && s.hintStage > 0) {
        val h = s.hint ?: return "💡 Calcul en cours, l'indice s'affiche dans un instant…" to Tone.GOLD
        return if (s.hintStage == 1) "💡 Indice 1/2 : la pièce à jouer est surlignée en vert. Touchez encore pour voir le coup." to Tone.GOLD
        else "💡 Indice 2/2 : coup suggéré ${pos.san(h)}.$evalNote" to Tone.GOLD
    }
    if (s.cursor < s.line.size) {
        return if (s.canPlay) "⏪ Coup ${s.cursor} / ${s.line.size} — trait aux $side. Jouez un autre coup ici pour reprendre la partie depuis cette position.$evalNote" to Tone.INFO
        else "⏪ Coup ${s.cursor} / ${s.line.size}. Reculez ou avancez jusqu'à votre tour pour rejouer depuis ce moment.$evalNote" to Tone.INFO
    }
    val check = if (pos.inCheck) "Échec ! " else ""
    return if (s.twoPlayers) "$check👥 À ${if (pos.whiteToMove) p1 else p2} de jouer (les $side).$evalNote" to Tone.INFO
    else if (s.line.size == 0) "⚔️ Nouvelle partie contre l'ordinateur (${s.aiElo} ELO). Vous jouez les $side : à vous de commencer !$evalNote" to Tone.INFO
    else "${check}À vous de jouer (les $side).$evalNote" to Tone.INFO
}

/** Liste horizontale des coups joués (jamais de défilement vertical). */
@Composable
private fun MoveStrip(line: Line, cursor: Int, modifier: Modifier = Modifier) {
    val c = LocalDuck.current
    val listState = rememberLazyListState()
    LaunchedEffect(cursor, line.size) { if (line.size > 0) listState.animateScrollToItem((cursor - 1).coerceAtLeast(0)) }
    LazyRow(
        modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(8.dp)).background(c.surface).padding(horizontal = 6.dp),
        state = listState, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (line.size == 0) item { AutoText("Les coups de la partie s'afficheront ici.", color = c.muted, maxSize = 12.sp) }
        items(line.size) { i ->
            val before = line.positions[i]
            val current = i == cursor - 1
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(if (current) c.gold else Color.Transparent).padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                AutoText(
                    (if (before.whiteToMove) "${before.fullmove}. " else "") + line.sans[i],
                    color = if (current) c.onAccent else if (before.whiteToMove) c.text else c.muted,
                    maxSize = 13.sp, weight = if (current) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ReviewLayout(vm: PlayViewModel, r: Review) {
    val c = LocalDuck.current
    val line = r.line
    val cur = r.cursor.coerceIn(0, line.size)
    val g = r.record
    val last = if (cur > 0) line.moves[cur - 1] else null
    val note = if (last != null) GameLogic.annotate(
        line.positions[cur - 1], last, r.analysis.getOrNull(cur - 1), r.analysis.getOrNull(cur), if (cur > 1) line.moves[cur - 2] else null,
    ) else null
    ModuleLayout(
        bottomMin = 150.dp,
        top = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(38.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1.6f)) {
                    AutoText("📜 Revue : " + if (g.whiteName.isEmpty()) g.opponent.replace("Stockfish", "Ordinateur") else "${g.whiteName} – ${g.blackName}", maxSize = 14.sp, weight = FontWeight.Bold)
                    AutoText("${formatDate(g.date)} · ${g.result} (${g.reason})", color = c.muted, maxSize = 12.sp)
                }
                DuckButton("✕ Quitter la revue", vm::closeReview, Modifier.weight(1f), accent = c.red, height = 38.dp)
            }
            EqualRow(Modifier.padding(vertical = 5.dp)) {
                DuckButton("⇅ Retourner", vm::flip, Modifier.weight(1f), height = 36.dp)
                DuckButton(if (r.evalOn) "📊 Évaluation ON" else "📊 Évaluation OFF", vm::toggleEval, Modifier.weight(1.2f), accent = c.blue, filled = r.evalOn, height = 36.dp)
            }
        },
        board = {
            val known = r.analysis.getOrNull(cur) ?: r.analysis.take(cur + 1).lastOrNull { it != null }
            BoardRow(if (r.evalOn) (winPercent(known?.eval ?: 0) / 100.0).toFloat() else null, r.flipped) { m ->
                val badges = ArrayList<Badge>()
                if (last != null) {
                    if (note != null) badges.add(Badge(last.to, note, annotationColor(note)))
                    captureBadge(line.positions[cur - 1], last)?.let { badges.add(it) }
                }
                ChessBoard(
                    position = line.positions[cur], modifier = m, flipped = r.flipped, lastMove = last, anim = r.anim, badges = badges,
                )
            }
        },
        underBoard = { CapturedBar(line.positions[cur], Modifier.padding(top = 3.dp, start = if (r.evalOn) 15.dp else 0.dp)) },
        bottom = {
            MoveStrip(line, cur, Modifier.padding(top = 5.dp))
            // Commentaire en langage naturel : aucune évaluation chiffrée, le joueur concerné est toujours nommé.
            val ready = r.analysis.count { it != null }
            val (text, tone) = if (cur == 0) {
                val all = (1..line.size).mapNotNull { i ->
                    GameLogic.missedMate(line.positions[i - 1].whiteToMove, r.analysis.getOrNull(i - 1), r.analysis.getOrNull(i))?.let { k -> "coup $i (mat en $k)" }
                }
                ("Position initiale. La partie se déroule automatiquement : ⏸ pour l'arrêter, ◀ ▶ pour naviguer." +
                    (if (all.isNotEmpty()) " ⚠️ Mats manqués : ${all.joinToString(", ")}." else "") +
                    (if (ready <= line.size) " Analyse des coups : $ready / ${line.size + 1}…" else "")) to Tone.INFO
            } else {
                val (c0, t0) = ReviewComments.comment(g, line, cur - 1, r.analysis)
                (c0 + if (cur == line.size) " " + ReviewComments.ending(g) else "") to t0
            }
            val title = if (cur == 0) "Coup 0 / ${line.size}" else {
                val before = line.positions[cur - 1]
                "Coup $cur / ${line.size} · " + moveLabel(before.fullmove, before.whiteToMove, line.sans[cur - 1]) + (if (note != null) " $note" else "")
            }
            FeedbackCard(text, tone, Modifier.padding(top = 5.dp).weight(1f), maxSize = 15.sp, title = title)
            EqualRow(Modifier.padding(vertical = 5.dp), gap = 5.dp) {
                DuckButton("⏮", vm::reviewFirst, Modifier.weight(1f), enabled = cur > 0, height = 44.dp, maxSize = 17.sp)
                DuckButton("◀", { vm.reviewStep(-1) }, Modifier.weight(1f), enabled = cur > 0, height = 44.dp, maxSize = 17.sp)
                DuckButton(if (r.playing) "⏸ Pause" else "▶ Lecture", vm::reviewToggle, Modifier.weight(1.6f), accent = c.blue, filled = true, height = 44.dp, maxSize = 15.sp)
                DuckButton("▶", { vm.reviewStep(1) }, Modifier.weight(1f), enabled = cur < line.size, height = 44.dp, maxSize = 17.sp)
                DuckButton("⏭", vm::reviewLast, Modifier.weight(1f), enabled = cur < line.size, height = 44.dp, maxSize = 17.sp)
            }
        },
    )
}

private fun formatDate(t: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(t))

@Composable
private fun HistorySheet(games: List<GameRecord>, onClose: () -> Unit, onDelete: (Long) -> Unit, onReplay: (GameRecord) -> Unit) {
    val c = LocalDuck.current
    // Partie en attente de confirmation de suppression (une seule à la fois).
    var pendingDelete by remember { mutableStateOf(-1L) }
    DuckSheet("📜 Historique des parties", onClose) {
        if (games.isEmpty()) {
            AutoText("Aucune partie pour l'instant. Terminez une partie en Jeu Libre : elle apparaîtra ici avec sa revue commentée.", color = c.muted, maxSize = 14.sp, maxLines = 4, modifier = Modifier.padding(vertical = 16.dp))
        }
        for (g in games) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (label, col) = when {
                        !g.vsComputer -> (if (g.outcome == 1) "Victoire de ${g.whiteName.ifEmpty { "Blancs" }}" else if (g.outcome == -1) "Victoire de ${g.blackName.ifEmpty { "Noirs" }}" else "Nulle") to c.gold
                        g.outcome == 1 -> "Victoire" to c.green
                        g.outcome == -1 -> "Défaite" to c.red
                        else -> "Nulle" to c.gold
                    }
                    AutoText(label, color = col, maxSize = 16.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (g.vsComputer) {
                        val d = g.eloAfter - g.eloBefore
                        AutoText("ELO ${g.eloAfter} (${if (d >= 0) "+$d" else "−${-d}"})", color = if (d >= 0) c.green else c.red, maxSize = 14.sp, weight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.padding(start = 10.dp).size(34.dp).clip(RoundedCornerShape(17.dp)).background(c.surfaceHi)
                            .clickable { pendingDelete = if (pendingDelete == g.id) -1L else g.id },
                        contentAlignment = Alignment.Center,
                    ) { AutoText("🗑", maxSize = 16.sp, align = TextAlign.Center) }
                }
                val nbMoves = (g.moves.count { it == ' ' } + 2) / 2
                val players = when {
                    g.whiteName.isEmpty() -> "contre ${g.opponent.replace("Stockfish", "Ordinateur")}" + if (g.vsComputer) " · avec les ${if (g.playerWhite) "Blancs" else "Noirs"}" else ""
                    g.vsComputer -> {
                        val me = "(${g.eloBefore})"
                        val ai = "(${g.opponentElo})"
                        "⬜ ${g.whiteName} ${if (g.playerWhite) me else ai}  –  ⬛ ${g.blackName} ${if (g.playerWhite) ai else me}"
                    }
                    else -> "⬜ ${g.whiteName}  –  ⬛ ${g.blackName} · hors ELO"
                }
                AutoText(players, maxSize = 14.sp, weight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                AutoText(
                    "${formatDate(g.date)} · ${g.result} par ${g.reason} · $nbMoves coups",
                    color = c.muted, maxSize = 12.sp, maxLines = 2, modifier = Modifier.padding(vertical = 4.dp),
                )
                if (pendingDelete == g.id) {
                    AutoText("Supprimer définitivement cette partie de l'historique ?", color = c.red, maxSize = 13.sp, weight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(bottom = 6.dp))
                    EqualRow {
                        DuckButton("Annuler", { pendingDelete = -1L }, Modifier.weight(1f), height = 38.dp, maxSize = 14.sp)
                        DuckButton("🗑 Supprimer", { pendingDelete = -1L; onDelete(g.id) }, Modifier.weight(1f), accent = c.red, filled = true, height = 38.dp, maxSize = 14.sp)
                    }
                } else {
                    DuckButton("Rejouer sur l’échiquier", { onReplay(g) }, Modifier.fillMaxWidth(), accent = c.blue, filled = true, height = 38.dp, maxSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun GameSettingsSheet(twoPlayers: Boolean, color: Int, aiElo: Int, onClose: () -> Unit, onApply: (Boolean, Int, Int) -> Unit) {
    val c = LocalDuck.current
    var two by remember { mutableStateOf(twoPlayers) }
    var col by remember { mutableIntStateOf(color) }
    var elo by remember { mutableFloatStateOf(aiElo.toFloat()) }
    DuckSheet("⚙️ Paramètres de partie", onClose) {
        SectionTitle("MODE DE JEU")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DuckChip("🤖 Contre l'ordinateur", !two, { two = false }, Modifier.weight(1f))
            DuckChip("👥 2 joueurs (Pass & Play)", two, { two = true }, Modifier.weight(1f))
        }
        if (!two) {
            SectionTitle("VOTRE COULEUR")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DuckChip("⬜ Blancs", col == 0, { col = 0 }, Modifier.weight(1f))
                DuckChip("🎲 Aléatoire", col == 1, { col = 1 }, Modifier.weight(1f))
                DuckChip("⬛ Noirs", col == 2, { col = 2 }, Modifier.weight(1f))
            }
            SectionTitle("NIVEAU DE L'ORDINATEUR : ${elo.toInt()} ELO")
            Slider(
                value = elo, onValueChange = { elo = (it / 50).toInt() * 50f }, valueRange = 600f..2400f, steps = 35,
                colors = SliderDefaults.colors(thumbColor = c.gold, activeTrackColor = c.gold, inactiveTrackColor = c.surfaceHi, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            )
            Row(Modifier.fillMaxWidth()) {
                AutoText("600 · débutant", color = c.muted, maxSize = 12.sp, modifier = Modifier.weight(1f))
                AutoText("2400 · maître", color = c.muted, maxSize = 12.sp, align = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
        DuckButton("Lancer une nouvelle partie", { onApply(two, col, elo.toInt()) }, Modifier.fillMaxWidth().padding(top = 16.dp), accent = c.gold, filled = true, height = 46.dp, maxSize = 15.sp)
        AutoText("Le pseudo et le thème se règlent dans ⚙️ Paramètres, en haut à droite.", color = c.muted, maxSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
    }
}
