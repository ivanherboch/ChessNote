package com.ivanherboch.chessduck

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

/** Vérifie la base embarquée (schéma Room + 100 000 problèmes) et produit des captures de chaque écran. */
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
        assertEquals(100_000, db.puzzles().count())
        val p = db.puzzles().byId("0000D")
        assertNotNull(p)
        db.puzzles().markSolved(PuzzleProgress(p!!.id, 1L))
        assertEquals(listOf(p.id), db.puzzles().solvedIds().first())
        assertEquals(100_000, db.puzzles().allMeta().size)
        // Une base déjà complète n'est pas réimportée.
        assertEquals(false, DuckDatabase.syncPuzzles(ApplicationProvider.getApplicationContext(), db))
        assertEquals(30, db.puzzles().slice(100, 30).size)
        db.user().markLesson(LessonProgress(3, 1L))
        assertEquals(listOf(3), db.user().masteredLessons().first())
        // La base embarquée (v1) est migrée en v2 : noms des joueurs et ELO de l'ordinateur.
        val id = db.user().insertGame(GameRecord(date = 1, opponent = "Ordinateur 1200", vsComputer = true, playerWhite = true, result = "1-0", outcome = 1, reason = "échec et mat", eloBefore = 1200, eloAfter = 1216, moves = "e2e4 e7e5", whiteName = "Ivan", blackName = "🤖 Ordinateur", opponentElo = 1200))
        assertEquals("Ivan", db.user().games().first().first().whiteName)
        assertEquals(1200, db.user().games().first().first().opponentElo)
        db.user().saveAnalysis(id, "10,5,e2e4")
        assertEquals(1, db.user().games().first().size)
        assertEquals(1, db.user().tally().first().first { it.outcome == 1 }.n)
        // Carte de partage : type de problème et conseil visibles.
        val line = com.ivanherboch.chessduck.chess.Line.fromUci(p.fen, p.moves)
        val bmp = com.ivanherboch.chessduck.feature.ShareCard.render(ApplicationProvider.getApplicationContext(), p, line.positions[1], line.positions[1].whiteToMove, line.moves[0])
        val dir = File(System.getenv("SHOT_DIR") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "6_partage.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val ctx: android.content.Context = ApplicationProvider.getApplicationContext()
        for ((name, id) in listOf("7_solution_courte" to "0000D", "8_solution_longue" to "005f3")) {
            val pz = db.puzzles().byId(id)!!
            val sol = com.ivanherboch.chessduck.feature.ShareCard.renderSolution(ctx, pz)
            File(dir, "$name.png").outputStream().use { sol.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertNotNull(db.puzzles().byIdIgnoreCase("0000d"))
        db.close()
    }

    @Test
    fun upgradeImportsNewPuzzlesAndKeepsProgress() = runBlocking {
        val old = File(System.getenv("OLD_DB") ?: "/opt/work/old50k.db")
        if (!old.exists()) return@runBlocking
        val ctx: android.content.Context = ApplicationProvider.getApplicationContext()
        // Simule une installation existante : l'ancienne base (v1, 50 000 problèmes) est déjà sur l'appareil.
        val target = ctx.getDatabasePath("chessduck.db").apply { parentFile?.mkdirs() }
        old.copyTo(target, overwrite = true)
        val db = DuckDatabase.create(ctx)
        assertEquals(50_000, db.puzzles().count())
        db.puzzles().markSolved(PuzzleProgress("0000D", 1L))
        assertEquals(true, DuckDatabase.syncPuzzles(ctx, db))
        assertEquals(100_000, db.puzzles().count())
        assertEquals(listOf("0000D"), db.puzzles().solvedIds().first())
        db.close()
    }

    @Test
    fun capturedBars() {
        // Pire cas : presque tout a été capturé des deux côtés ; la barre doit tout contenir sur 360 dp.
        val worst = com.ivanherboch.chessduck.chess.Position.fromFen("4k3/8/8/8/8/8/8/3QK3 w - - 0 1")
        val all = com.ivanherboch.chessduck.chess.Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        val few = com.ivanherboch.chessduck.chess.Position.fromFen("r1b1kb1r/pppppp2/8/8/8/8/1PPP1PPP/RNBQKB2 w Qkq - 0 1")
        rule.activity.setContent {
            androidx.compose.foundation.layout.Column {
                for (dark in listOf(true, false)) com.ivanherboch.chessduck.ui.ChessDuckTheme(dark) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.ivanherboch.chessduck.ui.LocalDuck.current.bg).padding(vertical = 8.dp)) {
                        for (p in listOf(few, worst, all)) com.ivanherboch.chessduck.ui.CapturedBar(p, androidx.compose.ui.Modifier.padding(vertical = 4.dp))
                    }
                }
                com.ivanherboch.chessduck.feature.KidTheme {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.ivanherboch.chessduck.ui.LocalDuck.current.bg).padding(8.dp)) {
                        for (p in listOf(few, worst, all)) com.ivanherboch.chessduck.ui.CapturedBar(p, androidx.compose.ui.Modifier.padding(vertical = 4.dp), kid = true)
                    }
                }
            }
        }
        settle(800)
        shot("10_prises")
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
        rule.onNodeWithText("🔕 Vibration désactivée").performClick(); settle(500)
        rule.onNodeWithText("🔇 Son désactivé").performClick(); settle(500)
        rule.onNodeWithText("☀️ Mode Jour (clair)").performClick(); settle(800)
        rule.onAllNodesWithText("✕").onLast().performClick(); settle(800); shot("4c_jeu_jour")
        // Mode enfant : écran épuré à deux joueurs, puis retour au Jeu Libre réinitialisé.
        rule.onNodeWithText("⚙️").performClick(); settle(800); shot("5b_parametres_jour")
        rule.onNodeWithText("🧒 Activer le mode enfant").performClick(); settle(1500); shot("9_enfant")
        // Leçons animées du mode enfant
        rule.onNodeWithText("Cavalier").performClick(); settle(1400); shot("9b_lecon_cavalier")
        rule.onNodeWithText("⬅️ Revenir à la partie").performClick(); settle(800)
        rule.onNodeWithText("Pion").performClick(); settle(900); shot("9c_lecon_pion")
        rule.onNodeWithText("⬅️ Revenir à la partie").performClick(); settle(800)
        rule.onNodeWithText("✕ Quitter le mode enfant").performClick(); settle(800); shot("9d_quitter")
        // « Non » referme la boîte et on reste en mode enfant ; « Oui » quitte.
        rule.onNodeWithText("Non, on continue à jouer").performClick(); settle(600)
        rule.onNodeWithText("✕ Quitter le mode enfant").performClick(); settle(600)
        rule.onNodeWithText("Oui, quitter").performClick(); settle(1500)
        // Régression : le mode enfant doit pouvoir être relancé plusieurs fois de suite.
        repeat(3) {
            rule.onNodeWithText("⚙️").performClick(); settle(800)
            rule.onNodeWithText("🧒 Activer le mode enfant").performClick(); settle(1200)
            rule.onNodeWithText("👆 Touche une pièce pour voir comment elle bouge !").assertExists()
            rule.onNodeWithText("✕ Quitter le mode enfant").performClick(); settle(600)
            rule.onNodeWithText("Oui, quitter").performClick(); settle(1200)
            rule.onNodeWithText("Jeu Libre").assertExists()
        }
        rule.onNodeWithText("Problèmes").performClick(); settle(1500)
        rule.onNodeWithText("🕘 Récents").assertExists()
        // Recherche instantanée par identifiant Lichess.
        rule.onAllNodesWithText("🧩", substring = true).onFirst().performClick(); settle(1500)
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("0d"); settle(1500); shot("3c_recherche")
        rule.onNodeWithText("#0D1Z6").performClick(); settle(2500)
        rule.onNodeWithText("🕘 Récents").performClick(); settle(1000); shot("3d_recents")
        rule.onAllNodesWithText("✕").onLast().performClick(); settle(800)
        rule.onNodeWithText("🖼️ Exporter").performClick(); settle(1000); shot("3e_exporter")
        rule.onAllNodesWithText("✕").onLast().performClick(); settle(800)
        // Thème choisi : chaque tirage aléatoire affiche ce thème, quel que soit le type principal du problème.
        rule.onAllNodesWithText("🧩", substring = true).onFirst().performClick(); settle(1500)
        rule.onNodeWithText("Mat en 1").performClick(); settle(2500)
        repeat(8) {
            rule.onNodeWithText("🧩 Type de problème : Mat en 1").assertExists()
            rule.onNodeWithText("🎲 Aléatoire").performClick(); settle(2000)
        }
        shot("3f_theme_choisi")
    }
}
