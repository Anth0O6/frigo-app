package com.frigopro.app.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Lecture et écriture des fichiers que l'utilisateur désigne lui-même, par le
 * sélecteur du système.
 *
 * Passer par ce sélecteur évite toute permission de stockage : l'application
 * n'accède qu'au fichier qu'on lui a montré, et l'utilisateur choisit où sa
 * sauvegarde atterrit — sa mémoire, une carte, un espace en ligne.
 */
class FichiersExternes(private val resolver: ContentResolver) {

    /**
     * Écrit l'archive de sauvegarde.
     *
     * Le mode `wt` tronque le fichier avant d'écrire : sans lui, réécrire
     * par-dessus une sauvegarde plus longue en laisserait la fin derrière, et
     * l'archive obtenue serait illisible.
     */
    suspend fun ecrireArchive(uri: Uri, json: String, photos: List<File>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val flux = resolver.openOutputStream(uri, "wt") ?: return@withContext false
                flux.use { ArchiveSauvegarde.ecrire(it, json, photos) }
                true
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }

    /**
     * Le JSON de la sauvegarde, qu'elle soit une archive ou un fichier texte
     * exporté par une version antérieure. `null` si le fichier est illisible ou
     * n'en contient pas.
     */
    suspend fun lireSauvegarde(uri: Uri): String? = withContext(Dispatchers.IO) {
        ouvrir(uri) { flux ->
            if (ArchiveSauvegarde.estUneArchive(flux)) {
                ArchiveSauvegarde.lireJson(flux)
            } else {
                flux.readBytes().decodeToString()
            }
        }
    }

    /**
     * Confie à [accueil] chaque photo de l'archive.
     *
     * Seconde lecture du même fichier, volontairement : la première sert à
     * valider le JSON, et rien ne doit être écrit sur le téléphone avant qu'il
     * le soit. Un fichier texte n'a pas de photos, la lecture ne donne alors
     * rien — ce qui est exact.
     */
    suspend fun extrairePhotos(uri: Uri, accueil: suspend (String, InputStream) -> Unit) {
        withContext(Dispatchers.IO) {
            ouvrir(uri) { flux ->
                if (ArchiveSauvegarde.estUneArchive(flux)) {
                    ArchiveSauvegarde.extrairePhotos(flux, accueil)
                }
            }
        }
    }

    private suspend fun <T> ouvrir(uri: Uri, lecture: suspend (BufferedInputStream) -> T): T? = try {
        resolver.openInputStream(uri)?.let { flux -> BufferedInputStream(flux).use { lecture(it) } }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
