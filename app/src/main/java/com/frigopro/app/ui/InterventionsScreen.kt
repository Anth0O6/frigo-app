package com.frigopro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.data.enDuree
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.theme.AValider
import com.frigopro.app.ui.theme.Planifie
import com.frigopro.app.ui.theme.StyleChiffre
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.time.Duration
import java.time.LocalDate

/**
 * La feuille de saisie d'une intervention, où qu'on l'ouvre.
 *
 * Elle est un composable à part plutôt qu'un bloc recopié dans chaque route qui
 * la montre, et elle n'est posée **qu'une fois, par la coquille**, au-dessus de
 * tous les onglets : « Aujourd'hui » l'ouvre pour créer ou corriger, le Planning
 * pour corriger un créneau depuis la semaine, la vue Devis pour planifier un
 * devis accepté — `viewModel()` leur rend la même instance, donc le même
 * formulaire —, et deux copies de ces cinq flux auraient divergé au premier
 * champ ajouté. Elle ne dessine rien tant qu'aucune saisie n'est en cours.
 */
@Composable
fun FeuilleFormulaireIntervention(
    viewModel: InterventionsViewModel = viewModel(factory = InterventionsViewModel.Factory),
) {
    val formulaire by viewModel.formulaire.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val types by viewModel.types.collectAsStateWithLifecycle()
    val machines by viewModel.machines.collectAsStateWithLifecycle()
    val techniciens by viewModel.techniciens.collectAsStateWithLifecycle()

    formulaire?.let { etat ->
        FormulaireIntervention(
            etat = etat,
            clients = clients,
            types = types,
            // Seules les machines du client choisi : celles des autres clients
            // n'ont rien à faire dans cette saisie.
            machines = machines.filter { it.clientId == etat.clientId },
            techniciens = techniciens,
            onEtatChange = viewModel::onFormulaireChange,
            onClientChoisi = viewModel::onClientChoisi,
            onTypeChoisi = viewModel::onTypeChoisi,
            onNouveauType = viewModel::onNouveauType,
            onMachineChoisie = viewModel::onMachineChoisie,
            onNouvelleMachine = viewModel::onNouvelleMachine,
            onNouveauTechnicien = viewModel::onNouveauTechnicien,
            onValider = viewModel::onValiderFormulaire,
            onSupprimer = viewModel::onSupprimerIntervention,
            onFermer = viewModel::onFermerFormulaire,
        )
    }
}

/*
 * Les morceaux d'une journée, partagés par « Aujourd'hui » (`EcranAujourdhui`).
 *
 * Ce fichier portait l'écran de la vue « Jour » et la route de la Tournée ; les
 * deux ont été fondus dans « Aujourd'hui », et il ne garde que ce qui dessine
 * une journée — les pastilles de la semaine, le bandeau de chiffres, les lignes
 * et la pastille de statut —, plus la feuille de saisie, que la coquille pose.
 */

/**
 * Les sept jours de la semaine, en pastilles.
 *
 * Elles remplacent deux appuis sur les flèches par un seul, et surtout elles
 * **montrent la semaine** : on voit où l'on est avant de choisir, ce qu'un
 * bouton « jour suivant » ne dit pas. Les flèches restent, pour franchir une
 * semaine sans passer par le sélecteur.
 */
