package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Le plan de maintenance en mémoire, avec le contrat du vrai DAO.
 *
 * Il **reproduit à la main l'index unique** `(equipementId, gammeId)` : le laisser
 * au hasard ferait passer des tests que la vraie base refuserait, et c'est
 * exactement ce que `FauxMaterielDao` fait déjà pour l'unicité `(articleId, lieu)`
 * des stocks. Un faux plus permissif que la base est un faux qui ment.
 *
 * Les deux suppressions transactionnelles — `supprimerGamme` et
 * `oublierEquipement` — ne sont **pas** réécrites : elles sont `open` et non
 * `abstract` sur le vrai DAO, si bien que le faux hérite de la vraie logique et
 * que les tests éprouvent le partage effacé / détaché plutôt qu'une copie de
 * celui-ci.
 */
class FauxMaintenanceDao : MaintenanceDao() {

    private val lesGammes = MutableStateFlow<List<GammeMaintenance>>(emptyList())
    private val lesPoints = MutableStateFlow<List<PointGamme>>(emptyList())
    private val lesAffectations = MutableStateFlow<List<AffectationGamme>>(emptyList())
    private val lesReleves = MutableStateFlow<List<ReleveGamme>>(emptyList())

    val gammes: List<GammeMaintenance> get() = lesGammes.value
    val points: List<PointGamme> get() = lesPoints.value
    val affectations: List<AffectationGamme> get() = lesAffectations.value
    val releves: List<ReleveGamme> get() = lesReleves.value

    // — Les gammes ——————————————————————————————————————————————————————————

    override fun observerGammes(): Flow<List<GammeMaintenance>> =
        lesGammes.map { liste -> liste.sortedWith(compareBy({ it.rang }, { it.libelle.lowercase() })) }

    override suspend fun gamme(id: String): GammeMaintenance? =
        lesGammes.value.firstOrNull { it.id == id }

    override suspend fun enregistrerGamme(gamme: GammeMaintenance) {
        lesGammes.value = lesGammes.value.filterNot { it.id == gamme.id } + gamme
    }

    override suspend fun enregistrerGammes(gammes: List<GammeMaintenance>) {
        gammes.forEach { enregistrerGamme(it) }
    }

    override suspend fun toutesLesGammes(): List<GammeMaintenance> = lesGammes.value

    override suspend fun effacerGamme(id: String) {
        lesGammes.value = lesGammes.value.filterNot { it.id == id }
    }

    // — Les points ——————————————————————————————————————————————————————————

    override fun observerPoints(): Flow<List<PointGamme>> =
        lesPoints.map { liste -> liste.sortedWith(compareBy({ it.rang }, { it.libelle.lowercase() })) }

    override suspend fun pointsDe(gammeId: String): List<PointGamme> =
        lesPoints.value.filter { it.gammeId == gammeId }.sortedBy { it.rang }

    override suspend fun enregistrerPoint(point: PointGamme) {
        lesPoints.value = lesPoints.value.filterNot { it.id == point.id } + point
    }

    override suspend fun enregistrerPoints(points: List<PointGamme>) {
        points.forEach { enregistrerPoint(it) }
    }

    override suspend fun tousLesPoints(): List<PointGamme> = lesPoints.value

    override suspend fun supprimerPoint(id: String) {
        lesPoints.value = lesPoints.value.filterNot { it.id == id }
    }

    override suspend fun effacerPointsDe(gammeId: String) {
        lesPoints.value = lesPoints.value.filterNot { it.gammeId == gammeId }
    }

    // — Les affectations ————————————————————————————————————————————————————

    override fun observerAffectations(): Flow<List<AffectationGamme>> = lesAffectations

    override suspend fun affectationsDe(equipementId: String): List<AffectationGamme> =
        lesAffectations.value.filter { it.equipementId == equipementId }

    override suspend fun affectation(equipementId: String, gammeId: String): AffectationGamme? =
        lesAffectations.value.firstOrNull {
            it.equipementId == equipementId && it.gammeId == gammeId
        }

    /**
     * L'index unique, reproduit : deux affectations de la même gamme à la même
     * machine donneraient deux échéances contradictoires, et la vraie base lève.
     */
    override suspend fun enregistrerAffectation(affectation: AffectationGamme) {
        val doublon = lesAffectations.value.firstOrNull {
            it.id != affectation.id &&
                it.equipementId == affectation.equipementId &&
                it.gammeId == affectation.gammeId
        }
        require(doublon == null) {
            "index unique (equipementId, gammeId) violé : " +
                "${affectation.equipementId} déjà rattaché à ${affectation.gammeId}"
        }
        lesAffectations.value =
            lesAffectations.value.filterNot { it.id == affectation.id } + affectation
    }

    override suspend fun enregistrerAffectations(affectations: List<AffectationGamme>) {
        affectations.forEach { enregistrerAffectation(it) }
    }

    override suspend fun toutesLesAffectations(): List<AffectationGamme> = lesAffectations.value

    override suspend fun retirerAffectation(equipementId: String, gammeId: String) {
        lesAffectations.value = lesAffectations.value.filterNot {
            it.equipementId == equipementId && it.gammeId == gammeId
        }
    }

    override suspend fun effacerAffectationsDe(gammeId: String) {
        lesAffectations.value = lesAffectations.value.filterNot { it.gammeId == gammeId }
    }

    override suspend fun effacerAffectationsDeEquipement(equipementId: String) {
        lesAffectations.value = lesAffectations.value.filterNot { it.equipementId == equipementId }
    }

    // — Le journal ———————————————————————————————————————————————————————————

    override fun observerReleves(): Flow<List<ReleveGamme>> =
        lesReleves.map { liste -> liste.sortedByDescending { it.faitLe } }

    override fun observerRelevesDe(equipementId: String): Flow<List<ReleveGamme>> =
        lesReleves.map { liste ->
            liste.filter { it.equipementId == equipementId }.sortedByDescending { it.faitLe }
        }

    override suspend fun enregistrerReleve(releve: ReleveGamme) {
        lesReleves.value = lesReleves.value.filterNot { it.id == releve.id } + releve
    }

    override suspend fun enregistrerReleves(releves: List<ReleveGamme>) {
        releves.forEach { enregistrerReleve(it) }
    }

    override suspend fun tousLesReleves(): List<ReleveGamme> = lesReleves.value

    override suspend fun supprimerReleve(id: String) {
        lesReleves.value = lesReleves.value.filterNot { it.id == id }
    }

    override suspend fun detacherReleves(gammeId: String) {
        lesReleves.value = lesReleves.value.map {
            if (it.gammeId == gammeId) it.copy(gammeId = null) else it
        }
    }

    override suspend fun detacherRelevesDeEquipement(equipementId: String) {
        lesReleves.value = lesReleves.value.map {
            if (it.equipementId == equipementId) it.copy(equipementId = null) else it
        }
    }
}
