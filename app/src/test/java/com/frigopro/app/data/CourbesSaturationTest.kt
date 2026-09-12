package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les courbes de saturation, et ce qu'on peut en vérifier sans réglette.
 *
 * Ces tests **ne disent pas que les valeurs sont justes** — seul un contrôle
 * contre une table constructeur peut le dire, et c'est le rôle de la vérification
 * par fluide. Ils disent autre chose, qui est vérifiable ici : que les données se
 * relisent toutes sans perdre un point, et qu'aucune ne viole une propriété
 * physique élémentaire.
 *
 * C'est utile précisément parce que ces valeurs ont été écrites à la main : une
 * coquille de frappe — un point-virgule au lieu d'un deux-points, un chiffre
 * transposé — serait autrement silencieuse.
 */
class CourbesSaturationTest {

    /**
     * Le garde-fou contre la coquille de frappe. `analyser` ignore un point mal
     * formé pour ne pas perdre tout un fluide ; du coup, une faute de frappe ne
     * se verrait pas — sauf ici.
     */
    @Test
    fun `chaque courbe livree se relit entierement`() {
        CourbesSaturation.fluidesCouverts.forEach { fluide ->
            val points = CourbesSaturation.points(fluide)
            assertTrue("$fluide : courbe vide", points.isNotEmpty())
            assertTrue(
                "$fluide : ${points.size} points seulement, un point a dû être mal écrit",
                points.size >= 15,
            )
        }
    }

    @Test
    fun `les temperatures montent, sans doublon`() {
        CourbesSaturation.fluidesCouverts.forEach { fluide ->
            val temperatures = CourbesSaturation.points(fluide).map { it.temperatureC }
            assertEquals(
                "$fluide : une température apparaît deux fois",
                temperatures.size,
                temperatures.distinct().size,
            )
            assertEquals("$fluide : températures non triées", temperatures.sorted(), temperatures)
        }
    }

    /**
     * La pression de saturation croît avec la température, toujours, pour tout
     * fluide. Un chiffre transposé — 10,17 écrit 11,07 — casse cette monotonie
     * une fois sur deux, et c'est le filet le plus efficace qu'on puisse tendre
     * sans table de référence.
     */
    @Test
    fun `la pression croit avec la temperature`() {
        CourbesSaturation.fluidesCouverts.forEach { fluide ->
            val points = CourbesSaturation.points(fluide)
            points.zipWithNext().forEach { (bas, haut) ->
                assertTrue(
                    "$fluide : bulle non croissante entre ${bas.temperatureC} et ${haut.temperatureC} °C",
                    haut.bulleBarAbs > bas.bulleBarAbs,
                )
                assertTrue(
                    "$fluide : rosée non croissante entre ${bas.temperatureC} et ${haut.temperatureC} °C",
                    haut.roseeBarAbs > bas.roseeBarAbs,
                )
            }
        }
    }

    /**
     * À température donnée, la pression de bulle est supérieure ou égale à celle
     * de rosée. L'inverse n'a pas de sens physique et signalerait deux colonnes
     * inversées — l'erreur exactement la plus coûteuse, puisqu'elle échangerait
     * surchauffe et sous-refroidissement.
     */
    @Test
    fun `la bulle n'est jamais sous la rosee`() {
        CourbesSaturation.fluidesCouverts.forEach { fluide ->
            CourbesSaturation.points(fluide).forEach { point ->
                assertTrue(
                    "$fluide à ${point.temperatureC} °C : bulle ${point.bulleBarAbs} < rosée ${point.roseeBarAbs}",
                    point.bulleBarAbs >= point.roseeBarAbs,
                )
            }
        }
    }

    @Test
    fun `un corps pur n'a pas de glissement`() {
        listOf("R134A", "R32", "R22", "R290", "R744").forEach { fluide ->
            CourbesSaturation.points(fluide).forEach { point ->
                assertEquals(
                    "$fluide est un corps pur : bulle et rosée se confondent",
                    point.bulleBarAbs,
                    point.roseeBarAbs,
                    0.0001,
                )
            }
        }
    }

