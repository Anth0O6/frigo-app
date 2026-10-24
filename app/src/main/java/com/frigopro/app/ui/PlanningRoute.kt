package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.EcheanceMaintenance
import com.frigopro.app.data.Intervention
import com.frigopro.app.ui.composants.RangeePastilles
import java.time.LocalDate

/**
 * Les deux vues du Planning : le travail **daté** et le travail **dû**.
 *
 * La semaine montre des interventions posées sur une date — « est-ce que je peux
 * caser un dépannage jeudi ? » —, l'entretien ce que le calendrier doit et qui
 * n'a pas encore d'heure — « qu'est-ce que le contrat me réclame ? ». Elles
 * étaient deux des quatre pastilles de la Tournée, avec « Maintenant » et
 * « Jour », et la quatrième sortait de l'écran sur un téléphone de 320 points :
 * « Préventif » était rogné, ou caché. La journée est devenue l'onglet
 * « Aujourd'hui », et ces deux-là, qui regardent plus loin qu'aujourd'hui, ont
 * leur onglet.
 *
 * La semaine est **la vue par défaut** : on prépare sa semaine plus souvent
 * qu'on ne relit le plan entier, et une ronde du jour se rappelle d'elle-même
 * sous « À traiter ».
 */
enum class VuePlanning(val libelle: String) {
    SEMAINE("Semaine"),
    ENTRETIEN("Entretien"),
}

/**
 * La bascule des deux vues du Planning.
 *
 * Elle passe par [RangeePastilles], comme celle des Factures et de l'Atelier : le
 * projet nomme son vocabulaire visuel une fois pour cette raison précise — deux
 * bascules qui se ressemblent sans être la même divergent au premier ajustement,
 * et l'utilisateur apprend alors deux gestes pour une seule idée.
 */
@Composable
fun BasculePlanning(
    vue: VuePlanning,
    onVue: (VuePlanning) -> Unit,
    modifier: Modifier = Modifier,
) {
    RangeePastilles(
        options = VuePlanning.entries,
        retenue = vue,
        libelle = { it.libelle },
        onChoisir = onVue,
        modifier = modifier,
    )
}

/**
 * L'onglet Planning, avec son état.
 *
 * La vue ouverte et l'écran des gammes sont tenus par la **coquille** et non ici,
 * comme la vue des Factures et celle de l'Atelier : posés dans cette route, ils se
 * perdaient au changement d'onglet, et l'état vide d'« Aujourd'hui » ne pourrait
 * pas désigner l'entretien.
 *
 * Une intervention ouverte depuis la semaine s'ouvre **dans « Aujourd'hui »**,
 * qui est le seul onglet à héberger une intervention : deux onglets capables de
 * la montrer auraient affiché la même fiche à deux endroits, et le retour
 * n'aurait pas su où ramener. On revient alors sur la journée de cette
 * intervention, que la semaine a retenue. La feuille de saisie, elle, se pose
 * par-dessus la semaine : elle est à la coquille.
 */
