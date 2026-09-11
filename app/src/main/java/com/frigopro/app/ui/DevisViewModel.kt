package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.LigneDevis
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.StatutDevis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * L'onglet Devis : la liste, et le devis ouvert.
 *
 * Même motif que partout ailleurs — le devis ouvert est retenu par son
 * identifiant, si bien qu'une ligne ajoutée ou un changement de statut se
 * voient sans rien recopier.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevisViewModel(
    private val devis: DevisRepository,
    private val clients: ClientRepository,
    private val parametres: ParametresRepository,
) : ViewModel() {

    val liste: StateFlow<List<Devis>> = devis.devis
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    val carnet: StateFlow<List<Client>> = clients.clients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    val reglages: StateFlow<Parametres> = parametres.parametres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), Parametres())

    private val _ouvert = MutableStateFlow<String?>(null)

    val ouvert: StateFlow<String?> = _ouvert.asStateFlow()

    /** Le devis ouvert et ses lignes, d'où se calculent les totaux. */
    val complet: StateFlow<DevisComplet?> = _ouvert
        .flatMapLatest { id -> if (id == null) flowOf(null) else devis.observerComplet(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), null)

    fun onOuvrir(document: Devis) {
        _ouvert.value = document.id
    }

    fun onFermer() {
        _ouvert.value = null
    }

    /**
     * Crée un devis et l'ouvre aussitôt.
     *
     * Le taux de TVA vient des réglages au moment de la création, puis le
     * devis en garde sa copie : un devis déjà envoyé ne doit pas se recalculer
     * parce qu'un réglage a bougé depuis.
     */
    fun onNouveau(client: Client?) {
        viewModelScope.launch {
            val cree = devis.creer(client = client, tauxTva = reglages.value.tauxTva)
            _ouvert.value = cree.id
        }
    }

    fun onObjet(texte: String) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch { devis.enregistrer(courant.copy(objet = texte)) }
    }

    fun onClient(client: Client) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch {
            devis.enregistrer(courant.copy(clientId = client.id, clientNom = client.nom))
        }
    }

    fun onStatut(statut: StatutDevis) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch { devis.changerStatut(courant, statut) }
    }

    fun onAjouterLigne(designation: String, quantite: Double, unite: String, prixUnitaire: Double) {
        val id = _ouvert.value ?: return
        viewModelScope.launch { devis.ajouterLigne(id, designation, quantite, unite, prixUnitaire) }
    }

    fun onModifierLigne(ligne: LigneDevis) {
        viewModelScope.launch { devis.enregistrerLigne(ligne) }
    }

    fun onSupprimerLigne(id: String) {
        viewModelScope.launch { devis.supprimerLigne(id) }
    }

    fun onSupprimer() {
        val id = _ouvert.value ?: return
        _ouvert.value = null
        viewModelScope.launch { devis.supprimer(id) }
    }

    companion object {

        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                DevisViewModel(conteneur.devis, conteneur.clients, conteneur.parametres)
            }
        }
    }
}
