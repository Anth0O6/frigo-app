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
    /**
     * Le donneur d'ordre dont cette fiche est un site.
     *
     * Il **traverse le formulaire sans y être modifiable** : on crée un site
     * depuis la fiche de son donneur d'ordre, et le rattachement est acquis à ce
     * moment-là. Le porter ici n'est donc pas un champ de saisie, c'est ce qui
     * évite qu'une simple correction d'adresse détache le site de son enseigne —
     * même motif que l'`origine` d'[EtatFormulaire], qui empêche une heure
     * corrigée d'effacer le chrono.
     */
    val parentId: String? = null,
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
        parentId = parentId,
    )

    companion object {

        fun depuis(client: Client): EtatFicheClient = EtatFicheClient(
            id = client.id,
            estCreation = false,
            nom = client.nom,
            ville = client.ville,
            adresse = client.adresse,
            telephone = client.telephone,
            parentId = client.parentId,
        )
    }
}
