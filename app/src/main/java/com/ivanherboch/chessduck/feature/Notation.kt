package com.ivanherboch.chessduck.feature

import android.app.Application
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.chess.KING
import com.ivanherboch.chessduck.chess.KNIGHT
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.PAWN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_EN
import com.ivanherboch.chessduck.chess.PIECE_NAMES_FR
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.pieceLetter
import com.ivanherboch.chessduck.chess.pieceType
import com.ivanherboch.chessduck.chess.sqName
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.Puzzle
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.AutoText
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Un exercice de notation : position légale réelle + coup légal à transcrire en SAN. */
data class NotationExercise(val before: Position, val move: Move, val san: String, val level: Int)

data class NotationState(
    val level: Int = 0,
    val exercise: NotationExercise? = null,
    val shown: Boolean = false,
    val anim: BoardAnim? = null,
    val flipped: Boolean = false,
    val input: String = "",
    val message: String = "Observez le coup joué sur l'échiquier, puis écrivez-le en notation anglaise FIDE.",
    val tone: Tone = Tone.INFO,
    val solved: Boolean = false,
    val hintStage: Int = 0,
    val streak: Int = 0,
)

object NotationLogic {
    val LEVELS = listOf("Tous", "1. Bases", "2. Prises & Échecs", "3. Roques & Avancé")

    /** Niveau pédagogique d'un coup : 1 = bases, 2 = prises & échecs, 3 = roques & avancé. */
    fun levelOf(p: Position, m: Move, san: String): Int {
        val body = san.trimEnd('+', '#')
        val disamb = pieceType(p[m.from]) != PAWN && !p.isCastle(m) && body.replace("x", "").length > 3
        return when {
            p.isCastle(m) || m.promo != 0 || san.endsWith("#") || p.isEnPassant(m) || disamb -> 3
            p.isCapture(m) || san.endsWith("+") -> 2
            else -> 1
        }
    }

    /** Choisit dans [positions] un coup légal du niveau demandé (0 = n'importe lequel). */
    fun pick(positions: List<Position>, level: Int, rnd: Random, wantKind: Int = 0): NotationExercise? {
        val pool = ArrayList<NotationExercise>()
        for (p in positions.shuffled(rnd)) {
            if (p.legalMoves.isEmpty()) continue
            for (m in p.legalMoves) {
                val san = p.san(m)
                val lv = levelOf(p, m, san)
                if (level != 0 && lv != level) continue
                if (wantKind != 0 && !isKind(p, m, san, wantKind)) continue
                pool.add(NotationExercise(p, m, san, lv))
            }
            if (pool.size >= 12) break
        }
        if (pool.isEmpty()) return null
        // Varie les types de pièces plutôt que de favoriser celles qui ont le plus de coups.
        val byType = pool.groupBy { pieceType(it.before[it.move.from]) }
        return byType.values.random(rnd).random(rnd)
    }

    /** Sous-familles du niveau 3 : 1 roque, 2 promotion, 3 mat, 4 désambiguïsation. */
    fun isKind(p: Position, m: Move, san: String, kind: Int): Boolean = when (kind) {
        1 -> p.isCastle(m)
        2 -> m.promo != 0
        3 -> san.endsWith("#")
        4 -> pieceType(p[m.from]) != PAWN && !p.isCastle(m) && san.trimEnd('+', '#').replace("x", "").length > 3
        else -> true
    }

    private fun nameOf(type: Int) = "${PIECE_NAMES_FR[type]} (${PIECE_NAMES_EN[type]})"

