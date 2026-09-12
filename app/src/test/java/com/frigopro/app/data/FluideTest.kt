package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Le GWP et la périodicité ne sont pas de l'affichage : un technicien qui se
 * trompe de fréquence de contrôle est en infraction. Ces calculs doivent être
 * justes, ou se taire.
 */
class FluideTest {

    @Test
    fun `un fluide se reconnait quelle que soit sa graphie`() {
        assertEquals(2141, Fluides.gwp("R452A"))
        assertEquals(2141, Fluides.gwp("r452a"))
        assertEquals(2141, Fluides.gwp("R-452A"))
        assertEquals(2141, Fluides.gwp(" R452A "))
    }

    @Test
    fun `un fluide inconnu n'a pas de GWP invente`() {
        assertNull(Fluides.gwp("R999Z"))
        assertNull(Fluides.tonnesEquivalentCo2("R999Z", 6.2))
    }

    /** Le chiffre de la maquette : 6,2 kg de R452A font 13,3 t éq. CO₂. */
    @Test
    fun `l'equivalent CO2 est la charge multipliee par le GWP`() {
        val tonnes = Fluides.tonnesEquivalentCo2("R452A", 6.2)

        assertEquals(13.3, tonnes!!.arrondiDixieme(), 0.001)
    }

    @Test
    fun `les seuils du reglement decident de la periodicite`() {
        assertEquals(PeriodiciteControle.AUCUNE, PeriodiciteControle.pour(4.9))
        assertEquals(PeriodiciteControle.DOUZE_MOIS, PeriodiciteControle.pour(5.0))
        assertEquals(PeriodiciteControle.DOUZE_MOIS, PeriodiciteControle.pour(49.9))
        assertEquals(PeriodiciteControle.SIX_MOIS, PeriodiciteControle.pour(50.0))
        assertEquals(PeriodiciteControle.SIX_MOIS, PeriodiciteControle.pour(499.9))
        assertEquals(PeriodiciteControle.TROIS_MOIS, PeriodiciteControle.pour(500.0))
    }

    @Test
    fun `l'echeance se deduit du dernier controle`() {
        val etat = EtatEtancheite.calculer(
            fluide = "R452A",
            chargeKg = 6.2,
            dernierControle = LocalDate.of(2025, 9, 18),
            aujourdhui = LocalDate.of(2026, 5, 14),
        )

        assertEquals(PeriodiciteControle.DOUZE_MOIS, etat.periodicite)
        assertEquals(LocalDate.of(2026, 9, 18), etat.echeance)
        assertFalse(etat.enRetard)
    }

    @Test
    fun `une echeance depassee est signalee`() {
        val etat = EtatEtancheite.calculer(
            fluide = "R452A",
            chargeKg = 6.2,
            dernierControle = LocalDate.of(2025, 1, 10),
            aujourdhui = LocalDate.of(2026, 5, 14),
        )

        assertTrue(etat.enRetard)
    }

    /**
     * Sans charge connue, aucun équivalent CO₂ n'est calculable : annoncer une
     * périodicité reviendrait à la deviner.
     */
    @Test
    fun `sans charge, aucune periodicite n'est annoncee`() {
        val etat = EtatEtancheite.calculer("R452A", null, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 5, 14))

        assertEquals(PeriodiciteControle.AUCUNE, etat.periodicite)
        assertNull(etat.echeance)
    }

    /** Sans contrôle antérieur, on ne peut pas dater le suivant. */
    @Test
    fun `sans dernier controle, aucune echeance n'est datee`() {
        val etat = EtatEtancheite.calculer("R452A", 6.2, null, LocalDate.of(2026, 5, 14))

        assertEquals(PeriodiciteControle.DOUZE_MOIS, etat.periodicite)
        assertNull(etat.echeance)
        assertFalse(etat.enRetard)
    }

    /** Une petite charge de fluide à faible GWP n'est pas soumise à contrôle. */
    @Test
    fun `le propane en petite charge n'est pas soumis`() {
        val etat = EtatEtancheite.calculer("R290", 0.4, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 5, 14))

        assertEquals(PeriodiciteControle.AUCUNE, etat.periodicite)
        assertNull(etat.echeance)
    }
}
