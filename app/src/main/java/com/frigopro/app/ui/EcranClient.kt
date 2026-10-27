package com.frigopro.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frigopro.app.data.Client
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EtatEtancheite
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.GroupeMachines
import com.frigopro.app.data.Intervention
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonDiscret
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.GraviteAlerte
import com.frigopro.app.ui.composants.IconeAlerte
import com.frigopro.app.ui.composants.IntituleSection
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffre
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

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
                    BlocDoit(
                        dues = dossier.dues,
                        aujourdhui = dossier.aujourdhui,
                        onOuvrirFacture = actions.onOuvrirFacture,
                    )
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
                ParcParZone(
                    clientId = client.id,
                    machines = machines,
                    aujourdhui = dossier.aujourdhui,
                    actions = actions,
                )
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
 * Où il est et comment le joindre : deux gros boutons, « Appeler » en plein —
 * c'est le geste qu'on fait le plus depuis cette page — et « Itinéraire » en
 * contour.
 *
 * Pas de troisième bouton « Intervention » : aucun geste ne crée encore une
 * intervention **pour ce client**, et un bouton qui ouvrirait une saisie vide
 * ferait retaper le nom qu'on a sous les yeux.
 *
 * Les boutons n'apparaissent que lorsqu'il y a de quoi agir — un « Appeler »
 * grisé ne renseigne personne. Sans l'un ni l'autre, la carte mène à la fiche,
 * puisque c'est elle qui les fait apparaître.
 */
