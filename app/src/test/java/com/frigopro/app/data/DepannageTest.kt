package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'aide au dépannage oriente un geste sur une installation en marche : elle
 * doit se taire quand elle ne sait pas, et proposer un contrôle avec chaque
 * piste plutôt qu'un verdict.
 */
class DepannageTest {

    @Test
    fun `sans relevé, aucun diagnostic`() {
        assertNull(Depannage.analyser(releve()))
    }

    /** Le cas de la maquette : SH 11,2 K et SC 4,0 K. */
    @Test
    fun `une surchauffe elevee a sous-refroidissement normal oriente vers l'evaporateur`() {
        val diagnostic = Depannage.analyser(releve(surchauffe = 11.2, sousRefroidissement = 4.0))

        assertEquals(Symptome.SOUS_ALIMENTATION_EVAPORATEUR, diagnostic!!.symptome)
        assertEquals("Détendeur trop fermé", diagnostic.causes.first().intitule)
    }

    @Test
    fun `surchauffe elevee et sous-refroidissement faible designent le manque de fluide`() {
        val diagnostic = Depannage.analyser(releve(surchauffe = 14.0, sousRefroidissement = 1.0))

        assertEquals(Symptome.MANQUE_DE_FLUIDE, diagnostic!!.symptome)
        assertEquals("Charge insuffisante", diagnostic.causes.first().intitule)
    }

    @Test
    fun `les deux eleves designent une restriction avant l'evaporateur`() {
        val diagnostic = Depannage.analyser(releve(surchauffe = 13.0, sousRefroidissement = 12.0))

        assertEquals(Symptome.RESTRICTION_LIGNE_LIQUIDE, diagnostic!!.symptome)
        assertEquals("Filtre déshydrateur colmaté", diagnostic.causes.first().intitule)
    }

    @Test
    fun `surchauffe faible et sous-refroidissement eleve designent l'exces de charge`() {
        val diagnostic = Depannage.analyser(releve(surchauffe = 1.0, sousRefroidissement = 12.0))

        assertEquals(Symptome.EXCES_DE_CHARGE, diagnostic!!.symptome)
        assertEquals("Surcharge en fluide", diagnostic.causes.first().intitule)
    }

    /**
     * Le retour liquide casse les clapets : la piste doit figurer dès qu'une
     * surchauffe faible est relevée.
     */
    @Test
    fun `une surchauffe faible avertit du retour liquide`() {
        val faible = Depannage.analyser(releve(surchauffe = 1.0, sousRefroidissement = 12.0))
        val seule = Depannage.analyser(releve(surchauffe = 1.5))

        assertTrue(faible!!.causes.any { it.intitule.contains("retour liquide", ignoreCase = true) })
        assertEquals(Symptome.SUR_ALIMENTATION_EVAPORATEUR, seule!!.symptome)
    }

    /**
     * Deux valeurs normales ne disent rien, et le dire serait rassurer à tort :
     * l'installation peut très bien avoir un autre défaut.
     */
    @Test
    fun `des valeurs normales ne produisent aucun diagnostic`() {
        assertNull(Depannage.analyser(releve(surchauffe = 6.0, sousRefroidissement = 5.0)))
    }

    @Test
    fun `une seule grandeur normale ne conclut pas`() {
        assertNull(Depannage.analyser(releve(surchauffe = 6.0)))
        assertNull(Depannage.analyser(releve(sousRefroidissement = 5.0)))
    }

    /** Les pressions seules ne suffisent pas : le diagnostic tient aux écarts. */
    @Test
    fun `des pressions seules ne concluent pas`() {
        assertNull(Depannage.analyser(releve(bp = 2.4, hp = 14.8)))
    }

    /** Une piste sans geste de contrôle ferait changer des pièces au hasard. */
    @Test
    fun `chaque cause propose un controle`() {
        val diagnostics = listOf(
            releve(surchauffe = 11.2, sousRefroidissement = 4.0),
            releve(surchauffe = 14.0, sousRefroidissement = 1.0),
            releve(surchauffe = 13.0, sousRefroidissement = 12.0),
            releve(surchauffe = 1.0, sousRefroidissement = 12.0),
            releve(surchauffe = 1.5),
            releve(sousRefroidissement = 12.0),
        ).mapNotNull(Depannage::analyser)

        assertEquals("les six cas doivent conclure", 6, diagnostics.size)
        diagnostics.forEach { diagnostic ->
            assertTrue(diagnostic.causes.isNotEmpty())
            diagnostic.causes.forEach { cause ->
                assertTrue("« ${cause.intitule} » n'a pas de contrôle", cause.controle.isNotBlank())
            }
        }
    }

    private fun releve(
        bp: Double? = null,
        hp: Double? = null,
        surchauffe: Double? = null,
        sousRefroidissement: Double? = null,
    ) = Releve(
        interventionId = "id-1",
        bpBar = bp,
        hpBar = hp,
        surchauffeK = surchauffe,
        sousRefroidissementK = sousRefroidissement,
    )
}

/** La numérotation : un client qui rappelle cite un numéro, pas un UUID. */
class NumerotationTest {

    private val mai = java.time.LocalDate.of(2026, 5, 14)

    @Test
    fun `le premier numero du mois part a un`() {
        assertEquals("INT-2605-001", Numerotation.suivant("INT", mai, emptyList()))
    }

    @Test
    fun `le numero suit le plus grand rang du mois`() {
        val existants = listOf("INT-2605-001", "INT-2605-017", "INT-2605-009")

        assertEquals("INT-2605-018", Numerotation.suivant("INT", mai, existants))
    }

    @Test
    fun `le rang repart a un au mois suivant`() {
        val existants = listOf("INT-2604-042")

        assertEquals("INT-2605-001", Numerotation.suivant("INT", mai, existants))
    }

    @Test
    fun `les prefixes ne se melangent pas`() {
        val existants = listOf("DEV-2605-007")

        assertEquals("INT-2605-001", Numerotation.suivant("INT", mai, existants))
        assertEquals("DEV-2605-008", Numerotation.suivant("DEV", mai, existants))
    }

    /**
     * Prendre le plus grand rang plutôt que le nombre de documents : sinon,
     * supprimer un brouillon ferait réattribuer un numéro déjà sorti chez un
     * client.
     */
    @Test
    fun `une suppression ne fait pas reattribuer un numero`() {
        val apresSuppression = listOf("INT-2605-001", "INT-2605-003")

        assertEquals("INT-2605-004", Numerotation.suivant("INT", mai, apresSuppression))
    }

    @Test
    fun `un numero malforme n'empeche pas de numeroter`() {
        val existants = listOf("INT-2605-abc", "INT-2605-002")

        assertEquals("INT-2605-003", Numerotation.suivant("INT", mai, existants))
    }
}
