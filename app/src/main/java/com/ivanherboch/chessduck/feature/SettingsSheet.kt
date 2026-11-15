package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.ui.icons.Replay
import com.ivanherboch.chessduck.ui.ConfirmDialog
import com.ivanherboch.chessduck.data.DEFAULT_ELO
import com.ivanherboch.chessduck.ui.DuckPalette
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.ui.Narrator
import com.ivanherboch.chessduck.ui.ButtonVariant
import com.ivanherboch.chessduck.ui.DuckButton
import androidx.compose.foundation.layout.fillMaxHeight
import com.ivanherboch.chessduck.ui.BoardTheme
import android.content.Intent
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
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

/**
 * Interrupteur d'une voix : neutre (grisé) tant que le moteur vocal n'a pas répondu, grisé avec une explication
 * si aucune voix française hors-ligne n'est installée.
 */
@Composable
private fun VoiceSwitch(title: String, description: String, checked: Boolean, voice: Boolean?, onChange: (Boolean) -> Unit) =
    SwitchRow(
        title,
        when (voice) {
            false -> "Aucune voix française hors-ligne n'est installée sur ce téléphone."
            null -> "Recherche d'une voix française…"
            true -> description
        },
        checked && voice != false, enabled = voice == true, onChange = onChange,
    )

/**
 * Paramètres globaux (persistés via DataStore), collectés uniquement pendant que le tiroir est ouvert.
 * Les réglages sont écrits dans [scope], la portée de l'application : fermer le tiroir n'annule jamais l'écriture.
 */
