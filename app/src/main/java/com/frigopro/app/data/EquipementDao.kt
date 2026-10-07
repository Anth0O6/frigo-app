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
 * effacer ses photos, détacher ses interventions, emporter son plan de
 * maintenance, détacher ses visites et disparaître **d'un bloc**. Cinq tables
 * touchées, donc, comme [TypeInterventionDao] en touche deux, et pour la même
 * raison : seul un DAO peut rendre l'enchaînement atomique.
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

    /** Les unités intérieures d'un groupe, pour les emporter avec lui. */
    @Query("SELECT * FROM equipements WHERE parentId = :id")
    abstract suspend fun unitesDe(id: String): List<Equipement>

    /** Enregistre la machine et répercute son nom sur ses interventions. */
    @Transaction
    open suspend fun renommer(equipement: Equipement) {
        enregistrer(equipement)
        propagerNom(equipement.id, equipement.nom)
    }

    /**
     * Supprime la machine, et **ses unités intérieures avec elle**.
     *
     * Une unité intérieure n'a aucune existence sans son groupe — c'est le même
     * raisonnement que le parc d'un client sans le client. Les laisser derrière
     * les ferait remonter comme des machines indépendantes au client, et personne
     * ne comprendrait d'où sort une « unité salon » sans groupe.
     *
     * Chaque unité passe par le même traitement que le groupe : ses photos
     * effacées, ses interventions détachées. Une unité a pu être photographiée et
     * recevoir ses propres interventions, et l'oublier laisserait des fichiers
     * orphelins qu'aucun écran ne montrerait plus.
     */
    @Transaction
    open suspend fun supprimer(id: String) {
        unitesDe(id).forEach { unite ->
            detacher(unite.id)
            effacerPhotosDe(unite.id)
            effacerPlanDe(unite.id)
            detacherVisitesDe(unite.id)
            effacer(unite.id)
        }
        detacher(id)
        effacerPhotosDe(id)
        effacerPlanDe(id)
        detacherVisitesDe(id)
        effacer(id)
    }

    /**
     * Le plan de maintenance de la machine part avec elle.
     *
     * Une affectation n'existe que par sa machine — un contrat d'entretien sur
     * un équipement qui n'est plus là ne veut rien dire —, et plus rien ne
     * l'afficherait : `PlanMaintenance.echeances` écarte en silence une
     * affectation dont la machine a disparu. Elle resterait donc en base et
     * repartirait dans chaque archive de sauvegarde sans qu'aucun écran ne
     * puisse la montrer ni l'effacer. C'est le même raisonnement que pour les
     * photos, un rang au-dessus.
     *
     * Ces deux instructions vivent ici plutôt que dans [MaintenanceDao] pour
     * une raison et une seule : seul un `@Transaction` d'un même DAO est
     * atomique, et une machine effacée dont le plan survivrait serait
     * exactement l'incohérence que ce DAO existe pour empêcher.
     */
    @Query("DELETE FROM affectations_gamme WHERE equipementId = :id")
    abstract suspend fun effacerPlanDe(id: String)

    /**
     * Les visites, elles, sont **gardées, lien coupé**.
     *
     * C'est le partage de la suppression d'un client, et il compte autant ici :
     * une ligne du journal est la preuve qu'une maintenance contractuelle a eu
     * lieu, et l'effacer parce que quelqu'un range son inventaire reviendrait à
     * perdre ce qu'un contrôle vient chercher. Elle garde le nom de la machine,
     * recopié sur elle, et se relit donc entière.
     */
    @Query("UPDATE releves_gamme SET equipementId = NULL WHERE equipementId = :id")
    abstract suspend fun detacherVisitesDe(id: String)
}
