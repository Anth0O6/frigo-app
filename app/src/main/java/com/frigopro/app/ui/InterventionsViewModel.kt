package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Détient l'état de l'écran « Interventions ».
 *
 * L'UI observe [jour], [interventions] et [formulaire], et remonte les
 * intentions utilisateur via les méthodes `on…`.
 */
class InterventionsViewModel(
    private val repository: InterventionRepository,
) : ViewModel() {

    private val _jour = MutableStateFlow(LocalDate.now())

    /** Journée affichée. Changer sa valeur suffit à recharger la liste. */
    val jour: StateFlow<LocalDate> = _jour.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val interventions: StateFlow<List<Intervention>> = _jour
        .flatMapLatest { repository.observerJournee(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    private val _formulaire = MutableStateFlow<EtatFormulaire?>(null)

    /** Formulaire ouvert, ou `null` quand l'écran affiche seulement la liste. */
    val formulaire: StateFlow<EtatFormulaire?> = _formulaire.asStateFlow()

    fun onJourPrecedent() {
        _jour.update { it.minusDays(1) }
    }

    fun onJourSuivant() {
        _jour.update { it.plusDays(1) }
    }

    fun onJourChoisi(date: LocalDate) {
        _jour.value = date
    }

    /** La nouvelle intervention est proposée sur la journée consultée. */
    fun onNouvelleIntervention() {
        _formulaire.value = EtatFormulaire(date = _jour.value)
    }

    fun onModifierIntervention(intervention: Intervention) {
        _formulaire.value = EtatFormulaire.depuis(intervention)
    }

    fun onFormulaireChange(etat: EtatFormulaire) {
        _formulaire.value = etat
    }

    fun onFermerFormulaire() {
        _formulaire.value = null
    }

    /**
     * Enregistre la saisie, puis se place sur la journée de l'intervention :
     * sans cela, une ligne datée d'un autre jour disparaîtrait sans un mot.
     * Une saisie incomplète laisse le formulaire ouvert.
     */
    fun onValiderFormulaire() {
        val etat = _formulaire.value ?: return
        if (!etat.estValide) return

        _formulaire.value = null
        _jour.value = etat.date
        viewModelScope.launch { repository.enregistrer(etat.versIntervention()) }
    }

    /** Supprime l'intervention en cours d'édition. Sans effet sur une création. */
    fun onSupprimerIntervention() {
        val id = _formulaire.value?.id ?: return

        _formulaire.value = null
        viewModelScope.launch { repository.supprimer(id) }
    }

    companion object {

        /**
         * Délai avant d'arrêter d'observer la base quand l'écran passe en
         * arrière-plan : assez long pour traverser une rotation sans relancer
         * la requête, assez court pour ne rien collecter inutilement.
         */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                InterventionsViewModel((application as FrigoProApplication).conteneur.interventions)
            }
        }
    }
}
