package com.frigopro.app.data

import java.time.Duration
import java.time.Instant

/**
 * Le temps passé sur une intervention.
 *
 * C'était la première des pistes ouvertes par le carnet de route, et la plus
 * réclamée : « ce qui s'est vraiment passé sur place ». Une intervention se
 * chronomètre en plusieurs fois — on arrive, on diagnostique, on repart
 * chercher une pièce, on revient — et c'est pour cela que le modèle ne peut
 * pas se réduire à deux horodatages.
 *
 * Trois champs suffisent :
 *
 * - [arriveeLe] : le premier démarrage, jamais réécrit. C'est l'heure
 *   d'arrivée qui figurera sur le compte-rendu.
 * - [demarreLe] : le début du segment en cours, `null` à l'arrêt. Sa présence
 *   *est* l'état « en marche » ; un booléen séparé pourrait le contredire.
 * - [cumuleS] : les secondes des segments déjà refermés.
 *
 * Le temps écoulé se recalcule donc à chaque affichage plutôt que d'être
 * stocké : une valeur stockée serait fausse dès la seconde suivante, et
 * surtout elle cesserait d'avancer si l'application était tuée pendant une
 * intervention — ce qui arrive, un téléphone dans une poche de combinaison.
 */
data class Chrono(
    val arriveeLe: Instant? = null,
    val demarreLe: Instant? = null,
    val cumuleS: Long = 0L,
) {

    /** En marche tant qu'un segment est ouvert. */
    val enMarche: Boolean get() = demarreLe != null

    /** Rien n'a jamais été chronométré. */
    val vierge: Boolean get() = arriveeLe == null && cumuleS == 0L

    /**
     * Durée totale à l'instant [maintenant], segment en cours compris.
     *
     * Une horloge qui recule — changement de fuseau, remise à l'heure par le
     * réseau — ne doit pas retrancher du temps déjà acquis : le segment en
     * cours est alors compté pour zéro plutôt que pour une valeur négative.
     */
    fun ecoulee(maintenant: Instant): Duration {
        val debut = demarreLe ?: return Duration.ofSeconds(cumuleS)
        val segment = Duration.between(debut, maintenant)
        val retenu = if (segment.isNegative) Duration.ZERO else segment
        return Duration.ofSeconds(cumuleS).plus(retenu)
    }

    /** Démarre, ou reprend après une pause. Sans effet s'il tourne déjà. */
    fun demarrer(maintenant: Instant): Chrono =
        if (enMarche) this else copy(arriveeLe = arriveeLe ?: maintenant, demarreLe = maintenant)

    /** Met en pause en banquant le segment en cours. Sans effet à l'arrêt. */
    fun arreter(maintenant: Instant): Chrono {
        if (!enMarche) return this
        return copy(demarreLe = null, cumuleS = ecoulee(maintenant).seconds)
    }

    /** Bascule marche/pause : ce que fait le bouton unique de l'écran. */
    fun basculer(maintenant: Instant): Chrono =
        if (enMarche) arreter(maintenant) else demarrer(maintenant)

    /**
     * Pose le temps total à la main.
     *
     * Le chronomètre suppose qu'on y pense en arrivant, et on n'y pense pas
     * toujours : on entre chez un client, on ouvre un carter, et le téléphone
     * reste dans la camionnette. Le temps se saisit donc aussi après coup, et
     * c'est un chemin normal — pas un rattrapage.
     *
     * Deux choses qu'elle ne fait **pas**, et c'est délibéré :
     *
     * - Elle **n'invente pas l'heure d'arrivée**. On pourrait la déduire —
     *   « il est 16 h, vous dites une heure et demie, vous êtes donc arrivé à
     *   14 h 30 » — mais le temps se saisit parfois le lendemain matin, et le
     *   chiffre déduit partirait alors sur un compte-rendu signé. [arriveeLe]
     *   reste ce qu'il était, quitte à rester inconnu : l'écran sait le dire.
     * - Elle **ne touche pas au statut**. Combien de temps a duré le travail et
     *   où il en est sont deux questions différentes ; clore une intervention
     *   reste un geste explicite.
     *
     * Un segment en cours est refermé : poser un total pendant que le chrono
     * tourne doit donner ce total, pas ce total plus ce qui court.
     */
    fun poser(duree: Duration): Chrono =
        copy(demarreLe = null, cumuleS = if (duree.isNegative) 0L else duree.seconds)
}

/**
 * Formate une durée telle qu'elle se lit sur un chronomètre : `42:10`, et
 * `1:05:30` passé l'heure.
 *
 * Les minutes et les secondes sont toujours sur deux chiffres, pour que le
 * chiffre ne saute pas latéralement à chaque seconde.
 */
fun Duration.enChrono(): String {
    val total = if (isNegative) 0L else seconds
    val heures = total / 3600
    val minutes = (total % 3600) / 60
    val secondes = total % 60
    return if (heures > 0) {
        "%d:%02d:%02d".format(heures, minutes, secondes)
    } else {
        "%02d:%02d".format(minutes, secondes)
    }
}

/**
 * Formate une durée comme on la dit : `1 h 05`, `45 min`.
 *
 * C'est la forme qui va sur un compte-rendu et dans un total de journée, là
 * où les secondes n'apportent rien.
 */
fun Duration.enDuree(): String {
    val total = if (isNegative) 0L else seconds
    val heures = total / 3600
    val minutes = (total % 3600) / 60
    return if (heures > 0) "$heures h %02d".format(minutes) else "$minutes min"
}
