package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.data.Technicien
import com.frigopro.app.data.TypeIntervention
import com.frigopro.app.ui.composants.RangeePastilles
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
    machines: List<Equipement>,
    techniciens: List<Technicien>,
    onEtatChange: (EtatFormulaire) -> Unit,
    onClientChoisi: (Client) -> Unit,
    onTypeChoisi: (TypeIntervention?) -> Unit,
    onNouveauType: (String) -> Unit,
    onMachineChoisie: (Equipement?) -> Unit,
    onNouvelleMachine: (String) -> Unit,
    onNouveauTechnicien: (String) -> Unit,
    onValider: () -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choixDateOuvert by rememberSaveable { mutableStateOf(false) }
    var choixHeureOuvert by rememberSaveable { mutableStateOf(false) }
    var nouveauTypeOuvert by rememberSaveable { mutableStateOf(false) }
    var nouvelleMachineOuverte by rememberSaveable { mutableStateOf(false) }
    var nouveauTechnicienOuvert by rememberSaveable { mutableStateOf(false) }
    var suppressionOuverte by rememberSaveable { mutableStateOf(false) }

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

            // La durée suit l'heure parce que les deux décrivent le même créneau,
            // et c'est elle qui donne au planning la hauteur à dessiner. Des
            // choix prédéfinis plutôt qu'un champ libre : une durée se pense en
            // demi-heures sur une tournée, et taper « 90 » au clavier est plus
            // long que toucher « 1 h 30 ».
            Text(
                text = "Durée prévue",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RangeePastilles(
                options = DUREES_PROPOSEES,
                retenue = etat.dureeMin,
                libelle = ::libelleDuree,
                onChoisir = { onEtatChange(etat.copy(dureeMin = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )

            OutlinedTextField(
                value = etat.client,
                // Modifier le nom détache du carnet : ce n'est plus le même
                // client, donc plus son parc non plus — une machine appartient à
                // quelqu'un, la garder ici la rattacherait au mauvais.
                onValueChange = {
                    onEtatChange(
                        etat.copy(
                            client = it,
                            clientId = null,
                            equipementId = null,
                            equipementNom = "",
                        ),
                    )
                },
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
                text = "Technicien",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // « Personne » est une option et non une absence d'option : une
                // tournée peut légitimement n'être confiée à personne — c'est le
                // cas quand on travaille seul — et il faut pouvoir y revenir.
                FilterChip(
                    selected = etat.technicienId == null && etat.technicienNom.isBlank(),
                    onClick = { onEtatChange(etat.copy(technicienId = null, technicienNom = "")) },
                    label = { Text(text = "Personne") },
                )
                techniciens.forEach { technicien ->
                    FilterChip(
                        selected = etat.technicienId == technicien.id,
                        onClick = {
                            onEtatChange(
                                etat.copy(
                                    technicienId = technicien.id,
                                    // Le nom est recopié, comme pour le type et
                                    // la machine : une tournée de mars doit
                                    // continuer de dire qui l'a faite même si la
                                    // fiche disparaît depuis.
                                    technicienNom = technicien.nom,
                                ),
                            )
                        },
                        label = { Text(text = technicien.nom) },
                    )
                }
                // Nom venu d'avant la liste, ou dont la fiche a été retirée : il
                // s'affiche quand même, sinon ouvrir le formulaire pour changer
                // l'heure effacerait silencieusement le technicien.
                if (etat.technicienId == null && etat.technicienNom.isNotBlank()) {
                    FilterChip(
                        selected = true,
                        onClick = { onEtatChange(etat.copy(technicienNom = "")) },
                        label = { Text(text = etat.technicienNom) },
                    )
                }
                AssistChip(
                    onClick = { nouveauTechnicienOuvert = true },
                    label = { Text(text = "Nouveau technicien") },
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
                text = "Machine concernée",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (etat.clientId == null) {
                // Une machine appartient à un client du carnet : sans client
                // désigné, il n'y a pas de parc où la choisir ni où l'inscrire.
                Text(
                    text = "Choisissez un client du carnet pour accéder à ses machines.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    machines.forEach { machine ->
                        FilterChip(
                            selected = etat.equipementId == machine.id,
                            onClick = {
                                onMachineChoisie(if (etat.equipementId == machine.id) null else machine)
                            },
                            label = { Text(text = machine.nom) },
                        )
                    }
                    // Machine retirée du parc depuis : son nom reste affiché,
                    // sinon rouvrir l'intervention l'effacerait sans un mot.
                    if (etat.equipementId == null && etat.equipementNom.isNotBlank()) {
                        FilterChip(
                            selected = true,
                            onClick = { onMachineChoisie(null) },
                            label = { Text(text = etat.equipementNom) },
                        )
                    }
                    AssistChip(
                        onClick = { nouvelleMachineOuverte = true },
                        label = { Text(text = "Nouvelle machine") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }

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
                        onClick = { suppressionOuverte = true },
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

    // Une confirmation, parce qu'une intervention n'est pas qu'une ligne d'agenda :
    // elle porte le temps chronométré, les relevés, les photos et la signature du
    // client. Les faire disparaître d'un appui, sans filet, était le seul geste
    // vraiment irrattrapable de l'application.
    if (suppressionOuverte) {
        ConfirmationSuppressionIntervention(
            etat = etat,
            onConfirmer = {
                suppressionOuverte = false
                onSupprimer()
            },
            onFermer = { suppressionOuverte = false },
        )
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
        DialogueIntitule(
            titre = "Nouveau type d'intervention",
            libelleAction = "Ajouter",
            messageConflit = "Ce type existe déjà.",
            onValider = {
                onNouveauType(it)
                nouveauTypeOuvert = false
            },
            onFermer = { nouveauTypeOuvert = false },
        )
    }

    if (nouvelleMachineOuverte) {
        DialogueIntitule(
            titre = "Nouvelle machine",
            libelleAction = "Ajouter",
            libelleChamp = "Nom de la machine",
            messageConflit = "Ce client a déjà une machine de ce nom.",
            estDejaPris = { nom -> machines.any { it.nom.equals(nom, ignoreCase = true) } },
            onValider = {
                onNouvelleMachine(it)
                nouvelleMachineOuverte = false
            },
            onFermer = { nouvelleMachineOuverte = false },
        )
    }

    if (nouveauTechnicienOuvert) {
        DialogueIntitule(
            titre = "Nouveau technicien",
            libelleAction = "Ajouter",
            libelleChamp = "Nom du technicien",
            messageConflit = "Ce technicien existe déjà.",
            estDejaPris = { nom -> techniciens.any { it.nom.equals(nom, ignoreCase = true) } },
            onValider = {
                onNouveauTechnicien(it)
                nouveauTechnicienOuvert = false
            },
            onFermer = { nouveauTechnicienOuvert = false },
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

/**
 * Ce qu'une suppression emporte, dit avant et non après.
 *
 * Le compte des pièces jointes est rappelé parce que c'est lui qui fait hésiter :
 * « supprimer l'intervention » se lit comme une ligne d'agenda, « avec sa
 * signature et ses quatre photos » se lit comme ce que c'est.
 */
@Composable
private fun ConfirmationSuppressionIntervention(
    etat: EtatFormulaire,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    val origine = etat.origine
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Supprimer cette intervention ?") },
        text = {
            Text(
                text = buildString {
                    append("« ")
                    append(etat.client.ifBlank { "Sans client" })
                    append(" » du ")
                    append(libelleDateAvecAnnee(etat.date))
                    append(" sera supprimée.")
                    if (origine != null) {
                        val porte = buildList {
                            if (!origine.chrono.vierge) add("le temps passé")
                            if (origine.signatureFichier != null) add("la signature du client")
                            if (origine.numero.isNotBlank()) add("son numéro ${origine.numero}")
                        }
                        if (porte.isNotEmpty()) {
                            append(" Elle porte ")
                            append(porte.joinToString(", "))
                            append(" : rien de tout cela ne se récupère.")
                        }
                    }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmer,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = "Supprimer")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
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
            machines = listOf(
                Equipement(id = "e1", clientId = "c1", nom = "Vitrine salle 2"),
                Equipement(id = "e2", clientId = "c1", nom = "Chambre froide positive"),
            ),
            techniciens = listOf(
                Technicien(id = "tech-1", nom = "Karim Benali"),
                Technicien(id = "tech-2", nom = "Mehdi Lacroix"),
            ),
            onClientChoisi = {},
            onTypeChoisi = {},
            onNouveauType = {},
            onMachineChoisie = {},
            onNouvelleMachine = {},
            onNouveauTechnicien = {},
            etat = EtatFormulaire(
                id = "1",
                date = LocalDate.now(),
                heure = LocalTime.of(10, 30),
                client = "Boucherie Lemoine",
                ville = "Rouen",
                clientId = "c1",
                typeId = "t2",
                typeLibelle = "Fuite de fluide",
                equipementId = "e1",
                equipementNom = "Vitrine salle 2",
                statut = StatutIntervention.EN_COURS,
                notes = "Manque de fluide, à recontrôler la semaine prochaine.",
                dureeMin = 90,
                technicienId = "tech-1",
                technicienNom = "Karim Benali",
            ),
            onEtatChange = {},
            onValider = {},
            onSupprimer = {},
            onFermer = {},
        )
    }
}

/**
 * Les durées proposées, en minutes.
 *
 * Des choix prédéfinis plutôt qu'un champ libre : une tournée se pense en
 * demi-heures, et toucher « 1 h 30 » est plus court que taper « 90 ». Une durée
 * hors de cette liste — reprise d'une ancienne intervention, ou d'un format de
 * sauvegarde — reste enregistrée telle quelle : aucune pastille n'est alors
 * retenue, et n'en toucher aucune ne la change pas.
 */
private val DUREES_PROPOSEES = listOf(30, 60, 90, 120, 180, 240)

/** « 30 min », « 1 h », « 1 h 30 ». */
private fun libelleDuree(minutes: Int): String {
    val heures = minutes / 60
    val reste = minutes % 60
    return when {
        heures == 0 -> "$minutes min"
        reste == 0 -> "$heures h"
        else -> "$heures h $reste"
    }
}
