package com.ivanherboch.chessduck

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Vérifie la base embarquée (schéma Room + 150 000 problèmes) et produit des captures de chaque écran. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class AppRobolectricTest {
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

    @Test
    fun prepackagedDatabaseMatchesRoomSchema() = runBlocking {
        val db = DuckDatabase.create(ApplicationProvider.getApplicationContext())
        assertEquals(150_000, db.puzzles().count())
        val p = db.puzzles().byId("0000D")
        assertNotNull(p)
        db.puzzles().markSolved(PuzzleProgress(p!!.id, 1L))
        assertEquals(listOf(p.id), db.puzzles().solvedIds().first())
        val meta = db.puzzles().allMeta()
        assertEquals(150_000, meta.size)
        assertEquals(150_000, meta.map { it.id }.toSet().size) // aucun doublon
        // Au moins 300 problèmes par difficulté × thème, sauf les cases que la base Lichess entière ne peut pas remplir.
        // La règle optimisée de PuzzleThemes.primary donne exactement le même thème que la règle de référence.
        val byKey = com.ivanherboch.chessduck.content.PuzzleThemes.byKey
        for (pm in meta) {
            val set = pm.themes.trim().split(' ').toSet()
            val ref = REFERENCE_PRIORITY.firstOrNull { it in set && it in byKey } ?: "middlegame"
            assertEquals(pm.id, ref, com.ivanherboch.chessduck.content.PuzzleThemes.primary(pm.themes).key)
            // Colonne v3 précalculée dans la base livrée : même thème principal.
            assertEquals(pm.id, ref, pm.primaryTheme)
        }
        // Requêtes indexées (v3) : compteurs, tirage filtré et recherche d'identifiant.
        val repo = com.ivanherboch.chessduck.data.RoomPuzzleRepository(ApplicationProvider.getApplicationContext(), db)
        val counts = repo.themeCounts(0..4000)
        assertEquals(150_000, counts.sumOf { it.total })
        assertEquals(1, counts.sumOf { it.solved })
        assertEquals(meta.count { it.primaryTheme == "fork" && it.rating in 1200..1599 }, repo.themeCounts(1200..1599).first { it.theme == "fork" }.total)
        val rnd = kotlin.random.Random(7)
        repeat(20) {
            val id = repo.randomId(listOf("pin", "skewer"), 1600..1999, "", rnd)!!
            val pz = db.puzzles().byId(id)!!
            assertTrue(pz.primaryTheme in setOf("pin", "skewer") && pz.rating in 1600..1999)
        }
        assertNull(repo.randomId(listOf("fork"), 5000..6000, "", rnd))
        val (hits, total) = repo.searchIds("0000d", 5)
        assertEquals("0000D", hits.first().id)
        assertTrue(total >= hits.size)
        assertEquals(0, repo.searchIds("%_", 5).second)
        val cells = meta.groupingBy { com.ivanherboch.chessduck.content.PuzzleThemes.bandOf(it.rating) to com.ivanherboch.chessduck.content.PuzzleThemes.primary(it.themes).key }.eachCount()
        val shortCells = (1..4).flatMap { b -> com.ivanherboch.chessduck.content.PuzzleThemes.all.map { b to it.key } }.filter { (cells[it] ?: 0) < 300 }
        assertEquals(18, shortCells.size)
        assertEquals(182, 200 - shortCells.size)
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
        db.puzzles().byId("00WqT")?.let { pz ->
            val l = com.ivanherboch.chessduck.chess.Line.fromUci(pz.fen, pz.moves)
            val img = com.ivanherboch.chessduck.feature.ShareCard.render(ctx, pz, l.positions[1], l.positions[1].whiteToMove, l.moves[0])
            File(dir, "6b_partage_mat.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        for ((name, id) in listOf("7_solution_courte" to "0000D", "8_solution_longue" to "005f3", "8b_solution_mat" to "00WqT")) {
            val pz = db.puzzles().byId(id) ?: continue
            val sol = com.ivanherboch.chessduck.feature.ShareCard.renderSolution(ctx, pz)
            File(dir, "$name.png").outputStream().use { sol.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Toutes les variantes : portrait / carré, sombre / clair.
        for (fmt in com.ivanherboch.chessduck.feature.ShareFormat.values()) for (light in listOf(false, true)) {
            val st = com.ivanherboch.chessduck.feature.ShareStyle(fmt, light)
            val tag = fmt.name.lowercase() + if (light) "_clair" else "_sombre"
            for (id in listOf("00WqT", "005f3", "0000D")) {
                val pz = db.puzzles().byId(id) ?: continue
                val l = com.ivanherboch.chessduck.chess.Line.fromUci(pz.fen, pz.moves)
                val a = com.ivanherboch.chessduck.feature.ShareCard.render(ctx, pz, l.positions[1], l.positions[1].whiteToMove, l.moves[0], st)
                val b = com.ivanherboch.chessduck.feature.ShareCard.renderSolution(ctx, pz, st)
                assertEquals(fmt.height, a.height); assertEquals(fmt.height, b.height)
                File(dir, "share_${tag}_${id}_pb.png").outputStream().use { a.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                File(dir, "share_${tag}_${id}_sol.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
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
        assertEquals(150_000, db.puzzles().count())
        assertEquals(listOf("0000D"), db.puzzles().solvedIds().first())
        // Migrations 1 → 2 → 3 puis import SQL natif : chaque problème a le même thème principal que la règle Kotlin.
        for (pm in db.puzzles().allMeta()) assertEquals(pm.id, com.ivanherboch.chessduck.content.PuzzleThemes.primary(pm.themes).key, pm.primaryTheme)
        db.close()
    }

    @Test
    fun reviewSummaryLabelsAreNeverTruncated() {
        // Cas réel : « L'ordinateur (Blancs) » + 5 compteurs + « 1 mat manqué » sur un téléphone de 360 dp.
        rule.activity.setContent {
            com.ivanherboch.chessduck.ui.ChessDuckTheme(true) {
                val c = com.ivanherboch.chessduck.ui.LocalDuck.current
                val counts: (String) -> @androidx.compose.runtime.Composable () -> Unit = { missed -> {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                        for (n in listOf("!!", "!", "?!", "?", "??")) androidx.compose.material3.Text("$n 0", color = c.text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        if (missed.isNotEmpty()) androidx.compose.material3.Text(missed, color = c.redText, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    }
                } }
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.width(360.dp).background(c.bg).padding(12.dp)) {
                    // 1) Cas réel : ne tient pas sur une ligne → les deux joueurs passent à la ligne, compteurs à gauche.
                    // 2) Noms courts : tout tient → une ligne chacun, compteurs alignés en colonne.
                    for (group in listOf(
                        listOf("L'ordinateur (Blancs)" to "1 mat manqué", "Guduche (Noirs)" to ""),
                        listOf("Ivan (Blancs)" to "", "Lu (Noirs)" to ""),
                        listOf("Un pseudo vraiment très long (Noirs)" to "2 mats manqués", "Moi (Blancs)" to ""),
                    )) {
                        com.ivanherboch.chessduck.ui.AlignedLabelRows(
                            group.map { (label, missed) -> com.ivanherboch.chessduck.ui.LabeledRow(label, label, counts(missed)) },
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = c.text,
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 14.dp),
                        )
                    }
                }
            }
        }
        settle(500)
        for (label in listOf("L'ordinateur (Blancs)", "Guduche (Noirs)", "Ivan (Blancs)", "Lu (Noirs)", "Un pseudo vraiment très long (Noirs)", "Moi (Blancs)")) {
            val node = rule.onNodeWithText(label).fetchSemanticsNode()
            val layouts = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val tl = layouts.first()
            // Tout le texte est mis en page et chaque ligne tient dans la largeur réellement affichée (aucune troncature).
            org.junit.Assert.assertEquals(label, label.length, tl.getLineEnd(tl.lineCount - 1))
            for (i in 0 until tl.lineCount) {
                org.junit.Assert.assertFalse(label, tl.isLineEllipsized(i))
                org.junit.Assert.assertTrue(label, tl.getLineRight(i) <= node.size.width + 1f)
            }
        }
        shot("11_resume_libelles")
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
                com.ivanherboch.chessduck.ui.ChessDuckTheme(dark = false, kid = true) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.ivanherboch.chessduck.ui.LocalDuck.current.bg).padding(8.dp)) {
                        for (p in listOf(few, worst, all)) com.ivanherboch.chessduck.ui.CapturedBar(p, androidx.compose.ui.Modifier.padding(vertical = 4.dp), kid = true)
                    }
                }
            }
        }
        settle(800)
        shot("10_prises")
    }

    private fun desc(text: String) = rule.onNodeWithContentDescription(text, substring = true)
    private fun close() = rule.onAllNodesWithContentDescription("Fermer").onLast().performClick()

    @Test
    fun screens() {
        settle(2500)
        shot("1_notation")
        desc("Mémo anglais").performClick(); settle(800); shot("1b_memo")
        close(); settle(800)
        rule.onNodeWithText("Académie").performClick(); settle(1500); shot("2_academie")
        rule.onNodeWithText("Défi (pratique)").performClick(); settle(1500); shot("2b_defi")
        desc("Ouvrir le catalogue des leçons").performClick(); settle(1200); shot("2c_catalogue_lecons")
        close(); settle(800)
        rule.onNodeWithText("Problèmes").performClick(); settle(2500); shot("3_problemes")
        desc("Catalogue des problèmes").performClick(); settle(2000); shot("3b_catalogue")
        close(); settle(800)
        rule.onNodeWithText("Jeu Libre").performClick(); settle(2000); shot("4_jeu")
        // Retour Android : depuis un onglet secondaire, on revient à la Notation.
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(1200)
        desc("Mémo anglais").assertExists()
        rule.onNodeWithText("Jeu Libre").performClick(); settle(1500)
        rule.onNodeWithContentDescription("Paramètres").performClick(); settle(800); shot("5_parametres")
        rule.onNodeWithText("Vibrations").performScrollTo().performClick(); settle(500)
        rule.onNodeWithText("Bruit des pièces").performScrollTo().performClick(); settle(500)
        rule.onNodeWithText("Jour").performScrollTo().performClick(); settle(800)
        close(); settle(800); shot("4c_jeu_jour")
        // Mode enfant : écran épuré à deux joueurs, puis retour au Jeu Libre.
        rule.onNodeWithContentDescription("Paramètres").performClick(); settle(800); shot("5b_parametres_jour")
        rule.onNodeWithText("Activer le mode enfant").performScrollTo().performClick(); settle(1500); shot("9_enfant")
        // Leçons animées du mode enfant
        desc("Cavalier :").performClick(); settle(1400); shot("9b_lecon_cavalier")
        rule.onNodeWithText("Revenir à la partie").performClick(); settle(800)
        desc("Pion :").performClick(); settle(900); shot("9c_lecon_pion")
        // Retour Android dans une leçon : retour à la partie du mode enfant.
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(900)
        rule.onNodeWithText("👆 Touche une pièce pour voir comment elle bouge !").assertExists()
        desc("Quitter le mode enfant").performClick(); settle(800); shot("9d_quitter")
        // « Non » referme la boîte et on reste en mode enfant ; « Oui » quitte.
        rule.onNodeWithText("Non, on continue à jouer").performClick(); settle(600)
        desc("Quitter le mode enfant").performClick(); settle(600)
        rule.onNodeWithText("Oui, quitter").performClick(); settle(1500)
        // Régression : le mode enfant doit pouvoir être relancé plusieurs fois de suite.
        repeat(3) {
            rule.onNodeWithContentDescription("Paramètres").performClick(); settle(800)
            rule.onNodeWithText("Activer le mode enfant").performScrollTo().performClick(); settle(1200)
            rule.onNodeWithText("👆 Touche une pièce pour voir comment elle bouge !").assertExists()
            desc("Quitter le mode enfant").performClick(); settle(600)
            rule.onNodeWithText("Oui, quitter").performClick(); settle(1200)
            rule.onNodeWithText("Jeu Libre").assertExists()
        }
        rule.onNodeWithText("Problèmes").performClick(); settle(1500)
        desc("Problèmes récents").assertExists()
        // Recherche instantanée par identifiant Lichess.
        desc("Catalogue des problèmes").performClick(); settle(1500)
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("0d"); settle(1500); shot("3c_recherche")
        rule.onNodeWithText("#0D1Z6").performClick(); settle(2500)
        desc("Problèmes récents").performClick(); settle(1000); shot("3d_recents")
        close(); settle(800)
        desc("Exporter en image").performClick(); settle(1000); shot("3e_exporter")
        close(); settle(800)
        // Thème choisi : chaque tirage aléatoire affiche ce thème (visible : le joueur l'a choisi).
        desc("Catalogue des problèmes").performClick(); settle(1500)
        desc("Déplier les thèmes : Mats").performClick(); settle(800)
        rule.onNodeWithText("Mat en 1").performScrollTo().performClick(); settle(2500)
        repeat(8) {
            rule.onNodeWithText("Type de problème : Mat en 1").assertExists()
            desc("Problème aléatoire").performClick(); settle(2000)
        }
        shot("3f_theme_choisi")
        // Famille « Mats » : thème masqué (anti-spoiler) jusqu'à ce qu'on le révèle ; chaque tirage est un mat.
        desc("Catalogue des problèmes").performClick(); settle(1500); shot("3g_familles")
        rule.onNodeWithText("Mats").performClick(); settle(2500)
        rule.onNodeWithText("Thème masqué (mode libre)").assertExists()
        shot("3h_theme_masque")
        val mates = com.ivanherboch.chessduck.content.PuzzleThemes.all.filter { it.group == com.ivanherboch.chessduck.content.PuzzleThemes.G_MATE }.map { it.name }
        val seen = HashSet<String>()
        repeat(16) {
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Révéler le thème").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Révéler le thème").performClick(); settle(600)
            val shown = mates.filter { name -> rule.onAllNodesWithText("Type de problème : $name").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, shown.size)
            seen.addAll(shown)
            desc("Problème aléatoire").performClick(); settle(2000)
        }
        org.junit.Assert.assertTrue("plusieurs catégories de mats attendues : $seen", seen.size >= 2)
        // Historique et revue : résumé de partie, puis retour Android vers la partie en cours.
        seedGame()
        rule.onNodeWithText("Jeu Libre").performClick(); settle(1200)
        rule.onNodeWithContentDescription("Historique des parties").performClick(); settle(1200); shot("4d_historique")
        rule.onNodeWithText("Rejouer sur l’échiquier").performClick(); settle(400); shot("4e_revue")
        rule.onNodeWithText("Pause").performClick(); settle(400)
        rule.onNodeWithContentDescription("Coup suivant").performClick(); settle(600); shot("4f_revue_coup")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(1000)
        rule.onNodeWithContentDescription("Annuler mon coup", substring = false).assertDoesNotExist()
        rule.onNodeWithText("Annuler mon coup").assertExists()
    }

    private companion object {
        /** Ordre de priorité de référence (copie figée de PuzzleThemes) pour vérifier la non-régression. */
        val REFERENCE_PRIORITY = listOf(
            "smotheredMate", "anastasiaMate", "arabianMate", "bodenMate", "doubleBishopMate", "dovetailMate", "hookMate", "backRankMate",
            "mateIn1", "mateIn2", "mateIn3", "mateIn4", "mateIn5",
            "underPromotion", "enPassant", "castling", "doubleCheck", "discoveredCheck", "zugzwang", "interference", "xRayAttack",
            "trappedPiece", "capturingDefender", "intermezzo", "clearance", "attraction", "deflection", "skewer", "pin", "fork",
            "discoveredAttack", "hangingPiece", "quietMove", "defensiveMove", "sacrifice", "promotion", "advancedPawn",
            "attackingF2F7", "exposedKing", "kingsideAttack", "queensideAttack",
            "pawnEndgame", "rookEndgame", "bishopEndgame", "knightEndgame", "queenEndgame", "queenRookEndgame",
            "opening", "endgame", "middlegame",
        )
    }
}

/** Durée pendant laquelle l'interface doit rester identique pour être considérée comme stable. */
private const val STABLE_WINDOW_MS = 250L
