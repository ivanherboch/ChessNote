package com.ivanherboch.chessduck.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import com.ivanherboch.chessduck.ui.icons.Lightbulb
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

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
    val shape = RoundedCornerShape(DuckRadius.M)
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
@Composable
fun DuckIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    filled: Boolean = false,
    selected: Boolean? = null,
    badge: String? = null,
) = DuckIconButtonImpl(rememberVectorPainter(icon), contentDescription, onClick, modifier, enabled, accent, filled, selected, badge)

/** Variante pour une icône dessinée (ressource vectorielle de l'application). */
@Composable
fun DuckIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    filled: Boolean = false,
    selected: Boolean? = null,
    badge: String? = null,
) = DuckIconButtonImpl(icon, contentDescription, onClick, modifier, enabled, accent, filled, selected, badge)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuckIconButtonImpl(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    accent: Color?,
    filled: Boolean,
    selected: Boolean?,
    badge: String?,
) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    val r = c.role(accent)
    val on = selected == true || filled
    // Désactivé : fond éteint et icône très atténuée (jamais confondu avec un bouton actif sans fond).
    // Interrupteur éteint (selected == false) : surface neutre ; l'accent n'apparaît qu'une fois activé.
    val bg = when {
        !enabled -> c.surfaceSubtle
        on -> r.fill.takeIf { accent != null } ?: c.text
        selected == false -> c.surfaceHi
        accent != null -> r.fill.copy(alpha = if (c.dark) 0.16f else 0.14f)
        else -> c.surfaceHi
    }
    val fg = when {
        !enabled -> c.muted.copy(alpha = 0.45f)
        on -> if (accent != null) r.onFill else c.bg
        selected == false -> c.text
        accent != null -> r.text
        else -> c.text
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
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(DuckRadius.M)).background(bg), contentAlignment = Alignment.Center) {
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
        shape = RoundedCornerShape(50),
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

/** Couleurs des champs de saisie ChessDuck (bordure et curseur dorés, libellés atténués). */
@Composable
fun duckTextFieldColors(): TextFieldColors {
    val c = LocalDuck.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.text, unfocusedTextColor = c.text, errorTextColor = c.text,
        focusedBorderColor = c.goldText, unfocusedBorderColor = c.outlineStrong,
        cursorColor = c.goldText, focusedLabelColor = c.goldText, unfocusedLabelColor = c.muted,
        focusedLeadingIconColor = c.muted, unfocusedLeadingIconColor = c.muted,
        focusedSupportingTextColor = c.muted, unfocusedSupportingTextColor = c.muted,
    )
}

/** Champ de saisie M3 sur une ligne, au style ChessDuck (recherche, pseudo…). */
@Composable
fun DuckTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier,
        colors = duckTextFieldColors(),
    )
}

/**
 * Boutons segmentés M3 au style ChessDuck (segment actif doré) : un choix unique parmi [options].
 * [icons] remplace la coche par défaut par une icône propre à chaque segment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuckSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
    /** Coche du segment choisi (sans icônes) ; sans elle, le choix reste signalé par la couleur et pour TalkBack. */
    checkmark: Boolean = true,
) {
    val c = LocalDuck.current
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, label ->
            val icon = icons?.getOrNull(i)
            SegmentedButton(
                selected = selectedIndex == i, onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = if (icon != null) {
                    { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else if (checkmark) {
                    { SegmentedButtonDefaults.Icon(selectedIndex == i) }
                } else {
                    {}
                },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = c.goldRole.fill, activeContentColor = c.goldRole.onFill, activeBorderColor = c.outlineStrong,
                    inactiveContainerColor = c.surface, inactiveContentColor = c.text, inactiveBorderColor = c.outlineStrong,
                ),
            ) {
                // Deux lignes au besoin (grandes polices) : un libellé comme « Devinez le coup » n'est jamais coupé.
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
            }
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

/** Petite pastille d'information (ex. orientation de l'échiquier, série en cours). */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, accent: Color? = null, description: String? = null) {
    val c = LocalDuck.current
    val r = c.role(accent)
    Row(
        modifier.clip(RoundedCornerShape(DuckRadius.L)).background(if (accent == null) c.surfaceHi else r.fill.copy(alpha = if (c.dark) 0.18f else 0.14f))
            .semantics(mergeDescendants = true) { if (description != null) contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (accent == null) c.text else r.text, modifier = Modifier.padding(end = 4.dp).size(16.dp))
        Text(text, color = if (accent == null) c.text else r.text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Libellé du bouton Indice selon le nombre d'indices déjà révélés (0, 1 ou 2). */
fun hintLabel(stage: Int) = if (stage >= 2) "Indice utilisé" else "Indice ${stage + 1}/2"

/** Bouton Indice commun à l'Académie, aux Problèmes et au Jeu Libre : désactivé une fois les deux indices révélés. */
@Composable
fun HintButton(stage: Int, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    DuckButton(hintLabel(stage), onClick, modifier, enabled = enabled && stage < 2, accent = LocalDuck.current.green, icon = Icons.Rounded.Lightbulb)

/** Variante compacte du bouton Indice (icône et pastille « 1/2 ») pour une barre d'outils. */
@Composable
fun HintIconButton(stage: Int, onClick: () -> Unit, enabled: Boolean = true) =
    DuckIconButton(
        Icons.Rounded.Lightbulb, hintLabel(stage), onClick, enabled = enabled && stage < 2, accent = LocalDuck.current.green,
        badge = if (stage > 0) "$stage/2" else null,
    )
