package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La réduction est le seul traitement d'image qui se trompe en silence : une
 * photo deux fois trop petite reste une photo, et la plaque n'y est plus
 * lisible. D'où ces cas, sur l'arithmétique seule.
 */
class ReductionPhotoTest {

    @Test
    fun `une photo deja petite n'est pas touchee`() {
        assertEquals(1, ReductionPhoto.facteurEchantillonnage(1024, 768, coteMax = 2048))
        assertEquals(1024 to 768, ReductionPhoto.dimensionsReduites(1024, 768, coteMax = 2048))
    }

    /**
     * 4000 pixels pour une cible de 2048 : échantillonner par deux donnerait
     * 2000, sous la cible. Le décodage reste donc entier et c'est la mise à
     * l'échelle qui finit le travail.
     */
    @Test
    fun `le facteur laisse le grand cote au-dessus de la cible`() {
        assertEquals(1, ReductionPhoto.facteurEchantillonnage(4000, 3000, coteMax = 2048))
        assertEquals(2, ReductionPhoto.facteurEchantillonnage(8000, 6000, coteMax = 2048))
        assertEquals(8, ReductionPhoto.facteurEchantillonnage(9000, 12000, coteMax = 1024))
    }

    @Test
    fun `le facteur est toujours une puissance de deux`() {
        for (cote in listOf(100, 999, 2049, 3000, 5000, 12000)) {
            val facteur = ReductionPhoto.facteurEchantillonnage(cote, cote / 2)
            assertTrue("$facteur n'est pas une puissance de deux", facteur.countOneBits() == 1)
        }
    }

    @Test
    fun `la reduction conserve les proportions`() {
        val (largeur, hauteur) = ReductionPhoto.dimensionsReduites(4000, 3000, coteMax = 2048)

        assertEquals(2048, largeur)
        assertEquals(1536, hauteur)
    }

    @Test
    fun `une photo en hauteur se mesure sur son grand cote`() {
        assertEquals(1536 to 2048, ReductionPhoto.dimensionsReduites(3000, 4000, coteMax = 2048))
    }

    /** Une image très allongée ne doit pas se réduire à une dimension nulle. */
    @Test
    fun `un cote ne tombe jamais a zero`() {
        val (largeur, hauteur) = ReductionPhoto.dimensionsReduites(10_000, 3, coteMax = 2048)

        assertEquals(2048, largeur)
        assertEquals(1, hauteur)
    }

    /** Des bornes absurdes ne doivent pas faire boucler ni diviser par zéro. */
    @Test
    fun `des dimensions invalides sont rendues telles quelles`() {
        assertEquals(1, ReductionPhoto.facteurEchantillonnage(0, 0))
        assertEquals(1, ReductionPhoto.facteurEchantillonnage(4000, 3000, coteMax = 0))
        assertEquals(0 to 0, ReductionPhoto.dimensionsReduites(0, 0))
    }
}
