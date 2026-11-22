package com.ivanherboch.chessduck.feature

import androidx.compose.ui.semantics.semantics
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.icons.Lightbulb
import androidx.activity.compose.BackHandler
import com.ivanherboch.chessduck.ui.HintButton
import androidx.compose.foundation.layout.heightIn
import com.ivanherboch.chessduck.ui.LocalIsTwoPane
import com.ivanherboch.chessduck.ui.LoadingState
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.icons.SearchOff
import androidx.compose.foundation.layout.Box
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.content.LessonKind
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.MoveStrip
import com.ivanherboch.chessduck.ui.RefuteCountdown
import com.ivanherboch.chessduck.ui.refuteArrow
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.icons.Pause
import com.ivanherboch.chessduck.ui.icons.RestartAlt
import com.ivanherboch.chessduck.ui.icons.SkipNext
import com.ivanherboch.chessduck.ui.icons.SkipPrevious
import com.ivanherboch.chessduck.ui.icons.Undo
import com.ivanherboch.chessduck.ui.icons.Visibility
import com.ivanherboch.chessduck.ui.icons.VolumeOff
import com.ivanherboch.chessduck.ui.icons.VolumeUp
import com.ivanherboch.chessduck.ui.stepTitle
import com.ivanherboch.chessduck.ui.turnOf

