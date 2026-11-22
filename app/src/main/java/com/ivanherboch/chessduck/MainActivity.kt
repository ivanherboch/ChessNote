package com.ivanherboch.chessduck

import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.runtime.Stable
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
import com.ivanherboch.chessduck.feature.ReviewTopic
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
            // Barres système transparentes, icônes claires ou sombres selon le thème (réappliqué à chaque changement).
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
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

    /** L'utilisateur quitte l'application : Stockfish, la synthèse vocale et les sons sont libérés (pas à une recréation). */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) (application as ChessDuckApp).container.release()
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
private fun TabLabel(t: TabSpec, maxLines: Int = Int.MAX_VALUE) {
    // Une ligne : en très grande police, le libellé se réduit (jusqu'à 11 sp) au lieu d'être coupé (« Académi »).
    val color = LocalContentColor.current
    val style = MaterialTheme.typography.labelMedium
    if (maxLines == 1) BasicText(
        t.label, style = style, color = { color }, maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
    else Text(t.label, style = style, maxLines = maxLines)
}

@Composable
fun MainScreen(st: AppChrome, prefs: SettingsRepository, narrator: Narrator, appScope: CoroutineScope) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    // L'application s'ouvre sur l'Académie : le parcours d'apprentissage est la porte d'entrée.
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var showSettings by remember { mutableStateOf(false) }
    // Les ViewModels vivent au niveau de l'activité : l'état de chaque module est conservé d'un onglet à l'autre.
    // Ils ne sont créés qu'à la première visite de leur onglet. Stockfish démarre en arrière-plan dès l'ouverture de
    // l'Académie (onglet d'accueil), pour que le premier coup joué n'attende pas son lancement.
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

    // ViewModel de l'Académie : une seule obtention, à la première visite de l'onglet, puis gardé pour l'animation de
    // sortie et les retours.
    var academyUsed by rememberSaveable { mutableStateOf(tab == 1) }
    LaunchedEffect(tab) { if (tab == 1) academyUsed = true }
    val academy: AcademyViewModel? = if (tab == 1 || academyUsed) viewModel(factory = DuckViewModels.Factory) else null
    val header: @Composable (Modifier) -> Unit = { m ->
        Row(m.fillMaxWidth().height(50.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.mascot), contentDescription = null, modifier = Modifier.size(34.dp))
            // En-tête identique pour les 4 onglets : mascotte, titre (réduit si la place manque), Paramètres.
            val titleStyle = MaterialTheme.typography.titleLarge
            BasicText(
                "ChessDuck", style = titleStyle, color = { c.text }, maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = titleStyle.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp).weight(1f).semantics { heading() },
            )
            DuckIconButton(Icons.Rounded.Settings, "Paramètres", { showSettings = true })
        }
    }
    // Passerelles entre onglets : une demande en attente, consommée puis effacée par l'onglet d'arrivée.
    val bridges = remember { TabBridges() }
    val content: @Composable (Modifier) -> Unit = { m ->
        AnimatedContent(
            targetState = tab, modifier = m, label = "onglet",
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        ) { t ->
            // Chaque onglet garde son état d'interface (défilements, tiroirs, champs) quand on en change.
            holder.SaveableStateProvider(t) {
                when (t) {
                    0 -> {
                        val nvm = viewModel<NotationViewModel>(factory = DuckViewModels.Factory)
                        LaunchedEffect(bridges.vision) { if (bridges.vision) { nvm.startVision(com.ivanherboch.chessduck.feature.VisionKind.TOUCH); bridges.vision = false } }
                        NotationScreen(nvm)
                    }
                    1 -> academy?.let { avm ->
                        LaunchedEffect(bridges.academyTopic) {
                            bridges.academyTopic?.let { t -> avm.trainTopic(t.lessonId, t.theme); bridges.academyTopic = null }
                        }
                        AcademyScreen(
                            avm,
                            onTrainTheme = { theme, level -> bridges.trainTheme = theme to level; select(2) },
                            onPlayPosition = { fen, white -> bridges.playPosition = fen to white; select(3) },
                            onVision = { bridges.vision = true; select(0) },
                        )
                    }
                    2 -> {
                        val pvm = viewModel<PuzzlesViewModel>(factory = DuckViewModels.Factory)
                        LaunchedEffect(bridges.trainTheme) {
                            bridges.trainTheme?.let { (theme, level) -> pvm.train(theme, (level - 1).coerceIn(1, 4)); bridges.trainTheme = null }
                        }
                        PuzzlesScreen(pvm)
                    }
                    else -> {
                        val play = viewModel<PlayViewModel>(factory = DuckViewModels.Factory)
                        val review = viewModel<GameReviewViewModel>(factory = DuckViewModels.Factory)
                        LaunchedEffect(bridges.playPosition) {
                            bridges.playPosition?.let { (fen, white) -> review.close(); play.startFromPosition(fen, white); bridges.playPosition = null }
                        }
                        PlayScreen(play, review, onTrainTopic = { t -> bridges.academyTopic = t; select(1) })
                    }
                }
            }
        }
    }

    // La disposition (rail ou barre du bas) suit LocalIsTwoPane, déjà calculé par l'activité : un simple Box suffit.
    Box(Modifier.fillMaxSize().background(c.bg)) {
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

/**
 * Passerelles entre onglets, regroupées : thème à travailler dans Problèmes, position à jouer dans le Jeu Libre,
 * Vision des cases (onglet Notation), point faible repéré par la revue de partie (Académie).
 */
@Stable
private class TabBridges {
    var trainTheme by mutableStateOf<Pair<String, Int>?>(null)
    var playPosition by mutableStateOf<Pair<String, Boolean>?>(null)
    var vision by mutableStateOf(false)
    var academyTopic by mutableStateOf<ReviewTopic?>(null)
}
