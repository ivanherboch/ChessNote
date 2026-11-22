package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
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
    val coachLeft: Int = 0,
    /** Le coup de l'élève est en cours de vérification (partie accompagnée). */
    val checking: Boolean = false,
    /** Coup arrêté avant d'être joué : l'élève en change, ou le joue quand même. */
    val warning: CoachWarning? = null,
    /** Partie normale : le filet de sécurité a arrêté au moins une gaffe ; la partie ne compte plus pour l'ELO. */
    val netUsed: Boolean = false,
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
) : ViewModel() {
    private val rnd = Random(System.nanoTime())
    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state.asStateFlow()
    // Flux partagés seulement tant que l'écran les observe (5 s de grâce pour une rotation ou un changement d'onglet).
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val games = gamesRepo.games().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tally = gamesRepo.tally().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Jeton de partie : incrémenté à chaque nouvelle partie, il invalide les résultats asynchrones d'une partie précédente. */
    private var gameGeneration = 0L
    private var applyJob: Job? = null
    private var aiJob: Job? = null
    private var analysisJob: Job? = null
    private var coachJob: Job? = null
    /** Lecture automatique de la revue de partie. */

    init {
        // Partie de départ : comme toute mise en place, elle passe par applyJob, si bien qu'une position demandée
        // aussitôt (« Jouer cette position » à la première ouverture du Jeu Libre) l'annule au lieu d'être écrasée par elle.
        applyJob = viewModelScope.launch { newGame(prefs.settings.first()) }
    }

    fun applySettings(twoPlayers: Boolean, color: Int, aiElo: Int) {
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            prefs.setGameSettings(twoPlayers, color, aiElo)
            newGame(prefs.settings.first())
        }
    }

    /** Nouvelle partie avec les réglages enregistrés (lus dans DataStore, même si l'écran n'est pas abonné). */
    fun newGame() {
        applyJob?.cancel()
        applyJob = viewModelScope.launch { newGame(prefs.settings.first()) }
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
            )
        }
        if (!st.twoPlayers && !playerWhite) aiMove() else precompute()
    }

    /**
     * « Jouer cette position » (Académie) : partie contre l'Ordinateur depuis [fen], le joueur ayant les Blancs si
     * [playerWhite]. C'est une partie d'entraînement : elle est enregistrée avec sa position de départ, sans changer l'ELO.
     * L'Ordinateur joue au niveau du palier de l'Académie (et non au réglage du Jeu Libre).
     */
    fun startFromPosition(fen: String, playerWhite: Boolean) {
        val start = runCatching { Line(fen) }.getOrNull() ?: return
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            val st = prefs.settings.first()
            gameGeneration++
            aiJob?.cancel(); analysisJob?.cancel(); coachJob?.cancel()
            _state.update {
                PlayState(
                    line = start, playerWhite = playerWhite, twoPlayers = false, aiElo = positionElo(st.academyLevel), flipped = !playerWhite, evalOn = it.evalOn,
                    coached = true, coachLeft = COACH_STOPS,
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
        _state.update { it.copy(hintStage = (it.hintStage + 1).coerceAtMost(2)) }
    }

    fun playerMove(m: Move) {
        val s = state.value
        if (!s.canPlay || !s.position.isLegal(m)) return
        if (s.coached && s.coachLeft > 0 && !s.twoPlayers && s.cursor == s.line.size) return coach(s, m)
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
            val sign = if (s.playerWhite) 1 else -1
            val warn = before != null && res != null && sign * before.eval > -LOST_CP && sign * (before.eval - res.eval) >= BLUNDER_CP
            if (!warn) {
                _state.update { it.copy(checking = false, analysis = if (res != null) it.analysis + (nl.size to res) else it.analysis) }
                return@launch afterMove()
            }
            val reply = res.best.takeIf { it.isNotEmpty() }?.let { Move.fromUci(it) }?.takeIf { after.isLegal(it) }
            // L'explication de la revue de partie, montrée à la demande (« Pourquoi ? »), et le point faible révélé.
            val reason = runCatching { ReviewComments.netReason(pos, m, before, res) }.getOrNull()
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
            it.copy(line = nl, cursor = nl.size, anim = moveAnim(s.position, m), hintStage = 0, analysis = it.analysis.filterKeys { k -> k <= cur })
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
        if (s.thinking) return
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
    }

    private fun navigate(delta: Int) {
        val s = state.value
        if (s.thinking) return
        val t = (s.cursor + delta).coerceIn(0, s.line.size)
        if (t == s.cursor) return
        val anim = LinePlaybackController.transition(s.line, s.cursor, t)
        _state.update { it.copy(cursor = t, anim = anim, hintStage = 0) }
        precompute()
    }

    fun resign() {
        val s = state.value
        if (s.result != null) return
        aiJob?.cancel()
        stopCoach()
        _state.update { it.copy(thinking = false) }
        finish(GameEnd.NONE, if (s.twoPlayers) s.line.last.whiteToMove else s.playerWhite)
    }

    /** Clôt la partie : résultat, ELO FIDE (K = 32) contre l'ordinateur, enregistrement dans l'historique. */
    private fun finish(end: GameEnd, resignedWhite: Boolean?) {
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
                // Partie d'entraînement (position de leçon) ou filet de sécurité utilisé : l'ELO ne change pas.
                val rated = !s.fromPosition && !s.netUsed
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
                                s.fromPosition -> " Partie d'entraînement depuis une leçon : l'ELO ne change pas."
                                s.netUsed -> " Le filet de sécurité vous a arrêté : la partie ne compte pas pour l'ELO."
                                else -> " ELO ${st.elo} → $newElo ($delta)."
                            },
                            if (outcome == 1) Tone.GOOD else if (outcome == 0) Tone.GOLD else Tone.BAD,
                        ),
                    )
                }
                if (rated) prefs.setElo(newElo)
                var record = GameRecord(
                        date = System.currentTimeMillis(), opponent = "Ordinateur ${s.aiElo}", vsComputer = true, playerWhite = s.playerWhite,
                        result = resultStr, outcome = outcome, reason = reason, eloBefore = st.elo, eloAfter = newElo, moves = line.uciMoves(), analysis = analysis,
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
const val BLUNDER_CP = 200
/** Temps d'analyse de chaque position vérifiée par le filet (le coup est déjà affiché pendant ce temps). */
private const val CHECK_MS = 250
private const val LOST_CP = 300

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
