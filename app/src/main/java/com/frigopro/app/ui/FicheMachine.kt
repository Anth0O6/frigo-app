package com.frigopro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EtatEtancheite
import com.frigopro.app.data.Fluides
import com.frigopro.app.data.PeriodiciteControle
import com.frigopro.app.data.Releve
import com.frigopro.app.data.arrondiDixieme
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.AValider
import com.frigopro.app.ui.theme.LocalCibles
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
    val etancheite = EtatEtancheite.de(equipement, aujourdhui)
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
        // Dit sous la tuile plutôt que dans son libellé, qui n'a la place que
        // de trois mots : une échéance qui recule d'un an sans explication se
        // lit comme une erreur de saisie, et c'est la raison qu'on vient chercher.
        if (etancheite.doubleeParDetecteur) {
            Text(
                text = "Contrôle ${etancheite.periodicite.libelle.lowercase()} : périodicité " +
                    "doublée par le détecteur de fuite fixe (art. 4 § 3 du règlement 517/2014).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    /**
     * Les zones déjà employées dans le parc, proposées sous le champ.
     *
     * C'est le garde-fou du texte libre, et le seul qu'il y ait : sans elles
     * « Toiture », « toiture » et « toit » deviendraient trois zones, et un
     * groupement par zone qui éclate en trois ne range plus rien. Proposer
     * plutôt qu'imposer, parce qu'il faut bien pouvoir en créer une.
     */
    zones: List<String> = emptyList(),
) {
    var zone by remember { mutableStateOf(equipement.zone) }
    var marque by remember { mutableStateOf(equipement.marque) }
    var modele by remember { mutableStateOf(equipement.modele) }
    var numeroSerie by remember { mutableStateOf(equipement.numeroSerie) }
    var fluide by remember { mutableStateOf(equipement.fluide) }
    var charge by remember { mutableStateOf(Nombres.enTexte(equipement.chargeKg)) }
    var miseEnService by remember { mutableStateOf(equipement.misEnServiceLe) }
    var controle by remember { mutableStateOf(equipement.dernierControleLe) }
    var detecteur by remember { mutableStateOf(equipement.detecteurFuite) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Zone, plaque et fluide") },
        text = {
            // Le contenu dépasse la hauteur d'écran sur un petit téléphone.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // La zone en premier, et c'est un choix de tâche plutôt que de
                // maquette : ranger un parc de cinquante machines veut dire ouvrir
                // cinquante fois cette boîte pour un seul champ, et le mettre
                // sous la plaque aurait demandé de faire défiler chaque fois.
                ChampTexte(
                    libelle = "Zone (toiture, linéaire surgelés…)",
                    valeur = zone,
                    onValeur = { zone = it },
                )
                if (zones.isNotEmpty()) {
                    ZonesProposees(zones = zones, onChoisir = { zone = it })
                }
                ChampTexte(libelle = "Marque", valeur = marque, onValeur = { marque = it })
                ChampTexte(libelle = "Modèle", valeur = modele, onValeur = { modele = it })
                ChampTexte(
                    libelle = "Numéro de série",
                    valeur = numeroSerie,
                    onValeur = { numeroSerie = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChampFluide(
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
                            val tonnes = masse * gwp / 1000.0
                            // La périodicité suit la case du détecteur pendant la
                            // saisie : c'est ainsi qu'on voit ce que la case change.
                            val periodicite = PeriodiciteControle.pour(tonnes, detecteurFixe = detecteur)
                            "GWP $gwp · ${Nombres.enTexte(tonnes.arrondiDixieme())} t éq. CO₂" +
                                " · ${periodicite.libelle.lowercase()}"
                        }

                        else -> "Le fluide et la charge déterminent la périodicité des contrôles."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CaseDetecteurFuite(detecteur = detecteur, onDetecteur = { detecteur = it })
                ChampDateFacultative(
                    libelle = "Mise en service",
                    date = miseEnService,
                    onDate = { miseEnService = it },
                )
                ChampDateFacultative(
                    libelle = "Dernier contrôle d'étanchéité",
                    date = controle,
                    onDate = { controle = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValider(
                        equipement.copy(
                            zone = zone.trim(),
                            marque = marque.trim(),
                            modele = modele.trim(),
                            numeroSerie = numeroSerie.trim(),
                            fluide = Fluides.normaliser(fluide),
                            chargeKg = Nombres.versDecimal(charge),
                            misEnServiceLe = miseEnService,
                            dernierControleLe = controle,
                            detecteurFuite = detecteur,
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

/**
 * Les zones déjà employées, à toucher pour remplir le champ.
 *
 * Elle **défile horizontalement**, et c'est le second endroit du projet à
 * l'assumer après les pastilles de périodicité : les intitulés sont libres, un
 * parc en porte parfois huit, et les faire tenir aurait demandé de les tronquer
 * — une zone tronquée ne se reconnaît pas, là où une zone hors cadre se ramène
 * d'un doigt. Rien n'est caché derrière : le champ au-dessus reste la saisie,
 * celles-ci ne sont qu'un raccourci.
 */
@Composable
internal fun ZonesProposees(zones: List<String>, onChoisir: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        zones.forEach { zone ->
            Puce(
                texte = zone,
                modifier = Modifier.clickable { onChoisir(zone) },
                couleur = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * La case du détecteur de fuite fixe.
 *
 * Le libellé dit **ce qu'elle change** — le contrôle s'espace — plutôt que de
 * nommer un équipement, et il écarte en toutes lettres le détecteur portatif :
 * tout frigoriste en a un dans sa caisse, et cocher la case parce qu'on s'en est
 * servi ferait reculer d'un an une échéance que le règlement n'a pas reculée.
 * Seul un système **installé à demeure** double l'intervalle (art. 4 § 3).
 *
 * Même interrupteur que dans l'outil F-Gas, pour que la même question ait la
 * même forme aux deux endroits où elle se pose.
 */
@Composable
private fun CaseDetecteurFuite(detecteur: Boolean, onDetecteur: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Détecteur de fuite fixe", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Installé à demeure, il double l'intervalle entre deux contrôles " +
                    "d'étanchéité. Un détecteur portatif ne compte pas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = detecteur,
            onCheckedChange = onDetecteur,
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
 * Une date facultative, choisie au calendrier et jamais tapée.
 *
 * Elle se tapait au format AAAA-MM-JJ, et une saisie mal formée — « 12/03/2024 »,
 * la façon dont tout le monde écrit une date — devenait `null` **sans un mot** :
 * le dernier contrôle d'étanchéité disparaissait, et l'échéance F-Gas avec lui,
 * sur une fiche qu'on croyait avoir remplie. Le calendrier ne laisse rien passer
 * de mal formé, et c'est la seule façon d'en être sûr.
 *
 * « Effacer » reste un geste à part entière : une date de mise en service posée
 * sur la mauvaise machine doit pouvoir s'enlever, et une date facultative qu'on
 * ne peut plus vider ne l'est plus. Sans date, le calendrier s'ouvre sur
 * aujourd'hui, qui n'est qu'un point de départ — rien n'est posé avant « Valider ».
 */
@Composable
private fun ChampDateFacultative(
    libelle: String,
    date: LocalDate?,
    onDate: (LocalDate?) -> Unit,
) {
    var choix by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Elle dit qu'elle se touche, par l'icône de calendrier : même règle que
        // le titre de la tournée et l'entrée au plan.
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = LocalCibles.current.action)
                .clickable { choix = true },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = libelle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = date?.let { libelleDateAvecAnnee(it) } ?: "Non renseignée",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (date == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = "Choisir la date : ${libelle.lowercase()}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        if (date != null) {
            TextButton(
                onClick = { onDate(null) },
                modifier = Modifier.heightIn(min = LocalCibles.current.action),
            ) {
                Text(text = "Effacer")
            }
        }
    }

    if (choix) {
        SelecteurDate(
            date = date ?: LocalDate.now(),
            onDateChoisie = { jour ->
                onDate(jour)
                choix = false
            },
            onFermer = { choix = false },
        )
    }
}

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
