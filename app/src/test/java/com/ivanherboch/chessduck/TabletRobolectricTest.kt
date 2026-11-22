package com.ivanherboch.chessduck

import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import android.os.Looper
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
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
import org.junit.Before
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

    /** Les réglages (DataStore) survivent d'un test à l'autre : chaque test démarre hors du mode enfant. */
    @Before
    fun leaveKidMode() = runBlocking {
        val prefs = ApplicationProvider.getApplicationContext<ChessDuckApp>().prefs
        prefs.setKidMode(false)
        prefs.setLesson(1)
        prefs.setAcademyLevel(2)
    }

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

    /**
     * Laisse l'application se stabiliser sans attente fixe : le temps virtuel avance de [ms] (animations,
     * délais du fil principal), puis on attend la condition « interface stable » — arbre sémantique inchangé
     * pendant [STABLE_WINDOW_MS], aucun indicateur de chargement et fil principal au repos — le temps que les travaux en arrière-plan
     * (base de données, DataStore, rendus) aient publié leurs résultats.
     */
    private fun settle(ms: Long = 1500) {
        val looper = shadowOf(Looper.getMainLooper())
        looper.idleFor(Duration.ofMillis(ms))
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
        var last = ""
        var since = System.nanoTime()
        rule.waitUntil(timeoutMillis = 15_000) {
            looper.idle()
            val tree = runCatching { rule.onRoot(useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE) }.getOrDefault("")
            if (tree != last) { last = tree; since = System.nanoTime() }
            // Aucun indicateur de progression indéterminé (chargement, réflexion, rendu d'aperçu) à l'écran.
            val busy = rule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
            !busy && looper.isIdle && (System.nanoTime() - since) / 1_000_000 >= STABLE_WINDOW_MS
        }
    }

    private fun shot(name: String) {
        val dir = File(System.getenv("SHOT_DIR") ?: "build/screenshots").apply { mkdirs() }
        rule.onRoot().captureRoboImage(File(dir, "$name.png").path)
    }

    /** Téléphone en paysage : l'orientation n'est plus imposée, l'échiquier passe à gauche avec le rail de navigation. */
    @Test
    @Config(qualifiers = "w760dp-h360dp-land-xxhdpi")
    fun phoneLandscape() {
        settle(2500)
        rule.onNodeWithText("Notation").performClick(); settle(1500); shot("o1_telephone_paysage_notation")
        rule.onNodeWithText("Jeu Libre").performClick(); settle(2000); shot("o2_telephone_paysage_jeu")
    }

    /** Tablette en portrait : une seule colonne, échiquier en haut et commandes dessous. */
    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun tabletPortrait() {
        settle(2500)
        rule.onNodeWithText("Notation").performClick(); settle(1500); shot("o3_tablette_portrait_notation")
        rule.onNodeWithText("Académie").performClick(); settle(1500); shot("o4_tablette_portrait_academie")
        rule.onNodeWithText("Mon parcours").assertExists()
    }

    @Test
    fun tabletLandscape() {
        settle(2500)
        rule.onNodeWithText("Notation").performClick(); settle(1500); shot("t1_notation")
        // Accueil de l'Académie en deux colonnes, puis une leçon, l'Atelier et une finale.
        rule.onNodeWithText("Académie").performClick(); settle(1500); shot("t0_accueil")
        rule.onNodeWithContentDescription("Ouvrir le catalogue des leçons").performClick(); settle(1000)
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("trois principes"); settle(800)
        rule.onNodeWithText("Les trois principes de l'ouverture").performClick(); settle(1500); shot("t2_academie")
        rule.onNodeWithContentDescription("Revenir à l'accueil de l'Académie").performClick(); settle(1000)
        rule.onNodeWithText("Finales contre l'Ordinateur").performClick(); settle(1000); shot("t9_atelier_finales")
        rule.onNodeWithText("Gagnez : Roi et dame contre roi").performScrollTo().performClick(); settle(1500); shot("t10_finale")
        rule.onNodeWithContentDescription("Revenir à l'Atelier").performClick(); settle(1000)
        rule.onNodeWithContentDescription("Revenir à l'accueil de l'Académie").performClick(); settle(800)
        rule.onNodeWithText("Problèmes").performClick(); settle(2500); shot("t3_problemes")
        rule.onNodeWithText("Jeu Libre").performClick(); settle(2000); shot("t4_jeu")
        seedGame()
        rule.onNodeWithContentDescription("Historique des parties").performClick(); settle(1200)
        rule.onAllNodesWithText("Revue commentée").onFirst().performClick(); settle(400)
        rule.onNodeWithText("Lecture").assertExists() // ouverte en pause
        rule.onNodeWithText("Bc4").performClick(); settle(800); shot("t8_revue")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(1000)
        rule.onNodeWithContentDescription("Paramètres").performClick(); settle(800); shot("t7_parametres")
        rule.onNodeWithText("Activer le mode enfant").performScrollTo().performClick(); settle(1500); shot("t5_enfant")
        rule.onNodeWithContentDescription("Dame :", substring = true).performClick(); settle(1200); shot("t6_lecon")
    }
}

/** Durée pendant laquelle l'interface doit rester identique pour être considérée comme stable. */
private const val STABLE_WINDOW_MS = 250L
