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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import com.frigopro.app.data.TypeIntervention
import com.frigopro.app.ui.theme.FrigoProTheme

/** Point d'entrée de l'onglet, branché sur le [ReglagesViewModel]. */
@Composable
fun ReglagesRoute(
    modifier: Modifier = Modifier,
    viewModel: ReglagesViewModel = viewModel(factory = ReglagesViewModel.Factory),
) {
    val types by viewModel.types.collectAsStateWithLifecycle()
    val dialogue by viewModel.dialogue.collectAsStateWithLifecycle()

    ReglagesScreen(
        types = types,
        onAjouterType = viewModel::onAjouterType,
        onRenommerType = viewModel::onRenommerType,
        onSupprimerType = viewModel::onSupprimerType,
        modifier = modifier,
    )

    // Un intitulé déjà porté par un *autre* type est refusé ; renommer un type
    // en lui-même doit rester possible, ne serait-ce que pour corriger la casse.
    val dejaPris = { intitule: String, exclu: String? ->
        types.any { it.libelle.equals(intitule, ignoreCase = true) && it.id != exclu }
    }

    when (val ouvert = dialogue) {
        DialogueReglages.Creation -> DialogueIntitule(
            titre = "Nouveau type d'intervention",
            libelleAction = "Ajouter",
            messageConflit = MESSAGE_TYPE_EXISTANT,
            estDejaPris = { dejaPris(it, null) },
            onValider = viewModel::onValiderIntitule,
            onFermer = viewModel::onFermerDialogue,
        )

        is DialogueReglages.Renommage -> DialogueIntitule(
            titre = "Renommer le type",
            libelleAction = "Enregistrer",
            messageConflit = MESSAGE_TYPE_EXISTANT,
            intituleInitial = ouvert.type.libelle,
            estDejaPris = { dejaPris(it, ouvert.type.id) },
            onValider = viewModel::onValiderIntitule,
            onFermer = viewModel::onFermerDialogue,
        )

        is DialogueReglages.Suppression -> ConfirmationSuppression(
            type = ouvert.type,
            onConfirmer = viewModel::onConfirmerSuppression,
            onFermer = viewModel::onFermerDialogue,
        )

        null -> Unit
    }
}

private const val MESSAGE_TYPE_EXISTANT = "Ce type existe déjà."

/** Écran sans état : pour l'instant, la seule liste des types d'intervention. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReglagesScreen(
    types: List<TypeIntervention>,
    onAjouterType: () -> Unit,
    onRenommerType: (TypeIntervention) -> Unit,
    onSupprimerType: (TypeIntervention) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets pose déjà la marge du bas ; voir `FrigoProApp`.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Réglages") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAjouterType) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Ajouter un type d'intervention",
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
            item {
                Column {
                    Text(
                        text = "Types d'intervention",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (types.isEmpty()) {
                            "Aucun type pour l'instant. Ajoutez-en ici, ou au moment " +
                                "de saisir une intervention."
                        } else {
                            "Renommer un type met à jour toutes les interventions " +
                                "qui l'utilisent, y compris les anciennes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = types, key = { it.id }) { type ->
                LigneType(
                    type = type,
                    onRenommer = { onRenommerType(type) },
                    onSupprimer = { onSupprimerType(type) },
                )
            }
        }
    }
}

@Composable
private fun LigneType(
    type: TypeIntervention,
    onRenommer: () -> Unit,
    onSupprimer: () -> Unit,
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
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = type.libelle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRenommer) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Renommer ${type.libelle}",
                )
            }
            IconButton(onClick = onSupprimer) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Supprimer ${type.libelle}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Supprimer un type ne perd aucune donnée : le dire évite d'hésiter devant le
 * bouton, et évite surtout de croire qu'on va effacer ses tournées.
 */
@Composable
private fun ConfirmationSuppression(
    type: TypeIntervention,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Supprimer « ${type.libelle} » ?") },
        text = {
            Text(
                text = "Le type quitte la liste. Les interventions qui l'utilisaient " +
                    "gardent leur intitulé : rien n'est perdu.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmer) { Text(text = "Supprimer") }
        },
        dismissButton = {
            TextButton(onClick = onFermer) { Text(text = "Annuler") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ReglagesScreenPreview() {
    FrigoProTheme {
        Surface {
            ReglagesScreen(
                types = listOf(
                    TypeIntervention(id = "t1", libelle = "Entretien annuel"),
                    TypeIntervention(id = "t2", libelle = "Fuite de fluide"),
                    TypeIntervention(id = "t3", libelle = "Mise en service"),
                ),
                onAjouterType = {},
                onRenommerType = {},
                onSupprimerType = {},
            )
        }
    }
}
