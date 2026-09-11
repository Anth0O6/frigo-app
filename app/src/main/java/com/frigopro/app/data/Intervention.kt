package com.frigopro.app.data

import androidx.room.Embedded
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
 * @param typeId type d'intervention choisi dans la liste du technicien, ou
 *   `null` quand l'intitulé ne vient pas d'elle — une intervention saisie
 *   avant que la liste existe, ou dont le type a été supprimé depuis.
 * @param typeLibelle intitulé du type, recopié sur la ligne. Renommer un type
 *   met cette copie à jour partout où il est employé ; la copie sert à
 *   afficher quelque chose même sans lien, et à survivre à la suppression du
 *   type. Vide tant qu'aucun type n'est choisi, ce qui est permis : exiger un
 *   type alors que la liste démarre vide interdirait la première saisie.
 * @param equipementId machine du parc du client sur laquelle on intervient, ou
 *   `null` quand l'intervention n'en désigne aucune — celle d'avant le parc,
 *   celle dont la machine a été retirée, ou simplement une visite qui ne porte
 *   sur aucune machine en particulier.
 * @param equipementNom nom de la machine, recopié sur la ligne, pour les mêmes
 *   raisons que [typeLibelle] : renommer la machine corrige ses tournées
 *   passées, et la copie reste quand la fiche disparaît.
 * @param clientId client du carnet, quand l'intervention y est rattachée.
 *   `client` et `ville` restent stockés sur la ligne : ce sont les
 *   coordonnées **au moment de l'intervention**, qu'un compte-rendu d'il y a
 *   six mois doit continuer d'afficher même si le client a été renommé.
 * @param statut avancement dans la journée.
 * @param notes observations relevées sur place.
 * @param urgente intervention à traiter en priorité. Distincte du statut :
 *   une urgence reste une urgence une fois terminée, et c'est ce qui permet
 *   de compter les urgences d'une journée après coup.
 * @param chrono temps réellement passé sur place. Voir [Chrono] : le calcul
 *   vit là, la ligne n'en porte que les trois horodatages.
 * @param numero référence du compte-rendu, de la forme `INT-2405-018`. Vide
 *   tant qu'aucun compte-rendu n'a été établi : une intervention planifiée
 *   puis annulée n'a aucune raison de consommer un numéro.
 * @param signatureFichier image de la signature du client, rangée comme les
 *   photos et nommée de la même façon. `null` tant que rien n'est signé.
 * @param signeeLe horodatage de la signature, qui vaut acceptation.
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
    val typeId: String? = null,
    val typeLibelle: String = "",
    val clientId: String? = null,
    val equipementId: String? = null,
    val equipementNom: String = "",
    val statut: StatutIntervention = StatutIntervention.A_FAIRE,
    val notes: String = "",
    val urgente: Boolean = false,
    @Embedded val chrono: Chrono = Chrono(),
    val numero: String = "",
    val signatureFichier: String? = null,
    val signeeLe: Instant? = null,
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