    /** Diagnostic pédagogique d'une réponse fausse, sans donner la solution. */
    fun diagnose(input: String, ex: NotationExercise): String {
        val san = ex.san
        val p = ex.before
        val m = ex.move
        val type = pieceType(p[m.from])
        val castle = p.isCastle(m)
        if (input.isEmpty()) return "Écrivez d'abord le coup avec le clavier ci-dessous."
        if (input.startsWith("P")) return "Les pions n'ont pas de lettre : on écrit « e4 », jamais « Pe4 »."
        if (castle) {
            return if (input.startsWith("O-O")) "Attention au côté : O-O = petit roque (côté roi), O-O-O = grand roque (côté dame)."
            else "Le roi se déplace de deux cases : c'est un roque. Il se note O-O (petit) ou O-O-O (grand)."
        }
        if (input.startsWith("O")) return "Ce coup n'est pas un roque : commencez par la lettre de la pièce (rien pour un pion)."
        val inBody = input.trimEnd('+', '#')
        val sanBody = san.trimEnd('+', '#')
        val inSuffix = input.substring(inBody.length)
        val sanSuffix = san.substring(sanBody.length)
        if (inBody == sanBody) {
            return when {
                sanSuffix == "#" && inSuffix == "+" -> "C'est plus qu'un échec : le roi n'a aucune parade. L'échec et mat se note « # »."
                sanSuffix == "#" -> "Ce coup donne échec et mat : terminez par « # »."
                sanSuffix == "+" && inSuffix == "#" -> "Le roi peut encore parer : c'est un simple échec, noté « + »."
                sanSuffix == "+" -> "Ce coup donne échec au roi : ajoutez « + » à la fin."
                else -> "Ce coup ne donne pas échec : retirez le « $inSuffix » final."
            }
        }
        val first = input[0]
        if (type == KNIGHT && first == 'K') return "N = Knight (Cavalier). La lettre K est réservée au King (Roi)."
        if (type == KING && first == 'N') return "K = King (Roi). La lettre N désigne le Knight (Cavalier)."
        if (type == PAWN && first.isUpperCase()) return "C'est un pion qui joue : aucune lettre de pièce, on commence par la colonne."
        if (type != PAWN && first != pieceLetter(type)) {
            return "La pièce jouée est un(e) ${nameOf(type)} : sa lettre est « ${pieceLetter(type)} »."
        }
        val capture = p.isCapture(m)
        if (capture && 'x' !in inBody) {
            return if (type == PAWN) "Le pion capture : colonne de départ, puis « x », puis la case d'arrivée (comme « exd5 »)."
            else "C'est une prise : ajoutez « x » juste avant la case d'arrivée."
        }
        if (!capture && 'x' in inBody) return "Aucune pièce n'est capturée : pas de « x » dans ce coup."
        if (m.promo != 0) {
            if ('=' !in inBody) return "Le pion atteint la dernière rangée : indiquez la promotion avec « = » et la pièce (ex. « =Q »)."
            if (inBody.substringAfter('=') != sanBody.substringAfter('=')) return "Regardez bien la pièce apparue : la lettre après « = » n'est pas la bonne."
        } else if ('=' in inBody) return "Pas de promotion ici : le « = » ne sert que lorsqu'un pion atteint la dernière rangée."
        val dest = sqName(m.to)
        if (dest !in inBody) return "La case d'arrivée est inexacte : repérez la colonne (a–h) puis la rangée (1–8) de la case où la pièce se pose."
        if (type == PAWN && capture && inBody[0] != sanBody[0]) return "Pour une prise de pion, on commence par la colonne de départ du pion."
        if (type != PAWN) {
            val inCore = inBody.replace("x", "")
            val sanCore = sanBody.replace("x", "")
            if (sanCore.length > 3 && inCore.length == 3) {
                return "Deux ${PIECE_NAMES_FR[type].lowercase()}s peuvent aller sur $dest : précisez la colonne (ou la rangée) de départ, comme dans « Rad1 »."
            }
            if (sanCore.length == 3 && inCore.length > 3) return "Une seule pièce peut jouer ce coup : inutile de préciser sa case de départ."
            if (sanCore.length > 3) return "La précision de départ est inexacte : indiquez la colonne de la pièce qui joue (ou sa rangée si les colonnes sont identiques)."
        }
        return "Presque ! Ordre à respecter : pièce, (départ), (x), case d'arrivée, (=promotion), (+ ou #)."
    }

    fun hint(ex: NotationExercise, stage: Int): String {
        val p = ex.before
        val m = ex.move
        val type = pieceType(p[m.from])
        if (stage >= 2) return "Solution : ${ex.san} — recopiez-la pour la mémoriser."
        val sb = StringBuilder()
        when {
            p.isCastle(m) -> sb.append("Le roi et la tour bougent ensemble : c'est un roque (O-O ou O-O-O).")
            type == PAWN -> sb.append("Un pion joue : pas de lettre de pièce.")
            else -> sb.append("Pièce : ${nameOf(type)} → lettre « ${pieceLetter(type)} ».")
        }
        if (p.isCapture(m)) sb.append(" C'est une prise (x).")
        if (m.promo != 0) sb.append(" Il y a promotion (=).")
        if (ex.san.endsWith("#")) sb.append(" Le coup fait mat (#).") else if (ex.san.endsWith("+")) sb.append(" Le coup donne échec (+).")
        return sb.toString()
    }

