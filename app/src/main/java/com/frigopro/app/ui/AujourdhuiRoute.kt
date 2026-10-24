package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.EcheanceMaintenance
import com.frigopro.app.data.Equipement

/**
 * L'onglet « Aujourd'hui », avec son état : la journée, et l'intervention
 * ouverte, qui la remplace.
 *
 * Il réunit ce qui était trois morceaux de la Tournée — la route qui hébergeait
 * l'intervention ouverte, l'accueil « Maintenant » et la vue « Jour » —, et
 * c'est le même geste que celui qui avait réuni l'accueil et le planning : deux
 * entrées pour les mêmes interventions finissent toujours par ne pas montrer la
 * même chose. Une intervention ouverte **remplace** la journée plutôt que de
 * s'empiler dessus : une seule profondeur, et toujours pas de graphe de
 * navigation.
 *
 * Tous les ViewModels sont partagés — `viewModel()` rend la même instance par
 * classe —, et c'est ce qui fait marcher chaque passage vers un autre onglet
 * sans rien transporter : une facture ouverte ici est celle que l'onglet
 * Factures montre, un article ouvert celui du magasin, une machine ouverte celle
 * du carnet des clients. La coquille n'a qu'à changer d'onglet.
 *
 * La **feuille de saisie** n'est pas posée ici mais par la coquille, une seule
 * fois au-dessus de tous les onglets : le Planning et la vue Devis l'ouvrent
 * aussi, et c'est le même formulaire.
 */
