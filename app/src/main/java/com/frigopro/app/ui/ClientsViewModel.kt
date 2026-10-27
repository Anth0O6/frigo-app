package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.FactureRepository
import com.frigopro.app.data.GroupeClients
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.StatutDevis
import com.frigopro.app.data.StatutFacture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Tout ce que la page d'un client réunit, filtré sur **ce seul client**.
 *
 * Ses sites n'y remontent pas : chacun a sa propre page, ouverte depuis la
 * section des sites. Les mêler aurait montré deux fois la même intervention —
 * sur le site et sur son enseigne — et fait lire au donneur d'ordre une dette
 * qu'un site règle lui-même. Le donneur d'ordre y retrouve en revanche les
 * interventions **qu'on lui facture** (`clientFactureId`), parce que c'est à
 * lui qu'on en parle.
 */
data class DossierClient(
    val client: Client,
    /** L'enseigne dont il est un site, `null` pour un donneur d'ordre. */
    val donneurDOrdre: Client? = null,
    /** Ses sites, s'il est un donneur d'ordre ; toujours vide sur un site. */
    val sites: List<Client> = emptyList(),
    /** Pas encore close, aujourd'hui ou plus tard : la plus proche d'abord. */
    val aVenir: List<Intervention> = emptyList(),
    /** Close, ou d'un jour passé : la plus récente d'abord. */
    val passees: List<Intervention> = emptyList(),
    /** Émises et non soldées, l'échéance la plus ancienne d'abord. */
    val dues: List<FactureChiffree> = emptyList(),
    /** Ni refusés ni facturés : ce qui attend encore une réponse ou une facture. */
    val devisEnCours: List<DevisChiffre> = emptyList(),
    /** Le jour du calcul, pour dire le retard sans relire l'horloge. */
    val aujourdhui: LocalDate = LocalDate.now(),
)

