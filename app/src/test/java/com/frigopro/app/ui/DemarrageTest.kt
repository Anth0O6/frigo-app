package com.frigopro.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Le minutage de l'écran de démarrage, et sa chorégraphie.
 *
 * Deux erreurs symétriques guettaient le minutage, et ce sont les deux seules :
 * effacer l'écran si vite qu'il clignote, ou garder quelqu'un devant alors que
 * l'application est prête. Depuis qu'il y a une animation, une troisième
 * apparaît et c'est celle que surveille `la choregraphie tient dans le temps
 * affiche` : une chorégraphie plus longue que le temps disponible ferait partir
 * l'écran au milieu d'un mouvement, ce qui se voit et se voit mal.
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
     * l'animation n'aurait jamais le temps de se dérouler.
     */
    @Test
    fun `un demarrage instantane laisse toute sa place a l'animation`() {
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
            Demarrage.attenteRestante(8.seconds, pret = true),
        )
    }

    /**
     * L'invariant de la chorégraphie : aucun mouvement ne doit être en cours au
     * moment où l'écran s'efface. Allonger une phase sans allonger le minimum
     * couperait l'anneau en plein tour.
     */
    @Test
    fun `la choregraphie tient dans le temps affiche`() {
        val debordent = Demarrage.CHOREGRAPHIE.filter { it.fin > Demarrage.MINIMUM }
        assertEquals(
            "une phase déborde du temps affiché : l'écran partirait en plein mouvement",
            emptyList<Demarrage.Phase>(),
            debordent,
        )

        Demarrage.CHOREGRAPHIE.forEach { phase ->
            assertEquals(
                "à l'instant où l'écran part, tout doit être arrivé",
                1f,
                Demarrage.avancement(Demarrage.MINIMUM, phase),
                0f,
            )
        }
    }

    /** Chaque phase reste bornée : rien ne commence avant l'heure ni ne repart. */
    @Test
    fun `une phase ne deborde pas de sa fenetre`() {
        val nom = Demarrage.NOM

        assertEquals("avant sa fenêtre, rien", 0f, Demarrage.avancement(Duration.ZERO, nom), 0f)
        assertEquals(
            "juste avant son départ, rien non plus",
            0f,
            Demarrage.avancement(nom.depart - 1.milliseconds, nom),
            0f,
        )
        assertEquals(
            "à mi-chemin, la moitié",
            0.5f,
            Demarrage.avancement(nom.depart + nom.duree / 2, nom),
            0.001f,
        )
        assertEquals("à sa fin, tout", 1f, Demarrage.avancement(nom.fin, nom), 0f)
        assertEquals(
            "l'écran peut s'attarder sans que le mouvement reparte",
            1f,
            Demarrage.avancement(1.seconds + nom.fin, nom),
            0f,
        )
    }

    /**
     * L'ordre de lecture : on voit le logo, puis son nom, puis — à peine — de
     * quelle build il s'agit. L'inverse donnerait un numéro de version seul à
     * l'écran, ce qui ne veut rien dire.
     */
    @Test
    fun `le logo paraît avant le nom, et le nom avant la version`() {
        assertTrue(Demarrage.LOGO.depart < Demarrage.NOM.depart)
        assertTrue(Demarrage.NOM.depart < Demarrage.VERSION.depart)
        assertTrue(
            "le nom doit être lisible avant que la version ne paraisse",
            Demarrage.NOM.fin <= Demarrage.VERSION.fin,
        )
    }

    /**
     * Le garde-fou de l'allongement : l'écran a été rallongé pour l'animation,
     * et c'est un choix. Trois secondes resteraient un démarrage ; au-delà, ce
     * serait une attente, et sur un toit elle se compte.
     */
    @Test
    fun `le demarrage reste un demarrage`() {
        assertTrue(
            "un écran de démarrage de plus de trois secondes n'est plus un démarrage",
            Demarrage.MINIMUM + Demarrage.FONDU <= 3.seconds,
        )
    }
}