    /** Exercices de secours si la base n'est pas disponible. */
    val FALLBACK = listOf(
        "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
        "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 6 5",
        "r3k2r/ppp2ppp/2n1bn2/3qp3/8/2NP1N2/PPP1BPPP/R2Q1RK1 b kq - 0 9",
        "8/4P1k1/8/8/8/8/1p4K1/8 w - - 0 1",
        "3r2k1/5ppp/8/8/8/8/5PPP/R2R2K1 w - - 0 1",
    )
}

class NotationViewModel(app: Application) : AndroidViewModel(app) {
    private val duck = app as ChessDuckApp
    private val rnd = Random(System.nanoTime())
    val state = MutableStateFlow(NotationState())
    private var showJob: Job? = null

    init {
        viewModelScope.launch {
            val lv = duck.prefs.settings.first().notationLevel
            state.update { it.copy(level = lv) }
            next()
        }
    }

    fun setLevel(level: Int) {
        state.update { it.copy(level = level) }
        viewModelScope.launch { duck.prefs.setNotationLevel(level) }
        next()
    }

    fun next() {
        viewModelScope.launch {
            val level = state.value.level
            val ex = withContext(Dispatchers.Default) { generate(level) }
            state.update {
                it.copy(
                    exercise = ex, shown = false, anim = null, flipped = !ex.before.whiteToMove, input = "", solved = false, hintStage = 0,
                    message = "Quel coup vient d'être joué ? Écrivez-le en notation anglaise FIDE.", tone = Tone.INFO,
                )
            }
            play(450)
        }
    }

    private suspend fun generate(level: Int): NotationExercise {
        val dao = duck.db.puzzles()
        val target = if (level == 0) rnd.nextInt(1, 4) else level
        runCatching {
            repeat(6) {
                val start = rnd.nextInt(1, DuckDatabase.PUZZLE_COUNT - 40)
                val kind = if (target == 3) rnd.nextInt(1, 5) else 0
                val batch: List<Puzzle> = when (kind) {
                    1 -> dao.sliceCastling(start, 60)
                    2 -> dao.sliceTheme(start, "promotion", 30)
                    3 -> dao.sliceTheme(start, "mateIn1", 30)
                    else -> dao.slice(start, 30)
                }
                val positions = batch.flatMap { pz ->
                    val line = runCatching { Line.fromUci(pz.fen, pz.moves) }.getOrNull() ?: return@flatMap emptyList()
                    when (kind) {
                        1 -> listOf(line.positions[0], line.positions[1])
                        3 -> listOf(line.positions[line.size - 1])
                        else -> line.positions.dropLast(1)
                    }
                }
                NotationLogic.pick(positions, target, rnd, kind)?.let { return it }
            }
        }
        val positions = NotationLogic.FALLBACK.map { Position.fromFen(it) }
        return NotationLogic.pick(positions, target, rnd) ?: NotationLogic.pick(positions, 0, rnd)!!
    }

    private fun play(delayMs: Long) {
        showJob?.cancel()
        showJob = viewModelScope.launch {
            delay(delayMs)
            val ex = state.value.exercise ?: return@launch
            state.update { it.copy(shown = true, anim = moveAnim(ex.before, ex.move)) }
        }
    }

    /** Rejoue l'animation une seule fois, à la demande. */
    fun replay() {
        state.update { it.copy(shown = false, anim = null) }
        play(350)
    }

    fun flip() = state.update { it.copy(flipped = !it.flipped) }

    fun type(key: String) {
        val s = state.value
        if (s.solved) return
        if (s.input.length + key.length > 9) return
        state.update { it.copy(input = if (key.startsWith("O-O")) key else it.input + key) }
    }

    fun backspace() = state.update { if (it.solved) it else it.copy(input = it.input.dropLast(1)) }
    fun clear() = state.update { if (it.solved) it else it.copy(input = "") }

