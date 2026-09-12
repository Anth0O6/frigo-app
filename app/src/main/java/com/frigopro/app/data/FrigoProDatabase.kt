package com.frigopro.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

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
        Technicien::class,
        PointChecklist::class,
        Prestation::class,
    ],
    version = 8,
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

    abstract fun technicienDao(): TechnicienDao

    abstract fun prestationDao(): PrestationDao

    companion object {

        const val NOM = "frigopro.db"

        /**
         * Le catalogue de prestations, garni à la création de la base.
         *
         * [MIGRATION_7_8] le pose pour un téléphone déjà garni de tournées ;
         * une installation neuve ne passe par aucune migration et repartirait
         * d'un catalogue vide. Les deux chemins partagent donc le même SQL —
         * voir [SQL_CATALOGUE_INITIAL].
         *
         * `onCreate` ne s'exécute qu'une fois, à la création du fichier : rien
         * n'écrase ensuite un prix que le technicien aurait posé.
         */
        private val CATALOGUE_AU_PREMIER_LANCEMENT = object : Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_CATALOGUE_INITIAL)
            }
        }

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
                    MIGRATION_7_8,
                )
                .addCallback(CATALOGUE_AU_PREMIER_LANCEMENT)
                .build()
    }
}
