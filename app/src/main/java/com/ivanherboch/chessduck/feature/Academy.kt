package com.ivanherboch.chessduck.feature

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.ivanherboch.chessduck.ui.AutoText
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
import com.ivanherboch.chessduck.ui.captureBadge
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
                message = "🎯 À vous de jouer les coups des ${sideName(l)} sur l'échiquier. ${l.summary}",
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
                    it.copy(done = true, tone = Tone.GOOD, message = "✅ Maîtrisée ! Vous avez joué tous les coups clés de « ${l.title} ».")
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
                    message = "✅ ${l.line.sans[s.cursor]} — ${l.texts[s.cursor]}",
                )
            }
            advanceOpponent()
            return true
        }
        val played = l.line.positions[s.cursor].san(m)
        state.update {
            it.copy(tone = Tone.BAD, message = "❌ $played n'est pas le coup de la leçon. Réfléchissez à l'idée du thème, ou demandez un 💡 indice.")
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
                message = if (stage == 1) "💡 Indice 1/2 : la pièce à jouer est surlignée en vert." else "💡 Indice 2/2 : suivez la flèche.",
            )
        }
    }

    fun nextLesson() {
        val all = Lessons.all
        val s = state.value
        val pool = if (s.filter != null) all.filter { it.category == s.filter } else all
        val idx = pool.indexOfFirst { it.id == s.lessonId }
        open(pool[(idx + 1) % pool.size].id)
    }
}

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

    ModuleLayout(
        bottomMin = 150.dp,
        top = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DuckChip("Tous", s.filter == null, { vm.setFilter(null) }, Modifier.weight(0.7f), accent = c.blue)
                DuckChip("Ouvertures", s.filter == LessonCategory.OPENING, { vm.setFilter(LessonCategory.OPENING) }, Modifier.weight(1.1f), accent = c.blue)
                DuckChip("Milieu de partie", s.filter == LessonCategory.MIDDLEGAME, { vm.setFilter(LessonCategory.MIDDLEGAME) }, Modifier.weight(1.4f), accent = c.blue)
                DuckChip("Finales", s.filter == LessonCategory.ENDGAME, { vm.setFilter(LessonCategory.ENDGAME) }, Modifier.weight(0.9f), accent = c.blue)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
                    .clickable { catalog = true }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AutoText(
                    "${if (l.id in mastered) "✅" else l.category.emoji} ${l.id}. ${l.title}",
                    maxSize = 15.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                AutoText("📚 ${mastered.size} / $total ▾", color = c.gold, maxSize = 13.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            EqualRow(Modifier.padding(bottom = 5.dp)) {
                DuckButton("👁️ Mode Guidé (Démo)", { vm.setChallenge(false) }, Modifier.weight(1f), accent = c.blue, filled = !s.challenge, height = 34.dp)
                DuckButton("🎯 Mode Défi (Pratique)", { vm.setChallenge(true) }, Modifier.weight(1f), accent = c.gold, filled = s.challenge, height = 34.dp)
            }
        },
        board = {
            val cur = s.cursor.coerceIn(0, line.size)
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
                badges = if (last != null) listOfNotNull(captureBadge(line.positions[cur - 1], last)) else emptyList(),
                onMove = { m -> if (vm.challengeMove(m)) fb?.success() else fb?.error() },
            )
        },
        bottom = {
            if (!s.challenge) {
                val cur = s.cursor.coerceIn(0, line.size)
                val title = if (cur == 0) "Objectif de la leçon" else moveLabel(line.positions[cur - 1].fullmove, line.positions[cur - 1].whiteToMove, line.sans[cur - 1]) + "   (${cur} / ${line.size})"
                val text = if (cur == 0) l.summary + " Appuyez sur ▶ Lecture pour dérouler la leçon coup par coup." else l.texts[cur - 1]
                FeedbackCard(text, Tone.INFO, Modifier.padding(top = 5.dp).weight(1f), maxSize = 15.sp, title = title)
                EqualRow(Modifier.padding(vertical = 5.dp), gap = 5.dp) {
                    DuckButton("⏮", vm::rewind, Modifier.weight(0.7f), enabled = cur > 0, height = 44.dp, maxSize = 17.sp)
                    DuckButton("◀", { vm.step(-1) }, Modifier.weight(0.7f), enabled = cur > 0, height = 44.dp, maxSize = 17.sp)
                    DuckButton(if (s.playing) "⏸ Pause" else "▶ Lecture", vm::togglePlay, Modifier.weight(1.5f), accent = c.blue, filled = true, height = 44.dp, maxSize = 15.sp)
                    DuckButton("▶", { vm.step(1) }, Modifier.weight(0.7f), enabled = cur < line.size, height = 44.dp, maxSize = 17.sp)
                    DuckButton("⇅ Retourner", vm::flip, Modifier.weight(1.3f), height = 44.dp)
                }
            } else {
                FeedbackCard(s.message, s.tone, Modifier.padding(top = 5.dp).weight(1f), maxSize = 15.sp, title = if (s.done) "Défi réussi" else "Défi : jouez les ${if (l.heroWhite) "Blancs" else "Noirs"}")
                EqualRow(Modifier.padding(vertical = 5.dp), gap = 5.dp) {
                    DuckButton("↺ Recommencer", vm::restartChallenge, Modifier.weight(1f), height = 44.dp)
                    DuckButton("⇅ Retourner", vm::flip, Modifier.weight(1f), height = 44.dp)
                    if (s.done) DuckButton("Leçon suivante →", vm::nextLesson, Modifier.weight(1.4f), accent = c.green, filled = true, height = 44.dp, maxSize = 14.sp)
                    else DuckButton("💡 Indice ${(s.hintStage + 1).coerceAtMost(2)}/2", vm::hint, Modifier.weight(1.4f), accent = c.green, height = 44.dp, maxSize = 14.sp)
                }
            }
        },
    )

    if (catalog) {
        var cat by remember { mutableStateOf(s.filter ?: l.category) }
        DuckSheet("📚 Catalogue — ${mastered.size} / $total maîtrisées", { catalog = false }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in LessonCategory.entries) {
                    val n = Lessons.all.count { it.category == k }
                    val m = Lessons.all.count { it.category == k && it.id in mastered }
                    DuckChip("${k.label} $m/$n", cat == k, { cat = k }, Modifier.weight(1f), accent = c.blue)
                }
            }
            SectionTitle("${cat.emoji} ${cat.label.uppercase()} — toutes les leçons sont en accès libre")
            for (item in Lessons.all.filter { it.category == cat }) {
                val current = item.id == s.lessonId
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (current) c.surfaceHi else c.surface)
                        .clickable { vm.open(item.id); catalog = false }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        AutoText(if (item.id in mastered) "✅" else "${item.id}", color = c.gold, maxSize = 14.sp, weight = FontWeight.Bold, align = TextAlign.Center)
                    }
                    androidx.compose.foundation.layout.Column(Modifier.padding(start = 8.dp).weight(1f)) {
                        AutoText(item.title, maxSize = 15.sp, weight = FontWeight.SemiBold, maxLines = 2)
                        AutoText(item.summary, color = c.muted, maxSize = 12.sp, maxLines = 3)
                    }
                }
            }
        }
    }
}

fun moveLabel(fullmove: Int, white: Boolean, san: String) = if (white) "$fullmove. $san" else "$fullmove… $san"
