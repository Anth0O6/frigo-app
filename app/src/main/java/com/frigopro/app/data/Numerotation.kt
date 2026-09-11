package com.frigopro.app.data

import java.time.LocalDate

/**
 * Les références des documents : `INT-2605-018`, `DEV-2605-007`.
 *
 * Un client qui rappelle cite un numéro, pas un UUID. La forme retenue —
 * préfixe, année et mois sur quatre chiffres, rang sur trois — se dicte au
 * téléphone, se retrouve dans un classeur et se trie toute seule.
 *
 * Le rang repart de 1 à chaque mois. C'est volontaire : un compteur global
 * finirait par imposer des numéros à cinq chiffres, et surtout le mois rend la
 * référence *parlante* — on sait de quand date le document sans l'ouvrir.
 *
 * Le calcul est une fonction pure du mois et des numéros déjà attribués, ce
 * qui le rend éprouvable et, accessoirement, sûr : il prend le plus grand rang
 * existant plutôt que le nombre de documents, si bien qu'une suppression ne
 * peut pas faire réattribuer un numéro déjà sorti chez un client.
 */
object Numerotation {

    const val PREFIXE_INTERVENTION = "INT"

    const val PREFIXE_DEVIS = "DEV"

    /**
     * @param existants tous les numéros déjà attribués, quel que soit leur
     *   mois — le filtrage se fait ici.
     */
    fun suivant(prefixe: String, jour: LocalDate, existants: Collection<String>): String {
        val periode = "%02d%02d".format(jour.year % 100, jour.monthValue)
        val debut = "$prefixe-$periode-"
        val dernier = existants
            .filter { it.startsWith(debut) }
            .mapNotNull { it.removePrefix(debut).toIntOrNull() }
            .maxOrNull()
            ?: 0
        return "$debut%03d".format(dernier + 1)
    }
}
