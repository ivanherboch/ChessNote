package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.data.GameRepository
import com.ivanherboch.chessduck.data.SettingsRepository
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.chess.GameEnd
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.LinePlaybackController
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.ui.undoAnim
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

@Immutable
data class GameResult(val text: String, val tone: Tone)

const val COMPUTER = "🤖 Ordinateur"

@Immutable
data class PlayState(
    val line: Line = Line(START_FEN),
    val cursor: Int = 0,
    val playerWhite: Boolean = true,
    val twoPlayers: Boolean = false,
    val aiElo: Int = 1200,
    val flipped: Boolean = false,
    val anim: BoardAnim? = null,
    val thinking: Boolean = false,
    val result: GameResult? = null,
    val hintStage: Int = 0,
    val analysis: Map<Int, Analysis> = emptyMap(),
    val evalOn: Boolean = false,
    /** Partie qui vient de se terminer, enregistrée dans l'historique : « Analyser la partie » l'ouvre en revue. */
    val lastRecord: GameRecord? = null,
    /**
     * Partie accompagnée (positions de l'Académie) : avant une gaffe, l'Ordinateur arrête le coup et pose la question
     * réflexe ; au plus [coachLeft] fois par partie.
     */
    val coached: Boolean = false,
    /** Partie depuis une position : d'où elle vient (leçon de l'Académie ou problème), pour le dire au joueur. */
    val origin: PositionOrigin = PositionOrigin.LESSON,
    val coachLeft: Int = 0,
    /** Le coup de l'élève est en cours de vérification (partie accompagnée). */
    val checking: Boolean = false,
    /** Coup arrêté avant d'être joué : l'élève en change, ou le joue quand même. */
    val warning: CoachWarning? = null,
    /** Partie normale : le filet de sécurité a arrêté au moins une gaffe ; la partie ne compte plus pour l'ELO. */
    val netUsed: Boolean = false,
    /**
     * Partie classée : lancée avec le réglage « Partie classée », elle compte pour l'ELO tant qu'aucune aide ne l'en sort
     * (coup annulé, flèche de l'indice, filet de sécurité). Faux : partie libre, l'ELO ne change jamais.
     */
    val rated: Boolean = false,
    /** Pourquoi une partie classée ne compte plus pour l'ELO (« vous avez annulé un coup »…) ; null sinon. */
    val unratedBy: String? = null,
    /** Annonce ponctuelle au-dessus du statut (partie sortie du classement), effacée au coup suivant. */
    val notice: String? = null,
) {
    /** Partie d'entraînement lancée depuis une position de l'Académie : elle ne compte pas pour l'ELO. */
    val fromPosition: Boolean get() = line.startFen != START_FEN

    /** Une partie est en cours (au moins un échange joué, pas encore de résultat). */
    val inProgress: Boolean get() = result == null && line.size >= 2

    val position: Position get() = line.positions[cursor.coerceIn(0, line.size)]
    val canPlay: Boolean get() = result == null && !thinking && !checking && warning == null && (twoPlayers || position.whiteToMove == playerWhite)
    val hint: Move? get() = analysis[cursor]?.best?.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }
}

