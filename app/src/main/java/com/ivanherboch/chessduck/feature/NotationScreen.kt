package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.BISHOP
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.chess.QUEEN
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import com.ivanherboch.chessduck.ui.icons.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import com.ivanherboch.chessduck.ui.icons.LocalFireDepartment
import com.ivanherboch.chessduck.ui.icons.Replay
import com.ivanherboch.chessduck.ui.icons.SkipNext
import com.ivanherboch.chessduck.ui.icons.SwapVert
import com.ivanherboch.chessduck.ui.icons.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.InfoPill
import com.ivanherboch.chessduck.ui.LoadingState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.ivanherboch.chessduck.ui.icons.MenuBook
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.pieceLetter
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.HintIconButton
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout

private val LEVEL_SHORT = listOf("Tous", "Bases", "Prises", "Roques")

@Composable
fun NotationScreen(vm: NotationViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    var memo by rememberSaveable { mutableStateOf(false) }
    val fb = LocalFeedback.current
    val ex = s.exercise
    if (ex == null) {
        LoadingState("Préparation de l'exercice…")
        return
    }
    ModuleLayout(
        // Zone basse : carte (~34 dp), saisie 6 + 48, clavier 4 + 52 + 3 × (4 + KEY_ROW) + 4.
        bottomMin = 298.dp,
        top = {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                LevelMenu(s.level, vm::setLevel, Modifier.weight(1f))
                DuckIconButton(Icons.AutoMirrored.Rounded.MenuBook, "Mémo anglais : lettres et symboles FIDE", { memo = true }, accent = c.blue)
                DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
                DuckIconButton(Icons.Rounded.Replay, "Revoir le coup", vm::replay)
                HintIconButton(s.hintStage, vm::hint, enabled = !s.solved)
                DuckIconButton(Icons.Rounded.SkipNext, "Exercice suivant", vm::next, accent = c.gold, filled = s.solved)
            }
        },
        board = {
            val pos = if (s.shown) ex.before.play(ex.move) else ex.before
            ChessBoard(
                position = pos,
                modifier = Modifier.fillMaxSize(),
                flipped = s.flipped,
                lastMove = if (s.shown) ex.move else null,
                anim = s.anim,
                arrows = if (s.shown) listOf(Arrow(ex.move.from, ex.move.to, c.blue)) else emptyList(),
            )
        },
        underBoardFullWidth = true,
        underBoard = {
            // Orientation de l'échiquier et série en cours : toujours présents, la mise en page ne saute pas.
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(
                    if (s.flipped) "Vue Noirs" else "Vue Blancs",
                    icon = Icons.Rounded.SwapVert,
                    description = if (s.flipped) "Échiquier vu du côté des Noirs : les colonnes vont de h à a" else "Échiquier vu du côté des Blancs",
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(0.01f))
                InfoPill(
                    "Série ${s.streak} · record ${s.best}", icon = Icons.Rounded.LocalFireDepartment, accent = if (s.streak > 0) c.gold else null,
                    description = "Série en cours : ${s.streak} bonne${if (s.streak > 1) "s" else ""} réponse${if (s.streak > 1) "s" else ""}. Record : ${s.best}",
                )
            }
        },
        bottom = {
            FeedbackCard(s.message, s.tone, Modifier.padding(top = 6.dp).weight(1f))
            // Barre de saisie : « Tout effacer » à gauche, ⌫ (appui long = tout effacer) et Valider à droite.
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
                        .border(1.dp, if (s.solved) c.greenText else c.outlineStrong, RoundedCornerShape(DuckRadius.M))
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (s.input.isEmpty()) "Votre réponse : vide" else "Votre réponse : ${s.input.toList().joinToString(" ")}"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (s.input.isNotEmpty() && !s.solved) {
                        DuckIconButton(Icons.Rounded.Close, "Tout effacer", vm::clear)
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        s.input.ifEmpty { "Votre réponse…" },
                        color = if (s.input.isEmpty()) c.muted else if (s.solved) c.greenText else c.text,
                        style = if (s.input.isEmpty()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                    )
                }
                BackspaceKey(enabled = !s.solved && s.input.isNotEmpty(), onTap = vm::backspace, onLong = vm::clear)
                if (s.solved) DuckButton("Suivant", vm::next, Modifier.width(112.dp), accent = c.green, filled = true, height = 48.dp, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                else DuckButton("Valider", { if (vm.validate()) fb?.success() else fb?.error() }, Modifier.width(112.dp), enabled = s.input.isNotEmpty(), accent = c.gold, filled = true, height = 48.dp, icon = Icons.Rounded.Check)
            }
            // Clavier virtuel FIDE (aucun clavier système), désactivé une fois la réponse validée.
            val type: (String) -> Unit = { k -> if (!vm.type(k)) fb?.error() }
            val on = !s.solved
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Touches de pièces : lettre FIDE, nom anglais, puis nom français.
                Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((pt, sub) in listOf(KING to "King", QUEEN to "Queen", ROOK to "Rook", BISHOP to "Bishop", KNIGHT to "Knight")) {
                        val k = pieceLetter(pt).toString()
                        Key(k, sub, Modifier.weight(1f), on, "$k, ${sub}, ${PIECE_NAMES_FR[pt]}", sub2 = PIECE_NAMES_FR[pt]) { type(k) }
                    }
                }
                Row(Modifier.fillMaxWidth().height(KEY_ROW), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (ch in 'a'..'h') Key(ch.toString(), null, Modifier.weight(1f), on, "colonne $ch") { type(ch.toString()) }
                }
                Row(Modifier.fillMaxWidth().height(KEY_ROW), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (ch in '1'..'8') Key(ch.toString(), null, Modifier.weight(1f), on, "rangée $ch") { type(ch.toString()) }
                }
                Row(Modifier.fillMaxWidth().height(KEY_ROW), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((k, d) in listOf("x" to "x, prise", "+" to "plus, échec", "#" to "dièse, échec et mat", "=" to "égal, promotion")) {
                        Key(k, null, Modifier.weight(1f), on, d) { type(k) }
                    }
                    Key("O-O", null, Modifier.weight(1.4f), on, "petit roque") { type("O-O") }
                    Key("O-O-O", null, Modifier.weight(1.8f), on, "grand roque") { type("O-O-O") }
                }
            }
        },
    )
    if (memo) MemoSheet { memo = false }
}

