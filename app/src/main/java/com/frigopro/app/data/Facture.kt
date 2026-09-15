package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Où en est une facture.
 *
 * Quatre états, et la frontière qui compte passe après [BROUILLON] : c'est là
 * que le numéro est attribué et que le document cesse d'être modifiable. Une
 * facture émise fait foi — pour le client, pour l'administration, et pour la
 * comptabilité — et la retoucher après coup n'est pas une correction, c'est une
 * falsification.
 *
 * [ANNULEE] existe pour cette raison précise. Une facture qu'on regrette ne
 * s'efface pas : elle s'annule, **en gardant son numéro**, sinon la séquence
 * aurait un trou. C'est aussi pourquoi il n'y a pas de « supprimer » au-delà du
 * brouillon.
 */
enum class StatutFacture(val libelle: String) {
    BROUILLON("Brouillon"),
    EMISE("Émise"),
    PAYEE("Payée"),
    ANNULEE("Annulée"),
    ;

    /** Le document fait foi : ses lignes et ses montants ne bougent plus. */
    val figee: Boolean get() = this != BROUILLON

    /** Elle attend un règlement : c'est ce que comptent l'accueil et les relances. */
    val attendPaiement: Boolean get() = this == EMISE
}

/**
 * Une facture : ce que le client doit, et depuis quand.
 *
 * Elle vient de deux endroits, et les deux comptent. D'une **intervention
 * terminée**, dont les lignes se construisent toutes seules — le temps passé,
 * les pièces posées, le fluide ajouté : c'est le dépannage imprévu, celui qui
 * n'a pas été devisé. Et d'un **devis accepté**, dont elle reprend les lignes à
 * l'identique : c'est le chantier annoncé, et ressaisir ce qui a déjà été
 * chiffré serait à la fois du travail et une occasion de se tromper.
 *
 * Tout ce qui vient d'ailleurs est **recopié** plutôt que lu au moment de
 * l'affichage — le nom et l'adresse du client, le taux de TVA, le régime
 * d'assujettissement, le taux de pénalités. Même raison que pour le devis, en
 * plus forte : une facture est un document daté qui a quitté l'entreprise. Que
 * le client déménage, que l'entreprise franchisse le seuil de la franchise en
 * base ou change son taux de pénalités ne doit rien changer à ce qui a été
 * envoyé en mars.
 *
 * @param numero vide tant que la facture est un brouillon. Il n'est attribué
 *   qu'à l'émission, et c'est **ce qui rend la séquence continue** : un numéro
 *   posé dès la création serait abandonné à chaque brouillon supprimé, et une
 *   numérotation de facture ne tolère pas de trou (voir [Numerotation]).
 * @param echeanceLe la date à laquelle le règlement est exigible, calculée à
 *   l'émission depuis le délai des réglages. Stockée et non recalculée : elle
 *   est imprimée sur le document, et changer le délai six mois plus tard ne doit
 *   pas déplacer l'échéance d'une facture déjà partie.
 * @param tauxPenalites taux annuel des pénalités de retard, tel qu'il figure sur
 *   le document. À zéro, aucun taux n'a été fixé et c'est le taux légal qui
 *   s'applique — le document le dit alors ainsi, plutôt que d'annoncer « 0 % »,
 *   qui serait faux et priverait l'entreprise de son recours.
 * @param relanceeLe la dernière relance envoyée, pour ne pas relancer deux fois
 *   le même jour ni oublier qu'on l'a déjà fait.
 */