@Composable
fun AujourdhuiRoute(
    /** Chiffrer depuis une intervention : la vue Devis est un autre onglet. */
    onAllerAuxDevis: () -> Unit,
    /**
     * Mener à la vue des factures : depuis « Facturer » sur une intervention,
     * depuis une relance d'« À traiter ». Distinct de [onAllerAuxDevis] :
     * facturer ne doit créer aucun devis.
     */
    onAllerAuxFactures: () -> Unit,
    /** Le magasin de l'Atelier : un article à racheter, ou la liste entière. */
    onVoirMagasin: () -> Unit,
    /** L'Entretien du Planning, quand les visites dues débordent de l'accueil. */
    onVoirEntretien: () -> Unit,
    /** Le carnet des clients, sur la machine qu'une échéance F-Gas désigne. */
    onVoirClients: () -> Unit,
    /** Les Réglages en plein écran, sur une page ou sur le sommaire (`null`). */
    onReglages: (PageReglages?) -> Unit,
    modifier: Modifier = Modifier,
    tournee: InterventionsViewModel = viewModel(factory = InterventionsViewModel.Factory),
    accueil: AujourdhuiViewModel = viewModel(factory = AujourdhuiViewModel.Factory),
    detail: InterventionViewModel = viewModel(factory = InterventionViewModel.Factory),
    devis: DevisViewModel = viewModel(factory = DevisViewModel.Factory),
    factures: FacturesViewModel = viewModel(factory = FacturesViewModel.Factory),
    materiel: MaterielViewModel = viewModel(factory = MaterielViewModel.Factory),
    /**
     * Le plan de maintenance, partagé avec l'Entretien du Planning et la fiche
     * d'une machine : une visite consignée d'ici disparaît de là-bas, et
     * inversement, sans que rien ne les relie.
     */
    plan: MaintenanceViewModel = viewModel(factory = MaintenanceViewModel.Factory),
    machines: EquipementsViewModel = viewModel(factory = EquipementsViewModel.Factory),
) {
    val ouverte by detail.ouverte.collectAsStateWithLifecycle()
    val jour by tournee.jour.collectAsStateWithLifecycle()
    val lignes by tournee.lignes.collectAsStateWithLifecycle()
    val frise by tournee.frise.collectAsStateWithLifecycle()
    val recherche by tournee.recherche.collectAsStateWithLifecycle()
    val rechercheOuverte by tournee.rechercheOuverte.collectAsStateWithLifecycle()
    val trouvees by tournee.trouvees.collectAsStateWithLifecycle()
    val etat by accueil.etat.collectAsStateWithLifecycle()
    val impayees by factures.aRelancer.collectAsStateWithLifecycle()
    val manquants by materiel.aReapprovisionner.collectAsStateWithLifecycle()
    val visitesDues by plan.aFaire.collectAsStateWithLifecycle()
    val pointsGamme by plan.points.collectAsStateWithLifecycle()
    val contexte = LocalContext.current

    // La visite ouverte vit ici et non dans le ViewModel : c'est une feuille de
    // saisie, pas un état du plan — même motif que le sélecteur de date.
    var visite by remember { mutableStateOf<EcheanceMaintenance?>(null) }

    // Le retour système referme la recherche avant tout le reste : c'est la seule
    // profondeur que la journée ajoute. L'intervention ouverte a son propre
    // `BackHandler`, posé par `InterventionRoute`.
    BackHandler(enabled = ouverte == null && rechercheOuverte) { tournee.onFermerRecherche() }

    if (ouverte != null) {
        InterventionRoute(
            viewModel = detail,
            onCreerDevis = {
                // La machine de l'intervention suit dans le devis : c'est elle qui
                // fait proposer le nombre d'unités d'une prestation comptée par
                // unité. `etat` est collecté par `InterventionRoute`, qui est
                // affichée : `.value` y est donc à jour.
                val courant = detail.etat.value
                devis.onNouveau(courant?.client, courant?.intervention?.equipementId)
                onAllerAuxDevis()
            },
            onAllerAuxFactures = onAllerAuxFactures,
            onModifierFiche = {
                detail.etat.value?.intervention?.let(tournee::onModifierIntervention)
            },
            modifier = modifier,
        )
    } else {
        EcranAujourdhui(
            etat = etat,
            jour = jour,
            lignes = lignes,
            onJourPrecedent = tournee::onJourPrecedent,
            onJourSuivant = tournee::onJourSuivant,
            onJourChoisi = tournee::onJourChoisi,
            onNouvelleIntervention = tournee::onNouvelleIntervention,
            onOuvrirIntervention = detail::onOuvrir,
            onModifierIntervention = tournee::onModifierIntervention,
            // Un seul chemin par transition : voir [GesteStatut].
            onChangerStatut = { intervention ->
                when (gesteStatut(intervention.statut)) {
                    GesteStatut.DEMARRER -> tournee.onDemarrer(intervention)
                    GesteStatut.CLOTURER, GesteStatut.VOIR_RAPPORT ->
                        detail.onOuvrirSur(intervention, OngletIntervention.RAPPORT)
                }
            },
            onDemarrer = detail::onDemarrer,
            onItineraire = { client -> contexte.ouvrirItineraire(client.adresseComplete) },
            frise = frise,
            onBasculerVue = tournee::onBasculerVue,
            onOuvrirReglages = { onReglages(null) },
            recherche = RechercheJournee(
                ouverte = rechercheOuverte,
                saisie = recherche,
                trouvees = trouvees,
            ),
            actionsRecherche = ActionsRecherche(
                onOuvrir = tournee::onOuvrirRecherche,
                onSaisie = tournee::onRecherche,
                onFermer = tournee::onFermerRecherche,
            ),
            aTraiter = ATraiter(
                impayees = impayees,
                visites = visitesDues,
                manquants = manquants,
                echeances = etat.echeances,
                reglages = etat.reglagesManquants,
            ),
            actionsATraiter = ActionsATraiter(
                // Ouvrir l'objet dans son ViewModel partagé, puis changer
                // d'onglet : l'onglet d'arrivée montre ce qui est ouvert.
                onOuvrirFacture = { facture ->
                    factures.onOuvrir(facture)
                    onAllerAuxFactures()
                },
                onVoirFactures = onAllerAuxFactures,
                // La feuille de visite se pose ici même : consigner une visite
                // ne demande pas de quitter l'accueil.
                onOuvrirVisite = { visite = it },
                onVoirEntretien = onVoirEntretien,
                onOuvrirArticle = { entree ->
                    materiel.onOuvrirArticle(entree.article.id)
                    onVoirMagasin()
                },
                onVoirMagasin = onVoirMagasin,
                onOuvrirMachine = { machine: Equipement ->
                    machines.onOuvrir(machine)
                    onVoirClients()
                },
                onReglage = { page -> onReglages(page) },
            ),
            modifier = modifier,
        )
    }

    visite?.let { echeance ->
        FeuilleVisite(
            echeance = echeance,
            points = pointsGamme.filter { it.gammeId == echeance.gamme.id }
                .sortedBy { it.rang },
            onConsigner = { faitLe, notes ->
                plan.onConsigner(echeance.equipement, echeance.gamme, faitLe, notes)
                visite = null
            },
            onFermer = { visite = null },
        )
    }
}
