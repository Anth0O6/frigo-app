package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.data.TypePanne
import com.frigopro.app.ui.theme.FrigoProTheme
import java.time.LocalDate
import java.time.LocalTime

/** Point d'entrée de l'écran, branché sur le [InterventionsViewModel]. */
@Composable
fun InterventionsRoute(
    modifier: Modifier = Modifier,
    viewModel: InterventionsViewModel = viewModel(factory = InterventionsViewModel.Factory),
) {
    val jour by viewModel.jour.collectAsStateWithLifecycle()
    val interventions by viewModel.interventions.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val formulaire by viewModel.formulaire.collectAsStateWithLifecycle()

    InterventionsScreen(
        jour = jour,
        interventions = interventions,
        onJourPrecedent = viewModel::onJourPrecedent,
        onJourSuivant = viewModel::onJourSuivant,
        onJourChoisi = viewModel::onJourChoisi,
        onNouvelleIntervention = viewModel::onNouvelleIntervention,
        onModifierIntervention = viewModel::onModifierIntervention,
        onChangerStatut = viewModel::onChangerStatut,
        modifier = modifier,
    )

    formulaire?.let { etat ->
        FormulaireIntervention(
            etat = etat,
            clients = clients,
            onEtatChange = viewModel::onFormulaireChange,
            onClientChoisi = viewModel::onClientChoisi,
            onValider = viewModel::onValiderFormulaire,
            onSupprimer = viewModel::onSupprimerIntervention,
            onFermer = viewModel::onFermerFormulaire,
        )
    }
}

/** Écran sans état : tournée d'une journée, navigable jour par jour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterventionsScreen(
    jour: LocalDate,
    interventions: List<Intervention>,
    onJourPrecedent: () -> Unit,
    onJourSuivant: () -> Unit,
    onJourChoisi: (LocalDate) -> Unit,
    onNouvelleIntervention: () -> Unit,
    onModifierIntervention: (Intervention) -> Unit,
    onChangerStatut: (Intervention) -> Unit,
    modifier: Modifier = Modifier,
) {
    var calendrierOuvert by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(text = "Interventions") },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                BarreJour(
                    jour = jour,
                    sousTitre = sousTitre(interventions),
                    onPrecedent = onJourPrecedent,
                    onSuivant = onJourSuivant,
                    onOuvrirCalendrier = { calendrierOuvert = true },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNouvelleIntervention) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Ajouter une intervention",
                )
            }
        },
    ) { innerPadding ->
        if (interventions.isEmpty()) {
            JourneeVide(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = interventions, key = { it.id }) { intervention ->
                    InterventionCard(
                        intervention = intervention,
                        onClick = { onModifierIntervention(intervention) },
                        onChangerStatut = { onChangerStatut(intervention) },
                    )
                }
            }
        }
    }

    if (calendrierOuvert) {
        SelecteurDate(
            date = jour,
            onDateChoisie = {
                onJourChoisi(it)
                calendrierOuvert = false
            },
            onFermer = { calendrierOuvert = false },
        )
    }
}

/**
 * Navigation de journée : une flèche de chaque côté, et le libellé central
 * ouvre le calendrier pour sauter directement à une date lointaine.
 */
@Composable
private fun BarreJour(
    jour: LocalDate,
    sousTitre: String,
    onPrecedent: () -> Unit,
    onSuivant: () -> Unit,
    onOuvrirCalendrier: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrecedent) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "Jour précédent",
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onOuvrirCalendrier)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = titreJour(jour),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Choisir une date",
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(text = sousTitre, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onSuivant) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Jour suivant",
                )
            }
        }
    }
}

/**
 * Carte d'une intervention. Le corps ouvre le formulaire ; l'icône de droite
 * fait avancer le statut sans le rouvrir, pour marquer un passage terminé
 * d'un seul geste.
 */
@Composable
fun InterventionCard(
    intervention: Intervention,
    onClick: () -> Unit,
    onChangerStatut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminee = intervention.statut == StatutIntervention.TERMINEE

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (terminee) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = intervention.heure.format(FORMAT_HEURE),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (terminee) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = intervention.client,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (terminee) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = intervention.ville,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = intervention.typePanne.libelle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    if (intervention.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = intervention.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            BoutonStatut(statut = intervention.statut, onClick = onChangerStatut)
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

/** Icône d'avancement, qui passe au statut suivant à chaque appui. */
@Composable
private fun BoutonStatut(
    statut: StatutIntervention,
    onClick: () -> Unit,
) {
    val icone = when (statut) {
        StatutIntervention.A_FAIRE -> Icons.Filled.RadioButtonUnchecked
        StatutIntervention.EN_COURS -> Icons.Filled.Pending
        StatutIntervention.TERMINEE -> Icons.Filled.CheckCircle
    }
    val teinte: Color = when (statut) {
        StatutIntervention.A_FAIRE -> MaterialTheme.colorScheme.onSurfaceVariant
        StatutIntervention.EN_COURS -> MaterialTheme.colorScheme.tertiary
        StatutIntervention.TERMINEE -> MaterialTheme.colorScheme.primary
    }

    IconButton(onClick = onClick) {
        Icon(
            imageVector = icone,
            contentDescription = "${statut.libelle} — toucher pour changer de statut",
            tint = teinte,
        )
    }
}

/** Affiché quand la journée consultée ne contient aucune intervention. */
@Composable
private fun JourneeVide(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "Aucune intervention ce jour-là.\nTouchez + pour en planifier une.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * « rendez-vous » est invariable ; seul l'accord du participe change. Le
 * décompte des interventions terminées répond à la question qu'un technicien
 * se pose en cours de journée : ce qu'il lui reste.
 */
private fun sousTitre(interventions: List<Intervention>): String {
    if (interventions.isEmpty()) return "Aucun rendez-vous"

    val total = interventions.size
    val base = if (total == 1) "1 rendez-vous" else "$total rendez-vous"
    val terminees = interventions.count { it.statut == StatutIntervention.TERMINEE }

    return when {
        terminees == 0 -> base
        terminees == total -> "$base · tout est terminé"
        terminees == 1 -> "$base · 1 terminée"
        else -> "$base · $terminees terminées"
    }
}

@Preview(showBackground = true)
@Composable
private fun InterventionsScreenPreview() {
    FrigoProTheme {
        Surface {
            InterventionsScreen(
                jour = LocalDate.now(),
                interventions = listOf(
                    Intervention(
                        id = "1",
                        date = LocalDate.now(),
                        heure = LocalTime.of(8, 30),
                        client = "Boucherie Lemoine",
                        ville = "Rouen",
                        typePanne = TypePanne.FUITE_FLUIDE,
                        statut = StatutIntervention.TERMINEE,
                    ),
                    Intervention(
                        id = "2",
                        date = LocalDate.now(),
                        heure = LocalTime.of(10, 0),
                        client = "Supérette Val-Fleuri",
                        ville = "Elbeuf",
                        typePanne = TypePanne.COMPRESSEUR,
                        statut = StatutIntervention.EN_COURS,
                        notes = "Compresseur bruyant, pièce commandée.",
                    ),
                    Intervention(
                        id = "3",
                        date = LocalDate.now(),
                        heure = LocalTime.of(14, 15),
                        client = "Traiteur Delaunay",
                        ville = "Barentin",
                        typePanne = TypePanne.GIVRAGE,
                    ),
                ),
                onJourPrecedent = {},
                onJourSuivant = {},
                onJourChoisi = {},
                onNouvelleIntervention = {},
                onModifierIntervention = {},
                onChangerStatut = {},
            )
        }
    }
}
