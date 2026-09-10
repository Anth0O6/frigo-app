package com.frigopro.app.ui

import com.frigopro.app.data.Client
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EtatFicheClientTest {

    @Test
    fun `une fiche sans nom ou sans ville n'est pas valide`() {
        assertFalse(EtatFicheClient().estValide)
        assertFalse(EtatFicheClient(nom = "Boucherie Lemoine").estValide)
        assertFalse(EtatFicheClient(ville = "Rouen").estValide)
        assertTrue(EtatFicheClient(nom = "Boucherie Lemoine", ville = "Rouen").estValide)
    }

    /** L'adresse et le téléphone ne conditionnent rien : ils arrivent plus tard. */
    @Test
    fun `une fiche est valide sans adresse ni telephone`() {
        assertTrue(EtatFicheClient(nom = "Primeur Vasseur", ville = "Duclair").estValide)
    }

    @Test
    fun `une creation recoit un identifiant neuf a chaque fois`() {
        assertNotEquals(EtatFicheClient().id, EtatFicheClient().id)
    }

    /**
     * L'identifiant est tiré à la construction et non à l'enregistrement :
     * sinon deux appuis sur « Ajouter » créeraient deux clients.
     */
    @Test
    fun `la meme fiche produit toujours le meme client`() {
        val etat = EtatFicheClient(nom = "Primeur Vasseur", ville = "Duclair")

        assertEquals(etat.versClient().id, etat.versClient().id)
    }

    @Test
    fun `ouvrir un client du carnet en reprend tous les champs`() {
        val client = Client(
            id = "cl-1",
            nom = "Boucherie Lemoine",
            ville = "Rouen",
            adresse = "12 rue des Carmes",
            telephone = "02 35 00 00 00",
        )

        val etat = EtatFicheClient.depuis(client)

        assertFalse("éditer n'est pas créer", etat.estCreation)
        assertEquals(client.id, etat.id)
        assertEquals("Boucherie Lemoine", etat.nom)
        assertEquals("Rouen", etat.ville)
        assertEquals("12 rue des Carmes", etat.adresse)
        assertEquals("02 35 00 00 00", etat.telephone)
    }

    /** Une édition réécrit la même ligne : l'identifiant doit traverser. */
    @Test
    fun `une edition conserve l'identifiant de la ligne`() {
        val client = Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen")

        val modifie = EtatFicheClient.depuis(client).copy(telephone = "0235000000").versClient()

        assertEquals("cl-1", modifie.id)
        assertEquals("0235000000", modifie.telephone)
    }
}
