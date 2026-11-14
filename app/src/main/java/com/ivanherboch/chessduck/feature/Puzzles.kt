package com.ivanherboch.chessduck.feature

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.ivanherboch.chessduck.ui.ButtonVariant
import com.ivanherboch.chessduck.ui.SideDot
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.LoadingState
import java.text.Normalizer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.data.PuzzleProgress
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.engine.mateIn
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.AutoText
import com.ivanherboch.chessduck.ui.Badge
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.ui.undoAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat
import java.util.Locale

/** Affichage temporaire d'un mauvais coup puis de sa réfutation (la grille reste dégagée). */
data class Refutation(val position: Position, val last: Move, val arrow: Arrow?, val badge: Badge?)

data class PuzzleState(
    val puzzle: Puzzle? = null,
    val line: Line? = null,
    /** Nombre de demi-coups de la solution déjà joués. */
    val progress: Int = 0,
    val cursor: Int = 0,
    val solved: Boolean = false,
    val anim: BoardAnim? = null,
    val hintStage: Int = 0,
    val message: String = "Chargement du problème…",
    val tone: Tone = Tone.INFO,
    val refute: Refutation? = null,
    val band: Int = 0,
    val theme: String = "",
    val empty: Boolean = false,
    /** Thème révélé à la demande avant la résolution (anti-spoiler en mode libre). */
    val themeRevealed: Boolean = false,
    /** Fin (horloge système) et durée de l'affichage de la réfutation, pour le compte à rebours. */
    val refuteUntil: Long = 0L,
    val refuteHold: Long = 0L,
) {
    /** Le thème est visible si le joueur l'a choisi lui-même, l'a révélé, ou a résolu le problème. */
    val themeVisible: Boolean get() = solved || themeRevealed || PuzzleThemes.byKey.containsKey(theme)

    val playerWhite: Boolean get() = line?.positions?.getOrNull(1)?.whiteToMove ?: true
}

class ThemeCount(val total: Int, val solved: Int)

/** Suggestion de recherche : identifiant Lichess + type de problème. */
data class Suggestion(val id: String, val type: String, val rating: Int)
data class Suggestions(val query: String = "", val items: List<Suggestion> = emptyList(), val more: Boolean = false)

/** Durée d'affichage de la réfutation avant l'annulation automatique. */
const val REFUTE_HOLD = 4000L

class PuzzlesViewModel(app: Application) : AndroidViewModel(app) {
    private val duck = app as ChessDuckApp
    private val dao = duck.db.puzzles()
    private val rnd = kotlin.random.Random(System.nanoTime())
    val state = MutableStateFlow(PuzzleState())
    val solvedIds = dao.solvedIds().map { it.toHashSet() as Set<String> }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val counts = MutableStateFlow<Map<String, ThemeCount>>(emptyMap())
    val suggestions = MutableStateFlow(Suggestions())
    /** Les 5 derniers problèmes joués avant le problème courant (persistés via DataStore). */
    val recents = MutableStateFlow<List<Puzzle>>(emptyList())
    private var job: Job? = null
    private var searchJob: Job? = null
    private val metaLock = Mutex()

    // Index mémoire compact du catalogue (classement + masque de thèmes par problème).
    private var metaIds: Array<String> = emptyArray()
    private var metaRatings = IntArray(0)
    /** Thème unique de chaque problème (indice dans PuzzleThemes.all) : son idée la plus caractéristique. */
    private var metaCat = ByteArray(0)
    private var metaIdsLower: Array<String> = emptyArray()
    private var metaThemes: Array<String> = emptyArray()

    init {
        viewModelScope.launch {
            val st = duck.prefs.settings.first()
            state.update { it.copy(band = st.puzzleBand, theme = st.puzzleTheme) }
            val saved = if (st.puzzleId.isNotEmpty()) runCatching { dao.byId(st.puzzleId) }.getOrNull() else null
            if (saved != null) show(saved) else random()
        }
        viewModelScope.launch {
            duck.prefs.settings.map { it.recentPuzzles }.distinctUntilChanged().collect { ids ->
                val others = ids.drop(1).take(5)
                val found = if (others.isEmpty()) emptyList() else runCatching { dao.byIds(others) }.getOrDefault(emptyList())
                recents.value = others.mapNotNull { id -> found.firstOrNull { it.id == id } }
            }
        }
        // L'index mémoire est préparé dès le lancement : la recherche répond ensuite instantanément.
        viewModelScope.launch(Dispatchers.Default) {
            // Après une mise à jour de l'application, les nouveaux problèmes du catalogue sont d'abord importés.
            if (DuckDatabase.syncPuzzles(duck, duck.db)) metaLock.withLock { metaIds = emptyArray() }
            ensureMeta()
        }
    }

