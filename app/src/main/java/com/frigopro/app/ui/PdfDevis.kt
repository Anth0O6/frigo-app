package com.frigopro.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Le devis dessiné en PDF.
 *
 * La seule classe du projet à connaître `PdfDocument` et `Canvas`, comme
 * [com.frigopro.app.data.StockagePhotos] est la seule à connaître
 * `BitmapFactory` : ce qui relève du document — ce qu'il dit, où les choses
 * tombent — vit dans [DocumentDevis] et [MiseEnPageDevis], qui s'éprouvent sans
 * téléphone. Il ne reste ici que des appels de dessin.
 *
 * Aucune dépendance ajoutée. `PdfDocument` est dans Android depuis l'API 19 et
 * fonctionne hors réseau, ce qu'une bibliothèque de génération de PDF ne
 * garantirait pas — et l'application revendique de marcher au fond d'une chambre
 * froide.
 *
 * Le document sort **en noir sur blanc**, à l'inverse du thème sombre de
 * l'application : un devis s'imprime, et un fond sombre y viderait une cartouche
 * d'encre par client.
 */
object PdfDevis {

    /**
     * Écrit le devis dans [cible]. Renvoie `false` si l'écriture échoue, sans
     * laisser de fichier tronqué derrière elle — un PDF à moitié écrit s'ouvre sur
     * une erreur chez le client, ce qui est pire que pas de pièce jointe.
     */
    suspend fun ecrire(
        document: DocumentDevis,
        logo: Bitmap?,
        cible: File,
    ): Boolean = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()
        try {
            val pages = MiseEnPageDevis.paginer(
                lignes = document.lignes,
                totaux = document.totaux,
                mentions = document.mentions,
            )
            pages.forEach { page -> dessiner(pdf, document, logo, page) }
            cible.parentFile?.mkdirs()
            cible.outputStream().use { pdf.writeTo(it) }
            true
        } catch (_: IOException) {
            cible.delete()
            false
        } finally {
            pdf.close()
        }
    }

    private fun dessiner(
        pdf: PdfDocument,
        document: DocumentDevis,
        logo: Bitmap?,
        page: PageDevis,
    ) {
        val info = PdfDocument.PageInfo.Builder(
            MiseEnPageDevis.LARGEUR_PAGE,
            MiseEnPageDevis.TOTAL_HAUTEUR_PAGE,
            page.numero,
        ).create()
        val feuille = pdf.startPage(info)
        val toile = feuille.canvas
        val premiere = page.numero == 1

        if (premiere) enTete(toile, document, logo)
        titresTableau(toile, premiere)
        var y = MiseEnPageDevis.hautDuTableau(premiere)
        page.lignes.forEach { ligne ->
            ligneTableau(toile, ligne, y)
            y += MiseEnPageDevis.HAUTEUR_LIGNE
        }
        if (page.totaux.isNotEmpty()) blocTotaux(toile, page, y)
        pied(toile, document, page)

        pdf.finishPage(feuille)
    }

    /**
     * L'en-tête : le logo et l'émetteur à gauche, le titre et le destinataire à
     * droite.
     *
     * Le logo est **contenu** dans un carré et non étiré : une image déformée sur
     * un devis dit quelque chose de l'entreprise qui l'envoie.
     */
    private fun enTete(toile: Canvas, document: DocumentDevis, logo: Bitmap?) {
        val gauche = MiseEnPageDevis.MARGE.toFloat()
        val droite = (MiseEnPageDevis.LARGEUR_PAGE - MiseEnPageDevis.MARGE).toFloat()
        var y = MiseEnPageDevis.MARGE + 12f

        if (logo != null) {
            val cote = MiseEnPageDevis.COTE_LOGO
            val echelle = minOf(cote.toFloat() / logo.width, cote.toFloat() / logo.height)
            val large = (logo.width * echelle).toInt()
            val haut = (logo.height * echelle).toInt()
            val cadre = Rect(
                gauche.toInt(),
                MiseEnPageDevis.MARGE,
                gauche.toInt() + large,
                MiseEnPageDevis.MARGE + haut,
            )
            toile.drawBitmap(logo, null, cadre, ENCRE_IMAGE)
            y = (MiseEnPageDevis.MARGE + haut + 14).toFloat()
        }

        document.emetteur.forEachIndexed { index, ligne ->
            val style = if (index == 0) TITRE_EMETTEUR else CORPS_PETIT
            toile.drawText(ligne, gauche, y, style)
            y += if (index == 0) 18f else 13f
        }

        // Le titre et le numéro à droite, alignés sur la marge : c'est là que
        // l'œil cherche la référence d'un document à classer.
        var droiteY = MiseEnPageDevis.MARGE + 18f
        toile.drawText(document.titre, droite, droiteY, TITRE_DOCUMENT)
        droiteY += 20f
        if (document.numero.isNotBlank()) {
            toile.drawText(document.numero, droite, droiteY, CORPS_DROITE)
            droiteY += 15f
        }
        document.dates.forEach { ligne ->
            toile.drawText(ligne, droite, droiteY, CORPS_DROITE)
            droiteY += 13f
        }
        droiteY += 8f
        document.destinataire.forEachIndexed { index, ligne ->
            toile.drawText(ligne, droite, droiteY, if (index == 0) CORPS_DROITE_FORT else CORPS_DROITE)
            droiteY += 13f
        }

        // L'objet sous les deux colonnes, sur toute la largeur : c'est une phrase,
        // et la couper en deux pour tenir dans une colonne la rendrait illisible.
        val basEntete = (MiseEnPageDevis.MARGE + MiseEnPageDevis.HAUTEUR_ENTETE).toFloat()
        val sujet = listOf(document.objet, document.machine).filter { it.isNotBlank() }
        if (sujet.isNotEmpty()) {
            toile.drawText("Objet : ${sujet.joinToString(" — ")}", gauche, basEntete - 14f, CORPS)
        }
        toile.drawLine(gauche, basEntete - 6f, droite, basEntete - 6f, FILET)
    }

    private fun titresTableau(toile: Canvas, premiere: Boolean) {
        val bas = MiseEnPageDevis.hautDuTableau(premiere) - 8f
        val bords = MiseEnPageDevis.bordsDroits()
        toile.drawText("Désignation", MiseEnPageDevis.MARGE.toFloat(), bas, TITRE_COLONNE)
        toile.drawText("Qté", bords[1].toFloat(), bas, TITRE_COLONNE_DROITE)
        toile.drawText("P.U. HT", bords[2].toFloat(), bas, TITRE_COLONNE_DROITE)
        toile.drawText("Montant HT", bords[3].toFloat(), bas, TITRE_COLONNE_DROITE)
        toile.drawLine(
            MiseEnPageDevis.MARGE.toFloat(),
            bas + 4f,
            bords[3].toFloat(),
            bas + 4f,
            FILET,
        )
    }

    private fun ligneTableau(toile: Canvas, ligne: LigneImprimee, haut: Int) {
        val bords = MiseEnPageDevis.bordsDroits()
        val base = haut + 14f
        val encre = if (ligne.offerte) CORPS_BARRE else CORPS
        // La désignation est rognée à la largeur de sa colonne : un intitulé long
        // qui déborderait écraserait la quantité d'à côté.
        val designation = rogner(ligne.designation, bords[0] - MiseEnPageDevis.MARGE - 6f, encre)
        toile.drawText(designation, MiseEnPageDevis.MARGE.toFloat(), base, encre)
        toile.drawText(ligne.quantite, bords[1].toFloat(), base, CORPS_DROITE)
        toile.drawText(ligne.prixUnitaire, bords[2].toFloat(), base, CORPS_DROITE)
        toile.drawText(ligne.montant, bords[3].toFloat(), base, CORPS_DROITE)
        toile.drawLine(
            MiseEnPageDevis.MARGE.toFloat(),
            (haut + MiseEnPageDevis.HAUTEUR_LIGNE).toFloat(),
            bords[3].toFloat(),
            (haut + MiseEnPageDevis.HAUTEUR_LIGNE).toFloat(),
            FILET_LEGER,
        )
    }

    private fun blocTotaux(toile: Canvas, page: PageDevis, apresLesLignes: Int) {
        val droite = (MiseEnPageDevis.LARGEUR_PAGE - MiseEnPageDevis.MARGE).toFloat()
        val gaucheBloc = droite - 240f
        var y = apresLesLignes + 26f

        page.totaux.forEach { total ->
            if (total.forte) {
                toile.drawLine(gaucheBloc, y - 12f, droite, y - 12f, FILET)
                toile.drawText(total.intitule, gaucheBloc, y + 4f, CORPS_FORT)
                toile.drawText(total.valeur, droite, y + 4f, TOTAL_DROITE)
                y += 24f
            } else {
                toile.drawText(total.intitule, gaucheBloc, y, CORPS)
                toile.drawText(total.valeur, droite, y, CORPS_DROITE)
                y += 16f
            }
        }

        y += 12f
        page.mentions.forEach { mention ->
            toile.drawText(mention, MiseEnPageDevis.MARGE.toFloat(), y, CORPS_PETIT)
            y += 14f
        }
        // Le cadre de signature : c'est ce qui fait d'un devis un document qu'on
        // renvoie accepté, et non une simple estimation.
        if (page.mentions.isNotEmpty()) {
            toile.drawRect(MiseEnPageDevis.MARGE.toFloat(), y, MiseEnPageDevis.MARGE + 220f, y + 70f, FILET)
        }
    }

    private fun pied(toile: Canvas, document: DocumentDevis, page: PageDevis) {
        val y = MiseEnPageDevis.hautDuPied + 18f
        val droite = (MiseEnPageDevis.LARGEUR_PAGE - MiseEnPageDevis.MARGE).toFloat()
        toile.drawLine(MiseEnPageDevis.MARGE.toFloat(), y - 14f, droite, y - 14f, FILET_LEGER)
        toile.drawText(document.emetteur.first(), MiseEnPageDevis.MARGE.toFloat(), y, CORPS_PETIT)
        toile.drawText("Page ${page.numero} / ${page.total}", droite, y, PIED_DROITE)
    }

    /** Rogne un texte à la largeur disponible, suffixé d'une ellipse. */
    private fun rogner(texte: String, largeur: Float, encre: Paint): String {
        if (encre.measureText(texte) <= largeur) return texte
        var coupe = texte
        while (coupe.isNotEmpty() && encre.measureText("$coupe…") > largeur) {
            coupe = coupe.dropLast(1)
        }
        return "$coupe…"
    }

    private val CORPS = Paint().apply {
        color = Color.BLACK
        textSize = 10f
        isAntiAlias = true
    }

    private val CORPS_PETIT = Paint(CORPS).apply {
        textSize = 8.5f
        color = Color.DKGRAY
    }

    private val CORPS_FORT = Paint(CORPS).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 11f
    }

    private val CORPS_BARRE = Paint(CORPS).apply {
        color = Color.DKGRAY
        isStrikeThruText = true
    }

    private val CORPS_DROITE = Paint(CORPS).apply { textAlign = Paint.Align.RIGHT }

    private val CORPS_DROITE_FORT = Paint(CORPS_DROITE).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val TOTAL_DROITE = Paint(CORPS_DROITE).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 13f
    }

    private val TITRE_DOCUMENT = Paint(CORPS).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 22f
        textAlign = Paint.Align.RIGHT
    }

    private val TITRE_EMETTEUR = Paint(CORPS).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 13f
    }

    private val TITRE_COLONNE = Paint(CORPS).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 9f
    }

    private val TITRE_COLONNE_DROITE = Paint(TITRE_COLONNE).apply {
        textAlign = Paint.Align.RIGHT
    }

    private val PIED_DROITE = Paint(CORPS_PETIT).apply { textAlign = Paint.Align.RIGHT }

    private val FILET = Paint().apply {
        color = Color.BLACK
        strokeWidth = 0.8f
        style = Paint.Style.STROKE
    }

    private val FILET_LEGER = Paint(FILET).apply {
        color = Color.LTGRAY
        strokeWidth = 0.5f
    }

    private val ENCRE_IMAGE = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }
}
