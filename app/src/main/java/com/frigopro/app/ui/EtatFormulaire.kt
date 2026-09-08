package com.frigopro.app.ui

import com.frigopro.app.data.Intervention
import com.frigopro.app.data.TypePanne
import java.time.LocalTime

/**
 * Contenu éditable du formulaire d'intervention.
 *
 * [id] vaut `null` pour une création et porte l'identifiant de la ligne en
 * cours d'édition sinon : c'est ce qui distingue « Ajouter » d'« Enregistrer ».
 */
data class EtatFormulaire(
    val id: Long? = null,
    val heure: LocalTime = LocalTime.of(9, 0),
    val client: String = "",
    val ville: String = "",
    val typePanne: TypePanne = TypePanne.FUITE_FLUIDE,
) {

    val estCreation: Boolean get() = id == null

    /** On n'enregistre pas d'intervention sans savoir chez qui ni où. */
    val estValide: Boolean get() = client.isNotBlank() && ville.isNotBlank()

    companion object {

        fun depuis(intervention: Intervention): EtatFormulaire = EtatFormulaire(
            id = intervention.id,
            heure = intervention.heure,
            client = intervention.client,
            ville = intervention.ville,
            typePanne = intervention.typePanne,
        )
    }
}
