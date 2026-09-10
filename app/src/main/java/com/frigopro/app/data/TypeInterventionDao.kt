package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès SQL aux types d'intervention.
 *
 * Classe abstraite plutôt qu'interface : `@Transaction` a besoin d'une méthode
 * avec un corps pour enchaîner deux écritures sans laisser la base entre les
 * deux dans un état où un type serait renommé mais pas les interventions qui
 * l'emploient.
 *
 * Ce DAO touche aussi la table `interventions` : c'est le prix de la
 * propagation, et le seul endroit d'où elle puisse être atomique.
 */
@Dao
abstract class TypeInterventionDao {

    /** Sans `ORDER BY` : le tri alphabétique se fait côté Kotlin, accents repliés. */
    @Query("SELECT * FROM types_intervention")
    abstract fun observerTous(): Flow<List<TypeIntervention>>

    /** Toute la liste, pour la sauvegarde. */
    @Query("SELECT * FROM types_intervention")
    abstract suspend fun tous(): List<TypeIntervention>

    /**
     * `COLLATE NOCASE` : « Entretien » et « entretien » sont le même type. Deux
     * intitulés qui ne diffèrent que par un accent en sont deux différents.
     */
    @Query("SELECT * FROM types_intervention WHERE libelle = :libelle COLLATE NOCASE LIMIT 1")
    abstract suspend fun trouverParLibelle(libelle: String): TypeIntervention?

    @Upsert
    abstract suspend fun enregistrer(type: TypeIntervention)

    @Upsert
    abstract suspend fun enregistrerTous(types: List<TypeIntervention>)

    @Query("UPDATE interventions SET typeLibelle = :libelle WHERE typeId = :id")
    abstract suspend fun propagerLibelle(id: String, libelle: String)

    @Query("DELETE FROM types_intervention WHERE id = :id")
    abstract suspend fun effacer(id: String)

    /**
     * Le lien est coupé, l'intitulé reste : une intervention passée continue
     * d'afficher ce qu'elle affichait, et ne désigne plus une ligne absente.
     */
    @Query("UPDATE interventions SET typeId = NULL WHERE typeId = :id")
    abstract suspend fun detacher(id: String)

    /** Enregistre le type et répercute son intitulé sur les interventions. */
    @Transaction
    open suspend fun renommer(type: TypeIntervention) {
        enregistrer(type)
        propagerLibelle(type.id, type.libelle)
    }

    @Transaction
    open suspend fun supprimer(id: String) {
        detacher(id)
        effacer(id)
    }
}
