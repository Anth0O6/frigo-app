package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxEquipementDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.FauxTypeInterventionDao
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.data.TypeIntervention
import com.frigopro.app.data.TypeInterventionRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class InterventionsViewModelTest {

    private val dao = FauxInterventionDao()
    private val daoClients = FauxClientDao()
    private val daoTypes = FauxTypeInterventionDao(dao)
    private val daoEquipements = FauxEquipementDao(dao)

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
            viewModel.lignes.collect { }
        }

        enregistrer(viewModel, client = "Le jour même", ville = "Rouen")
        enregistrer(viewModel, client = "Le lendemain", ville = "Elbeuf", date = mardi)
        advanceUntilIdle()

        assertEquals(mardi, viewModel.jour.value)
        assertEquals(listOf("Le lendemain"), viewModel.lignes.value.map { it.intervention.client })

        viewModel.onJourPrecedent()
        advanceUntilIdle()

        assertEquals(listOf("Le jour même"), viewModel.lignes.value.map { it.intervention.client })
    }

    /**
     * Ce que la tournée doit savoir pour agir : le numéro et l'adresse ne sont
     * pas sur l'intervention, mais sur la fiche du client qu'elle désigne.
     */
    @Test
    fun `une ligne de tournee porte la fiche du client rattache`() = runTest {
        val viewModel = creerViewModel()
        val client = Client(
            id = "cl-1",
            nom = "Boucherie Lemoine",
            ville = "Rouen",
            adresse = "12 rue des Carmes",
            telephone = "02 35 00 00 00",
        )
        daoClients.enregistrer(client)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.lignes.collect { }
        }

        viewModel.onNouvelleIntervention()
        viewModel.onFormulaireChange(viewModel.formulaire.value!!.copy(ville = "Rouen"))
        viewModel.onClientChoisi(client)
        viewModel.onValiderFormulaire()
        advanceUntilIdle()

        val ligne = viewModel.lignes.value.single()
        assertEquals(client, ligne.client)
        assertTrue(ligne.appelable)
        assertTrue(ligne.localisable)
    }

    /**
     * Une intervention saisie avant l'arrivée du carnet porte un `clientId`
     * nul. L'écran doit tenir sans fiche plutôt que de refuser de l'afficher,
     * et ne proposer ni appel ni itinéraire.
     */
    @Test
    fun `une intervention sans client rattache donne une ligne sans fiche`() = runTest {
        val viewModel = creerViewModel()
        dao.enregistrer(
            Intervention(
                id = "ancienne",
                date = viewModel.jour.value,
                heure = LocalTime.of(9, 0),
                client = "Client de passage",
                ville = "Rouen",
                typeLibelle = "Fuite de fluide",
                clientId = null,
            ),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.lignes.collect { }
        }
        advanceUntilIdle()

        val ligne = viewModel.lignes.value.single()
        assertNull(ligne.client)
        assertFalse(ligne.appelable)
        assertFalse(ligne.localisable)
    }

    @Test
    fun `choisir un type le pose sur le formulaire, le rechoisir l'enleve`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Entretien annuel")
        daoTypes.enregistrer(type)
        viewModel.onNouvelleIntervention()

        viewModel.onTypeChoisi(type)

        assertEquals("t1", viewModel.formulaire.value!!.typeId)
        assertEquals("Entretien annuel", viewModel.formulaire.value!!.typeLibelle)

        viewModel.onTypeChoisi(null)

        assertNull(viewModel.formulaire.value!!.typeId)
        assertEquals("", viewModel.formulaire.value!!.typeLibelle)
    }

    /** Rencontrer un type qui manque ne doit pas obliger à quitter sa saisie. */
    @Test
    fun `un nouveau type rejoint la liste et est choisi aussitot`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouvelleIntervention()

        viewModel.onNouveauType("  Mise en service ")
        advanceUntilIdle()

        val cree = daoTypes.contenu.single()
        assertEquals("Mise en service", cree.libelle)
        assertEquals(cree.id, viewModel.formulaire.value!!.typeId)
        assertEquals("Mise en service", viewModel.formulaire.value!!.typeLibelle)
    }

    @Test
    fun `un intitule vide ne cree pas de type`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouvelleIntervention()

        viewModel.onNouveauType("   ")
        advanceUntilIdle()

        assertTrue(daoTypes.contenu.isEmpty())
        assertNull(viewModel.formulaire.value!!.typeId)
    }

    @Test
    fun `l'intitule du type est enregistre avec l'intervention`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Dépannage")
        daoTypes.enregistrer(type)
        viewModel.onNouvelleIntervention()
        viewModel.onFormulaireChange(
            viewModel.formulaire.value!!.copy(client = "Client", ville = "Rouen"),
        )
        viewModel.onTypeChoisi(type)

        viewModel.onValiderFormulaire()
        advanceUntilIdle()

        val enregistree = dao.contenu.single()
        assertEquals("t1", enregistree.typeId)
        assertEquals("Dépannage", enregistree.typeLibelle)
    }

    /**
     * La machine appartient au client : la saisie doit lui proposer le parc de
     * celui qu'elle désigne, et celui-là seulement. Le filtrage se fait à
     * l'affichage, mais encore faut-il que le parc arrive jusque-là.
     */
    @Test
    fun `le parc observe contient les machines de tous les clients`() = runTest {
        val viewModel = creerViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.machines.collect { }
        }
        daoEquipements.enregistrer(Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine"))
        daoEquipements.enregistrer(Equipement(id = "eq-2", clientId = "cl-2", nom = "Armoire"))
        advanceUntilIdle()

        assertEquals(setOf("eq-1", "eq-2"), viewModel.machines.value.map { it.id }.toSet())
    }

    @Test
    fun `la machine choisie est enregistree avec l'intervention`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine salle 2")
        daoEquipements.enregistrer(machine)
        viewModel.onNouvelleIntervention()
        viewModel.onFormulaireChange(
            viewModel.formulaire.value!!.copy(client = "Boucherie", ville = "Rouen"),
        )

        viewModel.onMachineChoisie(machine)
        viewModel.onValiderFormulaire()
        advanceUntilIdle()

        val enregistree = dao.contenu.single()
        assertEquals("eq-1", enregistree.equipementId)
        assertEquals("Vitrine salle 2", enregistree.equipementNom)
    }

    /** Le type est facultatif, la machine aussi : se tromper doit pouvoir s'annuler. */
    @Test
    fun `retirer la machine vide le lien et le nom`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine salle 2")
        viewModel.onNouvelleIntervention()
        viewModel.onMachineChoisie(machine)

        viewModel.onMachineChoisie(null)

        assertNull(viewModel.formulaire.value!!.equipementId)
        assertEquals("", viewModel.formulaire.value!!.equipementNom)
    }

    /**
     * Une machine appartient à un client : la garder en changeant de client la
     * rattacherait au mauvais parc.
     */
    @Test
    fun `changer de client oublie la machine`() = runTest {
        val viewModel = creerViewModel()
        val premier = Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen")
        val second = Client(id = "cl-2", nom = "Primeur Vasseur", ville = "Elbeuf")
        viewModel.onNouvelleIntervention()
        viewModel.onClientChoisi(premier)
        viewModel.onMachineChoisie(Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine"))

        viewModel.onClientChoisi(second)

        assertNull(viewModel.formulaire.value!!.equipementId)
        assertEquals("", viewModel.formulaire.value!!.equipementNom)
    }

    /** Rechoisir le même client ne doit pas faire perdre la machine déjà désignée. */
    @Test
    fun `rechoisir le meme client garde la machine`() = runTest {
        val viewModel = creerViewModel()
        val client = Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen")
        viewModel.onNouvelleIntervention()
        viewModel.onClientChoisi(client)
        viewModel.onMachineChoisie(Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine"))

        viewModel.onClientChoisi(client)

        assertEquals("eq-1", viewModel.formulaire.value!!.equipementId)
    }

    @Test
    fun `une nouvelle machine rejoint le parc du client et est choisie aussitot`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouvelleIntervention()
        viewModel.onClientChoisi(Client(id = "cl-1", nom = "Boucherie Lemoine", ville = "Rouen"))

        viewModel.onNouvelleMachine("  Vitrine salle 2 ")
        advanceUntilIdle()

        val inscrite = daoEquipements.contenu.single()
        assertEquals("le nom est nettoyé par le dépôt", "Vitrine salle 2", inscrite.nom)
        assertEquals("cl-1", inscrite.clientId)
        assertEquals(inscrite.id, viewModel.formulaire.value!!.equipementId)
        assertEquals("Vitrine salle 2", viewModel.formulaire.value!!.equipementNom)
    }

    /** Sans client du carnet, il n'y a pas de parc où inscrire quoi que ce soit. */
    @Test
    fun `aucune machine n'est inscrite sans client du carnet`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouvelleIntervention()
        viewModel.onFormulaireChange(
            viewModel.formulaire.value!!.copy(client = "Client de passage", ville = "Rouen"),
        )

        viewModel.onNouvelleMachine("Vitrine")
        advanceUntilIdle()

        assertTrue(daoEquipements.contenu.isEmpty())
        assertNull(viewModel.formulaire.value!!.equipementId)
    }

    @Test
    fun `un nom vide ne cree pas de machine`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onNouvelleIntervention()
        viewModel.onClientChoisi(Client(id = "cl-1", nom = "Boucherie", ville = "Rouen"))

        viewModel.onNouvelleMachine("   ")
        advanceUntilIdle()

        assertTrue(daoEquipements.contenu.isEmpty())
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
        return InterventionsViewModel(
            InterventionRepository(dao),
            ClientRepository(daoClients),
            TypeInterventionRepository(daoTypes),
            EquipementRepository(daoEquipements, FauxRangementPhotos()),
        )
    }
}
