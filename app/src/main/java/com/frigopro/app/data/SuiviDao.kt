package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès SQL à ce qui s'est passé pendant une intervention : relevés,
 * mouvements de fluide, pièces posées et photos avant/après.
 *
 * Les quatre sont réunis pour la raison qui réunit déjà machines et photos
 * dans [EquipementDao] : supprimer une intervention doit les emporter **d'un
 * bloc**. Une intervention effacée qui laisserait derrière elle des
 * kilogrammes de fluide au registre serait pire qu'un bug d'affichage — ce
 * sont des lignes réglementaires, et elles ne doivent désigner que des
 * interventions qui existent.
 *
 * Comme ailleurs, les fichiers image ne sont pas du ressort de SQLite : c'est
 * le dépôt qui les efface une fois la transaction passée.
 */
@Dao
abstract class SuiviDao {

    // — Relevés ————————————————————————————————————————————————————————————

    @Query("SELECT * FROM releves WHERE interventionId = :interventionId ORDER BY releveLe ASC")
    abstract fun observerReleves(interventionId: String): Flow<List<Releve>>

    /**
     * Les relevés d'une machine, du plus ancien au plus récent, toutes
     * interventions confondues : c'est la tendance que trace la fiche machine.
     */
    @Query("SELECT * FROM releves WHERE equipementId = :equipementId ORDER BY releveLe ASC")
    abstract fun observerRelevesMachine(equipementId: String): Flow<List<Releve>>

    @Query("SELECT * FROM releves")
    abstract suspend fun tousLesReleves(): List<Releve>

    @Upsert
    abstract suspend fun enregistrerReleve(releve: Releve)

    @Upsert
    abstract suspend fun enregistrerReleves(releves: List<Releve>)

    @Query("DELETE FROM releves WHERE id = :id")
    abstract suspend fun effacerReleve(id: String)

    @Query("DELETE FROM releves WHERE interventionId = :interventionId")
    abstract suspend fun effacerRelevesDe(interventionId: String)

    // — Fluide —————————————————————————————————————————————————————————————

    @Query("SELECT * FROM mouvements_fluide WHERE interventionId = :interventionId ORDER BY le ASC")
    abstract fun observerMouvements(interventionId: String): Flow<List<MouvementFluide>>

    /** Tout le registre, du plus récent au plus ancien : ce qui s'exporte. */
    @Query("SELECT * FROM mouvements_fluide ORDER BY le DESC")
    abstract fun observerRegistre(): Flow<List<MouvementFluide>>

    @Query("SELECT * FROM mouvements_fluide")
    abstract suspend fun tousLesMouvements(): List<MouvementFluide>

    @Upsert
    abstract suspend fun enregistrerMouvement(mouvement: MouvementFluide)

    @Upsert
    abstract suspend fun enregistrerMouvements(mouvements: List<MouvementFluide>)

    @Query("DELETE FROM mouvements_fluide WHERE id = :id")
    abstract suspend fun effacerMouvement(id: String)

    @Query("DELETE FROM mouvements_fluide WHERE interventionId = :interventionId")
    abstract suspend fun effacerMouvementsDe(interventionId: String)

    // — Pièces —————————————————————————————————————————————————————————————

    @Query("SELECT * FROM pieces_posees WHERE interventionId = :interventionId")
    abstract fun observerPieces(interventionId: String): Flow<List<PiecePosee>>

    @Query("SELECT * FROM pieces_posees")
    abstract suspend fun toutesLesPieces(): List<PiecePosee>

    @Upsert
    abstract suspend fun enregistrerPiece(piece: PiecePosee)

    @Upsert
    abstract suspend fun enregistrerPieces(pieces: List<PiecePosee>)

    @Query("DELETE FROM pieces_posees WHERE id = :id")
    abstract suspend fun effacerPiece(id: String)

    @Query("DELETE FROM pieces_posees WHERE interventionId = :interventionId")
    abstract suspend fun effacerPiecesDe(interventionId: String)

    // — Photos avant / après ———————————————————————————————————————————————

    @Query("SELECT * FROM photos WHERE interventionId = :interventionId ORDER BY priseLe ASC")
    abstract fun observerPhotos(interventionId: String): Flow<List<Photo>>

    /** Les photos d'une intervention, pour savoir quels fichiers effacer. */
    @Query("SELECT * FROM photos WHERE interventionId = :interventionId")
    abstract suspend fun photosDe(interventionId: String): List<Photo>

    @Upsert
    abstract suspend fun enregistrerPhoto(photo: Photo)

    @Query("DELETE FROM photos WHERE id = :id")
    abstract suspend fun effacerPhoto(id: String)

    @Query("DELETE FROM photos WHERE interventionId = :interventionId")
    abstract suspend fun effacerPhotosDe(interventionId: String)

    // — Checklist ——————————————————————————————————————————————————————————

    @Query("SELECT * FROM points_checklist WHERE interventionId = :interventionId ORDER BY rang ASC")
    abstract fun observerChecklist(interventionId: String): Flow<List<PointChecklist>>

    @Query("SELECT COUNT(*) FROM points_checklist WHERE interventionId = :interventionId")
    abstract suspend fun compterChecklist(interventionId: String): Int

    @Query("SELECT * FROM points_checklist")
    abstract suspend fun tousLesPoints(): List<PointChecklist>

    @Upsert
    abstract suspend fun enregistrerPoint(point: PointChecklist)

    @Upsert
    abstract suspend fun enregistrerPoints(points: List<PointChecklist>)

    @Query("UPDATE points_checklist SET fait = 1 WHERE interventionId = :interventionId")
    abstract suspend fun toutCocher(interventionId: String)

    @Query("DELETE FROM points_checklist WHERE interventionId = :interventionId")
    abstract suspend fun effacerChecklistDe(interventionId: String)

    // — Suppression d'une intervention —————————————————————————————————————

    @Query("DELETE FROM interventions WHERE id = :id")
    abstract suspend fun effacerIntervention(id: String)

    /**
     * Efface l'intervention et tout ce qu'elle portait, en une transaction.
     *
     * L'ordre est celui des dépendances : les lignes rattachées d'abord, la
     * ligne maîtresse ensuite. Interrompue au milieu — ce qu'une transaction
     * interdit, mais le raisonnement tient quand même — elle laisserait des
     * orphelins invisibles plutôt qu'une intervention sans son contenu.
     */
    @Transaction
    open suspend fun supprimerIntervention(id: String) {
        effacerRelevesDe(id)
        effacerMouvementsDe(id)
        effacerPiecesDe(id)
        effacerPhotosDe(id)
        effacerChecklistDe(id)
        effacerIntervention(id)
    }
}
