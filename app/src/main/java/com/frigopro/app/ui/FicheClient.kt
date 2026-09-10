package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frigopro.app.ui.theme.FrigoProTheme

/** Noms propres et adresses : majuscule initiale, clavier qui enchaîne les champs. */
private val OPTIONS_TEXTE = KeyboardOptions(
    capitalization = KeyboardCapitalization.Words,
    imeAction = ImeAction.Next,
)

/** Un numéro se tape sur un pavé numérique, et rien ne suit. */
private val OPTIONS_TELEPHONE = KeyboardOptions(
    keyboardType = KeyboardType.Phone,
    imeAction = ImeAction.Done,
)

/**
 * Fiche d'un client, en création comme en édition.
 *
 * Pas de suppression : une intervention passée garde le `clientId` de celui
 * chez qui elle a eu lieu, et supprimer la fiche détacherait son historique.
 * Le jour où la suppression arrivera, elle devra décider du sort de ce
 * rattachement — ce qui est une décision, pas un bouton.
 *
 * Composable sans état : la saisie est remontée telle quelle via [onEtatChange].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheClient(
    etat: EtatFicheClient,
    onEtatChange: (EtatFicheClient) -> Unit,
    onEnregistrer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexte = LocalContext.current
    val client = etat.versClient()

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
                text = if (etat.estCreation) "Nouveau client" else etat.nom.ifBlank { "Client" },
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedTextField(
                value = etat.nom,
                onValueChange = { onEtatChange(etat.copy(nom = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Nom") },
                singleLine = true,
                keyboardOptions = OPTIONS_TEXTE,
            )

            OutlinedTextField(
                value = etat.adresse,
                onValueChange = { onEtatChange(etat.copy(adresse = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Adresse") },
                singleLine = true,
                keyboardOptions = OPTIONS_TEXTE,
            )

            OutlinedTextField(
                value = etat.ville,
                onValueChange = { onEtatChange(etat.copy(ville = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Ville") },
                singleLine = true,
                keyboardOptions = OPTIONS_TEXTE,
            )

            OutlinedTextField(
                value = etat.telephone,
                onValueChange = { onEtatChange(etat.copy(telephone = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Téléphone") },
                singleLine = true,
                keyboardOptions = OPTIONS_TELEPHONE,
            )

            // Agir depuis la fiche, sans la refermer d'abord. Les boutons
            // n'apparaissent que lorsqu'il y a de quoi agir : un bouton
            // « Appeler » grisé ne renseigne personne.
            if (client.appelable || client.localisable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (client.appelable) {
                        OutlinedButton(
                            onClick = { contexte.appeler(client.telephone) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Appeler")
                        }
                    }
                    if (client.localisable) {
                        OutlinedButton(
                            onClick = { contexte.ouvrirItineraire(client.adresseComplete) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Directions,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Itinéraire")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onFermer, modifier = Modifier.weight(1f)) {
                    Text(text = "Annuler")
                }
                Button(
                    onClick = onEnregistrer,
                    enabled = etat.estValide,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = if (etat.estCreation) "Ajouter" else "Enregistrer")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FicheClientPreview() {
    FrigoProTheme {
        Surface {
            FicheClient(
                etat = EtatFicheClient(
                    estCreation = false,
                    nom = "Boucherie Lemoine",
                    ville = "Rouen",
                    adresse = "12 rue des Carmes",
                    telephone = "02 35 00 00 00",
                ),
                onEtatChange = {},
                onEnregistrer = {},
                onFermer = {},
            )
        }
    }
}
