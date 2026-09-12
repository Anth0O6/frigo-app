package com.frigopro.app.data

/**
 * L'aide au dépannage : ce que les relevés suggèrent, et quoi vérifier.
 *
 * Le raisonnement est celui qu'un frigoriste fait de tête — croiser la
 * surchauffe et le sous-refroidissement pour situer le défaut entre le
 * condenseur et l'évaporateur. L'écrire ici ne remplace pas le technicien :
 * cela lui évite de le refaire à 7 heures du matin sur un toit, et surtout
 * cela lui rappelle les pistes qu'on oublie quand on a déjà une idée.
 *
 * Deux partis pris tiennent tout le reste :
 *
 * - **Des pistes, jamais un verdict.** Chaque cause est accompagnée du
 *   *contrôle* qui la confirme ou l'écarte. Une application qui annoncerait
 *   « le détendeur est fermé » ferait remplacer des détendeurs qui vont bien.
 * - **Le silence est une réponse.** Sans surchauffe ni sous-refroidissement,
 *   il n'y a pas de diagnostic, et [analyser] rend `null` plutôt que de
 *   meubler.
 *
 * Rien d'Android ici : tout s'éprouve sur la JVM.
 */
object Depannage {

    /** En deçà, la surchauffe est jugée faible : risque de retour liquide. */
    const val SURCHAUFFE_FAIBLE_K = 3.0

    /** Au-delà, la surchauffe est jugée élevée : l'évaporateur est mal alimenté. */
    const val SURCHAUFFE_ELEVEE_K = 8.0

    /** En deçà, le sous-refroidissement est jugé faible : manque de liquide. */
    const val SOUS_REFROIDISSEMENT_FAIBLE_K = 2.0

    /** Au-delà, le sous-refroidissement est jugé élevé : liquide en excès. */
    const val SOUS_REFROIDISSEMENT_ELEVE_K = 8.0

    /**
     * Croise surchauffe et sous-refroidissement pour retenir un symptôme et
     * classer les causes probables.
     *
     * @return `null` quand le relevé n'en dit pas assez — c'est le cas normal
     *   d'un relevé à peine commencé.
     */
    fun analyser(releve: Releve): Diagnostic? {
        val surchauffe = releve.surchauffeK
        val sousRefroidissement = releve.sousRefroidissementK
        if (surchauffe == null && sousRefroidissement == null) return null

        val sh = surchauffe?.let(::niveauSurchauffe)
        val sc = sousRefroidissement?.let(::niveauSousRefroidissement)

        val symptome = symptome(sh, sc) ?: return null
        return Diagnostic(symptome = symptome, causes = causes(symptome))
    }

    private fun niveauSurchauffe(valeur: Double): Niveau = when {
        valeur < SURCHAUFFE_FAIBLE_K -> Niveau.FAIBLE
        valeur > SURCHAUFFE_ELEVEE_K -> Niveau.ELEVE
        else -> Niveau.NORMAL
    }

    private fun niveauSousRefroidissement(valeur: Double): Niveau = when {
        valeur < SOUS_REFROIDISSEMENT_FAIBLE_K -> Niveau.FAIBLE
        valeur > SOUS_REFROIDISSEMENT_ELEVE_K -> Niveau.ELEVE
        else -> Niveau.NORMAL
    }

    /**
     * Le symptôme retenu. Quand une seule des deux grandeurs est relevée, on
     * ne conclut que si elle sort de la plage normale : une surchauffe normale
     * seule ne dit rien, et prétendre le contraire serait rassurer à tort.
     */
    private fun symptome(sh: Niveau?, sc: Niveau?): Symptome? = when {
        sh == Niveau.ELEVE && sc == Niveau.FAIBLE -> Symptome.MANQUE_DE_FLUIDE
        sh == Niveau.ELEVE && sc == Niveau.ELEVE -> Symptome.RESTRICTION_LIGNE_LIQUIDE
        sh == Niveau.ELEVE -> Symptome.SOUS_ALIMENTATION_EVAPORATEUR
        sh == Niveau.FAIBLE && sc == Niveau.ELEVE -> Symptome.EXCES_DE_CHARGE
        sh == Niveau.FAIBLE -> Symptome.SUR_ALIMENTATION_EVAPORATEUR
        sc == Niveau.ELEVE -> Symptome.CONDENSATION_DEGRADEE
        sc == Niveau.FAIBLE -> Symptome.MANQUE_DE_FLUIDE
        else -> null
    }

