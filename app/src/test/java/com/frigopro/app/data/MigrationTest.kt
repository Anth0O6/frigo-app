package com.frigopro.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Les migrations séparent une mise à jour d'une perte de données chez un
 * client. Chaque cas recrée une base telle que la version visée l'écrivait —
 * empreinte d'identité comprise — puis l'ouvre par [FrigoProDatabase.creer].
 *
 * Passer par `creer` plutôt que par un constructeur de test vérifie aussi que
 * les migrations sont **enregistrées** auprès du constructeur : un oubli
 * d'`addMigrations` planterait chez l'utilisateur sans qu'un test exécutant
 * seulement le SQL le voie.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val contexte: Context = RuntimeEnvironment.getApplication()

    private var base: FrigoProDatabase? = null

    @Before
    fun partirDeRien() {
        contexte.deleteDatabase(FrigoProDatabase.NOM)
    }

    /**
     * Fermer la base ouverte par le test : sans cela, Robolectric signale une
     * ressource SQLite abandonnée — et le verrou sur le fichier peut survivre
     * au test suivant, qui échouerait alors pour une raison sans rapport.
     */
    @After
    fun effacerLaBase() {
        base?.close()
        base = null
        contexte.deleteDatabase(FrigoProDatabase.NOM)
    }

    @Test
    fun `une base version 1 se migre jusqu'a la version courante sans rien perdre`() {
        creerBase(
            version = 1,
            empreinte = EMPREINTE_V1,
            ddl = listOf(DDL_INTERVENTIONS_V1, DDL_INDEX_DATE),
            insertions = listOf(
                "INSERT INTO `interventions` " +
                    "(`id`, `date`, `heure`, `client`, `ville`, `typePanne`, `modifieLe`) " +
                    "VALUES ('id-1', '2026-03-09', '08:30', 'Boucherie Lemoine', 'Rouen', 'FUITE_FLUIDE', 0)",
            ),
        )

        val db = ouvrirEtMigrer()

        db.query(
            "SELECT `client`, `heure`, `statut`, `notes`, `clientId` FROM `interventions`",
        ).use { curseur ->
            assertEquals(1, curseur.count)
            assertTrue(curseur.moveToFirst())
            assertEquals("Boucherie Lemoine", curseur.getString(0))
            assertEquals("08:30", curseur.getString(1))
            assertEquals("une intervention d'avant le suivi reste à faire", "A_FAIRE", curseur.getString(2))
            assertEquals("", curseur.getString(3))
            assertTrue("elle n'était rattachée à aucun client", curseur.isNull(4))
        }
        assertEquals(VERSION_COURANTE, db.version)
    }

    @Test
    fun `une base version 2 recoit le carnet sans rien perdre`() {
        creerBase(
            version = 2,
            empreinte = EMPREINTE_V2,
            ddl = listOf(DDL_INTERVENTIONS_V2, DDL_INDEX_DATE),
            insertions = listOf(
                "INSERT INTO `interventions` " +
                    "(`id`, `date`, `heure`, `client`, `ville`, `typePanne`, `statut`, `notes`, `modifieLe`) " +
                    "VALUES ('id-1', '2026-03-09', '14:00', 'Traiteur Delaunay', 'Barentin', " +
                    "'GIVRAGE', 'TERMINEE', 'Dégivrage complet.', 0)",
            ),
        )

        val db = ouvrirEtMigrer()

        db.query("SELECT `statut`, `notes`, `clientId` FROM `interventions`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("le suivi déjà saisi doit survivre", "TERMINEE", curseur.getString(0))
            assertEquals("Dégivrage complet.", curseur.getString(1))
            assertTrue("le carnet n'existait pas encore", curseur.isNull(2))
        }
        db.query("SELECT * FROM `clients`").use { curseur ->
            assertEquals("le carnet arrive vide", 0, curseur.count)
        }
        assertEquals(VERSION_COURANTE, db.version)
    }

    @Test
    fun `une base version 3 recoit adresse et telephone sans rien perdre`() {
        creerBase(
            version = 3,
            empreinte = EMPREINTE_V3,
            ddl = listOf(DDL_INTERVENTIONS_V3, DDL_CLIENTS_V3, DDL_INDEX_DATE),
            insertions = listOf(
                "INSERT INTO `clients` (`id`, `nom`, `ville`, `modifieLe`) " +
                    "VALUES ('cli-1', 'Primeur Vasseur', 'Elbeuf', 0)",
                "INSERT INTO `interventions` " +
                    "(`id`, `date`, `heure`, `client`, `ville`, `typePanne`, `clientId`, " +
                    "`statut`, `notes`, `modifieLe`) " +
                    "VALUES ('id-1', '2026-03-09', '09:15', 'Primeur Vasseur', 'Elbeuf', " +
                    "'COMPRESSEUR', 'cli-1', 'EN_COURS', 'Compresseur bruyant.', 0)",
            ),
        )

        val db = ouvrirEtMigrer()

        db.query("SELECT `nom`, `ville`, `adresse`, `telephone` FROM `clients`").use { curseur ->
            assertEquals(1, curseur.count)
            assertTrue(curseur.moveToFirst())
            assertEquals("Primeur Vasseur", curseur.getString(0))
            assertEquals("Elbeuf", curseur.getString(1))
            assertEquals("un client inscrit depuis une intervention arrive sans adresse", "", curseur.getString(2))
            assertEquals("", curseur.getString(3))
        }
        db.query("SELECT `clientId`, `statut` FROM `interventions`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("le rattachement au carnet doit survivre", "cli-1", curseur.getString(0))
            assertEquals("EN_COURS", curseur.getString(1))
        }
        assertEquals(VERSION_COURANTE, db.version)
    }

    /**
     * Le cas le plus délicat de la série : la table des interventions est
     * reconstruite, et les types figés d'hier doivent ressortir en français.
     */
    @Test
    fun `une base version 4 traduit ses types de panne en intitules`() {
        creerBase(
            version = 4,
            empreinte = EMPREINTE_V4,
            ddl = listOf(DDL_INTERVENTIONS_V4, DDL_CLIENTS_V4, DDL_INDEX_DATE),
            insertions = listOf(
                "INSERT INTO `clients` (`id`, `nom`, `ville`, `adresse`, `telephone`, `modifieLe`) " +
                    "VALUES ('cli-1', 'Primeur Vasseur', 'Elbeuf', '3 place du Marché', '0235000000', 7)",
                "INSERT INTO `interventions` " +
                    "(`id`, `date`, `heure`, `client`, `ville`, `typePanne`, `clientId`, " +
                    "`statut`, `notes`, `modifieLe`) " +
                    "VALUES ('id-1', '2026-03-09', '09:15', 'Primeur Vasseur', 'Elbeuf', " +
                    "'ENTRETIEN', 'cli-1', 'EN_COURS', 'Entretien du groupe.', 7)",
                "INSERT INTO `interventions` " +
                    "(`id`, `date`, `heure`, `client`, `ville`, `typePanne`, `clientId`, " +
                    "`statut`, `notes`, `modifieLe`) " +
                    "VALUES ('id-2', '2026-03-10', '11:00', 'Client de passage', 'Rouen', " +
                    "'FUITE_FLUIDE', NULL, 'A_FAIRE', '', 7)",
            ),
        )

        val db = ouvrirEtMigrer()

        db.query(
            "SELECT `id`, `typeId`, `typeLibelle`, `clientId`, `statut`, `notes`, `modifieLe` " +
                "FROM `interventions` ORDER BY `id`",
        ).use { curseur ->
            assertEquals(2, curseur.count)

            assertTrue(curseur.moveToFirst())
            assertEquals("id-1", curseur.getString(0))
            assertTrue("la liste démarre vide : aucun lien possible", curseur.isNull(1))
            assertEquals("Entretien préventif", curseur.getString(2))
            assertEquals("le rattachement au carnet doit survivre", "cli-1", curseur.getString(3))
            assertEquals("EN_COURS", curseur.getString(4))
            assertEquals("Entretien du groupe.", curseur.getString(5))
            assertEquals("l'horodatage doit traverser la reconstruction", 7L, curseur.getLong(6))

            assertTrue(curseur.moveToNext())
            assertEquals("Fuite de fluide", curseur.getString(2))
            assertTrue(curseur.isNull(3))
        }
        db.query("SELECT * FROM `types_intervention`").use { curseur ->
            assertEquals("la liste des types arrive vide", 0, curseur.count)
        }
        db.query("SELECT `nom`, `adresse` FROM `clients`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("le carnet n'est pas touché", "Primeur Vasseur", curseur.getString(0))
            assertEquals("3 place du Marché", curseur.getString(1))
        }
        assertEquals(VERSION_COURANTE, db.version)
    }

    /**
     * La reconstruction détruit la table : sans reposer l'index, le
     * `WHERE date = :date` du DAO repartirait en parcours complet, et Room
     * refuserait d'ouvrir une base dont le schéma ne correspond plus.
     */
    @Test
    fun `l'index sur la date survit a la reconstruction`() {
        creerBase(
            version = 4,
            empreinte = EMPREINTE_V4,
            ddl = listOf(DDL_INTERVENTIONS_V4, DDL_CLIENTS_V4, DDL_INDEX_DATE),
            insertions = emptyList(),
        )

        val db = ouvrirEtMigrer()

        db.query(
            "SELECT `name` FROM `sqlite_master` WHERE `type` = 'index' AND `tbl_name` = 'interventions'",
        ).use { curseur ->
            val noms = buildList {
                while (curseur.moveToNext()) add(curseur.getString(0))
            }
            assertTrue("index_interventions_date attendu, trouvé $noms", noms.contains("index_interventions_date"))
        }
    }

    @Test
    fun `une base neuve s'ouvre directement en version courante`() {
        val db = ouvrirEtMigrer()

        assertEquals(VERSION_COURANTE, db.version)
        db.query("SELECT `typeId`, `typeLibelle` FROM `interventions`").use { assertEquals(0, it.count) }
        db.query("SELECT `nom`, `ville`, `adresse`, `telephone` FROM `clients`").use { assertEquals(0, it.count) }
        db.query("SELECT `libelle` FROM `types_intervention`").use { assertEquals(0, it.count) }
    }

    /**
     * L'ouverture déclenche les migrations puis la validation du schéma : une
     * migration incohérente fait échouer cette ligne, pas une assertion.
     */
    private fun ouvrirEtMigrer(): SupportSQLiteDatabase {
        val ouverte = FrigoProDatabase.creer(contexte)
        base = ouverte
        return ouverte.openHelper.writableDatabase
    }

    private fun creerBase(version: Int, empreinte: String, ddl: List<String>, insertions: List<String>) {
        contexte.openOrCreateDatabase(FrigoProDatabase.NOM, Context.MODE_PRIVATE, null).use { db ->
            ddl.forEach { db.execSQL(it) }
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)", arrayOf(empreinte))
            insertions.forEach { db.execSQL(it) }
            db.version = version
        }
    }

    private companion object {

        const val VERSION_COURANTE = 5

        /** Empreintes et DDL repris mot pour mot des schémas exportés dans `app/schemas`. */
        const val EMPREINTE_V1 = "576bb93c8e6bdad21224e8d0898547f0"
        const val EMPREINTE_V2 = "fdbe212c83828e74d0d0625614cce133"
        const val EMPREINTE_V3 = "5b3449dffc8764d2688a1d5b0190a516"
        const val EMPREINTE_V4 = "670f8966c393d071075ec34e7240726e"

        const val DDL_INTERVENTIONS_V1 =
            "CREATE TABLE IF NOT EXISTS `interventions` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`heure` TEXT NOT NULL, `client` TEXT NOT NULL, `ville` TEXT NOT NULL, " +
                "`typePanne` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val DDL_INTERVENTIONS_V2 =
            "CREATE TABLE IF NOT EXISTS `interventions` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`heure` TEXT NOT NULL, `client` TEXT NOT NULL, `ville` TEXT NOT NULL, " +
                "`typePanne` TEXT NOT NULL, `statut` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val DDL_INTERVENTIONS_V3 =
            "CREATE TABLE IF NOT EXISTS `interventions` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`heure` TEXT NOT NULL, `client` TEXT NOT NULL, `ville` TEXT NOT NULL, " +
                "`typePanne` TEXT NOT NULL, `clientId` TEXT, `statut` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val DDL_CLIENTS_V3 =
            "CREATE TABLE IF NOT EXISTS `clients` (`id` TEXT NOT NULL, `nom` TEXT NOT NULL, " +
                "`ville` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val DDL_INTERVENTIONS_V4 = DDL_INTERVENTIONS_V3

        const val DDL_CLIENTS_V4 =
            "CREATE TABLE IF NOT EXISTS `clients` (`id` TEXT NOT NULL, `nom` TEXT NOT NULL, " +
                "`ville` TEXT NOT NULL, `adresse` TEXT NOT NULL, `telephone` TEXT NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val DDL_INDEX_DATE =
            "CREATE INDEX IF NOT EXISTS `index_interventions_date` ON `interventions` (`date`)"
    }
}