    private suspend fun ensureMeta() = metaLock.withLock {
        if (metaIds.isNotEmpty()) return@withLock
        val meta = runCatching { dao.allMeta() }.getOrDefault(emptyList())
        val idx = PuzzleThemes.all.withIndex().associate { it.value.key to it.index }
        metaRatings = IntArray(meta.size) { meta[it].rating }
        metaCat = ByteArray(meta.size) { i -> (idx[PuzzleThemes.primary(meta[i].themes).key] ?: 0).toByte() }
        metaThemes = Array(meta.size) { meta[it].themes }
        metaIdsLower = Array(meta.size) { meta[it].id.lowercase() }
        metaIds = Array(meta.size) { meta[it].id }
    }

    /**
     * Suggestions pendant la saisie : correspondance insensible à la casse sur l'identifiant,
     * classée par pertinence (identique, puis préfixe, puis contenu), limitée à 5 résultats.
     */
    fun search(raw: String) {
        searchJob?.cancel()
        val q = raw.trim().removePrefix("#").lowercase()
        if (q.isEmpty()) { suggestions.value = Suggestions(); return }
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            ensureMeta()
            val exact = ArrayList<Int>()
            val prefix = ArrayList<Int>()
            val inside = ArrayList<Int>()
            var total = 0
            for (i in metaIdsLower.indices) {
                val id = metaIdsLower[i]
                val at = id.indexOf(q)
                if (at < 0) continue
                total++
                when {
                    id.length == q.length -> exact.add(i)
                    at == 0 -> if (prefix.size < 6) prefix.add(i)
                    else -> if (inside.size < 6) inside.add(i)
                }
                if (i and 0x3FF == 0) ensureActive()
            }
            val top = (exact + prefix + inside).take(5)
            suggestions.value = Suggestions(
                raw.trim().removePrefix("#"),
                top.map { Suggestion(metaIds[it], PuzzleThemes.primary(metaThemes[it]).name, metaRatings[it]) },
                total > top.size,
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        suggestions.value = Suggestions()
    }

    fun openPuzzle(p: Puzzle) = show(p)

    fun openSuggestion(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { dao.byId(id) }.getOrNull()?.let { show(it) }
            onDone()
        }
    }

    /** Conseil du thème que le joueur a choisi de travailler (si le problème le porte), sinon celui du type du problème. */
    private fun turnLabel(white: Boolean) = if (white) "Trait aux Blancs" else "Trait aux Noirs"

    private fun show(p: Puzzle) {
        job?.cancel()
        val line = runCatching { Line.fromUci(p.fen, p.moves) }.getOrNull() ?: return
        val white = line.positions[1].whiteToMove
        state.update {
            it.copy(
                puzzle = p, line = line, progress = 0, cursor = 0, solved = false, anim = null, hintStage = 0, refute = null, empty = false,
                themeRevealed = false, message = "${turnLabel(white)} : trouvez la meilleure suite.", tone = Tone.INFO,
            )
        }
        job = viewModelScope.launch {
            duck.prefs.pushRecentPuzzle(p.id)
            delay(600)
            state.update { it.copy(progress = 1, cursor = 1, anim = moveAnim(line.positions[0], line.moves[0])) }
        }
    }

