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
            assertEquals("une intervention d'avant le suivi reste à faire", "PLANIFIEE", curseur.getString(2))
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
        db.query("SELECT `dureeMin`, `technicienId`, `technicienNom` FROM `interventions`").use {
            assertEquals(0, it.count)
        }
        db.query("SELECT `nom` FROM `techniciens`").use { assertEquals(0, it.count) }
        db.query("SELECT `libelle`, `fait`, `rang` FROM `points_checklist`").use { assertEquals(0, it.count) }
        // Une base neuve ne passe par aucune migration : c'est le `onCreate` de
        // [FrigoProDatabase] qui doit lui garnir le même catalogue, faute de quoi
        // un téléphone neuf et un téléphone mis à jour n'auraient pas la même
        // application.
        db.query("SELECT `designation`, `categorie` FROM `prestations`").use { assertEquals(21, it.count) }
        db.query("SELECT `parentId` FROM `equipements`").use { assertEquals(0, it.count) }
        db.query("SELECT `offerte` FROM `lignes_devis`").use { assertEquals(0, it.count) }
        db.query("SELECT `tvaOfferte`, `assujettiTva` FROM `devis`").use { assertEquals(0, it.count) }
        db.query("SELECT `fluide`, `verifieLe` FROM `verifications_fluide`").use { assertEquals(0, it.count) }
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

    /**
     * La version 8 renomme un statut et ajoute quatre choses : la durée, le
     * technicien, la checklist et le catalogue.
     *
     * Le renommage est le seul point qui puisse abîmer une tournée déjà saisie :
     * un `A_FAIRE` resté en base ferait échouer la lecture de l'énumération, et
     * l'intervention disparaîtrait de l'écran sans que rien ne le signale.
     */
    @Test
    fun `une base version 7 renomme ses statuts et recoit durees et technicien`() {
        creerBase(
            version = 7,
            empreinte = EMPREINTE_V7,
            ddl = DDL_V7,
            insertions = listOf(
                "INSERT INTO `interventions` " +
                    "(`id`, `date`, `heure`, `client`, `ville`, `typeId`, `typeLibelle`, " +
                    "`clientId`, `equipementId`, `equipementNom`, `statut`, `notes`, `urgente`, " +
                    "`numero`, `signatureFichier`, `signeeLe`, `modifieLe`, `arriveeLe`, " +
                    "`demarreLe`, `cumuleS`) " +
                    "VALUES ('id-1', '2026-03-09', '08:30', 'Boucherie Lemoine', 'Rouen', " +
                    "NULL, 'Fuite de fluide', 'cl-1', NULL, '', 'A_FAIRE', 'À reprendre.', 1, " +
                    "'', NULL, NULL, 7, NULL, NULL, 0)",
                "INSERT INTO `interventions` " +
                    "(`id`, `date`, `heure`, `client`, `ville`, `typeId`, `typeLibelle`, " +
                    "`clientId`, `equipementId`, `equipementNom`, `statut`, `notes`, `urgente`, " +
                    "`numero`, `signatureFichier`, `signeeLe`, `modifieLe`, `arriveeLe`, " +
                    "`demarreLe`, `cumuleS`) " +
                    "VALUES ('id-2', '2026-03-09', '11:00', 'Traiteur Delaunay', 'Barentin', " +
                    "NULL, 'Entretien préventif', NULL, NULL, '', 'TERMINEE', '', 0, " +
                    "'INT-2603-001', 'sig.png', 9, 7, NULL, NULL, 1800)",
            ),
        )

        val db = ouvrirEtMigrer()

        db.query(
            "SELECT `id`, `statut`, `notes`, `urgente`, `dureeMin`, `technicienId`, " +
                "`technicienNom`, `cumuleS`, `numero`, `modifieLe` " +
                "FROM `interventions` ORDER BY `id`",
        ).use { curseur ->
            assertEquals(2, curseur.count)

            assertTrue(curseur.moveToFirst())
            assertEquals("id-1", curseur.getString(0))
            assertEquals("« à faire » devient « planifié », et reste le même état", "PLANIFIEE", curseur.getString(1))
            assertEquals("À reprendre.", curseur.getString(2))
            assertEquals("l'urgence n'est pas un statut : elle survit telle quelle", 1, curseur.getInt(3))
            assertEquals("une heure, faute de mieux", 60, curseur.getInt(4))
            assertTrue("aucun technicien n'existait", curseur.isNull(5))
            assertEquals("", curseur.getString(6))

            assertTrue(curseur.moveToNext())
            assertEquals("un statut déjà bon n'est pas touché", "TERMINEE", curseur.getString(1))
            assertEquals("le temps chronométré doit survivre", 1800L, curseur.getLong(7))
            assertEquals("et le numéro attribué aussi", "INT-2603-001", curseur.getString(8))
            assertEquals(7L, curseur.getLong(9))
        }

        db.query("SELECT * FROM `techniciens`").use { curseur ->
            assertEquals("la liste des techniciens arrive vide", 0, curseur.count)
        }
        db.query("SELECT * FROM `points_checklist`").use { curseur ->
            assertEquals("la checklist se recopie à l'ouverture d'une intervention", 0, curseur.count)
        }
        assertEquals(VERSION_COURANTE, db.version)
    }

    /**
     * Le catalogue arrive garni d'intitulés de métier mais **sans prix** : ceux
     * de la maquette sont ceux d'une entreprise imaginaire, et un prix faux
     * partirait chez un vrai client sans que personne ne l'ait relu. Un zéro se
     * voit, et appelle la correction.
     */
    @Test
    fun `le catalogue arrive garni et sans prix`() {
        creerBase(version = 7, empreinte = EMPREINTE_V7, ddl = DDL_V7, insertions = emptyList())

        val db = ouvrirEtMigrer()

        db.query("SELECT COUNT(*), SUM(`prixUnitaire`) FROM `prestations`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals(21, curseur.getInt(0))
            assertEquals("aucun prix inventé", 0.0, curseur.getDouble(1), 0.001)
        }
        db.query(
            "SELECT `designation`, `categorie`, `unite` FROM `prestations` ORDER BY `rang` LIMIT 1",
        ).use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("Dépannage froid commercial", curseur.getString(0))
            assertEquals("DEPANNAGE", curseur.getString(1))
            assertEquals("forfait", curseur.getString(2))
        }
        db.query(
            "SELECT COUNT(DISTINCT `categorie`) FROM `prestations`",
        ).use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("les cinq rayons du catalogue", 5, curseur.getInt(0))
        }
    }

    /**
     * La version 9 apporte les gestes commerciaux, le multi-split, le régime de
     * TVA et l'en-tête de l'entreprise. Que de l'ajout — donc ce qui doit être
     * vérifié, c'est que les valeurs par défaut décrivent bien l'état d'avant.
     */
    @Test
    fun `une base version 8 recoit les gestes commerciaux et le multi-split`() {
        creerBase(
            version = 8,
            empreinte = EMPREINTE_V8,
            ddl = DDL_V8,
            insertions = listOf(
                "INSERT INTO `equipements` (`id`, `clientId`, `nom`, `marque`, `modele`, " +
                    "`numeroSerie`, `fluide`, `chargeKg`, `misEnServiceLe`, `dernierControleLe`, " +
                    "`modifieLe`) VALUES ('eq-1', 'cl-1', 'Split salon', 'Daikin', 'FTXM50', " +
                    "'', 'R32', 1.2, NULL, NULL, 7)",
                "INSERT INTO `prestations` (`id`, `designation`, `categorie`, `prixUnitaire`, " +
                    "`unite`, `rang`, `modifieLe`) " +
                    "VALUES ('presta-18', 'Installation split mural', 'INSTALLATION', 1450.0, " +
                    "'unité', 18, 7)",
                "INSERT INTO `devis` (`id`, `numero`, `clientId`, `clientNom`, `equipementId`, " +
                    "`equipementNom`, `objet`, `statut`, `tauxTva`, `creeLe`, `valableJusquau`, " +
                    "`modifieLe`) VALUES ('dev-1', 'DEV-2609-001', 'cl-1', 'Hôtel Bellevue', " +
                    "NULL, '', 'Pose split', 'ENVOYE', 20.0, '2026-09-10', NULL, 7)",
                "INSERT INTO `lignes_devis` (`id`, `devisId`, `designation`, `quantite`, " +
                    "`unite`, `prixUnitaire`, `rang`) " +
                    "VALUES ('lig-1', 'dev-1', 'Installation split mural', 1.0, 'unité', 1450.0, 0)",
            ),
        )

        val db = ouvrirEtMigrer()

        db.query("SELECT `nom`, `parentId`, `fluide` FROM `equipements`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("Split salon", curseur.getString(0))
            assertTrue("une machine d'avant le multi-split n'est l'unité de personne", curseur.isNull(1))
            assertEquals("R32", curseur.getString(2))
        }

        db.query("SELECT `prixUnitaire`, `parUnite` FROM `prestations`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("le prix déjà saisi survit", 1450.0, curseur.getDouble(0), 0.001)
            assertEquals("rien ne se compte par unité sans qu'on l'ait dit", 0, curseur.getInt(1))
        }

        db.query("SELECT `tauxTva`, `tvaOfferte`, `assujettiTva` FROM `devis`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals(20.0, curseur.getDouble(0), 0.001)
            assertEquals("aucun geste commercial rétroactif", 0, curseur.getInt(1))
            assertEquals(
                "un devis d'alors a été fait sous TVA : le régime n'existait pas",
                1,
                curseur.getInt(2),
            )
        }

        db.query("SELECT `designation`, `prixUnitaire`, `offerte` FROM `lignes_devis`").use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals("Installation split mural", curseur.getString(0))
            assertEquals(1450.0, curseur.getDouble(1), 0.001)
            assertEquals("une ligne déjà envoyée n'était pas offerte", 0, curseur.getInt(2))
        }

        db.query(
            "SELECT `assujettiTva`, `entreprise`, `entrepriseSiret`, `logoFichier` FROM `parametres`",
        ).use { curseur ->
            assertTrue(curseur.moveToFirst())
            assertEquals(
                "assujetti par défaut : le contraire ferait disparaître la TVA des devis",
                1,
                curseur.getInt(0),
            )
            assertEquals("", curseur.getString(1))
            assertEquals("", curseur.getString(2))
            assertTrue(curseur.isNull(3))
        }

        db.query("SELECT * FROM `verifications_fluide`").use { curseur ->
            assertEquals("aucune courbe n'est vérifiée d'avance", 0, curseur.count)
        }

        assertEquals(VERSION_COURANTE, db.version)
    }

    /** Room refuse une base dont un index manque : celui des unités aussi. */
    @Test
    fun `l'index des unites interieures arrive avec la colonne`() {
        creerBase(version = 8, empreinte = EMPREINTE_V8, ddl = DDL_V8, insertions = emptyList())

        val db = ouvrirEtMigrer()

        val index = buildList {
            db.query("SELECT `name` FROM `sqlite_master` WHERE `type` = 'index'").use { curseur ->
                while (curseur.moveToNext()) add(curseur.getString(0))
            }
        }
        assertTrue(
            "index_equipements_parentId attendu, trouvé $index",
            index.contains("index_equipements_parentId"),
        )
    }

    private companion object {

        const val VERSION_COURANTE = 9

        /** Empreintes et DDL repris mot pour mot des schémas exportés dans `app/schemas`. */
        const val EMPREINTE_V1 = "576bb93c8e6bdad21224e8d0898547f0"
        const val EMPREINTE_V2 = "fdbe212c83828e74d0d0625614cce133"
        const val EMPREINTE_V3 = "5b3449dffc8764d2688a1d5b0190a516"
        const val EMPREINTE_V4 = "670f8966c393d071075ec34e7240726e"
        const val EMPREINTE_V5 = "2fea87f26cb4e3f690878aad5160618d"
        const val EMPREINTE_V6 = "3693966e2dd927510a3eecb627dbd8e6"
        const val EMPREINTE_V7 = "c820db45f9ff53441090ae6e040433ab"
        const val EMPREINTE_V8 = "d5ba8b1487006ccbef5d9ba7bba67b7e"

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

        /**
         * Le schéma de la version 8 au complet, repris du schéma exporté.
         *
         * Comme pour la version 7 : la migration 8 → 9 ne touche qu'une partie de
         * ces tables, mais Room valide le schéma **entier** à l'ouverture.
         */
        val DDL_V8 = listOf(
            "CREATE TABLE IF NOT EXISTS `interventions` (`id` TEXT NOT NULL, `date` TEXT NOT " +
                "NULL, `heure` TEXT NOT NULL, `client` TEXT NOT NULL, `ville` TEXT NOT NULL, `typeId` " +
                "TEXT, `typeLibelle` TEXT NOT NULL, `clientId` TEXT, `equipementId` TEXT, " +
                "`equipementNom` TEXT NOT NULL, `statut` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "`urgente` INTEGER NOT NULL, `dureeMin` INTEGER NOT NULL, `technicienId` TEXT, " +
                "`technicienNom` TEXT NOT NULL, `numero` TEXT NOT NULL, `signatureFichier` TEXT, " +
                "`signeeLe` INTEGER, `modifieLe` INTEGER NOT NULL, `arriveeLe` INTEGER, `demarreLe` " +
                "INTEGER, `cumuleS` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_interventions_date` ON `interventions` (`date`)",
            "CREATE INDEX IF NOT EXISTS `index_interventions_technicienId` ON `interventions` " +
                "(`technicienId`)",
            "CREATE TABLE IF NOT EXISTS `clients` (`id` TEXT NOT NULL, `nom` TEXT NOT NULL, " +
                "`ville` TEXT NOT NULL, `adresse` TEXT NOT NULL, `telephone` TEXT NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `types_intervention` (`id` TEXT NOT NULL, `libelle` TEXT " +
                "NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `equipements` (`id` TEXT NOT NULL, `clientId` TEXT NOT " +
                "NULL, `nom` TEXT NOT NULL, `marque` TEXT NOT NULL, `modele` TEXT NOT NULL, " +
                "`numeroSerie` TEXT NOT NULL, `fluide` TEXT NOT NULL, `chargeKg` REAL, " +
                "`misEnServiceLe` TEXT, `dernierControleLe` TEXT, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_equipements_clientId` ON `equipements` " +
                "(`clientId`)",
            "CREATE TABLE IF NOT EXISTS `photos` (`id` TEXT NOT NULL, `equipementId` TEXT, " +
                "`interventionId` TEXT, `categorie` TEXT NOT NULL, `fichier` TEXT NOT NULL, `legende` " +
                "TEXT NOT NULL, `priseLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_photos_equipementId` ON `photos` (`equipementId`)",
            "CREATE INDEX IF NOT EXISTS `index_photos_interventionId` ON `photos` " +
                "(`interventionId`)",
            "CREATE TABLE IF NOT EXISTS `releves` (`id` TEXT NOT NULL, `interventionId` TEXT NOT " +
                "NULL, `equipementId` TEXT, `bpBar` REAL, `hpBar` REAL, `surchauffeK` REAL, " +
                "`sousRefroidissementK` REAL, `releveLe` INTEGER NOT NULL, `modifieLe` INTEGER NOT " +
                "NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_releves_interventionId` ON `releves` " +
                "(`interventionId`)",
            "CREATE INDEX IF NOT EXISTS `index_releves_equipementId` ON `releves` " +
                "(`equipementId`)",
            "CREATE TABLE IF NOT EXISTS `mouvements_fluide` (`id` TEXT NOT NULL, `interventionId` " +
                "TEXT NOT NULL, `equipementId` TEXT, `fluide` TEXT NOT NULL, `sens` TEXT NOT NULL, " +
                "`masseKg` REAL NOT NULL, `le` INTEGER NOT NULL, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_mouvements_fluide_interventionId` ON " +
                "`mouvements_fluide` (`interventionId`)",
            "CREATE INDEX IF NOT EXISTS `index_mouvements_fluide_equipementId` ON " +
                "`mouvements_fluide` (`equipementId`)",
            "CREATE TABLE IF NOT EXISTS `pieces_posees` (`id` TEXT NOT NULL, `interventionId` " +
                "TEXT NOT NULL, `designation` TEXT NOT NULL, `reference` TEXT NOT NULL, `quantite` " +
                "REAL NOT NULL, `prixUnitaire` REAL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_pieces_posees_interventionId` ON `pieces_posees` " +
                "(`interventionId`)",
            "CREATE TABLE IF NOT EXISTS `devis` (`id` TEXT NOT NULL, `numero` TEXT NOT NULL, " +
                "`clientId` TEXT, `clientNom` TEXT NOT NULL, `equipementId` TEXT, `equipementNom` " +
                "TEXT NOT NULL, `objet` TEXT NOT NULL, `statut` TEXT NOT NULL, `tauxTva` REAL NOT " +
                "NULL, `creeLe` TEXT, `valableJusquau` TEXT, `modifieLe` INTEGER NOT NULL, PRIMARY " +
                "KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_devis_clientId` ON `devis` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_devis_equipementId` ON `devis` (`equipementId`)",
            "CREATE TABLE IF NOT EXISTS `lignes_devis` (`id` TEXT NOT NULL, `devisId` TEXT NOT " +
                "NULL, `designation` TEXT NOT NULL, `quantite` REAL NOT NULL, `unite` TEXT NOT NULL, " +
                "`prixUnitaire` REAL NOT NULL, `rang` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_lignes_devis_devisId` ON `lignes_devis` " +
                "(`devisId`)",
            "CREATE TABLE IF NOT EXISTS `parametres` (`id` INTEGER NOT NULL, `technicien` TEXT " +
                "NOT NULL, `attestation` TEXT NOT NULL, `themeSombre` INTEGER NOT NULL, `modeGants` " +
                "INTEGER NOT NULL, `chronoAuto` INTEGER NOT NULL, `tauxHoraire` REAL NOT NULL, " +
                "`tauxTva` REAL NOT NULL, `derniereSauvegardeLe` INTEGER, `modifieLe` INTEGER NOT " +
                "NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `techniciens` (`id` TEXT NOT NULL, `nom` TEXT NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `points_checklist` (`id` TEXT NOT NULL, `interventionId` " +
                "TEXT NOT NULL, `libelle` TEXT NOT NULL, `fait` INTEGER NOT NULL, `rang` INTEGER NOT " +
                "NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_points_checklist_interventionId` ON " +
                "`points_checklist` (`interventionId`)",
            "CREATE TABLE IF NOT EXISTS `prestations` (`id` TEXT NOT NULL, `designation` TEXT NOT " +
                "NULL, `categorie` TEXT NOT NULL, `prixUnitaire` REAL NOT NULL, `unite` TEXT NOT " +
                "NULL, `rang` INTEGER NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_prestations_categorie` ON `prestations` " +
                "(`categorie`)",
        )

        /**
         * Le schéma de la version 7 au complet : la migration 7 → 8 ne touche
         * qu'une partie de ces tables, mais Room valide le schéma **entier** à
         * l'ouverture et refuserait une base amputée du reste.
         */
        val DDL_V7 = listOf(
            "CREATE TABLE IF NOT EXISTS `interventions` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`heure` TEXT NOT NULL, `client` TEXT NOT NULL, `ville` TEXT NOT NULL, " +
                "`typeId` TEXT, `typeLibelle` TEXT NOT NULL, `clientId` TEXT, " +
                "`equipementId` TEXT, `equipementNom` TEXT NOT NULL, `statut` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, `urgente` INTEGER NOT NULL, `numero` TEXT NOT NULL, " +
                "`signatureFichier` TEXT, `signeeLe` INTEGER, `modifieLe` INTEGER NOT NULL, " +
                "`arriveeLe` INTEGER, `demarreLe` INTEGER, `cumuleS` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            DDL_INDEX_DATE,
            DDL_CLIENTS_V4,
            DDL_TYPES_V5,
            "CREATE TABLE IF NOT EXISTS `equipements` (`id` TEXT NOT NULL, `clientId` TEXT NOT NULL, " +
                "`nom` TEXT NOT NULL, `marque` TEXT NOT NULL, `modele` TEXT NOT NULL, " +
                "`numeroSerie` TEXT NOT NULL, `fluide` TEXT NOT NULL, `chargeKg` REAL, " +
                "`misEnServiceLe` TEXT, `dernierControleLe` TEXT, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            DDL_INDEX_EQUIPEMENTS_CLIENT,
            "CREATE TABLE IF NOT EXISTS `photos` (`id` TEXT NOT NULL, `equipementId` TEXT, " +
                "`interventionId` TEXT, `categorie` TEXT NOT NULL, `fichier` TEXT NOT NULL, " +
                "`legende` TEXT NOT NULL, `priseLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            DDL_INDEX_PHOTOS_EQUIPEMENT,
            "CREATE INDEX IF NOT EXISTS `index_photos_interventionId` ON `photos` (`interventionId`)",
            "CREATE TABLE IF NOT EXISTS `releves` (`id` TEXT NOT NULL, `interventionId` TEXT NOT NULL, " +
                "`equipementId` TEXT, `bpBar` REAL, `hpBar` REAL, `surchauffeK` REAL, " +
                "`sousRefroidissementK` REAL, `releveLe` INTEGER NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_releves_interventionId` ON `releves` (`interventionId`)",
            "CREATE INDEX IF NOT EXISTS `index_releves_equipementId` ON `releves` (`equipementId`)",
            "CREATE TABLE IF NOT EXISTS `mouvements_fluide` (`id` TEXT NOT NULL, " +
                "`interventionId` TEXT NOT NULL, `equipementId` TEXT, `fluide` TEXT NOT NULL, " +
                "`sens` TEXT NOT NULL, `masseKg` REAL NOT NULL, `le` INTEGER NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_mouvements_fluide_interventionId` " +
                "ON `mouvements_fluide` (`interventionId`)",
            "CREATE INDEX IF NOT EXISTS `index_mouvements_fluide_equipementId` " +
                "ON `mouvements_fluide` (`equipementId`)",
            "CREATE TABLE IF NOT EXISTS `pieces_posees` (`id` TEXT NOT NULL, " +
                "`interventionId` TEXT NOT NULL, `designation` TEXT NOT NULL, " +
                "`reference` TEXT NOT NULL, `quantite` REAL NOT NULL, `prixUnitaire` REAL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_pieces_posees_interventionId` " +
                "ON `pieces_posees` (`interventionId`)",
            "CREATE TABLE IF NOT EXISTS `devis` (`id` TEXT NOT NULL, `numero` TEXT NOT NULL, " +
                "`clientId` TEXT, `clientNom` TEXT NOT NULL, `equipementId` TEXT, " +
                "`equipementNom` TEXT NOT NULL, `objet` TEXT NOT NULL, `statut` TEXT NOT NULL, " +
                "`tauxTva` REAL NOT NULL, `creeLe` TEXT, `valableJusquau` TEXT, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_devis_clientId` ON `devis` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_devis_equipementId` ON `devis` (`equipementId`)",
            "CREATE TABLE IF NOT EXISTS `lignes_devis` (`id` TEXT NOT NULL, `devisId` TEXT NOT NULL, " +
                "`designation` TEXT NOT NULL, `quantite` REAL NOT NULL, `unite` TEXT NOT NULL, " +
                "`prixUnitaire` REAL NOT NULL, `rang` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_lignes_devis_devisId` ON `lignes_devis` (`devisId`)",
            "CREATE TABLE IF NOT EXISTS `parametres` (`id` INTEGER NOT NULL, " +
                "`technicien` TEXT NOT NULL, `attestation` TEXT NOT NULL, " +
                "`themeSombre` INTEGER NOT NULL, `modeGants` INTEGER NOT NULL, " +
                "`chronoAuto` INTEGER NOT NULL, `tauxHoraire` REAL NOT NULL, " +
                "`tauxTva` REAL NOT NULL, `derniereSauvegardeLe` INTEGER, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