@Composable
internal fun PastillesSemaine(jour: LocalDate, onJourChoisi: (LocalDate) -> Unit) {
    val lundi = lundiDe(jour)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MargeEcran)
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(JOURS_SEMAINE) { rang ->
            val date = lundi.plusDays(rang.toLong())
            val retenu = date == jour
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = if (retenu) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                onClick = { onJourChoisi(date) },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = jourSemaineCourt(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (retenu) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                    Text(
                        text = "${date.dayOfMonth}",
                        style = StyleChiffrePetit,
                        color = if (retenu) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val JOURS_SEMAINE = 7

/**
 * Le bandeau de chiffres : ce qui est fait, le temps saisi, les urgences.
 *
 * Le temps saisi est la somme des chronomètres de la journée. Il ne sert pas
 * qu'à informer : c'est lui qui fait remarquer qu'on a oublié d'arrêter un
 * chrono, ou qu'une journée à rallonge n'a été facturée qu'à moitié.
 */
@Composable
internal fun BandeauJournee(lignes: List<LigneTournee>) {
    if (lignes.isEmpty()) return

    val terminees = lignes.count { it.intervention.statut == StatutIntervention.TERMINEE }
    val cumul = lignes.fold(Duration.ZERO) { total, ligne ->
        total.plus(ligne.intervention.chrono.ecoulee(java.time.Instant.now()))
    }
    val urgences = lignes.count { it.intervention.urgente }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MargeEcran)
            .padding(bottom = 14.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChiffreJournee(
                valeur = "$terminees/${lignes.size}",
                libelle = "terminées",
                couleur = MaterialTheme.colorScheme.primary,
            )
            SeparateurVertical()
            ChiffreJournee(valeur = cumul.enDuree(), libelle = "temps saisi")
            if (urgences > 0) {
                SeparateurVertical()
                ChiffreJournee(valeur = "$urgences", libelle = "urgence", couleur = AValider)
            }
        }
    }
}

@Composable
private fun ChiffreJournee(
    valeur: String,
    libelle: String,
    couleur: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(text = valeur, style = StyleChiffre, color = couleur)
        Text(
            text = libelle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeparateurVertical() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * L'intervention en cours, dépliée.
 *
 * C'est la seule carte de la liste qui porte des boutons : celle qu'on est en
 * train de faire est la seule sur laquelle on agit sans réfléchir — terminer,
 * appeler, y aller. Les autres se contentent d'attendre.
 *
 * « Terminer » **ouvre le volet Rapport** et ne termine rien d'un appui : il
 * écrivait le statut seul, sans arrêter le temps ni attribuer la référence du
 * compte-rendu. Sur le Rapport on fait signer, puis on clôture — le seul chemin
 * vers « terminé ». Voir [GesteStatut].
 */
@Composable
internal fun CarteEnCours(
    ligne: LigneTournee,
    onOuvrir: () -> Unit,
    onModifier: () -> Unit,
    onChangerStatut: () -> Unit,
) {
    val contexte = LocalContext.current
    val intervention = ligne.intervention
    val ecoule = intervention.chrono.ecoulee(java.time.Instant.now())

    Carte(relief = true, liseré = AValider, onClick = onOuvrir, onLongClick = onModifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = intervention.heure.format(FORMAT_HEURE),
                style = StyleChiffrePetit,
                color = AValider,
            )
            Puce(
                texte = if (intervention.chrono.vierge) {
                    "EN COURS"
                } else {
                    "EN COURS · ${ecoule.enDuree()}"
                },
                couleur = AValider,
                fond = AValider.copy(alpha = 0.16f),
            )
        }
        Column {
            Text(
                text = intervention.client,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sousTitre(intervention),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (intervention.typeLibelle.isNotBlank()) {
                Puce(texte = intervention.typeLibelle, couleur = Planifie)
            }
            if (intervention.equipementNom.isNotBlank()) {
                Puce(texte = intervention.equipementNom, couleur = Planifie)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoutonPlein(
                texte = "Terminer",
                onClick = onChangerStatut,
                modifier = Modifier.weight(1f),
            )
            if (ligne.appelable) {
                BoutonCarre(
                    icone = Icons.Filled.Call,
                    description = "Appeler ${intervention.client}",
                    onClick = { ligne.client?.telephone?.let(contexte::appeler) },
                    fond = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            if (ligne.localisable) {
                BoutonCarre(
                    icone = Icons.Filled.Directions,
                    description = "Itinéraire vers ${intervention.client}",
                    onClick = { ligne.client?.adresseComplete?.let(contexte::ouvrirItineraire) },
                    fond = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            BoutonCarre(
                icone = Icons.Filled.Edit,
                description = "Modifier ${intervention.client}",
                onClick = onModifier,
                fond = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

/**
 * Une intervention qui attend, ou qui est faite.
 *
 * Une ligne, une heure, un nom : de quoi balayer la journée du pouce. La
 * pastille de droite démarre une intervention planifiée sans rien ouvrir — c'est
 * le geste qu'on fait en descendant de la camionnette —, et mène au compte-rendu
 * de toutes les autres. Voir [GesteStatut].
 */
@Composable
internal fun LigneCompacte(
    ligne: LigneTournee,
    onOuvrir: () -> Unit,
    onModifier: () -> Unit,
    onChangerStatut: () -> Unit,
) {
    val intervention = ligne.intervention
    val terminee = intervention.statut == StatutIntervention.TERMINEE

    Carte(onClick = onOuvrir, onLongClick = onModifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = intervention.heure.format(FORMAT_HEURE),
                style = StyleChiffrePetit,
                color = if (terminee) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = intervention.client,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (terminee) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sousTitre(intervention),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (intervention.urgente && !terminee) {
                Puce(texte = "URGENCE", couleur = AValider, fond = AValider.copy(alpha = 0.16f))
            }
            // Un bouton, et pas seulement l'appui long. L'appui long est resté la
            // convention des cartes du projet, mais il ne se devine pas : corriger
            // une heure mal saisie était impossible pour qui ne l'avait pas
            // découvert par hasard, et impossible tout court avec des gants, où
            // le doigt glisse avant que l'appui soit reconnu.
            BoutonCarre(
                icone = Icons.Filled.Edit,
                description = "Modifier ${intervention.client}",
                onClick = onModifier,
                couleur = MaterialTheme.colorScheme.onSurfaceVariant,
                fond = androidx.compose.ui.graphics.Color.Transparent,
            )
            PastilleStatut(statut = intervention.statut, onClick = onChangerStatut)
        }
    }
}

/**
 * Le rond qu'on touche pour avancer : vide à faire, coché terminé.
 *
 * Sa description dit ce que l'appui **fait**, et non l'état qu'il montre :
 * c'est elle que lit un lecteur d'écran, et « terminée » sur un rond qui ouvre
 * le compte-rendu promettrait un autre geste que celui qui se produit.
 */
@Composable
private fun PastilleStatut(statut: StatutIntervention, onClick: () -> Unit) {
    val terminee = statut == StatutIntervention.TERMINEE
    val description = when (gesteStatut(statut)) {
        GesteStatut.DEMARRER -> "Démarrer l'intervention"
        GesteStatut.CLOTURER -> "Ouvrir le compte-rendu pour clôturer"
        GesteStatut.VOIR_RAPPORT -> "Voir le compte-rendu"
    }
    Surface(
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.extraLarge,
        color = androidx.compose.ui.graphics.Color.Transparent,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (terminee) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Surface(
                    modifier = Modifier.size(14.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {}
            }
        }
    }
}

@Composable
internal fun JourneeVide() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Aucune intervention",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Le bouton + en ajoute une.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** « Chambre froide positive · Vitry-sur-Seine », selon ce qui est connu. */
private fun sousTitre(intervention: Intervention): String = listOf(
    intervention.equipementNom.ifBlank { intervention.typeLibelle },
    intervention.ville,
).filter { it.isNotBlank() }.joinToString(" · ")
