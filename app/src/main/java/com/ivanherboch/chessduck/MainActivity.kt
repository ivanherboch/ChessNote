package com.ivanherboch.chessduck

import android.content.pm.ActivityInfo
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.feature.AcademyScreen
import com.ivanherboch.chessduck.feature.AcademyViewModel
import com.ivanherboch.chessduck.feature.KidScreen
import com.ivanherboch.chessduck.feature.KidViewModel
import com.ivanherboch.chessduck.feature.MemoSheet
import com.ivanherboch.chessduck.feature.NotationScreen
import com.ivanherboch.chessduck.feature.NotationViewModel
import com.ivanherboch.chessduck.feature.PlayScreen
import com.ivanherboch.chessduck.feature.PlayViewModel
import com.ivanherboch.chessduck.feature.PuzzlesScreen
import com.ivanherboch.chessduck.feature.PuzzlesViewModel
import com.ivanherboch.chessduck.ui.ChessDuckTheme
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.LocalShowCoords
import com.ivanherboch.chessduck.ui.SectionTitle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as ChessDuckApp
        // Téléphone : portrait. Tablette (plus petite largeur >= 600 dp) : paysage, en deux colonnes.
        val tablet = resources.getBoolean(R.bool.is_tablet)
        requestedOrientation = if (tablet) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
            val st = settings ?: return@setContent
            val systemDark = isSystemInDarkTheme()
            // Le mode enfant impose un écran clair et épuré.
            val dark = !st.kidMode && when (st.themeMode) {
                0 -> systemDark
                1 -> false
                else -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            app.feedback.hapticsEnabled = st.haptics
            app.feedback.soundEnabled = st.sound
            ChessDuckTheme(dark = dark, kid = st.kidMode, dynamic = st.dynamicColor) {
                CompositionLocalProvider(LocalFeedback provides app.feedback, LocalShowCoords provides st.coords) {
                    MainScreen(st, app)
                }
            }
        }
    }
}

private class TabSpec(val label: String, val description: String, val icon: @Composable () -> Painter, val selectedIcon: @Composable () -> Painter)

private val TABS = listOf(
    TabSpec("Notation", "Notation : apprendre la notation anglaise", { rememberVectorPainter(Icons.Outlined.Keyboard) }, { rememberVectorPainter(Icons.Rounded.Keyboard) }),
    TabSpec("Académie", "Académie : leçons interactives", { rememberVectorPainter(Icons.Outlined.School) }, { rememberVectorPainter(Icons.Rounded.School) }),
    TabSpec("Problèmes", "Problèmes Lichess", { rememberVectorPainter(Icons.Outlined.Extension) }, { rememberVectorPainter(Icons.Rounded.Extension) }),
    TabSpec("Jeu Libre", "Jeu libre contre l'ordinateur ou à deux", { painterResource(R.drawable.ic_knight) }, { painterResource(R.drawable.ic_knight) }),
)

