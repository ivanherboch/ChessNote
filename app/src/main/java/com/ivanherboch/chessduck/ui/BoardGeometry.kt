package com.ivanherboch.chessduck.ui

import com.ivanherboch.chessduck.chess.fileOf
import com.ivanherboch.chessduck.chess.rankOf
import com.ivanherboch.chessduck.chess.sq
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Géométrie pure de l'échiquier (sans Compose ni Android), partagée par l'échiquier interactif (DrawScope),
 * les images exportées (android.graphics.Canvas) et la démo du mode enfant : case ↔ coordonnées selon
 * l'orientation, coude des sauts de Cavalier et forme des flèches.
 */
object BoardGeometry {
    /** Colonne d'affichage (0 = gauche) de la case [sq]. */
    fun column(sq: Int, flipped: Boolean): Int = if (flipped) 7 - fileOf(sq) else fileOf(sq)

    /** Rangée d'affichage (0 = haut) de la case [sq]. */
    fun row(sq: Int, flipped: Boolean): Int = if (flipped) rankOf(sq) else 7 - rankOf(sq)

    /** Case affichée en (colonne, rangée). */
    fun squareAt(column: Int, row: Int, flipped: Boolean): Int =
        if (flipped) sq(7 - column, row) else sq(column, 7 - row)

    /** Case sous le point (x, y) d'un échiquier dont les cases mesurent [cell]. */
    fun squareAt(x: Float, y: Float, cell: Float, flipped: Boolean): Int =
        squareAt((x / cell).toInt().coerceIn(0, 7), (y / cell).toInt().coerceIn(0, 7), flipped)

    /** Coude du « L » d'un saut de Cavalier de [from] à [to], ou -1 si ce n'est pas un saut de Cavalier. */
    fun knightCorner(from: Int, to: Int): Int {
        val df = abs(fileOf(to) - fileOf(from))
        val dr = abs(rankOf(to) - rankOf(from))
        if (!((df == 1 && dr == 2) || (df == 2 && dr == 1))) return -1
        return if (dr == 2) sq(fileOf(from), rankOf(to)) else sq(fileOf(to), rankOf(from))
    }

    /** Cases traversées par une flèche : départ, coude éventuel (Cavalier), arrivée. */
    fun arrowSquares(from: Int, to: Int): IntArray {
        val corner = knightCorner(from, to)
        return if (corner >= 0) intArrayOf(from, corner, to) else intArrayOf(from, to)
    }

    /**
     * Forme d'une flèche : tracé du fût ([shaftX], [shaftY], du départ décalé jusqu'à la base de la pointe)
     * et triangle de la pointe ([headX], [headY]).
     */
    class ArrowShape(val shaftX: FloatArray, val shaftY: FloatArray, val headX: FloatArray, val headY: FloatArray)

    /**
     * Calcule la flèche passant par les centres ([cx], [cy]) : départ décalé de 28 % de case, pointe dépassant
     * de 12 % de case, pointe de longueur [head] ouverte de ±0,52 rad (mêmes proportions à l'écran et à l'export).
     */
    fun arrowShape(cx: FloatArray, cy: FloatArray, cell: Float, head: Float): ArrowShape {
        val n = cx.size
        fun unit(i: Int, j: Int): Pair<Float, Float> {
            val dx = cx[j] - cx[i]
            val dy = cy[j] - cy[i]
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            return dx / len to dy / len
        }
        val (u0x, u0y) = unit(0, 1)
        val (u1x, u1y) = unit(n - 2, n - 1)
        val tipX = cx[n - 1] + u1x * cell * 0.12f
        val tipY = cy[n - 1] + u1y * cell * 0.12f
        val shaftX = FloatArray(n)
        val shaftY = FloatArray(n)
        shaftX[0] = cx[0] + u0x * cell * 0.28f
        shaftY[0] = cy[0] + u0y * cell * 0.28f
        for (i in 1 until n - 1) { shaftX[i] = cx[i]; shaftY[i] = cy[i] }
        shaftX[n - 1] = tipX - u1x * head * 0.85f
        shaftY[n - 1] = tipY - u1y * head * 0.85f
        val ang = atan2(u1y, u1x)
        val spread = 0.52f
        return ArrowShape(
            shaftX, shaftY,
            floatArrayOf(tipX, tipX - head * cos(ang - spread), tipX - head * cos(ang + spread)),
            floatArrayOf(tipY, tipY - head * sin(ang - spread), tipY - head * sin(ang + spread)),
        )
    }
}
