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
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `statut` TEXT NOT NULL DEFAULT 'A_FAIRE'")
        db.execSQL("ALTER TABLE `interventions` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
    }
}
