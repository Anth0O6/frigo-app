package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Une machine du parc d'un client : vitrine, chambre froide, groupe de
 * production de froid.
 *
 * Le **nom d'usage** reste ce par quoi on la reconnaît : « vitrine salle 2 »
 * dit en trois mots ce que trois références ne disent pas, et c'est lui qu'on
 * lit dans une liste avant d'être monté voir la plaque.
 *
 * Marque, modèle et numéro de série l'accompagnent désormais. La photo de la
 * plaque signalétique reste la source de vérité — on la prend en trente
 * secondes, on ne se trompe pas en la prenant — mais un numéro de série qui
 * n'existe qu'en image ne se cherche pas, ne se copie pas dans une commande de
 * pièce et ne part pas dans un compte-rendu. Les deux se complètent : la photo
 * prouve, les champs servent. Tous facultatifs, parce qu'une plaque illisible
 * est un cas courant.
 *
 * Le fluide, lui, n'est pas une commodité : c'est de lui et de la charge que
 * découlent l'équivalent CO₂ et la périodicité des contrôles d'étanchéité
 * imposés par le règlement 517/2014. Voir [Fluides] et [EtatEtancheite].
 *
 * @param clientId client chez qui la machine se trouve. Une machine n'existe
 *   jamais seule : c'est le parc d'un client, et c'est par lui qu'on y arrive.
 * @param fluide intitulé du fluide frigorigène, forme canonique « R452A ».
 *   Vide tant qu'il n'a pas été relevé.
 * @param chargeKg charge nominale en kilogrammes, telle qu'elle figure sur la
 *   plaque. `null` quand elle est inconnue — auquel cas aucun équivalent CO₂
 *   ne peut être calculé, et l'application le dit plutôt que de supposer.
 * @param misEnServiceLe date de mise en service, qui datera l'installation
 *   dans un registre.
 * @param dernierControleLe date du dernier contrôle d'étanchéité consigné.
 *   C'est d'elle que se déduit l'échéance du suivant.
 * @param modifieLe voir [Intervention.modifieLe] : même rôle, même usage futur.
 */
@Entity(
    tableName = "equipements",
    indices = [Index("clientId")],
)
data class Equipement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val nom: String,
    val marque: String = "",
    val modele: String = "",
    val numeroSerie: String = "",
    val fluide: String = "",
    val chargeKg: Double? = null,
    val misEnServiceLe: LocalDate? = null,
    val dernierControleLe: LocalDate? = null,
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** « Bitzer 4FES-3Y », ce qui se lit sous le nom d'usage. Vide si rien n'est su. */
    val designation: String get() = listOf(marque, modele).filter { it.isNotBlank() }.joinToString(" ")
}
