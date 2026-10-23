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

/**
 * Le catalogue livré, en SQL, **figé**.
 *
 * Deux chemins doivent poser les mêmes lignes : la migration, pour un téléphone
 * déjà garni de tournées, et l'ouverture d'une base neuve, pour une
 * installation neuve. Les faire divergerait reviendrait à livrer deux
 * applications différentes selon l'ancienneté du téléphone, et c'est le genre
 * d'écart qu'on ne découvre que chez l'utilisateur.
 *
 * Le SQL est écrit en clair plutôt que dérivé de [CATALOGUE_INITIAL] : une
 * migration doit faire aujourd'hui ce qu'elle faisait le jour où elle est
 * passée, et une liste Kotlin qu'on rallonge réécrirait l'histoire. Une
 * prestation ajoutée plus tard le sera donc par sa propre migration.
 *
 * Les identifiants sont stables et non tirés au hasard : restaurer une
 * sauvegarde faite ailleurs doit retrouver les mêmes lignes, et non en créer
 * vingt et une de plus. `INSERT OR IGNORE` rend l'insertion rejouable.
 */
internal const val SQL_CATALOGUE_INITIAL: String =
    "INSERT OR IGNORE INTO `prestations` " +
    "(`id`, `designation`, `categorie`, `prixUnitaire`, `unite`, `rang`, `modifieLe`) " +
    "VALUES " +
    "('presta-01', 'Dépannage froid commercial', 'DEPANNAGE', 0.0, 'forfait', 1, 0)," +
    "('presta-02', 'Main d''œuvre', 'DEPANNAGE', 0.0, 'h', 2, 0)," +
    "('presta-03', 'Déplacement', 'DEPANNAGE', 0.0, 'forfait', 3, 0)," +
    "('presta-04', 'Majoration urgence / astreinte', 'DEPANNAGE', 0.0, 'forfait', 4, 0)," +
    "('presta-05', 'Maintenance chambre froide', 'MAINTENANCE', 0.0, 'visite', 5, 0)," +
    "('presta-06', 'Maintenance vitrine réfrigérée', 'MAINTENANCE', 0.0, 'visite', 6, 0)," +
    "('presta-07', 'Maintenance climatisation', 'MAINTENANCE', 0.0, 'visite', 7, 0)," +
    "('presta-08', 'Contrôle étanchéité F-Gas', 'FLUIDE', 0.0, 'contrôle', 8, 0)," +
    "('presta-09', 'Recharge R-449A', 'FLUIDE', 0.0, 'kg', 9, 0)," +
    "('presta-10', 'Recharge R-134a', 'FLUIDE', 0.0, 'kg', 10, 0)," +
    "('presta-11', 'Recharge R-32', 'FLUIDE', 0.0, 'kg', 11, 0)," +
    "('presta-12', 'Récupération fluide', 'FLUIDE', 0.0, 'forfait', 12, 0)," +
    "('presta-13', 'Compresseur hermétique', 'PIECES', 0.0, 'pièce', 13, 0)," +
    "('presta-14', 'Détendeur thermostatique', 'PIECES', 0.0, 'pièce', 14, 0)," +
    "('presta-15', 'Ventilateur évaporateur', 'PIECES', 0.0, 'pièce', 15, 0)," +
    "('presta-16', 'Résistance de dégivrage', 'PIECES', 0.0, 'pièce', 16, 0)," +
    "('presta-17', 'Filtre déshydrateur', 'PIECES', 0.0, 'pièce', 17, 0)," +
    "('presta-18', 'Installation split mural', 'INSTALLATION', 0.0, 'unité', 18, 0)," +
    "('presta-19', 'Installation chambre froide', 'INSTALLATION', 0.0, 'forfait', 19, 0)," +
    "('presta-20', 'Pompe à chaleur air/eau', 'INSTALLATION', 0.0, 'unité', 20, 0)," +
    "('presta-21', 'Mise en service', 'INSTALLATION', 0.0, 'forfait', 21, 0)"

/**
 * La refonte visuelle, et ce qu'elle apporte : techniciens, durées, checklist
 * et catalogue de prestations.
 *
 * Tout y est ajout, sauf un renommage de valeur : le statut `A_FAIRE` devient
 * `PLANIFIEE`. Le mot change parce que l'écran change — « planifié » se dit
 * d'un créneau posé sur une frise horaire, « à faire » d'une case de liste —
 * mais c'est bien le même état, et la ligne migrée garde sa place dans la
 * tournée.
 *
 * La **durée** arrive avec une valeur par défaut d'une heure. C'est une
 * supposition, et c'est assumé : sans durée, le planning ne saurait pas quelle
 * hauteur donner à un créneau, et une heure est ce qui demande le moins de
 * correction sur une tournée ordinaire.
 *
 * Le **catalogue** est inséré avec ses intitulés et **sans ses prix**. Ceux de
 * la maquette sont ceux d'une entreprise imaginaire ; les recopier les ferait
 * partir chez un vrai client sans que personne ne les ait relus. Une ligne à
 * zéro euro se voit, et appelle une correction — un prix faux, non.
 */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // — Le statut renommé, la durée, le technicien —
        db.execSQL("UPDATE `interventions` SET `statut` = 'PLANIFIEE' WHERE `statut` = 'A_FAIRE'")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `dureeMin` INTEGER NOT NULL DEFAULT 60")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `technicienId` TEXT")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `technicienNom` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_interventions_technicienId` " +
                "ON `interventions` (`technicienId`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `techniciens` (" +
                "`id` TEXT NOT NULL, `nom` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )

        // — La checklist, recopiée sur chaque intervention —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `points_checklist` (" +
                "`id` TEXT NOT NULL, `interventionId` TEXT NOT NULL, `libelle` TEXT NOT NULL, " +
                "`fait` INTEGER NOT NULL, `rang` INTEGER NOT NULL, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_points_checklist_interventionId` " +
                "ON `points_checklist` (`interventionId`)",
        )

        // — Le catalogue —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `prestations` (" +
                "`id` TEXT NOT NULL, `designation` TEXT NOT NULL, `categorie` TEXT NOT NULL, " +
                "`prixUnitaire` REAL NOT NULL, `unite` TEXT NOT NULL, `rang` INTEGER NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_prestations_categorie` " +
                "ON `prestations` (`categorie`)",
        )
        db.execSQL(SQL_CATALOGUE_INITIAL)
    }
}

