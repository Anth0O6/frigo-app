package com.frigopro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Menu de sauvegarde, posé dans la barre de la tournée.
 *
 * Les données ne vivant que sur ce téléphone, l'export est le seul filet contre
 * un appareil perdu ou cassé. Il passe par le sélecteur du système : c'est
 * l'utilisateur qui choisit où le fichier atterrit, et l'application n'a besoin
 * d'aucune permission de stockage.
 */
@Composable
fun MenuSauvegarde(
    viewModel: SauvegardeViewModel = viewModel(factory = SauvegardeViewModel.Factory),
) {
    var ouvert by rememberSaveable { mutableStateOf(false) }
    val message by viewModel.message.collectAsStateWithLifecycle()

    val enregistrer = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { destination -> destination?.let(viewModel::onExporterVers) }

    // `*/*` plutôt que `application/json` : selon l'endroit où la sauvegarde a
    // été rangée, le système lui attribue parfois un autre type, et un filtre
    // strict la rendrait tout simplement invisible dans le sélecteur.
    val restaurer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source -> source?.let(viewModel::onRestaurerDepuis) }

    IconButton(onClick = { ouvert = true }) {
        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Plus d'options")
    }
    DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
        DropdownMenuItem(
            text = { Text(text = "Sauvegarder mes données") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Save, contentDescription = null) },
            onClick = {
                ouvert = false
                enregistrer.launch(viewModel.nomFichierPropose())
            },
        )
        DropdownMenuItem(
            text = { Text(text = "Restaurer une sauvegarde") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Restore, contentDescription = null) },
            onClick = {
                ouvert = false
                restaurer.launch(arrayOf("*/*"))
            },
        )
    }

    message?.let { texte ->
        AlertDialog(
            onDismissRequest = viewModel::onMessageLu,
            confirmButton = {
                TextButton(onClick = viewModel::onMessageLu) { Text(text = "D'accord") }
            },
            title = { Text(text = "Sauvegarde") },
            text = { Text(text = texte) },
        )
    }
}
