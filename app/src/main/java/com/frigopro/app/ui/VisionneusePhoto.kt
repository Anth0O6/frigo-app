package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frigopro.app.data.Photo
import com.frigopro.app.data.ReductionPhoto

/**
 * Une photo en grand, sur fond noir, avec de quoi la supprimer.
 *
 * Plein écran et non dans une carte : on ouvre cette vue pour lire une plaque,
 * et chaque pixel compte. La suppression demande confirmation — une plaque
 * photographiée sur un toit ne se reprend pas d'un geste, et un appui malheureux
 * est vite arrivé avec des gants.
 */
@Composable
fun VisionneusePhoto(
    photo: Photo,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    onSupprimer: (Photo) -> Unit,
    onFermer: () -> Unit,
) {
    var confirmation by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onFermer,
        // Sans cela, la boîte garde la largeur d'un dialogue ordinaire et
        // l'image reste minuscule au milieu de l'écran.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            PhotoChargee(
                fichier = photo.fichier,
                coteMax = ReductionPhoto.COTE_MAX,
                charger = chargerPhoto,
                contentDescription = photo.categorie.libelle,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    // La visionneuse est une fenêtre à part : la marge posée
                    // par la coquille ne l'atteint pas, et sans celle-ci les
                    // deux boutons passeraient sous la barre d'état.
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(8.dp),
            ) {
                IconButton(
                    onClick = onFermer,
                    modifier = Modifier.align(Alignment.TopStart),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Fermer")
                }
                IconButton(
                    onClick = { confirmation = true },
                    modifier = Modifier.align(Alignment.TopEnd),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Supprimer cette photo",
                    )
                }
            }
        }
    }

    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text(text = "Supprimer cette photo ?") },
            text = { Text(text = "Elle sera définitivement effacée du téléphone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = false
                        onSupprimer(photo)
                    },
                ) {
                    Text(text = "Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = false }) { Text(text = "Annuler") }
            },
        )
    }
}
