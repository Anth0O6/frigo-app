package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Ce qu'une photo sert à retrouver.
 *
 * Quatre catégories, pas un album libre, parce que ces photos-là ne répondent
 * pas aux mêmes questions et ne vivent pas au même endroit :
 *
 * - [PLAQUE] et [EMPLACEMENT] décrivent un **état durable** de la machine.
 *   La plaque dit *quelle* machine c'est, l'emplacement dit *où* elle est.
 *   Elles se prennent une fois et servent à toutes les visites suivantes.
 * - [AVANT] et [APRES] décrivent **ce qui s'est passé un jour donné**. Elles
 *   appartiennent à l'intervention, pas à la machine : un évaporateur givré
 *   le 14 mai n'est pas une propriété de l'évaporateur, c'est le motif du
 *   déplacement, et c'est ce que le client doit voir sur le compte-rendu.
 *
 * Ranger les quatre dans la même table est délibéré : ce sont les mêmes
 * fichiers, le même stockage, la même réduction, la même reprise dans
 * l'archive de sauvegarde. Seul leur point d'accroche diffère, d'où les deux
 * identifiants facultatifs.
 */
enum class CategoriePhoto(val libelle: String, val surIntervention: Boolean) {
    PLAQUE("Plaque signalétique", surIntervention = false),
    EMPLACEMENT("Emplacement", surIntervention = false),
    AVANT("Avant", surIntervention = true),
    APRES("Après", surIntervention = true),
    ;

    companion object {

        /** Les deux catégories d'une fiche machine. */
        val deMachine: List<CategoriePhoto> get() = entries.filterNot { it.surIntervention }

        /** Les deux catégories d'une intervention. */
        val dIntervention: List<CategoriePhoto> get() = entries.filter { it.surIntervention }
    }
}

/**
 * Une photo, rattachée soit à une machine, soit à une intervention.
 *
 * Exactement l'un des deux identifiants est renseigné, ce que la catégorie
 * détermine ([CategoriePhoto.surIntervention]). SQLite ne sait pas exprimer
 * cette contrainte-là simplement ; c'est [EquipementRepository] qui la tient,
 * et les tests qui la vérifient.
 *
 * @param fichier nom du fichier image dans le stockage interne de
 *   l'application (voir [StockagePhotos]). La base ne porte que le nom : le
 *   chemin absolu du dossier de données change d'une installation à l'autre, et
 *   une sauvegarde restaurée sur un autre téléphone doit retrouver ses images.
 * @param priseLe horodatage de l'ajout, qui donne l'ordre d'affichage — et,
 *   pour une photo d'intervention, la preuve de quand elle a été prise.
 * @param legende ce que la photo montre, en quelques mots. Facultative :
 *   l'exiger ferait renoncer à photographier.
 */
@Entity(
    tableName = "photos",
    indices = [Index("equipementId"), Index("interventionId")],
)
data class Photo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val equipementId: String? = null,
    val interventionId: String? = null,
    val categorie: CategoriePhoto,
    val fichier: String,
    val legende: String = "",
    val priseLe: Instant = Instant.EPOCH,
)