    fun hint() {
        val s = state.value
        val ex = s.exercise ?: return
        if (s.solved) return
        val stage = (s.hintStage + 1).coerceAtMost(2)
        state.update { it.copy(hintStage = stage, message = NotationLogic.hint(ex, stage), tone = Tone.GOLD) }
    }

    /** Valide la saisie ; renvoie vrai si la réponse est exacte. */
    fun validate(): Boolean {
        val s = state.value
        val ex = s.exercise ?: return false
        if (s.input == ex.san) {
            state.update {
                it.copy(
                    solved = true, tone = Tone.GOOD, streak = it.streak + 1,
                    message = "✅ Exact : ${ex.san}. " + explain(ex),
                )
            }
            return true
        }
        state.update { it.copy(tone = Tone.BAD, streak = 0, message = "❌ " + NotationLogic.diagnose(s.input, ex)) }
        return false
    }

    private fun explain(ex: NotationExercise): String {
        val p = ex.before
        val m = ex.move
        val type = pieceType(p[m.from])
        return when {
            p.isCastle(m) -> if (ex.san.startsWith("O-O-O")) "Grand roque, côté dame." else "Petit roque, côté roi."
            type == PAWN && m.promo != 0 -> "Le pion est promu en ${PIECE_NAMES_FR[m.promo]} (${PIECE_NAMES_EN[m.promo]})."
            type == PAWN -> "Le pion va en ${sqName(m.to)}" + if (p.isCapture(m)) " en capturant." else "."
            else -> "${PIECE_NAMES_FR[type]} (${PIECE_NAMES_EN[type]}) en ${sqName(m.to)}" + if (p.isCapture(m)) ", avec prise." else "."
        }
    }
}

@Composable
fun NotationScreen(vm: NotationViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val ex = s.exercise
    ModuleLayout(
        bottomMin = 262.dp,
        top = {
            EqualRow(Modifier.padding(top = 4.dp)) {
                DuckButton("⇅ Retourner", vm::flip, Modifier.weight(1f), height = 36.dp)
                DuckButton("🔄 Revoir le coup", vm::replay, Modifier.weight(1.25f), height = 36.dp)
                DuckButton("💡 Indice", vm::hint, Modifier.weight(1f), height = 36.dp, accent = c.green)
                DuckButton("🎲 Nouveau", vm::next, Modifier.weight(1f), height = 36.dp, accent = c.gold)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                NotationLogic.LEVELS.forEachIndexed { i, label ->
                    DuckChip(label, s.level == i, { vm.setLevel(i) }, Modifier.weight(if (i == 0) 0.6f else if (i == 1) 0.8f else 1.3f), accent = c.blue)
                }
            }
        },
        board = {
            if (ex != null) {
                val pos = if (s.shown) ex.before.play(ex.move) else ex.before
                ChessBoard(
                    position = pos,
                    modifier = Modifier.fillMaxSize(),
                    flipped = s.flipped,
                    lastMove = if (s.shown) ex.move else null,
                    anim = s.anim,
                    arrows = if (s.shown) listOf(Arrow(ex.move.from, ex.move.to, c.blue)) else emptyList(),
                    badges = if (s.shown) listOfNotNull(captureBadge(ex.before, ex.move)) else emptyList(),
                )
            }
        },
        bottom = {
            FeedbackCard(s.message, s.tone, Modifier.padding(top = 5.dp).weight(1f), maxSize = 13.sp)
            // Barre de saisie
            Row(Modifier.fillMaxWidth().padding(top = 5.dp).height(42.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier.weight(1.5f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(c.surface).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AutoText(
                        s.input.ifEmpty { "Votre réponse…" },
                        color = if (s.input.isEmpty()) c.muted else if (s.solved) c.green else c.text,
                        maxSize = if (s.input.isEmpty()) 14.sp else 22.sp, weight = FontWeight.Bold,
                    )
                }
                DuckButton("⌫", vm::backspace, Modifier.weight(0.5f), height = 42.dp, maxSize = 18.sp)
                DuckButton("✕", vm::clear, Modifier.weight(0.5f), height = 42.dp, maxSize = 16.sp)
                if (s.solved) DuckButton("Suivant →", vm::next, Modifier.weight(1.1f), accent = c.green, filled = true, height = 42.dp, maxSize = 15.sp)
                else DuckButton("Valider", { if (vm.validate()) fb?.success() else fb?.error() }, Modifier.weight(1.1f), accent = c.gold, filled = true, height = 42.dp, maxSize = 15.sp)
            }
            // Clavier virtuel FIDE (aucun clavier système)
            Column(Modifier.padding(top = 5.dp, bottom = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((k, sub) in listOf("K" to "King", "Q" to "Queen", "R" to "Rook", "B" to "Bishop", "N" to "Knight")) {
                        Key(k, sub, Modifier.weight(1f)) { vm.type(k) }
                    }
                }
                Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (ch in 'a'..'h') Key(ch.toString(), null, Modifier.weight(1f)) { vm.type(ch.toString()) }
                }
                Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (ch in '1'..'8') Key(ch.toString(), null, Modifier.weight(1f)) { vm.type(ch.toString()) }
                }
                Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in listOf("x", "+", "#", "=")) Key(k, null, Modifier.weight(1f)) { vm.type(k) }
                    Key("O-O", null, Modifier.weight(1.4f)) { vm.type("O-O") }
                    Key("O-O-O", null, Modifier.weight(1.8f)) { vm.type("O-O-O") }
                }
            }
        },
    )
}