    private fun causes(symptome: Symptome): List<Cause> = when (symptome) {
        Symptome.MANQUE_DE_FLUIDE -> listOf(
            Cause(
                "Charge insuffisante",
                "Contrôler le voyant liquide et rechercher une fuite au détecteur avant tout complément.",
            ),
            Cause(
                "Fuite sur la ligne liquide",
                "Inspecter raccords, brasures et passages de paroi ; consigner la fuite au registre.",
            ),
            Cause(
                "Filtre déshydrateur colmaté",
                "Mesurer l'écart de température entre l'entrée et la sortie du filtre.",
            ),
        )

        Symptome.RESTRICTION_LIGNE_LIQUIDE -> listOf(
            Cause(
                "Filtre déshydrateur colmaté",
                "Mesurer l'écart de température entre l'entrée et la sortie du filtre.",
            ),
            Cause(
                "Détendeur bloqué ou sous-dimensionné",
                "Vérifier la manœuvre du détendeur et l'absence de givre localisé à son entrée.",
            ),
            Cause(
                "Vanne liquide partiellement fermée",
                "Reprendre le tracé de la ligne et l'ouverture complète de chaque vanne.",
            ),
        )

        Symptome.SOUS_ALIMENTATION_EVAPORATEUR -> listOf(
            Cause(
                "Détendeur trop fermé",
                "Vérifier le serrage du bulbe et son isolation, puis ouvrir d'un quart de tour.",
            ),
            Cause(
                "Charge insuffisante",
                "Contrôler le voyant liquide et rechercher une fuite avant tout complément.",
            ),
            Cause(
                "Évaporateur encrassé ou givré",
                "Contrôler le débit d'air, l'état des ailettes et le bon déroulement du dégivrage.",
            ),
        )

        Symptome.EXCES_DE_CHARGE -> listOf(
            Cause(
                "Surcharge en fluide",
                "Récupérer le surplus et le consigner ; ne jamais purger à l'atmosphère.",
            ),
            Cause(
                "Détendeur trop ouvert",
                "Contrôler la surchauffe au bulbe et refermer par quarts de tour.",
            ),
            Cause(
                "Risque de retour liquide au compresseur",
                "Arrêter si le carter est givré : un coup de liquide casse les clapets.",
            ),
        )

        Symptome.SUR_ALIMENTATION_EVAPORATEUR -> listOf(
            Cause(
                "Détendeur trop ouvert",
                "Contrôler le serrage et l'isolation du bulbe, puis refermer par quarts de tour.",
            ),
            Cause(
                "Bulbe mal placé ou mal isolé",
                "Le reposer sur une partie horizontale propre de l'aspiration, à 1 h ou 5 h.",
            ),
            Cause(
                "Charge thermique insuffisante",
                "Vérifier que la chambre n'est pas déjà au point de consigne et que les ventilateurs tournent.",
            ),
        )

        Symptome.CONDENSATION_DEGRADEE -> listOf(
            Cause(
                "Condenseur encrassé",
                "Nettoyer les ailettes et contrôler la rotation des ventilateurs.",
            ),
            Cause(
                "Incondensables dans le circuit",
                "Comparer la pression à l'arrêt avec la pression de saturation à la température ambiante.",
            ),
            Cause(
                "Surcharge en fluide",
                "Récupérer le surplus et le consigner.",
            ),
        )
    }

    private enum class Niveau { FAIBLE, NORMAL, ELEVE }
}

/** Ce que les relevés désignent, en une phrase que le technicien reconnaît. */
enum class Symptome(val libelle: String) {
    MANQUE_DE_FLUIDE("Surchauffe élevée, sous-refroidissement faible"),
    RESTRICTION_LIGNE_LIQUIDE("Surchauffe et sous-refroidissement élevés"),
    SOUS_ALIMENTATION_EVAPORATEUR("Surchauffe élevée"),
    EXCES_DE_CHARGE("Surchauffe faible, sous-refroidissement élevé"),
    SUR_ALIMENTATION_EVAPORATEUR("Surchauffe faible"),
    CONDENSATION_DEGRADEE("Sous-refroidissement élevé"),
}

/**
 * Une piste, et le geste qui tranche.
 *
 * Les deux vont ensemble : une cause sans contrôle associé est une invitation
 * à changer une pièce au hasard.
 */
data class Cause(val intitule: String, val controle: String)

/** Ce que l'aide au dépannage rend : un symptôme, et des pistes ordonnées. */
data class Diagnostic(val symptome: Symptome, val causes: List<Cause>)
