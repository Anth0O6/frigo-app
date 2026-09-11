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

    /** Historique d'une machine, du plus récent au plus ancien. */
    fun observerParEquipement(equipementId: String): Flow<List<Intervention>> =
        dao.observerParEquipement(equipementId)

    /** Une intervention suivie par son identifiant : ce qu'observe son écran. */
    fun observer(id: String): Flow<Intervention?> = dao.observer(id)

    /** La semaine du lundi [debut] au dimanche qui suit, pour le planning. */
    fun observerSemaine(debut: LocalDate): Flow<List<Intervention>> =
        dao.observerPeriode(debut, debut.plusDays(6))

    /** Crée l'intervention ou remplace celle qui porte le même identifiant. */
    suspend fun enregistrer(intervention: Intervention) {
        dao.enregistrer(
            intervention.copy(
                client = intervention.client.trim(),
                ville = intervention.ville.trim(),
                typeLibelle = intervention.typeLibelle.trim(),
                modifieLe = Instant.now(),
            ),
        )
    }

    /**
     * Démarre ou met en pause le chronomètre.
     *
     * Démarrer fait passer l'intervention « en cours » si elle ne l'était pas :
     * le technicien qui lance son chrono est arrivé, et lui demander de le dire
     * une seconde fois serait du travail pour rien.
     *
     * @param maintenant passé en paramètre plutôt que lu de l'horloge, pour que
     *   le comportement soit éprouvable seconde par seconde.
     */
    suspend fun basculerChrono(
        intervention: Intervention,
        maintenant: Instant = Instant.now(),
    ): Intervention {
        val chrono = intervention.chrono.basculer(maintenant)
        val statut = if (chrono.enMarche && intervention.statut == StatutIntervention.A_FAIRE) {
            StatutIntervention.EN_COURS
        } else {
            intervention.statut
        }
        val misAJour = intervention.copy(chrono = chrono, statut = statut)
        enregistrer(misAJour)
        return misAJour
    }

    /**
     * Clôt l'intervention : chronomètre arrêté, statut terminé, et un numéro
     * de compte-rendu attribué s'il n'en avait pas.
     *
     * Le numéro n'est posé qu'ici, et jamais réécrit : une intervention
     * planifiée puis annulée n'a aucune raison d'en consommer un, et un
     * document déjà remis au client ne doit pas changer de référence.
     */
    suspend fun cloturer(
        intervention: Intervention,
        maintenant: Instant = Instant.now(),
        aujourdhui: LocalDate = LocalDate.now(),
    ): Intervention {
        val numero = intervention.numero.ifEmpty {
            Numerotation.suivant(
                Numerotation.PREFIXE_INTERVENTION,
                aujourdhui,
                dao.numerosAttribues(),
            )
        }
        val close = intervention.copy(
            chrono = intervention.chrono.arreter(maintenant),
            statut = StatutIntervention.TERMINEE,
            numero = numero,
        )
        enregistrer(close)
        return close
    }

    suspend fun supprimer(id: String) {
        dao.supprimer(id)
    }
}
