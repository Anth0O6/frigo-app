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
