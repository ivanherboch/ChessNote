package com.ivanherboch.chessduck.feature

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.ivanherboch.chessduck.ui.captureBadge
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
) {
    val playerWhite: Boolean get() = line?.positions?.getOrNull(1)?.whiteToMove ?: true
}

class ThemeCount(val total: Int, val solved: Int)

/** Suggestion de recherche : identifiant Lichess + type de problème. */
data class Suggestion(val id: String, val type: String, val rating: Int)
data class Suggestions(val query: String = "", val items: List<Suggestion> = emptyList(), val more: Boolean = false)

class PuzzlesViewModel(app: Application) : AndroidViewModel(app) {
    private val duck = app as ChessDuckApp
    private val dao = duck.db.puzzles()
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
    private var metaMasks = LongArray(0)
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
        viewModelScope.launch(Dispatchers.Default) { ensureMeta() }
    }

    private suspend fun ensureMeta() = metaLock.withLock {
        if (metaIds.isNotEmpty()) return@withLock
        val meta = runCatching { dao.allMeta() }.getOrDefault(emptyList())
        val idx = PuzzleThemes.all.withIndex().associate { it.value.key to it.index }
        metaRatings = IntArray(meta.size) { meta[it].rating }
        metaMasks = LongArray(meta.size) { i ->
            var m = 0L
            for (t in meta[i].themes.split(' ')) idx[t]?.let { m = m or (1L shl it) }
            m
        }
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

    private fun turnLabel(white: Boolean) = if (white) "⬜ Trait aux Blancs" else "⬛ Trait aux Noirs"

    private fun show(p: Puzzle) {
        job?.cancel()
        val line = runCatching { Line.fromUci(p.fen, p.moves) }.getOrNull() ?: return
        val white = line.positions[1].whiteToMove
        state.update {
            it.copy(
                puzzle = p, line = line, progress = 0, cursor = 0, solved = false, anim = null, hintStage = 0, refute = null, empty = false,
                message = "${turnLabel(white)}. 💡 Conseil : ${PuzzleThemes.primary(p.themes).tip}", tone = Tone.INFO,
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
            val p = runCatching {
                dao.randomUnsolved(r.first, r.last, s.theme, cur) ?: dao.randomAny(r.first, r.last, s.theme, cur)
            }.getOrNull()
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
                    var m = metaMasks[i]
                    while (m != 0L) {
                        val b = java.lang.Long.numberOfTrailingZeros(m)
                        total[b]++
                        if (isSolved) done[b]++
                        m = m and (m - 1)
                    }
                }
                val map = HashMap<String, ThemeCount>()
                PuzzleThemes.all.forEachIndexed { i, t -> map[t.key] = ThemeCount(total[i], done[i]) }
                map[""] = ThemeCount(total[n], done[n])
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
                message = if (stage == 1) "💡 Indice 1/2 : la pièce à jouer est surlignée en vert." else "💡 Indice 2/2 : la flèche montre le coup à jouer.",
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
                    message = if (finished) solvedMessage(p) else "✅ ${newLine.sans[cur]} — bon coup ! La suite arrive…",
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
                        message = "✅ Bien vu. L'adversaire répond ${newLine.sans[cur + 1]} : à vous de continuer.",
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
        return "✅ Problème résolu ! " + (if (themes.isNotEmpty()) "Thèmes : ${themes.joinToString(", ")}. " else "") +
            "Touchez 🎲 Aléatoire pour le suivant."
    }

    /**
     * Mauvais coup : exécuté à t = 0, réfutation adverse à t = 650 ms (flèche rouge),
     * maintenue 2,5 s, puis retour automatique à la position d'avant l'erreur (t ≈ 3200 ms).
     */
    private fun refute(pos: Position, m: Move, after: Position, cur: Int) {
        val san = pos.san(m)
        val oppName = if (pos.whiteToMove) "Noirs" else "Blancs"
        state.update {
            it.copy(
                progress = minOf(it.progress, cur).coerceAtLeast(cur), cursor = cur, hintStage = 0, tone = Tone.BAD, anim = moveAnim(pos, m),
                refute = Refutation(after, m, null, captureBadge(pos, m)),
                message = "❌ $san n'est pas la solution. Regardez la réponse des $oppName…",
            )
        }
        job = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            if (after.legalMoves.isEmpty()) {
                state.update { it.copy(message = "❌ $san met l'adversaire pat : la partie serait nulle alors que vous aviez bien mieux !") }
                delay(3200)
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
                    refute = Refutation(after2, reply, Arrow(reply.from, reply.to, androidx.compose.ui.graphics.Color(0xFFEF4444)), captureBadge(after, reply)),
                    message = "❌ Réfutation : après $san, les $oppName répondent $replySan$detail Le coup va être annulé.",
                )
            }
            delay(2550)
            retry()
        }
    }

    /** Annule immédiatement le mauvais coup (bouton ↩️ Réessayer ou fin de la temporisation). */
    fun retry() {
        if (state.value.refute == null) return
        job?.cancel()
        state.update {
            val tip = it.puzzle?.let { p -> PuzzleThemes.primary(p.themes).tip } ?: ""
            it.copy(refute = null, anim = null, tone = Tone.INFO, message = "↩️ Position rétablie, à vous de rejouer. 💡 Conseil : $tip")
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

    ModuleLayout(
        bottomMin = 140.dp,
        top = {
            EqualRow(Modifier.padding(top = 4.dp)) {
                DuckButton("🧩 ${fr(solved.size)} / ${fr(DuckDatabase.PUZZLE_COUNT)} ▾", { catalog = true }, Modifier.weight(1.45f), accent = c.blue, height = 38.dp, maxSize = 14.sp)
                DuckButton("🎲 Aléatoire", vm::random, Modifier.weight(1.05f), accent = c.gold, filled = s.solved || s.empty, height = 38.dp)
                DuckButton("🕘 Récents", { recentSheet = true }, Modifier.weight(1f), enabled = recents.isNotEmpty(), height = 38.dp)
                DuckButton("🖼️ Exporter", { shareChoice = true }, Modifier.weight(1f), enabled = p != null && line != null, height = 38.dp)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p != null) {
                    AutoText(if (s.playerWhite) "⬜ Trait aux Blancs" else "⬛ Trait aux Noirs", maxSize = 14.sp, weight = FontWeight.Bold)
                    AutoText(
                        "   ${PuzzleThemes.primary(p.themes).name} · ${PuzzleThemes.BANDS[PuzzleThemes.bandOf(p.rating)]} ${p.rating}   #${p.id}",
                        color = c.muted, maxSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        board = {
            if (line != null) {
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
                    badges = listOfNotNull(if (r != null) r.badge else if (cur > 0) captureBadge(line.positions[cur - 1], line.moves[cur - 1]) else null),
                    onMove = { m ->
                        when (vm.onMove(m)) {
                            2 -> fb?.success()
                            -1 -> fb?.error()
                        }
                    },
                )
            }
        },
        bottom = {
            FeedbackCard(
                s.message, s.tone, Modifier.padding(top = 5.dp).weight(1f), maxSize = 15.sp,
                title = p?.let { "🧩 Type de problème : ${PuzzleThemes.primary(it.themes).name}" },
            )
            EqualRow(Modifier.padding(vertical = 5.dp), gap = 5.dp) {
                DuckButton("◀ Arrière", vm::back, Modifier.weight(1f), enabled = s.refute == null && s.cursor > 0, height = 44.dp)
                if (s.refute != null) DuckButton("↩️ Réessayer", vm::retry, Modifier.weight(1.3f), accent = c.red, filled = true, height = 44.dp, maxSize = 14.sp)
                else DuckButton("💡 Indice ${(s.hintStage + 1).coerceAtMost(2)}/2", vm::hint, Modifier.weight(1.3f), enabled = vm.canPlay(s), accent = c.green, height = 44.dp, maxSize = 14.sp)
                DuckButton("Avant ▶", vm::forward, Modifier.weight(1f), enabled = s.refute == null && s.cursor < s.progress, height = 44.dp)
            }
        },
    )

    if (catalog) PuzzleCatalog(vm, s, onClose = { catalog = false; vm.clearSearch() })
    if (recentSheet) {
        DuckSheet("🕘 Derniers problèmes joués", { recentSheet = false }) {
            for (r in recents) {
                val done = r.id in solved
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
                        .clickable { recentSheet = false; vm.openPuzzle(r) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        AutoText("#${r.id} · ${PuzzleThemes.primary(r.themes).name}", maxSize = 16.sp, weight = FontWeight.SemiBold)
                        AutoText("${PuzzleThemes.BANDS[PuzzleThemes.bandOf(r.rating)]} · ${r.rating}", color = c.muted, maxSize = 13.sp)
                    }
                    AutoText(if (done) "✅ Résolu" else "À résoudre", color = if (done) c.green else c.gold, maxSize = 13.sp, weight = FontWeight.Bold)
                }
            }
        }
    }
    if (shareChoice && p != null && line != null) {
        var status by remember { mutableStateOf<String?>(null) }
        DuckSheet("🖼️ Exporter en image", { shareChoice = false }) {
            ExportCard(
                "🧩 Le problème", "L'énoncé seul : échiquier, trait, difficulté, type et conseil — sans la solution.", c.blue,
                onShare = { shareChoice = false; ShareCard.share(context, p, line.positions[1], s.playerWhite, line.moves[0]) },
                onSave = { status = ShareCard.saveProblem(context, p, line.positions[1], s.playerWhite, line.moves[0])?.let { "✅ $it" } ?: "❌ L'image n'a pas pu être enregistrée." },
            )
            ExportCard(
                "✅ La solution", "Une grille par coup : flèches des déplacements, symboles ✕ + # et pièces capturées.", c.green,
                onShare = { shareChoice = false; ShareCard.shareSolution(context, p) },
                onSave = { status = ShareCard.saveSolution(context, p)?.let { "✅ $it" } ?: "❌ L'image n'a pas pu être enregistrée." },
            )
            status?.let { AutoText(it, color = if (it.startsWith("✅")) c.green else c.red, maxSize = 13.sp, weight = FontWeight.SemiBold, maxLines = 3, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) }
        }
    }
}

/** Une image exportable : son titre, sa description et deux actions, partager ou enregistrer. */
@Composable
private fun ExportCard(title: String, description: String, accent: Color, onShare: () -> Unit, onSave: () -> Unit) {
    val c = LocalDuck.current
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp)) {
        AutoText(title, maxSize = 16.sp, weight = FontWeight.Bold)
        AutoText(description, color = c.muted, maxSize = 13.sp, maxLines = 3, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        EqualRow(gap = 8.dp) {
            DuckButton("📤 Partager", onShare, Modifier.weight(1f), accent = accent, filled = true, height = 44.dp, maxSize = 15.sp)
            DuckButton("⬇️ Enregistrer", onSave, Modifier.weight(1f), accent = accent, height = 44.dp, maxSize = 15.sp)
        }
    }
}

@Composable
private fun PuzzleCatalog(vm: PuzzlesViewModel, s: PuzzleState, onClose: () -> Unit) {
    val c = LocalDuck.current
    val counts by vm.counts.collectAsStateWithLifecycle()
    var band by remember { mutableIntStateOf(s.band) }
    LaunchedEffect(band) { vm.loadCounts(band) }
    var query by remember { mutableStateOf("") }
    var notFound by remember { mutableStateOf(false) }
    val sugg by vm.suggestions.collectAsStateWithLifecycle()
    val open = { vm.openById(query) { ok -> if (ok) onClose() else notFound = true } }
    DuckSheet("🧩 Catalogue des problèmes", onClose) {
        SectionTitle("RECHERCHER PAR IDENTIFIANT LICHESS")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(12); notFound = false; vm.search(query) }, singleLine = true,
                placeholder = { AutoText("ex. 00sHx", color = c.muted, maxSize = 15.sp) },
                isError = notFound,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { open() }),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = c.text, unfocusedTextColor = c.text, focusedBorderColor = c.gold, unfocusedBorderColor = c.outline,
                    cursorColor = c.gold, errorTextColor = c.text,
                ),
            )
            DuckButton("🔎 Ouvrir", { open() }, Modifier.width(110.dp), enabled = query.isNotBlank(), accent = c.gold, filled = true, height = 52.dp, maxSize = 15.sp)
        }
        if (sugg.items.isNotEmpty() && query.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)) {
                for (item in sugg.items) {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openSuggestion(item.id) { onClose() } }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(highlight("#${item.id}", sugg.query, c.gold, c.onAccent), style = TextStyle(color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        AutoText(item.type, color = c.blue, maxSize = 14.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        AutoText("${item.rating}", color = c.muted, maxSize = 13.sp)
                    }
                }
                if (sugg.more) AutoText("…", color = c.muted, maxSize = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
            }
        } else if (query.isNotBlank() && sugg.query.equals(query.trim().removePrefix("#"), ignoreCase = true) && !notFound) {
            AutoText("Aucun identifiant ne contient « ${query.trim()} ».", color = c.muted, maxSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (notFound) AutoText(
            "Aucun problème « ${query.trim()} » parmi les 50 000 embarqués. Vérifiez l'identifiant (majuscules et minuscules comptent).",
            color = c.red, maxSize = 13.sp, maxLines = 3, modifier = Modifier.padding(top = 6.dp),
        )
        SectionTitle("1. DIFFICULTÉ")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PuzzleThemes.BANDS.forEachIndexed { i, label ->
                DuckChip(label, band == i, { band = i }, Modifier.weight(if (i == 2) 1.5f else if (i == 0) 0.8f else 1.1f), accent = c.gold)
            }
        }
        SectionTitle("2. THÈME — 50 thèmes officiels Lichess")
        ThemeRow("🎯 Tous les thèmes", "Un mélange de toutes les idées tactiques.", counts[""], s.theme.isEmpty() && s.band == band) {
            vm.setFilter(band, ""); onClose()
        }
        for (g in PuzzleThemes.groups) {
            SectionTitle(g.uppercase())
            for (t in PuzzleThemes.all.filter { it.group == g }) {
                ThemeRow(t.name, t.goal, counts[t.key], s.theme == t.key && s.band == band) { vm.setFilter(band, t.key); onClose() }
            }
        }
    }
}

@Composable
private fun ThemeRow(name: String, goal: String, count: ThemeCount?, selected: Boolean, onClick: () -> Unit) {
    val c = LocalDuck.current
    val enabled = count == null || count.total > 0
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.surfaceHi else c.surface)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            AutoText(name, color = if (enabled) c.text else c.muted, maxSize = 15.sp, weight = FontWeight.SemiBold)
            AutoText(goal, color = c.muted, maxSize = 12.sp, maxLines = 3)
        }
        if (count != null) {
            AutoText("${fr(count.solved)} / ${fr(count.total)}", color = if (count.solved > 0) c.green else c.muted, maxSize = 12.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
