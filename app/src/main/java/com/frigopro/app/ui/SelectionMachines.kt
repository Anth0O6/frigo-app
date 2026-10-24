package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.AffectationGamme
import com.frigopro.app.data.Client
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.GammeMaintenance
import com.frigopro.app.data.GroupeMachines
import com.frigopro.app.data.Periodicite
import com.frigopro.app.data.PlanMaintenance
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.theme.FrigoProTheme
import com.frigopro.app.ui.theme.LocalCibles
import java.time.LocalDate

/**
 * Les gestes du mode sélection, regroupés en un objet pour la raison qui a produit
 * `EtatDeplacement` et `ActionsPlanMachine` : `ClientsScreen` en portait déjà sept,
 * et huit de plus l'auraient rendu illisible.
 */
data class ActionsSelection(
    /** Entre en sélection sur le parc de ce client, par le bouton visible. */
    val onSelectionner: (String) -> Unit = {},
    /** Entre en sélection par l'appui long, la machine touchée déjà cochée. */
    val onSelectionnerDepuis: (Equipement) -> Unit = {},
    val onBasculer: (Equipement) -> Unit = {},
    /** Coche les machines montrées : voir [EquipementsViewModel.onSelectionnerTout]. */
    val onTout: (List<Equipement>) -> Unit = {},
    val onAucune: (List<Equipement>) -> Unit = {},
    val onQuitter: () -> Unit = {},
    val onZone: () -> Unit = {},
    /**
     * `null` tant qu'aucune gamme n'existe : le bouton disparaît alors, même règle
     * que la section de plan absente d'une fiche machine — une entreprise sans
     * contrat d'entretien n'a rien à affecter.
     */
    val onGamme: (() -> Unit)? = null,
)

/** Les deux boîtes qu'une sélection ouvre. Une seule à la fois, d'où le type. */
internal enum class BoiteSelection { ZONE, GAMME }

/**
 * Le parc d'un client **en mode sélection** : la première sélection multiple de
 * l'application.
 *
 * Elle répond au cas qui manquait entre « tout le parc » et « cette machine » :
 * **ces douze meubles-là**. Ranger un parc de trois cents machines déjà saisies
 * demandait trois cents ouvertures de fiche, et rattacher une gamme à un linéaire
 * sans y entraîner la chambre froide d'à côté demandait douze.
 *
 * Elle **remplace le carnet** le temps du geste plutôt que de cocher dans les
 * cartes, et c'est ce qui tient la règle d'un seul client : on ne voit que le
 * parc sur lequel on agit, si bien qu'aucune machine cochée n'est hors de l'écran
 * dans une autre carte au moment de ranger. Le bandeau ne défile pas — compteur,
 * filtre et actions restent sous le pouce au bout d'un parc de trois cents lignes,
 * là où des boutons en tête de liste se seraient perdus au premier défilement.
 *
 * **Les unités intérieures paraissent, chacune sur sa ligne**, sous leur groupe et
 * en retrait — alors que le carnet ne montre que les groupes. C'est la zone qui
 * l'impose : un groupe extérieur et ses unités sont l'exemple même de machines
 * liées qui ne sont **pas au même endroit** — le groupe en toiture, les unités en
 * salle et en chambre. Cocher un groupe ne coche donc pas ses unités ; « Tout
 * cocher » les prend toutes, comme l'affectation au parc entier, qui les inclut
 * parce qu'un split suivi sans ses unités ne voudrait rien dire.
 *
 * Le **filtre** n'est pas la recherche du carnet : celle-ci cherche un client,
 * celui-là des machines dans un parc déjà choisi. Il porte sur le nom, la
 * désignation et la zone, et c'est ce qui rend « ces douze meubles-là »
 * atteignable en deux gestes sur un grand site : « vitrine », puis « Tout cocher ».
 */
