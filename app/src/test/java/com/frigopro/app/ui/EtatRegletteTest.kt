package com.frigopro.app.ui

import com.frigopro.app.data.CourbesSaturation
import com.frigopro.app.data.enBarRelatifs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La réglette, et la seule erreur qui casse un compresseur.
 *
 * Ces tests ne vérifient pas que les courbes sont justes — elles sont calculées,
 * et [CourbesSaturationTest] dit ce qui reste vérifiable sans les recalculer. Ils
 * vérifient ce qui est entièrement de notre ressort : que la **bonne colonne** est
 * prise de chaque côté du circuit, et que rien d'incomplet ou de hors plage
 * n'entre dans un relevé.
 */
class EtatRegletteTest {

    /**
     * L'erreur qui motive tout le reste : prendre la bulle pour la rosée.
     *
     * Sur un R-448A, dont le glissement approche 5 K, la surchauffe calculée sur
     * la bulle est fausse de tout le glissement — et c'est dans le sens qui fait
     * croire à une surchauffe suffisante quand elle ne l'est pas. Le détendeur est
     * alors ouvert davantage, et c'est du liquide qui arrive au compresseur.
     */
    @Test
    fun `la surchauffe se calcule sur la rosee`() {
        // R-448A à 0 °C : bulle 6,23 bar abs, rosée 5,11 bar abs. À la pression de
        // bulle, la rosée est donc nettement plus chaude que 0 °C.
        val pression = 6.23.enBarRelatifs()
        val lecture = CourbesSaturation.temperatureA("R448A", 6.23)!!
        val etat = EtatReglette(
            fluide = "R448A",
            pressionRelativeBar = pression,
            cote = CoteCircuit.ASPIRATION,
            temperatureLigneC = 10.0,
        )

        assertEquals(lecture.temperatureRoseeC, etat.temperatureSaturationC)
        assertEquals(
            "la surchauffe part de la rosée, pas de la bulle",
            10.0 - lecture.temperatureRoseeC,
            etat.ecartK!!,
            0.05,
        )
        assertTrue(
            "et l'écart entre les deux colonnes est assez grand pour compter",
            lecture.glissementNotable,
        )
    }

    /**
     * Au refoulement, c'est l'inverse : le fluide y est liquide, donc la bulle
     * fait référence. Prendre la rosée sous-estimerait le sous-refroidissement.
     */
    @Test
    fun `le sous-refroidissement se calcule sur la bulle`() {
        // 18,83 bar abs est le point 40 °C du R-448A côté bulle : une condensation
        // ordinaire, dont le liquide ressort à 34 °C.
        val lecture = CourbesSaturation.temperatureA("R448A", 18.83)!!
        val etat = EtatReglette(
            fluide = "R448A",
            pressionRelativeBar = 18.83.enBarRelatifs(),
            cote = CoteCircuit.REFOULEMENT,
            temperatureLigneC = 34.0,
        )

        assertEquals(lecture.temperatureBulleC, etat.temperatureSaturationC)
        assertEquals(
            "la bulle moins la température du tube",
            lecture.temperatureBulleC - 34.0,
            etat.ecartK!!,
            0.05,
        )
    }

    /**
     * Les deux écarts sont positifs quand tout va bien, et c'est ce qui les rend
     * comparables aux plages du métier : une vapeur surchauffée est plus chaude
     * que sa saturation, un liquide sous-refroidi est plus froid que la sienne.
     * Les deux soustractions vont donc en sens opposés.
     */
    @Test
    fun `les deux ecarts sont positifs dans le cas normal`() {
        val saturation = CourbesSaturation.temperatureA("R410A", 8.01)!!
        val aspiration = EtatReglette(
            fluide = "R410A",
            pressionRelativeBar = 8.01.enBarRelatifs(),
            cote = CoteCircuit.ASPIRATION,
            temperatureLigneC = saturation.temperatureRoseeC + 6.0,
        )
        val refoulement = EtatReglette(
            fluide = "R410A",
            pressionRelativeBar = 8.01.enBarRelatifs(),
            cote = CoteCircuit.REFOULEMENT,
            temperatureLigneC = saturation.temperatureBulleC - 5.0,
        )

        assertEquals(6.0, aspiration.ecartK!!, 0.05)
        assertEquals(5.0, refoulement.ecartK!!, 0.05)
    }

    /**
     * Le relatif et l'absolu : 1,013 bar d'écart, soit environ 7 K sur un R-410A
     * en basse pression. C'est l'erreur la plus facile à commettre, et la réglette
     * doit interroger la courbe en absolus alors qu'elle affiche du relatif.
     */
    @Test
    fun `la pression saisie est relative, la courbe absolue`() {
        // Le point 0 °C du R-410A est à 8,01 bar abs, donc 6,997 bar au manomètre.
        val etat = EtatReglette(fluide = "R410A", pressionRelativeBar = 6.997)

        assertEquals(0.0, etat.lecture!!.temperatureBulleC, 0.05)
    }

    /**
     * Le report ne dépend plus de la marque de vérification.
     *
     * Il en a dépendu, et c'était justifié : les courbes avaient été écrites de
     * mémoire, plusieurs étaient fausses de plus de 8 K, et un tel chiffre reporté
     * dans un relevé devient un fait — il part dans le compte-rendu signé et
     * nourrit l'aide au dépannage sans que rien ne dise plus d'où il venait. Elles
     * sont désormais calculées, et exiger « j'ai contrôlé » pour s'en servir
     * n'aurait plus protégé de rien : la case aurait fini cochée par habitude, ce
     * qui est la façon la plus sûre de vider un garde-fou de son sens.
     */
    @Test
    fun `un ecart calcule se reporte, coche ou non`() {
        val etat = EtatReglette(
            fluide = "R410A",
            pressionRelativeBar = 6.997,
            temperatureLigneC = 6.0,
            verifie = false,
        )

        assertNotNull("le chiffre est calculé et montré", etat.ecartK)
        assertTrue("et il se reporte sans avoir à cocher quoi que ce soit", etat.reportable)
        assertTrue("cocher ne change rien", etat.copy(verifie = true).reportable)
    }

