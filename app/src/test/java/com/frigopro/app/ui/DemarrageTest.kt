package com.frigopro.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Le minutage de l'écran de démarrage.
 *
 * Deux erreurs symétriques sont possibles, et ce sont les deux seules : effacer
 * l'écran si vite qu'il clignote, ou le garder alors que l'application est déjà
 * prête — c'est-à-dire faire attendre pour rien quelqu'un qui ouvre
 * l'application vingt fois par jour.
 */
class DemarrageTest {

    @Test
    fun `tant que l'application n'est pas prete, rien n'est programme`() {
        assertNull(Demarrage.attenteRestante(Duration.ZERO, pret = false))
        assertNull(
            "un long chargement ne fait pas partir l'écran de démarrage",
            Demarrage.attenteRestante(10.seconds, pret = false),
        )
    }

    /**
     * Le cas du téléphone rapide, et la raison d'être du minimum : sans lui,
     * le logo apparaîtrait et disparaîtrait en deux images.
     */
    @Test
    fun `un demarrage instantane affiche quand meme le logo`() {
        assertEquals(
            Demarrage.MINIMUM,
            Demarrage.attenteRestante(Duration.ZERO, pret = true),
        )
        assertEquals(
            50.milliseconds,
            Demarrage.attenteRestante(Demarrage.MINIMUM - 50.milliseconds, pret = true),
        )
    }

    /**
     * Le cas inverse : un démarrage lent a déjà consommé le minimum, et n'a
     * donc plus rien à attendre. Une attente ajoutée là serait du temps volé.
     */
    @Test
    fun `un demarrage lent n'ajoute aucune attente`() {
        assertEquals(
            Duration.ZERO,
            Demarrage.attenteRestante(Demarrage.MINIMUM, pret = true),
        )
        assertEquals(
            Duration.ZERO,
            Demarrage.attenteRestante(4.seconds, pret = true),
        )
    }

    @Test
    fun `le demarrage reste bref`() {
        assertTrue(
            "un écran de démarrage d'une seconde se remarque, et pas en bien",
            Demarrage.MINIMUM + Demarrage.FONDU <= 1.seconds,
        )
    }
}
