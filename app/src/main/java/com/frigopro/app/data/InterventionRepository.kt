package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Point d'entrée unique de l'UI vers les données.
 *
 * Tout est local en v1 : une base Room sur l'appareil, qui fonctionne en
 * chambre froide comme en sous-sol. Le jour où une synchronisation serveur
 * s'ajoutera, elle se branchera ici sans que le ViewModel ni l'UI changent.
 */
class InterventionRepository(private val dao: InterventionDao) {

    /** Interventions d'une journée, triées par heure, réémises à chaque écriture. */
    fun observerJournee(date: LocalDate): Flow<List<Intervention>> = dao.observerJournee(date)

    /** Crée l'intervention ou remplace celle qui porte le même identifiant. */
    suspend fun enregistrer(intervention: Intervention) {
        dao.enregistrer(
            intervention.copy(
                client = intervention.client.trim(),
                ville = intervention.ville.trim(),
                modifieLe = Instant.now(),
            ),
        )
    }

    suspend fun supprimer(id: String) {
        dao.supprimer(id)
    }
}
