package com.ivanherboch.chessduck.feature

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import com.ivanherboch.chessduck.ui.HintButton
import com.ivanherboch.chessduck.ui.HintIconButton
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import com.ivanherboch.chessduck.ui.LocalIsTwoPane
import com.ivanherboch.chessduck.ui.BoardRow
import com.ivanherboch.chessduck.ui.LoadingState
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.icons.SearchOff
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.ui.DuckSegmentedRow
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.InfoPill
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.MoveStrip
import com.ivanherboch.chessduck.ui.RefuteCountdown
import com.ivanherboch.chessduck.ui.refuteArrow
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.icons.ExpandMore
import com.ivanherboch.chessduck.ui.icons.Extension
import com.ivanherboch.chessduck.ui.icons.Pause
import com.ivanherboch.chessduck.ui.icons.RestartAlt
import com.ivanherboch.chessduck.ui.icons.SkipNext
import com.ivanherboch.chessduck.ui.icons.SkipPrevious
import com.ivanherboch.chessduck.ui.icons.SportsEsports
import com.ivanherboch.chessduck.ui.icons.SportsScore
import com.ivanherboch.chessduck.ui.icons.SwapVert
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
    /** Tiroir « Entraînements », ouvert depuis le bouton de l'en-tête de l'application. */
    trainingsOpen: Boolean = false,
    onTrainings: (Boolean) -> Unit = {},
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val mastered by vm.mastered.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val variantsDone by vm.variantsDone.collectAsStateWithLifecycle()
    val eg by vm.endgame.state.collectAsStateWithLifecycle()
    val rx by vm.reflex.state.collectAsStateWithLifecycle()
    val narration by vm.narration.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    // Retour Android pendant une séance (finale, réflexes, série, question, révision, répertoire) : on la quitte, on ne
    // quitte pas l'Académie.
    val inSession = eg != null || rx != null || s.practice != null || s.quiz != null || s.review != null || s.repertoire != null
    BackHandler(enabled = inSession) { vm.closeTraining() }
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
    // Le Défi de la leçon reste à réussir (au moins 2 étoiles) : c'est l'étape suivante du parcours, avant la leçon suivante.
    val needsChallenge = l.id !in mastered && stars < 2 && s.review == null && s.repertoire == null
    val pr = s.practice
    val asking = vm.asking(s)
    val model = l.kind == LessonKind.MODEL_GAME
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
    // Séance lancée depuis le tiroir « Entraînements » : on y revient quand elle se termine.
    val backLabel = if (s.fromDrawer) "Revenir aux entraînements" else "Revenir à la leçon"
    var drawerSeen by remember { mutableIntStateOf(s.drawerReturn) }
    LaunchedEffect(s.drawerReturn) { if (s.drawerReturn != drawerSeen) { drawerSeen = s.drawerReturn; onTrainings(true) } }
    LaunchedEffect(eg?.pulse?.id) { buzz(1, eg?.pulse) }
    LaunchedEffect(rx?.pulse?.id) { buzz(2, rx?.pulse) }
    val endFen = line.positions[cur].takeIf { it.legalMoves.isNotEmpty() }?.toFen()
    // Parties modèles : le camp se choisit avant d'ouvrir le Jeu Libre (Blancs ou Noirs).
    var playChoice by remember { mutableStateOf<String?>(null) }
    val play: (String, Boolean) -> Unit = if (model) { fen, _ -> playChoice = fen } else onPlayPosition
    val bridges: @Composable () -> Unit = { LessonBridges(l, endFen, onTrainTheme, play, onVision) }
    // Partie modèle : « Jouer cette position » à chaque coup (hors jugement et réfutation).
    val modelPlay = model && endFen != null && !s.judging && s.refute == null && !s.done

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
    ModuleLayout(
        bottomMin = 190.dp,
        top = {
            val session = vm.sessionTitle(s, eg, rx)
            if (session != null) {
                TrainingHeader(session, vm::closeTraining, vm::flip, backLabel)
                return@ModuleLayout
            }
            // Défi réussi (2 étoiles ou plus) mais leçon pas encore validée sur problèmes ou contre l'Ordinateur.
            val toValidate = l.id !in mastered && stars >= 2 && vm.validation(l) != Validation.NONE
            // Sélecteur de leçon : précédente / leçon courante (ouvre le catalogue) / suivante.
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Leçon précédente", vm::prevLesson)
                Column(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
                        .border(1.dp, c.outline, RoundedCornerShape(DuckRadius.M))
                        .clickable(role = Role.Button, onClickLabel = "Ouvrir le catalogue des leçons") { catalog = true }
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = "Leçon ${Lessons.number(l.id)} : ${l.title}, ${l.category.label}, palier ${levelSpoken(l.level)}" +
                                (if (l.id in mastered) ", maîtrisée" else if (toValidate) ", Défi réussi, validation restante" else "") + (if (stars > 0) ", ${starsText(stars)}" else "") +
                                ". ${mastered.size} leçons maîtrisées sur $total. Ouvrir le catalogue des leçons"
                            onClick { catalog = true; true }
                        }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    // Deux lignes compactes pour laisser le maximum de hauteur à l'échiquier : le titre, puis la
                    // catégorie, le palier, la progression, la maîtrise et les étoiles en petit.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${Lessons.number(l.id)}. ${l.title}", color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${l.category.label} · P${l.level}", color = c.blueText, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (l.id in mastered) Icon(Icons.Rounded.CheckCircle, "Maîtrisée", tint = c.greenText, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                        else if (toValidate) Text(" · À valider", color = c.goldText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        if (stars > 0) StarRow(stars, Modifier.padding(start = 6.dp), size = 14.dp)
                        Spacer(Modifier.weight(0.01f))
                        Text("${mastered.size}/$total", color = c.goldText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Leçon suivante", vm::nextLesson)
            }
            // Très grande police : les modes occupent toute une rangée (libellés jamais coupés), les icônes passent dessous.
            val big = LocalDensity.current.fontScale > 1.3f
            val modes: @Composable (Modifier) -> Unit = { m ->
                // Partie modèle : libellé court (« Deviner »), la rangée porte aussi la fiche et « Jouer cette position ».
                DuckSegmentedRow(
                    listOf("Guidé", if (model) "Deviner" else "Défi"), if (s.challenge) 1 else 0, { i -> vm.setChallenge(i == 1) }, m,
                    icons = if (big) null else listOf(Icons.Rounded.Visibility, Icons.Rounded.SportsScore),
                )
            }
            val tools: @Composable () -> Unit = {
                // Les variantes d'une ouverture se choisissent ici (Guidé comme Défi), sans rangée de plus sous l'échiquier.
                // Hors répertoire et révision, qui imposent leur ligne.
                if (l.variants.isNotEmpty() && s.repertoire == null && s.review == null) VariantMenu(l, s.variant, vm::setVariant, variantsDone)
                // Fiche de la leçon (pour toutes les leçons) et, pour une partie modèle, « Jouer cette position » à chaque
                // coup : des icônes ici, pour que l'échiquier garde toute sa taille.
                DuckIconButton(Icons.Rounded.Info, "Fiche de la leçon", { brief = true }, accent = c.blue)
                // Partie modèle en mode Deviner : ne deviner que les moments clés (les autres coups se jouent seuls).
                if (model && s.challenge && l.pauseSteps.isNotEmpty()) DuckIconButton(
                    Icons.Rounded.Star,
                    if (s.keyMoments) "Moments clés seulement (activé) : deviner tous les coups" else "Ne deviner que les ${l.pauseSteps.size} moments clés",
                    { vm.setKeyMoments(!s.keyMoments) }, accent = c.gold, selected = s.keyMoments,
                )
                if (modelPlay) DuckIconButton(
                    Icons.Rounded.SportsEsports, "Jouer cette position contre l'Ordinateur, avec le camp de votre choix", { playChoice = endFen }, accent = c.blue,
                )
                DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
            }
            if (big) {
                modes(Modifier.fillMaxWidth().padding(top = 2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) { tools() }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    modes(Modifier.weight(1f))
                    tools()
                }
            }
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
                egs?.position != null -> {
                    val pos = egs.position
                    val flip = !egs.family.heroWhite != s.sessionFlipped
                    val hint = egs.hint.takeIf { egs.heroToMove }
                    // La jauge n'apparaît qu'une fois la position terminée : elle montre l'issue sans aider pendant la recherche.
                    BoardRow(egs.evalWhite.takeIf { egs.over || egs.finished }, flip) { mod ->
                        ChessBoard(
                            position = pos, modifier = mod, flipped = flip, interactive = egs.heroToMove,
                            lastMove = egs.last, anim = egs.anim,
                            arrows = listOfNotNull(
                                egs.punish?.let { Arrow(it.from, it.to, c.red) },
                                // Coup repris après une erreur : le bon coup en vert.
                                egs.better?.takeIf { egs.replay && egs.heroToMove && egs.before == null && egs.moves == egs.beforeMoves }
                                    ?.let { Arrow(it.from, it.to, c.green) },
                                hint?.takeIf { egs.hintStage >= 2 }?.let { Arrow(it.from, it.to, c.green) },
                            ),
                            marks = listOfNotNull(hint?.takeIf { egs.hintStage == 1 }?.let { it.from to c.green }),
                            onIllegalTouch = { stuck = GameLogic.whyStuck(pos, it) },
                            onMove = vm.endgame::move,
                        )
                    }
                }
                rxs?.position != null -> {
                    val pos = rxs.position!!
                    val t = rxs.threat
                    val cp = rxs.capture
                    val b = rxs.blind
                    ChessBoard(
                        // Calcul à l'aveugle : l'échiquier reste orienté comme au départ, même réaffiché.
                        position = pos, modifier = Modifier.fillMaxSize(), flipped = !(b?.start ?: pos).whiteToMove != s.sessionFlipped,
                        lastMove = t?.lastMove,
                        arrows = when {
                            cp != null -> listOf(Arrow(cp.capture.from, cp.capture.to, c.blue))
                            t != null && rxs.answered -> t.threats.map { (to, from) -> Arrow(from, to, c.red) }
                            b != null && rxs.answered -> listOf(Arrow(b.expected.from, b.expected.to, c.green))
                            else -> emptyList()
                        },
                        marks = when {
                            t != null && rxs.answered -> t.targets.map { it to c.green } + listOfNotNull(rxs.tapped.takeIf { it >= 0 && it !in t.targets }?.let { it to c.red })
                            b != null && !rxs.answered && rxs.from >= 0 -> listOf(rxs.from to c.select)
                            // Calcul à l'aveugle, réponse fausse : la case d'arrivée jouée en rouge, la flèche verte montre le bon coup.
                            b != null && rxs.answered && rxs.tapped >= 0 && rxs.tone == Tone.BAD &&
                                !(rxs.from == b.expected.from && rxs.tapped == b.expected.to) ->
                                listOf(rxs.tapped to c.red)
                            else -> emptyList()
                        },
                        onSquareTap = if ((t != null || b != null) && !rxs.answered) vm.reflex::tap else null,
                    )
                }
                pr != null -> {
                    val pl = pr.line
                    val pc = pr.cursor.coerceIn(0, pl?.size ?: 0)
                    val r = pr.refute
                    val ppos = r?.position ?: pl?.positions?.get(pc) ?: line.positions[0]
                    val pnext = if (pl != null && pc < pl.size) pl.moves[pc] else null
                    val pTurn = pl != null && r == null && !pr.failed && !pr.solved && !pr.finished && pc == pr.progress && pc < pl.size &&
                        ppos.whiteToMove == pr.heroWhite
                    val refArrow = refuteArrow(r)
                    ChessBoard(
                        position = ppos,
                        modifier = Modifier.fillMaxSize(),
                        flipped = !pr.heroWhite != s.sessionFlipped,
                        interactive = pTurn,
                        lastMove = r?.last ?: if (pl != null && pc > 0) pl.moves[pc - 1] else null,
                        anim = pr.anim,
                        arrows = when {
                            refArrow != null -> listOf(refArrow)
                            pnext != null && (pr.failed || (pTurn && pr.hintStage >= 2)) -> listOf(Arrow(pnext.from, pnext.to, c.green))
                            else -> emptyList()
                        },
                        marks = if (pTurn && pr.hintStage >= 1 && pnext != null) listOf(pnext.from to c.green) else emptyList(),
                        onIllegalTouch = { stuck = GameLogic.whyStuck(ppos, it) },
                        // Réfutation affichée : un toucher sur l'échiquier remet la position pour rejouer aussitôt.
                        onSquareTap = if (r != null) { _ -> vm.retryPractice() } else null,
                        onMove = vm::practiceMove,
                    )
                }
                else -> {
                    // Réfutation d'un mauvais coup, ou coup de l'élève en cours de jugement : affiché tel qu'il l'a joué.
                    val r = s.refute ?: s.tentative
                    val pos = r?.position ?: line.positions[cur]
                    val last = r?.last ?: if (cur > 0) line.moves[cur - 1] else null
                    val heroTurn = s.challenge && !s.done && !s.judging && r == null && cur < line.size && line.positions[cur].whiteToMove == l.heroWhite
                    val next = if (cur < line.size) line.moves[cur] else null
                    // Repères du mode Guidé : cases dorées (observation) et flèches bleues (démonstration) de la leçon.
                    val visuals = if (!s.challenge && s.variant == 0) l.stepVisuals[cur] else null
                    val refArrow = refuteArrow(r)
                    ChessBoard(
                        position = pos,
                        modifier = Modifier.fillMaxSize(),
                        flipped = s.flipped,
                        interactive = heroTurn || (asking && !s.judging),
                        lastMove = last,
                        anim = s.anim,
                        arrows = when {
                            refArrow != null -> listOf(refArrow)
                            (heroTurn || asking) && s.suggest != null -> s.suggest!!.let { listOf(Arrow(it.from, it.to, c.green)) }
                            heroTurn && s.hintStage >= 2 && next != null -> listOf(Arrow(next.from, next.to, c.green))
                            // Démonstration pédagogique : flèches bleues (le dernier coup est déjà surligné).
                            !s.challenge -> (visuals?.arrows ?: emptyList()).map { (f, t) -> Arrow(f, t, c.blue) }
                            else -> emptyList()
                        },
                        marks = when {
                            (heroTurn || (asking && !s.judging)) && s.hintStage >= 1 && next != null -> listOf(next.from to c.green)
                            visuals != null -> visuals.marks.map { it to c.gold }
                            else -> emptyList()
                        },
                        onIllegalTouch = { stuck = GameLogic.whyStuck(pos, it) },
                        // Réfutation affichée : un toucher sur l'échiquier revient au coup manqué pour rejouer aussitôt.
                        onSquareTap = if (s.refute != null) { _ -> vm.resumeAtMiss() } else null,
                        onMove = { m -> if (s.challenge) vm.challengeMove(m) else vm.guideMove(m) },
                    )
                }
            }
        },
        bottom = {
            val egs = eg
            val rxs = rx
            val quiz = s.quiz
            when {
                egs != null -> EndgamePanel(vm, egs, s.review, stuck, needsChallenge && egs.family == EndgameFamily.of(l.drill))
                rxs != null -> ReflexPanel(vm, rxs)
                pr != null -> PracticePanel(vm, pr, s.review, stuck)
                quiz != null -> QuizPanel(vm, l, quiz, needsChallenge)
                !s.challenge -> {
                    // Progression dans la leçon : chaque coup est cliquable (sur tablette, tableau complet sous le commentaire).
                    val wide = LocalIsTwoPane.current
                    // Pas de solution en clair : les coups des questions « À vous » encore à résoudre (et la suite) sont masqués.
                    val ownLine = s.variant == 0
                    val hiddenFrom = if (!ownLine) Int.MAX_VALUE else l.pauseSteps.filter { it >= cur && it !in s.answered && it < line.size }.minOrNull() ?: Int.MAX_VALUE
                    if (!wide) MoveStrip(
                        line, cur, Modifier.padding(top = 4.dp), onSelect = vm::jump, onPrev = { vm.step(-1) }, onNext = { vm.step(1) },
                        emptyText = "Les coups de la leçon s'afficheront ici.", firstTurn = 1, hiddenFrom = hiddenFrom,
                    )
                    // Dans une leçon, les tours sont numérotés depuis son début, quelle que soit la partie d'origine.
                    val inVariant = s.variant > 0 && cur > l.variants[s.variant - 1].from
                    val title = when {
                        asking -> ASK
                        cur == 0 -> "Objectif de la leçon"
                        inVariant -> l.variants[s.variant - 1].title
                        else -> line.stepTitle(cur, first = 1)
                    }
                    // Position de départ : le résumé et la règle seulement ; la fiche complète s'ouvre dans un tiroir.
                    val base = if (cur == 0) {
                        l.summary + "\n\nÀ retenir : " + l.takeaway + if (asking) "" else "\n\nTouchez Lecture pour dérouler la leçon coup par coup."
                    } else texts[cur - 1]
                    val text = when {
                        // L'aide sur la voix s'ajoute au commentaire, qui reste lisible.
                        voiceTip != null -> voiceTip + "\n\n" + base
                        stuck != null && asking -> stuck!!
                        s.note != null -> s.note + "\n\n" + base
                        asking -> base + "\n\nJouez votre coup sur l'échiquier, ou touchez Lecture pour découvrir celui de la leçon."
                        else -> base
                    }
                    val tone = when {
                        voiceTip != null -> Tone.GOLD
                        stuck != null && asking -> Tone.GOLD
                        s.note != null -> s.noteTone
                        asking -> Tone.GOLD
                        else -> Tone.INFO
                    }
                    FeedbackCard(
                        text, tone, Modifier.padding(top = 4.dp).then(if (wide) Modifier.heightIn(max = 220.dp) else Modifier.weight(1f)), title = title, busy = s.judging,
                        action = when {
                            cur == line.size -> bridges
                            else -> null
                        },
                    )
                    if (wide) MoveTable(line, cur, emptyList(), vm::jump, Modifier.padding(top = 6.dp).weight(1f), hiddenFrom = hiddenFrom)
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
                        // Avec le haut-parleur, quatre boutons : Début et Fin en icônes pour que rien ne se coupe sur 360 dp.
                        // Arrêt « À vous » : un indice (la pièce à jouer) avant de découvrir le coup avec Lecture.
                        if (asking) HintIconButton(s.hintStage, vm::guideHint, enabled = !s.judging && s.hintStage == 0)
                        else DuckIconButton(Icons.Rounded.SkipPrevious, "Revenir au début", vm::rewind, enabled = cur > 0)
                        // Fin de leçon : le QCM ou la finale à jouer prend la place de Lecture.
                        // La voix qui lit (l'objectif à l'ouverture, ou un commentaire) compte comme une lecture en cours :
                        // le bouton dit alors « Pause » et la suspend ; « Lecture » reprend la voix puis déroule les coups.
                        val running = s.playing || speaking
                        if (cur < line.size || !EndOfLessonAction(vm, l)) DuckButton(
                            if (running) "Pause" else "Lecture", { if (!s.playing && speaking) vm.pause() else vm.togglePlay() }, Modifier.weight(1f),
                            accent = c.blue, filled = true, icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (asking && !running) "Lecture : découvrir le coup de la leçon et continuer" else null,
                        )
                        DuckIconButton(Icons.Rounded.SkipNext, "Aller à la fin", vm::last, enabled = cur < line.size)
                    }
                }
                else -> {
                    // Défi réussi : le bilan et les passerelles gardent toute la hauteur ; la leçon se relit coup par coup en
                    // mode Guidé (aucune bande de coups ajoutée ici, qui tronquerait le bilan).
                    // Même numérotation par tour que le mode Guidé (depuis le début de la leçon).
                    val turn = line.turnOf(cur.coerceAtMost(line.size - 1), first = 1)
                    val side = if (l.heroWhite) "Blancs" else "Noirs"
                    val title = when {
                        s.done && model -> "Partie terminée · ${s.guessScore} points sur ${s.guessMax}"
                        s.done -> "Défi réussi · ${starsText(s.stars)}"
                        model -> "Devinez le coup des $side · coup $turn sur ${line.turnOf(line.size - 1, first = 1)} · ${s.guessScore} points sur ${s.guessMax}"
                        else -> "Défi : jouez les $side" + (if (s.variant > 0) " · ${variantLabel(l, s.variant)}" else "") +
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
                                "Reprendre au coup manqué", vm::resumeAtMiss, Modifier.weight(1f), accent = c.red, filled = true,
                                icon = Icons.AutoMirrored.Rounded.Undo,
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
                                DuckIconButton(Icons.Rounded.RestartAlt, "Recommencer le Défi", vm::restartChallenge)
                                // Relire la leçon en mode Guidé, à la position atteinte (la fin).
                                DuckButton(
                                    "Revoir", { vm.setChallenge(false) }, Modifier.weight(1f), icon = Icons.Rounded.Visibility,
                                    contentDescription = "Revoir les coups de la leçon en mode Guidé",
                                )
                                val v = vm.validation(l)
                                when {
                                    l.id !in mastered && v == Validation.PUZZLES -> DuckButton(
                                        "Valider : 5 problèmes", vm::startPractice, Modifier.weight(1.4f), accent = c.green, filled = true,
                                        icon = Icons.Rounded.Extension, contentDescription = "Valider la leçon sur 5 problèmes inédits du même thème",
                                    )
                                    l.id !in mastered && v == Validation.ENDGAME -> DuckButton(
                                        "Valider : 5 positions", { EndgameFamily.of(l.drill)?.let { vm.startEndgame(it, fromLesson = true) } }, Modifier.weight(1.4f),
                                        accent = c.green, filled = true, icon = Icons.Rounded.SportsEsports,
                                        contentDescription = "Valider la leçon contre l'Ordinateur sur 5 positions",
                                    )
                                    else -> DuckButton("Leçon suivante", { vm.nextLesson(guided = true) }, Modifier.weight(1.4f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                                }
                            }
                            model -> {
                                DuckButton("Recommencer", vm::restartChallenge, Modifier.weight(1f), icon = Icons.Rounded.RestartAlt)
                                DuckButton("Passer ce coup", vm::skipGuess, Modifier.weight(1.4f), enabled = !s.judging, icon = Icons.AutoMirrored.Rounded.ArrowForward)
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

    if (catalog) LessonCatalog(vm, s, mastered, catalogStatus, { catalogStatus = it }, catalogLevel, { catalogLevel = it }) { catalog = false }
    if (brief) LessonBriefSheet(l) { brief = false }
    playChoice?.let { fen -> ChooseColorSheet(fen, l, { white -> playChoice = null; onPlayPosition(fen, white) }) { playChoice = null } }
    if (trainingsOpen) TrainingsSheet(vm, home, s, eg, rx) { onTrainings(false) }
}
