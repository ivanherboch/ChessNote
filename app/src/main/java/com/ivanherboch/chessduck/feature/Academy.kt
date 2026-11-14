package com.ivanherboch.chessduck.feature

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.EmptyState
import com.ivanherboch.chessduck.ui.InfoPill
import com.ivanherboch.chessduck.ui.MoveStrip
import java.text.Normalizer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.data.LessonProgress
import com.ivanherboch.chessduck.ui.Arrow
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.ChessBoard
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.EqualRow
import com.ivanherboch.chessduck.ui.FeedbackCard
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.ModuleLayout
import com.ivanherboch.chessduck.ui.SectionTitle
import com.ivanherboch.chessduck.ui.Tone
import com.ivanherboch.chessduck.ui.moveAnim
import com.ivanherboch.chessduck.ui.undoAnim
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AcademyState(
    val lessonId: Int = 1,
    /** null = toutes les catégories. */
    val filter: LessonCategory? = null,
    val challenge: Boolean = false,
    val cursor: Int = 0,
    val playing: Boolean = false,
    val flipped: Boolean = false,
    val anim: BoardAnim? = null,
    val hintStage: Int = 0,
    val message: String = "",
    val tone: Tone = Tone.INFO,
    val done: Boolean = false,
)

class AcademyViewModel(app: Application) : AndroidViewModel(app) {
    private val duck = app as ChessDuckApp
    val state = MutableStateFlow(AcademyState())
    val mastered = duck.db.user().masteredLessons().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private var job: Job? = null

    val lesson: Lesson get() = Lessons.byId(state.value.lessonId)

    init {
        viewModelScope.launch {
            val id = duck.prefs.settings.first().lessonId
            open(id, save = false)
        }
    }

    fun open(id: Int, save: Boolean = true) {
        job?.cancel()
        val l = Lessons.byId(id)
        val challenge = state.value.challenge
        state.update {
            it.copy(
                lessonId = l.id, cursor = 0, playing = false, flipped = !l.heroWhite, anim = null, hintStage = 0, done = false,
                filter = if (it.filter != null && it.filter != l.category) l.category else it.filter,
            )
        }
        if (save) viewModelScope.launch { duck.prefs.setLesson(l.id) }
        if (challenge) startChallenge() else state.update { it.copy(message = "", tone = Tone.INFO) }
    }

    fun setFilter(cat: LessonCategory?) {
        state.update { it.copy(filter = cat) }
        if (cat != null && lesson.category != cat) open(Lessons.all.first { it.category == cat }.id)
    }

    fun flip() = state.update { it.copy(flipped = !it.flipped) }

    fun setChallenge(on: Boolean) {
        if (state.value.challenge == on) return
        job?.cancel()
        state.update { it.copy(challenge = on, cursor = 0, playing = false, anim = null, hintStage = 0, done = false, message = "", tone = Tone.INFO) }
        if (on) startChallenge()
    }

    // ---- Mode Guidé ----
    fun goTo(target: Int) {
        val l = lesson
        val s = state.value
        val t = target.coerceIn(0, l.line.size)
        if (t == s.cursor) return
        val anim = when (t) {
            s.cursor + 1 -> moveAnim(l.line.positions[s.cursor], l.line.moves[s.cursor])
            s.cursor - 1 -> undoAnim(l.line.positions[t], l.line.moves[t])
            else -> null
        }
        state.update { it.copy(cursor = t, anim = anim) }
    }

    fun step(delta: Int) { pause(); goTo(state.value.cursor + delta) }
    fun rewind() { pause(); goTo(0) }

    fun pause() {
        job?.cancel()
        state.update { it.copy(playing = false) }
    }

    fun togglePlay() {
        if (state.value.playing) return pause()
        job?.cancel()
        if (state.value.cursor >= lesson.line.size) goTo(0)
        state.update { it.copy(playing = true) }
        job = viewModelScope.launch {
            while (state.value.cursor < lesson.line.size) {
                delay(if (state.value.cursor == 0) 700 else 2600)
                goTo(state.value.cursor + 1)
            }
            state.update { it.copy(playing = false) }
        }
    }

    // ---- Mode Défi ----
    private fun sideName(l: Lesson) = if (l.heroWhite) "Blancs" else "Noirs"

    private fun startChallenge() {
        val l = lesson
        state.update {
            it.copy(
                cursor = 0, anim = null, hintStage = 0, done = false, tone = Tone.INFO, flipped = !l.heroWhite,
                message = "À vous de jouer les coups des ${sideName(l)} sur l'échiquier. ${l.summary}",
            )
        }
        advanceOpponent()
    }

    fun restartChallenge() {
        job?.cancel()
        startChallenge()
    }

    private fun heroToMove(cursor: Int): Boolean {
        val l = lesson
        return cursor < l.line.size && l.line.positions[cursor].whiteToMove == l.heroWhite
    }

