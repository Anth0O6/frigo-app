package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.GroupeMachines
import com.frigopro.app.data.NatureFacture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce qu'une recherche doit trouver, et ce qu'elle ne doit pas confondre.
 *
 * Elle est éprouvée à part des écrans qui s'en servent, parce que c'est la même
 * pour les trois et que c'est là que les surprises se trouvent : un accent, un
 * tiret dans un numéro, deux mots dans l'ordre inverse.
 */
class RechercheTest {

    /** Le cas normal : l'écran filtre sans avoir à se demander s'il cherche. */
    @Test
    fun `une recherche vide laisse tout passer`() {
        assertTrue(Recherche.correspond("", "Carrefour"))
        assertTrue(Recherche.correspond("   ", "Carrefour"))
    }

    /**
     * Un technicien tape sans accents, une main sur une lampe. Refuser de
     * trouver « Péan » sur « pean » serait exact et inutile.
     */
    @Test
    fun `les accents se replient dans les deux sens`() {
        assertTrue(Recherche.correspond("pean", "Boucherie Péan"))
        assertTrue(Recherche.correspond("Péan", "boucherie pean"))
        assertTrue(Recherche.correspond("elise", "Élise Traiteur"))
    }

    /**
     * On se souvient d'une fiche, pas de la façon dont elle est rédigée : les
     * mots se cherchent séparément, dans n'importe quel ordre, et peuvent tomber
     * dans des champs différents.
     */
    @Test
    fun `les mots se cherchent separement et dans n'importe quel ordre`() {
        assertTrue(Recherche.correspond("toit carrefour", "Carrefour Market", "Clim de toiture"))
        assertTrue(Recherche.correspond("carrefour toit", "Carrefour Market", "Clim de toiture"))
    }

    /** Tous les mots doivent être là : « et » ne se change pas en « ou ». */
    @Test
    fun `un mot absent fait echouer la recherche entiere`() {
        assertFalse(Recherche.correspond("carrefour lidl", "Carrefour Market", "Clim de toiture"))
    }

    /**
     * Un numéro s'écrit « DEV-2609-007 » et se tape « dev 2609 » : exiger la
     * ponctuation exacte pour retrouver un document par son numéro serait
     * l'inverse du service rendu.
     */
    @Test
    fun `un numero se retrouve sans sa ponctuation`() {
        assertTrue(Recherche.correspond("dev 2609", "DEV-2609-007"))
        assertTrue(Recherche.correspond("2609-007", "DEV-2609-007"))
        assertTrue(Recherche.correspond("DEV2609", "DEV-2609-007"))
    }

    /** Un champ absent ne doit ni faire échouer ni faire réussir la recherche. */
    @Test
    fun `un champ nul est simplement ignore`() {
        assertTrue(Recherche.correspond("carrefour", null, "Carrefour Market"))
        assertFalse(Recherche.correspond("carrefour", null, null))
    }

    /** La recherche ne trouve pas ce qui n'y est pas. */
    @Test
    fun `rien ne se trouve dans un texte qui ne le contient pas`() {
        assertFalse(Recherche.correspond("chambre froide", "Carrefour Market", "Clim de toiture"))
    }

    /**
     * **La zone est cherchable**, et c'est la seconde moitié de la réponse aux
     * cinquante machines d'une ronde journalière : le groupement par zone dit
     * l'ordre dans lequel marcher, la recherche isole la zone où l'on se trouve
     * déjà. Taper « toiture » rend le toit, sans qu'il ait fallu un filtre de
     * plus à l'écran — et « toiture clim » le restreint encore, puisque les mots
     * se cherchent séparément et dans n'importe lequel des champs.
     */
    @Test
    fun `une zone se cherche comme le reste, accents replies`() {
        assertTrue(Recherche.correspond("toiture", "Rooftop 1", "Toiture"))
        assertTrue(Recherche.correspond("surgeles", "Vitrine 2", "Linéaire surgelés"))
        assertTrue(
            "les mots traversent les champs",
            Recherche.correspond("toiture rooftop", "Rooftop 1", "Toiture"),
        )
        assertFalse(
            "et une zone qui n'est pas la sienne ne la trouve pas",
            Recherche.correspond("cuisine", "Rooftop 1", "Toiture"),
        )
    }