@Composable
private fun Key(label: String, sub: String?, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(c.surfaceHi).clickable { fb?.key(); onClick() },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AutoText(label, maxSize = if (sub != null) 18.sp else 17.sp, weight = FontWeight.Bold, align = TextAlign.Center)
        if (sub != null) AutoText(sub, color = c.muted, maxSize = 10.sp, align = TextAlign.Center)
    }
}

/** Fiche « Mémo Anglais » : lettres FIDE et symboles. */
@Composable
fun MemoSheet(onClose: () -> Unit) {
    val c = LocalDuck.current
    DuckSheet("📖 Mémo Anglais — notation FIDE", onClose) {
        SectionTitle("LES PIÈCES")
        val pieces = listOf(
            Triple("K", "King", "Roi"), Triple("Q", "Queen", "Dame"), Triple("R", "Rook", "Tour"),
            Triple("B", "Bishop", "Fou"), Triple("N", "Knight", "Cavalier"), Triple("—", "Pawn", "Pion : aucune lettre"),
        )
        for ((l, en, fr) in pieces) MemoRow(l, "$en  →  $fr")
        SectionTitle("LES SYMBOLES")
        MemoRow("x", "Prise (capture) : Bxf7, exd5")
        MemoRow("+", "Échec au roi : Qh5+")
        MemoRow("#", "Échec et mat : Qxf7#")
        MemoRow("=", "Promotion du pion : e8=Q")
        MemoRow("O-O", "Petit roque (côté roi)")
        MemoRow("O-O-O", "Grand roque (côté dame)")
        SectionTitle("LES RÈGLES D'OR")
        for (t in listOf(
            "Un coup = lettre de la pièce + case d'arrivée : Nf3.",
            "Un pion ne prend jamais de lettre : e4. S'il capture, on indique sa colonne de départ : exd5.",
            "N comme kNight : le K est réservé au roi (King).",
            "Si deux pièces identiques peuvent aller sur la même case, on précise la colonne de départ (Rad1), sinon la rangée (N5f3).",
            "L'ordre est toujours : pièce, départ éventuel, x, case d'arrivée, =promotion, + ou #.",
        )) {
            Row(Modifier.padding(vertical = 3.dp)) {
                AutoText("•  ", color = c.gold, maxSize = 14.sp, weight = FontWeight.Bold)
                AutoText(t, maxSize = 14.sp, maxLines = 4, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MemoRow(key: String, text: String) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.height(34.dp).fillMaxWidth(0.22f).clip(RoundedCornerShape(8.dp)).background(c.surfaceHi), contentAlignment = Alignment.Center) {
            AutoText(key, color = c.gold, maxSize = 17.sp, weight = FontWeight.Bold, align = TextAlign.Center)
        }
        AutoText(text, maxSize = 14.sp, maxLines = 2, modifier = Modifier.padding(start = 10.dp).weight(1f))
    }
}