@Composable
private fun EnteteClient(client: Client, onModifier: () -> Unit) {
    val contexte = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val lieu = listOf(client.adresse, client.ville).filter { it.isNotBlank() }
        Column {
            Text(
                text = lieu.joinToString(", ").ifEmpty { "Adresse non renseignée" },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (client.telephone.isNotBlank()) {
                Text(
                    text = client.telephone,
                    style = StyleChiffrePetit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (client.appelable || client.localisable) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (client.appelable) {
                    BoutonPlein(
                        texte = "Appeler",
                        onClick = { contexte.appeler(client.telephone) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                    )
                }
                if (client.localisable) {
                    BoutonContour(
                        texte = "Itinéraire",
                        onClick = { contexte.ouvrirItineraire(client.adresseComplete) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                    )
                }
            }
        } else {
            BoutonDiscret(
                texte = "Compléter l'adresse et le téléphone",
                onClick = onModifier,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * « Doit » : le reste dû en gros chiffres, puis chaque facture.
 *
 * Le total est **ce qui reste dû**, avoirs déduits — la somme des
 * `resteTtc` —, et c'est le chiffre qui change la conversation : on le lit
 * avant de décrocher.
 */
@Composable
private fun BlocDoit(
    dues: List<FactureChiffree>,
    aujourdhui: LocalDate,
    onOuvrirFacture: (Facture) -> Unit,
) {
    Carte(relief = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IntituleSection(
                texte = "Doit · ${dues.size} facture${if (dues.size > 1) "s" else ""}",
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Nombres.enEuros(dues.sumOf { it.resteTtc }),
                style = StyleChiffre.copy(fontSize = 24.sp, lineHeight = 30.sp),
            )
        }
        dues.forEach { chiffree ->
            LigneDue(
                chiffree = chiffree,
                aujourdhui = aujourdhui,
                onClick = { onOuvrirFacture(chiffree.facture) },
            )
        }
    }
}

/** Une zone du parc : son nom (vide pour « Sans zone »), ses machines, et sa première échéance F-Gas proche. */
private data class ZoneParc(
    val zone: String,
    val machines: List<Equipement>,
    val fgas: EtatEtancheite?,
)

/**
 * Le parc rangé **par zone** : c'est ainsi que le client connaît son site, et
 * ainsi qu'on le parcourt. Les unités intérieures sont rangées chacune dans la
 * leur — le groupe est en toiture, ses unités en salle.
 *
 * La F-Gas d'une zone est sa **plus proche** échéance dans l'horizon de
 * l'accueil, calculée par [EtatEtancheite.de] comme partout ailleurs : la même
 * machine ne doit pas être due à deux dates selon l'écran qui la montre.
 */
private fun zonesDuParc(machines: List<GroupeMachines>, aujourdhui: LocalDate): List<ZoneParc> {
    val francais = Collator.getInstance(Locale.FRENCH)
    val horizon = aujourdhui.plusMonths(AujourdhuiViewModel.HORIZON_ECHEANCES_MOIS)
    return machines
        .flatMap { listOf(it.groupe) + it.unites }
        .groupBy { it.zone.trim() }
        .map { (zone, liste) ->
            ZoneParc(
                zone = zone,
                machines = liste.sortedWith { a, b -> francais.compare(a.nom, b.nom) },
                fgas = liste
                    .map { EtatEtancheite.de(it, aujourdhui) }
                    .filter { it.echeance != null && !it.echeance.isAfter(horizon) }
                    .minByOrNull { it.echeance!! },
            )
        }
        // « Sans zone » en dernier : une machine non rangée reste visible, mais
        // ne passe pas devant celles qu'on a pris la peine de ranger.
        .sortedWith(compareBy<ZoneParc> { it.zone.isEmpty() }.thenComparator { a, b -> francais.compare(a.zone, b.zone) })
}

@Composable
private fun ParcParZone(
    clientId: String,
    machines: List<GroupeMachines>,
    aujourdhui: LocalDate,
    actions: ActionsEcranClient,
) {
    val zones = remember(machines, aujourdhui) { zonesDuParc(machines, aujourdhui) }
    val total = zones.sumOf { it.machines.size }
    // Ce qui est déplié est un état d'affichage, tenu ici et pour ce client seul.
    var depliees by rememberSaveable(clientId) { mutableStateOf(listOf<String>()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IntituleSection(
                texte = "Parc · $total machine${if (total > 1) "s" else ""}",
                modifier = Modifier.weight(1f),
            )
            if (total > 0) {
                TextButton(onClick = actions.onSelectionner) { Text(text = "Sélectionner") }
            }
            TextButton(onClick = actions.onAjouterMachine) { Text(text = "+ Machine") }
        }
        if (zones.isEmpty()) {
            Encart(texte = "Aucune machine chez ce client pour l'instant.")
        }
        zones.forEach { zone ->
            val ouverte = zone.zone in depliees
            Carte(
                onClick = {
                    depliees = if (ouverte) depliees - zone.zone else depliees + zone.zone
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = zone.zone.ifEmpty { "Sans zone" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val fgas = zone.fgas
                        val echeance = fgas?.echeance
                        if (fgas != null && echeance != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconeAlerte(
                                    gravite = if (fgas.enRetard) GraviteAlerte.URGENCE else GraviteAlerte.ALERTE,
                                    taille = 14.dp,
                                )
                                Text(
                                    text = if (fgas.enRetard) {
                                        "F-Gas en retard depuis le ${jourCourt(echeance)}"
                                    } else {
                                        "F-Gas dû le ${jourCourt(echeance)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (fgas.enRetard) {
                                        LocalStatuts.current.urgence
                                    } else {
                                        LocalStatuts.current.aValider
                                    },
                                )
                            }
                        }
                    }
                    Text(text = "${zone.machines.size}", style = StyleChiffre)
                    Icon(
                        imageVector = if (ouverte) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (ouverte) "Replier la zone" else "Déplier la zone",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ouverte) {
                    zone.machines.forEach { machine ->
                        LigneMachineZone(
                            machine = machine,
                            groupe = machine.parentId?.let { id ->
                                machines.firstOrNull { it.groupe.id == id }?.groupe
                            },
                            onOuvrir = { actions.onOuvrirMachine(machine) },
                            onSelectionner = { actions.onSelectionnerDepuis(machine) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Une machine sous sa zone. L'appui long la coche, comme dans le carnet — il
 * double « Sélectionner », il ne le remplace pas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneMachineZone(
    machine: Equipement,
    groupe: Equipement?,
    onOuvrir: () -> Unit,
    onSelectionner: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOuvrir, onLongClick = onSelectionner)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = machine.nom,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(
                groupe?.let { "unité de ${it.nom}" },
                machine.fluide.takeIf { it.isNotBlank() },
            )
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Une facture due : son reste, et depuis quand elle est échue s'il y a lieu. */
@Composable
private fun LigneDue(chiffree: FactureChiffree, aujourdhui: LocalDate, onClick: () -> Unit) {
    val facture = chiffree.facture
    val retard = if (chiffree.enRetard(aujourdhui)) facture.joursDeRetard(aujourdhui) else null
    val urgence = LocalStatuts.current.urgence
    Carte(onClick = onClick, liseré = if (retard != null) urgence else null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (retard != null) IconeAlerte(gravite = GraviteAlerte.URGENCE)
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
            Text(text = Nombres.enEuros(chiffree.resteTtc), style = StyleChiffrePetit)
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
