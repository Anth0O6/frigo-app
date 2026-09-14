package com.frigopro.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * L'accueil, avec son état : la première des trois vues de la tournée.
 *
 * Il ne porte plus que l'accueil. L'intervention ouverte et la feuille de saisie
 * sont tenues par [TourneeRoute], qui l'héberge — elles y étaient recopiées à
 * l'identique du temps où l'accueil était un onglet à lui seul, et deux copies
 * du même branchement finissent par ne plus se comporter pareil selon l'onglet
 * d'où l'on vient.
 *
 * Il partage l'`InterventionViewModel` et l'`InterventionsViewModel` des autres
 * vues — `viewModel()` rend la même instance par classe — si bien qu'une
 * intervention ouverte d'ici est la même que celle ouverte du planning, avec son
 * chronomètre en marche et ses relevés déjà saisis.
 */
@Composable
fun AujourdhuiRoute(
    vue: VueTournee,
    onVue: (VueTournee) -> Unit,
    onVoirDevis: () -> Unit,
    onVoirMagasin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AujourdhuiViewModel = viewModel(factory = AujourdhuiViewModel.Factory),
    detail: InterventionViewModel = viewModel(factory = InterventionViewModel.Factory),
    factures: FacturesViewModel = viewModel(factory = FacturesViewModel.Factory),
    materiel: MaterielViewModel = viewModel(factory = MaterielViewModel.Factory),
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    val impayees by factures.aRelancer.collectAsStateWithLifecycle()
    val manquants by materiel.aReapprovisionner.collectAsStateWithLifecycle()
    val contexte = LocalContext.current

    EcranAujourdhui(
        etat = etat,
        vue = vue,
        onVue = onVue,
        onOuvrir = detail::onOuvrir,
        onItineraire = { client -> contexte.ouvrirItineraire(client.adresseComplete) },
        onVoirDevis = onVoirDevis,
        modifier = modifier,
        impayees = impayees,
        // Ouvrir la facture, puis basculer : l'onglet montre celle qui est
        // ouverte, et le ViewModel des factures est partagé.
        onOuvrirFacture = { facture ->
            factures.onOuvrir(facture)
            onVoirDevis()
        },
        manquants = manquants,
        onVoirMagasin = onVoirMagasin,
    )
}
