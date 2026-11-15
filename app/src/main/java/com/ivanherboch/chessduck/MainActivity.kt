package com.ivanherboch.chessduck

import kotlinx.coroutines.CoroutineScope
import com.ivanherboch.chessduck.data.SettingsRepository
import com.ivanherboch.chessduck.ui.Narrator
import com.ivanherboch.chessduck.ui.DuckPalette
import com.ivanherboch.chessduck.ui.BoardTheme
import com.ivanherboch.chessduck.feature.GameReviewViewModel
import com.ivanherboch.chessduck.ui.isTwoPane
import com.ivanherboch.chessduck.ui.LocalIsTwoPane
import com.ivanherboch.chessduck.feature.SettingsSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import android.graphics.Color as AColor
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
import androidx.compose.material.icons.Icons
import com.ivanherboch.chessduck.ui.icons.Extension
import com.ivanherboch.chessduck.ui.icons.Keyboard
import com.ivanherboch.chessduck.ui.icons.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.feature.AcademyScreen
import com.ivanherboch.chessduck.feature.AcademyViewModel
import com.ivanherboch.chessduck.feature.KidScreen
import com.ivanherboch.chessduck.feature.KidViewModel
import com.ivanherboch.chessduck.feature.NotationScreen
import com.ivanherboch.chessduck.feature.NotationViewModel
import com.ivanherboch.chessduck.feature.PlayScreen
import com.ivanherboch.chessduck.feature.PlayViewModel
import com.ivanherboch.chessduck.feature.PuzzlesScreen
import com.ivanherboch.chessduck.feature.PuzzlesViewModel
import com.ivanherboch.chessduck.ui.ChessDuckTheme
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.LocalShowCoords
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/** Réglages qui habillent toute l'application (thème, retours, mode enfant). */
data class AppChrome(
    val themeMode: Int,
    val palette: Int,
    val boardTheme: Int,
    val coords: Boolean,
    val haptics: Boolean,
    val sound: Boolean,
    val kidMode: Boolean,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // L'écran de démarrage reste affiché jusqu'à la lecture des réglages : jamais d'écran vide ni de flash.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as ChessDuckApp
        // Aucune orientation imposée : la mise en page s'adapte à la fenêtre (portrait, paysage, pliable,
        // écran partagé) ; l'activité n'est pas recréée à la rotation (configChanges).
        // Seuls les réglages d'habillage sont observés ici : les changements de problème, de leçon,
        // d'ELO ou de série ne recomposent plus tout l'arbre de l'application.
        val chrome = MutableStateFlow<AppChrome?>(null)
        lifecycleScope.launch {
            app.prefs.settings.map { AppChrome(it.themeMode, it.palette, it.boardTheme, it.coords, it.haptics, it.sound, it.kidMode) }
                .distinctUntilChanged().collect { chrome.value = it }
        }
        splash.setKeepOnScreenCondition { chrome.value == null }
        setContent {
            val st = chrome.collectAsStateWithLifecycle().value ?: return@setContent
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
            LaunchedEffect(st.haptics, st.sound) {
                app.feedback.hapticsEnabled = st.haptics
                app.feedback.soundEnabled = st.sound
            }
            ChessDuckTheme(dark = dark, kid = st.kidMode, palette = DuckPalette.of(st.palette), board = BoardTheme.of(st.boardTheme)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalFeedback provides app.feedback, LocalShowCoords provides st.coords,
                        LocalIsTwoPane provides isTwoPane(maxWidth, maxHeight),
                    ) {
                        MainScreen(st, app.container.settings, app.container.narrator, app.scope)
                    }
                }
            }
        }
    }
}

private class TabSpec(val label: String, val icon: @Composable () -> Painter, val selectedIcon: @Composable () -> Painter)

private val TABS = listOf(
    TabSpec("Notation", { rememberVectorPainter(Icons.Outlined.Keyboard) }, { rememberVectorPainter(Icons.Rounded.Keyboard) }),
    TabSpec("Académie", { rememberVectorPainter(Icons.Outlined.School) }, { rememberVectorPainter(Icons.Rounded.School) }),
    TabSpec("Problèmes", { rememberVectorPainter(Icons.Outlined.Extension) }, { rememberVectorPainter(Icons.Rounded.Extension) }),
    TabSpec("Jeu Libre", { painterResource(R.drawable.ic_knight_outline) }, { painterResource(R.drawable.ic_knight) }),
)

