package com.frigopro.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class InterventionRepositoryTest {

    private val dao = FauxInterventionDao()
    private val repository = InterventionRepository(dao)

    private val lundi = LocalDate.of(2026, 3, 9)
    private val mardi = lundi.plusDays(1)

    @Test
    fun `les espaces parasites de saisie sont retires avant enregistrement`() = runTest {
        repository.enregistrer(
            intervention(client = "  Boucherie Lemoine ", ville = "\tRouen  "),
        )

        val enregistree = dao.contenu.single()
        assertEquals("Boucherie Lemoine", enregistree.client)
        assertEquals("Rouen", enregistree.ville)
    }

    @Test
    fun `l'horodatage de modification est pose par le depot`() = runTest {
        val avant = Instant.now()

        repository.enregistrer(intervention())

        val modifieLe = dao.contenu.single().modifieLe
        assertNotEquals("le dépôt doit écraser la valeur par défaut", Instant.EPOCH, modifieLe)
        assertTrue("l'horodatage doit être postérieur à l'appel", !modifieLe.isBefore(avant))
    }

    @Test
    fun `enregistrer deux fois le meme identifiant remplace la ligne`() = runTest {
        val initiale = intervention(client = "Boucherie Lemoine")
        repository.enregistrer(initiale)
        repository.enregistrer(initiale.copy(client = "Boucherie Lemoine et fils"))

        assertEquals(1, dao.contenu.size)
        assertEquals("Boucherie Lemoine et fils", dao.contenu.single().client)
    }

    @Test
    fun `une journee ne montre que ses interventions, triees par heure`() = runTest {
        repository.enregistrer(intervention(id = "a", heure = LocalTime.of(14, 0), client = "Après-midi"))
        repository.enregistrer(intervention(id = "b", heure = LocalTime.of(8, 30), client = "Matin"))
        repository.enregistrer(intervention(id = "c", date = mardi, client = "Le lendemain"))

        val journee = repository.observerJournee(lundi).first()

        assertEquals(listOf("Matin", "Après-midi"), journee.map { it.client })
    }

    @Test
    fun `supprimer retire la ligne et laisse les autres`() = runTest {
        repository.enregistrer(intervention(id = "a"))
        repository.enregistrer(intervention(id = "b"))

        repository.supprimer("a")

        assertEquals(listOf("b"), dao.contenu.map { it.id })
    }

    private fun intervention(
        id: String = "id-1",
        date: LocalDate = lundi,
        heure: LocalTime = LocalTime.of(9, 0),
        client: String = "Client",
        ville: String = "Ville",
    ) = Intervention(
        id = id,
        date = date,
        heure = heure,
        client = client,
        ville = ville,
        typePanne = TypePanne.COMPRESSEUR,
    )
}
