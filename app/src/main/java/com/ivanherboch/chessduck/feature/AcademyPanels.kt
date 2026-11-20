package com.ivanherboch.chessduck.feature

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.ButtonVariant
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.HintButton
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.icons.Extension
import com.ivanherboch.chessduck.ui.icons.Lightbulb
import com.ivanherboch.chessduck.ui.icons.RestartAlt
import com.ivanherboch.chessduck.ui.icons.School
import com.ivanherboch.chessduck.ui.icons.SportsEsports
import com.ivanherboch.chessduck.ui.icons.StarOutline
import com.ivanherboch.chessduck.ui.turnOf

/** « 2 étoiles sur 3 ». */
internal fun starsText(n: Int) = "$n étoile${if (n > 1) "s" else ""} sur 3"

/** Trois étoiles, pleines selon le résultat du Défi (description lue par TalkBack sur le parent). */
@Composable
internal fun StarRow(stars: Int, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val c = LocalDuck.current
    Row(modifier) {
        for (i in 1..3) Icon(
            if (i <= stars) Icons.Rounded.Star else Icons.Rounded.StarOutline, contentDescription = null,
            tint = if (i <= stars) c.goldText else c.muted, modifier = Modifier.size(size),
        )
    }
}

/**
 * Bandeau d'accueil de l'Académie : leçon à reprendre, révisions du jour, leçon conseillée, et accès aux
 * entraînements. Pendant une séance (révisions, répertoire), il en montre l'avancement et permet de la quitter.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AcademyBanner(vm: AcademyViewModel, s: AcademyState, home: AcademyHome, onTrainings: () -> Unit) {
    val c = LocalDuck.current
    // Une seule rangée qui passe à la ligne (grandes polices) : aucune puce ne défile sous « Entraînements » ni ne le
    // pousse hors de l'écran ; un titre plus large que l'écran est abrégé avec des points de suspension.
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        run {
            val r = s.review
            val rep = s.repertoire
            when {
                r != null -> {
                    DuckChip("Révision ${r.index + 1} sur ${r.queue.size}", true, {}, accent = c.gold)
                    DuckChip("Quitter", false, vm::closeReviews, icon = Icons.Rounded.Close)
                }
                rep != null -> {
                    DuckChip("Répertoire ${rep.index + 1} sur ${rep.items.size}", true, {}, accent = c.blue)
                    DuckChip("Quitter", false, vm::closeRepertoire, icon = Icons.Rounded.Close)
                }
                else -> {
                    if (home.due.isNotEmpty()) DuckChip("Révisions du jour : ${home.due.size}", false, vm::startReviews, accent = c.gold)
                    home.continueId?.let { id -> DuckChip("Continuer : ${Lessons.byId(id).title}", false, { vm.open(id) }, accent = c.blue) }
                    home.recommended?.takeIf { it != s.lessonId }?.let { id ->
                        DuckChip("Conseillée : ${Lessons.byId(id).title}", false, { vm.open(id) }, accent = c.green)
                    }
                    if (home.due.isEmpty() && home.continueId == null && (home.recommended == null || home.recommended == s.lessonId)) {
                        DuckChip("Palier ${levelName(home.level)}", false, onTrainings, accent = c.green)
                    }
                }
            }
        }
        DuckChip("Entraînements", false, onTrainings, accent = c.gold, icon = Icons.Rounded.School)
    }
}

/**
 * En-tête d'un entraînement indépendant de la leçon (réflexes, finale, tests) : il remplace le sélecteur de leçon et
 * les modes Guidé/Défi, pour qu'on sache toujours ce que montre l'échiquier, et permet de revenir à la leçon.
 */
@Composable
internal fun TrainingHeader(title: String, onClose: () -> Unit) {
    val c = LocalDuck.current
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.School, contentDescription = null, tint = c.goldText, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 10.dp).weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text("Entraînement", color = c.muted, style = MaterialTheme.typography.labelMedium)
            Text(title, color = c.text, style = MaterialTheme.typography.titleMedium)
        }
        DuckButton("Revenir à la leçon", onClose, accent = c.blue, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.Close)
    }
}

