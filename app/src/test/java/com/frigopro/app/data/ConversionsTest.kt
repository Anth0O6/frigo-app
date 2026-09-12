package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le convertisseur d'unités.
 *
 * Les repères choisis sont ceux qu'un frigoriste connaît par cœur — 0 °C vaut
 * 32 °F, un bar vaut 14,5 psi, un kilowatt vaut 3 412 BTU/h — parce qu'un test
 * dont on peut vérifier la valeur attendue de tête est un test qui se relit.
 */
class ConversionsTest {

    private fun conv(valeur: Double, de: Unite, vers: Unite): Double =
        Conversions.convertir(valeur, de, vers)!!

    // — Pression ————————————————————————————————————————————————————————————

    @Test
    fun `le bar et le psi`() {
        assertEquals(14.5038, conv(1.0, Unite.BAR, Unite.PSI), 0.001)
        assertEquals(1.0, conv(14.5038, Unite.PSI, Unite.BAR), 0.0001)
        // Une BP courante au manomètre américain.
        assertEquals(4.83, conv(70.0, Unite.PSI, Unite.BAR), 0.01)
    }

    @Test
    fun `le bar, le pascal et ses multiples`() {
        assertEquals(100.0, conv(1.0, Unite.BAR, Unite.KILOPASCAL), 0.0001)
        assertEquals(0.1, conv(1.0, Unite.BAR, Unite.MEGAPASCAL), 0.0001)
        assertEquals(1000.0, conv(1.0, Unite.BAR, Unite.MILLIBAR), 0.0001)
    }

    // — Température, et ce qui la distingue d'un écart ——————————————————————

    /**
     * Le repère le plus connu, et celui qui attrape un décalage d'origine oublié :
     * sans le décalage, 0 °C donnerait 0 °F.
     */
    @Test
    fun `zero degre Celsius vaut trente-deux Fahrenheit`() {
        assertEquals(32.0, conv(0.0, Unite.CELSIUS, Unite.FAHRENHEIT), 0.0001)
        assertEquals(212.0, conv(100.0, Unite.CELSIUS, Unite.FAHRENHEIT), 0.0001)
        assertEquals(-40.0, conv(-40.0, Unite.CELSIUS, Unite.FAHRENHEIT), 0.0001)
    }

    @Test
    fun `le Kelvin est le Celsius decale de l'absolu`() {
        assertEquals(273.15, conv(0.0, Unite.CELSIUS, Unite.KELVIN), 0.0001)
        assertEquals(-273.15, conv(0.0, Unite.KELVIN, Unite.CELSIUS), 0.0001)
    }

    /**
     * **La distinction qui compte.** Une température de 10 °C vaut 50 °F, mais un
     * écart de 10 K vaut 18 °F. Les confondre dans un convertisseur généraliste
     * donne une surchauffe fausse de 32 — l'erreur ne se rattrape pas, elle se lit
     * comme un relevé plausible.
     */
    @Test
    fun `un ecart de temperature ne porte pas le decalage d'une temperature`() {
        assertEquals(50.0, conv(10.0, Unite.CELSIUS, Unite.FAHRENHEIT), 0.0001)
        assertEquals(18.0, conv(10.0, Unite.ECART_CELSIUS, Unite.ECART_FAHRENHEIT), 0.0001)
        assertEquals(
            "un écart en kelvins est un écart en degrés Celsius",
            7.0,
            conv(7.0, Unite.ECART_KELVIN, Unite.ECART_CELSIUS),
            0.0001,
        )
    }

    /** Et les deux familles ne se mélangent pas : la conversion est refusée. */
    @Test
    fun `une temperature ne se convertit pas en ecart`() {
        assertNull(Conversions.convertir(10.0, Unite.CELSIUS, Unite.ECART_FAHRENHEIT))
        assertNull(Conversions.convertir(10.0, Unite.ECART_KELVIN, Unite.KELVIN))
    }

    // — Puissance ———————————————————————————————————————————————————————————

    @Test
    fun `le kilowatt et le BTU par heure`() {
        assertEquals(3412.14, conv(1.0, Unite.KILOWATT, Unite.BTU_PAR_HEURE), 0.1)
        // Une plaque de climatiseur américaine : 12 000 BTU/h, le « 1 tonne ».
        assertEquals(3.517, conv(12000.0, Unite.BTU_PAR_HEURE, Unite.KILOWATT), 0.01)
    }

    /** La frigorie, qui se dit encore et que portent les plaques d'avant 1980. */
    @Test
    fun `le kilowatt et la frigorie par heure`() {
        assertEquals(859.845, conv(1.0, Unite.KILOWATT, Unite.KCAL_PAR_HEURE), 0.01)
        assertEquals(11.63, conv(10000.0, Unite.KCAL_PAR_HEURE, Unite.KILOWATT), 0.01)
    }