@Composable
fun MainScreen(st: Settings, app: ChessDuckApp) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showMemo by remember { mutableStateOf(false) }
    // Les ViewModels vivent au niveau de l'activité : l'état de chaque module est conservé d'un onglet à l'autre.
    val notation: NotationViewModel = viewModel()
    val academy: AcademyViewModel = viewModel()
    val puzzles: PuzzlesViewModel = viewModel()
    val play: PlayViewModel = viewModel()
    val kid: KidViewModel = viewModel()

    // En quittant le mode enfant, sa partie est effacée ; la partie du Jeu Libre, elle, est retrouvée telle quelle.
    var wasKid by remember { mutableStateOf(st.kidMode) }
    LaunchedEffect(st.kidMode) {
        if (wasKid && !st.kidMode) kid.reset()
        wasKid = st.kidMode
    }
    if (st.kidMode) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                KidScreen(kid) { app.scope.launch { app.prefs.setKidMode(false) } }
            }
        }
        return
    }

    // Retour Android : depuis un onglet secondaire, on revient à la Notation avant de quitter l'application.
    BackHandler(enabled = tab != 0) { tab = 0 }
    val select: (Int) -> Unit = { if (it != tab) { fb?.key(); tab = it } }

    val header: @Composable (Modifier) -> Unit = { m ->
        Row(m.fillMaxWidth().height(50.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.mascot), contentDescription = null, modifier = Modifier.size(34.dp))
            Text(
                "ChessDuck", style = MaterialTheme.typography.titleLarge, color = c.text,
                modifier = Modifier.padding(start = 8.dp).weight(1f).semantics { heading() },
            )
            if (tab == 0) DuckButton("Mémo", { showMemo = true }, icon = Icons.AutoMirrored.Rounded.MenuBook, accent = c.blue, contentDescription = "Mémo anglais : lettres et symboles FIDE")
            DuckIconButton(Icons.Rounded.Settings, "Paramètres", { showSettings = true })
        }
    }
    val content: @Composable (Modifier) -> Unit = { m ->
        AnimatedContent(
            targetState = tab, modifier = m, label = "onglet",
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        ) { t ->
            when (t) {
                0 -> NotationScreen(notation)
                1 -> AcademyScreen(academy)
                2 -> PuzzlesScreen(puzzles)
                else -> PlayScreen(play)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        if (maxWidth > maxHeight) {
            // Paysage (tablette) : rail de navigation à gauche, l'échiquier garde toute la hauteur.
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = c.surface, contentColor = c.text) {
                    Spacer(Modifier.weight(1f))
                    TABS.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i, onClick = { select(i) },
                            icon = { Icon(if (tab == i) t.selectedIcon() else t.icon(), contentDescription = null) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = c.goldRole.onFill, indicatorColor = c.goldRole.fill, selectedTextColor = c.text,
                                unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                            ),
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom))) {
                    header(Modifier)
                    content(Modifier.fillMaxWidth().weight(1f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))
                content(Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
                // Barre de navigation M3 compacte ; sa surface s'étend sous la barre système.
                val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                NavigationBar(containerColor = c.surface, contentColor = c.text, tonalElevation = 0.dp, modifier = Modifier.height(64.dp + navInset)) {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { select(i) },
                            icon = { Icon(if (tab == i) t.selectedIcon() else t.icon(), contentDescription = null) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.goldRole.onFill, indicatorColor = c.goldRole.fill, selectedTextColor = c.text,
                                unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showMemo) MemoSheet { showMemo = false }
    if (showSettings) SettingsSheet(st, app) { showSettings = false }
}

/** Ligne de réglage M3 : titre, description et interrupteur. */
@Composable
private fun SwitchRow(title: String, description: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
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

/** Champ de pseudo : libellé flottant, compteur, validation par la touche « OK » du clavier. */
@Composable
private fun PseudoField(label: String, initial: String, fallback: String, onSave: (String) -> Unit) {
    val c = LocalDuck.current
    val focus = LocalFocusManager.current
    var value by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = value,
        onValueChange = { v -> value = v.take(16); onSave(value.trim().ifEmpty { fallback }) },
        label = { Text(label) },
        supportingText = { Text("${value.length}/16", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.text, unfocusedTextColor = c.text, focusedBorderColor = c.goldText, unfocusedBorderColor = c.outlineStrong,
            cursorColor = c.goldText, focusedLabelColor = c.goldText, unfocusedLabelColor = c.muted,
            focusedSupportingTextColor = c.muted, unfocusedSupportingTextColor = c.muted,
        ),
    )
}

/** Paramètres globaux (persistés via DataStore). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(st: Settings, app: ChessDuckApp, onClose: () -> Unit) {
    val c = LocalDuck.current
    // Les réglages sont écrits dans la portée de l'application : fermer le tiroir n'annule jamais l'écriture.
    val scope = app.scope
    DuckSheet("Paramètres", onClose) {
        SectionTitle("Apparence")
        val modes = listOf("Système", "Jour", "Nuit")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = st.themeMode == i, onClick = { scope.launch { app.prefs.setThemeMode(i) } },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = c.goldRole.fill, activeContentColor = c.goldRole.onFill, activeBorderColor = c.outlineStrong,
                        inactiveContainerColor = c.surface, inactiveContentColor = c.text, inactiveBorderColor = c.outlineStrong,
                    ),
                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        }
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
