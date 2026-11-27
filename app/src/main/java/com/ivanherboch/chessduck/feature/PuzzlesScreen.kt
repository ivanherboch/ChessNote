package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.rememberCoroutineScope
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.ivanherboch.chessduck.ui.icons.Undo
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.ivanherboch.chessduck.ui.DuckSegmentedRow
import com.ivanherboch.chessduck.ui.icons.BookmarkBorder
import com.ivanherboch.chessduck.ui.icons.Bookmark
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.ui.MoveStrip
import com.ivanherboch.chessduck.ui.icons.School
import com.ivanherboch.chessduck.ui.icons.SportsEsports
import com.ivanherboch.chessduck.ui.icons.RestartAlt
import com.ivanherboch.chessduck.ui.icons.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import com.ivanherboch.chessduck.ui.icons.Extension
import com.ivanherboch.chessduck.ui.icons.History
import com.ivanherboch.chessduck.ui.icons.IosShare
import com.ivanherboch.chessduck.ui.icons.SearchOff
import com.ivanherboch.chessduck.ui.icons.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.Puzzle
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
fun PuzzlesScreen(
    vm: PuzzlesViewModel,
    /** « Continuer contre l'Ordinateur » : position finale du problème, camp du joueur (Jeu Libre). */
    onPlayPosition: ((fen: String, playerWhite: Boolean) -> Unit)? = null,
    /** « Voir la leçon » : la leçon de l'Académie qui enseigne ce thème. */
    onOpenLesson: ((theme: String) -> Unit)? = null,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val solvedCount by vm.solvedCount.collectAsStateWithLifecycle()
    val solved by vm.recentSolved.collectAsStateWithLifecycle()
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
    // Thèmes secondaires : un problème tiré pour le thème choisi s'affiche sous ce thème (sinon son thème principal).
    val type = if (s.secondary && s.filterMatches) PuzzleThemes.byKey[s.theme] ?: themeOf(p) else themeOf(p)
    val bookmarked by vm.bookmarked.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val toRedo by vm.toRedo.collectAsStateWithLifecycle()
    val visible = s.themeVisible

    // Explication brève quand on touche une pièce qui ne peut pas bouger ; effacée au coup suivant.
    var stuck by remember(s.puzzle?.id, s.cursor, s.refute) { mutableStateOf<String?>(null) }
    ModuleLayout(
        // Grande police : plus de hauteur pour la carte, pour que l'indice ou la réfutation se lisent sans « Lire la suite ».
        bottomMin = if (LocalDensity.current.fontScale > 1.3f) 240.dp else 170.dp,
        // Nouveau problème : la réserve de hauteur sous l'échiquier se recalcule (message et conseil de longueurs différentes).
        reserveKey = p.id,
        top = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Le filtre actif plutôt qu'un compteur « 0 / 150 000 » décourageant.
                val filter = filterLabel(s.band, s.theme, solvedCount)
                DuckButton(
                    filter, { catalog = true }, Modifier.weight(1f),
                    accent = c.blue, icon = Icons.Rounded.Extension,
                    contentDescription = "Catalogue des problèmes : $filter, ${fr(solvedCount)} résolus sur ${fr(DuckDatabase.PUZZLE_COUNT)}",
                )
                // Une fois le problème fini, l'action principale « Problème suivant » est en bas : ce dé reste discret.
                DuckIconButton(Icons.Rounded.Casino, "Problème aléatoire", vm::random, accent = c.gold)
                DuckIconButton(Icons.Rounded.History, "Problèmes joués : récents, à refaire, favoris", { recentSheet = true }, enabled = recents.isNotEmpty() || toRedo.isNotEmpty() || bookmarks.isNotEmpty())
                DuckIconButton(Icons.Rounded.IosShare, "Exporter en image", { shareChoice = true })
            }
            // Ligne d'information toujours lisible en entier : elle passe à la ligne au lieu de se couper. En grande police,
            // le trait et le signet restent sur une ligne et les détails passent dessous (sinon cinq lignes étroites).
            val large = LocalDensity.current.fontScale > 1.3f
            // Le vrai trait de la position affichée : avant le coup d'introduction, c'est l'adversaire qui joue.
            val turnWhite = if (s.cursor == 0) line.positions[0].whiteToMove else s.playerWhite
            val details = (if (visible) type.name else "Thème masqué") + " · ${PuzzleThemes.BANDS[PuzzleThemes.bandOf(p.rating)]} ${p.rating} · #${p.id}"
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                SideDot(turnWhite)
                Text(
                    if (turnWhite) "Trait aux Blancs" else "Trait aux Noirs", color = c.text, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 6.dp).then(if (large) Modifier.weight(1f) else Modifier),
                )
                if (!large) Text(
                    details, color = c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                // Favori : le problème se retrouve dans l'onglet « Favoris » des problèmes joués.
                DuckIconButton(
                    if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    if (bookmarked) "Retirer des favoris" else "Ajouter aux favoris", vm::toggleBookmark, accent = c.gold, selected = bookmarked,
                )
            }
            if (large) Text(details, color = c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth())
        },
        // Bandeau des coups déjà joués (jamais la suite de la solution) : toucher un coup y revient.
        underBoard = {
            MoveStrip(
                Line(line.startFen, line.moves.take(s.progress)), s.cursor.coerceIn(0, s.progress), Modifier.padding(top = 2.dp),
                onSelect = { n -> vm.goTo(n) }, emptyText = "Les coups joués s'afficheront ici.",
            )
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
                    // 3 : terminé avec la flèche de l'indice, sans la vibration de réussite (le problème ne compte pas).
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
            val showTip = visible && s.refute == null && !s.solved && (s.tone == Tone.INFO || s.tone == Tone.GOOD)
            // On revoit un coup précédent : la carte le dit, et comment revenir au problème.
            val reviewing = s.refute == null && s.cursor < s.progress
            val shown = when {
                !reviewing -> s.message
                s.over -> "Vous revoyez la solution. Touchez › pour avancer jusqu'au dernier coup."
                else -> "Vous revoyez un coup précédent. Touchez › pour revenir au problème et jouer."
            }
            FeedbackCard(
                stuck ?: (shown + if (showTip && !reviewing) "\n\nConseil : ${type.tip}" else ""),
                if (stuck != null) Tone.GOLD else s.tone, Modifier.padding(top = 6.dp).weight(1f),
                title = if (visible) "Type de problème : ${type.name}" else "Thème masqué (mode libre)",
                action = when {
                    !visible -> ({
                        DuckButton("Révéler le thème", vm::revealTheme, icon = Icons.Rounded.Visibility, accent = c.blue, variant = ButtonVariant.OUTLINED)
                    })
                    s.over -> puzzleBridges(s, line, onPlayPosition, onOpenLesson)
                    else -> null
                },
            )
            if (s.refute != null && s.refuteUntil > 0) RefuteCountdown(s.refuteUntil, s.refuteHold, vm::holdRefutation)
            EqualRow(Modifier.padding(vertical = 6.dp)) {
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Coup précédent", vm::back, enabled = s.refute == null && s.cursor > 0)
                when {
                    s.refute != null -> DuckButton("Réessayer", vm::retry, Modifier.weight(1f), accent = c.red, filled = true, icon = Icons.AutoMirrored.Rounded.Undo)
                    s.over -> {
                        // Fini (résolu ou dévoilé) : le rejouer depuis le coup d'introduction, ou passer au suivant.
                        DuckIconButton(Icons.Rounded.RestartAlt, "Recommencer ce problème", vm::restart)
                        DuckButton("Problème suivant", vm::random, Modifier.weight(1f), accent = c.gold, filled = true, icon = Icons.Rounded.Casino)
                    }
                    else -> {
                        // Avant l'indice 2/2, le bouton dit ce qu'il coûte : la flèche montre le coup, le problème ne compte plus.
                        // Libellé complet s'il a la place ; sinon court (la carte de l'indice 1/2 dit déjà qu'il ne compte pas).
                        val roomy = !s.hadError && LocalDensity.current.fontScale <= 1.3f
                        HintButton(
                            s.hintStage, vm::hint, Modifier.weight(1f), enabled = vm.canPlay(s),
                            nextLabel = if (roomy) "Montrer le coup (non compté)" else "Montrer le coup",
                        )
                        // Après une erreur : la solution complète, sans compter le problème comme résolu.
                        if (s.hadError) DuckButton(
                            "Solution", vm::solution, Modifier.weight(0.8f), accent = c.red, variant = ButtonVariant.OUTLINED,
                            contentDescription = "Voir la solution : le problème ne comptera pas comme résolu",
                        )
                    }
                }
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Coup suivant", vm::forward, enabled = s.refute == null && s.cursor < s.progress)
            }
        },
    )

    if (catalog) PuzzleCatalog(vm, s, onClose = { catalog = false; vm.clearSearch() })
    if (recentSheet) {
        DuckSheet("Problèmes joués", { recentSheet = false }) {
            // Trois listes : les 50 derniers, ceux à refaire (manqués ou résolus avec une erreur), les favoris.
            var tab by rememberSaveable { mutableIntStateOf(0) }
            DuckSegmentedRow(listOf("Récents", "À refaire", "Favoris"), tab, { tab = it }, Modifier.fillMaxWidth().padding(bottom = 6.dp))
            val now = System.currentTimeMillis()
            when (tab) {
                0 -> if (recents.isEmpty()) EmptyListText("Les problèmes que vous jouez s'afficheront ici (les 50 derniers).") else for (r in recents) {
                    val done = r.id in solved
                    // Thème visible si le problème est résolu, ou s'il était déjà affiché quand il a été joué.
                    PlayedRow(r, done || r.id in knownThemes, if (done) "Résolu" else "À résoudre", done) { recentSheet = false; vm.openPuzzle(r) }
                }
                1 -> if (toRedo.isEmpty()) EmptyListText("Rien à refaire : un problème manqué, ou résolu après une erreur, revient ici le lendemain, puis à intervalles croissants (7, 30, 90 jours) tant qu'il n'est pas solide.") else for ((r, at) in toRedo) {
                    val days = ((at - now + 86_399_999L) / 86_400_000L).toInt()
                    PlayedRow(r, true, if (days <= 0) "À refaire" else "Dans $days j", false) { recentSheet = false; vm.openPuzzle(r) }
                }
                else -> if (bookmarks.isEmpty()) EmptyListText("Touchez le signet à côté du numéro d'un problème pour le garder ici.") else for (r in bookmarks) {
                    val done = r.id in solved
                    PlayedRow(r, done || r.id in knownThemes, if (done) "Résolu" else "À résoudre", done) { recentSheet = false; vm.openPuzzle(r) }
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

/**
 * Passerelles d'un problème fini : continuer la partie contre l'Ordinateur depuis la position finale (sauf mat), et
 * ouvrir la leçon de l'Académie qui enseigne son thème (si elle existe). Null si aucune ne s'applique.
 */
private fun puzzleBridges(
    s: PuzzleState, line: Line, onPlayPosition: ((String, Boolean) -> Unit)?, onOpenLesson: ((String) -> Unit)?,
): (@Composable () -> Unit)? {
    val theme = s.puzzle?.primaryTheme.orEmpty()
    val end = line.last
    val play = onPlayPosition?.takeIf { end.legalMoves.isNotEmpty() }
    val lesson = onOpenLesson?.takeIf { theme.isNotEmpty() && Lessons.all.any { it.theme == theme } }
    if (play == null && lesson == null) return null
    return {
        val c = LocalDuck.current
        EqualRow(gap = 8.dp) {
            if (play != null) DuckButton(
                "Continuer contre l'Ordinateur", { play(end.toFen(), s.playerWhite) }, Modifier.weight(1f), accent = c.blue, variant = ButtonVariant.OUTLINED,
                icon = Icons.Rounded.SportsEsports, contentDescription = "Continuer la partie contre l'Ordinateur depuis la position finale",
            )
            if (lesson != null) DuckButton(
                "Voir la leçon", { lesson(theme) }, Modifier.weight(1f), accent = c.green, variant = ButtonVariant.OUTLINED,
                icon = Icons.Rounded.School, contentDescription = "Voir la leçon de l'Académie sur ce thème",
            )
        }
    }
}

/** Filtre actif du catalogue, en clair : « Intermédiaire · Clouage », « Libre · 12 résolus »… */
/**
 * Thème principal du problème : la clé calculée une fois pour toutes dans la base ([Puzzle.primaryTheme]),
 * sans réanalyser la liste des thèmes à chaque recomposition ; la liste ne sert que si la clé manque.
 */
private fun themeOf(p: Puzzle) = PuzzleThemes.byKey[p.primaryTheme] ?: PuzzleThemes.primary(p.themes)

private fun filterLabel(band: Int, theme: String, solved: Int): String {
    val name = PuzzleThemes.byKey[theme]?.name ?: PuzzleThemes.groupOf(theme)
    return if (name == null && band == 0) "Libre · ${fr(solved)} résolu${if (solved > 1) "s" else ""}"
    else listOfNotNull(PuzzleThemes.bandLabel(band).takeIf { band > 0 }, name ?: "Tous les thèmes").joinToString(" · ")
}

/** Une ligne des problèmes joués : identifiant, thème (masqué tant qu'il n'est pas connu), difficulté et état. */
@Composable
private fun PlayedRow(r: Puzzle, themeShown: Boolean, status: String, done: Boolean, onOpen: () -> Unit) {
    val c = LocalDuck.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
            .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // Le thème d'un problème non résolu et jamais révélé reste masqué (anti-spoiler).
            Text("#${r.id} · ${if (themeShown) themeOf(r).name else "thème masqué"}", color = c.text, style = MaterialTheme.typography.titleSmall)
            Text("${PuzzleThemes.BANDS[PuzzleThemes.bandOf(r.rating)]} · ${r.rating}", color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.greenText, modifier = Modifier.padding(end = 4.dp).size(18.dp))
        Text(status, color = if (done) c.greenText else c.goldText, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EmptyListText(text: String) =
    Text(text, color = LocalDuck.current.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
