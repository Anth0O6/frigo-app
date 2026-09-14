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
 * ## D'où viennent ces valeurs
 *
 * Elles sont **calculées**, pas recopiées : `donnees/genere-courbes.py` les
 * produit avec CoolProp, l'implémentation libre des équations d'état de
 * référence — les mêmes que celles de REFPROP pour la plupart de ces fluides.
 *
 * Ce n'était pas le cas au début, et la première version a coûté cher. Elle
 * avait été écrite « de mémoire documentaire », et le résultat était
 * caractéristique : les fluides anciens tombaient juste — R-134a, R-22, R-290,
 * R-600a, R-744 au centième près — et **tous les mélanges récents étaient trop
 * bas**. Le pire trio, R-407C / R-448A / R-449A, l'était de près d'un quart :
 * 4,80 bar au lieu de 6,15 pour le R-449A à 0 °C, soit **plus de 8 K d'erreur
 * sur la température de rosée**. Une surchauffe calculée là-dessus annonçait
 * 10 K là où il y en avait 2, et c'est du liquide qui part au compresseur.
 *
 * La leçon n'est pas « mieux recopier », c'est **ne plus recopier**. Une valeur
 * douteuse se recontrôle en relançant le script, et n'importe quelle table
 * constructeur donne désormais les mêmes chiffres à quelques centièmes près.
 * Corollaire : **ne pas retoucher ce tableau à la main** — une correction faite
 * ici et pas dans le script serait reperdue au calcul suivant.
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
 * ## Ce que la marque « contrôlée » veut dire désormais
 *
 * [VerificationFluide] existait parce que les valeurs n'étaient pas fiables : le
 * report d'un écart dans un relevé était **fermé** tant qu'un fluide n'avait pas
 * été coché. Ce verrou n'a plus lieu d'être — il obligerait à cocher pour se
 * servir de valeurs qui, elles, sont maintenant justes.
 *
 * La marque reste, et ne bloque plus rien : elle dit « j'ai recoupé cette courbe
 * avec la table de mon fournisseur », ce qui est une information utile et qui
 * engage celui qui la pose. La courbe du CO₂ s'arrête au point critique, comme
 * avant : au-delà il n'y a plus de saturation, et un chiffre inventé serait le
 * plus nuisible là précisément.
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
        // Calculées par CoolProp 8.0.0 — voir
        // `donnees/genere-courbes.py`. Ne pas retoucher à la main : une valeur
        // corrigée ici et pas dans le script serait reperdue au calcul suivant.
        // R134A — glissement max 0.0 K, -40 à 60 °C
        "R134A" to """
            -40:0.51 -35:0.66 -30:0.84 -25:1.06 -20:1.33 -15:1.64 -10:2.01 -5:2.43
            0:2.93 5:3.50 10:4.15 15:4.88 20:5.72 25:6.65 30:7.70 35:8.87
            40:10.17 45:11.60 50:13.18 55:14.92 60:16.82
        """,
        // R32 — glissement max 0.0 K, -40 à 60 °C
        "R32" to """
            -40:1.77 -35:2.21 -30:2.73 -25:3.35 -20:4.06 -15:4.88 -10:5.83 -5:6.91
            0:8.13 5:9.51 10:11.07 15:12.81 20:14.75 25:16.90 30:19.28 35:21.90
            40:24.78 45:27.95 50:31.41 55:35.20 60:39.33
        """,
        // R22 — glissement max 0.0 K, -40 à 60 °C
        "R22" to """
            -40:1.05 -35:1.32 -30:1.64 -25:2.01 -20:2.45 -15:2.96 -10:3.55 -5:4.22
            0:4.98 5:5.84 10:6.81 15:7.89 20:9.10 25:10.44 30:11.92 35:13.55
            40:15.34 45:17.29 50:19.43 55:21.75 60:24.27
        """,
        // R290 — glissement max 0.0 K, -40 à 60 °C
        "R290" to """
            -40:1.11 -35:1.37 -30:1.68 -25:2.03 -20:2.45 -15:2.92 -10:3.45 -5:4.06
            0:4.74 5:5.51 10:6.37 15:7.32 20:8.36 25:9.52 30:10.79 35:12.18
            40:13.69 45:15.34 50:17.13 55:19.07 60:21.17
        """,
        // R600A — glissement max 0.0 K, -40 à 60 °C
        "R600A" to """
            -40:0.29 -35:0.37 -30:0.47 -25:0.58 -20:0.72 -15:0.89 -10:1.08 -5:1.31
            0:1.57 5:1.87 10:2.21 15:2.59 20:3.02 25:3.51 30:4.05 35:4.65
            40:5.31 45:6.04 50:6.85 55:7.73 60:8.69
        """,
        // R1234YF — glissement max 0.0 K, -40 à 60 °C
        "R1234YF" to """
            -40:0.62 -35:0.79 -30:0.99 -25:1.23 -20:1.51 -15:1.84 -10:2.22 -5:2.66
            0:3.16 5:3.73 10:4.38 15:5.10 20:5.92 25:6.83 30:7.84 35:8.95
            40:10.18 45:11.54 50:13.02 55:14.65 60:16.42
        """,
        // R1234ZE — glissement max 0.0 K, -40 à 60 °C
        "R1234ZE" to """
            -40:0.37 -35:0.48 -30:0.61 -25:0.77 -20:0.97 -15:1.20 -10:1.47 -5:1.79
            0:2.17 5:2.59 10:3.08 15:3.64 20:4.27 25:4.99 30:5.78 35:6.67
            40:7.66 45:8.76 50:9.97 55:11.30 60:12.77
        """,
        // R717 — glissement max 0.0 K, -40 à 60 °C
        "R717" to """
            -40:0.72 -35:0.93 -30:1.19 -25:1.51 -20:1.90 -15:2.36 -10:2.91 -5:3.55
            0:4.29 5:5.16 10:6.15 15:7.28 20:8.57 25:10.03 30:11.67 35:13.50
            40:15.55 45:17.82 50:20.33 55:23.10 60:26.14
        """,
        // R744 — glissement max 0.0 K, -40 à 30 °C
        "R744" to """
            -40:10.04 -35:12.02 -30:14.28 -25:16.83 -20:19.70 -15:22.91 -10:26.49 -5:30.46
            0:34.85 5:39.69 10:45.02 15:50.87 20:57.29 25:64.34 30:72.14
        """,
        // R404A — glissement max 0.7 K, -40 à 60 °C
        "R404A" to """
            -40:1.35/1.31 -35:1.69/1.64 -30:2.08/2.02 -25:2.54/2.48 -20:3.07/3.00 -15:3.69/3.61 -10:4.39/4.31 -5:5.19/5.10
            0:6.10/6.00 5:7.12/7.02 10:8.27/8.16 15:9.55/9.43 20:10.97/10.84 25:12.55/12.41 30:14.28/14.14 35:16.20/16.05
            40:18.30/18.15 45:20.59/20.45 50:23.11/22.96 55:25.85/25.71 60:28.85/28.71
        """,
        // R407C — glissement max 6.9 K, -40 à 60 °C
        "R407C" to """
            -40:1.20/0.86 -35:1.51/1.10 -30:1.87/1.39 -25:2.30/1.73 -20:2.80/2.15 -15:3.38/2.63 -10:4.05/3.20 -5:4.81/3.85
            0:5.68/4.61 5:6.66/5.47 10:7.76/6.45 15:9.00/7.56 20:10.38/8.80 25:11.90/10.20 30:13.59/11.76 35:15.45/13.49
            40:17.49/15.41 45:19.72/17.53 50:22.16/19.88 55:24.81/22.45 60:27.69/25.29
        """,
        // R407F — glissement max 6.3 K, -40 à 45 °C
        "R407F" to """
            -40:1.35/1.00 -35:1.69/1.27 -30:2.09/1.60 -25:2.57/2.00 -20:3.12/2.46 -15:3.76/3.01 -10:4.50/3.64 -5:5.34/4.38
            0:6.29/5.22 5:7.37/6.18 10:8.58/7.27 15:9.93/8.51 20:11.44/9.89 25:13.11/11.44 30:14.96/13.16 35:16.99/15.07
            40:19.21/17.19 45:21.65/19.53
        """,
        // R410A — glissement max 0.1 K, -40 à 60 °C
        "R410A" to """
            -40:1.75/1.75 -35:2.19/2.18 -30:2.70/2.69 -25:3.31/3.29 -20:4.01/3.99 -15:4.82/4.80 -10:5.75/5.73 -5:6.81/6.78
            0:8.01/7.98 5:9.36/9.33 10:10.88/10.85 15:12.58/12.54 20:14.47/14.43 25:16.57/16.52 30:18.89/18.83 35:21.45/21.38
            40:24.26/24.19 45:27.34/27.26 50:30.71/30.63 55:34.40/34.32 60:38.43/38.35
        """,
        // R448A — glissement max 6.5 K, -40 à 60 °C
        "R448A" to """
            -40:1.36/1.00 -35:1.69/1.27 -30:2.09/1.59 -25:2.56/1.98 -20:3.11/2.43 -15:3.74/2.96 -10:4.47/3.58 -5:5.30/4.29
            0:6.23/5.11 5:7.29/6.03 10:8.48/7.09 15:9.80/8.27 20:11.27/9.60 25:12.90/11.08 30:14.70/12.74 35:16.67/14.57
            40:18.83/16.60 45:21.19/18.84 50:23.76/21.31 55:26.55/24.02 60:29.57/27.01
        """,
        // R449A — glissement max 6.0 K, -40 à 60 °C
        "R449A" to """
            -40:1.34/1.00 -35:1.67/1.28 -30:2.06/1.60 -25:2.53/1.99 -20:3.07/2.44 -15:3.69/2.97 -10:4.41/3.59 -5:5.22/4.30
            0:6.15/5.11 5:7.19/6.03 10:8.36/7.08 15:9.67/8.26 20:11.12/9.58 25:12.73/11.05 30:14.50/12.69 35:16.45/14.51
            40:18.58/16.52 45:20.91/18.74 50:23.45/21.18 55:26.20/23.87 60:29.19/26.82
        """,
        // R450A — glissement max 0.6 K, -40 à 60 °C
        "R450A" to """
            -40:0.46/0.45 -35:0.59/0.58 -30:0.76/0.74 -25:0.95/0.93 -20:1.19/1.16 -15:1.47/1.43 -10:1.80/1.75 -5:2.18/2.13
            0:2.62/2.56 5:3.13/3.06 10:3.71/3.63 15:4.37/4.28 20:5.11/5.01 25:5.95/5.84 30:6.88/6.76 35:7.92/7.79
            40:9.08/8.93 45:10.36/10.20 50:11.76/11.59 55:13.31/13.12 60:15.00/14.80
        """,
        // R452A — glissement max 4.2 K, -40 à 60 °C
        "R452A" to """
            -40:1.40/1.16 -35:1.75/1.46 -30:2.16/1.82 -25:2.64/2.24 -20:3.20/2.73 -15:3.85/3.30 -10:4.58/3.96 -5:5.42/4.71
            0:6.37/5.57 5:7.44/6.54 10:8.64/7.63 15:9.97/8.86 20:11.45/10.24 25:13.09/11.76 30:14.88/13.46 35:16.86/15.34
            40:19.02/17.41 45:21.37/19.69 50:23.94/22.19 55:26.72/24.95 60:29.73/27.99
        """,
        // R452B — glissement max 1.3 K, -40 à 50 °C
        "R452B" to """
            -40:1.70/1.63 -35:2.12/2.03 -30:2.62/2.51 -25:3.20/3.07 -20:3.88/3.72 -15:4.66/4.47 -10:5.56/5.34 -5:6.58/6.32
            0:7.74/7.44 5:9.04/8.71 10:10.51/10.13 15:12.14/11.72 20:13.96/13.49 25:15.98/15.45 30:18.20/17.62 35:20.65/20.02
            40:23.34/22.66 45:26.28/25.55 50:29.50/28.73
        """,
        // R454B — glissement max 1.5 K, -40 à 60 °C
        "R454B" to """
            -40:1.69/1.60 -35:2.11/1.99 -30:2.60/2.46 -25:3.18/3.01 -20:3.85/3.65 -15:4.62/4.40 -10:5.51/5.25 -5:6.53/6.22
            0:7.67/7.32 5:8.97/8.57 10:10.42/9.97 15:12.04/11.54 20:13.85/13.28 25:15.84/15.22 30:18.05/17.36 35:20.47/19.72
            40:23.14/22.33 45:26.05/25.18 50:29.23/28.32 55:32.69/31.75 60:36.46/35.50
        """,
        // R454C — glissement max 8.3 K, -40 à 60 °C
        "R454C" to """
            -40:1.30/0.89 -35:1.61/1.12 -30:1.99/1.40 -25:2.42/1.74 -20:2.93/2.13 -15:3.51/2.59 -10:4.17/3.12 -5:4.93/3.72
            0:5.78/4.42 5:6.74/5.21 10:7.81/6.11 15:9.00/7.12 20:10.32/8.25 25:11.77/9.51 30:13.37/10.92 35:15.12/12.47
            40:17.02/14.19 45:19.09/16.09 50:21.34/18.18 55:23.77/20.48 60:26.38/23.00
        """,
        // R455A — glissement max 13.4 K, -40 à 60 °C
        "R455A" to """
            -40:1.74/0.95 -35:2.13/1.20 -30:2.58/1.50 -25:3.11/1.85 -20:3.72/2.27 -15:4.41/2.76 -10:5.19/3.33 -5:6.08/3.98
            0:7.06/4.72 5:8.17/5.57 10:9.39/6.53 15:10.74/7.61 20:12.23/8.82 25:13.85/10.17 30:15.63/11.68 35:17.57/13.35
            40:19.67/15.20 45:21.94/17.25 50:24.39/19.50 55:27.02/21.98 60:29.84/24.72
        """,
        // R507A — glissement max 0.0 K, -40 à 60 °C
        "R507A" to """
            -40:1.39 -35:1.73 -30:2.13 -25:2.60 -20:3.15 -15:3.77 -10:4.50 -5:5.32
            0:6.24 5:7.29/7.28 10:8.46/8.45 15:9.77/9.76 20:11.22/11.21 25:12.83/12.81 30:14.60/14.59 35:16.55/16.54
            40:18.70/18.68 45:21.04/21.03 50:23.61/23.59 55:26.42/26.40 60:29.49/29.47
        """,
        // R513A — glissement max 0.2 K, -40 à 60 °C
        "R513A" to """
            -40:0.62 -35:0.78 -30:0.99 -25:1.23 -20:1.52 -15:1.86 -10:2.25 -5:2.71
            0:3.23 5:3.83 10:4.51 15:5.28 20:6.14 25:7.10 30:8.17 35:9.36
            40:10.67 45:12.11 50:13.70 55:15.43 60:17.33
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
        val bas = triees.first().first
        val haut = triees.last().first
        if (x < bas - PINCEMENT || x > haut + PINCEMENT) return null

        // Ramené sur la borne quand il n'en sort que du bruit : voir [PINCEMENT].
        val borne = x.coerceIn(bas, haut)
        val apres = triees.indexOfFirst { it.first >= borne }
        if (apres == 0) return triees.first().second
        val (x0, y0) = triees[apres - 1]
        val (x1, y1) = triees[apres]
        if (x1 == x0) return y0
        return y0 + (y1 - y0) * (borne - x0) / (x1 - x0)
    }

    /**
     * De combien on tolère de sortir de la table avant de se taire.
     *
     * Ce n'est **pas** une extrapolation déguisée, et l'ordre de grandeur le dit :
     * un millionième de bar, soit dix mille fois moins que ce qu'un manomètre
     * affiche. C'est du bruit de virgule flottante, et il vient d'un aller-retour
     * précis : la réglette raisonne en bar relatifs et interroge la courbe en
     * absolus, or `0,46 − 1,013 + 1,013` ne rend pas `0,46` mais
     * `0,459999999999999 96`. La borne basse d'une courbe tombait ainsi « hors
     * table » à un milliardième de bar de son propre premier point — le R-450A,
     * dont la courbe démarre à 0,46 bar, ne se lisait plus au bas de son curseur.
     *
     * Au-delà de ce bruit, la règle tient entière : rien n'est extrapolé, et une
     * pression hors de la plage calculée ne rend toujours rien.
     */
    private const val PINCEMENT = 1e-6

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
