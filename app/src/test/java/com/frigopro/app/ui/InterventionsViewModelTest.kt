package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.StatutIntervention
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
    private val daoClients = FauxClientDao()

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
    fun `changer le statut fait avancer l'intervention en boucle`() = runTest {
        val viewModel = creerViewModel()
        enregistrer(viewModel, client = "Fromagerie Hardy", ville = "Caudebec")
        advanceUntilIdle()

        assertEquals(StatutIntervention.A_FAIRE, dao.contenu.single().statut)

        viewModel.onChangerStatut(dao.contenu.single())
        advanceUntilIdle()
        assertEquals(StatutIntervention.EN_COURS, dao.contenu.single().statut)

        viewModel.onChangerStatut(dao.contenu.single())
        advanceUntilIdle()
        assertEquals(StatutIntervention.TERMINEE, dao.contenu.single().statut)

        viewModel.onChangerStatut(dao.contenu.single())
        advanceUntilIdle()
        assertEquals(
            "revenir au début permet de corriger une fausse manœuvre",
            StatutIntervention.A_FAIRE,
            dao.contenu.single().statut,
        )
    }

    @Test
    fun `changer le statut ne touche a rien d'autre`() = runTest {
        val viewModel = creerViewModel()
        enregistrer(viewModel, client = "Primeur Vasseur", ville = "Duclair")
        advanceUntilIdle()
        val avant = dao.contenu.single()

        viewModel.onChangerStatut(avant)
        advanceUntilIdle()

        val apres = dao.contenu.single()
        assertEquals(avant.copy(statut = apres.statut, modifieLe = apres.modifieLe), apres)
        assertNull("la liste ne doit pas ouvrir le formulaire", viewModel.formulaire.value)
    }

    @Test
    fun `un client absent du carnet y est inscrit a l'enregistrement`() = runTest {
        val viewModel = creerViewModel()

        enregistrer(viewModel, client = "Fromagerie Hardy", ville = "Caudebec")
        advanceUntilIdle()

        val inscrit = daoClients.contenu.single()
        assertEquals("Fromagerie Hardy", inscrit.nom)
        assertEquals("Caudebec", inscrit.ville)
        assertEquals(
            "l'intervention doit pointer vers le client inscrit",
            inscrit.id,
            dao.contenu.single().clientId,
        )
    }

    @Test
    fun `deux interventions chez le meme client ne le dupliquent pas`() = runTest {
        val viewModel = creerViewModel()

        enregistrer(viewModel, client = "Boucherie Lemoine", ville = "Rouen")
        advanceUntilIdle()
        enregistrer(viewModel, client = "Boucherie Lemoine", ville = "Rouen")
        advanceUntilIdle()

        assertEquals(1, daoClients.contenu.size)
        assertEquals(2, dao.contenu.size)
        assertEquals(1, dao.contenu.mapNotNull { it.clientId }.distinct().size)
    }

    @Test
    fun `choisir un client du carnet remplit le formulaire et le rattache`() = runTest {
        val viewModel = creerViewModel()
        val client = Client(id = "cl-1", nom = "Restaurant Le Comptoir", ville = "Elbeuf")
        viewModel.onNouvelleIntervention()

        viewModel.onClientChoisi(client)

        val formulaire = viewModel.formulaire.value!!
        assertEquals("Restaurant Le Comptoir", formulaire.client)
        assertEquals("Elbeuf", formulaire.ville)
        assertEquals("cl-1", formulaire.clientId)
    }

    @Test
    fun `un client venu du carnet n'y est pas reinscrit`() = runTest {
        val viewModel = creerViewModel()
        val client = Client(id = "cl-1", nom = "Restaurant Le Comptoir", ville = "Elbeuf")
        viewModel.onNouvelleIntervention()
        viewModel.onClientChoisi(client)

        viewModel.onValiderFormulaire()
        advanceUntilIdle()

        assertTrue("rien ne doit être créé pour un client déjà rattaché", daoClients.contenu.isEmpty())
        assertEquals("cl-1", dao.contenu.single().clientId)
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
        return InterventionsViewModel(InterventionRepository(dao), ClientRepository(daoClients))
    }
}
