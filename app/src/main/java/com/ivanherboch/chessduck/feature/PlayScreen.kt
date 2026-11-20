package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.ui.semantics.Role
import com.ivanherboch.chessduck.ui.LocalIsTwoPane
import com.ivanherboch.chessduck.ui.turnOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import com.ivanherboch.chessduck.ui.icons.Undo
import androidx.compose.material.icons.rounded.Add
import com.ivanherboch.chessduck.ui.icons.Analytics
import com.ivanherboch.chessduck.ui.icons.Flag
import com.ivanherboch.chessduck.ui.icons.History
import com.ivanherboch.chessduck.ui.icons.Insights
import com.ivanherboch.chessduck.ui.icons.People
import com.ivanherboch.chessduck.ui.icons.SmartToy
import com.ivanherboch.chessduck.ui.icons.SwapVert
import com.ivanherboch.chessduck.ui.icons.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.BoardRow
import com.ivanherboch.chessduck.ui.evalBarSpace
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.ConfirmDialog
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.HintButton
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.MoveStrip
import com.ivanherboch.chessduck.ui.PlayerBar
import com.ivanherboch.chessduck.ui.Tone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
internal data class Players(val white: Pair<String, String?>, val black: Pair<String, String?>)

@Composable
fun PlayScreen(vm: PlayViewModel, rv: GameReviewViewModel, onTrainTopic: ((ReviewTopic) -> Unit)? = null) {
    val review by rv.review.collectAsStateWithLifecycle()
    // Entrée et sortie de la revue en fondu ; le bouton Retour Android quitte la revue.
    AnimatedContent(review != null, label = "revue", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }) { inReview ->
        if (inReview) review?.let { ReviewLayout(rv, it, onTrainTopic) } else LiveGame(vm, rv::openReview)
    }
}

@Composable
private fun LiveGame(vm: PlayViewModel, openReview: (GameRecord) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val st by vm.settings.collectAsStateWithLifecycle()
    val tally by vm.tally.collectAsStateWithLifecycle()
    val games by vm.games.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    var history by remember { mutableStateOf(false) }
    var gameSettings by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var pendingSettings by remember { mutableStateOf<Triple<Boolean, Int, Int>?>(null) }
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

    // Explication brève quand on touche une pièce qui ne peut pas bouger ; effacée au coup suivant.
    var stuck by remember(pos) { mutableStateOf<String?>(null) }
    ModuleLayout(
        bottomMin = 170.dp,
        top = {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                // Adversaire et bilan : toucher ouvre les réglages de partie (adversaire, couleur, niveau).
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(DuckRadius.M))
                        .clickable(role = Role.Button, onClickLabel = "Modifier les réglages de partie") { gameSettings = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Icon(if (s.twoPlayers) Icons.Rounded.People else Icons.Rounded.SmartToy, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(
                        if (s.twoPlayers) "Partie à 2 joueurs" else "Ordinateur · ${s.aiElo}",
                        color = c.text, style = MaterialTheme.typography.titleSmall,
                    )
                    val w = tally.firstOrNull { it.outcome == 1 }?.n ?: 0
                    val d = tally.firstOrNull { it.outcome == 0 }?.n ?: 0
                    val l = tally.firstOrNull { it.outcome == -1 }?.n ?: 0
                    Text(
                        if (s.twoPlayers) "Hors classement ELO" else "Bilan $w V · $d N · $l D",
                        color = c.muted, style = MaterialTheme.typography.labelMedium,
                    )
                }
                }
                DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
                DuckIconButton(Icons.Rounded.Insights, "Jauge d'évaluation", vm::toggleEval, accent = c.blue, selected = s.evalOn)
                DuckIconButton(Icons.Rounded.History, "Historique des parties", { history = true })
                DuckIconButton(Icons.Rounded.Tune, "Paramètres de partie", { gameSettings = true })
            }
        },
        overBoard = { Box(Modifier.padding(start = if (s.evalOn) evalBarSpace() else 0.dp)) { bar(s.flipped) } },
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
                    onIllegalTouch = { stuck = GameLogic.whyStuck(pos, it) },
                    onMove = vm::playerMove,
                )
            }
        },
        underBoard = { Box(Modifier.padding(start = if (s.evalOn) evalBarSpace() else 0.dp)) { bar(!s.flipped) } },
        bottom = {
            val wide = LocalIsTwoPane.current
            if (!wide) MoveStrip(
                s.line, s.cursor, Modifier.padding(top = 2.dp), onSelect = vm::goTo, onPrev = vm::back, onNext = vm::forward,
                prevEnabled = s.cursor > 0 && !s.thinking, nextEnabled = s.cursor < s.line.size && !s.thinking,
            )
            val (msg, tone) = liveStatus(s, st.pseudo, st.pseudo2)
            // Tablette : message compact puis tableau complet des coups, comme dans la revue.
            FeedbackCard(stuck ?: msg, if (stuck != null) Tone.GOLD else tone, Modifier.padding(top = 4.dp).then(if (wide) Modifier.heightIn(max = 180.dp) else Modifier.weight(1f)))
            if (wide) MoveTable(s.line, s.cursor, emptyList(), { if (!s.thinking) vm.goTo(it) }, Modifier.padding(top = 6.dp).weight(1f))
            EqualRow(Modifier.padding(vertical = 6.dp), gap = 6.dp) {
                if (s.result != null) {
                    // Fin de partie : l'analyse est à un geste.
                    DuckButton(
                        "Analyser la partie", { s.lastRecord?.let(openReview) }, Modifier.weight(1f), enabled = s.lastRecord != null,
                        accent = c.blue, filled = true, icon = Icons.Rounded.Analytics,
                    )
                    DuckButton("Nouvelle partie", vm::newGame, Modifier.weight(1f), accent = c.gold, filled = true, icon = Icons.Rounded.Add)
                } else {
                    HintButton(s.hintStage, vm::hint, Modifier.weight(1f), enabled = s.canPlay)
                    DuckButton(
                        "Annuler", vm::undoMove, Modifier.weight(1f), enabled = undo >= 0, icon = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = if (s.twoPlayers) "Annuler le dernier coup" else "Annuler mon dernier coup",
                    )
                    DuckIconButton(
                        Icons.Rounded.Flag, "Abandonner", { if (st.confirmActions && s.inProgress) confirm = ConfirmAction.RESIGN else vm.resign() },
                        enabled = s.line.size > 0, accent = c.red,
                    )
                    // Neutre pendant la partie : l'action principale reste de jouer.
                    DuckIconButton(Icons.Rounded.Add, "Nouvelle partie", { if (st.confirmActions && s.inProgress) confirm = ConfirmAction.NEW_GAME else vm.newGame() })
                }
            }
        },
    )

    when (confirm) {
        ConfirmAction.RESIGN -> ConfirmDialog(
            "Abandonner la partie ?",
            if (s.twoPlayers) "Le camp au trait perd la partie." else "La partie sera comptée comme une défaite et votre ELO sera mis à jour.",
            "Abandonner", { confirm = null; vm.resign() }, { confirm = null },
        )
        ConfirmAction.NEW_GAME -> ConfirmDialog(
            "Commencer une nouvelle partie ?", "La partie en cours sera perdue (elle n'est pas enregistrée).",
            "Nouvelle partie", { confirm = null; vm.newGame() }, { confirm = null },
        )
        null -> Unit
    }
    // Nouveaux réglages : ils relancent une partie, donc confirmation si une partie est en cours.
    pendingSettings?.let { (two, color, elo) ->
        ConfirmDialog(
            "Appliquer les nouveaux réglages ?", "Une nouvelle partie commencera : la partie en cours sera perdue (elle n'est pas enregistrée).",
            "Nouvelle partie", { pendingSettings = null; vm.applySettings(two, color, elo) }, { pendingSettings = null },
        )
    }
    if (history) HistorySheet(games, onClose = { history = false }, onDelete = vm::deleteGame) { g ->
        // Fermeture instantanée du tiroir, puis revue ouverte sur son résumé.
        history = false
        openReview(g)
    }
    if (gameSettings) GameSettingsSheet(st.twoPlayers, st.colorChoice, st.aiElo, onClose = { gameSettings = false }) { two, color, elo ->
        gameSettings = false
        if (st.confirmActions && s.inProgress) pendingSettings = Triple(two, color, elo) else vm.applySettings(two, color, elo)
    }
}

