package com.ivanherboch.chessduck.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.ivanherboch.chessduck.ui.icons.RestartAlt
import com.ivanherboch.chessduck.ui.icons.Undo
import com.ivanherboch.chessduck.ui.icons.Visibility

/*
 * Boutons d'action et textes d'indice communs à l'Académie, aux Problèmes et au Jeu Libre : un même geste a partout
 * le même libellé, la même couleur et la même icône.
 */

/** Textes des deux indices (la pièce, puis la flèche), identiques dans tous les modes. */
object HintText {
    const val PIECE = "Indice 1/2 : la pièce à jouer est surlignée en vert."
    const val ARROW = "Indice 2/2 : la flèche montre le coup à jouer."

    /** Exercice noté (Problèmes, Pratiquer) : chaque indice annonce que la flèche fait perdre le problème. */
    fun scored(stage: Int) = if (stage == 1) "$PIECE Montrer le coup : problème non compté." else "$ARROW Ce problème ne comptera pas comme résolu."

    /** Exercice non noté (leçon, finale) : les deux indices seuls. */
    fun plain(stage: Int) = if (stage == 1) PIECE else ARROW
}

/**
 * Indice d'un exercice noté : avant la flèche, le bouton dit ce qu'elle coûte. Libellé complet s'il a la place ;
 * court si la police est grande ou si le bouton Solution partage la rangée ([crowded]).
 */
@Composable
fun ScoredHintButton(stage: Int, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, crowded: Boolean = false) {
    val roomy = !crowded && LocalDensity.current.fontScale <= 1.3f
    HintButton(stage, onClick, modifier, enabled = enabled, nextLabel = if (roomy) "Montrer le coup (non compté)" else "Montrer le coup")
}

/** « Solution » d'un exercice noté, proposée après une erreur. */
@Composable
fun SolutionButton(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String) =
    DuckButton("Solution", onClick, modifier, icon = Icons.Rounded.Visibility, contentDescription = contentDescription)

/** Passer à la suite (problème, position, question, leçon, bilan) : vert plein, flèche vers l'avant. */
@Composable
fun NextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentDescription: String? = null) =
    DuckButton(
        label, onClick, modifier, enabled = enabled, accent = LocalDuck.current.green, filled = true,
        icon = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = contentDescription,
    )

/** Après un coup réfuté : revenir avant l'erreur. Rouge plein. */
@Composable
fun RetryButton(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "Réessayer : revenir avant le coup manqué") =
    DuckButton("Réessayer", onClick, modifier, accent = LocalDuck.current.red, filled = true, icon = Icons.AutoMirrored.Rounded.Undo, contentDescription = contentDescription)

/** Recommencer une série terminée (finale, réflexes, pratique d'une leçon) : vert plein. */
@Composable
fun RestartSeriesButton(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null) =
    DuckButton("Recommencer", onClick, modifier, accent = LocalDuck.current.green, filled = true, icon = Icons.Rounded.RestartAlt, contentDescription = contentDescription)
