package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.content.Lesson
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.content.Lessons
import com.ivanherboch.chessduck.ui.DuckIconButton
import com.ivanherboch.chessduck.ui.DuckRadius
import com.ivanherboch.chessduck.ui.DuckSegmentedRow
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.icons.ExpandMore
import com.ivanherboch.chessduck.ui.icons.SportsEsports
import com.ivanherboch.chessduck.ui.icons.SportsScore
import com.ivanherboch.chessduck.ui.icons.SwapVert
import com.ivanherboch.chessduck.ui.icons.Visibility


// En-tête du mode leçon de l'Académie : sélecteur de leçon (catalogue) et barre des modes Guidé/Défi et de leurs outils.

/** Sélecteur de leçon : précédente / leçon courante (ouvre le catalogue) / suivante, avec maîtrise et étoiles. */
@Composable
internal fun LessonSelector(
    l: Lesson, mastered: Set<Int>, stars: Int, toValidate: Boolean, total: Int,
    onCatalog: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit,
) {
    val c = LocalDuck.current
    // Sélecteur de leçon : précédente / leçon courante (ouvre le catalogue) / suivante.
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Leçon précédente", onPrev)
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
                .border(1.dp, c.outline, RoundedCornerShape(DuckRadius.M))
                .clickable(role = Role.Button, onClickLabel = "Ouvrir le catalogue des leçons") { onCatalog() }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = "Leçon ${Lessons.number(l.id)} : ${l.title}, ${l.category.label}, palier ${levelSpoken(l.level)}" +
                        (if (l.id in mastered) ", maîtrisée" else if (toValidate) ", Défi réussi, validation restante" else "") + (if (stars > 0) ", ${starsText(stars)}" else "") +
                        ". ${mastered.size} leçons maîtrisées sur $total. Ouvrir le catalogue des leçons"
                    onClick { onCatalog(); true }
                }
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            // Deux lignes compactes pour laisser le maximum de hauteur à l'échiquier : le titre, puis la
            // catégorie, le palier, la progression, la maîtrise et les étoiles en petit.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Lessons.number(l.id)}. ${l.title}", color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${l.category.label} · P${l.level}", color = c.blueText, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (l.id in mastered) Icon(Icons.Rounded.CheckCircle, "Maîtrisée", tint = c.greenText, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                else if (toValidate) Text(" · À valider", color = c.goldText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                if (stars > 0) StarRow(stars, Modifier.padding(start = 6.dp), size = 14.dp)
                Spacer(Modifier.weight(0.01f))
                Text("${mastered.size}/$total", color = c.goldText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Leçon suivante", onNext)
    }
}

/**
 * Modes Guidé/Défi (ou Deviner) et outils de la leçon : variantes, fiche, moments clés, « Jouer cette position »,
 * retournement. En très grande police, les modes occupent toute une rangée et les outils passent dessous.
 */
@Composable
internal fun LessonModeBar(
    vm: AcademyViewModel, s: AcademyState, l: Lesson, model: Boolean, variantsDone: Set<String>, modelPlay: Boolean,
    onBrief: () -> Unit, onPlay: () -> Unit,
) {
    val c = LocalDuck.current
    // Très grande police : les modes occupent toute une rangée (libellés jamais coupés), les icônes passent dessous.
    val big = LocalDensity.current.fontScale > 1.3f
    val modes: @Composable (Modifier) -> Unit = { m ->
        // Partie modèle : libellé court (« Deviner »), la rangée porte aussi la fiche et « Jouer cette position ».
        DuckSegmentedRow(
            listOf("Guidé", if (model) "Deviner" else "Défi"), if (s.challenge) 1 else 0, { i -> vm.setChallenge(i == 1) }, m,
            icons = if (big) null else listOf(Icons.Rounded.Visibility, Icons.Rounded.SportsScore),
        )
    }
    val tools: @Composable () -> Unit = {
        // Les variantes d'une ouverture se choisissent ici (Guidé comme Défi), sans rangée de plus sous l'échiquier.
        // Hors répertoire et révision, qui imposent leur ligne.
        if (l.variants.isNotEmpty() && s.repertoire == null && s.review == null) VariantMenu(l, s.variant, vm::setVariant, variantsDone)
        // Fiche de la leçon (pour toutes les leçons) et, pour une partie modèle, « Jouer cette position » à chaque
        // coup : des icônes ici, pour que l'échiquier garde toute sa taille.
        DuckIconButton(Icons.Rounded.Info, "Fiche de la leçon", onBrief, accent = c.blue)
        // Partie modèle en mode Deviner : ne deviner que les moments clés (les autres coups se jouent seuls).
        if (model && s.challenge && l.pauseSteps.isNotEmpty()) DuckIconButton(
            Icons.Rounded.Star,
            if (s.keyMoments) "Moments clés seulement (activé) : deviner tous les coups" else "Ne deviner que les ${l.pauseSteps.size} moments clés",
            { vm.setKeyMoments(!s.keyMoments) }, accent = c.gold, selected = s.keyMoments,
        )
        if (modelPlay) DuckIconButton(
            Icons.Rounded.SportsEsports, "Jouer cette position contre l'Ordinateur, avec le camp de votre choix", onPlay, accent = c.blue,
        )
        DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
    }
    if (big) {
        modes(Modifier.fillMaxWidth().padding(top = 2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) { tools() }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            modes(Modifier.weight(1f))
            tools()
        }
    }
}
