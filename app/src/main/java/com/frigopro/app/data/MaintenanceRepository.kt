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

    /**
     * Rattache une sélection de machines à une gamme, et rend **combien la
     * suivaient déjà**.
     *
     * C'est la règle d'[affecter], appliquée en lot et poussée d'un cran : une
     * machine déjà rattachée **n'est pas réécrite du tout**. Son `depuisLe` reste
     * le sien — c'est le dénominateur de son attestation, et le remettre à la
     * date choisie pour les autres aurait effacé des mois de contrat d'un geste
     * fait pour en ajouter —, et son `modifieLe` aussi : rien n'a changé pour
     * elle, et un horodatage déplacé dirait le contraire le jour où deux
     * téléphones départageront leurs versions.
     *
     * [depuisLe] ne vaut donc que pour les **nouvelles** affectations, et c'est ce
     * qui rend la date proposable sans danger : on peut dater de janvier l'entrée
     * au plan d'un linéaire inventorié en octobre sans toucher à la vitrine qui y
     * était depuis mars.
     *
     * Les nouvelles s'écrivent en **une seule** écriture groupée, que Room passe
     * dans une transaction : douze meubles rattachés à moitié feraient une ronde
     * qui oublie le bout du linéaire.
     */
    suspend fun affecterGroupe(
        equipementIds: Collection<String>,
        gammeId: String,
        depuisLe: LocalDate = LocalDate.now(),
    ): Int {
        val identifiants = equipementIds.distinct()
        val maintenant = Instant.now()
        val nouvelles = identifiants
            .filter { dao.affectation(it, gammeId) == null }
            .map { equipementId ->
                AffectationGamme(
                    equipementId = equipementId,
                    gammeId = gammeId,
                    depuisLe = depuisLe,
                    modifieLe = maintenant,
                )
            }
        if (nouvelles.isNotEmpty()) dao.enregistrerAffectations(nouvelles)
        return identifiants.size - nouvelles.size
    }

    /**
     * Retire une gamme d'une sélection de machines. Le journal ne bouge pas :
     * voir [MaintenanceDao.retirerDe].
     */
    suspend fun retirerGroupe(equipementIds: Collection<String>, gammeId: String) {
        if (equipementIds.isEmpty()) return
        dao.retirerDe(equipementIds.distinct(), gammeId)
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
