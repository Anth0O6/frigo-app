package com.frigopro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Encart

/**
 * La sauvegarde, dans les Réglages.
 *
 * Elle était derrière le bouton **⋮** de la barre de la tournée, et elle y était
 * introuvable — au point que la seule vue qui la portait était celle du jour :
 * depuis « Maintenant » ou « Semaine », le seul filet contre un téléphone perdu
 * ou cassé était hors d'atteinte. C'est la fonction la plus importante de
 * l'application et c'était la plus cachée, ce qui est exactement l'inverse de
 * l'ordre à tenir.
 *
 * Elle est donc une page des Réglages, où l'on va la chercher, et elle nomme ce
 * qu'elle emporte : une liste vaut mieux qu'une promesse, parce que c'est ce qui
 * permet de juger, avant de changer de téléphone, s'il manque quelque chose.
 *
 * L'export passe par le sélecteur du système : c'est l'utilisateur qui choisit
 * où le fichier atterrit, et l'application n'a besoin d'aucune permission de
 * stockage.
 */
@Composable
fun SectionSauvegarde(
    modifier: Modifier = Modifier,
    viewModel: SauvegardeViewModel = viewModel(factory = SauvegardeViewModel.Factory),
) {
    val message by viewModel.message.collectAsStateWithLifecycle()

    val enregistrer = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination -> destination?.let(viewModel::onExporterVers) }

    // `*/*` plutôt qu'un type précis : selon l'endroit où la sauvegarde a été
    // rangée, le système lui attribue parfois un autre type, et un filtre
    // strict la rendrait tout simplement invisible dans le sélecteur. C'est
    // aussi ce qui laisse restaurer un ancien export `.json`.
    val restaurer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source -> source?.let(viewModel::onRestaurerDepuis) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Encart(
            texte = "Vos données ne vivent que sur ce téléphone. Une archive emporte tout : " +
                "les tournées, les clients et leurs machines, les photos et les signatures, " +
                "les devis, les factures et leurs numéros, le magasin, le catalogue et les " +
                "réglages. Rangez-la ailleurs que sur l'appareil.",
            icone = Icons.Filled.Save,
        )
        BoutonPlein(
            texte = "Sauvegarder mes données",
            onClick = { enregistrer.launch(viewModel.nomFichierPropose()) },
            modifier = Modifier.fillMaxWidth(),
        )
        BoutonContour(
            texte = "Restaurer une sauvegarde",
            onClick = { restaurer.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            // La fusion est une propriété qu'il faut dire : c'est elle qui rend
            // une restauration sans danger, et donc faisable sans hésiter.
            text = "Une restauration ajoute et met à jour, elle n'efface rien : restaurer " +
                "deux fois le même fichier ne crée aucun doublon, et une archive ancienne " +
                "ne fait pas disparaître ce que vous avez saisi depuis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
