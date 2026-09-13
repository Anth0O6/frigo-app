package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Le rappel des factures échues : son minutage, et ce qu'il dit.
 *
 * Deux choses seulement se vérifient sans téléphone, et ce sont les deux qui
 * peuvent être fausses sans qu'on s'en aperçoive : le délai jusqu'au prochain
 * rappel — un calcul qui se trompe d'un jour ne se voit qu'en attendant un
 * matin — et le texte, qui est ce que l'utilisateur lira.
 */
class RappelsFacturesTest {

    @Test
    fun `le rappel du matin attend le matin`() {
        val delai = RappelsFactures.delaiJusquAuRappel(
            LocalDateTime.of(2026, 3, 12, 6, 30),
        )

        assertEquals("une heure et demie jusqu'à 8 h", 90L, delai.toMinutes())
    }

    /**
     * Le cas qui décide de `KEEP` plutôt que d'`UPDATE` au moment de programmer :
     * l'application ouverte après l'heure doit viser le lendemain, et non
     * notifier tout de suite ni attendre un délai négatif.
     */
    @Test
    fun `passe l'heure, le rappel vise le lendemain`() {
        val delai = RappelsFactures.delaiJusquAuRappel(
            LocalDateTime.of(2026, 3, 12, 9, 0),
        )

        assertEquals(23L * 60, delai.toMinutes())
        assertTrue("jamais de délai négatif", !delai.isNegative)
    }

    @Test
    fun `une seule facture echue est nommee`() {
        val (titre, corps) = RappelsFactures.texte(
            listOf(
                FactureChiffree(
                    Facture(numero = "FAC-2026-0042", clientNom = "Boucherie Morel"),
                    totalHt = 500.0,
                ),
            ),
        )

        assertEquals("1 facture échue", titre)
        assertTrue("savoir laquelle évite d'ouvrir l'application", corps.contains("Boucherie Morel"))
        assertTrue(corps.contains("FAC-2026-0042"))
        assertTrue("600 € TTC", corps.contains("600,00"))
    }

    /**
     * Au-delà d'une, nommer n'a plus de sens : c'est le nombre et le montant qui
     * décident s'il faut ouvrir l'application maintenant.
     */
    @Test
    fun `plusieurs factures se comptent et se totalisent`() {
        val (titre, corps) = RappelsFactures.texte(
            listOf(
                FactureChiffree(Facture(numero = "FAC-2026-0042", clientNom = "Morel"), 500.0),
                FactureChiffree(Facture(numero = "FAC-2026-0043", clientNom = "Delaunay"), 250.0),
            ),
        )

        assertEquals("2 factures échues", titre)
        assertTrue("900 € TTC au total", corps.contains("900,00"))
        assertTrue("aucun nom n'est mis en avant", !corps.contains("Morel"))
    }
}
