package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

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

/**
 * Une **gamme de maintenance** : une liste de points à vérifier, et la cadence à
 * laquelle on les vérifie.
 *
 * C'est la pièce centrale de la GMAO, et elle est volontairement **détachée des
 * équipements** : « Visite mensuelle groupe froid » se décrit une fois et
 * s'affecte à cent machines, là où la décrire machine par machine aurait rendu
 * impossible de corriger un point sur tout un parc. L'affectation est une table à
 * part ([AffectationGamme]) pour cette seule raison.
 *
 * Elle est **vide à l'installation**, comme la liste des types d'intervention et
 * pour le même motif : les points d'une visite mensuelle sont ceux d'un contrat et
 * d'un site, pas ceux d'un métier. En livrer une d'office aurait fait cocher des
 * points que personne n'a relus.
 */
@Entity(tableName = "gammes")
data class GammeMaintenance(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val libelle: String,
    val periodicite: Periodicite = Periodicite.MENSUEL,
    val rang: Int = 0,
    val modifieLe: Instant = Instant.EPOCH,
)

/**
 * Un point d'une gamme : « contrôler la surchauffe », « nettoyer le condenseur ».
 *
 * Il est **recopié** sur chaque visite au moment où elle se fait, exactement comme
 * [PointChecklist] l'est sur chaque intervention : le modèle peut changer sans
 * réécrire ce qu'on avait demandé de vérifier aux visites passées. Une gamme dont
 * on retire un point ne doit pas faire disparaître ce point des visites de l'an
 * dernier — un contrôle les rapproche du contrat de l'époque.
 */
@Entity(tableName = "points_gamme", indices = [Index("gammeId")])
data class PointGamme(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val gammeId: String,
    val libelle: String,
    val rang: Int = 0,
    val modifieLe: Instant = Instant.EPOCH,
)

/**
 * Quelle gamme suit quel équipement, et depuis quand.
 *
 * L'unicité `(equipementId, gammeId)` est portée par un **index unique**, et c'est
 * une règle et non une optimisation : deux affectations de la même gamme à la même
 * machine donneraient deux échéances contradictoires sans que rien ne dise
 * laquelle est bonne. C'est le deuxième index unique du projet, après celui des
 * stocks, et il est là pour la même raison — la base le refuse plutôt que de
 * compter sur le dépôt pour y penser.
 *
 * [depuisLe] est le point de départ du plan, et c'est le champ qui rend l'écran
 * utilisable le jour de sa mise en route : voir [Maintenance.echeance].
 */
@Entity(
    tableName = "affectations_gamme",
    indices = [
        Index(value = ["equipementId", "gammeId"], unique = true),
        Index("gammeId"),
    ],
)
data class AffectationGamme(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val equipementId: String,
    val gammeId: String,
    val depuisLe: LocalDate,
    val modifieLe: Instant = Instant.EPOCH,
)

/**
 * Une visite faite : la ligne du **journal** d'où tout le reste se déduit.
 *
 * C'est un journal et non un « dernier contrôle » sur l'équipement, et ce choix
 * porte les trois sorties demandées d'un coup : l'échéance se déduit de la visite
 * la plus récente, l'historique d'une machine est la liste de ses lignes, et le
 * taux de réalisation se compte dessus. Un seul champ `derniereVisiteLe` aurait
 * donné l'échéance et perdu les deux autres — c'est l'écart entre le registre des
 * fluides, qui est un journal, et `Equipement.dernierControleLe`, qui ne sert qu'à
 * l'échéance F-Gas.
 *
 * **Les deux liens sont nullables et les deux intitulés recopiés** : c'est le
 * couple lien / copie que le projet applique au type d'intervention, à la machine,
 * au technicien et au client d'une facture, et il compte double ici. Une ligne de
 * ce journal est la **preuve** qu'une maintenance contractuelle a été faite, et
 * elle doit survivre à tout ce qui se range autour : supprimer une gamme, ou
 * ranger l'inventaire des machines, ne doit pas effacer ce qui s'est passé — c'est
 * exactement ce qu'un contrôle vient rapprocher du contrat. Les deux suppressions
 * coupent donc le lien et laissent l'histoire lisible.
 *
 * La **périodicité** est recopiée pour la même raison : une gamme passée de
 * mensuelle à trimestrielle ne doit pas réécrire ce qui a été fait sous l'ancienne
 * cadence, pas plus qu'un changement de taux de TVA ne réécrit une facture
 * partie.
 */
@Entity(
    tableName = "releves_gamme",
    indices = [Index("equipementId"), Index("gammeId"), Index("faitLe")],
)
data class ReleveGamme(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val equipementId: String? = null,
    val equipementNom: String = "",
    val gammeId: String? = null,
    val gammeLibelle: String = "",
    val periodicite: Periodicite = Periodicite.MENSUEL,
    val faitLe: LocalDate,
    val technicienId: String? = null,
    val technicienNom: String = "",
    val notes: String = "",
    /** L'intervention d'où la visite a été close, quand il y en a une. */
    val interventionId: String? = null,
    val modifieLe: Instant = Instant.EPOCH,
)

/**
 * Une échéance telle que l'écran la montre : la machine, sa gamme, et quand.
 *
 * **Rien n'en est stocké** — elle se recompose à chaque affichage depuis
 * l'affectation et le journal. Voir [Maintenance] pour la raison.
 */
