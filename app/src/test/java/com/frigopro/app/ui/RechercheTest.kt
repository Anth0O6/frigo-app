package com.frigopro.app.ui

import org.junit.Assert.assertFalse
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
}
