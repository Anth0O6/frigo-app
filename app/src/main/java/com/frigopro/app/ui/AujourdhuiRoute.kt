package com.frigopro.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * L'écran d'accueil, avec son état.
 *
 * Il partage le `InterventionViewModel` des autres onglets — `viewModel()` rend
 * la même instance pour une même classe — si bien qu'une intervention ouverte
 * depuis l'accueil est la même que celle ouverte depuis le planning, avec son
 * chronomètre en marche et ses relevés déjà saisis. C'est aussi ce qui évite un
 * détour par l'onglet Planning pour commencer sa journée.
 */
@Composable
fun AujourdhuiRoute(
    onVoirPlanning: () -> Unit,
    onVoirDevis: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AujourdhuiViewModel = viewModel(factory = AujourdhuiViewModel.Factory),
    detail: InterventionViewModel = viewModel(factory = InterventionViewModel.Factory),
    devis: DevisViewModel = viewModel(factory = DevisViewModel.Factory),
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    val ouverte by detail.ouverte.collectAsStateWithLifecycle()
    val contexte = LocalContext.current

    if (ouverte != null) {
        InterventionRoute(
            viewModel = detail,
            onCreerDevis = {
                devis.onNouveau(detail.etat.value?.client)
                onVoirDevis()
            },
            modifier = modifier,
        )
        return
    }

    EcranAujourdhui(
        etat = etat,
        onOuvrir = detail::onOuvrir,
        onItineraire = { client -> contexte.ouvrirItineraire(client.adresseComplete) },
        onVoirPlanning = onVoirPlanning,
        onVoirDevis = onVoirDevis,
        modifier = modifier,
    )
}