private fun liveStatus(s: PlayState, p1: String, p2: String): Pair<String, Tone> {
    val pos = s.position
    val side = if (pos.whiteToMove) "Blancs" else "Noirs"
    // Numérotation par coup complet (Blancs puis Noirs), comme sur une feuille de partie.
    val at = if (s.cursor == 0) "Position de départ" else "Coup ${s.line.turnOf(s.cursor - 1)} sur ${s.line.turnOf(s.line.size - 1)}"
    s.result?.let { r ->
        return (if (s.cursor < s.line.size) "$at. " else "") + r.text +
            (if (s.lastRecord != null) " Touchez « Analyser la partie » pour la revue commentée." else "") to r.tone
    }
    if (s.thinking) return "L'ordinateur (${s.aiElo} ELO) réfléchit…" to Tone.NEUTRAL
    if (s.canPlay && s.hintStage > 0) {
        val h = s.hint ?: return "Calcul en cours, l'indice s'affiche dans un instant…" to Tone.GOLD
        return if (s.hintStage == 1) "Indice 1/2 : la pièce à jouer est surlignée en vert. Touchez encore pour voir le coup." to Tone.GOLD
        else "Indice 2/2 : coup suggéré ${pos.san(h)}." to Tone.GOLD
    }
    if (s.cursor < s.line.size) {
        return if (s.canPlay) "$at, trait aux $side. Jouez un autre coup ici pour reprendre la partie depuis cette position." to Tone.INFO
        else "$at. Reculez ou avancez jusqu'à votre tour pour rejouer depuis ce moment." to Tone.INFO
    }
    val check = if (pos.inCheck) "Échec ! " else ""
    return if (s.twoPlayers) "${check}À ${if (pos.whiteToMove) p1 else p2} de jouer (les $side)." to Tone.INFO
    else if (s.line.size == 0 && s.fromPosition) "Position de la leçon contre l'ordinateur (${s.aiElo} ELO). Vous jouez les $side. Partie d'entraînement : l'ELO ne change pas." to Tone.INFO
    else if (s.line.size == 0) "Nouvelle partie contre l'ordinateur (${s.aiElo} ELO). Vous jouez les $side : à vous de commencer !" to Tone.INFO
    else "${check}À vous de jouer (les $side)." to Tone.INFO
}

/** Formateur immuable et thread-safe, partagé par tous les appels. */
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)

internal fun formatDate(t: Long): String = DATE_FORMAT.format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()))

/** Action destructive en attente de confirmation dans le Jeu Libre. */
private enum class ConfirmAction { RESIGN, NEW_GAME }
