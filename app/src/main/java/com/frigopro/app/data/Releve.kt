package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Un relevé frigorifique pris pendant une intervention.
 *
 * Les quatre grandeurs sont celles qu'on lit au manifold et à la pince : deux
 * pressions et deux écarts de température. Ce sont elles qui, croisées, disent
 * où chercher — voir [Depannage].
 *
 * Toutes facultatives, et c'est essentiel : un relevé se remplit au fur et à
 * mesure, une case à la fois, entre deux manipulations. Exiger les quatre pour
 * enregistrer ferait tout perdre au premier appel téléphonique.
 *
 * Le relevé porte [equipementId] en plus de [interventionId] alors que
 * l'intervention désigne déjà la machine. Ce doublon est voulu : c'est lui qui
 * rend la tendance de la fiche machine — « surchauffe en hausse sur les six
 * dernières visites » — lisible en une requête, et qui la préserve si
 * l'intervention venait à perdre son lien vers la machine.
 *
 * @param bpBar basse pression, en bar relatifs.
 * @param hpBar haute pression, en bar relatifs.
 * @param surchauffeK surchauffe à l'aspiration, en kelvins.
 * @param sousRefroidissementK sous-refroidissement en sortie de condenseur.
 * @param releveLe horodatage, qui ordonne les relevés d'une même intervention.
 */
@Entity(
    tableName = "releves",
    indices = [Index("interventionId"), Index("equipementId")],
)
data class Releve(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val interventionId: String,
    val equipementId: String? = null,
    val bpBar: Double? = null,
    val hpBar: Double? = null,
    val surchauffeK: Double? = null,
    val sousRefroidissementK: Double? = null,
    val releveLe: Instant = Instant.EPOCH,
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Aucune des quatre grandeurs n'est renseignée. */
    val vide: Boolean
        get() = bpBar == null && hpBar == null &&
            surchauffeK == null && sousRefroidissementK == null
}

/**
 * Sens d'un mouvement de fluide.
 *
 * Le vocabulaire est celui du registre : on *ajoute* de la charge, on
 * *récupère* du fluide. Ni « entrée » ni « sortie », qui inverseraient le
 * point de vue selon qu'on parle de la machine ou de la bouteille.
 */
enum class SensFluide(val libelle: String) {
    AJOUT("Ajouté"),
    RECUPERATION("Récupéré"),
}

/**
 * Un mouvement de fluide frigorigène, consigné.
 *
 * Ce n'est pas une commodité d'affichage : la traçabilité des fluides est une
 * obligation réglementaire, et c'est cette table qui permet de sortir le
 * registre demandé en cas de contrôle. Chaque ligne dit qui a mis ou repris
 * quoi, où, et quand.
 *
 * Le fluide est recopié sur le mouvement plutôt que lu sur la machine : une
 * machine reconvertie du R404A au R449A ne doit pas réécrire l'histoire de ce
 * qu'on y a mis avant.
 *
 * @param masseKg masse en kilogrammes, toujours positive — c'est [sens] qui
 *   porte la direction. Un nombre signé inviterait à des additions fausses.
 */
@Entity(
    tableName = "mouvements_fluide",
    indices = [Index("interventionId"), Index("equipementId")],
)
data class MouvementFluide(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val interventionId: String,
    val equipementId: String? = null,
    val fluide: String,
    val sens: SensFluide,
    val masseKg: Double,
    val le: Instant = Instant.EPOCH,
    val modifieLe: Instant = Instant.EPOCH,
)

/**
 * Une pièce posée pendant une intervention.
 *
 * La référence est séparée de la désignation parce qu'elles ne servent pas au
 * même moment : la désignation se lit sur le compte-rendu du client, la
 * référence se recopie dans une commande.
 *
 * @param prixUnitaire prix unitaire hors taxes, `null` quand il n'est pas
 *   connu sur place — cas courant, la pièce étant souvent chiffrée au bureau.
 */
@Entity(
    tableName = "pieces_posees",
    indices = [Index("interventionId")],
)
data class PiecePosee(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val interventionId: String,
    val designation: String,
    val reference: String = "",
    val quantite: Double = 1.0,
    val prixUnitaire: Double? = null,
    val modifieLe: Instant = Instant.EPOCH,
)
