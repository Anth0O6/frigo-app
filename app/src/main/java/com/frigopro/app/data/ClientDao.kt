package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès SQL au carnet de clients.
 *
 * Une **classe abstraite** et non une interface, parce que supprimer un client
 * touche six tables d'un bloc : le carnet, ses sites, son parc, les photos de ce
 * parc, ses interventions, ses devis et ses factures. C'est le DAO le plus large
 * du projet, et pour la raison qui a déjà fait de [EquipementDao] et de
 * [TypeInterventionDao] des classes abstraites — seul un `@Transaction` rend
 * l'enchaînement atomique, et un client à moitié supprimé serait pire qu'un
 * client qu'on ne peut pas supprimer.
 */
@Dao
abstract class ClientDao {

    /**
     * Sans `ORDER BY` : le tri alphabétique se fait côté Kotlin, `COLLATE
     * NOCASE` de SQLite ne repliant pas les accents (« Élise » finirait après
     * « Zoé »).
     */
    @Query("SELECT * FROM clients")
    abstract fun observerTous(): Flow<List<Client>>

    /**
     * `COLLATE NOCASE` suffit ici : on cherche une correspondance exacte à la
     * casse près. Deux noms qui ne diffèrent que par un accent sont bien deux
     * clients différents.
     */
    @Query("SELECT * FROM clients WHERE nom = :nom COLLATE NOCASE LIMIT 1")
    abstract suspend fun trouverParNom(nom: String): Client?

    /**
     * Le site de ce nom chez ce donneur d'ordre.
     *
     * La recherche est **portée par le parent**, et ce n'est pas un détail :
     * deux donneurs d'ordre qui ont chacun un site « Lyon » ne sont pas une
     * confusion, c'est le cas ordinaire. C'est la même règle que pour les unités
     * de multi-split, où deux « Salon » sous deux groupes différents cohabitent.
     */
    @Query(
        "SELECT * FROM clients WHERE nom = :nom COLLATE NOCASE " +
            "AND parentId = :parentId LIMIT 1",
    )
    abstract suspend fun trouverSite(nom: String, parentId: String): Client?

    /** Tout le carnet, pour la sauvegarde. Le tri est l'affaire du dépôt. */
    @Query("SELECT * FROM clients")
    abstract suspend fun tous(): List<Client>

    @Upsert
    abstract suspend fun enregistrer(client: Client)

    /** Room enveloppe une écriture multiple dans une transaction : tout ou rien. */
    @Upsert
    abstract suspend fun enregistrerTous(clients: List<Client>)

    // — La suppression, et ce qu'elle emporte ————————————————————————————

    /** Les sites d'un donneur d'ordre : ils partent avec lui. */
    @Query("SELECT * FROM clients WHERE parentId = :id")
    abstract suspend fun sitesDe(id: String): List<Client>

    /**
     * Les photos du parc d'un client, pour savoir quels **fichiers** effacer.
     *
     * Relevées avant la transaction : après, les lignes n'existent plus et rien
     * ne dirait plus quelles images sont devenues orphelines. Elles resteraient
     * sur le téléphone sans qu'aucun écran ne puisse les montrer ni les effacer
     * — et elles repartiraient dans chaque archive de sauvegarde.
     */
    @Query("SELECT * FROM photos WHERE equipementId IN (SELECT id FROM equipements WHERE clientId = :id)")
    abstract suspend fun photosDuParc(id: String): List<Photo>

    @Query("DELETE FROM photos WHERE equipementId IN (SELECT id FROM equipements WHERE clientId = :id)")
    abstract suspend fun effacerPhotosDuParc(id: String)

    @Query("UPDATE interventions SET equipementId = NULL WHERE equipementId IN (SELECT id FROM equipements WHERE clientId = :id)")
    abstract suspend fun detacherMachinesDuParc(id: String)

    @Query("DELETE FROM equipements WHERE clientId = :id")
    abstract suspend fun effacerParc(id: String)

    /**
     * Le lien est coupé, la copie reste.
     *
     * C'est la règle que le projet applique déjà au type d'intervention et à la
     * machine, et elle compte double ici : `client` et `ville` sont sur la ligne,
     * si bien qu'une tournée de mars continue de dire chez qui l'on est allé
     * après que la fiche a disparu du carnet. Effacer ces interventions aurait
     * emporté le temps chronométré, les relevés et la signature du client — ce
     * qui s'est passé sur place, et qui ne se retrouve pas.
     */
    @Query("UPDATE interventions SET clientId = NULL WHERE clientId = :id")
    abstract suspend fun detacherInterventions(id: String)

    /** Le donneur d'ordre d'une sous-traitance disparaît : `clientFactureNom` reste. */
    @Query("UPDATE interventions SET clientFactureId = NULL WHERE clientFactureId = :id")
    abstract suspend fun detacherSousTraitance(id: String)

    @Query("UPDATE devis SET clientId = NULL WHERE clientId = :id")
    abstract suspend fun detacherDevis(id: String)

    /**
     * Une facture ne perd que son lien.
     *
     * Jamais sa ligne : c'est un document comptable, que l'entreprise doit
     * pouvoir représenter pendant dix ans, et son numéro tient une séquence
     * continue qu'un trou trahirait. Tout ce qu'elle imprime — le nom du client,
     * son adresse, le taux de TVA — est déjà recopié sur elle, si bien que le
     * lien coupé ne lui retire rien de ce qui est parti chez le client.
     */
    @Query("UPDATE factures SET clientId = NULL WHERE clientId = :id")
    abstract suspend fun detacherFactures(id: String)

    @Query("DELETE FROM clients WHERE id = :id")
    abstract suspend fun effacer(id: String)

    /**
     * Supprime un client, ses sites, et son parc — d'un bloc.
     *
     * Deux traitements opposés cohabitent ici, et c'est tout le sujet :
     *
     * - **Ce qui n'existe que par le client est effacé** : ses machines, leurs
     *   photos, et ses sites. Une machine sans client ne se rattache à rien et
     *   ne remonterait sur aucun écran ; un site est l'adresse d'un donneur
     *   d'ordre et n'a pas de sens sans lui.
     * - **Ce qui raconte ce qui s'est passé est gardé, lien coupé** : les
     *   interventions, les devis, les factures. Chacun porte une copie du nom,
     *   et la copie est précisément ce qui leur permet de survivre — même couple
     *   lien / copie que pour le type d'intervention et la machine.
     *
     * Les sites sont traités **avant** le client et par le même chemin, si bien
     * qu'un site emporte son propre parc et détache ses propres interventions.
     * La récursion s'arrête là : le carnet est un arbre à un seul niveau, et un
     * site n'a pas de site.
     *
     * Les fichiers image ne sont pas du ressort de SQLite : c'est
     * [ClientRepository] qui les efface, après la transaction.
     */
    @Transaction
    open suspend fun supprimer(id: String) {
        sitesDe(id).forEach { site -> supprimerSeul(site.id) }
        supprimerSeul(id)
    }

    /** Un client et ce qui pend de lui, sans regarder ses sites. */
    private suspend fun supprimerSeul(id: String) {
        detacherMachinesDuParc(id)
        effacerPhotosDuParc(id)
        effacerParc(id)
        detacherInterventions(id)
        detacherSousTraitance(id)
        detacherDevis(id)
        detacherFactures(id)
        effacer(id)
    }
}
