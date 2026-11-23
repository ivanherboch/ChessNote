package com.ivanherboch.chessduck.feature

import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ivanherboch.chessduck.ui.MIN_TEXT
import com.ivanherboch.chessduck.ui.AutoText
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.remember
import com.ivanherboch.chessduck.ui.icons.ExpandMore
import com.ivanherboch.chessduck.ui.icons.ExpandLess
import com.ivanherboch.chessduck.ui.icons.BookmarkBorder
import com.ivanherboch.chessduck.ui.icons.Bookmark
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.ButtonVariant
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalIsTwoPane
import com.ivanherboch.chessduck.ui.SessionHeader
import com.ivanherboch.chessduck.ui.icons.Insights
import com.ivanherboch.chessduck.ui.icons.LocalFireDepartment
import com.ivanherboch.chessduck.ui.icons.MenuBook
import com.ivanherboch.chessduck.ui.icons.SportsScore

// Accueil de l'Académie (« Séance du jour », « À travailler », « Leçons », « Atelier ») et sous-listes de l'Atelier.

/**
 * Accueil de l'Académie, ouvert à chaque arrivée sur l'onglet : le palier et sa progression avec une seule action
 * principale (continuer, ou la prochaine leçon), les révisions du jour, les points faibles à travailler s'il y en a,
 * les 4 tuiles de l'Atelier, puis l'accès au catalogue des leçons. Sur tablette, deux colonnes équilibrées.
 * [onCatalog] ouvre le catalogue, filtré sur un palier (0 : tous) ou une catégorie (null : toutes).
 */
@Composable
internal fun AcademyHomeScreen(vm: AcademyViewModel, onCatalog: (level: Int, category: LessonCategory?) -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val priorities by vm.priorities.collectAsStateWithLifecycle()
    val mastered by vm.mastered.collectAsStateWithLifecycle()
    var levels by rememberSaveable { mutableStateOf(false) }
    val left: @Composable ColumnScope.() -> Unit = {
        JourneyCard(vm, home) { levels = true }
        if (priorities.isNotEmpty()) PrioritiesCard(vm, priorities)
    }
    val right: @Composable ColumnScope.() -> Unit = {
        WorkshopTiles(vm, home)
        LessonsCard(mastered.size, home.level, onCatalog)
    }
    if (LocalIsTwoPane.current) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            Column(Modifier.weight(1f).widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = left)
            Column(Modifier.weight(1f).widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = right)
        }
    } else {
        // Conçu pour tenir sans défilement sur un téléphone ; le défilement ne sert qu'aux très petits écrans et grandes polices.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp)) {
            left()
            right()
        }
    }
    if (levels) LevelSheet(vm, home) { levels = false }
}