@Composable
fun PlanningRoute(
    vue: VuePlanning,
    onVue: (VuePlanning) -> Unit,
    /** L'écran des gammes remplace l'entretien : voir [EcranGammes]. */
    gammesOuvertes: Boolean,
    onGammes: (Boolean) -> Unit,
    /** Ouvrir une journée, ou une intervention : c'est l'onglet « Aujourd'hui ». */
    onAllerAAujourdhui: () -> Unit,
    modifier: Modifier = Modifier,
    tournee: InterventionsViewModel = viewModel(factory = InterventionsViewModel.Factory),
    detail: InterventionViewModel = viewModel(factory = InterventionViewModel.Factory),
    /**
     * Le plan de maintenance, partagé avec la fiche d'une machine et l'accueil :
     * `viewModel()` rend une seule instance par classe, si bien qu'une gamme
     * créée ici est déjà celle que la fiche propose, et qu'une visite consignée
     * depuis l'accueil disparaît d'ici.
     */
    plan: MaintenanceViewModel = viewModel(factory = MaintenanceViewModel.Factory),
) {
    // La visite ouverte vit ici et non dans le ViewModel : c'est une feuille de
    // saisie, pas un état du plan — même motif que le sélecteur de date.
    var visite by remember { mutableStateOf<EcheanceMaintenance?>(null) }

    // L'écran des gammes est la seule profondeur de l'onglet.
    BackHandler(enabled = vue == VuePlanning.ENTRETIEN && gammesOuvertes) { onGammes(false) }

    when (vue) {
        VuePlanning.SEMAINE -> {
            val jour by tournee.jour.collectAsStateWithLifecycle()
            val semaine by tournee.semaine.collectAsStateWithLifecycle()
            EcranSemaine(
                vue = vue,
                onVue = onVue,
                lundi = lundiDe(jour),
                jourRetenu = jour,
                interventions = semaine,
                // Toucher un jour de la semaine ouvre ce jour-là : c'est le geste
                // qu'on fait pour aller voir, et « Aujourd'hui » est l'écran qui
                // montre une journée — avec sa frise, pour voir les trous.
                onJourRetenu = { date: LocalDate ->
                    tournee.onJourChoisi(date)
                    onAllerAAujourdhui()
                },
                onSemainePrecedente = tournee::onSemainePrecedente,
                onSemaineSuivante = tournee::onSemaineSuivante,
                onOuvrir = { intervention: Intervention ->
                    detail.onOuvrir(intervention)
                    onAllerAAujourdhui()
                },
                onModifier = tournee::onModifierIntervention,
                modifier = modifier,
            )
        }

        VuePlanning.ENTRETIEN -> if (gammesOuvertes) {
            val gammes by plan.gammes.collectAsStateWithLifecycle()
            val points by plan.points.collectAsStateWithLifecycle()
            val affectations by plan.affectations.collectAsStateWithLifecycle()
            val realisations by plan.realisations.collectAsStateWithLifecycle()
            val gammeOuverte by plan.gammeOuverte.collectAsStateWithLifecycle()
            EcranGammes(
                gammes = gammes,
                points = points,
                // Combien de machines suivent chaque gamme : dérivé des
                // affectations, jamais stocké — un compteur en base serait faux
                // au premier rattachement fait depuis le carnet.
                machinesParGamme = affectations.groupingBy { it.gammeId }.eachCount(),
                realisationParGamme = realisations,
                gammeOuverte = gammeOuverte,
                actions = ActionsGammes(
                    onOuvrir = plan::onOuvrirGamme,
                    onFermer = plan::onFermerGamme,
                    onCreer = plan::onCreerGamme,
                    onRenommer = plan::onRenommerGamme,
                    onPeriodicite = plan::onPeriodicite,
                    onSupprimer = plan::onSupprimerGamme,
                    onAjouterPoint = plan::onAjouterPoint,
                    onSupprimerPoint = plan::onSupprimerPoint,
                ),
                onFermer = { onGammes(false) },
                modifier = modifier,
            )
        } else {
            val echeances by plan.echeances.collectAsStateWithLifecycle()
            EcranPreventif(
                echeances = echeances,
                vue = vue,
                onVue = onVue,
                onOuvrir = { visite = it },
                onVoirGammes = { onGammes(true) },
                onNouvelleGamme = {
                    onGammes(true)
                    plan.onOuvrirGamme(NOUVELLE_GAMME)
                },
                modifier = modifier,
            )
        }
    }

    visite?.let { echeance ->
        val points by plan.points.collectAsStateWithLifecycle()
        FeuilleVisite(
            echeance = echeance,
            points = points.filter { it.gammeId == echeance.gamme.id }.sortedBy { it.rang },
            onConsigner = { faitLe, notes ->
                plan.onConsigner(echeance.equipement, echeance.gamme, faitLe, notes)
                visite = null
            },
            onFermer = { visite = null },
        )
    }
}
