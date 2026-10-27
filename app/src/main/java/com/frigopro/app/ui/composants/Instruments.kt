package com.frigopro.app.ui.composants

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frigopro.app.ui.Nombres
import com.frigopro.app.ui.theme.StyleChiffre
import com.frigopro.app.ui.theme.StyleChiffrePetit
import com.frigopro.app.ui.theme.StyleSection

/**
 * Les instruments : le cadran et la réglette de plage.
 *
 * Un relevé se lit **contre une plage**, pas comme un nombre seul : « 4 K » ne
 * dit rien à qui n'a pas la plage en tête, alors qu'un curseur posé à gauche de
 * la zone habituelle se juge sans lire le chiffre. C'est ce que fait un
 * manomètre, et c'est ce que l'écran des relevés imite.
 */

/**
 * Une échelle de mesure et, facultativement, la zone habituelle qu'elle porte.
 *
 * Pure arithmétique, sans Compose : la position d'un curseur est un calcul, et
 * un curseur mal placé se trouve par un test plutôt qu'à l'œil.
 */
data class PlageMesure(
    val min: Double,
    val max: Double,
    val habituelBas: Double? = null,
    val habituelHaut: Double? = null,
) {
    init {
        require(max > min) { "Une échelle doit avoir une étendue." }
    }

    /**
     * La position d'une valeur sur l'échelle, de 0 à 1.
     *
     * Hors échelle, le curseur **bute** au bord plutôt que de disparaître : une
     * surchauffe de 20 K sur une réglette de 12 doit se voir tout à droite, pas
     * nulle part.
     */
    fun fraction(valeur: Double): Float =
        ((valeur - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()

    /** La valeur sort-elle de l'échelle affichée ? Le curseur bute alors au bord. */
    fun horsEchelle(valeur: Double): Boolean = valeur < min || valeur > max

    /**
     * La valeur est-elle dans la zone habituelle ? `null` quand l'échelle n'en
     * porte pas : ne rien savoir n'est pas « dans la norme ».
     */
    fun habituelle(valeur: Double): Boolean? {
        val bas = habituelBas ?: return null
        val haut = habituelHaut ?: return null
        return valeur in bas..haut
    }
}

/** Les deux bouts d'un arc de cadran : 240° ouverts vers le bas, comme un manomètre. */
private const val ARC_DEPART = 150f
private const val ARC_ETENDUE = 240f

/**
 * Un cadran : l'arc de l'échelle, la valeur au centre en gros chiffres, l'unité
 * en haut à droite et une légende dessous — la température de saturation pour
 * une pression.
 *
 * Il se **touche** quand [onClick] est donné : l'arc n'est pas une saisie, il
 * renvoie à celle-ci.
 */
@Composable
fun Cadran(
    libelle: String,
    valeur: Double?,
    unite: String,
    plage: PlageMesure,
    modifier: Modifier = Modifier,
    legende: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val piste = MaterialTheme.colorScheme.outline
    val aiguille = MaterialTheme.colorScheme.onSurface
    Carte(modifier = modifier, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = libelle,
                style = StyleSection,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = unite,
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val epaisseur = 7.dp.toPx()
                // Un arc de 240° ouvert vers le bas tient en trois quarts de son
                // diamètre de haut : c'est ce qui borne le cadran dans sa boîte.
                val diametre = minOf(size.width, size.height / 0.75f) - epaisseur
                val coin = Offset(
                    (size.width - diametre) / 2f,
                    epaisseur / 2f + 2.dp.toPx(),
                )
                val boite = Size(diametre, diametre)
                val trait = Stroke(width = epaisseur, cap = StrokeCap.Round)
                drawArc(piste, ARC_DEPART, ARC_ETENDUE, false, coin, boite, style = trait)
                if (valeur != null) {
                    val balayage = ARC_ETENDUE * plage.fraction(valeur)
                    if (balayage > 0f) {
                        drawArc(aiguille, ARC_DEPART, balayage, false, coin, boite, style = trait)
                    }
                }
            }
            Text(
                text = valeur?.let { Nombres.enTexte(it, decimales = 1) } ?: "—",
                style = StyleChiffre.copy(fontSize = 34.sp, lineHeight = 40.sp),
                color = if (valeur == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (legende != null) {
            Text(
                text = legende,
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Une réglette de plage : la barre de l'échelle, la zone habituelle marquée, un
 * curseur à la valeur, et la valeur en gros à droite.
 *
 * La valeur passe à l'ambre **hors de la zone habituelle**, et seulement là : la
 * couleur n'apparaît que lorsqu'elle porte une information.
 */
@Composable
fun Reglette(
    libelle: String,
    valeur: Double?,
    unite: String,
    plage: PlageMesure,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val horsPlage = valeur != null && plage.habituelle(valeur) == false
    val teinte = when {
        valeur == null -> MaterialTheme.colorScheme.onSurfaceVariant
        horsPlage -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val piste = MaterialTheme.colorScheme.surfaceContainerHighest
    val zone = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Carte(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = libelle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valeur?.let { Nombres.enTexte(it, decimales = 1) } ?: "—",
                style = StyleChiffre.copy(fontSize = 30.sp, lineHeight = 36.sp),
                color = teinte,
            )
            Text(
                text = " $unite",
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
        ) {
            val hauteurBarre = 6.dp.toPx()
            val haut = (size.height - hauteurBarre) / 2f
            val rayon = CornerRadius(hauteurBarre / 2f)
            drawRoundRect(
                color = piste,
                topLeft = Offset(0f, haut),
                size = Size(size.width, hauteurBarre),
                cornerRadius = rayon,
            )
            val bas = plage.habituelBas
            val hautZone = plage.habituelHaut
            if (bas != null && hautZone != null) {
                val x0 = size.width * plage.fraction(bas)
                val x1 = size.width * plage.fraction(hautZone)
                drawRoundRect(
                    color = zone,
                    topLeft = Offset(x0, haut),
                    size = Size(x1 - x0, hauteurBarre),
                    cornerRadius = rayon,
                )
            }
            if (valeur != null) {
                val x = size.width * plage.fraction(valeur)
                val largeur = 3.dp.toPx()
                drawRoundRect(
                    color = teinte,
                    topLeft = Offset((x - largeur / 2f).coerceIn(0f, size.width - largeur), 0f),
                    size = Size(largeur, size.height),
                    cornerRadius = CornerRadius(largeur / 2f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = Nombres.enTexte(plage.min, decimales = 1),
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (plage.habituelBas != null && plage.habituelHaut != null) {
                Text(
                    text = "Plage habituelle " +
                        Nombres.enTexte(plage.habituelBas, decimales = 1) + "–" +
                        Nombres.enTexte(plage.habituelHaut, decimales = 1) + " $unite",
                    style = StyleChiffrePetit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Nombres.enTexte(plage.max, decimales = 1) + " $unite",
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
