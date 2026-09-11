package com.frigopro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Client
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.FrigoProTheme
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.time.LocalDate
import java.time.LocalTime

/**
 * L'écran d'accueil : ce qu'il faut savoir en ouvrant l'application.
 *
 * Il répond à une seule question — « et maintenant ? » — et tout y est
 * subordonné : l'intervention en cours occupe le haut, en grand, avec de quoi
 * l'ouvrir ou s'y rendre sans rien chercher ; le reste de la journée suit en
 * lignes serrées ; les chiffres et les échéances ferment la marche.
 *
 * Aucune navigation dans le temps ici. L'accueil montre *aujourd'hui*, et le
 * Planning montre les autres jours : donner deux façons de changer de date
 * conduirait à se demander laquelle des deux on regarde.
 */
@Composable
fun EcranAujourdhui(
    etat: EtatAujourdhui,
    onOuvrir: (Intervention) -> Unit,
    onItineraire: (Client) -> Unit,
    onVoirPlanning: () -> Unit,
    onVoirDevis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La coquille pose déjà les marges des barres système.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            EnTeteAccueil(etat = etat)

            if (etat.enAvant != null) {
                CarteEnAvant(
                    ligne = etat.enAvant,
                    onOuvrir = { onOuvrir(etat.enAvant.intervention) },
                    onItineraire = { etat.enAvant.client?.let(onItineraire) },
                )
            } else {
                Carte {
                    Text(
                        text = if (etat.journeeVide) "Aucune intervention aujourd'hui" else "Journée terminée",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (etat.journeeVide) {
                            "Le planning est libre. De quoi rattraper une échéance, ou souffler."
                        } else {
                            "Tout ce qui était prévu est fait."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TuileChiffre(
                    valeur = "${etat.nombreDuJour}",
                    libelle = "aujourd'hui",
                    modifier = Modifier.weight(1f),
                )
                TuileChiffre(
                    valeur = "${etat.devisEnAttente}",
                    libelle = "devis en attente",
                    modifier = Modifier.weight(1f),
                    couleur = LocalStatuts.current.aValider,
                )
                TuileChiffre(
                    valeur = Nombres.enEurosCourt(etat.chiffreDuMois),
                    libelle = "acceptés ce mois",
                    modifier = Modifier.weight(1f),
                )
            }

            if (etat.suite.isNotEmpty()) {
                Section(intitule = "Suite de la journée", espacement = 8.dp) {
                    etat.suite.forEach { ligne ->
                        LigneSuite(ligne = ligne, onOuvrir = { onOuvrir(ligne.intervention) })
                    }
                }
            }

            if (etat.echeances.isNotEmpty()) {
                Section(intitule = "Échéances F-Gas", espacement = 8.dp) {
                    etat.echeances.forEach { echeance -> LigneEcheance(echeance = echeance) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoutonContour(
                    texte = "Planning",
                    onClick = onVoirPlanning,
                    modifier = Modifier.weight(1f),
                )
                BoutonContour(
                    texte = "Devis",
                    onClick = onVoirDevis,
                    modifier = Modifier.weight(1f),
                )
            }
            EspaceVertical(24)
        }
    }
}

/** « Bonjour Karim », la date du jour, et la pastille des initiales. */
@Composable
private fun EnTeteAccueil(etat: EtatAujourdhui) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = libelleDate(etat.jour),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (etat.technicien.isBlank()) "Bonjour" else "Bonjour ${etat.technicien}",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Les initiales viennent des réglages ; tant que le nom n'y est pas,
        // il n'y a rien à afficher et la pastille disparaît plutôt que de
        // montrer un point d'interrogation.
        if (etat.initiales.isNotBlank()) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = etat.initiales,
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * La carte du haut : l'intervention en cours, ou la prochaine.
 *
 * C'est la seule carte de l'application à porter deux boutons pleins. Elle les
 * mérite : ouvrir la fiche et lancer l'itinéraire sont les deux gestes du
 * matin, et les faire chercher dans une liste coûterait plus cher que la place
 * qu'ils prennent.
 */
@Composable
private fun CarteEnAvant(ligne: LigneTournee, onOuvrir: () -> Unit, onItineraire: () -> Unit) {
    val intervention = ligne.intervention
    val couleur = couleurStatut(intervention)
    Carte(relief = true, liseré = couleur, onClick = onOuvrir) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Puce(
                texte = libelleStatut(intervention),
                couleur = couleur,
                fond = couleur.copy(alpha = 0.16f),
            )
            Text(
                text = creneau(intervention),
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = intervention.client,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sousTitre = listOf(intervention.typeLibelle, intervention.equipementNom)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (sousTitre.isNotEmpty()) {
            Text(
                text = sousTitre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val adresse = ligne.client?.adresseComplete.orEmpty().ifBlank { intervention.ville }
        if (adresse.isNotBlank()) {
            Text(
                text = adresse,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BoutonPlein(texte = "Ouvrir la fiche", onClick = onOuvrir, modifier = Modifier.weight(1.4f))
            if (ligne.localisable) {
                BoutonContour(texte = "Itinéraire", onClick = onItineraire, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Une ligne de la suite du jour : l'heure, le client, et ce qu'on y fait. */
@Composable
private fun LigneSuite(ligne: LigneTournee, onOuvrir: () -> Unit) {
    val intervention = ligne.intervention
    Carte(onClick = onOuvrir, forme = MaterialTheme.shapes.medium) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = intervention.heure.format(FORMAT_HEURE),
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 30.dp)
                    .background(couleurStatut(intervention), MaterialTheme.shapes.extraSmall),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = intervention.client,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = intervention.typeLibelle.ifBlank { intervention.ville },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Une échéance de contrôle d'étanchéité.
 *
 * Le retard est dit en rouge et en toutes lettres : c'est le seul élément de
 * l'accueil qui expose à une sanction, et une date seule ne se compare pas
 * d'un coup d'œil à celle du jour.
 */
@Composable
private fun LigneEcheance(echeance: EcheanceFgas) {
    val couleur = if (echeance.etat.enRetard) {
        LocalStatuts.current.urgence
    } else {
        LocalStatuts.current.aValider
    }
    Carte(contour = true, forme = MaterialTheme.shapes.medium) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = couleur.copy(alpha = 0.16f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${echeance.echeance.dayOfMonth}",
                        style = StyleChiffrePetit,
                        color = couleur,
                    )
                    Text(
                        text = moisCourt(echeance.echeance),
                        style = MaterialTheme.typography.labelSmall,
                        color = couleur,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = echeance.clientNom.ifBlank { echeance.equipement.nom },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(if (echeance.etat.enRetard) "En retard" else "Contrôle d'étanchéité")
                        append(" · ")
                        append(echeance.equipement.nom)
                        if (echeance.equipement.fluide.isNotBlank()) {
                            append(" · ")
                            append(echeance.equipement.fluide)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (echeance.etat.enRetard) couleur else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** « 08:00 → 09:30 », d'après l'heure de début et la durée prévue. */
private fun creneau(intervention: Intervention): String {
    val debut = intervention.heure
    val fin = debut.plusMinutes(intervention.dureeMin.toLong())
    return "${debut.format(FORMAT_HEURE)} → ${fin.format(FORMAT_HEURE)}"
}

@Preview(showBackground = true)
@Composable
private fun ApercuAujourdhui() {
    val client = Client(nom = "Boulangerie Martin", ville = "Lyon 2e", adresse = "12 rue des Halles")
    val intervention = Intervention(
        date = LocalDate.now(),
        heure = LocalTime.of(8, 0),
        client = client.nom,
        ville = client.ville,
        clientId = client.id,
        typeLibelle = "Dépannage chambre froide",
        statut = StatutIntervention.EN_COURS,
        urgente = true,
        dureeMin = 90,
    )
    FrigoProTheme {
        EcranAujourdhui(
            etat = EtatAujourdhui(
                technicien = "Karim",
                initiales = "K",
                enAvant = LigneTournee(intervention, client),
                suite = emptyList(),
                nombreDuJour = 1,
                devisEnAttente = 2,
                chiffreDuMois = 12400.0,
            ),
            onOuvrir = {},
            onItineraire = {},
            onVoirPlanning = {},
            onVoirDevis = {},
        )
    }
}
