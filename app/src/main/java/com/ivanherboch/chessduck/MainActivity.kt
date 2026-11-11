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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        setContent {
            val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
            val st = settings ?: return@setContent
            DisposableEffect(st.dark) {
                val style = if (st.dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            app.feedback.hapticsEnabled = st.haptics
            ChessDuckTheme(st.dark) {
                CompositionLocalProvider(LocalFeedback provides app.feedback) { MainScreen(st, app) }
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

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
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
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                0 -> NotationScreen(notation)
                1 -> AcademyScreen(academy)
                2 -> PuzzlesScreen(puzzles)
                else -> PlayScreen(play)
            }
        }
        // Barre de navigation inférieure fixe à 4 modules
        Row(Modifier.fillMaxWidth().height(56.dp).background(c.surface)) {
            TABS.forEachIndexed { i, (emoji, label) ->
                val selected = tab == i
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { fb?.key(); tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) c.gold.copy(alpha = 0.22f) else c.surface).padding(horizontal = 16.dp, vertical = 1.dp)) {
                        AutoText(emoji, maxSize = 19.sp, align = TextAlign.Center)
                    }
                    AutoText(label, color = if (selected) c.gold else c.muted, maxSize = 12.sp, weight = if (selected) FontWeight.Bold else FontWeight.Medium, align = TextAlign.Center)
                }
            }
        }
    }

    if (showMemo) MemoSheet { showMemo = false }
    if (showSettings) SettingsSheet(st, app) { showSettings = false }
}

/** Paramètres globaux : thème Jour/Nuit et pseudo (persistés via DataStore). */
@Composable
private fun SettingsSheet(st: Settings, app: ChessDuckApp, onClose: () -> Unit) {
    val c = LocalDuck.current
    val scope = rememberCoroutineScope()
    var pseudo by remember { mutableStateOf(st.pseudo) }
    DuckSheet("⚙️ Paramètres", onClose) {
        SectionTitle("APPARENCE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuckChip("🌙 Mode Nuit (sombre)", st.dark, { scope.launch { app.prefs.setDark(true) } }, Modifier.weight(1f))
            DuckChip("☀️ Mode Jour (clair)", !st.dark, { scope.launch { app.prefs.setDark(false) } }, Modifier.weight(1f))
        }
        SectionTitle("RETOUR HAPTIQUE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuckChip("📳 Vibration activée", st.haptics, { scope.launch { app.prefs.setHaptics(true) } }, Modifier.weight(1f))
            DuckChip("🔕 Vibration désactivée", !st.haptics, { scope.launch { app.prefs.setHaptics(false) } }, Modifier.weight(1f))
        }
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
        AutoText("Votre classement actuel : ${st.elo} ELO (formule FIDE, K = 32).", color = c.muted, maxSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
        SectionTitle("À PROPOS")
        AutoText(
            "ChessDuck fonctionne 100 % hors-ligne. Moteur : Stockfish 11 (GPLv3). Problèmes : base ouverte Lichess (CC0). " +
                "Pièces : série classique de Cburnett, Wikimedia Commons (CC BY-SA 3.0).",
            color = c.muted, maxSize = 12.sp, maxLines = 6,
        )
        AutoText("© Ivan Herboch, 2026", color = c.muted, maxSize = 12.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp))
    }
}