/** Carte de l'accueil : fond de surface, bord discret (ou coloré), titre de section lu comme un titre par TalkBack. */
@Composable
private fun HomeCard(title: String?, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDuck.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
            .border(1.dp, accent ?: c.outline, RoundedCornerShape(DuckRadius.M)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (title != null) Text(title, color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

/**
 * « Séance du jour » : titre, série de jours, palier et « Changer de palier », progression du palier, étapes numérotées
 * (révisions, leçon, point faible, cochées une fois faites) et un seul bouton pour commencer ou poursuivre la séance.
 * Compacte, pour que tout l'accueil tienne sur un écran de téléphone sans défilement.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JourneyCard(vm: AcademyViewModel, home: AcademyHome, onLevels: () -> Unit) {
    val c = LocalDuck.current
    val plan by vm.daily.collectAsStateWithLifecycle()
    // Carte principale de l'accueil : la « Séance du jour » (bord vert), avec le palier et sa progression en sous-titre.
    HomeCard(null, accent = c.greenRole.fill) {
        val heading: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Séance du jour", color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    if (plan.streak > 0) Text(
                        "  🔥 ${plan.streak} jour${if (plan.streak > 1) "s" else ""}", color = c.goldText, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { contentDescription = "Série : ${plan.streak} jour${if (plan.streak > 1) "s" else ""} d'affilée" },
                    )
                }
                Text(
                    "Palier ${levelName(home.level)}", color = c.blueText, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { contentDescription = "Palier ${levelSpoken(home.level)}" },
                )
            }
        }
        // Grande police : le bouton passe sous le titre au lieu de le serrer.
        if (LocalDensity.current.fontScale > 1.3f) {
            heading(Modifier.fillMaxWidth())
            DuckButton("Changer de palier", onLevels, Modifier.fillMaxWidth().padding(top = 4.dp), accent = c.blue, variant = ButtonVariant.OUTLINED, height = 40.dp)
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            heading(Modifier.weight(1f))
            DuckButton("Changer de palier", onLevels, accent = c.blue, variant = ButtonVariant.OUTLINED, height = 40.dp)
        }
        if (home.levelTotal > 0) {
            val exam = home.level in home.examsPassed
            val text = "${home.levelMastered}/${home.levelTotal} maîtrisée${if (home.levelMastered > 1) "s" else ""}" + if (exam) " · examen réussi" else ""
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).semantics(mergeDescendants = true) {
                    contentDescription = "Palier ${home.level} : ${home.levelMastered} leçons maîtrisées sur ${home.levelTotal}" + if (exam) ", examen réussi" else ""
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { home.levelMastered.toFloat() / home.levelTotal }, color = c.greenRole.fill, trackColor = c.surfaceHi,
                    // Barre pleine et continue : ni écart entre la partie remplie et le reste, ni point final (style Material 3 par défaut).
                    gapSize = 0.dp, drawStopIndicator = {},
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(text, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
        // Les étapes du jour, numérotées, en pastilles : cochées en vert une fois faites.
        if (plan.steps.isNotEmpty()) FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) {
                contentDescription = "Étapes de la séance : " + plan.steps.joinToString(", ") { st ->
                    plan.stepName(st) + if (st in plan.done) " faite" else " à faire"
                }
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            plan.steps.forEachIndexed { i, st ->
                val ok = st in plan.done
                val current = st == plan.next
                Text(
                    (if (ok) "✓ " else "${i + 1}. ") + plan.stepName(st),
                    color = when { ok -> c.greenText; current -> c.text; else -> c.muted },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(DuckRadius.S))
                        .background(if (current) c.greenRole.fill.copy(alpha = 0.18f) else c.surfaceHi)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        plan.buttonLabel()?.let { label ->
            DuckButton(
                label, vm::startDaily, Modifier.fillMaxWidth().padding(top = 8.dp),
                accent = c.green, filled = true, icon = if (plan.finished) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.PlayArrow,
            )
        }
    }
}

/**
 * « À travailler » : les 1 à 3 points faibles du moment, chacun sur une ligne qu'on touche pour le travailler (leçon et
 * exercices) : le titre, la raison en petit, et « Travailler » à droite.
 */
@Composable
private fun PrioritiesCard(vm: AcademyViewModel, priorities: List<Priority>) {
    val c = LocalDuck.current
    HomeCard("À travailler", accent = c.goldRole.fill) {
        priorities.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().padding(top = if (i == 0) 4.dp else 6.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(DuckRadius.S))
                    .background(c.surfaceHi)
                    .clickable(role = Role.Button) { vm.trainTopic(p.lessonId, p.theme) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Travailler ce point : ${p.title}. ${p.reason}" + if (p.theme.isEmpty()) " La leçon." else " La leçon et 5 exercices."
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${i + 1}. ${p.title}", color = c.text, style = MaterialTheme.typography.titleSmall)
                    Text(p.reason, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.goldText, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/**
 * « Leçons » : le nombre de leçons maîtrisées, un raccourci vers les leçons du palier en cours (catalogue filtré) et le
 * catalogue complet (recherche et filtres par palier et catégorie), sur une ligne.
 */
@Composable
private fun LessonsCard(masteredCount: Int, level: Int, onCatalog: (Int, LessonCategory?) -> Unit) {
    val c = LocalDuck.current
    val total = Lessons.all.size
    // Grande police : les deux boutons passent sous le titre (sinon « Leçons » se couperait).
    val stacked = LocalDensity.current.fontScale > 1.3f
    val buttons: @Composable RowScope.() -> Unit = {
        DuckButton(
            "Palier $level", { onCatalog(level, null) }, if (stacked) Modifier.weight(1f) else Modifier.padding(end = 6.dp),
            accent = c.blue, variant = ButtonVariant.OUTLINED, height = 40.dp, contentDescription = "Leçons du palier ${levelSpoken(level)}",
        )
        if (stacked) Spacer(Modifier.width(6.dp))
        DuckButton(
            "Catalogue", { onCatalog(0, null) }, if (stacked) Modifier.weight(1f) else Modifier, accent = c.blue, filled = true, height = 40.dp,
            icon = Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Ouvrir le catalogue des leçons",
        )
    }
    HomeCard(null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Leçons ($total)", color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text("$masteredCount maîtrisée${if (masteredCount > 1) "s" else ""}", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            if (!stacked) buttons()
        }
        if (stacked) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { buttons() }
    }
}

/** « Atelier » : 4 tuiles compactes (finales, réflexes et calcul, répertoire, tests), chacune vers sa sous-liste. */
@Composable
private fun WorkshopTiles(vm: AcademyViewModel, home: AcademyHome) {
    val c = LocalDuck.current
    HomeCard("Atelier") {
        val tests = listOfNotNull(if (home.placementDone) "placement fait" else null, if (home.level in home.examsPassed) "examen réussi" else null)
        // Une seule rangée de 4 tuiles (deux rangées de 2 en grande police) : nom court, description complète pour TalkBack.
        val tiles: List<@Composable (Modifier) -> Unit> = listOf(
            { m ->
                Tile(
                    "Finales", "${home.endgamesDone.size}/${EndgameFamily.entries.size}", Icons.Rounded.SportsScore, c.green, m,
                    "Finales contre l'Ordinateur : ${home.endgamesDone.size} validées sur ${EndgameFamily.entries.size}",
                ) { vm.openRoute(AcademyRoute.WORKSHOP_ENDGAMES) }
            },
            { m -> Tile("Réflexes & Calcul", null, Icons.Rounded.LocalFireDepartment, c.red, m, "Réflexes et calcul : 4 exercices") { vm.openRoute(AcademyRoute.WORKSHOP_REFLEX) } },
            { m ->
                Tile("Mon répertoire", null, Icons.AutoMirrored.Rounded.MenuBook, c.blue, m, "Mon répertoire : réviser mes ouvertures, Blancs ou Noirs") {
                    vm.openRoute(AcademyRoute.WORKSHOP_REPERTOIRE)
                }
            },
            { m ->
                Tile(
                    "Tests de niveau", null, Icons.Rounded.Insights, c.gold, m,
                    "Tests de niveau : placement et examen du palier" + tests.joinToString("") { ", $it" },
                ) { vm.openRoute(AcademyRoute.WORKSHOP_TESTS) }
            },
        )
        val perRow = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
        for (row in tiles.chunked(perRow)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in row) t(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Tuile de l'Atelier : icône, nom court (deux lignes au plus, réduit plutôt que coupé) et état éventuel. */
@Composable
private fun Tile(title: String, detail: String?, icon: ImageVector, accent: Color, modifier: Modifier, description: String, onClick: () -> Unit) {
    val c = LocalDuck.current
    Column(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surfaceHi)
            .clickable(role = Role.Button, onClickLabel = "Ouvrir") { onClick() }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        AutoText(
            title, Modifier.padding(top = 4.dp), color = c.text, maxSize = 13.sp, minSize = MIN_TEXT, weight = FontWeight.SemiBold,
            align = TextAlign.Center, maxLines = 2,
        )
        if (detail != null) Text(detail, color = c.muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Choix du palier (les leçons conseillées, les exercices et l'examen le suivent) et test de placement s'il reste à faire. */
@Composable
private fun LevelSheet(vm: AcademyViewModel, home: AcademyHome, onClose: () -> Unit) {
    val c = LocalDuck.current
    DuckSheet("Changer de palier", onClose) {
        Text(
            "Les leçons conseillées, les exercices et l'examen suivent votre palier.", color = c.muted, style = MaterialTheme.typography.bodyMedium,
        )
        for (lv in 1..5) {
            val on = home.level == lv
            DuckButton(
                levelName(lv), { vm.setLevel(lv); onClose() },
                Modifier.fillMaxWidth().padding(top = 6.dp).semantics { if (on) stateDescription = "palier actuel" },
                accent = c.blue, variant = if (on) ButtonVariant.PRIMARY else ButtonVariant.OUTLINED, icon = if (on) Icons.Rounded.CheckCircle else null,
                contentDescription = "Palier ${levelSpoken(lv)}" + if (on) ", palier actuel" else "",
            )
        }
        if (!home.placementDone) DuckButton(
            "Test de placement : 12 positions", { onClose(); vm.startPlacement() }, Modifier.fillMaxWidth().padding(top = 12.dp),
            accent = c.gold, filled = true, icon = Icons.Rounded.Insights,
        )
    }
}

/**
 * Sous-liste de l'Atelier ([route]) : finales groupées à gagner / à tenir, réflexes et calcul, répertoire, tests.
 * La flèche de retour (et le retour Android) ramène à l'accueil.
 */
@Composable
internal fun WorkshopScreen(vm: AcademyViewModel, route: AcademyRoute) {
    val c = LocalDuck.current
    val home by vm.home.collectAsStateWithLifecycle()
    val title = when (route) {
        AcademyRoute.WORKSHOP_ENDGAMES -> "Finales contre l'Ordinateur"
        AcademyRoute.WORKSHOP_REFLEX -> "Réflexes & Calcul"
        AcademyRoute.WORKSHOP_REPERTOIRE -> "Mon répertoire"
        else -> "Tests de niveau"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        SessionHeader(title, { vm.openRoute(AcademyRoute.HOME) }, backLabel = "Revenir à l'accueil de l'Académie", label = "Atelier")
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                when (route) {
                    AcademyRoute.WORKSHOP_ENDGAMES -> {
                        Text(
                            "${home.endgamesDone.size} finales validées sur ${EndgameFamily.entries.size}. Chaque séance : 5 positions contre l'Ordinateur, 3 réussies pour valider.",
                            color = c.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                        )
                        for ((goal, name) in listOf(EndgameGoal.WIN to "À gagner", EndgameGoal.DRAW to "À tenir (nulle)")) {
                            Text(name, color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                            for (f in EndgameFamily.entries.filter { it.goal == goal }) WorkshopItem(
                                "${if (f.goal == EndgameGoal.WIN) "Gagnez" else "Tenez la nulle"} : ${f.label}", "Méthode : ${f.method}",
                                done = f in home.endgamesDone,
                            ) { vm.startEndgame(f) }
                        }
                    }
                    AcademyRoute.WORKSHOP_REFLEX -> {
                        WorkshopItem(ReflexKind.THREAT.label, "Repérez la pièce que l'adversaire menace de prendre.") { vm.startReflex(ReflexKind.THREAT) }
                        WorkshopItem(ReflexKind.CAPTURE.label, "Décidez si une prise est bonne, avant de la jouer.") { vm.startReflex(ReflexKind.CAPTURE) }
                        WorkshopItem("Le seul coup", "5 positions où une seule défense tient.") { vm.startOnlyMove() }
                        WorkshopItem("Calcul à l'aveugle", "Palier 5 : imaginez deux coups annoncés sans les voir joués, puis jouez le suivant.") { vm.startReflex(ReflexKind.BLIND) }
                    }
                    AcademyRoute.WORKSHOP_REPERTOIRE -> {
                        Text(
                            "Choisissez vos ouvertures : la révision en tire 5 de votre camp, celles à revoir aujourd'hui d'abord, ligne principale ou variante.",
                            color = c.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                        )
                        RepertoireSide(vm, white = true)
                        RepertoireSide(vm, white = false)
                    }
                    else -> {
                        WorkshopItem(
                            "Test de placement", "12 positions pour ajuster votre palier.", done = home.placementDone, doneLabel = "Fait",
                        ) { vm.startPlacement() }
                        WorkshopItem(
                            "Examen du palier ${levelName(home.level)}", "10 positions mélangées : 8 réussies pour valider le palier.",
                            done = home.level in home.examsPassed, doneLabel = "Validé",
                        ) { vm.startExam() }
                    }
                }
            }
        }
    }
}

/** Élément d'une sous-liste de l'Atelier : titre, explication, coche discrète quand il est déjà validé. */
@Composable
private fun WorkshopItem(title: String, detail: String, done: Boolean = false, doneLabel: String = "Validée", onClick: () -> Unit) {
    val c = LocalDuck.current
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(DuckRadius.M))
            .clickable(role = Role.Button) { onClick() }
            .semantics(mergeDescendants = true) { if (done) stateDescription = doneLabel }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, style = MaterialTheme.typography.titleSmall)
            Text(detail, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.greenText, modifier = Modifier.padding(start = 8.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.muted)
    }
}

/**
 * « Mon répertoire » pour un camp : les ouvertures choisies (ou, à défaut, déjà travaillées), le bouton de révision, et
 * la liste des ouvertures de ce camp à cocher (signet). Sans aucune ouverture, une invitation claire à en choisir.
 */
@Composable
private fun RepertoireSide(vm: AcademyViewModel, white: Boolean) {
    val c = LocalDuck.current
    val chosen by vm.repertoireIds.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val side = if (white) "Blancs" else "Noirs"
    val openings = remember(white) { Lessons.all.filter { it.category == LessonCategory.OPENING && it.heroWhite == white } }
    val mine = openings.filter { it.id in chosen }
    val pool = vm.repertoirePool(white, chosen, progress.keys)
    var expanded by rememberSaveable(white) { mutableStateOf(false) }
    Text("Avec les $side", color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp).semantics { heading() })
    Text(
        when {
            mine.isNotEmpty() -> "${mine.size} ouverture${if (mine.size > 1) "s" else ""} dans votre répertoire : " + mine.joinToString(", ") { it.title } + "."
            pool.isNotEmpty() -> "Aucune ouverture choisie : la révision porte sur les ${pool.size} ouverture${if (pool.size > 1) "s" else ""} des $side déjà travaillée${if (pool.size > 1) "s" else ""}."
            else -> "Aucune ouverture des $side choisie ni travaillée. Ajoutez vos ouvertures ci-dessous (ou avec le signet d'une leçon d'ouverture)."
        },
        color = c.muted, style = MaterialTheme.typography.bodyMedium,
    )
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DuckButton(
            "Réviser", { vm.startRepertoire(white) }, Modifier.weight(1f), enabled = pool.isNotEmpty(), accent = c.green, filled = true,
            icon = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Réviser mon répertoire avec les $side",
        )
        DuckButton(
            if (expanded) "Masquer la liste" else "Choisir mes ouvertures", { expanded = !expanded }, Modifier.weight(1f), accent = c.blue,
            variant = ButtonVariant.OUTLINED, icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = (if (expanded) "Masquer" else "Afficher") + " les ouvertures des $side à choisir",
        )
    }
    if (expanded) for (l in openings) {
        val on = l.id in chosen
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
                .toggleable(value = on, role = Role.Checkbox, onValueChange = { vm.toggleRepertoire(l.id) })
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (on) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = null, tint = if (on) c.goldText else c.muted)
            Text("${Lessons.number(l.id)}. ${l.title}", color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
        }
    }
}
