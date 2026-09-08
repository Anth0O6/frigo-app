package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Détient l'état de l'écran « Interventions du jour ».
 *
 * L'UI est purement déclarative : elle observe [interventions] et remonte les
 * intentions utilisateur via [onAjouterIntervention].
 */
class InterventionsViewModel(
    private val repository: InterventionRepository = InterventionRepository(),
) : ViewModel() {

    val interventions: StateFlow<List<Intervention>> = repository.interventions

    fun onAjouterIntervention() {
        repository.ajouterIntervention()
    }
}