/**
 * Les gestes commerciaux, le multi-split, le régime de TVA et l'identité de
 * l'entreprise.
 *
 * Tout y est ajout : aucune colonne ne disparaît, aucune table ne se reconstruit.
 * Les valeurs par défaut décrivent exactement l'état antérieur — aucune ligne
 * offerte, aucune TVA offerte, aucune unité intérieure, une entreprise assujettie
 * et sans en-tête renseignée.
 *
 * `assujettiTva` arrive à 1 et non à 0, et le choix compte : supposer la
 * franchise en base ferait disparaître la TVA des devis d'un artisan qui la
 * facture, alors que l'inverse — afficher une TVA à quelqu'un qui n'y est pas
 * assujetti — se remarque à la première lecture du document.
 *
 * `parentId` est nullable et **sans clé étrangère**, pour les raisons dites en
 * [MIGRATION_2_3] : SQLite ne sait pas ajouter une contrainte à une table
 * existante sans la reconstruire, et une restauration de sauvegarde écrit ligne
 * après ligne — une contrainte immédiate rejetterait une unité arrivant avant son
 * groupe.
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // — Le multi-split : une unité intérieure désigne son groupe —
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `parentId` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipements_parentId` ON `equipements` (`parentId`)")

        // — Une prestation qui se compte par unité intérieure —
        db.execSQL("ALTER TABLE `prestations` ADD COLUMN `parUnite` INTEGER NOT NULL DEFAULT 0")

        // — Les gestes commerciaux —
        db.execSQL("ALTER TABLE `lignes_devis` ADD COLUMN `offerte` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `devis` ADD COLUMN `tvaOfferte` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `devis` ADD COLUMN `assujettiTva` INTEGER NOT NULL DEFAULT 1")

        // — Le régime et l'en-tête, sur la ligne unique des réglages —
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `assujettiTva` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `entreprise` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `entrepriseAdresse` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `entrepriseTelephone` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `entrepriseEmail` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `entrepriseSiret` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `logoFichier` TEXT")

        // — Qui a contrôlé quelle courbe de saturation —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `verifications_fluide` (" +
                "`fluide` TEXT NOT NULL, `verifieLe` INTEGER NOT NULL, `par` TEXT NOT NULL, " +
                "PRIMARY KEY(`fluide`))",
        )
    }
}

/**
 * Version 10 : la facturation du déplacement.
 *
 * Tout y est ajout — une table et des colonnes — donc rien à reconstruire. Le
 * `NOT NULL DEFAULT` de chaque colonne est obligatoire et non cosmétique :
 * `ALTER TABLE ADD COLUMN` refuse une colonne non nulle sans valeur par défaut,
 * et une ligne de réglages existe déjà sur tous les téléphones.
 *
 * Les défauts disent quelque chose. `modeDeplacement` arrive à `KM` parce que
 * c'est le mode le plus répandu et le plus facile à vérifier sur une facture.
 * Les prix arrivent à **zéro**, comme le catalogue de prestations et pour la
 * même raison : un tarif kilométrique inventé partirait chez un vrai client
 * sans que personne ne l'ait relu, alors qu'un zéro se voit et appelle une
 * correction. `refacturerPeages` arrive à `1` — celui qui a avancé un péage
 * s'attend par défaut à le récupérer, et l'oubli coûte de l'argent là où
 * l'inverse se remarque à la lecture du devis.
 *
 * `deplacement` sur les lignes de devis arrive à `0` : aucune ligne existante ne
 * vient d'un calcul de trajet, et les prendre pour telles les ferait effacer au
 * premier recalcul.
 *
 * `peagesConnus` est séparé de `peages` pour la raison dite dans [Trajet] :
 * « pas de péage » et « je n'en sais rien » valent tous deux zéro dans une
 * colonne de chiffres et ne valent pas la même chose sur un devis.
 */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // — Le trajet facturé d'un devis —
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trajets` (" +
                "`id` TEXT NOT NULL, `devisId` TEXT NOT NULL, " +
                "`depart` TEXT NOT NULL, `arrivee` TEXT NOT NULL, " +
                "`distanceKm` REAL NOT NULL, `dureeMinutes` INTEGER NOT NULL, " +
                "`peages` REAL NOT NULL, `peagesConnus` INTEGER NOT NULL, " +
                "`allerRetour` INTEGER NOT NULL, `offert` INTEGER NOT NULL, " +
                "`origine` TEXT NOT NULL, `calculeLe` INTEGER, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trajets_devisId` ON `trajets` (`devisId`)")

        // — Les lignes que le déplacement a produites —
        db.execSQL("ALTER TABLE `lignes_devis` ADD COLUMN `deplacement` INTEGER NOT NULL DEFAULT 0")

        // — Le tarif, sur la ligne unique des réglages —
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `adresseDepart` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `modeDeplacement` TEXT NOT NULL DEFAULT 'KM'")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `prixKm` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `prixHeureTrajet` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `minimumDeplacement` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `refacturerPeages` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `parametres` ADD COLUMN `cleItineraire` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Version 11 : la clé d'itinéraire quitte les réglages.
 *
 * Elle n'a plus de raison d'être : le calcul passe désormais par un relais qui
 * tient la clé (voir `relais/`), et l'utilisateur n'a rien à saisir. La lui
 * demander était la première version de cette fonctionnalité, et c'était une
 * erreur de conception — personne n'ouvre un compte sur une console d'API pour
 * saisir un kilométrage, si bien que le calcul automatique n'aurait jamais servi.
 *
 * **Une colonne qui disparaît impose de reconstruire la table** : `DROP COLUMN`
 * n'existe dans SQLite que depuis la version 3.35, absente des appareils
 * couverts par `minSdk 26`. C'est peu coûteux ici — `parametres` n'a qu'une
 * ligne — mais l'ordre compte : créer la nouvelle table, recopier, détruire
 * l'ancienne, renommer.
 *
 * Ne pas la supprimer aurait laissé en base un champ de **credential** que plus
 * rien ne lit ni n'efface. Une clé oubliée dans une colonne morte est une clé
 * qui traîne.
 */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `parametres_neuf` (" +
                "`id` INTEGER NOT NULL, `technicien` TEXT NOT NULL, " +
                "`attestation` TEXT NOT NULL, `themeSombre` INTEGER NOT NULL, " +
                "`modeGants` INTEGER NOT NULL, `chronoAuto` INTEGER NOT NULL, " +
                "`tauxHoraire` REAL NOT NULL, `tauxTva` REAL NOT NULL, " +
                "`assujettiTva` INTEGER NOT NULL, `entreprise` TEXT NOT NULL, " +
                "`entrepriseAdresse` TEXT NOT NULL, `entrepriseTelephone` TEXT NOT NULL, " +
                "`entrepriseEmail` TEXT NOT NULL, `entrepriseSiret` TEXT NOT NULL, " +
                "`logoFichier` TEXT, `adresseDepart` TEXT NOT NULL, " +
                "`modeDeplacement` TEXT NOT NULL, `prixKm` REAL NOT NULL, " +
                "`prixHeureTrajet` REAL NOT NULL, `minimumDeplacement` REAL NOT NULL, " +
                "`refacturerPeages` INTEGER NOT NULL, `derniereSauvegardeLe` INTEGER, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `parametres_neuf` (" +
                "`id`, `technicien`, `attestation`, `themeSombre`, `modeGants`, " +
                "`chronoAuto`, `tauxHoraire`, `tauxTva`, `assujettiTva`, `entreprise`, " +
                "`entrepriseAdresse`, `entrepriseTelephone`, `entrepriseEmail`, " +
                "`entrepriseSiret`, `logoFichier`, `adresseDepart`, `modeDeplacement`, " +
                "`prixKm`, `prixHeureTrajet`, `minimumDeplacement`, `refacturerPeages`, " +
                "`derniereSauvegardeLe`, `modifieLe`) " +
                "SELECT `id`, `technicien`, `attestation`, `themeSombre`, `modeGants`, " +
                "`chronoAuto`, `tauxHoraire`, `tauxTva`, `assujettiTva`, `entreprise`, " +
                "`entrepriseAdresse`, `entrepriseTelephone`, `entrepriseEmail`, " +
                "`entrepriseSiret`, `logoFichier`, `adresseDepart`, `modeDeplacement`, " +
                "`prixKm`, `prixHeureTrajet`, `minimumDeplacement`, `refacturerPeages`, " +
                "`derniereSauvegardeLe`, `modifieLe` FROM `parametres`",
        )
        db.execSQL("DROP TABLE `parametres`")
        db.execSQL("ALTER TABLE `parametres_neuf` RENAME TO `parametres`")
    }
}

