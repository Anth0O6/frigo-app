package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Accès SQL aux interventions. Room réémet les `Flow` à chaque écriture. */
@Dao
interface InterventionDao {

    @Query("SELECT * FROM interventions WHERE date = :date ORDER BY heure ASC")
    fun observerJournee(date: LocalDate): Flow<List<Intervention>>

    /**
     * Historique d'une machine, du plus récent au plus ancien : « qu'a-t-on déjà
     * fait sur celle-ci ? » se lit de haut en bas.
     */
    @Query("SELECT * FROM interventions WHERE equipementId = :equipementId ORDER BY date DESC, heure DESC")
    fun observerParEquipement(equipementId: String): Flow<List<Intervention>>

    /** Une intervention suivie par son identifiant : ce qu'observe son écran. */
    @Query("SELECT * FROM interventions WHERE id = :id")
    fun observer(id: String): Flow<Intervention?>

    /**
     * Les interventions d'une période, pour le planning de la semaine. Les
     * dates étant stockées en texte de largeur fixe, `BETWEEN` les compare
     * correctement — c'est tout l'intérêt du format retenu.
     */
    @Query("SELECT * FROM interventions WHERE date BETWEEN :debut AND :fin ORDER BY date ASC, heure ASC")
    fun observerPeriode(debut: LocalDate, fin: LocalDate): Flow<List<Intervention>>

    /** Les numéros déjà attribués, pour que le suivant ne les répète pas. */
    @Query("SELECT numero FROM interventions WHERE numero <> ''")
    suspend fun numerosAttribues(): List<String>

    /** Toutes les interventions, pour la sauvegarde. Aucun tri : le fichier n'en demande pas. */
    @Query("SELECT * FROM interventions")
    suspend fun toutes(): List<Intervention>

    @Upsert
    suspend fun enregistrer(intervention: Intervention)

    /** Room enveloppe une écriture multiple dans une transaction : tout ou rien. */
    @Upsert
    suspend fun enregistrerToutes(interventions: List<Intervention>)

    @Query("DELETE FROM interventions WHERE id = :id")
    suspend fun supprimer(id: String)
}
