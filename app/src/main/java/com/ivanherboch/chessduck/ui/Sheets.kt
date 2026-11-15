package com.ivanherboch.chessduck.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
        modifier.fillMaxWidth().clip(RoundedCornerShape(DuckRadius.L)).background(c.surface).padding(16.dp),
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
@Composable
fun DuckSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    DuckSheetFrame(title, onClose) { bottom ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = bottom), content = content)
    }

/**
 * Variante paresseuse de [DuckSheet] pour les longues listes (catalogues, historique) : seules les lignes
 * visibles sont composées ; [content] décrit les éléments avec des clés stables.
 */
@Composable
fun DuckLazySheet(title: String, onClose: () -> Unit, content: LazyListScope.() -> Unit) =
    DuckSheetFrame(title, onClose) { bottom ->
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = bottom), content = content)
    }

/**
 * Cadre commun des feuilles : hauteur max 88 % de l'écran, en-tête titre + fermer, marges et insets.
 * [content] reçoit la marge basse à appliquer et occupe l'espace restant (sans le forcer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuckSheetFrame(title: String, onClose: () -> Unit, content: @Composable (Dp) -> Unit) {
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
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { content(16.dp) }
        }
    }
}