data class EcheanceMaintenance(
    val equipement: Equipement,
    val gamme: GammeMaintenance,
    val depuisLe: LocalDate,
    val derniereVisite: LocalDate?,
    /** Le nom du client ou du site, recopié pour l'affichage. */
    val clientNom: String = "",
) {

    val echeance: LocalDate
        get() = Maintenance.echeance(derniereVisite, depuisLe, gamme.periodicite)

    fun statut(aujourdhui: LocalDate): StatutEcheance =
        Maintenance.statut(echeance, gamme.periodicite, aujourdhui)

    fun joursDeRetard(aujourdhui: LocalDate): Long =
        Maintenance.joursDeRetard(echeance, aujourdhui)

    /** Jamais visitée : l'écran le dit, parce que c'est une information. */
    val jamaisVisitee: Boolean get() = derniereVisite == null
}

/**
 * L'assemblage du plan et le compte des réalisations, en fonctions pures.
 *
 * Elles sont ici et non dans un ViewModel pour la raison qui vaut partout dans ce
 * projet : ce qui décide de quelque chose doit s'éprouver sans Android. Un écran
 * qui annonce « quatre visites en retard » le fait sur ce calcul, et un contrat
 * se discute dessus.
 */
object PlanMaintenance {

    /**
     * Toutes les échéances du parc, **la plus urgente d'abord**.
     *
     * Elle croise quatre listes plutôt que de faire une jointure SQL, et c'est le
     * même choix que pour `LigneTournee` : les quatre flux sont déjà observés par
     * l'écran, et le croisement en mémoire lui donne de quoi afficher comme de
     * quoi agir. Sur un site de plusieurs centaines d'équipements cela fait
     * quelques milliers de comparaisons, ce qui est sans conséquence — là où une
     * jointure aurait demandé une vue SQL à maintenir en parallèle du calcul
     * d'échéance, qui est en Kotlin.
     *
     * Une affectation qui ne retrouve ni sa machine ni sa gamme est **écartée en
     * silence** : c'est un reste de suppression, et faire paraître une échéance
     * sur une machine qui n'existe plus serait pire que de l'ignorer.
     */
    fun echeances(
        equipements: List<Equipement>,
        gammes: List<GammeMaintenance>,
        affectations: List<AffectationGamme>,
        releves: List<ReleveGamme>,
        nomDuClient: (String) -> String = { "" },
    ): List<EcheanceMaintenance> {
        val parEquipement = equipements.associateBy { it.id }
        val parGamme = gammes.associateBy { it.id }
        // La dernière visite de chaque couple (machine, gamme), en une passe.
        val dernieres = HashMap<Pair<String, String>, LocalDate>()
        releves.forEach { releve ->
            val equipementId = releve.equipementId ?: return@forEach
            val gammeId = releve.gammeId ?: return@forEach
            val cle = equipementId to gammeId
            val connue = dernieres[cle]
            if (connue == null || releve.faitLe.isAfter(connue)) dernieres[cle] = releve.faitLe
        }

        return affectations
            .mapNotNull { affectation ->
                val equipement = parEquipement[affectation.equipementId] ?: return@mapNotNull null
                val gamme = parGamme[affectation.gammeId] ?: return@mapNotNull null
                EcheanceMaintenance(
                    equipement = equipement,
                    gamme = gamme,
                    depuisLe = affectation.depuisLe,
                    derniereVisite = dernieres[equipement.id to gamme.id],
                    clientNom = nomDuClient(equipement.clientId),
                )
            }
            // L'échéance, puis le nom : deux machines dues le même jour se suivent
            // dans un ordre stable, sans quoi la liste sauterait d'un affichage à
            // l'autre sur un parc de plusieurs centaines.
            .sortedWith(
                compareBy({ it.echeance }, { it.equipement.nom.lowercase() }, { it.gamme.libelle }),
            )
    }

    /**
     * Ce qu'une gamme a produit sur une fenêtre, tout le parc confondu.
     *
     * Les attendues se comptent **affectation par affectation** et non une fois
     * pour la gamme : deux machines rattachées à six mois d'intervalle n'ont pas
     * attendu le même nombre de visites, et multiplier une cadence par un nombre
     * de machines aurait réclamé des visites avant que le plan ne commence.
     */
    fun realisation(
        gamme: GammeMaintenance,
        affectations: List<AffectationGamme>,
        releves: List<ReleveGamme>,
        debut: LocalDate,
        fin: LocalDate,
    ): RealisationGamme {
        val siennes = affectations.filter { it.gammeId == gamme.id }
        val attendues = siennes.sumOf { affectation ->
            Maintenance.occurrencesAttendues(
                depuisLe = affectation.depuisLe,
                debut = debut,
                fin = fin,
                periodicite = gamme.periodicite,
            )
        }
        // Les visites comptées sont celles du journal, qui portent la gamme par son
        // identifiant : une visite dont la gamme a été supprimée garde son intitulé
        // pour l'historique mais ne compte plus pour aucun taux, puisque plus aucun
        // plan ne l'attend.
        val faites = releves.count { releve ->
            releve.gammeId == gamme.id &&
                !releve.faitLe.isBefore(debut) &&
                !releve.faitLe.isAfter(fin)
        }
        return RealisationGamme(attendues = attendues, faites = faites)
    }
}