    /** Tire un problème non résolu correspondant aux filtres actifs. */
    fun random() {
        viewModelScope.launch {
            val s = state.value
            val r = PuzzleThemes.bandRange(s.band)
            val cur = s.puzzle?.id ?: ""
            // Tirage dans l'index mémoire : difficulté + thème unique du problème, non résolus d'abord.
            val id = withContext(Dispatchers.Default) {
                ensureMeta()
                // Filtre : un thème précis, ou un groupe entier (tous les mats, toutes les finales…).
                val group = PuzzleThemes.groupOf(s.theme)
                val allowed = BooleanArray(PuzzleThemes.all.size) { i ->
                    if (group != null) PuzzleThemes.all[i].group == group else s.theme.isEmpty() || PuzzleThemes.all[i].key == s.theme
                }
                val solved = solvedIds.value
                var fresh = -1
                var freshCount = 0
                var any = -1
                var anyCount = 0
                for (i in metaIds.indices) {
                    if (metaRatings[i] !in r || !allowed[metaCat[i].toInt()] || metaIds[i] == cur) continue
                    anyCount++
                    if (rnd.nextInt(anyCount) == 0) any = i
                    if (metaIds[i] !in solved) {
                        freshCount++
                        if (rnd.nextInt(freshCount) == 0) fresh = i
                    }
                }
                val pick = if (fresh >= 0) fresh else any
                if (pick >= 0) metaIds[pick] else null
            }
            val p = id?.let { runCatching { dao.byId(it) }.getOrNull() }
            if (p != null) show(p)
            else state.update { it.copy(empty = true, tone = Tone.BAD, message = "Aucun problème ne correspond à ces filtres. Élargissez la sélection dans le catalogue.") }
        }
    }

    /** Ouvre un problème par son identifiant Lichess (ex. « 00sHx ») ; renvoie faux s'il n'est pas embarqué. */
    fun openById(raw: String, onResult: (Boolean) -> Unit) {
        val id = raw.trim().removePrefix("#").substringAfterLast('/').trim()
        viewModelScope.launch {
            val p = if (id.isEmpty()) null else runCatching { dao.byId(id) ?: dao.byIdIgnoreCase(id) }.getOrNull()
            if (p != null) show(p)
            onResult(p != null)
        }
    }

    /** Révèle le thème (et son conseil) avant la résolution. */
    fun revealTheme() = state.update { it.copy(themeRevealed = true) }

    /** Change la difficulté sans tirer de problème : le filtre s'applique au prochain tirage aléatoire. */
    fun setBand(band: Int) {
        state.update { it.copy(band = band) }
        viewModelScope.launch { duck.prefs.setPuzzleFilter(band, state.value.theme) }
    }

    fun setFilter(band: Int, theme: String) {
        state.update { it.copy(band = band, theme = theme) }
        viewModelScope.launch { duck.prefs.setPuzzleFilter(band, theme) }
        random()
    }

