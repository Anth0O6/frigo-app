package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Le GWP et la périodicité ne sont pas de l'affichage : un technicien qui se
 * trompe de fréquence de contrôle est en infraction. Ces calculs doivent être
 * justes, ou se taire.
 */
class FluideTest {

    @Test
    fun `un fluide se reconnait quelle que soit sa graphie`() {
        assertEquals(2141, Fluides.gwp("R452A"))
        assertEquals(2141, Fluides.gwp("r452a"))
        assertEquals(2141, Fluides.gwp("R-452A"))
        assertEquals(2141, Fluides.gwp(" R452A "))
    }

    @Test
    fun `un fluide inconnu n'a pas de GWP invente`() {
        assertNull(Fluides.gwp("R999Z"))
        assertNull(Fluides.tonnesEquivalentCo2("R999Z", 6.2))
    }

    /** Le chiffre de la maquette : 6,2 kg de R452A font 13,3 t éq. CO₂. */
    @Test
    fun `l'equivalent CO2 est la charge multipliee par le GWP`() {
        val tonnes = Fluides.tonnesEquivalentCo2("R452A", 6.2)

        assertEquals(13.3, tonnes!!.arrondiDixieme(), 0.001)
    }

    @Test
    fun `les seuils du reglement decident de la periodicite`() {
        assertEquals(PeriodiciteControle.AUCUNE, PeriodiciteControle.pour(4.9))
        assertEquals(PeriodiciteControle.DOUZE_MOIS, PeriodiciteControle.pour(5.0))
        assertEquals(PeriodiciteControle.DOUZE_MOIS, PeriodiciteControle.pour(49.9))
        assertEquals(PeriodiciteControle.SIX_MOIS, PeriodiciteControle.pour(50.0))
        assertEquals(PeriodiciteControle.SIX_MOIS, PeriodiciteControle.pour(499.9))
        assertEquals(PeriodiciteControle.TROIS_MOIS, PeriodiciteControle.pour(500.0))
    }

    @Test
    fun `l'echeance se deduit du dernier controle`() {
        val etat = EtatEtancheite.calculer(
            fluide = "R452A",
            chargeKg = 6.2,
            dernierControle = LocalDate.of(2025, 9, 18),
            aujourdhui = LocalDate.of(2026, 5, 14),
        )

        assertEquals(PeriodiciteControle.DOUZE_MOIS, etat.periodicite)
        assertEquals(LocalDate.of(2026, 9, 18), etat.echeance)
        assertFalse(etat.enRetard)
    }

    @Test
    fun `une echeance depassee est signalee`() {
        val etat = EtatEtancheite.calculer(
            fluide = "R452A",
            chargeKg = 6.2,
            dernierControle = LocalDate.of(2025, 1, 10),
            aujourdhui = LocalDate.of(2026, 5, 14),
        )

        assertTrue(etat.enRetard)
    }

    /**
     * Sans charge connue, aucun équivalent CO₂ n'est calculable : annoncer une
     * périodicité reviendrait à la deviner.
     */
    @Test
    fun `sans charge, aucune periodicite n'est annoncee`() {
        val etat = EtatEtancheite.calculer("R452A", null, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 5, 14))

