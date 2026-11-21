package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.chess.ROOK
import com.ivanherboch.chessduck.ui.undoAnim
import com.ivanherboch.chessduck.engine.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.QUEEN
import com.ivanherboch.chessduck.chess.isWhite
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.chess.sq
import com.ivanherboch.chessduck.engine.ChessEngine
import com.ivanherboch.chessduck.engine.MATE
import com.ivanherboch.chessduck.engine.isMateScore
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.util.runCatchingSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** But d'une finale jouable : gagner (mat ou promotion) ou tenir la nulle. */
enum class EndgameGoal { WIN, DRAW }

/**
 * Familles de finales théoriques jouées contre l'Ordinateur à pleine force. [maxMoves] : nombre de coups du joueur
 * pour gagner (WIN) ou à tenir (DRAW). Le joueur a les Blancs pour gagner, les Noirs pour défendre.
 */
enum class EndgameFamily(val label: String, val goal: EndgameGoal, val maxMoves: Int, val task: String) {
    KQK("Roi et dame contre roi", EndgameGoal.WIN, 16, "matez le roi noir"),
    KRRK("Deux tours contre roi", EndgameGoal.WIN, 12, "matez le roi noir"),
    KRK("Roi et tour contre roi", EndgameGoal.WIN, 25, "matez le roi noir"),
    KBBK("Les deux fous", EndgameGoal.WIN, 30, "matez le roi noir"),
    KPK_WIN("Roi et pion contre roi", EndgameGoal.WIN, 20, "menez votre pion à dame"),
    LUCENA("Position de Lucena", EndgameGoal.WIN, 25, "menez votre pion à dame"),
    PHILIDOR_DRAW("Défense de Philidor", EndgameGoal.DRAW, 15, "tenez la nulle"),
    ROOK_PAWN_DRAW("Pion tour : la nulle", EndgameGoal.DRAW, 15, "tenez la nulle"),
    KQKP("Dame contre pion en septième", EndgameGoal.WIN, 25, "gagnez le pion ou matez"),
    QUEEN_BISHOP_PAWN_DRAW("Dame contre pion fou : le pat", EndgameGoal.DRAW, 15, "tenez la nulle"),
    VANCURA_DRAW("Défense Vancura", EndgameGoal.DRAW, 15, "tenez la nulle"),
    ;

    val heroWhite: Boolean get() = goal == EndgameGoal.WIN

    /** La méthode à appliquer, en une phrase : rappelée au départ de chaque position et après une erreur. */
    val method: String
        get() = when (this) {
            KQK -> "Enfermez le roi avec la dame, à un saut de cavalier de lui, amenez votre roi, puis matez au bord. Laissez-lui toujours une case pour éviter le pat."
            KRRK -> "Montez l'escalier : une tour garde une rangée, l'autre donne échec une rangée plus loin, jusqu'au bord."
            KRK -> "La tour coupe le roi noir et rétrécit sa boîte, votre roi vient se placer en face du sien, puis la tour mate sur le bord."
            KBBK -> "Les deux fous côte à côte forment un mur, votre roi les soutient, et l'on repousse le roi noir vers un coin, sans le mettre pat."
            KPK_WIN -> "Le roi passe devant son pion et gagne une case clé, deux rangées devant lui. Ne poussez le pion que quand la route est sûre : si le roi noir prend l'opposition devant le pion, c'est nul."
            LUCENA -> "Construisez le pont : la tour vient sur la quatrième rangée pour abriter le roi des échecs, puis le pion va à dame."
            PHILIDOR_DRAW -> "Gardez la tour sur votre troisième rangée pour empêcher le roi blanc d'avancer ; dès que le pion y arrive, donnez des échecs par derrière."
            ROOK_PAWN_DRAW -> "Gardez votre roi dans le coin, devant le pion tour : personne ne peut l'en chasser."
            KQKP -> "Par des échecs et des attaques du pion, forcez le roi noir à se placer devant son pion ; chaque fois, rapprochez votre roi d'un pas."
            QUEEN_BISHOP_PAWN_DRAW -> "Réfugiez votre roi dans le coin, à côté du pion : si la dame prend le pion, c'est pat."
            VANCURA_DRAW -> "Votre tour attaque le pion tour de côté ; donnez des échecs dès que le roi blanc s'approche du pion."
        }

