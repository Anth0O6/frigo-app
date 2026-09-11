package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.SensFluide
import com.frigopro.app.data.enDuree
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.theme.NuitPointille
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.time.Duration

/**
 * Le compte-rendu : ce qu'on remet au client.
 *
 * Il ne se saisit pas, il se **récapitule** : durée, fluide, pièces et photos
 * viennent des volets précédents, et le technicien n'a plus qu'à décrire les
 * travaux et faire signer. Redemander ici ce qui a déjà été saisi serait la
 * meilleure façon d'obtenir deux versions différentes de la même intervention.
 */
@Composable
fun OngletRapport(
    etat: EtatIntervention,
    ecoule: Duration,
    actions: ActionsIntervention,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (etat.intervention.numero.isNotEmpty()) {
            Text(
                text = etat.intervention.numero,
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Carte(relief = true) {
            LigneRecap("Client", etat.intervention.client)
            val machine = listOf(etat.intervention.equipementNom, etat.equipement?.designation.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (machine.isNotEmpty()) LigneRecap("Machine", machine)
            LigneRecap("Durée", ecoule.enDuree(), chiffre = true)
            if (etat.ajoute > 0.0) {
                LigneRecap(
                    "Fluide ajouté",
                    "${Nombres.enMasse(etat.ajoute)} kg ${etat.fluide}",
                    chiffre = true,
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
            if (etat.recupere > 0.0) {
                LigneRecap(
                    "Fluide récupéré",
                    "${Nombres.enMasse(etat.recupere)} kg ${etat.fluide}",
                    chiffre = true,
                )
            }
        }

        Section(intitule = "Travaux réalisés") {
            ChampTexte(
                libelle = "Ce qui a été fait",
                valeur = etat.intervention.notes,
                onValeur = actions.onTravaux,
                lignes = 4,
            )
        }

        if (etat.pieces.isNotEmpty()) {
            Section(intitule = "Pièces posées") {
                Carte(contour = true) {
                    etat.pieces.forEach { piece ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = piece.designation,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "×${Nombres.enTexte(piece.quantite)}",
                                style = StyleChiffrePetit,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }

        if (etat.photos.isNotEmpty()) {
            Section(intitule = "Photos jointes") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    etat.photos.take(4).forEach { photo ->
                        VignettePhoto(
                            photo = photo,
                            chargerPhoto = chargerPhoto,
                            onClick = { actions.onAgrandir(photo) },
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp),
                            coteMax = 256,
                        )
                    }
                    repeat(4 - etat.photos.take(4).size) {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        SectionSignature(etat = etat, actions = actions, chargerPhoto = chargerPhoto)

        if (etat.intervention.numero.isEmpty()) {
            Encart(
                texte = "Le compte-rendu recevra sa référence à la clôture de l'intervention.",
            )
        }
        EspaceVertical(24)
    }
}

@Composable
private fun LigneRecap(
    intitule: String,
    valeur: String,
    chiffre: Boolean = false,
    accent: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = intitule,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valeur,
            style = if (chiffre) StyleChiffrePetit else MaterialTheme.typography.titleSmall,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * La signature du client.
 *
 * Une fois signée, elle ne se retouche pas : on l'efface et on refait signer.
 * Une signature modifiable ne vaudrait rien, et l'horodatage qui
 * l'accompagne ne voudrait plus rien dire.
 */
@Composable
private fun SectionSignature(
    etat: EtatIntervention,
    actions: ActionsIntervention,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
) {
    Section(intitule = "Signature client") {
        val fichier = etat.intervention.signatureFichier
        if (fichier != null) {
            Carte(relief = true) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.White,
                ) {
                    PhotoChargee(
                        fichier = fichier,
                        coteMax = 1024,
                        charger = chargerPhoto,
                        contentDescription = "Signature du client",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Signé le ${heureLocale(etat.intervention.signeeLe)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BoutonContour(
                        texte = "Refaire signer",
                        onClick = actions.onEffacerSignature,
                        couleur = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NuitPointille),
                onClick = actions.onSigner,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Signer avec le doigt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Le tracé d'une signature.
 *
 * Le trait est mémorisé en **traits séparés** et non en un seul chemin : lever
 * le doigt entre deux lettres ne doit pas les relier par une barre. Chaque
 * `détectDragGestures` ouvre donc un nouveau trait.
 *
 * La conversion en image est laissée à l'appelant, qui seul sait où la ranger.
 */
@Composable
fun PanneauSignature(
    modifier: Modifier = Modifier,
    encre: Color = Color.Black,
    onTrace: (List<List<Offset>>) -> Unit = {},
) {
    val traits = remember { mutableStateListOf<List<Offset>>() }
    var enCours by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier = modifier
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { depart -> enCours = listOf(depart) },
                    onDrag = { changement, _ ->
                        changement.consume()
                        enCours = enCours + changement.position
                    },
                    onDragEnd = {
                        if (enCours.size > 1) traits.add(enCours)
                        enCours = emptyList()
                        onTrace(traits.toList())
                    },
                )
            },
    ) {
        (traits + listOf(enCours)).forEach { trait ->
            if (trait.size < 2) return@forEach
            val chemin = Path().apply {
                moveTo(trait.first().x, trait.first().y)
                trait.drop(1).forEach { point -> lineTo(point.x, point.y) }
            }
            drawPath(
                path = chemin,
                color = encre,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
