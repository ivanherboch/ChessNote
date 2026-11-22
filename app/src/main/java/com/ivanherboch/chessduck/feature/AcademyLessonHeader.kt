package com.ivanherboch.chessduck.feature

import com.ivanherboch.chessduck.content.Lesson
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.rounded.Info
import com.ivanherboch.chessduck.ui.ButtonVariant
import com.ivanherboch.chessduck.ui.DuckButton
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import com.ivanherboch.chessduck.ui.icons.SwapVert


// En-tête du mode leçon de l'Académie : sélecteur de leçon (catalogue) et barre des modes Guidé/Défi et de leurs outils.

@Composable
internal fun LessonSelector(
    l: Lesson, mastered: Set<Int>, stars: Int, status: String, total: Int,
    onCatalog: () -> Unit, onHome: () -> Unit, onNext: () -> Unit,
) {
    val c = LocalDuck.current
    // ← Accueil / leçon courante (ouvre le catalogue) / leçon suivante.
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        DuckIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Revenir à l'accueil de l'Académie", onHome)
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(DuckRadius.M)).background(c.surface)
                .border(1.dp, c.outline, RoundedCornerShape(DuckRadius.M))
                .clickable(role = Role.Button, onClickLabel = "Ouvrir le catalogue des leçons") { onCatalog() }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = "Leçon ${Lessons.number(l.id)} : ${l.title}, ${l.category.label}, palier ${levelSpoken(l.level)}, " +
                        status.lowercase() + (if (stars > 0) ", ${starsText(stars)}" else "") +
                        ". ${mastered.size} leçons maîtrisées sur $total. Ouvrir le catalogue des leçons"
                    onClick { onCatalog(); true }
                }
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            // Deux lignes compactes pour laisser le maximum de hauteur à l'échiquier : le titre, puis la catégorie, le
            // palier et un seul statut (À faire, En cours ou Maîtrisée), avec les étoiles éventuelles.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Lessons.number(l.id)}. ${l.title}", color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${l.category.label} · P${l.level} · ", color = c.blueText, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    status, color = if (status == STATUS_MASTERED) c.greenText else if (status == STATUS_STARTED) c.goldText else c.muted,
                    style = MaterialTheme.typography.labelMedium, maxLines = 1,
                )
                if (stars > 0) StarRow(stars, Modifier.padding(start = 6.dp), size = 14.dp)
            }
        }
        DuckIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Leçon suivante", onNext)
    }
}

/** Statut unique d'une leçon, affiché sous son titre et dans le catalogue. */
internal const val STATUS_TODO = "À faire"
internal const val STATUS_STARTED = "En cours"
internal const val STATUS_MASTERED = "Maîtrisée"

/** Statut d'une leçon d'après sa progression : maîtrisée, commencée (exercice tenté), ou à faire. */
internal fun lessonStatus(p: com.ivanherboch.chessduck.data.LessonProgress?): String = when {
    p == null -> STATUS_TODO
    p.mastered -> STATUS_MASTERED
    p.stars > 0 || p.practiceTotal > 0 || p.errors > 0 -> STATUS_STARTED
    else -> STATUS_TODO
}

/**
 * Les deux modes de la leçon, Comprendre (démonstration commentée) et Pratiquer (l'exercice actif), et les seuls
 * outils utiles : les variantes d'une ouverture, la fiche et le retournement de l'échiquier. En très grande police, ou
 * quand le menu des variantes est présent, les modes occupent toute une rangée et les outils passent dessous (aucun
 * libellé coupé).
 */
@Composable
internal fun LessonModeBar(
    vm: AcademyViewModel, s: AcademyState, l: Lesson, variantsDone: Set<String>, onBrief: () -> Unit,
) {
    val c = LocalDuck.current
    val variants = l.variants.isNotEmpty() && s.repertoire == null && s.review == null
    val twoRows = LocalDensity.current.fontScale > 1.3f || variants
    val modes: @Composable (Modifier) -> Unit = { m ->
        DuckSegmentedRow(listOf("Comprendre", "Pratiquer"), if (s.challenge) 1 else 0, { i -> vm.setChallenge(i == 1) }, m)
    }
    val tools: @Composable () -> Unit = {
        // Les variantes d'une ouverture se choisissent ici (dans les deux modes) ; hors répertoire et révision.
        if (variants) VariantMenu(l, s.variant, vm::setVariant, variantsDone)
        // Même hauteur visible que les segments Comprendre | Pratiquer (40 dp) ; la cible tactile reste de 48 dp.
        DuckButton(
            "Fiche", onBrief, accent = c.blue, variant = ButtonVariant.OUTLINED, icon = Icons.Rounded.Info, height = 40.dp,
            contentDescription = "Fiche de la leçon : objectif, règle, idées et moments clés",
        )
        DuckIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip)
    }
    if (twoRows) {
        modes(Modifier.fillMaxWidth().padding(top = 2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) { tools() }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            modes(Modifier.weight(1f))
            tools()
        }
    }
}
