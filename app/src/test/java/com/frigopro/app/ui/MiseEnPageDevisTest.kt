package com.frigopro.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La mise en page du devis : ce qui se vérifie sans ouvrir le PDF.
 *
 * Un bloc de totaux coupé par un saut de page, un « Page 2 / 1 », une dernière
 * ligne qui déborde sous le pied : ces défauts ne se voient qu'en ouvrant le
 * fichier, c'est-à-dire après l'avoir envoyé au client.
 */
class MiseEnPageDevisTest {

    private fun ligne(index: Int) = LigneImprimee(
        designation = "Ligne $index",
        quantite = "1",
        prixUnitaire = "100,00 €",
        montant = "100,00 €",
    )

    private val totaux = listOf(
        LigneTotalImprimee("Total HT", "100,00 €"),
        LigneTotalImprimee("TVA 20 %", "20,00 €"),
        LigneTotalImprimee("TOTAL À PAYER", "120,00 €", forte = true),
    )

    /**
     * Un devis sans ligne produit une page. Ne rien imprimer laisserait croire à
     * un échec de l'export plutôt qu'à un devis vide.
     */
    @Test
    fun `un devis vide tient sur une page`() {
        val pages = MiseEnPageDevis.paginer(emptyList(), totaux)

        assertEquals(1, pages.size)
        assertEquals(1, pages.single().total)
        assertEquals(totaux, pages.single().totaux)
    }

    @Test
    fun `un devis court tient sur une page, totaux compris`() {
        val pages = MiseEnPageDevis.paginer((1..5).map(::ligne), totaux)

        assertEquals(1, pages.size)
        assertEquals(5, pages.single().lignes.size)
        assertTrue("les totaux sont sur la même page", pages.single().totaux.isNotEmpty())
    }

    /**
     * Le bloc des totaux ne se coupe pas : s'il ne tient pas sous la dernière
     * ligne, il part sur une page à lui. Un total TTC séparé de son hors taxes par
     * un saut de page fait relire un devis trois fois.
     */
    @Test
    fun `le bloc des totaux part sur sa propre page quand il ne tient pas`() {
        val pleine = MiseEnPageDevis.lignesPremierePage

        val pages = MiseEnPageDevis.paginer((1..pleine).map(::ligne), totaux)

        assertEquals(2, pages.size)
        assertTrue("la première n'en porte aucun", pages.first().totaux.isEmpty())
        assertEquals(totaux, pages.last().totaux)
        assertTrue("et aucune ligne avec eux", pages.last().lignes.isEmpty())
    }

    /**
     * « Page 2 / 3 » n'est imprimable que si le découpage précède le dessin : un
     * document paginé au fil du dessin ne connaît son nombre de pages qu'à la fin,
     * quand la première est déjà écrite.
     */
    @Test
    fun `chaque page connait le nombre total de pages`() {
        val pages = MiseEnPageDevis.paginer((1..80).map(::ligne), totaux)

        assertTrue("quatre-vingts lignes ne tiennent pas sur une page", pages.size >= 3)
        pages.forEachIndexed { index, page ->
            assertEquals(index + 1, page.numero)
            assertEquals("toutes annoncent le même total", pages.size, page.total)
        }
    }

    @Test
    fun `aucune ligne n'est perdue ni dupliquee au decoupage`() {
        val lignes = (1..100).map(::ligne)

        val pages = MiseEnPageDevis.paginer(lignes, totaux)

        assertEquals(lignes, pages.flatMap { it.lignes })
    }

    /**
     * Les pages suivantes portent plus de lignes : elles n'ont pas l'en-tête à
     * supporter, seulement la ligne de titres.
     */
    @Test
    fun `la premiere page porte moins de lignes que les suivantes`() {
        assertTrue(MiseEnPageDevis.lignesPremierePage < MiseEnPageDevis.lignesPagesSuivantes)
        assertTrue("et il en tient quand même une vingtaine", MiseEnPageDevis.lignesPremierePage >= 20)
    }

    /**
     * La dernière ligne d'une page pleine doit rester au-dessus du pied : un
     * tableau qui recouvre le numéro de page rend le document illisible, et le
     * dessin, lui, ne s'en plaindrait pas.
     */
    @Test
    fun `la derniere ligne d'une page pleine reste au-dessus du pied`() {
        listOf(true, false).forEach { premiere ->
            val place = if (premiere) {
                MiseEnPageDevis.lignesPremierePage
            } else {
                MiseEnPageDevis.lignesPagesSuivantes
            }
            val bas = MiseEnPageDevis.hautDuTableau(premiere) + place * MiseEnPageDevis.HAUTEUR_LIGNE

            assertTrue(
                "page ${if (premiere) "1" else "suivante"} : le tableau descend à $bas",
                bas <= MiseEnPageDevis.hautDuPied,
            )
        }
    }

    /**
     * Les colonnes couvrent la largeur utile sans la dépasser : un montant calé
     * au-delà de la marge droite sortirait de la page à l'impression.
     */
    @Test
    fun `les colonnes tiennent dans la largeur utile`() {
        val bords = MiseEnPageDevis.bordsDroits()

        assertEquals("quatre colonnes", 4, bords.size)
        assertEquals(
            "la dernière finit sur la marge droite",
            MiseEnPageDevis.LARGEUR_PAGE - MiseEnPageDevis.MARGE,
            bords.last(),
        )
        assertEquals("et elles montent", bords.sorted(), bords)
        assertTrue("la première commence après la marge gauche", bords.first() > MiseEnPageDevis.MARGE)
    }

    /** Les parts des colonnes font un tout : une part manquante laisserait un blanc. */
    @Test
    fun `les parts de colonnes font la largeur entiere`() {
        val somme = MiseEnPageDevis.PART_DESIGNATION + MiseEnPageDevis.PART_QUANTITE +
            MiseEnPageDevis.PART_PRIX + MiseEnPageDevis.PART_MONTANT

        assertEquals(1.0, somme, 0.001)
    }

    /** A4 : les dimensions du `PdfDocument`, pas celles d'un écran. */
    @Test
    fun `la page est un A4 en points`() {
        assertEquals(595, MiseEnPageDevis.LARGEUR_PAGE)
        assertEquals(842, MiseEnPageDevis.TOTAL_HAUTEUR_PAGE)
    }
}
