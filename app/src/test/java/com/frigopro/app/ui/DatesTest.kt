package com.frigopro.app.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class DatesTest {

    private val fuseauInitial: TimeZone = TimeZone.getDefault()

    @After
    fun restaurerFuseau() {
        TimeZone.setDefault(fuseauInitial)
    }

    /**
     * Le piège classique du sélecteur Material 3 : il raisonne en UTC, et
     * convertir dans le fuseau local décale la date d'un jour selon l'heure
     * qu'il est. Ce test échouera si quelqu'un remplace `ZoneOffset.UTC` par
     * `ZoneId.systemDefault()`.
     */
    @Test
    fun `une date traverse le selecteur sans deriver, quel que soit le fuseau`() {
        val date = LocalDate.of(2026, 3, 9)

        for (fuseau in listOf("Pacific/Kiritimati", "Pacific/Niue", "Europe/Paris", "UTC")) {
            TimeZone.setDefault(TimeZone.getTimeZone(fuseau))

            assertEquals(fuseau, date, date.versMillisUtc().versLocalDate())
        }
    }

    @Test
    fun `les millisecondes produites tombent bien a minuit UTC`() {
        val millis = LocalDate.of(1970, 1, 2).versMillisUtc()

        assertEquals(86_400_000L, millis)
    }

    @Test
    fun `les trois jours autour d'aujourd'hui sont nommes plutot que dates`() {
        val aujourdhui = LocalDate.of(2026, 3, 9)

        assertEquals("Aujourd'hui", titreJour(aujourdhui, aujourdhui))
        assertEquals("Hier", titreJour(aujourdhui.minusDays(1), aujourdhui))
        assertEquals("Demain", titreJour(aujourdhui.plusDays(1), aujourdhui))
    }

    @Test
    fun `les autres jours sont dates en francais, majuscule initiale`() {
        val aujourdhui = LocalDate.of(2026, 3, 9)

        val titre = titreJour(LocalDate.of(2026, 3, 12), aujourdhui)

        assertTrue(titre, titre.contains("12"))
        assertTrue(titre, titre.contains("mars"))
        assertTrue("la première lettre doit être une majuscule : $titre", titre.first().isUpperCase())
    }
}
