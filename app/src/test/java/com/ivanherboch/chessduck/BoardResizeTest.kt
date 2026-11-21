package com.ivanherboch.chessduck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.ChessDuckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Un échiquier qui change de taille sans changer de position (message plus long dessous, jauge qui apparaît) doit
 * toujours toucher la bonne case : sinon le toucher tombe à côté de la pièce et l'échiquier paraît gelé.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xxhdpi")
class BoardResizeTest {
    @get:Rule
    val rule = createComposeRule()

    private fun cell(name: String) = rule.onNode(
        SemanticsMatcher("case $name") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.startsWith("$name,") == true },
        useUnmergedTree = true,
    )

    @Test
    fun touchesHitTheRightSquareAfterTheBoardShrinks() {
        val position = Position.fromFen("8/8/3K4/k7/3P4/8/8/8 w - - 0 1")
        // Taille lue seulement pendant la mise en page (comme la réserve de ModuleLayout) : l'échiquier n'est pas recomposé.
        var sidePx by mutableIntStateOf(900)
        var played: Move? = null
        rule.setContent {
            ChessDuckTheme(dark = false) {
                Box(
                    Modifier.layout { m, _ ->
                        val p = m.measure(Constraints.fixed(sidePx, sidePx))
                        layout(p.width, p.height) { p.place(0, 0) }
                    },
                ) {
                    ChessBoard(position = position, modifier = Modifier.fillMaxSize(), interactive = true, onMove = { played = it })
                }
            }
        }
        rule.waitForIdle()
        // Un premier toucher (case vide) démarre le gestionnaire de toucher, comme le premier geste de l'élève.
        cell("h1").performTouchInput { click() }
        rule.waitForIdle()
        // Puis l'échiquier rétrécit (la position ne change pas), et on joue Rd6-c5 en touchant le roi puis c5.
        sidePx = 640
        rule.waitForIdle()
        cell("d6").performTouchInput { click() }
        rule.waitForIdle()
        val state = cell("d6").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
        assertTrue("le roi touché doit être sélectionné ($state)", "sélectionnée" in state)
        cell("c5").performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(Move.fromUci("d6c5"), played)
    }
}
