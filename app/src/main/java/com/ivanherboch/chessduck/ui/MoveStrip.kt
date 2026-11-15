package com.ivanherboch.chessduck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.chess.Line

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
