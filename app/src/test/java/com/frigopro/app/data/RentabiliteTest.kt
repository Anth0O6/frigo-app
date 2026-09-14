package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Ce qu'une intervention a coûté, et ce qu'elle rapporte.
 *
 * Les cas retenus sont ceux où un chiffre faux ne se voit pas : une marge égale
 * à la recette faute de coût horaire, un fluide repris compté comme un achat,
 * une perte affichée comme un bénéfice.
 */
class RentabiliteTest {

    private fun intervention(secondes: Long) = Intervention(
        id = "int-1",
        date = LocalDate.of(2026, 3, 12),
        heure = LocalTime.of(9, 0),
        client = "Boucherie Morel",
        ville = "Lyon",
        statut = StatutIntervention.TERMINEE,
        chrono = Chrono(arriveeLe = Instant.parse("2026-03-12T08:00:00Z"), cumuleS = secondes),
    )

    /**
     * Le cas ordinaire : deux heures, une pièce, du fluide, et une facture.
     *
     * 2 h × 45 € = 90 € de main-d'œuvre, 1 × 38 € de pièce, 1,5 kg × 24 € de
     * fluide, soit 164 € de coûts directs pour 420 € facturés.
     */
    @Test
    fun `le cout direct additionne le temps, les pieces et le fluide`() {
        val rentabilite = RentabiliteIntervention.de(
            intervention = intervention(secondes = 7200),
            pieces = listOf(
                PiecePosee(
                    interventionId = "int-1",
                    designation = "Détendeur",
                    quantite = 1.0,
                    prixUnitaire = 90.0,
                    prixAchat = 38.0,
                ),
            ),
            mouvements = listOf(
                MouvementFluide(
                    interventionId = "int-1",
                    fluide = "R449A",
                    sens = SensFluide.AJOUT,
                    masseKg = 1.5,
                    prixAchatKg = 24.0,
                ),
            ),
            parametres = Parametres(coutHoraireInterne = 45.0),
            recetteHt = 420.0,
        )

        assertEquals(2.0, rentabilite.heures, 0.001)
        assertEquals(90.0, rentabilite.coutMainDoeuvre, 0.001)
        assertEquals(38.0, rentabilite.coutPieces, 0.001)
        assertEquals(36.0, rentabilite.coutFluide, 0.001)
        assertEquals(164.0, rentabilite.coutDirect, 0.001)
        assertEquals(256.0, rentabilite.marge!!, 0.001)
        assertEquals(61.0, rentabilite.tauxDeMarque!!, 0.1)
        assertFalse(rentabilite.aPerte)
    }

    /**
     * Le fluide **récupéré** ne coûte rien : c'est une reprise, pas un achat.
     *
     * L'imputer en coût reviendrait à se faire payer deux fois le même kilo.
     * Même règle que pour les lignes de facture, où une récupération ne produit
     * aucune ligne.
     */
    @Test
    fun `le fluide recupere n'entre pas dans le cout`() {
        val rentabilite = RentabiliteIntervention.de(
            intervention = intervention(secondes = 3600),
            pieces = emptyList(),
            mouvements = listOf(
                MouvementFluide(
                    interventionId = "int-1",
                    fluide = "R404A",
                    sens = SensFluide.AJOUT,
                    masseKg = 2.0,
                    prixAchatKg = 60.0,
                ),
                MouvementFluide(
                    interventionId = "int-1",
                    fluide = "R404A",
                    sens = SensFluide.RECUPERATION,
                    masseKg = 3.0,
                    prixAchatKg = 60.0,
                ),
            ),
            parametres = Parametres(coutHoraireInterne = 0.0),
            recetteHt = null,
        )

        assertEquals("seuls les 2 kg ajoutés comptent", 120.0, rentabilite.coutFluide, 0.001)
    }

    /**
     * Sans coût horaire ni prix d'achat, le calcul se tait plutôt que de
     * flatter.
     *
     * Le coût vaudrait zéro et la marge serait exactement la recette : un
     * chiffre juste par accident, que personne ne pourrait distinguer d'un vrai.
     * Facturer l'année suivante sur cette base coûterait cher.
     */
    @Test
    fun `sans cout renseigne, le calcul se declare non chiffrable`() {
        val rentabilite = RentabiliteIntervention.de(
            intervention = intervention(secondes = 7200),
            pieces = emptyList(),
            mouvements = emptyList(),
            parametres = Parametres(coutHoraireInterne = 0.0),
            recetteHt = 420.0,
        )

        assertFalse(rentabilite.chiffrable)
        assertEquals(0.0, rentabilite.coutDirect, 0.001)
    }

    /**
     * Une intervention non facturée n'a pas de marge, et surtout pas zéro.
     *
     * Zéro se lirait comme « ça n'a rien rapporté », ce qui est faux : la
     * facture n'est pas encore partie.
     */
    @Test
    fun `sans facture, il n'y a pas de marge plutot qu'une marge nulle`() {
        val rentabilite = RentabiliteIntervention.de(
            intervention = intervention(secondes = 3600),
            pieces = emptyList(),
            mouvements = emptyList(),
            parametres = Parametres(coutHoraireInterne = 45.0),
            recetteHt = null,
        )

        assertEquals(45.0, rentabilite.coutDirect, 0.001)
        assertNull(rentabilite.marge)
        assertNull(rentabilite.tauxDeMarque)
        assertFalse("et rien ne se lit comme une perte", rentabilite.aPerte)
    }

    /** Une intervention à perte se signale, plutôt que d'être arrondie au bonheur. */
    @Test
    fun `une intervention a perte se voit`() {
        val rentabilite = RentabiliteIntervention.de(
            intervention = intervention(secondes = 14400),
            pieces = listOf(
                PiecePosee(
                    interventionId = "int-1",
                    designation = "Compresseur",
                    quantite = 1.0,
                    prixAchat = 620.0,
                ),
            ),
            mouvements = emptyList(),
            parametres = Parametres(coutHoraireInterne = 45.0),
            recetteHt = 600.0,
        )

        assertEquals(800.0, rentabilite.coutDirect, 0.001)
        assertEquals(-200.0, rentabilite.marge!!, 0.001)
        assertTrue(rentabilite.aPerte)
    }

    /**
     * Le coût horaire **interne** et le taux **facturé** sont deux réglages, et
     * les intervertir donnerait une marge négative sur une intervention
     * rentable. Le test le fige : ils ne se lisent pas au même endroit.
     */
    @Test
    fun `le cout horaire interne n'est pas le taux facture`() {
        val reglages = Parametres(tauxHoraire = 68.0, coutHoraireInterne = 45.0)

        val rentabilite = RentabiliteIntervention.de(
            intervention = intervention(secondes = 3600),
            pieces = emptyList(),
            mouvements = emptyList(),
            parametres = reglages,
            recetteHt = 68.0,
        )

        assertEquals("le coût vient du taux interne", 45.0, rentabilite.coutMainDoeuvre, 0.001)
        assertEquals(23.0, rentabilite.marge!!, 0.001)
    }
}
