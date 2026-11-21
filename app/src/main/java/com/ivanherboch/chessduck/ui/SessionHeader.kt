package com.ivanherboch.chessduck.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.ui.icons.SwapVert

/**
 * En-tête commun des séances (Revue de partie, Vision des cases, séances de l'Académie) : retour à gauche, titre
 * sur deux lignes au plus (jamais tronqué : il passe à la ligne), retournement de l'échiquier et actions à droite.
 */
@Composable
fun SessionHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String = "Revenir à la leçon",
    label: String? = null,
    subtitle: String? = null,
    onFlip: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        DuckIconButton(Icons.AutoMirrored.Rounded.ArrowBack, backLabel, onBack)
        Column(Modifier.padding(start = 4.dp).weight(1f).semantics(mergeDescendants = true) { heading() }) {
            if (label != null) Text(label, color = c.muted, style = MaterialTheme.typography.labelMedium)
            Text(title, color = c.text, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, color = c.muted, style = MaterialTheme.typography.labelMedium)
        }
        if (onFlip != null) DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", onFlip)
        actions()
    }
}
