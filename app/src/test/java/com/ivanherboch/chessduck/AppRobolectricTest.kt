package com.ivanherboch.chessduck

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivanherboch.chessduck.data.DuckDatabase
import com.ivanherboch.chessduck.data.GameRecord
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.data.PuzzleProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Vérifie la base embarquée (schéma Room + 50 000 problèmes) et produit des captures de chaque écran. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class AppRobolectricTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun settle(ms: Long = 1500) {
        repeat(6) {
            Thread.sleep(ms / 6)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms / 6))
            rule.waitForIdle()
        }
    }

    private fun shot(name: String) {
        val dir = File(System.getenv("SHOT_DIR") ?: "build/screenshots").apply { mkdirs() }
        rule.onRoot().captureRoboImage(File(dir, "$name.png").path)
    }

    @Test
    fun prepackagedDatabaseMatchesRoomSchema() = runBlocking {
        val db = DuckDatabase.create(ApplicationProvider.getApplicationContext())
        assertEquals(50_000, db.puzzles().count())
        val p = db.puzzles().randomUnsolved(1200, 1599, "fork", "")
        assertNotNull(p)
        db.puzzles().markSolved(PuzzleProgress(p!!.id, 1L))
        assertEquals(listOf(p.id), db.puzzles().solvedIds().first())
        assertEquals(50_000, db.puzzles().allMeta().size)
        assertEquals(30, db.puzzles().slice(100, 30).size)
        db.user().markLesson(LessonProgress(3, 1L))
        assertEquals(listOf(3), db.user().masteredLessons().first())
        val id = db.user().insertGame(GameRecord(date = 1, opponent = "Stockfish 1200", vsComputer = true, playerWhite = true, result = "1-0", outcome = 1, reason = "échec et mat", eloBefore = 1200, eloAfter = 1216, moves = "e2e4 e7e5"))
        db.user().saveAnalysis(id, "10,5,e2e4")
        assertEquals(1, db.user().games().first().size)
        assertEquals(1, db.user().tally().first().first { it.outcome == 1 }.n)
        // Carte de partage : type de problème et conseil visibles.
        val line = com.ivanherboch.chessduck.chess.Line.fromUci(p.fen, p.moves)
        val bmp = com.ivanherboch.chessduck.feature.ShareCard.render(ApplicationProvider.getApplicationContext(), p, line.positions[1], line.positions[1].whiteToMove, line.moves[0])
        val dir = File(System.getenv("SHOT_DIR") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "6_partage.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        db.close()
    }

    @Test
    fun screens() {
        settle(2500)
        shot("1_notation")
        rule.onNodeWithText("📖 Mémo Anglais").performClick(); settle(800); shot("1b_memo")
        rule.onAllNodesWithText("✕").onLast().performClick(); settle(800)
        rule.onNodeWithText("Académie").performClick(); settle(1500); shot("2_academie")
        rule.onNodeWithText("🎯 Mode Défi (Pratique)").performClick(); settle(1500); shot("2b_defi")
        rule.onNodeWithText("Problèmes").performClick(); settle(2500); shot("3_problemes")
        rule.onAllNodesWithText("🧩", substring = true).onFirst().performClick(); settle(2000); shot("3b_catalogue")
        rule.onAllNodesWithText("✕").onLast().performClick(); settle(800)
        rule.onNodeWithText("Jeu Libre").performClick(); settle(2000); shot("4_jeu")
        rule.onNodeWithText("⚙️").performClick(); settle(800); shot("5_parametres")
        rule.onNodeWithText("☀️ Mode Jour (clair)").performClick(); settle(800)
        rule.onAllNodesWithText("✕").onLast().performClick(); settle(800); shot("4c_jeu_jour")
    }
}
