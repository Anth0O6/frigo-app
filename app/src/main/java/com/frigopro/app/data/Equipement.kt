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
    indices = [Index("clientId"), Index("parentId")],
)
data class Equipement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val nom: String,
    /**
     * Le groupe auquel cette unité appartient, `null` pour un groupe ou une
     * machine seule.
     *
     * C'est ce qui modélise un bi-split, un tri-split ou un VRF : le groupe
     * extérieur est une machine ordinaire, et chaque unité intérieure en est une
     * autre qui le désigne. Une **hiérarchie à un seul niveau** — une unité ne
     * porte pas d'unité — parce qu'aucun matériel n'en demande deux.
     *
     * Une unité plutôt qu'un simple compteur, et c'est le point : chaque unité
     * intérieure a son emplacement (« salon », « chambre 1 »), sa plaque, ses
     * photos et son historique. Un compteur `nombreUnites` aurait été plus court
     * à écrire et aurait perdu tout cela ; surtout, il aurait fallu le tenir à
     * jour à la main, alors que le nombre se **déduit** (voir
     * [EquipementRepository.parGroupe]) et ne peut donc pas dériver.
     *
     * Sans clé étrangère, pour les raisons dites en [MIGRATION_2_3], et une de
     * plus : une restauration de sauvegarde écrit ligne après ligne, et une
     * contrainte immédiate rejetterait une unité arrivant avant son groupe.
     */
    val parentId: String? = null,
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

    /** Une unité intérieure, rattachée à un groupe. */
    val estUnite: Boolean get() = parentId != null
}

/**
 * Un groupe et ses unités intérieures.
 *
 * Ce n'est pas une entité : aucune table n'y correspond, c'est la table des
 * équipements regroupée sur `parentId` (voir [EquipementRepository.parGroupe]).
 *
 * Un monosplit, une chambre froide, un groupe de condensation sont des groupes
 * **sans unité** : l'écran n'a donc qu'un cas à dessiner, et le bi-split est
 * simplement celui où [unites] n'est pas vide.
 */
data class GroupeMachines(
    val groupe: Equipement,
    val unites: List<Equipement> = emptyList(),
) {

    /**
     * Combien d'unités intérieures compte l'installation.
     *
     * **Un au minimum**, et c'est le point : un monosplit n'a pas d'unité
     * enregistrée mais il en possède bien une, physiquement. Rendre zéro
     * ramènerait à zéro toute prestation comptée par unité — une pose de
     * monosplit chiffrée à néant, ce qui se verrait, mais seulement après avoir
     * envoyé le devis.
     */
    val nombreUnites: Int get() = maxOf(unites.size, 1)

    /** Une installation à plusieurs unités : bi-split, tri-split, VRF. */
    val multiSplit: Boolean get() = unites.size > 1

    /** « Groupe + 2 unités », ce que la fiche annonce sous le nom. */
    val resume: String
        get() = when (unites.size) {
            0 -> ""
            1 -> "1 unité intérieure"
            else -> "${unites.size} unités intérieures"
        }
}
