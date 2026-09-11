package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.Prestation
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.IntituleSection
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * Ce que les Réglages règlent, au-delà des types d'intervention.
 *
 * Trois blocs, dans l'ordre de la maquette : qui est le technicien, comment
 * l'application se comporte, et ce qu'on fait des données.
 */

/** La fiche du technicien : son nom, et son attestation fluides. */
@Composable
fun SectionTechnicien(
    parametres: Parametres,
    onTechnicien: (String) -> Unit,
    onAttestation: (String) -> Unit,
) {
    var ouverte by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IntituleSection(texte = "Technicien")
        Carte(relief = true, onClick = { ouverte = true }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = parametres.initiales.ifEmpty { "?" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parametres.technicien.ifBlank { "Nom non renseigné" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = parametres.attestation.ifBlank { "Attestation fluides non renseignée" },
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (ouverte) {
        var nom by remember { mutableStateOf(parametres.technicien) }
        var attestation by remember { mutableStateOf(parametres.attestation) }
        AlertDialog(
            onDismissRequest = { ouverte = false },
            title = { Text(text = "Technicien") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChampTexte(libelle = "Nom", valeur = nom, onValeur = { nom = it })
                    ChampTexte(
                        libelle = "Attestation fluides",
                        valeur = attestation,
                        onValeur = { attestation = it },
                    )
                    Text(
                        text = "Ces mentions figurent sur les comptes-rendus remis au client.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTechnicien(nom)
                        onAttestation(attestation)
                        ouverte = false
                    },
                ) {
                    Text(text = "Enregistrer")
                }
            },
            dismissButton = { TextButton(onClick = { ouverte = false }) { Text(text = "Annuler") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** Le comportement de l'application : thème, cibles, chronomètre, tarifs. */
@Composable
fun SectionGeneral(
    parametres: Parametres,
    onThemeSombre: (Boolean) -> Unit,
    onModeGants: (Boolean) -> Unit,
    onChronoAuto: (Boolean) -> Unit,
    onTauxHoraire: (Double) -> Unit,
    onTauxTva: (Double) -> Unit,
) {
    var tarifsOuverts by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IntituleSection(texte = "Général")
        Carte(contour = true) {
            LigneInterrupteur(
                intitule = "Thème sombre",
                actif = parametres.themeSombre,
                onChange = onThemeSombre,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LigneInterrupteur(
                intitule = "Mode gants",
                detail = "Agrandit les boutons et les lignes touchables.",
                actif = parametres.modeGants,
                onChange = onModeGants,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LigneInterrupteur(
                intitule = "Chrono automatique à l'arrivée",
                // Désactivé par défaut, et le dire : un chrono qui démarre à
                // l'insu du technicien fausse le temps facturé, et il vaut
                // mieux un chrono oublié qu'un chrono faux.
                detail = "Démarre le temps dès qu'une intervention passe en cours.",
                actif = parametres.chronoAuto,
                onChange = onChronoAuto,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Tarifs", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${Nombres.enEuros(parametres.tauxHoraire)} HT · " +
                            "TVA ${Nombres.enTexte(parametres.tauxTva)} %",
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { tarifsOuverts = true }) { Text(text = "Modifier") }
            }
        }
    }

    if (tarifsOuverts) {
        var taux by remember { mutableStateOf(Nombres.enTexte(parametres.tauxHoraire)) }
        var tva by remember { mutableStateOf(Nombres.enTexte(parametres.tauxTva)) }
        AlertDialog(
            onDismissRequest = { tarifsOuverts = false },
            title = { Text(text = "Tarifs") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChampTexte(
                        libelle = "Taux horaire HT",
                        valeur = taux,
                        onValeur = { taux = it },
                        clavier = KeyboardType.Decimal,
                    )
                    ChampTexte(
                        libelle = "TVA par défaut (%)",
                        valeur = tva,
                        onValeur = { tva = it },
                        clavier = KeyboardType.Decimal,
                    )
                    Text(
                        text = "Un devis garde le taux en vigueur au moment où il est créé : " +
                            "changer ce réglage ne recalcule aucun devis déjà établi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        Nombres.versDecimal(taux)?.let(onTauxHoraire)
                        Nombres.versDecimal(tva)?.let(onTauxTva)
                        tarifsOuverts = false
                    },
                ) {
                    Text(text = "Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { tarifsOuverts = false }) { Text(text = "Annuler") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun LigneInterrupteur(
    intitule: String,
    actif: Boolean,
    onChange: (Boolean) -> Unit,
    detail: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = intitule, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = actif,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

/**
 * Le catalogue de prestations, et ses prix.
 *
 * Il est livré avec les intitulés du métier et **sans les tarifs** : « recharge
 * R-449A » est le vocabulaire d'un métier, un prix celui d'une entreprise. C'est
 * donc ici, et nulle part ailleurs, qu'ils se renseignent — un catalogue qu'on
 * ne peut pas tarifer ne sert à rien, et la feuille des devis annonce
 * « prix à renseigner » en attendant.
 *
 * La section se replie, et part repliée. Vingt et une lignes ouvertes
 * écraseraient tout le reste des Réglages, alors qu'on y vient une fois — le
 * jour où l'on pose ses tarifs — puis presque jamais.
 */
@Composable
fun SectionCatalogue(
    prestations: List<Prestation>,
    onPrix: (Prestation, Double, String) -> Unit,
) {
    var dépliée by remember { mutableStateOf(false) }
    var enEdition by remember { mutableStateOf<Prestation?>(null) }
    val àTarifer = prestations.count { !it.tarifee }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IntituleSection(texte = "Catalogue de prestations")
        Carte(relief = true, onClick = { dépliée = !dépliée }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${prestations.size} prestations",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        // Le compte de ce qui manque est dit en clair : c'est la
                        // seule chose qui doit faire ouvrir cette section.
                        text = when {
                            prestations.isEmpty() -> "Catalogue vide"
                            àTarifer == 0 -> "Toutes tarifées"
                            àTarifer == 1 -> "1 prix à renseigner"
                            else -> "$àTarifer prix à renseigner"
                        },
                        style = StyleChiffrePetit,
                        color = if (àTarifer > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = if (dépliée) "▾" else "›",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (dépliée) {
            // Les non tarifées d'abord : c'est ce qu'on est venu faire, et les
            // chercher parmi vingt autres serait la meilleure façon d'en oublier.
            prestations
                .sortedWith(compareBy({ it.tarifee }, { it.rang }))
                .forEach { prestation ->
                    LignePrestationReglages(
                        prestation = prestation,
                        onModifier = { enEdition = prestation },
                    )
                }
        }
    }

    enEdition?.let { prestation ->
        DialoguePrixPrestation(
            prestation = prestation,
            onValider = { prix, unite ->
                onPrix(prestation, prix, unite)
                enEdition = null
            },
            onFermer = { enEdition = null },
        )
    }
}

@Composable
private fun LignePrestationReglages(prestation: Prestation, onModifier: () -> Unit) {
    Carte(contour = true, forme = MaterialTheme.shapes.medium, onClick = onModifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prestation.designation,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = prestation.categorie.libelle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (prestation.tarifee) {
                    Nombres.enEuros(prestation.prixUnitaire) +
                        prestation.unite.let { if (it.isBlank()) "" else " / $it" }
                } else {
                    "à renseigner"
                },
                style = StyleChiffrePetit,
                color = if (prestation.tarifee) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 1,
            )
        }
    }
}

/**
 * Le prix d'une prestation, et son unité.
 *
 * L'unité est modifiable avec le prix parce que les deux vont ensemble : un
 * tarif de 38 € ne veut rien dire sans « par kilo », et c'est le genre de chose
 * qu'on corrige au moment où l'on tape le chiffre.
 */
@Composable
private fun DialoguePrixPrestation(
    prestation: Prestation,
    onValider: (Double, String) -> Unit,
    onFermer: () -> Unit,
) {
    var prix by remember { mutableStateOf(if (prestation.tarifee) Nombres.enTexte(prestation.prixUnitaire) else "") }
    var unite by remember { mutableStateOf(prestation.unite) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = prestation.designation) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChampTexte(
                    libelle = "Prix unitaire (€)",
                    valeur = prix,
                    onValeur = { prix = it },
                    clavier = KeyboardType.Decimal,
                )
                ChampTexte(libelle = "Unité", valeur = unite, onValeur = { unite = it })
                Text(
                    text = "Le prix est recopié sur la ligne du devis : le changer ici ne " +
                        "touche pas aux devis déjà établis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onValider(Nombres.versDecimal(prix) ?: 0.0, unite) }) {
                Text(text = "Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
