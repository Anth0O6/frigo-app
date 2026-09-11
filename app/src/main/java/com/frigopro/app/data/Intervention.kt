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
 *   de compter les urgences d'une journée après coup. La maquette les fond en
 *   une seule dimension ; les garder séparées est ce qui permet de dire « on a
 *   eu trois urgences cette semaine » une fois qu'elles sont toutes faites.
 * @param dureeMin durée prévue, en minutes. Sans elle, le planning ne saurait
 *   pas quelle hauteur donner à un créneau, et deux interventions qui se
 *   chevauchent ne se verraient pas.
 * @param technicienId technicien à qui la tournée est confiée, ou `null` quand
 *   personne n'est encore désigné. Même couple lien / copie que pour le type et
 *   la machine, et pour les mêmes raisons.
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
    indices = [Index("date"), Index("technicienId")],
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
    val statut: StatutIntervention = StatutIntervention.PLANIFIEE,
    val notes: String = "",
    val urgente: Boolean = false,
    val dureeMin: Int = DUREE_PAR_DEFAUT_MIN,
    val technicienId: String? = null,
    val technicienNom: String = "",
    @Embedded val chrono: Chrono = Chrono(),
    val numero: String = "",
    val signatureFichier: String? = null,
    val signeeLe: Instant? = null,
    val modifieLe: Instant = Instant.EPOCH,
)

/**
 * Avancement d'une intervention dans la journée du technicien.
 *
 * `A_VALIDER` est arrivé avec la refonte, et désigne autre chose que « pas
 * encore fait » : le travail est terminé côté technicien, mais quelque chose
 * attend le client — un devis à accepter, une fiche F-Gas à signer. La
 * distinction compte, parce qu'une intervention en attente ne se replanifie
 * pas, elle se relance.
 */
enum class StatutIntervention(val libelle: String) {
    PLANIFIEE("Planifié"),
    EN_COURS("En cours"),
    A_VALIDER("À valider"),
    TERMINEE("Terminé"),
    ;

    /** Rien n'attend plus personne. */
    val close: Boolean get() = this == TERMINEE

    /**
     * Statut suivant du cycle court, celui qu'on touche sur la carte :
     * planifié → en cours → terminé, puis on repasse au début.
     *
     * `A_VALIDER` n'y figure pas volontairement. Ce n'est pas une étape du
     * travail mais une attente côté client, et elle se pose depuis la fiche —
     * la faire traverser à chaque changement de statut la rendrait pénible
     * seize fois par jour pour le cas rare.
     */
    fun suivant(): StatutIntervention = when (this) {
        PLANIFIEE -> EN_COURS
        EN_COURS -> TERMINEE
        A_VALIDER -> TERMINEE
        TERMINEE -> PLANIFIEE
    }
}


/**
 * Durée par défaut d'une intervention, en minutes.
 *
 * Une heure : ni la visite de contrôle de vingt minutes, ni le remplacement de
 * compresseur de la journée, mais ce qui demande le moins de correction sur une
 * tournée ordinaire.
 */
const val DUREE_PAR_DEFAUT_MIN: Int = 60
