package com.frigopro.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Rangement des photos dans le stockage interne de l'application.
 *
 * Les images ne vont pas en base : SQLite n'est pas un entrepôt de fichiers, et
 * une photo dans une colonne alourdit chaque lecture de la ligne. La base porte
 * le nom du fichier (voir [Photo.fichier]), les octets vivent ici, dans
 * `files/photos/`. Le dossier est privé à l'application, donc invisible de la
 * galerie et emporté par sa désinstallation — c'est pour cela que la sauvegarde
 * les embarque.
 *
 * Toute photo entrante est **réduite** avant d'être rangée (voir
 * [ReductionPhoto]) : l'original de l'appareil photo ne sert à rien, et sa
 * taille se paierait dans l'archive de sauvegarde.
 */
class StockagePhotos(private val contexte: Context) : RangementPhotos {

    /** Autorité du [FileProvider] déclaré au manifeste, par où passe l'appareil photo. */
    val autorite: String get() = "${contexte.packageName}.photos"

    private val dossier: File
        get() = File(contexte.filesDir, DOSSIER).apply { mkdirs() }

    fun fichier(nom: String): File = File(dossier, nom)

    fun existe(nom: String): Boolean = fichier(nom).isFile

    /** Tous les fichiers rangés, pour les embarquer dans une sauvegarde. */
    fun tous(): List<File> = dossier.listFiles()?.filter { it.isFile }.orEmpty()

    /**
     * Prépare le fichier qu'une application d'appareil photo viendra remplir.
     *
     * Elle écrit dans *notre* dossier par un [FileProvider] : l'image ne
     * transite pas par la galerie, où elle n'a rien à faire, et aucune
     * permission de stockage n'est nécessaire.
     */
    override fun preparerCapture(): Capture {
        val nom = nomNeuf()
        val cible = fichier(nom)
        return Capture(nom, FileProvider.getUriForFile(contexte, autorite, cible))
    }

    /**
     * Réduit la photo que l'appareil vient d'écrire. Renvoie `null` si rien
     * d'exploitable n'est arrivé — prise de vue abandonnée, fichier vide — après
     * avoir fait le ménage.
     */
    override suspend fun finaliserCapture(nom: String): String? = withContext(Dispatchers.IO) {
        if (reduire(fichier(nom))) nom else null.also { effacer(nom) }
    }

