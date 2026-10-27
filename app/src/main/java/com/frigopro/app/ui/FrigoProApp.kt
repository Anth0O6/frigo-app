package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Les cinq onglets de l'application.
 *
 * L'ordre est celui des questions qu'on se pose : **Aujourd'hui** d'abord —
 * « et maintenant ? » —, le **Planning** ensuite — « et la semaine ? », « et ce
 * que le contrat me doit ? » —, puis pour qui je travaille, ce que je réclame, et
 * avec quoi je travaille.
 *
 * La barre a été refaite après un audit, et trois défauts l'avaient décidé. La
 * Tournée portait **quatre pastilles** — Maintenant, Jour, Semaine, Préventif —
 * qui débordaient d'un écran de 320 points, si bien que « Préventif » était rogné
 * ou caché ; **Maintenant et Jour faisaient doublon**, la suite de la journée de
 * l'une étant la liste de l'autre ; et la **maintenance** était éclatée entre
 * trois onglets. Maintenant et Jour sont devenus « Aujourd'hui », Semaine et
 * Préventif le Planning, où les gammes ont rejoint les visites ; les Outils sont
 * une vue de l'Atelier, avec le magasin et les fournisseurs ; et les **Réglages**
 * ont quitté la barre pour une roue crantée en tête d'« Aujourd'hui » — on les
 * ouvre rarement, et l'on sait où les chercher.
 *
 * Aucun libellé n'est abrégé, aucun ne ment sur ce qu'il ouvre, et aucune
 * bascule ne porte plus de trois pastilles.
 */
enum class Onglet(val libelle: String, val icone: ImageVector) {
    AUJOURDHUI("Aujourd'hui", Icons.Filled.Today),
    PLANNING("Planning", Icons.Filled.DateRange),
    CLIENTS("Clients", Icons.Filled.Contacts),
    FACTURES("Factures", Icons.Filled.RequestQuote),
    ATELIER("Atelier", Icons.Filled.Build),
}

/**
 * Coquille de l'application : l'onglet affiché, la barre qui en change, les
 * Réglages par-dessus, et la feuille de saisie d'une intervention.
 *
 * Pas de graphe de navigation. Avec cinq onglets sans lien hiérarchique, une
 * variable d'état suffit, et `rememberSaveable` la fait survivre à une rotation
 * comme à la mise en arrière-plan. Chaque onglet n'a **qu'une profondeur** — un
 * objet ouvert remplace sa liste —, et les Réglages en plein écran n'en ajoutent
 * qu'une. La bibliothèque de navigation aura son intérêt le jour où il faudra une
 * vraie pile arrière ou des liens profonds.
 *
 * **C'est la coquille qui pose les marges des barres système**, et elle seule :
 * la barre d'onglets porte celle du bas, et le contenu reçoit ici celle du haut
 * et des côtés — et celle du bas quand les Réglages masquent la barre. Les écrans
 * qu'elle héberge passent donc `contentWindowInsets = WindowInsets(0, 0, 0, 0)` à
 * leur `Scaffold` — sans quoi la marge serait comptée deux fois.
 *
 * La marge du haut vient de `safeDrawing` et non des seules barres système,
 * parce qu'elle doit aussi écarter la **découpe d'écran** : sur un téléphone à
 * appareil photo perforé, celui-ci déborde de la barre d'état et masquerait le
 * titre de l'écran.
 */
