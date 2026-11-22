package com.ivanherboch.chessduck

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivanherboch.chessduck.engine.Stockfish
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Finales jouées avec un vrai moteur UCI (Stockfish de la machine de test, branché dans le conteneur de l'application
 * avant le lancement de l'activité) : l'Ordinateur répond vraiment, comme sur un téléphone. Après chaque réponse,
 * l'échiquier doit rester jouable (aucun gel). Ignoré si /usr/games/stockfish est absent.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class EndgameRealEngineTest {
    private val sf = File("/usr/games/stockfish")

    /** Avant l'activité : réglages de départ et moteur réel à la place du binaire Android. */
    @get:Rule(order = 0)
    val engine = object : ExternalResource() {
        override fun before() {
            assumeTrue("Stockfish absent de la machine de test", sf.canExecute())
            val app = ApplicationProvider.getApplicationContext<ChessDuckApp>()
            runBlocking {
                app.prefs.setKidMode(false)
                app.prefs.setLesson(1)
                app.prefs.setAcademyLevel(2)
            }
            val field = AppContainer::class.java.getDeclaredField("engineLazy").apply { isAccessible = true }
            field.set(app.container, lazyOf(Stockfish(sf)))
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    /** Avance l'horloge du fil principal par petits pas, en laissant le vrai moteur répondre, jusqu'à [cond]. */
    private fun waitFor(timeoutMs: Long = 60_000, cond: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            looper.idleFor(Duration.ofMillis(50))
            rule.mainClock.advanceTimeBy(50)
            if (cond()) return
            Thread.sleep(20)
        }
        val texts = rule.onAllNodes(SemanticsMatcher("texte") { it.config.getOrNull(SemanticsProperties.Text) != null }, useUnmergedTree = true)
            .fetchSemanticsNodes().joinToString(" | ") { n -> n.config[SemanticsProperties.Text].joinToString(" ") }
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity, DuckViewModels.Factory)[com.ivanherboch.chessduck.feature.AcademyViewModel::class.java]
        val eg = vm.endgame.state.value
        throw AssertionError("délai dépassé ; écran : $texts ; finale : moves=${eg?.moves} thinking=${eg?.thinking} over=${eg?.over} hero=${eg?.heroToMove} fen=${eg?.position?.toFen()} last=${eg?.last?.uci}")
    }

    private fun settle(ms: Long) = waitFor(ms + 5_000) { true }.also {
        val looper = shadowOf(Looper.getMainLooper())
        looper.idleFor(Duration.ofMillis(ms)); rule.mainClock.advanceTimeBy(ms); rule.waitForIdle()
    }

    private val dest = SemanticsMatcher("destination légale") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.contains("destination légale") == true
    }

    private fun destinations() = rule.onAllNodes(dest, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun text(s: String) = rule.onAllNodesWithText(s, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun kingAndPawnStaysPlayableAfterEachReply() {
        settle(2500)
        rule.onNodeWithText("Académie").performClick(); settle(1500)
        rule.onNodeWithText("Finales contre l'Ordinateur").performScrollTo().performClick(); settle(1000)
        rule.onNodeWithText("Gagnez : Roi et pion contre roi").performScrollTo().performClick(); settle(1500)
        waitFor { rule.onAllNodes(hasContentDescription("roi blanc", substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        for (n in 1..4) {
            if (text("Position réussie") || text("vous maîtrisez") || text("Échec et mat") || text("devient dame") || text("laisse échapper")) break
            // Toucher le roi blanc : ses destinations apparaissent ; toucher la première : le coup est joué.
            rule.onNode(hasContentDescription("roi blanc", substring = true, ignoreCase = true), useUnmergedTree = true)
                .performTouchInput { click() }
            settle(300)
            assertTrue("coup $n : le roi touché doit montrer ses destinations", destinations() > 0)
            rule.onAllNodes(dest, useUnmergedTree = true).onFirst().performTouchInput { click() }
            // La réponse de l'Ordinateur arrive ; le coup suivant est demandé (ou la position est terminée).
            waitFor { text("Coup $n sur") || text("rejouez votre coup") || text("Position") && text("terminée") || text("laisse échapper") || text("Position réussie") }
            assertTrue("coup $n : l'Ordinateur doit répondre (pas de délai dépassé)", !text("rejouez votre coup"))
            settle(800)
        }
    }

    /** Ouvre une séance de l'Atelier par sa tuile de l'accueil puis son libellé. */
    private fun openTraining(label: String, tile: String = "Réflexes & Calcul") {
        settle(2500)
        rule.onNodeWithText("Académie").performClick(); settle(1500)
        rule.onNodeWithText(tile).performScrollTo().performClick(); settle(1000)
        rule.onNodeWithText(label).performScrollTo().performClick(); settle(1000)
    }

    /** Nulle à tenir avec les Noirs (échiquier retourné) : chaque coup reste jouable après la réponse. */
    @Test
    fun drawWithBlackStaysPlayable() {
        openTraining("Tenez la nulle : Défense de Philidor", tile = "Finales contre l'Ordinateur")
        waitFor { rule.onAllNodes(hasContentDescription("roi noir", substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        for (n in 1..3) {
            if (text("nulle est acquise") || text("perd la nulle") || text("vous maîtrisez")) break
            rule.onNode(hasContentDescription("roi noir", substring = true, ignoreCase = true), useUnmergedTree = true).performTouchInput { click() }
            settle(300)
            assertTrue("coup $n : le roi noir touché doit montrer ses destinations", destinations() > 0)
            rule.onAllNodes(dest, useUnmergedTree = true).onFirst().performTouchInput { click() }
            waitFor { text("Coup $n sur") || text("rejouez votre coup") || text("perd la nulle") || text("nulle est acquise") }
            assertTrue("coup $n : l'Ordinateur doit répondre", !text("rejouez votre coup"))
            settle(800)
        }
    }

    /** Réflexes : la préparation des questions (confirmées par l'Ordinateur) se termine vite, puis on répond. */
    private fun reflex(label: String, answer: () -> Unit, answered: () -> Boolean) {
        openTraining(label)
        val t0 = System.currentTimeMillis()
        waitFor(60_000) { text("Question 1 sur") || text("Aucun exercice disponible") }
        val elapsed = System.currentTimeMillis() - t0
        println("Préparation « $label » : $elapsed ms")
        assertTrue("« $label » : préparation trop longue ($elapsed ms)", elapsed < 20_000)
        assertTrue("« $label » : aucune question", text("Question 1 sur"))
        answer(); settle(500)
        assertTrue("« $label » : une réponse doit être donnée", answered())
    }

    @Test
    fun captureReflexPreparesQuicklyAndAnswers() = reflex(
        "Puis-je prendre ?", { rule.onNodeWithText("Oui, je prends").performClick() }, { text("Exact") || text("Pas tout à fait") },
    )

    @Test
    fun threatReflexPreparesQuicklyAndAnswers() = reflex(
        "Quelle est la menace ?",
        {
            rule.onAllNodes(hasContentDescription("blanc", substring = true, ignoreCase = true), useUnmergedTree = true).onFirst()
                .performTouchInput { click() }
        },
        { text("Bien vu") || text("pas la pièce menacée") },
    )
}
