package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
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
import com.frigopro.app.data.ModeDeplacement
import com.frigopro.app.data.PalierPrestation
import com.frigopro.app.data.CoutAchat
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.Prestation
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampChiffre
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.IntituleSection
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.theme.StyleChiffrePetit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import com.frigopro.app.ui.composants.Encart

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

    // Pas d'intitulé : la barre du haut nomme déjà la page, et le répéter juste
    // dessous ne disait rien de plus.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * L'affichage : thème, cibles tactiles, chronomètre.
 *
 * Trois interrupteurs, et rien d'autre. Ils étaient auparavant dans la même
 * section que les tarifs, ce qui mettait sur la même carte le thème sombre et le
 * taux horaire — un réglage de confort et une donnée qui part sur une facture.
 * Les séparer est ce qui rend le sommaire des Réglages lisible : on cherche l'un
 * une fois pour toutes, et l'autre chaque fois qu'un tarif change.
 */
@Composable
fun SectionAffichage(
    parametres: Parametres,
    onThemeSombre: (Boolean) -> Unit,
    onModeGants: (Boolean) -> Unit,
    onChronoAuto: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
    }
}

/**
 * Les tarifs et le paiement : ce qui chiffre un devis et ce qui part sur une
 * facture.
 *
 * C'était une **boîte de dialogue** ouverte depuis la section « Général », et
 * c'est maintenant une page à elle. Le changement n'est pas cosmétique : une
 * boîte se valide ou s'annule, et six réglages qui décident de tous les montants
 * de l'entreprise n'ont pas à tenir dans une fenêtre qu'on referme d'un geste de
 * travers. Chaque champ **s'enregistre à la frappe**, comme l'identité de
 * l'entreprise et comme une case de relevé : il n'y a donc pas de bouton
 * « Enregistrer » à oublier.
 *
 * [Parametres.coutHoraireInterne] est ici, et c'est une correction : la colonne
 * existait en base depuis la migration 16, la sauvegarde l'emportait, le calcul
 * de marge la lisait — mais **aucun écran ne permettait de la saisir**. La fiche
 * d'une intervention invitait donc à « renseigner le coût horaire interne dans
 * les Réglages », où il n'y avait rien à renseigner, et la marge d'une
 * intervention était de ce fait impossible à obtenir.
 */
@Composable
fun SectionTarifs(
    parametres: Parametres,
    onTauxHoraire: (Double) -> Unit,
    onCoutHoraireInterne: (Double) -> Unit,
    onTauxTva: (Double) -> Unit,
    onDelaiPaiement: (Int) -> Unit,
    onTauxPenalites: (Double) -> Unit,
    onCoefficientMateriel: (Double) -> Unit,
    onAssujettiTva: (Boolean) -> Unit,
    chiffrage: ActionsChiffrage = ActionsChiffrage(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IntituleSection(texte = "La main-d'œuvre")
        ChampDecimal(
            libelle = "Taux horaire facturé, HT",
            valeur = parametres.tauxHoraire,
            onValeur = onTauxHoraire,
        )
        ChampDecimal(
            libelle = "Coût horaire interne, HT",
            valeur = parametres.coutHoraireInterne,
            onValeur = onCoutHoraireInterne,
        )
        NoteReglage(
            texte = "Les deux ne sont pas la même chose : l'un est ce qu'une heure se " +
                "facture, l'autre ce qu'elle coûte à l'entreprise, salaire chargé compris. " +
                "C'est tout l'écart qui fait la marge, et c'est le second qui manquait : " +
                "sans lui la fiche d'une intervention ne peut pas la calculer.",
        )

        // Le régime et le taux **dans un même bloc**, et c'est une correction : le
        // régime était dans « Mon entreprise », le taux ici, si bien qu'un
        // franchisé qui ne passait que par les tarifs facturait 20 % de TVA sans
        // que rien le lui demande. Le régime vient en premier parce qu'il décide
        // si le taux a seulement un sens.
        IntituleSection(texte = "La TVA")
        BlocTva(
            parametres = parametres,
            onAssujettiTva = onAssujettiTva,
            onTauxTva = onTauxTva,
        )

        IntituleSection(texte = "Le matériel")
        ChampDecimal(
            libelle = "Coefficient matériel",
            valeur = parametres.coefficientMateriel,
            onValeur = onCoefficientMateriel,
        )
        NoteReglage(
            texte = "Il chiffre le matériel dont la fiche ne porte pas de prix de vente : " +
                "1,4 vend 100 € d'achat à 140 €. Laissé à zéro, un article sans prix de " +
                "vente se propose au prix coûtant — jamais à un prix inventé.",
        )

        IntituleSection(texte = "HT ou TTC")
        BlocChiffrage(parametres = parametres, actions = chiffrage)

        IntituleSection(texte = "Le paiement")
        ChampEntier(
            libelle = "Délai de paiement (jours)",
            valeur = parametres.delaiPaiementJours,
            onValeur = onDelaiPaiement,
        )
        ChampDecimal(
            libelle = "Pénalités de retard (% l'an)",
            valeur = parametres.tauxPenalitesRetard,
            onValeur = onTauxPenalites,
        )
        // Les deux valeurs paraissent sur chaque facture, et ce sont des
        // mentions obligatoires : mieux vaut dire ce qu'elles engagent au moment
        // de les saisir qu'après l'envoi.
        NoteReglage(
            texte = "Trente jours est le délai qui s'applique faute d'accord, soixante le " +
                "maximum légal. Laisser les pénalités à zéro veut dire « non fixées » et " +
                "non « aucune » : le taux légal s'applique alors de plein droit, et la " +
                "facture le mentionne.",
        )
    }
}

