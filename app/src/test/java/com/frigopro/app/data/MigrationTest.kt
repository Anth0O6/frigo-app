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

    /**
     * Le parc arrive vide, et c'est exact : les interventions déjà saisies ne
     * pouvaient désigner aucune machine. Ce qu'elles portaient doit en revanche
     * traverser l'ajout des deux colonnes sans une égratignure.
     */
    @Test
    fun `une base version 5 recoit le parc de machines sans rien perdre`() {
        creerBase(
            version = 5,
            empreinte = EMPREINTE_V5,
            ddl = listOf(DDL_INTERVENTIONS_V5, DDL_CLIENTS_V5, DDL_TYPES_V5, DDL_INDEX_DATE),
            insertions = listOf(
                "INSERT INTO `types_intervention` (`id`, `libelle`, `modifieLe`) " +
                    "VALUES ('typ-1', 'Entretien préventif', 7)",
                "INSERT INTO `clients` (`id`, `nom`, `ville`, `adresse`, `telephone`, `modifieLe`) " +
                    "VALUES ('cli-1', 'Primeur Vasseur', 'Elbeuf', '3 place du Marché', '0235000000', 7)",
                "INSERT INTO `interventions` " +
                    "(`id`, `date`, `heure`, `client`, `ville`, `typeId`, `typeLibelle`, " +
                    "`clientId`, `statut`, `notes`, `modifieLe`) " +
                    "VALUES ('id-1', '2026-03-09', '09:15', 'Primeur Vasseur', 'Elbeuf', " +
                    "'typ-1', 'Entretien préventif', 'cli-1', 'EN_COURS', 'Groupe revu.', 7)",
            ),
        )

        val db = ouvrirEtMigrer()

        db.query(
            "SELECT `typeId`, `typeLibelle`, `clientId`, `equipementId`, `equipementNom`, " +
                "`notes`, `modifieLe` FROM `interventions`",
        ).use { curseur ->
            assertEquals(1, curseur.count)
            assertTrue(curseur.moveToFirst())
            assertEquals("le lien vers le type doit survivre", "typ-1", curseur.getString(0))
            assertEquals("Entretien préventif", curseur.getString(1))
            assertEquals("cli-1", curseur.getString(2))
            assertTrue("le parc n'existait pas : aucune machine désignée", curseur.isNull(3))
            assertEquals("et donc aucun nom à afficher", "", curseur.getString(4))
            assertEquals("Groupe revu.", curseur.getString(5))
            assertEquals("l'horodatage ne bouge pas", 7L, curseur.getLong(6))
        }
        db.query("SELECT * FROM `equipements`").use { curseur ->
            assertEquals("le parc arrive vide", 0, curseur.count)
        }
        db.query("SELECT * FROM `photos`").use { curseur ->
            assertEquals(0, curseur.count)
        }
        db.query("SELECT `libelle` FROM `types_intervention`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("la liste des types n'est pas touchée", "Entretien préventif", curseur.getString(0))
        }
        assertEquals(VERSION_COURANTE, db.version)
    }

    /**
     * Room refuse d'ouvrir une base dont le schéma ne correspond pas : l'index
     * des deux tables neuves est donc aussi obligatoire que les tables.
     */
    @Test
    fun `les tables du parc arrivent avec leurs index`() {
        creerBase(
            version = 5,
            empreinte = EMPREINTE_V5,
            ddl = listOf(DDL_INTERVENTIONS_V5, DDL_CLIENTS_V5, DDL_TYPES_V5, DDL_INDEX_DATE),
            insertions = emptyList(),
        )

        val db = ouvrirEtMigrer()

        val index = buildList {
            db.query("SELECT `name` FROM `sqlite_master` WHERE `type` = 'index'").use { curseur ->
                while (curseur.moveToNext()) add(curseur.getString(0))
            }
        }
        assertTrue("index_equipements_clientId attendu, trouvé $index", index.contains("index_equipements_clientId"))
        assertTrue("index_photos_equipementId attendu, trouvé $index", index.contains("index_photos_equipementId"))
    }

    @Test
    fun `une base neuve s'ouvre directement en version courante`() {
        val db = ouvrirEtMigrer()

        assertEquals(VERSION_COURANTE, db.version)
        db.query("SELECT `typeId`, `typeLibelle` FROM `interventions`").use { assertEquals(0, it.count) }
        db.query("SELECT `equipementId`, `equipementNom` FROM `interventions`").use { assertEquals(0, it.count) }
        db.query("SELECT `nom`, `ville`, `adresse`, `telephone` FROM `clients`").use { assertEquals(0, it.count) }
        db.query("SELECT `libelle` FROM `types_intervention`").use { assertEquals(0, it.count) }
        db.query("SELECT `clientId`, `nom` FROM `equipements`").use { assertEquals(0, it.count) }
        db.query("SELECT `equipementId`, `categorie`, `fichier` FROM `photos`").use { assertEquals(0, it.count) }
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

    /**
     * Le cas le plus délicat du projet : la table des photos est **détruite et
     * reconstruite** pour que `equipementId` puisse devenir nullable, SQLite ne
     * sachant pas relâcher un `NOT NULL` par `ALTER TABLE`.
     *
     * Deux choses doivent survivre à l'opération — les photos déjà prises, et
     * les index, qui suivent la table détruite. Room refuse d'ouvrir une base
     * dont un index manque, si bien que l'ouverture par [FrigoProDatabase.creer]
     * vérifie le second point à elle seule.
     */
    @Test
    fun `une base version 6 recoit le suivi d'intervention sans perdre ses photos`() {
        creerBase(
            version = 6,
            empreinte = EMPREINTE_V6,
            ddl = listOf(
                DDL_INTERVENTIONS_V6,
                DDL_INDEX_DATE,
                DDL_CLIENTS_V6,
                DDL_TYPES_V6,
                DDL_EQUIPEMENTS_V6,
                DDL_INDEX_EQUIPEMENTS_CLIENT,
                DDL_PHOTOS_V6,
                DDL_INDEX_PHOTOS_EQUIPEMENT,
            ),
            insertions = listOf(
                "INSERT INTO `interventions` " +
                    "(`id`, `date`, `heure`, `client`, `ville`, `typeId`, `typeLibelle`, " +
                    "`clientId`, `equipementId`, `equipementNom`, `statut`, `notes`, `modifieLe`) " +
                    "VALUES ('id-1', '2026-03-09', '08:30', 'Boucherie Lemoine', 'Rouen', " +
                    "NULL, 'Fuite de fluide', 'cl-1', 'eq-1', 'Vitrine salle 2', 'A_FAIRE', '', 0)",
                "INSERT INTO `equipements` (`id`, `clientId`, `nom`, `modifieLe`) " +
                    "VALUES ('eq-1', 'cl-1', 'Vitrine salle 2', 0)",
                "INSERT INTO `photos` (`id`, `equipementId`, `categorie`, `fichier`, `priseLe`) " +
                    "VALUES ('ph-1', 'eq-1', 'PLAQUE', 'plaque.jpg', 1700000000000)",
            ),
        )

        val db = ouvrirEtMigrer()

        db.query(
            "SELECT `equipementId`, `interventionId`, `categorie`, `fichier`, `legende`, `priseLe` " +
                "FROM `photos`",
        ).use { curseur ->
            assertEquals("la photo doit survivre à la reconstruction", 1, curseur.count)
            assertTrue(curseur.moveToFirst())
            assertEquals("eq-1", curseur.getString(0))
            assertTrue("elle n'appartient à aucune intervention", curseur.isNull(1))
            assertEquals("PLAQUE", curseur.getString(2))
            assertEquals("plaque.jpg", curseur.getString(3))
            assertEquals("", curseur.getString(4))
            assertEquals(1700000000000L, curseur.getLong(5))
        }

        // Le chronomètre part à zéro, ce qui décrit exactement l'état d'avant :
        // aucune intervention n'avait été chronométrée.
        db.query(
            "SELECT `urgente`, `arriveeLe`, `demarreLe`, `cumuleS`, `numero` FROM `interventions`",
        ).use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals(0, curseur.getInt(0))
            assertTrue(curseur.isNull(1))
            assertTrue(curseur.isNull(2))
            assertEquals(0L, curseur.getLong(3))
            assertEquals("", curseur.getString(4))
        }

        // La machine reçoit ses champs de plaque, vides faute de les connaître.
        db.query("SELECT `marque`, `fluide`, `chargeKg` FROM `equipements`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("", curseur.getString(0))
            assertEquals("", curseur.getString(1))
            assertTrue(curseur.isNull(2))
        }

        // La ligne unique des réglages doit exister dès la migration passée.
        db.query("SELECT `themeSombre`, `tauxTva` FROM `parametres`").use { curseur ->
            assertEquals(1, curseur.count)
            assertTrue(curseur.moveToFirst())
            assertEquals("sombre par défaut", 1, curseur.getInt(0))
            assertEquals(20.0, curseur.getDouble(1), 0.001)
        }

        assertEquals(VERSION_COURANTE, db.version)
    }

    private companion object {

        const val VERSION_COURANTE = 7

        /** Empreintes et DDL repris mot pour mot des schémas exportés dans `app/schemas`. */
        const val EMPREINTE_V1 = "576bb93c8e6bdad21224e8d0898547f0"
        const val EMPREINTE_V2 = "fdbe212c83828e74d0d0625614cce133"
        const val EMPREINTE_V3 = "5b3449dffc8764d2688a1d5b0190a516"
        const val EMPREINTE_V4 = "670f8966c393d071075ec34e7240726e"
        const val EMPREINTE_V5 = "2fea87f26cb4e3f690878aad5160618d"
        const val EMPREINTE_V6 = "3693966e2dd927510a3eecb627dbd8e6"

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

        const val DDL_INTERVENTIONS_V5 =
            "CREATE TABLE IF NOT EXISTS `interventions` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`heure` TEXT NOT NULL, `client` TEXT NOT NULL, `ville` TEXT NOT NULL, " +
                "`typeId` TEXT, `typeLibelle` TEXT NOT NULL, `clientId` TEXT, " +
                "`statut` TEXT NOT NULL, `notes` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"

        const val DDL_CLIENTS_V5 = DDL_CLIENTS_V4

        const val DDL_TYPES_V5 =
            "CREATE TABLE IF NOT EXISTS `types_intervention` (`id` TEXT NOT NULL, " +
                "`libelle` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val DDL_INTERVENTIONS_V6 =
            "CREATE TABLE IF NOT EXISTS `interventions` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`heure` TEXT NOT NULL, `client` TEXT NOT NULL, `ville` TEXT NOT NULL, " +
                "`typeId` TEXT, `typeLibelle` TEXT NOT NULL, `clientId` TEXT, " +
                "`equipementId` TEXT, `equipementNom` TEXT NOT NULL, `statut` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val DDL_CLIENTS_V6 = DDL_CLIENTS_V4

        const val DDL_TYPES_V6 = DDL_TYPES_V5

        const val DDL_EQUIPEMENTS_V6 =
            "CREATE TABLE IF NOT EXISTS `equipements` (`id` TEXT NOT NULL, " +
                "`clientId` TEXT NOT NULL, `nom` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"

        const val DDL_INDEX_EQUIPEMENTS_CLIENT =
            "CREATE INDEX IF NOT EXISTS `index_equipements_clientId` ON `equipements` (`clientId`)"

        /** `equipementId` y est encore `NOT NULL` : c'est ce que la migration relâche. */
        const val DDL_PHOTOS_V6 =
            "CREATE TABLE IF NOT EXISTS `photos` (`id` TEXT NOT NULL, " +
                "`equipementId` TEXT NOT NULL, `categorie` TEXT NOT NULL, " +
                "`fichier` TEXT NOT NULL, `priseLe` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val DDL_INDEX_PHOTOS_EQUIPEMENT =
            "CREATE INDEX IF NOT EXISTS `index_photos_equipementId` ON `photos` (`equipementId`)"

        const val DDL_INDEX_DATE =
            "CREATE INDEX IF NOT EXISTS `index_interventions_date` ON `interventions` (`date`)"
    }
}
