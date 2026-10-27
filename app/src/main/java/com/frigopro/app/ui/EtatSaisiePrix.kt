package com.frigopro.app.ui

import com.frigopro.app.data.Article
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.data.LigneTarif
import com.frigopro.app.data.PrixArticle
import com.frigopro.app.data.auCentime

/**
 * Le prix de vente que le coefficient propose pour un prix d'achat.
 *
 * Même calcul que la feuille du matériel d'un devis — prix d'achat multiplié,
 * arrondi au centime —, et c'est la condition pour que les deux ne se
 * contredisent pas : un article tarifé ici au coefficient doit retomber sur le
 * prix que le devis aurait proposé de lui-même.
 *
 * `null` quand il n'y a rien à proposer : sans coefficient réglé, ou sans prix
 * d'achat. Un coefficient à zéro veut dire « non réglé » et non « vendre pour
 * rien », et un prix d'achat inconnu ne donne pas un prix de vente nul — il n'en
 * donne pas du tout. Même règle que le GWP d'un fluide hors catalogue.
 */
fun propositionVente(prixAchat: Double?, coefficient: Double): Double? =
    if (coefficient > 0.0 && prixAchat != null && prixAchat > 0.0) {
        (prixAchat * coefficient).auCentime()
    } else {
        null
    }

/**
 * Ce qu'on a tapé sur un article pendant la saisie groupée, et qui n'est pas
 * encore en base.
 *
 * Une ligne n'a de saisie qu'à partir du moment où on la touche : les autres
 * s'affichent telles qu'en base et ne s'écriront pas. C'est ce qui permet au
 * compteur « à enregistrer » de valoir zéro à l'ouverture, au lieu d'annoncer
 * trente-huit modifications que personne n'a faites.
 *
 * ## Le prix de vente : proposé, ou tapé
 *
 * Il n'est **pas stocké quand il se déduit** : tant qu'on ne l'a pas tapé, il se
 * relit du prix d'achat et du coefficient, si bien qu'un prix d'achat corrigé
 * entraîne la vente avec lui. Dès qu'on le tape, il fait foi, et aucun calcul ne
 * l'écrase ensuite — un prix de vente arrondi à la main, une remise sur un
 * article qu'on pose tous les jours, sont des décisions et pas des erreurs de
 * calcul.
 *
 * **Vider le champ rend la main au coefficient.** C'est le seul geste qui le
 * fasse, et il ne demande aucun bouton : un champ vide n'est pas un prix.
 *
 * Reste le prix de vente déjà en base quand on ouvre la ligne. Il est **gardé**
 * s'il ne correspond pas au coefficient — quelqu'un l'a décidé, sur la fiche ou
 * lors d'une saisie précédente — et **suit le calcul** s'il vaut zéro ou s'il est
 * exactement ce que le coefficient aurait proposé : un prix qu'on ne peut pas
 * distinguer d'une proposition est traité comme tel. C'est ce qui fait suivre la
 * hausse d'un tarif d'un mois sur l'autre sans retaper la vente de chaque ligne.
 *
 * @param venteTouchee le champ de vente a été modifié pendant cette saisie ;
 *   [venteManuelle] dit alors ce qu'on y a tapé, `null` pour un champ vidé.
 */
