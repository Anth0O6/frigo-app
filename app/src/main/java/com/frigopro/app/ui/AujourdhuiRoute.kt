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
    // Celui du planning, et c'est voulu : c'est lui qui tient la saisie en cours,
    // si bien qu'un formulaire ouvert ici est le même que celui de l'onglet
    // Planning — un onglet changé en pleine saisie ne perd donc rien.
    tournee: InterventionsViewModel = viewModel(factory = InterventionsViewModel.Factory),
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    val ouverte by detail.ouverte.collectAsStateWithLifecycle()
    val contexte = LocalContext.current

    // Un `if`/`else` plutôt qu'un retour anticipé : la feuille de saisie se pose
    // **après** ce bloc et doit pouvoir recouvrir les deux écrans, sans quoi
    // corriger une heure depuis une intervention ouverte ici n'affichait rien.
    if (ouverte != null) {
        InterventionRoute(
            viewModel = detail,
            onCreerDevis = {
                devis.onNouveau(detail.etat.value?.client)
                onVoirDevis()
            },
            onModifierFiche = {
                detail.etat.value?.intervention?.let(tournee::onModifierIntervention)
            },
            modifier = modifier,
        )
    } else {
        EcranAujourdhui(
            etat = etat,
            onOuvrir = detail::onOuvrir,
            onItineraire = { client -> contexte.ouvrirItineraire(client.adresseComplete) },
            onVoirPlanning = onVoirPlanning,
            onVoirDevis = onVoirDevis,
            modifier = modifier,
        )
    }

    FeuilleFormulaireIntervention(tournee)
}
