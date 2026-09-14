package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Un client du carnet, réutilisable d'une intervention à l'autre.
 *
 * [adresse] et [telephone] peuvent rester vides : le carnet se remplit tout
 * seul à partir des interventions, où seuls le nom et la ville sont demandés.
 * Une chaîne vide signifie donc « pas encore renseigné », et c'est le cas
 * normal — non un défaut de saisie.
 *
 * ## Un client, plusieurs sites
 *
 * Une enseigne a plusieurs magasins, un syndic plusieurs immeubles, et chacun a
 * son adresse et son parc de machines. [parentId] les rattache, et c'est
 * **exactement le motif des unités de multi-split** : le lien est sur la même
 * table plutôt que sur une table `sites` à part, parce qu'un site est un client
 * — il se nomme, il a une adresse, un téléphone, un parc, un historique — et
 * qu'une table à part aurait doublé les écrans qui le montrent.
 *
 * Comme pour les machines, la hiérarchie n'a **qu'un seul niveau**, et c'est une
 * décision de l'écran et non une limite du modèle. Une enseigne, ses magasins :
 * un magasin n'a pas de sous-magasin.
 *
 * Les machines n'ont rien eu à changer : elles pendent d'un `clientId`, et le
 * parc d'un site est donc le parc de la ligne du site.
 *
 * @param modifieLe voir [Intervention.modifieLe] : même rôle, même usage futur.
 */
@Entity(
    tableName = "clients",
    indices = [Index("parentId")],
)
data class Client(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nom: String,
    val ville: String,
    val adresse: String = "",
    val telephone: String = "",
    /** Le donneur d'ordre dont ce client est un site, `null` s'il n'en est pas un. */
    val parentId: String? = null,
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Un site rattaché à un autre client, et non un client de plein droit. */
    val estSite: Boolean get() = parentId != null

    /** Un numéro à appeler. */
    val appelable: Boolean get() = telephone.isNotBlank()

    /**
     * Une destination à ouvrir dans une application de cartographie. La ville
     * seule ne suffit pas : elle mènerait au centre-ville, pas chez le client.
     */
    val localisable: Boolean get() = adresse.isNotBlank()

    /** Adresse complète, telle qu'on la dicterait à quelqu'un. */
    val adresseComplete: String get() = listOf(adresse, ville).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Un donneur d'ordre et ses sites.
 *
 * Réuni par le dépôt plutôt que stocké, et **le compte de sites est dérivé** :
 * même raison que pour [GroupeMachines], un champ `nombreSites` aurait dérivé au
 * premier site arrivé autrement que par l'écran qui l'incrémente — une
 * restauration de sauvegarde, par exemple.
 */
data class GroupeClients(
    val donneur: Client,
    val sites: List<Client> = emptyList(),
) {

    val nombreSites: Int get() = sites.size

    /** Le donneur d'ordre et ses sites, dans l'ordre où ils se lisent. */
    val tous: List<Client> get() = listOf(donneur) + sites
}
