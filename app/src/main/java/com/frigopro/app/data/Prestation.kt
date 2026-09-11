package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Un technicien.
 *
 * L'application était jusqu'ici celle d'un homme seul ; la maquette montre des
 * interventions confiées à « KB » ou « ML », et c'est un vrai changement : dès
 * qu'on est deux, une tournée doit dire *à qui* elle est. Rien d'autre n'est
 * modélisé — ni compte, ni droits, ni synchronisation. Un technicien est un nom
 * qu'on pose sur une ligne, et cela suffit tant que le planning tient sur un
 * seul téléphone.
 */
@Entity(tableName = "techniciens")
data class Technicien(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nom: String,
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** « KB » pour « Karim Benali » : ce qu'affiche la pastille du planning. */
    val initiales: String get() = initialesDe(nom)
}

/**
 * Un point de la liste à cocher d'une intervention.
 *
 * La checklist n'est pas un pense-bête : trois de ses quatre points par défaut
 * sont des obligations — étanchéité contrôlée, fluide pesé et tracé, fiche
 * F-Gas signée. Les cocher est ce qui, plus tard, permettra de dire ce qui a
 * réellement été fait, et la progression affichée sur la fiche est là pour que
 * l'oubli se voie **avant** de quitter le site.
 *
 * Les points sont recopiés sur chaque intervention plutôt que référencés : le
 * modèle peut changer sans réécrire l'histoire des interventions passées, qui
 * doivent continuer de montrer ce qu'on leur avait demandé de vérifier.
 *
 * @param rang position dans la liste, pour que l'ordre du modèle survive.
 */
@Entity(
    tableName = "points_checklist",
    indices = [Index("interventionId")],
)
data class PointChecklist(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val interventionId: String,
    val libelle: String,
    val fait: Boolean = false,
    val rang: Int = 0,
    val modifieLe: Instant = Instant.EPOCH,
)

/** Famille d'une prestation du catalogue, telle que la maquette les groupe. */
enum class CategoriePrestation(val libelle: String) {
    DEPANNAGE("Dépannage"),
    MAINTENANCE("Maintenance"),
    FLUIDE("Fluide"),
    PIECES("Pièces"),
    INSTALLATION("Installation"),
}

/**
 * Une ligne du catalogue de prestations, d'où se construisent les devis.
 *
 * C'est ce qui fait qu'un devis se chiffre sur place en quelques gestes plutôt
 * qu'en tapant des intitulés au clavier, gants aux mains, sur un capot de
 * camionnette — et qu'un même travail porte le même nom et le même prix d'un
 * client à l'autre.
 *
 * **Le catalogue est livré avec ses intitulés mais sans ses prix.** La
 * distinction est celle qui vaut déjà pour les types d'intervention :
 * « recharge R-449A » ou « filtre déshydrateur » sont le vocabulaire d'un
 * métier, un tarif horaire est celui d'une entreprise. Poser un prix inventé
 * dans un catalogue serait pire qu'une case vide — il partirait chez un client
 * sans que personne ne l'ait relu.
 *
 * @param unite ce que compte la quantité : `h`, `kg`, `forfait`, `pièce`,
 *   `visite`. Affichée telle quelle sur le devis.
 */
@Entity(
    tableName = "prestations",
    indices = [Index("categorie")],
)
data class Prestation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val designation: String,
    val categorie: CategoriePrestation,
    val prixUnitaire: Double = 0.0,
    val unite: String = "",
    val rang: Int = 0,
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Un prix jamais renseigné : la ligne se voit, et appelle une correction. */
    val tarifee: Boolean get() = prixUnitaire > 0.0
}

/**
 * Le catalogue livré au premier lancement : les intitulés du métier, à zéro
 * euro.
 *
 * Repris de la maquette, moins les prix — voir [Prestation]. L'ordre est celui
 * dans lequel on les rencontre sur le terrain, pas l'alphabétique.
 */
val CATALOGUE_INITIAL: List<Triple<String, CategoriePrestation, String>> = listOf(
    Triple("Dépannage froid commercial", CategoriePrestation.DEPANNAGE, "forfait"),
    Triple("Main d'œuvre", CategoriePrestation.DEPANNAGE, "h"),
    Triple("Déplacement", CategoriePrestation.DEPANNAGE, "forfait"),
    Triple("Majoration urgence / astreinte", CategoriePrestation.DEPANNAGE, "forfait"),
    Triple("Maintenance chambre froide", CategoriePrestation.MAINTENANCE, "visite"),
    Triple("Maintenance vitrine réfrigérée", CategoriePrestation.MAINTENANCE, "visite"),
    Triple("Maintenance climatisation", CategoriePrestation.MAINTENANCE, "visite"),
    Triple("Contrôle étanchéité F-Gas", CategoriePrestation.FLUIDE, "contrôle"),
    Triple("Recharge R-449A", CategoriePrestation.FLUIDE, "kg"),
    Triple("Recharge R-134a", CategoriePrestation.FLUIDE, "kg"),
    Triple("Recharge R-32", CategoriePrestation.FLUIDE, "kg"),
    Triple("Récupération fluide", CategoriePrestation.FLUIDE, "forfait"),
    Triple("Compresseur hermétique", CategoriePrestation.PIECES, "pièce"),
    Triple("Détendeur thermostatique", CategoriePrestation.PIECES, "pièce"),
    Triple("Ventilateur évaporateur", CategoriePrestation.PIECES, "pièce"),
    Triple("Résistance de dégivrage", CategoriePrestation.PIECES, "pièce"),
    Triple("Filtre déshydrateur", CategoriePrestation.PIECES, "pièce"),
    Triple("Installation split mural", CategoriePrestation.INSTALLATION, "unité"),
    Triple("Installation chambre froide", CategoriePrestation.INSTALLATION, "forfait"),
    Triple("Pompe à chaleur air/eau", CategoriePrestation.INSTALLATION, "unité"),
    Triple("Mise en service", CategoriePrestation.INSTALLATION, "forfait"),
)

/**
 * Les points cochés par défaut sur une intervention neuve.
 *
 * Trois des quatre sont des obligations réglementaires, et c'est pour cela
 * qu'ils sont là plutôt que laissés à la mémoire.
 */
val CHECKLIST_INITIALE: List<String> = listOf(
    "Pressions HP / BP relevées",
    "Étanchéité contrôlée au détecteur",
    "Fluide pesé et tracé",
    "Fiche F-Gas signée par le client",
)
