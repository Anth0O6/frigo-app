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
        VerificationFluide::class,
        Trajet::class,
        Facture::class,
        LigneFacture::class,
        Fournisseur::class,
        Article::class,
        Stock::class,
        PalierPrestation::class,
        GammeMaintenance::class,
        PointGamme::class,
        AffectationGamme::class,
        ReleveGamme::class,
    ],
    version = 22,
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

    abstract fun verificationFluideDao(): VerificationFluideDao

    abstract fun factureDao(): FactureDao

    abstract fun materielDao(): MaterielDao

    abstract fun maintenanceDao(): MaintenanceDao

    companion object {

        const val NOM = "frigopro.db"

        /**
         * Les catalogues livrés, garnis à la création de la base.
         *
         * [MIGRATION_7_8] pose les prestations et [MIGRATION_19_20] les
         * accessoires, pour un téléphone déjà garni de tournées ; une
         * installation neuve ne passe par aucune migration et repartirait de
         * catalogues vides. Les deux chemins partagent donc le même SQL — voir
         * [SQL_CATALOGUE_INITIAL] et [poserCatalogueAccessoires].
         *
         * `onCreate` ne s'exécute qu'une fois, à la création du fichier : rien
         * n'écrase ensuite un prix que le technicien aurait posé.
         */
        private val CATALOGUES_AU_PREMIER_LANCEMENT = object : Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_CATALOGUE_INITIAL)
                poserCatalogueAccessoires(db)
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
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                )
                .addCallback(CATALOGUES_AU_PREMIER_LANCEMENT)
                .build()
    }
}