data class SaisiePrix(
    val reference: String,
    /** `null` pour un champ vide ; écrit zéro, qui veut dire « pas renseigné ». */
    val prixAchat: Double?,
    val venteTouchee: Boolean = false,
    val venteManuelle: Double? = null,
    val fournisseurId: String?,
    val fournisseurNom: String,
) {

    /** Le prix de vente retenu, compte tenu de ce qui a été tapé et du coefficient. */
    fun prixVente(article: Article, coefficient: Double): Double {
        val propose = propositionVente(prixAchat, coefficient)
        return when {
            venteTouchee -> venteManuelle ?: propose ?: 0.0
            suitLeCoefficient(article, coefficient) -> propose ?: 0.0
            else -> article.prixVente
        }
    }

    /** D'où vient le prix de vente affiché : l'écran le dit sous le champ. */
    fun origineVente(article: Article, coefficient: Double): OrigineVente = when {
        venteTouchee && venteManuelle != null -> OrigineVente.SAISIE
        !venteTouchee && !suitLeCoefficient(article, coefficient) -> OrigineVente.FICHE
        propositionVente(prixAchat, coefficient) != null -> OrigineVente.COEFFICIENT
        else -> OrigineVente.AUCUNE
    }

    /** Ce que le dépôt écrira. */
    fun versPrix(article: Article, coefficient: Double): PrixArticle = PrixArticle(
        articleId = article.id,
        reference = reference,
        prixAchat = prixAchat ?: 0.0,
        prixVente = prixVente(article, coefficient),
        fournisseurId = fournisseurId,
        fournisseurNom = fournisseurNom,
    )

    companion object {

        /** La saisie d'une ligne qu'on touche pour la première fois : ce qu'elle porte en base. */
        fun de(article: Article): SaisiePrix = SaisiePrix(
            reference = article.reference,
            prixAchat = article.prixAchat.takeIf { it > 0.0 },
            fournisseurId = article.fournisseurId,
            fournisseurNom = article.fournisseurNom,
        )

        /**
         * Le prix de vente en base n'est qu'une proposition du coefficient — ou
         * il n'y en a pas.
         *
         * Comparé au centime près et non à l'égalité stricte : un prix proposé
         * puis enregistré a déjà été arrondi, et c'est l'arrondi qu'on retrouve.
         */
        private fun suitLeCoefficient(article: Article, coefficient: Double): Boolean {
            if (article.prixVente <= 0.0) return true
            val propose = propositionVente(article.prixAchat, coefficient) ?: return false
            return propose.auCentime() == article.prixVente.auCentime()
        }
    }
}

/** D'où vient le prix de vente d'une ligne de la saisie. */
enum class OrigineVente {
    /** Tapé pendant cette saisie : aucun calcul n'y touchera. */
    SAISIE,

    /**
     * Celui de la fiche, tel qu'en base : la ligne n'a pas été touchée, ou son
     * prix ne correspond pas au coefficient et il est donc gardé.
     */
    FICHE,

    /** Proposé par le coefficient, et qui suivra le prix d'achat. */
    COEFFICIENT,

    /** Rien : ni prix tapé, ni de quoi en proposer un. */
    AUCUNE,
}

/**
 * Ce que la liste de la saisie montre : tous les articles, ou ceux qui
 * attendent un prix.
 *
 * « Sans prix » se juge sur **la base et non sur la saisie en cours**, et c'est
 * délibéré : une ligne qu'on est en train de remplir ne doit pas disparaître de
 * sous le doigt au premier chiffre tapé. Elle quitte le filtre à
 * l'enregistrement, quand elle a vraiment un prix.
 */
enum class FiltrePrix(val libelle: String) {
    TOUS("Tous"),
    SANS_PRIX("Sans prix"),

    /** Ce qui s'écrira — après la lecture d'un devis, ce qui a été lu. */
    A_ENREGISTRER("À enregistrer"),
}

/**
 * Un devis fournisseur lu, ou en train de l'être.
 *
 * @param lues les lignes de prix reconnues dans le document.
 * @param reportees celles qui sont passées dans la saisie, d'elles-mêmes ou à la main.
 */
data class LectureDevis(
    val enCours: Boolean = false,
    val echec: String? = null,
    val fournisseur: Fournisseur? = null,
    val date: java.time.LocalDate? = null,
    val lues: Int = 0,
    val reportees: Int = 0,
    val aRapprocher: List<LigneARapprocher> = emptyList(),
)

/** Une ligne lue qu'aucune référence du magasin ne désigne, et l'article qu'elle évoque peut-être. */
data class LigneARapprocher(
    val rang: Int,
    val ligne: LigneTarif,
    val suggestion: Article?,
)

/**
 * « GOULOTTE BLANC 80X60MM » devient « Goulotte blanc 80X60MM » : les tarifs
 * écrivent en capitales, le magasin non. Les mots qui portent un chiffre gardent
 * leur casse — une section ou un code se lisent mieux tels quels.
 */
