package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import java.time.LocalDate
import org.junit.Test

class LectureTarifTest {

    /** Une proposition Richardson telle que la reconnaissance la rend : désignation et chiffres en deux colonnes. */
    private val proposition = listOf(
        "RICHARDSON",
        "S.A.S. AU CAPITAL DE 15.300.000 € - 054 800 958 R.C.S MARSEILLE",
        "Date d'édition : lundi 26 octobre 2026",
        "N.: 135-3770 DU 26/10/2026",
        "DESIGNATION  QUANT.  UT  PRIX NET HT  MONTANT €",
        "GOULOTTE BLANC 80X60MM  WHITE-80BCANA-2M  6,00 U  7, 15  42,90",
        "ANGLE A PLAT BLANC 80X60MM  WHITE-80CPIAN  2,00 U  1,90  3,80",
        "CABLE R2V 3G2.5 C50 MIGUELEZ 81010312-509205  1,00 U  70,00  70,00",
        "TUBE CU.PREISO.BI C.20M 1/4X0.8-3/8X0.8 F1220  1,00 U  100,50  100,50",
        "SUPPORT REGL.EQ.+NIV. 800X420/160KG  SI 1301  1,00 U  12,30  12,30",
        "MONTANT H.T  229,50",
        "TVA 20,00 %  45,90",
        "MONTANT TTC  275,40",
        "*** OFFRE N. : 135-3770 VALABLE JUSQU'AU : 26/11/2026 ***",
    )

    private val richardson = Fournisseur(id = "four-richardson", nom = "Richardson")
    private val gff = Fournisseur(id = "four-gff", nom = "GFF")

    @Test
    fun `seules les lignes dont l'arithmetique tombe juste sont lues`() {
        val tarif = LectureTarif.lire(proposition, listOf(gff, richardson))

        assertEquals(5, tarif.lignes.size)
        val goulotte = tarif.lignes.first()
        assertEquals("GOULOTTE BLANC 80X60MM", goulotte.designation)
        assertEquals("WHITE-80BCANA-2M", goulotte.reference)
        assertEquals(6.0, goulotte.quantite, 0.0)
        assertEquals("la décimale coupée par la lecture est recollée", 7.15, goulotte.prixNet, 0.001)
        assertEquals(42.90, goulotte.montant, 0.001)
    }

    @Test
    fun `la reference est le dernier champ ou le dernier mot qui en a l'air`() {
        val lignes = LectureTarif.lire(proposition, emptyList()).lignes.associateBy { it.prixNet }

        assertEquals("81010312-509205", lignes.getValue(70.0).reference)
        assertEquals("CABLE R2V 3G2.5 C50 MIGUELEZ", lignes.getValue(70.0).designation)
        assertEquals("F1220", lignes.getValue(100.5).reference)
        assertEquals("SI 1301", lignes.getValue(12.3).reference)
        assertEquals("SUPPORT REGL.EQ.+NIV. 800X420/160KG", lignes.getValue(12.3).designation)
    }

    @Test
    fun `le fournisseur vient du carnet, et la date de la tete du document`() {
        val tarif = LectureTarif.lire(proposition, listOf(gff, richardson))

        assertEquals(richardson, tarif.fournisseur)
        assertEquals(LocalDate.of(2026, 10, 26), tarif.date)
        assertNull(LectureTarif.lire(proposition, listOf(gff)).fournisseur)
    }

    @Test
    fun `sur une ligne a remise, c'est le prix net qui est retenu`() {
        val ligne = LectureTarif.lireLigne("POMPE RELEVAGE MINI  PR-220  2  45,00  30%  31,50  63,00")

        assertNotNull(ligne)
        assertEquals(31.5, ligne!!.prixNet, 0.001)
        assertEquals(2.0, ligne.quantite, 0.0)
        assertEquals("PR-220", ligne.reference)
    }

    @Test
    fun `les milliers separes d'une espace se recollent`() {
        val ligne = LectureTarif.lireLigne("GROUPE CONDENSATION  GC-12  1,00 U  1 250,00  1 250,00")

        assertNotNull(ligne)
        assertEquals(1250.0, ligne!!.prixNet, 0.001)
    }

    @Test
    fun `un chiffre mal lu fait ecarter la ligne plutot que la lire fausse`() {
        // 6 × 7,11 ne fait pas 42,90 : un « 5 » lu « 1 ».
        assertNull(LectureTarif.lireLigne("GOULOTTE BLANC 80X60MM  WHITE-80BCANA-2M  6,00 U  7,11  42,90"))
        assertNull(LectureTarif.lireLigne("MONTANT H.T  229,50"))
        assertNull(LectureTarif.lireLigne("1,00  2,00  2,00"))
    }

    @Test
    fun `les morceaux d'une meme hauteur font une ligne, meme un peu de travers`() {
        val fragments = listOf(
            Fragment("7,15", gauche = 700f, haut = 104f, bas = 124f),
            Fragment("GOULOTTE BLANC 80X60MM", gauche = 10f, haut = 100f, bas = 120f),
            Fragment("ANGLE A PLAT", gauche = 10f, haut = 130f, bas = 150f),
            Fragment("6,00 U", gauche = 500f, haut = 102f, bas = 122f),
            Fragment("1,90", gauche = 700f, haut = 133f, bas = 153f),
        )

        assertEquals(
            listOf("GOULOTTE BLANC 80X60MM  6,00 U  7,15", "ANGLE A PLAT  1,90"),
            LectureTarif.rangees(fragments),
        )
    }

    @Test
    fun `le rapprochement se fait par la reference, ponctuation et casse comprises`() {
        val barre = Article(designation = "Goulotte clim 80x60 — barre 2 m", reference = "WHITE-80BCANA-2M")
        val angle = Article(designation = "Goulotte clim 80x60 — angle plat")

        assertEquals(barre, LectureTarif.parReference("white 80bcana 2m", listOf(angle, barre)))
        assertNull("sans référence, rien n'est rapproché d'office", LectureTarif.parReference("", listOf(angle, barre)))
    }

    @Test
    fun `la suggestion prefere l'article qui ne dit pas plus que le tarif`() {
        val barre = Article(designation = "Goulotte clim 80x60 — barre 2 m")
        val angle = Article(designation = "Goulotte clim 80x60 — angle plat")
        val cuivre = Article(designation = "Cuivre frigorifique 1/4 — couronne 25 m")

        assertEquals(barre, LectureTarif.suggestion("GOULOTTE BLANC 80X60MM", listOf(angle, barre, cuivre)))
        assertEquals(angle, LectureTarif.suggestion("ANGLE A PLAT BLANC 80X60MM", listOf(angle, barre, cuivre)))
        assertNull(LectureTarif.suggestion("MOUSSE GEBSOMOUSSE AEROSOL 500ML", listOf(angle, barre, cuivre)))
    }
}