@Composable
fun SettingsSheet(prefs: SettingsRepository, narrator: Narrator, scope: CoroutineScope, onClose: () -> Unit) {
    val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = null)
    // Une voix française hors-ligne est-elle installée ? (null tant que le moteur n'a pas répondu)
    val voice by narrator.available.collectAsStateWithLifecycle()
    LaunchedEffect(narrator) { narrator.prepare() }
    val context = LocalContext.current
    val st = settings ?: return
    val c = LocalDuck.current
    var resetElo by remember { mutableStateOf(false) }
    DuckSheet("Paramètres", onClose) {
        // Le mode enfant change toute l'application : c'est le premier choix proposé.
        SectionTitle("Mode enfant")
        DuckButton(
            "Activer le mode enfant", { onClose(); scope.launch { prefs.setKidMode(true) } }, Modifier.fillMaxWidth(),
            accent = c.green, filled = true, icon = Icons.Rounded.ChildCare,
        )
        Text(
            "Un écran joyeux et coloré pour jouer à deux avec un enfant : grand échiquier, cavalier en forme de girafe, " +
                "animations qui montrent comment chaque pièce bouge, sans ordinateur ni historique.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
        )
        VoiceSwitch(
            "Voix en mode enfant", "Lit chaque façon de bouger au moment où l'enfant la voit, une seule fois. Rien n'est lu pendant les parties.",
            st.kidNarration, voice,
        ) { v -> scope.launch { prefs.setKidNarration(v) } }

        SectionTitle("Apparence")
        DuckSegmentedRow(listOf("Système", "Jour", "Nuit"), st.themeMode, { i -> scope.launch { prefs.setThemeMode(i) } }, Modifier.fillMaxWidth())

        SectionTitle("Échiquier et parties")
        BoardThemePicker(BoardTheme.of(st.boardTheme)) { t -> scope.launch { prefs.setBoardTheme(t.ordinal) } }
        SwitchRow("Afficher les coordonnées", "Lettres a–h et chiffres 1–8 sur l'échiquier. Masquez-les pour vous entraîner à la notation.", st.coords) { v ->
            scope.launch { prefs.setCoords(v) }
        }
        SwitchRow("Confirmer l'abandon et la nouvelle partie", "Demande une confirmation quand une partie est en cours.", st.confirmActions) { v ->
            scope.launch { prefs.setConfirmActions(v) }
        }

        SectionTitle("Audio, voix et retours")
        SwitchRow("Bruit des pièces", "Petit bruit de bois quand une pièce se pose.", st.sound) { v -> scope.launch { prefs.setSound(v) } }
        SwitchRow("Vibrations", "Retour haptique au toucher, aux erreurs et aux réussites.", st.haptics) { v -> scope.launch { prefs.setHaptics(v) } }
        VoiceSwitch("Lecture vocale des leçons", "L'Académie lit à voix haute les commentaires du mode Guidé.", st.narration, voice) { v ->
            scope.launch { prefs.setNarration(v) }
        }
        if (voice == false) {
            DuckButton(
                "Installer une voix française",
                { runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                Modifier.fillMaxWidth(), variant = ButtonVariant.OUTLINED,
            )
        }

        SectionTitle("Joueurs")
        PseudoField("Pseudo du joueur", st.pseudo, "Joueur") { v -> scope.launch { prefs.setPseudo(v) } }
        PseudoField("Pseudo du joueur 2 (mode 2 joueurs)", st.pseudo2, "Joueur 2") { v -> scope.launch { prefs.setPseudo2(v) } }
        Text(
            "Classement actuel : ${st.elo} ELO (formule FIDE, K = 32). Les parties à 2 joueurs ne comptent pas.",
            color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
        )
        DuckButton(
            "Réinitialiser mon ELO", { resetElo = true }, Modifier.fillMaxWidth().padding(top = 6.dp),
            enabled = st.elo != DEFAULT_ELO, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.Replay,
        )

        SectionTitle("Palette de couleurs")
        Column(Modifier.selectableGroup()) {
            for (palette in DuckPalette.entries) {
                PaletteOption(palette, selected = st.palette == palette.ordinal) { scope.launch { prefs.setPalette(palette.ordinal) } }
            }
        }

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
    if (resetElo) ConfirmDialog(
        "Réinitialiser votre ELO ?", "Votre classement repartira de $DEFAULT_ELO. L'historique des parties est conservé.",
        "Réinitialiser", { resetElo = false; scope.launch { prefs.setElo(DEFAULT_ELO) } }, { resetElo = false },
    )
}

/**
 * Choix d'une palette : aperçu fidèle (fond, carte, accent principal, réussite, information, erreur et cases de
 * l'échiquier) dans le mode d'affichage courant (Jour ou Nuit), nom et description ; toute la ligne est sélectionnable.
 */
/** Matière de l'échiquier : quatre aperçus de 2 × 2 cases, sélection unique. */
@Composable
private fun BoardThemePicker(current: BoardTheme, onSelect: (BoardTheme) -> Unit) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().selectableGroup().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in BoardTheme.entries) {
            val selected = t == current
            val shape = RoundedCornerShape(DuckRadius.M)
            Column(
                Modifier.weight(1f).clip(shape).background(if (selected) c.surfaceHi else c.surface)
                    .border(if (selected) 2.dp else 1.dp, if (selected) c.goldRole.fill else c.outline, shape)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(t) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.size(44.dp).clip(RoundedCornerShape(DuckRadius.S))) {
                    for (r in 0..1) Row(Modifier.weight(1f)) {
                        for (f in 0..1) Box(Modifier.weight(1f).fillMaxHeight().background(if ((r + f) % 2 == 0) t.light else t.dark))
                    }
                }
                Text(t.label, color = c.text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun PaletteOption(palette: DuckPalette, selected: Boolean, onSelect: () -> Unit) {
    val c = LocalDuck.current
    val p = palette.colors(c.dark)
    val shape = RoundedCornerShape(DuckRadius.L)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
            .background(if (selected) c.surfaceHi else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.goldRole.fill else c.outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mini-écran : fond de la palette, carte, bouton principal et pastilles des couleurs de sens.
        Box(Modifier.size(width = 92.dp, height = 60.dp).clip(RoundedCornerShape(DuckRadius.M)).background(p.bg).border(1.dp, p.outline, RoundedCornerShape(DuckRadius.M)).padding(6.dp)) {
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(DuckRadius.S)).background(p.surface).padding(5.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(DuckRadius.S)).background(p.goldRole.fill))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (col in listOf(p.greenRole.fill, p.blueRole.fill, p.redRole.fill)) Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                    Spacer(Modifier.weight(1f))
                    Row {
                        Box(Modifier.size(9.dp).background(p.squareLight)); Box(Modifier.size(9.dp).background(p.squareDark))
                    }
                }
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(palette.label, color = c.text, style = MaterialTheme.typography.titleSmall)
            Text(palette.description, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = c.goldRole.fill, unselectedColor = c.outlineStrong),
        )
    }
}
