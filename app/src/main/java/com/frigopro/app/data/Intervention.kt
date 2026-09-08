package com.frigopro.app.data

import java.time.LocalTime

/**
 * Une intervention planifiée dans la journée d'un technicien frigoriste.
 *
 * @param id identifiant stable, utilisé comme clé de liste par Compose.
 * @param heure heure de passage prévue chez le client.
 * @param client raison sociale du client.
 * @param ville commune où se déroule l'intervention.
 * @param typePanne nature de la panne signalée.
 */
data class Intervention(
    val id: Long,
    val heure: LocalTime,
    val client: String,
    val ville: String,
    val typePanne: TypePanne,
)

/** Nature de la panne signalée, utilisée pour trier et colorer la liste. */
enum class TypePanne(val libelle: String) {
    FUITE_FLUIDE("Fuite de fluide"),
    COMPRESSEUR("Compresseur"),
    REGULATION("Régulation"),
    GIVRAGE("Givrage"),
    ENTRETIEN("Entretien préventif"),
}