@Composable
fun FrigoProApp(modifier: Modifier = Modifier) {
    var onglet by rememberSaveable { mutableStateOf(Onglet.AUJOURDHUI) }

    // Les vues des onglets à bascule sont tenues **ici** et non dans leurs
    // routes. Posées dans un `rememberSaveable` de la route, elles se perdaient
    // au premier changement d'onglet — l'onglet Factures revenait sur sa vue par
    // défaut après un détour par une intervention —, et elles auraient été hors
    // d'atteinte de l'accueil : « il manque trois articles » n'est une réponse à
    // « et maintenant ? » que si elle mène au magasin. Un onglet n'est pas un
    // `ViewModel`, et ces vues ne peuvent donc pas être partagées par
    // `viewModel()` comme les objets ouverts le sont.
    var planning by rememberSaveable { mutableStateOf(VuePlanning.SEMAINE) }
    var gammesOuvertes by rememberSaveable { mutableStateOf(false) }
    var facturation by rememberSaveable { mutableStateOf(VueFacturation.FACTURES) }
    var atelier by rememberSaveable { mutableStateOf(VueAtelier.MAGASIN) }

    // Les Réglages, en plein écran par-dessus l'onglet, et la page ouverte. La
    // page est tenue ici pour la même raison que les vues : « une ligne de
    // main-d'œuvre se chiffre à 0 € » n'est une réponse que si elle mène à la
    // page qui le corrige.
    var reglagesOuverts by rememberSaveable { mutableStateOf(false) }
    var pageReglages by rememberSaveable { mutableStateOf<PageReglages?>(null) }

    // Les ViewModels que la coquille touche pour ramener un onglet à son début.
    // `viewModel()` rend la même instance que celle des routes : refermer un
    // objet ici le referme là-bas. Les créer n'observe rien — leurs flux ne
    // coulent que collectés.
    val tournee: InterventionsViewModel = viewModel(factory = InterventionsViewModel.Factory)
    val detail: InterventionViewModel = viewModel(factory = InterventionViewModel.Factory)
    val plan: MaintenanceViewModel = viewModel(factory = MaintenanceViewModel.Factory)
    val machines: EquipementsViewModel = viewModel(factory = EquipementsViewModel.Factory)
    val carnet: ClientsViewModel = viewModel(factory = ClientsViewModel.Factory)
    val factures: FacturesViewModel = viewModel(factory = FacturesViewModel.Factory)
    val devis: DevisViewModel = viewModel(factory = DevisViewModel.Factory)
    val materiel: MaterielViewModel = viewModel(factory = MaterielViewModel.Factory)
    val outils: OutilsViewModel = viewModel(factory = OutilsViewModel.Factory)

    fun ouvrirReglages(page: PageReglages?) {
        pageReglages = page
        reglagesOuverts = true
    }

    /**
     * Toucher l'onglet déjà ouvert le ramène à son début : vue par défaut, objet
     * ouvert refermé. C'est le geste de qui s'est perdu, et il ne faisait rien.
     *
     * Ne sont refermés que les objets **dont rien n'est en cours de saisie** : une
     * intervention, une machine, une facture, un devis, un article écrivent à
     * chaque geste, et les refermer ne perd rien. La feuille de saisie d'une
     * intervention, la fiche d'un client et la saisie groupée des prix tiennent
     * en revanche une saisie qui n'est pas encore écrite — les refermer d'un
     * appui sur la barre l'abandonnerait en silence, ce que le projet refuse
     * partout ailleurs. Elles restent ouvertes.
     */
    fun revenirAuDebut(cible: Onglet) {
        when (cible) {
            Onglet.AUJOURDHUI -> {
                detail.onFermer()
                tournee.onRevenirAAujourdhui()
            }

            Onglet.PLANNING -> {
                planning = VuePlanning.SEMAINE
                gammesOuvertes = false
                plan.onFermerGamme()
            }

            Onglet.CLIENTS -> {
                machines.onRevenirAuCarnet()
                carnet.onFermerClient()
            }

            Onglet.FACTURES -> {
                facturation = VueFacturation.FACTURES
                factures.onFermer()
                devis.onFermer()
            }

            Onglet.ATELIER -> {
                atelier = VueAtelier.MAGASIN
                materiel.onFermerArticle()
                materiel.onFermerFournisseur()
                outils.onFermer()
            }
        }
    }

    // À la racine d'un onglet autre qu'« Aujourd'hui », le retour système **ramène
    // à « Aujourd'hui »** au lieu de fermer l'application : on ouvre un autre
    // onglet pour un geste, et le retour qui suit veut revenir à sa journée, pas
    // quitter. Sur « Aujourd'hui », il ferme comme avant — c'est la porte de
    // sortie.
    //
    // Il est posé **avant** les routes, et c'est ce qui le tient à sa place : le
    // dispatcher donne la main au rappel ajouté en dernier, si bien que celui-ci,
    // composé le premier et une seule fois, passe toujours après ceux des écrans
    // — machine ouverte, gammes, sélection, outil, facture. Il ne court-circuite
    // jamais une profondeur à défaire, et ne joue qu'une fois toutes défaites.
    BackHandler(enabled = !reglagesOuverts && onglet != Onglet.AUJOURDHUI) {
        onglet = Onglet.AUJOURDHUI
    }

    // Les Réglages se referment au retour, une fois leur page refermée : celle-ci
    // a son propre rappel, posé par `ReglagesScreen` et donc plus récent. Même
    // raison de placement que le précédent.
    BackHandler(enabled = reglagesOuverts) { reglagesOuverts = false }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (reglagesOuverts) {
                            // Sans barre d'onglets, la marge du bas revient au
                            // contenu ; `safeDrawing` y compte aussi le clavier,
                            // que les champs des Réglages font monter.
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal +
                                WindowInsetsSides.Bottom
                        } else {
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        },
                    ),
                ),
        ) {
            // Les Réglages **remplacent** l'onglet plutôt que de se poser dessus :
            // l'onglet recouvert garderait ses rappels de retour, et le premier
            // retour refermerait, sous les Réglages, une intervention qu'on ne
            // voit pas. Rien n'est perdu à les retirer de la composition — les
            // objets ouverts sont aux ViewModels et les vues à la coquille.
            if (reglagesOuverts) {
                ReglagesRoute(
                    page = pageReglages,
                    onPage = { pageReglages = it },
                    onFermer = { reglagesOuverts = false },
                )
            } else {
                when (onglet) {
                    Onglet.AUJOURDHUI -> AujourdhuiRoute(
                        onAllerAuxDevis = {
                            facturation = VueFacturation.DEVIS
                            onglet = Onglet.FACTURES
                        },
                        onAllerAuxFactures = {
                            facturation = VueFacturation.FACTURES
                            onglet = Onglet.FACTURES
                        },
                        onVoirMagasin = {
                            atelier = VueAtelier.MAGASIN
                            onglet = Onglet.ATELIER
                        },
                        onVoirEntretien = {
                            planning = VuePlanning.ENTRETIEN
                            gammesOuvertes = false
                            onglet = Onglet.PLANNING
                        },
                        onVoirClients = { onglet = Onglet.CLIENTS },
                        onReglages = { page -> ouvrirReglages(page) },
                    )

                    Onglet.PLANNING -> PlanningRoute(
                        vue = planning,
                        onVue = { planning = it },
                        gammesOuvertes = gammesOuvertes,
                        onGammes = { gammesOuvertes = it },
                        onAllerAAujourdhui = { onglet = Onglet.AUJOURDHUI },
                    )

                    // La page d'un client ouvre ses objets dans leur onglet :
                    // le ViewModel partagé reçoit l'objet, la coquille change
                    // d'onglet — le chemin de l'accueil vers une facture à
                    // relancer et du Planning vers une intervention.
                    Onglet.CLIENTS -> ClientsRoute(
                        onOuvrirIntervention = { intervention ->
                            detail.onOuvrir(intervention)
                            onglet = Onglet.AUJOURDHUI
                        },
                        onOuvrirFacture = { facture ->
                            factures.onOuvrir(facture)
                            facturation = VueFacturation.FACTURES
                            onglet = Onglet.FACTURES
                        },
                        onOuvrirDevis = { document ->
                            devis.onOuvrir(document)
                            facturation = VueFacturation.DEVIS
                            onglet = Onglet.FACTURES
                        },
                    )

                    Onglet.FACTURES -> FacturationRoute(
                        vue = facturation,
                        onVue = { facturation = it },
                        // « Planifier » un devis accepté : la feuille de saisie
                        // est déjà ouverte par la vue Devis, et c'est devant la
                        // journée que la date se choisit.
                        onAllerALaTournee = { onglet = Onglet.AUJOURDHUI },
                    )

                    Onglet.ATELIER -> AtelierRoute(
                        vue = atelier,
                        onVue = { atelier = it },
                        onReglage = { page -> ouvrirReglages(page) },
                    )
                }
            }
        }
        if (!reglagesOuverts) {
            NavigationBar {
                Onglet.entries.forEach { cible ->
                    NavigationBarItem(
                        selected = onglet == cible,
                        onClick = {
                            if (onglet == cible) revenirAuDebut(cible) else onglet = cible
                        },
                        icon = { Icon(imageVector = cible.icone, contentDescription = null) },
                        // `maxLines` explicite : la place d'un libellé se compte
                        // à cinq, et un retour à la ligne décalerait toute la
                        // barre.
                        label = { Text(text = cible.libelle, maxLines = 1) },
                    )
                }
            }
        }
    }

    // La feuille de saisie d'une intervention, posée **une fois** au-dessus de
    // tout : « Aujourd'hui » l'ouvre, le Planning y corrige un créneau, la vue
    // Devis y planifie un devis accepté. Une seule instance du formulaire, une
    // seule feuille — voir [FeuilleFormulaireIntervention].
    FeuilleFormulaireIntervention(tournee)
}
