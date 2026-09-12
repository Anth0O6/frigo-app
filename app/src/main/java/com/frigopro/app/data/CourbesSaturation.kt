package com.frigopro.app.data

import kotlin.math.roundToInt

/**
 * Un point de la courbe de saturation d'un fluide.
 *
 * @param temperatureC température de saturation, en degrés Celsius.
 * @param bulleBarAbs pression de **bulle** (liquide saturé), en bar absolus.
 * @param roseeBarAbs pression de **rosée** (vapeur saturée), en bar absolus.
 *   Égale à la bulle pour un corps pur ou un azéotrope.
 */
data class PointSaturation(
    val temperatureC: Double,
    val bulleBarAbs: Double,
    val roseeBarAbs: Double,
)

/**
 * Ce que la réglette rend pour une pression donnée.
 *
 * Les deux températures sont distinctes parce que le métier s'en sert
 * différemment, et les confondre fausse le diagnostic :
 *
 * - la **surchauffe** se calcule à partir de la température de **rosée** à la
 *   pression d'aspiration : à la sortie de l'évaporateur le fluide est vapeur ;
 * - le **sous-refroidissement** à partir de la température de **bulle** à la
 *   pression de refoulement : en sortie de condenseur il est liquide.
 *
 * Sur un R-448A, dont le glissement approche 5 K, prendre l'une pour l'autre
 * donne une surchauffe fausse de 5 K — assez pour régler un détendeur à
 * l'envers. C'est la raison d'être de ce type : l'appelant ne peut pas se
 * tromper de colonne, il demande ce dont il a besoin.
 */
data class LectureSaturation(
    val fluide: String,
    val pressionBarAbs: Double,
    val temperatureBulleC: Double,
    val temperatureRoseeC: Double,
) {

    /** Le glissement à cette pression : l'écart entre rosée et bulle. */
    val glissementK: Double get() = temperatureRoseeC - temperatureBulleC

    /** Un mélange à glissement notable, dont il faut se méfier. */
    val glissementNotable: Boolean get() = kotlin.math.abs(glissementK) >= SEUIL_GLISSEMENT_K

    private companion object {

        /**
         * Au-delà d'un kelvin, le glissement change le résultat d'un réglage et
         * l'écran doit le dire. En dessous, il relève du bruit de mesure.
         */
        const val SEUIL_GLISSEMENT_K = 1.0
    }
}

/**
 * Les courbes de saturation, fluide par fluide.
 *
 * ## Pourquoi ces données sont écrites ainsi
 *
 * Chaque courbe est une **chaîne de caractères** et non un tableau de code, et
 * c'est délibéré : sous cette forme elle se relit ligne à ligne contre une
 * réglette physique ou une table constructeur, ce qu'une liste de vingt
 * `PointSaturation(...)` ne permet pas. Ces valeurs doivent pouvoir être
 * contrôlées par quelqu'un qui n'écrit pas de Kotlin.
 *
 * Format : `température:bulle` pour un corps pur, `température:bulle/rosée` pour
 * un mélange à glissement. Températures en °C, pressions en **bar absolus**.
 *
 * ## Pourquoi les bar absolus
 *
 * Parce que c'est l'unité des tables publiées, donc celle dans laquelle ces
 * valeurs se vérifient. Un manomètre de chantier, lui, affiche du relatif, et
 * l'écran convertit — en étiquetant les deux. Confondre les deux, c'est une
 * erreur d'un bar, soit jusqu'à 7 K sur du R-410A à basse température : c'est
 * pour cela que l'étiquette n'est jamais facultative à l'affichage.
 *
 * ## Ce que ces données ne sont pas
 *
 * Elles ne sont **pas une référence certifiée**. Elles ont été écrites de
 * mémoire documentaire, pas recopiées d'une source tracée, et peuvent comporter
 * des écarts. Tant qu'un fluide n'a pas été marqué vérifié
 * (voir [VerificationFluide]), la réglette l'annonce à l'écran et l'aide au
 * dépannage refuse de s'en servir sans le dire.
 *
 * **Les mélanges sont à contrôler en premier** : un corps pur n'a qu'une courbe
 * et une erreur s'y voit vite, tandis qu'un glissement faux est silencieux.
 */
object CourbesSaturation {

