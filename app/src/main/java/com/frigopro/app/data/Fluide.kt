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
/**
 * Classification de sécurité d'un fluide frigorigène (ISO 817 / ASHRAE 34).
 *
 * Deux axes, et chacun commande quelque chose sur le chantier : la **toxicité**
 * décide de la ventilation et de la détection, l'**inflammabilité** décide des
 * outils, du brasage et de la charge maximale admise dans un local.
 *
 * Le libellé porte le sens et pas seulement le code : c'est lui que l'écran
 * montre, parce que « A2L » ne dit rien à qui n'a pas la table en tête.
 */
enum class ClasseSecurite(
    val code: String,
    val toxicite: String,
    val inflammabilite: String,
) {
    A1("A1", "Faible toxicité", "Aucune propagation de flamme"),
    A2L("A2L", "Faible toxicité", "Faiblement inflammable"),
    A2("A2", "Faible toxicité", "Inflammable"),
    A3("A3", "Faible toxicité", "Très inflammable"),
    B1("B1", "Toxicité élevée", "Aucune propagation de flamme"),
    B2L("B2L", "Toxicité élevée", "Faiblement inflammable"),
    B2("B2", "Toxicité élevée", "Inflammable"),
    B3("B3", "Toxicité élevée", "Très inflammable"),
    ;

    /** Le résumé d'une ligne : « A2L — faible toxicité, faiblement inflammable ». */
    val resume: String get() = "$code — ${toxicite.lowercase()}, ${inflammabilite.lowercase()}"

    /** Un fluide qui demande des précautions particulières de mise en œuvre. */
    val inflammable: Boolean get() = this != A1 && this != B1

    /** Un fluide toxique : l'ammoniac, dans ce catalogue. */
    val toxique: Boolean get() = code.startsWith("B")
}

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

    /**
     * Fluide → classification de sécurité ISO 817 / ASHRAE 34.
     *
     * Elle dit deux choses qui commandent la façon de travailler : la **toxicité**
     * (A faible, B élevée) et l'**inflammabilité** (1 aucune propagation de flamme,
     * 2L faible, 2 moyenne, 3 élevée). Un A2L impose des outils et une ventilation
     * qu'un A1 ne demande pas, et le R-717 est le seul B du catalogue — on ne
     * travaille pas l'ammoniac comme un HFC.
     *
     * Ces valeurs sont **des faits de normalisation**, pas des mesures : chacune
     * tient en trois caractères, ce qui les rend bien moins exposées à la coquille
     * qu'une courbe de vingt points (voir [CourbesSaturation]). L'écran affiche
     * malgré tout ce que la classe **signifie** plutôt que son seul code : « A2L »
     * ne dit rien à qui n'a pas la table en tête, et un code faux passerait alors
     * inaperçu là où une mention « faiblement inflammable » sur un R-410A sauterait
     * aux yeux.
     */
    private val SECURITE: Map<String, ClasseSecurite> = mapOf(
        "R22" to ClasseSecurite.A1,
        "R32" to ClasseSecurite.A2L,
        "R134A" to ClasseSecurite.A1,
        "R290" to ClasseSecurite.A3,
        "R404A" to ClasseSecurite.A1,
        "R407A" to ClasseSecurite.A1,
        "R407C" to ClasseSecurite.A1,
        "R407F" to ClasseSecurite.A1,
        "R410A" to ClasseSecurite.A1,
        "R413A" to ClasseSecurite.A1,
        "R417A" to ClasseSecurite.A1,
        "R422D" to ClasseSecurite.A1,
        "R427A" to ClasseSecurite.A1,
        "R434A" to ClasseSecurite.A1,
        "R437A" to ClasseSecurite.A1,
        "R438A" to ClasseSecurite.A1,
        "R448A" to ClasseSecurite.A1,
        "R449A" to ClasseSecurite.A1,
        "R450A" to ClasseSecurite.A1,
        "R452A" to ClasseSecurite.A1,
        "R452B" to ClasseSecurite.A2L,
        "R454B" to ClasseSecurite.A2L,
        "R454C" to ClasseSecurite.A2L,
        "R455A" to ClasseSecurite.A2L,
        "R507A" to ClasseSecurite.A1,
        "R513A" to ClasseSecurite.A1,
        "R600A" to ClasseSecurite.A3,
        "R717" to ClasseSecurite.B2L,
        "R744" to ClasseSecurite.A1,
        "R1234YF" to ClasseSecurite.A2L,
        "R1234ZE" to ClasseSecurite.A2L,
    )

    /** Les fluides proposés à la saisie, dans l'ordre alphanumérique usuel. */
    val connus: List<String> = GWP.keys.sortedWith(compareBy({ it.length }, { it }))

    /** Forme canonique d'un intitulé saisi : « r452a », « R-452A » → « R452A ». */
    fun normaliser(saisie: String): String =
        saisie.trim().uppercase().replace("-", "").replace(" ", "")

    /**
     * Forme lisible d'un nom canonique : « R410A » → « R-410A ».
     *
     * Le tiret est celui de la désignation normalisée, telle qu'elle est imprimée
     * sur une bouteille comme dans une table constructeur. Le stockage, lui, reste
     * sans tiret : [normaliser] est ce qui permet de comparer deux saisies, et les
     * deux fonctions ne doivent pas se confondre — comparer des formes d'affichage
     * ferait rater un doublon.
     *
     * Les hydrocarbures (6xx) et les oléfines (1xxx) portent un suffixe d'isomère
     * en minuscule — « R-600a », « R-1234yf » —, le seul endroit de la
     * nomenclature où la casse dit quelque chose.
     */
    fun afficher(fluide: String): String {
        val nom = normaliser(fluide)
        if (!nom.startsWith("R") || nom.length < 2) return nom
        val reste = nom.drop(1)
        if (!reste.first().isDigit()) return nom
        val chiffres = reste.takeWhile { it.isDigit() }
        val suffixe = reste.drop(chiffres.length)
        val isomere = chiffres.firstOrNull() == '6' || chiffres.length == 4
        return "R-" + chiffres + if (isomere) suffixe.lowercase() else suffixe
    }

    /** GWP du fluide, ou `null` s'il n'est pas au catalogue. */
    fun gwp(fluide: String): Int? = GWP[normaliser(fluide)]

    /**
     * Classe de sécurité du fluide, ou `null` s'il n'est pas au catalogue.
     *
     * Même règle que le GWP : rien n'est deviné. Un fluide inconnu ne se voit pas
     * attribuer « A1 » parce que c'est le cas le plus fréquent — ce serait dire
     * « ininflammable » d'un fluide dont on ne sait rien, et c'est exactement le
     * genre de supposition qui met le feu à un local technique.
     */
    fun classeSecurite(fluide: String): ClasseSecurite? = SECURITE[normaliser(fluide)]

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

    /**
     * Vingt-quatre mois : la seule périodicité qu'aucun seuil ne produit seul.
     *
     * Elle n'existe qu'avec un détecteur de fuite fixe, qui double l'intervalle de
     * douze mois. C'est pour cela qu'elle est déclarée ici et pas dans la suite des
     * seuils : `pour()` ne la rend jamais sans détecteur.
     */
    VINGT_QUATRE_MOIS(24, "Tous les 24 mois"),

    DOUZE_MOIS(12, "Tous les 12 mois"),
    SIX_MOIS(6, "Tous les 6 mois"),
    TROIS_MOIS(3, "Tous les 3 mois"),
    ;

    /**
     * L'intervalle doublé, tel qu'un détecteur de fuite fixe l'autorise.
     *
     * Une machine non soumise le reste : doubler une absence d'obligation ne veut
     * rien dire, et rendre « tous les 24 mois » pour trois kilogrammes de R-134A
     * inventerait une obligation là où le règlement n'en pose aucune.
     */
    fun allege(): PeriodiciteControle = when (this) {
        AUCUNE -> AUCUNE
        TROIS_MOIS -> SIX_MOIS
        SIX_MOIS -> DOUZE_MOIS
        DOUZE_MOIS -> VINGT_QUATRE_MOIS
        VINGT_QUATRE_MOIS -> VINGT_QUATRE_MOIS
    }

    companion object {

        /**
         * Périodicité pour un équivalent CO₂ donné.
         *
         * Seuils du règlement : 5 t, 50 t et 500 t.
         *
         * Un **détecteur de fuite fixe double les intervalles** (art. 4 § 3 du
         * règlement 517/2014), et c'est ce que `detecteurFixe` modélise. Il vaut
         * `false` par défaut, et ce défaut est un choix : une machine dont on ne
         * sait pas si elle est équipée retient la périodicité la plus exigeante.
         * Annoncer un contrôle trop tôt fait perdre une heure ; l'annoncer trop
         * tard expose à une sanction.
         *
         * La fiche machine ne porte pas encore ce champ — seul l'outil F-Gas
         * permet de poser la question — ce qui est la raison pour laquelle le
         * paramètre arrive avec un défaut plutôt qu'en rupture.
         */
        fun pour(tonnesEqCo2: Double, detecteurFixe: Boolean = false): PeriodiciteControle {
            val base = when {
                tonnesEqCo2 < 5.0 -> AUCUNE
                tonnesEqCo2 < 50.0 -> DOUZE_MOIS
                tonnesEqCo2 < 500.0 -> SIX_MOIS
                else -> TROIS_MOIS
            }
            return if (detecteurFixe) base.allege() else base
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
