package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientTest {

    /**
     * Le carnet se remplissant depuis les interventions, la plupart des clients
     * n'ont longtemps ni adresse ni téléphone. L'écran doit pouvoir le savoir
     * pour ne pas proposer d'appeler dans le vide.
     */
    @Test
    fun `un client sans coordonnees n'est ni appelable ni localisable`() {
        val client = Client(nom = "Primeur Vasseur", ville = "Duclair")

        assertFalse(client.appelable)
        assertFalse(client.localisable)
    }

    @Test
    fun `un client renseigne est appelable et localisable`() {
        val client = Client(
            nom = "Primeur Vasseur",
            ville = "Duclair",
            adresse = "3 place du Marché",
            telephone = "0235000000",
        )

        assertTrue(client.appelable)
        assertTrue(client.localisable)
    }

    /**
     * Un espace n'est pas une adresse : sans cela, un champ effacé à moitié
     * ouvrirait une carte sur une destination vide.
     */
    @Test
    fun `des coordonnees faites d'espaces ne comptent pas`() {
        val client = Client(nom = "Primeur Vasseur", ville = "Duclair", adresse = "   ", telephone = " ")

        assertFalse(client.appelable)
        assertFalse(client.localisable)
    }

    @Test
    fun `l'adresse complete joint l'adresse a la ville, et se passe de ce qui manque`() {
        val nu = Client(nom = "Primeur Vasseur", ville = "Duclair")

        assertEquals("Duclair", nu.adresseComplete)
        assertEquals("3 place du Marché, Duclair", nu.copy(adresse = "3 place du Marché").adresseComplete)
    }
}
