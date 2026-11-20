package com.ivanherboch.chessduck

import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.LessonKind
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.content.PuzzleThemes
import com.ivanherboch.chessduck.ui.stepTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonsTest {
    @Test
    fun catalogueHasLegalDistinctLessons() {
        val all = Lessons.all
        val counts = mapOf(
            LessonCategory.BASICS to 16, LessonCategory.OPENING to 37, LessonCategory.MIDDLEGAME to 61,
            LessonCategory.STRATEGY to 18, LessonCategory.ENDGAME to 54, LessonCategory.MODEL_GAMES to 8,
        )
        for ((cat, n) in counts) assertEquals(cat.label, n, all.count { it.category == cat })
        assertEquals(counts.values.sum(), all.size)
        assertEquals(all.size, all.map { it.title }.toSet().size)
        assertTrue(all.filter { it.category == LessonCategory.MODEL_GAMES }.all { it.kind == LessonKind.MODEL_GAME })
        // Identifiants fixes : 1–113 conservés, 114 l'Opéra, puis la suite sans trou (étape 3).
        assertEquals((1..all.size).toSet(), all.map { it.id }.toSet())
        // Cursus : palier 1 réservé aux bases, paliers 2 à 5 ailleurs ; ordre du catalogue par catégorie puis palier.
        assertTrue(all.all { (it.level == 1) == (it.category == LessonCategory.BASICS) && it.level in 1..5 })
        for (cat in Lessons.categories) {
            val levels = all.filter { it.category == cat }.map { it.level }
            assertEquals(cat.label, levels.sorted(), levels)
        }
        // Formats de l'étape 2 : variantes légales commentées, QCM complet, finales jouables connues.
        for (l in all) {
            l.variants.forEachIndexed { i, v ->
                val line = l.lineFor(i + 1)
                assertEquals(l.title, line.size, l.textsFor(i + 1).size)
                assertTrue(l.title, v.from in 1 until l.line.size && line.uciMoves(v.from) == l.line.uciMoves(v.from))
            }
            l.quiz?.let { assertTrue(l.title, it.wrong.size in 1..2 && it.explanation.length > 10) }
            assertTrue(l.title, l.drill.isEmpty() || com.ivanherboch.chessduck.feature.EndgameFamily.of(l.drill) != null)
        }
        val italian = all.first { it.title.startsWith("La partie italienne") }
        assertEquals(2, italian.variants.size)
        assertEquals(3, italian.ideas.size)
        assertEquals(2, italian.traps.size)
        assertTrue(italian.quiz != null)
        assertEquals(12, all.count { it.drill.isNotEmpty() })
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
        // Cas limites : chaîne vide, espaces multiples, thèmes inconnus → « milieu de partie ».
        assertEquals("middlegame", PuzzleThemes.primary("").key)
        assertEquals("middlegame", PuzzleThemes.primary("   long  short ").key)
        assertEquals("pin", PuzzleThemes.primary("pin   endgame").key)
        assertEquals(PuzzleThemes.all.indexOfFirst { it.key == "fork" }, PuzzleThemes.primaryIndex(" fork "))
    }

    /** Les leçons sont numérotées depuis leur début, même tirées d'une vraie partie ; « coup N sur M » compte les tours. */
    @Test
    fun lessonTurnsAreNumberedFromOne() {
        val l = Lessons.all.first { it.title == "L'enfilade" } // position réelle, les Noirs jouent d'abord
        assertEquals("1… Qxe5 · coup 1 sur 3", l.line.stepTitle(1, first = 1))
        assertEquals("2. Bf4 · coup 2 sur 3", l.line.stepTitle(2, first = 1))
        assertEquals("3. Bxb8 · coup 3 sur 3", l.line.stepTitle(4, first = 1))
        // Sans renumérotation (revue, problèmes), on garde les vrais numéros de la partie.
        assertEquals("16… Qxe5 · coup 16 sur 18", l.line.stepTitle(1))
    }
}
