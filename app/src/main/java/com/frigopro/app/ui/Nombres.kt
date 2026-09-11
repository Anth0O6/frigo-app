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
        return arrondi.trimEnd('0').trimEnd(',').ifEmpty { "0" }
    }

    /** Une masse de fluide : toujours deux décimales, comme sur une balance. */
    fun enMasse(valeur: Double): String = String.format(Locale.FRANCE, "%.2f", valeur)

    /** Un montant : deux décimales et un espace insécable avant l'euro. */
    fun enEuros(valeur: Double): String =
        String.format(Locale.FRANCE, "%,.2f €", valeur).replace(' ', ' ')
}