/** Tiroir des entraînements : réflexes, finales jouables, répertoire, placement et examen de palier. */
@Composable
internal fun TrainingsSheet(vm: AcademyViewModel, home: AcademyHome, onClose: () -> Unit) {
    val c = LocalDuck.current
    fun go(action: () -> Unit): () -> Unit = { onClose(); action() }
    val priorities by vm.priorities.collectAsStateWithLifecycle()
    DuckSheet("Entraînements", onClose) {
        Text(
            "Votre palier : ${levelName(home.level)}." + if (home.placementDone) "" else " Un court test de placement peut l'ajuster.",
            color = c.muted, style = MaterialTheme.typography.bodyMedium,
        )
        // Bilan des thèmes faibles : les 3 priorités du moment (parties analysées, Défis et exercices).
        SectionTitle("Vos 3 priorités")
        if (priorities.isEmpty()) {
            Text(
                "Analysez vos parties du Jeu Libre et travaillez des leçons : vos points faibles apparaîtront ici.",
                color = c.muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        priorities.forEachIndexed { i, p ->
            Text("${i + 1}. ${p.title}", color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            Text(p.reason, color = c.muted, style = MaterialTheme.typography.bodyMedium)
            TrainingButton(if (p.theme.isEmpty()) "Revoir la leçon" else "Travailler ce point : leçon et 5 exercices", go { vm.trainTopic(p.lessonId, p.theme) })
        }
        SectionTitle("Réflexes")
        TrainingButton(ReflexKind.THREAT.label, go { vm.startReflex(ReflexKind.THREAT) })
        TrainingButton("Le seul coup", go(vm::startOnlyMove))
        TrainingButton(ReflexKind.CAPTURE.label, go { vm.startReflex(ReflexKind.CAPTURE) })
        SectionTitle("Finales contre l'Ordinateur")
        for (f in EndgameFamily.entries) {
            TrainingButton("${if (f.goal == EndgameGoal.WIN) "Gagnez" else "Tenez la nulle"} : ${f.label}", go { vm.startEndgame(f) })
        }
        SectionTitle("Répertoire")
        TrainingButton("Réviser mon répertoire avec les Blancs", go { vm.startRepertoire(true) })
        TrainingButton("Réviser mon répertoire avec les Noirs", go { vm.startRepertoire(false) })
        SectionTitle("Tests")
        TrainingButton("Test de placement : 12 positions", go(vm::startPlacement))
        TrainingButton("Examen du palier ${levelName(home.level.coerceAtLeast(2))} : 10 positions", go(vm::startExam))
    }
}

@Composable
private fun TrainingButton(label: String, onClick: () -> Unit) =
    DuckButton(label, onClick, Modifier.fillMaxWidth().padding(vertical = 3.dp), variant = ButtonVariant.OUTLINED)

/** Choix de la ligne d'une ouverture : principale ou écart de l'adversaire (« Si 3… Nf6 »). */
@Composable
internal fun VariantChips(l: Lesson, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DuckChip("Ligne principale", selected == 0, { onSelect(0) }, accent = c.blue)
        l.variants.forEachIndexed { i, v ->
            val line = l.lineFor(i + 1)
            val turn = line.turnOf(v.from, first = 1)
            val first = line.sans[v.from]
            val label = "Si " + if (line.positions[v.from].whiteToMove) "$turn. $first" else "$turn… $first"
            DuckChip(label, selected == i + 1, { onSelect(i + 1) }, accent = c.blue)
        }
    }
}

/** QCM de fin de leçon : question, 2 ou 3 choix, puis l'explication. */
@Composable
internal fun ColumnScope.QuizPanel(vm: AcademyViewModel, l: Lesson, q: QuizState) {
    val c = LocalDuck.current
    val quiz = l.quiz ?: return
    val picked = q.picked
    val text = when {
        picked == null -> quiz.question
        picked == q.correct -> "Exact ! " + quiz.explanation
        else -> "Pas tout à fait. La bonne réponse : ${quiz.answer}. " + quiz.explanation
    }
    FeedbackCard(
        text, if (picked == null) Tone.INFO else if (picked == q.correct) Tone.GOOD else Tone.BAD,
        Modifier.padding(top = 4.dp).weight(1f), title = if (picked == null) "Question" else quiz.question,
    )
    if (picked == null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            q.choices.forEachIndexed { i, choice ->
                DuckButton(choice, { vm.answerQuiz(i) }, Modifier.fillMaxWidth(), accent = c.blue, variant = ButtonVariant.OUTLINED)
            }
        }
    } else EqualRow(Modifier.padding(vertical = 6.dp)) {
        DuckButton("Revoir la leçon", vm::closeQuiz, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
        DuckButton("Leçon suivante", { vm.closeQuiz(); vm.nextLesson() }, Modifier.weight(1f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
    }
}

/** Série de problèmes : problème en cours, bilan et commandes (aucune lecture vocale pendant les exercices). */
@Composable
internal fun ColumnScope.PracticePanel(vm: AcademyViewModel, pr: Practice, review: ReviewSession?) {
    val c = LocalDuck.current
    val name = when (pr.kind) {
        PracticeKind.LESSON -> "Validation"
        PracticeKind.REVIEW -> "Révision"
        PracticeKind.ONLY_MOVE -> "Le seul coup"
        PracticeKind.PLACEMENT -> "Test de placement"
        PracticeKind.EXAM -> "Examen du palier ${levelName(pr.level)}"
    }
    val title = when {
        pr.finished -> "$name · bilan : ${pr.score} sur ${pr.ids.size.coerceAtLeast(1)}"
        pr.loading -> name
        else -> "$name · problème ${pr.index + 1} sur ${pr.ids.size} · ${pr.score} réussi${if (pr.score > 1) "s" else ""}"
    }
    FeedbackCard(pr.message, pr.tone, Modifier.padding(top = 4.dp).weight(1f), title = title)
    EqualRow(Modifier.padding(vertical = 6.dp)) {
        when {
            pr.finished && pr.kind == PracticeKind.REVIEW && review != null -> ReviewNextButton(vm, review, Modifier.weight(1f))
            pr.finished -> {
                DuckButton("Revenir à la leçon", vm::closePractice, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                if (pr.kind == PracticeKind.LESSON && pr.score >= 4) DuckButton("Leçon suivante", { vm.closePractice(); vm.nextLesson() }, Modifier.weight(1f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                else if (pr.kind == PracticeKind.LESSON || pr.kind == PracticeKind.ONLY_MOVE) DuckButton(
                    "Retenter", if (pr.kind == PracticeKind.LESSON) vm::startPractice else vm::startOnlyMove, Modifier.weight(1f),
                    accent = c.green, filled = true, icon = Icons.Rounded.RestartAlt,
                )
            }
            pr.failed || pr.solved -> {
                DuckButton("Arrêter", vm::closePractice, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                DuckButton(
                    if (pr.index + 1 < pr.ids.size) "Problème suivant" else "Voir le bilan", vm::nextPractice, Modifier.weight(1.4f),
                    accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward,
                )
            }
            else -> {
                DuckButton("Arrêter", vm::closePractice, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                HintButton(pr.hintStage, vm::practiceHint, Modifier.weight(1.4f), enabled = !pr.loading)
            }
        }
    }
}

/** « Révision suivante », ou « Terminer » à la dernière révision du jour. */
@Composable
internal fun ReviewNextButton(vm: AcademyViewModel, r: ReviewSession, modifier: Modifier) {
    val c = LocalDuck.current
    val last = r.index + 1 >= r.queue.size
    DuckButton(
        if (last) "Terminer les révisions" else "Révision suivante", { vm.nextReview(); if (last) vm.closeReviews() }, modifier,
        accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward,
    )
}

/** Finale jouée contre l'Ordinateur. */
@Composable
internal fun ColumnScope.EndgamePanel(vm: AcademyViewModel, eg: EndgameSession, review: ReviewSession?) {
    val c = LocalDuck.current
    val title = when {
        eg.finished -> "${eg.family.label} · bilan : ${eg.score} sur ${eg.total}"
        else -> "${eg.family.label} · position ${eg.index + 1} sur ${eg.total} · coup ${eg.moves} sur ${eg.family.maxMoves}"
    }
    FeedbackCard(eg.message, eg.tone, Modifier.padding(top = 4.dp).weight(1f), title = title)
    EqualRow(Modifier.padding(vertical = 6.dp)) {
        when {
            eg.finished && review != null -> ReviewNextButton(vm, review, Modifier.weight(1f))
            eg.finished -> {
                DuckButton("Fermer", vm::closeEndgame, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                DuckButton("Recommencer", { vm.startEndgame(eg.family) }, Modifier.weight(1f), accent = c.green, filled = true, icon = Icons.Rounded.RestartAlt)
            }
            eg.over -> {
                DuckButton("Arrêter", vm::closeEndgame, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                DuckButton(
                    if (eg.index + 1 < eg.total) "Position suivante" else "Voir le bilan", vm.endgame::next, Modifier.weight(1.4f),
                    accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward,
                )
            }
            else -> {
                DuckButton("Arrêter", vm::closeEndgame, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                DuckButton("Indice", vm.endgame::hint, Modifier.weight(1.4f), enabled = eg.heroToMove, accent = c.green, icon = Icons.Rounded.Lightbulb)
            }
        }
    }
}

/** Réflexes : menace à toucher, ou prise à juger (oui / non). */
@Composable
internal fun ColumnScope.ReflexPanel(vm: AcademyViewModel, rx: ReflexSession) {
    val c = LocalDuck.current
    val title = when {
        rx.finished -> "${rx.kind.label} · bilan : ${rx.score} sur ${rx.total}"
        rx.loading -> rx.kind.label
        else -> "${rx.kind.label} · ${rx.index + 1} sur ${rx.total}"
    }
    FeedbackCard(rx.message, rx.tone, Modifier.padding(top = 4.dp).weight(1f), title = title)
    EqualRow(Modifier.padding(vertical = 6.dp)) {
        when {
            rx.finished -> {
                DuckButton("Fermer", vm::closeReflex, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                DuckButton("Recommencer", { vm.startReflex(rx.kind) }, Modifier.weight(1f), accent = c.green, filled = true, icon = Icons.Rounded.RestartAlt)
            }
            rx.answered -> {
                DuckButton("Arrêter", vm::closeReflex, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                DuckButton(
                    if (rx.index + 1 < rx.total) "Question suivante" else "Voir le bilan", vm.reflex::next, Modifier.weight(1.4f),
                    accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward,
                )
            }
            rx.capture != null -> {
                DuckButton("Oui, je prends", { vm.reflex.answer(true) }, Modifier.weight(1f), accent = c.green, filled = true)
                DuckButton("Non", { vm.reflex.answer(false) }, Modifier.weight(1f), accent = c.red, filled = true)
            }
            else -> DuckButton("Arrêter", vm::closeReflex, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
        }
    }
}

/** Passerelles de fin de leçon : thème dans Problèmes, position dans le Jeu Libre (une seule rangée, sous le commentaire). */
@Composable
internal fun LessonBridges(l: Lesson, endFen: String?, onTrainTheme: (String, Int) -> Unit, onPlayPosition: (String, Boolean) -> Unit) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (l.theme.isNotEmpty()) DuckButton(
            "S'entraîner", { onTrainTheme(l.theme, l.level) }, Modifier.weight(1f), accent = c.blue, variant = ButtonVariant.OUTLINED,
            icon = Icons.Rounded.Extension, contentDescription = "S'entraîner sur ce thème dans l'onglet Problèmes",
        )
        DuckButton(
            "Jouer cette position", { endFen?.let { onPlayPosition(it, l.heroWhite) } }, Modifier.weight(1f), enabled = endFen != null,
            accent = c.blue, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.SportsEsports,
            contentDescription = "Jouer cette position contre l'Ordinateur dans le Jeu Libre",
        )
    }
}

/**
 * Action principale à la fin du mode Guidé : le QCM « Quel est le plan ? », ou la finale à jouer contre l'Ordinateur ;
 * null s'il n'y en a pas (le bouton Lecture reste alors en place).
 */
@Composable
internal fun RowScope.EndOfLessonAction(vm: AcademyViewModel, l: Lesson): Boolean {
    val c = LocalDuck.current
    val family = EndgameFamily.of(l.drill)
    when {
        l.quiz != null -> DuckButton("Quel est le plan ?", vm::startQuiz, Modifier.weight(1f), accent = c.gold, filled = true)
        family != null -> DuckButton(
            if (family.goal == EndgameGoal.WIN) "Gagnez-la" else "Tenez la nulle", { vm.startEndgame(family, fromLesson = true) }, Modifier.weight(1f),
            accent = c.green, filled = true, icon = Icons.Rounded.SportsEsports, contentDescription = "${family.label} : jouer contre l'Ordinateur",
        )
        else -> return false
    }
    return true
}
