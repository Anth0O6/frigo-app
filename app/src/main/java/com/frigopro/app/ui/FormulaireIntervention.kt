package com.frigopro.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frigopro.app.data.Client
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.data.TypeIntervention
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
    clients: List<Client>,
    types: List<TypeIntervention>,
    onEtatChange: (EtatFormulaire) -> Unit,
    onClientChoisi: (Client) -> Unit,
    onTypeChoisi: (TypeIntervention?) -> Unit,
    onNouveauType: (String) -> Unit,
    onValider: () -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choixDateOuvert by rememberSaveable { mutableStateOf(false) }
    var choixHeureOuvert by rememberSaveable { mutableStateOf(false) }
    var nouveauTypeOuvert by rememberSaveable { mutableStateOf(false) }

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
                // Modifier le nom détache du carnet : ce n'est plus le même client.
                onValueChange = { onEtatChange(etat.copy(client = it, clientId = null)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Client") },
                singleLine = true,
                keyboardOptions = OPTIONS_CLAVIER,
                trailingIcon = {
                    if (etat.clientId != null) {
                        Icon(
                            imageVector = Icons.Filled.ContactPage,
                            contentDescription = "Client du carnet",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )

            val propositions = suggestions(clients, etat)
            if (propositions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    propositions.forEach { client ->
                        SuggestionClient(client = client, onClick = { onClientChoisi(client) })
                    }
                }
            }

            OutlinedTextField(
                value = etat.ville,
                onValueChange = { onEtatChange(etat.copy(ville = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Ville") },
                singleLine = true,
                keyboardOptions = OPTIONS_CLAVIER,
            )

            Text(
                text = "Type d'intervention",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                types.forEach { type ->
                    FilterChip(
                        selected = etat.typeId == type.id,
                        // Retoucher le type choisi l'enlève : il est facultatif,
                        // et se tromper ne doit pas être définitif.
                        onClick = { onTypeChoisi(if (etat.typeId == type.id) null else type) },
                        label = { Text(text = type.libelle) },
                    )
                }
                // Intitulé venu d'avant la liste, ou dont le type a été
                // supprimé : il s'affiche quand même, sinon l'ouvrir pour
                // changer l'heure effacerait silencieusement le type.
                if (etat.typeId == null && etat.typeLibelle.isNotBlank()) {
                    FilterChip(
                        selected = true,
                        onClick = { onTypeChoisi(null) },
                        label = { Text(text = etat.typeLibelle) },
                    )
                }
                AssistChip(
                    onClick = { nouveauTypeOuvert = true },
                    label = { Text(text = "Nouveau type") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
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

    if (nouveauTypeOuvert) {
        NouveauType(
            onValider = {
                onNouveauType(it)
                nouveauTypeOuvert = false
            },
            onFermer = { nouveauTypeOuvert = false },
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
 * Saisie d'un nouveau type d'intervention, sans quitter le formulaire.
 *
 * Le type créé rejoint la liste et sera proposé aux interventions suivantes :
 * c'est ainsi que le technicien se constitue son vocabulaire, au fil des
 * tournées plutôt qu'en remplissant un écran de configuration d'avance.
 */
@Composable
private fun NouveauType(
    onValider: (String) -> Unit,
    onFermer: () -> Unit,
) {
    var intitule by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Nouveau type d'intervention") },
        text = {
            OutlinedTextField(
                value = intitule,
                onValueChange = { intitule = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Intitulé") },
                singleLine = true,
                keyboardOptions = OPTIONS_CLAVIER,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onValider(intitule) },
                enabled = intitule.isNotBlank(),
            ) {
                Text(text = "Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onFermer) { Text(text = "Annuler") }
        },
    )
}

/**
 * Clients du carnet proposés sous le champ de saisie.
 *
 * Rien n'est proposé une fois le client rattaché, ni sur une saisie trop
 * courte pour discriminer, ni pour un nom déjà tapé en entier — la suggestion
 * n'apporterait alors rien.
 */
private fun suggestions(clients: List<Client>, etat: EtatFormulaire): List<Client> {
    val saisie = etat.client.trim()
    if (etat.clientId != null || saisie.length < 2) return emptyList()

    return clients
        .filter { it.nom.contains(saisie, ignoreCase = true) && !it.nom.equals(saisie, ignoreCase = true) }
        .take(NOMBRE_SUGGESTIONS)
}

/** Au-delà, la feuille de saisie se transforme en liste de clients. */
private const val NOMBRE_SUGGESTIONS = 4

@Composable
private fun SuggestionClient(
    client: Client,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ContactPage,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = client.nom,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = client.ville,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
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
            clients = emptyList(),
            types = listOf(
                TypeIntervention(id = "t1", libelle = "Entretien annuel"),
                TypeIntervention(id = "t2", libelle = "Fuite de fluide"),
            ),
            onClientChoisi = {},
            onTypeChoisi = {},
            onNouveauType = {},
            etat = EtatFormulaire(
                id = "1",
                date = LocalDate.now(),
                heure = LocalTime.of(10, 30),
                client = "Boucherie Lemoine",
                ville = "Rouen",
                typeId = "t2",
                typeLibelle = "Fuite de fluide",
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
