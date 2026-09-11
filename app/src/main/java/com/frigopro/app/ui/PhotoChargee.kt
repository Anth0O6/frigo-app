package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** Ce qu'une image peut être pendant qu'on la regarde. */
private sealed interface EtatImage {

    data object Chargement : EtatImage

    /** Le fichier manque : une sauvegarde restaurée à moitié, un ménage malheureux. */
    data object Absente : EtatImage

    data class Prete(val image: ImageBitmap) : EtatImage
}

/**
 * Une photo du stockage interne, décodée à la taille demandée.
 *
 * Pas de bibliothèque de chargement d'images : les fichiers sont locaux, peu
 * nombreux, et déjà réduits à l'entrée (voir `ReductionPhoto`). Ce qu'une
 * bibliothèque apporterait — cache réseau, transformations, préchargement — ne
 * servirait à rien ici, et sa taille se paierait dans l'APK.
 *
 * [coteMax] est demandé plutôt que déduit : une vignette de liste et une photo
 * plein écran ne doivent pas coûter la même mémoire.
 */
@Composable
fun PhotoChargee(
    fichier: String,
    coteMax: Int,
    charger: suspend (String, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val etat by produceState<EtatImage>(EtatImage.Chargement, fichier, coteMax) {
        val image = charger(fichier, coteMax)
        value = if (image == null) EtatImage.Absente else EtatImage.Prete(image.asImageBitmap())
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val courant = etat) {
            EtatImage.Chargement -> CircularProgressIndicator(modifier = Modifier.size(24.dp))

            EtatImage.Absente -> Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = "Photo introuvable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is EtatImage.Prete -> Image(
                bitmap = courant.image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