/**
 * Version 12 : la facturation.
 *
 * Deux tables neuves et deux colonnes sur les réglages — que des ajouts, donc
 * rien à reconstruire. `factures` porte un `interventionId` et un `devisId` tous
 * deux nullables, parce qu'une facture vient de l'un **ou** de l'autre : d'une
 * intervention terminée pour le dépannage imprévu, d'un devis accepté pour le
 * chantier annoncé, et de rien du tout quand elle est saisie à la main.
 *
 * Les défauts posés ici disent quelque chose, comme ceux de [MIGRATION_9_10] :
 *
 * - `delaiPaiementJours` à **30**, le délai supplétif du code de commerce —
 *   celui qui s'applique quand rien n'a été convenu. C'est le seul défaut du
 *   projet qui soit une valeur réglementaire plutôt qu'un choix.
 * - `tauxPenalitesRetard` à **zéro**, qui veut dire « non fixé » et non « pas de
 *   pénalités » : faute de taux convenu, c'est le taux légal qui s'applique de
 *   plein droit, et le document le mentionne ainsi. Y mettre un chiffre inventé
 *   l'aurait fait partir chez un client sans que personne ne l'ait relu.
 */
val MIGRATION_11_12: Migration = object : Migration(11, 12) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `factures` (" +
                "`id` TEXT NOT NULL, `numero` TEXT NOT NULL, `clientId` TEXT, " +
                "`clientNom` TEXT NOT NULL, `clientAdresse` TEXT NOT NULL, " +
                "`interventionId` TEXT, `devisId` TEXT, `equipementNom` TEXT NOT NULL, " +
                "`objet` TEXT NOT NULL, `statut` TEXT NOT NULL, `tauxTva` REAL NOT NULL, " +
                "`tvaOfferte` INTEGER NOT NULL, `assujettiTva` INTEGER NOT NULL, " +
                "`emiseLe` TEXT, `echeanceLe` TEXT, `tauxPenalites` REAL NOT NULL, " +
                "`payeeLe` TEXT, `relanceeLe` TEXT, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        // Les index sont aussi obligatoires que les tables : Room valide le
        // schéma entier à l'ouverture et refuse une base qui n'y correspond pas.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_factures_clientId` ON `factures` (`clientId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_factures_interventionId` " +
                "ON `factures` (`interventionId`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_factures_devisId` ON `factures` (`devisId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lignes_facture` (" +
                "`id` TEXT NOT NULL, `factureId` TEXT NOT NULL, " +
                "`designation` TEXT NOT NULL, `quantite` REAL NOT NULL, " +
                "`unite` TEXT NOT NULL, `prixUnitaire` REAL NOT NULL, " +
                "`offerte` INTEGER NOT NULL, `rang` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_lignes_facture_factureId` " +
                "ON `lignes_facture` (`factureId`)",
        )

        db.execSQL(
            "ALTER TABLE `parametres` ADD COLUMN `delaiPaiementJours` " +
                "INTEGER NOT NULL DEFAULT 30",
        )
        db.execSQL(
            "ALTER TABLE `parametres` ADD COLUMN `tauxPenalitesRetard` " +
                "REAL NOT NULL DEFAULT 0",
        )
    }
}

