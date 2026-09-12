package com.frigopro.app.ui

import com.frigopro.app.data.CategoriePrestation
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxDevisDao
import com.frigopro.app.data.FauxEquipementDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxParametresDao
import com.frigopro.app.data.FauxPrestationDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.PrestationRepository
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * L'onglet Devis : le geste commercial, et la quantité que le multi-split
 * pré-remplit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevisViewModelTest {

    private val daoDevis = FauxDevisDao()
    private val daoClients = FauxClientDao()
    private val daoParametres = FauxParametresDao()
    private val daoPrestations = FauxPrestationDao()
    private val daoInterventions = FauxInterventionDao()
    private val daoEquipements = FauxEquipementDao(daoInterventions)
    private val stockage = FauxRangementPhotos()

    private val mai = LocalDate.of(2026, 5, 14)

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    /**
     * Le cas qui motive tout le multi-split : poser un bi-split, c'est le même
     * groupe extérieur et deux unités, et la main-d'œuvre double sans que le
     * forfait de déplacement double.
     */
    @Test
    fun `une prestation par unite arrive au nombre d'unites du groupe`() = runTest {
        val groupe = Equipement(id = "g-1", clientId = "cl-1", nom = "Groupe Daikin")
        daoEquipements.enregistrer(groupe)
        daoEquipements.enregistrer(
            Equipement(id = "u-1", clientId = "cl-1", parentId = "g-1", nom = "Salon"),
        )
        daoEquipements.enregistrer(
            Equipement(id = "u-2", clientId = "cl-1", parentId = "g-1", nom = "Chambre"),
        )
        val viewModel = creerViewModel()
        val devis = DevisRepository(daoDevis).creer(
            client = null,
            equipement = groupe,
            aujourdhui = mai,
        )
        viewModel.onOuvrir(devis)
        advanceUntilIdle()

        viewModel.onAjouterPrestation(
            Prestation(
                designation = "Mise en service",
                categorie = CategoriePrestation.INSTALLATION,
                prixUnitaire = 90.0,
                unite = "u",
                parUnite = true,
            ),
        )
        viewModel.onAjouterPrestation(
            Prestation(
                designation = "Déplacement",
                categorie = CategoriePrestation.DEPANNAGE,
                prixUnitaire = 45.0,
                unite = "forfait",
            ),
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.unitesVisees.value)
        val lignes = daoDevis.contenuLignes.associateBy { it.designation }
        assertEquals(2.0, lignes.getValue("Mise en service").quantite, 0.001)
        assertEquals(
            "un forfait ne se multiplie pas : c'est un seul déplacement",
            1.0,
            lignes.getValue("Déplacement").quantite,
            0.001,
        )
    }

    /**
     * Un monosplit a bien une unité, confondue avec son groupe : la quantité vaut
     * alors un, et l'écran n'a pas deux cas à distinguer.
     */
    @Test
    fun `sans unite declaree, la quantite reste a un`() = runTest {
        val machine = Equipement(id = "m-1", clientId = "cl-1", nom = "Split bureau")
        daoEquipements.enregistrer(machine)
        val viewModel = creerViewModel()
        val devis = DevisRepository(daoDevis).creer(
            client = null,
            equipement = machine,
            aujourdhui = mai,
        )
        viewModel.onOuvrir(devis)
        advanceUntilIdle()

        viewModel.onAjouterPrestation(
            Prestation(
                designation = "Mise en service",
                categorie = CategoriePrestation.INSTALLATION,
                prixUnitaire = 90.0,
                parUnite = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.unitesVisees.value)
        assertEquals(1.0, daoDevis.contenuLignes.single().quantite, 0.001)
    }

    /** Un devis qui ne désigne aucune machine compte pour une unité. */
    @Test
    fun `un devis sans machine compte pour une unite`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouveau(null)
        advanceUntilIdle()

        assertEquals(1, viewModel.unitesVisees.value)
    }

    /**
     * Le régime de TVA vient des réglages **à la création**, puis le devis en garde
     * la copie : franchir le seuil de la franchise ne doit pas faire apparaître de
     * la TVA sur un devis déjà envoyé.
     */
    @Test
    fun `un devis cree en franchise en base le reste`() = runTest {
        daoParametres.enregistrer(Parametres(assujettiTva = false))
        val viewModel = creerViewModel()
        // Le régime est lu dans `reglages`, alimenté par un flux : sans cette
        // attente, `onNouveau` verrait encore la valeur de départ.
        advanceUntilIdle()

        viewModel.onNouveau(null)
        advanceUntilIdle()

        assertTrue(!daoDevis.contenu.single().assujettiTva)
    }

    /**
     * Créer une prestation depuis le devis l'inscrit au catalogue **et** l'ajoute au
     * document : c'est pour lui qu'on la saisit, et l'obliger à la rechercher
     * ensuite dans la feuille serait un geste pour rien.
     */
    @Test
    fun `une prestation creee depuis le devis rejoint le catalogue et le document`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouveau(null)
        advanceUntilIdle()

        viewModel.onCreerPrestation(
            Prestation(
                designation = "  Vanne 3 voies DN25  ",
                categorie = CategoriePrestation.PIECES,
                prixUnitaire = 112.5,
                unite = "u",
            ),
        )
        advanceUntilIdle()

        assertEquals("Vanne 3 voies DN25", daoPrestations.contenu.single().designation)
        val ligne = daoDevis.contenuLignes.single()
        assertEquals("l'intitulé nettoyé, pas celui qu'on a tapé", "Vanne 3 voies DN25", ligne.designation)
        assertEquals(112.5, ligne.prixUnitaire, 0.001)
    }

    /** Offrir une ligne, puis reprendre le geste, par le même appui. */
    @Test
    fun `l'appui simple offre la ligne puis la reprend`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouveau(null)
        advanceUntilIdle()
        viewModel.onAjouterLigne("Déplacement", 1.0, "forfait", 45.0)
        advanceUntilIdle()

        viewModel.onOffrirLigne(daoDevis.contenuLignes.single())
        advanceUntilIdle()
        assertTrue(daoDevis.contenuLignes.single().offerte)
        assertEquals(
            "le prix reste, pour s'afficher barré",
            45.0,
            daoDevis.contenuLignes.single().prixUnitaire,
            0.001,
        )

        viewModel.onOffrirLigne(daoDevis.contenuLignes.single())
        advanceUntilIdle()
        assertTrue(!daoDevis.contenuLignes.single().offerte)
    }

    /** Même raison que dans [InterventionsViewModelTest] pour `Dispatchers.Main`. */
    private fun TestScope.creerViewModel(): DevisViewModel {
        val ordonnanceur = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(ordonnanceur)
        val viewModel = DevisViewModel(
            DevisRepository(daoDevis),
            ClientRepository(daoClients),
            ParametresRepository(daoParametres, stockage),
            PrestationRepository(daoPrestations),
            EquipementRepository(daoEquipements, stockage),
            // Le PDF ne se dessine pas sans Android : le producteur rend `null`, ce
            // qui est le chemin d'échec. Ce que le document dit et où tombent ses
            // lignes est vérifié par [DocumentDevisTest] et [MiseEnPageDevisTest].
            ProducteurPdf { null },
        )
        listOf(
            viewModel.liste,
            viewModel.compteurs,
            viewModel.catalogue,
            viewModel.carnet,
            viewModel.reglages,
            viewModel.complet,
            viewModel.unitesVisees,
        ).forEach { flux -> backgroundScope.launch(ordonnanceur) { flux.collect { } } }
        return viewModel
    }
}
