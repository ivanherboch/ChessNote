package com.ivanherboch.chessduck

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivanherboch.chessduck.data.Settings
import com.ivanherboch.chessduck.feature.AcademyScreen
import com.ivanherboch.chessduck.feature.AcademyViewModel
import com.ivanherboch.chessduck.feature.KidScreen
import com.ivanherboch.chessduck.feature.KidTheme
import com.ivanherboch.chessduck.feature.KidViewModel
import com.ivanherboch.chessduck.feature.MemoSheet
import com.ivanherboch.chessduck.feature.NotationScreen
import com.ivanherboch.chessduck.feature.NotationViewModel
import com.ivanherboch.chessduck.feature.PlayScreen
import com.ivanherboch.chessduck.feature.PlayViewModel
import com.ivanherboch.chessduck.feature.PuzzlesScreen
import com.ivanherboch.chessduck.feature.PuzzlesViewModel
import com.ivanherboch.chessduck.ui.AutoText
import com.ivanherboch.chessduck.ui.ChessDuckTheme
import com.ivanherboch.chessduck.ui.DuckButton
import com.ivanherboch.chessduck.ui.DuckChip
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.LocalFeedback
import com.ivanherboch.chessduck.ui.SectionTitle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as ChessDuckApp
        // Téléphone : portrait. Tablette (plus petite largeur >= 600 dp) : paysage.
        val tablet = resources.getBoolean(R.bool.is_tablet)
        requestedOrientation = if (tablet) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
            val st = settings ?: return@setContent
            DisposableEffect(st.dark, st.kidMode) {
                val style = if (st.dark && !st.kidMode) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            app.feedback.hapticsEnabled = st.haptics
            app.feedback.soundEnabled = st.sound
            // Le mode enfant impose un écran clair et épuré.
            val dark = st.dark && !st.kidMode
            // Sur tablette, toute l'interface est agrandie pour garder des textes et des boutons confortables.
            val base = LocalDensity.current
            val factor = if (!tablet) 1f else if (LocalConfiguration.current.smallestScreenWidthDp >= 720) 1.3f else 1.15f
            ChessDuckTheme(dark) {
                CompositionLocalProvider(LocalFeedback provides app.feedback, LocalDensity provides Density(base.density * factor, base.fontScale)) {
                    MainScreen(st, app)
                }
            }
        }
    }
}

private val TABS = listOf("⌨️" to "Notation", "🎓" to "Académie", "🧩" to "Problèmes", "⚔️" to "Jeu Libre")

@Composable
fun MainScreen(st: Settings, app: ChessDuckApp) {
    val c = LocalDuck.current
    val fb = LocalFeedback.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showMemo by remember { mutableStateOf(false) }
    // Les quatre ViewModels vivent au niveau de l'activité : l'état de chaque module est conservé d'un onglet à l'autre.
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
        KidTheme {
            Box(Modifier.fillMaxSize().background(LocalDuck.current.bg).safeDrawingPadding()) {
                KidScreen(kid) { app.scope.launch { app.prefs.setKidMode(false) } }
            }
        }
        return
    }

    val header: @Composable () -> Unit = {
        // En-tête global compact
        Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.mascot), "Mascotte Cavalier-Canard", Modifier.size(40.dp))
                Column(Modifier.padding(start = 6.dp).weight(1f, fill = false)) {
                    AutoText("ChessDuck", maxSize = 19.sp, weight = FontWeight.Black)
                    if (tab != 0) AutoText("© Ivan Herboch, 2026", color = c.muted.copy(alpha = 0.7f), maxSize = 8.sp)
                }
                Box(Modifier.padding(start = 6.dp, end = 6.dp).clip(RoundedCornerShape(8.dp)).background(c.gold).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    AutoText("ELO ${st.elo}", color = c.onAccent, maxSize = 13.sp, weight = FontWeight.Bold)
                }
            }
            if (tab == 0) DuckButton("📖 Mémo Anglais", { showMemo = true }, Modifier.padding(end = 6.dp).width(106.dp), accent = c.blue, height = 36.dp)
            DuckButton("⚙️", { showSettings = true }, Modifier.size(width = 44.dp, height = 36.dp), maxSize = 18.sp)
        }
    }
    val content: @Composable () -> Unit = {
        when (tab) {
            0 -> NotationScreen(notation)
            1 -> AcademyScreen(academy)
            2 -> PuzzlesScreen(puzzles)
            else -> PlayScreen(play)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        if (maxWidth > maxHeight) {
            // Paysage (tablette) : rail de navigation à gauche pour laisser toute la hauteur à l'échiquier.
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(84.dp).fillMaxHeight().background(c.surface)) {
                    TABS.forEachIndexed { i, (emoji, label) -> TabItem(emoji, label, tab == i, Modifier.weight(1f).fillMaxWidth()) { fb?.key(); tab = i } }
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp)) {
                    header()
                    Box(Modifier.fillMaxWidth().weight(1f)) { content() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header()
                Box(Modifier.fillMaxWidth().weight(1f)) { content() }
                // Barre de navigation inférieure fixe à 4 modules
                Row(Modifier.fillMaxWidth().height(56.dp).background(c.surface)) {
                    TABS.forEachIndexed { i, (emoji, label) -> TabItem(emoji, label, tab == i, Modifier.weight(1f).fillMaxHeight()) { fb?.key(); tab = i } }
                }
            }
        }
    }

    if (showMemo) MemoSheet { showMemo = false }
    if (showSettings) SettingsSheet(st, app) { showSettings = false }
}

