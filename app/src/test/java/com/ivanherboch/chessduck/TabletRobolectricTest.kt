package com.ivanherboch.chessduck

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import com.ivanherboch.chessduck.data.GameRecord
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Captures en paysage sur un écran de tablette (1280 × 800 dp, type Galaxy Tab). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi")
class TabletRobolectricTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Ajoute une courte partie terminée à l'historique de l'application (mat du berger). */
    private fun seedGame() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ChessDuckApp>()
        app.db.user().insertGame(
            GameRecord(
                date = 1_763_000_000_000, opponent = "Ordinateur 1200", vsComputer = true, playerWhite = true, result = "1-0", outcome = 1,
                reason = "échec et mat", eloBefore = 1200, eloAfter = 1216, moves = "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7",
                whiteName = "Joueur", blackName = "🤖 Ordinateur", opponentElo = 1200,
            ),
        )
    }

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
    fun tabletLandscape() {
        settle(2500); shot("t1_notation")
        rule.onNodeWithText("Académie").performClick(); settle(1500); shot("t2_academie")
        rule.onNodeWithText("Problèmes").performClick(); settle(2500); shot("t3_problemes")
        rule.onNodeWithText("Jeu Libre").performClick(); settle(2000); shot("t4_jeu")
        seedGame()
        rule.onNodeWithContentDescription("Historique des parties").performClick(); settle(1200)
        rule.onNodeWithText("Rejouer sur l’échiquier").performClick(); settle(400)
        rule.onNodeWithText("Pause").performClick(); settle(400)
        rule.onNodeWithText("Bc4").performClick(); settle(800); shot("t8_revue")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(1000)
        rule.onNodeWithContentDescription("Paramètres").performClick(); settle(800); shot("t7_parametres")
        rule.onNodeWithText("Activer le mode enfant").performScrollTo().performClick(); settle(1500); shot("t5_enfant")
        rule.onNodeWithContentDescription("Dame :", substring = true).performClick(); settle(1200); shot("t6_lecon")
    }
}