    /** Consigne complète : « Matez le roi noir en 12 coups au plus. » */
    val instruction: String
        get() = task.replaceFirstChar { it.uppercase() } +
            if (goal == EndgameGoal.WIN) " en $maxMoves coups au plus." else " pendant $maxMoves coups."

    companion object {
        fun of(name: String): EndgameFamily? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Générateurs de positions de départ légales et conformes à la théorie (gain ou nulle connus).
 * Positions aléatoires (symétries, colonnes, rangées) pour ne jamais rejouer la même : on apprend la méthode.
 */
object EndgamePositions {
    fun generate(f: EndgameFamily, rnd: Random): Position {
        repeat(5_000) { candidate(f, rnd)?.let { return it } }
        error("aucune position trouvée pour $f")
    }

    private fun dist(a: Int, b: Int) = max(abs(fileOf(a) - fileOf(b)), abs(rankOf(a) - rankOf(b)))

    /** FEN à partir d'une liste (case, pièce en lettre FEN). */
    fun fen(pieces: List<Pair<Int, Char>>, whiteToMove: Boolean): String {
        val board = arrayOfNulls<Char>(64)
        for ((q, p) in pieces) { if (board[q] != null) return ""; board[q] = p }
        val rows = (7 downTo 0).joinToString("/") { r ->
            val sb = StringBuilder(); var empty = 0
            for (fl in 0..7) {
                val p = board[sq(fl, r)]
                if (p == null) empty++ else { if (empty > 0) sb.append(empty); empty = 0; sb.append(p) }
            }
            if (empty > 0) sb.append(empty)
            sb.toString()
        }
        return "$rows ${if (whiteToMove) "w" else "b"} - - 0 1"
    }

    private fun valid(fen: String, f: EndgameFamily): Position? {
        if (fen.isEmpty()) return null
        val p = runCatching { Position.fromFen(fen) }.getOrNull() ?: return null
        val wk = p.kingSquare(true); val bk = p.kingSquare(false)
        if (wk < 0 || bk < 0 || dist(wk, bk) < 2) return null
        // Le camp qui n'a pas le trait ne doit pas être en échec ; le camp au trait ne l'est pas non plus.
        if (p.inCheck || Position.fromFen(fen.replace(if (p.whiteToMove) " w " else " b ", if (p.whiteToMove) " b " else " w ")).inCheck) return null
        if (p.legalMoves.isEmpty()) return null
        // Le joueur ne doit pas perdre une pièce tout de suite : aucune pièce blanche prise sans protection.
        return p.takeIf { f.heroWhite == p.whiteToMove }
    }

    private fun candidate(f: EndgameFamily, rnd: Random): Position? {
        fun any() = rnd.nextInt(64)
        fun center() = sq(2 + rnd.nextInt(4), 2 + rnd.nextInt(4))
        val mirror = rnd.nextBoolean()
        fun m(q: Int) = if (mirror) sq(7 - fileOf(q), rankOf(q)) else q
        return when (f) {
            EndgameFamily.KQK, EndgameFamily.KRK, EndgameFamily.KRRK, EndgameFamily.KBBK -> {
                val bk = center()
                val pieces = arrayListOf(bk to 'k', any() to 'K')
                when (f) {
                    EndgameFamily.KQK -> pieces.add(any() to 'Q')
                    EndgameFamily.KRK -> pieces.add(any() to 'R')
                    EndgameFamily.KRRK -> { pieces.add(any() to 'R'); pieces.add(any() to 'R') }
                    else -> {
                        val light = generateSequence { any() }.first { (fileOf(it) + rankOf(it)) % 2 == 1 }
                        val dark = generateSequence { any() }.first { (fileOf(it) + rankOf(it)) % 2 == 0 }
                        pieces.add(light to 'B'); pieces.add(dark to 'B')
                    }
                }
                // Aucune pièce blanche à portée immédiate du roi noir (sinon elle tomberait).
                if (pieces.drop(2).any { dist(it.first, bk) < 2 }) return null
                valid(fen(pieces, true), f)
            }
            EndgameFamily.KPK_WIN -> {
                // Roi sur une case clé (deux rangées devant le pion, colonne voisine) : gain quel que soit le trait.
                val pf = 1 + rnd.nextInt(6)
                val pr = 1 + rnd.nextInt(4)
                val pawn = sq(pf, pr)
                val wk = sq(pf - 1 + rnd.nextInt(3), pr + 2)
                val bk = any()
                if (dist(bk, pawn) < 2 || rankOf(bk) <= pr) return null
                valid(fen(listOf(pawn to 'P', wk to 'K', bk to 'k'), true), f)
            }
            EndgameFamily.LUCENA -> {
                // Roi blanc devant son pion en 7e, roi noir coupé de deux colonnes, tour blanche entre eux,
                // tour noire de l'autre côté.
                val pf = 2 + rnd.nextInt(4)
                val dir = if (rnd.nextBoolean()) 1 else -1
                val bkFile = pf + 2 * dir
                val rookFile = pf + dir
                val farFiles = (0..7).filter { it * dir < (pf - dir) * dir }
                if (bkFile !in 0..7 || farFiles.isEmpty()) return null
                val pieces = listOf(
                    sq(pf, 6) to 'P', sq(pf, 7) to 'K', sq(bkFile, 5 + rnd.nextInt(2)) to 'k',
                    sq(rookFile, rnd.nextInt(3)) to 'R', sq(farFiles.random(rnd), rnd.nextInt(3)) to 'r',
                )
                valid(fen(pieces, true), f)
            }
            EndgameFamily.PHILIDOR_DRAW -> {
                // Pion en 4e rangée, roi blanc devant lui en 5e ; roi noir sur la case de promotion, tour noire en
                // 6e rangée (la défense est déjà en place : il faut la tenir).
                val pf = 2 + rnd.nextInt(4)
                val rookCol = generateSequence { rnd.nextInt(8) }.first { abs(it - pf) >= 2 }
                val wRookCol = generateSequence { rnd.nextInt(8) }.first { abs(it - pf) >= 2 && it != rookCol }
                val pieces = listOf(
                    m(sq(pf, 3)) to 'P', m(sq(pf, 4)) to 'K', m(sq(pf, 7)) to 'k',
                    m(sq(rookCol, 5)) to 'r', m(sq(wRookCol, 6)) to 'R',
                )
                valid(fen(pieces, false), f)
            }
            EndgameFamily.ROOK_PAWN_DRAW -> {
                // Pion tour, roi noir dans le coin de promotion ou tout près : on ne peut plus l'en chasser.
                val pr = 3 + rnd.nextInt(3)
                val pawn = m(sq(7, pr))
                val bk = m(listOf(sq(7, 7), sq(6, 7), sq(6, 6), sq(7, 6)).random(rnd))
                val wk = m(sq(4 + rnd.nextInt(3), pr - 1 + rnd.nextInt(3)))
                valid(fen(listOf(pawn to 'P', bk to 'k', wk to 'K'), false), f)
            }
            EndgameFamily.KQKP -> {
                // Pion central ou pion cavalier en 7e (pour les Blancs : 2e rangée), roi noir à côté sans le bloquer ;
                // la dame tient la case de promotion depuis la première rangée, le roi blanc est loin.
                val pf = listOf(1, 3, 4, 6).random(rnd)
                val pawn = sq(pf, 1)
                val bkFile = pf + listOf(-1, 1).random(rnd)
                if (bkFile !in 0..7) return null
                val bk = sq(bkFile, 1 + rnd.nextInt(2))
                val qf = generateSequence { rnd.nextInt(8) }.first { abs(it - pf) >= 3 }
                val queen = sq(qf, 0)
                val wk = sq(rnd.nextInt(8), 3 + rnd.nextInt(5))
                if (dist(queen, bk) < 2 || dist(wk, pawn) < 4) return null
                valid(fen(listOf(pawn to 'p', bk to 'k', queen to 'Q', wk to 'K'), true), f)
            }
            EndgameFamily.QUEEN_BISHOP_PAWN_DRAW -> {
                // Pion fou en 7e soutenu par le roi côté coin ; roi blanc loin : la prise du pion donnerait un pat.
                val pawn = m(sq(2, 1))
                val bk = m(listOf(sq(1, 1), sq(1, 0)).random(rnd))
                val queen = any()
                val wk = sq(rnd.nextInt(8), 4 + rnd.nextInt(4))
                if (dist(queen, bk) < 2 || dist(wk, pawn) < 5 || queen == m(sq(2, 0))) return null
                valid(fen(listOf(pawn to 'p', bk to 'k', queen to 'Q', wk to 'K'), false), f)
            }
            EndgameFamily.VANCURA_DRAW -> {
                // Pion tour en 6e, tour blanche devant lui ; tour noire sur la 6e rangée qui l'attaque de côté,
                // roi noir près de la case g7 ; roi blanc encore loin du pion.
                val pieces = listOf(
                    m(sq(0, 5)) to 'P', m(sq(0, 7)) to 'R', m(sq(5, 5)) to 'r',
                    m(listOf(sq(6, 6), sq(7, 6)).random(rnd)) to 'k', m(sq(2 + rnd.nextInt(6), rnd.nextInt(4))) to 'K',
                )
                valid(fen(pieces, false), f)
            }
        }
    }
}

/** Partie d'entraînement d'une finale, sur plusieurs positions. */
@Immutable
data class EndgameSession(
    val family: EndgameFamily,
    val index: Int = 0,
    val total: Int = 5,
    val results: List<Boolean> = emptyList(),
    val position: Position? = null,
    val last: Move? = null,
    val anim: BoardAnim? = null,
    /** Coups joués par l'élève sur la position en cours. */
    val moves: Int = 0,
    val thinking: Boolean = false,
    /** Indice en deux temps : 1 = pièce à jouer surlignée, 2 = flèche du coup. */
    val hint: Move? = null,
    val hintStage: Int = 0,
    /** L'Ordinateur cherche un indice : le bouton est désactivé pendant la recherche. */
    val hinting: Boolean = false,
    /** Position en cours terminée (gagnée, tenue ou perdue) ; série terminée. */
    val over: Boolean = false,
    val finished: Boolean = false,
    val message: String = "",
    val tone: Tone = Tone.INFO,
    /** Évaluation finale (centipions, point de vue des Blancs), montrée par la jauge une fois la position terminée. */
    val evalWhite: Int? = null,
    /** Réponse de l'Ordinateur qui punit le coup perdant (flèche rouge). */
    val punish: Move? = null,
    /** Position de départ, pour la rejouer à l'entraînement après un échec (sans changer le score). */
    val start: Position? = null,
    /** Position rejouée pour s'entraîner : son résultat ne compte pas. */
    val replay: Boolean = false,
    /** Issue du dernier rejeu d'entraînement (null : pas de rejeu terminé). */
    val replayOk: Boolean? = null,
    val pulse: Pulse? = null,
    /** Coup qu'il fallait jouer à la place du coup perdant (flèche verte quand on reprend le coup). */
    val better: Move? = null,
    /** Position juste avant le coup perdant, et coups déjà joués : « Reprendre le coup » y revient. */
    val before: Position? = null,
    val beforeMoves: Int = 0,
    /** Positions déjà vues depuis le départ (pièces, trait, roques, prise en passant) : détection de la triple répétition. */
    val history: List<String> = emptyList(),
) {
    val score: Int get() = results.count { it }
    val heroToMove: Boolean get() = position != null && !over && !thinking && position.whiteToMove == family.heroWhite
}

/**
 * Entraîneur de finales : génère les positions, fait défendre (ou attaquer) l'Ordinateur à pleine force et juge
 * chaque coup de l'élève. Gain : mat, ou promotion en gardant un avantage décisif ; échec si l'avantage retombe
 * (gain gâché), en cas de pat ou au-delà du nombre de coups. Nulle : tenir le nombre de coups sans que l'Ordinateur
 * prenne un avantage décisif (une nulle forcée, pat ou matériel insuffisant, est une réussite).
 * Aucune lecture vocale pendant la partie.
 */
class EndgameTrainer(
    private val scope: CoroutineScope,
    private val engine: ChessEngine,
    private val random: Random,
    private val movetimeMs: Int = 400,
    /** Fin de série : famille, positions réussies, positions jouées. */
    private val onFinished: suspend (EndgameFamily, Int, Int) -> Unit = { _, _, _ -> },
) {
    private val _state = MutableStateFlow<EndgameSession?>(null)
    val state: StateFlow<EndgameSession?> = _state.asStateFlow()
    private var job: Job? = null
    private var hintJob: Job? = null

    /**
     * Analyse de la position où l'élève a le trait (meilleur coup, évaluation), lancée dès qu'il a la main : le moteur
     * démarre et travaille pendant qu'il réfléchit. Elle sert de référence pour juger son coup, et donne l'indice tout de suite.
     */
    private var prepFen: String? = null
    private var prep: Deferred<SearchResult?>? = null

    private fun prepare(p: Position?) {
        if (p == null) return
        val fen = p.toFen()
        if (prepFen == fen) return
        prep?.cancel()
        prepFen = fen
        prep = scope.async { runCatchingSuspend { withTimeoutOrNull(ENGINE_WAIT) { engine.search(fen, movetimeMs) } }.getOrNull() }
    }

    private suspend fun prepared(fen: String): SearchResult? {
        val d = prep?.takeIf { prepFen == fen && !it.isCancelled } ?: return null
        return runCatchingSuspend { withTimeoutOrNull(ENGINE_WAIT) { d.await() } }.getOrNull()
    }

    fun start(family: EndgameFamily, total: Int = 5) {
        job?.cancel()
        _state.value = EndgameSession(family, total = total)
        show(0)
    }

    /** Ajoute une précision au bilan affiché (par exemple, la date de la prochaine révision). */
    fun note(text: String) = _state.update { it?.copy(message = it.message + text) }

    fun stop() {
        job?.cancel()
        hintJob?.cancel()
        prep?.cancel()
        prep = null
        prepFen = null
        _state.value = null
    }

    private fun show(index: Int) {
        val s = _state.value ?: return
        val p = EndgamePositions.generate(s.family, random)
        hintJob?.cancel()
        _state.value = s.copy(
            index = index, position = p, start = p, last = null, anim = null, moves = 0, thinking = false, hint = null, hintStage = 0, hinting = false,
            over = false, evalWhite = null, punish = null, replay = false, replayOk = null,
            better = null, before = null, beforeMoves = 0, history = listOf(p.key()),
            tone = Tone.INFO, message = "Position ${index + 1} sur ${s.total} : ${s.family.instruction}\n\nMéthode : ${s.family.method}",
        )
        prepare(p.takeIf { it.whiteToMove == s.family.heroWhite })
    }

    /**
     * Reprend le coup perdant, pour s'entraîner : la position revient juste avant l'erreur (ou au départ si l'échec
     * n'est pas dû à un coup précis), avec le bon coup en flèche verte. Le score de la série ne change pas.
     */
    fun retry() {
        val s = _state.value ?: return
        if (!s.over || s.finished || s.start == null || s.results.lastOrNull() != false) return
        job?.cancel()
        hintJob?.cancel()
        val back = s.before
        _state.value = s.copy(
            position = back ?: s.start, last = null, anim = null, moves = if (back != null) s.beforeMoves else 0, thinking = false,
            hint = null, hintStage = 0, hinting = false, over = false, evalWhite = null, punish = null, replay = true, tone = Tone.INFO,
            before = null,
            // Chaque coup de l'élève ajoute deux positions (le sien, puis la réponse) à l'historique.
            history = if (back != null) s.history.take(1 + 2 * s.beforeMoves) else listOf(s.start.key()),
            message = if (back != null && s.better != null) "Entraînement (hors score) : rejouez ce coup. La flèche verte montre le bon coup. ${s.family.method}"
            else "Entraînement (hors score) : rejouez la position. ${s.family.method}",
        )
        prepare(back ?: s.start)
    }

    fun next() {
        val s = _state.value ?: return
        if (!s.over) return
        if (s.index + 1 < s.total) show(s.index + 1)
        else {
            _state.value = s.copy(
                // Seuil : 3 positions sur 5 ; une révision n'en joue qu'une, qu'il suffit de réussir.
                finished = true, tone = if (s.score >= minOf(PASS, s.total)) Tone.GOOD else Tone.GOLD,
                message = when {
                    s.total == 1 -> if (s.score == 1) "Position réussie." else "Position manquée."
                    s.score >= PASS -> "${s.score} sur ${s.total} : vous maîtrisez cette finale."
                    else -> "${s.score} sur ${s.total} : il en faut $PASS pour valider. Relisez la leçon, puis réessayez."
                },
            )
            job = scope.launch { onFinished(s.family, s.score, s.total) }
        }
    }

    fun hint() {
        val s = _state.value ?: return
        if (!s.heroToMove || s.hinting) return
        // Second temps : la flèche du coup déjà trouvé, sans nouvelle recherche.
        if (s.hint != null && s.hintStage == 1) {
            _state.value = s.copy(hintStage = 2, tone = Tone.GOLD, message = "Indice 2/2 : suivez la flèche verte.")
            return
        }
        if (s.hint != null) return
        // Une seule recherche d'indice à la fois, sans toucher à la réponse de l'Ordinateur en cours.
        hintJob?.cancel()
        val fen = s.position!!.toFen()
        _state.update { it?.copy(hinting = true, tone = Tone.INFO, message = "L'Ordinateur cherche un bon coup…") }
        hintJob = scope.launch {
            val best = prepared(fen)?.best
                ?: runCatchingSuspend { withTimeoutOrNull(ENGINE_WAIT) { engine.search(fen, movetimeMs).best } }.getOrNull()
            _state.update { cur ->
                // La position a changé entre-temps (coup joué, position suivante) : l'indice est périmé.
                if (cur == null || cur.position?.toFen() != fen) cur?.copy(hinting = false)
                else cur.copy(
                    hinting = false, hint = best, hintStage = if (best != null) 1 else 0, tone = Tone.GOLD,
                    message = if (best != null) "Indice 1/2 : la pièce à jouer est surlignée en vert." else "Indice indisponible.",
                )
            }
        }
    }

    /** Coup de l'élève. */
    fun move(m: Move) {
        val s = _state.value ?: return
        val pos = s.position ?: return
        if (!s.heroToMove || !pos.isLegal(m)) return
        hintJob?.cancel()
        val after = pos.play(m)
        val moves = s.moves + 1
        _state.value = s.copy(
            position = after, last = m, anim = moveAnim(pos, m), moves = moves, thinking = true, hint = null, hintStage = 0, hinting = false, better = null,
            history = s.history + after.key(),
        )
        job = scope.launch {
            val f = s.family
            val promoted = m.promo != 0
            when {
                after.isCheckmate -> return@launch end(true, "Échec et mat ! Position gagnée en $moves coup${if (moves > 1) "s" else ""}.")
                after.legalMoves.isEmpty() -> return@launch if (f.goal == EndgameGoal.WIN) end(false, "Pat ! La partie est nulle : laissez toujours une case au roi adverse.")
                else end(true, "Pat : la nulle est acquise.")
                drawRule(after, s.history) != null -> return@launch drawn(drawRule(after, s.history)!!)
                f.goal == EndgameGoal.DRAW && after.noPawns() -> return@launch pawnGone(after)
                f == EndgameFamily.KQKP && after.noPawns() -> return@launch end(true, "Le pion est tombé : avec la dame contre le roi seul, le gain est assuré.")
                f.goal == EndgameGoal.WIN && after.noPawns() && f in PAWN_FAMILIES && !promoted ->
                    // Pion disparu : avec une tour (ou une dame) contre le roi seul, le gain reste assuré.
                    return@launch if (after.onlyKing(!f.heroWhite) && after.hasMajor(f.heroWhite)) end(true, "Le pion a disparu, mais il vous reste une pièce lourde contre le roi seul : le gain est assuré.")
                    else end(false, "Le pion est perdu : le gain s'est envolé.")
            }
            // Référence : l'évaluation de la position avant le coup (préparée pendant la réflexion de l'élève).
            val ref = prepared(pos.toFen())
            val res = runCatchingSuspend { withTimeoutOrNull(ENGINE_WAIT) { engine.search(after.toFen(), movetimeMs) } }.getOrNull()
            val reply = res?.best?.takeIf { after.isLegal(it) }
            if (res?.score == null || reply == null) {
                // Le moteur n'a pas répondu à temps (démarrage lent, téléphone occupé) : ce n'est pas la faute de l'élève.
                // Le coup est annulé et la position reste jouable, sans compter d'échec.
                _state.update {
                    it?.copy(
                        position = pos, last = null, anim = undoAnim(pos, m), moves = s.moves, thinking = false, tone = Tone.GOLD, history = s.history,
                        message = "L'Ordinateur n'a pas répondu à temps : rejouez votre coup.",
                    )
                }
                prepare(pos)
                return@launch
            }
            // Score du point de vue de l'élève (le moteur l'a donné pour l'adversaire, au trait).
            val mine = -res.score!!
            val below = when (f.goal) {
                EndgameGoal.WIN -> !(isMateScore(mine) && mine > 0) && mine < WIN_CP
                EndgameGoal.DRAW -> mine < -DRAW_CP
            }
            // Un coup n'est perdant que s'il fait nettement baisser l'évaluation de la position d'avant : si le moteur
            // sous-estime toute la position (recherche courte sur téléphone, ou nulle théorique qu'il croit perdue,
            // comme la défense Vancura), les bons coups ne sont pas refusés.
            val before = ref?.score
            val drop = if (f.goal == EndgameGoal.DRAW) DRAW_DROP_CP else DROP_CP
            val lost = below && (if (before == null) f !in MISJUDGED else before - mine >= drop)
            val ev = if (f.heroWhite) mine else -mine
            if (lost) {
                // L'Ordinateur montre comment il punit l'erreur (sa réponse, flèche rouge) et quel coup il fallait jouer.
                val best = (ref?.best ?: runCatchingSuspend { withTimeoutOrNull(ENGINE_WAIT) { engine.search(pos.toFen(), movetimeMs).best } }.getOrNull())
                    ?.takeIf { it != m && pos.isLegal(it) }
                delay(450)
                val punished = after.play(reply)
                _state.update {
                    it?.copy(position = punished, last = reply, anim = moveAnim(after, reply), punish = reply, better = best, before = pos, beforeMoves = s.moves)
                }
                val san = pos.san(m)
                val text = (if (f.goal == EndgameGoal.WIN) "$san laisse échapper le gain" else "$san perd la nulle") +
                    " : l'Ordinateur répond ${after.san(reply)}." +
                    (if (best != null) " Il fallait jouer ${pos.san(best)}." else "") +
                    "\n\nMéthode : ${f.method}\n\nReprenez le coup pour vous entraîner, ou passez à la position suivante."
                return@launch end(false, text, ev)
            }
            if (f.goal == EndgameGoal.WIN && promoted) return@launch end(true, "Le pion devient dame et le gain est assuré !", ev)
            delay(450)
            val next = after.play(reply)
            val seen = s.history + after.key()
            _state.update { it?.copy(position = next, last = reply, anim = moveAnim(after, reply), thinking = false, history = seen + next.key()) }
            when {
                next.isCheckmate -> end(false, "Échec et mat : l'Ordinateur a gagné.")
                next.legalMoves.isEmpty() -> end(f.goal == EndgameGoal.DRAW, if (f.goal == EndgameGoal.DRAW) "Pat : la nulle est acquise." else "Pat : la partie est nulle.")
                f.goal == EndgameGoal.DRAW && next.noPawns() -> pawnGone(next)
                drawRule(next, seen) != null -> drawn(drawRule(next, seen)!!, ev)
                f.goal == EndgameGoal.DRAW && moves >= f.maxMoves -> end(true, "Vous avez tenu $moves coups : la nulle est acquise.", ev)
                f.goal == EndgameGoal.WIN && moves >= f.maxMoves -> end(false, "Trop lent : il fallait conclure en ${f.maxMoves} coups.\n\nMéthode : ${f.method}", ev)
                else -> {
                    _state.update { it?.copy(tone = Tone.INFO, message = "Coup $moves sur ${f.maxMoves}. ${f.instruction}") }
                    prepare(next)
                }
            }
        }
    }

    /**
     * Fin de la position. [evalWhite] : évaluation finale (point de vue des Blancs) ; sans recherche, elle se déduit de
     * l'issue (mat : ±MATE, pat ou nulle : 0). Une position rejouée à l'entraînement ne compte pas dans la série.
     */
    private fun end(ok: Boolean, text: String, evalWhite: Int? = null) {
        _state.update { s ->
            if (s == null) return@update null
            val p = s.position
            val ev = evalWhite ?: when {
                p != null && p.isCheckmate -> if (p.whiteToMove) -MATE else MATE
                p != null && p.legalMoves.isEmpty() -> 0
                s.family.goal == EndgameGoal.DRAW && ok -> 0
                else -> null
            }
            s.copy(
                over = true, thinking = false, results = if (s.replay) s.results else s.results + ok, tone = if (ok) Tone.GOOD else Tone.BAD,
                replayOk = if (s.replay) ok else null,
                message = if (s.replay) "$text (entraînement, hors score)" else text, evalWhite = ev, pulse = Pulse(++pulses, ok),
            )
        }
    }

    private var pulses = 0L

    /** Nulle par la règle : triple répétition (échec perpétuel compris) ou 50 coups sans prise ni coup de pion. */
    private fun drawRule(p: Position, seen: List<String>): String? = when {
        seen.count { it == p.key() } >= 2 -> "Triple répétition"
        p.halfmove >= 100 -> "Règle des 50 coups"
        else -> null
    }

    private fun drawn(rule: String, evalWhite: Int? = null) {
        val f = _state.value?.family ?: return
        if (f.goal == EndgameGoal.DRAW) end(true, "$rule : la nulle est acquise.", 0)
        else end(false, "$rule : la partie est nulle, le gain s'est envolé.\n\nMéthode : ${f.method}", evalWhite ?: 0)
    }

    private fun Position.key(): String = toFen().split(' ').take(4).joinToString(" ")

    private fun Position.noPawns(): Boolean = (0 until 64).none { pieceType(this[it]) == PAWN }
    private fun Position.onlyKing(white: Boolean): Boolean = (0 until 64).none { this[it] != 0 && isWhite(this[it]) == white && pieceType(this[it]) != KING }
    private fun Position.hasMajor(white: Boolean): Boolean =
        (0 until 64).any { this[it] != 0 && isWhite(this[it]) == white && (pieceType(this[it]) == ROOK || pieceType(this[it]) == QUEEN) }

    /**
     * Nulle à tenir et plus aucun pion : nulle acquise, sauf si l'attaquant garde une dame contre le roi seul
     * (le pion fou du défenseur a été pris au lieu d'aller à dame).
     */
    private fun pawnGone(p: Position) {
        val queenLeft = (0 until 64).any { p[it] != 0 && pieceType(p[it]) == QUEEN && isWhite(p[it]) }
        val defenderPieces = (0 until 64).any { p[it] != 0 && !isWhite(p[it]) && pieceType(p[it]) != KING }
        if (queenLeft && !defenderPieces) end(false, "Le pion est tombé : la dame gagne contre le roi seul.")
        else end(true, "Le pion a disparu : la nulle est acquise.")
    }

    companion object {
        /** Positions réussies (sur 5) pour valider une finale. */
        const val PASS = 3
        /** En dessous de cet avantage (centipions), un gain n'est plus décisif. */
        const val WIN_CP = 250
        /** Baisse d'évaluation (centipions) au-delà de laquelle un coup est jugé fautif. */
        const val DROP_CP = 150
        /** Nulle à tenir : en dessous de cet avantage adverse (centipions), la nulle est menacée… */
        const val DRAW_CP = 150
        /** … et la baisse d'évaluation qui rend un coup fautif. */
        const val DRAW_DROP_CP = 120
        /**
         * Nulles théoriques que le moteur embarqué (sans tables de finales) croit perdues : sans évaluation de référence,
         * le coup n'y est jamais refusé ; seule une nette baisse par rapport à la position d'avant le condamne.
         */
        private val MISJUDGED = setOf(EndgameFamily.VANCURA_DRAW, EndgameFamily.PHILIDOR_DRAW, EndgameFamily.QUEEN_BISHOP_PAWN_DRAW)
        /** Attente maximale d'une réponse du moteur (démarrage compris, sur un téléphone lent). */
        const val ENGINE_WAIT = 10_000L
        private val PAWN_FAMILIES = setOf(EndgameFamily.KPK_WIN, EndgameFamily.LUCENA)
    }
}
