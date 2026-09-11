package com.frigopro.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.theme.Ambre
import com.frigopro.app.ui.theme.BleuFroid
import com.frigopro.app.ui.theme.Cyan
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.time.LocalDate
import java.time.format.TextStyle as TextStyleJava
import java.util.Locale

/**
 * La semaine : où l'on passe, et quand.
 *
 * La tournée du jour répond à « et maintenant ? » ; cet écran répond à « est-ce
 * que je peux caser un dépannage jeudi ? », qui est une autre question et
 * demande une autre forme. D'où le bandeau des cinq jours, et le déroulé
 * horaire du jour retenu — les trous y sautent aux yeux, ce qu'une liste ne
 * permet pas.
 */
@Composable
fun EcranSemaine(
    lundi: LocalDate,
    jourRetenu: LocalDate,
    interventions: List<Intervention>,
    onJourRetenu: (LocalDate) -> Unit,
    onSemainePrecedente: () -> Unit,
    onSemaineSuivante: () -> Unit,
    onOuvrir: (Intervention) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duJour = interventions.filter { it.date == jourRetenu }.sortedBy { it.heure }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            EnTeteSemaine(
                lundi = lundi,
                total = interventions.size,
                onPrecedente = onSemainePrecedente,
                onSuivante = onSemaineSuivante,
            )
            BandeauJours(
                lundi = lundi,
                jourRetenu = jourRetenu,
                interventions = interventions,
                onJourRetenu = onJourRetenu,
            )
            LazyColumn(
                contentPadding = PaddingValues(
                    start = MargeEcran,
                    end = MargeEcran,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (duJour.isEmpty()) {
                    item { Encart(texte = "Journée libre. Rien de prévu ce jour-là.") }
                }
                items(items = duJour, key = { it.id }) { intervention ->
                    LigneHoraire(intervention = intervention, onOuvrir = { onOuvrir(intervention) })
                }
            }
        }
    }
}

@Composable
private fun EnTeteSemaine(
    lundi: LocalDate,
    total: Int,
    onPrecedente: () -> Unit,
    onSuivante: () -> Unit,
) {
    val vendredi = lundi.plusDays(4)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MargeEcran, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Semaine ${lundi.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())}",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "${jourCourt(lundi)} – ${jourCourt(vendredi)} · $total interventions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BoutonCarre(
            icone = Icons.AutoMirrored.Filled.ArrowBack,
            description = "Semaine précédente",
            onClick = onPrecedente,
        )
        BoutonCarre(
            icone = Icons.AutoMirrored.Filled.ArrowForward,
            description = "Semaine suivante",
            onClick = onSuivante,
        )
    }
}

/**
 * Les cinq jours ouvrés.
 *
 * Le compte d'interventions figure sous chaque jour : c'est l'information qui
 * fait choisir où poser un rendez-vous, et la lire demanderait sinon d'ouvrir
 * les cinq journées l'une après l'autre.
 */
@Composable
private fun BandeauJours(
    lundi: LocalDate,
    jourRetenu: LocalDate,
    interventions: List<Intervention>,
    onJourRetenu: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MargeEcran)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (0..4).forEach { decalage ->
            val jour = lundi.plusDays(decalage.toLong())
            val retenu = jour == jourRetenu
            val compte = interventions.count { it.date == jour }
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
                color = if (retenu) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                onClick = { onJourRetenu(jour) },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = jour.dayOfWeek
                            .getDisplayName(TextStyleJava.SHORT, Locale.FRENCH)
                            .uppercase()
                            .take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (retenu) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = "${jour.dayOfMonth}",
                        style = StyleChiffrePetit,
                        color = if (retenu) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = if (compte == 0) "—" else "$compte",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (retenu) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LigneHoraire(intervention: Intervention, onOuvrir: () -> Unit) {
    val liseré = when {
        intervention.urgente -> Ambre
        intervention.statut == StatutIntervention.TERMINEE -> Cyan
        else -> BleuFroid
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = intervention.heure.format(FORMAT_HEURE),
            style = StyleChiffrePetit,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(46.dp)
                .padding(top = 14.dp),
        )
        Carte(liseré = liseré, onClick = onOuvrir, modifier = Modifier.weight(1f)) {
            Text(
                text = intervention.client,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(intervention.typeLibelle, intervention.ville)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