    /**
     * Les courbes, par pas de 5 K.
     *
     * Limitées aux fluides qu'un frigoriste rencontre vraiment. Un fluide absent
     * n'est pas approximé par un voisin : la réglette dit « courbe non saisie »,
     * ce qui vaut mieux qu'une valeur plausible et fausse — c'est la même règle
     * que le GWP, qui reste vide plutôt que d'être devinée.
     */
    private val COURBES: Map<String, String> = mapOf(
        // — Corps purs et quasi-azéotropes : une seule courbe —
        "R134A" to """
            -40:0.51 -35:0.66 -30:0.85 -25:1.07 -20:1.33 -15:1.64 -10:2.01 -5:2.43
            0:2.93 5:3.50 10:4.15 15:4.89 20:5.72 25:6.65 30:7.70 35:8.87
            40:10.17 45:11.60 50:13.18 55:14.92 60:16.82
        """,
        "R410A" to """
            -40:1.77 -35:2.17 -30:2.64 -25:3.18 -20:3.81 -15:4.54 -10:5.37 -5:6.31
            0:7.38 5:8.58 10:9.93 15:11.44 20:13.12 25:14.98 30:17.04 35:19.31
            40:21.81 45:24.56 50:27.57 55:30.87 60:34.48
        """,
        "R32" to """
            -40:1.78 -35:2.17 -30:2.64 -25:3.18 -20:3.81 -15:4.53 -10:5.36 -5:6.30
            0:7.35 5:8.54 10:9.86 15:11.34 20:12.98 25:14.80 30:16.80 35:19.00
            40:21.42 45:24.07 50:26.96 55:30.11 60:33.55
        """,
        "R22" to """
            -40:1.05 -35:1.32 -30:1.64 -25:2.01 -20:2.45 -15:2.96 -10:3.55 -5:4.22
            0:4.98 5:5.84 10:6.81 15:7.90 20:9.10 25:10.44 30:11.92 35:13.55
            40:15.34 45:17.30 50:19.43 55:21.76 60:24.28
        """,
        "R290" to """
            -40:1.11 -35:1.38 -30:1.70 -25:2.07 -20:2.50 -15:2.99 -10:3.55 -5:4.19
            0:4.74 5:5.50 10:6.35 15:7.30 20:8.36 25:9.52 30:10.80 35:12.20
            40:13.73 45:15.40 50:17.22 55:19.19 60:21.32
        """,
        "R600A" to """
            -20:0.72 -15:0.89 -10:1.10 -5:1.34 0:1.57 5:1.88 10:2.21 15:2.59
            20:3.02 25:3.50 30:4.04 35:4.64 40:5.31 45:6.05 50:6.87 55:7.77
            60:8.75
        """,
        "R1234YF" to """
            -40:0.54 -35:0.69 -30:0.88 -25:1.10 -20:1.36 -15:1.67 -10:2.03 -5:2.44
            0:2.92 5:3.46 10:4.07 15:4.77 20:5.55 25:6.42 30:7.39 35:8.46
            40:9.65 45:10.96 50:12.39 55:13.96 60:15.68
        """,
        "R507A" to """
            -40:1.38 -35:1.71 -30:2.09 -25:2.54 -20:3.06 -15:3.66 -10:4.35 -5:5.13
            0:6.02 5:7.02 10:8.14 15:9.39 20:10.78 25:12.32 30:14.02 35:15.89
            40:17.95 45:20.20 50:22.66 55:25.35 60:28.29
        """,

        // — Mélanges : bulle / rosée. Ce sont ceux à vérifier d'abord. —
        "R404A" to """
            -40:1.35/1.30 -35:1.67/1.61 -30:2.07/1.99 -25:2.51/2.42 -20:3.03/2.93
            -15:3.62/3.50 -10:4.28/4.16 -5:5.03/4.89 0:5.87/5.72 5:6.81/6.63
            10:7.84/7.65 15:8.99/8.78 20:10.26/10.02 25:11.65/11.39 30:13.18/12.89
            35:14.85/14.53 40:16.66/16.31 45:18.63/18.25 50:20.77/20.36
        """,
        "R407C" to """
            -40:0.96/0.72 -35:1.21/0.92 -30:1.51/1.17 -25:1.87/1.46 -20:2.28/1.81
            -15:2.76/2.22 -10:3.32/2.69 -5:3.96/3.24 0:4.69/3.87 5:5.51/4.58
            10:6.44/5.39 15:7.48/6.31 20:8.64/7.34 25:9.93/8.49 30:11.35/9.77
            35:12.92/11.19 40:14.64/12.76 45:16.53/14.49 50:18.59/16.39
        """,
        "R448A" to """
            -40:1.01/0.80 -35:1.27/1.02 -30:1.58/1.28 -25:1.94/1.59 -20:2.36/1.95
            -15:2.85/2.37 -10:3.41/2.85 -5:4.05/3.40 0:4.78/4.03 5:5.60/4.74
            10:6.52/5.54 15:7.55/6.44 20:8.69/7.45 25:9.96/8.57 30:11.35/9.81
            35:12.88/11.18 40:14.56/12.69 45:16.39/14.34 50:18.38/16.15
        """,
        "R449A" to """
            -40:1.02/0.81 -35:1.28/1.03 -30:1.59/1.30 -25:1.96/1.61 -20:2.38/1.97
            -15:2.87/2.39 -10:3.43/2.87 -5:4.07/3.43 0:4.80/4.06 5:5.62/4.77
            10:6.54/5.57 15:7.57/6.47 20:8.72/7.48 25:9.99/8.61 30:11.38/9.85
            35:12.91/11.22 40:14.59/12.73 45:16.42/14.38 50:18.41/16.19
        """,
        "R452A" to """
            -40:1.36/1.21 -35:1.69/1.51 -30:2.08/1.86 -25:2.53/2.27 -20:3.05/2.74
            -15:3.64/3.28 -10:4.31/3.89 -5:5.07/4.58 0:5.92/5.36 5:6.87/6.23
            10:7.92/7.19 15:9.08/8.26 20:10.36/9.44 25:11.76/10.74 30:13.29/12.16
            35:14.96/13.71 40:16.78/15.41 45:18.76/17.25 50:20.91/19.26
        """,
        "R452B" to """
            -40:1.73/1.64 -35:2.12/2.01 -30:2.58/2.45 -25:3.11/2.96 -20:3.72/3.55
            -15:4.43/4.23 -10:5.23/5.00 -5:6.15/5.88 0:7.18/6.87 5:8.35/7.99
            10:9.65/9.25 15:11.11/10.65 20:12.73/12.21 25:14.53/13.95 30:16.51/15.86
            35:18.69/17.97 40:21.08/20.29 45:23.69/22.83 50:26.54/25.60
        """,
        "R454B" to """
            -40:1.71/1.62 -35:2.09/1.98 -30:2.55/2.42 -25:3.07/2.92 -20:3.68/3.50
            -15:4.37/4.17 -10:5.16/4.93 -5:6.06/5.80 0:7.07/6.77 5:8.21/7.87
            10:9.49/9.10 15:10.92/10.48 20:12.50/12.01 25:14.26/13.71 30:16.20/15.58
            35:18.34/17.65 40:20.68/19.92 45:23.24/22.41 50:26.04/25.13
        """,
        "R513A" to """
            -40:0.58/0.57 -35:0.74/0.73 -30:0.94/0.93 -25:1.18/1.16 -20:1.46/1.44
            -15:1.79/1.76 -10:2.17/2.14 -5:2.62/2.58 0:3.13/3.09 5:3.72/3.67
            10:4.39/4.33 15:5.15/5.09 20:6.01/5.93 25:6.97/6.88 30:8.04/7.94
            35:9.23/9.12 40:10.56/10.43 45:12.02/11.87 50:13.63/13.46
        """,

        // — CO₂ : un cas à part, voir [TEMPERATURE_CRITIQUE_R744_C] —
        "R744" to """
            -40:10.05 -35:11.93 -30:14.05 -25:16.45 -20:19.13 -15:22.13 -10:25.46
            -5:29.16 0:34.85 5:39.70 10:45.02 15:50.87 20:57.29 25:64.34 30:72.14
        """,
    )

