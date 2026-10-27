package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le passage HT ↔ TTC, les sous-totaux, et le coût d'achat selon le régime.
 * Les prix sont inventés.
 */
class ChiffrageTtcTest {

    /** 60 € TTC à 20 % font 50 € HT, et 50 € HT refont 60 € TTC. */
    @Test
    fun `le HT se deduit du TTC et le refait`() {
        assertEquals(50.0, ChiffrageTtc.versHt(60.0, 20.0), 0.0)
        assertEquals(60.0, ChiffrageTtc.versTtc(50.0, 20.0), 0.0)
        // Arrondi au centime : 55 € TTC donnent 45,83 € HT, qui refont 55 €.
        assertEquals(45.83, ChiffrageTtc.versHt(55.0, 20.0), 0.0)
        assertEquals(55.0, ChiffrageTtc.versTtc(45.83, 20.0), 0.0)
        // À 10 %, le taux réduit des travaux en logement.
        assertEquals(100.0, ChiffrageTtc.versHt(110.0, 10.0), 0.0)
    }

    /**
     * Les familles se déduisent de la ligne : matériel par son prix d'achat,
     * déplacement par son marqueur, le reste en main-d'œuvre et prestations. Une
     * ligne offerte ne compte pas, comme dans le total ; une famille vide
     * n'apparaît pas.
     */
    @Test
    fun `les sous-totaux se rangent par famille`() {
        val lignes = listOf(
            LigneDevis(devisId = "d", designation = "Split 3,5 kW", quantite = 1.0, prixUnitaire = 900.0, prixAchat = 600.0),
            LigneDevis(devisId = "d", designation = "Pose", quantite = 4.0, prixUnitaire = 50.0),
            LigneDevis(devisId = "d", designation = "Mise en service", quantite = 1.0, prixUnitaire = 80.0, offerte = true),
        )

        val sous = ChiffrageTtc.sousTotaux(lignes, tauxTva = 20.0, assujettie = true)

        assertEquals(listOf(FamilleLigne.MATERIEL, FamilleLigne.PRESTATIONS), sous.map { it.famille })
        assertEquals(900.0, sous[0].ht, 0.0)
        assertEquals(1080.0, sous[0].ttc, 0.0)
        assertEquals("l'offerte ne compte pas", 200.0, sous[1].ht, 0.0)
        assertEquals(240.0, sous[1].ttc, 0.0)
    }

    /** En franchise, pas de TVA : le TTC d'une famille est son HT. */
    @Test
    fun `en franchise le sous-total n'a pas de TVA`() {
        val lignes = listOf(LigneDevis(devisId = "d", designation = "Pose", quantite = 2.0, prixUnitaire = 45.0))

        val seul = ChiffrageTtc.sousTotaux(lignes, tauxTva = 20.0, assujettie = false).single()

        assertEquals(0.0, seul.tva, 0.0)
        assertEquals(90.0, seul.ttc, 0.0)
    }

    /**
     * Selon le régime : l'assujetti récupère la TVA du fournisseur et compte en HT,
     * le franchisé la paie sans la récupérer et compte en TTC. Les deux autres
     * choix forcent la réponse, quel que soit le régime.
     */
    @Test
    fun `le cout d'achat suit le regime ou le force`() {
        assertFalse(CoutAchat.SELON_REGIME.ttc(assujettie = true))
        assertTrue(CoutAchat.SELON_REGIME.ttc(assujettie = false))
        assertFalse(CoutAchat.HT.ttc(assujettie = false))
        assertTrue(CoutAchat.TTC.ttc(assujettie = true))
    }
}