@Composable
fun ParcEnSelection(
    client: Client,
    machines: List<GroupeMachines>,
    selection: SelectionParc,
    actions: ActionsSelection,
    modifier: Modifier = Modifier,
) {
    var filtre by rememberSaveable(client.id) { mutableStateOf("") }

    // Le groupe puis ses unités : l'ordre du parc, aplati sur une seule liste.
    val lignes = machines.flatMap { listOf(it.groupe) + it.unites }
    val nomsGroupes = machines.associate { it.groupe.id to it.groupe.nom }
    val visibles = lignes.filter { Recherche.correspond(filtre, it.nom, it.designation, it.zone) }
    // Compté contre le parc et non sur l'ensemble retenu : une machine supprimée
    // ailleurs pendant qu'on la tenait cochée ne doit plus faire nombre.
    val presentes = lignes.map { it.id }.toSet()
    val nombre = selection.ids.count { it in presentes }
    val toutCoche = visibles.isNotEmpty() && visibles.all { it.id in selection.ids }

    Column(modifier = modifier) {
        BandeauSelection(
            client = client,
            nombre = nombre,
            filtre = filtre,
            onFiltre = { filtre = it },
            toutCoche = toutCoche,
            peutCocher = visibles.isNotEmpty(),
            onTout = { if (toutCoche) actions.onAucune(visibles) else actions.onTout(visibles) },
            actions = actions,
        )
        if (visibles.isEmpty()) {
            Encart(
                texte = "Aucune machine de ce parc ne correspond à « ${filtre.trim()} ».",
                modifier = Modifier.padding(MargeEcran),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MargeEcran,
                    end = MargeEcran,
                    top = 12.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items = visibles, key = { it.id }) { machine ->
                    LigneSelection(
                        machine = machine,
                        cochee = machine.id in selection.ids,
                        groupe = machine.parentId?.let { nomsGroupes[it] },
                        onBasculer = { actions.onBasculer(machine) },
                    )
                }
            }
        }
    }
}

/**
 * Ce qui reste en place pendant qu'on coche : sortir, compter, filtrer, agir.
 *
 * Sortir est un bouton carré et non le seul retour système : celui-ci défait bien
 * la sélection, mais un geste qui ne se voit pas n'est pas une fonctionnalité.
 */
