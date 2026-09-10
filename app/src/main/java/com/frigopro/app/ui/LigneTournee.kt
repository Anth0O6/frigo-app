package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.Intervention

/**
 * Une intervention de la journée, accompagnée de la fiche du client chez qui
 * elle a lieu.
 *
 * L'intervention porte le nom et la ville tels qu'ils étaient au moment de la
 * saisie ; la fiche apporte ce qui sert à agir maintenant — appeler, se rendre
 * sur place. Elle vaut `null` pour une intervention saisie à la main avant
 * l'arrivée du carnet, ou dont le client a été retiré.
 */
data class LigneTournee(
    val intervention: Intervention,
    val client: Client?,
) {

    val appelable: Boolean get() = client?.appelable == true

    val localisable: Boolean get() = client?.localisable == true
}
