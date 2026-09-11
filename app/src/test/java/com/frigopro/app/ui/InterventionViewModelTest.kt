package com.frigopro.app.ui

import com.frigopro.app.data.CHECKLIST_INITIALE
import com.frigopro.app.data.CategoriePhoto
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxEquipementDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxParametresDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.FauxSuiviDao
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.SensFluide
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.data.SuiviRepository
import com.frigopro.app.data.Symptome
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
import java.time.LocalDate
import java.time.LocalTime

/**
 * L'écran d'une intervention manipule ce qui se facture et ce qui se déclare.
 * Ce qui est vérifié ici est ce qui coûte cher à rater : un chronomètre qui
 * ne démarre pas, un relevé qui n'atteint pas la base, un numéro de
 * compte-rendu réattribué.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InterventionViewModelTest {

    private val daoInterventions = FauxInterventionDao()
    private val daoSuivi = FauxSuiviDao(daoInterventions)
    private val daoEquipements = FauxEquipementDao(daoInterventions)
    private val daoClients = FauxClientDao()
    private val daoParametres = FauxParametresDao()
    private val stockage = FauxRangementPhotos()

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    @Test
    fun `aucune intervention ouverte, aucun etat`() = runTest {
        val viewModel = creerViewModel()

        assertNull(viewModel.etat.value)
    }

    @Test
    fun `ouvrir une intervention expose son etat`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = creerViewModel()

        viewModel.onOuvrir(INTERVENTION)
        advanceUntilIdle()

        assertEquals("Boucherie Martel", viewModel.etat.value?.intervention?.client)
        assertEquals(
            "on arrive sur la fiche : le créneau et l'adresse avant le manomètre",
            OngletIntervention.FICHE,
            viewModel.onglet.value,
        )
        assertEquals(
            "la checklist est posée à l'ouverture, pas à la création",
            CHECKLIST_INITIALE.size,
            viewModel.etat.value?.checklist?.size,
        )
        assertEquals("et rien n'est coché d'avance", 0, viewModel.etat.value?.pointsFaits)
    }

    /**
     * La checklist ne se repose pas à chaque ouverture : ce qui est coché doit
     * le rester, sinon la liste ne vaut rien.
     */
    @Test
    fun `rouvrir une intervention ne repose pas sa checklist`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = creerViewModel()
        viewModel.onOuvrir(INTERVENTION)
        advanceUntilIdle()
        val premier = viewModel.etat.value?.checklist?.first()!!
        viewModel.onBasculerPoint(premier)
        advanceUntilIdle()

        viewModel.onFermer()
        viewModel.onOuvrir(INTERVENTION)
        advanceUntilIdle()

        assertEquals(CHECKLIST_INITIALE.size, viewModel.etat.value?.checklist?.size)
        assertEquals("ce qui était coché le reste", 1, viewModel.etat.value?.pointsFaits)
    }

    @Test
    fun `une checklist a moitie cochee n'est pas finie`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = creerViewModel()
        viewModel.onOuvrir(INTERVENTION)
        advanceUntilIdle()

        viewModel.onBasculerPoint(viewModel.etat.value?.checklist?.first()!!)
        advanceUntilIdle()

        assertFalse(viewModel.etat.value?.checklistFinie == true)

        viewModel.etat.value?.checklist?.filterNot { it.fait }?.forEach { point ->
            viewModel.onBasculerPoint(point)
            advanceUntilIdle()
        }

        assertTrue("tout coché, donc finie", viewModel.etat.value?.checklistFinie == true)
    }

    // — Chronomètre ————————————————————————————————————————————————————————

    /**
     * Le technicien qui lance son chrono est arrivé : le lui faire dire une
     * seconde fois en changeant le statut à la main serait du travail pour rien.
     */
    @Test
    fun `demarrer le chrono fait passer l'intervention en cours`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()

        viewModel.onBasculerChrono()
        advanceUntilIdle()

        val ligne = daoInterventions.contenu.single()
        assertTrue(ligne.chrono.enMarche)
        assertNotNull(ligne.chrono.arriveeLe)
        assertEquals(StatutIntervention.EN_COURS, ligne.statut)
    }

    @Test
    fun `basculer une seconde fois met en pause`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()

        viewModel.onBasculerChrono()
        advanceUntilIdle()
        viewModel.onBasculerChrono()
        advanceUntilIdle()

        assertFalse(daoInterventions.contenu.single().chrono.enMarche)
    }

    /** Une intervention déjà terminée ne doit pas revenir « en cours » toute seule. */
    @Test
    fun `reprendre le chrono d'une intervention terminee ne change pas son statut`() = runTest {
        daoInterventions.enregistrer(INTERVENTION.copy(statut = StatutIntervention.TERMINEE))
        val viewModel = ouvrir()

        viewModel.onBasculerChrono()
        advanceUntilIdle()

        assertEquals(StatutIntervention.TERMINEE, daoInterventions.contenu.single().statut)
    }

    // — Clôture ————————————————————————————————————————————————————————————

    @Test
    fun `cloturer arrete le chrono, termine et numerote`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()
        viewModel.onBasculerChrono()
        advanceUntilIdle()

        viewModel.onCloturer()
        advanceUntilIdle()

        val ligne = daoInterventions.contenu.single()
        assertFalse(ligne.chrono.enMarche)
        assertEquals(StatutIntervention.TERMINEE, ligne.statut)
        assertTrue("un numéro doit être attribué", ligne.numero.startsWith("INT-"))
        assertEquals("la clôture ouvre le compte-rendu", OngletIntervention.RAPPORT, viewModel.onglet.value)
    }

    /**
     * Un document déjà remis au client ne doit pas changer de référence, même
     * si l'intervention est rouverte puis reclôturée.
     */
    @Test
    fun `une seconde cloture ne reattribue pas de numero`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()
        viewModel.onCloturer()
        advanceUntilIdle()
        val premier = daoInterventions.contenu.single().numero

        viewModel.onCloturer()
        advanceUntilIdle()

        assertEquals(premier, daoInterventions.contenu.single().numero)
    }

    // — Relevés ————————————————————————————————————————————————————————————

    /**
     * Le technicien n'a jamais à « créer » un relevé : il remplit des cases, et
     * la première valeur saisie le fait naître.
     */
    @Test
    fun `la premiere valeur saisie cree le releve`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()

        viewModel.onSurchauffe(11.2)
        advanceUntilIdle()

        val releve = daoSuivi.contenuReleves.single()
        assertEquals(11.2, releve.surchauffeK!!, 0.001)
        assertEquals("il doit désigner la machine", "eq-1", releve.equipementId)
    }

    @Test
    fun `les valeurs suivantes completent le meme releve`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()

        viewModel.onSurchauffe(11.2)
        advanceUntilIdle()
        viewModel.onSousRefroidissement(4.0)
        advanceUntilIdle()

        val releve = daoSuivi.contenuReleves.single()
        assertEquals(11.2, releve.surchauffeK!!, 0.001)
        assertEquals(4.0, releve.sousRefroidissementK!!, 0.001)
    }

    /** Le relevé de la maquette doit produire le diagnostic de la maquette. */
    @Test
    fun `le diagnostic decoule du releve`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()

        viewModel.onSurchauffe(11.2)
        advanceUntilIdle()
        viewModel.onSousRefroidissement(4.0)
        advanceUntilIdle()

        assertEquals(
            Symptome.SOUS_ALIMENTATION_EVAPORATEUR,
            viewModel.etat.value?.diagnostic?.symptome,
        )
    }

    // — Fluide —————————————————————————————————————————————————————————————

    @Test
    fun `les mouvements se totalisent par sens`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()

        viewModel.onMouvement(SensFluide.AJOUT, 0.5, "R452A")
        advanceUntilIdle()
        viewModel.onMouvement(SensFluide.AJOUT, 0.3, "R452A")
        advanceUntilIdle()
        viewModel.onMouvement(SensFluide.RECUPERATION, 0.2, "R452A")
        advanceUntilIdle()

        assertEquals(0.8, viewModel.etat.value!!.ajoute, 0.001)
        assertEquals(0.2, viewModel.etat.value!!.recupere, 0.001)
    }

    // — Photos —————————————————————————————————————————————————————————————

    @Test
    fun `les photos se rangent par categorie`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()

        viewModel.onCapture(CategoriePhoto.AVANT, "avant.jpg")
        advanceUntilIdle()
        viewModel.onCapture(CategoriePhoto.APRES, "apres.jpg")
        advanceUntilIdle()

        assertEquals(listOf("avant.jpg"), viewModel.etat.value!!.photosAvant.map { it.fichier })
        assertEquals(listOf("apres.jpg"), viewModel.etat.value!!.photosApres.map { it.fichier })
    }

    /** Sans intervention ouverte, une photo n'aurait rien à rejoindre. */
    @Test
    fun `aucune photo n'est enregistree sans intervention ouverte`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onCapture(CategoriePhoto.AVANT, "avant.jpg")
        advanceUntilIdle()

        assertTrue(daoSuivi.contenuPhotos.isEmpty())
    }

    // — Compte-rendu ———————————————————————————————————————————————————————

    @Test
    fun `les travaux realises sont les notes de l'intervention`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()

        viewModel.onTravaux("Reprise du raccord et complément de charge.")
        advanceUntilIdle()

        assertEquals(
            "Reprise du raccord et complément de charge.",
            daoInterventions.contenu.single().notes,
        )
    }

    @Test
    fun `la signature est horodatee, et s'efface avec son horodatage`() = runTest {
        daoInterventions.enregistrer(INTERVENTION)
        val viewModel = ouvrir()

        viewModel.onSignature("signature.png")
        advanceUntilIdle()
        assertNotNull(daoInterventions.contenu.single().signeeLe)

        viewModel.onEffacerSignature()
        advanceUntilIdle()
        assertNull(daoInterventions.contenu.single().signatureFichier)
        assertNull(daoInterventions.contenu.single().signeeLe)
    }

    private fun TestScope.ouvrir(): InterventionViewModel {
        val viewModel = creerViewModel()
        viewModel.onOuvrir(INTERVENTION)
        advanceUntilIdle()
        return viewModel
    }

    /**
     * Même raison que dans [InterventionsViewModelTest] pour `Dispatchers.Main`,
     * et même raison de collecter les flux aussitôt : `stateIn(WhileSubscribed)`
     * n'observe la base que tant que quelqu'un écoute.
     */
    private fun TestScope.creerViewModel(): InterventionViewModel {
        val ordonnanceur = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(ordonnanceur)
        val viewModel = InterventionViewModel(
            InterventionRepository(daoInterventions),
            SuiviRepository(daoSuivi, stockage),
            EquipementRepository(daoEquipements, stockage),
            ClientRepository(daoClients),
            ParametresRepository(daoParametres),
        )
        backgroundScope.launch(ordonnanceur) { viewModel.etat.collect { } }
        return viewModel
    }

    private companion object {

        val INTERVENTION = Intervention(
            id = "id-1",
            date = LocalDate.of(2026, 5, 14),
            heure = LocalTime.of(8, 0),
            client = "Boucherie Martel",
            ville = "Vitry-sur-Seine",
            clientId = "cl-1",
            equipementId = "eq-1",
            equipementNom = "Chambre froide positive",
        )
    }
}
