package com.frigopro.app.ui

import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxTypeInterventionDao
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.TypeIntervention
import com.frigopro.app.data.TypeInterventionRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ReglagesViewModelTest {

    private val daoInterventions = FauxInterventionDao()
    private val daoTypes = FauxTypeInterventionDao(daoInterventions)

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ajouter un type l'inscrit dans la liste`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onAjouterType()
        assertEquals(DialogueReglages.Creation, viewModel.dialogue.value)

        viewModel.onValiderIntitule("Mise en service")
        advanceUntilIdle()

        assertNull("le dialogue doit se refermer", viewModel.dialogue.value)
        assertEquals("Mise en service", daoTypes.contenu.single().libelle)
    }

    @Test
    fun `un intitule vide ne cree rien et laisse le dialogue ouvert`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onAjouterType()

        viewModel.onValiderIntitule("   ")
        advanceUntilIdle()

        assertEquals(DialogueReglages.Creation, viewModel.dialogue.value)
        assertTrue(daoTypes.contenu.isEmpty())
    }

    /** Le cœur de la demande : la correction doit remonter jusqu'aux tournées passées. */
    @Test
    fun `renommer un type corrige les interventions qui l'utilisent`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Entretien anuel")
        daoTypes.enregistrer(type)
        daoInterventions.enregistrer(intervention("i1", "t1", "Entretien anuel"))

        viewModel.onRenommerType(type)
        viewModel.onValiderIntitule("Entretien annuel")
        advanceUntilIdle()

        assertEquals("Entretien annuel", daoTypes.contenu.single().libelle)
        assertEquals("Entretien annuel", daoInterventions.contenu.single().typeLibelle)
    }

    @Test
    fun `supprimer un type demande confirmation avant d'agir`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Dépannage")
        daoTypes.enregistrer(type)

        viewModel.onSupprimerType(type)
        advanceUntilIdle()

        assertEquals(DialogueReglages.Suppression(type), viewModel.dialogue.value)
        assertEquals("rien ne doit être supprimé avant confirmation", 1, daoTypes.contenu.size)

        viewModel.onConfirmerSuppression()
        advanceUntilIdle()

        assertTrue(daoTypes.contenu.isEmpty())
    }

    @Test
    fun `fermer le dialogue de suppression ne supprime rien`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Dépannage")
        daoTypes.enregistrer(type)

        viewModel.onSupprimerType(type)
        viewModel.onFermerDialogue()
        advanceUntilIdle()

        assertNull(viewModel.dialogue.value)
        assertEquals(1, daoTypes.contenu.size)
    }

    /** Supprimer un type ne doit rien retirer d'une tournée déjà saisie. */
    @Test
    fun `supprimer un type laisse son intitule sur les interventions`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Dépannage")
        daoTypes.enregistrer(type)
        daoInterventions.enregistrer(intervention("i1", "t1", "Dépannage"))

        viewModel.onSupprimerType(type)
        viewModel.onConfirmerSuppression()
        advanceUntilIdle()

        val restee = daoInterventions.contenu.single()
        assertEquals("Dépannage", restee.typeLibelle)
        assertNull(restee.typeId)
    }

    /** Un dialogue fermé ne doit pas pouvoir écrire : la validation n'a plus de cible. */
    @Test
    fun `valider sans dialogue ouvert n'ecrit rien`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onValiderIntitule("Fantôme")
        advanceUntilIdle()

        assertTrue(daoTypes.contenu.isEmpty())
    }

    private fun intervention(id: String, typeId: String?, typeLibelle: String) = Intervention(
        id = id,
        date = LocalDate.of(2026, 9, 10),
        heure = LocalTime.of(9, 0),
        client = "Client",
        ville = "Rouen",
        typeId = typeId,
        typeLibelle = typeLibelle,
    )

    /** Même raison que dans [InterventionsViewModelTest]. */
    private fun TestScope.creerViewModel(): ReglagesViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return ReglagesViewModel(TypeInterventionRepository(daoTypes))
    }
}
