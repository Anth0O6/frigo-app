package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.frigopro.app.data.SiteDu
import com.frigopro.app.data.StatutEcheance
import com.frigopro.app.data.Technicien
import com.frigopro.app.ui.composants.BarreActions
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.LocalStatuts
import java.time.LocalDate

/**
 * La tournée d'entretien sur un site : ce qu'il doit, zone par zone, et une case
 * par visite.
 *
 * C'est la réponse au cas qui rendait le plan intenable : un site de cinquante
 * machines sous une ronde journalière, consigné **une visite à la fois** —
 * cinquante feuilles ouvertes, cinquante dates, cinquante « Consigner ». Ici on
 * coche ce qu'on a vu en marchant, et une seule feuille consigne le tout.
 *
 * L'écran **remplace** l'Entretien plutôt que de s'empiler dessus : une seule
 * profondeur, un `BackHandler`, toujours pas de graphe de navigation. Il ne
 * montre que le site choisi — une tournée est un endroit — et range ses lignes
 * **par zone, dans l'ordre des zones**, « Sans zone » en dernier : c'est l'ordre
 * des pieds, et c'est la raison d'être de la zone.
 */
@Composable
fun EcranTourneeSite(
    tournee: TourneeSite,
    onBasculer: (EcheanceMaintenance) -> Unit,
    onCocherTout: (List<EcheanceMaintenance>) -> Unit,
    onConsigner: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
    aujourdhui: LocalDate = LocalDate.now(),
) {
    val toutes = tournee.echeances
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BarreActions {
                // Inactif sans rien de coché plutôt qu'absent : la place reste la
                // même pendant qu'on coche — même règle que la sélection du parc.
                BoutonPlein(
                    texte = libelleConsigner(tournee.nombreCochees),
                    onClick = onConsigner,
                    actif = tournee.nombreCochees > 0,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargeEcran, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoutonCarre(
                    icone = Icons.Filled.Close,
                    description = "Quitter la tournée",
                    onClick = onFermer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tournee.clientNom.ifBlank { "Tournée" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Le compteur : c'est ce que la feuille va écrire.
                    Text(
                        text = "${tournee.nombreCochees} cochée${pluriel(tournee.nombreCochees)} " +
                            "sur ${toutes.size} visite${pluriel(toutes.size)} due${pluriel(toutes.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { onCocherTout(toutes) },
                    enabled = toutes.isNotEmpty(),
                    modifier = Modifier.heightIn(min = LocalCibles.current.action),
                ) {
                    Text(text = if (tournee.toutCoche(toutes)) "Tout décocher" else "Tout cocher")
                }
            }

            if (toutes.isEmpty()) {
                Encart(
                    texte = "Ce site ne doit plus aucune visite : tout ce qui était en retard " +
                        "ou à faire a été consigné.",
                    modifier = Modifier.padding(horizontal = MargeEcran),
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MargeEcran,
                    end = MargeEcran,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tournee.zones.forEach { zone ->
                    item(key = "zone-${zone.zone}") {
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${zone.zone.ifEmpty { "Sans zone" }} · ${zone.echeances.size}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = { onCocherTout(zone.echeances) },
                                modifier = Modifier.heightIn(min = LocalCibles.current.action),
                            ) {
                                Text(
                                    text = if (tournee.toutCoche(zone.echeances)) {
                                        "Décocher la zone"
                                    } else {
                                        "Cocher la zone"
                                    },
                                )
                            }
                        }
                    }
                    items(
                        items = zone.echeances,
                        key = { "${it.equipement.id}-${it.gamme.id}" },
                    ) { echeance ->
                        LigneVisiteTournee(
                            echeance = echeance,
                            cochee = tournee.cochee(echeance),
                            aujourdhui = aujourdhui,
                            onBasculer = { onBasculer(echeance) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Une visite qu'on coche. Toute la ligne se touche, et la case suit le mode gants,
 * pour la raison de la sélection du parc : on coche avec des gants, sur un toit.
 */
@Composable
private fun LigneVisiteTournee(
    echeance: EcheanceMaintenance,
    cochee: Boolean,
    aujourdhui: LocalDate,
    onBasculer: () -> Unit,
) {
    val statuts = LocalStatuts.current
    val teinte: Color = when (echeance.statut(aujourdhui)) {
        StatutEcheance.EN_RETARD -> statuts.urgence
        StatutEcheance.A_FAIRE -> statuts.aValider
        StatutEcheance.A_VENIR -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                    text = echeance.equipement.nom,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${echeance.gamme.libelle} · ${echeance.gamme.periodicite.libelle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = libelleEcheance(echeance, aujourdhui),
                style = MaterialTheme.typography.bodySmall,
                color = teinte,
            )
        }
    }
}

/**
 * Choisir le site de la tournée, parmi ceux qui doivent quelque chose.
 *
 * Seuls les sites **en retard ou à faire** y paraissent : un site dont tout est à
 * venir ouvrirait une tournée sans une case à cocher.
 */
@Composable
internal fun DialogueChoixSite(
    sites: List<SiteDu>,
    onChoisir: (String) -> Unit,
    onFermer: () -> Unit,
) {
    val statuts = LocalStatuts.current
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Tournée sur un site") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                sites.forEach { site ->
                    Carte(onClick = { onChoisir(site.clientId) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = site.clientNom.ifBlank { "Client sans nom" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (site.enRetard > 0) {
                                Puce(texte = "${site.enRetard} en retard", couleur = statuts.urgence)
                            } else {
                                Puce(texte = "${site.aFaire} à faire", couleur = statuts.aValider)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onFermer) { Text(text = "Annuler") }
        },
    )
}

/**
 * Consigner les visites cochées : une date, un auteur, des remarques — **communs**
 * à toutes.
 *
 * La **date est modifiable** pour la raison de [FeuilleVisite] : une tournée se
 * consigne le soir, parfois le lendemain, et la dater du jour de la saisie
 * décalerait le plan de tout le site d'un jour à chaque fois.
 *
 * Les **points des gammes ne s'y cochent pas** : même pour une visite seule, ils
 * servent à s'y retrouver et ne sont pas enregistrés. Les proposer ici en lot
 * aurait laissé croire le contraire, et la feuille le dit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeuilleVisitesTournee(
    nombre: Int,
    clientNom: String,
    techniciens: List<Technicien>,
    auteurPropose: Technicien?,
    onConsigner: (LocalDate, Technicien?, String) -> Unit,
    onFermer: () -> Unit,
    aujourdhui: LocalDate = LocalDate.now(),
) {
    var faitLe by remember { mutableStateOf(aujourdhui) }
    var notes by remember { mutableStateOf("") }
    var selecteurOuvert by remember { mutableStateOf(false) }
    // Le choix fait à la main l'emporte ; tant qu'il n'y en a pas, l'auteur
    // proposé suit les réglages, qui arrivent parfois après l'ouverture.
    var auteurChoisi by remember { mutableStateOf<Technicien?>(null) }
    var choixFait by remember { mutableStateOf(false) }
    val auteur = if (choixFait) auteurChoisi else auteurPropose
    // Le nom des Réglages qui n'est pas dans la liste se montre quand même.
    val proposes = if (auteurPropose != null && techniciens.none { it.nom == auteurPropose.nom }) {
        listOf(auteurPropose) + techniciens
    } else {
        techniciens
    }

    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = libelleConsigner(nombre),
                style = MaterialTheme.typography.titleLarge,
            )
            if (clientNom.isNotBlank()) {
                Text(
                    text = clientNom,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BoutonContour(
                texte = "Faites le ${jourCourt(faitLe)}",
                onClick = { selecteurOuvert = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Changez la date si vous consignez la tournée d'hier : la dater du " +
                    "jour de la saisie décalerait le plan de tout le site.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Technicien",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = auteur == null,
                    onClick = {
                        choixFait = true
                        auteurChoisi = null
                    },
                    label = { Text(text = "Personne") },
                )
                proposes.forEach { technicien ->
                    FilterChip(
                        selected = auteur?.nom == technicien.nom,
                        onClick = {
                            choixFait = true
                            auteurChoisi = technicien
                        },
                        label = { Text(text = technicien.nom) },
                    )
                }
            }

            ChampTexte(
                libelle = "Remarques communes",
                valeur = notes,
                onValeur = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                lignes = 3,
            )

            Text(
                text = "Une visite est écrite par machine cochée, avec cette date, ce " +
                    "technicien et ces remarques. Les points des gammes ne se cochent pas " +
                    "ici : même pour une visite seule, ils servent à s'y retrouver et ne " +
                    "sont pas enregistrés.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BoutonPlein(
                texte = libelleConsigner(nombre),
                onClick = { onConsigner(faitLe, auteur, notes) },
                actif = nombre > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            EspaceVertical(24)
        }
    }

    if (selecteurOuvert) {
        SelecteurDate(
            date = faitLe,
            onDateChoisie = {
                faitLe = it
                selecteurOuvert = false
            },
            onFermer = { selecteurOuvert = false },
        )
    }
}

/** « Consigner 1 visite », « Consigner 12 visites » : le geste dit ce qu'il écrit. */
private fun libelleConsigner(nombre: Int): String = when (nombre) {
    0 -> "Consigner les visites"
    else -> "Consigner $nombre visite${pluriel(nombre)}"
}
