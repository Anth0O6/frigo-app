package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Le plan de maintenance préventive : les gammes qu'on tient, et le journal de ce
 * qui a été fait.
 *
 * Il horodate chaque écriture comme tous les dépôts du projet, et nettoie les
 * intitulés à l'entrée. Il ne calcule **aucune** échéance : celles-ci se déduisent
 * des flux par [Maintenance.plan], qui est une fonction pure et s'éprouve sans
 * base.
 */
class MaintenanceRepository(private val dao: MaintenanceDao) {

    val gammes: Flow<List<GammeMaintenance>> = dao.observerGammes()
    val points: Flow<List<PointGamme>> = dao.observerPoints()
    val affectations: Flow<List<AffectationGamme>> = dao.observerAffectations()
    val releves: Flow<List<ReleveGamme>> = dao.observerReleves()

    fun relevesDe(equipementId: String): Flow<List<ReleveGamme>> =
        dao.observerRelevesDe(equipementId)

    /** Une gamme sans intitulé n'est pas une gamme : elle ne s'écrit pas. */
    suspend fun enregistrerGamme(gamme: GammeMaintenance): Boolean {
        val propre = gamme.libelle.trim()
        if (propre.isEmpty()) return false
        dao.enregistrerGamme(gamme.copy(libelle = propre, modifieLe = Instant.now()))
        return true
    }

    suspend fun supprimerGamme(gammeId: String) = dao.supprimerGamme(gammeId)

    suspend fun enregistrerPoint(point: PointGamme): Boolean {
        val propre = point.libelle.trim()
        if (propre.isEmpty()) return false
        dao.enregistrerPoint(point.copy(libelle = propre, modifieLe = Instant.now()))
        return true
    }

    suspend fun supprimerPoint(pointId: String) = dao.supprimerPoint(pointId)

    /**
     * Rattache un équipement à une gamme, à partir d'aujourd'hui par défaut.
     *
     * **L'affectation existante est réutilisée plutôt que doublée**, et c'est
     * l'index unique `(equipementId, gammeId)` qui l'exige : une seconde ligne
     * serait refusée par la base, et le refus se produirait au geste le plus
     * banal — rattacher deux fois la même gamme en balayant une liste de cent
     * machines. Rattacher de nouveau **ne remet donc pas le départ à zéro** : le
     * plan d'une machine ne doit pas repartir de zéro parce qu'on a touché sa
     * case deux fois.
     */
    suspend fun affecter(
        equipementId: String,
        gammeId: String,
        depuisLe: LocalDate = LocalDate.now(),
    ) {
        val existante = dao.affectation(equipementId, gammeId)
        dao.enregistrerAffectation(
            AffectationGamme(
                id = existante?.id ?: UUID.randomUUID().toString(),
                equipementId = equipementId,
                gammeId = gammeId,
                depuisLe = existante?.depuisLe ?: depuisLe,
                modifieLe = Instant.now(),
            ),
        )
    }

    /** Déplace le point de départ du plan, sans toucher au journal. */
    suspend fun reporterDepart(equipementId: String, gammeId: String, depuisLe: LocalDate) {
        val existante = dao.affectation(equipementId, gammeId) ?: return
        dao.enregistrerAffectation(existante.copy(depuisLe = depuisLe, modifieLe = Instant.now()))
    }

    suspend fun retirer(equipementId: String, gammeId: String) =
        dao.retirerAffectation(equipementId, gammeId)

    /**
     * Consigne une visite faite.
     *
     * Tout ce qui vient d'ailleurs est **recopié** sur la ligne — le nom de la
     * machine, l'intitulé de la gamme, sa périodicité, le nom du technicien —
     * parce que c'est une pièce datée qui sert de preuve : elle doit rester
     * lisible quand la gamme change de cadence, quand la machine est rangée, et
     * quand le technicien quitte l'entreprise. Même règle qu'une facture émise.
     */
    suspend fun consigner(
        equipement: Equipement,
        gamme: GammeMaintenance,
        faitLe: LocalDate = LocalDate.now(),
        technicien: Technicien? = null,
        notes: String = "",
    ) {
        dao.enregistrerReleve(
            ReleveGamme(
                equipementId = equipement.id,
                equipementNom = equipement.nom,
                gammeId = gamme.id,
                gammeLibelle = gamme.libelle,
                periodicite = gamme.periodicite,
                faitLe = faitLe,
                technicienId = technicien?.id,
                technicienNom = technicien?.nom.orEmpty(),
                notes = notes.trim(),
                modifieLe = Instant.now(),
            ),
        )
    }

    /**
     * Retire une visite consignée par erreur.
     *
     * Elle s'efface vraiment, à la différence d'une facture émise : un relevé de
     * visite n'ouvre aucune séquence numérotée, et une visite cochée par mégarde
     * sur la mauvaise machine fausserait l'échéance **et** le taux. Il vaut mieux
     * pouvoir la retirer que vivre avec.
     */
    suspend fun retirerReleve(releveId: String) = dao.supprimerReleve(releveId)
}
