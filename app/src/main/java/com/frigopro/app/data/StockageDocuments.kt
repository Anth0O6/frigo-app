package com.frigopro.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Les documents produits pour être envoyés : les devis en PDF.
 *
 * Ils vivent dans `cacheDir/documents/` et non dans `filesDir`, à l'inverse des
 * photos, et la différence est volontaire : un PDF est **dérivé**. Il se
 * reconstruit à l'identique depuis le devis, ne doit donc pas entrer dans
 * l'archive de sauvegarde — qui grossirait d'autant sans rien apporter — et le
 * système peut le supprimer quand la place manque, ce qui est exactement le
 * traitement qu'on veut pour un fichier déjà envoyé.
 *
 * Un nom fixe par devis, et non un nom unique : réexporter le même devis écrase
 * l'ancien PDF plutôt que d'en empiler cinq versions dont on ne saurait plus
 * laquelle est partie chez le client.
 */
class StockageDocuments(private val contexte: Context) {

    /**
     * La même autorité que les photos.
     *
     * Son nom dit « photos » parce qu'elle n'a longtemps servi qu'à elles ; en
     * déclarer une seconde pour deux dossiers du même `cacheDir` n'apporterait
     * rien qu'une entrée de plus au manifeste. Ce qui compte est que les chemins
     * exposés restent énumérés dans `xml/chemins_fichiers.xml`, et que la base de
     * données n'en fasse pas partie.
     */
    private val autorite: String get() = "${contexte.packageName}.photos"

    private val dossier: File
        get() = File(contexte.cacheDir, DOSSIER).apply { mkdirs() }

    fun fichier(nom: String): File = File(dossier, nom)

    /**
     * L'URI à remettre à une autre application pour qu'elle lise le document.
     *
     * Le partage passe par un [FileProvider] et non par un chemin : depuis
     * Android 7, un `file://` offert à une autre application lève une exception,
     * et c'est la bonne règle — notre dossier n'a pas à être lisible de tous.
     */
    fun uri(fichier: File): Uri = FileProvider.getUriForFile(contexte, autorite, fichier)

    /** Efface les documents déjà produits : rien n'y est une donnée à garder. */
    suspend fun vider() = withContext(Dispatchers.IO) {
        dossier.listFiles()?.forEach { it.delete() }
        Unit
    }

    private companion object {

        const val DOSSIER = "documents"
    }
}
