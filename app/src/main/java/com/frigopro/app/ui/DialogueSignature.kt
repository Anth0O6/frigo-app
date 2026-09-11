package com.frigopro.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas as CanvasAndroid
import android.graphics.Paint
import android.graphics.Path as PathAndroid
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import kotlin.math.roundToInt

/**
 * La boîte où le client signe.
 *
 * Plein écran, et non une boîte de dialogue ordinaire : on tend le téléphone à
 * quelqu'un d'autre, qui signe du doigt sans savoir où il a le droit d'appuyer.
 * Une zone de signature de la taille d'un timbre produit une signature qui ne
 * ressemble à rien.
 */
@Composable
fun DialogueSignature(
    onValider: (Bitmap) -> Unit,
    onFermer: () -> Unit,
) {
    var traces by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var generation by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onFermer,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Fenêtre à part, elle aussi : voir `VisionneusePhoto`.
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "Signature du client", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "En signant, le client reconnaît l'intervention réalisée.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(RAPPORT_SIGNATURE),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.White,
                ) {
                    // `generation` force un panneau neuf à chaque effacement :
                    // l'état du tracé vit dans le panneau, et seule sa
                    // recréation le remet à zéro.
                    key(generation) {
                        PanneauSignature(
                            modifier = Modifier.fillMaxWidth(),
                            onTrace = { traces = it },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoutonContour(
                        texte = "Effacer",
                        onClick = {
                            traces = emptyList()
                            generation += 1
                        },
                        modifier = Modifier.weight(1f),
                        couleur = MaterialTheme.colorScheme.secondary,
                    )
                    BoutonPlein(
                        texte = "Valider",
                        onClick = {
                            onValider(
                                rasteriser(
                                    traces,
                                    LARGEUR_SIGNATURE,
                                    (LARGEUR_SIGNATURE / RAPPORT_SIGNATURE).roundToInt(),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1.4f),
                        actif = traces.isNotEmpty(),
                    )
                }
                BoutonContour(
                    texte = "Annuler",
                    onClick = onFermer,
                    modifier = Modifier.fillMaxWidth(),
                    couleur = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Largeur de l'image produite, nettement supérieure à celle du panneau :
 * une signature réimprimée sur un compte-rendu à la taille du téléphone
 * serait floue.
 */
private const val LARGEUR_SIGNATURE = 1024

/** Proportions du panneau et de l'image, qui doivent coïncider. */
private const val RAPPORT_SIGNATURE = 1.6f

/**
 * Transforme les tracés en image.
 *
 * Les coordonnées viennent du panneau à sa taille d'écran ; elles sont mises à
 * l'échelle de l'image produite, qui est **plus grande** — une signature
 * réimprimée sur un compte-rendu à la taille du téléphone serait floue.
 *
 * Le fond est blanc et non transparent : la signature se relit sur un
 * compte-rendu clair comme sur un écran sombre, et une image transparente
 * deviendrait invisible sur l'un des deux.
 */
private fun rasteriser(traces: List<List<Offset>>, largeur: Int, hauteur: Int): Bitmap {
    val image = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
    val toile = CanvasAndroid(image)
    toile.drawColor(android.graphics.Color.WHITE)

    val points = traces.flatten()
    if (points.isEmpty()) return image

    // Les tracés sont mis à l'échelle d'après l'étendue réellement dessinée,
    // ce qui recadre au passage : une signature tracée dans un coin du
    // panneau occupe toute l'image plutôt que d'y flotter.
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val etendueX = (maxX - minX).coerceAtLeast(1f)
    val etendueY = (maxY - minY).coerceAtLeast(1f)
    val marge = largeur * 0.06f
    val echelle = minOf(
        (largeur - 2 * marge) / etendueX,
        (hauteur - 2 * marge) / etendueY,
    )

    val pinceau = Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = largeur * 0.006f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    traces.forEach { trace ->
        if (trace.size < 2) return@forEach
        val chemin = PathAndroid()
        trace.forEachIndexed { index, point ->
            val x = marge + (point.x - minX) * echelle
            val y = marge + (point.y - minY) * echelle
            if (index == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
        }
        toile.drawPath(chemin, pinceau)
    }
    return image
}
