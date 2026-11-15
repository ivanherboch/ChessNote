package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.DuckSegmentedRow
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import com.ivanherboch.chessduck.ui.icons.ExpandMore
import com.ivanherboch.chessduck.ui.icons.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import com.ivanherboch.chessduck.ui.icons.RestartAlt
import com.ivanherboch.chessduck.ui.icons.SkipNext
import com.ivanherboch.chessduck.ui.icons.SkipPrevious
import com.ivanherboch.chessduck.ui.icons.SportsScore
import com.ivanherboch.chessduck.ui.icons.SwapVert
import com.ivanherboch.chessduck.ui.icons.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.InfoPill
import com.ivanherboch.chessduck.ui.MoveStrip
import com.ivanherboch.chessduck.ui.stepTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.ivanherboch.chessduck.ui.turnOf
import android.speech.tts.TextToSpeech
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import com.ivanherboch.chessduck.ui.icons.VolumeOff
import com.ivanherboch.chessduck.ui.icons.VolumeUp
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.HintButton
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.Tone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademyScreen(vm: AcademyViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val mastered by vm.mastered.collectAsStateWithLifecycle()
    val narration by vm.narration.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    // La voix se tait dès que l'Académie n'est plus à l'écran (autre onglet, application en arrière-plan).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.silence() }
    DisposableEffect(vm) { onDispose { vm.silence() } }
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val l = Lessons.byId(s.lessonId)
    val line = l.line
    var catalog by remember { mutableStateOf(false) }
    val total = Lessons.all.size
    val cur = s.cursor.coerceIn(0, line.size)

    // Explication brève quand on touche une pièce qui ne peut pas bouger ; effacée au coup suivant.
    var stuck by remember(s.lessonId, s.cursor, s.challenge) { mutableStateOf<String?>(null) }
    // Haut-parleur touché alors que la voix est coupée ou absente : explication à la place du commentaire.
    var voiceTip by remember(s.lessonId, s.cursor) { mutableStateOf<String?>(null) }
    val voice by vm.voice.collectAsStateWithLifecycle()
    var catalogStatus by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    ModuleLayout(
        bottomMin = 190.dp,
        top = {
            // Sélecteur de leçon : précédente / leçon courante (ouvre le catalogue) / suivante.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Leçon précédente", vm::prevLesson)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
                        .clickable(role = Role.Button, onClickLabel = "Ouvrir le catalogue des leçons") { catalog = true }
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = "Leçon ${l.id} : ${l.title}, ${l.category.label}" + (if (l.id in mastered) ", maîtrisée" else "") +
                                ". ${mastered.size} leçons maîtrisées sur $total. Ouvrir le catalogue des leçons"
                            onClick { catalog = true; true }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InfoPill(l.category.label, accent = c.blue)
                        if (l.id in mastered) Icon(Icons.Rounded.CheckCircle, "Maîtrisée", tint = c.greenText, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                        Spacer(Modifier.weight(1f))
                        Text("${mastered.size}/$total", color = c.goldText, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        "${l.id}. ${l.title}", color = c.text, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Leçon suivante", vm::nextLesson)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                DuckSegmentedRow(
                    listOf("Guidé", "Défi"), if (s.challenge) 1 else 0, { i -> vm.setChallenge(i == 1) },
                    Modifier.weight(1f), icons = listOf(Icons.Rounded.Visibility, Icons.Rounded.SportsScore),
                )
                DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
            }
        },
        board = {
            val pos = line.positions[cur]
            val last = if (cur > 0) line.moves[cur - 1] else null
            val heroTurn = s.challenge && !s.done && cur < line.size && pos.whiteToMove == l.heroWhite
            val next = if (cur < line.size) line.moves[cur] else null
            ChessBoard(
                position = pos,
                modifier = Modifier.fillMaxSize(),
                flipped = s.flipped,
                interactive = heroTurn,
                lastMove = last,
                anim = s.anim,
                arrows = when {
                    heroTurn && s.hintStage >= 2 && next != null -> listOf(Arrow(next.from, next.to, c.green))
                    !s.challenge && last != null -> listOf(Arrow(last.from, last.to, c.blue))
                    else -> emptyList()
                },
                marks = if (heroTurn && s.hintStage >= 1 && next != null) listOf(next.from to c.green) else emptyList(),
                onIllegalTouch = { stuck = GameLogic.whyStuck(pos, it) },
                onMove = { m -> if (vm.challengeMove(m)) fb?.success() else fb?.error() },
            )
        },
        bottom = {
            if (!s.challenge) {
                // Progression dans la leçon : chaque coup est cliquable (masquée en Défi, où elle ne sert pas).
                MoveStrip(
                    line, cur, Modifier.padding(top = 4.dp), onSelect = vm::jump, onPrev = { vm.step(-1) }, onNext = { vm.step(1) },
                    emptyText = "Les coups de la leçon s'afficheront ici.", firstTurn = 1,
                )
                // Dans une leçon, les tours sont numérotés depuis son début, quelle que soit la partie d'origine.
                val title = if (cur == 0) "Objectif de la leçon" else line.stepTitle(cur, first = 1)
                val text = if (cur == 0) l.summary + " Touchez Lecture pour dérouler la leçon coup par coup." else l.texts[cur - 1]
                FeedbackCard(voiceTip ?: text, if (voiceTip != null) Tone.GOLD else Tone.INFO, Modifier.padding(top = 4.dp).weight(1f), title = title)
                EqualRow(Modifier.padding(vertical = 6.dp)) {
                    // Toujours visible : relit ou arrête la voix, ou explique comment l'activer.
                    DuckIconButton(
                        if (speaking) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        when {
                            !narration -> "Lecture vocale : comment l'activer"
                            speaking -> "Arrêter la lecture à voix haute"
                            else -> "Réécouter le commentaire"
                        },
                        {
                            when {
                                narration -> vm.toggleSpeech()
                                voice == false -> {
                                    voiceTip = "Aucune voix française n'est installée sur ce téléphone : choisissez-en une dans les réglages vocaux d'Android qui s'ouvrent."
                                    runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                                else -> {
                                    vm.checkVoice()
                                    voiceTip = "La lecture vocale est désactivée : activez-la dans Paramètres, section Audio, voix et retours."
                                }
                            }
                        },
                    )
                    // Avec le haut-parleur, quatre boutons : Début et Fin en icônes pour que rien ne se coupe sur 360 dp.
                    DuckIconButton(Icons.Rounded.SkipPrevious, "Revenir au début", vm::rewind, enabled = cur > 0)
                    DuckButton(
                        if (s.playing) "Pause" else "Lecture", vm::togglePlay, Modifier.weight(1f), accent = c.blue, filled = true,
                        icon = if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    )
                    DuckIconButton(Icons.Rounded.SkipNext, "Aller à la fin", vm::last, enabled = cur < line.size)
                }
            } else {
                // Même numérotation par tour que le mode Guidé (depuis le début de la leçon).
                val turn = line.turnOf(cur.coerceAtMost(line.size - 1), first = 1)
                val title = if (s.done) "Défi réussi" else "Défi : jouez les ${if (l.heroWhite) "Blancs" else "Noirs"} · coup $turn sur ${line.turnOf(line.size - 1, first = 1)}"
                FeedbackCard(stuck ?: s.message, if (stuck != null) Tone.GOLD else s.tone, Modifier.padding(top = 4.dp).weight(1f), title = title)
                EqualRow(Modifier.padding(vertical = 6.dp)) {
                    DuckButton("Recommencer", vm::restartChallenge, Modifier.weight(1f), icon = Icons.Rounded.RestartAlt)
                    if (s.done) DuckButton("Leçon suivante", vm::nextLesson, Modifier.weight(1.4f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                    else HintButton(s.hintStage, vm::hint, Modifier.weight(1.4f))
                }
            }
        },
    )

    if (catalog) LessonCatalog(vm, s, mastered, catalogStatus, { catalogStatus = it }) { catalog = false }
}

