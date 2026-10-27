package com.frigopro.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * La seule classe qui lit le texte d'un document, même motif que
 * `LecteurChamp` pour le magnétomètre : ce qui décide de quelque chose vit dans
 * [LectureTarif] et s'éprouve sans téléphone.
 *
 * **Un seul chemin pour la photo et le PDF** : chaque page d'un PDF est dessinée
 * en image, puis lue comme une photo. Lire le texte d'un PDF directement aurait
 * demandé une bibliothèque de plus, et n'aurait servi à rien sur un devis
 * scanné — celui que l'agence envoie le plus souvent.
 *
 * La reconnaissance tourne **sur l'appareil**, modèle embarqué dans l'APK : un
 * tarif négocié n'a rien à faire sur un serveur, et une réserve sans réseau doit
 * pouvoir lire un devis.
 */
class LecteurDevis(private val contexte: Context) : LecteurTarif {

    override suspend fun lire(adresse: String): List<String>? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(adresse)
        val type = contexte.contentResolver.getType(uri).orEmpty()
        val images = runCatching {
            if (type == "application/pdf") pagesDuPdf(uri) else listOf(InputImage.fromFilePath(contexte, uri))
        }.getOrNull() ?: return@withContext null

        val reconnaissance = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            // Les pages se suivent : leurs lignes aussi, sans se mêler — chaque
            // page est remise en lignes à part, ses hauteurs ne valant que pour elle.
            images.flatMap { image ->
                val texte = reconnaitre(reconnaissance, image) ?: return@flatMap emptyList()
                LectureTarif.rangees(fragmentsDe(texte))
            }
        } finally {
            reconnaissance.close()
        }
    }

    private suspend fun reconnaitre(
        reconnaissance: com.google.mlkit.vision.text.TextRecognizer,
        image: InputImage,
    ): Text? = suspendCancellableCoroutine { suite ->
        reconnaissance.process(image)
            .addOnSuccessListener { suite.resume(it) }
            .addOnFailureListener { suite.resume(null) }
    }

    /** Chaque ligne reconnue, avec sa place : c'est sa hauteur qui la remettra dans sa rangée. */
    private fun fragmentsDe(texte: Text): List<Fragment> =
        texte.textBlocks.flatMap { bloc ->
            bloc.lines.mapNotNull { ligne ->
                val cadre = ligne.boundingBox ?: return@mapNotNull null
                Fragment(
                    texte = ligne.text,
                    gauche = cadre.left.toFloat(),
                    haut = cadre.top.toFloat(),
                    bas = cadre.bottom.toFloat(),
                )
            }
        }

    /**
     * Les pages d'un PDF, dessinées assez grand pour que les petits caractères
     * d'un tableau restent lisibles, et sur fond blanc : le fond transparent
     * d'une page se lirait noir, et du texte noir sur du noir ne se lit pas.
     */
    private fun pagesDuPdf(uri: Uri): List<InputImage> {
        val descripteur = contexte.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        return descripteur.use { fd ->
            PdfRenderer(fd).use { pdf ->
                (0 until minOf(pdf.pageCount, PAGES_MAXIMUM)).map { i ->
                    pdf.openPage(i).use { page ->
                        val echelle = LARGEUR_RENDU.toFloat() / page.width
                        val image = Bitmap.createBitmap(
                            LARGEUR_RENDU,
                            (page.height * echelle).toInt(),
                            Bitmap.Config.ARGB_8888,
                        )
                        image.eraseColor(Color.WHITE)
                        page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        InputImage.fromBitmap(image, 0)
                    }
                }
            }
        }
    }

    private companion object {
        /** Près de 250 points par pouce sur une page A4 : assez pour un corps 7. */
        const val LARGEUR_RENDU = 2_000

        /** Un devis de dix pages est déjà long ; au-delà, c'est un catalogue. */
        const val PAGES_MAXIMUM = 10
    }
}
