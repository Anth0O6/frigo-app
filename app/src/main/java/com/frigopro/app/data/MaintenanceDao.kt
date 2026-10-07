package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * L'accès au plan de maintenance : les gammes, leurs points, leurs affectations
 * et le journal des visites.
 *
 * C'est une **classe abstraite** et non une interface, comme `ClientDao`,
 * `EquipementDao` et `TypeInterventionDao`, et pour la même raison : supprimer une
 * gamme touche trois tables, et une gamme à moitié supprimée serait pire qu'une
 * gamme qu'on ne peut pas supprimer. Le `@Transaction` est le seul moyen de
 * garantir que les trois avancent ou qu'aucune ne bouge.
 */
@Dao
abstract class MaintenanceDao {

    // — Les gammes ——————————————————————————————————————————————————————————

    /**
     * Le rang avant l'intitulé : l'ordre d'une gamme est un choix — on veut lire
     * la ronde du matin avant la visite annuelle — et l'alphabet ne le rendrait
     * pas. Il départage ensuite les rangs égaux, pour que la liste ne saute pas
     * d'un affichage à l'autre.
     */
    @Query("SELECT * FROM gammes ORDER BY rang, libelle COLLATE NOCASE")
    abstract fun observerGammes(): Flow<List<GammeMaintenance>>

    @Query("SELECT * FROM gammes WHERE id = :id")
    abstract suspend fun gamme(id: String): GammeMaintenance?

    @Upsert
    abstract suspend fun enregistrerGamme(gamme: GammeMaintenance)

    // — Les points d'une gamme ———————————————————————————————————————————————

    @Query("SELECT * FROM points_gamme ORDER BY rang, libelle COLLATE NOCASE")
    abstract fun observerPoints(): Flow<List<PointGamme>>

    @Query("SELECT * FROM points_gamme WHERE gammeId = :gammeId ORDER BY rang")
    abstract suspend fun pointsDe(gammeId: String): List<PointGamme>

    @Upsert
    abstract suspend fun enregistrerPoint(point: PointGamme)

    @Query("DELETE FROM points_gamme WHERE id = :id")
    abstract suspend fun supprimerPoint(id: String)

    @Query("DELETE FROM points_gamme WHERE gammeId = :gammeId")
    abstract suspend fun effacerPointsDe(gammeId: String)

    // — Les affectations ————————————————————————————————————————————————————

    @Query("SELECT * FROM affectations_gamme")
    abstract fun observerAffectations(): Flow<List<AffectationGamme>>

    @Query("SELECT * FROM affectations_gamme WHERE equipementId = :equipementId")
    abstract suspend fun affectationsDe(equipementId: String): List<AffectationGamme>

    @Query("SELECT * FROM affectations_gamme WHERE equipementId = :equipementId AND gammeId = :gammeId")
    abstract suspend fun affectation(equipementId: String, gammeId: String): AffectationGamme?

    @Upsert
    abstract suspend fun enregistrerAffectation(affectation: AffectationGamme)

    @Query("DELETE FROM affectations_gamme WHERE equipementId = :equipementId AND gammeId = :gammeId")
    abstract suspend fun retirerAffectation(equipementId: String, gammeId: String)

    @Query("DELETE FROM affectations_gamme WHERE gammeId = :gammeId")
    abstract suspend fun effacerAffectationsDe(gammeId: String)

    // — Le journal des visites ———————————————————————————————————————————————

    /**
     * Le journal entier, le plus récent d'abord.
     *
     * Antichronologique parce que c'est l'ordre dans lequel on le lit : la
     * dernière visite d'une machine est celle qui donne son échéance, et
     * l'historique se parcourt en remontant.
     */
    @Query("SELECT * FROM releves_gamme ORDER BY faitLe DESC")
    abstract fun observerReleves(): Flow<List<ReleveGamme>>

    @Query("SELECT * FROM releves_gamme WHERE equipementId = :equipementId ORDER BY faitLe DESC")
    abstract fun observerRelevesDe(equipementId: String): Flow<List<ReleveGamme>>

