package com.frigopro.app.ui

import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.FactureChiffree
import java.text.Normalizer

/**
 * La recherche d'une liste : ce qu'on tape, et ce à quoi on le compare.
 *
 * Elle est nommée une fois plutôt que réécrite par écran, pour la raison que le
 * projet donne à tout son vocabulaire partagé : trois filtres qui se ressemblent
 * sans être le même finissent par ne plus se comporter pareil, et l'utilisateur
 * apprend alors que « la recherche marche sur les devis mais pas sur les
 * factures » — ce qui est la façon la plus sûre de faire cesser de s'en servir.
 *
 * Deux décisions la gouvernent, et chacune répare une façon de ne rien trouver :
 *
 * - **Les accents sont repliés.** Un technicien tape « pean » sur un pavé
 *   numérique en tenant une lampe, et « Péan » doit sortir. Refuser de le
 *   trouver serait exact et inutile. Le repliage passe par la décomposition
 *   Unicode plutôt que par une table de correspondances : celle-ci aurait oublié
 *   un caractère au premier nom qui sort de l'alphabet français.
 * - **Les mots se cherchent séparément, et tous doivent être là.** « carrefour
 *   toit » trouve le devis du toit de Carrefour quel que soit l'ordre des mots
 *   dans la fiche, alors qu'une recherche de la chaîne entière n'aurait rien
 *   trouvé. C'est la différence entre se souvenir d'une fiche et savoir comment
 *   elle est rédigée.
 *
 * Une recherche vide laisse tout passer : c'est le cas normal, et c'est ce qui
 * permet à l'écran de filtrer sans se demander d'abord s'il cherche.
 */
internal object Recherche {

    /** `true` si tous les mots de [recherche] paraissent dans l'un des [champs]. */
    fun correspond(recherche: String, vararg champs: String?): Boolean {
        val mots = decouper(recherche)
        if (mots.isEmpty()) return true
        // Les champs sont réunis en un seul texte : « carrefour » dans le nom du
        // client et « toit » dans l'objet doivent satisfaire la même recherche,
        // et exiger que chaque mot tombe dans le *même* champ serait exiger de
        // l'utilisateur qu'il sache lequel.
        val foin = champs.filterNotNull().joinToString(" ", transform = ::plier)
        // Et le même texte **sans aucune coupure**, parce que la ponctuation se
        // tape de deux façons : « dev 2609 » se cherche dans le premier, mais
        // « dev2609 » — un numéro recopié de mémoire, sans tiret — ne s'y trouve
        // pas, puisque le repliage a séparé « dev » de « 2609 » des deux côtés.
        // Le second rattrape ce cas, et lui seul.
        //
        // Un mot peut alors chevaucher deux mots du texte : « tclim » trouverait
        // « Market Clim ». C'est le prix assumé de cette tolérance, et il est
        // faible — un filtre de liste peut se permettre d'en montrer un de trop,
        // là où n'en montrer aucun fait conclure que la fiche a disparu.
        val compact = foin.filter { !it.isWhitespace() }
        return mots.all { mot -> mot in foin || mot in compact }
    }

    /** Les mots d'une recherche, repliés, les séparateurs écartés. */
    fun decouper(recherche: String): List<String> =
        plier(recherche).split(' ').filter { it.isNotBlank() }

    /**
     * Minuscules et accents retirés : « Péan » et « pean » doivent se rencontrer.
     *
     * `NFD` décompose « é » en « e » plus un accent combinant, que le filtre des
     * marques non espaçantes retire ensuite. C'est aussi ce qui replie « ç », « ï »
     * et tout ce qu'on n'a pas pensé à énumérer.
     */
    private fun plier(texte: String): String =
        Normalizer.normalize(texte.lowercase(), Normalizer.Form.NFD)
            .replace(MARQUES, "")
            .replace(SEPARATEURS, " ")
            .trim()

    private val MARQUES = Regex("\\p{Mn}+")

    /**
     * Ce qui sépare deux mots, au-delà de l'espace.
     *
     * Un numéro de devis s'écrit « DEV-2609-007 », et quelqu'un qui tape
     * « dev 2609 » doit le trouver : sans cela il faudrait connaître la
     * ponctuation exacte du numéro pour retrouver un document par son numéro,
     * ce qui est l'inverse du service rendu.
     */
    private val SEPARATEURS = Regex("[^\\p{L}\\p{N}]+")
}

/**
 * Ce à quoi se compare une recherche de devis.
 *
 * Quatre champs, et pas les montants : on cherche un devis par le client, par ce
 * qu'il propose ou par son numéro, jamais par « 1 240,50 € » — un montant se
 * retient mal et s'écrit de trois façons. La machine en fait partie parce que
 * c'est souvent le seul nom dont on se souvienne : « le devis du groupe du
 * toit ».
 */
internal fun DevisChiffre.correspondA(recherche: String): Boolean = Recherche.correspond(
    recherche,
    devis.numero,
    devis.clientNom,
    devis.objet,
    devis.equipementNom,
)

/** Le pendant pour une facture. Voir [DevisChiffre.correspondA]. */
internal fun FactureChiffree.correspondA(recherche: String): Boolean = Recherche.correspond(
    recherche,
    facture.numero,
    facture.clientNom,
    facture.objet,
    facture.equipementNom,
)

/**
 * Ce à quoi se compare une recherche d'intervention.
 *
 * Le nom **recopié sur l'intervention** et celui de la fiche du client sont tous
 * deux cherchés, et ce n'est pas un doublon : le premier est le nom tel qu'il
 * était le jour de la tournée, le second celui d'aujourd'hui. Une enseigne qui a
 * changé de nom doit se retrouver sous les deux, sinon la recherche marche pour
 * les interventions d'avant le changement ou pour celles d'après, mais jamais
 * pour les deux.
 *
 * La ville en fait partie parce qu'elle est parfois tout ce dont on se souvient
 * d'une intervention ancienne, et le numéro parce qu'un compte-rendu envoyé porte
 * une référence à laquelle un client peut se référer.
 */
internal fun LigneTournee.correspondA(recherche: String): Boolean = Recherche.correspond(
    recherche,
    intervention.numero,
    intervention.client,
    intervention.ville,
    intervention.typeLibelle,
    intervention.equipementNom,
    intervention.notes,
    client?.nom,
    client?.ville,
)