    /**
     * Au-delà de cette température, le CO₂ est supercritique : il n'y a plus de
     * saturation, donc plus de surchauffe ni de sous-refroidissement à lire.
     *
     * Une réglette qui répondrait quand même produirait un nombre dénué de sens
     * physique — et c'est sur une installation transcritique qu'on a le moins
     * besoin d'un chiffre inventé.
     */
    const val TEMPERATURE_CRITIQUE_R744_C: Double = 31.0

    /** Les fluides dont la courbe est saisie, dans l'ordre du catalogue. */
    val fluidesCouverts: List<String> = COURBES.keys.toList()

    /** Le fluide a-t-il une courbe saisie ? */
    fun couvert(fluide: String): Boolean = Fluides.normaliser(fluide) in COURBES

    /** Les points de la courbe, ou une liste vide si le fluide n'est pas saisi. */
    fun points(fluide: String): List<PointSaturation> =
        COURBES[Fluides.normaliser(fluide)]?.let(::analyser).orEmpty()

    /**
     * La température de saturation à une pression donnée.
     *
     * `null` hors de la plage saisie, **sans extrapoler**. Prolonger une courbe
     * au-delà de ses points donnerait un chiffre d'autant plus faux qu'on s'en
     * éloigne, et c'est précisément aux extrêmes — une pompe à chaleur par -15 °C,
     * un condenseur à 60 °C au soleil — qu'on consulte une réglette.
     */
    fun temperatureA(fluide: String, pressionBarAbs: Double): LectureSaturation? {
        val points = points(fluide)
        if (points.size < 2) return null
        val bulle = interpoler(pressionBarAbs, points.map { it.bulleBarAbs to it.temperatureC })
        val rosee = interpoler(pressionBarAbs, points.map { it.roseeBarAbs to it.temperatureC })
        if (bulle == null || rosee == null) return null
        return LectureSaturation(
            fluide = Fluides.normaliser(fluide),
            pressionBarAbs = pressionBarAbs,
            temperatureBulleC = bulle.arrondiDixieme(),
            temperatureRoseeC = rosee.arrondiDixieme(),
        )
    }

