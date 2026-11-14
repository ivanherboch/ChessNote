package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.sqFromName
import com.ivanherboch.chessduck.ui.BoardGeometry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardGeometryTest {
    @Test
    fun squaresAndOrientation() {
        val a1 = sqFromName("a1")
        assertEquals(0, BoardGeometry.column(a1, false)); assertEquals(7, BoardGeometry.row(a1, false))
        assertEquals(7, BoardGeometry.column(a1, true)); assertEquals(0, BoardGeometry.row(a1, true))
        for (q in 0 until 64) for (f in listOf(false, true)) {
            assertEquals(q, BoardGeometry.squareAt(BoardGeometry.column(q, f), BoardGeometry.row(q, f), f))
            assertEquals(q, BoardGeometry.squareAt(BoardGeometry.column(q, f) * 10f + 5f, BoardGeometry.row(q, f) * 10f + 5f, 10f, f))
        }
    }

    @Test
    fun knightArrowsBendAndShapeIsSymmetric() {
        // g1-f3 : déplacement de deux rangées, le coude est sur g3.
        assertArrayEquals(intArrayOf(sqFromName("g1"), sqFromName("g3"), sqFromName("f3")), BoardGeometry.arrowSquares(sqFromName("g1"), sqFromName("f3")))
        assertArrayEquals(intArrayOf(sqFromName("e2"), sqFromName("e4")), BoardGeometry.arrowSquares(sqFromName("e2"), sqFromName("e4")))
        val shape = BoardGeometry.arrowShape(floatArrayOf(5f, 5f), floatArrayOf(75f, 5f), cell = 10f, head = 4.2f)
        assertEquals(5f, shape.headX[0], 1e-4f)
        assertEquals(5f - 1.2f, shape.headY[0], 1e-4f) // la pointe dépasse de 12 % de case
        assertEquals(75f - 2.8f, shape.shaftY[0], 1e-4f) // le fût démarre à 28 % de case
        assertEquals(shape.headX[1] - 5f, 5f - shape.headX[2], 1e-4f) // pointe symétrique
    }
}
