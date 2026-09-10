package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Un type d'intervention, tel que le technicien le nomme.
 *
 * La liste n'est pas figée dans le code et démarre vide : « fuite de fluide »
 * et « entretien préventif » sont le vocabulaire d'un métier, pas celui d'une
 * entreprise. Chacun construit le sien.
 *
 * Renommer un type met à jour l'intitulé recopié sur les interventions qui le
 * désignent (voir [Intervention.typeLibelle]) : corriger une faute la corrige
 * partout, y compris dans les tournées passées.
 *
 * @param modifieLe voir [Intervention.modifieLe] : même rôle, même usage futur.
 */
@Entity(tableName = "types_intervention")
data class TypeIntervention(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val libelle: String,
    val modifieLe: Instant = Instant.EPOCH,
)
