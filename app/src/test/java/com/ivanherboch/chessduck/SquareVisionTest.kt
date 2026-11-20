package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.chess.sqFromName
import com.ivanherboch.chessduck.feature.SquareVision
import com.ivanherboch.chessduck.feature.VisionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SquareVisionTest {
    @Test
    fun colorsAndKnightDistances() {
        assertFalse(SquareVision.isLight(sqFromName("a1")))
        assertTrue(SquareVision.isLight(sqFromName("h1")))
        assertTrue(SquareVision.isLight(sqFromName("e4")))
        assertFalse(SquareVision.isLight(sqFromName("d4")))
        assertEquals(1, SquareVision.knightDistance(sqFromName("g1"), sqFromName("f3")))
        assertEquals(2, SquareVision.knightDistance(sqFromName("a1"), sqFromName("c5")))
        assertEquals(6, SquareVision.knightDistance(sqFromName("a1"), sqFromName("h8")))
        assertEquals(4, SquareVision.knightDistance(sqFromName("a1"), sqFromName("b2")))
        val p = Position.fromFen(SquareVision.withKnight(sqFromName("h8")))
        assertEquals("7N/8/8/8/8/8/8/8 w - - 0 1", p.toFen())
        val rnd = Random(3)
        repeat(200) {
            val q = SquareVision.question(rnd)
            if (q.kind == VisionKind.KNIGHT) assertTrue(SquareVision.knightDistance(q.square, q.target) in 1..4)
            assertTrue(SquareVision.prompt(q).isNotBlank())
        }
        assertEquals(VisionKind.entries.toSet(), List(60) { SquareVision.question(rnd).kind }.toSet())
    }
}
