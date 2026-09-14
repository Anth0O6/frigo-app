package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Ce qu'un fournisseur vend, et c'est ce qui décide qui l'on appelle.
 *
 * Le classement est **par ce qu'on va chercher** et non par la forme juridique
 * de l'entreprise : à 7 h du matin devant une chambre froide en panne, la
 * question est « qui a des détendeurs ? », pas « qui est grossiste ? ».
 */
enum class CategorieFournisseur(val libelle: String) {
    FLUIDES("Fluides"),
    PIECES("Pièces détachées"),
    MATERIEL("Matériel"),
    CONSOMMABLES("Consommables"),
    OUTILLAGE("Outillage"),
    AUTRE("Autre"),
}

/**
 * Un fournisseur du carnet.
 *
 * ## Le catalogue est un lien, pas un import
 *
 * [siteCatalogue] porte l'adresse du catalogue en ligne, et l'application ne
 * fait que l'ouvrir. C'est une décision, pas une facilité : un catalogue
 * fournisseur n'a pas de format d'échange, il change sans prévenir, et
 * l'aspirer reviendrait à en tenir une copie qui serait fausse au premier
 * changement de tarif — le genre de faux qui part ensuite sur un devis. Le
 * navigateur montre le prix du jour, l'application ne prétend pas le connaître.
 *
 * ## La préférence
 *
 * [prefere] n'est pas une note sur cinq mais un booléen, et c'est voulu : on
 * n'a pas d'avis nuancé sur un fournisseur, on a celui qu'on appelle en premier
 * et les autres. Une échelle aurait demandé de la tenir à jour pour rien.
 *
 * @param modifieLe voir [Intervention.modifieLe] : même rôle, même usage futur.
 */
