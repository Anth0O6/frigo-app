package com.frigopro.app.ui

import com.frigopro.app.data.Article
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.Client
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureChiffree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La recherche globale d'« Aujourd'hui » : un seul champ pour les clients, les
 * machines, les factures, les devis et les articles.
 */
class RechercheGlobaleTest {

    private val carrefour = Client(id = "cl-1", nom = "Carrefour Market", ville = "Villeurbanne")
    private val morel = Client(id = "cl-2", nom = "Boucherie Morel", ville = "Lyon")
    private val toit = Equipement(id = "eq-1", clientId = "cl-1", nom = "Groupe toit", zone = "Toiture")
    private val vitrine = Equipement(id = "eq-2", clientId = "cl-2", nom = "Vitrine", zone = "Boutique")
    private val facture = FactureChiffree(Facture(id = "f-1", numero = "FAC-2026-0042", clientNom = "Boucherie Morel"), 100.0)
    private val devis = DevisChiffre(Devis(id = "d-1", numero = "DEV-2610-003", clientNom = "Carrefour Market"), 50.0)
    private val goulotte = ArticleEnStock(Article(id = "a-1", designation = "Goulotte clim 80x60", reference = "WHITE-80"), emptyMap())

    private fun chercher(recherche: String) = chercherPartout(
        recherche,
        listOf(morel, carrefour),
        listOf(toit, vitrine),
        listOf(facture),
        listOf(devis),
        listOf(goulotte),
    )

    @Test
    fun `une recherche vide ne trouve rien et ne compte rien`() {
        val resultats = chercher("  ")
        assertTrue(resultats.vide)
        assertTrue(resultats.totaux.isEmpty())
    }

    @Test
    fun `chaque famille se cherche avec les memes regles qu'ailleurs`() {
        assertEquals(listOf(morel), chercher("morel").clients)
        assertEquals(listOf(facture), chercher("fac 0042").factures)
        assertEquals(listOf(devis), chercher("dev2610").devis)
        assertEquals(listOf(goulotte), chercher("goulotte").articles)
    }

    /** « carrefour toiture » : une machine se désigne par l'endroit où elle est. */
    @Test
    fun `une machine se trouve par sa zone et par le nom de son client`() {
        val resultats = chercher("carrefour toiture")
        assertEquals(listOf("eq-1"), resultats.machines.map { it.equipement.id })
        assertEquals("Carrefour Market", resultats.machines.single().clientNom)
        assertTrue("le client seul ne porte pas « toiture »", resultats.clients.isEmpty())
    }

    @Test
    fun `chaque famille est plafonnee et dit son compte complet`() {
        val beaucoup = (1..8).map { Client(id = "c$it", nom = "Boulangerie $it", ville = "Lyon") }
        val resultats = chercherPartout("boulangerie", beaucoup, emptyList(), emptyList(), emptyList(), emptyList())

        assertEquals(ResultatsGlobaux.LIMITE, resultats.clients.size)
        assertEquals(8, resultats.totaux[FamilleTrouvee.CLIENTS])
    }
}
