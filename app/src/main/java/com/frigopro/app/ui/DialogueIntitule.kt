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
 * Saisie d'un intitulé, en création comme en renommage.
 *
 * Partagée par les types d'intervention et les machines : nommer, renommer,
 * refuser un doublon, tout cela se fait de la même façon, et des boîtes de
 * dialogue jumelles finiraient par diverger. Seuls le vocabulaire et le test du
 * doublon changent, donc seuls eux sont des paramètres.
 *
 * L'état de saisie est réinitialisé dès que [intituleInitial] change, faute de
 * quoi renommer un type après un autre reprendrait l'intitulé du précédent.
 */
@Composable
fun DialogueIntitule(
    titre: String,
    libelleAction: String,
    libelleChamp: String = "Intitulé",
    messageConflit: String = "Cet intitulé existe déjà.",
    intituleInitial: String = "",
    estDejaPris: (String) -> Boolean = { false },
    onValider: (String) -> Unit,
    onFermer: () -> Unit,
) {
    var intitule by rememberSaveable(intituleInitial) { mutableStateOf(intituleInitial) }
    // Deux entrées du même intitulé donneraient deux boutons identiques dans le
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
                label = { Text(text = libelleChamp) },
                singleLine = true,
                isError = conflit,
                supportingText = if (conflit) {
                    { Text(text = messageConflit) }
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
