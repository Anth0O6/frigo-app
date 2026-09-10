package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
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

    private val _fiche = MutableStateFlow<EtatFicheClient?>(null)

    /** Fiche ouverte, ou `null` quand l'écran n'affiche que le carnet. */
    val fiche: StateFlow<EtatFicheClient?> = _fiche.asStateFlow()

    fun onNouveauClient() {
        _fiche.value = EtatFicheClient()
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
