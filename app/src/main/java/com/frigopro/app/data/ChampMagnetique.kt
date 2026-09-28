package com.frigopro.app.data

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Une mesure du magnétomètre, en microteslas, dans le repère du téléphone.
 *
 * Les trois axes sont gardés plutôt que la seule amplitude, parce que la
 * **saturation** se juge axe par axe : un capteur borné à 2 000 µT qui reçoit
 * 3 000 µT sur un seul axe rend 2 000, et l'amplitude seule ne dirait pas que la
 * valeur est plafonnée plutôt que mesurée.
 */
data class MesureChamp(val x: Double, val y: Double, val z: Double) {

    val amplitude: Double get() = sqrt(x * x + y * y + z * z)

    /** La plus grande composante en valeur absolue : ce qui sature en premier. */
    val composanteMaximale: Double get() = maxOf(abs(x), abs(y), abs(z))
}

/**
 * Ce qu'une seconde de mesures dit : son niveau, et son **agitation**.
 *
 * L'agitation est la donnée la moins évidente et la plus utile. Un aimant
 * permanent devant le capteur donne une valeur qui ne bouge pas ; une bobine
 * alimentée en alternatif donne une valeur qui fluctue, parce que le téléphone
 * échantillonne à une cadence qui n'est pas accrochée au 50 Hz du réseau et
 * qu'il en sort un battement. C'est le seul moyen, sur un téléphone, de
 * distinguer un champ continu d'un champ alternatif — et c'est précisément la
 * question « est-ce que cette bobine est alimentée, ou est-ce que je tiens un
 * aimant ? ».
 *
 * Elle ne sert que **comparée** à celle de l'ambiant, jamais seule : une main
 * qui tremble dans le champ terrestre agite déjà la mesure, et sans point de
 * comparaison on prendrait un tremblement pour une bobine.
 */
data class FenetreChamp(
    val amplitude: Double,
    val agitation: Double,
    val composanteMaximale: Double,
    val echantillons: Int,
) {

    companion object {

        /**
         * En dessous, l'écart-type ne veut rien dire : trois points ne décrivent
         * pas une fluctuation. À la cadence d'un magnétomètre — cinquante mesures
         * par seconde au mieux — huit points sont un sixième de seconde.
         */
        const val ECHANTILLONS_MINIMUM: Int = 8

        /** `null` tant qu'il n'y a pas de quoi conclure. */
        fun de(mesures: List<MesureChamp>): FenetreChamp? {
            if (mesures.size < ECHANTILLONS_MINIMUM) return null
            val amplitudes = mesures.map { it.amplitude }
            val moyenne = amplitudes.average()
            val variance = amplitudes.sumOf { (it - moyenne) * (it - moyenne) } / amplitudes.size
            return FenetreChamp(
                amplitude = moyenne,
                agitation = sqrt(variance),
                composanteMaximale = mesures.maxOf { it.composanteMaximale },
                echantillons = mesures.size,
            )
        }
    }
}

/**
 * L'ordre de grandeur du champ trouvé, au-delà de l'ambiant.
 *
 * Quatre marches et non une valeur, parce que c'est tout ce que l'instrument
 * permet d'affirmer : un magnétomètre de téléphone n'est pas calibré, sa
 * position dans l'appareil n'est pas documentée, et la distance à la bobine
 * change la mesure d'un facteur dix pour deux centimètres. Annoncer « 340 µT »
 * comme une mesure serait inventer une précision ; annoncer « champ net »
 * répond à la question qu'on se pose vraiment.
 */
enum class NiveauChamp(val libelle: String) {
    AUCUN("Rien de détectable"),
    FAIBLE("Champ faible"),
    NET("Champ net"),
    FORT("Champ fort"),

    /**
     * Le capteur est au bout de sa plage : la valeur est plafonnée et non
     * mesurée. Le dire compte — sans cela, deux aimants très différents
     * afficheraient le même chiffre, et on croirait l'instrument précis là où il
     * a décroché.
     */
    SATURE("Capteur saturé"),
    ;

    val detecte: Boolean get() = this != AUCUN
}

/** Le champ est constant, ou il fluctue. Voir [FenetreChamp.agitation]. */
enum class AllureChamp {
    /** Rien ne bouge : aimant permanent, ou bobine alimentée en continu. */
    CONSTANTE,

    /** La valeur fluctue : compatible avec une bobine alimentée en alternatif. */
    FLUCTUANTE,

    /** Trop faible ou trop bruité pour trancher. */
    INDETERMINEE,
}