/**
 * Les sites d'un client, et la sous-traitance.
 *
 * Trois colonnes, aucune table, rien à reconstruire — et c'est le signe que le
 * modèle se prêtait à ces deux besoins. Un site est un client rattaché à un
 * autre (`clients.parentId`, le motif des unités de multi-split), et les
 * machines n'ont rien à changer puisqu'elles pendent déjà d'un `clientId`.
 *
 * `clientFactureId` arrive **nullable**, et c'est ce qui rend justes toutes les
 * interventions déjà saisies sans les toucher : `null` veut dire « facturé à
 * celui chez qui on est allé », qui est le cas ordinaire et ce qu'elles
 * disaient déjà. Sa copie arrive à la chaîne vide, comme les autres copies de
 * ce schéma.
 *
 * L'index sur `parentId` est aussi obligatoire que la colonne : Room valide le
 * schéma entier à l'ouverture, et l'entité le déclare.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `clients` ADD COLUMN `parentId` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_clients_parentId` ON `clients` (`parentId`)")

        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `clientFactureId` TEXT")
        db.execSQL(
            "ALTER TABLE `interventions` ADD COLUMN `clientFactureNom` " +
                "TEXT NOT NULL DEFAULT ''",
        )
    }
}

/**
 * Le magasin : fournisseurs, articles, et les deux stocks.
 *
 * Trois tables neuves, aucune reconstruction — mais **cinq index**, et ils sont
 * aussi obligatoires que les tables : Room valide le schéma entier à
 * l'ouverture, et une migration qui passe sans eux fait échouer le démarrage
 * suivant.
 *
 * L'index de `stocks` est le seul **unique** du projet, et il porte une règle
 * plutôt qu'une optimisation : un article n'a qu'une ligne par lieu. Deux lignes
 * « atelier » pour le même article donneraient deux comptes contradictoires, et
 * rien à l'écran ne dirait lequel est le bon. La base le refuse donc, plutôt que
 * de compter sur le dépôt pour n'en créer qu'une.
 *
 * Les prix arrivent à **zéro**, comme le catalogue de prestations et les tarifs
 * de déplacement, et pour la même raison : un prix inventé partirait chez un
 * vrai client sans que personne ne l'ait relu. Zéro se voit et appelle une
 * correction. Le `minimum` d'un stock à zéro veut dire « pas de seuil » et non
 * « seuil à zéro » — sans quoi tout article épuisé alerterait, y compris celui
 * qu'on ne tient délibérément pas en stock.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fournisseurs` (" +
                "`id` TEXT NOT NULL, `nom` TEXT NOT NULL, `categorie` TEXT NOT NULL, " +
                "`telephone` TEXT NOT NULL, `email` TEXT NOT NULL, " +
                "`adresse` TEXT NOT NULL, `ville` TEXT NOT NULL, " +
                "`siteCatalogue` TEXT NOT NULL, `prefere` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fournisseurs_categorie` " +
                "ON `fournisseurs` (`categorie`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `articles` (" +
                "`id` TEXT NOT NULL, `reference` TEXT NOT NULL, " +
                "`designation` TEXT NOT NULL, `fournisseurId` TEXT, " +
                "`fournisseurNom` TEXT NOT NULL, `prixAchat` REAL NOT NULL, " +
                "`prixVente` REAL NOT NULL, `unite` TEXT NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_fournisseurId` " +
                "ON `articles` (`fournisseurId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_reference` " +
                "ON `articles` (`reference`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stocks` (" +
                "`id` TEXT NOT NULL, `articleId` TEXT NOT NULL, `lieu` TEXT NOT NULL, " +
                "`quantite` REAL NOT NULL, `minimum` REAL NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stocks_articleId_lieu` " +
                "ON `stocks` (`articleId`, `lieu`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stocks_articleId` ON `stocks` (`articleId`)",
        )
    }
}

/**
 * Le prix dégressif par rang d'unité.
 *
 * Une table, un index, aucune colonne ajoutée — et c'est le signe que le
 * catalogue s'y prêtait : le prix du rang 1 est déjà `prestations.prixUnitaire`,
 * et un palier `aPartirDe = 1` aurait dupliqué cette valeur pour la faire
 * diverger au premier changement de tarif.
 *
 * Le prix arrive à **zéro** comme partout ailleurs dans ce schéma : un tarif
 * inventé partirait chez un vrai client sans que personne ne l'ait relu, et zéro
 * se voit.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `paliers_prestation` (" +
                "`id` TEXT NOT NULL, `prestationId` TEXT NOT NULL, " +
                "`aPartirDe` INTEGER NOT NULL, `prixUnitaire` REAL NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_paliers_prestation_prestationId` " +
                "ON `paliers_prestation` (`prestationId`)",
        )
    }
}

/**
 * Ce qu'une intervention coûte : trois colonnes, aucune table.
 *
 * Les deux prix d'achat sont **recopiés sur la ligne** plutôt que lus dans le
 * magasin au moment du calcul, et c'est la même règle que le taux de TVA d'une
 * facture : une intervention de mars doit rester chiffrable en mars. Une hausse
 * du tarif fournisseur en juin ne doit pas réécrire la marge qu'on a réellement
 * faite — sur un fluide dont le prix a doublé en un an, l'écart n'est pas
 * anecdotique.
 *
 * `coutHoraireInterne` arrive à **zéro**, qui veut dire « non renseigné » et non
 * « une heure ne coûte rien ». C'est le même choix que le taux de pénalités, et
 * pour la même raison : un chiffre inventé ici donnerait une marge fausse, et une
 * marge fausse se paie sur la tarification de l'année suivante. L'écran réclame
 * la valeur plutôt que d'afficher un résultat flatteur.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `pieces_posees` ADD COLUMN `prixAchat` REAL NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE `mouvements_fluide` ADD COLUMN `prixAchatKg` REAL NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE `parametres` ADD COLUMN `coutHoraireInterne` REAL NOT NULL DEFAULT 0",
        )
    }
}

/**
 * Le matériel chiffré sur un devis, et ce qu'il rapporte.
 *
 * Trois colonnes, aucune table : le magasin existait déjà, le catalogue de
 * prestations aussi, et il ne manquait que la porte entre le magasin et le
 * devis — plus de quoi savoir ce que la ligne a coûté une fois posée.
 *
 * `prixAchat` est ajouté aux **deux** tables de lignes, et pas seulement à celle
 * des devis. Un devis accepté devient une facture en recopiant ses lignes ; sans
 * la colonne côté facture, le coût se serait perdu exactement au moment où le
 * document devient celui qui compte. Zéro y veut dire « pas de matériel » ou
 * « coût inconnu », jamais « gratuit » — la marge se déclare alors non
 * chiffrable.
 *
 * `coefficientMateriel` arrive à **zéro**, qui veut dire « non réglé » et non
 * « coefficient nul » : un coefficient inventé partirait chez un vrai client
 * sans que personne ne l'ait relu, et un coefficient à zéro poserait des lignes
 * à zéro euro. Même choix que le taux de pénalités et le coût horaire interne,
 * et pour la même raison.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `lignes_devis` ADD COLUMN `prixAchat` REAL NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE `lignes_facture` ADD COLUMN `prixAchat` REAL NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE `parametres` ADD COLUMN `coefficientMateriel` REAL NOT NULL DEFAULT 0",
        )
    }
}

/**
 * Le plan de maintenance préventive : quatre tables, **six index**, aucune
 * reconstruction — et c'est le signe que le modèle s'y prêtait.
 *
 * L'index unique d'`affectations_gamme` est le **deuxième du projet**, après celui
 * des stocks, et il porte une règle plutôt qu'une optimisation : deux affectations
 * de la même gamme à la même machine donneraient deux échéances contradictoires
 * sans que rien ne dise laquelle est bonne. La base le refuse, plutôt que de
 * compter sur le dépôt pour y penser.
 *
 * Les quatre tables arrivent **vides**, et il n'y a donc aucun défaut à justifier :
 * les points d'une visite mensuelle sont ceux d'un contrat et d'un site, pas ceux
 * d'un métier, et en livrer d'office aurait fait cocher des points que personne
 * n'a relus. Même raison que la liste des types d'intervention, vide au premier
 * lancement.
 *
 * `releves_gamme.equipementId` et `gammeId` sont **nullables** à dessein : une
 * ligne de ce journal est la preuve qu'une maintenance contractuelle a été faite,
 * et supprimer une gamme ou ranger l'inventaire coupe le lien sans effacer
 * l'histoire. Les intitulés sont recopiés sur la ligne pour qu'elle reste lisible.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gammes` (" +
                "`id` TEXT NOT NULL, `libelle` TEXT NOT NULL, " +
                "`periodicite` TEXT NOT NULL, `rang` INTEGER NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `points_gamme` (" +
                "`id` TEXT NOT NULL, `gammeId` TEXT NOT NULL, " +
                "`libelle` TEXT NOT NULL, `rang` INTEGER NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_points_gamme_gammeId` " +
                "ON `points_gamme` (`gammeId`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `affectations_gamme` (" +
                "`id` TEXT NOT NULL, `equipementId` TEXT NOT NULL, " +
                "`gammeId` TEXT NOT NULL, `depuisLe` TEXT NOT NULL, " +
                "`modifieLe` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_affectations_gamme_equipementId_gammeId` " +
                "ON `affectations_gamme` (`equipementId`, `gammeId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_affectations_gamme_gammeId` " +
                "ON `affectations_gamme` (`gammeId`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `releves_gamme` (" +
                "`id` TEXT NOT NULL, `equipementId` TEXT, " +
                "`equipementNom` TEXT NOT NULL, `gammeId` TEXT, " +
                "`gammeLibelle` TEXT NOT NULL, `periodicite` TEXT NOT NULL, " +
                "`faitLe` TEXT NOT NULL, `technicienId` TEXT, " +
                "`technicienNom` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "`interventionId` TEXT, `modifieLe` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_releves_gamme_equipementId` " +
                "ON `releves_gamme` (`equipementId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_releves_gamme_gammeId` " +
                "ON `releves_gamme` (`gammeId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_releves_gamme_faitLe` " +
                "ON `releves_gamme` (`faitLe`)",
        )
    }
}

/**
 * La zone d'une machine : **une colonne, et rien d'autre**.
 *
 * C'est la plus petite migration du projet, et c'est le signe que le modèle s'y
 * prêtait : une zone est une étiquette sur une machine, pas une entité. Elle
 * n'a donc ni table, ni index — on ne cherche jamais « toutes les machines de
 * la toiture » en SQL, le parc tient en mémoire et le filtrage comme le
 * groupement se font côté Kotlin, exactement comme le tri du carnet.
 *
 * Le défaut `''` veut dire **non rangée**, et c'est ce qui rend juste tout parc
 * déjà saisi sans le toucher : une machine d'avant la migration n'est pas dans
 * une zone qui s'appellerait « vide », elle n'est dans aucune zone, et l'écran
 * la range sous « Sans zone » plutôt que de la perdre.
 *
 * L'entité, elle, **ne déclare pas** ce défaut : Room ne compare un défaut que
 * lorsqu'il vient de l'entité, si bien qu'un défaut côté base qu'aucune entité
 * ne déclare est ignoré à la validation. C'est déjà le cas des colonnes de tarif
 * de [MIGRATION_9_10] et des deux colonnes de [MIGRATION_11_12].
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `zone` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Les deux fournisseurs d'accessoires, et le catalogue qui va avec — **sans un
 * seul prix**.
 *
 * Même dispositif que [SQL_CATALOGUE_INITIAL] : deux chemins posent les mêmes
 * lignes, la migration pour un téléphone déjà garni et `onCreate` pour une
 * installation neuve, et ils partagent donc le même SQL. Identifiants stables,
 * `INSERT OR IGNORE` : l'insertion est rejouable, et restaurer une sauvegarde
 * faite ailleurs retrouve les mêmes lignes au lieu d'en créer quarante de plus.
 *
 * ## Pourquoi aucun prix, alors que c'est ce qu'on venait chercher
 *
 * Un prix de distributeur **n'est pas une donnée publique**. Ce que Richardson
 * ou GFF facturent est le tarif du fabricant moins une remise négociée qui
 * dépend du compte ; un chiffre pris ailleurs serait faux de plusieurs dizaines
 * de pour cent, dans le sens qui rogne la marge sans que rien ne le signale. Et
 * la couronne de cuivre est une **matière première** dont le prix suit le cours
 * du métal : figé ici, il serait faux avant la fin du mois. C'est la règle que
 * tout le projet applique déjà au catalogue de prestations et au GWP d'un fluide
 * inconnu — une ligne à zéro se voit et appelle une correction, un prix inventé
 * part chez un vrai client sans que personne ne l'ait relu.
 *
 * ## Ni référence, ni fournisseur par article
 *
 * [Article.reference] reste vide pour la même raison, en plus bête : une
 * référence inventée se dicte au téléphone à une agence qui ne la connaît pas,
 * et coûte l'appel. [Article.fournisseurNom] aussi, et c'est le point le moins
 * évident : les deux enseignes vendent **toutes** ces familles, si bien que
 * trancher maintenant serait afficher à l'écran une certitude qu'on n'a pas. Un
 * prix et son fournisseur arrivent ensemble — celui du tarif d'où le prix est
 * tiré —, et c'est au remplissage que les deux se posent.
 *
 * Ce qui est **su** est en revanche posé : les deux fournisseurs, avec
 * l'adresse de leur catalogue en ligne. C'est la position du projet depuis le
 * début — *le catalogue d'un fournisseur est un lien, pas un import* — et le
 * lien est ici la seule façon honnête de donner le prix du jour.
 *
 * ## La désignation porte sa famille d'abord
 *
 * `MaterielRepository.magasin` trie par désignation avec un collateur français,
 * et c'est le seul ordre que l'écran connaît. « Goulotte clim 80x60 — angle
 * plat » regroupe donc la famille, là où « Angle plat pour goulotte 80x60 »
 * l'aurait dispersée entre les A et les G. Les sections et les diamètres sont
 * ceux du commerce courant, à reprendre sur le tarif du fournisseur retenu : une
 * désignation se corrige d'un renommage, et elle se lit — au contraire d'un prix.
 */
