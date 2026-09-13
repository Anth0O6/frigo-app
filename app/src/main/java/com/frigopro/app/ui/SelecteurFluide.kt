package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Fluides
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.theme.LocalCibles

/**
 * Le champ par où l'on choisit un fluide.
 *
 * Il remplace une saisie libre, et ce n'est pas un confort : un fluide tapé à la
 * main se tape mal. « R410 », « 410a », « R-410 A » désignent tous le R-410A
 * sans qu'aucun ne lui soit égal, et [Fluides.normaliser] en rattrape la forme
 * mais pas la coquille. Or un fluide non reconnu n'a ni GWP, ni classe de
 * sécurité, ni périodicité de contrôle : l'application se tait exactement là où
 * elle avait quelque chose à dire, et rien ne signale que c'est une faute de
 * frappe qui l'a fait taire.
 *
 * **La saisie libre reste possible** par « Autre fluide », et ce n'est pas une
 * concession : le catalogue ne tient que les fluides courants, et une machine
 * ancienne ou un mélange de niche doit pouvoir être consigné. Ce qui change,
 * c'est qu'on y arrive par un geste explicite au lieu d'y tomber par une lettre
 * en trop.
 */
@Composable
fun ChampFluide(
    valeur: String,
    onValeur: (String) -> Unit,
    modifier: Modifier = Modifier,
    libelle: String = "Fluide",
) {
    var ouvert by remember { mutableStateOf(false) }

    // La hauteur d'une cible tactile plutôt que celle d'un bouton : le champ
    // voisine des champs de saisie, et il doit s'atteindre ganté comme eux.
    OutlinedButton(
        onClick = { ouvert = true },
        modifier = modifier.heightIn(min = LocalCibles.current.action),
    ) {
        Icon(
            imageVector = Icons.Filled.Science,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = if (valeur.isBlank()) libelle else libelleFluide(valeur))
    }

    if (ouvert) {
        DialogueChoixFluide(
            retenu = valeur,
            onChoisir = {
                onValeur(it)
                ouvert = false
            },
            onFermer = { ouvert = false },
        )
    }
}

/**
 * La liste des fluides, cherchable.
 *
 * Chaque ligne porte le **GWP et la classe de sécurité** en plus du code, et
 * c'est le vrai apport sur une saisie libre : on choisit souvent entre deux
 * fluides proches, et voir « A2L — faiblement inflammable » au moment de
 * choisir vaut mieux que de l'apprendre en ouvrant la fiche après coup.
 */
@Composable
fun DialogueChoixFluide(
    retenu: String,
    onChoisir: (String) -> Unit,
    onFermer: () -> Unit,
) {
    var recherche by remember { mutableStateOf("") }
    var horsCatalogue by remember { mutableStateOf(false) }
    var libre by remember { mutableStateOf(if (Fluides.gwp(retenu) == null) retenu else "") }

    val resultats = Fluides.rechercher(recherche)
    val canonique = Fluides.normaliser(retenu)

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Fluide") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (horsCatalogue) {
                    Text(
                        text = "Un fluide hors catalogue se consigne, mais son GWP, sa " +
                            "classe de sécurité et sa périodicité de contrôle resteront " +
                            "inconnus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChampTexte(
                        libelle = "Désignation",
                        valeur = libre,
                        onValeur = { libre = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ChampRecherche(
                        valeur = recherche,
                        onValeur = { recherche = it },
                        indication = "Code ou nom : 449, eau, ammoniac…",
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(resultats, key = { it }) { nom ->
                            LigneFluide(
                                nom = nom,
                                retenu = nom == canonique,
                                onChoisir = { onChoisir(nom) },
                            )
                        }
                        if (resultats.isEmpty()) {
                            item {
                                Text(
                                    text = "Aucun fluide du catalogue ne correspond.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = { horsCatalogue = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Autre fluide…")
                    }
                }
            }
        },
        confirmButton = {
            if (horsCatalogue) {
                TextButton(
                    onClick = { onChoisir(libre.trim()) },
                    enabled = libre.isNotBlank(),
                ) {
                    Text(text = "Utiliser")
                }
            } else {
                TextButton(onClick = onFermer) { Text(text = "Fermer") }
            }
        },
        dismissButton = {
            if (horsCatalogue) {
                TextButton(onClick = { horsCatalogue = false }) { Text(text = "Retour") }
            }
        },
    )
}

@Composable
private fun LigneFluide(nom: String, retenu: Boolean, onChoisir: () -> Unit) {
    Surface(
        onClick = onChoisir,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (retenu) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = libelleFluide(nom),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detailFluide(nom),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (retenu) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** « R-744 (CO₂) », ou « R-449A » pour un mélange qui n'a pas de nom courant. */
fun libelleFluide(fluide: String): String {
    val code = Fluides.afficher(fluide)
    val nom = Fluides.nomUsuel(fluide) ?: return code
    return "$code ($nom)"
}

/**
 * Ce qui décide entre deux fluides : ce qu'il pèse au climat, et ce qu'il
 * impose comme façon de travailler.
 */
private fun detailFluide(fluide: String): String {
    val gwp = Fluides.gwp(fluide)
    val classe = Fluides.classeSecurite(fluide)
    val morceaux = buildList {
        if (gwp != null) add(if (gwp == 0) "GWP nul" else "GWP $gwp")
        if (classe != null) add(classe.resume)
    }
    return morceaux.joinToString(" · ").ifBlank { "Hors catalogue" }
}
