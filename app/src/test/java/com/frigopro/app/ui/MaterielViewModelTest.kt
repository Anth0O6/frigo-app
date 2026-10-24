package com.frigopro.app.ui

import com.frigopro.app.data.Article
import com.frigopro.app.data.FauxMaterielDao
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.data.LieuStock
import com.frigopro.app.data.MaterielRepository
import com.frigopro.app.data.Stock
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le magasin et le carnet des fournisseurs, vus de l'écran.
 *
 * Les cas retenus sont ceux où l'écran trahirait ce qu'il annonce : une fiche
 * créée qui ne s'ouvre pas alors que la boîte promet qu'on la complétera
 * « ensuite, sur la fiche », un tri qui range « Écrou » après « Z », et un filtre
 * de ville qui ne trouve pas « Évreux » quand on tape « evreux ».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaterielViewModelTest {

    private val dao = FauxMaterielDao()

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    /**
     * Un article créé **s'ouvre** : on le crée pour lui donner un stock, un seuil
     * et un prix, qui se saisissent sur sa fiche.
     */
    @Test
    fun `un article cree ouvre sa fiche`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onNouvelArticle()
        viewModel.onBrouillonArticle(Article(designation = "  Pompe de relevage "))
        viewModel.onEnregistrerArticle()
        advanceUntilIdle()

        assertNull("le brouillon est refermé", viewModel.brouillonArticle.value)
        assertEquals("Pompe de relevage", viewModel.articleOuvert.value?.article?.designation)
    }

    /** Sans désignation, rien n'est écrit et rien ne s'ouvre. */
    @Test
    fun `un article sans designation ne s'ouvre pas`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onNouvelArticle()
        viewModel.onEnregistrerArticle()
        advanceUntilIdle()

        assertNull(viewModel.articleOuvert.value)
        assertTrue(viewModel.brouillonArticle.value != null)
    }

    /**
     * Un fournisseur créé **s'ouvre** : la boîte de création annonce que
     * l'adresse et le catalogue s'ajoutent ensuite, sur la fiche.
     */
    @Test
    fun `un fournisseur cree ouvre sa fiche`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onNouveauFournisseur()
        viewModel.onBrouillonFournisseur(Fournisseur(nom = "Clim Distribution"))
        viewModel.onEnregistrerFournisseur()
        advanceUntilIdle()

        assertNull(viewModel.brouillonFournisseur.value)
        assertEquals("Clim Distribution", viewModel.fournisseurOuvert.value?.nom)
    }

    /**
     * Le manquant d'abord, puis **l'ordre français du dépôt** dans chaque moitié :
     * un second tri sur la désignation rangeait « Écrou » après « Zinc ».
     */
    @Test
    fun `le magasin garde l'ordre francais sous le manquant`() = runTest {
        dao.enregistrerArticle(Article(id = "z", designation = "Zinc en bande"))
        dao.enregistrerArticle(Article(id = "e", designation = "Écrou laiton 3/8"))
        dao.enregistrerArticle(Article(id = "v", designation = "Vis inox"))
        dao.enregistrerStock(Stock(articleId = "z", lieu = LieuStock.CAMION, quantite = 1.0, minimum = 3.0))
        val viewModel = creerViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf("Zinc en bande", "Écrou laiton 3/8", "Vis inox"),
            viewModel.magasin.value.map { it.article.designation },
        )
    }

    /** « evreux » trouve « Évreux » : la ville passe par la recherche commune. */
    @Test
    fun `le filtre de ville replie les accents`() {
        val evreux = Fournisseur(nom = "Frigo Normandie", ville = "Évreux")
        val rouen = Fournisseur(nom = "Clim Rouen", ville = "Rouen")

        val filtres = FiltresFournisseurs(ville = "evreux")

        assertTrue(filtres.retient(evreux))
        assertFalse(filtres.retient(rouen))
        assertTrue("sans ville, tout passe", FiltresFournisseurs().retient(rouen))
    }

    /** Les flux sont collectés aussitôt, pour la raison écrite dans [EquipementsViewModelTest]. */
    private fun TestScope.creerViewModel(): MaterielViewModel {
        val ordonnanceur = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(ordonnanceur)
        val viewModel = MaterielViewModel(MaterielRepository(dao))
        listOf(
            viewModel.magasin,
            viewModel.articleOuvert,
            viewModel.fournisseurs,
            viewModel.fournisseurOuvert,
        ).forEach { flux -> backgroundScope.launch(ordonnanceur) { flux.collect { } } }
        return viewModel
    }
}
