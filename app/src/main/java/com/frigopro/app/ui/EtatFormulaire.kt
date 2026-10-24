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
    /**
     * Le donneur d'ordre, en sous-traitance ; `null` quand il n'y en a pas.
     *
     * Deux rôles, et l'ordre de saisie dit lequel prime : on saisit d'abord
     * **où** l'on va — c'est ce qui décide de la tournée, de l'itinéraire et du
     * parc proposé — et seulement ensuite pour qui, s'il y a lieu. L'inverse
     * aurait fait poser la question du donneur d'ordre à chaque intervention
     * ordinaire, où elle n'en est pas une.
     */
    val clientFactureId: String? = null,
    val clientFactureNom: String = "",
    /** Type choisi dans la liste du technicien ; `null` s'il n'en vient pas. */
    val typeId: String? = null,
    /** Intitulé affiché, recopié du type. Vide quand aucun n'est choisi. */
    val typeLibelle: String = "",
    /** Machine du parc du client ; `null` quand l'intervention n'en désigne pas. */
    val equipementId: String? = null,
    /** Nom affiché, recopié de la machine. Vide quand aucune n'est choisie. */
    val equipementNom: String = "",
    val statut: StatutIntervention = StatutIntervention.PLANIFIEE,
    /**
     * L'urgence, que la feuille pose enfin.
     *
     * Elle était affichée partout — pastille, compteur du bandeau, liseré de la
     * semaine — et aucun écran ne la posait : le dépannage qui appelle à 7 h ne
     * se distinguait pas de l'entretien de l'après-midi. Elle reste séparée du
     * statut, comme dans le modèle : une urgence reste une urgence une fois
     * terminée.
     */
    val urgente: Boolean = false,
    /**
     * L'adresse du client, **pour compléter sa fiche** et non pour la réécrire.
     *
     * Ce n'est pas un champ de l'intervention : elle vit sur la fiche du client,
     * d'où l'itinéraire la lit. La feuille ne demandait que le nom et la ville,
     * si bien qu'un client inscrit depuis la tournée n'était jamais localisable
     * et que « Y aller » disparaissait sans un mot. La saisie ne remplit la fiche
     * **que là où elle est vide** — une adresse déjà relevée ne s'écrase pas
     * d'une frappe faite en passant. Voir
     * [com.frigopro.app.data.ClientRepository.completer].
     */
    val adresse: String = "",
    /** Le téléphone du client, même règle que [adresse]. */
    val telephone: String = "",
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
    val estValide: Boolean get() = manque.isEmpty()

    /**
     * Ce qui manque pour enregistrer, nommé : « le client », « la ville ».
     *
     * Un bouton grisé sans explication se lit comme une panne, et l'on cherche
     * alors le défaut ailleurs que dans le champ vide du haut de la feuille. Le
     * dire tient en une ligne, et c'est cette même liste qui décide de la
     * validité : l'écran ne peut pas annoncer un manque que la règle ne voit pas.
     */
    val manque: List<String>
        get() = buildList {
            if (client.isBlank()) add("le client")
            if (ville.isBlank()) add("la ville")
        }

    /**
     * L'intervention à enregistrer.
     *
     * En édition, le formulaire écrit **par-dessus** [origine] : tout ce qu'il
     * n'affiche pas — le temps chronométré, le numéro attribué, la signature du
     * client — traverse l'opération intact. `modifieLe` est posé par le dépôt,
     * seul juge de l'instant d'écriture.
     *
     * [adresse] et [telephone] n'y passent pas : ils vont à la fiche du client,
     * pas à l'intervention.
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
            clientFactureId = clientFactureId,
            clientFactureNom = clientFactureNom,
            equipementId = equipementId,
            equipementNom = equipementNom,
            statut = statut,
            urgente = urgente,
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
            clientFactureId = intervention.clientFactureId,
            clientFactureNom = intervention.clientFactureNom,
            equipementId = intervention.equipementId,
            equipementNom = intervention.equipementNom,
            statut = intervention.statut,
            urgente = intervention.urgente,
            notes = intervention.notes,
            dureeMin = intervention.dureeMin,
            technicienId = intervention.technicienId,
            technicienNom = intervention.technicienNom,
            origine = intervention,
        )
    }
}
