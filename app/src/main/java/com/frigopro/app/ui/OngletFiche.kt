package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.PointChecklist
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * Le premier volet d'une intervention : ce qu'on lit en arrivant.
 *
 * Il rassemble ce qui répond aux questions de l'arrivée sur site — quel
 * créneau, quelle adresse, quelle machine, qui s'en occupe, et qu'est-ce qu'on
 * a à vérifier — avant que le manomètre ne sorte de la caisse. C'est aussi le
 * seul volet dont on a besoin quand on n'est pas encore descendu du camion.
 *
 * La checklist y vit plutôt que dans le compte-rendu parce qu'elle se coche
 * **pendant** le travail et non à la fin : trois de ses quatre points par défaut
 * sont des obligations, et une liste qu'on découvre au moment de signer ne sert
 * plus à rien.
 */
@Composable
fun OngletFiche(
    etat: EtatIntervention,
    actions: ActionsIntervention,
    modifier: Modifier = Modifier,
) {
    val contexte = LocalContext.current
    val intervention = etat.intervention
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CarteInfo(
                intitule = "Créneau",
                valeur = creneauDe(intervention),
                modifier = Modifier.weight(1f),
            )
            CarteInfo(
                intitule = "Technicien",
                valeur = intervention.technicienNom.ifBlank {
                    etat.parametres.technicien.ifBlank { "—" }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // L'adresse et les deux gestes du terrain. Ils ne s'affichent que
        // lorsqu'ils mènent quelque part : un bouton « Appeler » sur un client
        // sans numéro est un bouton qui trahit.
        val client = etat.client
        if (client != null && (client.localisable || client.appelable)) {
            Carte {
                if (client.adresseComplete.isNotBlank()) {
                    Text(
                        text = "ADRESSE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = client.adresseComplete, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (client.localisable) {
                        BoutonContour(
                            texte = "Y aller",
                            onClick = { contexte.ouvrirItineraire(client.adresseComplete) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (client.appelable) {
                        BoutonContour(
                            texte = "Appeler",
                            onClick = { contexte.appeler(client.telephone) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        val machine = etat.equipement
        if (machine != null || intervention.equipementNom.isNotBlank()) {
            Carte(contour = true) {
                Text(
                    text = "ÉQUIPEMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = intervention.equipementNom.ifBlank { machine?.nom.orEmpty() },
                    style = MaterialTheme.typography.titleMedium,
                )
                val plaque = listOfNotNull(
                    machine?.designation?.takeIf { it.isNotBlank() },
                    machine?.fluide?.takeIf { it.isNotBlank() },
                    machine?.chargeKg?.let { "${Nombres.enTexte(it)} kg" },
                ).joinToString(" · ")
                if (plaque.isNotEmpty()) {
                    Text(
                        text = plaque,
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        if (intervention.notes.isNotBlank()) {
            Carte {
                Text(
                    text = "CE QU'ON SAIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = intervention.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (etat.checklist.isNotEmpty()) {
            Section(
                intitule = "Checklist · ${etat.pointsFaits}/${etat.checklist.size}",
                espacement = 8.dp,
                couleurIntitule = if (etat.checklistFinie) {
                    LocalStatuts.current.termine
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                etat.checklist.forEach { point ->
                    LignePoint(point = point, onBasculer = { actions.onBasculerPoint(point) })
                }
            }
        }

        // Chiffrer depuis l'intervention : c'est le moment où l'on constate
        // qu'une réparation dépasse le dépannage, et attendre le bureau fait
        // perdre l'acceptation du jour même.
        BoutonContour(
            texte = "Créer un devis pour ce client",
            onClick = actions.onCreerDevis,
            modifier = Modifier.fillMaxWidth(),
        )
        EspaceVertical(24)
    }
}

/** Une tuile d'information : son intitulé, et sa valeur. */
@Composable
private fun CarteInfo(intitule: String, valeur: String, modifier: Modifier = Modifier) {
    Carte(modifier = modifier, forme = MaterialTheme.shapes.medium) {
        Text(
            text = intitule.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valeur,
            style = StyleChiffrePetit,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Un point de la checklist.
 *
 * Toute la ligne est cliquable, et non la seule case : on coche avec des gants,
 * souvent d'une main, et viser un carré de vingt pixels sur un toit est la
 * meilleure façon de renoncer à cocher.
 */
@Composable
private fun LignePoint(point: PointChecklist, onBasculer: () -> Unit) {
    val vert = LocalStatuts.current.termine
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onBasculer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(LocalCibles.current.case),
                shape = MaterialTheme.shapes.small,
                color = if (point.fait) vert else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (point.fait) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                text = point.libelle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (point.fait) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
