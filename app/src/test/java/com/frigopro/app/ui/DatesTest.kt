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

/**
 * Le montant abrégé des tuiles.
 *
 * Sur trois tuiles côte à côte, « 12 400,00 € » est tronqué, et un montant
 * tronqué ne dit rien — ou dit autre chose. La forme longue reste celle des
 * lignes et des totaux, où elle a la place et doit être exacte.
 */
class NombresTest {

    @Test
    fun `un petit montant garde ses unites`() {
        assertEquals("450 €", Nombres.enEurosCourt(450.0))
        assertEquals("les centimes ne tiennent pas sur une tuile", "450 €", Nombres.enEurosCourt(450.40))
        assertEquals("9 800 €", Nombres.enEurosCourt(9_800.0))
    }

    @Test
    fun `au-dela de dix mille on abrege en milliers`() {
        assertEquals("12,4 k€", Nombres.enEurosCourt(12_400.0))
        assertEquals("une decimale suffit", "12,5 k€", Nombres.enEurosCourt(12_460.0))
        assertEquals("un compte rond ne porte pas de decimale", "50 k€", Nombres.enEurosCourt(50_000.0))
    }

    @Test
    fun `le million a son abreviation`() {
        assertEquals("1,2 M€", Nombres.enEurosCourt(1_200_000.0))
    }

    @Test
    fun `rien vaut zero et non une case vide`() {
        assertEquals("0 €", Nombres.enEurosCourt(0.0))
    }

    /**
     * La forme longue groupe les milliers avec un espace insécable étroit, que
     * la locale française choisit et qui n'est pas celui d'un clavier. On le
     * normalise plutôt que de le recopier dans le test : une assertion qui
     * dépend du caractère exact casse au premier changement de JDK.
     */
    @Test
    fun `la forme longue reste exacte au centime`() {
        assertEquals("12 400,00 €", espacesNormales(Nombres.enEuros(12_400.0)))
        assertEquals("450,40 €", espacesNormales(Nombres.enEuros(450.40)))
    }

    private fun espacesNormales(texte: String): String =
        texte.map { if (it.isWhitespace() || it.code == 0x00A0 || it.code == 0x202F) ' ' else it }
            .joinToString("")
}
