package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.rememberCoroutineScope
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.ivanherboch.chessduck.ui.icons.Undo
import com.ivanherboch.chessduck.ui.icons.Casino
import com.ivanherboch.chessduck.ui.icons.Pause
import androidx.compose.material.icons.rounded.CheckCircle
import com.ivanherboch.chessduck.ui.icons.Extension
import com.ivanherboch.chessduck.ui.icons.History
import com.ivanherboch.chessduck.ui.icons.IosShare
import com.ivanherboch.chessduck.ui.icons.SearchOff
import com.ivanherboch.chessduck.ui.icons.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.ButtonVariant
import com.ivanherboch.chessduck.ui.SideDot
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.LoadingState
import com.ivanherboch.chessduck.ui.RefuteCountdown
import com.ivanherboch.chessduck.ui.refuteArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.HintButton
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.Tone
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** NumberFormat n'est pas thread-safe : une instance par thread. */
private val frNumber: ThreadLocal<NumberFormat> = ThreadLocal.withInitial { NumberFormat.getIntegerInstance(Locale.FRANCE) }

fun fr(n: Int): String = frNumber.get()!!.format(n).replace(' ', ' ').replace(' ', ' ')

@Composable
fun PuzzlesScreen(vm: PuzzlesViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val solved by vm.solvedIds.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val context = LocalContext.current
    var catalog by remember { mutableStateOf(false) }
    var shareChoice by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val exportScope = rememberCoroutineScope()
    var recentSheet by remember { mutableStateOf(false) }
    val recents by vm.recents.collectAsStateWithLifecycle()
    val knownThemes by vm.knownThemes.collectAsStateWithLifecycle()
    val line = s.line
    val p = s.puzzle

    if (p == null || line == null || s.empty) {
        if (s.empty) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            EmptyState(
                Icons.Rounded.SearchOff, "Aucun problème trouvé", "Aucun problème ne correspond à ces filtres.",
                actionLabel = "Ouvrir le catalogue", onAction = { catalog = true },
                secondaryLabel = "Réinitialiser les filtres", onSecondary = { vm.setFilter(0, "") },
            )
        } else LoadingState("Chargement du problème…")
        if (catalog) PuzzleCatalog(vm, s, onClose = { catalog = false; vm.clearSearch() })
        return
    }
    val type = PuzzleThemes.primary(p.themes)
    val visible = s.themeVisible

    // Explication brève quand on touche une pièce qui ne peut pas bouger ; effacée au coup suivant.
    var stuck by remember(s.puzzle?.id, s.cursor, s.refute) { mutableStateOf<String?>(null) }
    ModuleLayout(
        bottomMin = 170.dp,
        top = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Le filtre actif plutôt qu'un compteur « 0 / 150 000 » décourageant.
                val filter = filterLabel(s.band, s.theme, solved.size)
                DuckButton(
                    filter, { catalog = true }, Modifier.weight(1f),
                    accent = c.blue, icon = Icons.Rounded.Extension,
                    contentDescription = "Catalogue des problèmes : $filter, ${fr(solved.size)} résolus sur ${fr(DuckDatabase.PUZZLE_COUNT)}",
                )
                DuckIconButton(Icons.Rounded.Casino, "Problème aléatoire", vm::random, accent = c.gold, filled = s.solved)
                DuckIconButton(Icons.Rounded.History, "Problèmes récents", { recentSheet = true }, enabled = recents.isNotEmpty())
                DuckIconButton(Icons.Rounded.IosShare, "Exporter en image", { shareChoice = true })
            }
            // Ligne d'information toujours lisible en entier : elle passe à la ligne au lieu de se couper.
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                SideDot(s.playerWhite)
                Text(
                    if (s.playerWhite) "Trait aux Blancs" else "Trait aux Noirs", color = c.text, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Text(
                    (if (visible) type.name else "Thème masqué") + " · ${PuzzleThemes.BANDS[PuzzleThemes.bandOf(p.rating)]} ${p.rating} · #${p.id}",
                    color = c.muted, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
            }
        },
        board = {
            val r = s.refute
            val cur = s.cursor.coerceIn(0, line.size)
            val pos = r?.position ?: line.positions[cur]
            val last = r?.last ?: if (cur > 0) line.moves[cur - 1] else null
            val canPlay = vm.canPlay(s)
            val next = if (canPlay) line.moves[cur] else null
            ChessBoard(
                position = pos,
                modifier = Modifier.fillMaxSize(),
                flipped = !s.playerWhite,
                interactive = canPlay,
                lastMove = last,
                anim = s.anim,
                arrows = listOfNotNull(refuteArrow(r), if (next != null && s.hintStage >= 2) Arrow(next.from, next.to, c.green) else null),
                marks = if (next != null && s.hintStage >= 1) listOf(next.from to c.green) else emptyList(),
                onIllegalTouch = { stuck = GameLogic.whyStuck(pos, it) },
                // Réfutation affichée : un toucher sur l'échiquier remet tout de suite la position pour rejouer.
                onSquareTap = if (r != null) { _ -> vm.retry() } else null,
                onMove = { m ->
                    when (vm.onMove(m)) {
                        2 -> fb?.success()
                        -1 -> fb?.error()
                    }
                },
            )
        },
        bottom = {
            // Anti-spoiler : en mode libre (tous les thèmes ou une famille), le thème et son conseil
            // restent masqués jusqu'à la résolution, sauf demande explicite.
            val showTip = visible && s.refute == null && !s.solved && s.tone == Tone.INFO
            FeedbackCard(
                stuck ?: (s.message + if (showTip) "\n\nConseil : ${type.tip}" else ""),
                if (stuck != null) Tone.GOLD else s.tone, Modifier.padding(top = 6.dp).weight(1f),
                title = if (visible) "Type de problème : ${type.name}" else "Thème masqué (mode libre)",
                action = if (!visible) ({
                    DuckButton("Révéler le thème", vm::revealTheme, icon = Icons.Rounded.Visibility, accent = c.blue, variant = ButtonVariant.OUTLINED)
                }) else null,
            )
            if (s.refute != null && s.refuteUntil > 0) RefuteCountdown(s.refuteUntil, s.refuteHold, vm::holdRefutation)
            EqualRow(Modifier.padding(vertical = 6.dp)) {
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Coup précédent", vm::back, enabled = s.refute == null && s.cursor > 0)
                if (s.refute != null) DuckButton("Réessayer", vm::retry, Modifier.weight(1f), accent = c.red, filled = true, icon = Icons.AutoMirrored.Rounded.Undo)
                else if (s.solved) DuckButton("Problème aléatoire", vm::random, Modifier.weight(1f), accent = c.gold, filled = true, icon = Icons.Rounded.Casino)
                else HintButton(s.hintStage, vm::hint, Modifier.weight(1f), enabled = vm.canPlay(s))
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Coup suivant", vm::forward, enabled = s.refute == null && s.cursor < s.progress)
            }
        },
    )

    if (catalog) PuzzleCatalog(vm, s, onClose = { catalog = false; vm.clearSearch() })
    if (recentSheet) {
        DuckSheet("Derniers problèmes joués", { recentSheet = false }) {
            for (r in recents) {
                val done = r.id in solved
                // Thème visible si le problème est résolu, ou s'il était déjà affiché quand il a été joué.
                val themeShown = done || r.id in knownThemes
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
                        .clickable(role = Role.Button) { recentSheet = false; vm.openPuzzle(r) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        // Le thème d'un problème non résolu et jamais révélé reste masqué (anti-spoiler).
                        Text("#${r.id} · ${if (themeShown) PuzzleThemes.primary(r.themes).name else "thème masqué"}", color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text("${PuzzleThemes.BANDS[PuzzleThemes.bandOf(r.rating)]} · ${r.rating}", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.greenText, modifier = Modifier.padding(end = 4.dp).size(18.dp))
                    Text(if (done) "Résolu" else "À résoudre", color = if (done) c.greenText else c.goldText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (shareChoice) {
        ExportSheet(
            p, line, s.playerWhite, s.solved, exporting,
            loadStyle = vm::shareStyle,
            saveStyle = vm::saveShareStyle,
            onExport = { block ->
                if (!exporting) {
                    exporting = true
                    exportScope.launch { try { block() } finally { exporting = false } }
                }
            },
            onClose = { shareChoice = false },
        )
    }
}

/** Filtre actif du catalogue, en clair : « Intermédiaire · Clouage », « Libre · 12 résolus »… */
private fun filterLabel(band: Int, theme: String, solved: Int): String {
    val name = PuzzleThemes.byKey[theme]?.name ?: PuzzleThemes.groupOf(theme)
    return if (name == null && band == 0) "Libre · ${fr(solved)} résolu${if (solved > 1) "s" else ""}"
    else listOfNotNull(PuzzleThemes.BANDS[band].takeIf { band > 0 }, name ?: "Tous les thèmes").joinToString(" · ")
}
