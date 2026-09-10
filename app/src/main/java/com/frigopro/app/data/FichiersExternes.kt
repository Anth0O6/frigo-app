package com.frigopro.app.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

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
     * Le mode `wt` tronque le fichier avant d'écrire : sans lui, réécrire par-dessus
     * une sauvegarde plus longue en laisserait la fin derrière, et le JSON obtenu
     * serait illisible.
     */
    suspend fun ecrire(uri: Uri, contenu: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val flux = resolver.openOutputStream(uri, "wt") ?: return@withContext false
            flux.use { it.write(contenu.toByteArray()) }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** `null` quand le fichier ne peut pas être lu. */
    suspend fun lire(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
