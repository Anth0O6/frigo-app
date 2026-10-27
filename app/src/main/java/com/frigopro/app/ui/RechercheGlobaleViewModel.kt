package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.FactureRepository
import com.frigopro.app.data.MaterielRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Ce que la recherche d'« Aujourd'hui » trouve **hors des interventions** :
 * clients, machines, factures, devis, articles.
 *
 * Un seul champ pour tout, et c'est celui qu'on avait déjà : la loupe de
 * l'accueil cherchait les interventions de toutes les dates, et chercher un
 * client ou une facture demandait de changer d'onglet pour retrouver un autre
 * champ. On cherche une chose qu'on a en tête, pas un onglet.
 *
 * Chaque famille est **plafonnée** à [LIMITE] et son compte complet est dit :
 * la recherche mène à l'objet, l'onglet tient la liste. Les filtres sont ceux de
 * [Recherche], la seule recherche du projet, si bien que « pean » trouve
 * « Péan » ici comme partout.
 */
data class ResultatsGlobaux(
    val clients: List<Client> = emptyList(),
    val machines: List<MachineTrouvee> = emptyList(),
    val factures: List<FactureChiffree> = emptyList(),
    val devis: List<DevisChiffre> = emptyList(),
    val articles: List<ArticleEnStock> = emptyList(),
    /** Ce qui correspondait avant le plafond, famille par famille. */
    val totaux: Map<FamilleTrouvee, Int> = emptyMap(),
) {
    val vide: Boolean
        get() = clients.isEmpty() && machines.isEmpty() && factures.isEmpty() &&
            devis.isEmpty() && articles.isEmpty()

    companion object {
        const val LIMITE = 5
    }
}

/** Une machine trouvée, avec le nom de son client : « Groupe toit » seul ne dit pas où. */
data class MachineTrouvee(val equipement: Equipement, val clientNom: String)

enum class FamilleTrouvee(val libelle: String) {
    CLIENTS("Clients"),
    MACHINES("Machines"),
    FACTURES("Factures"),
    DEVIS("Devis"),
    ARTICLES("Articles"),
}

/**
 * Le tri des résultats, fonction pure pour s'éprouver sans `ViewModel`.
 *
 * Une machine se trouve aussi **par le nom de son client** — « carrefour
 * toiture » —, parce qu'on désigne une machine par l'endroit où elle est.
 * L'ordre de chaque famille est celui que son dépôt donne déjà — le carnet et
 * le parc triés à la française —, si bien qu'aucune n'est triée deux fois.
 */
internal fun chercherPartout(
    recherche: String,
    clients: List<Client>,
    machines: List<Equipement>,
    factures: List<FactureChiffree>,
    devis: List<DevisChiffre>,
    articles: List<ArticleEnStock>,
): ResultatsGlobaux {
    if (Recherche.decouper(recherche).isEmpty()) return ResultatsGlobaux()
    val nomDe = clients.associate { it.id to it.nom }

    val clientsTrouves = clients.filter {
        Recherche.correspond(recherche, it.nom, it.ville, it.adresse, it.telephone)
    }
    val machinesTrouvees = machines
        .map { MachineTrouvee(it, nomDe[it.clientId].orEmpty()) }
        .filter {
            Recherche.correspond(
                recherche,
                it.equipement.nom,
                it.equipement.designation,
                it.equipement.zone,
                it.clientNom,
            )
        }
    val facturesTrouvees = factures.filter { it.correspondA(recherche) }
    val devisTrouves = devis.filter { it.correspondA(recherche) }
    val articlesTrouves = articles.filter {
        Recherche.correspond(recherche, it.article.designation, it.article.reference, it.article.fournisseurNom)
    }

    val limite = ResultatsGlobaux.LIMITE
    return ResultatsGlobaux(
        clients = clientsTrouves.take(limite),
        machines = machinesTrouvees.take(limite),
        factures = facturesTrouvees.take(limite),
        devis = devisTrouves.take(limite),
        articles = articlesTrouves.take(limite),
        totaux = mapOf(
            FamilleTrouvee.CLIENTS to clientsTrouves.size,
            FamilleTrouvee.MACHINES to machinesTrouvees.size,
            FamilleTrouvee.FACTURES to facturesTrouvees.size,
            FamilleTrouvee.DEVIS to devisTrouves.size,
            FamilleTrouvee.ARTICLES to articlesTrouves.size,
        ),
    )
}

/**
 * Le `ViewModel` de la recherche globale.
 *
 * Ses cinq flux **ne coulent que tant qu'il y a quelque chose à chercher** :
 * `flatMapLatest` sur la saisie, comme la recherche des interventions. Sans
 * cela, l'onglet le plus ouvert de l'application observerait cinq tables en
 * permanence pour n'afficher qu'une journée.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RechercheGlobaleViewModel(
    clients: ClientRepository,
    equipements: EquipementRepository,
    factures: FactureRepository,
    devis: DevisRepository,
    materiel: MaterielRepository,
) : ViewModel() {

    private val _saisie = MutableStateFlow("")

    val resultats: StateFlow<ResultatsGlobaux> = _saisie
        .flatMapLatest { saisie ->
            if (Recherche.decouper(saisie).isEmpty()) {
                flowOf(ResultatsGlobaux())
            } else {
                combine(
                    clients.clients,
                    equipements.equipements,
                    factures.facturesChiffrees,
                    devis.devisChiffres,
                    materiel.magasin,
                ) { carnet, parc, documents, chiffres, magasin ->
                    chercherPartout(saisie, carnet, parc, documents, chiffres, magasin)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), ResultatsGlobaux())

    /** La saisie vient du champ d'« Aujourd'hui », que [InterventionsViewModel] détient. */
    fun onRecherche(saisie: String) {
        _saisie.value = saisie
    }

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                RechercheGlobaleViewModel(
                    conteneur.clients,
                    conteneur.equipements,
                    conteneur.factures,
                    conteneur.devis,
                    conteneur.materiel,
                )
            }
        }
    }
}
