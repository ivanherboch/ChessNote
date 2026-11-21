package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.data.PuzzleHit
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.data.PuzzleRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.ivanherboch.chessduck.util.runCatchingSuspend
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.ui.REFUTE_HOLD
import com.ivanherboch.chessduck.ui.Refutation
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.ui.undoAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
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
@Immutable
data class Suggestion(val id: String, val type: String, val rating: Int)

@Immutable
/** [more] : nombre de résultats au-delà de ceux affichés. */
data class Suggestions(val query: String = "", val items: List<Suggestion> = emptyList(), val more: Int = 0)

private const val AUTO_UNDO = RefutationLogic.AUTO_UNDO

class PuzzlesViewModel(
    private val prefs: SettingsRepository,
    private val dao: PuzzleRepository,
    private val engine: ChessEngine,
    /** Problème courant mémorisé pour survivre à l'arrêt du processus par Android. */
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val rnd = kotlin.random.Random(System.nanoTime())
    private val _state = MutableStateFlow(PuzzleState())
    val state: StateFlow<PuzzleState> = _state.asStateFlow()
    val solvedIds = dao.solvedIds().map { it.toHashSet() as Set<String> }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private val _counts = MutableStateFlow<Map<String, ThemeCount>>(emptyMap())
    val counts: StateFlow<Map<String, ThemeCount>> = _counts.asStateFlow()
    private val _suggestions = MutableStateFlow(Suggestions())
    val suggestions: StateFlow<Suggestions> = _suggestions.asStateFlow()
    /** Les 5 derniers problèmes joués avant le problème courant (persistés via DataStore). */
    private val _recents = MutableStateFlow<List<Puzzle>>(emptyList())
    val recents: StateFlow<List<Puzzle>> = _recents.asStateFlow()
    /** Problèmes récents dont le thème était visible quand ils ont été joués : il reste affiché dans « Récents ». */
    val knownThemes: StateFlow<Set<String>> = prefs.settings.map { it.knownThemePuzzles }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private var job: Job? = null
    /** Chargement de problème en cours (tirage, ouverture par identifiant ou suggestion) : un seul à la fois. */
    private var loadJob: Job? = null
    private var countsJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val st = prefs.settings.first()
            _state.update { it.copy(band = st.puzzleBand, theme = st.puzzleTheme) }
            val id = saved.get<String>(KEY_PUZZLE)?.takeIf { it.isNotEmpty() } ?: st.puzzleId
            val current = if (id.isNotEmpty()) runCatchingSuspend { dao.byId(id) }.getOrNull() else null
            if (current != null) show(current) else random()
        }
        viewModelScope.launch {
            prefs.settings.map { it.recentPuzzles }.distinctUntilChanged().collect { ids ->
                val others = ids.drop(1).take(5)
                val found = if (others.isEmpty()) emptyList() else runCatchingSuspend { dao.byIds(others) }.getOrDefault(emptyList())
                _recents.value = others.mapNotNull { id -> found.firstOrNull { it.id == id } }
            }
        }
        // Après une mise à jour de l'application, les nouveaux problèmes du catalogue sont importés (SQL natif).
        // Tirage, compteurs et recherche interrogent directement la base indexée : rien n'est chargé en mémoire.
        viewModelScope.launch(Dispatchers.IO) { dao.syncCatalogue() }
    }

    /**
     * Suggestions pendant la saisie : correspondance insensible à la casse sur l'identifiant,
     * classée par pertinence (identique, puis préfixe, puis contenu), limitée à 5 résultats.
     */
    fun search(raw: String) {
        searchJob?.cancel()
        val q = raw.trim().removePrefix("#").lowercase()
        if (q.isEmpty()) { _suggestions.value = Suggestions(); return }
        searchJob = viewModelScope.launch {
            val (hits, total) = runCatchingSuspend { dao.searchIds(q, 5) }.getOrDefault(emptyList<PuzzleHit>() to 0)
            _suggestions.value = Suggestions(
                raw.trim().removePrefix("#"),
                hits.map { Suggestion(it.id, (PuzzleThemes.byKey[it.primaryTheme] ?: PuzzleThemes.primary("")).name, it.rating) },
                (total - hits.size).coerceAtLeast(0),
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _suggestions.value = Suggestions()
    }

    fun openPuzzle(p: Puzzle) {
        loadJob?.cancel()
        show(p)
    }

    fun openSuggestion(id: String, onDone: () -> Unit) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runCatchingSuspend { dao.byId(id) }.getOrNull()?.let { show(it) }
            onDone()
        }
    }

    /** Conseil du thème que le joueur a choisi de travailler (si le problème le porte), sinon celui du type du problème. */
    private fun turnLabel(white: Boolean) = if (white) "Trait aux Blancs" else "Trait aux Noirs"

    private fun show(p: Puzzle) {
        job?.cancel()
        saved[KEY_PUZZLE] = p.id
        val line = runCatching { Line.fromUci(p.fen, p.moves) }.getOrNull() ?: return
        val white = line.positions[1].whiteToMove
        _state.update {
            it.copy(
                puzzle = p, line = line, progress = 0, cursor = 0, solved = false, anim = null, hintStage = 0, refute = null, empty = false,
                themeRevealed = false, message = "${turnLabel(white)} : trouvez la meilleure suite.", tone = Tone.INFO,
            )
        }
        job = viewModelScope.launch {
            prefs.pushRecentPuzzle(p.id)
            // Thème déjà affiché (filtre sur un thème précis) : il le restera dans la liste des récents.
            if (state.value.themeVisible) prefs.markThemeKnown(p.id)
            delay(600)
            _state.update { it.copy(progress = 1, cursor = 1, anim = moveAnim(line.positions[0], line.moves[0])) }
        }
    }

    /** Tire un problème non résolu correspondant aux filtres actifs. */
    fun random() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = state.value
            val r = PuzzleThemes.bandRange(s.band)
            val cur = s.puzzle?.id ?: ""
            // Tirage indexé en base : difficulté + thème principal du problème, non résolus d'abord.
            // Filtre : un thème précis, un groupe entier (tous les mats, toutes les finales…) ou tous les thèmes.
            val group = PuzzleThemes.groupOf(s.theme)
            val themes = when {
                group != null -> PuzzleThemes.all.filter { it.group == group }.map { it.key }
                s.theme.isEmpty() -> null
                else -> listOf(s.theme)
            }
            val id = runCatchingSuspend { dao.randomId(themes, r, cur, rnd) }.getOrNull()
            val p = id?.let { runCatchingSuspend { dao.byId(it) }.getOrNull() }
            if (p != null) show(p)
            else _state.update { it.copy(empty = true, tone = Tone.BAD, message = "Aucun problème ne correspond à ces filtres. Élargissez la sélection dans le catalogue.") }
        }
    }

    /** Ouvre un problème par son identifiant Lichess (ex. « 00sHx ») ; renvoie faux s'il n'est pas embarqué. */
    fun openById(raw: String, onResult: (Boolean) -> Unit) {
        val id = raw.trim().removePrefix("#").substringAfterLast('/').trim()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val p = if (id.isEmpty()) null else runCatchingSuspend { dao.byId(id) ?: dao.byIdIgnoreCase(id) }.getOrNull()
            if (p != null) show(p)
            onResult(p != null)
        }
    }

    /** Révèle le thème (et son conseil) avant la résolution. */
    fun revealTheme() {
        _state.update { it.copy(themeRevealed = true) }
        state.value.puzzle?.id?.let { id -> viewModelScope.launch { prefs.markThemeKnown(id) } }
    }

    /** Change la difficulté sans tirer de problème : le filtre s'applique au prochain tirage aléatoire. */
    fun setBand(band: Int) {
        _state.update { it.copy(band = band) }
        viewModelScope.launch { prefs.setPuzzleFilter(band, state.value.theme) }
    }

    /** « S'entraîner sur ce thème » (Académie) : filtre sur le thème et la tranche, puis tire un problème. */
    fun train(theme: String, band: Int) {
        setFilter(band, theme)
        random()
    }

    fun setFilter(band: Int, theme: String) {
        _state.update { it.copy(band = band, theme = theme) }
        viewModelScope.launch { prefs.setPuzzleFilter(band, theme) }
        random()
    }

    /** Calcule, pour une difficulté, le nombre de problèmes (et de résolus) par thème. */
    fun loadCounts(band: Int) {
        countsJob?.cancel()
        countsJob = viewModelScope.launch {
            val rows = runCatchingSuspend { dao.themeCounts(PuzzleThemes.bandRange(band)) }.getOrDefault(emptyList())
            val byTheme = rows.associateBy { it.theme }
            val map = HashMap<String, ThemeCount>()
            for (t in PuzzleThemes.all) map[t.key] = byTheme[t.key]?.let { ThemeCount(it.total, it.solved) } ?: ThemeCount(0, 0)
            map[""] = ThemeCount(rows.sumOf { it.total }, rows.sumOf { it.solved })
            for (g in PuzzleThemes.groups) {
                val keys = PuzzleThemes.all.filter { it.group == g }.map { it.key }
                map[PuzzleThemes.groupKey(g)] = ThemeCount(keys.sumOf { map.getValue(it).total }, keys.sumOf { map.getValue(it).solved })
            }
            val res: Map<String, ThemeCount> = map
            _counts.value = res
        }
    }

    fun back() {
        val s = state.value
        val line = s.line ?: return
        if (s.refute != null || s.cursor <= 0) return
        val t = s.cursor - 1
        _state.update { it.copy(cursor = t, anim = undoAnim(line.positions[t], line.moves[t]), hintStage = 0) }
    }

    fun forward() {
        val s = state.value
        val line = s.line ?: return
        if (s.refute != null || s.cursor >= s.progress) return
        _state.update { it.copy(cursor = s.cursor + 1, anim = moveAnim(line.positions[s.cursor], line.moves[s.cursor]), hintStage = 0) }
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
        _state.update {
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
            _state.update {
                it.copy(
                    line = newLine, cursor = cur + 1, progress = cur + 1, anim = moveAnim(pos, m), hintStage = 0,
                    solved = it.solved || finished, tone = Tone.GOOD,
                    message = if (finished) solvedMessage(p) else "${newLine.sans[cur]} — bon coup ! La suite arrive…",
                )
            }
            if (finished) {
                viewModelScope.launch { dao.markSolved(p.id) }
                return 2
            }
            job = viewModelScope.launch {
                delay(600)
                val reply = newLine.moves[cur + 1]
                _state.update {
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
        _state.update {
            it.copy(
                progress = minOf(it.progress, cur).coerceAtLeast(cur), cursor = cur, hintStage = 0, tone = Tone.BAD, anim = moveAnim(pos, m),
                refute = Refutation(after, m, null), refuteUntil = 0L, refuteHold = 0L,
                message = "$san n'est pas la solution. Regardez la réponse des $oppName…",
            )
        }
        missed = pos to m
        job = viewModelScope.launch {
            val r = RefutationLogic.reply(engine, pos, m)
            _state.update {
                it.copy(
                    anim = r.reply?.let { reply -> moveAnim(after, reply) } ?: it.anim,
                    refute = Refutation(r.position, r.reply ?: m, r.reply),
                    refuteUntil = System.currentTimeMillis() + REFUTE_HOLD, refuteHold = REFUTE_HOLD,
                    message = r.text + AUTO_UNDO,
                )
            }
            delay(REFUTE_HOLD)
            retry()
        }
    }

    /** Mauvais coup réfuté (position d'avant, coup) : il glisse en arrière quand la position revient. */
    private var missed: Pair<Position, Move>? = null

    /** Suspend l'annulation automatique : la réfutation reste affichée jusqu'à « Réessayer ». */
    fun holdRefutation() {
        if (state.value.refute == null || state.value.refuteUntil == 0L) return
        job?.cancel()
        _state.update { it.copy(refuteUntil = 0L, message = it.message.removeSuffix(AUTO_UNDO) + " Prenez votre temps, puis touchez Réessayer.") }
    }

    /** Annule immédiatement le mauvais coup (bouton ↩️ Réessayer ou fin de la temporisation). */
    fun retry() {
        if (state.value.refute == null) return
        job?.cancel()
        _state.update {
            it.copy(
                refute = null, anim = missed?.let { (p, m) -> undoAnim(p, m) }, tone = Tone.INFO, refuteUntil = 0L,
                message = "Position rétablie : à vous de rejouer.",
            )
        }
    }

    private companion object {
        const val KEY_PUZZLE = "puzzles_current"
    }

    /** Style d'image enregistré (format, fond), lu à l'ouverture du tiroir d'export. */
    suspend fun shareStyle(): ShareStyle = ShareStyle.from(prefs.settings.first())

    /** Enregistre le style choisi ; l'écriture va à son terme même si l'écran se ferme entre-temps. */
    fun saveShareStyle(v: ShareStyle) {
        viewModelScope.launch { withContext(NonCancellable) { prefs.setShareStyle(v.format.name, v.light) } }
    }
}
