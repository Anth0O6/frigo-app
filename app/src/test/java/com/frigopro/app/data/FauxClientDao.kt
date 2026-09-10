package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Carnet en mémoire, reproduisant le contrat SQL du vrai DAO. */
class FauxClientDao : ClientDao {

    private val lignes = MutableStateFlow<List<Client>>(emptyList())

    /** Contenu courant, pour les assertions. */
    val contenu: List<Client> get() = lignes.value

    override fun observerTous(): Flow<List<Client>> = lignes

    /** `COLLATE NOCASE` du vrai DAO : correspondance exacte à la casse près. */
    override suspend fun trouverParNom(nom: String): Client? =
        lignes.value.firstOrNull { it.nom.equals(nom, ignoreCase = true) }

    override suspend fun tous(): List<Client> = lignes.value

    override suspend fun enregistrer(client: Client) {
        lignes.update { liste -> liste.filterNot { it.id == client.id } + client }
    }

    override suspend fun enregistrerTous(clients: List<Client>) {
        val identifiants = clients.map { it.id }.toSet()
        lignes.update { liste -> liste.filterNot { it.id in identifiants } + clients }
    }
}
