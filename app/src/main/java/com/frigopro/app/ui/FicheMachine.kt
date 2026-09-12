package com.frigopro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EtatEtancheite
import com.frigopro.app.data.Fluides
import com.frigopro.app.data.Releve
import com.frigopro.app.data.arrondiDixieme
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.AValider
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Ce que la fiche d'une machine dit d'elle : sa plaque, son fluide, ses
 * obligations et sa tendance.
 *
 * L'échéance d'étanchéité y est un chiffre comme les autres, et c'est
 * volontaire : elle se lit d'un coup d'œil au même endroit que la charge,
 * parce qu'elle en découle. Elle passe à l'ambre quand elle est dépassée.
 */
@Composable
fun IdentiteMachine(
    equipement: Equipement,
    aujourdhui: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    val etancheite = EtatEtancheite.calculer(
        fluide = equipement.fluide,
        chargeKg = equipement.chargeKg,
        dernierControle = equipement.dernierControleLe,
        aujourdhui = aujourdhui,
    )
    val gwp = Fluides.gwp(equipement.fluide)
    val tonnes = equipement.chargeKg?.let { Fluides.tonnesEquivalentCo2(equipement.fluide, it) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TuileChiffre(
                valeur = equipement.fluide.ifBlank { "—" },
                libelle = "Fluide",
                unite = equipement.chargeKg?.let { "· ${Nombres.enTexte(it)} kg" },
                modifier = Modifier.weight(1f),
            )
            TuileChiffre(
                valeur = gwp?.toString() ?: "—",
                libelle = "GWP / t éq. CO₂",
                unite = tonnes?.let { "· ${Nombres.enTexte(it.arrondiDixieme())}" },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TuileChiffre(
                valeur = etancheite.echeance?.let { it.format(FORMAT_MOIS) } ?: "—",
                libelle = libelleEtancheite(etancheite),
                couleur = if (etancheite.enRetard) AValider else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TuileChiffre(
                valeur = equipement.misEnServiceLe?.format(FORMAT_MOIS) ?: "—",
                libelle = "Mise en service",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * La tendance des relevés : la surchauffe des dernières visites.
 *
 * Un histogramme sans axes ni valeurs, et c'est assez : ce qu'on y cherche
 * n'est pas un chiffre — ils sont dans les interventions — mais une *pente*.
 * La dernière barre est mise en avant.
 */
@Composable
fun TendanceReleves(releves: List<Releve>, modifier: Modifier = Modifier) {
    val valeurs = releves.mapNotNull { it.surchauffeK }.takeLast(NOMBRE_BARRES)
    if (valeurs.size < 2) return

    val maximum = valeurs.max().coerceAtLeast(0.1)

    Section(intitule = "Derniers relevés", modifier = modifier) {
        Carte(relief = true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                valeurs.forEachIndexed { index, valeur ->
                    val derniere = index == valeurs.lastIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction = (valeur / maximum).toFloat().coerceIn(0.08f, 1f))
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(
                                if (derniere) AValider else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                    )
                }
            }
            Text(
                text = descriptionTendance(valeurs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * La saisie de la plaque signalétique et du fluide.
 *
 * Tout y est facultatif : une plaque illisible est un cas courant, et un
 * formulaire qui refuse d'enregistrer tant qu'on n'a pas tout relevé ferait
 * simplement renoncer à en relever une partie.
 */
@Composable
fun DialogueFicheMachine(
    equipement: Equipement,
    onValider: (Equipement) -> Unit,
    onFermer: () -> Unit,
) {
    var marque by remember { mutableStateOf(equipement.marque) }
    var modele by remember { mutableStateOf(equipement.modele) }
    var numeroSerie by remember { mutableStateOf(equipement.numeroSerie) }
    var fluide by remember { mutableStateOf(equipement.fluide) }
    var charge by remember { mutableStateOf(Nombres.enTexte(equipement.chargeKg)) }
    var miseEnService by remember { mutableStateOf(equipement.misEnServiceLe?.toString().orEmpty()) }
    var controle by remember { mutableStateOf(equipement.dernierControleLe?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Plaque et fluide") },
        text = {
            // Le contenu dépasse la hauteur d'écran sur un petit téléphone.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChampTexte(libelle = "Marque", valeur = marque, onValeur = { marque = it })
                ChampTexte(libelle = "Modèle", valeur = modele, onValeur = { modele = it })
                ChampTexte(
                    libelle = "Numéro de série",
                    valeur = numeroSerie,
                    onValeur = { numeroSerie = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChampTexte(
                        libelle = "Fluide",
                        valeur = fluide,
                        onValeur = { fluide = it },
                        modifier = Modifier.weight(1f),
                    )
                    ChampTexte(
                        libelle = "Charge (kg)",
                        valeur = charge,
                        onValeur = { charge = it },
                        clavier = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                val gwp = Fluides.gwp(fluide)
                val masse = Nombres.versDecimal(charge)
                Text(
                    text = when {
                        gwp == null && fluide.isNotBlank() ->
                            "Fluide hors catalogue : ni GWP ni périodicité ne seront calculés."

                        gwp != null && masse != null -> {
                            val tonnes = (masse * gwp / 1000.0).arrondiDixieme()
                            "GWP $gwp · ${Nombres.enTexte(tonnes)} t éq. CO₂"
                        }

                        else -> "Le fluide et la charge déterminent la périodicité des contrôles."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChampTexte(
                    libelle = "Mise en service (AAAA-MM-JJ)",
                    valeur = miseEnService,
                    onValeur = { miseEnService = it },
                )
                ChampTexte(
                    libelle = "Dernier contrôle d'étanchéité (AAAA-MM-JJ)",
                    valeur = controle,
                    onValeur = { controle = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValider(
                        equipement.copy(
                            marque = marque.trim(),
                            modele = modele.trim(),
                            numeroSerie = numeroSerie.trim(),
                            fluide = Fluides.normaliser(fluide),
                            chargeKg = Nombres.versDecimal(charge),
                            misEnServiceLe = dateOuNull(miseEnService),
                            dernierControleLe = dateOuNull(controle),
                        ),
                    )
                },
            ) {
                Text(text = "Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** Une date saisie, ou `null` si elle est incomplète ou mal formée. */
private fun dateOuNull(valeur: String): LocalDate? =
    runCatching { LocalDate.parse(valeur.trim()) }.getOrNull()

private fun libelleEtancheite(etat: EtatEtancheite): String = when {
    etat.enRetard -> "Étanchéité en retard"
    etat.echeance != null -> "Étanchéité due"
    etat.periodicite.mois != null -> "Étanchéité · ${etat.periodicite.libelle.lowercase()}"
    else -> "Étanchéité · non soumise"
}

/** « Surchauffe en hausse sur les 6 dernières visites », ou le contraire. */
private fun descriptionTendance(valeurs: List<Double>): String {
    val sens = valeurs.last() - valeurs.first()
    val mot = when {
        sens > 1.0 -> "en hausse"
        sens < -1.0 -> "en baisse"
        else -> "stable"
    }
    return "Surchauffe $mot sur les ${valeurs.size} dernières visites"
}

/** Six barres : assez pour voir une pente, assez peu pour rester lisible. */
private const val NOMBRE_BARRES = 6

private val FORMAT_MOIS: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yyyy")
