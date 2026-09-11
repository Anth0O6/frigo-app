package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.CategoriePhoto
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.Photo
import com.frigopro.app.data.ReductionPhoto
import com.frigopro.app.data.Releve
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.ui.theme.FrigoProTheme
import java.time.LocalDate
import java.time.LocalTime

/** Côté d'une vignette : assez grand pour reconnaître une plaque, assez petit pour en voir plusieurs. */
private val COTE_VIGNETTE = 104.dp

/**
 * La fiche d'une machine : ses photos, puis son historique.
 *
 * Les photos d'abord, parce que c'est ce qu'on vient chercher en arrivant sur
 * place — la référence à commander, l'endroit où la trouver. L'historique
 * ensuite, qui répond à la question d'après.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranEquipement(
    equipement: Equipement,
    photos: List<Photo>,
    historique: List<Intervention>,
    releves: List<Releve>,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    onPhotographier: (CategoriePhoto) -> Unit,
    onChoisirImage: (CategoriePhoto) -> Unit,
    onAgrandir: (Photo) -> Unit,
    onRenommer: () -> Unit,
    onModifierFiche: () -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = equipement.nom, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val plaque = listOf(equipement.designation, equipement.numeroSerie)
                            .filter { it.isNotBlank() }
                            .joinToString(" · n° ")
                        if (plaque.isNotEmpty()) {
                            Text(
                                text = plaque,
                                style = com.frigopro.app.ui.theme.StyleChiffrePetit,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFermer) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Revenir au carnet",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRenommer) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Renommer la machine",
                        )
                    }
                    IconButton(onClick = onModifierFiche) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Plaque signalétique et fluide",
                        )
                    }
                    IconButton(onClick = onSupprimer) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Supprimer la machine",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "identite") {
                IdentiteMachine(
                    equipement = equipement,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (releves.isNotEmpty()) {
                item(key = "tendance") {
                    TendanceReleves(
                        releves = releves,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            // Seules les catégories de la machine : « avant » et « après »
            // appartiennent à une intervention et n'ont rien à faire ici.
            CategoriePhoto.deMachine.forEach { categorie ->
                item(key = categorie.name) {
                    SectionPhotos(
                        categorie = categorie,
                        photos = photos.filter { it.categorie == categorie },
                        chargerPhoto = chargerPhoto,
                        onPhotographier = { onPhotographier(categorie) },
                        onChoisirImage = { onChoisirImage(categorie) },
                        onAgrandir = onAgrandir,
                    )
                }
            }
            item(key = "historique") {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                TitreSection(texte = "Historique")
            }
            if (historique.isEmpty()) {
                item(key = "historique-vide") {
                    Text(
                        text = "Aucune intervention n'a encore été rattachée à cette machine.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            } else {
                items(items = historique, key = { it.id }) { intervention ->
                    LigneHistorique(intervention = intervention)
                }
            }
        }
    }
}

/**
 * Une catégorie de photos, et de quoi en ajouter.
 *
 * Les deux catégories sont toujours affichées, vides comprises : c'est ainsi
 * qu'on voit qu'il manque la photo de l'emplacement, ce qu'une liste qui cache
 * ses sections vides ne dirait pas.
 */
@Composable
private fun SectionPhotos(
    categorie: CategoriePhoto,
    photos: List<Photo>,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    onPhotographier: () -> Unit,
    onChoisirImage: () -> Unit,
    onAgrandir: (Photo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TitreSection(texte = categorie.libelle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = photos, key = { it.id }) { photo ->
                PhotoChargee(
                    fichier = photo.fichier,
                    coteMax = ReductionPhoto.COTE_VIGNETTE,
                    charger = chargerPhoto,
                    contentDescription = "${categorie.libelle}, voir en grand",
                    modifier = Modifier
                        .size(COTE_VIGNETTE)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAgrandir(photo) },
                )
            }
            item(key = "ajouter") {
                BoutonAjoutPhoto(
                    categorie = categorie,
                    onPhotographier = onPhotographier,
                    onChoisirImage = onChoisirImage,
                )
            }
        }
    }
}

/**
 * Tuile d'ajout : elle offre l'appareil photo *et* la galerie.
 *
 * L'appareil photo en premier, c'est le geste du terrain ; la galerie sert au
 * retour, pour une photo déjà prise avec l'appareil habituel du téléphone.
 */
@Composable
private fun BoutonAjoutPhoto(
    categorie: CategoriePhoto,
    onPhotographier: () -> Unit,
    onChoisirImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ouvert by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .size(COTE_VIGNETTE)
                .clickable { ouvert = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AddAPhoto,
                    contentDescription = "Ajouter une photo : ${categorie.libelle}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            DropdownMenuItem(
                text = { Text(text = "Photographier") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = null)
                },
                onClick = {
                    ouvert = false
                    onPhotographier()
                },
            )
            DropdownMenuItem(
                text = { Text(text = "Choisir une image") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null)
                },
                onClick = {
                    ouvert = false
                    onChoisirImage()
                },
            )
        }
    }
}

/** Une ligne d'historique : quand, quoi, et où cela en est. */
@Composable
private fun LigneHistorique(intervention: Intervention, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = libelleDateAvecAnnee(intervention.date),
                style = MaterialTheme.typography.titleSmall,
            )
            if (intervention.typeLibelle.isNotBlank()) {
                Text(
                    text = intervention.typeLibelle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = intervention.statut.libelle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun TitreSection(texte: String, modifier: Modifier = Modifier) {
    Text(
        text = texte,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun EcranEquipementPreview() {
    FrigoProTheme {
        Surface {
            EcranEquipement(
                equipement = Equipement(id = "1", clientId = "c1", nom = "Vitrine salle 2"),
                photos = emptyList(),
                historique = listOf(
                    Intervention(
                        id = "i1",
                        date = LocalDate.of(2026, 9, 8),
                        heure = LocalTime.of(9, 30),
                        client = "Boucherie Lemoine",
                        ville = "Rouen",
                        typeLibelle = "Fuite de fluide",
                        statut = StatutIntervention.TERMINEE,
                    ),
                ),
                chargerPhoto = { _, _ -> null },
                onPhotographier = {},
                onChoisirImage = {},
                onAgrandir = {},
                onRenommer = {},
                onSupprimer = {},
                onFermer = {},
                modifier = Modifier.height(600.dp),
            )
        }
    }
}
