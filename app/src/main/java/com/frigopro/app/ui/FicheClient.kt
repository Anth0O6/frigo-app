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
import androidx.compose.material.icons.filled.Description
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
 * **La suppression est ici et nulle part ailleurs**, à la différence d'un devis
 * qu'un appui long efface depuis la liste. Les deux gestes ne répondent pas à la
 * même chose : un devis de trop vient d'une fausse manœuvre et se voit dans la
 * liste, un client se supprime délibérément, après avoir ouvert sa fiche et vu
 * ce qu'elle porte. Un appui long sur une carte qui montre déjà le parc et
 * plusieurs boutons aurait effacé un client pendant qu'on cherchait une machine.
 *
 * Elle n'est proposée qu'en **édition** : il n'y a rien à supprimer d'une fiche
 * qu'on est en train de créer, et « Annuler » y répond déjà.
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
    /**
     * Demande la suppression. `null` en création, et la confirmation est
     * l'affaire de la route : c'est elle qui sait compter ce qui disparaît.
     */
    onSupprimer: (() -> Unit)? = null,
    /**
     * L'attestation d'entretien de ce client : les années à proposer, et
     * l'export.
     *
     * Elle vit sur la fiche du client parce que c'est **la seule surface qui
     * désigne un client** : le registre des fluides est tenu par l'entreprise et
     * va donc aux Réglages, une attestation se remet à quelqu'un et se cherche
     * là où ce quelqu'un est fiché. Le défaut vide la rend absente, ce qui est le
     * cas de tout parc qui ne suit aucune gamme — un bouton qui ne produirait
     * qu'un document blanc vaut moins que pas de bouton.
     */
    attestation: ActionsAttestation = ActionsAttestation(),
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

            if (!etat.estCreation && attestation.annees.isNotEmpty()) {
                SectionAttestation(attestation = attestation)
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

            // En dessous et non dans la rangée : une action destructrice à côté
            // d'« Enregistrer », sur une cible de doigt ganté, se touche par
            // erreur. Elle est en retrait, et la confirmation qui suit nomme ce
            // qui disparaît.
            if (!etat.estCreation && onSupprimer != null) {
                TextButton(
                    onClick = onSupprimer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Supprimer ce client",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Les deux rappels de l'attestation : ce qu'on peut proposer, et l'export. */
data class ActionsAttestation(
    val annees: List<Int> = emptyList(),
    val onExporter: (Int) -> Unit = {},
)

/**
 * L'attestation d'entretien, sur la fiche du client.
 *
 * Les années sont **dérivées des visites** de son parc, comme celles du registre
 * le sont des mouvements : proposer trois années dont deux sont vides ferait
 * ouvrir deux documents blancs. L'année courante y est toujours, parce qu'une
 * attestation demandée en cours d'année est le cas ordinaire — on la remet avec
 * la facture de décembre.
 *
 * La phrase au-dessus dit **ce que le document n'est pas**, et c'est délibéré :
 * on hésite à envoyer une pièce dont on ne sait pas ce qu'elle engage, et
 * « elle rapporte des dates » est ce qui permet de le faire sans relire le PDF.
 */
@Composable
private fun SectionAttestation(attestation: ActionsAttestation) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Attestation d'entretien", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Récapitulatif des visites de maintenance préventive consignées sur " +
                "le parc de ce client. Elle rapporte des dates et ne vaut pas certificat " +
                "de conformité.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        attestation.annees.forEach { annee ->
            OutlinedButton(
                onClick = { attestation.onExporter(annee) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Attestation $annee")
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
