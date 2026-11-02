package com.frigopro.app.ui

import org.junit.Test
import org.junit.Assert.assertEquals

class CommandeArticleTest {

    @Test
    fun `fournisseur inconnu ou sans coordonnees reste sur Ouvrir`() {
        assertEquals(GesteCommande.Ouvrir, choisirGesteCommande(null))
        assertEquals(GesteCommande.Ouvrir, choisirGesteCommande(ContactFournisseur("  ", "")))
        assertEquals("Ouvrir", choisirGesteCommande(null).libelle)
    }

    @Test
    fun `le telephone passe avant le catalogue`() {
        val geste = choisirGesteCommande(ContactFournisseur("01 00 00 00 00", "https://exemple.test/cat"))
        assertEquals(GesteCommande.Appeler("01 00 00 00 00"), geste)
        assertEquals("Commander", geste.libelle)
    }

    @Test
    fun `sans telephone le catalogue s'ouvre`() {
        val geste = choisirGesteCommande(ContactFournisseur(" ", " https://exemple.test/cat "))
        assertEquals(GesteCommande.Catalogue("https://exemple.test/cat"), geste)
        assertEquals("Commander", geste.libelle)
    }
}
