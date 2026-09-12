package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToLong

/** Où en est un devis. */
enum class StatutDevis(val libelle: String) {
    BROUILLON("Brouillon"),
    ENVOYE("Envoyé"),
    ACCEPTE("Accepté"),
    REFUSE("Refusé"),
    ;

    /** Un devis accepté ou refusé ne se modifie plus : il fait foi. */
    val figé: Boolean get() = this == ACCEPTE || this == REFUSE
}

/**
 * Un devis : ce qu'on propose au client, et combien.
 *
 * Il naît souvent sur place, au moment où l'on constate qu'une réparation
 * dépasse le dépannage — le compresseur est mort, il faut le remplacer. C'est
 * pour cela qu'il vit dans l'application de terrain et non au bureau :
 * chiffré et envoyé avant d'être redescendu du toit, il est accepté le jour
 * même plutôt que la semaine suivante.
 *
 * Le nom du client est recopié, comme sur l'intervention et pour la même
 * raison : un devis de mars doit continuer d'afficher le client tel qu'il
 * s'appelait en mars.
 *
 * @param tauxTva taux de TVA en pourcentage, porté par le devis et non par un
 *   réglage global : il change avec la nature des travaux, et un devis déjà
 *   envoyé ne doit pas se recalculer parce qu'un réglage a bougé depuis.
 * @param valableJusquau date de fin de validité, mentionnée sur le document.
 */
@Entity(
    tableName = "devis",
    indices = [Index("clientId"), Index("equipementId")],
)
data class Devis(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val numero: String = "",
    val clientId: String? = null,
    val clientNom: String = "",
    val equipementId: String? = null,
    val equipementNom: String = "",
    val objet: String = "",
    val statut: StatutDevis = StatutDevis.BROUILLON,
    val tauxTva: Double = 20.0,
    /**
     * La TVA est **offerte** : une remise égale à son montant, et le client paie
     * le hors taxes.
     *
     * C'est une remise et non un taux ramené à zéro, et la distinction n'est pas
     * cosmétique : la TVA reste due à l'État, et un document qui annoncerait
     * « TVA 0 % » alors qu'on y est assujetti serait faux. Le devis porte donc la
     * TVA à son taux, puis la remise en dessous.
     */
    val tvaOfferte: Boolean = false,
    /**
     * L'entreprise est assujettie à la TVA, recopié des réglages à la création.
     *
     * Recopié et non lu : même raison que [tauxTva]. Un devis envoyé en franchise
     * en base ne doit pas se mettre à afficher de la TVA le jour où l'entreprise
     * franchit le seuil — c'est le document d'alors qui fait foi.
     */
    val assujettiTva: Boolean = true,
    val creeLe: LocalDate? = null,
    val valableJusquau: LocalDate? = null,
    val modifieLe: Instant = Instant.EPOCH,
)

/**
 * Une ligne de devis.
 *
 * @param quantite en nombre d'unités : des heures, des kilogrammes, des
 *   pièces. Un décimal, parce que « 6,2 kg » et « 1,5 h » sont des quantités
 *   ordinaires dans ce métier.
 * @param unite ce que compte [quantite] : `h`, `kg`, ou vide pour du dénombré.
 * @param rang position dans le devis. Stocké plutôt que déduit : l'ordre des
 *   lignes est un choix de rédaction, pas une conséquence de la saisie.
 */
@Entity(
    tableName = "lignes_devis",
    indices = [Index("devisId")],
)
data class LigneDevis(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val devisId: String,
    val designation: String,
    val quantite: Double = 1.0,
    val unite: String = "",
    val prixUnitaire: Double = 0.0,
    /**
     * La ligne est **offerte** : elle ne compte pas dans le total.
     *
     * Le prix reste porté par la ligne, et c'est tout l'intérêt : un geste
     * commercial qu'on ne voit pas n'est pas un geste commercial. Le devis
     * affiche le prix barré et « offert », si bien que le client lit ce qu'on lui
     * a donné. Supprimer la ligne aurait été plus simple et aurait effacé
     * l'argument de vente.
     */
    val offerte: Boolean = false,
    val rang: Int = 0,
) {

    /** Montant hors taxes de la ligne — nul si elle est offerte. */
    val montant: Double get() = if (offerte) 0.0 else quantite * prixUnitaire

    /** Ce que la ligne aurait coûté : c'est ce qui se barre sur le document. */
    val montantAvantGeste: Double get() = quantite * prixUnitaire
}

