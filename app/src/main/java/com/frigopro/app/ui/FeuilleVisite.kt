package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.EcheanceMaintenance
import com.frigopro.app.data.PointGamme
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import java.time.LocalDate

/**
 * Consigner une visite : ce qu'il y a à vérifier, et le geste qui l'enregistre.
 *
 * Elle montre **les points de la gamme**, et c'est sa raison d'être première :
 * une visite préventive est une liste de choses à faire, et l'ouvrir sur la liste
 * évite de l'avoir en tête ou sur un papier. Les cases se cochent pour s'y
 * retrouver pendant qu'on travaille, et **elles ne sont pas enregistrées** : ce
 * qui part au journal est la visite, sa date, son auteur et ses remarques. Les
 * conserver une à une aurait demandé une cinquième table pour une information que
 * personne ne relit — à la différence de la checklist d'une intervention, qui
 * part sur un compte-rendu signé par le client.
 *
 * La **date est modifiable**, et ce n'est pas un détail : on consigne sa tournée
 * le soir, parfois le lendemain, et dater la visite du jour de la saisie
 * décalerait tout le plan d'un jour à chaque fois. Même défaut que la date d'un
 * mouvement de fluide au registre, où il a déjà fallu le corriger.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleVisite(
    echeance: EcheanceMaintenance,
    points: List<PointGamme>,
    onConsigner: (LocalDate, String) -> Unit,
    onFermer: () -> Unit,
    aujourdhui: LocalDate = LocalDate.now(),
) {
    var faitLe by remember { mutableStateOf(aujourdhui) }
    var notes by remember { mutableStateOf("") }
    var cochés by remember { mutableStateOf(emptySet<String>()) }
    var selecteurOuvert by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = echeance.equipement.nom,
                style = MaterialTheme.typography.titleLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Puce(texte = echeance.gamme.libelle)
                Puce(texte = echeance.gamme.periodicite.libelle)
            }
            Text(
                text = buildString {
                    if (echeance.clientNom.isNotBlank()) {
                        append(echeance.clientNom)
                        append(" · ")
                    }
                    append(
                        if (echeance.jamaisVisitee) {
                            "jamais visitée"
                        } else {
                            "dernière visite le ${jourCourt(echeance.derniereVisite!!)}"
                        },
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (points.isEmpty()) {
                Encart(
                    texte = "Cette gamme n'a aucun point. Elle enregistrera la visite, mais " +
                        "ne dira pas ce qu'il fallait vérifier — les points se posent dans " +
                        "les Réglages.",
                )
            } else {
                Text(
                    text = "À vérifier",
                    style = MaterialTheme.typography.titleSmall,
                )
                points.forEach { point ->
                    val coché = point.id in cochés
                    Carte(
                        onClick = {
                            cochés = if (coché) cochés - point.id else cochés + point.id
                        },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (coché) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = if (coché) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = point.libelle,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Text(
                    text = "Ces cases servent à s'y retrouver pendant la visite : elles ne " +
                        "sont pas enregistrées. Ce qui part au journal, c'est la visite, sa " +
                        "date et vos remarques.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ChampTexte(
                libelle = "Remarques",
                valeur = notes,
                onValeur = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                lignes = 3,
            )

            BoutonContour(
                texte = "Faite le ${jourCourt(faitLe)}",
                onClick = { selecteurOuvert = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Changez la date si vous consignez une visite d'hier : la dater du " +
                    "jour de la saisie décalerait tout le plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BoutonPlein(
                texte = "Consigner la visite",
                onClick = { onConsigner(faitLe, notes) },
                modifier = Modifier.fillMaxWidth(),
            )
            EspaceVertical(24)
        }
    }

    if (selecteurOuvert) {
        SelecteurDate(
            date = faitLe,
            onDateChoisie = {
                faitLe = it
                selecteurOuvert = false
            },
            onFermer = { selecteurOuvert = false },
        )
    }
}
