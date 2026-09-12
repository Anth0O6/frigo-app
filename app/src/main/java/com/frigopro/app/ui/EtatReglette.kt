package com.frigopro.app.ui

import com.frigopro.app.data.CourbesSaturation
import com.frigopro.app.data.Fluides
import com.frigopro.app.data.LectureSaturation
import com.frigopro.app.data.arrondiDixieme
import com.frigopro.app.data.enBarAbsolus
import com.frigopro.app.data.enBarRelatifs

/**
 * De quel côté du circuit on lit la réglette.
 *
 * La distinction n'est pas un confort d'affichage : elle **choisit la colonne**.
 * Sur un mélange à glissement, se tromper de côté donne un écart faux de tout le
 * glissement — près de 5 K sur un R-448A, assez pour régler un détendeur à
 * l'envers. L'appelant déclare donc ce qu'il mesure, et la réglette prend la
 * bonne température ; il n'a pas à se souvenir laquelle.
 */
enum class CoteCircuit(val libelle: String, val intituleEcart: String) {

    /**
     * Basse pression, sortie d'évaporateur : le fluide y est **vapeur**, donc la
     * température de **rosée** fait référence, et l'écart est la surchauffe.
     */
    ASPIRATION("Aspiration (BP)", "Surchauffe"),

    /**
     * Haute pression, sortie de condenseur : le fluide y est **liquide**, donc la
     * température de **bulle**, et l'écart est le sous-refroidissement.
     */
    REFOULEMENT("Refoulement (HP)", "Sous-refroidissement"),
}

/**
 * L'état de la réglette pression / température.
 *
 * Tout y est dérivé de trois saisies — le fluide, la pression lue au manomètre,
 * la température relevée sur la tuyauterie — et rien n'est stocké : une
 * conversion mémorisée serait fausse dès que la pression bouge, c'est-à-dire tout
 * le temps.
 *
 * Les pressions sont en **bar relatifs**, comme sur un manomètre de chantier, et
 * converties en absolus pour interroger la courbe. C'est un écart de 1,013 bar,
 * soit environ 7 K sur un R-410A en basse pression : le confondre est l'erreur la
 * plus facile à commettre et la plus coûteuse, et c'est pour cela que l'unité est
 * dite à l'écran plutôt que supposée.
 *
 * Séparé du composable et sans dépendance Android, pour que les tests puissent
 * l'exercer — même motif que [FriseHoraire] et [ReductionPhoto].
 */
