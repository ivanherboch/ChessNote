package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import com.ivanherboch.chessduck.ui.DuckIconButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.ButtonVariant
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.icons.School

// Tiroir « Entraînements » de l'Académie et son bouton d'en-tête.

/**
 * Bouton « Entraînements » de l'en-tête de l'application (onglet Académie) : surligné quand le tiroir est ouvert ou
 * qu'une séance est en cours. Avec une très grande police, il se réduit à son icône pour ne pas chevaucher le titre.
 */
@Composable
fun AcademyTrainingsButton(open: Boolean, active: Boolean, suggestions: Int, onClick: () -> Unit) {
    val c = LocalDuck.current
    val on = open || active
    val desc = "Entraînements" + (if (active) ", séance en cours" else "") +
        if (suggestions > 0) ", $suggestions suggestion${if (suggestions > 1) "s" else ""} à faire maintenant" else ""
    if (LocalDensity.current.fontScale > 1.3f) {
        DuckIconButton(
            Icons.Rounded.School, desc, onClick, accent = c.gold, selected = on,
            badge = when {
                suggestions > 0 -> "$suggestions"
                active -> "•"
                else -> null
            },
        )
    } else {
        DuckButton(
            if (suggestions > 0) "Entraînements · $suggestions" else "Entraînements", onClick, Modifier.semantics { selected = on },
            accent = c.gold, height = 44.dp, icon = Icons.Rounded.School, variant = if (on) ButtonVariant.PRIMARY else ButtonVariant.OUTLINED,
            contentDescription = if (active || suggestions > 0) desc else null,
        )
    }
}

/**
 * Tiroir des entraînements : vos 3 priorités, réflexes, exercices avancés, finales jouables, répertoire et tests.
 * L'entraînement en cours est surligné (« En cours ») ; les finales et tests déjà validés sont marqués.
 */