internal const val SQL_FOURNISSEURS_ACCESSOIRES: String =
    "INSERT OR IGNORE INTO `fournisseurs` (`id`, `nom`, `categorie`, " +
    "`telephone`, `email`, `adresse`, `ville`, `siteCatalogue`, `prefere`, " +
    "`notes`, `modifieLe`) VALUES ('four-richardson', 'Richardson', 'MATERIEL', " +
    "'', '', '', '', 'https://www.richardson.fr', 0, '', 0),('four-gff', 'GFF', " +
    "'MATERIEL', '', '', '', '', 'https://www.gff.fr', 0, '', 0)"

/** Les accessoires eux-mêmes. Voir [SQL_FOURNISSEURS_ACCESSOIRES]. */
internal const val SQL_ACCESSOIRES_INITIAUX: String =
    "INSERT OR IGNORE INTO `articles` (`id`, `reference`, `designation`, " +
    "`fournisseurId`, `fournisseurNom`, `prixAchat`, `prixVente`, `unite`, " +
    "`modifieLe`) VALUES ('acc-01', '', 'Goulotte clim 60x45 — barre 2 m', " +
    "NULL, '', 0.0, 0.0, 'barre', 0),('acc-02', '', 'Goulotte clim 80x60 — " +
    "barre 2 m', NULL, '', 0.0, 0.0, 'barre', 0),('acc-03', '', 'Goulotte clim " +
    "80x60 — angle plat', NULL, '', 0.0, 0.0, 'u', 0),('acc-04', '', 'Goulotte " +
    "clim 80x60 — angle intérieur', NULL, '', 0.0, 0.0, 'u', 0),('acc-05', '', " +
    "'Goulotte clim 80x60 — angle extérieur', NULL, '', 0.0, 0.0, 'u', " +
    "0),('acc-06', '', 'Goulotte clim 80x60 — embout de finition', NULL, '', " +
    "0.0, 0.0, 'u', 0),('acc-07', '', 'Goulotte clim 80x60 — manchon de " +
    "jonction', NULL, '', 0.0, 0.0, 'u', 0),('acc-08', '', 'Goulotte clim 80x60 " +
    "— traversée de mur', NULL, '', 0.0, 0.0, 'u', 0),('acc-09', '', 'Goulotte " +
    "clim 110x75 — barre 2 m', NULL, '', 0.0, 0.0, 'barre', 0),('acc-10', '', " +
    "'Goulotte clim 110x75 — angle plat', NULL, '', 0.0, 0.0, 'u', " +
    "0),('acc-11', '', 'Goulotte clim 110x75 — angle intérieur', NULL, '', 0.0, " +
    "0.0, 'u', 0),('acc-12', '', 'Goulotte clim 110x75 — angle extérieur', " +
    "NULL, '', 0.0, 0.0, 'u', 0),('acc-13', '', 'Goulotte clim 110x75 — embout " +
    "de finition', NULL, '', 0.0, 0.0, 'u', 0),('acc-14', '', 'Goulotte clim " +
    "110x75 — manchon de jonction', NULL, '', 0.0, 0.0, 'u', 0),('acc-15', '', " +
    "'Goulotte clim 110x75 — traversée de mur', NULL, '', 0.0, 0.0, 'u', " +
    "0),('acc-16', '', 'Cuivre frigorifique 1/4 — couronne 25 m', NULL, '', " +
    "0.0, 0.0, 'couronne', 0),('acc-17', '', 'Cuivre frigorifique 3/8 — " +
    "couronne 25 m', NULL, '', 0.0, 0.0, 'couronne', 0),('acc-18', '', 'Cuivre " +
    "frigorifique 1/2 — couronne 25 m', NULL, '', 0.0, 0.0, 'couronne', " +
    "0),('acc-19', '', 'Cuivre frigorifique 5/8 — couronne 25 m', NULL, '', " +
    "0.0, 0.0, 'couronne', 0),('acc-20', '', 'Cuivre frigorifique 3/4 — " +
    "couronne 25 m', NULL, '', 0.0, 0.0, 'couronne', 0),('acc-21', '', 'Liaison " +
    "pré-isolée 1/4-3/8 — couronne 25 m', NULL, '', 0.0, 0.0, 'couronne', " +
    "0),('acc-22', '', 'Liaison pré-isolée 1/4-1/2 — couronne 25 m', NULL, '', " +
    "0.0, 0.0, 'couronne', 0),('acc-23', '', 'Liaison pré-isolée — manchon " +
    "isolant', NULL, '', 0.0, 0.0, 'm', 0),('acc-24', '', 'Pompe de relevage — " +
    "pompe seule', NULL, '', 0.0, 0.0, 'u', 0),('acc-25', '', 'Pompe de " +
    "relevage — avec réservoir', NULL, '', 0.0, 0.0, 'u', 0),('acc-26', '', " +
    "'Pompe de relevage — encastrable en goulotte', NULL, '', 0.0, 0.0, 'u', " +
    "0),('acc-27', '', 'Pompe de relevage — tube de refoulement', NULL, '', " +
    "0.0, 0.0, 'm', 0),('acc-28', '', 'Condensats — flexible annelé Ø16', NULL, " +
    "'', 0.0, 0.0, 'm', 0),('acc-29', '', 'Condensats — siphon', NULL, '', 0.0, " +
    "0.0, 'u', 0),('acc-30', '', 'Condensats — manchon de raccordement Ø16', " +
    "NULL, '', 0.0, 0.0, 'u', 0),('acc-31', '', 'Support groupe extérieur — " +
    "châssis de sol', NULL, '', 0.0, 0.0, 'u', 0),('acc-32', '', 'Support " +
    "groupe extérieur — console murale, la paire', NULL, '', 0.0, 0.0, 'paire', " +
    "0),('acc-33', '', 'Support groupe extérieur — plots antivibratiles, le " +
    "jeu', NULL, '', 0.0, 0.0, 'jeu', 0),('acc-34', '', 'Support groupe " +
    "extérieur — rail de fixation', NULL, '', 0.0, 0.0, 'u', 0),('acc-35', '', " +
    "'Finition — passe-mur et rosace', NULL, '', 0.0, 0.0, 'u', 0),('acc-36', " +
    "'', 'Finition — mastic d''étanchéité', NULL, '', 0.0, 0.0, 'u', " +
    "0),('acc-37', '', 'Fixation — collier pour tube frigorifique', NULL, '', " +
    "0.0, 0.0, 'u', 0),('acc-38', '', 'Nettoyant évaporateur et condenseur — " +
    "aérosol', NULL, '', 0.0, 0.0, 'u', 0)"

