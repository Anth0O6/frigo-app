package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Ce qu'une photo de machine sert à retrouver.
 *
 * Deux catégories et pas un album libre : ces deux photos-là ne répondent pas à
 * la même question. La plaque dit *quelle* machine c'est, l'emplacement dit
 * *où* elle est — et l'une se consulte en commandant une pièce, l'autre en
 * arrivant sur place.
 */
enum class CategoriePhoto(val libelle: String) {
    PLAQUE("Plaque signalétique"),
    EMPLACEMENT("Emplacement"),
}

/**
 * Une photo rattachée à une machine.
 *
 * Elle appartient à la machine et non à l'intervention : une plaque ne change
 * pas, donc elle se photographie une fois et reste lisible depuis toutes les
 * visites suivantes. Des photos de ce qui s'est passé un jour donné sont un
 * autre besoin, qui viendra avec le compte-rendu.
 *
 * @param fichier nom du fichier image dans le stockage interne de
 *   l'application (voir [StockagePhotos]). La base ne porte que le nom : le
 *   chemin absolu du dossier de données change d'une installation à l'autre, et
 *   une sauvegarde restaurée sur un autre téléphone doit retrouver ses images.
 * @param priseLe horodatage de l'ajout, qui donne l'ordre d'affichage. La
 *   première photo de plaque est souvent la bonne ; les suivantes précisent.
 */
@Entity(
    tableName = "photos",
    indices = [Index("equipementId")],
)
data class Photo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val equipementId: String,
    val categorie: CategoriePhoto,
    val fichier: String,
    val priseLe: Instant = Instant.EPOCH,
)
