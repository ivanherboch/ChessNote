package com.ivanherboch.chessduck.feature

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalIsTwoPane
import com.ivanherboch.chessduck.ui.SessionHeader
import com.ivanherboch.chessduck.ui.icons.Flag
import com.ivanherboch.chessduck.ui.icons.History
import com.ivanherboch.chessduck.ui.icons.Insights
import com.ivanherboch.chessduck.ui.icons.LocalFireDepartment
import com.ivanherboch.chessduck.ui.icons.MenuBook
import com.ivanherboch.chessduck.ui.icons.SportsScore

// Accueil de l'Académie (« Mon parcours », « À travailler », « Leçons », « Atelier ») et sous-listes de l'Atelier.

/**
 * Accueil de l'Académie, ouvert à chaque arrivée sur l'onglet : le palier et sa progression avec une seule action
 * principale (continuer, ou la prochaine leçon), les révisions du jour, les points faibles à travailler s'il y en a,
 * l'accès au catalogue des leçons et les 4 tuiles de l'Atelier. Sur tablette, deux colonnes équilibrées.
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
        LessonsCard(home, mastered.size, onCatalog)
        WorkshopTiles(vm, home)
    }
    if (LocalIsTwoPane.current) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            Column(Modifier.weight(1f).widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = left)
            Column(Modifier.weight(1f).widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = right)
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
            left()
            right()
        }
    }
    if (levels) LevelSheet(vm, home) { levels = false }
}

/** Carte de l'accueil : fond de surface, bord discret (ou coloré), titre de section lu comme un titre par TalkBack. */
@Composable
private fun HomeCard(title: String, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDuck.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
            .border(1.dp, accent ?: c.outline, RoundedCornerShape(DuckRadius.M)).padding(14.dp),
    ) {
        Text(title, color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

/**
 * « Mon parcours » : palier, leçons maîtrisées du palier, « Changer de palier », puis une seule action principale
 * (reprendre la leçon commencée, sinon la prochaine leçon conseillée) et les révisions du jour quand il y en a.
 */
@Composable
private fun JourneyCard(vm: AcademyViewModel, home: AcademyHome, onLevels: () -> Unit) {
    val c = LocalDuck.current
    HomeCard("Mon parcours") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Palier ${levelName(home.level)}", color = c.blueText, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Palier ${levelSpoken(home.level)}" },
            )
            DuckButton("Changer de palier", onLevels, accent = c.blue, variant = ButtonVariant.OUTLINED, height = 40.dp)
        }
        if (home.levelTotal > 0) {
            val exam = home.level in home.examsPassed
            val text = "${home.levelMastered} leçon${if (home.levelMastered > 1) "s" else ""} maîtrisée${if (home.levelMastered > 1) "s" else ""} " +
                "sur ${home.levelTotal}" + if (exam) " · examen réussi" else ""
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { contentDescription = "Palier ${home.level} : $text" }) {
                LinearProgressIndicator(
                    progress = { home.levelMastered.toFloat() / home.levelTotal }, color = c.greenRole.fill, trackColor = c.surfaceHi,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(text, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        // Une seule action principale, selon l'état réel de l'élève.
        val cont = home.continueId
        val next = home.recommended
        val (label, id) = when {
            cont != null -> "Continuer : ${Lessons.byId(cont).title}" to cont
            next != null -> "Prochaine leçon : ${Lessons.byId(next).title}" to next
            else -> null to null
        }
        if (label != null && id != null) DuckButton(
            label, { vm.open(id, guided = true) }, Modifier.fillMaxWidth().padding(top = 10.dp), accent = c.green, filled = true,
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
        )
        if (home.due.isNotEmpty()) DuckButton(
            "Révisions du jour (${home.due.size})", vm::startReviews, Modifier.fillMaxWidth().padding(top = 8.dp), accent = c.gold,
            filled = cont == null && next == null, variant = if (cont == null && next == null) null else ButtonVariant.OUTLINED,
            icon = Icons.Rounded.History,
            contentDescription = "Révisions du jour : ${home.due.size} leçon${if (home.due.size > 1) "s" else ""} à rejouer",
        )
    }
}

/** « À travailler » : les 1 à 3 points faibles du moment (parties analysées, difficultés de l'Académie). */
@Composable
private fun PrioritiesCard(vm: AcademyViewModel, priorities: List<Priority>) {
    val c = LocalDuck.current
    HomeCard("À travailler", accent = c.goldRole.fill) {
        priorities.forEachIndexed { i, p ->
            Column(Modifier.fillMaxWidth().padding(top = if (i == 0) 6.dp else 12.dp)) {
                Text("${i + 1}. ${p.title}", color = c.text, style = MaterialTheme.typography.titleSmall)
                Text(p.reason, color = c.muted, style = MaterialTheme.typography.bodyMedium)
                DuckButton(
                    "Travailler ce point", { vm.trainTopic(p.lessonId, p.theme) }, Modifier.fillMaxWidth().padding(top = 6.dp),
                    accent = c.gold, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.Flag,
                    contentDescription = "Travailler ce point : ${p.title}" + if (p.theme.isEmpty()) ", la leçon" else ", la leçon et 5 exercices",
                )
            }
        }
    }
}

/** « Leçons » : le catalogue complet, et des raccourcis vers le palier en cours et chaque catégorie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonsCard(home: AcademyHome, masteredCount: Int, onCatalog: (Int, LessonCategory?) -> Unit) {
    val c = LocalDuck.current
    val total = Lessons.all.size
    HomeCard("Leçons ($total)") {
        Text("$masteredCount maîtrisée${if (masteredCount > 1) "s" else ""} sur $total, toutes en accès libre.", color = c.muted, style = MaterialTheme.typography.bodyMedium)
        DuckButton(
            "Ouvrir le catalogue", { onCatalog(0, null) }, Modifier.fillMaxWidth().padding(top = 8.dp), accent = c.blue, filled = true,
            icon = Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Ouvrir le catalogue des leçons",
        )
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DuckChip(
                "Palier ${home.level}", false, { onCatalog(home.level, null) },
                Modifier.semantics { contentDescription = "Leçons du palier ${levelSpoken(home.level)}" }, accent = c.blue,
            )
            for (k in Lessons.categories) DuckChip(k.label, false, { onCatalog(0, k) }, Modifier.semantics { contentDescription = "Leçons : ${k.label}" }, accent = c.blue)
        }
    }
}

/** « Atelier » : 4 tuiles (finales, réflexes et calcul, répertoire, tests), chacune vers sa sous-liste. */
@Composable
private fun WorkshopTiles(vm: AcademyViewModel, home: AcademyHome) {
    val c = LocalDuck.current
    HomeCard("Atelier") {
        val tests = listOfNotNull(if (home.placementDone) "placement fait" else null, if (home.level in home.examsPassed) "examen réussi" else null)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                "Finales contre l'Ordinateur", "${home.endgamesDone.size}/${EndgameFamily.entries.size} validées", Icons.Rounded.SportsScore, c.green,
                Modifier.weight(1f),
            ) { vm.openRoute(AcademyRoute.WORKSHOP_ENDGAMES) }
            Tile("Réflexes & Calcul", "4 exercices", Icons.Rounded.LocalFireDepartment, c.red, Modifier.weight(1f)) { vm.openRoute(AcademyRoute.WORKSHOP_REFLEX) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Mon répertoire", "Blancs ou Noirs", Icons.AutoMirrored.Rounded.MenuBook, c.blue, Modifier.weight(1f)) { vm.openRoute(AcademyRoute.WORKSHOP_REPERTOIRE) }
            Tile(
                "Tests de niveau", tests.joinToString(" · ").ifEmpty { "Placement et examen" }, Icons.Rounded.Insights, c.gold, Modifier.weight(1f),
            ) { vm.openRoute(AcademyRoute.WORKSHOP_TESTS) }
        }
    }
}

/** Tuile de l'Atelier : icône, titre et état sur deux lignes au plus, sans troncature. */
@Composable
private fun Tile(title: String, detail: String, icon: ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDuck.current
    Column(
        modifier.heightIn(min = 104.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surfaceHi)
            .clickable(role = Role.Button, onClickLabel = "Ouvrir") { onClick() }
            .semantics(mergeDescendants = true) {}
            .padding(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
        Text(title, color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        Text(detail, color = c.muted, style = MaterialTheme.typography.bodySmall)
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
                        WorkshopItem("Calcul à l'aveugle", "Palier 5 : suivez deux coups annoncés sans les voir, puis jouez le suivant.") { vm.startReflex(ReflexKind.BLIND) }
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
