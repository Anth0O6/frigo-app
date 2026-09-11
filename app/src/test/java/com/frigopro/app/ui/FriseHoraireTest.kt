package com.frigopro.app.ui

import com.frigopro.app.data.Intervention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * L'arithmétique de la frise horaire.
 *
 * Elle est isolée du dessin à dessein : une intervention mal placée sur le
 * planning est une erreur de calcul, et un test sans Compose la trouve là où un
 * coup d'œil à l'écran ne dirait que « ça a l'air à peu près bon ».
 */
class FriseHoraireTest {

    @Test
    fun `une journee ordinaire garde l'amplitude par defaut`() {
        val amplitude = amplitudeDe(
            listOf(
                intervention(LocalTime.of(8, 0), dureeMin = 90),
                intervention(LocalTime.of(14, 0), dureeMin = 60),
            ),
        )

        assertEquals(7, amplitude.premiereHeure)
        assertEquals(18, amplitude.derniereHeure)
    }

    /**
     * Le cas qui justifie une amplitude variable : une astreinte de nuit sortirait
     * d'une frise figée de 7 h à 18 h, et une intervention invisible sur le
     * planning est pire qu'un planning un peu plus long.
     */
    @Test
    fun `une astreinte matinale elargit la frise vers le haut`() {
        val amplitude = amplitudeDe(listOf(intervention(LocalTime.of(5, 30), dureeMin = 60)))

        assertEquals("la frise doit descendre jusqu'à l'heure d'arrivée", 5, amplitude.premiereHeure)
        assertEquals("sans raccourcir la journée ordinaire", 18, amplitude.derniereHeure)
    }

    @Test
    fun `un chantier qui finit tard elargit la frise vers le bas`() {
        val amplitude = amplitudeDe(listOf(intervention(LocalTime.of(17, 0), dureeMin = 210)))

        assertEquals(7, amplitude.premiereHeure)
        assertEquals("20:30 doit tenir, donc la frise va jusqu'à 21 h", 21, amplitude.derniereHeure)
    }

    @Test
    fun `une intervention qui mord sur minuit s'arrete a la journee`() {
        val amplitude = amplitudeDe(listOf(intervention(LocalTime.of(22, 0), dureeMin = 240)))

        assertEquals("minuit appartient au lendemain", 23, amplitude.derniereHeure)
    }

    @Test
    fun `une journee vide montre quand meme une journee`() {
        val amplitude = amplitudeDe(emptyList())

        assertEquals(7, amplitude.premiereHeure)
        assertEquals(18, amplitude.derniereHeure)
        assertEquals(12, amplitude.heures)
    }

    @Test
    fun `un creneau est place a son heure et dure sa duree`() {
        val amplitude = amplitudeDe(listOf(intervention(LocalTime.of(9, 30), dureeMin = 90)))
        val creneau = creneauxDe(
            listOf(LigneTournee(intervention(LocalTime.of(9, 30), dureeMin = 90), null)),
            amplitude,
        ).single()

        // 9 h 30 est à deux heures et demie du début (7 h), soit 2,5 × 64 dp.
        assertEquals(160f, creneau.haut.value, 0.01f)
        assertEquals("une heure et demie, soit 1,5 × 64 dp", 96f, creneau.hauteur.value, 0.01f)
    }

    /**
     * Un créneau très court reste lisible : sous une certaine hauteur, le client
     * et le type d'intervention ne tiennent plus, et une carte illisible ne sert
     * à rien — même si elle est à la bonne échelle.
     */
    @Test
    fun `un creneau tres court garde une hauteur minimale`() {
        val amplitude = amplitudeDe(listOf(intervention(LocalTime.of(8, 0), dureeMin = 10)))
        val creneau = creneauxDe(
            listOf(LigneTournee(intervention(LocalTime.of(8, 0), dureeMin = 10), null)),
            amplitude,
        ).single()

        assertTrue("dix minutes à l'échelle feraient 10 dp", creneau.hauteur.value >= 40f)
    }

    @Test
    fun `deux interventions qui se chevauchent se chevauchent a l'ecran`() {
        val premiere = intervention(LocalTime.of(9, 0), dureeMin = 120)
        val seconde = intervention(LocalTime.of(10, 0), dureeMin = 60)
        val amplitude = amplitudeDe(listOf(premiere, seconde))
        val creneaux = creneauxDe(
            listOf(LigneTournee(premiere, null), LigneTournee(seconde, null)),
            amplitude,
        )

        val basDeLaPremiere = creneaux[0].haut.value + creneaux[0].hauteur.value
        assertTrue(
            "un chevauchement est une erreur de planification : il doit se voir",
            creneaux[1].haut.value < basDeLaPremiere,
        )
    }

    @Test
    fun `le creneau s'annonce par ses deux bornes`() {
        assertEquals("08:00 – 09:30", creneauDe(intervention(LocalTime.of(8, 0), dureeMin = 90)))
    }

    /**
     * L'heure affichée repasse par zéro, et c'est juste : une astreinte qui
     * finit à 2 h du matin finit bien à 2 h. Ce qui ne devait pas repasser par
     * zéro, c'est le **calcul de l'amplitude** — il le faisait, et la frise
     * s'arrêtait avant d'avoir dessiné le créneau.
     */
    @Test
    fun `un creneau de nuit s'annonce a l'heure du lendemain`() {
        assertEquals("22:00 – 02:00", creneauDe(intervention(LocalTime.of(22, 0), dureeMin = 240)))
    }

    private fun intervention(heure: LocalTime, dureeMin: Int) = Intervention(
        date = LocalDate.of(2026, 9, 11),
        heure = heure,
        client = "Boulangerie Martin",
        ville = "Lyon",
        dureeMin = dureeMin,
    )
}
