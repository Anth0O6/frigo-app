package com.frigopro.app.ui.composants

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frigopro.app.ui.theme.LocalStatuts

/**
 * Deux degrés d'alerte, et deux **formes** pour les dire.
 *
 * La couleur seule ne suffit pas : ambre et framboise se confondent pour un œil
 * daltonien, ou au soleil. L'urgence porte donc toujours un carré plein « ! »,
 * l'alerte un triangle vide — la forme dit le degré, la couleur le confirme.
 */
enum class GraviteAlerte { URGENCE, ALERTE }

/** Le signe d'une alerte : carré plein « ! » ou triangle vide. */
@Composable
fun IconeAlerte(
    gravite: GraviteAlerte,
    modifier: Modifier = Modifier,
    taille: Dp = 22.dp,
) {
    val statuts = LocalStatuts.current
    when (gravite) {
        GraviteAlerte.URGENCE -> Box(
            modifier = modifier
                .size(taille)
                .background(statuts.urgence, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        GraviteAlerte.ALERTE -> {
            val couleur = statuts.aValider
            Canvas(modifier = modifier.size(taille)) {
                val trait = 2.dp.toPx()
                val triangle = Path().apply {
                    moveTo(size.width / 2f, trait)
                    lineTo(size.width - trait, size.height - trait)
                    lineTo(trait, size.height - trait)
                    close()
                }
                drawPath(triangle, couleur, style = Stroke(width = trait, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Une ligne d'alerte : le signe à gauche, ce qui se passe au centre, et le geste
 * à droite, en texte cyan — la couleur de l'action.
 *
 * Le geste **nomme** ce que l'on va faire, et il ne fait que ce que la ligne
 * faisait déjà : toucher la ligne et toucher le bouton mènent au même endroit.
 * Un bouton qui promettrait « Commander » sans rien commander serait pire que
 * pas de bouton.
 */
@Composable
fun LigneAlerte(
    gravite: GraviteAlerte,
    titre: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    couleurDetail: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Carte(
        modifier = modifier,
        onClick = onAction,
        contour = true,
        forme = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconeAlerte(gravite = gravite)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titre,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = couleurDetail,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline),
            )
            TextButton(onClick = onAction) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
