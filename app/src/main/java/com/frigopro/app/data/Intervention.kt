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
 * @param clientId client du carnet, quand l'intervention y est rattachée.
 *   `client` et `ville` restent stockés sur la ligne : ce sont les
 *   coordonnées **au moment de l'intervention**, qu'un compte-rendu d'il y a
 *   six mois doit continuer d'afficher même si le client a été renommé.
 * @param statut avancement dans la journée.
 * @param notes observations relevées sur place.
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
    val clientId: String? = null,
    val statut: StatutIntervention = StatutIntervention.A_FAIRE,
    val notes: String = "",
    val modifieLe: Instant = Instant.EPOCH,
)

/** Avancement d'une intervention dans la journée du technicien. */
enum class StatutIntervention(val libelle: String) {
    A_FAIRE("À faire"),
    EN_COURS("En cours"),
    TERMINEE("Terminée"),
    ;

    /**
     * Statut suivant, en boucle. La carte se touche pour avancer ; repasser
     * par le début est le seul moyen de corriger une fausse manœuvre sur un
     * toit, gants aux mains, sans rouvrir le formulaire.
     */
    fun suivant(): StatutIntervention = entries[(ordinal + 1) % entries.size]
}

/** Nature de la panne signalée. */
enum class TypePanne(val libelle: String) {
    FUITE_FLUIDE("Fuite de fluide"),
    COMPRESSEUR("Compresseur"),
    REGULATION("Régulation"),
    GIVRAGE("Givrage"),
    ENTRETIEN("Entretien préventif"),
}
