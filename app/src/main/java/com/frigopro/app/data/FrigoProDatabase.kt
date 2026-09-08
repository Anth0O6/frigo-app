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
    entities = [Intervention::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Convertisseurs::class)
abstract class FrigoProDatabase : RoomDatabase() {

    abstract fun interventionDao(): InterventionDao

    companion object {

        fun creer(contexte: Context): FrigoProDatabase =
            Room.databaseBuilder(
                contexte.applicationContext,
                FrigoProDatabase::class.java,
                "frigopro.db",
            ).build()
    }
}
