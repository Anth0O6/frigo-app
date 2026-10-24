package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.EcheanceMaintenance
import com.frigopro.app.data.GammeMaintenance
import com.frigopro.app.data.ReleveGamme
import com.frigopro.app.data.StatutEcheance
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.LocalStatuts
import java.time.LocalDate

/**
 * Ce que la fiche d'une machine montre de son plan de maintenance.
 *
 * Les trois listes sont passées en un objet plutôt qu'une à une, pour la raison
 * qui a déjà produit `EtatDeplacement` : [EcranEquipement] portait quinze
 * paramètres, et trois de plus plus leurs quatre rappels l'auraient rendu
 * illisible.
 */
data class PlanMachine(
    /** Les gammes que **cette machine** suit, avec leur échéance déjà calculée. */
    val echeances: List<EcheanceMaintenance>,
    /** Toutes les gammes de l'entreprise : c'est là qu'on prend celles à ajouter. */
    val gammes: List<GammeMaintenance>,
    /** Le journal de cette machine, le plus récent d'abord. */
    val visites: List<ReleveGamme>,
)

/** Les quatre gestes du plan d'une machine. */
data class ActionsPlanMachine(
    val onSuivre: (String) -> Unit,
    val onNePlusSuivre: (String) -> Unit,
    /** Ouvre la feuille de visite : consigner demande une date et des remarques. */
    val onConsigner: (EcheanceMaintenance) -> Unit,
    /** Déplace l'entrée au plan : voir [MaintenanceViewModel.onReporterDepart]. */
    val onDepart: (EcheanceMaintenance) -> Unit,
    val onRetirerVisite: (ReleveGamme) -> Unit,
)

/**
 * Le plan de maintenance d'une machine, sur sa fiche.
 *
 * C'est le pendant de l'Entretien du Planning, qui répond pour tout le parc
 * à « qu'est-ce qui tombe ? » ; ici on est devant **une** machine et la question
 * est l'inverse : « qu'est-ce que celle-ci me doit, et qu'a-t-elle reçu ? ».
 * Les deux sont nécessaires — on arrive sur un site par la liste des échéances,
 * et on ouvre une fiche quand un client demande ce qui a été fait sur *son*
 * armoire.
 *
 * **Elle disparaît entièrement tant qu'aucune gamme n'existe**, et c'est
 * délibéré : une entreprise qui ne fait pas de contrat d'entretien n'a rien à
 * lire ici, et un bloc vide sur chaque fiche d'un parc de trois cents machines
 * serait du bruit sur l'écran qu'on ouvre le plus. Celui qui cherche la
 * fonctionnalité la trouve par l'Entretien du Planning, qui, lui, mène à la
 * création d'une gamme quand la liste est vide — c'est le même partage que pour
 * la tendance des relevés, qui ne s'affiche qu'à partir de deux visites.
 */
