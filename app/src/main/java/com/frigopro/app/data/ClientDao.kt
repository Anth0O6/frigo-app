package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Accès SQL au carnet de clients. */
@Dao
interface ClientDao {

    /**
     * Sans `ORDER BY` : le tri alphabétique se fait côté Kotlin, `COLLATE
     * NOCASE` de SQLite ne repliant pas les accents (« Élise » finirait après
     * « Zoé »).
     */
    @Query("SELECT * FROM clients")
    fun observerTous(): Flow<List<Client>>

    /**
     * `COLLATE NOCASE` suffit ici : on cherche une correspondance exacte à la
     * casse près. Deux noms qui ne diffèrent que par un accent sont bien deux
     * clients différents.
     */
    @Query("SELECT * FROM clients WHERE nom = :nom COLLATE NOCASE LIMIT 1")
    suspend fun trouverParNom(nom: String): Client?

    /** Tout le carnet, pour la sauvegarde. Le tri est l'affaire du dépôt. */
    @Query("SELECT * FROM clients")
    suspend fun tous(): List<Client>

    @Upsert
    suspend fun enregistrer(client: Client)

    /** Room enveloppe une écriture multiple dans une transaction : tout ou rien. */
    @Upsert
    suspend fun enregistrerTous(clients: List<Client>)
}
