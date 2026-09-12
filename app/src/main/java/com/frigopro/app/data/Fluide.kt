package com.frigopro.app.data

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Les fluides frigorigènes courants et leur pouvoir de réchauffement.
 *
 * Le GWP n'est pas un ornement : c'est lui qui, multiplié par la charge, donne
 * l'équivalent CO₂ d'une installation, et c'est cet équivalent — pas le poids
 * de fluide — qui décide de la fréquence des contrôles d'étanchéité imposés
 * par le règlement européen 517/2014. Un technicien qui se trompe de
 * périodicité est en infraction ; la calculer pour lui est le genre de chose
 * qu'une application de terrain doit faire.
 *
 * La table reprend les valeurs du règlement (AR4, celles que l'administration
 * utilise). Elle est volontairement courte : les fluides qu'on croise en
 * commercial et en résidentiel. Un fluide absent reste saisissable — son GWP
 * est alors inconnu, et l'application le dit plutôt que d'inventer.
 */
object Fluides {

    /** Fluide → GWP (AR4), tel qu'employé par le règlement F-Gas. */
    private val GWP: Map<String, Int> = mapOf(
        "R22" to 1810,
        "R32" to 675,
        "R134A" to 1430,
        "R290" to 3,
        "R404A" to 3922,
        "R407A" to 2107,
        "R407C" to 1774,
        "R407F" to 1825,
        "R410A" to 2088,
        "R413A" to 2053,
        "R417A" to 2346,
        "R422D" to 2729,
        "R427A" to 2138,
        "R434A" to 3245,
        "R437A" to 1805,
        "R438A" to 2265,
        "R448A" to 1387,
        "R449A" to 1397,
        "R450A" to 605,
        "R452A" to 2141,
        "R452B" to 698,
        "R454B" to 466,
        "R454C" to 148,
        "R455A" to 148,
        "R507A" to 3985,
        "R513A" to 631,
        "R600A" to 3,
        "R717" to 0,
        "R744" to 1,
        "R1234YF" to 4,
        "R1234ZE" to 7,
    )

    /** Les fluides proposés à la saisie, dans l'ordre alphanumérique usuel. */
    val connus: List<String> = GWP.keys.sortedWith(compareBy({ it.length }, { it }))

    /** Forme canonique d'un intitulé saisi : « r452a », « R-452A » → « R452A ». */
    fun normaliser(saisie: String): String =
        saisie.trim().uppercase().replace("-", "").replace(" ", "")

    /** GWP du fluide, ou `null` s'il n'est pas au catalogue. */
    fun gwp(fluide: String): Int? = GWP[normaliser(fluide)]

    /**
     * Équivalent CO₂ de la charge, en tonnes.
     *
     * `null` quand le GWP est inconnu : mieux vaut une case vide qu'un chiffre
     * faux, dont découlerait une périodicité fausse.
     */
    fun tonnesEquivalentCo2(fluide: String, chargeKg: Double): Double? {
        val gwp = gwp(fluide) ?: return null
        return chargeKg * gwp / 1000.0
    }
}

/**
 * Périodicité du contrôle d'étanchéité, en mois, selon le règlement 517/2014.
 *
 * Les seuils portent sur l'équivalent CO₂ et non sur la masse : cinq
 * kilogrammes de R744 et cinq kilogrammes de R404A n'engagent pas les mêmes
 * obligations. [AUCUNE] n'est pas une dispense de bonne pratique, seulement
 * l'absence d'obligation réglementaire.
 */
enum class PeriodiciteControle(val mois: Int?, val libelle: String) {
    AUCUNE(null, "Non soumise"),
    DOUZE_MOIS(12, "Tous les 12 mois"),
    SIX_MOIS(6, "Tous les 6 mois"),
    TROIS_MOIS(3, "Tous les 3 mois"),
    ;

    companion object {

        /**
         * Périodicité pour un équivalent CO₂ donné.
         *
         * Seuils du règlement : 5 t, 50 t et 500 t. Un détecteur de fuite fixe
         * double les intervalles, ce que l'application ne modélise pas encore —
         * elle retient donc toujours la périodicité la plus exigeante, ce qui
         * est le sens de l'erreur à commettre.
         */
        fun pour(tonnesEqCo2: Double): PeriodiciteControle = when {
            tonnesEqCo2 < 5.0 -> AUCUNE
            tonnesEqCo2 < 50.0 -> DOUZE_MOIS
            tonnesEqCo2 < 500.0 -> SIX_MOIS
            else -> TROIS_MOIS
        }
    }
}

/**
 * Ce que l'application sait dire de l'étanchéité d'une machine : à quelle
 * échéance le prochain contrôle tombe, et s'il est déjà en retard.
 *
 * @param echeance `null` quand la machine n'est pas soumise à contrôle, ou
 *   qu'aucun contrôle n'a encore été consigné — on ne peut pas dater le
 *   suivant sans connaître le précédent.
 */
data class EtatEtancheite(
    val periodicite: PeriodiciteControle,
    val echeance: LocalDate?,
    val enRetard: Boolean,
) {

    companion object {

        /**
         * @param dernierControle date du dernier contrôle consigné, ou `null`.
         * @param aujourdhui le jour courant, passé en paramètre pour que le
         *   calcul soit éprouvable sans dépendre de l'horloge.
         */
        fun calculer(
            fluide: String,
            chargeKg: Double?,
            dernierControle: LocalDate?,
            aujourdhui: LocalDate,
        ): EtatEtancheite {
            val tonnes = chargeKg?.let { Fluides.tonnesEquivalentCo2(fluide, it) }
                ?: return EtatEtancheite(PeriodiciteControle.AUCUNE, null, false)
            val periodicite = PeriodiciteControle.pour(tonnes)
            val mois = periodicite.mois
            if (mois == null || dernierControle == null) {
                return EtatEtancheite(periodicite, null, false)
            }
            val echeance = dernierControle.plusMonths(mois.toLong())
            return EtatEtancheite(periodicite, echeance, echeance.isBefore(aujourdhui))
        }
    }
}

/** Arrondit à une décimale, la précision à laquelle un équivalent CO₂ se lit. */
fun Double.arrondiDixieme(): Double = (this * 10).roundToInt() / 10.0
