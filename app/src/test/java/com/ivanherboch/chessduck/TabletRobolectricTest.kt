package com.ivanherboch.chessduck

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
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
        rule.onNodeWithText("⚙️").performClick(); settle(800)
        rule.onNodeWithText("🧒 Activer le mode enfant").performClick(); settle(1500); shot("t5_enfant")
        rule.onNodeWithText("Dame").performClick(); settle(1200); shot("t6_lecon")
    }
}
