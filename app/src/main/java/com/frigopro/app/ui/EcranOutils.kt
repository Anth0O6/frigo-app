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
import androidx.compose.material.icons.filled.PublishedWithChanges
import androidx.compose.material.icons.filled.Sensors
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
 * Les outils de l'Atelier, et ce que chacun répond.
 *
 * Une vue à part plutôt qu'une fonction cachée dans un écran : ce sont des
 * outils de **métier**, qu'on consulte sans intervention ouverte — au téléphone
 * avec un client, devant une plaque, en préparant une tournée. Les enfouir dans la
 * fiche d'une intervention obligeait à en ouvrir une pour convertir des psi. Ils
 * ont été un onglet à eux seuls, et sont devenus la troisième vue de l'Atelier
 * (voir [VueAtelier]) quand le planning a eu besoin de la place.
 */
enum class Outil(
    val titre: String,
    val sousTitre: String,
    val icone: ImageVector,
) {
    REGLETTE(
        "Réglette pression / température",
        "Surchauffe et sous-refroidissement, vingt-trois fluides",
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
    SUBSTITUTION(
        "Remplacer un fluide",
        "Par quoi, et ce que la conversion demande",
        Icons.Filled.PublishedWithChanges,
    ),

    /**
     * Le seul outil qui **mesure** quelque chose plutôt que de calculer.
     *
     * Il ferme la marche pour cette raison : les six autres répondent à une
     * question qu'on se pose de tête, celui-ci demande d'approcher le téléphone
     * d'une bobine. Son sous-titre dit ce qu'il trouve et non ce qu'il est — un
     * « magnétomètre » ne dit rien à personne, « cette bobine est-elle
     * alimentée ? » se reconnaît tout de suite.
     */
    CHAMP_MAGNETIQUE(
        "Champ magnétique",
        "Bobine alimentée, contacteur collé, aimant de contact",
        Icons.Filled.Sensors,
    ),
}

/**
 * La vue Outils de l'Atelier : la liste, et l'outil ouvert.
 *
 * Un outil ouvert **remplace** la liste plutôt que de s'empiler dessus, comme un
 * devis ou une machine ailleurs dans l'application : une seule profondeur, un
 * simple `BackHandler`, et toujours pas de graphe de navigation.
 *
 * L'outil ouvert est tenu par le [OutilsViewModel] et non par un
 * `rememberSaveable` d'ici : passer à l'onglet des factures pour retrouver un
 * prix, puis revenir, refermait la réglette sur laquelle on avait réglé son
 * fluide. Un `ViewModel` survit au changement d'onglet, un état de composition
 * non.
 */
@Composable
fun OutilsRoute(
    /** La bascule de l'Atelier, posée sous le titre de la liste. */
    bascule: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OutilsViewModel = viewModel(factory = OutilsViewModel.Factory),
) {
    val verifies by viewModel.fluidesVerifies.collectAsStateWithLifecycle()
    val ouvert by viewModel.ouvert.collectAsStateWithLifecycle()

    BackHandler(enabled = ouvert != null) { viewModel.onFermer() }

    val outil = ouvert
    if (outil == null) {
        ListeOutils(onOuvrir = viewModel::onOuvrir, bascule = bascule, modifier = modifier)
        return
    }

    CadreOutil(outil = outil, onFermer = viewModel::onFermer, modifier = modifier) {
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
            Outil.SUBSTITUTION -> OutilSubstitution()
            Outil.CHAMP_MAGNETIQUE -> OutilChampMagnetique()
        }
    }
}

/** La liste des outils, un par carte, sous la bascule de l'Atelier. */
@Composable
private fun ListeOutils(
    onOuvrir: (Outil) -> Unit,
    bascule: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La coquille des carnets, sans bouton d'ajout : on n'ajoute pas d'outil.
    CadreCarnet(titre = VueAtelier.OUTILS.libelle, bascule = bascule, modifier = modifier) {
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
