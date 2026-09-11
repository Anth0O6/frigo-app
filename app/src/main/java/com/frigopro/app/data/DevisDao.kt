package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès SQL aux devis et à leurs lignes.
 *
 * Même raison que partout ailleurs de les réunir : supprimer un devis doit
 * emporter ses lignes, et un devis sans lignes ou des lignes sans devis n'ont
 * aucun sens.
 */
@Dao
abstract class DevisDao {

    /** Les devis, du plus récent au plus ancien. */
    @Query("SELECT * FROM devis ORDER BY creeLe DESC, modifieLe DESC")
    abstract fun observerTous(): Flow<List<Devis>>

    @Query("SELECT * FROM devis WHERE clientId = :clientId ORDER BY creeLe DESC")
    abstract fun observerDuClient(clientId: String): Flow<List<Devis>>

    @Query("SELECT * FROM devis WHERE id = :id")
    abstract fun observer(id: String): Flow<Devis?>

    @Query("SELECT * FROM devis")
    abstract suspend fun tous(): List<Devis>

    @Upsert
    abstract suspend fun enregistrer(devis: Devis)

    @Upsert
    abstract suspend fun enregistrerTous(devis: List<Devis>)

    @Query("SELECT * FROM lignes_devis WHERE devisId = :devisId ORDER BY rang ASC")
    abstract fun observerLignes(devisId: String): Flow<List<LigneDevis>>

    @Query("SELECT * FROM lignes_devis")
    abstract suspend fun toutesLesLignes(): List<LigneDevis>

    /** Le rang libre suivant, pour poser une ligne à la fin. */
    @Query("SELECT COALESCE(MAX(rang), -1) + 1 FROM lignes_devis WHERE devisId = :devisId")
    abstract suspend fun prochainRang(devisId: String): Int

    @Upsert
    abstract suspend fun enregistrerLigne(ligne: LigneDevis)

    @Upsert
    abstract suspend fun enregistrerLignes(lignes: List<LigneDevis>)

    @Query("DELETE FROM lignes_devis WHERE id = :id")
    abstract suspend fun effacerLigne(id: String)

    @Query("DELETE FROM lignes_devis WHERE devisId = :devisId")
    abstract suspend fun effacerLignesDe(devisId: String)

    @Query("DELETE FROM devis WHERE id = :id")
    abstract suspend fun effacer(id: String)

    @Transaction
    open suspend fun supprimer(id: String) {
        effacerLignesDe(id)
        effacer(id)
    }
}

/** Accès à la ligne unique des réglages. */
@Dao
interface ParametresDao {

    @Query("SELECT * FROM parametres WHERE id = ${Parametres.UNIQUE}")
    fun observer(): Flow<Parametres?>

    @Query("SELECT * FROM parametres WHERE id = ${Parametres.UNIQUE}")
    suspend fun lire(): Parametres?

    @Upsert
    suspend fun enregistrer(parametres: Parametres)
}