/**
 * Un champ décimal des Réglages, qui s'enregistre à la frappe.
 *
 * Aucun état local ici : [ChampTexte] tient déjà son texte et la position du
 * curseur tant qu'il a le focus, et ne reprend la valeur d'en haut qu'une fois
 * perdu. Une seconde couche du même mécanisme se serait disputé le curseur avec
 * la première.
 *
 * Un champ **vidé** ne remonte rien, parce que [Nombres.versDecimal] rend `null`
 * sur une saisie vide. C'est ce qu'il faut : zéro veut dire « non réglé » dans
 * ces écrans et se saisit en tapant zéro, alors que l'écrire à chaque champ en
 * cours d'effacement aurait mis un tarif à zéro à chaque correction.
 */
@Composable
private fun ChampDecimal(
    libelle: String,
    valeur: Double,
    onValeur: (Double) -> Unit,
) {
    ChampTexte(
        libelle = libelle,
        valeur = Nombres.enTexte(valeur),
        onValeur = { texte -> Nombres.versDecimal(texte)?.let(onValeur) },
        modifier = Modifier.fillMaxWidth(),
        clavier = KeyboardType.Decimal,
    )
}

/**
 * Le pendant entier de [ChampDecimal] : le délai de paiement, en jours.
 *
 * Une valeur hors des bornes du code de commerce est ramenée par le ViewModel, et
 * la correction **se voit** : le champ reprend la valeur retenue dès qu'il perd
 * le focus, si bien que saisir soixante-cinq jours affiche soixante.
 */
@Composable
private fun ChampEntier(
    libelle: String,
    valeur: Int,
    onValeur: (Int) -> Unit,
) {
    ChampTexte(
        libelle = libelle,
        valeur = valeur.toString(),
        onValeur = { texte -> texte.trim().toIntOrNull()?.let(onValeur) },
        modifier = Modifier.fillMaxWidth(),
        clavier = KeyboardType.Number,
    )
}