@Composable
private fun BandeauSelection(
    client: Client,
    nombre: Int,
    filtre: String,
    onFiltre: (String) -> Unit,
    toutCoche: Boolean,
    peutCocher: Boolean,
    onTout: () -> Unit,
    actions: ActionsSelection,
) {
    Column(
        modifier = Modifier.padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoutonCarre(
                icone = Icons.Filled.Close,
                description = "Quitter la sélection",
                onClick = actions.onQuitter,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = libelleCochees(nombre), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = client.nom,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onTout,
                enabled = peutCocher,
                modifier = Modifier.heightIn(min = LocalCibles.current.action),
            ) {
                Text(text = if (toutCoche) "Tout décocher" else "Tout cocher")
            }
        }
        ChampRecherche(
            valeur = filtre,
            onValeur = onFiltre,
            indication = "Filtrer : nom, zone, modèle…",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Inactifs sans rien de coché plutôt qu'absents : la place reste la
            // même pendant qu'on coche, et le doigt ne court pas après un bouton
            // qui apparaît à la première case.
            BoutonPlein(
                texte = "Donner une zone",
                onClick = actions.onZone,
                actif = nombre > 0,
                modifier = Modifier.weight(1f),
            )
            actions.onGamme?.let { onGamme ->
                BoutonPlein(
                    texte = "Suivre une gamme",
                    onClick = onGamme,
                    actif = nombre > 0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Une machine qu'on coche.
 *
 * Toute la ligne se touche, et non la seule case, pour la raison de la checklist :
 * on coche avec des gants, et viser un carré de vingt pixels sur un toit est la
 * meilleure façon de renoncer. La case suit le **mode gants** (`LocalCibles`),
 * comme celle de la checklist, et la ligne a au moins la hauteur d'une action.
 *
 * La zone actuelle est dite sous le nom, « Sans zone » compris : c'est ce qu'on
 * vient corriger, et cocher sans la voir ferait ranger deux fois la même machine.
 */
@Composable
private fun LigneSelection(
    machine: Equipement,
    cochee: Boolean,
    /** Le nom du groupe, pour une unité intérieure ; `null` sinon. */
    groupe: String?,
    onBasculer: () -> Unit,
) {
    val sousTitre = listOf(
        groupe?.let { "unité de $it" }.orEmpty(),
        machine.zone.ifBlank { "Sans zone" },
        machine.designation,
    ).filter { it.isNotBlank() }.joinToString(" · ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (groupe != null) RETRAIT_UNITE else 0.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (cochee) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        onClick = onBasculer,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = LocalCibles.current.action)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(LocalCibles.current.case),
                shape = MaterialTheme.shapes.small,
                color = if (cochee) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (cochee) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Cochée",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = machine.nom,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sousTitre,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Donner une zone à la sélection, ou la lui retirer.
 *
 * Les zones déjà employées sont proposées dessous, comme sur la fiche d'une
 * machine et pour la même raison — c'est le seul garde-fou du texte libre, et un
 * geste qui range douze machines d'un coup sous « toit » au lieu de « Toiture »
 * fendrait l'Entretien en deux groupes d'un seul coup.
 *
 * Le champ s'ouvre **sur la zone commune** quand toutes les machines cochées en
 * partagent une, et vide sinon : proposer celle de la première aurait invité à
 * l'étendre aux autres sans l'avoir choisie.
 *
 * **Vide veut dire retirer**, et la boîte le dit avec le nombre de machines qui
 * repasseraient « Sans zone » : c'est un geste légitime — une zone posée sur la
 * mauvaise sélection se corrige ainsi —, mais il ne doit pas se faire sans qu'on
 * l'ait lu. Rien à retirer et rien de saisi : le bouton est inactif.
 */
@Composable
internal fun DialogueZoneSelection(
    machines: List<Equipement>,
    zones: List<String>,
    onValider: (String) -> Unit,
    onFermer: () -> Unit,
) {
    val commune = machines.map { it.zone.trim() }.distinct().singleOrNull().orEmpty()
    val rangees = machines.count { it.zone.isNotBlank() }
    var zone by remember { mutableStateOf(commune) }
    val retrait = zone.isBlank()

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Ranger ${libelleCochees(machines.size).lowercase()}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChampTexte(
                    libelle = "Zone (toiture, linéaire surgelés…)",
                    valeur = zone,
                    onValeur = { zone = it },
                )
                if (zones.isNotEmpty()) {
                    ZonesProposees(zones = zones, onChoisir = { zone = it })
                }
                Text(
                    text = when {
                        !retrait -> "La zone de chacune est remplacée par celle-ci."
                        rangees == 0 -> "Saisissez une zone, ou touchez l'une de celles déjà employées."
                        rangees == 1 -> "Laissée vide, la zone est retirée : 1 machine repasse « Sans zone »."
                        else -> "Laissée vide, la zone est retirée : $rangees machines repassent « Sans zone »."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onValider(zone) }, enabled = !retrait || rangees > 0) {
                Text(text = if (retrait) "Retirer la zone" else "Ranger")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * Rattacher une gamme à la sélection, ou l'en retirer.
 *
 * La boîte dit **avant le geste** combien de machines suivent déjà la gamme, et
 * c'est ce qui rend la date d'entrée au plan proposable sans danger : elle ne vaut
 * que pour les autres, celles qui y étaient gardent la leur — c'est le
 * dénominateur de leur attestation, et le remettre à zéro d'un geste fait pour en
 * ajouter aurait effacé des mois de contrat. Voir
 * [com.frigopro.app.data.MaintenanceRepository.affecterGroupe].
 *
 * La date se choisit, et le défaut est aujourd'hui, comme le rattachement depuis
 * une fiche. Elle se **déplace** ici plutôt qu'ensuite machine par machine : un
 * linéaire inventorié en octobre sous un contrat commencé en janvier se date de
 * janvier en un geste, au lieu de douze reports.
 *
 * **Retirer** passe par une seconde touche qui dit ce qui se perd : les visites
 * restent au journal, mais l'entrée au plan de chaque machine disparaît, et la
 * remettre demanderait de la ressaisir. Sur une sélection de cent machines, un
 * retrait au premier appui serait le geste le plus coûteux de l'écran.
 */
@Composable
internal fun DialogueGammeSelection(
    ids: Set<String>,
    gammes: List<GammeMaintenance>,
    affectations: List<AffectationGamme>,
    onAffecter: (gammeId: String, depuisLe: LocalDate) -> Unit,
    onRetirer: (gammeId: String) -> Unit,
    onFermer: () -> Unit,
    aujourdhui: LocalDate = LocalDate.now(),
) {
    var retenue by remember { mutableStateOf(gammes.singleOrNull()?.id) }
    var depuisLe by remember { mutableStateOf(aujourdhui) }
    var choixDate by remember { mutableStateOf(false) }
    // Remis à zéro quand la gamme change : une confirmation donnée pour l'une ne
    // vaut pas pour l'autre.
    var retraitDemande by remember(retenue) { mutableStateOf(false) }

    val gamme = gammes.firstOrNull { it.id == retenue }
    val deja = gamme?.let { PlanMaintenance.suiventDeja(ids, it.id, affectations).size } ?: 0
    val nouvelles = ids.size - deja

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Gamme de ${libelleCochees(ids.size).lowercase()}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gammes.forEach { candidate ->
                    LigneGammeCandidate(
                        gamme = candidate,
                        retenue = candidate.id == retenue,
                        onChoisir = { retenue = candidate.id },
                    )
                }
                if (gamme != null) {
                    if (deja > 0) {
                        Text(
                            text = if (deja == 1) {
                                "1 machine suit déjà cette gamme : son entrée au plan ne bouge pas."
                            } else {
                                "$deja machines suivent déjà cette gamme : leur entrée au plan ne bouge pas."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (nouvelles > 0) {
                        // Elle dit qu'elle se touche, par le crayon : même règle que
                        // l'entrée au plan sur la fiche d'une machine.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = LocalCibles.current.action)
                                .clickable { choixDate = true },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = buildString {
                                    append("Entrée au plan")
                                    if (deja > 0) {
                                        append(if (nouvelles == 1) " de l'autre" else " des $nouvelles autres")
                                    }
                                    append(" : ${libelleDateAvecAnnee(depuisLe)}")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Changer la date d'entrée au plan",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Text(
                            text = "Toutes les machines cochées suivent déjà cette gamme.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (retraitDemande) {
                        Text(
                            text = "Retirer la gamme de ${libelleCochees(deja).lowercase()} ? " +
                                "Leurs visites restent au journal ; leur entrée au plan est perdue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { gamme?.let { onAffecter(it.id, depuisLe) } },
                enabled = gamme != null && nouvelles > 0,
            ) {
                Text(text = if (gamme != null && nouvelles > 0) "Suivre ($nouvelles)" else "Suivre")
            }
        },
        dismissButton = {
            Row {
                if (gamme != null && deja > 0) {
                    TextButton(
                        onClick = {
                            if (retraitDemande) {
                                onRetirer(gamme.id)
                            } else {
                                retraitDemande = true
                            }
                        },
                    ) {
                        Text(
                            text = if (retraitDemande) "Confirmer le retrait" else "Retirer ($deja)",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onFermer) { Text(text = "Annuler") }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    if (choixDate) {
        SelecteurDate(
            date = depuisLe,
            onDateChoisie = { jour ->
                depuisLe = jour
                choixDate = false
            },
            onFermer = { choixDate = false },
        )
    }
}

/**
 * Une gamme qu'on peut retenir, avec sa cadence.
 *
 * Des lignes pleine largeur plutôt qu'un menu déroulant comme sur la fiche : la
 * boîte affiche ensuite ce que le choix implique — combien la suivent déjà, à
 * partir de quand —, et un menu refermé sur son choix aurait caché les autres
 * au moment de comparer.
 */
@Composable
private fun LigneGammeCandidate(
    gamme: GammeMaintenance,
    retenue: Boolean,
    onChoisir: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (retenue) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        onClick = onChoisir,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = LocalCibles.current.action)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = gamme.libelle,
                style = MaterialTheme.typography.bodyLarge,
                color = if (retenue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Puce(texte = gamme.periodicite.libelle)
        }
    }
}

/** « Aucune machine », « 1 machine », « 12 machines » : le compteur de la sélection. */
private fun libelleCochees(nombre: Int): String = when (nombre) {
    0 -> "Aucune machine"
    1 -> "1 machine"
    else -> "$nombre machines"
}

/**
 * Le retrait d'une unité intérieure sous son groupe : c'est lui qui dit le
 * rattachement, comme celui d'un site sous son enseigne dans le carnet.
 */
private val RETRAIT_UNITE = 24.dp

@Preview(showBackground = true)
@Composable
private fun ParcEnSelectionPreview() {
    FrigoProTheme {
        Surface {
            ParcEnSelection(
                client = Client(id = "1", nom = "Carrefour Market Vaise", ville = "Lyon"),
                machines = listOf(
                    GroupeMachines(
                        Equipement(id = "v1", clientId = "1", nom = "Vitrine 1", zone = "Linéaire frais"),
                    ),
                    GroupeMachines(Equipement(id = "v2", clientId = "1", nom = "Vitrine 2")),
                    GroupeMachines(
                        groupe = Equipement(id = "g1", clientId = "1", nom = "Daikin bureaux", zone = "Toiture"),
                        unites = listOf(
                            Equipement(id = "u1", clientId = "1", parentId = "g1", nom = "Accueil"),
                        ),
                    ),
                ),
                selection = SelectionParc(clientId = "1", ids = setOf("v1", "u1")),
                actions = ActionsSelection(onGamme = {}),
            )
        }
    }
}

@Preview
@Composable
private fun DialogueGammeSelectionPreview() {
    FrigoProTheme {
        DialogueGammeSelection(
            ids = setOf("v1", "v2", "v3"),
            gammes = listOf(
                GammeMaintenance(id = "g1", libelle = "Ronde du matin", periodicite = Periodicite.JOURNALIER),
                GammeMaintenance(id = "g2", libelle = "Visite mensuelle", periodicite = Periodicite.MENSUEL),
            ),
            affectations = listOf(
                AffectationGamme(equipementId = "v1", gammeId = "g2", depuisLe = LocalDate.of(2026, 3, 1)),
            ),
            onAffecter = { _, _ -> },
            onRetirer = {},
            onFermer = {},
        )
    }
}
