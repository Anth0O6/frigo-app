package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le bilan d'un circuit secondaire, et les deux règles de pouce du métier.
 *
 * Les repères choisis sont ceux qu'on vérifie de tête : un mètre cube d'eau par
 * heure sur 5 K porte environ 5,8 kW, et mille mètres cubes d'air par heure sur
 * 10 K en portent environ 3,4. Un test dont on connaît l'ordre de grandeur attrape
 * une puissance mille fois trop grande — l'erreur d'unité, la plus probable ici.
 */
class PuissanceEchangeeTest {

    @Test
    fun `un metre cube d'eau par heure sur cinq kelvins porte cinq virgule huit kilowatts`() {
        val puissance = PuissanceEchangee.kilowatts(
            debitM3ParHeure = 1.0,
            ecartK = 5.0,
            caloporteur = Caloporteur.EAU,
        )

        assertEquals(5.8, puissance!!, 0.05)
    }

    @Test
    fun `mille metres cubes d'air par heure sur dix kelvins portent trois virgule quatre kilowatts`() {
        val puissance = PuissanceEchangee.kilowatts(
            debitM3ParHeure = 1000.0,
            ecartK = 10.0,
            caloporteur = Caloporteur.AIR,
        )

        assertEquals(3.4, puissance!!, 0.05)
    }

    /**
     * L'eau glycolée transporte **moins** que l'eau à débit égal : sa chaleur
     * massique est plus basse. C'est la raison pour laquelle un circuit glycolé
     * demande une pompe plus grosse, et l'oublier fait conclure à tort qu'un
     * évaporateur est encrassé.
     */
    @Test
    fun `l'eau glycolee transporte moins que l'eau a debit egal`() {
        val eau = PuissanceEchangee.kilowatts(2.0, 5.0, Caloporteur.EAU)!!
        val glycolee = PuissanceEchangee.kilowatts(2.0, 5.0, Caloporteur.EAU_GLYCOLEE_MEG_30)!!

        assertTrue("eau $eau kW, glycolée $glycolee kW", glycolee < eau)
        // La masse volumique plus forte compense en partie : l'écart reste modéré.
        assertTrue(glycolee > eau * 0.8)
    }

    /**
     * Le sens des sondes ne doit pas changer la puissance : c'est la valeur absolue
     * de l'écart qui compte, et exiger le bon ordre aurait obligé à se souvenir
     * laquelle des deux on a nommée « entrée ».
     */
    @Test
    fun `le signe de l'ecart ne change pas la puissance`() {
        val montant = PuissanceEchangee.kilowatts(3.0, 6.0, Caloporteur.EAU)
        val descendant = PuissanceEchangee.kilowatts(3.0, -6.0, Caloporteur.EAU)

        assertEquals(montant, descendant)
    }

    @Test
    fun `une saisie incomplete ne rend rien`() {
        assertNull(PuissanceEchangee.kilowatts(null, 5.0, Caloporteur.EAU))
        assertNull(PuissanceEchangee.kilowatts(2.0, null, Caloporteur.EAU))
        assertNull("un débit négatif n'a pas de sens", PuissanceEchangee.kilowatts(-2.0, 5.0, Caloporteur.EAU))
    }

    @Test
    fun `un debit nul ne transporte rien`() {
        assertEquals(0.0, PuissanceEchangee.kilowatts(0.0, 5.0, Caloporteur.EAU)!!, 0.0001)
    }

    /**
     * Les trois sens de la même formule doivent se retrouver : c'est ce qui
     * garantit qu'aucun des trois ne porte un facteur de travers. Un seul chemin
     * aurait laissé passer une division inversée dans les deux autres.
     */
    @Test
    fun `les trois sens de la formule se retrouvent`() {
        val debit = 4.5
        val ecart = 6.0

        val puissance = PuissanceEchangee.kilowatts(debit, ecart, Caloporteur.EAU)!!

        assertEquals(
            "l'écart retrouvé depuis la puissance",
            ecart,
            PuissanceEchangee.ecartAttenduK(puissance, debit, Caloporteur.EAU)!!,
            0.05,
        )
        assertEquals(
            "le débit retrouvé depuis la puissance",
            debit,
            PuissanceEchangee.debitAttenduM3ParHeure(puissance, ecart, Caloporteur.EAU)!!,
            0.05,
        )
    }

    /**
     * Le cas du réglage : la plaque annonce une puissance, on connaît le débit, et
     * l'on veut le Δt à attendre. 10 kW sur 2 m³/h d'eau demandent environ 4,3 K.
     */
    @Test
    fun `l'ecart attendu pour une puissance de plaque`() {
        val ecart = PuissanceEchangee.ecartAttenduK(10.0, 2.0, Caloporteur.EAU)

        assertEquals(4.3, ecart!!, 0.05)
    }

    /**
     * Une puissance sans écart de température demanderait un débit infini, et une
     * puissance sans débit un écart infini : ce sont des questions mal posées, pas
     * des cas limites à arrondir.
     */
    @Test
    fun `l'infini est refuse plutot qu'arrondi`() {
        assertNull(PuissanceEchangee.debitAttenduM3ParHeure(10.0, 0.0, Caloporteur.EAU))
        assertNull(PuissanceEchangee.ecartAttenduK(10.0, 0.0, Caloporteur.EAU))
        assertNull(PuissanceEchangee.ecartAttenduK(10.0, -1.0, Caloporteur.EAU))
    }

    /**
     * Chaque caloporteur annonce la condition à laquelle ses valeurs sont données.
     * Sans elle, le chiffre se lirait comme une mesure ; avec, comme un ordre de
     * grandeur — ce qu'il est.
     */
    @Test
    fun `chaque caloporteur dit sa condition de reference`() {
        Caloporteur.entries.forEach { caloporteur ->
            assertTrue(caloporteur.name, caloporteur.reference.isNotBlank())
            assertTrue("masse volumique", caloporteur.masseVolumique > 0)
            assertTrue("chaleur massique", caloporteur.chaleurMassique > 0)
        }
    }
}