/**
 * Onglet de navigation. L'onglet actif occupe tout son quart de bandeau avec un relief « enfoncé » :
 * fond plus sombre teinté d'or, ombre intérieure en haut, liseré doré en bas, contenu légèrement abaissé.
 */
@Composable
private fun TabItem(emoji: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDuck.current
    val shape = RoundedCornerShape(14.dp)
    val well = if (c.dark) Color(0xFF0B1220) else Color(0xFFC3CEDC)
    Column(
        modifier.padding(horizontal = 4.dp, vertical = 4.dp).clip(shape)
            .then(
                if (selected) Modifier.background(well).drawBehind {
                    // Ombre intérieure haute : le bouton paraît creusé dans le bandeau.
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (c.dark) 0.55f else 0.22f), Color.Transparent), endY = size.height * 0.45f))
                    drawRect(c.gold.copy(alpha = if (c.dark) 0.14f else 0.22f))
                    drawRect(c.gold, topLeft = Offset(size.width * 0.2f, size.height - 3.dp.toPx()), size = Size(size.width * 0.6f, 3.dp.toPx()))
                }.border(1.dp, Color.Black.copy(alpha = if (c.dark) 0.5f else 0.14f), shape)
                else Modifier,
            )
            .clickable { onClick() }
            .padding(top = if (selected) 2.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        AutoText(emoji, maxSize = 19.sp, align = TextAlign.Center)
        AutoText(label, color = if (selected) (if (c.dark) c.gold else Color(0xFF8A5A00)) else c.muted, maxSize = 12.sp, weight = if (selected) FontWeight.Bold else FontWeight.Medium, align = TextAlign.Center)
    }
}

/** Paramètres globaux : thème Jour/Nuit et pseudo (persistés via DataStore). */
@Composable
private fun SettingsSheet(st: Settings, app: ChessDuckApp, onClose: () -> Unit) {
    val c = LocalDuck.current
    // Les réglages sont écrits dans la portée de l'application : fermer le tiroir n'annule jamais l'écriture.
    val scope = app.scope
    var pseudo by remember { mutableStateOf(st.pseudo) }
    var pseudo2 by remember { mutableStateOf(st.pseudo2) }
    DuckSheet("⚙️ Paramètres", onClose) {
        SectionTitle("APPARENCE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuckChip("🌙 Mode Nuit (sombre)", st.dark, { scope.launch { app.prefs.setDark(true) } }, Modifier.weight(1f))
            DuckChip("☀️ Mode Jour (clair)", !st.dark, { scope.launch { app.prefs.setDark(false) } }, Modifier.weight(1f))
        }
        SectionTitle("BRUIT DES PIÈCES")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuckChip("🔉 Son activé", st.sound, { scope.launch { app.prefs.setSound(true) } }, Modifier.weight(1f))
            DuckChip("🔇 Son désactivé", !st.sound, { scope.launch { app.prefs.setSound(false) } }, Modifier.weight(1f))
        }
        SectionTitle("RETOUR HAPTIQUE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuckChip("📳 Vibration activée", st.haptics, { scope.launch { app.prefs.setHaptics(true) } }, Modifier.weight(1f))
            DuckChip("🔕 Vibration désactivée", !st.haptics, { scope.launch { app.prefs.setHaptics(false) } }, Modifier.weight(1f))
        }
        SectionTitle("MODE ENFANT")
        DuckButton("🧒 Activer le mode enfant", { onClose(); scope.launch { app.prefs.setKidMode(true) } }, Modifier.fillMaxWidth(), accent = c.green, filled = true, height = 44.dp, maxSize = 15.sp)
        AutoText(
            "Un écran joyeux et coloré pour jouer à deux avec un enfant : grand échiquier, cavalier en forme de girafe, animations qui montrent comment chaque pièce bouge, sans ordinateur ni historique.",
            color = c.muted, maxSize = 12.sp, maxLines = 4, modifier = Modifier.padding(top = 6.dp),
        )
        SectionTitle("PSEUDO DU JOUEUR")
        OutlinedTextField(
            value = pseudo,
            onValueChange = { v ->
                pseudo = v.take(16)
                scope.launch { app.prefs.setPseudo(pseudo.trim().ifEmpty { "Joueur" }) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.text, unfocusedTextColor = c.text, focusedBorderColor = c.gold, unfocusedBorderColor = c.outline, cursorColor = c.gold,
            ),
        )
        SectionTitle("PSEUDO DU JOUEUR 2 (MODE 2 JOUEURS)")
        OutlinedTextField(
            value = pseudo2,
            onValueChange = { v ->
                pseudo2 = v.take(16)
                scope.launch { app.prefs.setPseudo2(pseudo2.trim().ifEmpty { "Joueur 2" }) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.text, unfocusedTextColor = c.text, focusedBorderColor = c.gold, unfocusedBorderColor = c.outline, cursorColor = c.gold,
            ),
        )
        AutoText("Votre classement actuel : ${st.elo} ELO (formule FIDE, K = 32). Les parties à 2 joueurs ne comptent pas.", color = c.muted, maxSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
        SectionTitle("À PROPOS")
        AutoText(
            "ChessDuck fonctionne 100 % hors-ligne. Moteur : Stockfish 11 (GPLv3). Problèmes : base ouverte Lichess (CC0). " +
                "Pièces : série classique de Cburnett et pièce féerique Girafe, Wikimedia Commons (CC BY-SA 3.0). Police du mode enfant : Fredoka (OFL).",
            color = c.muted, maxSize = 12.sp, maxLines = 6,
        )
        val context = LocalContext.current
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
        AutoText("© Ivan Herboch, 2026 · v$version", color = c.muted, maxSize = 12.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp))
    }
}
