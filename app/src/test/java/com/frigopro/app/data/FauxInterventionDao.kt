package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * DAO en mémoire reproduisant le contrat SQL du vrai : filtre par journée,
 * tri par heure, et remplacement de la ligne portant le même identifiant.
 *
 * Permet d'exercer le dépôt et le ViewModel sur la JVM, sans Android ni SQLite.
 */
class FauxInterventionDao : InterventionDao {

    private val lignes = MutableStateFlow<List<Intervention>>(emptyList())

    /** Contenu courant, pour les assertions. */
    val contenu: List<Intervention> get() = lignes.value

    override fun observerJournee(date: LocalDate): Flow<List<Intervention>> =
        lignes.map { liste -> liste.filter { it.date == date }.sortedBy { it.heure } }

    override fun observerParEquipement(equipementId: String): Flow<List<Intervention>> =
        lignes.map { liste ->
            liste.filter { it.equipementId == equipementId }
                .sortedWith(compareByDescending<Intervention> { it.date }.thenByDescending { it.heure })
        }

    override fun observer(id: String): Flow<Intervention?> =
        lignes.map { liste -> liste.firstOrNull { it.id == id } }

    override fun observerPeriode(debut: LocalDate, fin: LocalDate): Flow<List<Intervention>> =
        lignes.map { liste ->
            liste.filter { it.date >= debut && it.date <= fin }
                .sortedWith(compareBy<Intervention> { it.date }.thenBy { it.heure })
        }

    override suspend fun numerosAttribues(): List<String> =
        lignes.value.map { it.numero }.filter { it.isNotEmpty() }

    override suspend fun toutes(): List<Intervention> = lignes.value

    override suspend fun enregistrer(intervention: Intervention) {
        lignes.update { liste -> liste.filterNot { it.id == intervention.id } + intervention }
    }

    override suspend fun enregistrerToutes(interventions: List<Intervention>) {
        val identifiants = interventions.map { it.id }.toSet()
        lignes.update { liste -> liste.filterNot { it.id in identifiants } + interventions }
    }

    override suspend fun supprimer(id: String) {
        lignes.update { liste -> liste.filterNot { it.id == id } }
    }

    /**
     * Reproduit le `UPDATE … SET typeLibelle` que lance [TypeInterventionDao] :
     * la propagation d'un renommage touche cette table, et c'est ici qu'elle
     * peut être vérifiée sans SQLite.
     */
    fun propagerLibelle(typeId: String, libelle: String) {
        lignes.update { liste ->
            liste.map { if (it.typeId == typeId) it.copy(typeLibelle = libelle) else it }
        }
    }

    /** Reproduit le `UPDATE … SET typeId = NULL` de [TypeInterventionDao]. */
    fun detacher(typeId: String) {
        lignes.update { liste ->
            liste.map { if (it.typeId == typeId) it.copy(typeId = null) else it }
        }
    }

    /** Même rôle pour le parc : voir [EquipementDao.propagerNom]. */
    fun propagerNom(equipementId: String, nom: String) {
        lignes.update { liste ->
            liste.map { if (it.equipementId == equipementId) it.copy(equipementNom = nom) else it }
        }
    }

    /** Voir [TechnicienDao.propagerNom]. */
    fun propagerTechnicien(technicienId: String, nom: String) {
        lignes.update { liste ->
            liste.map { if (it.technicienId == technicienId) it.copy(technicienNom = nom) else it }
        }
    }

    /** Voir [TechnicienDao.detacher] : le lien tombe, le nom reste. */
    fun detacherTechnicien(technicienId: String) {
        lignes.update { liste ->
            liste.map { if (it.technicienId == technicienId) it.copy(technicienId = null) else it }
        }
    }

    /** Voir [EquipementDao.detacher] : le lien tombe, le nom reste. */
    fun detacherEquipement(equipementId: String) {
        lignes.update { liste ->
            liste.map { if (it.equipementId == equipementId) it.copy(equipementId = null) else it }
        }
    }
}
