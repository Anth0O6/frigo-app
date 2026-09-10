package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.FauxClientDao
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientsViewModelTest {

    private val dao = FauxClientDao()

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    @Test
    fun `un nouveau client ouvre une fiche vide en creation`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onNouveauClient()

        val fiche = viewModel.fiche.value
        assertNotNull(fiche)
        assertTrue(fiche!!.estCreation)
        assertEquals("", fiche.nom)
    }

    @Test
    fun `ouvrir un client du carnet remplit la fiche`() = runTest {
        val viewModel = creerViewModel()
        val client = Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen")

        viewModel.onOuvrirFiche(client)

        val fiche = viewModel.fiche.value!!
        assertEquals("cl-1", fiche.id)
        assertEquals("Boucherie Lemoine", fiche.nom)
    }

    @Test
    fun `enregistrer une fiche ecrit au carnet et la referme`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouveauClient()
        viewModel.onFicheChange(
            viewModel.fiche.value!!.copy(
                nom = "Fromagerie Hardy",
                ville = "Caudebec",
                adresse = "3 rue du Pont",
                telephone = "02 35 11 11 11",
            ),
        )

        viewModel.onEnregistrerFiche()
        advanceUntilIdle()

        assertNull("la fiche doit se refermer", viewModel.fiche.value)
        val enregistre = dao.contenu.single()
        assertEquals("Fromagerie Hardy", enregistre.nom)
        assertEquals("3 rue du Pont", enregistre.adresse)
        assertEquals("02 35 11 11 11", enregistre.telephone)
    }

    /** Compléter une fiche existante ne doit pas créer un second client. */
    @Test
    fun `completer un client existant le remplace au lieu de le doubler`() = runTest {
        val viewModel = creerViewModel()
        val client = Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen")
        dao.enregistrer(client)

        viewModel.onOuvrirFiche(client)
        viewModel.onFicheChange(viewModel.fiche.value!!.copy(telephone = "0235000000"))
        viewModel.onEnregistrerFiche()
        advanceUntilIdle()

        val enregistre = dao.contenu.single()
        assertEquals("cl-1", enregistre.id)
        assertEquals("0235000000", enregistre.telephone)
    }

    @Test
    fun `une saisie incomplete laisse la fiche ouverte et n'ecrit rien`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouveauClient()
        viewModel.onFicheChange(viewModel.fiche.value!!.copy(nom = "Sans ville"))

        viewModel.onEnregistrerFiche()
        advanceUntilIdle()

        assertNotNull("la fiche doit rester ouverte", viewModel.fiche.value)
        assertTrue(dao.contenu.isEmpty())
    }

    @Test
    fun `fermer la fiche abandonne la saisie`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouveauClient()
        viewModel.onFicheChange(viewModel.fiche.value!!.copy(nom = "Abandonné", ville = "Rouen"))

        viewModel.onFermerFiche()
        advanceUntilIdle()

        assertNull(viewModel.fiche.value)
        assertTrue(dao.contenu.isEmpty())
    }

    /** Même raison que dans [InterventionsViewModelTest]. */
    private fun TestScope.creerViewModel(): ClientsViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return ClientsViewModel(ClientRepository(dao))
    }
}
