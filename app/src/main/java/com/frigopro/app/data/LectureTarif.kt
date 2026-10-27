package com.frigopro.app.data

import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.abs

/**
 * Ce qu'on lit d'un devis ou d'une proposition de fournisseur, et comment on le
 * rapproche du magasin.
 *
 * ## Aucun format par enseigne
 *
 * Une ligne de tarif se reconnaît **à son arithmétique**, pas à sa mise en
 * page : quelque part en fin de ligne il y a une quantité, un prix unitaire et
 * un montant, et le premier multiplié par le second redonne le troisième au
 * centime près. C'est vrai chez Richardson, chez GFF et chez n'importe quel
 * grossiste, avec ou sans colonne de remise, avec ou sans unité — et c'est ce
 * qui dispense d'écrire un lecteur par enseigne, chacun à reprendre au premier
 * changement de maquette. Le contrôle sert aussi de garde-fou : une ligne de
 * total, d'en-tête ou d'adresse ne passe pas la multiplication, et une erreur de
 * lecture sur un chiffre non plus — on préfère ne rien lire que lire faux.
 *
 * ## Rien n'est deviné
 *
 * Le rapprochement avec le magasin se fait **par la référence**, et seulement
 * par elle : c'est le seul identifiant qu'un tarif et le magasin partagent.
 * Une ligne sans référence connue n'est jamais reportée d'office — elle est
 * proposée, avec au mieux une suggestion tirée de la désignation, et c'est le
 * technicien qui tranche. La référence est alors retenue sur l'article, si
 * bien que le devis suivant du même fournisseur se reporte tout seul.
 *
 * Les fonctions sont pures : la reconnaissance de caractères vit dans
 * `LecteurDevis`, côté Android, et ce qui décide de quelque chose s'éprouve ici
 * sans téléphone — même partage que `ChampMagnetique` et `LecteurChamp`.
 */
object LectureTarif {

    /**
     * Les lignes de texte du document, reconstituées à partir de ce que la
     * reconnaissance de caractères rend.
     *
     * Elle découpe une page en morceaux — la désignation d'un côté, les chiffres
     * de l'autre, souvent dans des blocs différents — et la ligne du tableau
     * n'existe plus que par leur hauteur commune. Deux morceaux sont sur la même
     * ligne quand leurs milieux sont plus proches qu'une demi-hauteur de
     * caractère : assez large pour une photo prise un peu de travers, assez
     * étroit pour ne pas fondre deux lignes d'un tableau serré.
     *
     * Les morceaux d'une même ligne sont joints par **deux espaces**, qui disent
     * « ici changeait la colonne » : c'est ce qui permet ensuite de séparer une
     * désignation de sa référence.
     */
    fun rangees(fragments: List<Fragment>): List<String> {
        val tries = fragments.filter { it.texte.isNotBlank() }.sortedBy { it.milieu }
        val rangees = mutableListOf<MutableList<Fragment>>()
        for (fragment in tries) {
            val derniere = rangees.lastOrNull()
            val milieu = derniere?.let { r -> r.sumOf { it.milieu.toDouble() } / r.size }
            val tolerance = derniere?.let { r -> minOf(fragment.hauteur, r.minOf { it.hauteur }) / 2.0 }
            if (derniere != null && milieu != null && tolerance != null &&
                abs(fragment.milieu - milieu) < tolerance
            ) {
                derniere += fragment
            } else {
                rangees += mutableListOf(fragment)
            }
        }
        return rangees.map { r -> r.sortedBy { it.gauche }.joinToString(SEPARATEUR_COLONNE) { it.texte.trim() } }
    }

    /** Lit tout un document : ses lignes de tarif, sa date, et le fournisseur s'il est connu. */
    fun lire(rangees: List<String>, fournisseurs: List<Fournisseur>): TarifLu {
        val texte = rangees.joinToString("\n")
        return TarifLu(
            lignes = rangees.mapNotNull(::lireLigne),
            fournisseur = fournisseurDe(texte, fournisseurs),
            date = dateDe(texte),
        )
    }

