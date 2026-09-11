package com.frigopro.app.ui

import com.frigopro.app.data.Chrono
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutIntervention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class EtatFormulaireTest {

    @Test
    fun `une saisie sans client ou sans ville n'est pas valide`() {
        assertFalse(EtatFormulaire().estValide)
        assertFalse(EtatFormulaire(client = "Boucherie Lemoine").estValide)
        assertFalse(EtatFormulaire(ville = "Rouen").estValide)
        assertFalse(
            "des espaces ne renseignent rien",
            EtatFormulaire(client = "  ", ville = "  ").estValide,
        )
        assertTrue(EtatFormulaire(client = "Boucherie Lemoine", ville = "Rouen").estValide)
    }

    @Test
    fun `un formulaire sans identifiant est une creation`() {
        assertTrue(EtatFormulaire().estCreation)
        assertFalse(EtatFormulaire(id = "id-1").estCreation)
    }

    @Test
    fun `une creation recoit un identifiant neuf a chaque fois`() {
        val premier = EtatFormulaire(client = "A", ville = "B").versIntervention().id
        val second = EtatFormulaire(client = "A", ville = "B").versIntervention().id

        assertNotEquals("deux créations ne doivent pas partager d'identifiant", premier, second)
        assertTrue(premier.isNotBlank())
    }

    @Test
    fun `une edition conserve l'identifiant de la ligne`() {
        val etat = EtatFormulaire(id = "id-1", client = "A", ville = "B")

        assertEquals("id-1", etat.versIntervention().id)
    }

    @Test
    fun `ouvrir une intervention en edition en reprend tous les champs`() {
        val intervention = Intervention(
            id = "id-1",
            date = LocalDate.of(2026, 3, 9),
            heure = LocalTime.of(10, 30),
            client = "Boucherie Lemoine",
            ville = "Rouen",
            typeId = "t1",
            typeLibelle = "Givrage",
        )

        val etat = EtatFormulaire.depuis(intervention)

        assertEquals(intervention, etat.versIntervention().copy(modifieLe = intervention.modifieLe))
    }

    /**
     * Le piège que ce test garde fermé : le formulaire n'affiche qu'une partie
     * d'une intervention — le reste s'est passé sur place. S'il reconstruisait
     * la ligne à neuf, corriger l'heure d'une intervention déjà faite remettrait
     * son chronomètre à zéro, effacerait son numéro et la signature du client.
     * Et on ne s'en apercevrait qu'en rouvrant le compte-rendu, c'est-à-dire
     * trop tard.
     */
    @Test
    fun `editer une intervention ne touche pas a ce qui s'est passe sur place`() {
        val faite = Intervention(
            date = LocalDate.of(2026, 9, 10),
            heure = LocalTime.of(8, 30),
            client = "Boucherie Lemoine",
            ville = "Rouen",
            statut = StatutIntervention.TERMINEE,
            urgente = true,
            numero = "INT-2609-012",
            signatureFichier = "signature.png",
            signeeLe = Instant.ofEpochMilli(1_700_000_000_000),
            chrono = Chrono(cumuleS = 5_400),
        )

        // On ne corrige qu'une heure mal saisie.
        val corrigee = EtatFormulaire.depuis(faite)
            .copy(heure = LocalTime.of(9, 0))
            .versIntervention()

        assertEquals(LocalTime.of(9, 0), corrigee.heure)
        assertEquals("le temps chronométré survit", 5_400L, corrigee.chrono.cumuleS)
        assertEquals("le numéro attribué survit", "INT-2609-012", corrigee.numero)
        assertEquals("la signature du client survit", "signature.png", corrigee.signatureFichier)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), corrigee.signeeLe)
        assertTrue("l'urgence survit", corrigee.urgente)
        assertEquals("et c'est bien la même ligne", faite.id, corrigee.id)
    }

    /**
     * En création, il n'y a rien à préserver : la ligne naît du formulaire, avec
     * un identifiant neuf.
     */
    @Test
    fun `une creation part de rien`() {
        val creee = EtatFormulaire(client = "Primeur Vasseur", ville = "Elbeuf").versIntervention()

        assertTrue(creee.id.isNotBlank())
        assertEquals("aucun temps chronométré", 0L, creee.chrono.cumuleS)
        assertEquals("", creee.numero)
        assertFalse(creee.urgente)
    }

    @Test
    fun `la duree et le technicien traversent l'edition`() {
        val confiee = Intervention(
            date = LocalDate.of(2026, 9, 10),
            heure = LocalTime.of(8, 30),
            client = "Carrefour City",
            ville = "Lyon",
            dureeMin = 90,
            technicienId = "tech-1",
            technicienNom = "Karim Benali",
        )

        val relue = EtatFormulaire.depuis(confiee)

        assertEquals(90, relue.dureeMin)
        assertEquals("tech-1", relue.technicienId)
        assertEquals("Karim Benali", relue.technicienNom)
        assertEquals(confiee, relue.versIntervention())
    }
}