    @Upsert
    abstract suspend fun enregistrerReleve(releve: ReleveGamme)

    @Query("DELETE FROM releves_gamme WHERE id = :id")
    abstract suspend fun supprimerReleve(id: String)

    // — Ce que la sauvegarde lit, et ce qu'elle réécrit ————————————————————
    //
    // Les écritures de restauration passent par le DAO et non par le dépôt, qui
    // horodaterait chaque ligne et effacerait le `modifieLe` transporté par le
    // fichier — or c'est précisément lui qui départagera deux versions d'une même
    // ligne le jour où deux téléphones fusionneront leurs archives.

    @Query("SELECT * FROM gammes")
    abstract suspend fun toutesLesGammes(): List<GammeMaintenance>

    @Query("SELECT * FROM points_gamme")
    abstract suspend fun tousLesPoints(): List<PointGamme>

    @Query("SELECT * FROM affectations_gamme")
    abstract suspend fun toutesLesAffectations(): List<AffectationGamme>

    @Query("SELECT * FROM releves_gamme")
    abstract suspend fun tousLesReleves(): List<ReleveGamme>

    @Upsert
    abstract suspend fun enregistrerGammes(gammes: List<GammeMaintenance>)

    @Upsert
    abstract suspend fun enregistrerPoints(points: List<PointGamme>)

    @Upsert
    abstract suspend fun enregistrerAffectations(affectations: List<AffectationGamme>)

    @Upsert
    abstract suspend fun enregistrerReleves(releves: List<ReleveGamme>)

    // — Les deux suppressions qui touchent plusieurs tables ————————————————

    /**
     * Supprimer une gamme : ses points et ses affectations partent, **son
     * histoire reste**.
     *
     * C'est le même partage que pour la suppression d'un client, et il compte
     * autant : ce qui n'existe que par la gamme est effacé — ses points, le fait
     * que des machines la suivaient —, mais les visites qui ont été faites sont ce
     * qui s'est **passé**, et un contrôle les rapproche du contrat. Elles gardent
     * donc leur intitulé et leur périodicité, et perdent seulement leur lien.
     */
    @Transaction
    open suspend fun supprimerGamme(gammeId: String) {
        effacerPointsDe(gammeId)
        effacerAffectationsDe(gammeId)
        detacherReleves(gammeId)
        effacerGamme(gammeId)
    }

    @Query("UPDATE releves_gamme SET gammeId = NULL WHERE gammeId = :gammeId")
    abstract suspend fun detacherReleves(gammeId: String)

    @Query("DELETE FROM gammes WHERE id = :id")
    abstract suspend fun effacerGamme(id: String)

    /**
     * Ce qu'une machine supprimée laisse de son plan.
     *
     * Le même partage que pour une gamme, et que pour la suppression d'un client :
     * l'**affectation est effacée**, parce qu'elle n'existe que par la machine — un
     * plan de maintenance sur un équipement qui n'est plus là ne veut rien dire, et
     * il resterait en base et dans les sauvegardes sans s'afficher nulle part. Les
     * **visites sont gardées, lien coupé**, parce qu'elles sont la preuve qu'une
     * maintenance contractuelle a eu lieu : les effacer parce que quelqu'un range
     * l'inventaire reviendrait à perdre ce qu'un contrôle vient chercher. Elles
     * gardent le nom de la machine, recopié, et se relisent donc entières.
     */
    @Transaction
    open suspend fun oublierEquipement(equipementId: String) {
        effacerAffectationsDeEquipement(equipementId)
        detacherRelevesDeEquipement(equipementId)
    }

    @Query("DELETE FROM affectations_gamme WHERE equipementId = :equipementId")
    abstract suspend fun effacerAffectationsDeEquipement(equipementId: String)

    @Query("UPDATE releves_gamme SET equipementId = NULL WHERE equipementId = :equipementId")
    abstract suspend fun detacherRelevesDeEquipement(equipementId: String)
}
