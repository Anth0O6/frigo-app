package com.frigopro.app.data

/**
 * Arithmétique de la réduction d'une photo.
 *
 * Une plaque signalétique photographiée au téléphone pèse une dizaine de
 * mégaoctets, dont l'immense majorité ne sert à rien : ce qu'on demande à
 * l'image, c'est que les caractères restent lisibles à l'écran. Réduire est
 * donc la règle, et ce n'est pas un détail de confort — les photos partent dans
 * l'archive de sauvegarde, qu'on doit pouvoir transporter.
 *
 * Le calcul est isolé ici, sans Android, parce que c'est la seule partie du
 * traitement d'image qui se trompe silencieusement : une photo deux fois trop
 * petite reste une photo.
 */
object ReductionPhoto {

    /** Grand côté au-delà duquel une photo est réduite avant d'être rangée. */
    const val COTE_MAX = 2048

    /** Grand côté d'une vignette de liste. */
    const val COTE_VIGNETTE = 400

    /**
     * Facteur d'échantillonnage à donner à `BitmapFactory` : la plus grande
     * puissance de deux qui laisse encore le grand côté **au-dessus** de
     * [coteMax].
     *
     * Au-dessus, et non en dessous : l'échantillonnage est grossier, il divise
     * par deux ou par quatre. S'en contenter ferait tomber une photo de 2500
     * pixels à 1250 alors que 2048 étaient demandés. On décode donc juste
     * au-dessus de la cible, et [dimensionsReduites] finit le travail au pixel.
     */
    fun facteurEchantillonnage(largeur: Int, hauteur: Int, coteMax: Int = COTE_MAX): Int {
        val cote = maxOf(largeur, hauteur)
        if (cote <= 0 || coteMax <= 0) return 1
        var facteur = 1
        while (cote / (facteur * 2) >= coteMax) {
            facteur *= 2
        }
        return facteur
    }

    /**
     * Dimensions finales, proportions conservées, grand côté ramené à [coteMax].
     *
     * Une image déjà plus petite est laissée telle quelle : agrandir
     * n'ajouterait aucun détail et ferait grossir le fichier.
     */
    fun dimensionsReduites(largeur: Int, hauteur: Int, coteMax: Int = COTE_MAX): Pair<Int, Int> {
        val cote = maxOf(largeur, hauteur)
        if (largeur <= 0 || hauteur <= 0 || cote <= coteMax) return largeur to hauteur
        val rapport = coteMax.toDouble() / cote
        return maxOf(1, Math.round(largeur * rapport).toInt()) to
            maxOf(1, Math.round(hauteur * rapport).toInt())
    }
}