/**
 * Pose les deux fournisseurs et leurs accessoires, d'un seul endroit.
 *
 * Deux `execSQL` que la migration et `onCreate` doivent appeler tous les deux :
 * les réunir ici évite qu'un des deux chemins n'en oublie un, qui est exactement
 * l'écart qu'on ne découvre que chez l'utilisateur.
 */
internal fun poserCatalogueAccessoires(db: SupportSQLiteDatabase) {
    db.execSQL(SQL_FOURNISSEURS_ACCESSOIRES)
    db.execSQL(SQL_ACCESSOIRES_INITIAUX)
}

/**
 * Le catalogue d'accessoires : **aucune colonne, aucune table, que des lignes**.
 *
 * C'est la première migration du projet qui ne touche pas au schéma. Elle
 * n'apporte que des données, et c'est pourtant bien une migration qu'il faut :
 * `onCreate` ne s'exécute qu'à la création du fichier, si bien qu'un téléphone
 * déjà installé n'aurait jamais rien reçu. Le schéma étant inchangé, l'empreinte
 * d'identité de la base est **la même qu'en 19** — seul le numéro de version
 * bouge.
 *
 * Elle est **rejouable** et n'écrase rien : `INSERT OR IGNORE` sur des
 * identifiants stables laisse en place une ligne que le technicien aurait déjà
 * corrigée, et c'est ce qui compte ici — ces lignes sont faites pour recevoir un
 * prix, et une migration qui repasserait dessus l'effacerait.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        poserCatalogueAccessoires(db)
    }
}

/**
 * Le détecteur de fuite fixe d'une machine : **une colonne, et rien d'autre** —
 * le même cas exact que [MIGRATION_18_19].
 *
 * Le règlement 517/2014 (art. 4 § 3) double l'intervalle des contrôles
 * d'étanchéité d'une machine qui en porte un, et `PeriodiciteControle.pour`
 * savait déjà le calculer ; seul l'outil F-Gas pouvait pourtant poser la
 * question, faute de champ sur la machine. L'accueil et la fiche retenaient donc
 * toujours la périodicité la plus exigeante. Rien n'est stocké de ce que le
 * détecteur change : l'échéance reste dérivée, et la colonne ne porte que le
 * fait d'installation.
 *
 * Le défaut `0` veut dire **sans détecteur**, et c'est ce qui rend juste tout
 * parc déjà saisi sans le toucher : une machine dont personne n'a dit qu'elle
 * était équipée garde l'intervalle court. Annoncer un contrôle trop tôt fait
 * perdre une heure ; trop tard expose à une sanction — le défaut inverse aurait
 * fait reculer d'un an, d'un seul coup, toutes les échéances d'un parc.
 *
 * L'entité **ne déclare pas** ce défaut, pour la raison dite en
 * [MIGRATION_18_19] : Room ignore à la validation un défaut côté base qu'aucune
 * entité ne déclare.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `equipements` ADD COLUMN `detecteurFuite` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * L'avoir : **quatre colonnes et un index**, aucune table, rien à reconstruire.
 *
 * Un avoir vit dans `factures`, et non dans une table à lui : il obéit aux trois
 * règles d'une facture — numéroté à l'émission, insupprimable, figé —, il a des
 * lignes, une TVA et des totaux, et le mettre là fait fonctionner tout cela sans
 * en écrire une seconde copie. Voir [NatureFacture].
 *
 * Les défauts disent quelque chose, comme ceux de [MIGRATION_11_12] :
 *
 * - `nature` à **`FACTURE`**, et c'est ce qui rend justes toutes les lignes déjà
 *   en base sans les toucher : il n'existait que des factures. Un défaut vide en
 *   aurait fait des documents sans nature, que Room refuserait de relire.
 * - `factureOrigineId` **nullable** et sans défaut : `null` veut dire « ne corrige
 *   aucune facture », ce qui est le cas de toute facture.
 * - `factureOrigineNumero` à la chaîne vide et `factureOrigineEmiseLe` nullable,
 *   comme les autres copies du schéma.
 *
 * L'index sur `factureOrigineId` est aussi obligatoire que la colonne : Room
 * valide le schéma entier à l'ouverture, et l'entité le déclare — comme pour
 * `clientId`, `interventionId` et `devisId`, les trois autres liens de la table.
 *
 * L'entité **ne déclare pas** ces défauts, pour la raison déjà écrite à
 * [MIGRATION_18_19] : Room ne compare un défaut que lorsqu'il vient de l'entité.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `factures` ADD COLUMN `nature` TEXT NOT NULL DEFAULT 'FACTURE'",
        )
        db.execSQL("ALTER TABLE `factures` ADD COLUMN `factureOrigineId` TEXT")
        db.execSQL(
            "ALTER TABLE `factures` ADD COLUMN `factureOrigineNumero` " +
                "TEXT NOT NULL DEFAULT ''",
        )
        db.execSQL("ALTER TABLE `factures` ADD COLUMN `factureOrigineEmiseLe` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_factures_factureOrigineId` " +
                "ON `factures` (`factureOrigineId`)",
        )
    }
}
