package com.ivanherboch.chessduck.chess

import kotlin.math.abs

/** Types de pièces. Une pièce = type (blanc) ou type + 8 (noir). 0 = case vide. */
const val PAWN = 1
const val KNIGHT = 2
const val BISHOP = 3
const val ROOK = 4
const val QUEEN = 5
const val KING = 6
const val BLACK = 8

fun pieceType(p: Int) = p and 7
fun isWhite(p: Int) = p in 1..6
fun isBlack(p: Int) = p > 8
fun fileOf(sq: Int) = sq and 7
fun rankOf(sq: Int) = sq shr 3
fun sq(file: Int, rank: Int) = rank * 8 + file
fun sqName(sq: Int) = "${'a' + fileOf(sq)}${'1' + rankOf(sq)}"
fun sqFromName(s: String) = sq(s[0] - 'a', s[1] - '1')

private const val PIECE_LETTERS = " PNBRQK"
fun pieceLetter(type: Int) = PIECE_LETTERS[type]
fun pieceFromChar(c: Char): Int {
    val t = PIECE_LETTERS.indexOf(c.uppercaseChar())
    return if (t <= 0) 0 else if (c.isUpperCase()) t else t + BLACK
}
fun pieceChar(p: Int): Char = if (p > 8) PIECE_LETTERS[p and 7].lowercaseChar() else PIECE_LETTERS[p]

val PIECE_NAMES_FR = arrayOf("", "Pion", "Cavalier", "Fou", "Tour", "Dame", "Roi")
val PIECE_NAMES_EN = arrayOf("", "Pawn", "Knight", "Bishop", "Rook", "Queen", "King")
val PIECE_VALUES = intArrayOf(0, 1, 3, 3, 5, 9, 100)

data class Move(val from: Int, val to: Int, val promo: Int = 0) {
    val uci: String get() = sqName(from) + sqName(to) + if (promo != 0) pieceLetter(promo).lowercaseChar() else ""

    companion object {
        fun fromUci(s: String): Move =
            Move(sqFromName(s.substring(0, 2)), sqFromName(s.substring(2, 4)), if (s.length > 4) pieceType(pieceFromChar(s[4])) else 0)
    }
}

enum class GameEnd { NONE, CHECKMATE, STALEMATE, INSUFFICIENT, FIFTY_MOVES, REPETITION }

