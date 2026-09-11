package com.frigopro.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base locale de l'application.
 *
 * Les schémas sont exportés (`room.schemaLocation` dans `app/build.gradle.kts`)
 * : toute évolution de [Intervention] devra s'accompagner d'une migration
 * explicite, sous peine de perdre les tournées déjà saisies.
 */
@Database(
    entities = [
        Intervention::class,
        Client::class,
        TypeIntervention::class,
        Equipement::class,
        Photo::class,
        Releve::class,
        MouvementFluide::class,
        PiecePosee::class,
        Devis::class,
        LigneDevis::class,
        Parametres::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Convertisseurs::class)
abstract class FrigoProDatabase : RoomDatabase() {

    abstract fun interventionDao(): InterventionDao

    abstract fun clientDao(): ClientDao

    abstract fun typeInterventionDao(): TypeInterventionDao

    abstract fun equipementDao(): EquipementDao

    abstract fun suiviDao(): SuiviDao

    abstract fun devisDao(): DevisDao

    abstract fun parametresDao(): ParametresDao

    companion object {

        const val NOM = "frigopro.db"

        fun creer(contexte: Context): FrigoProDatabase =
            Room.databaseBuilder(
                contexte.applicationContext,
                FrigoProDatabase::class.java,
                NOM,
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                .build()
    }
}
