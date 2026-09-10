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
