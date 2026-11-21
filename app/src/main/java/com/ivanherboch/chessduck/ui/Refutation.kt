package com.ivanherboch.chessduck.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.chess.Position
import com.ivanherboch.chessduck.ui.icons.Pause

/**
 * Affichage temporaire d'un mauvais coup puis de sa réfutation : [position] montrée, [last] surligné et, une fois la
 * réponse adverse connue, [arrow] (dessinée par l'écran avec la couleur thématique d'erreur `LocalDuck.current.red`).
 * Commun aux Problèmes et à l'Académie.
 */
@Immutable
data class Refutation(val position: Position, val last: Move, val arrow: Move? = null)

/** Durée d'affichage de la réfutation avant l'annulation automatique. */
const val REFUTE_HOLD = 4000L

/** Flèche rouge de la réfutation, aux couleurs de la palette active (daltonisme compris). */
@Composable
fun refuteArrow(r: Refutation?): Arrow? = r?.arrow?.let { Arrow(it.from, it.to, LocalDuck.current.red) }

/** Compte à rebours visuel avant l'annulation automatique d'une réfutation ; le bouton pause le suspend. */
@Composable
fun RefuteCountdown(until: Long, hold: Long, onHold: () -> Unit) {
    val c = LocalDuck.current
    val progress = remember(until) { Animatable(((until - System.currentTimeMillis()).toFloat() / hold).coerceIn(0f, 1f)) }
    LaunchedEffect(until) {
        val left = (until - System.currentTimeMillis()).coerceAtLeast(0)
        progress.animateTo(0f, tween(left.toInt(), easing = LinearEasing))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { progress.value }, color = c.redRole.fill, trackColor = c.surfaceHi,
            modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                .semantics { contentDescription = "Annulation automatique du coup imminente" },
        )
        DuckIconButton(Icons.Rounded.Pause, "Garder la position affichée", onHold, Modifier.padding(start = 8.dp))
    }
}
