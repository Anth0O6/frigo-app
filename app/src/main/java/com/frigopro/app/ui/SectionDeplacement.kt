package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.CalculDeplacement
import com.frigopro.app.data.OrigineTrajet
import com.frigopro.app.data.RaisonEchec
import com.frigopro.app.data.TarifDeplacement
import com.frigopro.app.data.Trajet
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.ChampChiffre
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.EspaceVertical
import com.frigopro.app.ui.composants.Section
import java.time.ZoneId

/**
 * Le déplacement d'un devis, tel que l'écran le connaît.
 *
 * Un objet plutôt que six paramètres de plus : [EcranDevis] en portait déjà
 * dix-huit, et c'est le motif que suit déjà `ActionsIntervention`.
 */
data class EtatDeplacement(
    val trajet: Trajet?,
    val tarif: TarifDeplacement = TarifDeplacement(),
    val calculEnCours: Boolean = false,
    val echec: RaisonEchec? = null,
    /** Le calcul automatique est possible : une clé est saisie dans les Réglages. */
    val itineraireDisponible: Boolean = false,
)

/** Ce qu'on peut faire d'un déplacement. */
data class ActionsDeplacement(
    val trajetPropose: () -> Trajet = { Trajet(devisId = "") },
    val onEnregistrer: (Trajet) -> Unit = {},
    val onCalculer: (Trajet) -> Unit = {},
    val onOffrir: (Boolean) -> Unit = {},
    val onRetirer: () -> Unit = {},
    val onOublierEchec: () -> Unit = {},
)

/**
 * La section du déplacement, dans la feuille d'un devis.
 *
 * Elle tient la **saisie en cours** en état local — les deux adresses, les
 * kilomètres, la durée, le péage — et n'écrit en base que sur un geste explicite.
 * C'est délibéré : le trajet enregistré refait les lignes du devis, et sauver à
 * chaque caractère aurait réécrit trois lignes de facture à chaque frappe.
 *
 * Le tarif n'est pas modifiable ici : il se règle une fois dans les Réglages, et
 * le proposer sur chaque devis aurait invité à facturer chaque client
 * différemment sans s'en souvenir.
 */
