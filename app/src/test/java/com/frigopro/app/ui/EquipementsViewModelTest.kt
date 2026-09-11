package com.frigopro.app.ui

import com.frigopro.app.data.CategoriePhoto
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.FauxEquipementDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.FauxSuiviDao
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class EquipementsViewModelTest {

    private val daoInterventions = FauxInterventionDao()
    private val daoEquipements = FauxEquipementDao(daoInterventions)
    private val stockage = FauxRangementPhotos()
    private val daoSuivi = FauxSuiviDao(daoInterventions)

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    @Test
    fun `une machine creee rejoint le parc du client`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onAjouterMachine("cl-1")
        viewModel.onValiderNom("  Vitrine salle 2 ")
        advanceUntilIdle()

        val inscrite = daoEquipements.contenu.single()
        assertEquals("Vitrine salle 2", inscrite.nom)
        assertEquals("cl-1", inscrite.clientId)
        assertNull("la boîte se referme d'elle-même", viewModel.dialogue.value)
    }

    @Test
    fun `un nom vide ne cree rien et laisse la boite ouverte`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onAjouterMachine("cl-1")
        viewModel.onValiderNom("   ")
        advanceUntilIdle()

        assertTrue(daoEquipements.contenu.isEmpty())
        assertTrue(viewModel.dialogue.value is DialogueEquipement.Creation)
    }

    /**
     * La fiche ouverte est retenue par son identifiant : ce qu'elle affiche vient
     * donc de la base, et un renommage s'y voit sans que rien ne soit recopié.
     */
    @Test
    fun `un renommage se voit aussitot dans la fiche ouverte`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine")
        daoEquipements.enregistrer(machine)
        viewModel.onOuvrir(machine)
        advanceUntilIdle()

        viewModel.onRenommerMachine(machine)
        viewModel.onValiderNom("Vitrine salle 2")
        advanceUntilIdle()

        assertEquals("Vitrine salle 2", viewModel.ouverte.value?.nom)
    }

    @Test
    fun `un renommage suit les interventions passees`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine")
        daoEquipements.enregistrer(machine)
        daoInterventions.enregistrer(intervention(machine))
        advanceUntilIdle()

        viewModel.onRenommerMachine(machine)
        viewModel.onValiderNom("Vitrine salle 2")
        advanceUntilIdle()

        assertEquals("Vitrine salle 2", daoInterventions.contenu.single().equipementNom)
    }

    @Test
    fun `supprimer la machine referme sa fiche et laisse le nom aux interventions`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine salle 2")
        daoEquipements.enregistrer(machine)
        daoInterventions.enregistrer(intervention(machine))
        viewModel.onOuvrir(machine)
        advanceUntilIdle()

        viewModel.onSupprimerMachine(machine)
        viewModel.onConfirmerSuppression()
        advanceUntilIdle()

        assertNull("l'écran doit se refermer", viewModel.ouverte.value)
        assertTrue(daoEquipements.contenu.isEmpty())
        val ligne = daoInterventions.contenu.single()
        assertNull(ligne.equipementId)
        assertEquals("Vitrine salle 2", ligne.equipementNom)
    }

    /** Fermer la boîte sans confirmer ne doit évidemment rien supprimer. */
    @Test
    fun `renoncer a la suppression ne touche a rien`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine")
        daoEquipements.enregistrer(machine)
        advanceUntilIdle()

        viewModel.onSupprimerMachine(machine)
        viewModel.onFermerDialogue()
        viewModel.onConfirmerSuppression()
        advanceUntilIdle()

        assertEquals(1, daoEquipements.contenu.size)
    }

    @Test
    fun `la fiche ouverte expose ses photos et son historique`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine")
        val ailleurs = Equipement(id = "eq-2", clientId = "cl-1", nom = "Armoire")
        daoEquipements.enregistrer(machine)
        daoEquipements.enregistrer(ailleurs)
        daoInterventions.enregistrer(intervention(machine))
        daoInterventions.enregistrer(intervention(ailleurs).copy(id = "id-2"))
        viewModel.onOuvrir(machine)
        viewModel.onCapture(CategoriePhoto.PLAQUE, "plaque.jpg")
        advanceUntilIdle()

        assertEquals(listOf("plaque.jpg"), viewModel.photosOuvertes.value.map { it.fichier })
        assertEquals(listOf("id-1"), viewModel.historique.value.map { it.id })
    }

    /** L'historique se lit de haut en bas : la dernière visite d'abord. */
    @Test
    fun `l'historique va du plus recent au plus ancien`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine")
        daoEquipements.enregistrer(machine)
        daoInterventions.enregistrer(intervention(machine).copy(id = "vieille", date = LocalDate.of(2025, 1, 6)))
        daoInterventions.enregistrer(intervention(machine).copy(id = "recente", date = LocalDate.of(2026, 3, 9)))
        viewModel.onOuvrir(machine)
        advanceUntilIdle()

        assertEquals(listOf("recente", "vieille"), viewModel.historique.value.map { it.id })
    }

    @Test
    fun `rien n'est observe quand aucune fiche n'est ouverte`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine")
        daoEquipements.enregistrer(machine)
        daoInterventions.enregistrer(intervention(machine))
        advanceUntilIdle()

        assertEquals(emptyList<Intervention>(), viewModel.historique.value)
        assertNull(viewModel.ouverte.value)
    }

    @Test
    fun `supprimer la photo agrandie referme la visionneuse`() = runTest {
        val viewModel = creerViewModel()
        val machine = Equipement(id = "eq-1", clientId = "cl-1", nom = "Vitrine")
        daoEquipements.enregistrer(machine)
        viewModel.onOuvrir(machine)
        viewModel.onCapture(CategoriePhoto.PLAQUE, "plaque.jpg")
        advanceUntilIdle()
        val photo = viewModel.photosOuvertes.value.single()
        viewModel.onAgrandir(photo)

        viewModel.onSupprimerPhoto(photo)
        advanceUntilIdle()

        assertNull(viewModel.agrandie.value)
        assertTrue(daoEquipements.contenuPhotos.isEmpty())
        assertEquals("le fichier part avec la ligne", emptyList<String>(), stockage.fichiers)
    }

    /** Sans fiche ouverte, une photo n'aurait aucune machine à rejoindre. */
    @Test
    fun `aucune photo n'est enregistree sans fiche ouverte`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onCapture(CategoriePhoto.PLAQUE, "plaque.jpg")
        advanceUntilIdle()

        assertTrue(daoEquipements.contenuPhotos.isEmpty())
    }

    private fun intervention(machine: Equipement): Intervention = Intervention(
        id = "id-1",
        date = LocalDate.of(2026, 3, 9),
        heure = LocalTime.of(9, 0),
        client = "Boucherie Lemoine",
        ville = "Rouen",
        clientId = machine.clientId,
        equipementId = machine.id,
        equipementNom = machine.nom,
    )

    /**
     * Même raison que dans [InterventionsViewModelTest] pour `Dispatchers.Main`.
     *
     * Les flux sont collectés aussitôt : `stateIn(WhileSubscribed)` n'observe la
     * base que tant que quelqu'un écoute, et sans abonné leur valeur resterait
     * éternellement celle du départ.
     */
    private fun TestScope.creerViewModel(): EquipementsViewModel {
        val ordonnanceur = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(ordonnanceur)
        val viewModel = EquipementsViewModel(
            EquipementRepository(daoEquipements, stockage),
            InterventionRepository(daoInterventions),
            SuiviRepository(daoSuivi, stockage),
        )
        listOf(
            viewModel.parc,
            viewModel.ouverte,
            viewModel.photosOuvertes,
            viewModel.historique,
        ).forEach { flux -> backgroundScope.launch(ordonnanceur) { flux.collect { } } }
        return viewModel
    }
}
