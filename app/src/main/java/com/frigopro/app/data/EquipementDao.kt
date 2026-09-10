package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès SQL au parc de machines et à leurs photos.
 *
 * Les photos sont servies par ce DAO plutôt que par un DAO à elles : une photo
 * n'a aucune vie en dehors de sa machine, et surtout supprimer une machine doit
 * effacer ses photos, détacher ses interventions et disparaître **d'un bloc**.
 * Trois tables touchées, donc, comme [TypeInterventionDao] en touche deux, et
 * pour la même raison : seul un DAO peut rendre l'enchaînement atomique.
 *
 * Les fichiers image, eux, ne sont pas du ressort de SQLite : c'est
 * [EquipementRepository] qui les efface après la transaction.
 */
@Dao
abstract class EquipementDao {

    /**
     * Tout le parc, sans `ORDER BY` : le tri alphabétique se fait côté Kotlin,
     * accents repliés. Le filtrage par client aussi — le flux est déjà observé
     * pour le formulaire, et un parc tient en mémoire.
     */
    @Query("SELECT * FROM equipements")
    abstract fun observerTous(): Flow<List<Equipement>>

    /** Tout le parc, pour la sauvegarde. */
    @Query("SELECT * FROM equipements")
    abstract suspend fun tous(): List<Equipement>

    /**
     * `COLLATE NOCASE` et par client : deux clients peuvent très bien avoir
     * chacun leur « vitrine salle 2 », ce sont deux machines différentes.
     */
    @Query("SELECT * FROM equipements WHERE clientId = :clientId AND nom = :nom COLLATE NOCASE LIMIT 1")
    abstract suspend fun trouverParNom(clientId: String, nom: String): Equipement?

    @Upsert
    abstract suspend fun enregistrer(equipement: Equipement)

    @Upsert
    abstract suspend fun enregistrerTous(equipements: List<Equipement>)

    @Query("SELECT * FROM photos WHERE equipementId = :equipementId ORDER BY priseLe ASC")
    abstract fun observerPhotos(equipementId: String): Flow<List<Photo>>

    /** Les photos d'une machine, pour savoir quels fichiers effacer avec elle. */
    @Query("SELECT * FROM photos WHERE equipementId = :equipementId")
    abstract suspend fun photosDe(equipementId: String): List<Photo>

    @Query("SELECT * FROM photos WHERE id = :id")
    abstract suspend fun photo(id: String): Photo?

    /** Toutes les photos, pour la sauvegarde. */
    @Query("SELECT * FROM photos")
    abstract suspend fun toutesLesPhotos(): List<Photo>

    @Upsert
    abstract suspend fun enregistrerPhoto(photo: Photo)

    @Upsert
    abstract suspend fun enregistrerPhotos(photos: List<Photo>)

    @Query("DELETE FROM photos WHERE id = :id")
    abstract suspend fun effacerPhoto(id: String)

    @Query("DELETE FROM photos WHERE equipementId = :equipementId")
    abstract suspend fun effacerPhotosDe(equipementId: String)

    @Query("UPDATE interventions SET equipementNom = :nom WHERE equipementId = :id")
    abstract suspend fun propagerNom(id: String, nom: String)

    /**
     * Le lien est coupé, le nom reste : une tournée passée continue d'afficher
     * sur quelle machine on est intervenu, même si la fiche n'existe plus.
     */
    @Query("UPDATE interventions SET equipementId = NULL WHERE equipementId = :id")
    abstract suspend fun detacher(id: String)

    @Query("DELETE FROM equipements WHERE id = :id")
    abstract suspend fun effacer(id: String)

    /** Enregistre la machine et répercute son nom sur ses interventions. */
    @Transaction
    open suspend fun renommer(equipement: Equipement) {
        enregistrer(equipement)
        propagerNom(equipement.id, equipement.nom)
    }

    @Transaction
    open suspend fun supprimer(id: String) {
        detacher(id)
        effacerPhotosDe(id)
        effacer(id)
    }
}
