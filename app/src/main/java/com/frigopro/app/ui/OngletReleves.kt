package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.CourbesSaturation
import com.frigopro.app.data.Depannage
import com.frigopro.app.data.Fluides
import com.frigopro.app.data.enBarAbsolus
import com.frigopro.app.data.MouvementFluide
import com.frigopro.app.data.SensFluide
import com.frigopro.app.data.arrondiDixieme
import com.frigopro.app.data.enDuree
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonDiscret
import com.frigopro.app.ui.composants.Cadran
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampChiffre
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.IntituleSection
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.PlageMesure
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.composants.Reglette
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.AValider
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.time.Duration

/**
 * Le volet des relevés : le temps, les quatre grandeurs, et le fluide.
 *
 * C'est l'écran qu'on a sous les yeux manifold en main, et son ordre suit
 * celui du geste : on lance le temps en arrivant, on relève, et l'on ne
 * touche au fluide qu'après — un complément de charge posé avant d'avoir
 * relevé la surchauffe est précisément l'erreur que l'aide au dépannage
 * cherche à éviter.
 */
@Composable
fun OngletReleves(
    etat: EtatIntervention,
    ecoule: Duration,
    actions: ActionsIntervention,
    /** Les fluides dont la courbe de saturation a été contrôlée. */
    fluidesVerifies: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    var regletteOuverte by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CarteTempsPasse(etat = etat, ecoule = ecoule, actions = actions)

        Section(intitule = "Relevés frigorifiques") {
            val releve = etat.releve
            // Les cadrans et les réglettes **lisent** ; la saisie reste dans les
            // cases du dessous, sur place, comme elle l'a toujours été. Un relevé
            // se lit contre une plage — un curseur à gauche de la zone habituelle
            // se juge sans lire le chiffre —, mais il se tape dans une case.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Cadran(
                    libelle = "BP",
                    valeur = releve?.bpBar,
                    unite = "bar",
                    plage = ECHELLE_BP,
                    legende = legendeSaturation("T° évap.", etat.fluide, releve?.bpBar, CoteCircuit.ASPIRATION),
                    modifier = Modifier.weight(1f),
                )
                Cadran(
                    libelle = "HP",
                    valeur = releve?.hpBar,
                    unite = "bar",
                    plage = ECHELLE_HP,
                    legende = legendeSaturation("T° cond.", etat.fluide, releve?.hpBar, CoteCircuit.REFOULEMENT),
                    modifier = Modifier.weight(1f),
                )
            }
            Reglette(
                libelle = "Surchauffe",
                valeur = releve?.surchauffeK,
                unite = "K",
                plage = PLAGE_SURCHAUFFE,
            )
            Reglette(
                libelle = "Sous-refroidissement",
                valeur = releve?.sousRefroidissementK,
                unite = "K",
                plage = PLAGE_SOUS_REFROIDISSEMENT,
            )
            IntituleSection(texte = "SAISIE")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChampChiffre(
                    libelle = "BP",
                    valeur = releve?.bpBar,
                    unite = "bar",
                    onValeur = actions.onBp,
                    modifier = Modifier.weight(1f),
                )
                ChampChiffre(
                    libelle = "HP",
                    valeur = releve?.hpBar,
                    unite = "bar",
                    onValeur = actions.onHp,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChampChiffre(
                    libelle = "Surchauffe",
                    valeur = releve?.surchauffeK,
                    unite = "K",
                    onValeur = actions.onSurchauffe,
                    modifier = Modifier.weight(1f),
                    couleur = teinteEcart(
                        releve?.surchauffeK,
                        normalBas = Depannage.SURCHAUFFE_FAIBLE_K,
                        normalHaut = Depannage.SURCHAUFFE_ELEVEE_K,
                    ),
                )
                ChampChiffre(
                    libelle = "Sous-refr.",
                    valeur = releve?.sousRefroidissementK,
                    unite = "K",
                    onValeur = actions.onSousRefroidissement,
                    modifier = Modifier.weight(1f),
                    couleur = teinteEcart(
                        releve?.sousRefroidissementK,
                        normalBas = Depannage.SOUS_REFROIDISSEMENT_FAIBLE_K,
                        normalHaut = Depannage.SOUS_REFROIDISSEMENT_ELEVE_K,
                    ),
                )
            }
            // La réglette s'ouvre depuis l'endroit où l'on saisit les pressions,
            // et pas d'un onglet à part : la question « ça fait combien, ça ? » se
            // pose manomètre en main, le doigt encore sur la case BP.
            BoutonContour(
                texte = "Réglette pression / température",
                onClick = { regletteOuverte = true },
                modifier = Modifier.fillMaxWidth(),
                couleur = MaterialTheme.colorScheme.secondary,
            )

            val diagnostic = etat.diagnostic
            if (diagnostic != null) {
                Encart(
                    texte = diagnostic.symptome.libelle + ". Pistes : " +
                        diagnostic.causes.joinToString(", ") { it.intitule.lowercase() } + ".",
                    icone = Icons.Filled.Warning,
                    alerte = true,
                    complement = "Voir l'aide au dépannage",
                    onClick = actions.onOuvrirDepannage,
                )
            }
        }

        SectionFluide(etat = etat, actions = actions)
        EspaceVertical(24)
    }

    if (regletteOuverte) {
        FeuilleReglette(
            fluideMachine = etat.fluide,
            bpRelevee = etat.releve?.bpBar,
            hpRelevee = etat.releve?.hpBar,
            verifies = fluidesVerifies,
            onVerifier = actions.onVerifierFluide,
            onReporterPression = { cote, bar ->
                when (cote) {
                    CoteCircuit.ASPIRATION -> actions.onBp(bar)
                    CoteCircuit.REFOULEMENT -> actions.onHp(bar)
                }
            },
            onReporterEcart = { cote, kelvins ->
                when (cote) {
                    CoteCircuit.ASPIRATION -> actions.onSurchauffe(kelvins)
                    CoteCircuit.REFOULEMENT -> actions.onSousRefroidissement(kelvins)
                }
            },
            onFermer = { regletteOuverte = false },
        )
    }
}