const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/** Position d'échecs immuable : génération de coups légaux, FEN, SAN, UCI. */
class Position(
    val board: IntArray,
    val whiteToMove: Boolean,
    val castling: Int, // bits : 1 = K, 2 = Q, 4 = k, 8 = q
    val ep: Int,
    val halfmove: Int,
    val fullmove: Int,
) {
    operator fun get(sq: Int) = board[sq]

    private fun own(p: Int) = if (whiteToMove) isWhite(p) else isBlack(p)
    private fun enemy(p: Int) = if (whiteToMove) isBlack(p) else isWhite(p)

    fun kingSquare(white: Boolean): Int {
        val k = if (white) KING else KING + BLACK
        for (i in 0 until 64) if (board[i] == k) return i
        return -1
    }

    fun isAttacked(target: Int, byWhite: Boolean): Boolean = attackers(target, byWhite, true).isNotEmpty()

    /** Cases des pièces de la couleur donnée qui attaquent [target]. */
    fun attackers(target: Int, byWhite: Boolean, firstOnly: Boolean = false): List<Int> {
        val res = ArrayList<Int>(2)
        val c = if (byWhite) 0 else BLACK
        val f = fileOf(target)
        val r = rankOf(target)
        val pr = if (byWhite) r - 1 else r + 1
        if (pr in 0..7) {
            if (f > 0 && board[sq(f - 1, pr)] == PAWN + c) res.add(sq(f - 1, pr))
            if (f < 7 && board[sq(f + 1, pr)] == PAWN + c) res.add(sq(f + 1, pr))
        }
        if (firstOnly && res.isNotEmpty()) return res
        for (i in 0 until 8) {
            val nf = f + KN_DF[i]
            val nr = r + KN_DR[i]
            if (nf in 0..7 && nr in 0..7 && board[sq(nf, nr)] == KNIGHT + c) res.add(sq(nf, nr))
            val kf = f + K_DF[i]
            val kr = r + K_DR[i]
            if (kf in 0..7 && kr in 0..7 && board[sq(kf, kr)] == KING + c) res.add(sq(kf, kr))
        }
        if (firstOnly && res.isNotEmpty()) return res
        for (d in 0 until 8) {
            var nf = f + K_DF[d]
            var nr = r + K_DR[d]
            while (nf in 0..7 && nr in 0..7) {
                val p = board[sq(nf, nr)]
                if (p != 0) {
                    val t = p and 7
                    val diag = K_DF[d] != 0 && K_DR[d] != 0
                    if ((p >= BLACK) != byWhite && (t == QUEEN || (diag && t == BISHOP) || (!diag && t == ROOK))) {
                        res.add(sq(nf, nr))
                        if (firstOnly) return res
                    }
                    break
                }
                nf += K_DF[d]
                nr += K_DR[d]
            }
        }
        return res
    }

    val inCheck: Boolean by lazy { kingSquare(whiteToMove).let { it >= 0 && isAttacked(it, !whiteToMove) } }

    private fun pseudoMoves(): List<Move> {
        val out = ArrayList<Move>(48)
        for (from in 0 until 64) {
            val p = board[from]
            if (p == 0 || !own(p)) continue
            val f = fileOf(from)
            val r = rankOf(from)
            when (p and 7) {
                PAWN -> {
                    val dir = if (whiteToMove) 1 else -1
                    val startRank = if (whiteToMove) 1 else 6
                    val promoRank = if (whiteToMove) 7 else 0
                    val nr = r + dir
                    if (nr !in 0..7) continue
                    fun add(to: Int) {
                        if (rankOf(to) == promoRank) {
                            out.add(Move(from, to, QUEEN)); out.add(Move(from, to, ROOK))
                            out.add(Move(from, to, BISHOP)); out.add(Move(from, to, KNIGHT))
                        } else out.add(Move(from, to))
                    }
                    if (board[sq(f, nr)] == 0) {
                        add(sq(f, nr))
                        if (r == startRank && board[sq(f, r + 2 * dir)] == 0) out.add(Move(from, sq(f, r + 2 * dir)))
                    }
                    for (df in intArrayOf(-1, 1)) {
                        val nf = f + df
                        if (nf !in 0..7) continue
                        val to = sq(nf, nr)
                        if (enemy(board[to])) add(to) else if (to == ep && board[to] == 0) out.add(Move(from, to))
                    }
                }
                KNIGHT, KING -> {
                    val dfs = if (p and 7 == KNIGHT) KN_DF else K_DF
                    val drs = if (p and 7 == KNIGHT) KN_DR else K_DR
                    for (i in 0 until 8) {
                        val nf = f + dfs[i]
                        val nr = r + drs[i]
                        if (nf in 0..7 && nr in 0..7 && !own(board[sq(nf, nr)])) out.add(Move(from, sq(nf, nr)))
                    }
                    if (p and 7 == KING) addCastling(from, out)
                }
                else -> {
                    val t = p and 7
                    for (d in 0 until 8) {
                        val diag = K_DF[d] != 0 && K_DR[d] != 0
                        if ((t == BISHOP && !diag) || (t == ROOK && diag)) continue
                        var nf = f + K_DF[d]
                        var nr = r + K_DR[d]
                        while (nf in 0..7 && nr in 0..7) {
                            val q = board[sq(nf, nr)]
                            if (own(q)) break
                            out.add(Move(from, sq(nf, nr)))
                            if (q != 0) break
                            nf += K_DF[d]
                            nr += K_DR[d]
                        }
                    }
                }
            }
        }
        return out
    }

    private fun addCastling(from: Int, out: MutableList<Move>) {
        val home = if (whiteToMove) 4 else 60
        if (from != home) return
        val c = if (whiteToMove) 0 else BLACK
        val kBit = if (whiteToMove) 1 else 4
        val qBit = if (whiteToMove) 2 else 8
        val opp = !whiteToMove
        if (castling and kBit != 0 && board[home + 1] == 0 && board[home + 2] == 0 && board[home + 3] == ROOK + c &&
            !isAttacked(home, opp) && !isAttacked(home + 1, opp) && !isAttacked(home + 2, opp)
        ) out.add(Move(home, home + 2))
        if (castling and qBit != 0 && board[home - 1] == 0 && board[home - 2] == 0 && board[home - 3] == 0 &&
            board[home - 4] == ROOK + c && !isAttacked(home, opp) && !isAttacked(home - 1, opp) && !isAttacked(home - 2, opp)
        ) out.add(Move(home, home - 2))
    }

    val legalMoves: List<Move> by lazy {
        pseudoMoves().filter { m ->
            val n = play(m)
            val k = n.kingSquare(whiteToMove)
            k < 0 || !n.isAttacked(k, !whiteToMove)
        }
    }

    fun legalFrom(from: Int): List<Move> = legalMoves.filter { it.from == from }
    fun isLegal(m: Move) = legalMoves.contains(m)
    fun isCapture(m: Move) = board[m.to] != 0 || isEnPassant(m)
    fun isEnPassant(m: Move) = pieceType(board[m.from]) == PAWN && m.to == ep && board[m.to] == 0 && fileOf(m.from) != fileOf(m.to)
    fun isCastle(m: Move) = pieceType(board[m.from]) == KING && abs(fileOf(m.from) - fileOf(m.to)) == 2
    fun isPromotionMove(from: Int, to: Int) =
        pieceType(board[from]) == PAWN && (rankOf(to) == 7 || rankOf(to) == 0)

    /** Joue le coup (supposé pseudo-légal) et renvoie la nouvelle position. */
    fun play(m: Move): Position {
        val b = board.copyOf()
        val p = b[m.from]
        val t = p and 7
        val capture = b[m.to] != 0
        var newEp = -1
        b[m.from] = 0
        if (t == PAWN) {
            if (m.to == ep && !capture && fileOf(m.from) != fileOf(m.to)) b[sq(fileOf(m.to), rankOf(m.from))] = 0
            if (abs(m.to - m.from) == 16) newEp = (m.from + m.to) / 2
        }
        b[m.to] = if (m.promo != 0) m.promo + (p and BLACK) else p
        if (t == KING && abs(fileOf(m.to) - fileOf(m.from)) == 2) {
            if (fileOf(m.to) == 6) { b[m.to - 1] = b[m.to + 1]; b[m.to + 1] = 0 } else { b[m.to + 1] = b[m.to - 2]; b[m.to - 2] = 0 }
        }
        var c = castling
        fun touch(s: Int) {
            when (s) {
                4 -> c = c and 12
                60 -> c = c and 3
                7 -> c = c and 1.inv()
                0 -> c = c and 2.inv()
                63 -> c = c and 4.inv()
                56 -> c = c and 8.inv()
            }
        }
        touch(m.from); touch(m.to)
        return Position(
            b, !whiteToMove, c, newEp,
            if (t == PAWN || capture) 0 else halfmove + 1,
            if (whiteToMove) fullmove else fullmove + 1,
        )
    }

    val isCheckmate: Boolean get() = inCheck && legalMoves.isEmpty()
    val isStalemate: Boolean get() = !inCheck && legalMoves.isEmpty()

    val insufficientMaterial: Boolean
        get() {
            var minors = 0
            for (p in board) when (p and 7) {
                0, KING -> {}
                KNIGHT, BISHOP -> minors++
                else -> return false
            }
            return minors <= 1
        }

    /** Clé de répétition : placement, trait, roques, prise en passant. */
    val key: String get() = toFen().substringBeforeLast(' ').substringBeforeLast(' ')

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val p = board[sq(f, r)]
                if (p == 0) empty++ else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(pieceChar(p))
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (whiteToMove) " w " else " b ")
        if (castling == 0) sb.append('-') else {
            if (castling and 1 != 0) sb.append('K')
            if (castling and 2 != 0) sb.append('Q')
            if (castling and 4 != 0) sb.append('k')
            if (castling and 8 != 0) sb.append('q')
        }
        // Comme Lichess/Stockfish : la case e.p. n'est écrite que si la prise est réellement possible.
        val epLegal = ep >= 0 && legalMoves.any { isEnPassant(it) }
        sb.append(' ').append(if (epLegal) sqName(ep) else "-")
        sb.append(' ').append(halfmove).append(' ').append(fullmove)
        return sb.toString()
    }

    /** Notation algébrique FIDE (lettres anglaises) du coup légal [m]. */
    fun san(m: Move): String {
        val p = board[m.from]
        val t = p and 7
        val sb = StringBuilder()
        if (isCastle(m)) sb.append(if (fileOf(m.to) == 6) "O-O" else "O-O-O") else {
            val capture = isCapture(m)
            if (t == PAWN) {
                if (capture) sb.append('a' + fileOf(m.from))
            } else {
                sb.append(pieceLetter(t))
                val others = legalMoves.filter { it.to == m.to && it.from != m.from && board[it.from] == p }
                if (others.isNotEmpty()) {
                    val sameFile = others.any { fileOf(it.from) == fileOf(m.from) }
                    val sameRank = others.any { rankOf(it.from) == rankOf(m.from) }
                    if (!sameFile) sb.append('a' + fileOf(m.from))
                    else if (!sameRank) sb.append('1' + rankOf(m.from))
                    else sb.append(sqName(m.from))
                }
            }
            if (capture) sb.append('x')
            sb.append(sqName(m.to))
            if (m.promo != 0) sb.append('=').append(pieceLetter(m.promo))
        }
        val n = play(m)
        if (n.inCheck) sb.append(if (n.legalMoves.isEmpty()) '#' else '+')
        return sb.toString()
    }

    /** Retrouve le coup légal correspondant à une notation SAN (tolère l'absence de +, #, !, ?). */
    fun parseSan(san: String): Move? {
        val clean = san.trim().trimEnd('+', '#', '!', '?').replace("0", "O")
        return legalMoves.firstOrNull { san(it).trimEnd('+', '#') == clean }
    }

    companion object {
        private val KN_DF = intArrayOf(1, 2, 2, 1, -1, -2, -2, -1)
        private val KN_DR = intArrayOf(2, 1, -1, -2, -2, -1, 1, 2)
        private val K_DF = intArrayOf(1, 1, 1, 0, -1, -1, -1, 0)
        private val K_DR = intArrayOf(1, 0, -1, -1, -1, 0, 1, 1)

        val START: Position by lazy { fromFen(START_FEN) }

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            val b = IntArray(64)
            var r = 7
            var f = 0
            for (ch in parts[0]) {
                when {
                    ch == '/' -> { r--; f = 0 }
                    ch.isDigit() -> f += ch - '0'
                    else -> { b[sq(f, r)] = pieceFromChar(ch); f++ }
                }
            }
            var c = 0
            val cs = parts.getOrElse(2) { "-" }
            if ('K' in cs) c = c or 1
            if ('Q' in cs) c = c or 2
            if ('k' in cs) c = c or 4
            if ('q' in cs) c = c or 8
            val eps = parts.getOrElse(3) { "-" }
            return Position(
                b, parts.getOrElse(1) { "w" } == "w", c,
                if (eps == "-") -1 else sqFromName(eps),
                parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0,
                parts.getOrElse(5) { "1" }.toIntOrNull() ?: 1,
            )
        }
    }
}

