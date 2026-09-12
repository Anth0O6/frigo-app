package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.CategoriePhoto
import com.frigopro.app.data.PiecePosee
import com.frigopro.app.data.Photo
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.theme.AValider
import com.frigopro.app.ui.theme.Termine
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * Le volet des pièces posées.
 *
 * La référence est séparée de la désignation parce qu'elles ne servent pas au
 * même moment : la désignation se lit sur le compte-rendu du client, la
 * référence se recopie dans une commande.
 */
@Composable
fun OngletPieces(
    etat: EtatIntervention,
    actions: ActionsIntervention,
    modifier: Modifier = Modifier,
) {
    var saisieOuverte by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (etat.pieces.isEmpty()) {
            Encart(texte = "Aucune pièce posée. Celles que vous ajoutez ici se reportent sur le compte-rendu.")
        }
        etat.pieces.forEach { piece ->
            LignePiece(piece = piece, onSupprimer = actions.onSupprimerPiece)
        }
        BoutonContour(
            texte = "+ Ajouter une pièce",
            onClick = { saisieOuverte = true },
            modifier = Modifier.fillMaxWidth(),
        )
        EspaceVertical(24)
    }

    if (saisieOuverte) {
        DialoguePiece(
            onValider = { designation, reference, quantite ->
                actions.onAjouterPiece(designation, reference, quantite)
                saisieOuverte = false
            },
            onFermer = { saisieOuverte = false },
        )
    }
}

@Composable
private fun LignePiece(piece: PiecePosee, onSupprimer: (String) -> Unit) {
    Carte(contour = true, onClick = { onSupprimer(piece.id) }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = piece.designation, style = MaterialTheme.typography.titleSmall)
                if (piece.reference.isNotBlank()) {
                    Text(
                        text = piece.reference,
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "×${Nombres.enTexte(piece.quantite)}",
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun DialoguePiece(
    onValider: (String, String, Double) -> Unit,
    onFermer: () -> Unit,
) {
    var designation by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var quantite by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Pièce posée") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChampTexte(libelle = "Désignation", valeur = designation, onValeur = { designation = it })
                ChampTexte(libelle = "Référence", valeur = reference, onValeur = { reference = it })
                ChampTexte(
                    libelle = "Quantité",
                    valeur = quantite,
                    onValeur = { quantite = it },
                    clavier = KeyboardType.Decimal,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValider(designation, reference, Nombres.versDecimal(quantite) ?: 1.0)
                },
                enabled = designation.isNotBlank(),
            ) {
                Text(text = "Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * Le volet des photos avant / après.
 *
 * Les deux catégories sont séparées et colorées comme partout ailleurs —
 * l'ambre pour l'état constaté, le cyan pour l'état rendu. C'est la paire qui
 * vaut preuve auprès du client, et la séparer visuellement évite la question
 * « laquelle est laquelle » six mois plus tard.
 */
@Composable
fun OngletPhotos(
    etat: EtatIntervention,
    actions: ActionsIntervention,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        GrillePhotos(
            intitule = "Avant",
            accent = AValider,
            photos = etat.photosAvant,
            categorie = CategoriePhoto.AVANT,
            actions = actions,
            chargerPhoto = chargerPhoto,
        )
        GrillePhotos(
            intitule = "Après",
            accent = Termine,
            photos = etat.photosApres,
            categorie = CategoriePhoto.APRES,
            actions = actions,
            chargerPhoto = chargerPhoto,
        )
        Encart(
            texte = "Chaque photo est horodatée et rattachée à l'intervention. " +
                "Les photos durables de la machine — plaque, emplacement — vivent sur sa fiche.",
        )
        EspaceVertical(24)
    }
}

/**
 * Une catégorie de photos : deux colonnes, et les deux façons d'en ajouter.
 *
 * Deux colonnes et non trois : une vignette doit rester assez grande pour
 * qu'on reconnaisse un évaporateur givré sans l'ouvrir.
 */
@Composable
private fun GrillePhotos(
    intitule: String,
    accent: androidx.compose.ui.graphics.Color,
    photos: List<Photo>,
    categorie: CategoriePhoto,
    actions: ActionsIntervention,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
) {
    Section(intitule = intitule, couleurIntitule = accent) {
        photos.chunked(2).forEach { paire ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                paire.forEach { photo ->
                    VignettePhoto(
                        photo = photo,
                        chargerPhoto = chargerPhoto,
                        onClick = { actions.onAgrandir(photo) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
                // Une seule photo sur la rangée : garder la moitié libre pour
                // que la vignette ne s'étire pas sur toute la largeur.
                if (paire.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoutonContour(
                texte = "Photographier",
                onClick = { actions.onPhotographier(categorie) },
                modifier = Modifier.weight(1f),
                couleur = accent,
            )
            BoutonContour(
                texte = "Choisir",
                onClick = { actions.onChoisirImage(categorie) },
                modifier = Modifier.weight(1f),
                couleur = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** Une vignette, qui décode son image à la taille où elle s'affiche. */
@Composable
internal fun VignettePhoto(
    photo: Photo,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coteMax: Int = 512,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        PhotoChargee(
            fichier = photo.fichier,
            coteMax = coteMax,
            charger = chargerPhoto,
            contentDescription = photo.legende.ifBlank { photo.categorie.libelle },
        )
    }
}
