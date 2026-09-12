package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Fluides
import com.frigopro.app.data.MouvementFluide
import com.frigopro.app.data.SensFluide
import com.frigopro.app.data.arrondiDixieme
import com.frigopro.app.data.enDuree
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampChiffre
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.AValider
import com.frigopro.app.ui.theme.Urgence
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CarteTempsPasse(etat = etat, ecoule = ecoule, actions = actions)

        Section(intitule = "Relevés frigorifiques") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChampChiffre(
                    libelle = "BP",
                    valeur = etat.releve?.bpBar,
                    unite = "bar",
                    onValeur = actions.onBp,
                    modifier = Modifier.weight(1f),
                )
                ChampChiffre(
                    libelle = "HP",
                    valeur = etat.releve?.hpBar,
                    unite = "bar",
                    onValeur = actions.onHp,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChampChiffre(
                    libelle = "Surchauffe",
                    valeur = etat.releve?.surchauffeK,
                    unite = "K",
                    onValeur = actions.onSurchauffe,
                    modifier = Modifier.weight(1f),
                    couleur = teinteEcart(etat.releve?.surchauffeK, normalBas = 3.0, normalHaut = 8.0),
                )
                ChampChiffre(
                    libelle = "Sous-refr.",
                    valeur = etat.releve?.sousRefroidissementK,
                    unite = "K",
                    onValeur = actions.onSousRefroidissement,
                    modifier = Modifier.weight(1f),
                    couleur = teinteEcart(
                        etat.releve?.sousRefroidissementK,
                        normalBas = 2.0,
                        normalHaut = 8.0,
                    ),
                )
            }
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
}

/**
 * Le temps passé, et de quoi le corriger.
 *
 * L'heure d'arrivée est affichée en clair à côté du cumul : c'est elle qui
 * figurera sur le compte-rendu, et un technicien doit pouvoir vérifier d'un
 * coup d'œil qu'elle n'est pas absurde — un chrono lancé la veille et oublié
 * se voit immédiatement.
 */
@Composable
private fun CarteTempsPasse(
    etat: EtatIntervention,
    ecoule: Duration,
    actions: ActionsIntervention,
) {
    val chrono = etat.intervention.chrono
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
                        else -> "${heureLocale(chrono.arriveeLe)} → ${ecoule.enDuree()}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (chrono.enMarche) Urgence else MaterialTheme.colorScheme.onSurface,
                )
            }
            BoutonContour(
                texte = if (chrono.enMarche) "Pause" else "Démarrer",
                onClick = actions.onBasculerChrono,
                couleur = if (chrono.enMarche) Urgence else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
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
            LigneMouvement(mouvement = mouvement, onSupprimer = actions.onSupprimerMouvement)
        }
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

@Composable
private fun LigneMouvement(mouvement: MouvementFluide, onSupprimer: (String) -> Unit) {
    Carte(onClick = { onSupprimer(mouvement.id) }) {
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
                ChampTexte(
                    libelle = "Fluide",
                    valeur = fluide,
                    onValeur = { fluide = it },
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
 * AValider dès qu'un écart sort de sa plage usuelle.
 *
 * C'est un signal, pas un diagnostic : la couleur attire l'œil sur la case,
 * l'aide au dépannage dit ce qu'elle veut dire.
 */
@Composable
private fun teinteEcart(valeur: Double?, normalBas: Double, normalHaut: Double) =
    if (valeur != null && (valeur < normalBas || valeur > normalHaut)) {
        AValider
    } else {
        MaterialTheme.colorScheme.onSurface
    }
