package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Le prix dégressif par rang d'unité.
 *
 * Le cas qui motive tout : entretenir un split coûte 100 €, la deuxième unité
 * 80, la troisième 70. Ce sont trois prix, pas une remise sur le total, et la
 * différence se voit sur la facture.
 */
class DegressifTest {

    private fun palier(rang: Int, prix: Double, modifieLe: Instant = Instant.EPOCH) =
        PalierPrestation(
            prestationId = "p-1",
            aPartirDe = rang,
            prixUnitaire = prix,
            modifieLe = modifieLe,
        )

    private val maintenance = Prestation(
        id = "p-1",
        designation = "Maintenance split",
        categorie = CategoriePrestation.MAINTENANCE,
        prixUnitaire = 100.0,
        unite = "u",
    )

    /**
     * Le cas de l'énoncé : 100 + 80 + 70.
     *
     * Et non 3 × 70, qui serait la lecture « par tranche » — celle qu'on écarte,
     * parce que le déplacement et la mise en route sont payés par la première
     * unité et ne se rediscutent pas.
     */
    @Test
    fun `chaque rang porte son propre prix`() {
        val tarif = TarifDegressif(base = 100.0, paliers = listOf(palier(2, 80.0), palier(3, 70.0)))

        assertEquals(100.0, tarif.prixDuRang(1), 0.001)
        assertEquals(80.0, tarif.prixDuRang(2), 0.001)
        assertEquals(70.0, tarif.prixDuRang(3), 0.001)
        assertEquals("le dernier palier vaut aussi pour la suite", 70.0, tarif.prixDuRang(6), 0.001)

        assertEquals(250.0, tarif.total(3), 0.001)
    }

    /**
     * Ajouter une unité ne fait jamais baisser la facture.
     *
     * C'est la propriété que la lecture « par rang » garantit et que la lecture
     * « par tranche » peut violer sur un jeu de paliers mal réglé. Personne ne
     * s'en aperçoit avant qu'un client ne fasse le calcul.
     */
    @Test
    fun `le total ne decroit jamais quand on ajoute une unite`() {
        val tarif = TarifDegressif(
            base = 100.0,
            paliers = listOf(palier(2, 80.0), palier(3, 70.0), palier(5, 40.0)),
        )

        val totaux = (0..8).map { tarif.total(it) }

        totaux.zipWithNext().forEach { (avant, apres) ->
            assertTrue("le total a reculé : $totaux", apres >= avant)
        }
    }

    /** Sans palier, c'est le prix de la prestation, tout simplement. */
    @Test
    fun `sans palier, tout est au prix de base`() {
        val tarif = TarifDegressif(base = 100.0)

        assertFalse(tarif.degressif)
        assertEquals(300.0, tarif.total(3), 0.001)
        assertEquals(1, tarif.tranches(3).size)
    }

    /**
     * Les rangs consécutifs au même prix sont regroupés.
     *
     * Sans cela, poser un six-split produirait six lignes dont quatre
     * identiques — un devis qu'on ne relit plus.
     */
    @Test
    fun `les rangs au meme prix se regroupent en une tranche`() {
        val tarif = TarifDegressif(base = 100.0, paliers = listOf(palier(2, 80.0)))

        val tranches = tarif.tranches(5)

        assertEquals(2, tranches.size)
        assertEquals(TrancheDegressive(1, 1, 100.0), tranches[0])
        assertEquals(TrancheDegressive(2, 5, 80.0), tranches[1])
        assertEquals(4, tranches[1].nombre)
        assertEquals(320.0, tranches[1].montant, 0.001)
    }

    /** Zéro unité ne produit aucune tranche, donc aucune ligne. */
    @Test
    fun `une quantite nulle ne produit rien`() {
        val tarif = TarifDegressif(base = 100.0, paliers = listOf(palier(2, 80.0)))

        assertTrue(tarif.tranches(0).isEmpty())
        assertEquals(0.0, tarif.total(0), 0.001)
    }

    /** Un palier au rang 1 n'existe pas : ce rang est le prix de la prestation. */
    @Test
    fun `un palier au rang 1 est ignore`() {
        val tarif = TarifDegressif(base = 100.0, paliers = listOf(palier(1, 55.0)))

        assertFalse(tarif.degressif)
        assertEquals(100.0, tarif.prixDuRang(1), 0.001)
    }

    /** Deux paliers au même rang : le dernier posé gagne. */
    @Test
    fun `un doublon de rang se tranche par le plus recent`() {
        val tarif = TarifDegressif(
            base = 100.0,
            paliers = listOf(
                palier(2, 80.0, Instant.ofEpochMilli(1_000)),
                palier(2, 75.0, Instant.ofEpochMilli(2_000)),
            ),
        )

        assertEquals(75.0, tarif.prixDuRang(2), 0.001)
    }

    /**
     * Ce que la dégression devient sur le devis : des lignes ordinaires.
     *
     * C'est la même décision que pour le déplacement, et c'est aussi ce qui rend
     * le geste **visible** — le client lit ce qu'on lui a consenti.
     */
    @Test
    fun `la degression devient des lignes de devis ordinaires`() {
        val tarif = TarifDegressif(base = 100.0, paliers = listOf(palier(2, 80.0), palier(3, 70.0)))

        val lignes = LignesDegressives.pour(
            devisId = "d-1",
            prestation = maintenance,
            tarif = tarif,
            quantite = 3,
        )

        assertEquals(3, lignes.size)
        assertEquals("Maintenance split", lignes[0].designation)
        assertEquals(1.0, lignes[0].quantite, 0.001)
        assertEquals(100.0, lignes[0].prixUnitaire, 0.001)

        assertEquals("Maintenance split — 2e unité", lignes[1].designation)
        assertEquals("Maintenance split — 3e unité", lignes[2].designation)

        assertEquals(
            "la somme des lignes doit être celle du tarif",
            tarif.total(3),
            lignes.sumOf { it.montant }.auCentime(),
            0.001,
        )
        assertEquals("les rangs se suivent", listOf(0, 1, 2), lignes.map { it.rang })
    }

    /** Une tranche de plusieurs unités le dit, pour qu'un client retrouve son compte. */
    @Test
    fun `une tranche large nomme ses deux bornes`() {
        val tarif = TarifDegressif(base = 100.0, paliers = listOf(palier(2, 80.0)))

        val lignes = LignesDegressives.pour("d-1", maintenance, tarif, quantite = 4)

        assertEquals(2, lignes.size)
        assertEquals("Maintenance split — unités 2 à 4", lignes[1].designation)
        assertEquals(3.0, lignes[1].quantite, 0.001)
    }
}