    /**
     * Le carnet trouve un client **par la zone** d'une de ses machines, comme
     * l'Entretien : la même saisie ne doit pas trouver dans une vue et pas dans
     * l'autre.
     */
    @Test
    fun `le carnet trouve un client par la zone de ses machines`() {
        val carrefour = Client(id = "cl-1", nom = "Carrefour Vaise", ville = "Lyon")
        val parc = listOf(
            GroupeMachines(Equipement(id = "r1", clientId = "cl-1", nom = "Rooftop 1", zone = "Toiture")),
        )

        assertTrue(correspondAuCarnet(carrefour, parc, "toiture"))
        assertTrue("les mots traversent client et machine", correspondAuCarnet(carrefour, parc, "vaise toit"))
        assertFalse(correspondAuCarnet(carrefour, parc, "cuisine"))
    }

    /**
     * Une recherche qui désigne des machines ne montre **qu'elles** ; une
     * recherche qui désigne le client, ou rien, laisse tout le parc. Une unité
     * intérieure trouvée ramène son groupe, le seul que le carnet montre.
     */
    @Test
    fun `le parc trouve ne garde que les machines designees`() {
        val carrefour = Client(id = "cl-1", nom = "Carrefour Vaise", ville = "Lyon")
        val vitrine = GroupeMachines(Equipement(id = "v12", clientId = "cl-1", nom = "Vitrine 12"))
        val autre = GroupeMachines(Equipement(id = "v3", clientId = "cl-1", nom = "Vitrine 3"))
        val split = GroupeMachines(
            groupe = Equipement(id = "g1", clientId = "cl-1", nom = "Groupe bureaux"),
            unites = listOf(Equipement(id = "u1", clientId = "cl-1", parentId = "g1", nom = "Chambre 3")),
        )
        val parc = listOf(vitrine, autre, split)

        assertEquals(listOf(vitrine), parcTrouve(carrefour, parc, "vitrine 12"))
        assertEquals("le client prête ses mots", listOf(vitrine), parcTrouve(carrefour, parc, "vaise vitrine 12"))
        assertEquals("l'unité ramène son groupe", listOf(split), parcTrouve(carrefour, parc, "chambre"))
        assertNull("une recherche vide ne filtre rien", parcTrouve(carrefour, parc, " "))
        assertNull("chercher le client, c'est demander tout son parc", parcTrouve(carrefour, parc, "carrefour"))
        assertNull("rien de trouvé, rien de filtré", parcTrouve(carrefour, parc, "pompe"))
    }

    /**
     * Un avoir se trouve par son numéro, et par celui de la facture qu'il
     * corrige : un client qui conteste cite le numéro qu'il a reçu en premier.
     */
    @Test
    fun `un avoir se trouve par son numero et par celui de sa facture`() {
        val avoir = FactureChiffree(
            Facture(
                numero = "AV-2026-0001",
                clientNom = "Boucherie Morel",
                nature = NatureFacture.AVOIR,
                factureOrigineId = "fac-1",
                factureOrigineNumero = "FAC-2026-0042",
            ),
            totalHt = 100.0,
        )
        val facture = FactureChiffree(Facture(numero = "FAC-2026-0042"), totalHt = 100.0)

        assertTrue(avoir.correspondA("av 2026 0001"))
        assertTrue(avoir.correspondA("FAC-2026-0042"))
        assertTrue("le mot « avoir » les isole", avoir.correspondA("avoir morel"))
        assertFalse("une facture n'est pas un avoir", facture.correspondA("avoir"))
    }
}
