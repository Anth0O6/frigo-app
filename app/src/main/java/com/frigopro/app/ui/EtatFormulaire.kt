package com.frigopro.app.ui

import com.frigopro.app.data.DUREE_PAR_DEFAUT_MIN
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
 *
 * Le formulaire ne touche qu'à une partie d'une intervention — le reste s'est
 * passé sur place. [origine] porte donc la ligne telle qu'elle était, et
 * [versIntervention] écrit **par-dessus** elle : sans cela, corriger l'heure
 * d'une intervention déjà faite remettrait son chronomètre à zéro, effacerait
 * son numéro et sa signature, et on ne s'en apercevrait qu'en rouvrant le
 * compte-rendu.
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
    /** Machine du parc du client ; `null` quand l'intervention n'en désigne pas. */
    val equipementId: String? = null,
    /** Nom affiché, recopié de la machine. Vide quand aucune n'est choisie. */
    val equipementNom: String = "",
    val statut: StatutIntervention = StatutIntervention.PLANIFIEE,
    val notes: String = "",
    /** Durée prévue du créneau, d'où la hauteur qu'il prend sur le planning. */
    val dureeMin: Int = DUREE_PAR_DEFAUT_MIN,
    /** Technicien à qui la tournée est confiée ; `null` quand elle ne l'est pas. */
    val technicienId: String? = null,
    /** Nom affiché, recopié du technicien. Vide quand aucun n'est choisi. */
    val technicienNom: String = "",
    /**
     * L'intervention avant l'édition, `null` en création.
     *
     * Ce n'est pas un champ de saisie : c'est ce qui garantit que le formulaire
     * n'efface pas ce qu'il n'affiche pas.
     */
    val origine: Intervention? = null,
) {

    val estCreation: Boolean get() = id == null

    /** On n'enregistre pas d'intervention sans savoir chez qui ni où. */
    val estValide: Boolean get() = client.isNotBlank() && ville.isNotBlank()

    /**
     * L'intervention à enregistrer.
     *
     * En édition, le formulaire écrit **par-dessus** [origine] : tout ce qu'il
     * n'affiche pas — le temps chronométré, le numéro attribué, la signature du
     * client, l'urgence — traverse l'opération intact. `modifieLe` est posé par
     * le dépôt, seul juge de l'instant d'écriture.
     */
    fun versIntervention(): Intervention {
        val base = origine ?: Intervention(
            id = id ?: UUID.randomUUID().toString(),
            date = date,
            heure = heure,
            client = client,
            ville = ville,
        )
        return base.copy(
            date = date,
            heure = heure,
            client = client,
            ville = ville,
            typeId = typeId,
            typeLibelle = typeLibelle,
            clientId = clientId,
            equipementId = equipementId,
            equipementNom = equipementNom,
            statut = statut,
            notes = notes,
            dureeMin = dureeMin,
            technicienId = technicienId,
            technicienNom = technicienNom,
        )
    }

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
            equipementId = intervention.equipementId,
            equipementNom = intervention.equipementNom,
            statut = intervention.statut,
            notes = intervention.notes,
            dureeMin = intervention.dureeMin,
            technicienId = intervention.technicienId,
            technicienNom = intervention.technicienNom,
            origine = intervention,
        )
    }
}
