package com.frigopro.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Arrivée du suivi d'intervention : `statut` et `notes`.
 *
 * Les deux colonnes ont une valeur par défaut, si bien que les interventions
 * déjà saisies deviennent « à faire » sans note — exactement leur état réel
 * avant que le suivi existe. Sans ces valeurs par défaut, `ALTER TABLE` refuse
 * d'ajouter une colonne `NOT NULL` à une table qui contient déjà des lignes.
 *
 * Conséquence à connaître : une base migrée porte ces `DEFAULT` au niveau SQL,
 * une base neuve non, puisque l'entité ne déclare pas de `@ColumnInfo`
 * `defaultValue`. Room l'accepte — il ne compare les défauts que lorsque
 * l'entité en déclare — et rien n'en dépend, toute écriture passant par lui.
 * Mais un futur `INSERT` en SQL brut se comporterait différemment selon
 * l'origine de la base : le cas échéant, déclarer les défauts sur l'entité.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `statut` TEXT NOT NULL DEFAULT 'A_FAIRE'")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Arrivée du carnet de clients.
 *
 * `clientId` est volontairement nullable et **sans clé étrangère** : SQLite ne
 * sait pas ajouter une contrainte à une table existante, il faudrait la
 * reconstruire. Le jeu n'en vaut pas la chandelle tant que rien ne supprime de
 * client ; le jour où la suppression arrivera, sa migration reconstruira la
 * table et posera la contrainte avec un `ON DELETE SET NULL`.
 *
 * Les interventions déjà saisies restent sans client rattaché, ce qui est
 * exact : le carnet n'existait pas quand elles ont été créées.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `clients` (" +
                "`id` TEXT NOT NULL, `nom` TEXT NOT NULL, `ville` TEXT NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `clientId` TEXT")
    }
}

