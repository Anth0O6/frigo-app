package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.BaseBenefice
import com.frigopro.app.data.Benefice
import com.frigopro.app.data.PeriodeBenefice
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * Ce qui reste dans la poche, sur les cinq périodes à la fois.
 *
 * Les cinq chiffres se lisent **ensemble** plutôt qu'au travers d'une bascule :
 * « combien aujourd'hui » et « combien cette année » se comparent d'un coup d'œil,
 * et une bascule à cinq pastilles n'aurait pas tenu dans la largeur. Toucher une
 * période en déplie le détail — la marge du matériel, la main-d'œuvre, ce que les
 * gestes et les avoirs ont retiré —, parce qu'un total qu'on ne peut pas décomposer
 * ne se discute pas. La phrase du bas dit ce que le chiffre **ne compte pas** : lu
 * comme un résultat, il ferait facturer trop bas l'année suivante.
 */
@Composable
fun CarteBenefice(
    benefices: Map<PeriodeBenefice, Benefice>,
    base: BaseBenefice,
    onBase: (BaseBenefice) -> Unit,
    modifier: Modifier = Modifier,
) {
    // La période dépliée : affichage local, comme l'ouverture d'une boîte.
    var depliee by rememberSaveable { mutableStateOf<PeriodeBenefice?>(PeriodeBenefice.MOIS) }
    val statuts = LocalStatuts.current

    Carte(modifier = modifier, relief = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Bénéfice", style = MaterialTheme.typography.titleMedium)
            RangeePastilles(
                options = BaseBenefice.entries.toList(),
                retenue = base,
                libelle = { it.libelle },
                onChoisir = onBase,
            )
            PeriodeBenefice.entries.forEach { periode ->
                val b = benefices[periode] ?: Benefice()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = LocalCibles.current.case)
                        .clickable { depliee = if (depliee == periode) null else periode },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = periode.libelle,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (b.documents == 0) "—" else Nombres.enEuros(b.total),
                        style = StyleChiffrePetit,
                        color = when {
                            b.documents == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            b.total < 0.0 -> statuts.urgence
                            else -> statuts.termine
                        },
                    )
                }
                if (depliee == periode) DetailBenefice(b, base)
            }
            Text(
                text = "Hors taxes : la marge du matériel, plus la main-d'œuvre et les " +
                    "prestations (déplacement compris), moins la TVA offerte et les avoirs. " +
                    "Ni le véhicule, ni les charges, ni le coût horaire interne n'y sont : ce " +
                    "n'est pas un résultat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailBenefice(b: Benefice, base: BaseBenefice) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (b.documents == 0) {
            Text(
                text = if (base == BaseBenefice.FACTURE) {
                    "Aucune facture émise sur la période."
                } else {
                    "Aucune facture payée sur la période."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        LigneDetail("Marge matériel", b.margeMateriel)
        LigneDetail("Main-d'œuvre et prestations", b.mainOeuvre)
        if (b.tvaOfferte > 0.0) LigneDetail("TVA offerte", -b.tvaOfferte)
        if (b.avoirs > 0.0) LigneDetail("Avoirs", -b.avoirs)
        Text(
            text = "${b.documents} document${if (b.documents > 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LigneDetail(intitule: String, montant: Double) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = intitule,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (montant < 0.0) "− ${Nombres.enEuros(-montant)}" else Nombres.enEuros(montant),
            style = StyleChiffrePetit,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