    /**
     * Le glissement d'un R-407C dépasse 4 K, et c'est toute la raison pour
     * laquelle la réglette distingue bulle et rosée. Si ce test tombait à zéro,
     * la distinction serait devenue décorative.
     */
    @Test
    fun `un melange a glissement le montre`() {
        val lecture = CourbesSaturation.temperatureA("R407C", pressionBarAbs = 4.69)

        assertNotNull(lecture)
        assertTrue(
            "le glissement du R-407C doit être notable, lu ${lecture!!.glissementK} K",
            lecture.glissementNotable,
        )
        assertTrue("la rosée est plus chaude que la bulle", lecture.glissementK > 0)
    }

    @Test
    fun `la lecture retombe sur un point de la table`() {
        // 7,38 bar abs est exactement le point 0 °C du R-410A.
        val lecture = CourbesSaturation.temperatureA("R410A", pressionBarAbs = 7.38)

        assertNotNull(lecture)
        assertEquals(0.0, lecture!!.temperatureBulleC, 0.05)
    }

    @Test
    fun `la lecture interpole entre deux points`() {
        // Entre 0 °C (7,38) et 5 °C (8,58) : 7,98 doit donner environ 2,5 °C.
        val lecture = CourbesSaturation.temperatureA("R410A", pressionBarAbs = 7.98)

        assertNotNull(lecture)
        assertEquals(2.5, lecture!!.temperatureBulleC, 0.2)
    }

    /**
     * Hors plage, la réglette se taît. C'est la même règle que le GWP d'un fluide
     * inconnu : une case vide vaut mieux qu'un chiffre faux, et c'est justement
     * aux extrêmes qu'on la consulte.
     */
    @Test
    fun `hors de la plage saisie, rien n'est extrapole`() {
        assertNull("trop bas", CourbesSaturation.temperatureA("R410A", pressionBarAbs = 0.5))
        assertNull("trop haut", CourbesSaturation.temperatureA("R410A", pressionBarAbs = 60.0))
    }

    @Test
    fun `un fluide sans courbe ne rend rien`() {
        assertFalse(CourbesSaturation.couvert("R438A"))
        assertNull(CourbesSaturation.temperatureA("R438A", pressionBarAbs = 5.0))
        assertTrue(CourbesSaturation.points("R438A").isEmpty())
    }

    @Test
    fun `le nom du fluide se normalise comme ailleurs`() {
        assertTrue(CourbesSaturation.couvert("r-410a"))
        assertTrue(CourbesSaturation.couvert(" R410A "))
        assertEquals(
            CourbesSaturation.temperatureA("R410A", 7.38)?.temperatureBulleC,
            CourbesSaturation.temperatureA("r-410a", 7.38)?.temperatureBulleC,
        )
    }

    @Test
    fun `la reglette marche aussi dans l'autre sens`() {
        val (bulle, rosee) = CourbesSaturation.pressionA("R410A", temperatureC = 0.0)!!

        assertEquals(7.38, bulle, 0.05)
        assertEquals(7.38, rosee, 0.05)
    }

    /**
     * Le CO₂ n'a plus de saturation au-delà de 31 °C. La table s'arrête donc
     * avant : au-dessus, l'installation est transcritique et une température de
     * saturation n'existe pas — c'est là qu'un chiffre inventé serait le plus
     * nuisible.
     */
    @Test
    fun `la courbe du CO2 s'arrete au point critique`() {
        val derniere = CourbesSaturation.points("R744").last().temperatureC

        assertTrue(
            "la table du R-744 monte à $derniere °C, au-delà du point critique",
            derniere <= CourbesSaturation.TEMPERATURE_CRITIQUE_R744_C,
        )
    }

    /**
     * Tout fluide couvert doit aussi avoir un GWP : les deux tables décrivent le
     * même catalogue, et un fluide présent dans l'une et absent de l'autre
     * trahirait une faute de frappe sur le nom — « R499A » pour « R449A » —, qui
     * ferait silencieusement disparaître la courbe.
     */
    @Test
    fun `tout fluide couvert est un fluide connu`() {
        CourbesSaturation.fluidesCouverts.forEach { fluide ->
            assertNotNull("$fluide a une courbe mais pas de GWP", Fluides.gwp(fluide))
        }
    }

    @Test
    fun `l'absolu et le relatif se convertissent dans les deux sens`() {
        assertEquals(7.38, 6.367.enBarAbsolus(), 0.001)
        assertEquals(6.367, 7.38.enBarRelatifs(), 0.001)
        assertEquals(5.0, 5.0.enBarAbsolus().enBarRelatifs(), 0.0001)
    }
}
