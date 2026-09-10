package com.frigopro.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/** Majuscule initiale, et rien qui suive : un intitulé est le seul champ. */
private val OPTIONS_INTITULE = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    imeAction = ImeAction.Done,
)

/**
 * Saisie de l'intitulé d'un type d'intervention, en création comme en
 * renommage. Partagée par le formulaire et l'écran Réglages : ajouter un type
 * et le corriger demandent la même chose, et deux boîtes de dialogue jumelles
 * finiraient par diverger.
 *
 * L'état de saisie est réinitialisé dès que [intituleInitial] change, faute de
 * quoi renommer un type après un autre reprendrait l'intitulé du précédent.
 */
@Composable
fun DialogueType(
    titre: String,
    libelleAction: String,
    intituleInitial: String = "",
    estDejaPris: (String) -> Boolean = { false },
    onValider: (String) -> Unit,
    onFermer: () -> Unit,
) {
    var intitule by rememberSaveable(intituleInitial) { mutableStateOf(intituleInitial) }
    // Deux types du même intitulé donneraient deux boutons identiques dans le
    // formulaire, impossibles à distinguer. Mieux vaut l'empêcher que l'expliquer.
    val conflit = estDejaPris(intitule.trim())

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = titre) },
        text = {
            OutlinedTextField(
                value = intitule,
                onValueChange = { intitule = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Intitulé") },
                singleLine = true,
                isError = conflit,
                supportingText = if (conflit) {
                    { Text(text = "Ce type existe déjà.") }
                } else {
                    null
                },
                keyboardOptions = OPTIONS_INTITULE,
            )
        },
        confirmButton = {
            TextButton(onClick = { onValider(intitule) }, enabled = intitule.isNotBlank() && !conflit) {
                Text(text = libelleAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onFermer) { Text(text = "Annuler") }
        },
    )
}
