package com.ivanherboch.chessduck.ui

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivanherboch.chessduck.chess.Line

/** Taille minimale d'un texte dans l'application (labelSmall). */
val MIN_TEXT = 11.sp

/**
 * Texte qui s'adapte à la place disponible sans jamais descendre sous 11 sp :
 * au-delà, il passe à la ligne (jusqu'à [maxLines]) puis se termine par « … ».
 * Respecte l'échelle de police du système et utilise des chiffres tabulaires.
 */
@Composable
fun AutoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalDuck.current.text,
    maxSize: TextUnit = 14.sp,
    minSize: TextUnit = 12.sp,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    val lo = if (minSize.value < MIN_TEXT.value) MIN_TEXT else minSize
    val hi = if (maxSize.value < lo.value) lo else maxSize
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontWeight = weight, textAlign = align, lineHeight = 1.25.em,
            fontFamily = LocalDuckFont.current, fontFeatureSettings = TNUM,
        ),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = lo, maxFontSize = hi, stepSize = 0.5.sp),
    )
}

/** Variantes de bouton du design system. */
enum class ButtonVariant { PRIMARY, TONAL, OUTLINED, GHOST }

private data class ButtonColors(val bg: Color, val fg: Color, val border: Color?)

@Composable
private fun buttonColors(variant: ButtonVariant, accent: Color?, enabled: Boolean): ButtonColors {
    val c = LocalDuck.current
    val r = c.role(accent)
    if (!enabled) {
        // Désactivé : reste lisible (≥ 4,5:1) tout en paraissant inerte.
        return ButtonColors(if (variant == ButtonVariant.GHOST) Color.Transparent else c.surfaceSubtle, c.muted, if (variant == ButtonVariant.OUTLINED) c.outline else null)
    }
    return when (variant) {
        ButtonVariant.PRIMARY -> if (accent == null) ButtonColors(c.text, c.bg, null) else ButtonColors(r.fill, r.onFill, null)
        ButtonVariant.TONAL -> if (accent == null) ButtonColors(c.surfaceHi, c.text, null)
        else ButtonColors(r.fill.copy(alpha = if (c.dark) 0.16f else 0.14f), r.text, null)
        ButtonVariant.OUTLINED -> ButtonColors(Color.Transparent, if (accent == null) c.text else r.text, if (accent == null) c.outlineStrong else r.text)
        ButtonVariant.GHOST -> ButtonColors(Color.Transparent, if (accent == null) c.text else r.text, null)
    }
}

/**
 * Bouton texte (avec icône optionnelle) du design system : hauteur 48 dp par défaut, cible tactile
 * toujours ≥ 48 dp, libellé en labelLarge fixe (13 sp) sur deux lignes au plus.
 * [filled] = variante PRIMARY, [accent] sans [filled] = TONAL, sinon TONAL neutre ; [variant] force le style.
 */
@Composable
fun DuckButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    filled: Boolean = false,
    height: Dp = 48.dp,
    icon: ImageVector? = null,
    variant: ButtonVariant? = null,
    contentDescription: String? = null,
) {
    val fb = LocalFeedback.current
    val v = variant ?: if (filled) ButtonVariant.PRIMARY else ButtonVariant.TONAL
    val col = buttonColors(v, accent, enabled)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = height)
            .clip(shape)
            .background(col.bg)
            .then(if (col.border != null) Modifier.border(BorderStroke(1.dp, col.border), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, indication = ripple(), interactionSource = null) { fb?.key(); onClick() }
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = col.fg, modifier = Modifier.size(20.dp))
            if (label.isNotEmpty()) Spacer(Modifier.width(6.dp))
        }
        if (label.isNotEmpty()) {
            Text(
                label, color = col.fg, style = MaterialTheme.typography.labelLarge, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Bouton-icône 48 × 48 dp avec info-bulle (appui long) et description TalkBack.
 * [selected] l'affiche comme un interrupteur actif ; [badge] ajoute un petit compteur (ex. « 1/2 »).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuckIconButton(
    icon: Any,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    filled: Boolean = false,
    selected: Boolean? = null,
    badge: String? = null,
) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val r = c.role(accent)
    val on = selected == true || filled
    val bg = when {
        !enabled -> Color.Transparent
        on -> r.fill.takeIf { accent != null } ?: c.text
        accent != null -> r.fill.copy(alpha = if (c.dark) 0.16f else 0.14f)
        else -> c.surfaceHi
    }
    val fg = when {
        !enabled -> c.muted
        on -> if (accent != null) r.onFill else c.bg
        accent != null -> r.text
        else -> c.text
    }
    val painter: Painter = when (icon) {
        is ImageVector -> rememberVectorPainter(icon)
        is Painter -> icon
        else -> error("icône non prise en charge")
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier
                .size(48.dp)
                .clickable(enabled = enabled, role = Role.Button, indication = ripple(bounded = false, radius = 24.dp), interactionSource = null) { fb?.key(); onClick() }
                .semantics {
                    this.contentDescription = contentDescription + (badge?.let { " ($it)" } ?: "")
                    if (selected != null) {
                        this.selected = selected
                        stateDescription = if (selected) "activé" else "désactivé"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(painter, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            }
            if (badge != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp).clip(CircleShape).background(r.fill.takeIf { accent != null } ?: c.text)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(badge, color = if (accent != null) r.onFill else c.bg, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Pastille de filtre M3 : 32 dp visibles, cible tactile 48 dp, état annoncé par TalkBack. */
@Composable
fun DuckChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalDuck.current.gold,
    icon: ImageVector? = null,
) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val r = c.role(accent)
    FilterChip(
        selected = selected,
        onClick = { fb?.key(); onClick() },
        label = { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.semantics { stateDescription = if (selected) "sélectionné" else "non sélectionné" },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        shape = RoundedCornerShape(17.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surface, labelColor = c.text, iconColor = c.text,
            selectedContainerColor = r.fill, selectedLabelColor = r.onFill, selectedLeadingIconColor = r.onFill,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = c.outlineStrong, selectedBorderColor = r.fill, borderWidth = 1.dp,
        ),
    )
}

/** Rangée de boutons de largeur égale. */
@Composable
fun EqualRow(modifier: Modifier = Modifier, gap: Dp = 6.dp, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically, content = content)
}

enum class Tone { INFO, GOOD, BAD, NEUTRAL, GOLD }

@Composable
fun toneRole(tone: Tone): AccentRole {
    val c = LocalDuck.current
    return when (tone) {
        Tone.INFO -> c.blueRole
        Tone.GOOD -> c.greenRole
        Tone.BAD -> c.redRole
        Tone.GOLD -> c.goldRole
        Tone.NEUTRAL -> AccentRole(c.muted, c.outlineStrong, c.bg)
    }
}

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
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Layout(
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
                    if (over != overflow) overflow = over
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

/** État vide : icône, message clair et action directe. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val c = LocalDuck.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(36.dp))
        Text(title, color = c.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        Text(message, color = c.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        if (actionLabel != null && onAction != null) DuckButton(actionLabel, onAction, Modifier.fillMaxWidth(), accent = c.gold, filled = true)
        if (secondaryLabel != null && onSecondary != null) DuckButton(secondaryLabel, onSecondary, Modifier.fillMaxWidth().padding(top = 8.dp), variant = ButtonVariant.OUTLINED)
    }
}

/** État de chargement : indicateur circulaire et message. */
@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    val c = LocalDuck.current
    Column(modifier.fillMaxSize().semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = c.goldRole.fill)
        Text(message, color = c.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Boîte de confirmation M3 pour les actions destructives. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Annuler",
) {
    val c = LocalDuck.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = c.redText, style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, color = c.text, style = MaterialTheme.typography.labelLarge) } },
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.text,
    )
}

