package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Instant
import java.util.Locale

/** Carnet de clients : ce que l'on ne veut plus retaper à chaque intervention. */
class ClientRepository(private val dao: ClientDao) {

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
     * Renvoie le client de ce nom, en le créant au besoin.
     *
     * C'est ainsi que le carnet se remplit : sans formulaire dédié, chaque
     * intervention saisie chez un nouveau client l'y inscrit au passage.
     */
    suspend fun trouverOuCreer(nom: String, ville: String): Client {
        val recherche = nom.trim()
        return dao.trouverParNom(recherche) ?: enregistrer(Client(nom = recherche, ville = ville))
    }

    /** Crée le client ou remplace celui qui porte le même identifiant. */
    suspend fun enregistrer(client: Client): Client {
        val nettoye = client.copy(
            nom = client.nom.trim(),
            ville = client.ville.trim(),
            modifieLe = Instant.now(),
        )
        dao.enregistrer(nettoye)
        return nettoye
    }
}
