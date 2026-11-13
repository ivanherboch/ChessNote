package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.content.PuzzleThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonsTest {
    @Test
    fun catalogueHas113LegalDistinctLessons() {
        val all = Lessons.all
        assertEquals(113, all.size)
        assertEquals(37, all.count { it.category == LessonCategory.OPENING })
        assertEquals(38, all.count { it.category == LessonCategory.MIDDLEGAME })
        assertEquals(38, all.count { it.category == LessonCategory.ENDGAME })
        assertEquals(113, all.map { it.title }.toSet().size)
        assertEquals((1..113).toList(), all.map { it.id })
        for (l in all) {
            val line = l.line // lève une exception si un coup est illégal
            assertEquals(l.title, line.size, l.texts.size)
            assertTrue(l.title, line.positions[0].legalMoves.isNotEmpty())
            assertTrue(l.title, l.texts.all { it.length > 5 } && l.summary.length > 10)
            // Le joueur a au moins un coup à jouer en mode Défi.
            assertTrue(l.title, (0 until line.size).any { line.positions[it].whiteToMove == l.heroWhite })
        }
    }

    @Test
    fun fiftyOfficialThemes() {
        assertEquals(50, PuzzleThemes.all.size)
        assertEquals(50, PuzzleThemes.all.map { it.key }.toSet().size)
        assertTrue(PuzzleThemes.all.all { it.tip.length > 30 })
        assertEquals("fork", PuzzleThemes.primary(" crushing fork middlegame short ").key)
        assertEquals("smotheredMate", PuzzleThemes.primary(" mate mateIn2 smotheredMate middlegame ").key)
        // Un mat en 2 obtenu par attraction est classé « Mat en 2 », partout dans l'application.
        assertEquals("mateIn2", PuzzleThemes.primary(" attraction mate mateIn2 middlegame short ").key)
        assertEquals(PuzzleThemes.G_MATE, PuzzleThemes.groups.first())
        // #oWde7 porte « mat en 1 », « queue d'aronde » et « clouage » : il est rangé dans le plus caractéristique.
        assertEquals("dovetailMate", PuzzleThemes.primary(" dovetailMate mate mateIn1 middlegame oneMove pin ").key)
    }
}
