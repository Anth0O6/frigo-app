package com.frigopro.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Le pas d'une maintenance préventive.
 *
 * Six valeurs, celles d'un contrat de maintenance : la ronde du matin, la visite
 * de la semaine, et les quatre cadences qui se comptent en mois.
 *
 * **Les mois ne sont pas des paquets de trente jours**, et c'est le point le plus
 * facile à rater de tout ce fichier. Une visite trimestrielle faite le 31 janvier
 * tombe le 30 avril, ce que `plusMonths` sait faire en ramenant au dernier jour du
 * mois ; la compter en quatre-vingt-dix jours l'aurait fait tomber le 1ᵉʳ mai,
 * puis dériver d'un jour à chaque trimestre — quatre trimestres de quatre-vingt-dix
 * jours font trois cent soixante jours, si bien qu'au bout de trois ans la visite
 * « annuelle » arriverait quinze jours trop tôt. Sur un contrat qui se contrôle,
 * cette dérive se voit.
 *
 * Elle est **distincte de [PeriodiciteControle]**, qui porte les 3, 6, 12 et 24
 * mois du règlement 517/2014. Les deux partagent des unités et rien d'autre : la
 * périodicité F-Gas se **déduit** de la charge et du GWP, elle double en présence
 * d'un détecteur de fuite, et elle n'a pas à plier quand on réorganise un plan de
 * maintenance. Les réunir aurait fait dépendre une obligation réglementaire d'un
 * choix commercial, ou l'inverse.
 */
enum class Periodicite(
    val libelle: String,
    /** Le pas en jours, pour les deux cadences courtes. `null` sinon. */
    val jours: Int?,
    /** Le pas en mois, pour les quatre autres. `null` sinon. */
    val mois: Int?,
    /**
     * Combien de jours à l'avance la visite s'annonce comme « à faire ».
     *
     * Posé à la main et non calculé, parce que chaque valeur se défend seule : on
     * n'annonce pas une ronde du matin trente jours à l'avance, et trois jours de
     * préavis sur une visite annuelle n'aident personne à l'organiser. Un préavis
     * proportionnel à la période est ce que demande le bon sens, et les six
     * chiffres le disent mieux qu'une formule.
     */
    val preavisJours: Long,
) {
    JOURNALIER("Journalier", jours = 1, mois = null, preavisJours = 0),
    HEBDOMADAIRE("Hebdomadaire", jours = 7, mois = null, preavisJours = 1),
    MENSUEL("Mensuel", jours = null, mois = 1, preavisJours = 3),
    TRIMESTRIEL("Trimestriel", jours = null, mois = 3, preavisJours = 7),
    SEMESTRIEL("Semestriel", jours = null, mois = 6, preavisJours = 15),
    ANNUEL("Annuel", jours = null, mois = 12, preavisJours = 30),
    ;

    /** La date qui suit [depuis] d'une période. Voir le KDoc de l'enum. */
    fun prochaine(depuis: LocalDate): LocalDate = when {
        mois != null -> depuis.plusMonths(mois.toLong())
        jours != null -> depuis.plusDays(jours.toLong())
        // Inatteignable : chaque valeur porte l'un ou l'autre. Rendre la date
        // telle quelle plutôt que de lancer — une boucle d'occurrences qui ne
        // progresse pas se détecte, une exception sur un écran ne se rattrape pas.
        else -> depuis
    }
}

/** Où en est une visite par rapport au jour. */
enum class StatutEcheance(val libelle: String) {
    /** L'échéance est passée et la visite n'est pas faite. */
    EN_RETARD("En retard"),

    /** C'est maintenant, ou dans le préavis de la périodicité. */
    A_FAIRE("À faire"),

    /** Rien à faire pour l'instant. */
    A_VENIR("À venir"),
    ;

    val appelleUneAction: Boolean get() = this != A_VENIR
}

/**
 * L'arithmétique du plan de maintenance : quand une visite est due, et combien
 * en étaient attendues sur une période.
 *
 * **Rien n'est stocké.** Aucune table ne tient d'« occurrences à venir » : une
 * échéance se déduit de la dernière visite et du pas, et des occurrences
 * engendrées à l'avance auraient cessé d'être justes au premier changement de
 * périodicité — il aurait fallu les régénérer, décider du sort de celles qu'on
 * avait déjà cochées, et vivre avec les deux vérités pendant la transition. C'est
 * la règle que suivent déjà les échéances F-Gas, le retard d'une facture, le
 * manque d'un article et les pénalités de retard.
 *
 * Ce qui est stocké, c'est le **journal** : une ligne par visite faite. L'échéance
 * en découle, l'historique est là pour la fiche d'une machine, et le taux de
 * réalisation se compte dessus.
 */
object Maintenance {

