package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les initiales, une seule fois pour quatre écrans.
 *
 * Quatre endroits les dérivaient chacun à sa façon, et elles divergeaient déjà :
 * l'un coupait sur l'apostrophe, l'autre non. Ce test tient la seule version qui
 * reste — un changement ici se voit partout, ce qui est exactement le but.
 */
class InitialesTest {

    @Test
    fun `deux mots donnent deux lettres`() {
        assertEquals("KB", initialesDe("Karim Benali"))
        assertEquals("BM", initialesDe("Boucherie Martel"))
    }

    @Test
    fun `la casse de la saisie n'y change rien`() {
        assertEquals("AO", initialesDe("anthony ouvrard"))
    }

    @Test
    fun `un mot seul donne une lettre`() {
        assertEquals("K", initialesDe("Karim"))
    }

    @Test
    fun `un nom compose compte pour deux`() {
        assertEquals("JP", initialesDe("Jean-Pierre"))
    }

    /**
     * Le cas qui faisait diverger les quatre versions : « L'Épicerie du coin »
     * donnait « L » chez l'une et « LÉ » chez l'autre, pour le même client.
     */
    @Test
    fun `l'apostrophe coupe, droite comme courbe`() {
        assertEquals("LÉ", initialesDe("L'Épicerie du coin"))
        assertEquals("LÉ", initialesDe("L’Épicerie du coin"))
    }

    @Test
    fun `au-dela de deux mots on n'ajoute rien`() {
        assertEquals("JP", initialesDe("Jean Pierre Martin"))
    }

    @Test
    fun `rien a abreger donne un point d'interrogation`() {
        assertEquals("une pastille vide ressemblerait à un défaut d'affichage", "?", initialesDe("   "))
        assertEquals("?", initialesDe(""))
    }
}