/**
 * Ce que l'écran montre : le champ, ce qu'il dépasse, et ce qu'on peut en dire.
 *
 * Rien n'est stocké nulle part — un champ magnétique est une lecture d'instant,
 * et le garder en base n'aurait aucun sens. C'est la règle de tout l'onglet
 * Outils, qui ne regarde aucune donnée de l'application.
 */
data class LectureChamp(
    /** L'amplitude totale mesurée, ambiant compris. */
    val amplitude: Double,
    /** Ce qui dépasse l'ambiant. Négatif possible : un champ peut s'opposer. */
    val exces: Double,
    val niveau: NiveauChamp,
    val allure: AllureChamp,
    /** L'ambiant a été relevé sur place, plutôt que supposé. */
    val ambiantReleve: Boolean,
) {

    /**
     * Ce qu'on peut en dire, et **rien de plus**.
     *
     * Chaque phrase est une hypothèse compatible avec la mesure, pas un verdict :
     * « compatible avec une bobine alimentée » et « la bobine est alimentée » ne
     * s'affirment pas de la même façon, et la seconde engagerait l'instrument
     * au-delà de ce qu'il sait. Même règle que l'aide au dépannage, qui propose
     * des pistes et ne tranche jamais.
     */
    val interpretation: String
        get() = when {
            niveau == NiveauChamp.SATURE ->
                "Le champ dépasse ce que le capteur sait mesurer. Éloignez le téléphone " +
                    "de quelques centimètres pour retrouver une lecture."

            niveau == NiveauChamp.AUCUN && !ambiantReleve ->
                "Rien au-delà du champ terrestre supposé. Relevez l'ambiant à l'écart, " +
                    "puis approchez : dans une armoire, la tôle décale l'ambiant et un " +
                    "champ faible s'y perdrait."

            niveau == NiveauChamp.AUCUN ->
                "Rien au-delà de l'ambiant relevé. Approchez le dos du téléphone au " +
                    "contact : à deux centimètres, une bobine perd déjà l'essentiel de " +
                    "ce qu'elle rayonne."

            allure == AllureChamp.FLUCTUANTE ->
                "Champ qui fluctue : compatible avec une bobine alimentée en alternatif, " +
                    "ou un câble qui débite. À confirmer au multimètre si la suite en dépend."

            allure == AllureChamp.CONSTANTE ->
                "Champ constant : aimant permanent, ou bobine alimentée en continu. Une " +
                    "pièce d'acier aimantée en donne autant, et ne prouve rien d'électrique."

            else ->
                "Champ présent, trop peu marqué pour dire s'il est constant ou alternatif. " +
                    "Approchez encore, et tenez le téléphone immobile."
        }
}

/**
 * Le magnétomètre d'un téléphone, et ce qu'on peut honnêtement en tirer.
 *
 * **Il ne donne pas le sens de rotation d'un moteur, et aucun code n'y
 * changerait rien.** Le champ tournant d'un moteur triphasé en 50 Hz fait
 * cinquante tours par seconde. Pour dire dans quel sens un vecteur tourne il
 * faut au moins trois mesures par tour, et en pratique huit — soit quatre cents
 * mesures par seconde. Un magnétomètre de téléphone en rend cinquante à cent,
 * parce qu'il est conçu pour une boussole. À cinquante mesures pour cinquante
 * tours on retombe au même point à chaque fois : le vecteur paraît immobile, et
 * le sens n'est plus dans le signal. Ce n'est pas une approximation qu'on
 * pourrait resserrer, c'est une information perdue avant d'arriver au code.
 *
 * L'intuition qui trompe est celle de l'aiguille, et elle vaut d'être écrite :
 * une **vraie** aiguille de boussole ne tournerait pas davantage devant un
 * moteur — son inertie l'empêche de suivre du 50 Hz, elle resterait plantée. Les
 * indicateurs à disque tournant du commerce ne sont pas des boussoles : ce sont
 * de petits moteurs à induction **raccordés aux trois phases**. C'est le
 * raccordement qui fait le travail, jamais la proximité.
 */
object ChampMagnetique {

    /**
     * Le champ terrestre en France métropolitaine, faute d'ambiant relevé.
     *
     * Environ 48 µT, et c'est une **supposition assumée** : la valeur varie avec
     * le lieu, et surtout la tôle d'une armoire ou la charpente d'un local
     * technique la décale de plusieurs dizaines de microteslas. C'est pour cela
     * que relever l'ambiant sur place est proposé plutôt que facultatif — mais
     * supposer permet à l'outil de servir dès son ouverture, au lieu d'exiger un
     * geste avant d'afficher quoi que ce soit.
     */
    const val AMBIANT_NOMINAL: Double = 48.0