@Entity(
    tableName = "factures",
    indices = [Index("clientId"), Index("interventionId"), Index("devisId")],
)
data class Facture(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val numero: String = "",
    val clientId: String? = null,
    val clientNom: String = "",
    val clientAdresse: String = "",
    val interventionId: String? = null,
    val devisId: String? = null,
    val equipementNom: String = "",
    val objet: String = "",
    val statut: StatutFacture = StatutFacture.BROUILLON,
    val tauxTva: Double = 20.0,
    val tvaOfferte: Boolean = false,
    val assujettiTva: Boolean = true,
    val emiseLe: LocalDate? = null,
    val echeanceLe: LocalDate? = null,
    val tauxPenalites: Double = 0.0,
    val payeeLe: LocalDate? = null,
    val relanceeLe: LocalDate? = null,
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Elle porte un numéro : elle est sortie de l'entreprise. */
    val numerotee: Boolean get() = numero.isNotBlank()

    /**
     * Le règlement est en retard.
     *
     * Dérivé et jamais stocké, exactement comme les échéances F-Gas et pour la
     * même raison : un booléen « en retard » en base serait faux le lendemain.
     */
    fun enRetard(aujourdhui: LocalDate): Boolean =
        statut.attendPaiement && echeanceLe != null && aujourdhui.isAfter(echeanceLe)

    /** Depuis combien de jours, ou `null` si elle n'est pas en retard. */
    fun joursDeRetard(aujourdhui: LocalDate): Long? {
        if (!enRetard(aujourdhui)) return null
        return ChronoUnit.DAYS.between(echeanceLe, aujourdhui)
    }

    /**
     * Il est temps de relancer.
     *
     * En retard, et pas déjà relancé cette semaine. Le délai entre deux relances
     * n'est pas un réglage : relancer tous les jours fâche un client qui a un
     * virement en route, et ne jamais relancer revient à offrir la facture.
     */
    fun aRelancer(aujourdhui: LocalDate): Boolean {
        if (!enRetard(aujourdhui)) return false
        val derniere = relanceeLe ?: return true
        return ChronoUnit.DAYS.between(derniere, aujourdhui) >= JOURS_ENTRE_RELANCES
    }

    companion object {

        /** Une semaine : voir [aRelancer]. */
        const val JOURS_ENTRE_RELANCES = 7L

        /**
         * L'indemnité forfaitaire pour frais de recouvrement, en euros.
         *
         * Quarante euros, fixés par l'article D. 441-5 du code de commerce. Ce
         * n'est pas un réglage : c'est un montant réglementaire, et sa mention
         * est obligatoire sur toute facture entre professionnels.
         */
        const val INDEMNITE_RECOUVREMENT = 40.0

        /** Les pénalités courent sur une année de 365 jours. */
        const val JOURS_PAR_AN = 365.0
    }
}

/**
 * Ce qu'un retard de paiement a fait courir, à une date donnée.
 *
 * **Rien de tout cela n'est stocké**, et c'est la seule façon que ce soit juste :
 * le montant grandit d'un jour à l'autre, si bien qu'un chiffre écrit en base
 * serait faux le lendemain matin. Même règle que « en retard », que les échéances
 * F-Gas, et pour la même raison.
 *
 * Ce n'est pas non plus une modification de la facture. Une facture émise ne
 * bouge plus — c'est la règle qui tient toute la numérotation —, et les pénalités
 * ne s'y ajoutent pas : elles sont **dues en plus**, de plein droit, sans avoir à
 * être rappelées. Ce type dit ce qu'il y a à réclamer ; il ne retouche rien.
 *
 * @param interets `null` quand aucun taux n'a été convenu. Le taux légal
 *   s'applique alors — taux de refinancement de la BCE majoré de dix points —,
 *   mais ce taux-là n'est pas dans le téléphone et varie dans le temps. Le dire
 *   inconnu vaut mieux que d'inventer le chiffre qu'on réclamerait à un client.
 */
data class PenalitesDues(
    val joursDeRetard: Long,
    val tauxAnnuel: Double?,
    val interets: Double?,
    val indemnite: Double = Facture.INDEMNITE_RECOUVREMENT,
) {

    /** Le total réclamable, `null` tant que les intérêts ne sont pas chiffrables. */
    val total: Double? get() = interets?.let { (it + indemnite).auCentime() }

    companion object {

        /**
         * Ce qui est dû sur cette facture, ou `null` si elle n'est pas en retard.
         *
         * Les intérêts se calculent sur le montant **TTC**, et non sur le hors
         * taxes : c'est la somme que le client devait verser et n'a pas versée.
         */
        fun de(facture: Facture, totalTtc: Double, aujourdhui: LocalDate): PenalitesDues? {
            val jours = facture.joursDeRetard(aujourdhui) ?: return null
            val taux = facture.tauxPenalites.takeIf { it > 0.0 }
            return PenalitesDues(
                joursDeRetard = jours,
                tauxAnnuel = taux,
                interets = taux?.let {
                    (totalTtc * it / 100.0 * jours / Facture.JOURS_PAR_AN).auCentime()
                },
            )
        }
    }
}

