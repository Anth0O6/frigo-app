package com.frigopro.app.ui

import com.frigopro.app.data.Article
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.data.LectureTarif
import com.frigopro.app.data.LigneDevis
import com.frigopro.app.data.LigneTarif
import com.frigopro.app.data.PrixArticle
import com.frigopro.app.data.auCentime

/**
 * Ce qu'on fait du prix d'achat d'un devis fournisseur repris sur un devis client.
 *
 * La question est **posée, jamais supposée** : l'import ne s'ajoute pas tant
 * qu'on n'y a pas répondu. Le coefficient des Réglages vaut pour le matériel du
 * magasin, mais un devis fournisseur repris tel quel est souvent une commande
 * spéciale — un groupe, une pièce rare — sur laquelle on ne marge pas comme sur
 * une goulotte, et la ligne partirait chez le client sans que personne ait choisi.
 */
enum class ChoixMarge(val libelle: String) {
    MARGER("Marger"),
    COUTANT("Au prix coûtant"),
}

/**
 * Une ligne du devis fournisseur, et ce qu'on en fera sur le devis client.
 *
 * @param article l'article du magasin qui porte la même référence, `null` sinon :
 *   c'est le seul rapprochement fait d'office, comme dans la saisie des prix.
 * @param quantite reprise du devis fournisseur, modifiable — `null` pour un
 *   champ vidé, qui empêche d'ajouter.
 * @param coefficient tapé sur cette ligne ; `null` la laisse suivre le choix
 *   général. C'est ce qui permet de marger une ligne sur un import au coûtant,
 *   ou l'inverse, sans changer les autres.
 */
data class LigneImport(
    val rang: Int,
    val lue: LigneTarif,
    val article: Article?,
    val quantite: Double?,
    val coefficient: Double? = null,
    val retenue: Boolean = true,
) {

    /**
     * L'intitulé posé sur le devis : celui de la fiche quand la référence est au
     * magasin — c'est le nom qu'on a choisi —, celui du fournisseur sinon, rendu
     * lisible. La référence suit entre parenthèses, comme pour une ligne prise au
     * magasin : c'est ce qu'un client compare à un devis concurrent.
     */
    val designation: String
        get() {
            val nom = article?.designation ?: designationLisible(lue.designation)
            val reference = lue.reference.ifBlank { article?.reference.orEmpty() }
            return listOfNotNull(nom, reference.takeIf { it.isNotBlank() }?.let { "($it)" })
                .joinToString(" ")
        }

    val unite: String get() = article?.unite ?: UNITE_PAR_DEFAUT
}

/**
 * Un devis fournisseur lu depuis un devis client, avant qu'il n'y soit ajouté.
 *
 * **Rien n'est écrit avant « Ajouter au devis »** : ni les lignes, ni les prix
 * d'achat du magasin. C'est la règle de la saisie des prix, et pour la même
 * raison — un chiffre mal lu partirait sinon sur un devis sans avoir été relu.
 */