/**
 * Le temps passé, et de quoi le corriger.
 *
 * L'heure d'arrivée est affichée en clair à côté du cumul : c'est elle qui
 * figurera sur le compte-rendu, et un technicien doit pouvoir vérifier d'un
 * coup d'œil qu'elle n'est pas absurde — un chrono lancé la veille et oublié
 * se voit immédiatement.
 *
 * Elle **affiche** le chronomètre et ne le commande pas : le seul bouton est
 * celui de la barre du bas, visible depuis les cinq volets. Deux boutons pour
 * une même bascule finissaient par se contredire sous les yeux — « Pause » ici,
 * « Reprendre » en bas, selon celui qu'on avait touché en dernier.
 */
@Composable
private fun CarteTempsPasse(
    etat: EtatIntervention,
    ecoule: Duration,
    actions: ActionsIntervention,
) {
    val chrono = etat.intervention.chrono
    var saisieOuverte by remember { mutableStateOf(false) }

    Carte(relief = true) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TEMPS PASSÉ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when {
                        chrono.vierge -> "Pas encore démarré"
                        chrono.enMarche -> "${heureLocale(chrono.arriveeLe)} → en cours"
                        // Sans heure d'arrivée, il n'y a pas de flèche à tracer :
                        // le temps a été saisi après coup, et l'écran le dit plutôt
                        // que d'afficher « — → 1 h 30 ».
                        chrono.arriveeLe == null -> ecoule.enDuree()
                        else -> "${heureLocale(chrono.arriveeLe)} → ${ecoule.enDuree()}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (chrono.enMarche) AValider else MaterialTheme.colorScheme.onSurface,
                )
                if (!chrono.vierge && chrono.arriveeLe == null) {
                    Text(
                        text = "Saisi à la main",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Le chronomètre suppose qu'on y pense en arrivant. On n'y pense pas
        // toujours, et ce chemin-là n'est pas un rattrapage : c'est celui du
        // technicien qui a laissé le téléphone dans la camionnette.
        BoutonDiscret(
            texte = if (chrono.vierge) "Saisir le temps passé" else "Corriger le temps",
            onClick = { saisieOuverte = true },
        )
    }

    if (saisieOuverte) {
        DialogueTempsPasse(
            initial = ecoule,
            onValider = {
                actions.onPoserTemps(it)
                saisieOuverte = false
            },
            onFermer = { saisieOuverte = false },
        )
    }
}

/**
 * La saisie du temps passé, à la fin plutôt qu'au début.
 *
 * Les durées courantes sont proposées en pastilles **et** les deux champs
 * restent ouverts : une pose de bi-split se dit « deux heures » et se touche
 * d'un doigt ganté, un dépannage se compte en « 1 h 47 » et se tape. Ne
 * proposer que les pastilles aurait fait arrondir un temps facturé, ne proposer
 * que les champs aurait fait taper quatre chiffres pour le cas courant.
 */
@Composable
private fun DialogueTempsPasse(
    initial: Duration,
    onValider: (Duration) -> Unit,
    onFermer: () -> Unit,
) {
    val depart = if (initial.isNegative) 0L else initial.toMinutes()
    var heures by remember { mutableStateOf((depart / 60).toString()) }
    var minutes by remember { mutableStateOf((depart % 60).toString()) }

    val h = heures.trim().toIntOrNull()
    val m = minutes.trim().toIntOrNull()
    val valide = h != null && m != null && h >= 0 && m in 0..59
    val total = if (valide) h!! * 60 + m!! else null

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Temps passé") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RangeePastilles(
                    options = TEMPS_PROPOSES,
                    retenue = total ?: -1,
                    libelle = ::libelleTempsPasse,
                    onChoisir = {
                        heures = (it / 60).toString()
                        minutes = (it % 60).toString()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChampTexte(
                        libelle = "Heures",
                        valeur = heures,
                        onValeur = { heures = it },
                        clavier = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    ChampTexte(
                        libelle = "Minutes",
                        valeur = minutes,
                        onValeur = { minutes = it },
                        clavier = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "L'heure d'arrivée n'est pas déduite du temps saisi : elle " +
                        "figure sur le compte-rendu, et une heure inventée y serait fausse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { total?.let { onValider(Duration.ofMinutes(it.toLong())) } },
                enabled = total != null,
            ) {
                Text(text = "Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** Les durées d'intervention courantes, en minutes. */
private val TEMPS_PROPOSES = listOf(15, 30, 45, 60, 90, 120, 180, 240)

/** « 45 min », « 1 h », « 1 h 30 ». */
private fun libelleTempsPasse(minutes: Int): String {
    val heures = minutes / 60
    val reste = minutes % 60
    return when {
        heures == 0 -> "$minutes min"
        reste == 0 -> "$heures h"
        else -> "$heures h $reste"
    }
}

/**
 * Le fluide : ce que porte la machine, et ce qu'on y a mis ou repris.
 *
 * Les deux totaux sont côte à côte parce que c'est ainsi qu'ils se lisent au
 * registre — un ajout sans récupération sur une installation qui fuit est
 * précisément ce qu'un contrôle cherche.
 */
@Composable
private fun SectionFluide(etat: EtatIntervention, actions: ActionsIntervention) {
    var saisieOuverte by remember { mutableStateOf<SensFluide?>(null) }
    var aSupprimer by remember { mutableStateOf<MouvementFluide?>(null) }

    Section(intitule = "Fluide") {
        Carte(contour = true) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (etat.fluide.isNotBlank()) {
                    Puce(
                        texte = etat.fluide,
                        chiffre = true,
                        couleur = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = descriptionCharge(etat),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TuileChiffre(
                    valeur = "+" + Nombres.enMasse(etat.ajoute),
                    libelle = "Ajouté",
                    unite = "kg",
                    couleur = MaterialTheme.colorScheme.primary,
                    contour = false,
                    modifier = Modifier.weight(1f),
                )
                TuileChiffre(
                    valeur = Nombres.enMasse(etat.recupere),
                    libelle = "Récupéré",
                    unite = "kg",
                    contour = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoutonContour(
                    texte = "Ajouter",
                    onClick = { saisieOuverte = SensFluide.AJOUT },
                    modifier = Modifier.weight(1f),
                )
                BoutonContour(
                    texte = "Récupérer",
                    onClick = { saisieOuverte = SensFluide.RECUPERATION },
                    modifier = Modifier.weight(1f),
                    couleur = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        etat.mouvements.forEach { mouvement ->
            LigneMouvement(mouvement = mouvement, onSupprimer = { aSupprimer = mouvement })
        }
    }

    aSupprimer?.let { mouvement ->
        AlertDialog(
            onDismissRequest = { aSupprimer = null },
            title = { Text(text = "Retirer ce mouvement du registre ?") },
            text = {
                Text(
                    text = "« ${libelleMouvement(mouvement, etat.intervention.date)} » sortira du " +
                        "registre des fluides. C'est une donnée réglementaire : ne la retirez " +
                        "que si elle a été saisie par erreur.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onSupprimerMouvement(mouvement.id)
                        aSupprimer = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = "Retirer")
                }
            },
            dismissButton = { TextButton(onClick = { aSupprimer = null }) { Text(text = "Annuler") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    val sens = saisieOuverte
    if (sens != null) {
        DialogueMouvementFluide(
            sens = sens,
            fluideParDefaut = etat.fluide,
            onValider = { masse, fluide ->
                actions.onMouvement(sens, masse, fluide)
                saisieOuverte = null
            },
            onFermer = { saisieOuverte = null },
        )
    }
}

/**
 * Une ligne du registre, et sa corbeille.
 *
 * Toucher la ligne l'effaçait, sans rien demander : un frôlement en faisant
 * défiler sortait un mouvement du registre réglementaire. La suppression passe
 * maintenant par une **corbeille visible**, à la taille des cibles du mode gants,
 * puis par une confirmation qui nomme ce qui part ; la ligne elle-même ne fait
 * plus rien.
 */
@Composable
private fun LigneMouvement(mouvement: MouvementFluide, onSupprimer: () -> Unit) {
    Carte {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = mouvement.sens.libelle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = mouvement.fluide,
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = (if (mouvement.sens == SensFluide.AJOUT) "+" else "−") +
                    Nombres.enMasse(mouvement.masseKg) + " kg",
                style = StyleChiffrePetit,
                color = if (mouvement.sens == SensFluide.AJOUT) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            BoutonCarre(
                icone = Icons.Filled.Delete,
                description = "Retirer ce mouvement du registre",
                onClick = onSupprimer,
                couleur = MaterialTheme.colorScheme.onSurfaceVariant,
                fond = Color.Transparent,
            )
        }
    }
}

/**
 * La saisie d'un mouvement.
 *
 * Le fluide est pré-rempli par celui de la machine mais reste modifiable :
 * une installation reconvertie se rencontre, et consigner le mauvais fluide
 * au registre est pire que de ne rien consigner.
 */
@Composable
private fun DialogueMouvementFluide(
    sens: SensFluide,
    fluideParDefaut: String,
    onValider: (Double, String) -> Unit,
    onFermer: () -> Unit,
) {
    var masse by remember { mutableStateOf("") }
    var fluide by remember { mutableStateOf(fluideParDefaut) }
    val valeur = Nombres.versDecimal(masse)

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = if (sens == SensFluide.AJOUT) "Fluide ajouté" else "Fluide récupéré") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChampTexte(
                    libelle = "Masse en kilogrammes",
                    valeur = masse,
                    onValeur = { masse = it },
                    clavier = KeyboardType.Decimal,
                )
                ChampFluide(
                    valeur = fluide,
                    onValeur = { fluide = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                val gwp = Fluides.gwp(fluide)
                Text(
                    text = if (gwp != null) {
                        "GWP $gwp — la ligne rejoindra le registre des fluides."
                    } else {
                        "Fluide hors catalogue : aucun GWP ne sera calculé."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { valeur?.let { onValider(it, fluide) } },
                enabled = valeur != null && valeur > 0.0 && fluide.isNotBlank(),
            ) {
                Text(text = "Consigner")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * « 1,2 kg de R-449A ajouté le 12/03 » : ce qu'une confirmation nomme.
 *
 * La date est celle de l'**intervention**, comme au registre, et non celle de la
 * saisie : c'est sous elle que la ligne y figure, et c'est elle qu'un contrôle
 * rapproche de la fiche.
 */
internal fun libelleMouvement(mouvement: MouvementFluide, date: java.time.LocalDate): String =
    "${Nombres.enTexte(mouvement.masseKg)} kg de ${mouvement.fluide.ifBlank { "fluide" }} " +
        "${mouvement.sens.libelle.lowercase()} le ${jourCourt(date)}"

/** « GWP 2141 · charge 6,2 kg », ou ce qu'on en sait. */
private fun descriptionCharge(etat: EtatIntervention): String {
    val gwp = Fluides.gwp(etat.fluide)
    val charge = etat.equipement?.chargeKg
    val morceaux = buildList {
        if (gwp != null) add("GWP $gwp")
        if (charge != null) add("charge ${Nombres.enTexte(charge)} kg")
        if (gwp != null && charge != null) {
            val tonnes = Fluides.tonnesEquivalentCo2(etat.fluide, charge)
            if (tonnes != null) add("${Nombres.enTexte(tonnes.arrondiDixieme())} t éq. CO₂")
        }
    }
    return morceaux.joinToString(" · ").ifEmpty { "Fluide et charge non renseignés" }
}

/**
 * Les échelles des deux cadrans, en bar relatifs. Ce sont des **échelles
 * d'affichage**, pas des plages habituelles : elles ne jugent rien, elles disent
 * jusqu'où l'arc se remplit. Au-delà — le CO₂ en haute pression —, l'arc est
 * plein et le chiffre au centre reste exact.
 */
private val ECHELLE_BP = PlageMesure(min = 0.0, max = 10.0)
private val ECHELLE_HP = PlageMesure(min = 0.0, max = 30.0)

/**
 * Les plages habituelles des deux écarts. Elles ne sont pas inventées ici :
 * ce sont les seuils de l'aide au dépannage ([Depannage]), si bien que le
 * curseur sort de la zone exactement quand une piste s'affiche. L'échelle de
 * 0 à 12 K est celle de la maquette.
 */
private val PLAGE_SURCHAUFFE = PlageMesure(
    min = 0.0,
    max = 12.0,
    habituelBas = Depannage.SURCHAUFFE_FAIBLE_K,
    habituelHaut = Depannage.SURCHAUFFE_ELEVEE_K,
)
private val PLAGE_SOUS_REFROIDISSEMENT = PlageMesure(
    min = 0.0,
    max = 12.0,
    habituelBas = Depannage.SOUS_REFROIDISSEMENT_FAIBLE_K,
    habituelHaut = Depannage.SOUS_REFROIDISSEMENT_ELEVE_K,
)

/**
 * « T° évap. −4,5 °C » sous un cadran : la saturation tirée des courbes
 * calculées, rosée à l'aspiration et bulle au refoulement — la même colonne que
 * la réglette ([CoteCircuit]). `null` sans fluide connu ou hors de la courbe :
 * rien n'est extrapolé.
 */
private fun legendeSaturation(
    intitule: String,
    fluide: String,
    pressionRelativeBar: Double?,
    cote: CoteCircuit,
): String? {
    if (pressionRelativeBar == null || fluide.isBlank()) return null
    val lecture = CourbesSaturation.temperatureA(fluide, pressionRelativeBar.enBarAbsolus())
        ?: return null
    val temperature = when (cote) {
        CoteCircuit.ASPIRATION -> lecture.temperatureRoseeC
        CoteCircuit.REFOULEMENT -> lecture.temperatureBulleC
    }
    return "$intitule ${Nombres.enTexte(temperature, decimales = 1).replace('-', '−')} °C"
}

/**
 * AValider dès qu'un écart sort de sa plage usuelle.
 *
 * C'est un signal, pas un diagnostic : la couleur attire l'œil sur la case,
 * l'aide au dépannage dit ce qu'elle veut dire.
 */
@Composable
internal fun teinteEcart(valeur: Double?, normalBas: Double, normalHaut: Double) =
    if (valeur != null && (valeur < normalBas || valeur > normalHaut)) {
        AValider
    } else {
        MaterialTheme.colorScheme.onSurface
    }
