package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.FactureRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxFactureDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxParametresDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.FauxSuiviDao
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.SuiviRepository
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
import java.time.LocalTime

/**
 * L'onglet Facturation, et ce qu'une facture emporte des réglages.
 *
 * Le test existe pour un défaut précis, et il aurait dû exister avant lui : les
 * réglages et le carnet étaient tenus ici en `stateIn(WhileSubscribed)`, et
 * **aucun écran ne les collectait**. Un flux ainsi démarré ne coule que tant que
 * quelqu'un l'écoute, si bien que `.value` rendait éternellement la valeur
 * initiale — des réglages vierges et un carnet vide. La facture partait donc
 * sans logo, sans en-tête d'entreprise et sans l'adresse du client, et elle
 * **figeait à l'émission les valeurs par défaut** : une facture émise ne bouge
 * plus, c'est ce qui tient la numérotation, donc l'erreur était définitive.
 *
 * Les deux cas ci-dessous sont écrits pour échouer sur cette version-là.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FacturesViewModelTest {

    private val daoFactures = FauxFactureDao()
    private val daoClients = FauxClientDao()
    private val daoParametres = FauxParametresDao()
    private val daoInterventions = FauxInterventionDao()
    private val daoSuivi = FauxSuiviDao(daoInterventions)
    private val stockage = FauxRangementPhotos()

    /** Le document tel qu'il est parti à l'impression, capté au vol. */
    private var imprime: DocumentImprime? = null

    private val mars = LocalDate.of(2026, 3, 12)

    private val entreprise = Parametres(
        entreprise = "FrigoPro",
        entrepriseAdresse = "12 rue des Lilas, Lyon",
        entrepriseSiret = "80012345600017",
        logoFichier = "logo-1.png",
        tauxHoraire = 60.0,
        // Trois valeurs volontairement différentes des défauts de [Parametres] :
        // c'est à cela qu'on voit si ce sont les vrais réglages qui ont servi.
        tauxTva = 10.0,
        delaiPaiementJours = 45,
        tauxPenalitesRetard = 12.0,
    )

    private val client = Client(
        nom = "Boucherie Morel",
        ville = "Lyon",
        adresse = "8 place du Marché",
    )

    private val intervention = Intervention(
        date = mars,
        heure = LocalTime.of(8, 0),
        client = client.nom,
        ville = client.ville,
        clientId = client.id,
        numero = "INT-2603-004",
        equipementNom = "Chambre froide",
    )

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    /**
     * Le logo et l'en-tête de l'entreprise partent avec la facture.
     *
     * C'est la demande telle qu'elle a été faite — « sur la facture je veux voir
     * apparaître le logo comme pour le devis » —, et ce n'était pas un oubli de
     * maquette : le document se construisait bien, mais sur des réglages vides.
     * L'adresse du client est vérifiée dans la foulée, parce qu'elle manquait
     * pour exactement la même raison et qu'une facture sans adresse de
     * destinataire n'est pas une facture.
     */
    @Test
    fun `la facture imprimee porte le logo, l'en-tete et l'adresse du client`() = runTest {
        val viewModel = creerViewModel()
        daoClients.enregistrer(client)
        ParametresRepository(daoParametres, stockage).enregistrer(entreprise)
        advanceUntilIdle()

        viewModel.onFacturerIntervention(intervention)
        advanceUntilIdle()
        // Émise d'abord : une facture sans numéro ne s'exporte pas — un document
        // non numéroté parti chez un client est une facture irrégulière, et le
        // ViewModel le revérifie après l'écran.
        viewModel.onEmettre()
        advanceUntilIdle()
        viewModel.onExporterPdf()
        advanceUntilIdle()

        val document = requireNotNull(imprime) { "le document doit avoir été produit" }
        assertEquals("logo-1.png", document.logoFichier)
        assertTrue(document.emetteur.any { it.contains("FrigoPro") })
        assertTrue(document.emetteur.any { it.contains("80012345600017") })
        assertTrue(document.destinataire.contains("Boucherie Morel"))
        assertTrue(document.destinataire.any { it.contains("place du Marché") })
    }

    /**
     * L'émission fige les **vrais** réglages, et c'est le cas grave.
     *
     * Une facture émise ne se modifie plus : si l'échéance est calculée sur un
     * délai de trente jours parce que les réglages lus étaient vierges, le
     * document part chez le client avec une date fausse que rien ne rattrape. Le
     * taux de TVA et le taux de pénalités sont recopiés au même moment, et se
     * seraient trompés de la même façon.
     */
    @Test
    fun `l'emission fige le delai reellement regle, pas le defaut`() = runTest {
        val viewModel = creerViewModel()
        ParametresRepository(daoParametres, stockage).enregistrer(entreprise)
        advanceUntilIdle()

        viewModel.onFacturerIntervention(intervention)
        advanceUntilIdle()
        viewModel.onEmettre()
        advanceUntilIdle()

        val emise = daoFactures.contenu.single()
        val emiseLe = requireNotNull(emise.emiseLe) { "la facture doit être émise" }
        assertEquals(
            "quarante-cinq jours, ceux qui sont réglés — et non les trente du défaut",
            emiseLe.plusDays(45),
            emise.echeanceLe,
        )
        // Recopiés au même instant, et pour la même raison : ce sont les
        // mentions du document qui est parti.
        assertEquals(10.0, emise.tauxTva, 0.001)
        assertEquals(12.0, emise.tauxPenalites, 0.001)
    }

    /** Même raison que dans [DevisViewModelTest] pour `Dispatchers.Main`. */
    private fun TestScope.creerViewModel(): FacturesViewModel {
        val ordonnanceur = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(ordonnanceur)
        val viewModel = FacturesViewModel(
            FactureRepository(daoFactures),
            SuiviRepository(daoSuivi, stockage),
            ClientRepository(daoClients, stockage),
            ParametresRepository(daoParametres, stockage),
            // Le PDF ne se dessine pas sans Android : le producteur retient le
            // document et rend `null`. C'est ce que le document **dit** qui est
            // en cause ici, et non son tracé — celui-là est à [DocumentFactureTest].
            ProducteurPdf { document ->
                imprime = document
                null
            },
        )
        listOf(viewModel.liste, viewModel.complete).forEach { flux ->
            backgroundScope.launch(ordonnanceur) { flux.collect { } }
        }
        return viewModel
    }
}
