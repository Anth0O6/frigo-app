package com.frigopro.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

class ClientRepositoryTest {

    private val dao = FauxClientDao()
    private val repository = ClientRepository(dao)

    /**
     * L'assertion qui justifie le tri en Kotlin plutôt qu'en SQL : « Élise »
     * doit se ranger entre « Edouard » et « Fabien ». Un `ORDER BY nom COLLATE
     * NOCASE` la rejetterait après « Zoé », les accents passant après Z.
     */
    @Test
    fun `le carnet est trie en francais, accents replies`() = runTest {
        for (nom in listOf("Zoé", "Élise", "Edouard", "Fabien")) {
            repository.enregistrer(Client(id = nom, nom = nom, ville = "Rouen"))
        }

        val ordre = repository.clients.first().map { it.nom }

        assertEquals(listOf("Edouard", "Élise", "Fabien", "Zoé"), ordre)
    }

    @Test
    fun `un client inconnu est inscrit au carnet`() = runTest {
        val client = repository.trouverOuCreer("Boucherie Lemoine", "Rouen")

        assertEquals("Boucherie Lemoine", client.nom)
        assertEquals("Rouen", client.ville)
        assertEquals(listOf("Boucherie Lemoine"), dao.contenu.map { it.nom })
    }

    @Test
    fun `un client deja connu n'est pas duplique, meme a la casse pres`() = runTest {
        val premier = repository.trouverOuCreer("Boucherie Lemoine", "Rouen")

        val second = repository.trouverOuCreer("boucherie lemoine", "Elbeuf")

        assertEquals("le carnet doit rendre le client existant", premier.id, second.id)
        assertEquals("Rouen", second.ville)
        assertEquals(1, dao.contenu.size)
    }

    @Test
    fun `les espaces parasites de saisie sont retires`() = runTest {
        val client = repository.trouverOuCreer("  Fromagerie Hardy ", " Caudebec  ")

        assertEquals("Fromagerie Hardy", client.nom)
        assertEquals("Caudebec", client.ville)
    }

    @Test
    fun `l'adresse et le telephone sont nettoyes a l'enregistrement`() = runTest {
        val client = repository.enregistrer(
            Client(
                nom = "Fromagerie Hardy",
                ville = "Caudebec",
                adresse = "  12 rue des Halles ",
                telephone = " 02 35 00 00 00  ",
            ),
        )

        assertEquals("12 rue des Halles", client.adresse)
        assertEquals("02 35 00 00 00", client.telephone)
    }

    @Test
    fun `l'horodatage de modification est pose par le depot`() = runTest {
        val avant = Instant.now()

        repository.enregistrer(Client(nom = "Primeur Vasseur", ville = "Duclair"))

        val modifieLe = dao.contenu.single().modifieLe
        assertNotEquals(Instant.EPOCH, modifieLe)
        assertFalse("l'horodatage doit être postérieur à l'appel", modifieLe.isBefore(avant))
    }
}
