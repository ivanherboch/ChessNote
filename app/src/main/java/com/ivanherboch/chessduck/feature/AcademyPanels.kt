package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.SessionHeader
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.icons.StarOutline

/** « 2 étoiles sur 3 ». */
internal fun starsText(n: Int) = "$n étoile${if (n > 1) "s" else ""} sur 3"

/** Trois étoiles, pleines selon le résultat du Défi (description lue par TalkBack sur le parent). */
@Composable
internal fun StarRow(stars: Int, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val c = LocalDuck.current
    Row(modifier) {
        for (i in 1..3) Icon(
            if (i <= stars) Icons.Rounded.Star else Icons.Rounded.StarOutline, contentDescription = null,
            tint = if (i <= stars) c.goldText else c.muted, modifier = Modifier.size(size),
        )
    }
}

/**
 * En-tête d'une séance (entraînement, validation, révision, finale, question) : il remplace le sélecteur de leçon et
 * les modes Guidé/Défi, pour qu'on sache toujours ce que montre l'échiquier. La flèche de retour revient à la leçon
 * (c'est le seul moyen de quitter : plus de bouton « Arrêter » en double), le retournement de l'échiquier reste offert.
 */
@Composable
internal fun TrainingHeader(t: SessionTitle, onClose: () -> Unit, onFlip: () -> Unit, backLabel: String = "Revenir à la leçon") =
    SessionHeader(t.title, onClose, backLabel = backLabel, label = t.label, onFlip = onFlip)