/** Icône et libellé d'un onglet, partagés par le rail (tablette) et la barre de navigation (téléphone). */
@Composable
private fun TabIcon(t: TabSpec, selected: Boolean) = Icon(if (selected) t.selectedIcon() else t.icon(), contentDescription = null)

@Composable
private fun TabLabel(t: TabSpec, maxLines: Int = Int.MAX_VALUE) = Text(t.label, style = MaterialTheme.typography.labelMedium, maxLines = maxLines)

@Composable
fun MainScreen(st: AppChrome, prefs: SettingsRepository, narrator: Narrator, appScope: CoroutineScope) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    // Les ViewModels vivent au niveau de l'activité : l'état de chaque module est conservé d'un onglet à l'autre.
    // Ils ne sont créés qu'à la première visite de leur onglet (Stockfish et l'index des 150 000 problèmes
    // ne sont donc chargés que si l'utilisateur ouvre le Jeu Libre ou les Problèmes).
    val holder = rememberSaveableStateHolder()
    var kidUsed by rememberSaveable { mutableStateOf(st.kidMode) }
    val kid: KidViewModel? = if (st.kidMode || kidUsed) viewModel(factory = DuckViewModels.Factory) else null

    // En quittant le mode enfant, sa partie est effacée ; la partie du Jeu Libre, elle, est retrouvée telle quelle.
    var wasKid by remember { mutableStateOf(st.kidMode) }
    LaunchedEffect(st.kidMode) {
        if (st.kidMode) kidUsed = true
        if (wasKid && !st.kidMode) kid?.reset()
        wasKid = st.kidMode
    }
    if (st.kidMode && kid != null) {
        holder.SaveableStateProvider("kid") {
            Box(Modifier.fillMaxSize().background(c.bg)) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    KidScreen(kid) { appScope.launch { prefs.setKidMode(false) } }
                }
            }
        }
        return
    }

    // Retour Android : on revient aux onglets visités, du plus récent au plus ancien, puis on quitte l'application.
    var visited by rememberSaveable { mutableStateOf(intArrayOf()) }
    BackHandler(enabled = visited.isNotEmpty()) { tab = visited.last(); visited = visited.copyOf(visited.size - 1) }
    val select: (Int) -> Unit = {
        if (it != tab) {
            fb?.key()
            visited = (visited.filter { v -> v != tab } + tab).toIntArray()
            tab = it
        }
    }

    val header: @Composable (Modifier) -> Unit = { m ->
        Row(m.fillMaxWidth().height(50.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.mascot), contentDescription = null, modifier = Modifier.size(34.dp))
            Text(
                "ChessDuck", style = MaterialTheme.typography.titleLarge, color = c.text,
                modifier = Modifier.padding(start = 8.dp).weight(1f).semantics { heading() },
            )
            DuckIconButton(Icons.Rounded.Settings, "Paramètres", { showSettings = true })
        }
    }
    val content: @Composable (Modifier) -> Unit = { m ->
        AnimatedContent(
            targetState = tab, modifier = m, label = "onglet",
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        ) { t ->
            // Chaque onglet garde son état d'interface (défilements, tiroirs, champs) quand on en change.
            holder.SaveableStateProvider(t) {
                when (t) {
                    0 -> NotationScreen(viewModel<NotationViewModel>(factory = DuckViewModels.Factory))
                    1 -> AcademyScreen(viewModel<AcademyViewModel>(factory = DuckViewModels.Factory))
                    2 -> PuzzlesScreen(viewModel<PuzzlesViewModel>(factory = DuckViewModels.Factory))
                    else -> PlayScreen(
                        viewModel<PlayViewModel>(factory = DuckViewModels.Factory),
                        viewModel<GameReviewViewModel>(factory = DuckViewModels.Factory),
                    )
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        if (LocalIsTwoPane.current) {
            // Paysage (tablette) : rail de navigation à gauche, l'échiquier garde toute la hauteur.
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = c.surface, contentColor = c.text) {
                    Spacer(Modifier.weight(1f))
                    TABS.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i, onClick = { select(i) },
                            icon = { TabIcon(t, tab == i) },
                            label = { TabLabel(t) },
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
                            icon = { TabIcon(t, tab == i) },
                            label = { TabLabel(t, maxLines = 1) },
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

    if (showSettings) SettingsSheet(prefs, narrator, appScope) { showSettings = false }
}
