package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Parc et photos en mémoire, reproduisant le contrat SQL du vrai DAO.
 *
 * Il reçoit le faux DAO d'interventions pour la même raison que
 * [FauxTypeInterventionDao] : le vrai écrit dans cette table, et la propagation
 * d'un renommage comme le détachement d'une machine supprimée sont précisément
 * ce qu'on veut pouvoir vérifier sans SQLite.
 */
class FauxEquipementDao(
    private val interventions: FauxInterventionDao = FauxInterventionDao(),
) : EquipementDao() {

    private val lignes = MutableStateFlow<List<Equipement>>(emptyList())

    private val images = MutableStateFlow<List<Photo>>(emptyList())

    /** Contenu courant, pour les assertions. */
    val contenu: List<Equipement> get() = lignes.value

    val contenuPhotos: List<Photo> get() = images.value

    override fun observerTous(): Flow<List<Equipement>> = lignes

    override suspend fun tous(): List<Equipement> = lignes.value

    /** `COLLATE NOCASE`, et par client : le vrai DAO cherche dans un seul parc. */
    override suspend fun trouverParNom(clientId: String, nom: String): Equipement? =
        lignes.value.firstOrNull { it.clientId == clientId && it.nom.equals(nom, ignoreCase = true) }

    override suspend fun enregistrer(equipement: Equipement) {
        lignes.update { liste -> liste.filterNot { it.id == equipement.id } + equipement }
    }

    override suspend fun enregistrerTous(equipements: List<Equipement>) {
        val identifiants = equipements.map { it.id }.toSet()
        lignes.update { liste -> liste.filterNot { it.id in identifiants } + equipements }
    }

    override fun observerPhotos(equipementId: String): Flow<List<Photo>> = images.map { liste ->
        liste.filter { it.equipementId == equipementId }.sortedBy { it.priseLe }
    }

    override suspend fun photosDe(equipementId: String): List<Photo> =
        images.value.filter { it.equipementId == equipementId }

    override suspend fun unitesDe(id: String): List<Equipement> =
        contenu.filter { it.parentId == id }

    override suspend fun photo(id: String): Photo? = images.value.firstOrNull { it.id == id }

    override suspend fun toutesLesPhotos(): List<Photo> = images.value

    override suspend fun enregistrerPhoto(photo: Photo) {
        images.update { liste -> liste.filterNot { it.id == photo.id } + photo }
    }

    override suspend fun enregistrerPhotos(photos: List<Photo>) {
        val identifiants = photos.map { it.id }.toSet()
        images.update { liste -> liste.filterNot { it.id in identifiants } + photos }
    }

    override suspend fun effacerPhoto(id: String) {
        images.update { liste -> liste.filterNot { it.id == id } }
    }

    override suspend fun effacerPhotosDe(equipementId: String) {
        images.update { liste -> liste.filterNot { it.equipementId == equipementId } }
    }

    override suspend fun propagerNom(id: String, nom: String) {
        interventions.propagerNom(id, nom)
    }

    override suspend fun detacher(id: String) {
        interventions.detacherEquipement(id)
    }

    override suspend fun effacer(id: String) {
        lignes.update { liste -> liste.filterNot { it.id == id } }
    }
}
