package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La facturation du déplacement : ce qui se compte, ce qui double, et le
 * plancher.
 *
 * Les montants attendus sont écrits en clair plutôt que recalculés par le test :
 * un test qui refait le calcul de la classe qu'il éprouve ne vérifie que sa
 * propre cohérence.
 */
class DeplacementTest {

    private val auKm = TarifDeplacement(mode = ModeDeplacement.KM, prixKm = 0.45)

    private val aLHeure = TarifDeplacement(mode = ModeDeplacement.HEURE, prixHeure = 35.0)

    private val lesDeux = TarifDeplacement(
        mode = ModeDeplacement.KM_ET_HEURE,
        prixKm = 0.45,
        prixHeure = 35.0,
    )

    private fun trajet(
        km: Double = 12.0,
        minutes: Int = 18,
        allerRetour: Boolean = false,
        peages: Double = 0.0,
        offert: Boolean = false,
    ) = Trajet(
        devisId = "d-1",
        distanceKm = km,
        dureeMinutes = minutes,
        peages = peages,
        peagesConnus = peages > 0.0,
        allerRetour = allerRetour,
        offert = offert,
    )

    @Test
    fun `au kilometre, l'aller seul compte une fois`() {
        val calcul = CalculDeplacement.montant(trajet(), auKm)

        assertEquals(12.0, calcul.distanceFacturee, 0.001)
        assertEquals(5.40, calcul.total, 0.001)
        assertEquals("le temps ne se facture pas dans ce mode", 0.0, calcul.montantHeure, 0.001)
    }

    /** Le cas le plus courant, et celui qui double le montant. */
    @Test
    fun `l'aller-retour double la distance et le temps`() {
        val calcul = CalculDeplacement.montant(trajet(allerRetour = true), lesDeux)

        assertEquals(24.0, calcul.distanceFacturee, 0.001)
        assertEquals(0.6, calcul.heuresFacturees, 0.001)
        assertEquals(10.80, calcul.montantKm, 0.001)
        assertEquals(21.00, calcul.montantHeure, 0.001)
        assertEquals(31.80, calcul.total, 0.001)
    }

    /**
     * Le kilomètre paie le véhicule, l'heure paie le chauffeur : les deux modes
     * ne se recouvrent pas, et « les deux » est bien leur somme.
     */
    @Test
    fun `les deux modes s'additionnent`() {
        val distance = CalculDeplacement.montant(trajet(allerRetour = true), auKm).total
        val temps = CalculDeplacement.montant(trajet(allerRetour = true), aLHeure).total
        val ensemble = CalculDeplacement.montant(trajet(allerRetour = true), lesDeux).total

        assertEquals(distance + temps, ensemble, 0.001)
    }

    /**
     * Le piège de l'arrondi, et la raison pour laquelle les quantités sont
     * arrondies avant de servir : le devis imprime « 1,17 h », et la ligne doit
     * valoir 1,17 × le tarif. Un montant calculé sur 1,1666… h ne retomberait
     * pas sur l'addition que le client refait à la main.
     */
    @Test
    fun `la ligne retombe sur sa propre quantite`() {
        val calcul = CalculDeplacement.montant(
            trajet(minutes = 35, allerRetour = true),
            aLHeure,
        )

        assertEquals("70 minutes arrondies au centieme d'heure", 1.17, calcul.heuresFacturees, 0.0001)
        assertEquals(
            "le montant doit etre celui de la quantite affichee",
            (calcul.heuresFacturees * 35.0),
            calcul.montantHeure,
            0.001,
        )
        assertEquals(40.95, calcul.montantHeure, 0.001)
    }

    @Test
    fun `un tres petit trajet tombe au minimum`() {
        val tarif = auKm.copy(minimum = 25.0)
        val calcul = CalculDeplacement.montant(trajet(km = 3.0, minutes = 6), tarif)

        assertTrue("le plancher doit se signaler", calcul.minimumApplique)
        assertEquals(25.00, calcul.montantTrajet, 0.001)
        assertEquals(25.00, calcul.total, 0.001)
    }

    @Test
    fun `un trajet au-dela du minimum ne le mentionne pas`() {
        val tarif = auKm.copy(minimum = 25.0)
        val calcul = CalculDeplacement.montant(trajet(km = 90.0, allerRetour = true), tarif)

        assertTrue(!calcul.minimumApplique)
        assertEquals(81.00, calcul.total, 0.001)
    }