    /**
     * Une ligne de tarif, ou `null` si la ligne n'en est pas une.
     *
     * La lecture se fait **depuis la fin** : le montant est le dernier nombre,
     * puis on cherche, parmi les nombres qui le précèdent, un prix et une
     * quantité dont le produit le redonne. Le prix retenu est le plus proche du
     * montant — sur une ligne à remise, c'est le prix net et non le prix brut.
     *
     * Les milliers séparés d'une espace (« 1 250,00 ») sont recollés **d'abord**,
     * et la ligne brute n'est relue qu'à défaut : laissés séparés, « 1 » et
     * « 250,00 » se multiplient l'un par l'autre et donnent un prix d'un euro. Le
     * risque inverse — une quantité sans décimales collée à un prix — se rattrape
     * de lui-même, la ligne recollée ne passant plus la multiplication.
     */
    fun lireLigne(rangee: String): LigneTarif? {
        val propre = rangee.replace(DECIMALE_COUPEE, "$1,$2")
        val recollee = propre.replace(MILLIERS, "$1$2")
        return (if (recollee != propre) lireDepuis(recollee) else null) ?: lireDepuis(propre)
    }

    private fun lireDepuis(rangee: String): LigneTarif? {
        val champs = rangee.split(SEPARATEUR_COLONNE).map { it.trim() }.filter { it.isNotEmpty() }
        // Chaque mot, avec le numéro du champ d'où il vient.
        val mots = champs.flatMapIndexed { i, champ -> champ.split(ESPACES).map { i to it } }
        if (mots.size < 4) return null

        // La traîne de chiffres, en partant de la fin : nombres, unités, remises.
        var debut = mots.size
        while (debut > 0 && estDeTraine(mots[debut - 1].second)) debut--
        val traine = (debut until mots.size).filter { nombre(mots[it].second) != null }
        if (traine.size < 3) return null
        val iMontant = traine.last()
        // Rien d'autre qu'une unité ou un symbole après le montant.
        val montant = nombre(mots[iMontant].second) ?: return null
        if (montant <= 0.0) return null

        for (iPrix in traine.dropLast(1).reversed()) {
            val prix = nombre(mots[iPrix].second) ?: continue
            if (prix <= 0.0) continue
            for (iQuantite in traine.filter { it < iPrix }.reversed()) {
                val quantite = nombre(mots[iQuantite].second) ?: continue
                if (quantite <= 0.0) continue
                if (abs(quantite * prix - montant) <= TOLERANCE_EUROS) {
                    val tete = mots.subList(0, iQuantite)
                    if (tete.none { it.second.any(Char::isLetter) }) return null
                    val (designation, reference) = separer(tete)
                    if (designation.isBlank()) return null
                    return LigneTarif(designation, reference, quantite, prix, montant)
                }
            }
        }
        return null
    }

    /**
     * La désignation et la référence, à partir de ce qui précède la quantité.
     *
     * Quand la reconnaissance a séparé les colonnes, la référence est le dernier
     * champ s'il en a l'air — court, avec un chiffre. Sinon c'est le dernier mot,
     * aux mêmes conditions. Une ligne sans rien qui ressemble à une référence en
     * garde une vide : en inventer une la ferait rapprocher d'un article qui
     * n'est pas le sien au devis suivant.
     */
    private fun separer(tete: List<Pair<Int, String>>): Pair<String, String> {
        val dernierChamp = tete.last().first
        val duDernier = tete.filter { it.first == dernierChamp }.map { it.second }
        val avant = tete.filter { it.first != dernierChamp }.map { it.second }
        if (avant.isNotEmpty() && duDernier.size <= 2 && duDernier.any { it.any(Char::isDigit) }) {
            return avant.joinToString(" ") to duDernier.joinToString(" ")
        }
        val tous = tete.map { it.second }
        val dernier = tous.last()
        return if (tous.size > 1 && estReference(dernier)) {
            tous.dropLast(1).joinToString(" ") to dernier
        } else {
            tous.joinToString(" ") to ""
        }
    }

    private fun estReference(mot: String): Boolean =
        mot.length >= 4 && mot.any(Char::isDigit) && mot.none(Char::isLowerCase)

    private fun estDeTraine(mot: String): Boolean =
        nombre(mot) != null || UNITE.matches(mot) || REMISE.matches(mot) || mot == "€"

    /** Un nombre écrit à la française ou à l'anglaise ; `null` pour tout le reste. */
    internal fun nombre(mot: String): Double? {
        if (!NOMBRE.matches(mot)) return null
        return mot.replace(',', '.').toDoubleOrNull()
    }

    /**
     * Le fournisseur du carnet dont le nom paraît dans le document.
     *
     * Le carnet et non une liste figée : un tarif ne se reporte que sur un
     * fournisseur qu'on a, et le premier trouvé vaut mieux qu'aucun — l'écran
     * permet de le changer. Un nom de moins de trois lettres ne se cherche pas,
     * il se trouverait partout.
     */
    fun fournisseurDe(texte: String, fournisseurs: List<Fournisseur>): Fournisseur? {
        val plie = " " + plier(texte) + " "
        return fournisseurs.firstOrNull { f ->
            val nom = plier(f.nom)
            nom.length >= 3 && " $nom " in plie
        }
    }

