package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxDevisDao
import com.frigopro.app.data.FauxFactureDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureRepository
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.StatutDevis
import com.frigopro.app.data.StatutFacture
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
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ClientsViewModelTest {

    private val daoInterventions = FauxInterventionDao()
    private val daoFactures = FauxFactureDao()
    private val daoDevis = FauxDevisDao(daoFactures)
    private val dao = FauxClientDao(
        interventions = daoInterventions,
        devis = daoDevis,
        factures = daoFactures,
    )

    private val aujourdhui = LocalDate.of(2026, 10, 27)

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

    /**
     * La recherche et les parcs dépliés vivent dans le ViewModel, et non dans
     * l'écran du carnet qui quitte la composition quand une machine s'ouvre :
     * contrôler cinq machines d'un site ne doit pas demander de retaper cinq fois
     * la recherche ni de redéplier cinq fois la carte.
     */
    @Test
    fun `la recherche et les parcs deplies survivent a l'ecran`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onRecherche("carrefour")
        viewModel.onBasculerParc("cl-1")
        viewModel.onBasculerParc("cl-2")
        viewModel.onBasculerParc("cl-2")

        assertEquals("carrefour", viewModel.recherche.value)
        assertEquals("un second appui replie", setOf("cl-1"), viewModel.deplies.value)
    }

    /**
     * « Voir tout le parc » répond à une recherche : une nouvelle saisie l'oublie,
     * sans quoi la suivante semblerait ne plus rien filtrer. Le parc, lui, reste
     * déplié — le replier cacherait ce qu'on vient de demander à voir.
     */
    @Test
    fun `voir tout le parc vaut pour la recherche en cours seulement`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onRecherche("vitrine")

        viewModel.onVoirToutLeParc("cl-1")
        assertEquals(setOf("cl-1"), viewModel.parcsComplets.value)
        assertEquals(setOf("cl-1"), viewModel.deplies.value)

        viewModel.onRecherche("vitrine")
        assertEquals("la même saisie ne l'oublie pas", setOf("cl-1"), viewModel.parcsComplets.value)

        viewModel.onRecherche("vitrine 12")
        assertTrue(viewModel.parcsComplets.value.isEmpty())
        assertEquals(setOf("cl-1"), viewModel.deplies.value)
    }

    // — La page d'un client ————————————————————————————————————————————

    @Test
    fun `la page s'ouvre par identifiant et suit un renommage`() = runTest {
        val viewModel = creerViewModel()
        backgroundScope.launch { viewModel.ouvert.collect {} }
        dao.enregistrer(Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen"))

        viewModel.onOuvrirClient("cl-1")
        dao.enregistrer(Client(id = "cl-1", nom = "Boucherie Lemoine & fils", ville = "Rouen"))

        assertEquals("Boucherie Lemoine & fils", viewModel.ouvert.value?.nom)
    }

    /** Supprimé d'ailleurs — par la boîte d'édition, ou une restauration —, la page se referme seule. */
    @Test
    fun `la page se referme d'elle-meme quand le client est supprime`() = runTest {
        val viewModel = creerViewModel()
        backgroundScope.launch { viewModel.ouvert.collect {} }
        backgroundScope.launch { viewModel.dossier.collect {} }
        val client = Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen")
        dao.enregistrer(client)
        viewModel.onOuvrirClient("cl-1")
        assertNotNull(viewModel.dossier.value)

        ClientRepository(dao, FauxRangementPhotos()).supprimer(client)

        assertNull(viewModel.ouvert.value)
        assertNull(viewModel.dossier.value)
    }

    @Test
    fun `un site revient a son enseigne et l'enseigne au carnet`() = runTest {
        val viewModel = creerViewModel()
        backgroundScope.launch { viewModel.ouvert.collect {} }
        dao.enregistrer(Client(id = "ens", nom = "Carrefour", ville = "Lyon"))
        dao.enregistrer(Client(id = "site", nom = "Carrefour Vaise", ville = "Lyon", parentId = "ens"))

        viewModel.onOuvrirClient("site")
        viewModel.onRetourClient()
        assertEquals("ens", viewModel.ouvert.value?.id)

        viewModel.onRetourClient()
        assertNull(viewModel.ouvert.value)
    }

    @Test
    fun `le dossier ne retient que les interventions de ce client, a venir puis passees`() = runTest {
        val viewModel = creerViewModel()
        backgroundScope.launch { viewModel.dossier.collect {} }
        dao.enregistrer(Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen"))
        dao.enregistrer(Client(id = "site", nom = "Lemoine Halles", ville = "Rouen", parentId = "cl-1"))
        dao.enregistrer(Client(id = "autre", nom = "Fromagerie Hardy", ville = "Caudebec"))
        daoInterventions.enregistrer(intervention("demain", "cl-1", aujourdhui.plusDays(1)))
        daoInterventions.enregistrer(intervention("ce-matin", "cl-1", aujourdhui))
        daoInterventions.enregistrer(intervention("mars", "cl-1", aujourdhui.minusMonths(7)))
        daoInterventions.enregistrer(
            intervention("hier", "cl-1", aujourdhui.minusDays(1)),
        )
        daoInterventions.enregistrer(
            intervention("close", "cl-1", aujourdhui).copy(statut = StatutIntervention.TERMINEE),
        )
        // Sous-traitance : on est allé chez un autre, mais c'est à lui qu'on facture.
        daoInterventions.enregistrer(intervention("pour-lui", "autre", aujourdhui.minusDays(3)).copy(clientFactureId = "cl-1"))
        daoInterventions.enregistrer(intervention("voisin", "autre", aujourdhui.plusDays(2)))
        daoInterventions.enregistrer(intervention("au-site", "site", aujourdhui.plusDays(2)))

        viewModel.onOuvrirClient("cl-1")

        val dossier = viewModel.dossier.value!!
        assertEquals(listOf("ce-matin", "demain"), dossier.aVenir.map { it.id })
        assertEquals(listOf("close", "hier", "pour-lui", "mars"), dossier.passees.map { it.id })
        assertEquals(listOf("site"), dossier.sites.map { it.id })
    }

    @Test
    fun `le dossier ne retient que les factures dues et les devis en cours de ce client`() = runTest {
        val viewModel = creerViewModel()
        backgroundScope.launch { viewModel.dossier.collect {} }
        dao.enregistrer(Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen"))
        listOf(
            Facture(id = "due", numero = "FAC-2026-0001", clientId = "cl-1", statut = StatutFacture.EMISE),
            Facture(id = "payee", numero = "FAC-2026-0002", clientId = "cl-1", statut = StatutFacture.PAYEE),
            Facture(id = "brouillon", clientId = "cl-1"),
            Facture(id = "voisine", numero = "FAC-2026-0003", clientId = "autre", statut = StatutFacture.EMISE),
            Facture(
                id = "facture-du-devis",
                numero = "FAC-2026-0004",
                clientId = "cl-1",
                devisId = "facture",
                statut = StatutFacture.PAYEE,
            ),
            Facture(
                id = "annulee",
                numero = "FAC-2026-0005",
                clientId = "cl-1",
                devisId = "refacturable",
                statut = StatutFacture.ANNULEE,
            ),
        ).forEach { daoFactures.enregistrer(it) }
        listOf(
            Devis(id = "envoye", clientId = "cl-1", statut = StatutDevis.ENVOYE),
            Devis(id = "accepte", clientId = "cl-1", statut = StatutDevis.ACCEPTE),
            Devis(id = "refuse", clientId = "cl-1", statut = StatutDevis.REFUSE),
            Devis(id = "facture", clientId = "cl-1", statut = StatutDevis.ACCEPTE),
            Devis(id = "refacturable", clientId = "cl-1", statut = StatutDevis.ACCEPTE),
            Devis(id = "voisin", clientId = "autre", statut = StatutDevis.ENVOYE),
        ).forEach { daoDevis.enregistrer(it) }

        viewModel.onOuvrirClient("cl-1")

        val dossier = viewModel.dossier.value!!
        assertEquals(listOf("due"), dossier.dues.map { it.facture.id })
        assertEquals(
            setOf("envoye", "accepte", "refacturable"),
            dossier.devisEnCours.map { it.devis.id }.toSet(),
        )
    }

    @Test
    fun `aucune page ouverte, aucun dossier`() = runTest {
        val viewModel = creerViewModel()
        backgroundScope.launch { viewModel.dossier.collect {} }
        dao.enregistrer(Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen"))

        viewModel.onOuvrirClient("cl-1")
        viewModel.onFermerClient()

        assertNull(viewModel.dossier.value)
    }

    private fun intervention(id: String, clientId: String, date: LocalDate) = Intervention(
        id = id,
        date = date,
        heure = LocalTime.of(9, 0),
        client = clientId,
        ville = "Rouen",
        clientId = clientId,
    )

    /** Même raison que dans [InterventionsViewModelTest]. */
    private fun TestScope.creerViewModel(): ClientsViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return ClientsViewModel(
            repository = ClientRepository(dao, FauxRangementPhotos()),
            interventions = InterventionRepository(daoInterventions),
            factures = FactureRepository(daoFactures),
            devis = DevisRepository(daoDevis),
            horloge = { aujourdhui },
        )
    }
}
