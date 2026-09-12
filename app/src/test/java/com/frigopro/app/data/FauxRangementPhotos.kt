package com.frigopro.app.data

import android.graphics.Bitmap
import android.net.Uri

/**
 * Rangement d'images en mémoire : une simple liste de noms de fichiers.
 *
 * Il suffit à vérifier ce qui compte dans [EquipementRepository] — qu'une
 * machine supprimée emporte les fichiers de ses photos, qu'une prise de vue
 * abandonnée n'en laisse aucun — sans décoder une seule image.
 */
class FauxRangementPhotos(
    /** `false` simule une prise de vue abandonnée, donc une image illisible. */
    var captureAboutit: Boolean = true,
) : RangementPhotos {

    /** Fichiers rangés, pour les assertions. */
    val fichiers = mutableListOf<String>()

    /**
     * Un `Uri` ne s'instancie pas hors d'Android, et le dépôt ne fait que
     * relayer cet appel : ce qui mérite un test est l'enregistrement qui suit,
     * vérifiable par [finaliserCapture].
     */
    override fun preparerCapture(): Capture =
        throw UnsupportedOperationException("Une capture demande un Uri, absent de la JVM.")

    override suspend fun finaliserCapture(nom: String): String? =
        if (captureAboutit) nom.also { fichiers += it } else null

    override suspend fun importer(source: Uri): String? =
        if (captureAboutit) "importee-${fichiers.size + 1}.jpg".also { fichiers += it } else null

    /** Aucun `Bitmap` ne se fabrique hors d'Android : seul le nom rangé compte. */
    override suspend fun enregistrerImage(image: Bitmap): String? =
        if (captureAboutit) "signature-${fichiers.size + 1}.png".also { fichiers += it } else null

    override suspend fun supprimer(nom: String) {
        fichiers.remove(nom)
    }

    /** Un `Bitmap` ne se fabrique pas hors d'Android, et aucun test n'en regarde. */
    override suspend fun charger(nom: String, coteMax: Int): Bitmap? = null
}
