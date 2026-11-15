package com.ivanherboch.chessduck.feature

import androidx.compose.material3.MaterialTheme
import com.ivanherboch.chessduck.ui.DuckRadius
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivanherboch.chessduck.ui.DuckSheet
import com.ivanherboch.chessduck.ui.LocalDuck
import com.ivanherboch.chessduck.ui.SectionTitle

/** Fiche « Mémo Anglais » : lettres FIDE et symboles. */
@Composable
fun MemoSheet(onClose: () -> Unit) {
    val c = LocalDuck.current
    DuckSheet("Mémo anglais — notation FIDE", onClose) {
        SectionTitle("Les pièces")
        val pieces = listOf(
            Triple("K", "King", "Roi"), Triple("Q", "Queen", "Dame"), Triple("R", "Rook", "Tour"),
            Triple("B", "Bishop", "Fou"), Triple("N", "Knight", "Cavalier"), Triple("—", "Pawn", "Pion : aucune lettre"),
        )
        for ((l, en, fr) in pieces) MemoRow(l, "$en  →  $fr")
        SectionTitle("Les symboles")
        MemoRow("x", "Prise (capture) : Bxf7, exd5")
        MemoRow("+", "Échec au roi : Qh5+")
        MemoRow("#", "Échec et mat : Qxf7#")
        MemoRow("=", "Promotion du pion : e8=Q")
        MemoRow("O-O", "Petit roque (côté roi)")
        MemoRow("O-O-O", "Grand roque (côté dame)")
        SectionTitle("Les règles d'or")
        for (t in listOf(
            "Un coup = lettre de la pièce + case d'arrivée : Nf3.",
            "Un pion ne prend jamais de lettre : e4. S'il capture, on indique sa colonne de départ : exd5.",
            "N comme kNight : le K est réservé au roi (King).",
            "Si deux pièces identiques peuvent aller sur la même case, on précise la colonne de départ (Rad1), sinon la rangée (N5f3).",
            "L'ordre est toujours : pièce, départ éventuel, x, case d'arrivée, =promotion, + ou #.",
        )) {
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•  ", color = c.goldText, style = MaterialTheme.typography.bodyMedium)
                Text(t, color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MemoRow(key: String, text: String) {
    val c = LocalDuck.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.height(36.dp).fillMaxWidth(0.22f).clip(RoundedCornerShape(DuckRadius.S)).background(c.surfaceHi), contentAlignment = Alignment.Center) {
            Text(key, color = c.goldText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Text(text, color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
    }
}
