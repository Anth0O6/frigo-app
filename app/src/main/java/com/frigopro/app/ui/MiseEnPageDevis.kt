package com.frigopro.app.ui

/**
 * Une ligne du tableau, telle que le document l'imprime.
 *
 * Les montants sont déjà des chaînes : le formatage est un choix de rédaction
 * (« 1 920,80 € »), et le laisser au dessin aurait dispersé la mise en forme des
 * nombres entre le document et le canevas.
 */
data class LigneImprimee(
    val designation: String,
    val quantite: String,
    val prixUnitaire: String,
    val montant: String,
    /** Une ligne offerte s'imprime barrée, et son montant compte pour zéro. */
    val offerte: Boolean = false,
)

/** Un intitulé et une valeur du bloc des totaux. */
data class LigneTotalImprimee(
    val intitule: String,
    val valeur: String,
    /** Le total TTC : en gras, et précédé d'un filet. */
    val forte: Boolean = false,
)

/**
 * Ce qu'une page du devis contient.
 *
 * Le découpage est calculé avant tout dessin, et c'est ce qui permet d'imprimer
 * « Page 2 / 3 » : un document qui se pagine au fil du dessin ne connaît son
 * nombre de pages qu'à la fin, quand la première est déjà écrite.
 */
data class PageDevis(
    val numero: Int,
    val total: Int,
    val lignes: List<LigneImprimee>,
    /** Le bloc des totaux ne va que sur la dernière page. */
    val totaux: List<LigneTotalImprimee> = emptyList(),
    val mentions: List<String> = emptyList(),
)

/**
 * L'arithmétique de la mise en page du devis, sans Android.
 *
 * Isolée pour la même raison que [ReductionPhoto] : ce qui se vérifie sans
 * téléphone doit l'être. Un devis de trente lignes qui déborde sur une page
 * fantôme, un bloc de totaux coupé en deux, un « Page 2 / 1 » : autant de
 * défauts qui ne se voient qu'en ouvrant le PDF, c'est-à-dire trop tard — et
 * pour un document qui part chez un client.
 *
 * Les dimensions sont en **points PostScript** (1/72 de pouce), l'unité du
 * `PdfDocument` d'Android : un A4 y fait 595 × 842. Les exprimer en pixels
 * aurait lié le document à une densité d'écran, alors qu'un PDF n'en a pas.
 */
object MiseEnPageDevis {

    /** A4 à 72 ppp, l'unité du `PdfDocument`. */
    const val LARGEUR_PAGE = 595

    const val TOTAL_HAUTEUR_PAGE = 842

    /** Marges : 40 pt, soit environ 14 mm — de quoi agrafer sans manger le texte. */
    const val MARGE = 40

    /** Hauteur de l'en-tête : logo, identité de l'entreprise, numéro et dates. */
    const val HAUTEUR_ENTETE = 150

    /** La ligne de titres du tableau. */
    const val HAUTEUR_TITRES = 24

    /** Une ligne de prestation. */
    const val HAUTEUR_LIGNE = 22

    /** Le bloc des totaux, mentions légales comprises. */
    const val HAUTEUR_TOTAUX = 130

    /** Le pied de page : numéro de page, et le SIRET qui doit y figurer. */
    const val HAUTEUR_PIED = 30

    /** Côté maximal du logo, en points. Au-delà il écraserait l'identité à côté. */
    const val COTE_LOGO = 64

    /**
     * Les colonnes, en fraction de la largeur utile.
     *
     * La désignation prend plus de la moitié : c'est la seule colonne dont le
     * contenu est libre, et « remplacement compresseur hermétique Danfoss
     * NTZ068 » doit tenir sans se faire tronquer au tiers.
     */
    const val PART_DESIGNATION = 0.52

    const val PART_QUANTITE = 0.14

    const val PART_PRIX = 0.16

    const val PART_MONTANT = 0.18

    val largeurUtile: Int get() = LARGEUR_PAGE - 2 * MARGE

    /** Le bord droit de chaque colonne, d'où les montants s'alignent à droite. */
    fun bordsDroits(): List<Int> {
        val gauche = MARGE
        val designation = gauche + (largeurUtile * PART_DESIGNATION).toInt()
        val quantite = designation + (largeurUtile * PART_QUANTITE).toInt()
        val prix = quantite + (largeurUtile * PART_PRIX).toInt()
        return listOf(designation, quantite, prix, LARGEUR_PAGE - MARGE)
    }

