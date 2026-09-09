package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Un client du carnet, réutilisable d'une intervention à l'autre.
 *
 * Volontairement réduit au nom et à la ville : l'adresse et le téléphone
 * attendront d'avoir un écran où les saisir. Une colonne morte coûte plus cher
 * qu'une migration, maintenant que celles-ci sont outillées et testées.
 *
 * @param modifieLe voir [Intervention.modifieLe] : même rôle, même usage futur.
 */
@Entity(tableName = "clients")
data class Client(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nom: String,
    val ville: String,
    val modifieLe: Instant = Instant.EPOCH,
)