    /** Joue les réponses adverses jusqu'au prochain coup du joueur, puis conclut la leçon si elle est finie. */
    private fun advanceOpponent() {
        job?.cancel()
        job = viewModelScope.launch {
            val l = lesson
            while (state.value.cursor < l.line.size && !heroToMove(state.value.cursor)) {
                delay(650)
                val cur = state.value.cursor
                state.update { it.copy(cursor = cur + 1, anim = moveAnim(l.line.positions[cur], l.line.moves[cur]), hintStage = 0) }
            }
            if (state.value.cursor >= l.line.size) {
                duck.db.user().markLesson(LessonProgress(l.id, System.currentTimeMillis()))
                state.update {
                    it.copy(done = true, tone = Tone.GOOD, message = "Leçon maîtrisée ! Vous avez joué tous les coups clés de « ${l.title} ».")
                }
            }
        }
    }

    /** Coup du joueur en mode Défi ; renvoie vrai s'il est correct. */
    fun challengeMove(m: Move): Boolean {
        val s = state.value
        val l = lesson
        if (!s.challenge || s.done || !heroToMove(s.cursor)) return false
        val expected = l.line.moves[s.cursor]
        if (m == expected) {
            state.update {
                it.copy(
                    cursor = s.cursor + 1, anim = moveAnim(l.line.positions[s.cursor], m), hintStage = 0, tone = Tone.GOOD,
                    message = "${l.line.sans[s.cursor]} — ${l.texts[s.cursor]}",
                )
            }
            advanceOpponent()
            return true
        }
        val played = l.line.positions[s.cursor].san(m)
        state.update {
            it.copy(tone = Tone.BAD, message = "$played n'est pas le coup de la leçon. Réfléchissez à l'idée du thème, ou demandez un indice.")
        }
        return false
    }

    fun hint() {
        val s = state.value
        if (!s.challenge || s.done || !heroToMove(s.cursor)) return
        val stage = (s.hintStage + 1).coerceAtMost(2)
        state.update {
            it.copy(
                hintStage = stage, tone = Tone.GOLD,
                message = if (stage == 1) "Indice 1/2 : la pièce à jouer est surlignée en vert." else "Indice 2/2 : suivez la flèche.",
            )
        }
    }

    fun nextLesson() = shiftLesson(1)
    fun prevLesson() = shiftLesson(-1)

    /** Leçon voisine dans l'ordre du catalogue (limité à la catégorie filtrée s'il y en a une). */
    private fun shiftLesson(delta: Int) {
        val all = Lessons.all
        val s = state.value
        val pool = if (s.filter != null) all.filter { it.category == s.filter } else all
        val idx = pool.indexOfFirst { it.id == s.lessonId }.coerceAtLeast(0)
        open(pool[(idx + delta + pool.size) % pool.size].id)
    }