@Composable
internal fun TrainingsSheet(vm: AcademyViewModel, home: AcademyHome, s: AcademyState, eg: EndgameSession?, rx: ReflexSession?, onClose: () -> Unit) {
    val c = LocalDuck.current
    fun go(action: () -> Unit): () -> Unit = { onClose(); action() }
    // Séance du tiroir (réflexes, finales, tests, révisions, répertoire) : en la quittant, on revient au tiroir.
    fun session(action: () -> Unit): () -> Unit = { onClose(); vm.markFromDrawer(); action() }
    val priorities by vm.priorities.collectAsStateWithLifecycle()
    val pk = s.practice?.kind
    DuckSheet("Entraînements", onClose) {
        Text(
            "Votre palier : ${levelName(home.level)}." + if (home.placementDone) "" else " Un court test de placement peut l'ajuster.",
            color = c.muted, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = "Votre palier : ${levelSpoken(home.level)}." + if (home.placementDone) "" else " Un court test de placement peut l'ajuster." },
        )
        // Choix manuel du palier : les leçons conseillées, les exercices et l'examen suivent ce palier.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (lv in 1..5) DuckChip(
                levelName(lv), home.level == lv, { vm.setLevel(lv) },
                Modifier.semantics { contentDescription = "Palier ${levelSpoken(lv)}" }, accent = c.blue,
            )
        }
        // Progression du palier : leçons maîtrisées, puis l'examen qui le valide.
        if (home.levelTotal > 0) {
            val exam = home.level in home.examsPassed
            val text = "Palier ${home.level} : ${home.levelMastered} leçon${if (home.levelMastered > 1) "s" else ""} maîtrisée${if (home.levelMastered > 1) "s" else ""} sur ${home.levelTotal}" +
                if (exam) " · examen réussi" else " · examen à passer"
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { contentDescription = text }) {
                LinearProgressIndicator(
                    progress = { home.levelMastered.toFloat() / home.levelTotal }, color = c.greenRole.fill, trackColor = c.surfaceHi,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(text, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        // À faire maintenant : révisions du jour, leçon à reprendre, leçon conseillée. Placées ici plutôt qu'au-dessus
        // de l'échiquier, elles ne prennent aucune place à la grille.
        val cont = home.continueId
        val rec = home.recommended?.takeIf { it != s.lessonId && it != cont }
        if (home.due.isNotEmpty() || cont != null || rec != null) {
            SectionTitle("À faire maintenant")
            if (home.due.isNotEmpty()) SuggestionCard(
                "Révisions du jour : ${home.due.size}", "Rejouez les leçons à revoir aujourd'hui.", "Commencer les révisions",
                c.gold, session(vm::startReviews),
            )
            cont?.let { id ->
                SuggestionCard("Reprendre", Lessons.byId(id).title, "Reprendre cette leçon", c.blue, go { vm.open(id, guided = true) })
            }
            rec?.let { id ->
                SuggestionCard("Conseillée : ${Lessons.byId(id).title}", "La prochaine leçon de votre palier.", "Ouvrir la leçon conseillée", c.green, go { vm.open(id, guided = true) })
            }
        }
        // Bilan des thèmes faibles : des cartes à part, avant le catalogue des exercices.
        SectionTitle("Vos 3 priorités")
        if (priorities.isEmpty()) {
            Text(
                "Analysez vos parties du Jeu Libre et travaillez des leçons : vos points faibles apparaîtront ici.",
                color = c.muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        priorities.forEachIndexed { i, p ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
                    .border(1.dp, c.goldRole.fill, RoundedCornerShape(DuckRadius.M)).padding(12.dp),
            ) {
                Text("${i + 1}. ${p.title}", color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(p.reason, color = c.muted, style = MaterialTheme.typography.bodyMedium)
                DuckButton(
                    if (p.theme.isEmpty()) "Revoir la leçon" else "Travailler ce point : leçon et 5 exercices", go { vm.trainTopic(p.lessonId, p.theme) },
                    Modifier.fillMaxWidth().padding(top = 8.dp), accent = c.gold, filled = true, icon = Icons.Rounded.School,
                )
            }
        }
        // Les tests (placement, examen) juste après les priorités : ce sont eux qui font avancer le palier.
        SectionTitle("Tests")
        TrainingButton("Test de placement : 12 positions", session(vm::startPlacement), active = pk == PracticeKind.PLACEMENT, done = home.placementDone)
        val examLevel = home.level
        TrainingButton(
            "Examen du palier ${levelName(examLevel)} : 10 positions", session(vm::startExam), active = pk == PracticeKind.EXAM,
            done = examLevel in home.examsPassed,
        )
        SectionTitle("Réflexes")
        TrainingButton(ReflexKind.THREAT.label, session { vm.startReflex(ReflexKind.THREAT) }, active = rx?.kind == ReflexKind.THREAT)
        TrainingButton("Le seul coup", session(vm::startOnlyMove), active = pk == PracticeKind.ONLY_MOVE)
        TrainingButton(ReflexKind.CAPTURE.label, session { vm.startReflex(ReflexKind.CAPTURE) }, active = rx?.kind == ReflexKind.CAPTURE)
        SectionTitle("Exercices avancés")
        TrainingButton("Calcul à l'aveugle (palier 5)", session { vm.startReflex(ReflexKind.BLIND) }, active = rx?.kind == ReflexKind.BLIND)
        SectionTitle("Répertoire")
        TrainingButton("Réviser mon répertoire avec les Blancs", session { vm.startRepertoire(true) }, active = s.repertoire?.white == true)
        TrainingButton("Réviser mon répertoire avec les Noirs", session { vm.startRepertoire(false) }, active = s.repertoire?.white == false)
        // Les finales jouables contre l'Ordinateur, en fin de liste, toutes visibles : à gagner, puis à tenir.
        SectionTitle("Finales contre l'Ordinateur (${home.endgamesDone.size}/${EndgameFamily.entries.size} validées)")
        for ((goal, name) in listOf(EndgameGoal.WIN to "À gagner", EndgameGoal.DRAW to "À tenir (nulle)")) {
            Text(name, color = c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 4.dp))
            for (f in EndgameFamily.entries.filter { it.goal == goal }) {
                TrainingButton(
                    "${if (f.goal == EndgameGoal.WIN) "Gagnez" else "Tenez la nulle"} : ${f.label}", session { vm.startEndgame(f) },
                    active = eg?.family == f, done = f in home.endgamesDone,
                )
            }
        }
    }
}

/** Carte « À faire maintenant » : ce qui est proposé, pourquoi, et le bouton pour y aller. */
@Composable
private fun SuggestionCard(title: String, detail: String, action: String, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val c = LocalDuck.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
            .border(1.dp, accent, RoundedCornerShape(DuckRadius.M)).padding(12.dp),
    ) {
        Text(title, color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(detail, color = c.muted, style = MaterialTheme.typography.bodyMedium)
        DuckButton(action, onClick, Modifier.fillMaxWidth().padding(top = 8.dp), accent = accent, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
    }
}

/**
 * Bouton d'un entraînement. [active] : séance en cours, surlignée et annoncée « En cours » ; [done] : déjà validé,
 * marqué d'une coche discrète.
 */
@Composable
private fun TrainingButton(label: String, onClick: () -> Unit, active: Boolean = false, done: Boolean = false) {
    val c = LocalDuck.current
    val state = when {
        active -> "En cours"
        done -> "Validé"
        else -> null
    }
    DuckButton(
        if (active) "$label · En cours" else label, onClick,
        Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics {
            selected = active
            if (state != null) stateDescription = state
        },
        accent = if (active) c.gold else null, variant = if (active) ButtonVariant.PRIMARY else ButtonVariant.OUTLINED,
        icon = when {
            active -> Icons.Rounded.PlayArrow
            done -> Icons.Rounded.CheckCircle
            else -> null
        },
    )
}
