package com.frigopro.app.ui

import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.InterventionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterventionsViewModelTest {

    private val dao = FauxInterventionDao()

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    @Test
    fun `les fleches deplacent la journee consultee d'un jour`() = runTest {
        val viewModel = creerViewModel()
        val depart = viewModel.jour.value

        viewModel.onJourSuivant()
        assertEquals(depart.plusDays(1), viewModel.jour.value)

        viewModel.onJourPrecedent()
        viewModel.onJourPrecedent()
        assertEquals(depart.minusDays(1), viewModel.jour.value)
    }

    @Test
    fun `une nouvelle intervention est proposee sur la journee consultee`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onJourSuivant()

        viewModel.onNouvelleIntervention()

        val formulaire = viewModel.formulaire.value
        assertNotNull(formulaire)
        assertEquals(viewModel.jour.value, formulaire!!.date)
        assertTrue("une nouvelle intervention n'a pas d'identifiant", formulaire.estCreation)
    }

    @Test
    fun `une saisie incomplete laisse le formulaire ouvert et n'ecrit rien`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouvelleIntervention()
        viewModel.onFormulaireChange(viewModel.formulaire.value!!.copy(client = "Boucherie Lemoine"))

        viewModel.onValiderFormulaire()
        advanceUntilIdle()

        assertNotNull("le formulaire doit rester ouvert", viewModel.formulaire.value)
        assertTrue("rien ne doit être enregistré", dao.contenu.isEmpty())
    }

    @Test
    fun `enregistrer une intervention datee d'un autre jour deplace l'ecran sur ce jour`() = runTest {
        val viewModel = creerViewModel()
        val demain = viewModel.jour.value.plusDays(1)
        viewModel.onNouvelleIntervention()
        viewModel.onFormulaireChange(
            viewModel.formulaire.value!!.copy(date = demain, client = "Fromagerie Hardy", ville = "Caudebec"),
        )

        viewModel.onValiderFormulaire()
        advanceUntilIdle()

        assertNull("le formulaire doit se refermer", viewModel.formulaire.value)
        assertEquals("sinon la ligne disparaîtrait sans un mot", demain, viewModel.jour.value)
        assertEquals("Fromagerie Hardy", dao.contenu.single().client)
    }

    @Test
    fun `supprimer retire l'intervention et ferme le formulaire`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouvelleIntervention()
        viewModel.onFormulaireChange(
            viewModel.formulaire.value!!.copy(client = "Primeur Vasseur", ville = "Duclair"),
        )
        viewModel.onValiderFormulaire()
        advanceUntilIdle()

        viewModel.onModifierIntervention(dao.contenu.single())
        viewModel.onSupprimerIntervention()
        advanceUntilIdle()

        assertNull(viewModel.formulaire.value)
        assertTrue(dao.contenu.isEmpty())
    }

    @Test
    fun `la liste ne montre que la journee consultee`() = runTest {
        val viewModel = creerViewModel()
        val lundi = viewModel.jour.value
        val mardi = lundi.plusDays(1)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.interventions.collect { }
        }

        enregistrer(viewModel, client = "Le jour même", ville = "Rouen")
        enregistrer(viewModel, client = "Le lendemain", ville = "Elbeuf", date = mardi)
        advanceUntilIdle()

        assertEquals(mardi, viewModel.jour.value)
        assertEquals(listOf("Le lendemain"), viewModel.interventions.value.map { it.client })

        viewModel.onJourPrecedent()
        advanceUntilIdle()

        assertEquals(listOf("Le jour même"), viewModel.interventions.value.map { it.client })
    }

    private fun enregistrer(
        viewModel: InterventionsViewModel,
        client: String,
        ville: String,
        date: java.time.LocalDate = viewModel.jour.value,
    ) {
        viewModel.onNouvelleIntervention()
        viewModel.onFormulaireChange(
            viewModel.formulaire.value!!.copy(date = date, client = client, ville = ville),
        )
        viewModel.onValiderFormulaire()
    }

    /**
     * `Dispatchers.Main` doit partager l'ordonnanceur du test, sinon les
     * coroutines lancées par le ViewModel ne s'exécuteraient jamais.
     */
    private fun TestScope.creerViewModel(): InterventionsViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return InterventionsViewModel(InterventionRepository(dao))
    }
}