@Composable
fun AcademyScreen(
    vm: AcademyViewModel,
    /** « S'entraîner sur ce thème » : ouvre l'onglet Problèmes sur ce thème Lichess, au palier de la leçon. */
    onTrainTheme: (theme: String, level: Int) -> Unit = { _, _ -> },
    /** « Jouer cette position » : ouvre le Jeu Libre sur cette position (FEN), le joueur ayant le camp indiqué. */
    onPlayPosition: (fen: String, playerWhite: Boolean) -> Unit = { _, _ -> },
    /** « Vision des cases » : ouvre l'exercice dans l'onglet Notation (passerelle de la leçon sur la notation). */
    onVision: () -> Unit = {},
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val mastered by vm.mastered.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val variantsDone by vm.variantsDone.collectAsStateWithLifecycle()
    val eg by vm.endgame.state.collectAsStateWithLifecycle()
    val rx by vm.reflex.state.collectAsStateWithLifecycle()
    val narration by vm.narration.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    // Retour Android : une séance revient à l'écran qui l'a lancée (leçon, accueil ou Atelier), une leçon ou une
    // sous-liste de l'Atelier revient à l'accueil ; sur l'accueil, le retour quitte l'onglet.
    BackHandler(enabled = s.route != AcademyRoute.HOME) { vm.back() }
    // La voix se tait dès que l'Académie n'est plus à l'écran (autre onglet, application en arrière-plan).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.silence() }
    DisposableEffect(vm) { onDispose { vm.silence() } }
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val l = Lessons.byId(s.lessonId)
    val line = vm.line(s)
    val texts = vm.texts(s)
    var catalog by remember { mutableStateOf(false) }
    var brief by remember { mutableStateOf(false) }
    val total = Lessons.all.size
    val cur = s.cursor.coerceIn(0, line.size)
    val stars = progress[l.id]?.stars ?: 0
    val pr = s.practice
    val model = l.kind == LessonKind.MODEL_GAME
    // Question de fin à poser avant de passer à la pratique (ou à la leçon suivante).
    val quizPending = l.quiz != null && !s.quizPassed && s.review == null && s.repertoire == null
    // Retour haptique des coups jugés (bon coup, coup équivalent, erreur), y compris après le jugement de l'Ordinateur.
    // Les vibrations ne sont jouées qu'au moment de l'événement : en revenant sur l'onglet Académie, la dernière
    // réussite (ou erreur) déjà signalée n'est pas rejouée (seul reste le petit retour du toucher de l'onglet).
    val seenPulses = remember { longArrayOf(s.pulse?.id ?: -1, eg?.pulse?.id ?: -1, rx?.pulse?.id ?: -1) }
    fun buzz(slot: Int, p: Pulse?) {
        if (p == null || p.id == seenPulses[slot]) return
        seenPulses[slot] = p.id
        if (p.good) fb?.success() else fb?.error()
    }
    LaunchedEffect(s.pulse?.id) { buzz(0, s.pulse) }
    // Séance lancée depuis l'accueil ou l'Atelier : sa flèche de retour y ramène.
    val backLabel = backLabel(s)
    LaunchedEffect(eg?.pulse?.id) { buzz(1, eg?.pulse) }
    LaunchedEffect(rx?.pulse?.id) { buzz(2, rx?.pulse) }
    val endFen = line.positions[cur].takeIf { it.legalMoves.isNotEmpty() }?.toFen()
    // Parties modèles : le camp se choisit avant d'ouvrir le Jeu Libre (Blancs ou Noirs).
    var playChoice by remember { mutableStateOf<String?>(null) }
    val play: (String, Boolean) -> Unit = if (model) { fen, _ -> playChoice = fen } else onPlayPosition
    // « S'entraîner » (onglet Problèmes) n'est pas proposé quand Pratiquer est déjà une série de problèmes du thème.
    val bridges: @Composable () -> Unit = {
        LessonBridges(l, endFen, onTrainTheme, play, onVision, showTrain = vm.practiceMode(l) != AcademyViewModel.PracticeMode.PUZZLES)
    }

    // Explication brève quand on touche une pièce qui ne peut pas bouger ; effacée au coup suivant.
    var stuck by remember(
        s.lessonId, s.cursor, s.challenge, s.variant, s.refute, s.tentative, pr?.index, pr?.cursor, eg?.index, eg?.moves, rx?.index,
    ) { mutableStateOf<String?>(null) }
    // Haut-parleur touché alors que la voix est coupée ou absente : explication à la place du commentaire.
    var voiceTip by remember(s.lessonId, s.cursor) { mutableStateOf<String?>(null) }
    val voice by vm.voice.collectAsStateWithLifecycle()
    var catalogStatus by rememberSaveable { mutableIntStateOf(0) }
    var catalogLevel by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val sheets: @Composable () -> Unit = {
        if (catalog) LessonCatalog(vm, s, mastered, catalogStatus, { catalogStatus = it }, catalogLevel, { catalogLevel = it }) { catalog = false }
        val repertoire by vm.repertoireIds.collectAsStateWithLifecycle()
        if (brief) LessonBriefSheet(
            l, { brief = false }, inRepertoire = (l.id in repertoire).takeIf { l.category == com.ivanherboch.chessduck.content.LessonCategory.OPENING },
            onToggleRepertoire = { vm.toggleRepertoire(l.id) },
        )
        playChoice?.let { fen -> ChooseColorSheet(fen, l, { white -> playChoice = null; onPlayPosition(fen, white) }) { playChoice = null } }
    }
    // Accueil et Atelier : pas d'échiquier ; le catalogue s'ouvre depuis l'accueil, filtré au besoin.
    when (s.route) {
        AcademyRoute.HOME -> {
            AcademyHomeScreen(vm) { level, category -> catalogStatus = 0; catalogLevel = level; vm.setFilter(category); catalog = true }
            return sheets()
        }
        AcademyRoute.LESSON -> Unit
        else -> {
            WorkshopScreen(vm, s.route)
            return sheets()
        }
    }
    ModuleLayout(
        // Séance (finale, série, réflexes) : une carte et une rangée de boutons, sans bande de coups ; l'échiquier prend le reste.
        bottomMin = if (eg != null || pr != null || rx != null) 150.dp else 190.dp,
        reserveKey = listOf(s.lessonId, s.challenge, s.variant, pr?.kind, eg?.family, rx?.kind),
        top = {
            val session = vm.sessionTitle(s, eg, rx)
            if (session != null) {
                TrainingHeader(session, vm::closeTraining, vm::flip, backLabel)
                return@ModuleLayout
            }
            LessonSelector(
                l, mastered, stars, lessonStatus(progress[l.id]), total, onCatalog = { catalog = true }, onHome = vm::goHome, onNext = vm::nextLesson,
            )
            LessonModeBar(vm, s, l, variantsDone, onBrief = { brief = true })
        },
        overBoard = { AcademyBoardInfo(s, l, eg, rx) },
        board = {
            val egs = eg
            val rxs = rx
            when {
                // Préparation des exercices, ou aucun exercice trouvé : jamais l'échiquier de la leçon en arrière-plan.
                (pr != null && pr.loading) || (rxs != null && rxs.loading) -> LoadingState("Préparation des exercices…")
                (pr != null && pr.finished && pr.ids.isEmpty()) || (rxs != null && rxs.finished && rxs.total == 0) -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        Icons.Rounded.SearchOff, "Aucun exercice disponible", (pr?.message ?: rxs?.message).orEmpty(),
                        actionLabel = backLabel, onAction = vm::closeTraining,
                    )
                }
                // Position de finale en cours de génération (hors du fil principal) : un instant de chargement.
                egs != null && egs.position == null && !egs.finished -> LoadingState("Préparation de la position…")
                egs?.position != null -> EndgameBoard(vm, egs, s.sessionFlipped) { stuck = it }
                rxs?.position != null -> ReflexBoard(vm, rxs, s.sessionFlipped)
                pr != null -> PracticeBoard(vm, pr, line.positions[0], s.sessionFlipped) { stuck = it }
                else -> {
                    // Réfutation d'un mauvais coup, ou coup de l'élève en cours de jugement : affiché tel qu'il l'a joué.
                    val r = s.refute ?: s.tentative
                    val pos = r?.position ?: line.positions[cur]
                    val last = r?.last ?: if (cur > 0) line.moves[cur - 1] else null
                    val heroTurn = s.challenge && !s.done && !s.judging && r == null && cur < line.size && line.positions[cur].whiteToMove == l.heroWhite
                    val next = if (cur < line.size) line.moves[cur] else null
                    // Repères de Comprendre : cases dorées (observation) et flèches bleues (démonstration) de la leçon.
                    val visuals = if (!s.challenge && s.variant == 0) l.stepVisuals[cur] else null
                    val refArrow = refuteArrow(r)
                    ChessBoard(
                        position = pos,
                        modifier = Modifier.fillMaxSize(),
                        flipped = s.flipped,
                        // Comprendre est une démonstration : l'échiquier ne se joue qu'en Pratiquer.
                        interactive = heroTurn,
                        lastMove = last,
                        anim = s.anim,
                        arrows = when {
                            refArrow != null -> listOf(refArrow)
                            heroTurn && s.suggest != null -> s.suggest!!.let { listOf(Arrow(it.from, it.to, c.green)) }
                            heroTurn && s.hintStage >= 2 && next != null -> listOf(Arrow(next.from, next.to, c.green))
                            // Démonstration pédagogique : flèches bleues (le dernier coup est déjà surligné).
                            !s.challenge -> (visuals?.arrows ?: emptyList()).map { (f, t) -> Arrow(f, t, c.blue) }
                            else -> emptyList()
                        },
                        marks = when {
                            heroTurn && s.hintStage >= 1 && next != null -> listOf(next.from to c.green)
                            visuals != null -> visuals.marks.map { it to c.gold }
                            else -> emptyList()
                        },
                        onIllegalTouch = { stuck = GameLogic.whyStuck(pos, it) },
                        // Réfutation affichée : un toucher sur l'échiquier revient au coup manqué pour rejouer aussitôt.
                        onSquareTap = if (s.refute != null) { _ -> vm.resumeAtMiss() } else null,
                        onMove = { m -> vm.challengeMove(m) },
                    )
                }
            }
        },
        bottom = {
            val egs = eg
            val rxs = rx
            val quiz = s.quiz
            when {
                egs != null -> EndgamePanel(vm, egs, s.review, stuck, s.lessonPractice)
                rxs != null -> ReflexPanel(vm, rxs)
                pr != null -> PracticePanel(vm, pr, s.review, stuck, s.lessonPractice)
                quiz != null -> QuizPanel(vm, l, quiz, toPractice = !s.challenge)
                !s.challenge -> {
                    // Progression dans la leçon : chaque coup est cliquable (sur tablette, tableau complet sous le commentaire).
                    val wide = LocalIsTwoPane.current
                    if (!wide) MoveStrip(
                        line, cur, Modifier.padding(top = 4.dp), onSelect = vm::jump, onPrev = { vm.step(-1) }, onNext = { vm.step(1) },
                        emptyText = "Les coups de la leçon s'afficheront ici.", firstTurn = 1,
                    )
                    // Dans une leçon, les tours sont numérotés depuis son début, quelle que soit la partie d'origine.
                    val inVariant = s.variant > 0 && cur > l.variants[s.variant - 1].from
                    val title = when {
                        cur == 0 -> "Objectif de la leçon"
                        inVariant -> l.variants[s.variant - 1].title
                        else -> line.stepTitle(cur, first = 1)
                    }
                    // Position de départ : le résumé et la règle seulement ; la fiche complète s'ouvre dans un tiroir.
                    val base = if (cur == 0) {
                        l.summary + "\n\nÀ retenir : " + l.takeaway + "\n\nTouchez Lecture pour dérouler la leçon coup par coup."
                    } else texts[cur - 1]
                    val text = when {
                        // L'aide sur la voix s'ajoute au commentaire, qui reste lisible.
                        voiceTip != null -> voiceTip + "\n\n" + base
                        s.note != null -> s.note + "\n\n" + base
                        // Fin de la leçon : la suite du parcours est annoncée.
                        cur == line.size -> base + if (quizPending) "\n\nUne question de fin, puis place à la pratique." else "\n\nÀ vous maintenant : touchez Passer à la pratique."
                        else -> base
                    }
                    val tone = when {
                        voiceTip != null -> Tone.GOLD
                        s.note != null -> s.noteTone
                        else -> Tone.INFO
                    }
                    FeedbackCard(
                        text, tone, Modifier.padding(top = 4.dp).then(if (wide) Modifier.heightIn(max = 220.dp) else Modifier.weight(1f)), title = title, busy = s.judging,
                        action = when {
                            cur == line.size -> bridges
                            else -> null
                        },
                    )
                    if (wide) MoveTable(line, cur, emptyList(), vm::jump, Modifier.padding(top = 6.dp).weight(1f))
                    EqualRow(Modifier.padding(vertical = 6.dp)) {
                        // Toujours visible : relit ou arrête la voix, ou explique comment l'activer.
                        DuckIconButton(
                            // Voix coupée : icône barrée, atténuée ; voix active : haut-parleur (ou arrêt pendant la lecture).
                            if (speaking || !narration) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            when {
                                voiceTip != null && voice != false && !narration -> "Activer la lecture vocale"
                                voiceTip != null -> "Fermer l'aide sur la lecture vocale"
                                !narration -> "Lecture vocale désactivée : comment l'activer"
                                speaking -> "Arrêter la lecture à voix haute"
                                else -> "Réécouter le commentaire"
                            },
                            {
                                when {
                                    // Second toucher, voix coupée mais disponible : on l'active tout de suite.
                                    voiceTip != null && voice != false && !narration -> { voiceTip = null; vm.enableNarration() }
                                    voiceTip != null -> voiceTip = null
                                    narration -> vm.toggleSpeech()
                                    voice == false -> {
                                        voiceTip = "Aucune voix française n'est installée sur ce téléphone : choisissez-en une dans les réglages vocaux d'Android qui s'ouvrent."
                                        runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    }
                                    else -> {
                                        vm.checkVoice()
                                        voiceTip = "La lecture vocale est désactivée. Touchez de nouveau le haut-parleur pour l'activer (réglage aussi dans Paramètres, section Audio, voix et retours)."
                                    }
                                }
                            },
                        )
                        // Toujours les mêmes quatre commandes : voix, Début, Lecture ou Pause, Fin (icônes aux bords pour que
                        // rien ne se coupe sur 360 dp).
                        DuckIconButton(Icons.Rounded.SkipPrevious, "Revenir au début", vm::rewind, enabled = cur > 0)
                        // La voix qui lit (l'objectif à l'ouverture, ou un commentaire) compte comme une lecture en cours :
                        // le bouton dit alors « Pause » et la suspend ; « Lecture » reprend la voix puis déroule les coups.
                        // À la fin de la leçon : la question de fin s'il y en a une, puis le passage à la pratique.
                        val running = s.playing || speaking
                        when {
                            cur < line.size || running -> DuckButton(
                                if (running) "Pause" else "Lecture", { if (!s.playing && speaking) vm.pause() else vm.togglePlay() }, Modifier.weight(1f),
                                accent = c.blue, filled = true, icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            )
                            quizPending -> DuckButton(
                                "Question de fin", vm::startQuiz, Modifier.weight(1f), accent = c.gold, filled = true, icon = Icons.Rounded.Lightbulb,
                            )
                            else -> DuckButton(
                                "Passer à la pratique", { vm.setChallenge(true) }, Modifier.weight(1f), accent = c.green, filled = true,
                                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            )
                        }
                        DuckIconButton(Icons.Rounded.SkipNext, "Aller à la fin", vm::last, enabled = cur < line.size)
                    }
                }
                else -> {
                    // Pratiquer réussi : le bilan et les passerelles gardent toute la hauteur ; la leçon se relit coup par coup
                    // dans Comprendre (aucune bande de coups ajoutée ici, qui tronquerait le bilan).
                    // Même numérotation par tour que Comprendre (depuis le début de la leçon).
                    val turn = line.turnOf(cur.coerceAtMost(line.size - 1), first = 1)
                    val side = if (l.heroWhite) "Blancs" else "Noirs"
                    val title = when {
                        s.done && model -> "Partie terminée · ${s.guessScore} points sur ${s.guessMax}"
                        s.done -> "Exercice terminé · ${starsText(s.stars)}"
                        model -> "Devinez le coup des $side · coup $turn sur ${line.turnOf(line.size - 1, first = 1)} · ${s.guessScore} points sur ${s.guessMax}"
                        else -> "Jouez les $side" + (if (s.variant > 0) " · ${variantLabel(l, s.variant)}" else "") +
                            " · coup $turn sur ${line.turnOf(line.size - 1, first = 1)}"
                    }
                    FeedbackCard(
                        stuck ?: s.message, if (stuck != null) Tone.GOLD else s.tone, Modifier.padding(top = 4.dp).weight(1f), title = title, busy = s.judging,
                        action = if (s.done && s.review == null && s.repertoire == null) bridges else null,
                    )
                    if (s.refute != null && s.refuteUntil > 0) RefuteCountdown(s.refuteUntil, s.refuteHold, vm::holdRefutation)
                    EqualRow(Modifier.padding(vertical = 6.dp)) {
                        val review = s.review
                        val rep = s.repertoire
                        when {
                            s.refute != null -> DuckButton(
                                "Réessayer", vm::resumeAtMiss, Modifier.weight(1f), accent = c.red, filled = true,
                                icon = Icons.AutoMirrored.Rounded.Undo, contentDescription = "Réessayer : revenir avant le coup manqué",
                            )
                            s.done && review != null -> ReviewNextButton(vm, review, Modifier.weight(1f))
                            s.done && rep != null -> {
                                val lastItem = rep.index + 1 >= rep.items.size
                                DuckButton(
                                    if (lastItem) "Terminer (${rep.results.count { it }} sur ${rep.items.size})" else "Ouverture suivante",
                                    { vm.nextRepertoire(); if (lastItem) vm.closeRepertoire() }, Modifier.weight(1f), accent = c.green, filled = true,
                                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                                )
                            }
                            s.done -> {
                                DuckIconButton(Icons.Rounded.RestartAlt, "Recommencer l'exercice", vm::restartChallenge)
                                // Relire la leçon dans Comprendre, à la position atteinte (la fin).
                                DuckButton(
                                    "Revoir les coups", { vm.setChallenge(false) }, Modifier.weight(1f), icon = Icons.Rounded.Visibility,
                                    contentDescription = "Revoir les coups de la leçon dans Comprendre",
                                )
                                if (quizPending) DuckButton(
                                    "Question de fin", vm::startQuiz, Modifier.weight(1.2f), accent = c.gold, filled = true, icon = Icons.Rounded.Lightbulb,
                                )
                                else DuckButton(
                                    "Leçon suivante", { vm.nextLesson(guided = true) }, Modifier.weight(1.2f), accent = c.green, filled = true,
                                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                                )
                            }
                            model -> {
                                DuckIconButton(Icons.Rounded.RestartAlt, "Recommencer l'exercice", vm::restartChallenge)
                                // Coups clés (par défaut) ou tous les coups : un interrupteur en clair, qui relance la partie.
                                DuckChip(
                                    if (s.keyMoments) "Coups clés" else "Tous les coups", s.keyMoments, { vm.setKeyMoments(!s.keyMoments) },
                                    Modifier.semantics {
                                        contentDescription = if (s.keyMoments) "À deviner : les coups clés seulement. Toucher pour deviner tous les coups"
                                        else "À deviner : tous les coups. Toucher pour ne deviner que les coups clés"
                                    },
                                    accent = c.gold,
                                )
                                DuckButton("Passer ce coup", vm::skipGuess, Modifier.weight(1f), enabled = !s.judging, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                            }
                            else -> {
                                DuckButton("Recommencer", vm::restartChallenge, Modifier.weight(1f), icon = Icons.Rounded.RestartAlt)
                                HintButton(s.hintStage, vm::hint, Modifier.weight(1.4f), enabled = !s.judging)
                            }
                        }
                    }
                }
            }
        },
    )

    sheets()
}
