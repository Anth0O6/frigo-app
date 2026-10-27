package com.frigopro.app.ui.composants

import com.frigopro.app.data.Depannage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'arithmétique des instruments : où tombe le curseur, et ce qu'il dit.
 *
 * Un curseur mal placé sur une réglette ferait lire une surchauffe normale
 * comme faible, et ce défaut ne se voit pas à l'écran — il a l'air juste.
 */
class PlageMesureTest {

    private val surchauffe = PlageMesure(
        min = 0.0,
        max = 12.0,
        habituelBas = Depannage.SURCHAUFFE_FAIBLE_K,
        habituelHaut = Depannage.SURCHAUFFE_ELEVEE_K,
    )

    @Test
    fun `le curseur tombe a sa valeur sur l'echelle`() {
        assertEquals(0f, surchauffe.fraction(0.0), 1e-6f)
        assertEquals(0.5f, surchauffe.fraction(6.0), 1e-6f)
        assertEquals(1f, surchauffe.fraction(12.0), 1e-6f)
    }

    @Test
    fun `hors echelle le curseur bute au bord sans disparaitre`() {
        assertEquals(1f, surchauffe.fraction(20.0), 1e-6f)
        assertEquals(0f, surchauffe.fraction(-3.0), 1e-6f)
        assertTrue(surchauffe.horsEchelle(20.0))
        assertFalse(surchauffe.horsEchelle(12.0))
    }

    @Test
    fun `une echelle qui ne part pas de zero se lit depuis son minimum`() {
        val plage = PlageMesure(min = -10.0, max = 30.0)
        assertEquals(0.25f, plage.fraction(0.0), 1e-6f)
    }

    @Test
    fun `la zone habituelle est celle de l'aide au depannage, bornes comprises`() {
        assertEquals(true, surchauffe.habituelle(Depannage.SURCHAUFFE_FAIBLE_K))
        assertEquals(true, surchauffe.habituelle(Depannage.SURCHAUFFE_ELEVEE_K))
        assertEquals(false, surchauffe.habituelle(2.9))
        assertEquals(false, surchauffe.habituelle(8.1))
    }

    @Test
    fun `sans zone habituelle on ne dit pas que la valeur est normale`() {
        assertNull(PlageMesure(min = 0.0, max = 10.0).habituelle(4.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `une echelle sans etendue est refusee`() {
        PlageMesure(min = 5.0, max = 5.0)
    }
}