    /**
     * Quand la prochaine visite est due.
     *
     * @param derniereVisite la dernière visite faite, ou `null` si aucune.
     * @param depuisLe le point de départ du plan pour cet équipement — la date à
     *   laquelle la gamme lui a été affectée. Il est **indispensable** : sans lui,
     *   une machine mise en service en 2015 et rattachée aujourd'hui à une visite
     *   mensuelle paraîtrait devoir cent trente visites, et l'écran serait
     *   inutilisable le jour de sa mise en route. Le reculer à la main reste
     *   possible et affiche alors un vrai retard — c'est une information, pas un
     *   défaut.
     */
    fun echeance(
        derniereVisite: LocalDate?,
        depuisLe: LocalDate,
        periodicite: Periodicite,
    ): LocalDate = periodicite.prochaine(derniereVisite ?: depuisLe)

    /** Où en est cette visite, au jour dit. */
    fun statut(
        echeance: LocalDate,
        periodicite: Periodicite,
        aujourdhui: LocalDate,
    ): StatutEcheance = when {
        echeance.isBefore(aujourdhui) -> StatutEcheance.EN_RETARD
        echeance.isAfter(aujourdhui.plusDays(periodicite.preavisJours)) -> StatutEcheance.A_VENIR
        else -> StatutEcheance.A_FAIRE
    }

    /** Les jours de retard, ou zéro si l'échéance n'est pas passée. */
    fun joursDeRetard(echeance: LocalDate, aujourdhui: LocalDate): Long =
        if (echeance.isBefore(aujourdhui)) ChronoUnit.DAYS.between(echeance, aujourdhui) else 0L

    /**
     * Combien de visites le plan attendait entre [debut] et [fin], bornes
     * comprises.
     *
     * Les occurrences sont **parcourues** et non divisées, pour la raison qui fait
     * tout ce fichier : un trimestre n'a pas une longueur fixe, et diviser un
     * nombre de jours par quatre-vingt-dix aurait donné le mauvais compte sur une
     * période qui enjambe février. Le parcours part de [depuisLe] et avance d'un
     * pas à la fois, ce qui donne exactement les dates que l'écran affiche.
     *
     * Le compte s'arrête à [PAS_MAXIMUM] : une ronde journalière sur dix ans fait
     * trois mille six cents pas, ce qui est sans conséquence, mais une périodicité
     * qui n'avancerait pas boucherait l'écran sans rien dire. Le garde-fou est là
     * pour ce cas qui ne doit pas arriver.
     */
    fun occurrencesAttendues(
        depuisLe: LocalDate,
        debut: LocalDate,
        fin: LocalDate,
        periodicite: Periodicite,
    ): Int {
        if (fin.isBefore(debut)) return 0
        var compte = 0
        var courante = periodicite.prochaine(depuisLe)
        var pas = 0
        while (!courante.isAfter(fin) && pas < PAS_MAXIMUM) {
            if (!courante.isBefore(debut)) compte++
            val suivante = periodicite.prochaine(courante)
            // Une périodicité qui n'avance pas tournerait sans fin.
            if (!suivante.isAfter(courante)) break
            courante = suivante
            pas++
        }
        return compte
    }

    /** Dix ans de ronde journalière, et de quoi arrêter une périodicité immobile. */
    const val PAS_MAXIMUM: Int = 4_000
}

/**
 * Ce qu'une gamme a produit sur une période : ce qui était attendu, ce qui est
 * fait.
 *
 * **Les deux comptes plutôt qu'un pourcentage**, et c'est délibéré : « 23 faites
 * sur 26 attendues » se vérifie, « 88 % » invite à croire à une précision que ce
 * calcul n'a pas. Le pourcentage est là pour le coup d'œil, les deux nombres pour
 * la discussion — c'est ce qu'un client regarde quand il conteste.
 *
 * Il mesure **combien** ont été faites, et non si elles l'ont été à l'heure. Une
 * visite mensuelle faite trois fois en janvier puis plus rien jusqu'en mars compte
 * trois sur trois, ce qui est flatteur et faux au regard d'un contrat. Le dire ici
 * plutôt que de laisser croire : juger la ponctualité demanderait d'apparier
 * chaque visite à son occurrence et de décider d'une tolérance, ce qui est une
 * autre question — et une tolérance inventée serait pire qu'un compte simple.
 */
data class RealisationGamme(
    val attendues: Int,
    val faites: Int,
) {

    /**
     * La part réalisée, ou `null` quand rien n'était attendu.
     *
     * Jamais zéro : une gamme affectée la semaine dernière n'a rien à montrer, et
     * afficher « 0 % » la ferait passer pour négligée. Même règle que l'équivalent
     * CO₂ d'un fluide hors catalogue et que la marge sans prix d'achat.
     */
    val taux: Double? get() = if (attendues <= 0) null else faites.toDouble() / attendues

    /** Plafonné à cent : on peut faire plus de visites que le plan n'en demande. */
    val tauxPlafonne: Double? get() = taux?.coerceAtMost(1.0)
}
