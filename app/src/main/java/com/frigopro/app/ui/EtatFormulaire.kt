package com.frigopro.app.ui

import com.frigopro.app.data.Intervention
import com.frigopro.app.data.TypePanne
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Contenu éditable du formulaire d'intervention.
 *
 * [id] vaut `null` pour une création et porte l'identifiant de la ligne en
 * cours d'édition sinon : c'est ce qui distingue « Ajouter » d'« Enregistrer ».
 */
data class EtatFormulaire(
    val id: String? = null,
    val date: LocalDate = LocalDate.now(),
    val heure: LocalTime = LocalTime.of(9, 0),
    val client: String = "",
    val ville: String = "",
    val typePanne: TypePanne = TypePanne.FUITE_FLUIDE,
) {

    val estCreation: Boolean get() = id == null

    /** On n'enregistre pas d'intervention sans savoir chez qui ni où. */
    val estValide: Boolean get() = client.isNotBlank() && ville.isNotBlank()

    /** `modifieLe` est posé par le dépôt, seul juge de l'instant d'écriture. */
    fun versIntervention(): Intervention = Intervention(
        id = id ?: UUID.randomUUID().toString(),
        date = date,
        heure = heure,
        client = client,
        ville = ville,
        typePanne = typePanne,
    )

    companion object {

        fun depuis(intervention: Intervention): EtatFormulaire = EtatFormulaire(
            id = intervention.id,
            date = intervention.date,
            heure = intervention.heure,
            client = intervention.client,
            ville = intervention.ville,
            typePanne = intervention.typePanne,
        )
    }
}