@Composable
fun SectionPlanMachine(
    plan: PlanMachine,
    actions: ActionsPlanMachine,
    modifier: Modifier = Modifier,
    aujourdhui: LocalDate = LocalDate.now(),
) {
    if (plan.gammes.isEmpty()) return

    val suivies = plan.echeances.map { it.gamme.id }.toSet()
    val disponibles = plan.gammes.filterNot { it.id in suivies }
    // La gamme dont on a demandé le retrait, en attente de la seconde touche.
    // Par identifiant : si la gamme cesse d'être suivie ailleurs entre-temps, la
    // boîte se referme d'elle-même au lieu de confirmer un geste déjà fait.
    var aNePlusSuivre by remember { mutableStateOf<String?>(null) }

    Section(intitule = "Maintenance préventive", modifier = modifier) {
        plan.echeances.forEach { echeance ->
            LigneGammeSuivie(
                echeance = echeance,
                aujourdhui = aujourdhui,
                onConsigner = { actions.onConsigner(echeance) },
                onDepart = { actions.onDepart(echeance) },
                onRetirer = { aNePlusSuivre = echeance.gamme.id },
            )
        }
        if (plan.echeances.isEmpty()) {
            Text(
                text = "Cette machine ne suit aucune gamme : rien ne lui est réclamé.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (disponibles.isNotEmpty()) {
            ChoixGamme(disponibles = disponibles, onSuivre = actions.onSuivre)
        }
    }

    plan.echeances.firstOrNull { it.gamme.id == aNePlusSuivre }?.let { echeance ->
        ConfirmationNePlusSuivre(
            echeance = echeance,
            visites = plan.visites.count { it.gammeId == echeance.gamme.id },
            onConfirmer = {
                aNePlusSuivre = null
                actions.onNePlusSuivre(echeance.gamme.id)
            },
            onFermer = { aNePlusSuivre = null },
        )
    }
}

/**
 * Ne plus suivre une gamme passe par une seconde touche, comme le retrait en lot
 * de la sélection du carnet, et pour la même raison : ce qui part est **l'entrée
 * au plan**, c'est-à-dire le dénominateur de l'attestation, et la remettre
 * demanderait de la ressaisir — un nouveau rattachement repartirait d'aujourd'hui.
 *
 * La boîte nomme ce qui part **et ce qui reste**, le second comptant autant : la
 * crainte qui retient le doigt est celle d'effacer le journal, et ce n'est pas ce
 * qui se produit.
 */
@Composable
private fun ConfirmationNePlusSuivre(
    echeance: EcheanceMaintenance,
    visites: Int,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Ne plus suivre « ${echeance.gamme.libelle} » ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "L'entrée au plan du ${libelleDateAvecAnnee(echeance.depuisLe)} et " +
                        "l'échéance de cette machine disparaissent. Suivre de nouveau la " +
                        "gamme repartirait d'aujourd'hui.",
                )
                Text(
                    text = when (visites) {
                        0 -> "Le journal reste tel qu'il est."
                        1 -> "Le journal reste : la visite déjà consignée est gardée."
                        else -> "Le journal reste : les $visites visites déjà consignées sont gardées."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmer,
                modifier = Modifier.heightIn(min = LocalCibles.current.action),
            ) {
                Text(text = "Ne plus suivre", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onFermer,
                modifier = Modifier.heightIn(min = LocalCibles.current.action),
            ) {
                Text(text = "Annuler")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * Une gamme que la machine suit : ce qu'elle doit, et le geste qui l'éteint.
 *
 * « Fait » prend **toute la largeur**, sous la ligne qui décrit la gamme, parce
 * que c'est le geste qu'on vient faire : on ouvre la fiche d'une machine pendant
 * une ronde, le reste sert à vérifier qu'on est au bon endroit, et une cible
 * pleine largeur se touche gants aux mains sans regarder. Le liseré ne paraît
 * que si quelque chose est dû — une gamme à jour ne doit pas attirer l'œil,
 * c'est la propriété qui rend une liste de plusieurs centaines lisible.
 */
@Composable
private fun LigneGammeSuivie(
    echeance: EcheanceMaintenance,
    aujourdhui: LocalDate,
    onConsigner: () -> Unit,
    onDepart: () -> Unit,
    onRetirer: () -> Unit,
) {
    val statuts = LocalStatuts.current
    val statut = echeance.statut(aujourdhui)
    val teinte: Color = when (statut) {
        StatutEcheance.EN_RETARD -> statuts.urgence
        StatutEcheance.A_FAIRE -> statuts.aValider
        StatutEcheance.A_VENIR -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Carte(liseré = teinte.takeIf { statut.appelleUneAction }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = echeance.gamme.libelle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = libelleEcheance(echeance, aujourdhui),
                    style = MaterialTheme.typography.bodySmall,
                    color = teinte,
                )
                // Elle **dit qu'elle se touche**, par le crayon : un texte
                // cliquable dont rien n'annonce qu'il l'est n'est pas une
                // fonctionnalité — même règle que le titre de la tournée, qui a
                // reçu son icône de calendrier pour la même raison.
                Row(
                    modifier = Modifier.clickable(onClick = onDepart),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "au plan depuis le ${jourCourt(echeance.depuisLe)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Changer la date d'entrée au plan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Puce(texte = echeance.gamme.periodicite.libelle)
            // « Ne plus suivre » est une icône et non un bouton : le geste est
            // rare, et un second bouton de même poids que « Fait » se toucherait
            // par erreur avec un gant.
            IconButton(onClick = onRetirer, modifier = Modifier.size(LocalCibles.current.carre)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Ne plus suivre ${echeance.gamme.libelle}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BoutonPlein(texte = "Fait", onClick = onConsigner, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * De quoi rattacher une gamme à cette machine.
 *
 * Un menu plutôt qu'une rangée de pastilles : les intitulés sont libres — « Ronde
 * du matin », « Visite semestrielle groupe froid » — et une rangée les aurait fait
 * défiler horizontalement, c'est-à-dire cacher les dernières. Un menu les montre
 * en entier, et ses lignes sont assez hautes pour un pouce ganté.
 */
@Composable
private fun ChoixGamme(disponibles: List<GammeMaintenance>, onSuivre: (String) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BoutonContour(
            texte = "Suivre une gamme",
            onClick = { ouvert = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            disponibles.forEach { gamme ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(text = gamme.libelle)
                            Text(
                                text = gamme.periodicite.libelle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        ouvert = false
                        onSuivre(gamme.id)
                    },
                )
            }
        }
    }
}

/**
 * Le journal des visites de cette machine.
 *
 * C'est la pièce qu'on montre quand un client demande ce qui a été fait sur son
 * armoire, et elle se lit du plus récent au plus ancien. Chaque ligne porte des
 * valeurs **recopiées** — l'intitulé de la gamme, sa cadence, le nom du
 * technicien —, si bien qu'une visite reste lisible après qu'une gamme a changé
 * de nom ou qu'un technicien a quitté l'entreprise. C'est le couple lien / copie
 * du type d'intervention, et il compte autant ici : une preuve qui s'efface
 * parce qu'on a réorganisé ses gammes n'est pas une preuve.
 *
 * Elle est **plafonnée**, comme les trois listes de l'accueil : une machine sous
 * ronde journalière en porte plusieurs centaines au bout d'un an, et un écran de
 * fiche n'est pas le document à remettre. Le compte complet est annoncé au-dessus,
 * pour qu'on sache que rien n'a disparu.
 */
@Composable
fun HistoriqueVisites(
    visites: List<ReleveGamme>,
    onRetirer: (ReleveGamme) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (visites.isEmpty()) return

    val montrees = visites.take(VISITES_MONTREES)
    val reste = visites.size - montrees.size
    var aRetirer by remember { mutableStateOf<ReleveGamme?>(null) }

    Section(intitule = "Visites consignées", modifier = modifier) {
        Text(
            text = buildString {
                append("${visites.size} visite${pluriel(visites.size)}")
                if (reste > 0) append(" · les $VISITES_MONTREES dernières")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        montrees.forEach { visite ->
            Carte {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = listOf(jourCourt(visite.faitLe), visite.gammeLibelle)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val detail = listOf(visite.technicienNom, visite.notes)
                            .filter { it.isNotBlank() }
                            .joinToString(" — ")
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = { aRetirer = visite },
                        modifier = Modifier.size(LocalCibles.current.carre),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Retirer cette visite",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    aRetirer?.let { visite ->
        ConfirmationRetraitVisite(
            visite = visite,
            onConfirmer = {
                aRetirer = null
                onRetirer(visite)
            },
            onFermer = { aRetirer = null },
        )
    }
}

/**
 * Retirer une visite passe par une confirmation qui **la nomme** — sa date, sa
 * gamme, son auteur —, et ce n'est pas une politesse : le journal est la preuve
 * qu'un contrat a été exécuté, la croix est à un pouce de « Fait », et une visite
 * effacée par mégarde ne se retrouve nulle part. Le geste reste possible, parce
 * qu'une visite consignée sur la mauvaise machine doit pouvoir se corriger.
 *
 * Elle dit aussi ce que le retrait **change** : l'échéance se recalcule depuis la
 * visite précédente, si bien que la machine peut redevenir due sous les yeux.
 */
@Composable
private fun ConfirmationRetraitVisite(
    visite: ReleveGamme,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Retirer la visite du ${jourCourt(visite.faitLe)} ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = buildString {
                        append("La visite du ${libelleDateAvecAnnee(visite.faitLe)}")
                        if (visite.gammeLibelle.isNotBlank()) append(", gamme « ${visite.gammeLibelle} »")
                        if (visite.technicienNom.isNotBlank()) append(", faite par ${visite.technicienNom}")
                        append(", sort du journal. C'est définitif.")
                    },
                )
                Text(
                    text = "L'échéance se recalcule depuis la visite précédente, et " +
                        "l'attestation de l'année ne la comptera plus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmer,
                modifier = Modifier.heightIn(min = LocalCibles.current.action),
            ) {
                Text(text = "Retirer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onFermer,
                modifier = Modifier.heightIn(min = LocalCibles.current.action),
            ) {
                Text(text = "Annuler")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * Assez pour voir l'année d'une visite trimestrielle, assez peu pour que la fiche
 * d'une machine sous ronde journalière reste une fiche.
 */
private const val VISITES_MONTREES = 20
