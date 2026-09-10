package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Client
import com.frigopro.app.ui.theme.FrigoProTheme

/** Point d'entrée de l'onglet, branché sur le [ClientsViewModel]. */
@Composable
fun ClientsRoute(
    modifier: Modifier = Modifier,
    viewModel: ClientsViewModel = viewModel(factory = ClientsViewModel.Factory),
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val fiche by viewModel.fiche.collectAsStateWithLifecycle()

    ClientsScreen(
        clients = clients,
        onNouveauClient = viewModel::onNouveauClient,
        onOuvrirFiche = viewModel::onOuvrirFiche,
        modifier = modifier,
    )

    fiche?.let { etat ->
        FicheClient(
            etat = etat,
            onEtatChange = viewModel::onFicheChange,
            onEnregistrer = viewModel::onEnregistrerFiche,
            onFermer = viewModel::onFermerFiche,
        )
    }
}

/** Écran sans état : le carnet, trié alphabétiquement par le dépôt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    clients: List<Client>,
    onNouveauClient: () -> Unit,
    onOuvrirFiche: (Client) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas ;
        // l'y ajouter ici la compterait deux fois.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Clients") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNouveauClient) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = "Ajouter un client",
                )
            }
        },
    ) { innerPadding ->
        if (clients.isEmpty()) {
            CarnetVide(
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
                items(items = clients, key = { it.id }) { client ->
                    ClientCard(client = client, onClick = { onOuvrirFiche(client) })
                }
            }
        }
    }
}

/**
 * Fiche résumée d'un client. Le corps ouvre la fiche complète ; les deux icônes
 * de droite appellent et ouvrent l'itinéraire sans passer par elle — depuis le
 * carnet comme depuis la tournée, agir doit tenir en un geste.
 */
@Composable
fun ClientCard(
    client: Client,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexte = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            ) {
                Text(
                    text = client.nom,
                    style = MaterialTheme.typography.titleMedium,
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
                        text = client.adresseComplete,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (client.appelable) {
                    Text(
                        text = client.telephone,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (client.appelable) {
                IconButton(onClick = { contexte.appeler(client.telephone) }) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = "Appeler ${client.nom}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (client.localisable) {
                IconButton(onClick = { contexte.ouvrirItineraire(client.adresseComplete) }) {
                    Icon(
                        imageVector = Icons.Filled.Directions,
                        contentDescription = "Itinéraire vers ${client.nom}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

/**
 * Le carnet se remplissant tout seul, un carnet vide signifie surtout qu'aucune
 * intervention n'a encore été saisie : le dire évite de chercher un bouton.
 */
@Composable
private fun CarnetVide(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "Aucun client pour l'instant.\n" +
                "Le carnet se remplit à mesure que vous saisissez des interventions.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientsScreenPreview() {
    FrigoProTheme {
        Surface {
            ClientsScreen(
                clients = listOf(
                    Client(
                        id = "1",
                        nom = "Boucherie Lemoine",
                        ville = "Rouen",
                        adresse = "12 rue des Carmes",
                        telephone = "02 35 00 00 00",
                    ),
                    Client(id = "2", nom = "Supérette Val-Fleuri", ville = "Elbeuf"),
                    Client(
                        id = "3",
                        nom = "Traiteur Delaunay",
                        ville = "Barentin",
                        adresse = "5 place de la Gare",
                    ),
                ),
                onNouveauClient = {},
                onOuvrirFiche = {},
            )
        }
    }
}
