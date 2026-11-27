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
import kotlin.math.roundToInt
import com.ivanherboch.chessduck.data.PuzzleOutcome
import com.ivanherboch.chessduck.ui.REFUTE_HOLD
import com.ivanherboch.chessduck.ui.Refutation
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.ui.undoAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    /** La flèche de l'indice 2/2 a montré un coup : le problème, même terminé, ne compte pas comme résolu. */
    val arrowUsed: Boolean = false,
    /** Le problème était déjà résolu avant cette ouverture (lu en base à l'ouverture). */
    val alreadySolved: Boolean = false,
    /** Au moins une erreur sur ce problème : « Solution » est proposée. */
    val hadError: Boolean = false,
    /** Solution dévoilée (« Solution ») : la ligne se déroule, navigable, et le problème ne compte pas comme résolu. */
    val revealed: Boolean = false,
    /** Tirage : tous les problèmes non résolus du filtre sont épuisés, un problème déjà résolu est reproposé. */
    val allSolvedInFilter: Boolean = false,
    /** ELO tactique (« Mon niveau »), mis à jour après chaque problème. */
    val puzzleElo: Int = 1200,
    /** Filtre : thèmes secondaires inclus (le thème choisi peut ne pas être le thème principal du problème). */
    val secondary: Boolean = false,
    /** Filtre de longueur (PuzzleThemes.LENGTHS). */
    val length: Int = 0,
) {
    /** Tranche de classement du tirage : la difficulté choisie, ou ± 100 autour de l'ELO tactique (« Mon niveau »). */
    val range: IntRange get() = if (band == PuzzleThemes.MY_LEVEL) PuzzleThemes.levelRange(puzzleElo) else PuzzleThemes.bandRange(band)

    /** Le filtre de thème s'applique à ce problème (thème principal, ou secondaire si l'option est active). */
    val filterMatches: Boolean get() = theme.isNotEmpty() && puzzle != null &&
        (puzzle.primaryTheme == theme || (secondary && " $theme " in puzzle.themes))

    /** Problème fini : résolu, ou solution dévoilée (recommencer, problème suivant, passerelles). */
    val over: Boolean get() = solved || revealed

    /**
     * Le thème est visible si le problème est résolu (ou sa solution dévoilée), si le joueur l'a révélé, ou si le filtre choisi est précisément le
     * thème de ce problème (un problème ouvert depuis les récents ou la recherche garde son thème masqué).
     */
    val themeVisible: Boolean get() = solved || revealed || themeRevealed || filterMatches

    val playerWhite: Boolean get() = line?.positions?.getOrNull(1)?.whiteToMove ?: true
}

class ThemeCount(val total: Int, val solved: Int)

/** Suggestion de recherche : identifiant Lichess + type de problème. */
@Immutable
data class Suggestion(val id: String, val type: String, val rating: Int)