    @Test
    fun `la tonne de froid vaut douze mille BTU par heure`() {
        assertEquals(
            12000.0,
            conv(1.0, Unite.TONNE_DE_FROID, Unite.BTU_PAR_HEURE),
            2.0,
        )
    }

    // — Débit, masse, longueur, vitesse —————————————————————————————————————

    @Test
    fun `le metre cube par heure et le litre par seconde`() {
        assertEquals(1.0, conv(3.6, Unite.M3_PAR_HEURE, Unite.LITRE_PAR_SECONDE), 0.0001)
        assertEquals(1700.0, conv(1000.0, Unite.PIED3_PAR_MINUTE, Unite.M3_PAR_HEURE), 1.0)
    }

    @Test
    fun `le kilogramme et la livre`() {
        assertEquals(2.2046, conv(1.0, Unite.KILOGRAMME, Unite.LIVRE), 0.0001)
        // Une bouteille de 25 lb, comme on en reçoit.
        assertEquals(11.34, conv(25.0, Unite.LIVRE, Unite.KILOGRAMME), 0.01)
    }

    @Test
    fun `le pouce est exactement vingt-cinq virgule quatre millimetres`() {
        assertEquals(25.4, conv(1.0, Unite.POUCE, Unite.MILLIMETRE), 0.000001)
        // Du tube 3/8", le plus courant.
        assertEquals(9.525, conv(0.375, Unite.POUCE, Unite.MILLIMETRE), 0.0001)
    }

    @Test
    fun `le metre par seconde et le pied par minute`() {
        assertEquals(196.85, conv(1.0, Unite.METRE_PAR_SECONDE, Unite.PIED_PAR_MINUTE), 0.01)
    }

    // — Propriétés générales ————————————————————————————————————————————————

    /**
     * L'aller-retour est le filet le plus large : il attrape un facteur inversé
     * dans n'importe quelle unité, sans avoir à connaître la valeur attendue.
     */
    @Test
    fun `tout aller-retour revient a son point de depart`() {
        FamilleUnite.entries.forEach { famille ->
            val unites = Conversions.unites(famille)
            unites.forEach { depart ->
                unites.forEach { arrivee ->
                    val aller = conv(37.5, depart, arrivee)
                    val retour = conv(aller, arrivee, depart)
                    assertEquals(
                        "${famille.name} : ${depart.symbole} → ${arrivee.symbole} → ${depart.symbole}",
                        37.5,
                        retour,
                        0.00001,
                    )
                }
            }
        }
    }

    /** Convertir une unité en elle-même ne doit rien changer, décalage compris. */
    @Test
    fun `une unite vers elle-meme est l'identite`() {
        Unite.entries.forEach { unite ->
            assertEquals(unite.symbole, 12.5, conv(12.5, unite, unite), 0.00001)
        }
    }

    /**
     * Chaque famille a au moins deux unités — sans quoi elle n'aurait rien à
     * convertir — et sa référence est bien de la famille.
     */
    @Test
    fun `chaque famille a sa reference et de quoi convertir`() {
        FamilleUnite.entries.forEach { famille ->
            val unites = Conversions.unites(famille)
            assertTrue("${famille.name} n'a qu'une unité", unites.size >= 2)
            val reference = Conversions.reference(famille)
            assertEquals(famille, reference.famille)
            assertEquals("la référence a un facteur de 1", 1.0, reference.facteur, 0.0)
            assertEquals("et aucun décalage", 0.0, reference.decalage, 0.0)
        }
    }

    /** Les familles du projet : le bar, le kilogramme, celles du reste du code. */
    @Test
    fun `les references sont celles qu'emploie le reste de l'application`() {
        assertEquals(Unite.BAR, Conversions.reference(FamilleUnite.PRESSION))
        assertEquals(Unite.CELSIUS, Conversions.reference(FamilleUnite.TEMPERATURE))
        assertEquals(Unite.KILOGRAMME, Conversions.reference(FamilleUnite.MASSE))
        assertEquals(Unite.KILOWATT, Conversions.reference(FamilleUnite.PUISSANCE))
    }

    @Test
    fun `deux familles differentes ne se convertissent pas`() {
        assertNull(Conversions.convertir(1.0, Unite.BAR, Unite.KILOWATT))
        assertNull(Conversions.convertir(1.0, Unite.KILOGRAMME, Unite.METRE))
        assertNotNull(Conversions.convertir(1.0, Unite.BAR, Unite.PSI))
    }
}
