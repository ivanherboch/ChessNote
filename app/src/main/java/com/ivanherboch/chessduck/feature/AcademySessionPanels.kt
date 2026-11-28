package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.RetryButton
import com.ivanherboch.chessduck.ui.NextButton
import com.ivanherboch.chessduck.ui.ScoredHintButton
import com.ivanherboch.chessduck.ui.SolutionButton
import com.ivanherboch.chessduck.ui.RestartSeriesButton
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.ui.icons.Undo
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import com.ivanherboch.chessduck.ui.RefuteCountdown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.heightIn
import com.ivanherboch.chessduck.ui.SideDot
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import com.ivanherboch.chessduck.ui.DuckIconButton
import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.ui.ButtonVariant
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.HintButton
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.icons.Lightbulb
import com.ivanherboch.chessduck.ui.icons.RestartAlt
import com.ivanherboch.chessduck.ui.icons.Visibility
import com.ivanherboch.chessduck.ui.icons.VolumeUp

// Panneaux sous l'échiquier pendant une séance : QCM, série de problèmes, finale, réflexes, et bandeau d'information.

/** QCM de fin de leçon : question, 2 ou 3 choix, puis l'explication. */
@Composable
internal fun ColumnScope.QuizPanel(vm: AcademyViewModel, l: Lesson, q: QuizState, toPractice: Boolean = true) {
    val c = LocalDuck.current
    val quiz = l.quiz ?: return
    val picked = q.picked
    val text = when {
        picked == null -> quiz.question
        picked == q.correct -> "Exact ! " + quiz.explanation
        // Mauvaise réponse : la bonne n'est pas dévoilée, on réfléchit et on réessaie (l'explication vient avec la bonne réponse).
        else -> "Pas tout à fait : ce n'est pas la bonne réponse. Repensez à l'idée de la leçon, puis réessayez."
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
            else QuizChoice(q.choices[q.correct], true, "Votre réponse")
        }
    }
    if (picked != null && picked != q.correct) EqualRow(Modifier.padding(vertical = 6.dp)) {
        DuckButton("Revoir la leçon", vm::closeQuiz, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
        RetryButton(vm::retryQuiz, Modifier.weight(1f), contentDescription = "Réessayer la question")
    }
    if (picked != null && picked == q.correct) EqualRow(Modifier.padding(vertical = 6.dp)) {
        DuckButton("Revoir la leçon", vm::closeQuiz, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
        // Question posée à la fin de Comprendre : la suite est la pratique ; posée à la fin de Pratiquer : la leçon suivante.
        if (toPractice) DuckButton(
            "Passer à la pratique", { vm.closeQuiz(); vm.setChallenge(true) }, Modifier.weight(1f),
            accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward,
        )
        else DuckButton("Leçon suivante", { vm.closeQuiz(); vm.nextLesson(guided = true) }, Modifier.weight(1f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
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
internal fun ColumnScope.PracticePanel(vm: AcademyViewModel, pr: Practice, review: ReviewSession?, stuck: String? = null, inLesson: Boolean = false) {
    val c = LocalDuck.current
    val name = when (pr.kind) {
        PracticeKind.LESSON -> "Pratique"
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
                DuckButton(
                    when {
                        inLesson -> "Revoir les coups"
                        vm.state.value.sessionFrom != null -> "Retour"
                        else -> "Revenir à la leçon"
                    },
                    vm::closePractice, Modifier.weight(1f), icon = if (inLesson) Icons.Rounded.Visibility else Icons.AutoMirrored.Rounded.ArrowBack,
                )
                if (pr.kind == PracticeKind.LESSON && pr.score >= 4) DuckButton("Leçon suivante", { vm.closePractice(); vm.nextLesson(guided = true) }, Modifier.weight(1f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                else if (pr.kind == PracticeKind.LESSON || pr.kind == PracticeKind.ONLY_MOVE) RestartSeriesButton(
                    if (pr.kind == PracticeKind.LESSON) vm::startPractice else vm::startOnlyMove, Modifier.weight(1f),
                )
            }
            pr.refute != null -> RetryButton(vm::retryPractice, Modifier.weight(1f))
            pr.failed || pr.solved -> NextButton(if (pr.index + 1 < pr.ids.size) "Problème suivant" else "Voir le bilan", vm::nextPractice, Modifier.weight(1f))
            else -> {
                // Mêmes boutons que les Problèmes : l'indice annonce ce que coûte la flèche ; après une erreur, la solution.
                ScoredHintButton(pr.hintStage, vm::practiceHint, Modifier.weight(1f), enabled = !pr.loading, crowded = pr.hadError)
                if (pr.hadError) SolutionButton(vm::practiceSolution, Modifier.weight(1f), "Voir la solution : le problème comptera comme manqué")
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
    NextButton(
        if (last) "Terminer les révisions" else "Révision suivante", { vm.nextReview(); if (last) vm.closeReviews() }, modifier,
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
            Text(
                "🤖 Ordinateur · vous jouez les ${if (eg.family.heroWhite) "Blancs" else "Noirs"}", color = c.text,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
            )
            if (eg.thinking) CircularProgressIndicator(Modifier.padding(start = 8.dp).size(14.dp), color = c.goldRole.fill, strokeWidth = 2.dp)
            else if (toMove) Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(c.greenRole.fill))
            // Le texte prend toute la place libre avant les pastilles (pas de moitié réservée à un espace vide).
            Spacer(Modifier.width(8.dp))
            StepDots(eg.results, eg.total, if (eg.finished) -1 else eg.index, Modifier.padding(end = 4.dp))
        }
        return
    }
    val (white, text) = when {
        rx?.blind != null -> rx.blind.after.whiteToMove to "Après ces coups : trait aux ${if (rx.blind.after.whiteToMove) "Blancs" else "Noirs"}"
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
internal fun ColumnScope.EndgamePanel(vm: AcademyViewModel, eg: EndgameSession, review: ReviewSession?, stuck: String? = null, inLesson: Boolean = false) {
    val c = LocalDuck.current
    val title = when {
        eg.finished -> "${eg.family.label} · bilan : ${eg.score} sur ${eg.total}"
        // La position (1 sur 5) est donnée par les pastilles au-dessus de l'échiquier : le titre tient sur une ligne.
        else -> "${eg.family.label} · coup ${eg.moves} sur ${eg.family.maxMoves}"
    }
    FeedbackCard(stuck ?: eg.message, if (stuck != null) Tone.GOLD else eg.tone, Modifier.padding(top = 4.dp).weight(1f), title = title)
    EqualRow(Modifier.padding(vertical = 6.dp)) {
        when {
            eg.finished && review != null -> ReviewNextButton(vm, review, Modifier.weight(1f))
            // Finale de Pratiquer : relire la leçon, recommencer, ou passer à la leçon suivante une fois réussie.
            eg.finished && inLesson -> {
                DuckButton("Revoir les coups", vm::closeEndgame, Modifier.weight(1f), icon = Icons.Rounded.Visibility)
                if (eg.score >= minOf(EndgameTrainer.PASS, eg.total)) DuckButton(
                    "Leçon suivante", { vm.closeEndgame(); vm.nextLesson(guided = true) }, Modifier.weight(1f), accent = c.green, filled = true,
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                )
                else RestartSeriesButton({ vm.closeEndgame(); vm.setChallenge(true) }, Modifier.weight(1f))
            }
            eg.finished -> {
                DuckButton("Fermer", vm::closeEndgame, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack)
                RestartSeriesButton({ vm.startEndgame(eg.family) }, Modifier.weight(1f))
            }
            eg.over -> {
                // Position perdue : on peut la rejouer pour s'entraîner (le score de la série ne change pas).
                // Rejeu possible après un échec, tant que le rejeu d'entraînement n'a pas été réussi.
                if (eg.results.lastOrNull() == false && eg.replayOk != true) DuckButton(
                    "Réessayer", vm.endgame::retry, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = if (eg.before != null) "Revenir juste avant l'erreur pour s'entraîner, sans changer le score"
                    else "Rejouer cette position pour s'entraîner, sans changer le score",
                )
                NextButton(if (eg.index + 1 < eg.total) "Position suivante" else "Voir le bilan", vm.endgame::next, Modifier.weight(1.4f))
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
                RestartSeriesButton({ vm.startReflex(rx.kind) }, Modifier.weight(1f))
            }
            rx.answered -> {
                NextButton(if (rx.index + 1 < rx.total) "Question suivante" else "Voir le bilan", vm.reflex::next, Modifier.weight(1.4f))
            }
            rx.capture != null -> {
                DuckButton("Oui, je prends", { vm.reflex.answer(true) }, Modifier.weight(1f), accent = c.green, filled = true)
                DuckButton("Non", { vm.reflex.answer(false) }, Modifier.weight(1f), accent = c.red, filled = true)
            }
            rx.blind != null -> {
                // La voix peut être coupée (ou TalkBack actif) : les coups annoncés restent écrits dans la carte, et
                // « Réécouter » explique alors comment activer la voix.
                DuckIconButton(Icons.AutoMirrored.Rounded.VolumeUp, "Réécouter les coups annoncés", vm::replayBlind, accent = c.blue)
                // Aide en deux temps : voir la position après les coups (réponse hors score), puis la solution.
                if (!rx.shown) DuckButton(
                    "Voir la position", vm.reflex::showBoard, Modifier.weight(1.3f),
                    contentDescription = "Voir la position après les coups annoncés ; la réponse ne comptera plus",
                )
                SolutionButton(vm.reflex::giveUp, Modifier.weight(1f), "Voir la solution : la question comptera comme manquée")
            }
            // Menace à toucher : la réponse se donne sur l'échiquier, aucun bouton ici.
            else -> Unit
        }
    }
}