/**
 * Un devis et ses lignes, tels que l'écran les manipule.
 *
 * Les totaux se calculent ici plutôt qu'en base : ils découlent des lignes, et
 * une valeur stockée finirait par les contredire après une modification. Ils
 * sont **arrondis au centime ligne à ligne puis sommés**, dans cet ordre :
 * sommer des montants non arrondis puis arrondir le total donne un résultat
 * qui ne retombe pas sur l'addition que le client refait à la main, et c'est
 * le genre d'écart d'un centime qui fait rappeler.
 */
data class DevisComplet(
    val devis: Devis,
    val lignes: List<LigneDevis> = emptyList(),
) {

    /** Total hors taxes. */
    val totalHt: Double get() = lignes.sumOf { it.montant.auCentime() }

    /**
     * La TVA due, avant tout geste commercial.
     *
     * Nulle en franchise en base : il n'y a alors pas de TVA à afficher, et le
     * document porte la mention de l'article 293 B du CGI à la place.
     */
    val tvaDue: Double
        get() = if (!devis.assujettiTva) 0.0 else (totalHt * devis.tauxTva / 100.0).auCentime()

    /** La remise quand la TVA est offerte : son montant exact, pas un autre. */
    val remiseTva: Double get() = if (devis.tvaOfferte) tvaDue else 0.0

    /**
     * Ce qui a été donné : les lignes offertes, et la TVA si elle l'est.
     *
     * Affiché au technicien et non au client : c'est le chiffre qui dit ce que le
     * geste commercial a coûté, et il se regarde avant d'envoyer, pas après.
     */
    val gestesCommerciaux: Double
        get() = (lignes.filter { it.offerte }.sumOf { it.montantAvantGeste.auCentime() } + remiseTva)
            .auCentime()

    /** Montant de la TVA. */
    @Deprecated("Remplacé par tvaDue, qui tient compte de la franchise en base.")
    val tva: Double get() = tvaDue

    /** Total toutes taxes comprises. */
    val totalTtc: Double get() = (totalHt + tvaDue - remiseTva).auCentime()
}

/**
 * Le total hors taxes d'un devis, tel que SQL le renvoie.
 *
 * Ce n'est pas une entité : aucune table ne lui correspond, c'est le résultat
 * d'un `GROUP BY` sur les lignes. Room sait remplir une classe de ce genre du
 * moment que ses noms de propriétés sont ceux des colonnes projetées.
 */
data class TotalDevis(val devisId: String, val montant: Double)

/**
 * Un devis et son montant, tels que les listes et les compteurs les affichent.
 *
 * C'est [DevisComplet] sans les lignes : une liste de devis a besoin du total
 * de chacun, pas du détail, et charger toutes les lignes de tous les devis pour
 * n'en afficher que la somme serait payer cher un chiffre.
 */
data class DevisChiffre(val devis: Devis, val totalHt: Double) {

    val totalTtc: Double
        get() = when {
            // Franchise en base, ou TVA offerte : le client paie le hors taxes.
            !devis.assujettiTva || devis.tvaOfferte -> totalHt
            else -> (totalHt * (1 + devis.tauxTva / 100)).auCentime()
        }

    /** Un devis qui attend une réponse : c'est ce que comptent les compteurs. */
    val enAttente: Boolean
        get() = devis.statut == StatutDevis.BROUILLON || devis.statut == StatutDevis.ENVOYE
}

/** Arrondit au centime, la seule précision qui ait un sens sur une facture. */
fun Double.auCentime(): Double = (this * 100).roundToLong() / 100.0