    /** Calcule, pour une difficulté, le nombre de problèmes (et de résolus) par thème. */
    fun loadCounts(band: Int) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.Default) {
                ensureMeta()
                val solved = solvedIds.value
                val range = PuzzleThemes.bandRange(band)
                val n = PuzzleThemes.all.size
                val total = IntArray(n + 1)
                val done = IntArray(n + 1)
                for (i in metaIds.indices) {
                    if (metaRatings[i] !in range) continue
                    val isSolved = metaIds[i] in solved
                    total[n]++
                    if (isSolved) done[n]++
                    val b = metaCat[i].toInt()
                    total[b]++
                    if (isSolved) done[b]++
                }
                val map = HashMap<String, ThemeCount>()
                PuzzleThemes.all.forEachIndexed { i, t -> map[t.key] = ThemeCount(total[i], done[i]) }
                map[""] = ThemeCount(total[n], done[n])
                for (g in PuzzleThemes.groups) {
                    val idx = PuzzleThemes.all.indices.filter { PuzzleThemes.all[it].group == g }
                    map[PuzzleThemes.groupKey(g)] = ThemeCount(idx.sumOf { total[it] }, idx.sumOf { done[it] })
                }
                map
            }
            counts.value = res
        }
    }

    fun back() {
        val s = state.value
        val line = s.line ?: return
        if (s.refute != null || s.cursor <= 0) return
        val t = s.cursor - 1
        state.update { it.copy(cursor = t, anim = undoAnim(line.positions[t], line.moves[t]), hintStage = 0) }
    }

    fun forward() {
        val s = state.value
        val line = s.line ?: return
        if (s.refute != null || s.cursor >= s.progress) return
        state.update { it.copy(cursor = s.cursor + 1, anim = moveAnim(line.positions[s.cursor], line.moves[s.cursor]), hintStage = 0) }
    }

    fun canPlay(s: PuzzleState): Boolean {
        val line = s.line ?: return false
        return s.refute == null && s.cursor >= 1 && s.cursor < line.size && s.cursor <= s.progress &&
            line.positions[s.cursor].whiteToMove == s.playerWhite
    }

    fun hint() {
        val s = state.value
        if (!canPlay(s)) return
        val stage = (s.hintStage + 1).coerceAtMost(2)
        state.update {
            it.copy(
                hintStage = stage, tone = Tone.GOLD,
                message = if (stage == 1) "Indice 1/2 : la pièce à jouer est surlignée en vert." else "Indice 2/2 : la flèche montre le coup à jouer.",
            )
        }
    }

    /** Coup du joueur. Renvoie 1 (bon coup), 2 (problème résolu) ou -1 (erreur). */
    fun onMove(m: Move): Int {
        val s = state.value
        val line = s.line ?: return 0
        val p = s.puzzle ?: return 0
        if (!canPlay(s)) return 0
        job?.cancel()
        val cur = s.cursor
        val pos = line.positions[cur]
        val expected = line.moves[cur]
        val after = pos.play(m)
        if (m == expected || after.isCheckmate) {
            // Comme sur Lichess, tout mat immédiat est accepté.
            val newLine = if (m == expected) line else Line(line.startFen, line.moves.take(cur) + m)
            val finished = cur + 1 >= newLine.size
            state.update {
                it.copy(
                    line = newLine, cursor = cur + 1, progress = cur + 1, anim = moveAnim(pos, m), hintStage = 0,
                    solved = it.solved || finished, tone = Tone.GOOD,
                    message = if (finished) solvedMessage(p) else "${newLine.sans[cur]} — bon coup ! La suite arrive…",
                )
            }
            if (finished) {
                viewModelScope.launch { dao.markSolved(PuzzleProgress(p.id, System.currentTimeMillis())) }
                return 2
            }
            job = viewModelScope.launch {
                delay(600)
                val reply = newLine.moves[cur + 1]
                state.update {
                    it.copy(
                        cursor = cur + 2, progress = cur + 2, anim = moveAnim(newLine.positions[cur + 1], reply),
                        message = "Bien vu. L'adversaire répond ${newLine.sans[cur + 1]} : à vous de continuer.",
                    )
                }
            }
            return 1
        }
        refute(pos, m, after, cur)
        return -1
    }

    private fun solvedMessage(p: Puzzle): String {
        val themes = p.themes.trim().split(' ').mapNotNull { PuzzleThemes.byKey[it]?.name }.take(4)
        return "Problème résolu ! " + (if (themes.isNotEmpty()) "Thèmes : ${themes.joinToString(", ")}. " else "") +
            "Touchez « Problème aléatoire » pour le suivant."
    }

    /**
     * Mauvais coup : exécuté à t = 0, réfutation adverse à t = 650 ms (flèche rouge), maintenue
     * [REFUTE_HOLD] ms avec un compte à rebours visible, puis retour automatique à la position d'avant l'erreur.
     * « Réessayer » annule immédiatement.
     */
    private fun refute(pos: Position, m: Move, after: Position, cur: Int) {
        val san = pos.san(m)
        val oppName = if (pos.whiteToMove) "Noirs" else "Blancs"
        state.update {
            it.copy(
                progress = minOf(it.progress, cur).coerceAtLeast(cur), cursor = cur, hintStage = 0, tone = Tone.BAD, anim = moveAnim(pos, m),
                refute = Refutation(after, m, null, null), refuteUntil = 0L, refuteHold = 0L,
                message = "$san n'est pas la solution. Regardez la réponse des $oppName…",
            )
        }
        job = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            if (after.legalMoves.isEmpty()) {
                state.update { it.copy(message = "$san met l'adversaire pat : la partie serait nulle alors que vous aviez bien mieux !", refuteUntil = System.currentTimeMillis() + REFUTE_HOLD, refuteHold = REFUTE_HOLD) }
                delay(REFUTE_HOLD)
                retry()
                return@launch
            }
            val search = async { withTimeoutOrNull(1800) { duck.engine.search(after.toFen(), 380) } }
            val res = search.await()
            delay((650 - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
            val reply = res?.best?.takeIf { after.isLegal(it) }
                ?: after.legalMoves.firstOrNull { it.to == m.to } ?: after.legalMoves.first()
            val replySan = after.san(reply)
            val after2 = after.play(reply)
            val score = res?.score
            val captured = after[reply.to]
            val detail = when {
                after2.isCheckmate -> " : échec et mat !"
                score != null && isMateScore(score) && score > 0 -> " et forcent le mat en ${mateIn(score)}."
                captured != 0 && pieceType(captured) != PAWN -> " et capturent votre ${PIECE_NAMES_FR[pieceType(captured)].lowercase()}."
                captured != 0 -> " et gagnent un pion."
                score != null && score > 150 -> " et prennent l'avantage."
                score != null && score > -80 -> " et égalisent : votre avantage s'est envolé."
                else -> " et se défendent : il existait un coup bien plus fort."
            }
            state.update {
                it.copy(
                    anim = moveAnim(after, reply),
                    refute = Refutation(after2, reply, Arrow(reply.from, reply.to, androidx.compose.ui.graphics.Color(0xFFEF4444)), null),
                    refuteUntil = System.currentTimeMillis() + REFUTE_HOLD, refuteHold = REFUTE_HOLD,
                    message = "Réfutation : après $san, les $oppName répondent $replySan$detail Le coup sera annulé automatiquement.",
                )
            }
            delay(REFUTE_HOLD)
            retry()
        }
    }

    /** Annule immédiatement le mauvais coup (bouton ↩️ Réessayer ou fin de la temporisation). */
    fun retry() {
        if (state.value.refute == null) return
        job?.cancel()
        state.update {
            it.copy(refute = null, anim = null, tone = Tone.INFO, refuteUntil = 0L, message = "Position rétablie : à vous de rejouer.")
        }
    }
}

