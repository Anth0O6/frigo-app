package com.frigopro.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TypeInterventionRepositoryTest {

    private val daoInterventions = FauxInterventionDao()
    private val dao = FauxTypeInterventionDao(daoInterventions)
    private val repository = TypeInterventionRepository(dao)

    @Test
    fun `la liste demarre vide`() = runTest {
        assertEquals(emptyList<TypeIntervention>(), repository.types.first())
    }

    /** Même raison que pour le carnet : « Étanchéité » n'est pas après « Zinguerie ». */
    @Test
    fun `la liste est triee en francais, accents replies`() = runTest {
        for (libelle in listOf("Zinguerie", "Étanchéité", "Entretien", "Fuite")) {
            repository.enregistrer(TypeIntervention(id = libelle, libelle = libelle))
        }

        val ordre = repository.types.first().map { it.libelle }

        assertEquals(listOf("Entretien", "Étanchéité", "Fuite", "Zinguerie"), ordre)
    }

    @Test
    fun `un type inconnu est cree, un type connu est rendu tel quel`() = runTest {
        val premier = repository.trouverOuCreer("  Entretien annuel ")

        assertEquals("Entretien annuel", premier.libelle)

        val second = repository.trouverOuCreer("entretien annuel")

        assertEquals("un type ne doit pas se dédoubler à la casse près", premier.id, second.id)
        assertEquals(1, dao.contenu.size)
    }

    @Test
    fun `l'horodatage de modification est pose par le depot`() = runTest {
        repository.enregistrer(TypeIntervention(libelle = "Mise en service"))

        assertNotEquals(Instant.EPOCH, dao.contenu.single().modifieLe)
    }

    /**
     * Le cœur de la demande : corriger un intitulé doit le corriger partout,
     * interventions déjà saisies comprises.
     */
    @Test
    fun `renommer un type met a jour les interventions qui le designent`() = runTest {
        val type = repository.trouverOuCreer("Entretien anuel")
        daoInterventions.enregistrer(intervention("i1", type.id, type.libelle))
        daoInterventions.enregistrer(intervention("i2", null, "Fuite de fluide"))

        repository.enregistrer(type.copy(libelle = "Entretien annuel"))

        val parId = daoInterventions.contenu.associateBy { it.id }
        assertEquals("Entretien annuel", parId.getValue("i1").typeLibelle)
        assertEquals("celle qui ne le désigne pas ne bouge pas", "Fuite de fluide", parId.getValue("i2").typeLibelle)
    }

    /**
     * Ranger sa liste ne doit rien effacer d'une tournée passée : le lien
     * disparaît, l'intitulé reste.
     */
    @Test
    fun `supprimer un type laisse l'intitule sur les interventions`() = runTest {
        val type = repository.trouverOuCreer("Dépannage")
        daoInterventions.enregistrer(intervention("i1", type.id, type.libelle))

        repository.supprimer(type.id)

        assertEquals(emptyList<TypeIntervention>(), dao.contenu)
        val restee = daoInterventions.contenu.single()
        assertEquals("Dépannage", restee.typeLibelle)
        assertNull("le lien doit être coupé, pas pendant", restee.typeId)
    }

    @Test
    fun `un intitule vide n'est pas un type`() = runTest {
        val type = repository.enregistrer(TypeIntervention(libelle = "  Dépannage  "))

        assertEquals("Dépannage", type.libelle)
        assertFalse(type.libelle.isBlank())
    }

    private fun intervention(id: String, typeId: String?, typeLibelle: String) = Intervention(
        id = id,
        date = LocalDate.of(2026, 9, 10),
        heure = LocalTime.of(9, 0),
        client = "Client",
        ville = "Rouen",
        typeId = typeId,
        typeLibelle = typeLibelle,
    )
}
