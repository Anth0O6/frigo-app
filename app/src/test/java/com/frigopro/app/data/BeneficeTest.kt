package com.frigopro.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Ce qui reste dans la poche : la marge du matériel, la main-d'œuvre, et ce que
 * les gestes et les avoirs retirent, sur la bonne période. Les prix sont inventés.
 */
class BeneficeTest {

    /** Un mercredi : la semaine part du lundi 26. */
    private val aujourdhui = LocalDate.of(2026, 10, 28)

    private fun facture(
        id: String,
        emise: LocalDate?,
        statut: StatutFacture = StatutFacture.EMISE,
        payee: LocalDate? = null,
        nature: NatureFacture = NatureFacture.FACTURE,
        tvaOfferte: Boolean = false,
    ) = Facture(
        id = id,
        statut = statut,
        emiseLe = emise,
        payeeLe = payee,
        nature = nature,
        tvaOfferte = tvaOfferte,
    )

    private fun ligne(factureId: String, prix: Double, achat: Double = 0.0, quantite: Double = 1.0, offerte: Boolean = false) =
        LigneFacture(factureId = factureId, designation = "x", quantite = quantite, prixUnitaire = prix, prixAchat = achat, offerte = offerte)

    /**
     * Le matériel rapporte sa marge, la main-d'œuvre son montant : un split acheté
     * 600 € et vendu 900 €, plus 4 h à 50 €, font 300 + 200 = 500 € dans la poche.
     */
    @Test
    fun `le materiel rapporte sa marge et la main-d'oeuvre son montant`() {
        val b = CalculBenefice.apport(
            facture("f", aujourdhui),
            listOf(ligne("f", 900.0, achat = 600.0), ligne("f", 50.0, quantite = 4.0)),
        )
        assertEquals(300.0, b.margeMateriel, 0.0)
        assertEquals(200.0, b.mainOeuvre, 0.0)
        assertEquals(500.0, b.total, 0.0)
    }

    /**
     * Offert, le matériel coûte quand même : il retire son prix d'achat. La
     * main-d'œuvre offerte ne rapporte rien, et la TVA offerte se retire — la taxe
     * reste due, c'est l'entreprise qui la paie.
     */
    @Test
    fun `les gestes commerciaux se retirent`() {
        val b = CalculBenefice.apport(
            facture("f", aujourdhui, tvaOfferte = true),
            listOf(
                ligne("f", 30.0, achat = 12.0, offerte = true),
                ligne("f", 80.0, offerte = true),
                ligne("f", 100.0),
            ),
        )
        assertEquals(-12.0, b.margeMateriel, 0.0)
        assertEquals(100.0, b.mainOeuvre, 0.0)
        assertEquals("20 % de la ligne non offerte", 20.0, b.tvaOfferte, 0.0)
        assertEquals(68.0, b.total, 0.0)
    }

    /**
     * Chaque facture compte dans les périodes qui la contiennent : celle du jour
     * partout, celle de lundi dès la semaine, celle du 2 octobre dès le mois,
     * celle de mars dès l'année, celle de l'an dernier seulement au total.
     */
    @Test
    fun `chaque periode compte ce qui la contient`() {
        val dates = listOf(
            "jour" to aujourdhui,
            "lundi" to LocalDate.of(2026, 10, 26),
            "mois" to LocalDate.of(2026, 10, 2),
            "annee" to LocalDate.of(2026, 3, 15),
            "avant" to LocalDate.of(2025, 12, 31),
        )
        val factures = dates.map { (id, le) -> facture(id, le) }
        val lignes = dates.map { (id, _) -> ligne(id, 100.0) }

        val p = CalculBenefice.parPeriode(factures, lignes, BaseBenefice.FACTURE, aujourdhui)

        assertEquals(100.0, p.getValue(PeriodeBenefice.JOUR).total, 0.0)
        assertEquals(200.0, p.getValue(PeriodeBenefice.SEMAINE).total, 0.0)
        assertEquals(300.0, p.getValue(PeriodeBenefice.MOIS).total, 0.0)
        assertEquals(400.0, p.getValue(PeriodeBenefice.ANNEE).total, 0.0)
        assertEquals(500.0, p.getValue(PeriodeBenefice.TOUT).total, 0.0)
    }

    /**
     * Un brouillon et une facture annulée ne comptent jamais. Facturé, une facture
     * compte à son émission ; encaissé, seulement payée, à sa date de règlement.
     */
    @Test
    fun `facture ou encaisse ne comptent pas la meme chose`() {
        val factures = listOf(
            facture("emise", aujourdhui),
            facture("payee", LocalDate.of(2026, 9, 10), StatutFacture.PAYEE, payee = aujourdhui),
            facture("brouillon", null, StatutFacture.BROUILLON),
            facture("annulee", aujourdhui, StatutFacture.ANNULEE),
        )
        val lignes = factures.map { ligne(it.id, 100.0) }

        val facture = CalculBenefice.parPeriode(factures, lignes, BaseBenefice.FACTURE, aujourdhui)
        assertEquals(100.0, facture.getValue(PeriodeBenefice.JOUR).total, 0.0)
        assertEquals(200.0, facture.getValue(PeriodeBenefice.TOUT).total, 0.0)

        val encaisse = CalculBenefice.parPeriode(factures, lignes, BaseBenefice.ENCAISSE, aujourdhui)
        assertEquals("la payée du jour, et elle seule", 100.0, encaisse.getValue(PeriodeBenefice.JOUR).total, 0.0)
        assertEquals(1, encaisse.getValue(PeriodeBenefice.TOUT).documents)
    }

    /** Un avoir émis retire son montant HT, sans rien rendre en coût. */
    @Test
    fun `un avoir retire son montant`() {
        val factures = listOf(
            facture("f", aujourdhui),
            facture("av", aujourdhui, nature = NatureFacture.AVOIR),
        )
        val lignes = listOf(
            ligne("f", 900.0, achat = 600.0),
            ligne("av", 900.0, achat = 600.0),
        )

        val b = CalculBenefice.parPeriode(factures, lignes, BaseBenefice.FACTURE, aujourdhui)
            .getValue(PeriodeBenefice.JOUR)

        assertEquals(900.0, b.avoirs, 0.0)
        assertEquals(-600.0, b.total, 0.0)
    }

    /** Une période sans facture n'a rien compté : ce n'est pas « rien gagné ». */
    @Test
    fun `une periode vide ne compte aucun document`() {
        val p = CalculBenefice.parPeriode(emptyList(), emptyList(), BaseBenefice.FACTURE, aujourdhui)
        assertEquals(0, p.getValue(PeriodeBenefice.JOUR).documents)
    }
}
