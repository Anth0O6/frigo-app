package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Une intervention planifiée chez un client.
 *
 * @param id identifiant stable, clé de liste côté Compose et clé primaire en
 *   base. C'est un UUID et non un compteur : deux appareils travaillant hors
 *   réseau doivent pouvoir créer des interventions sans risquer de se donner
 *   le même identifiant le jour où une synchronisation arrive.
 * @param date jour de la tournée.
 * @param heure heure de passage prévue.
 * @param client raison sociale du client.
 * @param ville commune où se déroule l'intervention.
 * @param typePanne nature de la panne signalée.
 * @param modifieLe date de dernière écriture, posée par le dépôt. Inutilisée
 *   en v1, mais c'est elle qui permettra de départager deux versions d'une
 *   même ligne lors d'une synchronisation.
 */
@Entity(
    tableName = "interventions",
    indices = [Index("date")],
)
data class Intervention(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val heure: LocalTime,
    val client: String,
    val ville: String,
    val typePanne: TypePanne,
    val modifieLe: Instant = Instant.EPOCH,
)

/** Nature de la panne signalée. */
enum class TypePanne(val libelle: String) {
    FUITE_FLUIDE("Fuite de fluide"),
    COMPRESSEUR("Compresseur"),
    REGULATION("Régulation"),
    GIVRAGE("Givrage"),
    ENTRETIEN("Entretien préventif"),
}
