package com.frigopro.app.ui

import com.frigopro.app.data.Parametres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que l'accueil réclame, et ce qu'il laisse tranquille.
 *
 * Sept valeurs des Réglages arrivent à zéro ou vides sur une installation neuve,
 * à dessein — le projet préfère partout une valeur absente à une valeur devinée.
 * Le prix de ce choix est qu'il faut le **dire**, sans quoi un devis sort à 0 €
 * sans que rien n'en nomme la cause. Ces tests tiennent les deux bords : ce qui
 * doit être réclamé l'est, et ce qui est réglé ne l'est plus.
 */
class ReglagesManquantsTest {

    /**
     * Une installation neuve les réclame tous les quatre. C'est le premier
     * écran que voit un technicien, et c'est là que la liste compte le plus.
     */
    @Test
    fun `une installation neuve reclame les quatre reglages`() {
        val manquants = ReglageManquant.de(Parametres())

        assertEquals(ReglageManquant.entries.toList(), manquants)
    }

    /**
     * Le bandeau doit pouvoir disparaître, sinon c'est une alerte qu'on cesse
     * de lire : quatre réglages posés, plus rien à signaler.
     */
    @Test
    fun `tout regle ne reclame plus rien`() {
        val manquants = ReglageManquant.de(
            Parametres(
                entreprise = "Froid Azur",
                tauxHoraire = 65.0,
                coutHoraireInterne = 32.0,
                coefficientMateriel = 1.4,
            ),
        )

        assertTrue(manquants.isEmpty())
    }

    /**
     * Le taux facturé et le coût interne se réclament **séparément** : les
     * confondre est précisément l'erreur que la distinction cherche à éviter, et
     * un bandeau qui se tairait sur le second dès que le premier est posé
     * laisserait la marge d'une intervention incalculable sans rien en dire.
     */
    @Test
    fun `le taux facture pose ne dispense pas du cout interne`() {
        val manquants = ReglageManquant.de(Parametres(tauxHoraire = 65.0))

        assertTrue(ReglageManquant.TAUX_HORAIRE !in manquants)
        assertTrue(ReglageManquant.COUT_HORAIRE in manquants)
    }

    /**
     * Une raison sociale faite d'espaces n'est pas une raison sociale : elle ne
     * s'imprimerait pas plus sur un devis qu'un champ vide, et le laisser passer
     * ferait taire le bandeau sans rien régler.
     */
    @Test
    fun `une raison sociale faite d'espaces ne compte pas`() {
        val manquants = ReglageManquant.de(Parametres(entreprise = "   "))

        assertTrue(ReglageManquant.ENTREPRISE in manquants)
    }

    /**
     * Chaque ligne doit mener quelque part : une alerte qui ne mène nulle part
     * n'est pas une réponse à « et maintenant ? ». Le test tient la propriété
     * pour toute valeur à venir, et non pour les quatre d'aujourd'hui.
     */
    @Test
    fun `chaque reglage reclame nomme sa page et ce qu'il empeche`() {
        ReglageManquant.entries.forEach { manquant ->
            assertTrue(manquant.intitule.isNotBlank())
            assertTrue(manquant.consequence.isNotBlank())
        }
    }
}
