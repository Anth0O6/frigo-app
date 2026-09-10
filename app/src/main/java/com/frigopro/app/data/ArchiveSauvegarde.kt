package com.frigopro.app.data

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * L'archive de sauvegarde : le JSON et les photos dans un seul fichier.
 *
 * Depuis que les machines portent des images, une sauvegarde qui ne
 * contiendrait que du texte serait un piège — on croirait tout avoir sauvé, et
 * un téléphone perdu emporterait les plaques signalétiques. L'archive est donc
 * un `.zip` ordinaire, ouvrable sur n'importe quel ordinateur, où le JSON reste
 * un fichier lisible à l'oeil.
 *
 * Elle contient `sauvegarde.json` à sa racine et les images dans `photos/`.
 *
 * Le JSON est écrit **en premier**, ce qui permet de le relire seul, sans
 * toucher aux images, et donc de refuser un fichier douteux avant d'avoir rien
 * écrit sur le téléphone.
 *
 * Ni Android ni Room ici : cette classe s'éprouve sur la JVM.
 */
object ArchiveSauvegarde {

    const val ENTREE_JSON = "sauvegarde.json"

    private const val DOSSIER_PHOTOS = "photos/"

    /** « PK » : les quatre premiers octets de tout fichier zip. */
    private val SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun ecrire(sortie: OutputStream, json: String, photos: List<File>) {
        ZipOutputStream(sortie).use { archive ->
            archive.putNextEntry(ZipEntry(ENTREE_JSON))
            archive.write(json.toByteArray())
            archive.closeEntry()
            photos.filter { it.isFile }.forEach { photo ->
                archive.putNextEntry(ZipEntry(DOSSIER_PHOTOS + photo.name))
                photo.inputStream().use { it.copyTo(archive) }
                archive.closeEntry()
            }
        }
    }

    /**
     * Distingue une archive d'une sauvegarde de l'ancien temps, restée du JSON
     * en clair. Le flux doit gérer `mark` — un [BufferedInputStream] suffit —
     * puisqu'on le rend intact à l'appelant.
     */
    fun estUneArchive(flux: BufferedInputStream): Boolean {
        flux.mark(SIGNATURE.size)
        val debut = ByteArray(SIGNATURE.size)
        val lus = flux.read(debut)
        flux.reset()
        return lus == SIGNATURE.size && debut.contentEquals(SIGNATURE)
    }

    /** Le JSON de l'archive, ou `null` s'il n'y en a pas. */
    fun lireJson(flux: InputStream): String? {
        val archive = ZipInputStream(flux)
        var entree: ZipEntry? = archive.nextEntry
        while (entree != null) {
            if (entree.name == ENTREE_JSON) return archive.readBytes().decodeToString()
            entree = archive.nextEntry
        }
        return null
    }

    /**
     * Confie chaque photo de l'archive à [accueil], qui la range où il veut.
     *
     * Le nom transmis est celui du fichier, débarrassé du dossier : c'est
     * [StockagePhotos] qui décide où il atterrit, et qui vérifie qu'un nom
     * bricolé ne le fasse pas écrire ailleurs.
     */
    suspend fun extrairePhotos(flux: InputStream, accueil: suspend (String, InputStream) -> Unit) {
        val archive = ZipInputStream(flux)
        var entree: ZipEntry? = archive.nextEntry
        while (entree != null) {
            val nom = entree.name
            if (!entree.isDirectory && nom.startsWith(DOSSIER_PHOTOS)) {
                accueil(nom.removePrefix(DOSSIER_PHOTOS), NonFermable(archive))
            }
            entree = try {
                archive.nextEntry
            } catch (_: IOException) {
                null
            }
        }
    }

    /**
     * Le flux d'une entrée est celui de l'archive entière : le fermer
     * interromprait la lecture des entrées suivantes. Cette enveloppe laisse
     * l'appelant écrire son `use` sans conséquence.
     */
    private class NonFermable(private val source: InputStream) : InputStream() {

        override fun read(): Int = source.read()

        override fun read(destination: ByteArray, decalage: Int, longueur: Int): Int =
            source.read(destination, decalage, longueur)

        override fun available(): Int = source.available()

        override fun close() = Unit
    }
}
