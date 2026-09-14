package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Instant
import java.util.Locale

/** Carnet de clients : ce que l'on ne veut plus retaper à chaque intervention. */
class ClientRepository(
    private val dao: ClientDao,
    /**
     * Où vivent les photos du parc. Le dépôt est le seul endroit où la base et
     * les fichiers avancent ensemble, comme [EquipementRepository] — et dans le
     * même ordre : les lignes d'abord, les fichiers ensuite.
     */
    private val stockage: RangementPhotos,
) {

    /**
     * Carnet trié alphabétiquement. Le tri passe par un [Collator] français
     * plutôt que par SQL : lui seul range « Élise » entre « Edouard » et
     * « Fabien » au lieu de la rejeter en fin de liste.
     *
     * Un `Collator` n'étant pas sûr entre fils d'exécution, il est construit à
     * chaque émission — le carnet reste assez court pour que cela ne compte pas.
     */
    val clients: Flow<List<Client>> = dao.observerTous().map { liste ->
        val collateur = Collator.getInstance(Locale.FRENCH)
        liste.sortedWith { a, b -> collateur.compare(a.nom, b.nom) }
    }

    /**
     * Le carnet en groupes : chaque donneur d'ordre, et ses sites.
     *
     * Les sites ne paraissent **pas au premier rang** : « Carrefour Part-Dieu »
     * et « Carrefour Vaise » côte à côte avec « Carrefour » feraient trois
     * entrées pour un client, et la recherche d'un nom en ramènerait autant. Ils
     * se déplient sous le leur, comme les unités sous leur groupe.
     *
     * Un site **orphelin** — dont le donneur d'ordre a disparu — remonte au
     * premier rang plutôt que de s'évanouir : mieux vaut une fiche mal rangée
     * qu'une fiche introuvable, et c'est la même règle que pour un intitulé de
     * type supprimé.
     */
    val groupes: Flow<List<GroupeClients>> = clients.map { carnet ->
        val parId = carnet.associateBy { it.id }
        val sites = carnet.filter { it.parentId != null && it.parentId in parId }
        val parParent = sites.groupBy { it.parentId }
        carnet.filterNot { it in sites }
            .map { GroupeClients(it, parParent[it.id].orEmpty()) }
    }

    /** Les sites d'un donneur d'ordre, triés comme le carnet. */
    fun sitesDe(clientId: String): Flow<List<Client>> =
        clients.map { carnet -> carnet.filter { it.parentId == clientId } }

    /**
     * Renvoie le client de ce nom, en le créant au besoin.
     *
     * C'est ainsi que le carnet se remplit : sans formulaire dédié, chaque
     * intervention saisie chez un nouveau client l'y inscrit au passage.
     */
    suspend fun trouverOuCreer(nom: String, ville: String): Client {
        val recherche = nom.trim()
        return dao.trouverParNom(recherche) ?: enregistrer(Client(nom = recherche, ville = ville))
    }

    /**
     * Le site de ce nom chez ce donneur d'ordre, ou `null`.
     *
     * Sert à refuser un doublon là où il en est un — sous le même parent — et
     * nulle part ailleurs : voir [ClientDao.trouverSite].
     */
    suspend fun trouverSite(nom: String, parentId: String): Client? =
        dao.trouverSite(nom.trim(), parentId)

    /** Crée le client ou remplace celui qui porte le même identifiant. */
    suspend fun enregistrer(client: Client): Client {
        val nettoye = client.copy(
            nom = client.nom.trim(),
            ville = client.ville.trim(),
            adresse = client.adresse.trim(),
            telephone = client.telephone.trim(),
            modifieLe = Instant.now(),
        )
        dao.enregistrer(nettoye)
        return nettoye
    }

    /**
     * Supprime un client, ses sites et son parc.
     *
     * Ce que la suppression emporte et ce qu'elle garde est décidé par
     * [ClientDao.supprimer] ; ici on ne fait que ce que SQLite ne sait pas
     * faire : effacer les **fichiers image** du parc. Ils sont relevés avant la
     * transaction, parce qu'après, plus aucune ligne ne dit lesquels étaient là.
     *
     * L'ordre est celui de tout le projet — la base d'abord, les fichiers
     * ensuite : une ligne sans fichier se voit à l'écran et se corrige, un
     * fichier sans ligne ne se voit nulle part.
     */
    suspend fun supprimer(client: Client) {
        val fichiers = (dao.sitesDe(client.id) + client)
            .flatMap { dao.photosDuParc(it.id) }
            .map { it.fichier }
        dao.supprimer(client.id)
        fichiers.forEach { stockage.supprimer(it) }
    }
}