    /** La première date du document : celle de l'édition, en tête de page. */
    fun dateDe(texte: String): LocalDate? = DATE.find(texte)?.let { m ->
        val (jour, mois, annee) = m.destructured
        runCatching { LocalDate.of(annee.toInt(), mois.toInt(), jour.toInt()) }.getOrNull()
    }

    /**
     * L'article du magasin qui porte cette référence, à la ponctuation et à la
     * casse près : « white-80bcana-2m » et « WHITE 80BCANA 2M » sont la même.
     */
    fun parReference(reference: String, articles: List<Article>): Article? {
        val cle = cleReference(reference)
        if (cle.length < 3) return null
        return articles.firstOrNull { cleReference(it.reference) == cle }
    }

    /**
     * L'article dont la désignation ressemble le plus à celle du tarif, ou `null`.
     *
     * Ce n'est qu'une **suggestion**, et elle n'est jamais appliquée seule : deux
     * mots en commun au moins, et à égalité celui qui en a le moins d'autres —
     * « goulotte 80x60 » va plus volontiers à la barre qu'à l'angle, dont le nom
     * porte deux mots de plus que le tarif ne dit pas.
     */
    fun suggestion(designation: String, articles: List<Article>): Article? {
        val mots = motsSignificatifs(designation)
        if (mots.isEmpty()) return null
        return articles
            .map { a ->
                val siens = motsSignificatifs(a.designation)
                Triple(a, (mots intersect siens).size, siens.size)
            }
            .filter { it.second >= 2 }
            .maxWithOrNull(compareBy<Triple<Article, Int, Int>> { it.second }.thenByDescending { it.third })
            ?.first
    }

    private fun motsSignificatifs(texte: String): Set<String> =
        plier(texte).split(' ')
            .map { it.removeSuffix("mm") }
            .filter { it.length >= 3 || it.any(Char::isDigit) && it.length >= 2 }
            .toSet()

    private fun cleReference(reference: String): String =
        reference.uppercase().filter(Char::isLetterOrDigit)

    private fun plier(texte: String): String =
        Normalizer.normalize(texte.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** Ce qui sépare deux colonnes dans une ligne reconstituée. */
    const val SEPARATEUR_COLONNE = "  "

    /** Un centime d'arrondi, et un peu de marge pour les quantités à trois décimales. */
    private const val TOLERANCE_EUROS = 0.02

    private val ESPACES = Regex("\\s+")
    private val NOMBRE = Regex("^-?\\d{1,7}([.,]\\d{1,4})?$")
    private val UNITE = Regex("^[A-Za-z]{1,3}\\.?$")
    private val REMISE = Regex("^-?\\d{1,3}([.,]\\d{1,2})?%$|^%$")
    private val DECIMALE_COUPEE = Regex("(\\d)[.,] (\\d{2})(?!\\d)")
    private val MILLIERS = Regex("(?<![\\d.,])(\\d{1,3}) (\\d{3}[.,]\\d{2})(?!\\d)")
    private val DATE = Regex("\\b(\\d{2})/(\\d{2})/(\\d{4})\\b")
}

/** Un morceau de texte reconnu, et sa place sur la page. */
data class Fragment(
    val texte: String,
    val gauche: Float,
    val haut: Float,
    val bas: Float,
) {
    val milieu: Float get() = (haut + bas) / 2f
    val hauteur: Float get() = (bas - haut).coerceAtLeast(1f)
}

/** Une ligne de tarif lue : ce que le fournisseur facture, et sous quel nom. */
data class LigneTarif(
    val designation: String,
    /** Vide quand rien ne ressemblait à une référence. */
    val reference: String,
    val quantite: Double,
    /** Le prix unitaire net, hors taxes : c'est le prix d'achat. */
    val prixNet: Double,
    val montant: Double,
)

/** Un document lu en entier. */
data class TarifLu(
    val lignes: List<LigneTarif>,
    val fournisseur: Fournisseur?,
    val date: LocalDate?,
)

/**
 * Ce qui transforme un document — une photo, un PDF — en lignes de texte.
 *
 * Une interface pour que le `ViewModel` s'éprouve sans téléphone ; la seule
 * implémentation est `LecteurDevis`, qui lit **sur l'appareil** : un tarif
 * négocié n'a rien à faire sur un serveur.
 */
fun interface LecteurTarif {
    /** `null` quand le document n'a pas pu être ouvert ; une liste vide s'il ne contenait aucun texte. */
    suspend fun lire(adresse: String): List<String>?
}
