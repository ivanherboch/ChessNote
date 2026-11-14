package com.ivanherboch.chessduck.feature

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import com.ivanherboch.chessduck.ui.icons.ChildCare
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivanherboch.chessduck.ChessDuckApp
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckSegmentedRow
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.DuckTextField
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.SectionTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ligne de réglage M3 : titre, description et interrupteur. */
@Composable
internal fun SwitchRow(title: String, description: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = LocalDuck.current
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = description?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = {
            Switch(
                checked = checked, onCheckedChange = null, enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.goldRole.onFill, checkedTrackColor = c.goldRole.fill,
                    uncheckedThumbColor = c.outlineStrong, uncheckedTrackColor = c.surfaceHi, uncheckedBorderColor = c.outlineStrong,
                ),
            )
        },
        colors = ListItemDefaults.colors(containerColor = c.surface, headlineColor = c.text, supportingColor = c.muted),
        // Toute la ligne bascule le réglage (cible large, annoncée comme un interrupteur par TalkBack).
        modifier = Modifier.padding(vertical = 2.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

/**
 * Champ de pseudo : libellé flottant, compteur, validation par la touche « OK » du clavier.
 * L'écriture DataStore est différée de 300 ms pendant la frappe (une transaction par pause et non par lettre),
 * puis forcée à la validation, à la perte du focus et à la fermeture du tiroir : aucune saisie n'est perdue.
 */
@Composable
internal fun PseudoField(label: String, initial: String, fallback: String, onSave: (String) -> Unit) {
    val focus = LocalFocusManager.current
    var value by remember { mutableStateOf(initial) }
    var dirty by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val save by rememberUpdatedState(onSave)
    fun flush() {
        if (!dirty) return
        dirty = false
        save(value.trim().ifEmpty { fallback })
    }
    // Changement venu d'ailleurs (autre écran, restauration) : appliqué seulement hors saisie.
    LaunchedEffect(initial) {
        if (!focused && !dirty && initial != value.trim().ifEmpty { fallback }) value = initial
    }
    LaunchedEffect(value, dirty) {
        if (dirty) { delay(300); flush() }
    }
    DisposableEffect(Unit) { onDispose { flush() } }
    DuckTextField(
        value = value,
        onValueChange = { v -> value = v.take(16); dirty = true },
        label = label,
        supportingText = { Text("${value.length}/16", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { flush(); focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { st ->
            val was = focused
            focused = st.isFocused
            if (was && !st.isFocused) flush()
        },
    )
}

/** Paramètres globaux (persistés via DataStore), collectés uniquement pendant que le tiroir est ouvert. */
@Composable
fun SettingsSheet(app: ChessDuckApp, onClose: () -> Unit) {
    val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
    val st = settings ?: return
    val c = LocalDuck.current
    // Les réglages sont écrits dans la portée de l'application : fermer le tiroir n'annule jamais l'écriture.
    val scope = app.scope
    DuckSheet("Paramètres", onClose) {
        SectionTitle("Apparence")
        DuckSegmentedRow(listOf("Système", "Jour", "Nuit"), st.themeMode, { i -> scope.launch { app.prefs.setThemeMode(i) } }, Modifier.fillMaxWidth())
        val dynamicAvailable = Build.VERSION.SDK_INT >= 31
        SwitchRow(
            "Couleurs dynamiques (Material You)",
            if (dynamicAvailable) "Reprend les couleurs de votre fond d'écran. Désactivé : charte ChessDuck." else "Disponible à partir d'Android 12.",
            st.dynamicColor, enabled = dynamicAvailable,
        ) { v -> scope.launch { app.prefs.setDynamicColor(v) } }

        SectionTitle("Échiquier et parties")
        SwitchRow("Afficher les coordonnées", "Lettres a–h et chiffres 1–8 sur l'échiquier. Masquez-les pour vous entraîner à la notation.", st.coords) { v ->
            scope.launch { app.prefs.setCoords(v) }
        }
        SwitchRow("Confirmer l'abandon et la nouvelle partie", "Demande une confirmation quand une partie est en cours.", st.confirmActions) { v ->
            scope.launch { app.prefs.setConfirmActions(v) }
        }

        SectionTitle("Sons et vibrations")
        SwitchRow("Bruit des pièces", "Petit bruit de bois quand une pièce se pose.", st.sound) { v -> scope.launch { app.prefs.setSound(v) } }
        SwitchRow("Vibrations", "Retour haptique au toucher, aux erreurs et aux réussites.", st.haptics) { v -> scope.launch { app.prefs.setHaptics(v) } }

        SectionTitle("Joueurs")
        PseudoField("Pseudo du joueur", st.pseudo, "Joueur") { v -> scope.launch { app.prefs.setPseudo(v) } }
        PseudoField("Pseudo du joueur 2 (mode 2 joueurs)", st.pseudo2, "Joueur 2") { v -> scope.launch { app.prefs.setPseudo2(v) } }
        Text(
            "Classement actuel : ${st.elo} ELO (formule FIDE, K = 32). Les parties à 2 joueurs ne comptent pas.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
        )

        SectionTitle("Mode enfant")
        DuckButton(
            "Activer le mode enfant", { onClose(); scope.launch { app.prefs.setKidMode(true) } }, Modifier.fillMaxWidth(),
            accent = c.green, filled = true, icon = Icons.Rounded.ChildCare,
        )
        Text(
            "Un écran joyeux et coloré pour jouer à deux avec un enfant : grand échiquier, cavalier en forme de girafe, " +
                "animations qui montrent comment chaque pièce bouge, sans ordinateur ni historique.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
        )

        SectionTitle("À propos")
        Text(
            "ChessDuck fonctionne 100 % hors-ligne. Moteur : Stockfish 11 (GPLv3). Problèmes : base ouverte Lichess (CC0). " +
                "Pièces : série classique de Cburnett et pièce féerique Girafe, Wikimedia Commons (CC BY-SA 3.0). " +
                "Icônes : Material Icons (Apache 2.0). Police du mode enfant : Fredoka (OFL).",
            color = c.muted, style = MaterialTheme.typography.bodySmall,
        )
        val context = LocalContext.current
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
        Text(
            "© Ivan Herboch, 2026 · v$version", color = c.muted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        )
    }
}
