package com.frigopro.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /**
     * Les sites se rangent sous leur donneur d'ordre, et n'encombrent pas le
     * premier rang du carnet.
     *
     * « Carrefour », « Carrefour Part-Dieu » et « Carrefour Vaise » côte à côte
     * feraient trois entrées pour un client, et la recherche d'un nom en
     * ramènerait autant.
     */
    @Test
    fun `les sites se rangent sous leur donneur d'ordre`() = runTest {
        val enseigne = repository.enregistrer(Client(id = "cl-1", nom = "Carrefour", ville = "Lyon"))
        repository.enregistrer(
            Client(id = "cl-2", nom = "Part-Dieu", ville = "Lyon", parentId = enseigne.id),
        )
        repository.enregistrer(
            Client(id = "cl-3", nom = "Vaise", ville = "Lyon", parentId = enseigne.id),
        )
        repository.enregistrer(Client(id = "cl-4", nom = "Boucherie Morel", ville = "Lyon"))

        val groupes = repository.groupes.first()

        assertEquals(
            "seuls les donneurs d'ordre paraissent au premier rang",
            listOf("Boucherie Morel", "Carrefour"),
            groupes.map { it.donneur.nom },
        )
        val carrefour = groupes.single { it.donneur.nom == "Carrefour" }
        assertEquals(2, carrefour.nombreSites)
        assertEquals(listOf("Part-Dieu", "Vaise"), carrefour.sites.map { it.nom })
    }

    /**
     * Un site dont le donneur d'ordre a disparu remonte au premier rang.
     *
     * Mieux vaut une fiche mal rangée qu'une fiche introuvable : c'est la même
     * règle que l'intitulé d'un type supprimé, qui reste sur les tournées
     * passées.
     */
    @Test
    fun `un site orphelin ne disparait pas du carnet`() = runTest {
        repository.enregistrer(
            Client(id = "cl-2", nom = "Part-Dieu", ville = "Lyon", parentId = "cl-disparu"),
        )

        val groupes = repository.groupes.first()

        assertEquals(listOf("Part-Dieu"), groupes.map { it.donneur.nom })
    }

    /**
     * Deux donneurs d'ordre peuvent avoir chacun un site du même nom.
     *
     * Ce n'est pas une confusion, c'est le cas ordinaire : deux syndics ont
     * chacun un immeuble « Les Tilleuls ». Même règle que pour deux unités
     * « Salon » sous deux multi-splits différents.
     */
    @Test
    fun `le doublon d'un site se juge sous son parent`() = runTest {
        repository.enregistrer(Client(id = "cl-1", nom = "Syndic A", ville = "Lyon"))
        repository.enregistrer(Client(id = "cl-2", nom = "Syndic B", ville = "Lyon"))
        repository.enregistrer(
            Client(id = "s-1", nom = "Les Tilleuls", ville = "Lyon", parentId = "cl-1"),
        )

        assertNotNull(repository.trouverSite("les tilleuls", "cl-1"))
        assertNull(
            "le même nom sous un autre donneur d'ordre n'est pas un doublon",
            repository.trouverSite("Les Tilleuls", "cl-2"),
        )
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