data class EtatReglette(
    val fluide: String,
    val pressionRelativeBar: Double,
    val cote: CoteCircuit = CoteCircuit.ASPIRATION,
    /** La température lue sur la tuyauterie, `null` tant qu'on n'a pas mesuré. */
    val temperatureLigneC: Double? = null,
    /**
     * La courbe de ce fluide a été contrôlée par l'utilisateur contre une table
     * constructeur (voir `VerificationFluide`).
     */
    val verifie: Boolean = false,
) {

    val couvert: Boolean get() = CourbesSaturation.couvert(fluide)

    /** La lecture à cette pression, `null` hors de la plage saisie. */
    val lecture: LectureSaturation?
        get() = CourbesSaturation.temperatureA(fluide, pressionRelativeBar.enBarAbsolus())

    /**
     * La température de saturation qui fait référence de ce côté du circuit :
     * rosée à l'aspiration, bulle au refoulement.
     */
    val temperatureSaturationC: Double?
        get() = lecture?.let {
            when (cote) {
                CoteCircuit.ASPIRATION -> it.temperatureRoseeC
                CoteCircuit.REFOULEMENT -> it.temperatureBulleC
            }
        }

    /**
     * La surchauffe ou le sous-refroidissement, selon le côté.
     *
     * Les deux soustractions sont dans des sens opposés, et c'est la physique qui
     * le veut : à l'aspiration la vapeur est **plus chaude** que sa saturation, au
     * refoulement le liquide est **plus froid** que la sienne. Les deux grandeurs
     * sont positives quand tout va bien, et c'est ce qui les rend comparables aux
     * plages du métier.
     */
    val ecartK: Double?
        get() {
            val ligne = temperatureLigneC ?: return null
            val saturation = temperatureSaturationC ?: return null
            return when (cote) {
                CoteCircuit.ASPIRATION -> ligne - saturation
                CoteCircuit.REFOULEMENT -> saturation - ligne
            }.arrondiDixieme()
        }

    /**
     * L'écart peut-il être reporté dans le relevé ?
     *
     * **Non tant que la courbe n'est pas vérifiée**, et c'est la règle qui justifie
     * tout le dispositif de vérification. Une surchauffe écrite dans le relevé
     * devient un fait : elle part dans le compte-rendu signé par le client, elle
     * nourrit l'aide au dépannage, et plus rien ne dit ensuite d'où elle venait.
     * La réglette peut montrer un chiffre non vérifié — l'utilisateur le lit en
     * sachant ce qu'il lit, l'avertissement est sous ses yeux — mais elle ne peut
     * pas le laisser entrer en silence dans les données de l'intervention.
     */
    val reportable: Boolean get() = verifie && ecartK != null

    /** Le glissement du mélange à cette pression, quand il compte. */
    val glissementNotable: Boolean get() = lecture?.glissementNotable == true

    companion object {

        /**
         * La plage de pression que le curseur peut parcourir, en bar relatifs.
         *
         * Elle vient de la courbe elle-même et non d'une constante : une réglette
         * CO₂ monte à 72 bar, une réglette R-600a plafonne à 9, et un curseur
         * commun serait inutilisable pour les deux — sur une échelle de 0 à 72 bar,
         * régler un R-600a au dixième de bar demanderait un pixel.
         *
         * `null` pour un fluide sans courbe : il n'y a alors pas de curseur à
         * dessiner, et c'est ce que l'écran doit dire plutôt qu'afficher une
         * glissière inerte.
         */
        fun plagePressionRelative(fluide: String): ClosedFloatingPointRange<Double>? {
            val points = CourbesSaturation.points(fluide)
            if (points.size < 2) return null
            // L'**intersection** des deux colonnes, et non leur réunion. Une
            // lecture n'existe que si bulle *et* rosée s'interpolent ; or sur un
            // mélange la rosée descend plus bas que la bulle et monte moins haut.
            // Prendre les extrêmes des deux colonnes donnerait donc un curseur dont
            // les deux bouts afficheraient « hors plage », ce qui se lit comme une
            // panne de l'application. La bulle est toujours au-dessus de la rosée :
            // la borne basse est la plus basse bulle, la haute la plus haute rosée.
            val basse = points.first().bulleBarAbs.enBarRelatifs()
            val haute = points.last().roseeBarAbs.enBarRelatifs()
            return basse..haute
        }

        /**
         * La pression d'ouverture : celle du relevé si on en a une, sinon un point
         * de départ plausible au milieu de la plage.
         *
         * Arriver sur la pression déjà relevée est tout l'intérêt d'ouvrir la
         * réglette depuis une intervention : la question qu'on se pose est « ça fait
         * combien, ça ? », pas « où est le curseur ? ».
         */
        fun pressionInitiale(fluide: String, relevee: Double?): Double {
            val plage = plagePressionRelative(fluide) ?: return relevee ?: 0.0
            val depart = relevee ?: ((plage.start + plage.endInclusive) / 2.0)
            return depart.coerceIn(plage.start, plage.endInclusive)
        }

        /**
         * Le fluide sur lequel ouvrir : celui de la machine s'il a une courbe.
         *
         * Si la machine porte un fluide dont la courbe n'est pas saisie, la réglette
         * l'affiche quand même et dit qu'elle ne sait pas — le rabattre en silence
         * sur un voisin « proche » serait exactement l'approximation que le projet
         * refuse pour le GWP.
         */
        fun fluideInitial(deLaMachine: String): String {
            val nom = Fluides.normaliser(deLaMachine)
            return nom.ifBlank { CourbesSaturation.fluidesCouverts.first() }
        }
    }
}
