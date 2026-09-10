package com.frigopro.app.ui

import com.frigopro.app.data.Client
import java.util.UUID

/**
 * Contenu éditable de la fiche d'un client.
 *
 * Comme [EtatFormulaire], [id] distingue une création d'une édition. L'identifiant
 * d'une création est tiré à la construction et non à l'enregistrement : sans
 * cela, deux validations du même formulaire créeraient deux clients.
 */
data class EtatFicheClient(
    val id: String = UUID.randomUUID().toString(),
    val estCreation: Boolean = true,
    val nom: String = "",
    val ville: String = "",
    val adresse: String = "",
    val telephone: String = "",
) {

    /** Un client sans nom ni ville ne se retrouverait pas dans le carnet. */
    val estValide: Boolean get() = nom.isNotBlank() && ville.isNotBlank()

    /** `modifieLe` est posé par le dépôt, seul juge de l'instant d'écriture. */
    fun versClient(): Client = Client(
        id = id,
        nom = nom,
        ville = ville,
        adresse = adresse,
        telephone = telephone,
    )

    companion object {

        fun depuis(client: Client): EtatFicheClient = EtatFicheClient(
            id = client.id,
            estCreation = false,
            nom = client.nom,
            ville = client.ville,
            adresse = client.adresse,
            telephone = client.telephone,
        )
    }
}