@Composable
fun SectionDeplacement(
    etat: EtatDeplacement,
    actions: ActionsDeplacement,
    modifier: Modifier = Modifier,
) {
    val existant = etat.trajet
    // La saisie part du trajet enregistré, ou de la proposition — le dépôt et
    // l'adresse du client. `remember(existant)` la reprend quand la base répond,
    // ce qui est précisément ce qu'on veut après un calcul.
    var saisie by remember(existant) { mutableStateOf(existant ?: actions.trajetPropose()) }

    Section(intitule = "Déplacement", modifier = modifier) {
        if (!etat.tarif.renseigne) {
            Encart(
                texte = "Aucun tarif de déplacement n'est renseigné : le prix au " +
                    "kilomètre et le prix d'une heure de trajet se saisissent dans " +
                    "les Réglages. Sans eux, un déplacement se facturerait à zéro.",
            )
        }

        ChampTexte(
            libelle = "Départ",
            valeur = saisie.depart,
            onValeur = {
                saisie = saisie.copy(depart = it)
                actions.onOublierEchec()
            },
        )
        ChampTexte(
            libelle = "Arrivée",
            valeur = saisie.arrivee,
            onValeur = {
                saisie = saisie.copy(arrivee = it)
                actions.onOublierEchec()
            },
        )
        if (etat.itineraireDisponible) {
            BoutonPlein(
                texte = if (etat.calculEnCours) "Calcul en cours…" else "Calculer l'itinéraire",
                onClick = { actions.onCalculer(saisie) },
                modifier = Modifier.fillMaxWidth(),
                actif = !etat.calculEnCours && saisie.depart.isNotBlank() &&
                    saisie.arrivee.isNotBlank(),
            )
            if (etat.calculEnCours) {
                EspaceVertical(8)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    Text(
                        text = "Interrogation du service d'itinéraire.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Encart(
                texte = "Le calcul automatique demande une clé d'itinéraire, à saisir " +
                    "dans les Réglages. Sans elle, les kilomètres et le temps se " +
                    "saisissent à la main — ce qui marche aussi sans réseau.",
            )
        }

        etat.echec?.let { raison ->
            EspaceVertical(8)
            Encart(texte = raison.message)
        }
        // Les trois chiffres restent saisissables même après un calcul : c'est le
        // compteur du véhicule qui fait foi devant un client, pas une estimation.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChampChiffre(
                libelle = "Distance",
                valeur = saisie.distanceKm.takeIf { it > 0.0 },
                unite = "km",
                onValeur = { saisie = saisie.copy(distanceKm = it ?: 0.0) },
                modifier = Modifier.weight(1f),
            )
            ChampChiffre(
                libelle = "Durée",
                valeur = saisie.dureeMinutes.takeIf { it > 0 }?.toDouble(),
                unite = "min",
                onValeur = { saisie = saisie.copy(dureeMinutes = it?.toInt() ?: 0) },
                modifier = Modifier.weight(1f),
            )
            ChampChiffre(
                libelle = "Péages",
                valeur = saisie.peages.takeIf { it > 0.0 },
                unite = "€",
                onValeur = {
                    // Saisir un péage à la main, c'est s'être prononcé dessus : le
                    // marquer connu évite l'avertissement sur un chiffre qu'on vient
                    // d'écrire soi-même.
                    saisie = saisie.copy(peages = it ?: 0.0, peagesConnus = true)
                },
                modifier = Modifier.weight(1f),
            )
        }
        BasculeLigne(
            titre = "Aller-retour",
            detail = "Double la distance, le temps et les péages.",
            actif = saisie.allerRetour,
            onChange = { saisie = saisie.copy(allerRetour = it) },
        )

        if (saisie.origine == OrigineTrajet.CALCULE && !saisie.peagesConnus) {
            EspaceVertical(8)
            Encart(
                texte = "Le service ne s'est pas prononcé sur les péages de ce trajet. " +
                    "Un zéro ne veut pas dire qu'il n'y en a pas : vérifiez, et " +
                    "saisissez-les au besoin.",
            )
        }

        ApercuDeplacement(trajet = saisie, tarif = etat.tarif)

        BoutonPlein(
            texte = if (existant == null) "Ajouter au devis" else "Mettre à jour le devis",
            onClick = { actions.onEnregistrer(saisie) },
            modifier = Modifier.fillMaxWidth(),
            actif = saisie.renseigne,
        )

        if (existant != null) {
            BasculeLigne(
                titre = "Offrir le déplacement",
                detail = "Le montant reste affiché, barré : un geste qu'on ne voit pas " +
                    "n'est pas un argument de vente.",
                actif = existant.offert,
                onChange = actions.onOffrir,
            )
            BoutonContour(
                texte = "Retirer le déplacement",
                onClick = actions.onRetirer,
                modifier = Modifier.fillMaxWidth(),
                couleur = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Ce que le déplacement coûtera, poste par poste.
 *
 * Le détail et non le seul total, pour la raison dite dans
 * [com.frigopro.app.data.MontantDeplacement] : « 24 km à 0,45 € » se discute
 * avec un client, « 10,80 € » ne se discute pas. C'est un aperçu de ce que
 * l'enregistrement produira, et non ce qui est enregistré — il se recalcule à
 * chaque frappe, avant tout geste.
 */
@Composable
private fun ApercuDeplacement(trajet: Trajet, tarif: TarifDeplacement) {
    if (!trajet.renseigne) return
    val calcul = CalculDeplacement.montant(trajet, tarif)

    if (tarif.mode.compteLesKm) {
        LigneApercu(
            libelle = "${Nombres.enTexte(calcul.distanceFacturee)} km" +
                " × ${Nombres.enEuros(tarif.prixKm)}",
            montant = calcul.montantKm,
            barre = trajet.offert,
        )
    }
    if (tarif.mode.compteLeTemps) {
        LigneApercu(
            libelle = "${Nombres.enTexte(calcul.heuresFacturees)} h" +
                " × ${Nombres.enEuros(tarif.prixHeure)}",
            montant = calcul.montantHeure,
            barre = trajet.offert,
        )
    }
    if (calcul.minimumApplique) {
        LigneApercu(
            libelle = "Forfait minimum",
            montant = calcul.montantTrajet,
            barre = trajet.offert,
            appuye = true,
        )
    }
    if (calcul.peages > 0.0) {
        LigneApercu(libelle = "Péages", montant = calcul.peages, barre = trajet.offert)
    }
    LigneApercu(
        libelle = if (trajet.offert) "Offert" else "Total du déplacement",
        montant = if (trajet.offert) calcul.totalAvantGeste else calcul.total,
        barre = trajet.offert,
        appuye = true,
    )

    if (trajet.origine == OrigineTrajet.CALCULE && trajet.calculeLe != null) {
        EspaceVertical(6)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(1.dp),
            )
            Text(
                text = "Calculé le " + libelleDateAvecAnnee(
                    trajet.calculeLe.atZone(ZoneId.systemDefault()).toLocalDate(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LigneApercu(
    libelle: String,
    montant: Double,
    barre: Boolean,
    appuye: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = libelle,
            style = if (appuye) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = if (appuye) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = Nombres.enEuros(montant),
            style = if (appuye) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = if (barre) MaterialTheme.colorScheme.onSurfaceVariant else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (barre) TextDecoration.LineThrough else null,
        )
    }
}

/** Un interrupteur et ce qu'il fait, sur une ligne. */
@Composable
private fun BasculeLigne(
    titre: String,
    detail: String,
    actif: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = titre,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = actif, onCheckedChange = onChange)
    }
    Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