/** Surligne, sans tenir compte de la casse, la partie de [text] qui correspond à [query]. */
private fun highlight(text: String, query: String, bg: Color, fg: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val at = if (query.isEmpty()) -1 else text.indexOf(query, ignoreCase = true)
    if (at >= 0) addStyle(SpanStyle(background = bg, color = fg), at, at + query.length)
}

private val frNumber: NumberFormat = NumberFormat.getIntegerInstance(Locale.FRANCE)
fun fr(n: Int): String = frNumber.format(n).replace(' ', ' ').replace(' ', ' ')

@Composable
fun PuzzlesScreen(vm: PuzzlesViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val solved by vm.solvedIds.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val context = LocalContext.current
    var catalog by remember { mutableStateOf(false) }
    var shareChoice by remember { mutableStateOf(false) }
    var recentSheet by remember { mutableStateOf(false) }
    val recents by vm.recents.collectAsStateWithLifecycle()
    val line = s.line
    val p = s.puzzle

    if (p == null || line == null) {
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

    ModuleLayout(
        bottomMin = 170.dp,
        top = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DuckButton(
                    "${fr(solved.size)} / ${fr(DuckDatabase.PUZZLE_COUNT)}", { catalog = true }, Modifier.weight(1f),
                    accent = c.blue, icon = Icons.Rounded.Extension,
                    contentDescription = "Catalogue des problèmes : ${fr(solved.size)} résolus sur ${fr(DuckDatabase.PUZZLE_COUNT)}",
                )
                DuckIconButton(Icons.Rounded.Casino, "Problème aléatoire", vm::random, accent = c.gold, filled = s.solved || s.empty)
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
                arrows = listOfNotNull(r?.arrow, if (next != null && s.hintStage >= 2) Arrow(next.from, next.to, c.green) else null),
                marks = if (next != null && s.hintStage >= 1) listOf(next.from to c.green) else emptyList(),
                onMove = { m ->
                    when (vm.onMove(m)) {
                        2 -> fb?.success()
                        -1 -> fb?.error()
                    }
                },
            )
        },
        bottom = {
            if (s.empty) {
                EmptyState(
                    Icons.Rounded.SearchOff, "Plus aucun problème", "Aucun problème ne correspond à ces filtres.",
                    Modifier.padding(top = 6.dp), actionLabel = "Ouvrir le catalogue", onAction = { catalog = true },
                    secondaryLabel = "Réinitialiser les filtres", onSecondary = { vm.setFilter(0, "") },
                )
                Spacer(Modifier.weight(1f))
            } else {
                // Anti-spoiler : en mode libre (tous les thèmes ou une famille), le thème et son conseil
                // restent masqués jusqu'à la résolution, sauf demande explicite.
                val showTip = visible && s.refute == null && !s.solved && s.tone == Tone.INFO
                FeedbackCard(
                    s.message + if (showTip) "\n\nConseil : ${type.tip}" else "",
                    s.tone, Modifier.padding(top = 6.dp).weight(1f),
                    title = if (visible) "Type de problème : ${type.name}" else "Thème masqué (mode libre)",
                    action = if (!visible) ({
                        DuckButton("Révéler le thème", vm::revealTheme, icon = Icons.Rounded.Visibility, accent = c.blue, variant = ButtonVariant.OUTLINED)
                    }) else null,
                )
                if (s.refute != null && s.refuteUntil > 0) RefuteCountdown(s.refuteUntil, s.refuteHold)
                EqualRow(Modifier.padding(vertical = 6.dp)) {
                    DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Coup précédent", vm::back, enabled = s.refute == null && s.cursor > 0)
                    if (s.refute != null) DuckButton("Réessayer", vm::retry, Modifier.weight(1f), accent = c.red, filled = true, icon = Icons.AutoMirrored.Rounded.Undo)
                    else if (s.solved) DuckButton("Problème aléatoire", vm::random, Modifier.weight(1f), accent = c.gold, filled = true, icon = Icons.Rounded.Casino)
                    else DuckButton("Indice ${(s.hintStage + 1).coerceAtMost(2)}/2", vm::hint, Modifier.weight(1f), enabled = vm.canPlay(s), accent = c.green, icon = Icons.Rounded.Lightbulb)
                    DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Coup suivant", vm::forward, enabled = s.refute == null && s.cursor < s.progress)
                }
            }
        },
    )

    if (catalog) PuzzleCatalog(vm, s, onClose = { catalog = false; vm.clearSearch() })
    if (recentSheet) {
        DuckSheet("Derniers problèmes joués", { recentSheet = false }) {
            for (r in recents) {
                val done = r.id in solved
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                        .clickable(role = Role.Button) { recentSheet = false; vm.openPuzzle(r) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        // Le thème d'un problème non résolu reste masqué (anti-spoiler).
                        Text("#${r.id} · ${if (done) PuzzleThemes.primary(r.themes).name else "thème masqué"}", color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text("${PuzzleThemes.BANDS[PuzzleThemes.bandOf(r.rating)]} · ${r.rating}", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.greenText, modifier = Modifier.padding(end = 4.dp).size(18.dp))
                    Text(if (done) "Résolu" else "À résoudre", color = if (done) c.greenText else c.goldText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (shareChoice) {
        var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
        DuckSheet("Exporter en image", { shareChoice = false }) {
            var style by remember { mutableStateOf(ShareStyle.load(context)) }
            val setStyle = { v: ShareStyle -> style = v; ShareStyle.save(context, v) }
            // Aperçus calculés hors du fil principal, recalculés à chaque changement de réglage.
            val problemPreview by produceState<ImageBitmap?>(null, style, p.id) {
                value = withContext(Dispatchers.Default) { ShareCard.render(context, p, line.positions[1], s.playerWhite, line.moves[0], style).asImageBitmap() }
            }
            val solutionPreview by produceState<ImageBitmap?>(null, style, p.id) {
                value = withContext(Dispatchers.Default) { ShareCard.renderSolution(context, p, style).asImageBitmap() }
            }
            ExportOption("Format", listOf("Portrait 4:5" to (style.format == ShareFormat.PORTRAIT), "Carré 1:1" to (style.format == ShareFormat.SQUARE))) { i ->
                setStyle(style.copy(format = if (i == 0) ShareFormat.PORTRAIT else ShareFormat.SQUARE))
            }
            Text(
                if (style.format == ShareFormat.PORTRAIT) "Idéal pour WhatsApp et les messageries : affiché en entier, sans recadrage."
                else "Idéal pour une publication Instagram ou un aperçu compact.",
                color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp),
            )
            ExportOption("Fond", listOf("Sombre" to !style.light, "Clair" to style.light)) { i -> setStyle(style.copy(light = i == 1)) }
            ExportCard(
                "Le problème", "L'énoncé seul : le défi, l'échiquier, la difficulté et un indice — sans la solution.", c.blue, Icons.Rounded.Extension, problemPreview,
                onShare = { shareChoice = false; ShareCard.share(context, p, line.positions[1], s.playerWhite, line.moves[0], style) },
                onSave = { status = ShareCard.saveProblem(context, p, line.positions[1], s.playerWhite, line.moves[0], style)?.let { true to it } ?: (false to "L'image n'a pas pu être enregistrée.") },
            )
            ExportCard(
                "La solution", "Une carte par coup : flèches, échecs (+), mat (#), pièces prises et conclusion.", c.green, Icons.Rounded.CheckCircle, solutionPreview, masked = !s.solved,
                onShare = { shareChoice = false; ShareCard.shareSolution(context, p, style) },
                onSave = { status = ShareCard.saveSolution(context, p, style)?.let { true to it } ?: (false to "L'image n'a pas pu être enregistrée.") },
            )
            status?.let { (ok, msg) ->
                Row(Modifier.padding(top = 4.dp, bottom = 6.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, contentDescription = null, tint = if (ok) c.greenText else c.redText, modifier = Modifier.size(18.dp))
                    Text(msg, color = if (ok) c.greenText else c.redText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Compte à rebours visuel avant l'annulation automatique d'une réfutation. */
@Composable
private fun RefuteCountdown(until: Long, hold: Long) {
    val c = LocalDuck.current
    val progress = remember(until) { Animatable(((until - System.currentTimeMillis()).toFloat() / hold).coerceIn(0f, 1f)) }
    LaunchedEffect(until) {
        val left = (until - System.currentTimeMillis()).coerceAtLeast(0)
        progress.animateTo(0f, tween(left.toInt(), easing = LinearEasing))
    }
    LinearProgressIndicator(
        progress = { progress.value }, color = c.redRole.fill, trackColor = c.surfaceHi,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
            .semantics { contentDescription = "Annulation automatique du coup imminente" },
    )
}

/** Choix segmenté d'un réglage d'export (format, fond). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportOption(label: String, options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(72.dp))
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            options.forEachIndexed { i, (text, selected) ->
                SegmentedButton(
                    selected = selected, onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = c.goldRole.fill, activeContentColor = c.goldRole.onFill, activeBorderColor = c.outlineStrong,
                        inactiveContainerColor = c.surface, inactiveContentColor = c.text, inactiveBorderColor = c.outlineStrong,
                    ),
                ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
        }
    }
}

/** Une image exportable : aperçu, titre, description et deux actions, partager ou enregistrer. */
@Composable
private fun ExportCard(
    title: String, description: String, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, preview: ImageBitmap?,
    masked: Boolean = false, onShare: () -> Unit, onSave: () -> Unit,
) {
    val c = LocalDuck.current
    // Anti-spoiler : l'aperçu de la solution reste caché tant que le problème n'est pas résolu.
    var reveal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(96.dp).height(120.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceHi), contentAlignment = Alignment.Center) {
                if (masked && !reveal) {
                    Column(
                        Modifier.fillMaxSize().clickable(onClickLabel = "Afficher l'aperçu de la solution") { reveal = true },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = c.muted, modifier = Modifier.size(24.dp))
                        Text("Toucher pour voir", color = c.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                    }
                } else if (preview != null) {
                    androidx.compose.foundation.Image(preview, contentDescription = "Aperçu : $title", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    CircularProgressIndicator(Modifier.size(22.dp), color = c.role(accent).fill, strokeWidth = 2.dp)
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = c.role(accent).text, modifier = Modifier.size(20.dp))
                    Text(title, color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                }
                Text(description, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
        EqualRow(gap = 8.dp, modifier = Modifier.padding(top = 10.dp)) {
            DuckButton("Partager", onShare, Modifier.weight(1f), accent = accent, filled = true, icon = Icons.Rounded.Share, contentDescription = "Partager : $title")
            DuckButton("Enregistrer", onSave, Modifier.weight(1f), accent = accent, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.Download, contentDescription = "Enregistrer dans la galerie : $title")
        }
    }
}

private fun norm(t: String) = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PuzzleCatalog(vm: PuzzlesViewModel, s: PuzzleState, onClose: () -> Unit) {
    val c = LocalDuck.current
    val counts by vm.counts.collectAsStateWithLifecycle()
    LaunchedEffect(s.band) { vm.loadCounts(s.band) }
    var query by remember { mutableStateOf("") }
    var notFound by remember { mutableStateOf(false) }
    val sugg by vm.suggestions.collectAsStateWithLifecycle()
    val open = { vm.openById(query) { ok -> if (ok) onClose() else notFound = true } }
    // Familles dépliées : par défaut, celle du thème en cours.
    val expanded = remember {
        androidx.compose.runtime.mutableStateListOf<String>().apply { PuzzleThemes.byKey[s.theme]?.group?.let { add(it) } }
    }
    val q = norm(query.trim().removePrefix("#"))
    val themeMatches = if (q.length >= 2) PuzzleThemes.all.filter { norm(it.name).contains(q) || norm(it.goal).contains(q) } else emptyList()
    DuckSheet("Catalogue des problèmes", onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(24); notFound = false; vm.search(query) }, singleLine = true,
                label = { Text("Identifiant Lichess ou thème") },
                placeholder = { Text("ex. 00sHx, fourchette…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                isError = notFound,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { open() }),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = c.text, unfocusedTextColor = c.text, focusedBorderColor = c.goldText, unfocusedBorderColor = c.outlineStrong,
                    cursorColor = c.goldText, errorTextColor = c.text, focusedLabelColor = c.goldText, unfocusedLabelColor = c.muted,
                    focusedLeadingIconColor = c.muted, unfocusedLeadingIconColor = c.muted,
                ),
            )
            DuckButton("Ouvrir", { open() }, enabled = query.isNotBlank(), accent = c.gold, filled = true, height = 56.dp, contentDescription = "Ouvrir le problème « ${query.trim()} »")
        }
        if (sugg.items.isNotEmpty() && query.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)) {
                for (item in sugg.items) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { vm.openSuggestion(item.id) { onClose() } }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(highlight("#${item.id}", sugg.query, c.goldRole.fill, c.goldRole.onFill), color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text(item.type, color = c.blueText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.rating}", color = c.muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (sugg.more) Text("…", color = c.muted, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
            }
        }
        if (themeMatches.isNotEmpty()) {
            SectionTitle("Thèmes correspondants")
            for (t in themeMatches) ThemeRow(t.name, t.goal, counts[t.key], s.theme == t.key) { vm.setFilter(s.band, t.key); onClose() }
        }
        if (notFound) Text(
            "Aucun problème « ${query.trim()} » parmi les ${fr(DuckDatabase.PUZZLE_COUNT)} embarqués. Vérifiez l'identifiant (majuscules et minuscules comptent).",
            color = c.redText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
        )
        SectionTitle("1. Difficulté")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PuzzleThemes.BANDS.forEachIndexed { i, label -> DuckChip(label, s.band == i, { vm.setBand(i) }) }
        }
        Text(
            "Enregistrée immédiatement : elle s'applique au prochain problème aléatoire.",
            color = c.muted, style = MaterialTheme.typography.bodySmall,
        )
        SectionTitle("2. Thème — 50 thèmes officiels Lichess")
        Text(
            "Chaque problème est rangé dans un seul thème : son idée la plus caractéristique. En mode libre, le thème reste masqué jusqu'à la résolution.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp),
        )
        ThemeRow("Tous les thèmes", "Un mélange de toutes les idées, sans indication à l'avance.", counts[""], s.theme.isEmpty(), Icons.Rounded.Shuffle) {
            vm.setFilter(s.band, ""); onClose()
        }
        // Familles en accordéon : toucher la ligne choisit toute la famille, la flèche déplie ses thèmes.
        for (g in PuzzleThemes.groups) {
            val key = PuzzleThemes.groupKey(g)
            val isOpen = g in expanded
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ThemeRow(g, PuzzleThemes.GROUP_GOALS[g] ?: "", counts[key], s.theme == key, Icons.Rounded.Folder) { vm.setFilter(s.band, key); onClose() }
                }
                DuckIconButton(
                    if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    if (isOpen) "Replier les thèmes : $g" else "Déplier les thèmes : $g",
                    { if (isOpen) expanded.remove(g) else expanded.add(g) },
                )
            }
            AnimatedVisibility(isOpen) {
                Column(Modifier.padding(start = 16.dp)) {
                    for (t in PuzzleThemes.all.filter { it.group == g }) {
                        ThemeRow(t.name, t.goal, counts[t.key], s.theme == t.key) { vm.setFilter(s.band, t.key); onClose() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(name: String, goal: String, count: ThemeCount?, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val c = LocalDuck.current
    val enabled = count == null || count.total > 0
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.surfaceHi else c.surface)
            .then(if (selected) Modifier.border(1.dp, c.goldText, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .semantics { this.selected = selected; if (count != null) stateDescription = "${count.solved} résolus sur ${count.total}" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.goldText, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (enabled) c.text else c.muted, style = MaterialTheme.typography.titleSmall)
            Text(goal, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (count != null) {
            Text("${fr(count.solved)} / ${fr(count.total)}", color = if (count.solved > 0) c.greenText else c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