    /**
     * La pression de bulle et de rosée à une température donnée : l'autre sens de
     * la réglette, celui qu'on utilise pour savoir à quelle pression régler un
     * pressostat.
     */
    fun pressionA(fluide: String, temperatureC: Double): Pair<Double, Double>? {
        val points = points(fluide)
        if (points.size < 2) return null
        val bulle = interpoler(temperatureC, points.map { it.temperatureC to it.bulleBarAbs })
        val rosee = interpoler(temperatureC, points.map { it.temperatureC to it.roseeBarAbs })
        if (bulle == null || rosee == null) return null
        return bulle.arrondiCentieme() to rosee.arrondiCentieme()
    }

    /**
     * Interpolation linéaire entre deux points encadrants, `null` si la valeur
     * tombe hors de la plage.
     *
     * Linéaire sur un pas de 5 K, alors qu'une courbe de saturation est
     * exponentielle : l'écart que cela introduit est de l'ordre du dixième de
     * kelvin, très en dessous de la précision d'un manomètre de chantier. Une
     * interpolation plus savante donnerait une fausse impression de précision
     * sans rien apporter au réglage.
     */
    private fun interpoler(x: Double, paires: List<Pair<Double, Double>>): Double? {
        val triees = paires.sortedBy { it.first }
        if (x < triees.first().first || x > triees.last().first) return null
        val apres = triees.indexOfFirst { it.first >= x }
        if (apres == 0) return triees.first().second
        val (x0, y0) = triees[apres - 1]
        val (x1, y1) = triees[apres]
        if (x1 == x0) return y0
        return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    }

    /**
     * Lit une courbe écrite en clair.
     *
     * Tolère les sauts de ligne et les espaces multiples pour que la donnée reste
     * présentable à l'œil dans le source. Un point mal formé est **ignoré** plutôt
     * que de faire échouer le tout : il vaut mieux une courbe amputée d'un point,
     * que l'interpolation comblera, qu'un fluide entier qui disparaît de la
     * réglette — et [CourbesSaturationTest] vérifie justement qu'aucune courbe
     * livrée n'a de point perdu.
     */
    private fun analyser(brut: String): List<PointSaturation> =
        brut.trim().split(Regex("\\s+")).mapNotNull { bout ->
            val (tempBrut, pressionsBrut) = bout.split(':').let {
                if (it.size != 2) return@mapNotNull null else it[0] to it[1]
            }
            val temperature = tempBrut.toDoubleOrNull() ?: return@mapNotNull null
            val pressions = pressionsBrut.split('/')
            val bulle = pressions.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            // Un corps pur n'écrit qu'une pression : bulle et rosée se confondent.
            val rosee = pressions.getOrNull(1)?.toDoubleOrNull() ?: bulle
            PointSaturation(temperature, bulle, rosee)
        }.sortedBy { it.temperatureC }
}

/** Arrondit au centième, la précision à laquelle une pression se lit. */
internal fun Double.arrondiCentieme(): Double = (this * 100).roundToInt() / 100.0

/**
 * La pression atmosphérique de référence, pour passer de l'absolu au relatif.
 *
 * Un manomètre de chantier mesure une pression **relative** — zéro à
 * l'atmosphère — alors que les tables de saturation sont en absolu. L'écart est
 * d'un bar, ce qui paraît peu et vaut jusqu'à 7 K sur du R-410A en bas de
 * plage : c'est pourquoi l'écran étiquette toujours laquelle des deux il montre.
 */
const val PRESSION_ATMOSPHERIQUE_BAR: Double = 1.013

/** Du relatif (manomètre) vers l'absolu (tables). */
fun Double.enBarAbsolus(): Double = this + PRESSION_ATMOSPHERIQUE_BAR

/** De l'absolu (tables) vers le relatif (manomètre). */
fun Double.enBarRelatifs(): Double = this - PRESSION_ATMOSPHERIQUE_BAR
