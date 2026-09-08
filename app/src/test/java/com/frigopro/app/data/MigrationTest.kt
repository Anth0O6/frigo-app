package com.frigopro.app.data

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * La migration est ce qui sépare une mise à jour d'une perte de données chez
 * un client. Ce test recrée une base telle que la version 1 l'écrivait, puis
 * l'ouvre par [FrigoProDatabase.creer] : la migration est donc vérifiée dans
 * les conditions réelles, y compris son enregistrement auprès du constructeur
 * — l'oubli d'`addMigrations` ne serait pas rattrapé autrement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val contexte: Context = RuntimeEnvironment.getApplication()

    @After
    fun effacerLaBase() {
        contexte.deleteDatabase(FrigoProDatabase.NOM)
    }

    @Test
    fun `la migration 1 vers 2 conserve les interventions et pose les valeurs par defaut`() {
        contexte.deleteDatabase(FrigoProDatabase.NOM)
        creerBaseVersion1()

        val base = FrigoProDatabase.creer(contexte)
        // L'ouverture déclenche la migration puis la validation du schéma :
        // une migration incohérente ferait échouer cette ligne.
        val db = base.openHelper.writableDatabase

        db.query("SELECT `client`, `ville`, `heure`, `statut`, `notes` FROM `interventions`").use { curseur ->
            assertEquals("l'intervention d'origine doit survivre, seule", 1, curseur.count)
            assertTrue(curseur.moveToFirst())
            assertEquals("Boucherie Lemoine", curseur.getString(0))
            assertEquals("Rouen", curseur.getString(1))
            assertEquals("08:30", curseur.getString(2))
            assertEquals("une intervention d'avant le suivi reste à faire", "A_FAIRE", curseur.getString(3))
            assertEquals("", curseur.getString(4))
        }
        assertEquals(2, db.version)

        base.close()
    }

    @Test
    fun `une base neuve s'ouvre directement en version 2`() {
        contexte.deleteDatabase(FrigoProDatabase.NOM)

        val base = FrigoProDatabase.creer(contexte)
        val db = base.openHelper.writableDatabase

        assertEquals(2, db.version)
        db.query("SELECT `statut`, `notes` FROM `interventions`").use { curseur ->
            assertEquals(0, curseur.count)
        }

        base.close()
    }

    /**
     * DDL repris mot pour mot du schéma exporté `app/schemas/…/1.json`, empreinte
     * d'identité comprise : sans la bonne valeur dans `room_master_table`, Room
     * refuserait la base au lieu de la migrer, et le test ne prouverait rien.
     */
    private fun creerBaseVersion1() {
        contexte.openOrCreateDatabase(FrigoProDatabase.NOM, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `interventions` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                    "`heure` TEXT NOT NULL, `client` TEXT NOT NULL, `ville` TEXT NOT NULL, " +
                    "`typePanne` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_interventions_date` ON `interventions` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(EMPREINTE_VERSION_1),
            )
            db.execSQL(
                "INSERT INTO `interventions` (`id`, `date`, `heure`, `client`, `ville`, `typePanne`, `modifieLe`) " +
                    "VALUES ('id-1', '2026-03-09', '08:30', 'Boucherie Lemoine', 'Rouen', 'FUITE_FLUIDE', 0)",
            )
            db.version = 1
        }
    }

    private companion object {

        /** Empreinte de la version 1, telle qu'exportée dans `app/schemas`. */
        const val EMPREINTE_VERSION_1 = "576bb93c8e6bdad21224e8d0898547f0"
    }
}
