package com.ivanherboch.chessduck.ui

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onPlaced
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import com.ivanherboch.chessduck.ui.icons.ErrorOutline
import com.ivanherboch.chessduck.ui.icons.HourglassTop
import androidx.compose.material.icons.rounded.Info
import com.ivanherboch.chessduck.ui.icons.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Carte pédagogique affichée sous l'échiquier (jamais par-dessus la grille).
 * Elle ne défile jamais : le texte réduit d'abord sa taille (de bodyMedium jusqu'à 12 sp) pour tenir dans
 * la place disponible, et [ModuleLayout] réduit légèrement l'échiquier si nécessaire (la carte annonce sa
 * hauteur utile à 12 sp via ses dimensions intrinsèques). En dernier recours (très petits écrans, grande
 * police système), le texte se termine par « … » et un lien « Lire la suite » ouvre le message complet.
 * Les changements sont annoncés par TalkBack (région « polie »).
 */
@Composable
fun FeedbackCard(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = LocalDuck.current
    val role = toneRole(tone)
    val icon = when (tone) {
        Tone.INFO -> Icons.Rounded.Info
        Tone.GOOD -> Icons.Rounded.CheckCircle
        Tone.BAD -> Icons.Rounded.ErrorOutline
        Tone.GOLD -> Icons.Rounded.Lightbulb
        Tone.NEUTRAL -> Icons.Rounded.HourglassTop
    }
    val font = LocalDuckFont.current
    val titleStyle = MaterialTheme.typography.titleSmall.copy(color = role.text)
    val base = MaterialTheme.typography.bodyMedium
    val maxSize = if (base.fontSize.isSp && base.fontSize.value > FEEDBACK_MIN.value) base.fontSize else FEEDBACK_MIN
    val bodyStyle = base.copy(color = c.text, lineHeight = 1.3.em, fontFamily = base.fontFamily ?: font)
    var overflow by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val hasAction = action != null
    // Résultat de la mesure (débordement à 12 sp), noté sans écrire d'état pendant la passe de mesure ;
    // il est publié après le placement (onPlaced), ce qui ne déclenche aucune boucle de mise en page.
    val measuredOverflow = remember { BooleanArray(1) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Layout(
            modifier = Modifier.onPlaced { if (measuredOverflow[0] != overflow) overflow = measuredOverflow[0] },
            content = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface)
                        .drawBehind { drawRect(role.text, size = Size(4.dp.toPx(), size.height)) }
                        .then(if (overflow) Modifier.clickable(onClickLabel = "Lire le message complet") { full = true } else Modifier)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = listOfNotNull(title, text).joinToString(". ") }
                        .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.Top) {
                        Icon(icon, contentDescription = null, tint = role.text, modifier = Modifier.padding(top = 1.dp, end = 8.dp).size(18.dp))
                        Column(Modifier.weight(1f)) {
                            if (title != null) Text(title, style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 2.dp))
                            BasicText(
                                text,
                                style = bodyStyle,
                                overflow = TextOverflow.Ellipsis,
                                autoSize = TextAutoSize.StepBased(minFontSize = FEEDBACK_MIN, maxFontSize = maxSize, stepSize = 0.5.sp),
                            )
                        }
                    }
                    if (overflow) {
                        Text(
                            "Lire la suite",
                            color = role.text, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                        )
                    }
                    if (action != null) Box(Modifier.padding(top = 6.dp)) { action() }
                }
            },
            measurePolicy = object : MeasurePolicy {
                override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                    // Lien « Lire la suite » seulement si le texte ne tient pas, même à 12 sp.
                    val over = constraints.hasBoundedHeight && needed(constraints.maxWidth) > constraints.maxHeight + 2
                    measuredOverflow[0] = over
                    val p = measurables[0].measure(constraints)
                    return layout(p.width, p.height) { p.place(0, 0) }
                }

                /** Hauteur nécessaire pour tout afficher à la taille minimale (12 sp), sans défilement. */
                private fun IntrinsicMeasureScope.needed(width: Int): Int {
                    val inner = (width - (14 + 12 + 18 + 8).dp.roundToPx()).coerceAtLeast(1)
                    val cs = Constraints(maxWidth = inner)
                    var hgt = (8 + 8).dp.roundToPx()
                    if (title != null) hgt += measurer.measure(title, titleStyle, maxLines = 2, constraints = cs, density = this, layoutDirection = layoutDirection).size.height + 2.dp.roundToPx()
                    hgt += measurer.measure(text, bodyStyle.copy(fontSize = FEEDBACK_MIN), constraints = cs, density = this, layoutDirection = layoutDirection).size.height
                    if (hasAction) hgt += (6 + 48).dp.roundToPx()
                    return hgt
                }

                override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = needed(width)
                override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = needed(width)
                override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables[0].minIntrinsicWidth(height)
                override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables[0].maxIntrinsicWidth(height)
            },
        )
    }
    if (full) {
        DuckSheet(title ?: "Message", onClose = { full = false }) {
            Text(text, color = c.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Taille plancher du texte des cartes sous l'échiquier. */
private val FEEDBACK_MIN = 12.sp

/** Une ligne de [AlignedLabelRows] : libellé, description TalkBack de toute la ligne, éléments qui suivent. */
class LabeledRow(val label: String, val description: String, val trailing: @Composable () -> Unit)

/**
 * Lignes « libellé + éléments » (ex. joueur et compteurs d'annotations), cohérentes entre elles :
 * - si chaque ligne tient en entier, tout est sur une ligne et les éléments commencent tous à la même position
 *   (colonne alignée à gauche, juste après le libellé le plus long) ;
 * - sinon, pour toutes les lignes, le libellé est seul sur sa ligne (en entier) et les éléments passent dessous,
 *   alignés à gauche. Le libellé n'est jamais tronqué : sa largeur naturelle est mesurée exactement.
 */
@Composable
fun AlignedLabelRows(
    rows: List<LabeledRow>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 10.dp,
    rowGap: Dp = 6.dp,
) {
    val measurer = rememberTextMeasurer()
    Layout(
        content = {
            for (r in rows) {
                Text(r.label, color = color, style = style, modifier = Modifier.semantics { contentDescription = r.description })
                Box(Modifier.clearAndSetSemantics { }) { r.trailing() }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val n = rows.size
        val gapPx = gap.roundToPx()
        val rowGapPx = rowGap.roundToPx()
        val under = 2.dp.roundToPx()
        val trailing = List(n) { measurables[2 * it + 1].measure(Constraints(maxWidth = w)) }
        val natural = List(n) {
            measurer.measure(rows[it].label, style, maxLines = 1, softWrap = false, density = this, layoutDirection = layoutDirection).size.width
        }
        val column = (natural.maxOrNull() ?: 0) + gapPx
        val oneLine = trailing.all { column + it.width <= w }
        val labels = List(n) {
            measurables[2 * it].measure(if (oneLine) Constraints(maxWidth = natural[it].coerceAtMost(w)) else Constraints(maxWidth = w))
        }
        val heights = List(n) { if (oneLine) maxOf(labels[it].height, trailing[it].height) else labels[it].height + under + trailing[it].height }
        val total = heights.sum() + rowGapPx * (n - 1).coerceAtLeast(0)
        layout(w, total) {
            var y = 0
            for (i in 0 until n) {
                if (oneLine) {
                    labels[i].place(0, y + (heights[i] - labels[i].height) / 2)
                    trailing[i].place(column, y + (heights[i] - trailing[i].height) / 2)
                } else {
                    labels[i].place(0, y)
                    trailing[i].place(0, y + labels[i].height + under)
                }
                y += heights[i] + rowGapPx
            }
        }
    }
}

/**
 * Affiche [content] en entier ou pas du tout : s'il ne tient pas dans la hauteur disponible, il est masqué
 * plutôt que coupé (texte d'aide facultatif). Sa hauteur intrinsèque reste annoncée, si bien que
 * [ModuleLayout] peut libérer la place nécessaire.
 */
@Composable
fun WholeOrNothing(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val p = measurables.firstOrNull()?.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            ?: return@Layout layout(0, 0) {}
        if (p.height <= constraints.maxHeight) layout(p.width, p.height) { p.place(0, 0) } else layout(p.width, 0) {}
    }
}

/**
 * Gabarit d'un module : bandeau haut, [overBoard], échiquier carré adaptatif, [underBoard], zone du pouce.
 * En portrait, l'échiquier prend toute la largeur tant que la zone basse conserve [bottomMin].
 * En paysage (tablette), l'échiquier occupe la hauteur à gauche et les commandes forment une seconde colonne.
 */
@Composable
fun ModuleLayout(
    bottomMin: Dp,
    top: @Composable ColumnScope.() -> Unit,
    board: @Composable () -> Unit,
    underBoard: @Composable () -> Unit = {},
    overBoard: @Composable () -> Unit = {},
    adaptive: Boolean = true,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    // Place supplémentaire prise à l'échiquier pour que les textes du bas tiennent sans défilement.
    // Elle ne fait que croître pendant la vie de l'écran (pas de « respiration » de la grille à chaque coup).
    val twoPane = LocalIsTwoPane.current
    var reserve by remember { mutableIntStateOf(0) }
    val animReserve by animateIntAsState(reserve, tween(260), label = "reserve")
    // Besoin calculé pendant la mesure (sans écriture d'état), publié après le placement.
    val wanted = remember { IntArray(1) }
    Layout(
        modifier = Modifier.onPlaced { if (wanted[0] > reserve) reserve = wanted[0] },
        content = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), content = top)
            Box(contentAlignment = Alignment.Center) { board() }
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), content = bottom)
            Box { underBoard() }
            Box { overBoard() }
        }
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (twoPane) {
            // Paysage : échiquier à gauche sur toute la hauteur, panneau de commandes à droite.
            val gap = 12.dp.roundToPx()
            val probe = minOf(h, (w * 0.6f).toInt())
            val underH0 = measurables[3].minIntrinsicHeight(probe)
            val overH0 = measurables[4].minIntrinsicHeight(probe)
            val side = minOf(h - underH0 - overH0, w - 320.dp.roundToPx(), (w * 0.62f).toInt()).coerceAtLeast(0)
            val boardP = measurables[1].measure(Constraints.fixed(side, side))
            val underP = measurables[3].measure(Constraints(minWidth = side, maxWidth = side, minHeight = 0, maxHeight = underH0))
            val overP = measurables[4].measure(Constraints(minWidth = side, maxWidth = side, minHeight = 0, maxHeight = overH0))
            val panelW = (w - side - gap).coerceAtLeast(0)
            val topP = measurables[0].measure(Constraints(minWidth = panelW, maxWidth = panelW, minHeight = 0, maxHeight = h))
            val bottomP = measurables[2].measure(Constraints.fixed(panelW, (h - topP.height).coerceAtLeast(0)))
            val y0 = ((h - side - underP.height - overP.height) / 2).coerceAtLeast(0)
            return@Layout layout(w, h) {
                overP.place(0, y0)
                boardP.place(0, y0 + overP.height)
                underP.place(0, y0 + overP.height + side)
                topP.place(side + gap, 0)
                bottomP.place(side + gap, topP.height)
            }
        }
        val topP = measurables[0].measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = h))
        val underH = measurables[3].minIntrinsicHeight(w)
        val overH = measurables[4].minIntrinsicHeight(w)
        val baseSide = minOf(w, h - topP.height - underH - overH - bottomMin.roundToPx()).coerceAtLeast(0)
        if (adaptive) {
            val avail = h - topP.height - underH - overH - baseSide
            val needed = runCatching { measurables[2].minIntrinsicHeight(w) }.getOrDefault(0)
            val want = (needed - avail).coerceIn(0, (baseSide * 0.22f).toInt())
            if (want > wanted[0]) wanted[0] = want
        }
        val side = (baseSide - if (adaptive) animReserve else 0).coerceAtLeast(0)
        val boardP = measurables[1].measure(Constraints.fixed(side, side))
        val underP = measurables[3].measure(Constraints(minWidth = side, maxWidth = side, minHeight = 0, maxHeight = underH))
        val overP = measurables[4].measure(Constraints(minWidth = side, maxWidth = side, minHeight = 0, maxHeight = overH))
        val rest = (h - topP.height - side - underP.height - overP.height).coerceAtLeast(0)
        val bottomP = measurables[2].measure(Constraints.fixed(w, rest))
        layout(w, h) {
            topP.place(0, 0)
            overP.place((w - side) / 2, topP.height)
            boardP.place((w - side) / 2, topP.height + overP.height)
            underP.place((w - side) / 2, topP.height + overP.height + side)
            bottomP.place(0, topP.height + overP.height + side + underP.height)
        }
    }
}