internal fun designationLisible(designation: String): String =
    designation.trim().split(Regex("\\s+"))
        .joinToString(" ") { mot -> if (mot.any(Char::isDigit)) mot else mot.lowercase() }
        .replaceFirstChar { it.uppercase() }

/**
 * Une ligne de la saisie, prête à afficher : l'article, ce qu'on y a tapé, et ce
 * qui en découle.
 *
 * @param modifiee la ligne s'écrira à l'enregistrement — ce qui n'est pas la même
 *   chose qu'avoir été touchée : une valeur retapée à l'identique ne compte pas.
 */
data class LignePrix(
    val article: Article,
    val saisie: SaisiePrix,
    val prixVente: Double,
    val origineVente: OrigineVente,
    val cochee: Boolean,
    val modifiee: Boolean,
) {
    val sansPrix: Boolean get() = article.prixAchat <= 0.0
}

/**
 * Tout ce que l'écran de saisie des prix affiche, réuni.
 *
 * Un seul objet, pour la raison que donne [EtatAujourdhui] : le compteur « à
 * enregistrer » et la liste d'à côté doivent venir du même instant.
 */
data class EtatSaisiePrix(
    val lignes: List<LignePrix> = emptyList(),
    /** Le coefficient des Réglages ; zéro quand il n'est pas réglé. */
    val coefficient: Double = 0.0,
    val recherche: String = "",
    val filtre: FiltrePrix = FiltrePrix.TOUS,
    /** Les articles sans prix d'achat en base, tout le magasin compté. */
    val sansPrix: Int = 0,
    /** Ce que l'enregistrement écrira, lignes masquées par le filtre comprises. */
    val aEnregistrer: Int = 0,
    /** Les lignes cochées, masquées comprises : c'est sur elles que le lot portera. */
    val cochees: Int = 0,
) {
    val coefficientRegle: Boolean get() = coefficient > 0.0
}

/**
 * Compose l'état de l'écran à partir de la base et de la saisie.
 *
 * Fonction pure, pour s'éprouver sans `ViewModel` : c'est ici que se décide ce
 * qui compte comme modifié, et ce chiffre-là ne doit pas mentir.
 */
internal fun composerSaisiePrix(
    articles: List<Article>,
    coefficient: Double,
    saisies: Map<String, SaisiePrix>,
    cochees: Set<String>,
    recherche: String,
    filtre: FiltrePrix,
): EtatSaisiePrix {
    val toutes = articles.map { article ->
        val saisie = saisies[article.id]
        val effective = saisie ?: SaisiePrix.de(article)
        LignePrix(
            article = article,
            saisie = effective,
            // Une ligne non touchée montre la base telle quelle : y afficher une
            // proposition qui ne s'écrira pas ferait croire à un prix enregistré.
            prixVente = if (saisie == null) article.prixVente else saisie.prixVente(article, coefficient),
            origineVente = if (saisie == null) {
                if (article.prixVente > 0.0) OrigineVente.FICHE else OrigineVente.AUCUNE
            } else {
                saisie.origineVente(article, coefficient)
            },
            cochee = article.id in cochees,
            modifiee = saisie != null &&
                saisie.versPrix(article, coefficient).appliqueA(article) != article,
        )
    }

    val visibles = toutes
        .filter {
            when (filtre) {
                FiltrePrix.TOUS -> true
                FiltrePrix.SANS_PRIX -> it.sansPrix
                FiltrePrix.A_ENREGISTRER -> it.modifiee
            }
        }
        .filter {
            Recherche.correspond(
                recherche,
                it.article.designation,
                it.saisie.reference,
                it.saisie.fournisseurNom,
            )
        }
        // Ce qui attend un prix d'abord, l'ordre du magasin ensuite : c'est la
        // liste qu'on vient remplir, et le tri stable garde l'ordre alphabétique
        // que le dépôt a donné à l'intérieur de chaque groupe.
        .sortedByDescending { it.sansPrix }

    return EtatSaisiePrix(
        lignes = visibles,
        coefficient = coefficient,
        recherche = recherche,
        filtre = filtre,
        sansPrix = toutes.count { it.sansPrix },
        aEnregistrer = toutes.count { it.modifiee },
        cochees = toutes.count { it.cochee },
    )
}
