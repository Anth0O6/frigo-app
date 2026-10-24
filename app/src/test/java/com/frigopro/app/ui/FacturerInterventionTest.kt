package com.frigopro.app.ui

import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.FactureRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxFactureDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxParametresDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.FauxSuiviDao
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.data.SuiviRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * « Facturer cette intervention », tel que l'écran l'appelle.
 *
 * Le défaut réparé : le geste créait la facture **puis** passait par le chemin
 * « créer un devis », qui ouvrait un devis vide et numéroté avant que l'onglet
 * ne bascule sur la facture. [facturerIntervention] n'a plus accès à aucun
 * devis — sa signature ne porte que les factures et la navigation —, et ce test
 * tient ce qu'il doit faire : créer la facture, la poser comme facture ouverte
 * (c'est ce que l'onglet Factures montre) et mener aux factures, une fois.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FacturerInterventionTest {

    private val daoFactures = FauxFactureDao()
    private val daoInterventions = FauxInterventionDao()
    private val stockage = FauxRangementPhotos()

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    @Test
    fun `facturer ouvre la facture et mene aux factures, sans passer par les devis`() = runTest {
        val factures = creerViewModel()
        var versFactures = 0

        facturerIntervention(TERMINEE, factures) { versFactures++ }
        advanceUntilIdle()

        val facture = daoFactures.contenu.single()
        assertEquals(TERMINEE.id, facture.interventionId)
        assertEquals("l'onglet montre la facture créée", facture.id, factures.ouverte.value)
        assertEquals("une seule navigation, vers les factures", 1, versFactures)
    }

    @Test
    fun `facturer deux fois rouvre la meme facture`() = runTest {
        val factures = creerViewModel()

        facturerIntervention(TERMINEE, factures) {}
        advanceUntilIdle()
        facturerIntervention(TERMINEE, factures) {}
        advanceUntilIdle()

        assertEquals(1, daoFactures.contenu.size)
        assertEquals(daoFactures.contenu.single().id, factures.ouverte.value)
    }

    @Test
    fun `sans intervention, rien ne se cree ni ne navigue`() = runTest {
        val factures = creerViewModel()
        var versFactures = 0

        facturerIntervention(null, factures) { versFactures++ }
        advanceUntilIdle()

        assertEquals(0, daoFactures.contenu.size)
        assertEquals(0, versFactures)
    }

    private fun TestScope.creerViewModel(): FacturesViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return FacturesViewModel(
            FactureRepository(daoFactures),
            SuiviRepository(FauxSuiviDao(daoInterventions), stockage),
            ClientRepository(FauxClientDao(), stockage),
            ParametresRepository(FauxParametresDao(), stockage),
            ProducteurPdf { null },
        )
    }

    private companion object {

        val TERMINEE = Intervention(
            id = "int-1",
            date = LocalDate.of(2026, 3, 12),
            heure = LocalTime.of(8, 0),
            client = "Boucherie Morel",
            ville = "Lyon",
            statut = StatutIntervention.TERMINEE,
            numero = "INT-2603-001",
        )
    }
}