    /**
     * Les péages sont des débours et non une prestation : le plancher ne les
     * absorbe pas, sinon ils disparaîtraient sur les courtes distances — celles,
     * justement, où le plancher s'applique.
     */
    @Test
    fun `le minimum ne mange pas les peages`() {
        val tarif = auKm.copy(minimum = 25.0)
        val calcul = CalculDeplacement.montant(
            trajet(km = 3.0, minutes = 6, allerRetour = true, peages = 8.40),
            tarif,
        )

        assertEquals("les barrieres se paient dans les deux sens", 16.80, calcul.peages, 0.001)
        assertEquals(41.80, calcul.total, 0.001)
    }

    @Test
    fun `un peage non refacture ne compte pas`() {
        val tarif = auKm.copy(refacturerPeages = false)
        val calcul = CalculDeplacement.montant(
            trajet(km = 90.0, allerRetour = true, peages = 8.40),
            tarif,
        )

        assertEquals(0.0, calcul.peages, 0.001)
        assertEquals(81.00, calcul.total, 0.001)
    }

    /**
     * Offrir met le total à zéro **sans effacer les chiffres** : c'est le montant
     * barré qui fait l'argument de vente, et le reprendre ne doit pas demander de
     * ressaisir l'adresse.
     */
    @Test
    fun `un deplacement offert garde son montant visible`() {
        val calcul = CalculDeplacement.montant(
            trajet(km = 90.0, minutes = 65, allerRetour = true, peages = 8.40, offert = true),
            lesDeux,
        )

        assertEquals(0.0, calcul.total, 0.001)
        assertEquals(156.95, calcul.montantTrajet, 0.001)
        assertEquals(173.75, calcul.totalAvantGeste, 0.001)
    }

    // — Les lignes de devis produites —

    @Test
    fun `au kilometre, une seule ligne, en kilometres`() {
        val lignes = LignesDeplacement.pour(trajet(allerRetour = true), auKm)

        assertEquals(1, lignes.size)
        assertEquals("Déplacement (aller-retour)", lignes.single().designation)
        assertEquals(24.0, lignes.single().quantite, 0.001)
        assertEquals("km", lignes.single().unite)
        assertEquals(0.45, lignes.single().prixUnitaire, 0.001)
        assertEquals(10.80, lignes.single().montant, 0.001)
    }

    @Test
    fun `les deux modes donnent deux lignes, et le peage une troisieme`() {
        val lignes = LignesDeplacement.pour(
            trajet(allerRetour = true, peages = 8.40),
            lesDeux,
        )

        assertEquals(listOf("km", "h", "forfait"), lignes.map { it.unite })
        assertEquals(16.80, lignes.last().prixUnitaire, 0.001)
        assertTrue("les lignes du deplacement se reconnaissent", lignes.all { it.deplacement })
    }

    /**
     * Le plancher ne se dit pas en kilomètres : l'écrire « 3 km à 8,33 € » aurait
     * inventé un tarif pour justifier le montant, et c'est un tarif que le client
     * aurait pu opposer au trajet suivant.
     */
    @Test
    fun `le minimum donne une ligne de forfait, pas un tarif invente`() {
        val lignes = LignesDeplacement.pour(
            trajet(km = 3.0, minutes = 6),
            auKm.copy(minimum = 25.0),
        )

        assertEquals(1, lignes.size)
        assertEquals("forfait", lignes.single().unite)
        assertEquals(1.0, lignes.single().quantite, 0.001)
        assertEquals(25.00, lignes.single().prixUnitaire, 0.001)
    }

    @Test
    fun `un deplacement offert produit des lignes offertes`() {
        val lignes = LignesDeplacement.pour(
            trajet(allerRetour = true, peages = 8.40, offert = true),
            lesDeux,
        )

        assertTrue(lignes.isNotEmpty())
        assertTrue("l'offre porte sur tout le deplacement", lignes.all { it.offerte })
        assertEquals("aucune ne compte dans le total", 0.0, lignes.sumOf { it.montant }, 0.001)
        assertTrue("mais le prix reste, pour s'afficher barre", lignes.all { it.montantAvantGeste > 0.0 })
    }

    @Test
    fun `un trajet vide ne produit aucune ligne`() {
        assertTrue(LignesDeplacement.pour(trajet(km = 0.0, minutes = 0), lesDeux).isEmpty())
    }

    /** Sans prix, il n'y a rien à facturer : l'écran doit pouvoir le dire. */
    @Test
    fun `un tarif sans prix n'est pas renseigne`() {
        assertTrue(!TarifDeplacement(mode = ModeDeplacement.KM).renseigne)
        assertTrue(!TarifDeplacement(mode = ModeDeplacement.KM_ET_HEURE, prixKm = 0.45).renseigne)
        assertTrue(TarifDeplacement(mode = ModeDeplacement.KM, prixKm = 0.45).renseigne)
    }
}