/**
 * Détient l'état de l'onglet « Clients ».
 *
 * Le carnet se remplit seul depuis les interventions ; cet écran sert à le
 * consulter et à compléter ce que la saisie d'une intervention ne demande pas,
 * l'adresse et le téléphone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientsViewModel(
    private val repository: ClientRepository,
    private val interventions: InterventionRepository,
    private val factures: FactureRepository,
    private val devis: DevisRepository,
    /** Lue à l'ouverture d'une page, et passée en paramètre pour les tests. */
    private val horloge: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val clients: StateFlow<List<Client>> = repository.clients
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    /**
     * Le carnet en groupes : chaque donneur d'ordre, et ses sites repliés
     * dessous. C'est ce que la liste affiche ; [clients] reste la liste plate,
     * dont le formulaire d'intervention a besoin pour proposer *tous* les
     * interlocuteurs, sites compris.
     */
    val groupes: StateFlow<List<GroupeClients>> = repository.groupes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    // — La lecture du carnet ————————————————————————————————————————————

    private val _recherche = MutableStateFlow("")

    /**
     * Ce qu'on cherche dans le carnet.
     *
     * Tenue ici et non dans l'écran, sur le modèle de [MaterielViewModel.recherche]
     * : l'écran du carnet **quitte la composition** quand une machine s'ouvre, et
     * un `rememberSaveable` posé dedans repartait vide au retour. Contrôler cinq
     * machines d'un site obligeait alors à retaper cinq fois sa recherche et à
     * redéplier cinq fois sa carte, sur un carnet de trois cents lignes.
     */
    val recherche: StateFlow<String> = _recherche.asStateFlow()

    private val _deplies = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Les clients dont le parc est déplié, par identifiant — même raison que
     * [recherche]. L'identifiant et non la position : la liste recycle ses cartes,
     * et l'état déplié ne doit pas sauter d'un client à l'autre au défilement.
     */
    val deplies: StateFlow<Set<String>> = _deplies.asStateFlow()

    private val _parcsComplets = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Les clients dont on a demandé **tout le parc** alors que la recherche n'en
     * retenait que quelques machines. Vidé à chaque nouvelle recherche : « voir
     * tout » répondait à celle d'avant, et le garder ferait croire que la
     * suivante ne filtre plus rien.
     */
    val parcsComplets: StateFlow<Set<String>> = _parcsComplets.asStateFlow()

    fun onRecherche(saisie: String) {
        if (saisie != _recherche.value) _parcsComplets.value = emptySet()
        _recherche.value = saisie
    }

    /** Déplie ou replie le parc d'un client. */
    fun onBasculerParc(clientId: String) {
        val courants = _deplies.value
        _deplies.value = if (clientId in courants) courants - clientId else courants + clientId
    }

    /**
     * Montre tout le parc d'un client malgré la recherche : voir [parcsComplets].
     *
     * Le parc est aussi **noté déplié** : la recherche le dépliait de force, et
     * « voir tout » qui le replierait d'un coup cacherait ce qu'on vient de
     * demander à voir.
     */
    fun onVoirToutLeParc(clientId: String) {
        _parcsComplets.value = _parcsComplets.value + clientId
        _deplies.value = _deplies.value + clientId
    }

    // — La page d'un client ———————————————————————————————————————————————

    private val _ouvertId = MutableStateFlow<String?>(null)

    private val _aujourdhui = MutableStateFlow(horloge())

    /**
     * Le client dont la page est ouverte, tenu **par son identifiant** comme la
     * machine d'[EquipementsViewModel.ouverte] : la page relit toujours la base,
     * si bien qu'un renommage s'y voit sans rien recopier et qu'une suppression
     * la referme d'elle-même — le client introuvable rend `null`.
     */
    val ouvert: StateFlow<Client?> = combine(repository.clients, _ouvertId) { tous, id ->
        id?.let { cherche -> tous.firstOrNull { it.id == cherche } }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = null,
        )

    /**
     * Le dossier du client ouvert : interventions, factures dues, devis en
     * cours, sites.
     *
     * Les flux de toutes les interventions et de toutes les factures sont les
     * plus lourds du projet ; ils ne coulent donc **que tant qu'une page est
     * ouverte** (`flatMapLatest` sur l'identifiant), même règle que la recherche
     * de la journée. Filtrer ici plutôt que par une requête de plus : les flux
     * existent, le filtre tient en mémoire, et il s'éprouve sur les faux DAO.
     */
    val dossier: StateFlow<DossierClient?> = _ouvertId
        .flatMapLatest<String?, DossierClient?> { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(
                    repository.clients,
                    interventions.observerToutes(),
                    factures.facturesChiffrees,
                    combine(devis.devisChiffres, factures.devisFactures) { d, liens -> d to liens },
                    _aujourdhui,
                ) { tous, toutes, chiffrees, (chiffres, liens), jour ->
                    val client = tous.firstOrNull { it.id == id } ?: return@combine null
                    val siennes = toutes.filter { it.clientId == id || it.clientFactureId == id }
                    val (aVenir, passees) = siennes.partition { !it.statut.close && !it.date.isBefore(jour) }
                    DossierClient(
                        client = client,
                        donneurDOrdre = client.parentId?.let { parent -> tous.firstOrNull { it.id == parent } },
                        sites = tous.filter { it.parentId == id },
                        aVenir = aVenir.sortedWith(compareBy<Intervention> { it.date }.thenBy { it.heure }),
                        passees = passees.sortedWith(
                            compareByDescending<Intervention> { it.date }.thenByDescending { it.heure },
                        ),
                        dues = chiffrees
                            .filter { it.facture.clientId == id && it.attendPaiement }
                            .sortedBy { it.facture.echeanceLe },
                        devisEnCours = chiffres.filter { chiffre ->
                            val lien = liens[chiffre.devis.id]
                            chiffre.devis.clientId == id &&
                                chiffre.devis.statut != StatutDevis.REFUSE &&
                                // Une facture annulée rend le devis à nouveau facturable.
                                (lien == null || lien.statut == StatutFacture.ANNULEE)
                        },
                        aujourdhui = jour,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = null,
        )

    /** Ouvre la page d'un client, ou d'un site depuis celle de son enseigne. */
    fun onOuvrirClient(id: String) {
        _aujourdhui.value = horloge()
        _ouvertId.value = id
    }

    /**
     * Le retour depuis une page : un site revient à son donneur d'ordre, comme
     * une unité revient à son groupe ; un client revient au carnet.
     */
    fun onRetourClient() {
        val id = _ouvertId.value ?: return
        // L'enseigne est lue en base et non dans [ouvert] : ce flux n'est collecté
        // que si un écran l'observe, et `.value` sur un flux que personne ne
        // collecte reste `null` pour toujours — le site renverrait au carnet.
        viewModelScope.launch {
            val parent = repository.clients.first().firstOrNull { it.id == id }?.parentId
            // Une autre page a pu s'ouvrir entre-temps : ne pas la refermer.
            if (_ouvertId.value == id) _ouvertId.value = parent
        }
    }

    /** Referme la page, quel qu'en soit le niveau : toucher l'onglet déjà ouvert. */
    fun onFermerClient() {
        _ouvertId.value = null
    }

    // — La fiche ————————————————————————————————————————————————————————

    private val _fiche = MutableStateFlow<EtatFicheClient?>(null)

    /** Fiche ouverte, ou `null` quand l'écran n'affiche que le carnet. */
    val fiche: StateFlow<EtatFicheClient?> = _fiche.asStateFlow()

    fun onNouveauClient() {
        _fiche.value = EtatFicheClient()
    }

    /**
     * Un site neuf, rattaché d'emblée à son donneur d'ordre.
     *
     * Le rattachement se décide **ici** et pas dans le formulaire : on ouvre un
     * site depuis la fiche de l'enseigne, et demander ensuite « à qui
     * l'attacher ? » serait reposer une question déjà répondue par le geste.
     */
    fun onNouveauSite(donneurDOrdre: Client) {
        _fiche.value = EtatFicheClient(parentId = donneurDOrdre.id, ville = donneurDOrdre.ville)
    }

    fun onOuvrirFiche(client: Client) {
        _fiche.value = EtatFicheClient.depuis(client)
    }

    fun onFicheChange(etat: EtatFicheClient) {
        _fiche.value = etat
    }

    fun onFermerFiche() {
        _fiche.value = null
    }

    /** Une saisie incomplète laisse la fiche ouverte plutôt que d'écrire à moitié. */
    fun onEnregistrerFiche() {
        val etat = _fiche.value ?: return
        if (!etat.estValide) return

        _fiche.value = null
        viewModelScope.launch { repository.enregistrer(etat.versClient()) }
    }

    /**
     * Supprime un client, et referme sa fiche si c'est celle qu'on regardait.
     *
     * Ce que la suppression emporte est décidé par `ClientDao.supprimer` : le
     * parc et les sites partent, les interventions, devis et factures restent,
     * lien coupé. L'écran ne fait que le demander, après avoir nommé ce qui
     * disparaît.
     */
    fun onSupprimerClient(client: Client) {
        if (_fiche.value?.id == client.id) _fiche.value = null
        // Un site supprimé ramène à son enseigne ; un client, au carnet.
        if (_ouvertId.value == client.id) _ouvertId.value = client.parentId
        viewModelScope.launch { repository.supprimer(client) }
    }

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                ClientsViewModel(
                    repository = conteneur.clients,
                    interventions = conteneur.interventions,
                    factures = conteneur.factures,
                    devis = conteneur.devis,
                )
            }
        }
    }
}
