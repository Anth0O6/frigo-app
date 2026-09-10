package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Liste de types en mémoire, reproduisant le contrat SQL du vrai DAO.
 *
 * Il reçoit le faux DAO d'interventions parce que le vrai écrit aussi dans
 * cette table : sans cela, la propagation d'un renommage ne serait pas
 * testable hors émulateur, alors que c'est tout l'intérêt de la manœuvre.
 */
class FauxTypeInterventionDao(
    private val interventions: FauxInterventionDao = FauxInterventionDao(),
) : TypeInterventionDao() {

    private val lignes = MutableStateFlow<List<TypeIntervention>>(emptyList())

    /** Contenu courant, pour les assertions. */
    val contenu: List<TypeIntervention> get() = lignes.value

    override fun observerTous(): Flow<List<TypeIntervention>> = lignes

    override suspend fun tous(): List<TypeIntervention> = lignes.value

    /** `COLLATE NOCASE` du vrai DAO : correspondance exacte à la casse près. */
    override suspend fun trouverParLibelle(libelle: String): TypeIntervention? =
        lignes.value.firstOrNull { it.libelle.equals(libelle, ignoreCase = true) }

    override suspend fun enregistrer(type: TypeIntervention) {
        lignes.update { liste -> liste.filterNot { it.id == type.id } + type }
    }

    override suspend fun enregistrerTous(types: List<TypeIntervention>) {
        val identifiants = types.map { it.id }.toSet()
        lignes.update { liste -> liste.filterNot { it.id in identifiants } + types }
    }

    override suspend fun propagerLibelle(id: String, libelle: String) {
        interventions.propagerLibelle(id, libelle)
    }

    override suspend fun effacer(id: String) {
        lignes.update { liste -> liste.filterNot { it.id == id } }
    }

    override suspend fun detacher(id: String) {
        interventions.detacher(id)
    }
}
