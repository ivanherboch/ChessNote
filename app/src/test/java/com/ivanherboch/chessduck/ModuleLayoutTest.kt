package com.ivanherboch.chessduck

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.START_FEN
import com.ivanherboch.chessduck.ui.ChessDuckTheme
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.MoveStrip
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Réserve adaptative de [ModuleLayout] : avec la bande des coups (LazyRow) dans la zone basse, comme en mode Guidé,
 * au Jeu Libre et dans la revue, la hauteur nécessaire au texte reste calculée et l'échiquier cède de la place.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ModuleLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun boardMakesRoomForTheTextBelowTheMoveStrip() {
        // La mesure stricte (exception plutôt que réserve nulle) n'est active que dans une version de débogage.
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertTrue((app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        var long by mutableStateOf(false)
        rule.setContent {
            ChessDuckTheme(dark = false) {
                ModuleLayout(
                    bottomMin = 120.dp,
                    top = {},
                    board = { Box(Modifier.fillMaxSize().testTag("board")) },
                    bottom = {
                        MoveStrip(Line(START_FEN), 0)
                        Text(if (long) "Un long commentaire de leçon sous l'échiquier. ".repeat(30) else "Court.")
                    },
                )
            }
        }
        rule.waitForIdle()
        val before = rule.onNodeWithTag("board").fetchSemanticsNode().size.width
        long = true
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val after = rule.onNodeWithTag("board").fetchSemanticsNode().size.width
        assertTrue("l'échiquier doit rétrécir pour le texte ($before → $after)", after < before)
    }
}
