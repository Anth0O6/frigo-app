package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Client
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.GroupeMachines
import com.frigopro.app.data.Intervention
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonDiscret
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.theme.LocalStatuts
import java.time.LocalDate

/** Combien d'interventions passées la page montre avant « voir tout ». */
private const val PASSEES_MONTREES = 5

/**
 * Les gestes de la page d'un client, en un objet : l'écran en porte une dizaine,
 * et c'est le motif d'[ActionsLectureCarnet].
 */
data class ActionsEcranClient(
    val onFermer: () -> Unit = {},
    val onModifier: () -> Unit = {},
    val onOuvrirFacture: (Facture) -> Unit = {},
    val onOuvrirIntervention: (Intervention) -> Unit = {},
    val onOuvrirDevis: (Devis) -> Unit = {},
    val onOuvrirMachine: (Equipement) -> Unit = {},
    val onAjouterMachine: () -> Unit = {},
    val onSelectionner: () -> Unit = {},
    val onSelectionnerDepuis: (Equipement) -> Unit = {},
    val onOuvrirSite: (Client) -> Unit = {},
    /** `null` sur un site : on n'attache pas un site à un site. */
    val onNouveauSite: (() -> Unit)? = null,
)

/**
 * La page d'un client, en plein onglet : le **pivot** de l'onglet Clients.
 *
 * Elle répond à la question qu'on se pose en arrivant chez lui ou en décrochant
 * quand il appelle — « où en est-on avec lui ? » — et qui demandait jusque-là de
 * traverser trois onglets : ce qu'il doit, ce qu'on a fait et ce qui est prévu,
 * ce qu'on lui a proposé, son parc. L'ordre suit l'urgence : l'argent d'abord,
 * parce qu'une facture échue change la conversation qu'on va avoir.
 *
 * Elle **remplace** le carnet, comme une machine ouverte, et ne s'empile pas :
 * une seule profondeur, un `BackHandler`. Ce qu'elle ouvre ailleurs — une
 * facture, un devis, une intervention — change d'onglet plutôt que de s'empiler
 * ici, parce que c'est là-bas que ces objets ont leurs gestes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranClient(
    dossier: DossierClient,
    /** Le parc de ce client, par groupe : voir [ClientsScreen]. */
    machines: List<GroupeMachines>,
    attestation: ActionsAttestation,
    actions: ActionsEcranClient,
    modifier: Modifier = Modifier,
) {
    val client = dossier.client
    // « Voir tout » vaut pour ce client seulement : rouvrir une autre page ne
    // doit pas dérouler cinquante lignes qu'on n'a pas demandées.
    var toutesPassees by rememberSaveable(client.id) { mutableStateOf(false) }
    val passees = if (toutesPassees) dossier.passees else dossier.passees.take(PASSEES_MONTREES)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La coquille pose les marges des barres système : voir [FrigoProApp].
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(text = client.nom, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        dossier.donneurDOrdre?.let { enseigne ->
                            Text(
                                text = "Site de ${enseigne.nom}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onFermer) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = dossier.donneurDOrdre
                                ?.let { "Revenir à ${it.nom}" }
                                ?: "Revenir au carnet",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = actions.onModifier) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Modifier la fiche")
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
            contentPadding = PaddingValues(start = MargeEcran, end = MargeEcran, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "entete") { EnteteClient(client = client, onModifier = actions.onModifier) }

            if (dossier.dues.isNotEmpty()) {
                item(key = "dues") {
                    Section(intitule = "Ce qu'il doit") {
                        dossier.dues.forEach { chiffree ->
                            LigneDue(
                                chiffree = chiffree,
                                aujourdhui = dossier.aujourdhui,
                                onClick = { actions.onOuvrirFacture(chiffree.facture) },
                            )
                        }
                    }
                }
            }

            item(key = "interventions") {
                Section(intitule = "Interventions") {
                    if (dossier.aVenir.isEmpty() && dossier.passees.isEmpty()) {
                        Encart(texte = "Aucune intervention chez ce client pour l'instant.")
                    }
                    dossier.aVenir.forEach { intervention ->
                        LigneInterventionClient(intervention, onClick = { actions.onOuvrirIntervention(intervention) })
                    }
                    passees.forEach { intervention ->
                        LigneInterventionClient(intervention, onClick = { actions.onOuvrirIntervention(intervention) })
                    }
                    if (!toutesPassees && dossier.passees.size > PASSEES_MONTREES) {
                        BoutonDiscret(
                            texte = "Voir tout (${dossier.passees.size} passées)",
                            onClick = { toutesPassees = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (dossier.devisEnCours.isNotEmpty()) {
                item(key = "devis") {
                    Section(intitule = "Devis en cours") {
                        dossier.devisEnCours.forEach { chiffre ->
                            LigneDevisClient(chiffre, onClick = { actions.onOuvrirDevis(chiffre.devis) })
                        }
                    }
                }
            }

            item(key = "parc") {
                Section(intitule = "Parc") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        SectionMachines(
                            client = client,
                            machines = machines,
                            trouvees = null,
                            deplie = true,
                            onBasculerParc = {},
                            onVoirToutLeParc = {},
                            onOuvrirMachine = actions.onOuvrirMachine,
                            onAjouterMachine = actions.onAjouterMachine,
                            onSelectionner = actions.onSelectionner,
                            onSelectionnerDepuis = actions.onSelectionnerDepuis,
                            repliable = false,
                        )
                    }
                }
            }

            val nouveauSite = actions.onNouveauSite
            if (dossier.sites.isNotEmpty() || nouveauSite != null) {
                item(key = "sites") {
                    Section(intitule = "Sites") {
                        dossier.sites.forEach { site ->
                            Carte(onClick = { actions.onOuvrirSite(site) }) {
                                Text(text = site.nom, style = MaterialTheme.typography.titleSmall)
                                val lieu = listOf(site.adresse, site.ville).filter { it.isNotBlank() }
                                if (lieu.isNotEmpty()) {
                                    Text(
                                        text = lieu.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (nouveauSite != null) {
                            BoutonDiscret(
                                texte = "Ajouter un site",
                                onClick = nouveauSite,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (attestation.annees.isNotEmpty()) {
                item(key = "attestation") { SectionAttestation(attestation = attestation) }
            }
        }
    }
}

/**
 * Où il est et comment le joindre. Les boutons n'apparaissent que lorsqu'il y a
 * de quoi agir — un « Appeler » grisé ne renseigne personne —, et « Modifier »
 * est toujours là, puisque c'est lui qui les fait apparaître.
 */
@Composable
private fun EnteteClient(client: Client, onModifier: () -> Unit) {
    val contexte = LocalContext.current
    Carte {
        val lieu = listOf(client.adresse, client.ville).filter { it.isNotBlank() }
        Text(
            text = lieu.joinToString(", ").ifEmpty { "Adresse non renseignée" },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (client.telephone.isNotBlank()) {
            Text(
                text = client.telephone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (client.appelable) {
                BoutonContour(
                    texte = "Appeler",
                    onClick = { contexte.appeler(client.telephone) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (client.localisable) {
                BoutonContour(
                    texte = "Itinéraire",
                    onClick = { contexte.ouvrirItineraire(client.adresseComplete) },
                    modifier = Modifier.weight(1f),
                )
            }
            BoutonDiscret(texte = "Modifier", onClick = onModifier, modifier = Modifier.weight(1f))
        }
    }
}

/** Une facture due : son reste, et depuis quand elle est échue s'il y a lieu. */
@Composable
private fun LigneDue(chiffree: FactureChiffree, aujourdhui: LocalDate, onClick: () -> Unit) {
    val facture = chiffree.facture
    val retard = if (chiffree.enRetard(aujourdhui)) facture.joursDeRetard(aujourdhui) else null
    val urgence = LocalStatuts.current.urgence
    Carte(onClick = onClick, relief = true, liseré = if (retard != null) urgence else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = facture.numero, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when {
                        retard != null -> "échue depuis $retard jour${if (retard > 1) "s" else ""}"
                        facture.echeanceLe != null -> "échéance le ${libelleDateAvecAnnee(facture.echeanceLe)}"
                        else -> "émise"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (retard != null) urgence else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = Nombres.enEuros(chiffree.resteTtc), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Une intervention chez ce client : quand, quoi, sur quelle machine, où elle en est. */
@Composable
private fun LigneInterventionClient(intervention: Intervention, onClick: () -> Unit) {
    val couleur = couleurStatut(intervention)
    Carte(onClick = onClick, liseré = couleur) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${libelleDateAvecAnnee(intervention.date)} · ${intervention.heure.format(FORMAT_HEURE)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                val detail = listOf(intervention.typeLibelle, intervention.equipementNom, intervention.numero)
                    .filter { it.isNotBlank() }
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(text = libelleStatut(intervention), style = MaterialTheme.typography.labelMedium, color = couleur)
        }
    }
}

/** Un devis qui attend encore une réponse ou une facture. */
@Composable
private fun LigneDevisClient(chiffre: DevisChiffre, onClick: () -> Unit) {
    val devis = chiffre.devis
    Carte(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = devis.numero, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOf(devis.statut.libelle, devis.objet).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text = Nombres.enEuros(chiffre.totalTtc), style = MaterialTheme.typography.titleSmall)
        }
    }
}
