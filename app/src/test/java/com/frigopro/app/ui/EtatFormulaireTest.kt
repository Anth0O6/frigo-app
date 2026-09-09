package com.frigopro.app.ui

import com.frigopro.app.data.Intervention
import com.frigopro.app.data.TypePanne
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
            typePanne = TypePanne.GIVRAGE,
        )

        val etat = EtatFormulaire.depuis(intervention)

        assertEquals(intervention, etat.versIntervention().copy(modifieLe = intervention.modifieLe))
    }
}
