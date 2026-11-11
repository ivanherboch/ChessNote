package com.ivanherboch.chessduck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.sp

/**
 * Texte qui réduit automatiquement sa taille pour tenir dans la place disponible :
 * jamais de « … » ni de texte coupé.
 */
@Composable
fun AutoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalDuck.current.text,
    maxSize: TextUnit = 14.sp,
    minSize: TextUnit = 7.sp,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontWeight = weight, textAlign = align, lineHeight = 1.25.em, fontFamily = LocalDuckFont.current),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}

/** Bouton compact à libellé auto-ajusté. */
@Composable
fun DuckButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    filled: Boolean = false,
    height: Dp = 40.dp,
    maxSize: TextUnit = 13.sp,
) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val tint = accent ?: c.text
    val bg = when {
        filled && enabled -> tint
        filled -> c.surfaceHi
        else -> c.surface
    }
    val fg = when {
        !enabled -> c.muted.copy(alpha = 0.55f)
        filled -> c.onAccent
        else -> tint
    }
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(if (!filled && accent != null && enabled) Modifier.border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = enabled) { fb?.key(); onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AutoText(label, color = fg, maxSize = maxSize, weight = FontWeight.SemiBold, align = TextAlign.Center)
    }
}

/** Pastille de filtre sélectionnable. */
@Composable
fun DuckChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color = LocalDuck.current.gold) {
    val c = LocalDuck.current
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) accent else c.surface)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AutoText(label, color = if (selected) c.onAccent else c.text, maxSize = 12.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
    }
}

/** Rangée de boutons de largeur égale. */
@Composable
fun EqualRow(modifier: Modifier = Modifier, gap: Dp = 6.dp, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically, content = content)
}

enum class Tone { INFO, GOOD, BAD, NEUTRAL, GOLD }

/** Bandeau pédagogique affiché sous l'échiquier (jamais par-dessus la grille). */
@Composable
fun FeedbackCard(text: String, tone: Tone, modifier: Modifier = Modifier, maxSize: TextUnit = 14.sp, title: String? = null) {
    val c = LocalDuck.current
    val accent = when (tone) {
        Tone.INFO -> c.blue; Tone.GOOD -> c.green; Tone.BAD -> c.red; Tone.GOLD -> c.gold; Tone.NEUTRAL -> c.outline
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .drawBehind { drawRect(accent, size = Size(5.dp.toPx(), size.height)) }
            .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (title != null) AutoText(title, color = accent, maxSize = 13.sp, weight = FontWeight.Bold)
        AutoText(text, maxSize = maxSize, maxLines = 14, modifier = Modifier.weight(1f, fill = false))
    }
}

/**
 * Tiroir coulissant : se ferme par glissement vers le bas, appui sur l'arrière-plan ou bouton ✕.
 * Respecte la barre de navigation Android.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuckSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDuck.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = c.bg, contentColor = c.text) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxH).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AutoText(title, maxSize = 19.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(c.surface).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) { AutoText("✕", maxSize = 16.sp, weight = FontWeight.Bold) }
            }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), content = content)
        }
    }
}

/**
 * Gabarit « Zéro Scroll » d'un module : bandeau haut, échiquier carré adaptatif, zone du pouce.
 * L'échiquier prend toute la largeur tant que la zone basse conserve [bottomMin].
 */
@Composable
fun ModuleLayout(
    bottomMin: Dp,
    top: @Composable ColumnScope.() -> Unit,
    board: @Composable () -> Unit,
    underBoard: @Composable () -> Unit = {},
    bottom: @Composable ColumnScope.() -> Unit,
) {
    Layout(
        content = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), content = top)
            Box(contentAlignment = Alignment.Center) { board() }
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), content = bottom)
            // Zone collée sous l'échiquier, exactement à la largeur du plateau (vide par défaut).
            Box { underBoard() }
        }
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val topP = measurables[0].measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = h))
        val underH = measurables[3].minIntrinsicHeight(w)
        val side = minOf(w, h - topP.height - underH - bottomMin.roundToPx()).coerceAtLeast(0)
        val boardP = measurables[1].measure(Constraints.fixed(side, side))
        val underP = measurables[3].measure(Constraints(minWidth = side, maxWidth = side, minHeight = 0, maxHeight = underH))
        val rest = (h - topP.height - side - underP.height).coerceAtLeast(0)
        val bottomP = measurables[2].measure(Constraints.fixed(w, rest))
        layout(w, h) {
            topP.place(0, 0)
            boardP.place((w - side) / 2, topP.height)
            underP.place((w - side) / 2, topP.height + side)
            bottomP.place(0, topP.height + side + underP.height)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    AutoText(text, color = LocalDuck.current.muted, maxSize = 12.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

val CardPad = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
