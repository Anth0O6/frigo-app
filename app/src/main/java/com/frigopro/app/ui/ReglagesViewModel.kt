package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.TypeIntervention
import com.frigopro.app.data.TypeInterventionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Boîte de dialogue ouverte par l'écran Réglages.
 *
 * Un seul état plutôt que trois booléens : deux dialogues ne peuvent pas être
 * ouverts en même temps, et le dire au type supprime la question.
 */
sealed interface DialogueReglages {

    data object Creation : DialogueReglages

    data class Renommage(val type: TypeIntervention) : DialogueReglages

    data class Suppression(val type: TypeIntervention) : DialogueReglages
}

/**
 * Détient l'état de l'onglet « Réglages ».
 *
 * Il ne tient que les types d'intervention pour l'instant ; c'est ici que les
 * réglages suivants viendront se ranger.
 */
class ReglagesViewModel(
    private val typeRepository: TypeInterventionRepository,
    private val parametresRepository: ParametresRepository,
) : ViewModel() {

    /** Les réglages, jamais `null` : voir [ParametresRepository]. */
    val parametres: StateFlow<Parametres> = parametresRepository.parametres
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = Parametres(),
        )

    fun onThemeSombre(actif: Boolean) = modifier { it.copy(themeSombre = actif) }

    fun onModeGants(actif: Boolean) = modifier { it.copy(modeGants = actif) }

    fun onChronoAuto(actif: Boolean) = modifier { it.copy(chronoAuto = actif) }

    fun onTechnicien(nom: String) = modifier { it.copy(technicien = nom) }

    fun onAttestation(mention: String) = modifier { it.copy(attestation = mention) }

    fun onTauxHoraire(taux: Double) = modifier { it.copy(tauxHoraire = taux) }

    fun onTauxTva(taux: Double) = modifier { it.copy(tauxTva = taux) }

    private fun modifier(transformation: (Parametres) -> Parametres) {
        viewModelScope.launch { parametresRepository.modifier(transformation) }
    }


    val types: StateFlow<List<TypeIntervention>> = typeRepository.types
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    private val _dialogue = MutableStateFlow<DialogueReglages?>(null)

    val dialogue: StateFlow<DialogueReglages?> = _dialogue.asStateFlow()

    fun onAjouterType() {
        _dialogue.value = DialogueReglages.Creation
    }

    fun onRenommerType(type: TypeIntervention) {
        _dialogue.value = DialogueReglages.Renommage(type)
    }

    fun onSupprimerType(type: TypeIntervention) {
        _dialogue.value = DialogueReglages.Suppression(type)
    }

    fun onFermerDialogue() {
        _dialogue.value = null
    }

    /**
     * Valide la saisie du dialogue ouvert : création ou renommage selon le cas.
     * Un renommage se répercute sur les interventions qui désignent le type.
     */
    fun onValiderIntitule(libelle: String) {
        if (libelle.isBlank()) return

        when (val ouvert = _dialogue.value) {
            DialogueReglages.Creation -> {
                _dialogue.value = null
                viewModelScope.launch { typeRepository.trouverOuCreer(libelle) }
            }

            is DialogueReglages.Renommage -> {
                _dialogue.value = null
                viewModelScope.launch {
                    typeRepository.enregistrer(ouvert.type.copy(libelle = libelle))
                }
            }

            else -> Unit
        }
    }

    fun onConfirmerSuppression() {
        val ouvert = _dialogue.value as? DialogueReglages.Suppression ?: return

        _dialogue.value = null
        viewModelScope.launch { typeRepository.supprimer(ouvert.type.id) }
    }

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                ReglagesViewModel(conteneur.typesIntervention, conteneur.parametres)
            }
        }
    }
}