/**
 * Une ligne de facture.
 *
 * Même forme qu'une [LigneDevis], et pourtant une table à part : les deux
 * documents ne vivent pas la même vie. Une ligne de devis se retouche tant que
 * le devis n'est pas accepté ; une ligne de facture est figée dès l'émission.
 * Les faire cohabiter dans une table aurait demandé un `factureId` **et** un
 * `devisId` nullables sur chaque ligne, et une règle de plus pour dire laquelle
 * des deux est vraie.
 *
 * Le marqueur `deplacement` de [LigneDevis] n'a pas d'équivalent ici, et c'est
 * volontaire : il sert à refaire les lignes d'un trajet recalculé, et une
 * facture ne recalcule rien.
 */
@Entity(
    tableName = "lignes_facture",
    indices = [Index("factureId")],
)
data class LigneFacture(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val factureId: String,
    override val designation: String,
    override val quantite: Double = 1.0,
    override val unite: String = "",
    override val prixUnitaire: Double = 0.0,
    override val offerte: Boolean = false,
    /**
     * Ce que la ligne a **coûté**, à l'unité, au jour de la facturation.
     *
     * Zéro veut dire « pas de matériel » ou « coût inconnu », et non « gratuit » :
     * la marge se déclare alors non chiffrable plutôt que d'annoncer un bénéfice
     * égal à la recette — même règle que le coût horaire interne et que le GWP
     * d'un fluide hors catalogue.
     *
     * Il est **recopié** depuis la fiche article et jamais relu dans le magasin
     * au moment d'afficher : un devis de mars a été chiffré sur les prix de mars,
     * et sur un split dont le tarif fournisseur a bougé de 15 % en un an, relire
     * le prix du jour donnerait une marge que personne n'a jamais faite. Même
     * raison que `PiecePosee.prixAchat` et que le taux de TVA d'une facture.
     *
     * Il ne sort **jamais** sur le document du client, et un test le vérifie.
     */
    val prixAchat: Double = 0.0,
    val rang: Int = 0,
) : LigneChiffree

/**
 * Une facture et ses lignes, tels que l'écran les manipule.
 *
 * Les totaux passent par [TotauxDocument], partagé avec le devis : la règle
 * d'arrondi — **au centime ligne à ligne, puis sommé** — est le point le plus
 * subtil du calcul, et deux copies auraient fini par diverger d'un centime sur
 * l'un des deux documents sans que personne ne sache lequel a raison.
 */
data class FactureComplete(
    val facture: Facture,
    val lignes: List<LigneFacture> = emptyList(),
) {

    private val totaux = TotauxDocument(
        lignes = lignes,
        tauxTva = facture.tauxTva,
        assujettiTva = facture.assujettiTva,
        tvaOfferte = facture.tvaOfferte,
    )

    val totalHt: Double get() = totaux.totalHt

    val tvaDue: Double get() = totaux.tvaDue

    val remiseTva: Double get() = totaux.remiseTva

    val gestesCommerciaux: Double get() = totaux.gestesCommerciaux

    val totalTtc: Double get() = totaux.totalTtc
}

/**
 * Le total hors taxes d'une facture, tel que SQL le renvoie.
 *
 * Même motif que [TotalDevis] : le résultat d'un `GROUP BY`, pas une entité.
 */
data class TotalFacture(val factureId: String, val montant: Double)

/**
 * Ce qu'un devis est devenu : la facture qui en est sortie, en trois colonnes.
 *
 * C'est une **projection**, pas une entité. Elle sert à l'onglet des devis, qui
 * a besoin de savoir lequel est déjà facturé — pour le ranger plus bas — et sous
 * quel numéro — pour pouvoir le dire. Le reste de la facture ne le regarde pas.
 *
 * Le statut est là pour une raison précise : une facture **annulée** garde son
 * numéro et son lien, et un devis dont la facture a été annulée n'est pas dans
 * la même situation qu'un devis facturé pour de bon. L'écran doit pouvoir faire
 * la différence.
 */
data class DevisFacture(
    val devisId: String,
    val numero: String,
    val statut: StatutFacture,
)

/**
 * Une facture et son montant, tels que les listes et les compteurs les
 * affichent. C'est [FactureComplete] sans le détail des lignes.
 */
data class FactureChiffree(val facture: Facture, val totalHt: Double) {

    val totalTtc: Double
        get() = when {
            !facture.assujettiTva || facture.tvaOfferte -> totalHt
            else -> (totalHt * (1 + facture.tauxTva / 100)).auCentime()
        }
}

