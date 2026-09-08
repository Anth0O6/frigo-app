package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Détient l'état de l'écran « Interventions du jour ».
 *
 * L'UI est purement déclarative : elle observe [interventions] et [formulaire],
 * et remonte les intentions utilisateur via les méthodes `on…`.
 */
class InterventionsViewModel(
    private val repository: InterventionRepository = InterventionRepository(),
) : ViewModel() {

    val interventions: StateFlow<List<Intervention>> = repository.interventions

    private val _formulaire = MutableStateFlow<EtatFormulaire?>(null)

    /** Formulaire ouvert, ou `null` quand l'écran affiche seulement la liste. */
    val formulaire: StateFlow<EtatFormulaire?> = _formulaire.asStateFlow()

    fun onNouvelleIntervention() {
        _formulaire.value = EtatFormulaire()
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
     * Enregistre la saisie : création si le formulaire n'a pas d'identifiant,
     * mise à jour sinon. Une saisie incomplète laisse le formulaire ouvert.
     */
    fun onValiderFormulaire() {
        val etat = _formulaire.value ?: return
        if (!etat.estValide) return

        val id = etat.id
        if (id == null) {
            repository.ajouterIntervention(
                heure = etat.heure,
                client = etat.client,
                ville = etat.ville,
                typePanne = etat.typePanne,
            )
        } else {
            repository.modifierIntervention(
                Intervention(
                    id = id,
                    heure = etat.heure,
                    client = etat.client,
                    ville = etat.ville,
                    typePanne = etat.typePanne,
                ),
            )
        }
        _formulaire.value = null
    }

    /** Supprime l'intervention en cours d'édition. Sans effet sur une création. */
    fun onSupprimerIntervention() {
        _formulaire.value?.id?.let(repository::supprimerIntervention)
        _formulaire.value = null
    }
}
