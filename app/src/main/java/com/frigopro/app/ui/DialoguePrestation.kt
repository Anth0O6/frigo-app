package com.frigopro.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.CategoriePrestation
import com.frigopro.app.data.Prestation
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.RangeePastilles

/**
 * Une prestation du catalogue : la créer, la retarifer, la retirer.
 *
 * Une seule boîte pour les deux gestes, sur le modèle de [DialogueIntitule] :
 * créer et corriger se font des mêmes champs, et deux boîtes jumelles auraient
 * fini par diverger — on aurait ajouté « par unité » à l'une et oublié l'autre.
 * `prestation` à `null` vaut création, et c'est ce qui distingue « Ajouter »
 * d'« Enregistrer ».
 *
 * L'unité se saisit **avec** le prix parce que les deux vont ensemble : un tarif
 * de 38 € ne veut rien dire sans « par kilo », et c'est le genre de chose qu'on
 * corrige au moment où l'on tape le chiffre.
 *
 * Une prestation existante est modifiée par `copy` et non reconstruite : elle
 * porte un rang qui la place dans sa famille, et la recréer à neuf la renverrait
 * en fin de liste à chaque correction de prix.
 */
@Composable
fun DialoguePrestation(
    prestation: Prestation?,
    onValider: (Prestation) -> Unit,
    onFermer: () -> Unit,
    onSupprimer: (() -> Unit)? = null,
) {
    var designation by remember { mutableStateOf(prestation?.designation.orEmpty()) }
    var categorie by remember {
        mutableStateOf(prestation?.categorie ?: CategoriePrestation.DEPANNAGE)
    }
    var prix by remember {
        mutableStateOf(
            // Un prix jamais renseigné reste un champ vide : y afficher « 0 »
            // obligerait à l'effacer avant de taper, gants aux mains.
            prestation?.takeIf { it.tarifee }?.let { Nombres.enTexte(it.prixUnitaire) }.orEmpty(),
        )
    }
    var unite by remember { mutableStateOf(prestation?.unite.orEmpty()) }
    var parUnite by remember { mutableStateOf(prestation?.parUnite ?: false) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = if (prestation == null) "Nouvelle prestation" else prestation.designation) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChampTexte(
                    libelle = "Désignation",
                    valeur = designation,
                    onValeur = { designation = it },
                )
                // Cinq familles ne tiennent pas dans la largeur d'une boîte de
                // dialogue : la rangée défile plutôt que de se replier sur deux
                // lignes, qui donneraient une hauteur variable selon la famille
                // retenue.
                RangeePastilles(
                    options = CategoriePrestation.entries,
                    retenue = categorie,
                    libelle = { it.libelle },
                    onChoisir = { categorie = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChampTexte(
                        libelle = "Prix unitaire (€)",
                        valeur = prix,
                        onValeur = { prix = it },
                        clavier = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    ChampTexte(
                        libelle = "Unité",
                        valeur = unite,
                        onValeur = { unite = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Par unité intérieure", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "La quantité sera pré-remplie du nombre d'unités du groupe, " +
                                "et restera modifiable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = parUnite,
                        onCheckedChange = { parUnite = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                }
                Text(
                    text = "Le prix est recopié sur la ligne du devis : le changer ici ne " +
                        "touche pas aux devis déjà établis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onSupprimer != null) {
                    TextButton(onClick = onSupprimer) {
                        Text(
                            text = "Retirer du catalogue",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val montant = Nombres.versDecimal(prix) ?: 0.0
                    val base = prestation ?: Prestation(designation = designation, categorie = categorie)
                    onValider(
                        base.copy(
                            designation = designation,
                            categorie = categorie,
                            prixUnitaire = montant,
                            unite = unite,
                            parUnite = parUnite,
                        ),
                    )
                },
                enabled = designation.isNotBlank(),
            ) {
                Text(text = if (prestation == null) "Ajouter" else "Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