/**
 * Ce qu'une intervention devient sur une facture.
 *
 * Une fonction pure, comme [LignesDeplacement] et pour les mêmes raisons : elle
 * décide de ce qui sera facturé à un vrai client, et cela doit pouvoir
 * s'éprouver sans base ni téléphone.
 *
 * Elle construit ce que l'intervention a **produit**, dans l'ordre où on le
 * raconte : le temps passé, les pièces posées, le fluide ajouté. Les lignes
 * restent modifiables tant que la facture est un brouillon — c'est une
 * proposition, pas un verdict.
 */
object LignesFacture {

    /** L'intitulé de la main-d'œuvre. */
    const val MAIN_D_OEUVRE = "Main-d'œuvre"

    fun depuisIntervention(
        factureId: String,
        intervention: Intervention,
        pieces: List<PiecePosee>,
        mouvements: List<MouvementFluide>,
        tauxHoraire: Double,
    ): List<LigneFacture> {
        val lignes = mutableListOf<LigneFacture>()

        fun ajouter(
            designation: String,
            quantite: Double,
            unite: String,
            prix: Double,
            prixAchat: Double = 0.0,
        ) {
            lignes += LigneFacture(
                factureId = factureId,
                designation = designation,
                quantite = quantite,
                unite = unite,
                prixUnitaire = prix,
                // Zéro partout sauf sur les pièces : le temps n'a pas de prix
                // d'achat — il a un coût horaire interne, qui est une autre
                // grandeur et que `RentabiliteIntervention` sait manier.
                prixAchat = prixAchat,
                rang = lignes.size,
            )
        }

        // Le temps. **La quantité est arrondie avant de servir**, comme pour le
        // déplacement et pour la même raison : elle s'imprime à deux décimales,
        // si bien qu'un montant calculé sur la valeur exacte ne retomberait pas
        // sur la ligne que le client a sous les yeux. 1 h 47 s'écrit « 1,78 h »
        // et doit valoir 1,78 × le taux, sinon la ligne se contredit toute seule.
        val heures = (intervention.chrono.cumuleS / 3600.0).auCentime()
        if (heures > 0.0) {
            ajouter(MAIN_D_OEUVRE, heures, "h", tauxHoraire)
        }

        // Les pièces. Une pièce dont le prix n'a pas été relevé sur place sort à
        // zéro euro plutôt que d'être omise : une ligne à zéro se voit et appelle
        // une correction, une ligne absente ne se voit pas et part telle quelle.
        pieces.forEach { piece ->
            val intitule = if (piece.reference.isBlank()) {
                piece.designation
            } else {
                "${piece.designation} (${piece.reference})"
            }
            ajouter(intitule, piece.quantite, "", piece.prixUnitaire ?: 0.0, piece.prixAchat)
        }

        // Le fluide ajouté, regroupé par fluide : deux compléments de charge le
        // même jour font une ligne, pas deux. Le fluide **récupéré** n'en fait
        // aucune — c'est une reprise, et la facturer serait facturer au client le
        // fluide qu'on lui a retiré.
        mouvements
            .filter { it.sens == SensFluide.AJOUT && it.masseKg > 0.0 }
            .groupBy { Fluides.normaliser(it.fluide) }
            .forEach { (fluide, lot) ->
                val masse = lot.sumOf { it.masseKg }.auCentime()
                ajouter("Fluide ${Fluides.afficher(fluide)}", masse, "kg", 0.0)
            }

        return lignes
    }

    /**
     * Ce qu'un devis accepté devient : ses lignes, à l'identique.
     *
     * À l'identique, et c'est le point : le client a accepté des montants, et une
     * facture qui ne les reprend pas exactement est une facture qu'on discute.
     * Le marqueur `deplacement` ne traverse pas — il sert à refaire les lignes
     * d'un trajet recalculé, et une facture ne recalcule rien.
     */
    fun depuisDevis(factureId: String, lignes: List<LigneDevis>): List<LigneFacture> =
        lignes.sortedBy { it.rang }.mapIndexed { rang, ligne ->
            LigneFacture(
                factureId = factureId,
                designation = ligne.designation,
                quantite = ligne.quantite,
                unite = ligne.unite,
                prixUnitaire = ligne.prixUnitaire,
                offerte = ligne.offerte,
                // Le coût suit la ligne : sans lui il se perdrait exactement au
                // moment où le document devient celui qui compte.
                prixAchat = ligne.prixAchat,
                rang = rang,
            )
        }
}