@Immutable
/** [more] : nombre de résultats au-delà de ceux affichés. */
data class Suggestions(val query: String = "", val items: List<Suggestion> = emptyList(), val more: Int = 0)

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
    /** Nombre de problèmes résolus, compté par la base (aucun identifiant chargé en mémoire). */
    val solvedCount = dao.solvedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    /** Parmi les problèmes récents et favoris, ceux déjà résolus : coche et thème visible dans « Problèmes joués ». */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recentSolved = kotlinx.coroutines.flow.combine(prefs.settings.map { it.recentPuzzles }, dao.bookmarks()) { r, b -> (r + b.map { it.id }).distinct() }
        .distinctUntilChanged()
        .flatMapLatest { ids -> if (ids.isEmpty()) flowOf(emptyList()) else dao.solvedAmong(ids) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private val _counts = MutableStateFlow<Map<String, ThemeCount>>(emptyMap())
    val counts: StateFlow<Map<String, ThemeCount>> = _counts.asStateFlow()
    private val _suggestions = MutableStateFlow(Suggestions())
    val suggestions: StateFlow<Suggestions> = _suggestions.asStateFlow()
    /** Les derniers problèmes joués avant le problème courant (au plus [com.ivanherboch.chessduck.data.RECENT_PUZZLES], persistés via DataStore). */
    private val _recents = MutableStateFlow<List<Puzzle>>(emptyList())
    val recents: StateFlow<List<Puzzle>> = _recents.asStateFlow()
    /** Problèmes récents dont le thème était visible quand ils ont été joués : il reste affiché dans « Récents ». */
    val knownThemes: StateFlow<Set<String>> = prefs.settings.map { it.knownThemePuzzles }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    /** Le problème affiché est-il en favori (signet) ? */
    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarked: StateFlow<Boolean> = state.map { it.puzzle?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(false) else dao.bookmarked(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    /** Favoris (onglet du tiroir « Problèmes joués »). */
    val bookmarks: StateFlow<List<Puzzle>> = dao.bookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** « À refaire » : problèmes manqués ou résolus avec une erreur, avec leur date prévue. */
    val toRedo: StateFlow<List<Pair<Puzzle, Long>>> = dao.toRedo().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Bilan tactique du catalogue : ELO tactique, précision, série, thèmes les plus manqués. */
    val stats: StateFlow<TacticStats> = kotlinx.coroutines.flow.combine(dao.attempts(), state.map { it.puzzleElo }.distinctUntilChanged()) { rows, elo ->
        TacticStats.of(rows, elo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TacticStats())

    private var job: Job? = null
    /** Chargement de problème en cours (tirage, ouverture par identifiant ou suggestion) : un seul à la fois. */
    private var loadJob: Job? = null
    private var countsJob: Job? = null
    private var searchJob: Job? = null

    /** Le filtre a été changé (catalogue, « S'entraîner sur ce thème ») : celui enregistré ne doit plus l'écraser. */
    private var filterTouched = false

    init {
        // Le moteur démarre en tâche de fond : la première réfutation d'un mauvais coup n'attend pas son lancement.
        viewModelScope.launch { runCatchingSuspend { kotlinx.coroutines.withTimeoutOrNull(10_000) { engine.search(com.ivanherboch.chessduck.chess.START_FEN, 50) } } }
        // Problème de départ : un chargement comme les autres (loadJob), qu'un « S'entraîner sur ce thème » arrivé
        // entre-temps annule au lieu d'être écrasé par lui.
        loadJob = viewModelScope.launch {
            val st = prefs.settings.first()
            // ELO tactique : à la première ouverture, il part de l'ELO du Jeu Libre (borné), puis évolue seul.
            val elo = st.puzzleElo.takeIf { it > 0 } ?: st.elo.coerceIn(ELO_MIN, ELO_MAX).also { runCatchingSuspend { prefs.setPuzzleElo(it) } }
            _state.update { it.copy(puzzleElo = elo, secondary = if (filterTouched) it.secondary else st.puzzleSecondary, length = if (filterTouched) it.length else st.puzzleLength) }
            if (!filterTouched) _state.update { it.copy(band = st.puzzleBand, theme = st.puzzleTheme) }
            val id = saved.get<String>(KEY_PUZZLE)?.takeIf { it.isNotEmpty() } ?: st.puzzleId
            val current = if (id.isNotEmpty()) runCatchingSuspend { dao.byId(id) }.getOrNull() else null
            if (current != null) show(current) else drawRandom()
        }
        viewModelScope.launch {
            prefs.settings.map { it.recentPuzzles }.distinctUntilChanged().collect { ids ->
                val others = ids.drop(1).take(com.ivanherboch.chessduck.data.RECENT_PUZZLES)
                val found = if (others.isEmpty()) emptyList() else runCatchingSuspend { dao.byIds(others) }.getOrDefault(emptyList())
                _recents.value = others.mapNotNull { id -> found.firstOrNull { it.id == id } }
            }
        }
        // Après une mise à jour de l'application, les nouveaux problèmes du catalogue sont importés (SQL natif).
        // Tirage, compteurs et recherche interrogent directement la base indexée : rien n'est chargé en mémoire.
        viewModelScope.launch(Dispatchers.IO) {
            // Des problèmes ont été importés : un écran « aucun problème » se relance, et les compteurs se recalculent.
            if (runCatchingSuspend { dao.syncCatalogue() }.getOrDefault(false)) withContext(Dispatchers.Main) {
                if (state.value.empty) random()
                if (_counts.value.isNotEmpty()) loadCounts(state.value.band)
            }
        }
    }

    /**
     * Suggestions pendant la saisie : correspondance insensible à la casse sur l'identifiant,
     * classée par pertinence (identique, puis préfixe, puis contenu), limitée à 5 résultats.
     */
    fun search(raw: String) {
        searchJob?.cancel()
        val q = raw.trim().removePrefix("#").lowercase()
        // Une saisie qui ne peut pas être un identifiant (« fourchette ») ne lance aucune requête en base.
        if (q.isEmpty() || !looksLikeId(q)) { _suggestions.value = Suggestions(); return }
        searchJob = viewModelScope.launch {
            // On attend que la frappe se pose : une seule requête pour « 00s », pas une par lettre.
            delay(SEARCH_DEBOUNCE_MS)
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

    /**
     * Affiche [p] depuis le début. [allSolved] : il vient d'un tirage alors que tous les problèmes non résolus du filtre
     * sont épuisés (la carte le dit). Le trait est déjà écrit au-dessus de l'échiquier : le message ne le répète pas.
     */
    private fun show(p: Puzzle, allSolved: Boolean = false) {
        job?.cancel()
        saved[KEY_PUZZLE] = p.id
        val line = runCatching { Line.fromUci(p.fen, p.moves) }.getOrNull() ?: return
        val intro = "Après le coup de l'adversaire, trouvez la meilleure suite."
        _state.update {
            it.copy(
                puzzle = p, line = line, progress = 0, cursor = 0, solved = false, anim = null, hintStage = 0, refute = null, empty = false, arrowUsed = false,
                alreadySolved = false, themeRevealed = false, hadError = false, revealed = false, allSolvedInFilter = allSolved, tone = Tone.INFO,
                message = if (allSolved) "Vous avez résolu tous les problèmes de cette sélection : en voici un déjà résolu. Élargissez le filtre dans le catalogue pour en découvrir d'autres.\n\n$intro" else intro,
            )
        }
        // Récents, thème connu et « déjà résolu » : à part du coup d'introduction, qui ne les attend pas ; ouvrir aussitôt
        // un autre problème ne les annule pas.
        val themeShown = state.value.themeVisible
        viewModelScope.launch {
            runCatchingSuspend { prefs.pushRecentPuzzle(p.id) }
            // Thème déjà affiché (filtre sur ce thème précis) : il le restera dans la liste des récents.
            if (themeShown) runCatchingSuspend { prefs.markThemeKnown(p.id) }
        }
        viewModelScope.launch {
            val before = runCatchingSuspend { dao.solvedAmong(listOf(p.id)).first().isNotEmpty() }.getOrDefault(false)
            if (before) _state.update { if (it.puzzle?.id == p.id) it.copy(alreadySolved = true) else it }
        }
        job = viewModelScope.launch {
            delay(600)
            _state.update { it.copy(progress = 1, cursor = 1, anim = moveAnim(line.positions[0], line.moves[0])) }
        }
    }

    /** Tire un problème non résolu correspondant aux filtres actifs. */
    fun random() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { drawRandom() }
    }

    private suspend fun drawRandom() {
        val s = state.value
        val r = s.range
        val cur = s.puzzle?.id ?: ""
        // Tirage indexé en base : difficulté + thème principal du problème, non résolus d'abord.
        // Filtre : un thème précis, un groupe entier (tous les mats, toutes les finales…) ou tous les thèmes.
        val group = PuzzleThemes.groupOf(s.theme)
        val themes = when {
            group != null -> PuzzleThemes.all.filter { it.group == group }.map { it.key }
            s.theme.isEmpty() -> null
            else -> listOf(s.theme)
        }
        val id = runCatchingSuspend { dao.randomId(themes, r, cur, rnd, s.secondary, s.length) }.getOrNull()
        val p = id?.let { runCatchingSuspend { dao.byId(it) }.getOrNull() }
        // Le tirage ne repropose un problème résolu que lorsqu'il n'en reste aucun autre dans le filtre.
        val allSolved = p != null && runCatchingSuspend { dao.solvedAmong(listOf(p.id)).first().isNotEmpty() }.getOrDefault(false)
        if (p != null) show(p, allSolved)
        else _state.update { it.copy(empty = true, tone = Tone.BAD, message = "Aucun problème ne correspond à ces filtres. Élargissez la sélection dans le catalogue.") }
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
        filterTouched = true
        _state.update { it.copy(band = band) }
        viewModelScope.launch { prefs.setPuzzleFilter(band, state.value.theme) }
    }

    /** « S'entraîner sur ce thème » (Académie) : filtre sur le thème et la tranche, puis tire un problème. */
    /** « S'entraîner sur ce thème » (Académie) : le filtre est appliqué et enregistré, puis un seul problème est tiré. */
    fun train(theme: String, band: Int) = setFilter(band, theme)

    fun setFilter(band: Int, theme: String) {
        filterTouched = true
        _state.update { it.copy(band = band, theme = theme) }
        viewModelScope.launch { prefs.setPuzzleFilter(band, theme) }
        random()
    }

    /** Thèmes secondaires inclus ou non (enregistré) : s'applique au prochain tirage et aux compteurs du catalogue. */
    fun setSecondary(on: Boolean) {
        filterTouched = true
        _state.update { it.copy(secondary = on) }
        viewModelScope.launch { prefs.setPuzzleSecondary(on) }
    }

    /** Longueur de la solution (enregistrée) : s'applique au prochain tirage. */
    fun setLength(length: Int) {
        filterTouched = true
        _state.update { it.copy(length = length.coerceIn(0, PuzzleThemes.LENGTHS.size - 1)) }
        viewModelScope.launch { prefs.setPuzzleLength(state.value.length) }
    }

    /** Favori (signet) du problème affiché : ajouté ou retiré. */
    fun toggleBookmark() {
        val id = state.value.puzzle?.id ?: return
        val on = !bookmarked.value
        viewModelScope.launch { withContext(NonCancellable) { runCatchingSuspend { dao.setBookmark(id, on) } } }
    }

    /**
     * Calcule, pour une difficulté ([band] ; « Mon niveau » : autour de l'ELO tactique), le nombre de problèmes (et de
     * résolus) par thème : thème principal, ou tous les thèmes si l'option « thèmes secondaires » est active.
     */
    fun loadCounts(band: Int) {
        countsJob?.cancel()
        countsJob = viewModelScope.launch {
            val range = state.value.copy(band = band).range
            val rows = runCatchingSuspend { dao.themeCounts(range, state.value.secondary) }.getOrDefault(emptyList())
            val byTheme = rows.associateBy { it.theme }
            val map = HashMap<String, ThemeCount>()
            for (t in PuzzleThemes.all) map[t.key] = byTheme[t.key]?.let { ThemeCount(it.total, it.solved) } ?: ThemeCount(0, 0)
            // Thèmes secondaires : un problème compte dans chacun de ses thèmes, donc additionner les thèmes compterait
            // plusieurs fois le même problème. « Tous les thèmes » et les familles comptent les problèmes distincts.
            val secondary = state.value.secondary
            val all = if (secondary) runCatchingSuspend { dao.themeCounts(range, false) }.getOrDefault(emptyList()) else rows
            map[""] = ThemeCount(all.sumOf { it.total }, all.sumOf { it.solved })
            for (g in PuzzleThemes.groups) {
                val keys = PuzzleThemes.all.filter { it.group == g }.map { it.key }
                map[PuzzleThemes.groupKey(g)] = if (secondary) {
                    runCatchingSuspend { dao.linkedCount(keys, range) }.getOrNull()?.let { ThemeCount(it.total, it.solved) } ?: ThemeCount(0, 0)
                } else ThemeCount(keys.sumOf { map.getValue(it).total }, keys.sumOf { map.getValue(it).solved })
            }
            val res: Map<String, ThemeCount> = map
            _counts.value = res
        }
    }

    /**
     * « Solution » (après une erreur) : toute la ligne se déroule, coup après coup, puis reste navigable avec ‹ et ›.
     * Le problème ne compte pas comme résolu et reviendra au tirage.
     */
    fun solution() {
        val s = state.value
        val p = s.puzzle ?: return
        if (s.over || s.refute != null || !s.hadError || s.line == null) return
        job?.cancel()
        val full = runCatching { Line.fromUci(p.fen, p.moves) }.getOrNull() ?: return
        val from = s.progress.coerceIn(1, full.size)
        _state.update {
            it.copy(
                line = full, revealed = true, cursor = from, progress = from, hintStage = 0, tone = Tone.GOLD,
                message = "Solution : ${full.sans.drop(from).joinToString(" ")}. Ce problème ne compte pas comme résolu : il reviendra plus tard.",
            )
        }
        conclude(p, PuzzleOutcome.GAVE_UP)
        job = viewModelScope.launch {
            for (i in from until full.size) {
                delay(700)
                // Le joueur a reculé pour revoir un coup : la solution avance sans faire sauter l'échiquier.
                _state.update { if (it.cursor == i) it.copy(cursor = i + 1, progress = i + 1, anim = moveAnim(full.positions[i], full.moves[i])) else it.copy(progress = i + 1) }
            }
        }
    }

    /** « Recommencer » : le problème fini (résolu ou dévoilé) reprend au coup d'introduction, indices et erreurs remis à zéro. */
    fun restart() {
        val s = state.value
        if (s.over) s.puzzle?.let { show(it) }
    }

    /** Bandeau des coups : va au demi-coup [n] déjà joué (jamais au-delà de la ligne jouée). */
    fun goTo(n: Int) {
        val s = state.value
        val line = s.line ?: return
        if (s.refute != null) return
        val t = n.coerceIn(0, s.progress)
        if (t == s.cursor) return
        _state.update { it.copy(cursor = t, anim = com.ivanherboch.chessduck.ui.LinePlaybackController.transition(line, s.cursor, t), hintStage = 0) }
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
        // On ne joue qu'au bout de la ligne déjà jouée (pas depuis un coup précédent qu'on revoit), et jamais une fois résolu.
        return s.refute == null && !s.solved && !s.revealed && s.cursor >= 1 && s.cursor < line.size && s.cursor == s.progress &&
            line.positions[s.cursor].whiteToMove == s.playerWhite
    }

    fun hint() {
        val s = state.value
        if (!canPlay(s)) return
        val stage = (s.hintStage + 1).coerceAtMost(2)
        _state.update {
            it.copy(
                hintStage = stage, tone = Tone.GOLD, arrowUsed = it.arrowUsed || stage == 2,
                message = if (stage == 1) "Indice 1/2 : la pièce à jouer est en vert. Montrer le coup : problème non compté."
                else "Indice 2/2 : la flèche montre le coup à jouer. Ce problème ne comptera pas comme résolu.",
            )
        }
    }

    /** Coup du joueur. Renvoie 1 (bon coup), 2 (problème résolu), 3 (terminé avec la flèche de l'indice, non compté) ou -1 (erreur). */
    fun onMove(m: Move): Int {
        val s = state.value
        val line = s.line ?: return 0
        val p = s.puzzle ?: return 0
        if (!canPlay(s)) return 0
        job?.cancel()
        val cur = s.cursor
        val pos = line.positions[cur]
        if (!pos.isLegal(m)) return 0
        val expected = line.moves[cur]
        val after = pos.play(m)
        if (m == expected || after.isCheckmate) {
            // Comme sur Lichess, tout mat immédiat est accepté.
            val newLine = if (m == expected) line else Line(line.startFen, line.moves.take(cur) + m)
            val finished = cur + 1 >= newLine.size
            _state.update {
                it.copy(
                    line = newLine, cursor = cur + 1, progress = cur + 1, anim = moveAnim(pos, m), hintStage = 0,
                    solved = it.solved || finished, tone = if (finished && it.arrowUsed) Tone.GOLD else Tone.GOOD,
                    message = if (finished) solvedMessage(p, it.arrowUsed, it.alreadySolved) else "${newLine.sans[cur]} — bon coup ! La suite arrive…",
                )
            }
            if (finished) {
                // Comme dans les séries de l'Académie : résolu après une erreur, il compte ; avec la flèche de l'indice, non.
                val st = state.value
                conclude(p, if (st.arrowUsed) PuzzleOutcome.GAVE_UP else if (st.hadError) PuzzleOutcome.WITH_ERROR else PuzzleOutcome.CLEAN)
                return if (st.arrowUsed) 3 else 2
            }
            job = viewModelScope.launch {
                delay(600)
                val reply = newLine.moves[cur + 1]
                _state.update {
                    // Le joueur a reculé pour revoir la position entre-temps : la réponse s'ajoute sans faire sauter l'échiquier.
                    val here = it.cursor == cur + 1
                    it.copy(
                        cursor = if (here) cur + 2 else it.cursor, progress = cur + 2,
                        anim = if (here) moveAnim(newLine.positions[cur + 1], reply) else it.anim,
                        message = "Bien vu. L'adversaire répond ${newLine.sans[cur + 1]} : à vous de continuer.",
                    )
                }
            }
            return 1
        }
        refute(pos, m, after, cur)
        return -1
    }

    private fun solvedMessage(p: Puzzle, arrow: Boolean, already: Boolean): String {
        val themes = p.themes.trim().split(' ').mapNotNull { PuzzleThemes.byKey[it]?.name }.take(4)
        return when {
            arrow && already -> "Terminé avec la flèche de l'indice. Vous aviez déjà résolu ce problème : il reste compté comme résolu. "
            arrow -> "Terminé avec la flèche de l'indice : ce problème ne compte pas comme résolu, il reviendra plus tard. "
            already -> "Problème résolu ! (déjà résolu auparavant) "
            else -> "Problème résolu ! "
        } + (if (themes.isNotEmpty()) "Thèmes : ${themes.joinToString(", ")}. " else "") +
            "Touchez « Problème aléatoire » pour le suivant."
    }

    /**
     * Mauvais coup : exécuté à t = 0, réfutation adverse à t = 650 ms (flèche rouge), maintenue
     * [REFUTE_HOLD] ms avec un compte à rebours visible, puis retour automatique à la position d'avant l'erreur.
     * « Réessayer » annule immédiatement.
     */
    private fun refute(pos: Position, m: Move, after: Position, cur: Int) {
        // Première erreur sur ce problème : l'ELO tactique compte un échec (une seule fois par problème).
        state.value.puzzle?.let { rate(it, 0.0) }
        _state.update {
            it.copy(
                cursor = cur, hintStage = 0, tone = Tone.BAD, anim = moveAnim(pos, m), hadError = true,
                refute = Refutation(after, m, null), refuteUntil = 0L, refuteHold = 0L,
                message = RefutationLogic.intro(pos, m),
            )
        }
        missed = pos to m
        job = viewModelScope.launch {
            RefutationLogic.play(engine, pos, m) { refute, anim, message, until ->
                _state.update { it.copy(anim = anim ?: it.anim, refute = refute, refuteUntil = until, refuteHold = REFUTE_HOLD, message = message) }
            }
            retry()
        }
    }

    /** Problèmes déjà comptés dans l'ELO tactique pendant cette session (recommencer ne rapporte pas de points). */
    private val rated = HashSet<String>()

    /** Met à jour l'ELO tactique après le premier résultat d'un problème ([score] 1 : résolu net, 0 : erreur ou abandon). */
    private fun rate(p: Puzzle, score: Double) {
        if (!rated.add(p.id)) return
        val elo = state.value.puzzleElo
        val next = tacticElo(elo, p.rating, score)
        _state.update { it.copy(puzzleElo = next) }
        viewModelScope.launch { withContext(NonCancellable) { runCatchingSuspend { prefs.setPuzzleElo(next) } } }
    }

    /**
     * Fin d'un problème : l'essai est enregistré (résolu, à refaire…) à part du travail annulable, pour que passer
     * aussitôt au suivant ne le perde pas ; l'ELO tactique compte le résultat s'il ne l'a pas déjà fait.
     */
    private fun conclude(p: Puzzle, outcome: Int) {
        rate(p, if (outcome == PuzzleOutcome.CLEAN) 1.0 else 0.0)
        viewModelScope.launch { withContext(NonCancellable) { runCatchingSuspend { dao.recordAttempt(p.id, outcome) } } }
    }

    /** Mauvais coup réfuté (position d'avant, coup) : il glisse en arrière quand la position revient. */
    private var missed: Pair<Position, Move>? = null

    /** Suspend l'annulation automatique : la réfutation reste affichée jusqu'à « Réessayer ». */
    fun holdRefutation() {
        if (state.value.refute == null || state.value.refuteUntil == 0L) return
        job?.cancel()
        _state.update { it.copy(refuteUntil = 0L, message = RefutationLogic.held(it.message, "Réessayer")) }
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

    companion object {
        private const val KEY_PUZZLE = "puzzles_current"
        internal const val SEARCH_DEBOUNCE_MS = 150L
        /** Bornes et coefficient de l'ELO tactique. */
        const val ELO_MIN = 400
        const val ELO_MAX = 3000
        private const val ELO_K = 32.0

        /** ELO tactique après un problème de classement [rating] : formule Elo (K = 32), [score] 1 (réussi) ou 0. */
        fun tacticElo(elo: Int, rating: Int, score: Double): Int {
            val expected = 1.0 / (1.0 + Math.pow(10.0, (rating - elo) / 400.0))
            return (elo + ELO_K * (score - expected)).roundToInt().coerceIn(ELO_MIN, ELO_MAX)
        }
        private val ID_LIKE = Regex("[A-Za-z0-9]{2,5}")

        /** La saisie peut être (le début d') un identifiant Lichess : 2 à 5 lettres ou chiffres. */
        fun looksLikeId(text: String): Boolean = ID_LIKE.matches(text.trim().removePrefix("#"))
    }

    /** Style d'image enregistré (format, fond), lu à l'ouverture du tiroir d'export. */
    suspend fun shareStyle(): ShareStyle = ShareStyle.from(prefs.settings.first())

    /** Enregistre le style choisi ; l'écriture va à son terme même si l'écran se ferme entre-temps. */
    fun saveShareStyle(v: ShareStyle) {
        viewModelScope.launch { withContext(NonCancellable) { prefs.setShareStyle(v.format.name, v.light) } }
    }
}

/**
 * Bilan tactique (catalogue) : ELO tactique, problèmes tentés, résolus nets, précision (en %), série de résolutions
 * nettes en cours et thèmes les plus manqués (erreur ou abandon), du plus fréquent au moins fréquent.
 */
@Immutable
data class TacticStats(
    val elo: Int = 1200, val tried: Int = 0, val clean: Int = 0, val streak: Int = 0, val missed: List<Pair<String, Int>> = emptyList(),
) {
    val precision: Int get() = if (tried == 0) 0 else clean * 100 / tried

    companion object {
        fun of(rows: List<com.ivanherboch.chessduck.data.AttemptRow>, elo: Int): TacticStats {
            val tried = rows.filter { it.status > 0 }
            val streak = tried.sortedByDescending { it.lastTriedAt }.takeWhile { it.status == PuzzleOutcome.CLEAN }.size
            val missed = tried.filter { it.status != PuzzleOutcome.CLEAN }.groupingBy { it.theme }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(3).map { it.key to it.value }
            return TacticStats(elo, tried.size, tried.count { it.status == PuzzleOutcome.CLEAN }, streak, missed)
        }
    }
}
