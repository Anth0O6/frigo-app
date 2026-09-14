package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.frigopro.app.data.PalierPrestation
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.TarifDegressif
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * Le prix dégressif d'une prestation, rang par rang.
 *
 * ## Pourquoi seulement sur une prestation comptée par unité
 *
 * La dégressivité répond à un cas précis : entretenir un bi-split, c'est deux
 * unités, et la deuxième coûte moins que la première parce que le déplacement
 * et la mise en route sont déjà payés. Un **forfait** n'a pas de rangs — il n'y
 * a rien à dégresser sur « déplacement zone 1 » —, et proposer la boîte dessus
 * aurait invité à saisir des paliers qui ne serviraient jamais.
 *
 * ## Ce que l'aperçu montre
 *
 * Le total pour deux, trois et quatre unités, recalculé à chaque frappe. C'est
 * le seul moyen de vérifier qu'un jeu de paliers dit bien ce qu'on croit : les
 * chiffres unitaires se lisent mal, leur somme se lit tout de suite. Et le rang
 * 1 y figure en clair, **non modifiable ici** : c'est le prix de la prestation,
 * et le dupliquer l'aurait fait diverger au premier changement de tarif.
 */
@Composable
fun DialoguePaliers(
    prestation: Prestation,
    paliers: List<PalierPrestation>,
    onDefinir: (Int, Double) -> Unit,
    onSupprimer: (String) -> Unit,
    onFermer: () -> Unit,
) {
    var rang by remember { mutableStateOf("2") }
    var prix by remember { mutableStateOf("") }

    val tarif = TarifDegressif(base = prestation.prixUnitaire, paliers = paliers)
    val tries = paliers.filter { it.aPartirDe >= 2 }.sortedBy { it.aPartirDe }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Prix dégressif") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = prestation.designation,
                    style = MaterialTheme.typography.titleSmall,
                )

                LignePalier(
                    intitule = "1re unité",
                    prix = prestation.prixUnitaire,
                    unite = prestation.unite,
                    detail = "Le prix de la prestation, réglé au-dessus.",
                )
                tries.forEach { palier ->
                    LignePalier(
                        intitule = "à partir de la ${palier.aPartirDe}e",
                        prix = palier.prixUnitaire,
                        unite = prestation.unite,
                        onRetirer = { onSupprimer(palier.id) },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChampTexte(
                        libelle = "À partir de la",
                        valeur = rang,
                        onValeur = { rang = it },
                        clavier = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    ChampTexte(
                        libelle = "Prix HT",
                        valeur = prix,
                        onValeur = { prix = it },
                        clavier = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                BoutonContour(
                    texte = "Ajouter ce palier",
                    onClick = {
                        val depuis = rang.trim().toIntOrNull()
                        val montant = Nombres.versDecimal(prix)
                        if (depuis != null && montant != null) {
                            onDefinir(depuis, montant)
                            rang = (depuis + 1).toString()
                            prix = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // L'aperçu : les chiffres unitaires se lisent mal, leur somme se
                // lit tout de suite.
                Encart(
                    texte = listOf(2, 3, 4).joinToString("  ·  ") { nombre ->
                        "$nombre u : ${Nombres.enEuros(tarif.total(nombre))}"
                    },
                )
                Text(
                    text = "Chaque rang porte son propre prix : trois unités à 100, 80 et 70 " +
                        "font 250 €, et non trois fois 70. Le dernier palier vaut aussi pour " +
                        "toutes les unités suivantes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onFermer) { Text(text = "Fermer") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun LignePalier(
    intitule: String,
    prix: Double,
    unite: String,
    detail: String? = null,
    onRetirer: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = intitule, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = Nombres.enEuros(prix) + unite.let { if (it.isBlank()) "" else " / $it" },
            style = StyleChiffrePetit,
        )
        if (onRetirer != null) {
            IconButton(onClick = onRetirer) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Retirer ce palier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
