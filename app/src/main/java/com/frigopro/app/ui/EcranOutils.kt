package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.MargeEcran

/**
 * Les outils de l'onglet, et ce que chacun répond.
 *
 * Un onglet à part plutôt qu'une fonction cachée dans un écran : ce sont des
 * outils de **métier**, qu'on consulte sans intervention ouverte — au téléphone
 * avec un client, devant une plaque, en préparant une tournée. Les enfouir dans la
 * fiche d'une intervention obligeait à en ouvrir une pour convertir des psi.
 */
enum class Outil(
    val titre: String,
    val sousTitre: String,
    val icone: ImageVector,
) {
    REGLETTE(
        "Réglette pression / température",
        "Surchauffe et sous-refroidissement, dix-sept fluides",
        Icons.Filled.Straighten,
    ),
    CONVERTISSEUR(
        "Convertisseur d'unités",
        "bar et psi, °C et °F, kW et BTU/h, frigories",
        Icons.Filled.SwapHoriz,
    ),
    BILAN(
        "Puissance échangée",
        "Débit et Δt d'un circuit d'eau, d'eau glycolée ou d'air",
        Icons.Filled.Bolt,
    ),
    FGAS(
        "Contrôle d'étanchéité",
        "Équivalent CO₂ et périodicité réglementaire",
        Icons.Filled.VerifiedUser,
    ),
    FICHE_FLUIDE(
        "Fiche fluide",
        "GWP, classe de sécurité, glissement",
        Icons.Filled.Warning,
    ),
}

/**
 * L'onglet Outils : la liste, et l'outil ouvert.
 *
 * Un outil ouvert **remplace** la liste plutôt que de s'empiler dessus, comme un
 * devis ou une machine ailleurs dans l'application : une seule profondeur, un
 * simple `BackHandler`, et toujours pas de graphe de navigation.
 */
@Composable
fun OutilsRoute(
    modifier: Modifier = Modifier,
    viewModel: OutilsViewModel = viewModel(factory = OutilsViewModel.Factory),
) {
    val verifies by viewModel.fluidesVerifies.collectAsStateWithLifecycle()
    var ouvert by rememberSaveable { mutableStateOf<Outil?>(null) }

    BackHandler(enabled = ouvert != null) { ouvert = null }

    val outil = ouvert
    if (outil == null) {
        ListeOutils(onOuvrir = { ouvert = it }, modifier = modifier)
        return
    }

    CadreOutil(outil = outil, onFermer = { ouvert = null }, modifier = modifier) {
        when (outil) {
            Outil.REGLETTE -> CorpsReglette(
                // Aucune machine en vue : la réglette ouvre sur un fluide couvert,
                // et sans relevé où reporter quoi que ce soit.
                fluideMachine = "",
                bpRelevee = null,
                hpRelevee = null,
                verifies = verifies,
                onVerifier = viewModel::onVerifierFluide,
            )

            Outil.CONVERTISSEUR -> OutilConvertisseur()
            Outil.BILAN -> OutilBilanPuissance()
            Outil.FGAS -> OutilControleEtancheite()
            Outil.FICHE_FLUIDE -> OutilFicheFluide(verifies = verifies)
        }
    }
}

/** La liste des outils, un par carte. */
@Composable
private fun ListeOutils(onOuvrir: (Outil) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            Text(
                text = "Outils",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = MargeEcran, vertical = 12.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = MargeEcran, end = MargeEcran, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = Outil.entries, key = { it.name }) { outil ->
                    Carte(onClick = { onOuvrir(outil) }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = outil.icone,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = outil.titre, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = outil.sousTitre,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Le cadre commun d'un outil ouvert : son titre, et de quoi revenir. */
@Composable
private fun CadreOutil(
    outil: Outil,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
    contenu: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargeEcran, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoutonCarre(
                    icone = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Revenir aux outils",
                    onClick = onFermer,
                )
                Text(
                    text = outil.titre,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) { contenu() }
    }
}
