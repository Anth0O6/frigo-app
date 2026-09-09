package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.data.TypePanne
import com.frigopro.app.ui.theme.FrigoProTheme
import java.time.LocalDate
import java.time.LocalTime

/** Saisie de noms propres : majuscule initiale, clavier qui enchaîne les champs. */
private val OPTIONS_CLAVIER = KeyboardOptions(
    capitalization = KeyboardCapitalization.Words,
    imeAction = ImeAction.Next,
)

/** Les notes sont des phrases, sur plusieurs lignes : pas d'enchaînement de champ. */
private val OPTIONS_NOTES = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    imeAction = ImeAction.Default,
)

/**
 * Feuille de saisie d'une intervention, en création comme en édition.
 *
 * Composable sans état : la saisie courante est remontée telle quelle via
 * [onEtatChange], le ViewModel restant seul propriétaire du formulaire.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormulaireIntervention(
    etat: EtatFormulaire,
    onEtatChange: (EtatFormulaire) -> Unit,
    onValider: () -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choixDateOuvert by rememberSaveable { mutableStateOf(false) }
    var choixHeureOuvert by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onFermer,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (etat.estCreation) "Nouvelle intervention" else "Modifier l'intervention",
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedButton(
                onClick = { choixDateOuvert = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = libelleDate(etat.date))
            }

            OutlinedButton(
                onClick = { choixHeureOuvert = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Heure de passage : ${etat.heure.format(FORMAT_HEURE)}")
            }

            OutlinedTextField(
                value = etat.client,
                onValueChange = { onEtatChange(etat.copy(client = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Client") },
                singleLine = true,
                keyboardOptions = OPTIONS_CLAVIER,
            )

            OutlinedTextField(
                value = etat.ville,
                onValueChange = { onEtatChange(etat.copy(ville = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Ville") },
                singleLine = true,
                keyboardOptions = OPTIONS_CLAVIER,
            )

            Text(
                text = "Type de panne",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TypePanne.entries.forEach { type ->
                    FilterChip(
                        selected = etat.typePanne == type,
                        onClick = { onEtatChange(etat.copy(typePanne = type)) },
                        label = { Text(text = type.libelle) },
                    )
                }
            }

            Text(
                text = "Statut",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatutIntervention.entries.forEach { statut ->
                    FilterChip(
                        selected = etat.statut == statut,
                        onClick = { onEtatChange(etat.copy(statut = statut)) },
                        label = { Text(text = statut.libelle) },
                    )
                }
            }

            OutlinedTextField(
                value = etat.notes,
                onValueChange = { onEtatChange(etat.copy(notes = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Notes de passage") },
                minLines = 3,
                keyboardOptions = OPTIONS_NOTES,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!etat.estCreation) {
                    TextButton(
                        onClick = onSupprimer,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Supprimer")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onValider, enabled = etat.estValide) {
                    Text(text = if (etat.estCreation) "Ajouter" else "Enregistrer")
                }
            }
        }
    }

    if (choixDateOuvert) {
        SelecteurDate(
            date = etat.date,
            onDateChoisie = {
                onEtatChange(etat.copy(date = it))
                choixDateOuvert = false
            },
            onFermer = { choixDateOuvert = false },
        )
    }

    if (choixHeureOuvert) {
        SelecteurHeure(
            heure = etat.heure,
            onHeureChoisie = {
                onEtatChange(etat.copy(heure = it))
                choixHeureOuvert = false
            },
            onFermer = { choixHeureOuvert = false },
        )
    }
}

/**
 * Boîte de dialogue d'horloge Material 3.
 *
 * `material3` 1.3 ne fournit pas encore de `TimePickerDialog` prêt à l'emploi :
 * le [TimePicker] est donc posé dans un [Dialog] dont on relâche la largeur
 * imposée, faute de quoi le cadran est rogné sur les petits écrans.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelecteurHeure(
    heure: LocalTime,
    onHeureChoisie: (LocalTime) -> Unit,
    onFermer: () -> Unit,
) {
    val etatHeure = rememberTimePickerState(
        initialHour = heure.hour,
        initialMinute = heure.minute,
        is24Hour = true,
    )

    Dialog(
        onDismissRequest = onFermer,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Heure de passage",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                TimePicker(state = etatHeure)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onFermer) { Text(text = "Annuler") }
                    TextButton(
                        onClick = { onHeureChoisie(LocalTime.of(etatHeure.hour, etatHeure.minute)) },
                    ) {
                        Text(text = "Valider")
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun FormulaireInterventionPreview() {
    FrigoProTheme {
        FormulaireIntervention(
            etat = EtatFormulaire(
                id = "1",
                date = LocalDate.now(),
                heure = LocalTime.of(10, 30),
                client = "Boucherie Lemoine",
                ville = "Rouen",
                typePanne = TypePanne.COMPRESSEUR,
                statut = StatutIntervention.EN_COURS,
                notes = "Manque de fluide, à recontrôler la semaine prochaine.",
            ),
            onEtatChange = {},
            onValider = {},
            onSupprimer = {},
            onFermer = {},
        )
    }
}
