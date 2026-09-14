package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les pistes de remplacement d'un fluide.
 *
 * Ces tests ne jugent pas la pertinence d'une substitution — c'est un fait du
 * métier, et le dernier mot revient de toute façon au constructeur du
 * compresseur. Ils vérifient ce qui est de notre ressort : que la table et le
 * catalogue disent la même chose, que rien n'est extrapolé, et que les deux
 * changements qui coûtent cher à découvrir tard — l'inflammabilité et le
 * glissement — sont bien signalés.
 */
class SubstitutionTest {

    /**
     * Le filet contre la coquille de frappe sur un nom de fluide.
     *
     * « R499A » pour « R449A » ferait disparaître silencieusement le GWP, la
     * classe de sécurité et la courbe d'une piste, qui s'afficherait alors vide
     * de tout ce qui permet de choisir.
     */
    @Test
    fun `tout fluide de la table est au catalogue`() {
        Substitutions.fluidesConvertibles.forEach { origine ->
            assertNotNull("$origine n'a pas de GWP", Fluides.gwp(origine))
            Substitutions.pour(origine).forEach { piste ->
                assertNotNull(
                    "${piste.remplacant} n'a pas de GWP",
                    Fluides.gwp(piste.remplacant),
                )
                assertNotNull(
                    "${piste.remplacant} n'a pas de classe de sécurité",
                    Fluides.classeSecurite(piste.remplacant),
                )
            }
        }
    }

    /** Un fluide ne se remplace pas par lui-même. */
    @Test
    fun `aucune piste ne renvoie au fluide d'origine`() {
        Substitutions.fluidesConvertibles.forEach { origine ->
            Substitutions.pour(origine).forEach { piste ->
                assertFalse(
                    "$origine se remplace par lui-même",
                    piste.remplacant == piste.origine,
                )
            }
        }
    }

    /**
     * Les pistes sont ordonnées par GWP croissant.
     *
     * C'est la direction que la réglementation impose ; les conversions de
     * prolongation restent proposées, mais plus bas.
     */
    @Test
    fun `les pistes sortent du plus bas GWP au plus haut`() {
        val pistes = Substitutions.pour("R404A")
        val gwp = pistes.map { Fluides.gwp(it.remplacant)!! }

        assertEquals("l'ordre doit être croissant, lu $gwp", gwp.sorted(), gwp)
        assertTrue("le R-404A doit avoir plusieurs pistes", pistes.size >= 3)
    }

    /**
     * Le changement qui coûte le plus cher à découvrir tard.
     *
     * Passer d'un R-410A (A1) à un R-32 (A2L) change les outils, le brasage, la
     * charge admise dans le local et la qualification. L'écran doit le dire
     * avant le GWP, qui ne décide que d'une paperasse.
     */
    @Test
    fun `passer a un A2L est signale`() {
        val versR32 = Substitutions.pour("R410A").single { it.remplacant == "R32" }

        val ecart = Substitutions.ecart(versR32)

        assertTrue("le R-32 est un A2L là où le R-410A est un A1", ecart.devientInflammable)
        assertEquals(ClasseSecurite.A1, ecart.classeOrigine)
        assertEquals(ClasseSecurite.A2L, ecart.classeRemplacant)
    }

    /** Rester en A1 ne déclenche pas l'avertissement. */
    @Test
    fun `rester en A1 ne signale rien`() {
        val versR513A = Substitutions.pour("R134A").single { it.remplacant == "R513A" }

        assertFalse(Substitutions.ecart(versR513A).devientInflammable)
    }

    /**
     * La baisse de GWP est ce que la réglementation regarde, et elle se calcule
     * plutôt que de se recopier — une table de pourcentages aurait divergé du
     * catalogue à la première correction de GWP.
     */
    @Test
    fun `la baisse de GWP se calcule depuis le catalogue`() {
        val versR449A = Substitutions.pour("R404A").single { it.remplacant == "R449A" }

        val ecart = Substitutions.ecart(versR449A)

        // 3 922 → 1 397, soit environ 64 % de moins.
        assertEquals(64.4, ecart.baisseGwp!!, 0.5)
    }

    /**
     * Un remplaçant peut être **pire**, et cela doit se voir.
     *
     * Le R-422D remplace le R-22 pour prolonger une installation, avec un GWP
     * plus élevé : taire cette hausse laisserait croire qu'une conversion est
     * toujours un progrès.
     */
    @Test
    fun `une hausse de GWP se voit au lieu d'etre tue`() {
        val versR422D = Substitutions.pour("R22").single { it.remplacant == "R422D" }

        val baisse = Substitutions.ecart(versR422D).baisseGwp!!

        assertTrue("le R-422D est plus lourd que le R-22, lu $baisse %", baisse < 0.0)
    }

    /**
     * Rien n'est proposé pour un fluide absent de la table.
     *
     * Rapprocher deux fluides « parce qu'ils se ressemblent » est ce que le
     * projet refuse déjà pour le GWP, et ce serait ici plus grave : c'est un
     * compresseur qui en dépend.
     */
    @Test
    fun `un fluide hors table n'a aucune piste inventee`() {
        assertFalse(Substitutions.couvert("R290"))
        assertTrue(Substitutions.pour("R290").isEmpty())
        assertTrue(Substitutions.pour("R999Z").isEmpty())
    }

    @Test
    fun `le nom du fluide se normalise comme ailleurs`() {
        assertTrue(Substitutions.couvert("r-404a"))
        assertEquals(Substitutions.pour("R404A"), Substitutions.pour(" r404a "))
    }

    /**
     * Une conversion sans vidange n'est pas une recharge.
     *
     * Le libellé porte ce que le geste demande vraiment : le détendeur se règle
     * et la charge se refait. « Drop-in » laisserait croire qu'on transvase.
     */
    @Test
    fun `l'ampleur dit ce que le chantier demande`() {
        val versR407C = Substitutions.pour("R22").single { it.remplacant == "R407C" }
        val versR427A = Substitutions.pour("R22").single { it.remplacant == "R427A" }

        assertEquals(AmpleurSubstitution.VIDANGE_POE, versR407C.ampleur)
        assertEquals(AmpleurSubstitution.SANS_VIDANGE, versR427A.ampleur)
        assertTrue(
            "la vidange doit dire pourquoi",
            versR407C.ampleur.detail.contains("polyolester"),
        )
    }

    /** L'avertissement renvoie au constructeur, et jamais à une configuration. */
    @Test
    fun `l'avertissement renvoie au constructeur`() {
        assertTrue(Substitutions.AVERTISSEMENT.contains("constructeur"))
    }
}