/** Le texte qui explique un réglage, sous le champ qu'il explique. */
@Composable
private fun NoteReglage(texte: String) {
    Text(
        text = texte,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * L'identité de l'entreprise : ce qui s'imprime en haut d'un devis.
 *
 * Elle est dans les Réglages et non sur chaque devis parce qu'elle ne change
 * pas : une raison sociale, une adresse, un SIRET se saisissent une fois. Le
 * document reste produisible sans — mieux vaut un devis sans en-tête que pas de
 * devis — mais l'accueil réclame ce qui manque, parce qu'une facture sans
 * adresse ni SIRET manque à des mentions obligatoires.
 *
 * La page s'ouvre **dépliée**, sans titre répété : elle était une carte repliée
 * sous un intitulé, qu'il fallait encore toucher après avoir choisi la page au
 * sommaire — deux gestes pour une seule intention. Le régime de TVA n'est plus
 * ici : il a rejoint le taux dans les tarifs, voir [BlocTva].
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
    onChoisirLogo: () -> Unit,
    onRetirerLogo: () -> Unit,
) {
    // Ce qui manque est dit en tête, en clair : ce sont des mentions qui
    // manqueront sur ce qui part chez un client.
    val manques = listOfNotNull(
        "la raison sociale".takeIf { parametres.entreprise.isBlank() },
        "l'adresse".takeIf { parametres.entrepriseAdresse.isBlank() },
        "le SIRET".takeIf { parametres.entrepriseSiret.isBlank() },
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (manques.isNotEmpty()) {
            Text(
                text = "Manque${if (manques.size > 1) "nt" else ""} ${manques.joinToString(", ")} : " +
                    "ce sont des mentions obligatoires d'une facture.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
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
    }
}

/**
 * Le régime de TVA et le taux, ensemble.
 *
 * Le régime se **choisit** entre deux pastilles plutôt que par un interrupteur
 * déjà levé : un interrupteur sur « assujetti » se lit comme un réglage fait, et
 * c'est ainsi qu'un franchisé facturait 20 % sans l'avoir décidé. Deux choix
 * nommés côte à côte posent la question. Rien n'est stocké de plus — ni
 * « régime confirmé » ni migration : la question est posée là où le taux se
 * saisit, et c'est là qu'on la voit.
 *
 * En franchise, le taux disparaît : il ne s'applique à rien, et un champ « 20 % »
 * resté visible laisserait croire le contraire. La mention de l'article 293 B est
 * dite en toutes lettres, avec ce qu'elle veut dire — c'est elle qui part sur le
 * document à la place de la TVA.
 */
@Composable
private fun BlocTva(
    parametres: Parametres,
    onAssujettiTva: (Boolean) -> Unit,
    onTauxTva: (Double) -> Unit,
) {
    RangeePastilles(
        options = listOf(true, false),
        retenue = parametres.assujettiTva,
        libelle = { if (it) "Assujetti" else "Franchise en base" },
        onChoisir = onAssujettiTva,
        modifier = Modifier.fillMaxWidth(),
    )
    if (parametres.assujettiTva) {
        ChampDecimal(
            libelle = "TVA par défaut (%)",
            valeur = parametres.tauxTva,
            onValeur = onTauxTva,
        )
        NoteReglage(
            texte = "Les devis et les factures portent la TVA à ce taux, et elle peut être " +
                "offerte en remise. Un devis garde le régime et le taux en vigueur à sa " +
                "création : les changer ici ne recalcule aucun document déjà établi.",
        )
    } else {
        NoteReglage(
            texte = "Franchise en base (art. 293 B du CGI) : vous ne facturez pas de TVA, " +
                "et chaque devis et facture porte la mention « ${Parametres.MENTION_FRANCHISE} ». " +
                "Elle n'est juste que tant que votre chiffre d'affaires reste sous le seuil " +
                "de la franchise ; au-delà, choisissez « Assujetti ». Un document déjà " +
                "établi garde son régime.",
        )
    }
}

/** Les gestes du bloc « HT ou TTC », regroupés comme ceux du déplacement. */
data class ActionsChiffrage(
    val onCoutAchat: (CoutAchat) -> Unit = {},
    val onSaisieTtc: (Boolean) -> Unit = {},
    val onTtcParLigne: (Boolean) -> Unit = {},
    val onSousTotaux: (Boolean) -> Unit = {},
)

/**
 * Comment l'application chiffre et montre la TVA, réglé une fois pour toutes —
 * chaque devis fournisseur repris et chaque devis ouvert permettant ensuite d'en
 * décider autrement sur place.
 *
 * Le coût d'achat se **choisit entre trois pastilles**, dont la première suit le
 * régime : c'est elle qui fait la bonne chose sans qu'on y pense, et le dire en
 * toutes lettres dessous évite qu'on la croie arbitraire. La saisie TTC ne se
 * propose qu'à une entreprise assujettie : en franchise il n'y a pas de TVA à
 * retirer, et une case sans effet vaut moins que pas de case.
 */
@Composable
private fun BlocChiffrage(parametres: Parametres, actions: ActionsChiffrage) {
    val assujettie = parametres.assujettiTva
    Text(
        text = "Coût d'achat d'un devis fournisseur repris",
        style = MaterialTheme.typography.bodyLarge,
    )
    RangeePastilles(
        options = CoutAchat.entries.toList(),
        retenue = parametres.coutAchat,
        libelle = { it.libelle },
        onChoisir = actions.onCoutAchat,
        modifier = Modifier.fillMaxWidth(),
    )
    NoteReglage(
        texte = when (parametres.coutAchat) {
            CoutAchat.SELON_REGIME -> if (assujettie) {
                "Assujetti : vous récupérez la TVA payée au fournisseur, le coût est donc " +
                    "le HT, et la marge s'applique dessus."
            } else {
                "Franchise : vous payez la TVA du fournisseur sans la récupérer, le coût " +
                    "est donc le TTC, et la marge s'applique dessus."
            }
            CoutAchat.HT -> "La marge s'applique toujours au prix HT du fournisseur."
            CoutAchat.TTC -> "La marge s'applique toujours au prix fournisseur TVA comprise."
        } + " Chaque devis fournisseur repris peut en décider autrement.",
    )
    Carte(contour = true) {
        if (assujettie) {
            LigneInterrupteur(
                intitule = "Saisir les prix en TTC",
                // La raison de la case, et sa limite : penser en TTC, oui ;
                // retirer la TVA d'une ligne, non — elle est due sur tout le devis.
                detail = "Lignes libres et lignes retouchées : vous tapez le prix TTC, le " +
                    "devis en déduit le HT. La TVA reste due sur toutes les lignes.",
                actif = parametres.saisieTtc,
                onChange = actions.onSaisieTtc,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LigneInterrupteur(
                intitule = "Afficher le TTC de chaque ligne",
                actif = parametres.ttcParLigne,
                onChange = actions.onTtcParLigne,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        LigneInterrupteur(
            intitule = "Sous-totaux par famille",
            detail = "Matériel, main-d'œuvre et prestations, déplacement, avant le total.",
            actif = parametres.sousTotaux,
            onChange = actions.onSousTotaux,
        )
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
 * Elle a sa page, et s'y ouvre **dépliée** : elle était une carte repliée sous un
 * intitulé, du temps où toutes les sections se suivaient sur un seul écran, et ce
 * repli ne protégeait plus rien une fois la page choisie au sommaire.
 */
@Composable
fun SectionCatalogue(
    prestations: List<Prestation>,
    onEnregistrer: (Prestation) -> Unit,
    onSupprimer: (Prestation) -> Unit,
    /** Les paliers dégressifs, par identifiant de prestation. */
    paliers: Map<String, List<PalierPrestation>> = emptyMap(),
    onDefinirPalier: (String, Int, Double) -> Unit = { _, _, _ -> },
    onSupprimerPalier: (String) -> Unit = {},
) {
    var enEdition by remember { mutableStateOf<Prestation?>(null) }
    var paliersOuverts by remember { mutableStateOf<Prestation?>(null) }
    var creationOuverte by remember { mutableStateOf(false) }
    val àTarifer = prestations.count { !it.tarifee }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Le compte de ce qui manque est dit en tête, en clair : c'est ce qu'on
        // vient faire sur cette page.
        Text(
            text = when {
                prestations.isEmpty() -> "Catalogue vide"
                àTarifer == 0 -> "${prestations.size} prestations, toutes tarifées"
                àTarifer == 1 -> "${prestations.size} prestations · 1 prix à renseigner"
                else -> "${prestations.size} prestations · $àTarifer prix à renseigner"
            },
            style = StyleChiffrePetit,
            color = if (àTarifer > 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        // Les non tarifées d'abord : c'est ce qu'on est venu faire, et les
        // chercher parmi vingt autres serait la meilleure façon d'en oublier.
        prestations
            .sortedWith(compareBy({ it.tarifee }, { it.rang }))
            .forEach { prestation ->
                LignePrestationReglages(
                    prestation = prestation,
                    paliers = paliers[prestation.id].orEmpty(),
                    onModifier = { enEdition = prestation },
                    onPaliers = { paliersOuverts = prestation },
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

    paliersOuverts?.let { prestation ->
        DialoguePaliers(
            prestation = prestation,
            paliers = paliers[prestation.id].orEmpty(),
            onDefinir = { rang, prix -> onDefinirPalier(prestation.id, rang, prix) },
            onSupprimer = onSupprimerPalier,
            onFermer = { paliersOuverts = null },
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
private fun LignePrestationReglages(
    prestation: Prestation,
    paliers: List<PalierPrestation>,
    onModifier: () -> Unit,
    onPaliers: () -> Unit,
) {
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
                // La dégressivité ne s'offre que sur une prestation comptée par
                // unité : un forfait n'a pas de rangs, et il n'y a rien à
                // dégresser sur « déplacement zone 1 ».
                if (prestation.parUnite) {
                    Puce(
                        texte = if (paliers.isEmpty()) {
                            "Prix dégressif ?"
                        } else {
                            "Dégressif : " + paliers.sortedBy { it.aPartirDe }
                                .joinToString(" / ") { Nombres.enTexte(it.prixUnitaire) }
                        },
                        modifier = Modifier.clickable(onClick = onPaliers),
                        couleur = MaterialTheme.colorScheme.secondary,
                    )
                }
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
 * Le tarif de déplacement, et la clé du service d'itinéraire.
 *
 * Les prix se règlent **une fois, ici**, et non sur chaque devis : les proposer
 * à chaque fois aurait invité à facturer chaque client différemment sans s'en
 * souvenir. Ils arrivent à zéro, comme le catalogue et pour la même raison — un
 * tarif kilométrique inventé partirait chez un vrai client sans que personne ne
 * l'ait relu, alors qu'un zéro se voit.
 *
 * Elle s'ouvre **dépliée** sur sa page, sans titre répété : voir
 * [SectionEntreprise].
 */
data class ActionsTarifDeplacement(
    val onAdresseDepart: (String) -> Unit = {},
    val onMode: (ModeDeplacement) -> Unit = {},
    val onPrixKm: (Double?) -> Unit = {},
    val onPrixHeure: (Double?) -> Unit = {},
    val onMinimum: (Double?) -> Unit = {},
    val onRefacturerPeages: (Boolean) -> Unit = {},
)

@Composable
fun SectionDeplacementReglages(
    parametres: Parametres,
    actions: ActionsTarifDeplacement,
) {
    val tarif = parametres.tarifDeplacement

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Ce qui manque est dit en tête, comme pour l'entreprise.
        if (!tarif.renseigne) {
            Text(
                text = "Tarif à renseigner : un déplacement se chiffre à 0 € tant qu'il ne l'est pas.",
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Carte {
            ChampTexte(
                libelle = "Adresse de départ",
                valeur = parametres.adresseDepart,
                onValeur = actions.onAdresseDepart,
            )
            Text(
                text = "Le dépôt, l'atelier ou le domicile : c'est le départ " +
                    "proposé sur un nouveau devis. Il reste modifiable trajet " +
                    "par trajet — le deuxième client de la journée se rejoint " +
                    "depuis le premier.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EspaceVertical(4)
            RangeePastilles(
                options = ModeDeplacement.entries,
                retenue = parametres.modeDeplacement,
                libelle = { it.libelle },
                onChoisir = actions.onMode,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Le kilomètre paie le véhicule, l'heure paie le chauffeur. " +
                    "Trente kilomètres d'autoroute et trente dans une ville ne " +
                    "coûtent pas le même temps : les deux ensemble est le cas le " +
                    "plus juste.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EspaceVertical(4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parametres.modeDeplacement.compteLesKm) {
                    ChampChiffre(
                        libelle = "Prix au km",
                        valeur = parametres.prixKm.takeIf { it > 0.0 },
                        unite = "€",
                        onValeur = actions.onPrixKm,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (parametres.modeDeplacement.compteLeTemps) {
                    ChampChiffre(
                        libelle = "Heure de trajet",
                        valeur = parametres.prixHeureTrajet.takeIf { it > 0.0 },
                        unite = "€",
                        onValeur = actions.onPrixHeure,
                        modifier = Modifier.weight(1f),
                    )
                }
                ChampChiffre(
                    libelle = "Minimum",
                    valeur = parametres.minimumDeplacement.takeIf { it > 0.0 },
                    unite = "€",
                    onValeur = actions.onMinimum,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Le minimum s'applique au trajet seul : un très petit " +
                    "déplacement est facturé à ce prix. Les péages s'ajoutent " +
                    "par-dessus — ce sont des débours, et les fondre dans un " +
                    "plancher les ferait disparaître sur les courtes distances.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LigneInterrupteur(
                intitule = "Refacturer les péages",
                actif = parametres.refacturerPeages,
                onChange = actions.onRefacturerPeages,
                detail = "Ajoutés au devis sur une ligne à part, doublés en aller-retour.",
            )

            EspaceVertical(4)
            Text(
                // Il n'y a **rien à configurer** ici, et le dire vaut mieux que
                // de laisser chercher : la version précédente demandait une clé
                // d'API, ce qu'aucun technicien n'allait faire.
                text = if (parametres.itineraireDisponible) {
                    "Le temps de trajet et les kilomètres se calculent tout seuls " +
                        "depuis les deux adresses d'un devis : il n'y a rien à " +
                        "installer ni à renseigner. Sans réseau, ils se saisissent " +
                        "à la main, et le devis se chiffre quand même. Les péages, " +
                        "eux, restent à saisir."
                } else {
                    "Le calcul automatique n'est pas disponible dans cette version : " +
                        "les kilomètres, la durée et les péages se saisissent à la " +
                        "main sur chaque devis."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le registre des fluides, et le geste qui le sort.
 *
 * Un objet plutôt que deux paramètres de plus : `ReglagesScreen` en portait déjà
 * trente, et c'est le même regroupement que celui du tarif de déplacement.
 */
data class ActionsRegistre(
    /** Les années où du fluide a bougé, la plus récente d'abord. */
    val annees: List<Int> = emptyList(),
    val onExporter: (Int) -> Unit = {},
    /** L'année en cours, toujours proposée : voir [SectionRegistre]. */
    val anneeCourante: Int = java.time.LocalDate.now().year,
    /**
     * L'attestation de capacité est réglée. Son absence se dit **ici**, avant
     * l'export, et non sur le document : un registre tendu à un inspecteur ne
     * porte pas de note qui renvoie aux Réglages d'une application.
     */
    val attestationRenseignee: Boolean = true,
)

/**
 * Le registre des fluides, dans les Réglages.
 *
 * Il est ici et non dans les Outils de l'Atelier — qui ne regarde aucune donnée de
 * l'application — ni sur une fiche machine, qui n'en montrerait qu'une. Un
 * registre est un document **de l'entreprise**, au même titre que la
 * sauvegarde : c'est la pièce qu'on sort quand un contrôle la demande, et c'est
 * là qu'on va la chercher.
 *
 * Les années passées sont **dérivées des mouvements** : proposer trois années
 * dont deux sont vides ferait ouvrir deux registres pour rien. **L'année en cours
 * est toujours proposée**, en revanche, mouvement ou non : le registre ne se tait
 * jamais — une année sans mouvement produit un document qui le dit, et c'est
 * celui qu'on pose sur la table quand un contrôle arrive en mars. Il n'avait
 * jusque-là pas de bouton, si bien que la propriété existait dans le document et
 * nulle part à l'écran.
 */
@Composable
fun SectionRegistre(actions: ActionsRegistre) {
    val annees = (listOf(actions.anneeCourante) + actions.annees).distinct().sortedDescending()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Carte(contour = true) {
            Text(
                text = "La traçabilité des fluides frigorigènes est exigible lors d'un " +
                    "contrôle (règlement UE 517/2014). Le registre reprend les mouvements " +
                    "consignés sur les interventions, groupés par fluide.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!actions.attestationRenseignee) {
                Encart(
                    texte = "Votre attestation de capacité n'est pas renseignée (page " +
                        "Technicien) : le registre sortira sans elle, et c'est le premier " +
                        "numéro qu'un contrôle y cherche.",
                    icone = Icons.Filled.Warning,
                    alerte = true,
                )
            }
            if (actions.annees.isEmpty()) {
                Text(
                    text = "Aucun mouvement consigné pour l'instant : le registre de " +
                        "l'année le dit, et c'est une réponse à un contrôle.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            annees.forEach { annee ->
                BoutonContour(
                    texte = "Registre $annee",
                    onClick = { actions.onExporter(annee) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
