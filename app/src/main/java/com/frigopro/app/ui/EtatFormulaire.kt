package com.frigopro.app.ui

import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutIntervention
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
    /** Renseigné quand le client vient du carnet ; `null` s'il est saisi à la main. */
    val clientId: String? = null,
    /** Type choisi dans la liste du technicien ; `null` s'il n'en vient pas. */
    val typeId: String? = null,
    /** Intitulé affiché, recopié du type. Vide quand aucun n'est choisi. */
    val typeLibelle: String = "",
    val statut: StatutIntervention = StatutIntervention.A_FAIRE,
    val notes: String = "",
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
        typeId = typeId,
        typeLibelle = typeLibelle,
        clientId = clientId,
        statut = statut,
        notes = notes,
    )

    companion object {

        fun depuis(intervention: Intervention): EtatFormulaire = EtatFormulaire(
            id = intervention.id,
            date = intervention.date,
            heure = intervention.heure,
            client = intervention.client,
            ville = intervention.ville,
            typeId = intervention.typeId,
            typeLibelle = intervention.typeLibelle,
            clientId = intervention.clientId,
            statut = intervention.statut,
            notes = intervention.notes,
        )
    }
}