/** Partie : position initiale + suite de coups, avec positions et SAN précalculés. */
class Line(val startFen: String, moves: List<Move> = emptyList()) {
    val positions = ArrayList<Position>()
    val moves = ArrayList<Move>()
    val sans = ArrayList<String>()

    init {
        positions.add(Position.fromFen(startFen))
        moves.forEach { push(it) }
    }

    val size get() = moves.size
    val last get() = positions.last()

    fun push(m: Move) {
        val p = positions.last()
        sans.add(p.san(m))
        moves.add(m)
        positions.add(p.play(m))
    }

    /** Copie indépendante limitée aux [n] premiers coups. */
    fun copy(n: Int = size): Line {
        val l = Line(startFen)
        val k = n.coerceIn(0, size)
        l.moves.addAll(moves.subList(0, k)); l.sans.addAll(sans.subList(0, k))
        l.positions.clear(); l.positions.addAll(positions.subList(0, k + 1))
        return l
    }

    fun truncate(n: Int) {
        while (moves.size > n) {
            moves.removeAt(moves.size - 1); sans.removeAt(sans.size - 1); positions.removeAt(positions.size - 1)
        }
    }

    fun uciMoves(upTo: Int = size) = moves.take(upTo).joinToString(" ") { it.uci }

    fun end(): GameEnd {
        val p = last
        if (p.legalMoves.isEmpty()) return if (p.inCheck) GameEnd.CHECKMATE else GameEnd.STALEMATE
        if (p.insufficientMaterial) return GameEnd.INSUFFICIENT
        if (p.halfmove >= 100) return GameEnd.FIFTY_MOVES
        val k = p.key
        if (positions.count { it.key == k } >= 3) return GameEnd.REPETITION
        return GameEnd.NONE
    }

    companion object {
        fun fromUci(startFen: String, uci: String): Line {
            val l = Line(startFen)
            uci.split(' ').filter { it.isNotBlank() }.forEach { l.push(Move.fromUci(it)) }
            return l
        }

        /** Construit une ligne depuis des coups notés en UCI ou en SAN. */
        fun fromMoves(startFen: String, tokens: List<String>): Line {
            val l = Line(startFen)
            for (t in tokens) {
                val p = l.last
                val uci = if (UCI.matches(t)) Move.fromUci(t).takeIf { p.isLegal(it) } else null
                l.push(uci ?: p.parseSan(t) ?: error("Coup illégal « $t » après ${l.sans.joinToString(" ")} ($startFen)"))
            }
            return l
        }

        private val UCI = Regex("[a-h][1-8][a-h][1-8][qrbn]?")

        /** Construit une ligne depuis des coups SAN ; lève une exception si un coup est illégal. */
        fun fromSan(startFen: String, sans: List<String>): Line {
            val l = Line(startFen)
            for (s in sans) l.push(l.last.parseSan(s) ?: error("Coup illégal « $s » après ${l.sans.joinToString(" ")} ($startFen)"))
            return l
        }
    }
}
