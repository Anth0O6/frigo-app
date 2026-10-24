package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.GroupeClients
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Détient l'état de l'onglet « Clients ».
 *
 * Le carnet se remplit seul depuis les interventions ; cet écran sert à le
 * consulter et à compléter ce que la saisie d'une intervention ne demande pas,
 * l'adresse et le téléphone.
 */
class ClientsViewModel(private val repository: ClientRepository) : ViewModel() {

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
        viewModelScope.launch { repository.supprimer(client) }
    }

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                ClientsViewModel(conteneur.clients)
            }
        }
    }
}
