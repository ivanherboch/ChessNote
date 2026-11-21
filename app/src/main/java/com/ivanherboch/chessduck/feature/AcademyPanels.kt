package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.icons.ExpandLess
import androidx.compose.runtime.saveable.rememberSaveable
import com.ivanherboch.chessduck.content.LessonKind
import com.ivanherboch.chessduck.ui.icons.SportsScore
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.ui.icons.Undo
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import com.ivanherboch.chessduck.ui.RefuteCountdown
import com.ivanherboch.chessduck.ui.SessionHeader
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.heightIn
import com.ivanherboch.chessduck.ui.SideDot
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.icons.ExpandMore
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
import com.ivanherboch.chessduck.ui.icons.Visibility
import com.ivanherboch.chessduck.ui.icons.VolumeUp
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
 * En-tête d'une séance (entraînement, validation, révision, finale, question) : il remplace le sélecteur de leçon et
 * les modes Guidé/Défi, pour qu'on sache toujours ce que montre l'échiquier. La flèche de retour revient à la leçon
 * (c'est le seul moyen de quitter : plus de bouton « Arrêter » en double), le retournement de l'échiquier reste offert.
 */
@Composable
internal fun TrainingHeader(t: SessionTitle, onClose: () -> Unit, onFlip: () -> Unit) =
    SessionHeader(t.title, onClose, label = t.label, onFlip = onFlip)

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
    val priorities by vm.priorities.collectAsStateWithLifecycle()
    val pk = s.practice?.kind
    DuckSheet("Entraînements", onClose) {
        Text(
            "Votre palier : ${levelName(home.level)}." + if (home.placementDone) "" else " Un court test de placement peut l'ajuster.",
            color = c.muted, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = "Votre palier : ${levelSpoken(home.level)}." + if (home.placementDone) "" else " Un court test de placement peut l'ajuster." },
        )
        // À faire maintenant : révisions du jour, leçon à reprendre, leçon conseillée. Placées ici plutôt qu'au-dessus
        // de l'échiquier, elles ne prennent aucune place à la grille.
        val cont = home.continueId
        val rec = home.recommended?.takeIf { it != s.lessonId && it != cont }
        if (home.due.isNotEmpty() || cont != null || rec != null) {
            SectionTitle("À faire maintenant")
            if (home.due.isNotEmpty()) SuggestionCard(
                "Révisions du jour : ${home.due.size}", "Rejouez en Défi les leçons à revoir aujourd'hui.", "Commencer les révisions",
                c.gold, go(vm::startReviews),
            )
            cont?.let { id ->
                SuggestionCard("Reprendre", Lessons.byId(id).title, "Reprendre cette leçon", c.blue, go { vm.open(id) })
            }
            rec?.let { id ->
                SuggestionCard("Conseillée : ${Lessons.byId(id).title}", "La prochaine leçon de votre palier.", "Ouvrir la leçon conseillée", c.green, go { vm.open(id) })
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
        TrainingButton("Test de placement : 12 positions", go(vm::startPlacement), active = pk == PracticeKind.PLACEMENT, done = home.placementDone)
        val examLevel = home.level.coerceAtLeast(2)
        TrainingButton(
            "Examen du palier ${levelName(examLevel)} : 10 positions", go(vm::startExam), active = pk == PracticeKind.EXAM,
            done = examLevel in home.examsPassed,
        )
        SectionTitle("Réflexes")
        TrainingButton(ReflexKind.THREAT.label, go { vm.startReflex(ReflexKind.THREAT) }, active = rx?.kind == ReflexKind.THREAT)
        TrainingButton("Le seul coup", go(vm::startOnlyMove), active = pk == PracticeKind.ONLY_MOVE)
        TrainingButton(ReflexKind.CAPTURE.label, go { vm.startReflex(ReflexKind.CAPTURE) }, active = rx?.kind == ReflexKind.CAPTURE)
        SectionTitle("Exercices avancés")
        TrainingButton("Calcul à l'aveugle (palier 5)", go { vm.startReflex(ReflexKind.BLIND) }, active = rx?.kind == ReflexKind.BLIND)
        // Les 11 finales jouables, repliées par défaut (dépliées si l'une d'elles est en cours).
        var endgamesOpen by rememberSaveable { mutableStateOf(eg != null) }
        val families = EndgameFamily.entries
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(DuckRadius.M))
                .clickable(role = Role.Button, onClickLabel = if (endgamesOpen) "Replier" else "Déplier") { endgamesOpen = !endgamesOpen }
                .semantics { heading(); stateDescription = if (endgamesOpen) "dépliée" else "repliée" }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Finales contre l'Ordinateur (${families.size})", color = c.text, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text("${families.count { it in home.endgamesDone }}/${families.size}", color = c.goldText, style = MaterialTheme.typography.labelMedium)
            Icon(if (endgamesOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = c.muted, modifier = Modifier.padding(start = 6.dp))
        }
        if (endgamesOpen) {
            for ((goal, name) in listOf(EndgameGoal.WIN to "Gagner", EndgameGoal.DRAW to "Tenir la nulle")) {
                Text(name, color = c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                for (f in families.filter { it.goal == goal }) {
                    TrainingButton(
                        "${if (f.goal == EndgameGoal.WIN) "Gagnez" else "Tenez la nulle"} : ${f.label}", go { vm.startEndgame(f) },
                        active = eg?.family == f, done = f in home.endgamesDone,
                    )
                }
            }
        }
        SectionTitle("Répertoire")
        TrainingButton("Réviser mon répertoire avec les Blancs", go { vm.startRepertoire(true) }, active = s.repertoire?.white == true)
        TrainingButton("Réviser mon répertoire avec les Noirs", go { vm.startRepertoire(false) }, active = s.repertoire?.white == false)
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

/** Libellé d'une ligne d'ouverture : « Ligne principale » ou l'écart de l'adversaire (« Si 3… Nf6 »). */
internal fun variantLabel(l: Lesson, v: Int): String {
    if (v == 0) return "Ligne principale"
    val vv = l.variants[v - 1]
    val line = l.lineFor(v)
    val turn = line.turnOf(vv.from, first = 1)
    val first = line.sans[vv.from]
    return "Si " + if (line.positions[vv.from].whiteToMove) "$turn. $first" else "$turn… $first"
}

/**
 * Choix de la ligne d'une ouverture, en une puce à menu placée à côté des modes Guidé/Défi : aucune rangée
 * supplémentaire sous l'échiquier.
 */
@Composable
internal fun VariantMenu(l: Lesson, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalDuck.current
    var open by remember { mutableStateOf(false) }
    Box {
        DuckChip(
            if (selected == 0) "Variantes" else "Variante $selected", selected != 0, { open = true },
            Modifier.semantics { contentDescription = "Ligne affichée : ${variantLabel(l, selected)}. Choisir une autre ligne" },
            accent = c.blue, icon = Icons.Rounded.ExpandMore,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            for (v in 0..l.variants.size) {
                DropdownMenuItem(
                    text = { Text(variantLabel(l, v), style = MaterialTheme.typography.bodyMedium, color = c.text) },
                    onClick = { open = false; onSelect(v) },
                    leadingIcon = { if (v == selected) Icon(Icons.Rounded.Check, contentDescription = "sélectionnée", tint = c.blueText) else Spacer(Modifier.size(24.dp)) },
                )
            }
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
    } else {
        // Récapitulatif : le choix touché reste visible, en vert s'il est juste, en rouge sinon, avec la bonne réponse.
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (picked != q.correct) QuizChoice(q.choices[picked], false, "Votre réponse")
            QuizChoice(q.choices[q.correct], true, if (picked == q.correct) "Votre réponse" else "Bonne réponse")
        }
    }
    if (picked != null) EqualRow(Modifier.padding(vertical = 6.dp)) {
        DuckButton("Revoir la leçon", vm::closeQuiz, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
        DuckButton("Leçon suivante", { vm.closeQuiz(); vm.nextLesson(guided = true) }, Modifier.weight(1f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
    }
}

/** Choix du QCM après la réponse : juste (vert, coche) ou faux (rouge, croix), avec son rôle annoncé à TalkBack. */
@Composable
private fun QuizChoice(text: String, right: Boolean, what: String) {
    val c = LocalDuck.current
    val role = if (right) c.greenRole else c.redRole
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
            .border(2.dp, role.fill, RoundedCornerShape(DuckRadius.M)).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$what : $text, ${if (right) "juste" else "fausse"}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (right) Icons.Rounded.CheckCircle else Icons.Rounded.Close, contentDescription = null, tint = role.text, modifier = Modifier.size(20.dp))
        Text(text, color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Série de problèmes : problème en cours, bilan et commandes (aucune lecture vocale pendant les exercices). */
@Composable
internal fun ColumnScope.PracticePanel(vm: AcademyViewModel, pr: Practice, review: ReviewSession?, stuck: String? = null) {
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
    FeedbackCard(stuck ?: pr.message, if (stuck != null) Tone.GOLD else pr.tone, Modifier.padding(top = 4.dp).weight(1f), title = title)
    if (pr.refute != null && pr.refuteUntil > 0) RefuteCountdown(pr.refuteUntil, pr.refuteHold, vm::holdPracticeRefutation)
    EqualRow(Modifier.padding(vertical = 6.dp)) {
        // Comme dans les Problèmes : ‹ et › encadrent l'action principale pour revoir les coups déjà joués.
        val nav = !pr.finished && !pr.loading && pr.line != null
        if (nav) DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Coup précédent", vm::practiceBack, enabled = pr.refute == null && pr.cursor > 0)
        when {
            pr.finished && pr.kind == PracticeKind.REVIEW && review != null -> ReviewNextButton(vm, review, Modifier.weight(1f))
            pr.finished -> {
                DuckButton("Revenir à la leçon", vm::closePractice, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                if (pr.kind == PracticeKind.LESSON && pr.score >= 4) DuckButton("Leçon suivante", { vm.closePractice(); vm.nextLesson(guided = true) }, Modifier.weight(1f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                else if (pr.kind == PracticeKind.LESSON || pr.kind == PracticeKind.ONLY_MOVE) DuckButton(
                    "Retenter", if (pr.kind == PracticeKind.LESSON) vm::startPractice else vm::startOnlyMove, Modifier.weight(1f),
                    accent = c.green, filled = true, icon = Icons.Rounded.RestartAlt,
                )
            }
            pr.refute != null -> DuckButton("Réessayer", vm::retryPractice, Modifier.weight(1f), accent = c.red, filled = true, icon = Icons.AutoMirrored.Rounded.Undo)
            pr.failed || pr.solved -> DuckButton(
                if (pr.index + 1 < pr.ids.size) "Problème suivant" else "Voir le bilan", vm::nextPractice, Modifier.weight(1f),
                accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward,
            )
            else -> {
                HintButton(pr.hintStage, vm::practiceHint, Modifier.weight(1f), enabled = !pr.loading)
                // Après une première erreur, la solution peut être montrée (le problème compte alors comme manqué).
                if (pr.hadError) DuckButton("Solution", vm::practiceSolution, Modifier.weight(1f), icon = Icons.Rounded.Visibility)
            }
        }
        if (nav) DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Coup suivant", vm::practiceForward, enabled = pr.refute == null && pr.cursor < pr.progress)
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

/**
 * Pastilles d'avancement d'une série : vert coché (réussi), rouge barré (manqué), or (en cours), neutre (à venir).
 * Une seule description pour TalkBack.
 */
@Composable
internal fun StepDots(results: List<Boolean>, total: Int, current: Int, modifier: Modifier = Modifier) {
    val c = LocalDuck.current
    if (total <= 0) return
    val ok = results.count { it }
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = "Avancement : $ok réussi${if (ok > 1) "s" else ""}, ${results.size - ok} manqué${if (results.size - ok > 1) "s" else ""}, sur $total"
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until total) {
            val r = results.getOrNull(i)
            val fill = when {
                r == true -> c.greenRole.fill
                r == false -> c.redRole.fill
                i == current -> c.goldRole.fill
                else -> c.surfaceHi
            }
            Box(Modifier.size(14.dp).clip(CircleShape).background(fill).border(1.dp, c.outlineStrong, CircleShape), contentAlignment = Alignment.Center) {
                when (r) {
                    true -> Icon(Icons.Rounded.Check, null, tint = c.greenRole.onFill, modifier = Modifier.size(10.dp))
                    false -> Icon(Icons.Rounded.Close, null, tint = c.redRole.onFill, modifier = Modifier.size(10.dp))
                    null -> Unit
                }
            }
        }
    }
}

/**
 * Ligne d'information au-dessus de l'échiquier, comme dans les autres modes : bandeau de l'Ordinateur en finale
 * (réflexion), sinon le camp au trait dans une série ou un réflexe. Rien pour la leçon (Guidé, Défi) : l'échiquier y
 * garde toute la hauteur disponible et le titre du Défi nomme déjà le camp joué.
 */
@Composable
internal fun AcademyBoardInfo(s: AcademyState, l: Lesson, eg: EndgameSession?, rx: ReflexSession?) {
    val c = LocalDuck.current
    val pr = s.practice
    val egPos = eg?.position
    if (egPos != null) {
        // Pas de pièces « prises » ici : une finale commence sans la plupart des pièces.
        val computerWhite = !eg.family.heroWhite
        val toMove = !eg.over && egPos.whiteToMove == computerWhite
        Row(
            Modifier.fillMaxWidth().heightIn(min = 28.dp).semantics(mergeDescendants = true) {
                stateDescription = if (eg.thinking) "réfléchit" else if (toMove) "au trait" else ""
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideDot(computerWhite)
            Text("🤖 Ordinateur", color = c.text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            if (eg.thinking) CircularProgressIndicator(Modifier.padding(start = 8.dp).size(14.dp), color = c.goldRole.fill, strokeWidth = 2.dp)
            else if (toMove) Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(c.greenRole.fill))
            Spacer(Modifier.weight(1f))
            StepDots(eg.results, eg.total, if (eg.finished) -1 else eg.index, Modifier.padding(end = 4.dp))
        }
        return
    }
    val (white, text) = when {
        rx?.blind != null -> rx.blind.after.whiteToMove to "Après les coups annoncés, trait aux ${if (rx.blind.after.whiteToMove) "Blancs" else "Noirs"}"
        rx?.position != null -> rx.position!!.whiteToMove to "Trait aux ${if (rx.position!!.whiteToMove) "Blancs" else "Noirs"}"
        pr != null && pr.line != null && !pr.finished -> pr.heroWhite to "Vous jouez les ${if (pr.heroWhite) "Blancs" else "Noirs"}"
        else -> return
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        SideDot(white)
        Text(text, color = c.text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp).weight(1f))
        when {
            rx != null -> StepDots(rx.results, rx.total, rx.index, Modifier.padding(start = 6.dp, end = 4.dp))
            pr != null -> StepDots(pr.results, pr.ids.size, pr.index, Modifier.padding(start = 6.dp, end = 4.dp))
        }
    }
}

/** Finale jouée contre l'Ordinateur. */
@Composable
internal fun ColumnScope.EndgamePanel(vm: AcademyViewModel, eg: EndgameSession, review: ReviewSession?, stuck: String? = null) {
    val c = LocalDuck.current
    val title = when {
        eg.finished -> "${eg.family.label} · bilan : ${eg.score} sur ${eg.total}"
        else -> "${eg.family.label} · position ${eg.index + 1} sur ${eg.total} · coup ${eg.moves} sur ${eg.family.maxMoves}"
    }
    FeedbackCard(stuck ?: eg.message, if (stuck != null) Tone.GOLD else eg.tone, Modifier.padding(top = 4.dp).weight(1f), title = title)
    EqualRow(Modifier.padding(vertical = 6.dp)) {
        when {
            eg.finished && review != null -> ReviewNextButton(vm, review, Modifier.weight(1f))
            eg.finished -> {
                DuckButton("Fermer", vm::closeEndgame, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                DuckButton("Recommencer", { vm.startEndgame(eg.family) }, Modifier.weight(1f), accent = c.green, filled = true, icon = Icons.Rounded.RestartAlt)
            }
            eg.over -> {
                // Position perdue : on peut la rejouer pour s'entraîner (le score de la série ne change pas).
                if (eg.results.lastOrNull() == false) DuckButton(
                    "Rejouer", vm.endgame::retry, Modifier.weight(1f), icon = Icons.Rounded.RestartAlt,
                    contentDescription = "Rejouer cette position pour s'entraîner, sans changer le score",
                )
                DuckButton(
                    if (eg.index + 1 < eg.total) "Position suivante" else "Voir le bilan", vm.endgame::next, Modifier.weight(1.4f),
                    accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward,
                )
            }
            else -> {
                // Indice en deux temps, comme dans les Problèmes : la pièce, puis la flèche (sans nouvelle recherche).
                if (eg.hinting) DuckButton(
                    "Recherche…", {}, Modifier.weight(1.4f), enabled = false, accent = c.green, icon = Icons.Rounded.Lightbulb,
                    contentDescription = "Indice : l'Ordinateur cherche un bon coup",
                )
                else HintButton(eg.hintStage, vm.endgame::hint, Modifier.weight(1.4f), enabled = eg.heroToMove)
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
                DuckButton(
                    if (rx.index + 1 < rx.total) "Question suivante" else "Voir le bilan", vm.reflex::next, Modifier.weight(1.4f),
                    accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward,
                )
            }
            rx.capture != null -> {
                DuckButton("Oui, je prends", { vm.reflex.answer(true) }, Modifier.weight(1f), accent = c.green, filled = true)
                DuckButton("Non", { vm.reflex.answer(false) }, Modifier.weight(1f), accent = c.red, filled = true)
            }
            rx.blind != null -> {
                // La voix peut être coupée (ou TalkBack actif) : les coups annoncés restent écrits dans la carte, et
                // « Réécouter » explique alors comment activer la voix.
                DuckIconButton(Icons.AutoMirrored.Rounded.VolumeUp, "Réécouter les coups annoncés", vm::replayBlind, accent = c.blue)
                DuckButton(
                    "Revoir l'échiquier", vm.reflex::showBoard, Modifier.weight(1.3f), enabled = !rx.shown, icon = Icons.Rounded.Visibility,
                    contentDescription = "Réafficher l'échiquier après les coups annoncés ; la réponse ne comptera plus",
                )
            }
            // Menace à toucher : la réponse se donne sur l'échiquier, aucun bouton ici.
            else -> Unit
        }
    }
}

/** Choix du camp avant d'ouvrir une position de partie modèle dans le Jeu Libre. */
@Composable
internal fun ChooseColorSheet(fen: String, l: Lesson, onPick: (Boolean) -> Unit, onClose: () -> Unit) {
    val c = LocalDuck.current
    val whiteToMove = Position.fromFen(fen).whiteToMove
    DuckSheet("Jouer cette position", onClose) {
        Text(
            "Trait aux ${if (whiteToMove) "Blancs" else "Noirs"} · le maître jouait les ${if (l.heroWhite) "Blancs" else "Noirs"}.",
            color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp),
        )
        for (white in listOf(true, false)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(DuckRadius.M))
                    .border(1.dp, c.outlineStrong, RoundedCornerShape(DuckRadius.M))
                    .clickable(role = Role.Button) { onPick(white) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SideDot(white)
                Text(
                    "Jouer avec les ${if (white) "Blancs" else "Noirs"}", color = c.text, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

/** La leçon a-t-elle une fiche détaillée (conditions, idées, pièges) à ouvrir dans un tiroir ? */
internal fun hasBrief(l: Lesson) = l.conditions.isNotEmpty() || l.ideas.isNotEmpty() || l.traps.isNotEmpty()

/** Fiche de la leçon dans un tiroir : objectif, règle, conditions, idées et pièges (l'échiquier garde toute sa taille). */
@Composable
internal fun LessonBriefSheet(l: Lesson, onClose: () -> Unit) {
    val c = LocalDuck.current
    DuckSheet("Fiche de la leçon", onClose) {
        Text(l.title, color = c.text, style = MaterialTheme.typography.titleMedium)
        SectionTitle("Objectif")
        Text(l.summary, color = c.text, style = MaterialTheme.typography.bodyMedium)
        SectionTitle("À retenir")
        Text(l.takeaway, color = c.text, style = MaterialTheme.typography.bodyMedium)
        if (l.conditions.isNotEmpty()) {
            SectionTitle("Conditions")
            Text(l.conditions, color = c.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (l.ideas.isNotEmpty()) {
            SectionTitle("Les idées")
            for (t in l.ideas) Text("• $t", color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (l.traps.isNotEmpty()) {
            SectionTitle("Les pièges")
            for (t in l.traps) Text("• $t", color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/** Passerelles de fin de leçon : thème dans Problèmes, position dans le Jeu Libre (une seule rangée, sous le commentaire). */
@Composable
internal fun LessonBridges(l: Lesson, endFen: String?, onTrainTheme: (String, Int) -> Unit, onPlayPosition: (String, Boolean) -> Unit) {
    val c = LocalDuck.current
    // Position finale de mat ou de pat : rien à jouer, le bouton n'apparaît pas (plutôt que désactivé sans raison).
    if (l.theme.isEmpty() && endFen == null) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (l.theme.isNotEmpty()) DuckButton(
            "S'entraîner", { onTrainTheme(l.theme, l.level) }, Modifier.weight(1f), accent = c.blue, variant = ButtonVariant.OUTLINED,
            icon = Icons.Rounded.Extension, contentDescription = "S'entraîner sur ce thème dans l'onglet Problèmes",
        )
        if (endFen != null) DuckButton(
            "Jouer cette position", { onPlayPosition(endFen, l.heroWhite) }, Modifier.weight(1f),
            accent = c.blue, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.SportsEsports,
            contentDescription = "Jouer cette position contre l'Ordinateur dans le Jeu Libre",
        )
    }
}

/**
 * Action principale à la fin du mode Guidé : le QCM « Quel est le plan ? », la finale à jouer contre l'Ordinateur, ou
 * le passage au Défi. Renvoie toujours vrai (le bouton Lecture, inutile en fin de leçon, cède sa place).
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
        // Sans question ni finale : la suite naturelle est le Défi (ou « Devinez le coup » pour une partie modèle).
        else -> DuckButton(
            if (l.kind == LessonKind.MODEL_GAME) "Devinez le coup" else "Passer au Défi", { vm.setChallenge(true) }, Modifier.weight(1f),
            accent = c.gold, filled = true, icon = Icons.Rounded.SportsScore,
        )
    }
    return true
}