    /** Dans le mode Guidé, saute directement au coup [n] (nombre de demi-coups joués). */
    fun jump(n: Int) { pause(); goTo(n) }
    fun last() { pause(); goTo(lesson.line.size) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademyScreen(vm: AcademyViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val mastered by vm.mastered.collectAsStateWithLifecycle()
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val l = Lessons.byId(s.lessonId)
    val line = l.line
    var catalog by remember { mutableStateOf(false) }
    val total = Lessons.all.size
    val cur = s.cursor.coerceIn(0, line.size)

    ModuleLayout(
        bottomMin = 190.dp,
        top = {
            // Sélecteur de leçon : précédente / leçon courante (ouvre le catalogue) / suivante.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Leçon précédente", vm::prevLesson)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.surface)
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
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    listOf("Guidé (démo)" to false, "Défi (pratique)" to true).forEachIndexed { i, (label, challenge) ->
                        SegmentedButton(
                            selected = s.challenge == challenge, onClick = { vm.setChallenge(challenge) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = { Icon(if (challenge) Icons.Rounded.SportsScore else Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = c.goldRole.fill, activeContentColor = c.goldRole.onFill, activeBorderColor = c.outlineStrong,
                                inactiveContainerColor = c.surface, inactiveContentColor = c.text, inactiveBorderColor = c.outlineStrong,
                            ),
                        ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                    }
                }
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
                onMove = { m -> if (vm.challengeMove(m)) fb?.success() else fb?.error() },
            )
        },
        bottom = {
            // Progression dans la leçon : chaque coup est cliquable en mode Guidé.
            MoveStrip(
                line, cur, Modifier.padding(top = 4.dp),
                onSelect = if (!s.challenge) vm::jump else null,
                onPrev = if (!s.challenge) ({ vm.step(-1) }) else null,
                onNext = if (!s.challenge) ({ vm.step(1) }) else null,
                emptyText = "Les coups de la leçon s'afficheront ici.",
            )
            if (!s.challenge) {
                val title = if (cur == 0) "Objectif de la leçon" else moveLabel(line.positions[cur - 1].fullmove, line.positions[cur - 1].whiteToMove, line.sans[cur - 1]) + " · coup $cur / ${line.size}"
                val text = if (cur == 0) l.summary + " Touchez Lecture pour dérouler la leçon coup par coup." else l.texts[cur - 1]
                FeedbackCard(text, Tone.INFO, Modifier.padding(top = 4.dp).weight(1f), title = title)
                EqualRow(Modifier.padding(vertical = 6.dp)) {
                    DuckButton("Début", vm::rewind, Modifier.weight(1f), enabled = cur > 0, icon = Icons.Rounded.SkipPrevious)
                    DuckButton(
                        if (s.playing) "Pause" else "Lecture", vm::togglePlay, Modifier.weight(1.5f), accent = c.blue, filled = true,
                        icon = if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    )
                    DuckButton("Fin", vm::last, Modifier.weight(1f), enabled = cur < line.size, icon = Icons.Rounded.SkipNext)
                }
            } else {
                val title = if (s.done) "Défi réussi" else "Défi : jouez les ${if (l.heroWhite) "Blancs" else "Noirs"} · coup $cur / ${line.size}"
                FeedbackCard(s.message, s.tone, Modifier.padding(top = 4.dp).weight(1f), title = title)
                EqualRow(Modifier.padding(vertical = 6.dp)) {
                    DuckButton("Recommencer", vm::restartChallenge, Modifier.weight(1f), icon = Icons.Rounded.RestartAlt)
                    if (s.done) DuckButton("Leçon suivante", vm::nextLesson, Modifier.weight(1.4f), accent = c.green, filled = true, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                    else DuckButton("Indice ${(s.hintStage + 1).coerceAtMost(2)}/2", vm::hint, Modifier.weight(1.4f), accent = c.green, icon = Icons.Rounded.Lightbulb)
                }
            }
        },
    )

    if (catalog) LessonCatalog(vm, s, mastered) { catalog = false }
}

/** Catalogue des leçons : recherche, statut (toutes / à maîtriser / maîtrisées) et catégorie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonCatalog(vm: AcademyViewModel, s: AcademyState, mastered: Set<Int>, onClose: () -> Unit) {
    val c = LocalDuck.current
    var query by remember { mutableStateOf("") }
    var status by remember { mutableIntStateOf(0) }
    var cat by remember { mutableStateOf(s.filter) }
    val total = Lessons.all.size
    fun norm(t: String) = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
    val q = norm(query.trim())
    val items = Lessons.all.filter { l ->
        (cat == null || l.category == cat) &&
            (status == 0 || (status == 1) == (l.id !in mastered)) &&
            (q.isEmpty() || norm(l.title).contains(q) || norm(l.summary).contains(q) || l.id.toString() == q)
    }
    DuckSheet("Leçons — ${mastered.size} / $total maîtrisées", onClose) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Rechercher une leçon") },
            placeholder = { Text("ex. fourchette, Lucena, sicilienne…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) DuckIconButton(Icons.Rounded.Close, "Effacer la recherche", { query = "" }) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.text, unfocusedTextColor = c.text, focusedBorderColor = c.goldText, unfocusedBorderColor = c.outlineStrong,
                cursorColor = c.goldText, focusedLabelColor = c.goldText, unfocusedLabelColor = c.muted,
                focusedLeadingIconColor = c.muted, unfocusedLeadingIconColor = c.muted,
            ),
        )
        SectionTitle("Statut")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Toutes", "À maîtriser", "Maîtrisées").forEachIndexed { i, label -> DuckChip(label, status == i, { status = i }, accent = c.blue) }
        }
        SectionTitle("Catégorie")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DuckChip("Toutes", cat == null, { cat = null; vm.setFilter(null) }, accent = c.blue)
            for (k in LessonCategory.entries) {
                val n = Lessons.all.count { it.category == k }
                val m = Lessons.all.count { it.category == k && it.id in mastered }
                DuckChip("${k.label} $m/$n", cat == k, { cat = k; vm.setFilter(k) }, accent = c.blue)
            }
        }
        SectionTitle("${items.size} leçon${if (items.size > 1) "s" else ""} — toutes en accès libre")
        if (items.isEmpty()) {
            EmptyState(
                Icons.Rounded.SearchOff, "Aucune leçon trouvée", "Essayez un autre mot ou retirez des filtres.",
                actionLabel = "Réinitialiser les filtres", onAction = { query = ""; status = 0; cat = null; vm.setFilter(null) },
            )
        }
        for (item in items) {
            val current = item.id == s.lessonId
            val done = item.id in mastered
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (current) c.surfaceHi else c.surface)
                    .clickable(role = Role.Button) { vm.open(item.id); onClose() }
                    .semantics { stateDescription = (if (done) "maîtrisée" else "à maîtriser") + if (current) ", leçon en cours" else "" }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.greenText)
                    else Text("${item.id}", color = c.goldText, style = MaterialTheme.typography.labelLarge)
                }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(item.title, color = c.text, style = MaterialTheme.typography.titleSmall)
                    Text(item.summary, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.muted)
            }
        }
    }
}

fun moveLabel(fullmove: Int, white: Boolean, san: String) = if (white) "$fullmove. $san" else "$fullmove… $san"
