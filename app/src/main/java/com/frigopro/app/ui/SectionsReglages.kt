package com.frigopro.app.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.Prestation
import com.frigopro.app.ui.composants.BoutonContour
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

/**
 * L'identité de l'entreprise : ce qui s'imprime en haut d'un devis.
 *
 * Elle est dans les Réglages et non sur chaque devis parce qu'elle ne change
 * pas : une raison sociale, une adresse, un SIRET se saisissent une fois. Le
 * document reste produisible sans — mieux vaut un devis sans en-tête que pas de
 * devis — mais l'écran du devis le signale, parce qu'un devis sans raison sociale
 * ni SIRET n'est pas un devis qu'on envoie.
 *
 * Le régime de TVA est ici, à côté, et pas dans la section des tarifs : il change
 * ce que le document **dit** — une TVA, ou la mention de l'article 293 B — et pas
 * seulement ce qu'il calcule.
 */
@Composable
fun SectionEntreprise(
    parametres: Parametres,
    /** Décode le logo à la demande, comme les photos de machines. */
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    onEntreprise: (String) -> Unit,
    onAdresse: (String) -> Unit,
    onTelephone: (String) -> Unit,
    onEmail: (String) -> Unit,
    onSiret: (String) -> Unit,
    onAssujettiTva: (Boolean) -> Unit,
    onChoisirLogo: () -> Unit,
    onRetirerLogo: () -> Unit,
) {
    var dépliée by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IntituleSection(texte = "Entreprise et documents")
        Carte(relief = true, onClick = { dépliée = !dépliée }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parametres.entreprise.ifBlank { "Entreprise non renseignée" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        // Ce qui manque est dit en clair : c'est la seule chose qui
                        // doit faire ouvrir cette section.
                        text = when {
                            !parametres.entreprisePresentable -> "Les devis partiront sans en-tête"
                            parametres.entrepriseSiret.isBlank() -> "SIRET à renseigner"
                            else -> parametres.mentionTva.ifBlank { "TVA ${Nombres.enTexte(parametres.tauxTva)} %" }
                        },
                        style = StyleChiffrePetit,
                        color = if (parametres.entreprisePresentable && parametres.entrepriseSiret.isNotBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
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
            Carte {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Le logo est montré tel qu'il partira : une image trop sombre
                    // ou de travers se voit ici, pas après l'envoi.
                    val fichier = parametres.logoFichier
                    if (fichier == null) {
                        Box(
                            modifier = Modifier.size(COTE_LOGO),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Aucun logo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // `Fit` et non `Crop` : un logo rogné n'est plus le logo, et
                        // c'est lui qui partira en haut du devis.
                        PhotoChargee(
                            fichier = fichier,
                            coteMax = COTE_LOGO_PX,
                            charger = chargerPhoto,
                            modifier = Modifier.size(COTE_LOGO),
                            contentDescription = "Logo de l'entreprise",
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BoutonContour(
                            texte = if (fichier == null) "Choisir un logo" else "Remplacer",
                            onClick = onChoisirLogo,
                            modifier = Modifier.fillMaxWidth(),
                            couleur = MaterialTheme.colorScheme.primary,
                        )
                        if (fichier != null) {
                            BoutonContour(
                                texte = "Retirer",
                                onClick = onRetirerLogo,
                                modifier = Modifier.fillMaxWidth(),
                                couleur = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            ChampTexte(
                libelle = "Raison sociale",
                valeur = parametres.entreprise,
                onValeur = onEntreprise,
            )
            ChampTexte(
                libelle = "Adresse",
                valeur = parametres.entrepriseAdresse,
                onValeur = onAdresse,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChampTexte(
                    libelle = "Téléphone",
                    valeur = parametres.entrepriseTelephone,
                    onValeur = onTelephone,
                    clavier = KeyboardType.Phone,
                    modifier = Modifier.weight(1f),
                )
                ChampTexte(
                    libelle = "SIRET",
                    valeur = parametres.entrepriseSiret,
                    onValeur = onSiret,
                    modifier = Modifier.weight(1f),
                )
            }
            ChampTexte(
                libelle = "Courriel",
                valeur = parametres.entrepriseEmail,
                onValeur = onEmail,
                clavier = KeyboardType.Email,
            )
            LigneInterrupteur(
                intitule = "Assujetti à la TVA",
                actif = parametres.assujettiTva,
                onChange = onAssujettiTva,
                detail = if (parametres.assujettiTva) {
                    "Les devis portent la TVA, et la TVA peut être offerte en remise."
                } else {
                    "Franchise en base : les devis portent « ${Parametres.MENTION_FRANCHISE} »."
                },
            )
            Text(
                text = "Le régime est recopié sur chaque devis à sa création : le changer " +
                    "ici ne touche pas aux devis déjà établis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Le logo dans les Réglages : assez grand pour juger de sa lisibilité. */
private val COTE_LOGO = 72.dp

/** Décodé un peu plus grand que son cadre, pour ne pas le voir pixelisé. */
private const val COTE_LOGO_PX = 256

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
    onEnregistrer: (Prestation) -> Unit,
    onSupprimer: (Prestation) -> Unit,
) {
    var dépliée by remember { mutableStateOf(false) }
    var enEdition by remember { mutableStateOf<Prestation?>(null) }
    var creationOuverte by remember { mutableStateOf(false) }
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
            // Le catalogue livré couvre le métier, pas une entreprise : une pièce
            // qu'on repose trois fois par mois et qui n'y figure pas finirait
            // saisie en ligne libre à chaque devis, et le catalogue ne grossirait
            // jamais. La même boîte s'ouvre depuis la feuille des devis.
            BoutonContour(
                texte = "+ Nouvelle prestation",
                onClick = { creationOuverte = true },
                modifier = Modifier.fillMaxWidth(),
                couleur = MaterialTheme.colorScheme.secondary,
            )
        }
    }

    if (creationOuverte) {
        DialoguePrestation(
            prestation = null,
            onValider = {
                onEnregistrer(it)
                creationOuverte = false
            },
            onFermer = { creationOuverte = false },
        )
    }

    enEdition?.let { prestation ->
        DialoguePrestation(
            prestation = prestation,
            onValider = {
                onEnregistrer(it)
                enEdition = null
            },
            onFermer = { enEdition = null },
            onSupprimer = {
                onSupprimer(prestation)
                enEdition = null
            },
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
                    text = prestation.categorie.libelle +
                        if (prestation.parUnite) " · par unité" else "",
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
