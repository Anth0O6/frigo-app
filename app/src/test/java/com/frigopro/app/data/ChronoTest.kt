package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Le temps passé est ce que le technicien facture : un chronomètre qui perd
 * une reprise, ou qui recule, se paie en euros.
 */
class ChronoTest {

    private val depart: Instant = Instant.parse("2026-05-14T08:00:00Z")

    @Test
    fun `un chrono neuf ne compte rien`() {
        val chrono = Chrono()

        assertTrue(chrono.vierge)
        assertFalse(chrono.enMarche)
        assertEquals(Duration.ZERO, chrono.ecoulee(depart))
    }

    @Test
    fun `demarrer retient l'heure d'arrivee`() {
        val chrono = Chrono().demarrer(depart)

        assertEquals(depart, chrono.arriveeLe)
        assertTrue(chrono.enMarche)
    }

    @Test
    fun `en marche, le temps avance avec l'horloge`() {
        val chrono = Chrono().demarrer(depart)

        assertEquals(Duration.ofMinutes(42), chrono.ecoulee(depart.plusSeconds(42 * 60)))
    }

    /** Le cas réel : on diagnostique, on part chercher une pièce, on revient. */
    @Test
    fun `une reprise s'ajoute au temps deja compte`() {
        val chrono = Chrono()
            .demarrer(depart)
            .arreter(depart.plusSeconds(600))
            .demarrer(depart.plusSeconds(3600))

        assertEquals(Duration.ofSeconds(900), chrono.ecoulee(depart.plusSeconds(3900)))
    }

    @Test
    fun `en pause, le temps ne bouge plus`() {
        val chrono = Chrono().demarrer(depart).arreter(depart.plusSeconds(600))

        assertFalse(chrono.enMarche)
        assertEquals(Duration.ofSeconds(600), chrono.ecoulee(depart.plusSeconds(9999)))
    }

    /** L'heure d'arrivée est celle du premier démarrage, jamais réécrite. */
    @Test
    fun `reprendre ne deplace pas l'heure d'arrivee`() {
        val chrono = Chrono()
            .demarrer(depart)
            .arreter(depart.plusSeconds(600))
            .demarrer(depart.plusSeconds(3600))

        assertEquals(depart, chrono.arriveeLe)
    }

    @Test
    fun `demarrer un chrono deja en marche ne le redemarre pas`() {
        val chrono = Chrono().demarrer(depart).demarrer(depart.plusSeconds(600))

        assertEquals(depart, chrono.demarreLe)
        assertEquals(Duration.ofSeconds(600), chrono.ecoulee(depart.plusSeconds(600)))
    }

    @Test
    fun `arreter un chrono a l'arret ne change rien`() {
        val arrete = Chrono().demarrer(depart).arreter(depart.plusSeconds(600))

        assertEquals(arrete, arrete.arreter(depart.plusSeconds(9999)))
    }

    /**
     * Une horloge peut reculer — changement de fuseau, remise à l'heure par le
     * réseau. Le temps déjà acquis ne doit pas s'en trouver amputé.
     */
    @Test
    fun `une horloge qui recule ne retranche pas du temps acquis`() {
        val chrono = Chrono()
            .demarrer(depart)
            .arreter(depart.plusSeconds(600))
            .demarrer(depart.plusSeconds(600))

        assertEquals(Duration.ofSeconds(600), chrono.ecoulee(depart.plusSeconds(60)))
    }

    @Test
    fun `basculer alterne marche et pause`() {
        val enMarche = Chrono().basculer(depart)
        val enPause = enMarche.basculer(depart.plusSeconds(300))

        assertTrue(enMarche.enMarche)
        assertFalse(enPause.enMarche)
        assertNull(enPause.demarreLe)
        assertEquals(300L, enPause.cumuleS)
    }

    @Test
    fun `le chrono s'affiche en minutes, puis en heures`() {
        assertEquals("42:10", Duration.ofSeconds(2530).enChrono())
        assertEquals("00:07", Duration.ofSeconds(7).enChrono())
        assertEquals("1:05:30", Duration.ofSeconds(3930).enChrono())
    }

    @Test
    fun `une duree se dit comme on la dit`() {
        assertEquals("1 h 05", Duration.ofSeconds(3930).enDuree())
        assertEquals("45 min", Duration.ofMinutes(45).enDuree())
        assertEquals("0 min", Duration.ZERO.enDuree())
    }

    /**
     * Le cas du technicien qui a laissé le téléphone dans la camionnette :
     * le temps se saisit à la fin, et l'heure d'arrivée reste inconnue plutôt
     * que d'être déduite — elle partirait sinon sur un compte-rendu signé.
     */
    @Test
    fun `un temps saisi a la main n'invente pas l'heure d'arrivee`() {
        val pose = Chrono().poser(Duration.ofMinutes(95))

        assertEquals(95 * 60L, pose.cumuleS)
        assertEquals(Duration.ofMinutes(95), pose.ecoulee(depart))
        assertNull("rien n'autorise à dire à quelle heure il est arrivé", pose.arriveeLe)
        assertFalse(pose.enMarche)
        assertFalse(pose.vierge)
    }

    /** Corriger un temps chronométré ne doit pas effacer l'heure d'arrivée. */
    @Test
    fun `corriger un temps chronometre garde l'heure d'arrivee`() {
        val chronometre = Chrono().demarrer(depart).arreter(depart.plusSeconds(600))
        val corrige = chronometre.poser(Duration.ofMinutes(45))

        assertEquals(depart, corrige.arriveeLe)
        assertEquals(Duration.ofMinutes(45), corrige.ecoulee(depart.plusSeconds(600)))
    }

    /**
     * Poser un total pendant que le chrono tourne doit donner ce total, et non
     * ce total plus ce qui court : sans quoi le temps saisi grandirait tout seul.
     */
    @Test
    fun `poser un temps referme le segment en cours`() {
        val enMarche = Chrono().demarrer(depart)
        val pose = enMarche.poser(Duration.ofHours(2))

        assertFalse(pose.enMarche)
        assertEquals(Duration.ofHours(2), pose.ecoulee(depart.plusSeconds(3600)))
    }

    @Test
    fun `un temps negatif est ramene a zero`() {
        assertEquals(0L, Chrono().poser(Duration.ofMinutes(-30)).cumuleS)
    }
}
