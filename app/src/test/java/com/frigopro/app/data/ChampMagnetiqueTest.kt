package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que le magnétomètre peut affirmer, et ce qu'il doit refuser d'affirmer.
 *
 * L'arithmétique est éprouvée sans téléphone, comme celle des courbes de
 * saturation et de la réduction de photo : le capteur n'entre nulle part ici, et
 * `LecteurChamp` est la seule pièce qui le touche.
 */
class ChampMagnetiqueTest {

    /** Une mesure d'amplitude connue, répétée, avec l'agitation demandée. */
    private fun mesures(
        amplitude: Double,
        agitation: Double = 0.0,
        combien: Int = 32,
    ): List<MesureChamp> = List(combien) { rang ->
        // Une alternance plutôt qu'un bruit tiré au hasard : un test qui dépend
        // d'un générateur aléatoire échoue un jour sur cent sans qu'on sache
        // pourquoi, et finit désactivé.
        val ecart = if (rang % 2 == 0) agitation else -agitation
        MesureChamp(x = amplitude + ecart, y = 0.0, z = 0.0)
    }

    @Test
    fun `l'amplitude est la norme des trois axes`() {
        assertEquals(5.0, MesureChamp(3.0, 4.0, 0.0).amplitude, 0.001)
        assertEquals(4.0, MesureChamp(0.0, 0.0, -4.0).amplitude, 0.001)
    }

    /**
     * La plus grande composante est ce qui sature en premier, et c'est elle
     * qu'il faut surveiller : un capteur borné à 2 000 µT qui reçoit 3 000 sur un
     * seul axe rend 2 000, et l'amplitude seule ne dirait pas que la valeur est
     * plafonnée plutôt que mesurée.
     */
    @Test
    fun `la composante maximale ignore le signe`() {
        assertEquals(40.0, MesureChamp(-40.0, 12.0, 3.0).composanteMaximale, 0.001)
    }

    /** Trois points ne décrivent pas une fluctuation : la fenêtre se tait. */
    @Test
    fun `une fenetre trop courte ne conclut rien`() {
        assertNull(FenetreChamp.de(mesures(amplitude = 50.0, combien = 3)))
    }

    @Test
    fun `une fenetre calme n'a pas d'agitation`() {
        val fenetre = requireNotNull(FenetreChamp.de(mesures(amplitude = 50.0)))

        assertEquals(50.0, fenetre.amplitude, 0.001)
        assertEquals(0.0, fenetre.agitation, 0.001)
    }

    @Test
    fun `une fenetre qui fluctue porte son ecart-type`() {
        val fenetre = requireNotNull(
            FenetreChamp.de(mesures(amplitude = 200.0, agitation = 30.0)),
        )

        assertEquals(200.0, fenetre.amplitude, 0.001)
        assertEquals(30.0, fenetre.agitation, 0.001)
    }

    /**
     * Le champ terrestre seul ne doit rien déclencher. C'est le cas le plus
     * fréquent — l'outil est ouvert et ne regarde rien — et une détection qui se
     * déclenche tout le temps est une détection qu'on cesse de lire.
     */
    @Test
    fun `le champ terrestre seul ne detecte rien`() {
        val lecture = lire(amplitude = 48.0)

        assertEquals(NiveauChamp.AUCUN, lecture.niveau)
        assertTrue(!lecture.niveau.detecte)
    }

    /**
     * Tourner le téléphone de quelques degrés change déjà l'amplitude de
     * plusieurs microteslas : le seuil de détection doit laisser passer ça.
     */
    @Test
    fun `une main qui bouge ne passe pas pour un champ`() {
        assertEquals(NiveauChamp.AUCUN, lire(amplitude = 53.0).niveau)
    }

    @Test
    fun `une bobine au contact donne un champ net`() {
        val lecture = lire(amplitude = 48.0 + 300.0)

        assertEquals(NiveauChamp.NET, lecture.niveau)
        assertEquals(300.0, lecture.exces, 0.001)
    }

    @Test
    fun `un aimant au contact donne un champ fort`() {
        assertEquals(NiveauChamp.FORT, lire(amplitude = 48.0 + 1_500.0).niveau)
    }

    /**
     * Un champ peut **s'opposer** à l'ambiant et le faire baisser. Un aimant
     * présenté dans l'autre sens passerait sinon pour une absence de champ, ce
     * qui est exactement le contraire de ce qui se passe.
     */
    @Test
    fun `un champ qui s'oppose a l'ambiant se detecte quand meme`() {
        val lecture = lire(amplitude = 48.0 - 40.0)

        assertTrue(lecture.niveau.detecte)
        assertEquals(-40.0, lecture.exces, 0.001)
    }

    /**
     * La saturation se juge contre la plage **du capteur de l'appareil**, qui va
     * de 2 000 à 4 900 µT selon les téléphones. La dire plutôt que d'afficher la
     * valeur plafonnée est ce qui empêche de croire l'instrument précis là où il
     * a décroché.
     */
    @Test
    fun `un capteur au bout de sa plage se declare sature`() {
        val fenetre = requireNotNull(FenetreChamp.de(mesures(amplitude = 1_990.0)))

        val lecture = ChampMagnetique.lire(fenetre, reference = null, porteeCapteur = 2_000.0)

        assertEquals(NiveauChamp.SATURE, lecture.niveau)
    }