        assertEquals(PeriodiciteControle.AUCUNE, etat.periodicite)
        assertNull(etat.echeance)
    }

    /** Sans contrôle antérieur, on ne peut pas dater le suivant. */
    @Test
    fun `sans dernier controle, aucune echeance n'est datee`() {
        val etat = EtatEtancheite.calculer("R452A", 6.2, null, LocalDate.of(2026, 5, 14))

        assertEquals(PeriodiciteControle.DOUZE_MOIS, etat.periodicite)
        assertNull(etat.echeance)
        assertFalse(etat.enRetard)
    }

    /** Une petite charge de fluide à faible GWP n'est pas soumise à contrôle. */
    @Test
    fun `le propane en petite charge n'est pas soumis`() {
        val etat = EtatEtancheite.calculer("R290", 0.4, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 5, 14))

        assertEquals(PeriodiciteControle.AUCUNE, etat.periodicite)
        assertNull(etat.echeance)
    }
    /**
     * Un détecteur de fuite fixe double les intervalles (art. 4 § 3). Le vérifier
     * par seuil et pas seulement sur un cas : c'est une obligation réglementaire, et
     * un décalage d'un cran annoncerait un contrôle tous les deux ans là où il en
     * faut un par an.
     */
    @Test
    fun `un detecteur de fuite fixe double les intervalles`() {
        assertEquals(PeriodiciteControle.VINGT_QUATRE_MOIS, PeriodiciteControle.pour(5.0, detecteurFixe = true))
        assertEquals(PeriodiciteControle.VINGT_QUATRE_MOIS, PeriodiciteControle.pour(49.9, detecteurFixe = true))
        assertEquals(PeriodiciteControle.DOUZE_MOIS, PeriodiciteControle.pour(50.0, detecteurFixe = true))
        assertEquals(PeriodiciteControle.DOUZE_MOIS, PeriodiciteControle.pour(499.9, detecteurFixe = true))
        assertEquals(PeriodiciteControle.SIX_MOIS, PeriodiciteControle.pour(500.0, detecteurFixe = true))
    }

    /**
     * Doubler une absence d'obligation ne veut rien dire : une machine non soumise
     * le reste, détecteur ou pas. Rendre « tous les 24 mois » pour trois kilogrammes
     * de R-134A inventerait une obligation là où le règlement n'en pose aucune.
     */
    @Test
    fun `un detecteur ne soumet pas une machine qui ne l'est pas`() {
        assertEquals(PeriodiciteControle.AUCUNE, PeriodiciteControle.pour(4.9, detecteurFixe = true))
        assertEquals(PeriodiciteControle.AUCUNE, PeriodiciteControle.pour(0.0, detecteurFixe = true))
    }

    /**
     * Le défaut est **sans** détecteur, et ce n'est pas neutre : une machine dont on
     * ne sait rien retient la périodicité la plus exigeante. Annoncer un contrôle
     * trop tôt fait perdre une heure, trop tard expose à une sanction.
     */
    @Test
    fun `sans precision, la periodicite la plus exigeante est retenue`() {
        assertEquals(PeriodiciteControle.pour(60.0), PeriodiciteControle.pour(60.0, detecteurFixe = false))
        assertEquals(PeriodiciteControle.SIX_MOIS, PeriodiciteControle.pour(60.0))
    }

    /** Alléger est monotone : aucun cran ne saute, et 24 mois est le plafond. */
    @Test
    fun `alleger ne saute aucun cran`() {
        assertEquals(PeriodiciteControle.SIX_MOIS, PeriodiciteControle.TROIS_MOIS.allege())
        assertEquals(PeriodiciteControle.DOUZE_MOIS, PeriodiciteControle.SIX_MOIS.allege())
        assertEquals(PeriodiciteControle.VINGT_QUATRE_MOIS, PeriodiciteControle.DOUZE_MOIS.allege())
        assertEquals(
            "vingt-quatre mois est le plafond du règlement",
            PeriodiciteControle.VINGT_QUATRE_MOIS,
            PeriodiciteControle.VINGT_QUATRE_MOIS.allege(),
        )
        assertEquals(PeriodiciteControle.AUCUNE, PeriodiciteControle.AUCUNE.allege())
    }

    // — La classe de sécurité ———————————————————————————————————————————————

    /**
     * L'inflammabilité commande les outils, le brasage et la charge admise dans un
     * local : confondre un A2L et un A1 n'est pas une erreur d'affichage.
     */
    @Test
    fun `la classe de securite distingue les fluides inflammables`() {
        assertEquals(ClasseSecurite.A1, Fluides.classeSecurite("R410A"))
        assertEquals(ClasseSecurite.A2L, Fluides.classeSecurite("R32"))
        assertEquals(ClasseSecurite.A2L, Fluides.classeSecurite("R454B"))
        assertEquals("le propane", ClasseSecurite.A3, Fluides.classeSecurite("R290"))
        assertEquals("l'ammoniac est le seul B du catalogue", ClasseSecurite.B2L, Fluides.classeSecurite("R717"))
    }

    @Test
    fun `la graphie du fluide n'empeche pas de trouver sa classe`() {
        assertEquals(ClasseSecurite.A2L, Fluides.classeSecurite("r-32"))
        assertEquals(ClasseSecurite.A2L, Fluides.classeSecurite(" R32 "))
    }

    /**
     * **Aucune classe n'est devinée.** Rendre « A1 » pour un fluide inconnu parce
     * que c'est le cas le plus fréquent reviendrait à le dire ininflammable sans
     * rien en savoir, et c'est ce genre de supposition qui met le feu à un local.
     */
    @Test
    fun `un fluide inconnu n'a pas de classe inventee`() {
        assertNull(Fluides.classeSecurite("R999Z"))
    }

    /**
     * Tout fluide du catalogue a une classe : les deux tables décrivent le même
     * catalogue, et un fluide présent dans l'une et absent de l'autre trahirait une
     * faute de frappe sur le nom — qui ferait disparaître la classe en silence.
     */
    @Test
    fun `tout fluide connu porte une classe de securite`() {
        Fluides.connus.forEach { fluide ->
            assertNotNull("$fluide a un GWP mais pas de classe", Fluides.classeSecurite(fluide))
        }
    }

    @Test
    fun `le resume d'une classe dit ce qu'elle signifie`() {
        val a2l = ClasseSecurite.A2L

        assertTrue("le code seul ne dit rien à qui n'a pas la table", a2l.resume.contains("inflammable"))
        assertTrue(a2l.resume.startsWith("A2L"))
        assertTrue(a2l.inflammable)
        assertFalse(a2l.toxique)
        assertFalse("un A1 ne demande pas les précautions d'un A2L", ClasseSecurite.A1.inflammable)
        assertTrue(ClasseSecurite.B2L.toxique)
    }
}