    /**
     * Combien de lignes tiennent sur la première page, et sur les suivantes.
     *
     * La première en porte moins : elle supporte l'en-tête. Les suivantes
     * reprennent juste la ligne de titres, parce qu'un tableau dont les colonnes
     * ne sont pas nommées sur la page qu'on lit ne se lit pas.
     */
    val lignesPremierePage: Int
        get() = (TOTAL_HAUTEUR_PAGE - MARGE - HAUTEUR_ENTETE - HAUTEUR_TITRES - HAUTEUR_PIED - MARGE) /
            HAUTEUR_LIGNE

    val lignesPagesSuivantes: Int
        get() = (TOTAL_HAUTEUR_PAGE - MARGE - HAUTEUR_TITRES - HAUTEUR_PIED - MARGE) / HAUTEUR_LIGNE

    /**
     * Découpe les lignes en pages, et place le bloc des totaux.
     *
     * **Le bloc des totaux ne se coupe pas.** S'il ne tient pas sous la dernière
     * ligne, il part sur une page à lui : un total TTC séparé de son hors taxes
     * par un saut de page est exactement ce qui fait relire un devis trois fois,
     * et un devis qu'on relit trois fois n'est pas signé le jour même.
     *
     * Un devis **sans ligne** produit quand même une page : le document existe, il
     * porte un numéro, et ne rien imprimer laisserait croire à un échec de
     * l'export plutôt qu'à un devis vide.
     */
    fun paginer(
        lignes: List<LigneImprimee>,
        totaux: List<LigneTotalImprimee>,
        mentions: List<String> = emptyList(),
    ): List<PageDevis> {
        val paquets = decouper(lignes)
        val placeRestante = hauteurRestante(paquets.last().size, premiere = paquets.size == 1)
        val totauxAPart = placeRestante < HAUTEUR_TOTAUX
        val nombre = paquets.size + if (totauxAPart) 1 else 0

        val pages = paquets.mapIndexed { index, paquet ->
            PageDevis(
                numero = index + 1,
                total = nombre,
                lignes = paquet,
                totaux = if (!totauxAPart && index == paquets.lastIndex) totaux else emptyList(),
                mentions = if (!totauxAPart && index == paquets.lastIndex) mentions else emptyList(),
            )
        }
        return if (totauxAPart) {
            pages + PageDevis(
                numero = nombre,
                total = nombre,
                lignes = emptyList(),
                totaux = totaux,
                mentions = mentions,
            )
        } else {
            pages
        }
    }

    /**
     * L'ordonnée de la première ligne du tableau sur une page donnée.
     *
     * Le dessin part du haut — comme un canevas Android, où l'origine est en haut
     * à gauche, à l'inverse du PostScript : le confondre retournerait la page.
     */
    fun hautDuTableau(premiere: Boolean): Int =
        MARGE + (if (premiere) HAUTEUR_ENTETE else 0) + HAUTEUR_TITRES

    /** L'ordonnée de référence du pied de page. */
    val hautDuPied: Int get() = TOTAL_HAUTEUR_PAGE - MARGE - HAUTEUR_PIED

    private fun decouper(lignes: List<LigneImprimee>): List<List<LigneImprimee>> {
        if (lignes.isEmpty()) return listOf(emptyList())
        val paquets = mutableListOf<List<LigneImprimee>>()
        var reste = lignes
        var premiere = true
        while (reste.isNotEmpty()) {
            val place = if (premiere) lignesPremierePage else lignesPagesSuivantes
            paquets += reste.take(place)
            reste = reste.drop(place)
            premiere = false
        }
        return paquets
    }

    /**
     * La place qui reste sous la dernière ligne d'une page, jusqu'au pied.
     *
     * [hautDuTableau] comprend déjà la marge du haut et l'en-tête, et [hautDuPied]
     * la marge du bas : la différence est exactement l'espace libre, et en
     * retrancher une marge de plus l'aurait comptée deux fois — le bloc des totaux
     * serait parti sur une page à lui alors qu'il tenait.
     */
    private fun hauteurRestante(lignesSurLaPage: Int, premiere: Boolean): Int =
        hautDuPied - hautDuTableau(premiere) - lignesSurLaPage * HAUTEUR_LIGNE
}