data class ImportFournisseur(
    val enCours: Boolean = false,
    val echec: String? = null,
    val fournisseur: Fournisseur? = null,
    val date: java.time.LocalDate? = null,
    val lignes: List<LigneImport> = emptyList(),
    /** `null` tant qu'on n'a pas répondu : voir [ChoixMarge]. */
    val marge: ChoixMarge? = null,
    /** Le coefficient général ; proposé depuis les Réglages, `null` s'il n'y en a pas. */
    val coefficient: Double? = null,
    /** Reporter les prix lus sur les articles du magasin de même référence. */
    val mettreAJourPrix: Boolean = true,
    /** Créer une fiche au magasin pour chaque ligne cochée qu'il ne connaît pas. */
    val creerArticles: Boolean = true,
) {

    /**
     * Le coefficient appliqué à une ligne : le sien s'il a été tapé, sinon le
     * choix général — 1 au prix coûtant. `null` tant que rien ne permet de le dire.
     */
    fun coefficientDe(ligne: LigneImport): Double? =
        ligne.coefficient ?: when (marge) {
            ChoixMarge.MARGER -> coefficient
            ChoixMarge.COUTANT -> 1.0
            null -> null
        }

    /** Le prix de vente unitaire, arrondi au centime — le même calcul que la feuille du matériel. */
    fun prixVente(ligne: LigneImport): Double? =
        coefficientDe(ligne)?.takeIf { it > 0.0 }?.let { (ligne.lue.prixNet * it).auCentime() }

    val retenues: List<LigneImport> get() = lignes.filter { it.retenue }

    /** Les lignes dont la référence est au magasin : celles dont le prix peut s'y reporter. */
    val reconnues: List<LigneImport> get() = lignes.filter { it.article != null }

    /**
     * Les lignes **cochées** que le magasin ne connaît pas : celles dont une fiche
     * peut naître. Une ligne décochée n'en crée pas, à la différence du report de
     * prix — l'avoir écartée du devis est souvent l'avoir jugée mal lue, et une
     * fiche née d'un chiffre mal lu resterait au magasin.
     */
    val horsMagasin: List<LigneImport> get() = retenues.filter { it.article == null }

    /** Ce qui empêche encore d'ajouter, dit en clair ; `null` quand tout est prêt. */
    val manque: String?
        get() = when {
            marge == null -> "Choisissez de marger ou non."
            retenues.isEmpty() -> "Aucune ligne cochée."
            retenues.any { (it.quantite ?: 0.0) <= 0.0 } -> "Une quantité manque."
            retenues.any { prixVente(it) == null } -> "Un coefficient manque."
            else -> null
        }

    /**
     * Les lignes du devis client. Le **prix d'achat est celui lu**, et non celui
     * de la fiche : c'est le prix du jour du chiffrage, celui qui rend la marge
     * juste — même règle que [DevisViewModel.onAjouterArticle].
     */
    fun versLignes(): List<LigneDevis> = retenues.mapNotNull { ligne ->
        val prix = prixVente(ligne) ?: return@mapNotNull null
        val quantite = ligne.quantite?.takeIf { it > 0.0 } ?: return@mapNotNull null
        LigneDevis(
            devisId = "",
            designation = ligne.designation,
            quantite = quantite,
            unite = ligne.unite,
            prixUnitaire = prix,
            prixAchat = ligne.lue.prixNet,
        )
    }

    /**
     * Les prix à reporter au magasin, **toutes lignes reconnues comprises**, même
     * décochées : ne pas poser une ligne sur ce devis-ci ne rend pas faux le prix
     * que le fournisseur vient d'annoncer. Le prix de vente de la fiche suit la
     * règle de la saisie des prix : gardé s'il a été décidé, recalculé s'il
     * n'était qu'une proposition du coefficient des Réglages.
     */
    fun versPrix(coefficientReglages: Double): List<PrixArticle> {
        if (!mettreAJourPrix) return emptyList()
        return reconnues.map { ligne ->
            val article = ligne.article!!
            SaisiePrix.de(article)
                .copy(
                    reference = ligne.lue.reference.ifBlank { article.reference },
                    prixAchat = ligne.lue.prixNet,
                    fournisseurId = fournisseur?.id ?: article.fournisseurId,
                    fournisseurNom = fournisseur?.nom ?: article.fournisseurNom,
                )
                .versPrix(article, coefficientReglages)
        }
    }

    /**
     * Les fiches à créer pour les lignes [horsMagasin], relues contre le magasin
     * **au moment d'écrire** ([existants]) : une référence arrivée entre-temps —
     * le même devis lu deux fois — ne fait pas de doublon, et deux lignes du même
     * document à la même référence n'en font qu'une.
     *
     * La fiche naît **avec le prix d'achat lu** et son fournisseur, qui arrivent
     * ensemble comme partout au magasin : « Ajouter au devis » est le geste
     * explicite, et une fiche créée vide du prix qu'on vient de lire attendrait
     * dans « sans prix » qu'on le retape. La vente suit la règle de la saisie des
     * prix — proposée au coefficient des Réglages, rien sans lui —, et non celle
     * de ce devis-ci : une marge consentie à un client ne devient pas le tarif.
     * L'intitulé et l'unité sont ceux de la ligne posée, si bien que la fiche et
     * le devis disent la même chose.
     */
    fun versArticles(coefficientReglages: Double, existants: List<Article>): List<Article> {
        if (!creerArticles) return emptyList()
        val connus = existants.toMutableList()
        return horsMagasin.mapNotNull { ligne ->
            val reference = ligne.lue.reference
            if (reference.isNotBlank() && LectureTarif.parReference(reference, connus) != null) {
                return@mapNotNull null
            }
            Article(
                reference = reference,
                designation = designationLisible(ligne.lue.designation),
                unite = ligne.unite,
                prixAchat = ligne.lue.prixNet,
                prixVente = propositionVente(ligne.lue.prixNet, coefficientReglages) ?: 0.0,
                fournisseurId = fournisseur?.id,
                fournisseurNom = fournisseur?.nom.orEmpty(),
            ).also { connus += it }
        }
    }

    fun avecLigne(rang: Int, change: (LigneImport) -> LigneImport): ImportFournisseur =
        copy(lignes = lignes.map { if (it.rang == rang) change(it) else it })
}

/** L'unité d'une ligne lue hors du magasin : celle d'un article neuf. */
private const val UNITE_PAR_DEFAUT = "u"