    /**
     * Recopie dans le dossier l'image désignée par l'utilisateur dans la
     * galerie, puis la réduit. Renvoie son nom, ou `null` si elle est illisible.
     *
     * La recopie est indispensable : l'URI du sélecteur n'est valable que le
     * temps de l'écran, et la photo doit rester lisible dans six mois.
     */
    override suspend fun importer(source: Uri): String? = withContext(Dispatchers.IO) {
        val nom = nomNeuf()
        val copie = try {
            contexte.contentResolver.openInputStream(source)?.use { flux ->
                ecrireFlux(nom, flux)
            } ?: false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (copie && reduire(fichier(nom))) nom else null.also { effacer(nom) }
    }

    /**
     * Range un fichier venu d'une archive de sauvegarde, sans le réduire : il
     * l'a déjà été avant d'y entrer.
     *
     * Le nom vient d'un fichier choisi par l'utilisateur, donc de nulle part :
     * [nomSur] est ce qui empêche une entrée malicieusement nommée
     * `../databases/frigopro.db` d'écrire ailleurs que dans le dossier.
     */
    override suspend fun enregistrerImage(image: Bitmap): String? = withContext(Dispatchers.IO) {
        val nom = nomNeuf()
        try {
            FileOutputStream(fichier(nom)).use { sortie ->
                // PNG plutôt que JPEG : un trait noir sur blanc que le JPEG
                // entourerait d'un halo, pour un fichier qui n'est pas plus
                // petit à cette taille-là.
                image.compress(Bitmap.CompressFormat.PNG, 100, sortie)
            }
            nom
        } catch (_: IOException) {
            effacer(nom)
            null
        }
    }

    suspend fun restaurer(nom: String, flux: InputStream): Boolean = withContext(Dispatchers.IO) {
        val sur = nomSur(nom) ?: return@withContext false
        ecrireFlux(sur, flux)
    }

    /** Oublie la photo. Un fichier absent n'est pas une erreur : le but est qu'il n'y soit plus. */
    override suspend fun supprimer(nom: String) {
        withContext(Dispatchers.IO) { effacer(nom) }
    }

    /**
     * Décode la photo à la taille demandée, ou `null` si le fichier manque.
     *
     * Le grand côté voulu est un argument parce qu'une vignette de liste et une
     * photo plein écran n'ont pas à coûter la même mémoire.
     */
    override suspend fun charger(nom: String, coteMax: Int): Bitmap? = withContext(Dispatchers.IO) {
        val source = fichier(nom)
        if (!source.isFile) return@withContext null
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bornes)
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ReductionPhoto.facteurEchantillonnage(bornes.outWidth, bornes.outHeight, coteMax)
        }
        BitmapFactory.decodeFile(source.path, options)
    }

    private fun nomNeuf(): String = "${UUID.randomUUID()}.jpg"

    private fun effacer(nom: String) {
        nomSur(nom)?.let { fichier(it).delete() }
    }

    private fun ecrireFlux(nom: String, flux: InputStream): Boolean = try {
        FileOutputStream(fichier(nom)).use { sortie -> flux.copyTo(sortie) }
        true
    } catch (_: IOException) {
        false
    }

    /**
     * Réduit l'image sur place. Renvoie `false` si elle n'est pas décodable,
     * auquel cas le fichier est laissé tel quel : c'est à l'appelant de décider
     * s'il le garde.
     */
    private fun reduire(source: File): Boolean {
        if (!source.isFile || source.length() == 0L) return false
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bornes)
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return false
        val options = BitmapFactory.Options().apply {
            inSampleSize = ReductionPhoto.facteurEchantillonnage(bornes.outWidth, bornes.outHeight)
        }
        val decodee = BitmapFactory.decodeFile(source.path, options) ?: return false
        val redressee = redresser(decodee, rotation(source))
        val (largeur, hauteur) = ReductionPhoto.dimensionsReduites(redressee.width, redressee.height)
        val finale = if (largeur == redressee.width && hauteur == redressee.height) {
            redressee
        } else {
            Bitmap.createScaledBitmap(redressee, largeur, hauteur, true)
        }
        val provisoire = File(source.parentFile, "${source.name}.tmp")
        val ecrite = try {
            FileOutputStream(provisoire).use { sortie ->
                finale.compress(Bitmap.CompressFormat.JPEG, QUALITE, sortie)
            }
        } catch (_: IOException) {
            false
        }
        listOf(decodee, redressee, finale).distinct().forEach { it.recycle() }
        if (!ecrite) {
            provisoire.delete()
            return false
        }
        // `renameTo` échoue si la cible existe : on la retire d'abord, les deux
        // fichiers étant dans le même dossier.
        source.delete()
        return provisoire.renameTo(source)
    }

    /**
     * L'appareil photo n'oriente pas toujours les pixels : il note l'orientation
     * dans l'EXIF et laisse l'afficheur tourner l'image. Comme on réencode — et
     * que le nouveau fichier n'aura pas cet EXIF — il faut tourner les pixels
     * maintenant, sans quoi toutes les plaques s'afficheraient couchées.
     *
     * `android.media.ExifInterface` plutôt que celle d'AndroidX : la plateforme
     * la fournit depuis l'API 24 et lit le JPEG, ce qui suffit ici et épargne
     * une dépendance.
     */
    private fun rotation(source: File): Int = try {
        val orientation = ExifInterface(source.path)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: IOException) {
        0
    }

    private fun redresser(image: Bitmap, degres: Int): Bitmap {
        if (degres == 0) return image
        val matrice = Matrix().apply { postRotate(degres.toFloat()) }
        return Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrice, true)
    }

    companion object {

        private const val DOSSIER = "photos"

        /** 85 : au-delà le fichier grossit sans qu'une plaque se lise mieux. */
        private const val QUALITE = 85

        /**
         * Un nom de fichier, et rien d'autre : ni chemin, ni `..`. Seul rempart
         * contre une archive qui chercherait à écrire hors du dossier.
         */
        fun nomSur(nom: String): String? = nom.takeIf { it.length <= 128 && it.matches(MOTIF_NOM) }

        /**
         * Le premier caractère ne peut pas être un point, ce qui écarte `.` et
         * `..` : un point suffit à désigner un dossier, et `..` à en sortir.
         */
        private val MOTIF_NOM = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*")
    }
}
