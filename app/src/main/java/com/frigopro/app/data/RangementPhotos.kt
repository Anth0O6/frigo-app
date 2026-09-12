package com.frigopro.app.data

import android.graphics.Bitmap
import android.net.Uri

/** Le fichier qu'une capture va remplir, et l'URI à confier à l'appareil photo. */
data class Capture(val nom: String, val uri: Uri)

/**
 * Ce qu'[EquipementRepository] attend du rangement des images.
 *
 * Une interface pour une seule implémentation — [StockagePhotos] — et c'est
 * assumé : le dépôt coordonne la base et les fichiers, et c'est exactement ce
 * qu'il faut pouvoir éprouver sans Android, notamment qu'une machine supprimée
 * emporte ses fichiers. Sans cette couture, ce code ne serait vérifié que sur
 * un téléphone, c'est-à-dire jamais.
 */
interface RangementPhotos {

    fun preparerCapture(): Capture

    suspend fun finaliserCapture(nom: String): String?

    suspend fun importer(source: Uri): String?

    /**
     * Range une image fabriquée par l'application elle-même — une signature
     * tracée au doigt, aujourd'hui la seule.
     *
     * Elle ne passe pas par [importer] : il n'y a ni `Uri`, ni EXIF à
     * redresser, et surtout rien à réduire — une signature est déjà petite, et
     * la recompresser abîmerait un trait fin sans rien faire gagner.
     */
    suspend fun enregistrerImage(image: Bitmap): String?

    suspend fun supprimer(nom: String)

    suspend fun charger(nom: String, coteMax: Int): Bitmap?
}