class PlayViewModel(
    private val prefs: SettingsRepository,
    private val gamesRepo: GameRepository,
    private val engine: ChessEngine,
    private val analyzer: GameAnalysisService,
    /** Hasard (couleur tirée au sort, fautes de l'Ordinateur faible) ; fixé dans les tests pour rejouer un cas. */
    private val rnd: Random = Random(System.nanoTime()),
    /** Partie en cours, gardée si Android arrête l'application en arrière-plan (appel, autre application). */
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state.asStateFlow()
    // Flux partagés seulement tant que l'écran les observe (5 s de grâce pour une rotation ou un changement d'onglet).
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val games = gamesRepo.games().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tally = gamesRepo.tally().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Jeton de partie : incrémenté à chaque nouvelle partie, il invalide les résultats asynchrones d'une partie précédente. */
    private var gameGeneration = 0L
    /**
     * Génération de la dernière partie conclue. Le résultat s'affiche un instant après la fin (lecture des réglages) :
     * ce repère, posé aussitôt, empêche qu'un second abandon ou une nouvelle partie l'enregistre (et compte l'ELO) deux fois.
     */
    private var finishedGeneration = -1L
    private var applyJob: Job? = null
    private var aiJob: Job? = null
    private var analysisJob: Job? = null
    private var coachJob: Job? = null
    /** Lecture automatique de la revue de partie. */

    init {
        val restored = restore()
        if (restored != null) {
            // Application relancée par Android : la partie interrompue reprend là où elle en était (ni abandon ni perte).
            gameGeneration++
            _state.value = restored
            if (!restored.twoPlayers && restored.line.last.whiteToMove != restored.playerWhite && restored.cursor == restored.line.size) aiMove()
            else precompute()
        } else {
            // Partie de départ : comme toute mise en place, elle passe par applyJob, si bien qu'une position demandée
            // aussitôt (« Jouer cette position » à la première ouverture du Jeu Libre) l'annule au lieu d'être écrasée par elle.
            applyJob = viewModelScope.launch { newGame(prefs.settings.first()) }
        }
        viewModelScope.launch { state.collect(::save) }
    }

    /**
     * Garde la partie non terminée dans [saved] (quelques chaînes et nombres). Un coup en cours de vérification par le
     * filet n'est pas gardé : à la reprise, l'élève le rejoue. Une partie terminée ou vide efface la sauvegarde.
     */
    private fun save(s: PlayState) {
        val keep = s.result == null && (s.line.size > 0 || s.fromPosition)
        if (!keep) {
            if (saved.contains(K_MOVES)) SAVED_KEYS.forEach { saved.remove<Any>(it) }
            return
        }
        val size = if (s.checking) s.line.size - 1 else s.line.size
        saved[K_FEN] = s.line.startFen
        saved[K_MOVES] = s.line.uciMoves(size)
        saved[K_CURSOR] = s.cursor.coerceAtMost(size)
        saved[K_WHITE] = s.playerWhite
        saved[K_TWO] = s.twoPlayers
        saved[K_ELO] = s.aiElo
        saved[K_FLIPPED] = s.flipped
        saved[K_EVAL] = s.evalOn
        saved[K_COACHED] = s.coached
        saved[K_COACH_LEFT] = s.coachLeft
        saved[K_NET] = s.netUsed
        saved[K_RATED] = s.rated
        saved[K_UNRATED_BY] = s.unratedBy
        saved[K_ORIGIN] = s.origin.name
    }

    /** Partie sauvegardée par [save], ou null (aucune, ou illisible). */
    private fun restore(): PlayState? {
        val moves = saved.get<String>(K_MOVES) ?: return null
        val fen = saved.get<String>(K_FEN) ?: return null
        val line = runCatching { Line.fromUci(fen, moves) }.getOrNull() ?: return null
        if (line.end() != GameEnd.NONE) return null
        return PlayState(
            line = line, cursor = (saved.get<Int>(K_CURSOR) ?: line.size).coerceIn(0, line.size),
            playerWhite = saved.get<Boolean>(K_WHITE) ?: true, twoPlayers = saved.get<Boolean>(K_TWO) ?: false,
            aiElo = saved.get<Int>(K_ELO) ?: 1200, flipped = saved.get<Boolean>(K_FLIPPED) ?: false,
            evalOn = saved.get<Boolean>(K_EVAL) ?: false, coached = saved.get<Boolean>(K_COACHED) ?: false,
            coachLeft = saved.get<Int>(K_COACH_LEFT) ?: 0, netUsed = saved.get<Boolean>(K_NET) ?: false,
            rated = saved.get<Boolean>(K_RATED) ?: false, unratedBy = saved.get<String>(K_UNRATED_BY),
            origin = saved.get<String>(K_ORIGIN)?.let { o -> PositionOrigin.entries.firstOrNull { it.name == o } } ?: PositionOrigin.LESSON,
        )
    }

    private companion object {
        const val K_FEN = "play_start_fen"
        const val K_MOVES = "play_moves"
        const val K_CURSOR = "play_cursor"
        const val K_WHITE = "play_white"
        const val K_TWO = "play_two_players"
        const val K_ELO = "play_ai_elo"
        const val K_FLIPPED = "play_flipped"
        const val K_EVAL = "play_eval"
        const val K_COACHED = "play_coached"
        const val K_COACH_LEFT = "play_coach_left"
        const val K_NET = "play_net_used"
        const val K_ORIGIN = "play_origin"
        const val K_RATED = "play_rated"
        const val K_UNRATED_BY = "play_unrated_by"
        val SAVED_KEYS = listOf(K_FEN, K_MOVES, K_CURSOR, K_WHITE, K_TWO, K_ELO, K_FLIPPED, K_EVAL, K_COACHED, K_COACH_LEFT, K_NET, K_ORIGIN, K_RATED, K_UNRATED_BY)
    }

    fun applySettings(twoPlayers: Boolean, color: Int, aiElo: Int) {
        abandonInProgress()
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            prefs.setGameSettings(twoPlayers, color, aiElo)
            newGame(prefs.settings.first())
        }
    }

    /** Nouvelle partie avec les réglages enregistrés (lus dans DataStore, même si l'écran n'est pas abonné). */
    fun newGame() {
        abandonInProgress()
        applyJob?.cancel()
        applyJob = viewModelScope.launch { newGame(prefs.settings.first()) }
    }

    /**
     * Partie contre l'Ordinateur quittée en cours (nouvelle partie, nouveaux réglages, position de leçon) : elle compte
     * comme un abandon, enregistré avec son effet sur l'ELO. Sinon, relancer une partie mal engagée éviterait la défaite.
     * À deux joueurs, la partie est simplement laissée de côté.
     */
    private fun abandonInProgress() {
        val s = state.value
        if (!s.inProgress || s.twoPlayers || finishedGeneration == gameGeneration) return
        aiJob?.cancel()
        stopCoach()
        _state.update { it.copy(thinking = false) }
        finish(GameEnd.NONE, s.playerWhite)
    }

    private fun newGame(st: Settings) {
        gameGeneration++
        aiJob?.cancel(); analysisJob?.cancel(); coachJob?.cancel()
        val white = when (st.colorChoice) {
            0 -> true
            2 -> false
            else -> rnd.nextBoolean()
        }
        val playerWhite = st.twoPlayers || white
        _state.update {
            PlayState(
                playerWhite = playerWhite, twoPlayers = st.twoPlayers, aiElo = st.aiElo, flipped = !playerWhite,
                evalOn = it.evalOn, coached = st.safetyNet && !st.twoPlayers, coachLeft = Int.MAX_VALUE,
                // Partie classée seulement si le réglage le demande, et sans filet (le filet en sort la partie).
                rated = st.ratedGame && !st.twoPlayers && !st.safetyNet,
            )
        }
        if (!st.twoPlayers && !playerWhite) aiMove() else precompute()
    }

    /**
     * « Jouer cette position » (Académie) : partie contre l'Ordinateur depuis [fen], le joueur ayant les Blancs si
     * [playerWhite]. C'est une partie d'entraînement : elle est enregistrée avec sa position de départ, sans changer l'ELO.
     * L'Ordinateur joue au niveau du palier de l'Académie (et non au réglage du Jeu Libre).
     */
    fun startFromPosition(fen: String, playerWhite: Boolean, origin: PositionOrigin = PositionOrigin.LESSON) {
        val start = runCatching { Line(fen) }.getOrNull() ?: return
        abandonInProgress()
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            val st = prefs.settings.first()
            gameGeneration++
            aiJob?.cancel(); analysisJob?.cancel(); coachJob?.cancel()
            _state.update {
                PlayState(
                    line = start, playerWhite = playerWhite, twoPlayers = false, aiElo = positionElo(st.academyLevel), flipped = !playerWhite, evalOn = it.evalOn,
                    coached = true, coachLeft = COACH_STOPS, origin = origin,
                )
            }
            if (start.last.whiteToMove != playerWhite) aiMove() else precompute()
        }
    }

    fun flip() = _state.update { it.copy(flipped = !it.flipped) }

    fun toggleEval() {
        _state.update { it.copy(evalOn = !it.evalOn) }
        precompute()
    }

    /** Indice en 2 temps ; le meilleur coup est déjà pré-calculé en tâche de fond. */
    fun hint() {
        val s = state.value
        if (!s.canPlay) return
        val stage = (s.hintStage + 1).coerceAtMost(2)
        _state.update { it.copy(hintStage = stage) }
        if (stage == 2) unrate("vous avez demandé la flèche de l'indice")
    }

    /**
     * Partie classée qui reçoit une aide (coup annulé, flèche de l'indice, filet) : elle continue en partie libre, sans
     * effet sur l'ELO, et le dit une fois, sereinement.
     */
    private fun unrate(why: String) {
        if (!state.value.rated) return
        _state.update {
            it.copy(rated = false, unratedBy = why, notice = "Cette partie ne compte plus pour l'ELO ($why) : jouez-la tranquillement jusqu'au bout.")
        }
    }

    /** « Paramètres de partie » : partie classée ou libre. La partie libre s'applique aussitôt, la classée à la prochaine partie. */
    fun setRatedGame(on: Boolean) {
        viewModelScope.launch { prefs.setRatedGame(on) }
        val s = state.value
        if (!on) return unrate("vous êtes passé en partie libre")
        // Partie classée : sans filet de sécurité (coupé aussi dans les réglages).
        if (s.coached && !s.fromPosition && !s.twoPlayers) toggleCoach()
    }

    fun playerMove(m: Move) {
        val s = state.value
        if (!s.canPlay || !s.position.isLegal(m)) return
        // Aussi quand on rejoue depuis un coup de l'historique (la suite est alors remplacée).
        if (s.coached && s.coachLeft > 0 && !s.twoPlayers) return coach(s, m)
        commit(m)
    }

    /**
     * Partie accompagnée (position de leçon) ou filet de sécurité (partie normale, réglage mémorisé) : active ou coupe
     * les arrêts avant une gaffe.
     */
    fun toggleCoach() {
        val s = state.value
        if (s.twoPlayers) return
        // Une vérification en cours est abandonnée : le coup (déjà affiché) est gardé et l'Ordinateur répond.
        coachJob?.cancel()
        val on = !s.coached
        _state.update { it.copy(coached = on, coachLeft = if (it.fromPosition) it.coachLeft else Int.MAX_VALUE, warning = null, checking = false) }
        if (on) unrate("vous avez activé le filet de sécurité")
        if (s.checking) afterMove()
        if (!s.fromPosition) viewModelScope.launch { prefs.setSafetyNet(on) }
    }

    /** « Paramètres de partie » : filet de sécurité mémorisé, appliqué aussitôt à la partie normale en cours. */
    fun setSafetyNet(on: Boolean) {
        val s = state.value
        if (!s.fromPosition && !s.twoPlayers && s.coached != on) toggleCoach()
        else viewModelScope.launch { prefs.setSafetyNet(on) }
    }

    /** Coup arrêté : montre pourquoi c'est une gaffe (la réponse adverse, en rouge). */
    fun showWhy() = _state.update { s -> s.warning?.takeIf { it.why != null }?.let { s.copy(warning = it.copy(showWhy = true)) } ?: s }

    /** Abandonne la vérification ou l'arrêt en cours de la partie accompagnée. */
    private fun stopCoach() {
        coachJob?.cancel()
        _state.update { it.copy(checking = false, warning = null) }
    }

    /** Coup arrêté : l'élève le joue quand même. */
    fun playAnyway() {
        val w = state.value.warning ?: return
        _state.update { it.copy(warning = null) }
        if (!state.value.position.isLegal(w.move)) return
        commit(w.move)
    }

    /** Coup arrêté : l'élève cherche un autre coup. */
    fun dismissWarning() = _state.update { it.copy(warning = null) }

    /**
     * Vérifie le coup avant de le jouer : s'il fait perdre au moins [BLUNDER_CP] centipions (sans que la partie soit
     * déjà perdue), le coup est arrêté et la question réflexe s'affiche, sans dévoiler la réponse adverse.
     */
    /** Position (demi-coup et ligne) du dernier arrêt : un deuxième arrêt au même endroit montre la solution. */
    private var stopPly = -1
    private var stopLine: Line? = null

    /**
     * Instant où le coup vérifié par le filet a été affiché : la pause minimale de la réponse de l'Ordinateur part de là
     * (la vérification compte dans son « temps de réflexion », sans s'y ajouter).
     */
    private var replySince: Long? = null

    private fun coach(s: PlayState, m: Move) {
        analysisJob?.cancel()
        val gen = gameGeneration
        val pos = s.position
        val cur = s.cursor
        // Fluidité : le coup s'affiche aussitôt, comme sans filet ; seule la réponse de l'Ordinateur attend la
        // vérification (courte), qui se fond dans son temps de réflexion. Une gaffe est retirée de l'échiquier.
        val nl = s.line.copy(cur)
        nl.push(m)
        replySince = System.currentTimeMillis()
        _state.update {
            it.copy(line = nl, cursor = nl.size, anim = moveAnim(pos, m), hintStage = 0, checking = true, analysis = it.analysis.filterKeys { k -> k <= cur })
        }
        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            val before = s.analysis[cur] ?: analyse(pos, CHECK_MS)
            val after = pos.play(m)
            val res = if (before == null) null else analyse(after, CHECK_MS)
            // Partie changée entre-temps (nouvelle partie, coup annulé, navigation) : cette vérification ne la concerne plus.
            if (gen != gameGeneration || state.value.line !== nl) return@launch
            // Arrêt seulement pour une gaffe nette et explicable (voir SafetyNet).
            val reason = if (before != null && res != null) SafetyNet.decide(pos, m, before, res)?.takeIf { it.stop } else null
            if (reason == null) {
                _state.update { it.copy(checking = false, analysis = if (res != null) it.analysis + (nl.size to res) else it.analysis) }
                return@launch afterMove()
            }
            val reply = res?.best?.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }?.takeIf { after.isLegal(it) }
            // L'explication de la revue de partie, montrée à la demande (« Pourquoi ? »), et le point faible révélé.
            val net = !s.fromPosition
            reason?.topic?.let { t -> if (net) launch { prefs.addNetEvent(t.name, System.currentTimeMillis()) } }
            // Deuxième arrêt dans la même position : l'explication et le coup conseillé s'affichent d'emblée (on ne tourne
            // pas en rond quand presque tous les coups perdent du matériel).
            val again = stopPly == s.cursor && stopLine === s.line
            stopPly = s.cursor; stopLine = s.line
            _state.update {
                it.copy(
                    // La gaffe est retirée : la pièce revient (animation inverse), la position d'avant est rejouable.
                    line = s.line, cursor = cur, anim = undoAnim(pos, m), analysis = it.analysis.filterKeys { k -> k <= cur },
                    checking = false, coachLeft = it.coachLeft - 1, netUsed = it.netUsed || net,
                    rated = it.rated && !net, unratedBy = if (it.rated && net) "le filet de sécurité vous a arrêté" else it.unratedBy,
                    warning = CoachWarning(m, coachText(pos.san(m), after, reply, net), reason?.text, reply, showWhy = again && reason != null, best = reason?.best),
                )
            }
        }
    }

    private fun commit(m: Move) {
        val s = state.value
        if (s.result != null || !s.position.isLegal(m)) return
        analysisJob?.cancel()
        val cur = s.cursor
        val nl = s.line.copy(cur)
        nl.push(m)
        _state.update {
            it.copy(line = nl, cursor = nl.size, anim = moveAnim(s.position, m), hintStage = 0, notice = null, analysis = it.analysis.filterKeys { k -> k <= cur })
        }
        afterMove()
    }

    private fun afterMove() {
        val s = state.value
        val end = s.line.end()
        when {
            end != GameEnd.NONE -> finish(end, null)
            !s.twoPlayers && s.line.last.whiteToMove != s.playerWhite -> aiMove()
            else -> precompute()
        }
    }

    private fun aiMove() {
        analysisJob?.cancel()
        aiJob?.cancel()
        _state.update { it.copy(thinking = true) }
        val gen = gameGeneration
        aiJob = viewModelScope.launch {
            val t0 = replySince ?: System.currentTimeMillis()
            replySince = null
            val s = state.value
            val pos = s.line.last
            val elo = s.aiElo
            // À partir de 1350 ELO, Stockfish se bride lui-même (UCI_Elo) : il choisit son coup à faible profondeur,
            // si bien que le temps de réflexion n'influe pas sur son niveau. Sous 1350 (son plancher), on affaiblit
            // le jeu par des fautes « humaines » : un coup plausible mais inférieur, dont la gravité est bornée par
            // le niveau choisi (jamais un coup tiré totalement au hasard).
            var move: Move? = null
            if (elo < 1350 && rnd.nextDouble() < (1350 - elo) / 1500.0) {
                val lines = engine.search(pos.toFen(), 160, multiPv = 30, depth = 4).lines
                val top = lines.firstOrNull()?.score
                if (top != null) {
                    val cap = 150 + ((1350 - elo) * 1.2).toInt()
                    move = lines.drop(1).filter { top - it.score <= cap && pos.isLegal(it.move) }.randomOrNull(rnd)?.move
                }
            }
            if (move == null) move = engine.search(pos.toFen(), 220 + elo / 10, elo = elo.coerceAtLeast(1350)).best
            if (move == null || !pos.isLegal(move)) move = pos.legalMoves.random(rnd)
            delay((550 - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
            // La partie a changé pendant la réflexion (nouvelle partie, réglages) : ce coup ne la concerne plus.
            if (gen != gameGeneration || state.value.line !== s.line) return@launch
            val nl = s.line.copy()
            nl.push(move)
            _state.update { it.copy(line = nl, cursor = nl.size, anim = moveAnim(pos, move), thinking = false, hintStage = 0) }
            val end = nl.end()
            if (end != GameEnd.NONE) finish(end, null) else precompute()
        }
    }

    private suspend fun analyse(pos: Position, ms: Int): Analysis? = analyzer.analyse(pos, ms)

    /** Pré-calcule en tâche de fond le meilleur coup de la position affichée (indice instantané). */
    private fun precompute() {
        analysisJob?.cancel()
        val s = state.value
        if (s.thinking || s.analysis.containsKey(s.cursor)) return
        val ply = s.cursor
        val line = s.line
        analysisJob = viewModelScope.launch {
            val a = analyse(line.positions[ply], 450) ?: return@launch
            _state.update { if (it.line === line) it.copy(analysis = it.analysis + (ply to a)) else it }
        }
    }

    fun back() = navigate(-1)
    fun forward() = navigate(1)

    /** Affiche la position après [n] demi-coups (clic dans la liste des coups). */
    fun goTo(n: Int) {
        val s = state.value
        // Pendant la réflexion de l'Ordinateur ou la vérification du filet, la partie avance : pas de navigation.
        if (s.thinking || s.checking) return
        val t = n.coerceIn(0, s.line.size)
        if (t != s.cursor) navigate(t - s.cursor)
    }

    /**
     * Cible de « Annuler mon coup » : contre l'ordinateur, on revient avant le dernier coup du joueur
     * (deux demi-coups si l'ordinateur a déjà répondu) ; à deux joueurs, d'un demi-coup. -1 si impossible.
     */
    fun undoTarget(s: PlayState = state.value): Int {
        if (s.thinking || s.checking || s.result != null || s.cursor == 0) return -1
        if (s.twoPlayers) return s.cursor - 1
        var t = s.cursor - 1
        while (t >= 0 && s.line.positions[t].whiteToMove != s.playerWhite) t--
        return t
    }

    fun undoMove() {
        val t = undoTarget()
        if (t < 0) return
        aiJob?.cancel()
        stopCoach()
        goTo(t)
        if (!state.value.twoPlayers) unrate("vous avez annulé un coup")
    }

    private fun navigate(delta: Int) {
        val s = state.value
        if (s.thinking || s.checking) return
        val t = (s.cursor + delta).coerceIn(0, s.line.size)
        if (t == s.cursor) return
        val anim = LinePlaybackController.transition(s.line, s.cursor, t)
        // Un coup arrêté par le filet concerne la position quittée : l'arrêt se referme (sinon « Jouer quand même »
        // jouerait ce coup ailleurs, et « Pourquoi ? » dessinerait ses flèches sur un autre échiquier).
        _state.update { it.copy(cursor = t, anim = anim, hintStage = 0, warning = null) }
        precompute()
    }

    fun resign() {
        val s = state.value
        if (s.result != null || finishedGeneration == gameGeneration) return
        aiJob?.cancel()
        stopCoach()
        _state.update { it.copy(thinking = false) }
        finish(GameEnd.NONE, if (s.twoPlayers) s.line.last.whiteToMove else s.playerWhite)
    }

    /** Clôt la partie : résultat, ELO FIDE (K = 32) contre l'ordinateur, enregistrement dans l'historique. */
    private fun finish(end: GameEnd, resignedWhite: Boolean?) {
        if (finishedGeneration == gameGeneration) return
        finishedGeneration = gameGeneration
        // Mat ou nulle : l'évaluation de la position finale est connue, la jauge ne garde pas une ancienne valeur.
        if (end != GameEnd.NONE) {
            analysisJob?.cancel()
            _state.update { it.copy(analysis = it.analysis + (it.line.size to Analysis.decided(it.line.last, end))) }
        }
        val s = state.value
        val line = s.line
        // 1 = victoire des Blancs, 0 = nulle, -1 = victoire des Noirs
        val whiteResult = when {
            resignedWhite != null -> if (resignedWhite) -1 else 1
            end == GameEnd.CHECKMATE -> if (line.last.whiteToMove) -1 else 1
            else -> 0
        }
        val reason = if (resignedWhite != null) "abandon" else GameLogic.endText(end)
        val resultStr = when (whiteResult) { 1 -> "1-0"; -1 -> "0-1"; else -> "1/2-1/2" }
        // Une partie d'entraînement depuis une leçon compte dès le premier coup (un mat en 1 est une vraie fin).
        if (line.size < if (s.fromPosition) 1 else 2) {
            _state.update { it.copy(result = GameResult("Partie interrompue avant le premier échange : rien n'est enregistré.", Tone.NEUTRAL)) }
            return
        }
        val gen = gameGeneration
        // La partie est close tout de suite (plus aucun coup jouable) ; le texte du résultat arrive un instant après.
        _state.update { it.copy(result = it.result ?: GameResult("Partie terminée.", Tone.NEUTRAL), thinking = false) }
        // Mise à jour de l'écran seulement si la partie affichée est toujours celle qui vient de se terminer.
        fun show(f: (PlayState) -> PlayState) = _state.update { if (gen == gameGeneration) f(it) else it }
        // La partie est réellement terminée : son enregistrement et l'ELO ne sont jamais annulés.
        viewModelScope.launch(NonCancellable) {
            val st = prefs.settings.first()
            val analysis = Analysis.encodeAll(List(line.size + 1) { s.analysis[it] })
            if (s.twoPlayers) {
                val text = when (whiteResult) {
                    1 -> "Victoire de ${st.pseudo} (Blancs) par $reason."
                    -1 -> "Victoire de ${st.pseudo2} (Noirs) par $reason."
                    else -> "Partie nulle ($reason)."
                } + " Partie à 2 joueurs : l'ELO ne change pas."
                show { it.copy(result = GameResult(text, Tone.GOLD), thinking = false) }
                var record = GameRecord(
                        date = System.currentTimeMillis(), opponent = "2 joueurs", vsComputer = false, playerWhite = true, result = resultStr,
                        outcome = whiteResult, reason = reason, eloBefore = st.elo, eloAfter = st.elo, moves = line.uciMoves(), analysis = analysis,
                        whiteName = st.pseudo, blackName = st.pseudo2,
                    )
                record = record.copy(id = gamesRepo.insertGame(record))
                show { it.copy(lastRecord = record) }
                analyzer.start(record, line)
            } else {
                val outcome = if (s.playerWhite) whiteResult else -whiteResult
                // Seule une partie classée, sans aide, change l'ELO (partie libre, position de leçon, filet : jamais).
                val rated = s.rated && !s.fromPosition && !s.netUsed
                val newElo = if (!rated) st.elo else GameLogic.newElo(st.elo, s.aiElo, (outcome + 1) / 2.0)
                val diff = newElo - st.elo
                val delta = if (diff >= 0) "+$diff" else "−${-diff}"
                val head = when (outcome) {
                    1 -> "Victoire par $reason !"
                    -1 -> "Défaite par $reason."
                    else -> "Partie nulle ($reason)."
                }
                show {
                    it.copy(
                        thinking = false,
                        result = GameResult(
                            head + when {
                                s.fromPosition -> " Partie d'entraînement depuis ${s.origin.fromText} : l'ELO ne change pas."
                                rated -> " ELO ${st.elo} → $newElo ($delta)."
                                s.unratedBy != null -> " Partie sortie du classement (${s.unratedBy}) : l'ELO ne change pas."
                                else -> " Partie libre : l'ELO ne change pas."
                            },
                            if (outcome == 1) Tone.GOOD else if (outcome == 0) Tone.GOLD else Tone.BAD,
                        ),
                    )
                }
                if (rated) prefs.setElo(newElo)
                var record = GameRecord(
                        date = System.currentTimeMillis(), opponent = "Ordinateur ${s.aiElo}", vsComputer = true, playerWhite = s.playerWhite,
                        result = resultStr, outcome = outcome, reason = reason, eloBefore = st.elo,
                        // Hors ELO (partie libre, position, aide) : eloAfter = 0, comme opponentElo = 0 pour les parties à deux.
                        eloAfter = if (rated) newElo else 0, moves = line.uciMoves(), analysis = analysis,
                        whiteName = if (s.playerWhite) st.pseudo else COMPUTER, blackName = if (s.playerWhite) COMPUTER else st.pseudo, opponentElo = s.aiElo,
                        startFen = if (s.fromPosition) line.startFen else "",
                    )
                record = record.copy(id = gamesRepo.insertGame(record))
                show { it.copy(lastRecord = record) }
                analyzer.start(record, line)
            }
        }
    }

    fun deleteGame(id: Long) {
        viewModelScope.launch { gamesRepo.deleteGame(id) }
    }

}

/** Force de l'Ordinateur pour « Jouer cette position », selon le palier de l'Académie (1 à 5). */
/** Coup arrêté par la partie accompagnée, et la question réflexe à se poser. */
data class CoachWarning(
    val move: Move,
    val text: String,
    /** Explication de la gaffe (comme dans la revue), montrée par « Pourquoi ? ». */
    val why: String? = null,
    /** Réponse adverse qui punirait le coup (flèche rouge une fois l'explication montrée). */
    val reply: Move? = null,
    val showWhy: Boolean = false,
    /** Coup conseillé (flèche verte une fois l'explication montrée). */
    val best: Move? = null,
)

/** Arrêts de la partie accompagnée (une ou deux fois par partie), et seuils (centipions). */
const val COACH_STOPS = 2
const val BLUNDER_CP = SafetyNet.BLUNDER_CP
/** Temps d'analyse de chaque position vérifiée par le filet (le coup est déjà affiché pendant ce temps). */
private const val CHECK_MS = 250


/** La question réflexe adaptée à la réponse adverse qui punirait le coup [san] (sans la dévoiler). */
internal fun coachText(san: String, after: Position, reply: Move?, net: Boolean = false): String {
    val next = reply?.let { after.play(it) }
    val q = when {
        reply == null || next == null -> "Quelle menace laissez-vous à l'adversaire ?"
        next.isCheckmate -> "Attention au mat : quel échec laissez-vous à l'adversaire ?"
        after[reply.to] != 0 -> "Quelle prise laissez-vous à l'adversaire ? Vérifiez les défenseurs de vos pièces."
        next.inCheck -> "Quel échec laissez-vous à l'adversaire ?"
        else -> "Quelle menace laissez-vous à l'adversaire ? Regardez ses échecs, ses prises et ses attaques."
    }
    return (if (net) "Filet de sécurité" else "Partie accompagnée") + " : $san serait une grosse erreur. $q Changez de coup, ou jouez-le quand même."
}

fun positionElo(level: Int): Int = when (level.coerceIn(1, 5)) {
    1 -> 600
    2 -> 900
    3 -> 1300
    4 -> 1700
    else -> 2100
}

/** Origine d'une partie lancée depuis une position (« Jouer cette position », « Continuer contre l'Ordinateur »). */
enum class PositionOrigin(
    /** « depuis une leçon », « depuis un problème ». */
    val fromText: String,
    /** « Position de la leçon », « Position du problème ». */
    val title: String,
) {
    LESSON("une leçon", "Position de la leçon"),
    PUZZLE("un problème", "Position du problème"),
}
