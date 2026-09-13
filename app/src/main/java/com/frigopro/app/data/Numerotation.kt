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

    const val PREFIXE_FACTURE = "FAC"

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

    /**
     * La référence d'une facture : `FAC-2026-0042`.
     *
     * Elle ne suit **pas** la règle des deux autres, et l'écart est le sujet.
     * Un devis ou un compte-rendu se numérotent au mois et tolèrent un rang
     * abandonné : rien n'oblige à ce qu'ils se suivent. Une facture, si — la
     * numérotation doit être « chronologique et continue », sans rupture, et un
     * trou dans la séquence est ce qu'un contrôle cherche en premier.
     *
     * D'où deux différences :
     *
     * - **L'année entière** plutôt que le mois. Un compteur mensuel repart à 1
     *   douze fois par an, ce qui fait douze séquences à justifier là où une
     *   seule suffit. Le rang va donc à quatre chiffres, ce qui tient jusqu'à
     *   dix mille factures dans l'année.
     * - **Aucun numéro n'est attribué à un brouillon.** C'est ce qui rend la
     *   continuité tenable sans registre séparé : le rang ne se consomme qu'au
     *   moment de l'émission, et une facture émise ne se supprime plus — elle
     *   s'annule en gardant son numéro. Le `max + 1` d'ici suffit alors, là où
     *   il ne suffirait pas si un brouillon abandonné avait déjà pris un rang.
     */
    fun suivantAnnuel(prefixe: String, jour: LocalDate, existants: Collection<String>): String {
        val debut = "$prefixe-${jour.year}-"
        val dernier = existants
            .filter { it.startsWith(debut) }
            .mapNotNull { it.removePrefix(debut).toIntOrNull() }
            .maxOrNull()
            ?: 0
        return "$debut%04d".format(dernier + 1)
    }
}
