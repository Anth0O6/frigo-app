package com.frigopro.app.ui

import java.util.Locale

/**
 * Saisie et affichage des nombres décimaux.
 *
 * Un frigoriste français tape « 11,2 ». Un pavé numérique Android propose
 * tantôt la virgule, tantôt le point, selon le clavier installé et la langue
 * du système — et un champ qui refuse l'un des deux est un champ qui refuse la
 * saisie une fois sur deux, gants aux mains, sur un toit. Les deux sont donc
 * acceptés en entrée ; la virgule seule est produite en sortie.
 */
object Nombres {

    /**
     * Lit un nombre saisi, virgule ou point. `null` pour une saisie vide ou
     * illisible — ce qui est un état normal d'un champ en cours de frappe, pas
     * une erreur à signaler.
     */
    fun versDecimal(saisie: String): Double? {
        val nettoye = saisie.trim().replace(',', '.').replace(" ", "")
        if (nettoye.isEmpty()) return null
        return nettoye.toDoubleOrNull()
    }

    /**
     * Écrit un nombre pour l'affichage : virgule décimale, et pas de zéro
     * inutile — « 6,2 » et non « 6,20 », « 3 » et non « 3,0 ».
     */
    fun enTexte(valeur: Double?, decimales: Int = 2): String {
        if (valeur == null) return ""
        val arrondi = String.format(Locale.FRANCE, "%.${decimales}f", valeur)
        // Les zéros inutiles ne se trouvent qu'après la virgule : sans elle,
        // « 450 » deviendrait « 45 ». Le cas se présentait dès qu'on demandait
        // zéro décimale, ce que fait le montant abrégé des tuiles.
        if (!arrondi.contains(',')) return arrondi
        return arrondi.trimEnd('0').trimEnd(',').ifEmpty { "0" }
    }

    /** Une masse de fluide : toujours deux décimales, comme sur une balance. */
    fun enMasse(valeur: Double): String = String.format(Locale.FRANCE, "%.2f", valeur)

    /** Un montant : deux décimales et un espace insécable avant l'euro. */
    fun enEuros(valeur: Double): String =
        String.format(Locale.FRANCE, "%,.2f €", valeur).replace(' ', ' ')

    /**
     * Un montant **abrégé**, pour une tuile large d'un tiers d'écran.
     *
     * « 12,4 k€ » plutôt que « 12 400,00 € » : sur trois tuiles côte à côte, la
     * forme longue est tronquée, et un montant tronqué ne dit rien — ou, pire,
     * dit autre chose. Les centimes disparaissent, et c'est voulu : on lit un
     * chiffre d'affaires à l'euro près sur un document, pas sur une tuile.
     *
     * La forme longue reste celle des lignes et des totaux, où elle a la place
     * et où elle doit être exacte.
     */
    fun enEurosCourt(valeur: Double): String = when {
        valeur >= 1_000_000 -> "${enTexte(valeur / 1_000_000, decimales = 1)} M\u20ac"
        valeur >= 10_000 -> "${enTexte(valeur / 1_000, decimales = 1)} k\u20ac"
        else -> "${enTexte(valeur, decimales = 0)} \u20ac"
    }
}