/** Sélecteur de niveau compact (menu déroulant). */
@Composable
private fun LevelMenu(level: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val c = LocalDuck.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        DuckButton(
            "Niveau : ${LEVEL_SHORT[level]}", { open = true }, Modifier.widthIn(max = 180.dp), icon = Icons.Rounded.Tune,
            contentDescription = "Niveau d'exercice : ${NotationLogic.LEVELS[level]}. Toucher pour changer",
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            NotationLogic.LEVELS.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text(if (i == 0) "Tous les niveaux" else label, style = MaterialTheme.typography.bodyMedium, color = c.text) },
                    onClick = { open = false; onSelect(i) },
                    leadingIcon = { if (i == level) Icon(Icons.Rounded.Check, contentDescription = "sélectionné", tint = c.goldText) else Spacer(Modifier.size(24.dp)) },
                )
            }
        }
    }
}

/** Touche ⌫ : un appui efface un caractère, un appui long efface toute la saisie. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackspaceKey(enabled: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(DuckRadius.M)).background(if (enabled) c.surfaceHi else c.surfaceSubtle)
            .combinedClickable(
                enabled = enabled, role = Role.Button, onClickLabel = "Effacer un caractère", onLongClickLabel = "Tout effacer",
                onLongClick = { fb?.error(); onLong() },
            ) { fb?.key(); onTap() }
            .semantics { contentDescription = "Effacer" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null, tint = if (enabled) c.text else c.muted)
    }
}

@Composable
private fun Key(label: String, sub: String?, modifier: Modifier, enabled: Boolean, description: String, sub2: String? = null, onClick: () -> Unit) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(DuckRadius.M)).background(if (enabled) c.surfaceHi else c.surfaceSubtle)
            .clickable(enabled = enabled, role = Role.Button) { fb?.key(); onClick() }
            .clearAndSetSemantics { contentDescription = description; if (!enabled) disabled() },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Interlignes resserrés : les trois lignes des touches de pièces tiennent dans 52 dp.
        val small = MaterialTheme.typography.labelSmall.copy(lineHeight = 1.1.em)
        Text(label, color = if (enabled) c.text else c.muted, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 1.2.em), maxLines = 1)
        if (sub != null) Text(sub, color = c.muted, style = small, maxLines = 1)
        if (sub2 != null) Text(sub2, color = if (enabled) c.text else c.muted, style = small, maxLines = 1)
    }
}

/**
 * Hauteur des rangées de lettres, chiffres et symboles (44 dp, taille de cible WCAG 2.5.5). La largeur dépend de l'écran :
 * huit touches par rangée, soit environ 41 dp sur un téléphone de 360 dp, bien au-dessus du minimum WCAG 2.5.8 (24 dp).
 */
private val KEY_ROW = 44.dp
