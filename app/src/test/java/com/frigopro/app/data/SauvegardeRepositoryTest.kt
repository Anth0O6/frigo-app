package com.frigopro.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * La sauvegarde est le seul filet contre un téléphone perdu : ce qui en sort
 * doit y rentrer à l'identique, et un fichier douteux ne doit rien abîmer.
 */
class SauvegardeRepositoryTest {

    private val daoInterventions = FauxInterventionDao()
    private val daoClients = FauxClientDao()
    private val repository = SauvegardeRepository(daoInterventions, daoClients)

    @Test
    fun `ce qui est exporte revient identique`() = runTest {
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)

        val export = repository.exporter()
        val vierge = SauvegardeRepository(FauxInterventionDao(), FauxClientDao())
        val resultat = vierge.restaurer(export.contenu)

        assertEquals(ResultatRestauration.Reussie(clients = 1, interventions = 1), resultat)
    }

    /**
     * L'horodatage ne doit pas être réécrit à la restauration : c'est lui qui
     * départagera un jour deux versions concurrentes d'une même ligne.
     */
    @Test
    fun `la restauration conserve les valeurs, horodatage compris`() = runTest {
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)
        val contenu = repository.exporter().contenu

        val autreInterventions = FauxInterventionDao()
        val autreClients = FauxClientDao()
        SauvegardeRepository(autreInterventions, autreClients).restaurer(contenu)

        assertEquals(CLIENT, autreClients.contenu.single())
        assertEquals(INTERVENTION, autreInterventions.contenu.single())
    }

    /** Restaurer deux fois la même sauvegarde ne doit pas tout dédoubler. */
    @Test
    fun `restaurer deux fois ne cree pas de doublon`() = runTest {
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)
        val contenu = repository.exporter().contenu

        repository.restaurer(contenu)
        repository.restaurer(contenu)

        assertEquals(1, daoClients.contenu.size)
        assertEquals(1, daoInterventions.contenu.size)
    }

    /** Une restauration fusionne : elle n'efface pas ce qu'elle ne contient pas. */
    @Test
    fun `la restauration n'efface pas les lignes absentes du fichier`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val contenu = repository.exporter().contenu
        val ailleurs = INTERVENTION.copy(id = "id-2", client = "Saisie depuis")
        daoInterventions.enregistrer(ailleurs)

        repository.restaurer(contenu)

        assertEquals(setOf("id-1", "id-2"), daoInterventions.contenu.map { it.id }.toSet())
    }

    @Test
    fun `un fichier qui n'est pas du JSON est refuse`() = runTest {
        val resultat = repository.restaurer("ceci n'est pas une sauvegarde")

        assertEquals(ResultatRestauration.Illisible, resultat)
        assertTrue(daoInterventions.contenu.isEmpty())
    }

    @Test
    fun `un fichier ecrit par une version plus recente est refuse`() = runTest {
        val contenu = """{"format":${FORMAT_COURANT + 1},"exporteeLe":"2026-09-10T10:00:00Z"}"""

        val resultat = repository.restaurer(contenu)

        assertEquals(ResultatRestauration.TropRecente(FORMAT_COURANT + 1), resultat)
    }

    /**
     * Un type de panne inconnu fait rejeter le fichier **en entier** : une
     * tournée restaurée à moitié serait pire qu'une restauration refusée.
     */
    @Test
    fun `une valeur inconnue fait refuser tout le fichier`() = runTest {
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)
        val contenu = repository.exporter().contenu.replace("FUITE_FLUIDE", "PANNE_INVENTEE")

        val vierge = FauxInterventionDao()
        val viergeClients = FauxClientDao()
        val resultat = SauvegardeRepository(vierge, viergeClients).restaurer(contenu)

        assertEquals(ResultatRestauration.Illisible, resultat)
        assertTrue("rien ne doit être écrit avant la vérification", viergeClients.contenu.isEmpty())
        assertTrue(vierge.contenu.isEmpty())
    }

    @Test
    fun `l'export annonce ce qu'il contient`() = runTest {
        daoClients.enregistrer(CLIENT)
        daoInterventions.enregistrer(INTERVENTION)
        daoInterventions.enregistrer(INTERVENTION.copy(id = "id-2"))

        val export = repository.exporter()

        assertEquals(1, export.clients)
        assertEquals(2, export.interventions)
    }

    private companion object {

        val CLIENT = Client(
            id = "cl-1",
            nom = "Boucherie Lemoine",
            ville = "Rouen",
            adresse = "12 rue des Carmes",
            telephone = "02 35 00 00 00",
            modifieLe = Instant.ofEpochMilli(1_757_500_000_000),
        )

        val INTERVENTION = Intervention(
            id = "id-1",
            date = LocalDate.of(2026, 9, 10),
            heure = LocalTime.of(8, 30),
            client = "Boucherie Lemoine",
            ville = "Rouen",
            typePanne = TypePanne.FUITE_FLUIDE,
            clientId = "cl-1",
            statut = StatutIntervention.EN_COURS,
            notes = "Fuite au détendeur.",
            modifieLe = Instant.ofEpochMilli(1_757_500_000_000),
        )
    }
}