    /** Sans température de tuyauterie, il n'y a pas d'écart — et rien à reporter. */
    @Test
    fun `sans temperature relevee, aucun ecart`() {
        val etat = EtatReglette(fluide = "R410A", pressionRelativeBar = 6.997, verifie = true)

        assertNull(etat.ecartK)
        assertFalse(etat.reportable)
    }

    /**
     * Hors de la plage calculée, la réglette se taît, et le report reste fermé :
     * il n'y a aucun écart à reporter, et cocher la courbe ne la prolonge pas.
     */
    @Test
    fun `hors plage, rien n'est lu ni reporte`() {
        val etat = EtatReglette(
            fluide = "R410A",
            pressionRelativeBar = 80.0,
            temperatureLigneC = 40.0,
            verifie = true,
        )

        assertNull(etat.lecture)
        assertNull(etat.temperatureSaturationC)
        assertNull(etat.ecartK)
        assertFalse(etat.reportable)
    }

    @Test
    fun `un fluide sans courbe est annonce comme tel`() {
        val etat = EtatReglette(fluide = "R438A", pressionRelativeBar = 5.0, verifie = true)

        assertFalse(etat.couvert)
        assertNull(etat.lecture)
        assertFalse(etat.reportable)
    }

    /**
     * La plage du curseur vient de la courbe : une réglette CO₂ monte à 70 bar,
     * une réglette R-600a plafonne sous 10. Un curseur commun rendrait le second
     * inutilisable — régler un dixième de bar sur une échelle de 0 à 72 demanderait
     * un pixel.
     */
    @Test
    fun `la plage du curseur suit le fluide`() {
        val co2 = EtatReglette.plagePressionRelative("R744")!!
        val butane = EtatReglette.plagePressionRelative("R600A")!!

        assertTrue("le CO₂ monte haut, ${co2.endInclusive}", co2.endInclusive > 60.0)
        assertTrue("le R-600a non, ${butane.endInclusive}", butane.endInclusive < 10.0)
        assertNull("et un fluide sans courbe n'a pas de curseur", EtatReglette.plagePressionRelative("R438A"))
    }

    /**
     * Toute la plage doit être lisible : un curseur qui atteindrait une pression
     * hors table afficherait « hors plage » à son extrémité, ce qui se lit comme
     * une panne de l'application.
     */
    @Test
    fun `les deux extremites de la plage se lisent`() {
        CourbesSaturation.fluidesCouverts.forEach { fluide ->
            val plage = EtatReglette.plagePressionRelative(fluide)!!
            listOf(plage.start, plage.endInclusive).forEach { pression ->
                assertNotNull(
                    "$fluide : $pression bar rel. tombe hors table",
                    EtatReglette(fluide = fluide, pressionRelativeBar = pression).lecture,
                )
            }
        }
    }

    /**
     * Ouvrir la réglette depuis une intervention doit tomber sur la pression déjà
     * relevée : la question qu'on se pose est « ça fait combien, ça ? », pas « où
     * est le curseur ? ».
     */
    @Test
    fun `la pression d'ouverture est celle du releve`() {
        assertEquals(6.4, EtatReglette.pressionInitiale("R410A", relevee = 6.4), 0.001)
    }

    /** Une pression relevée hors plage est ramenée dedans, pas affichée hors table. */
    @Test
    fun `une pression hors plage est ramenee dans la plage`() {
        val plage = EtatReglette.plagePressionRelative("R410A")!!

        val trop = EtatReglette.pressionInitiale("R410A", relevee = 500.0)

        assertEquals(plage.endInclusive, trop, 0.001)
    }

    /** Sans relevé, on ouvre au milieu de la plage : un point de départ plausible. */
    @Test
    fun `sans releve, on ouvre au milieu de la plage`() {
        val plage = EtatReglette.plagePressionRelative("R410A")!!

        val depart = EtatReglette.pressionInitiale("R410A", relevee = null)

        assertTrue(depart > plage.start && depart < plage.endInclusive)
    }

    /**
     * Le fluide de la machine est repris tel quel, même sans courbe : le rabattre
     * en silence sur un voisin « proche » serait exactement l'approximation que le
     * projet refuse pour le GWP.
     */
    @Test
    fun `le fluide de la machine est repris, meme sans courbe`() {
        assertEquals("R410A", EtatReglette.fluideInitial("r-410a"))
        assertEquals("R438A", EtatReglette.fluideInitial("R438A"))
        assertTrue(
            "sans fluide, on ouvre sur un fluide couvert",
            CourbesSaturation.couvert(EtatReglette.fluideInitial("")),
        )
    }

    /** Un corps pur n'a pas de glissement : les deux colonnes se confondent. */
    @Test
    fun `sur un corps pur les deux cotes donnent la meme saturation`() {
        val aspiration = EtatReglette(
            fluide = "R134A",
            pressionRelativeBar = 2.93.enBarRelatifs(),
            cote = CoteCircuit.ASPIRATION,
        )
        val refoulement = aspiration.copy(cote = CoteCircuit.REFOULEMENT)

        assertEquals(aspiration.temperatureSaturationC, refoulement.temperatureSaturationC)
        assertFalse(aspiration.glissementNotable)
    }
}
