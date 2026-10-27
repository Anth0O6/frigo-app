package com.frigopro.app.ui

import com.frigopro.app.data.Article
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.data.LigneTarif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un devis fournisseur repris sur un devis client : la marge demandée et jamais
 * supposée, le coefficient d'une ligne qui l'emporte sur le général, et les prix
 * du magasin qui suivent la règle de la saisie des prix. Les prix sont inventés.
 */
class EtatImportFournisseurTest {

    private val barre = Article(
        id = "acc-01",
        reference = "GOU-80-2M",
        designation = "Goulotte clim 80x60 — barre 2 m",
        unite = "barre",
        prixAchat = 6.0,
        prixVente = 8.10, // 6,00 × 1,35 : une proposition du coefficient
    )

    private fun lue(reference: String, quantite: Double, prix: Double) = LigneTarif(
        designation = "GOULOTTE BLANC 80X60MM",
        reference = reference,
        quantite = quantite,
        prixNet = prix,
        montant = quantite * prix,
    )

    private val etat = ImportFournisseur(
        fournisseur = Fournisseur(id = "four-1", nom = "Grossiste"),
        coefficient = 1.35,
        lignes = listOf(
            LigneImport(0, lue("GOU-80-2M", 6.0, 7.15), barre, quantite = 6.0),
            LigneImport(1, lue("POMPE-REL", 1.0, 80.0), null, quantite = 1.0),
        ),
    )

    /** Rien ne s'ajoute tant qu'on n'a pas répondu à « marger ou non ». */
    @Test
    fun `la marge se demande et ne se suppose pas`() {
        assertEquals("Choisissez de marger ou non.", etat.manque)
        assertNull(etat.prixVente(etat.lignes[0]))
        assertNull(etat.copy(marge = ChoixMarge.MARGER).manque)
    }

    /** Marger applique le coefficient au prix lu, arrondi au centime ; au coûtant, le prix lu tel quel. */
    @Test
    fun `le prix de vente vient du prix lu et du choix`() {
        val marge = etat.copy(marge = ChoixMarge.MARGER)
        assertEquals(9.65, marge.prixVente(marge.lignes[0])!!, 0.001) // 7,15 × 1,35 = 9,6525
        val coutant = etat.copy(marge = ChoixMarge.COUTANT)
        assertEquals(7.15, coutant.prixVente(coutant.lignes[0])!!, 0.001)
    }

    /** Un coefficient tapé sur une ligne l'emporte sur le choix général, dans les deux sens. */
    @Test
    fun `le coefficient d'une ligne l'emporte sur le general`() {
        val coutant = etat.copy(marge = ChoixMarge.COUTANT).avecLigne(1) { it.copy(coefficient = 1.2) }
        assertEquals(7.15, coutant.prixVente(coutant.lignes[0])!!, 0.001)
        assertEquals(96.0, coutant.prixVente(coutant.lignes[1])!!, 0.001)

        // Vidé, il rend la ligne au général.
        val rendu = coutant.avecLigne(1) { it.copy(coefficient = null) }
        assertEquals(80.0, rendu.prixVente(rendu.lignes[1])!!, 0.001)
    }

    /** Marger sans coefficient réglé ni tapé : la ligne n'a pas de prix, et l'écran le dit. */
    @Test
    fun `marger sans coefficient le dit`() {
        val sans = etat.copy(marge = ChoixMarge.MARGER, coefficient = null)
        assertEquals("Un coefficient manque.", sans.manque)
    }

    /**
     * Les lignes posées portent la **quantité retouchée**, le prix d'achat lu —
     * celui du jour, qui rend la marge juste — et la référence dans l'intitulé.
     * Une ligne décochée n'est pas posée.
     */
    @Test
    fun `les lignes posees portent la quantite et le prix d'achat lus`() {
        val pret = etat.copy(marge = ChoixMarge.MARGER)
            .avecLigne(0) { it.copy(quantite = 4.0) }
            .avecLigne(1) { it.copy(retenue = false) }

        val ligne = pret.versLignes().single()
        assertEquals("Goulotte clim 80x60 — barre 2 m (GOU-80-2M)", ligne.designation)
        assertEquals(4.0, ligne.quantite, 0.0)
        assertEquals("barre", ligne.unite)
        assertEquals(7.15, ligne.prixAchat, 0.001)
        assertEquals(9.65, ligne.prixUnitaire, 0.001)
    }

