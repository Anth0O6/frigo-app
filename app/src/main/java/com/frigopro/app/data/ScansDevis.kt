package com.frigopro.app.data

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Les pages d'un devis fournisseur photographiées le temps d'être lues.
 *
 * Une interface pour que le `ViewModel` s'éprouve sans téléphone, même motif
 * que [RangementPhotos] ; la seule implémentation est [StockageScans].
 */
interface ScansDevis {

    /** Le fichier qu'une application d'appareil photo viendra remplir. */
    fun preparer(): Capture

    /** L'adresse à remettre au [LecteurTarif] pour lire ce fichier. */
    fun adresse(nom: String): String

    suspend fun effacer(nom: String)

    /** Pas d'appareil photo : ce que reçoivent les tests qui ne scannent pas. */
    object Aucun : ScansDevis {
        override fun preparer(): Capture =
            throw UnsupportedOperationException("Aucun appareil photo n'est branché.")

        override fun adresse(nom: String): String = nom

        override suspend fun effacer(nom: String) = Unit
    }
}

/**
 * Un scan vit dans `cacheDir/scans/`, **et seulement le temps de sa lecture**.
 *
 * Ni dans `files/photos/`, ni dans la base : une page de devis porte les prix
 * nets d'un compte, qui ne sont ceux de personne d'autre. Lue, elle a donné tout
 * ce qu'elle avait à donner — ses lignes sont dans la saisie — et la garder la
 * ferait partir dans la prochaine archive de sauvegarde, puis sur l'espace
 * partagé où on la dépose. Le cache est le filet : un fichier qu'un arrêt brutal
 * aurait laissé là, le système finit par le reprendre.
 */
class StockageScans(private val contexte: Context) : ScansDevis {

    /** La même autorité que les photos et les PDF : voir `StockageDocuments`. */
    private val autorite: String get() = "${contexte.packageName}.photos"

    private val dossier: File
        get() = File(contexte.cacheDir, DOSSIER).apply { mkdirs() }

    override fun preparer(): Capture {
        val nom = "${UUID.randomUUID()}.jpg"
        return Capture(nom, FileProvider.getUriForFile(contexte, autorite, File(dossier, nom)))
    }

    override fun adresse(nom: String): String =
        FileProvider.getUriForFile(contexte, autorite, File(dossier, nom)).toString()

    override suspend fun effacer(nom: String) = withContext(Dispatchers.IO) {
        File(dossier, nom).delete()
        Unit
    }

    private companion object {

        const val DOSSIER = "scans"
    }
}
