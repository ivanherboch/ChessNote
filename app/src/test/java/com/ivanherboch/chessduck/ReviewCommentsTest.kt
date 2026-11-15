package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.engine.MATE
import com.ivanherboch.chessduck.feature.Analysis
import com.ivanherboch.chessduck.feature.ReviewComments
import com.ivanherboch.chessduck.ui.evalShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCommentsTest {
    /** Cas signalé : un mat en 1 manqué, après lequel la jauge indique encore un mat en 2. Texte et jauge concordent. */
    @Test
    fun missedMateAgreesWithTheEvalBar() {
        // 1. f3 e5 2. g4 : Dh4 mate, mais les Noirs jouent Fe7 ; la jauge d'après indique M2 (valeur d'analyse simulée).
        val line = Line.fromUci(START_FEN, "f2f3 e7e5 g2g4 f8e7")
        val analysis = listOf<Analysis?>(null, null, null, Analysis(-MATE + 1, null, "d8h4"), Analysis(-MATE + 4, null, "e7h4"))
        val g = GameRecord(
            date = 0, opponent = "Ordinateur", vsComputer = true, playerWhite = true, result = "*", outcome = 0,
            reason = "", eloBefore = 0, eloAfter = 0, moves = line.uciMoves(),
        )
        val (text, _) = ReviewComments.comment(g, line, 3, analysis)
        assertEquals("M2", evalShort(analysis[4]!!.eval))
        assertTrue(text, text.startsWith("⚠️ Mat en 1 manqué"))
        assertTrue(text, "Qh4# matait immédiatement" in text)
        assertTrue(text, "Après Be7, il lui faut encore 2 coups pour mater." in text)
    }
}
