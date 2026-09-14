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
import com.frigopro.app.data.RentabiliteIntervention
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
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

        // Facturer se propose dès que l'intervention est terminée, et pas
        // avant : facturer un travail en cours reviendrait à demander de l'argent
        // pour quelque chose qui n'est pas fini. Le brouillon reste modifiable —
        // c'est l'émission qui fige, pas ce bouton.
        if (etat.intervention.statut.close) {
            BoutonPlein(
                texte = "Facturer cette intervention",
                onClick = actions.onFacturer,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionRentabilite(rentabilite = etat.rentabilite)
        EspaceVertical(24)
    }
}

/**
 * Ce que l'intervention a coûté, et ce qu'elle rapporte.
 *
 * ## En bas de la fiche, et c'est voulu
 *
 * Ce n'est pas une information de terrain : elle ne sert ni à trouver le client,
 * ni à savoir quoi vérifier. Elle se lit **après coup**, souvent le soir, et la
 * mettre en haut l'aurait fait passer devant l'adresse et la checklist — qui
 * sont, elles, ce pour quoi on ouvre cet écran.
 *
 * ## Ce que l'encart dit, et ce qu'il refuse de dire
 *
 * Une marge sur **coûts directs** : le temps, les pièces, le fluide. Ni le
 * camion, ni l'assurance, ni les heures de bureau — le dire est le point, parce
 * qu'un technicien qui lirait « 256 € » en croyant que c'est ce qui lui reste
 * facturerait trop bas l'année suivante.
 *
 * Sans coût horaire renseigné, l'écran **réclame le réglage** au lieu d'afficher
 * une marge égale à la recette : un chiffre juste par accident ne se distingue
 * pas d'un vrai.
 */
@Composable
private fun SectionRentabilite(rentabilite: RentabiliteIntervention) {
    val statuts = LocalStatuts.current

    Section(intitule = "Coût et marge") {
        if (!rentabilite.chiffrable) {
            Encart(
                texte = "Pour connaître la marge de cette intervention, renseignez le coût " +
                    "horaire interne dans les Réglages, et le prix d'achat des pièces " +
                    "posées. Sans eux la marge vaudrait exactement la recette, ce qui " +
                    "serait flatteur et faux.",
            )
            return@Section
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CarteInfo(
                intitule = "Coût direct",
                valeur = Nombres.enEuros(rentabilite.coutDirect),
                modifier = Modifier.weight(1f),
            )
            CarteInfo(
                intitule = "Facturé HT",
                valeur = rentabilite.recetteHt?.let { Nombres.enEuros(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        val marge = rentabilite.marge
        if (marge == null) {
            Encart(
                texte = "Pas encore facturée : il n'y a donc pas de marge à calculer. " +
                    "Zéro se lirait « ça n'a rien rapporté », ce qui serait faux.",
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TuileChiffre(
                    valeur = Nombres.enEuros(marge),
                    libelle = "marge directe",
                    modifier = Modifier.weight(1f),
                    couleur = if (rentabilite.aPerte) statuts.urgence else statuts.termine,
                )
                rentabilite.tauxDeMarque?.let {
                    TuileChiffre(
                        valeur = "${Nombres.enTexte(it)} %",
                        libelle = "taux de marque",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            text = "Main-d'œuvre ${Nombres.enEuros(rentabilite.coutMainDoeuvre)} · " +
                "pièces ${Nombres.enEuros(rentabilite.coutPieces)} · " +
                "fluide ${Nombres.enEuros(rentabilite.coutFluide)}. " +
                "Le véhicule, l'assurance et les heures de bureau n'y sont pas : " +
                "c'est une marge sur coûts directs, pas un résultat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