/**
 * Tiroir coulissant : se ferme par glissement vers le bas, appui sur l'arrière-plan ou bouton Fermer.
 * Respecte la barre de navigation Android et le clavier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuckSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDuck.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenPx = LocalConfiguration.current.screenHeightDp * LocalContext.current.resources.displayMetrics.density
    val maxH = (screenPx * 0.88f / LocalDensity.current.density).dp
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = c.bg, contentColor = c.text) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxH).navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                DuckIconButton(Icons.Rounded.Close, "Fermer", onClose)
            }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), content = content)
        }
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
    var reserve by remember { mutableIntStateOf(0) }
    val animReserve by animateIntAsState(reserve, tween(260), label = "reserve")
    Layout(
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
        if (w > h * 1.15f) {
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
            if (want > reserve) reserve = want
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

@Composable
fun SectionTitle(text: String) {
    Text(
        text, color = LocalDuck.current.muted, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp).semantics { heading() },
    )
}

/**
 * Bande horizontale des coups joués, encadrée des flèches précédent / suivant.
 * Chaque coup est cliquable ([onSelect] reçoit le nombre de demi-coups à afficher) avec une cible de 48 dp.
 */
@Composable
fun MoveStrip(
    line: Line,
    cursor: Int,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    prevEnabled: Boolean = cursor > 0,
    nextEnabled: Boolean = cursor < line.size,
    emptyText: String = "Les coups de la partie s'afficheront ici.",
) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val listState = rememberLazyListState()
    LaunchedEffect(cursor, line.size) { if (line.size > 0) listState.animateScrollToItem((cursor - 1).coerceAtLeast(0)) }
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onPrev != null) DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Coup précédent", onPrev, enabled = prevEnabled)
        LazyRow(
            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.surface).padding(horizontal = 4.dp),
            state = listState, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (line.size == 0) item { Text(emptyText, color = c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp)) }
            items(line.size) { i ->
                val before = line.positions[i]
                val current = i == cursor - 1
                val label = (if (before.whiteToMove) "${before.fullmove}. " else "") + line.sans[i]
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .then(
                            if (onSelect != null) Modifier.clickable(role = Role.Button, onClickLabel = "Aller au coup $label") { fb?.key(); onSelect(i + 1) }
                            else Modifier,
                        )
                        .semantics { selected = current },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(if (current) c.goldRole.fill else Color.Transparent)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            label, color = if (current) c.goldRole.onFill else if (before.whiteToMove) c.text else c.muted,
                            style = if (current) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        if (onNext != null) DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Coup suivant", onNext, enabled = nextEnabled)
    }
}

/** Petite pastille d'information (ex. orientation de l'échiquier, série en cours). */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, accent: Color? = null, description: String? = null) {
    val c = LocalDuck.current
    val r = c.role(accent)
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (accent == null) c.surfaceHi else r.fill.copy(alpha = if (c.dark) 0.18f else 0.14f))
            .semantics(mergeDescendants = true) { if (description != null) contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (accent == null) c.text else r.text, modifier = Modifier.padding(end = 4.dp).size(16.dp))
        Text(text, color = if (accent == null) c.text else r.text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