    /** Une quantité vidée empêche d'ajouter, plutôt que de poser une ligne à zéro. */
    @Test
    fun `une quantite videe empeche d'ajouter`() {
        val vide = etat.copy(marge = ChoixMarge.COUTANT).avecLigne(0) { it.copy(quantite = null) }
        assertEquals("Une quantité manque.", vide.manque)
    }

    /**
     * Le prix d'achat lu se reporte sur l'article de même référence, **même
     * décoché** : ne pas le poser sur ce devis ne rend pas faux le prix annoncé.
     * La vente de la fiche n'était qu'une proposition du coefficient : elle suit.
     * Une ligne hors magasin ne crée rien.
     */
    @Test
    fun `les prix du magasin suivent la regle de la saisie des prix`() {
        val decoche = etat.copy(marge = ChoixMarge.MARGER).avecLigne(0) { it.copy(retenue = false) }

        val prix = decoche.versPrix(coefficientReglages = 1.35).single()
        assertEquals(barre.id, prix.articleId)
        assertEquals(7.15, prix.prixAchat, 0.001)
        assertEquals(9.65, prix.prixVente, 0.001)
        assertEquals("four-1", prix.fournisseurId)

        // Une vente décidée à la main sur la fiche est gardée.
        val decidee = etat.copy(lignes = listOf(etat.lignes[0].copy(article = barre.copy(prixVente = 12.0))))
        assertEquals(12.0, decidee.versPrix(1.35).single().prixVente, 0.001)

        assertTrue(etat.copy(mettreAJourPrix = false).versPrix(1.35).isEmpty())
    }

    /**
     * Une ligne cochée hors magasin fait naître sa fiche, au prix lu et chez le
     * fournisseur du devis, la vente proposée au coefficient des Réglages — pas à
     * celui consenti sur ce devis. Décochée, elle n'en crée pas ; la case
     * décochée non plus.
     */
    @Test
    fun `une ligne hors magasin fait naitre sa fiche`() {
        val marge = etat.copy(marge = ChoixMarge.MARGER).avecLigne(1) { it.copy(coefficient = 2.0) }

        val fiche = marge.versArticles(coefficientReglages = 1.25, existants = listOf(barre)).single()
        assertEquals("POMPE-REL", fiche.reference)
        assertEquals("Goulotte blanc 80X60MM", fiche.designation)
        assertEquals(80.0, fiche.prixAchat, 0.001)
        assertEquals(100.0, fiche.prixVente, 0.001)
        assertEquals("four-1", fiche.fournisseurId)
        assertEquals("Grossiste", fiche.fournisseurNom)

        assertEquals("sans coefficient réglé, aucune vente inventée", 0.0, marge.versArticles(0.0, listOf(barre)).single().prixVente, 0.0)
        assertTrue(marge.avecLigne(1) { it.copy(retenue = false) }.versArticles(1.25, listOf(barre)).isEmpty())
        assertTrue(marge.copy(creerArticles = false).versArticles(1.25, listOf(barre)).isEmpty())
    }

    /**
     * Pas de doublon : une référence déjà au magasin au moment d'écrire n'en crée
     * pas, et deux lignes du même document à la même référence n'en font qu'une.
     */
    @Test
    fun `une reference ne cree qu'une fiche`() {
        val deux = etat.copy(
            marge = ChoixMarge.COUTANT,
            lignes = etat.lignes + LigneImport(2, lue("POMPE-REL", 2.0, 80.0), null, quantite = 2.0),
        )
        assertEquals(1, deux.versArticles(1.25, listOf(barre)).size)

        val arrivee = Article(reference = "pompe rel", designation = "Pompe de relevage")
        assertTrue(deux.versArticles(1.25, listOf(barre, arrivee)).isEmpty())
    }

    /** Hors magasin, l'intitulé est celui du fournisseur rendu lisible, la référence à la suite. */
    @Test
    fun `une ligne hors magasin garde le nom du fournisseur`() {
        val ligne = etat.lignes[1]
        assertNull(ligne.article)
        assertEquals("Goulotte blanc 80X60MM (POMPE-REL)", ligne.designation)
        assertNotNull(ligne.unite)
    }
}
