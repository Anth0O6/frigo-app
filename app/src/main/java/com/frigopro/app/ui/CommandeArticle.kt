package com.frigopro.app.ui

import com.frigopro.app.data.Fournisseur

/**
 * Ce que le fournisseur d'un article permet de faire, copié sur la ligne.
 *
 * Une copie et non le fournisseur : la ligne n'a besoin que de ces deux
 * champs, et l'écran ne lit ainsi aucun flux de plus.
 */
data class ContactFournisseur(
    val telephone: String = "",
    val lien: String = "",
) {
    companion object {
        fun de(fournisseur: Fournisseur) =
            ContactFournisseur(fournisseur.telephone, fournisseur.siteCatalogue)
    }
}

/** Le geste que « Commander » déclenche. L'application ne passe aucune commande. */
sealed interface GesteCommande {
    data class Appeler(val telephone: String) : GesteCommande
    data class Catalogue(val lien: String) : GesteCommande

    /** Fournisseur inconnu ou sans coordonnées : la fiche de l'article, comme avant. */
    data object Ouvrir : GesteCommande
}

/**
 * Le geste le plus utile : appeler, sinon le catalogue, sinon rien d'autre que
 * la fiche. Rien n'est inventé : sans coordonnée saisie, le bouton reste
 * « Ouvrir ».
 */
fun choisirGesteCommande(contact: ContactFournisseur?): GesteCommande = when {
    contact == null -> GesteCommande.Ouvrir
    contact.telephone.isNotBlank() -> GesteCommande.Appeler(contact.telephone.trim())
    contact.lien.isNotBlank() -> GesteCommande.Catalogue(contact.lien.trim())
    else -> GesteCommande.Ouvrir
}

/** Le libellé du bouton, qui nomme ce qu'il fera. */
val GesteCommande.libelle: String
    get() = when (this) {
        is GesteCommande.Appeler, is GesteCommande.Catalogue -> "Commander"
        GesteCommande.Ouvrir -> "Ouvrir"
    }
