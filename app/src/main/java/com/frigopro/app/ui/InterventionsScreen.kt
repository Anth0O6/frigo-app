package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.TypePanne
import com.frigopro.app.ui.theme.FrigoProTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val FORMAT_HEURE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Point d'entrée de l'écran, branché sur le [InterventionsViewModel]. */
@Composable
fun InterventionsRoute(
    modifier: Modifier = Modifier,
    viewModel: InterventionsViewModel = viewModel(),
) {
    val interventions by viewModel.interventions.collectAsStateWithLifecycle()
    InterventionsScreen(
        interventions = interventions,
        onAjouterIntervention = viewModel::onAjouterIntervention,
        modifier = modifier,
    )
}

/** Écran sans état : liste des interventions du jour et bouton d'ajout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterventionsScreen(
    interventions: List<Intervention>,
    onAjouterIntervention: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Interventions du jour")
                        Text(
                            text = "${interventions.size} rendez-vous planifiés",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAjouterIntervention) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Ajouter une intervention",
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = interventions, key = { it.id }) { intervention ->
                InterventionCard(intervention = intervention)
            }
        }
    }
}

/** Carte présentant une intervention : heure, client, ville et type de panne. */
@Composable
fun InterventionCard(
    intervention: Intervention,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = intervention.heure.format(FORMAT_HEURE),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = intervention.client,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.width(16.dp),
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
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = intervention.typePanne.libelle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InterventionsScreenPreview() {
    FrigoProTheme {
        Surface {
            InterventionsScreen(
                interventions = listOf(
                    Intervention(1L, LocalTime.of(8, 30), "Boucherie Lemoine", "Rouen", TypePanne.FUITE_FLUIDE),
                    Intervention(2L, LocalTime.of(10, 0), "Supérette Val-Fleuri", "Elbeuf", TypePanne.COMPRESSEUR),
                ),
                onAjouterIntervention = {},
            )
        }
    }
}
