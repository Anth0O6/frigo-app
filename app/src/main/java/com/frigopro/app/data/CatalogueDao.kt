package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès SQL aux techniciens.
 *
 * Comme [TypeInterventionDao], il touche deux tables par `@Transaction` :
 * renommer un technicien doit suivre ses interventions, et le retirer doit
 * couper le lien sans effacer le nom — une tournée de mars doit continuer de
 * dire qui l'a faite.
 */
@Dao
abstract class TechnicienDao {

    @Query("SELECT * FROM techniciens")
    abstract fun observerTous(): Flow<List<Technicien>>

    @Query("SELECT * FROM techniciens")
    abstract suspend fun tous(): List<Technicien>

    @Query("SELECT * FROM techniciens WHERE nom = :nom COLLATE NOCASE LIMIT 1")
    abstract suspend fun trouverParNom(nom: String): Technicien?

    @Upsert
    abstract suspend fun enregistrer(technicien: Technicien)

    @Upsert
    abstract suspend fun enregistrerTous(techniciens: List<Technicien>)

    @Query("UPDATE interventions SET technicienNom = :nom WHERE technicienId = :id")
    abstract suspend fun propagerNom(id: String, nom: String)

    @Query("UPDATE interventions SET technicienId = NULL WHERE technicienId = :id")
    abstract suspend fun detacher(id: String)

    @Query("DELETE FROM techniciens WHERE id = :id")
    abstract suspend fun effacer(id: String)

    @Transaction
    open suspend fun renommer(technicien: Technicien) {
        enregistrer(technicien)
        propagerNom(technicien.id, technicien.nom)
    }

    /** Le lien est coupé, le nom reste. */
    @Transaction
    open suspend fun supprimer(id: String) {
        detacher(id)
        effacer(id)
    }
}

/**
 * Accès SQL au catalogue de prestations.
 *
 * Une seule table : une prestation n'est référencée nulle part une fois la
 * ligne de devis créée — celle-ci en recopie l'intitulé et le prix, comme
 * partout ailleurs dans le projet. Retirer une prestation du catalogue ne doit
 * pas changer un devis déjà envoyé.
 */
@Dao
interface PrestationDao {

    @Query("SELECT * FROM prestations ORDER BY rang ASC")
    fun observerToutes(): Flow<List<Prestation>>

    @Query("SELECT * FROM prestations")
    suspend fun toutes(): List<Prestation>

    /** Le rang libre suivant, pour poser une prestation à la fin de sa famille. */
    @Query("SELECT COALESCE(MAX(rang), 0) + 1 FROM prestations")
    suspend fun prochainRang(): Int

    @Upsert
    suspend fun enregistrer(prestation: Prestation)

    @Upsert
    suspend fun enregistrerToutes(prestations: List<Prestation>)

    @Query("DELETE FROM prestations WHERE id = :id")
    suspend fun effacer(id: String)
}