@Entity(
    tableName = "fournisseurs",
    indices = [Index("categorie")],
)
data class Fournisseur(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nom: String,
    val categorie: CategorieFournisseur = CategorieFournisseur.AUTRE,
    val telephone: String = "",
    val email: String = "",
    val adresse: String = "",
    val ville: String = "",
    /** L'adresse du catalogue en ligne, qu'on ouvre au navigateur. */
    val siteCatalogue: String = "",
    /** Celui qu'on appelle en premier. */
    val prefere: Boolean = false,
    val notes: String = "",
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Un numéro à appeler. Même règle que pour un client. */
    val appelable: Boolean get() = telephone.isNotBlank()

    /** Une destination à ouvrir : la ville seule mènerait au centre-ville. */
    val localisable: Boolean get() = adresse.isNotBlank()

    /** Un catalogue à ouvrir au navigateur. */
    val consultable: Boolean get() = siteCatalogue.isNotBlank()

    /** Adresse complète, telle qu'on la dicterait à quelqu'un. */
    val adresseComplete: String
        get() = listOf(adresse, ville).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Un article du magasin : ce qu'on pose chez un client et qu'il faut racheter.
 *
 * ## Deux prix, et la marge qui s'en déduit
 *
 * [prixAchat] est ce que le fournisseur facture, [prixVente] ce qu'on refacture.
 * **La marge n'est pas stockée** : elle se déduit des deux, et un troisième
 * champ aurait cessé d'être juste au premier prix d'achat qui monte — c'est-à-
 * dire au premier mois. Voir [Marge], qui porte les trois façons de la dire et
 * la raison de ne pas les confondre.
 *
 * Les deux prix sont **hors taxes**, comme tout le reste du chiffrage : mélanger
 * un prix d'achat TTC et un prix de vente HT donne une marge fausse de vingt
 * pour cent, dans le sens qui rassure.
 *
 * ## Le lien vers le fournisseur, et sa copie
 *
 * Même couple que partout ailleurs, et pour les mêmes raisons : le lien
 * répercute un renommage, la copie garantit qu'un article dont le fournisseur a
 * été supprimé dise encore d'où il venait.
 */
@Entity(
    tableName = "articles",
    indices = [Index("fournisseurId"), Index("reference")],
)
data class Article(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** La référence du fournisseur, celle qu'on dicte au téléphone. */
    val reference: String = "",
    val designation: String,
    val fournisseurId: String? = null,
    val fournisseurNom: String = "",
    /** Prix d'achat hors taxes, tel que le fournisseur le facture. */
    val prixAchat: Double = 0.0,
    /** Prix de vente hors taxes, tel qu'il part sur un devis. */
    val prixVente: Double = 0.0,
    val unite: String = "u",
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Ce que cet article rapporte, dit de trois façons. Voir [Marge]. */
    val marge: Marge get() = Marge(prixAchat = prixAchat, prixVente = prixVente)
}

/**
 * Ce qu'un article rapporte — et les trois façons de le dire, qui ne sont pas
 * la même chose.
 *
 * C'est la distinction la plus facile à rater de tout le chiffrage, et elle se
 * paie en euros. Un article acheté 100 € et vendu 150 € a :
 *
 * - une **marge brute** de 50 € ;
 * - un **taux de marque** de 33 % (50 / 150), qui est ce qu'un comptable lit ;
 * - un **coefficient** de 1,5 (150 / 100), qui est ce qu'un frigoriste applique.
 *
 * Annoncer « 50 % de marge » sur cet article n'est ni faux ni juste : c'est
 * ambigu, et l'ambiguïté se règle en nommant les trois plutôt qu'en en choisissant
 * une. Même motif que la séparation entre une température et un écart de
 * température dans [Conversions] : deux grandeurs voisines, deux noms distincts,
 * aucune conversion silencieuse de l'une vers l'autre.
 *
 * Tout est `null` quand le calcul n'a pas de sens — un prix d'achat à zéro ne
 * donne pas un coefficient infini, il donne « je ne sais pas ». Même règle que
 * le GWP d'un fluide inconnu.
 */
data class Marge(val prixAchat: Double, val prixVente: Double) {

    /** Les deux prix sont renseignés : il y a quelque chose à calculer. */
    val chiffrable: Boolean get() = prixAchat > 0.0 && prixVente > 0.0

    /** Ce qui reste, en euros. */
    val brute: Double? get() = if (chiffrable) (prixVente - prixAchat).auCentime() else null

    /**
     * La part du prix de vente qui n'est pas l'achat — le « taux de marque ».
     *
     * En pourcentage, de 0 à 100. C'est le chiffre d'un compte de résultat.
     */
    val tauxDeMarque: Double?
        get() = if (chiffrable) ((prixVente - prixAchat) / prixVente * 100.0).arrondiDixieme() else null

    /**
     * Le multiplicateur qu'on applique au prix d'achat pour tarifer.
     *
     * C'est celui dont on se sert sur le terrain — « je suis au coefficient
     * 1,8 » —, et il n'a pas de plafond, là où le taux de marque plafonne à 100.
     */
    val coefficient: Double?
        get() = if (chiffrable) (prixVente / prixAchat).arrondiCentieme() else null

    /** Vendu à perte : ce qu'un écran doit signaler, pas corriger en silence. */
    val aPerte: Boolean get() = chiffrable && prixVente < prixAchat
}

/**
 * Où se trouve un stock.
 *
 * Deux lieux, et la distinction est tout l'intérêt : ce qui est à l'atelier ne
 * dépanne personne à 40 km de là, et ce qui est dans le camion n'est pas
 * disponible pour le chantier de demain. Un stock unique aurait répondu « oui,
 * j'en ai » à une question qui est en fait « est-ce que j'en ai **ici** ? ».
 */
enum class LieuStock(val libelle: String) {
    ATELIER("Atelier"),
    CAMION("Camion"),
}

/**
 * Ce qu'on a d'un article, à un endroit.
 *
 * Une ligne par article **et par lieu** : l'unicité est portée par un index et
 * non par une clé composite, pour garder l'identifiant UUID que tout le projet
 * se donne en vue d'une synchronisation (voir « Deux choix faits pour une
 * synchronisation future »).
 *
 * **Le manque est dérivé, jamais stocké** : `quantite < minimum` se recalcule à
 * chaque lecture, et un booléen « à réapprovisionner » en base serait faux dès
 * la première sortie de stock. Même règle que « en retard » pour une facture, et
 * que les échéances F-Gas.
 *
 * [minimum] à zéro veut dire « pas de seuil » et non « seuil à zéro » : sans
 * cela, tout article épuisé alerterait, y compris celui qu'on ne tient
 * délibérément pas en stock et qu'on commande à la demande.
 */
@Entity(
    tableName = "stocks",
    indices = [Index(value = ["articleId", "lieu"], unique = true), Index("articleId")],
)
data class Stock(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val articleId: String,
    val lieu: LieuStock,
    val quantite: Double = 0.0,
    /** Le seuil sous lequel il faut racheter ; zéro veut dire « pas de seuil ». */
    val minimum: Double = 0.0,
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Un seuil a été posé sur cette ligne. */
    val surveille: Boolean get() = minimum > 0.0

    /** Il faut racheter. Dérivé, et c'est la seule façon que ce soit juste. */
    val sousSeuil: Boolean get() = surveille && quantite < minimum

    /** Combien il en manque pour revenir au seuil. */
    val manquant: Double? get() = if (sousSeuil) (minimum - quantite).arrondiCentieme() else null
}

/**
 * Un article, ce qu'on en a aux deux endroits, et ce qu'il vaut.
 *
 * Réuni par le dépôt plutôt que stocké, comme [GroupeMachines] et
 * [GroupeClients]. Les deux lieux sont toujours présents — à zéro si aucune
 * ligne n'existe — pour que l'écran n'ait pas à distinguer « pas de stock » de
 * « pas encore de ligne de stock », qui n'ont aucune différence pour le
 * technicien.
 */
data class ArticleEnStock(
    val article: Article,
    val stocks: Map<LieuStock, Stock>,
) {

    fun quantite(lieu: LieuStock): Double = stocks[lieu]?.quantite ?: 0.0

    /** Tout ce qu'on a, les deux lieux confondus. */
    val total: Double get() = LieuStock.entries.sumOf { quantite(it) }

    /** Les lieux où il faut racheter. */
    val aReapprovisionner: List<LieuStock>
        get() = LieuStock.entries.filter { stocks[it]?.sousSeuil == true }

    val enAlerte: Boolean get() = aReapprovisionner.isNotEmpty()

    /**
     * Ce que ce stock a coûté, prix d'achat à l'appui.
     *
     * C'est la valeur du magasin, celle qu'un assureur ou un bilan demande. Elle
     * se calcule sur le **prix d'achat** et jamais sur le prix de vente : du
     * stock n'est pas du chiffre d'affaires, c'est de l'argent immobilisé.
     */
    val valeurAchat: Double get() = (total * article.prixAchat).auCentime()
}