/**
 * L'adresse et le téléphone rejoignent la fiche client.
 *
 * Les deux colonnes arrivent vides sur les clients déjà inscrits, ce qui est
 * leur état réel : le carnet les a créés depuis une intervention, où seuls le
 * nom et la ville sont demandés. Elles se rempliront depuis l'onglet Clients.
 *
 * Même remarque que pour [MIGRATION_1_2] sur les `DEFAULT` : ils existent au
 * niveau SQL dans une base migrée, pas dans une base neuve.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `clients` ADD COLUMN `adresse` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `clients` ADD COLUMN `telephone` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Le type de panne devient un type d'intervention, choisi dans une liste que le
 * technicien tient lui-même.
 *
 * La colonne `typePanne` disparaît, ce qui impose de **reconstruire la table** :
 * `DROP COLUMN` n'existe dans SQLite que depuis la version 3.35, absente des
 * appareils couverts par `minSdk 26`. La reconstruction est de toute façon la
 * méthode recommandée, et Room exécute la migration dans une transaction : ou
 * tout passe, ou rien ne change.
 *
 * Les interventions déjà saisies reçoivent leur intitulé d'alors et aucun lien.
 * La liste démarrant vide, elles ne peuvent désigner aucun type ; elles
 * continuent pourtant d'afficher « Fuite de fluide » ou « Entretien préventif »,
 * parce que l'intitulé est recopié sur la ligne. C'est le rôle de cette copie.
 *
 * Le `CASE` traduit les constantes de l'ancienne énumération en français. Le
 * `ELSE` recopie la valeur brute : une énumération inconnue — impossible en
 * principe, mais une base abîmée existe — vaut mieux qu'une cellule vide.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `types_intervention` (" +
                "`id` TEXT NOT NULL, `libelle` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `interventions_nouvelle` (" +
                "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `heure` TEXT NOT NULL, " +
                "`client` TEXT NOT NULL, `ville` TEXT NOT NULL, `typeId` TEXT, " +
                "`typeLibelle` TEXT NOT NULL, `clientId` TEXT, `statut` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `interventions_nouvelle` " +
                "(`id`, `date`, `heure`, `client`, `ville`, `typeId`, `typeLibelle`, " +
                "`clientId`, `statut`, `notes`, `modifieLe`) " +
                "SELECT `id`, `date`, `heure`, `client`, `ville`, NULL, " +
                "CASE `typePanne` " +
                "WHEN 'FUITE_FLUIDE' THEN 'Fuite de fluide' " +
                "WHEN 'COMPRESSEUR' THEN 'Compresseur' " +
                "WHEN 'REGULATION' THEN 'Régulation' " +
                "WHEN 'GIVRAGE' THEN 'Givrage' " +
                "WHEN 'ENTRETIEN' THEN 'Entretien préventif' " +
                "ELSE `typePanne` END, " +
                "`clientId`, `statut`, `notes`, `modifieLe` FROM `interventions`",
        )
        db.execSQL("DROP TABLE `interventions`")
        db.execSQL("ALTER TABLE `interventions_nouvelle` RENAME TO `interventions`")
        // L'index suivait la table détruite : il faut le reposer.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_interventions_date` ON `interventions` (`date`)")
    }
}

/**
 * Arrivée du parc de machines et de leurs photos.
 *
 * Deux tables neuves et deux colonnes ajoutées aux interventions : `ALTER TABLE`
 * suffit, rien ne disparaît, donc aucune reconstruction — contrairement à
 * [MIGRATION_4_5].
 *
 * `equipementId` et `equipementNom` répètent le couple lien / copie déjà employé
 * pour le type (voir [Intervention.typeLibelle]) : le lien permet de répercuter
 * un renommage sur les tournées passées, la copie survit à la suppression de la
 * machine. Les interventions déjà saisies n'en désignent aucune et n'affichent
 * rien, ce qui est exact — le parc n'existait pas quand elles ont été créées.
 *
 * Toujours **sans clé étrangère**, pour les raisons dites en [MIGRATION_2_3], et
 * une de plus : une restauration de sauvegarde écrit table après table, et une
 * contrainte immédiate rejetterait une photo arrivant avant sa machine.
 *
 * Les fichiers image ne sont pas concernés : ils vivent dans `files/photos/`
 * (voir [StockagePhotos]), que cette migration ne touche pas.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `equipements` (" +
                "`id` TEXT NOT NULL, `clientId` TEXT NOT NULL, `nom` TEXT NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipements_clientId` ON `equipements` (`clientId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `photos` (" +
                "`id` TEXT NOT NULL, `equipementId` TEXT NOT NULL, `categorie` TEXT NOT NULL, " +
                "`fichier` TEXT NOT NULL, `priseLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_equipementId` ON `photos` (`equipementId`)")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `equipementId` TEXT")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `equipementNom` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * La refonte : ce qui s'est vraiment passé sur place.
 *
 * C'est la migration la plus lourde du projet, parce qu'elle apporte d'un
 * coup tout ce que la maquette a ajouté — le temps passé, les relevés, le
 * fluide, les pièces, les devis, les réglages — et parce qu'elle doit
 * **reconstruire la table des photos**.
 *
 * Cette reconstruction est la seule partie délicate. Jusqu'ici une photo
 * appartenait forcément à une machine, d'où un `equipementId NOT NULL` ; elle
 * peut désormais appartenir à une intervention (les clichés avant/après), et
 * `equipementId` doit donc devenir nullable. SQLite ne sait pas relâcher une
 * contrainte `NOT NULL` par `ALTER TABLE` : il faut créer la table voulue,
 * y recopier les lignes, supprimer l'ancienne, renommer la neuve — et
 * **reposer les index**, qui disparaissent avec la table détruite. Room valide
 * le schéma entier à l'ouverture et refuse une base dont un index manque ;
 * `MigrationTest` le vérifie explicitement.
 *
 * Tout le reste n'est qu'ajout, donc sans risque : les colonnes arrivent avec
 * une valeur par défaut qui décrit exactement l'état antérieur — aucun temps
 * chronométré, aucune urgence, aucun compte-rendu établi.
 *
 * La ligne unique des réglages est insérée ici avec ses valeurs par défaut.
 * Sans elle, la première lecture rendrait `null` et l'application devrait
 * traiter partout le cas « pas encore de réglages » ; l'insérer une fois, à
 * l'endroit prévu pour cela, supprime la question.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // — Le temps passé, l'urgence et le compte-rendu, sur l'intervention —
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `urgente` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `arriveeLe` INTEGER")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `demarreLe` INTEGER")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `cumuleS` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `numero` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `signatureFichier` TEXT")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `signeeLe` INTEGER")

        // — La plaque signalétique et le fluide, sur la machine —
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `marque` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `modele` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `numeroSerie` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `fluide` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `chargeKg` REAL")
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `misEnServiceLe` TEXT")
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `dernierControleLe` TEXT")

        // — Les photos : table reconstruite pour relâcher `equipementId` —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `photos_nouvelle` (" +
                "`id` TEXT NOT NULL, `equipementId` TEXT, `interventionId` TEXT, " +
                "`categorie` TEXT NOT NULL, `fichier` TEXT NOT NULL, " +
                "`legende` TEXT NOT NULL DEFAULT '', `priseLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `photos_nouvelle` " +
                "(`id`, `equipementId`, `interventionId`, `categorie`, `fichier`, `legende`, `priseLe`) " +
                "SELECT `id`, `equipementId`, NULL, `categorie`, `fichier`, '', `priseLe` FROM `photos`",
        )
        db.execSQL("DROP TABLE `photos`")
        db.execSQL("ALTER TABLE `photos_nouvelle` RENAME TO `photos`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_equipementId` ON `photos` (`equipementId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_interventionId` ON `photos` (`interventionId`)")

        // — Les relevés frigorifiques —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `releves` (" +
                "`id` TEXT NOT NULL, `interventionId` TEXT NOT NULL, `equipementId` TEXT, " +
                "`bpBar` REAL, `hpBar` REAL, `surchauffeK` REAL, `sousRefroidissementK` REAL, " +
                "`releveLe` INTEGER NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_releves_interventionId` ON `releves` (`interventionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_releves_equipementId` ON `releves` (`equipementId`)")

        // — Le registre des fluides —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `mouvements_fluide` (" +
                "`id` TEXT NOT NULL, `interventionId` TEXT NOT NULL, `equipementId` TEXT, " +
                "`fluide` TEXT NOT NULL, `sens` TEXT NOT NULL, `masseKg` REAL NOT NULL, " +
                "`le` INTEGER NOT NULL, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_mouvements_fluide_interventionId` " +
                "ON `mouvements_fluide` (`interventionId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_mouvements_fluide_equipementId` " +
                "ON `mouvements_fluide` (`equipementId`)",
        )

        // — Les pièces posées —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pieces_posees` (" +
                "`id` TEXT NOT NULL, `interventionId` TEXT NOT NULL, `designation` TEXT NOT NULL, " +
                "`reference` TEXT NOT NULL, `quantite` REAL NOT NULL, `prixUnitaire` REAL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pieces_posees_interventionId` " +
                "ON `pieces_posees` (`interventionId`)",
        )

        // — Les devis et leurs lignes —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `devis` (" +
                "`id` TEXT NOT NULL, `numero` TEXT NOT NULL, `clientId` TEXT, " +
                "`clientNom` TEXT NOT NULL, `equipementId` TEXT, `equipementNom` TEXT NOT NULL, " +
                "`objet` TEXT NOT NULL, `statut` TEXT NOT NULL, `tauxTva` REAL NOT NULL, " +
                "`creeLe` TEXT, `valableJusquau` TEXT, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_devis_clientId` ON `devis` (`clientId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_devis_equipementId` ON `devis` (`equipementId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lignes_devis` (" +
                "`id` TEXT NOT NULL, `devisId` TEXT NOT NULL, `designation` TEXT NOT NULL, " +
                "`quantite` REAL NOT NULL, `unite` TEXT NOT NULL, `prixUnitaire` REAL NOT NULL, " +
                "`rang` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lignes_devis_devisId` ON `lignes_devis` (`devisId`)")

        // — Les réglages, et leur ligne unique —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `parametres` (" +
                "`id` INTEGER NOT NULL, `technicien` TEXT NOT NULL, `attestation` TEXT NOT NULL, " +
                "`themeSombre` INTEGER NOT NULL, `modeGants` INTEGER NOT NULL, " +
                "`chronoAuto` INTEGER NOT NULL, `tauxHoraire` REAL NOT NULL, `tauxTva` REAL NOT NULL, " +
                "`derniereSauvegardeLe` INTEGER, `modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `parametres` " +
                "(`id`, `technicien`, `attestation`, `themeSombre`, `modeGants`, `chronoAuto`, " +
                "`tauxHoraire`, `tauxTva`, `derniereSauvegardeLe`, `modifieLe`) " +
                "VALUES (1, '', '', 1, 0, 0, 0.0, 20.0, NULL, 0)",
        )
    }
}