    /**
     * Le même champ n'est pas saturé sur un capteur plus large : le seuil suit
     * l'appareil et n'est pas une constante du projet.
     */
    @Test
    fun `le meme champ ne sature pas un capteur plus large`() {
        val fenetre = requireNotNull(FenetreChamp.de(mesures(amplitude = 1_990.0)))

        val lecture = ChampMagnetique.lire(fenetre, reference = null, porteeCapteur = 4_900.0)

        assertEquals(NiveauChamp.FORT, lecture.niveau)
    }

    /** Un aimant : champ net, et rien qui bouge. */
    @Test
    fun `un champ net et calme est dit constant`() {
        val lecture = lire(amplitude = 48.0 + 400.0, agitation = 0.0)

        assertEquals(AllureChamp.CONSTANTE, lecture.allure)
    }

    /**
     * Une bobine en alternatif : le téléphone échantillonne à une cadence qui
     * n'est pas accrochée au 50 Hz du réseau, et il en sort un battement. C'est
     * le seul moyen de la distinguer d'un aimant.
     */
    @Test
    fun `un champ net et agite est dit alternatif`() {
        val lecture = lire(amplitude = 48.0 + 400.0, agitation = 25.0)

        assertEquals(AllureChamp.FLUCTUANTE, lecture.allure)
    }

    /**
     * **Le test qui compte le plus.** Une main qui tremble agite déjà la mesure
     * dans le champ terrestre ; sans comparaison à l'agitation de l'ambiant, ce
     * tremblement passerait pour une bobine alimentée — et l'outil dirait
     * « alimentée » devant une bobine morte.
     */
    @Test
    fun `une main qui tremble ne fait pas passer un aimant pour une bobine`() {
        val ambiantAgite = requireNotNull(
            FenetreChamp.de(mesures(amplitude = 48.0, agitation = 20.0)),
        )
        val aimant = requireNotNull(
            FenetreChamp.de(mesures(amplitude = 448.0, agitation = 20.0)),
        )

        val lecture = ChampMagnetique.lire(aimant, ambiantAgite, porteeCapteur = 2_000.0)

        assertEquals(AllureChamp.CONSTANTE, lecture.allure)
    }

    /** Qualifier l'allure d'un champ absent n'aurait aucun sens. */
    @Test
    fun `sans champ detecte l'allure reste indeterminee`() {
        assertEquals(AllureChamp.INDETERMINEE, lire(amplitude = 48.0, agitation = 40.0).allure)
    }

    /**
     * Un champ faible et calme ne suffit pas à crier « aimant » : la fluctuation
     * d'une bobine lointaine est elle aussi faible, et trancher là serait
     * inventer.
     */
    @Test
    fun `un champ faible et calme ne s'affirme pas constant`() {
        assertEquals(AllureChamp.INDETERMINEE, lire(amplitude = 48.0 + 20.0).allure)
    }

    /**
     * L'ambiant relevé sur place remplace l'ambiant supposé, et c'est tout
     * l'intérêt du geste : dans une armoire, la tôle décale le champ terrestre de
     * plusieurs dizaines de microteslas, et un champ faible s'y perdrait.
     */
    @Test
    fun `l'ambiant releve remplace l'ambiant suppose`() {
        val dansUneArmoire = requireNotNull(FenetreChamp.de(mesures(amplitude = 160.0)))
        val fenetre = requireNotNull(FenetreChamp.de(mesures(amplitude = 175.0)))

        val suppose = ChampMagnetique.lire(fenetre, reference = null, porteeCapteur = 2_000.0)
        val releve = ChampMagnetique.lire(fenetre, dansUneArmoire, porteeCapteur = 2_000.0)

        // Sans relevé, les 160 µT de la tôle comptent pour du champ trouvé : 127 µT
        // de dépassement sur un ambiant supposé à 48, soit un « champ net » qui
        // n'est que de la ferraille.
        assertEquals(NiveauChamp.NET, suppose.niveau)
        assertTrue(!suppose.ambiantReleve)
        // Avec relevé, il ne reste que les 15 µT qui sont vraiment nouveaux.
        assertEquals(NiveauChamp.FAIBLE, releve.niveau)
        assertTrue(releve.ambiantReleve)
    }

    /**
     * Chaque situation dit quelque chose d'utile, et aucune ne reste muette : un
     * écran qui affiche un chiffre sans le commenter laisse conclure de travers.
     */
    @Test
    fun `chaque lecture porte une interpretation`() {
        listOf(
            lire(amplitude = 48.0),
            lire(amplitude = 68.0),
            lire(amplitude = 448.0),
            lire(amplitude = 448.0, agitation = 25.0),
            lire(amplitude = 2_500.0),
        ).forEach { lecture ->
            assertTrue(lecture.interpretation.isNotBlank())
        }
    }

    /**
     * Aucune interprétation n'affirme qu'une bobine **est** alimentée : la mesure
     * est compatible avec cette hypothèse, elle ne la prouve pas. Même règle que
     * l'aide au dépannage, qui propose des pistes et ne tranche jamais.
     */
    @Test
    fun `rien n'affirme qu'une bobine est alimentee`() {
        val lecture = lire(amplitude = 448.0, agitation = 25.0)

        assertTrue(lecture.interpretation.contains("compatible"))
    }

    private fun lire(
        amplitude: Double,
        agitation: Double = 0.0,
        porteeCapteur: Double = 2_000.0,
    ) = ChampMagnetique.lire(
        fenetre = requireNotNull(FenetreChamp.de(mesures(amplitude, agitation))),
        reference = null,
        porteeCapteur = porteeCapteur,
    )
}