    /**
     * En dessous, rien ne se distingue du bruit d'une main qui bouge.
     *
     * Tourner le téléphone de quelques degrés dans le champ terrestre change déjà
     * l'amplitude de plusieurs microteslas ; un seuil plus bas ferait clignoter
     * l'outil au moindre geste, et une détection qui se déclenche tout le temps
     * est une détection qu'on cesse de lire.
     */
    const val SEUIL_DETECTION: Double = 8.0

    /** Au-delà, le champ ne s'explique plus par un décalage d'ambiant. */
    const val SEUIL_NET: Double = 100.0

    /** Au-delà, on est au contact d'un aimant ou d'une bobine. */
    const val SEUIL_FORT: Double = 1_000.0

    /**
     * La part de la plage du capteur au-delà de laquelle on le déclare saturé.
     *
     * Pas 100 % : un capteur borné à 2 000 µT rend rarement exactement 2 000, et
     * attendre la valeur exacte laisserait passer une saturation en l'affichant
     * comme une mesure.
     */
    const val PART_SATURATION: Double = 0.95

    /**
     * L'agitation en deçà de laquelle on ne conclut rien, en µT.
     *
     * Un plancher en plus du rapport à l'ambiant : sur un téléphone posé sur une
     * table, l'agitation de l'ambiant peut tomber si bas que trois fois cette
     * valeur resterait dans le bruit du convertisseur.
     */
    const val AGITATION_PLANCHER: Double = 3.0

    /** Combien de fois l'agitation de l'ambiant avant de parler de fluctuation. */
    const val FACTEUR_AGITATION: Double = 3.0

    /**
     * Ce qu'on lit, à partir de la fenêtre courante et de l'ambiant.
     *
     * @param reference l'ambiant relevé à l'écart, ou `null` pour supposer
     *   [AMBIANT_NOMINAL].
     * @param porteeCapteur la plage du capteur, telle que l'appareil la déclare :
     *   la saturation se juge contre le vrai capteur et non contre une constante,
     *   parce qu'elle va de 2 000 à 4 900 µT selon les téléphones.
     */
    fun lire(
        fenetre: FenetreChamp,
        reference: FenetreChamp?,
        porteeCapteur: Double,
    ): LectureChamp {
        val ambiant = reference?.amplitude ?: AMBIANT_NOMINAL
        val exces = fenetre.amplitude - ambiant
        val sature = porteeCapteur > 0.0 &&
            fenetre.composanteMaximale >= porteeCapteur * PART_SATURATION

        val niveau = when {
            sature -> NiveauChamp.SATURE
            exces >= SEUIL_FORT -> NiveauChamp.FORT
            exces >= SEUIL_NET -> NiveauChamp.NET
            // La valeur absolue : un champ qui **s'oppose** à l'ambiant le fait
            // baisser, et un aimant présenté dans le mauvais sens se verrait
            // sinon comme une absence de champ.
            abs(exces) >= SEUIL_DETECTION -> NiveauChamp.FAIBLE
            else -> NiveauChamp.AUCUN
        }

        return LectureChamp(
            amplitude = fenetre.amplitude,
            exces = exces,
            niveau = niveau,
            allure = allure(fenetre, reference, niveau),
            ambiantReleve = reference != null,
        )
    }

    /**
     * Constant ou fluctuant, comparé à l'agitation de l'ambiant.
     *
     * Rien n'est tranché quand aucun champ n'est détecté : qualifier l'allure
     * d'un champ qui n'est pas là n'aurait aucun sens, et l'écran afficherait une
     * phrase sur du bruit.
     */
    private fun allure(
        fenetre: FenetreChamp,
        reference: FenetreChamp?,
        niveau: NiveauChamp,
    ): AllureChamp {
        if (!niveau.detecte) return AllureChamp.INDETERMINEE
        val plancher = maxOf(
            AGITATION_PLANCHER,
            (reference?.agitation ?: 0.0) * FACTEUR_AGITATION,
        )
        return when {
            fenetre.agitation >= plancher -> AllureChamp.FLUCTUANTE
            // Un champ net et parfaitement calme est un aimant ; un champ faible
            // et calme ne permet pas de l'affirmer, la fluctuation d'une bobine
            // lointaine étant elle aussi faible.
            niveau == NiveauChamp.FAIBLE -> AllureChamp.INDETERMINEE
            else -> AllureChamp.CONSTANTE
        }
    }
}
