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

    /**
     * Un bi-split, c'est un groupe et deux unités. L'unité hérite du client de son
     * groupe : lui en demander un autre serait une question sans réponse possible.
     */
    @Test
    fun `une unite rejoint son groupe, et le client avec`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onAjouterMachine("cl-1")
        viewModel.onValiderNom("Groupe Daikin")
        advanceUntilIdle()
        val groupe = daoEquipements.contenu.single()

        viewModel.onAjouterUnite(groupe)
        viewModel.onValiderNom("  Salon ")
        advanceUntilIdle()

        val unite = daoEquipements.contenu.single { it.id != groupe.id }
        assertEquals("Salon", unite.nom)
        assertEquals(groupe.id, unite.parentId)
        assertEquals("cl-1", unite.clientId)
        assertTrue(unite.estUnite)
    }

    /**
     * Le carnet montre des **groupes** : un bi-split est un appareil chez le
     * client, pas trois lignes. Le compte d'unités est dérivé, si bien qu'en
     * ajouter une suffit à le changer partout.
     */
    @Test
    fun `le parc par groupe ne compte pas les unites comme des machines`() = runTest {
        val viewModel = creerViewModel()
        val groupe = Equipement(id = "g-1", clientId = "cl-1", nom = "Groupe Daikin")
        daoEquipements.enregistrer(groupe)
        daoEquipements.enregistrer(
            Equipement(id = "u-1", clientId = "cl-1", parentId = "g-1", nom = "Salon"),
        )
        daoEquipements.enregistrer(
            Equipement(id = "u-2", clientId = "cl-1", parentId = "g-1", nom = "Chambre"),
        )
        daoEquipements.enregistrer(Equipement(id = "m-1", clientId = "cl-1", nom = "Vitrine"))
        advanceUntilIdle()

        val groupes = viewModel.groupes.value

        assertEquals("deux appareils, pas quatre", 2, groupes.size)
        val biSplit = groupes.single { it.groupe.id == "g-1" }
        assertEquals(2, biSplit.unites.size)
        assertTrue(biSplit.multiSplit)
        assertEquals("2 unités intérieures", biSplit.resume)
        val monosplit = groupes.single { it.groupe.id == "m-1" }
        assertEquals("un monosplit en a bien une", 1, monosplit.nombreUnites)
        assertTrue(!monosplit.multiSplit)
    }

    /**
     * La hiérarchie n'a qu'un seul niveau : une unité ouverte ne montre pas
     * d'unités, elle montre le groupe dont elle dépend. Le modèle l'autoriserait —
     * `parentId` est libre — et c'est ici qu'on s'interdit de s'en servir.
     */
    @Test
    fun `la fiche d'une unite annonce son groupe et pas d'unites`() = runTest {
        val viewModel = creerViewModel()
        val groupe = Equipement(id = "g-1", clientId = "cl-1", nom = "Groupe Daikin")
        val unite = Equipement(id = "u-1", clientId = "cl-1", parentId = "g-1", nom = "Salon")
        daoEquipements.enregistrer(groupe)
        daoEquipements.enregistrer(unite)
        advanceUntilIdle()

        viewModel.onOuvrir(groupe)
        advanceUntilIdle()
        assertEquals(listOf("Salon"), viewModel.unitesOuvertes.value.map { it.nom })
        assertNull("un groupe ne dépend de rien", viewModel.groupeOuvert.value)

        viewModel.onOuvrir(unite)
        advanceUntilIdle()
        assertTrue("une unité ne porte pas d'unité", viewModel.unitesOuvertes.value.isEmpty())
        assertEquals("Groupe Daikin", viewModel.groupeOuvert.value?.nom)
    }

    /**
     * Supprimer un groupe emporte ses unités, leurs photos comprises : une fiche
     * d'unité sans groupe ne se rattacherait à rien, et ses fichiers resteraient
     * sur le téléphone sans qu'aucun écran ne puisse plus les montrer.
     */
    @Test
    fun `supprimer un groupe emporte ses unites`() = runTest {
        val viewModel = creerViewModel()
        val groupe = Equipement(id = "g-1", clientId = "cl-1", nom = "Groupe Daikin")
        daoEquipements.enregistrer(groupe)
        daoEquipements.enregistrer(
            Equipement(id = "u-1", clientId = "cl-1", parentId = "g-1", nom = "Salon"),
        )
        advanceUntilIdle()

        viewModel.onSupprimerMachine(groupe)
        viewModel.onConfirmerSuppression()
        advanceUntilIdle()

        assertTrue(daoEquipements.contenu.isEmpty())
    }

    /**
     * **La copie est ouverte aussitôt**, comme une gamme qu'on vient de créer.
     *
     * On duplique pour renseigner ce qui *diffère* — le numéro de série, qui
     * n'est précisément pas recopié —, et refermer sur le carnet aurait obligé à
     * retrouver la copie dans une liste de trois cents machines. L'écran reste
     * sur la machine qu'on vient de faire naître.
     */
    @Test
    fun `dupliquer une machine ouvre la copie et non l'original`() = runTest {
        val viewModel = creerViewModel()
        val source = Equipement(
            id = "eq-1",
            clientId = "cl-1",
            nom = "Vitrine 1",
            marque = "Costan",
            numeroSerie = "4821007",
        )
        daoEquipements.enregistrer(source)
        viewModel.onOuvrir(source)
        advanceUntilIdle()

        viewModel.onDupliquerMachine(source)
        viewModel.onValiderNom("Vitrine 2")
        advanceUntilIdle()

        assertNull("la boîte est refermée", viewModel.dialogue.value)
        val ouverte = viewModel.ouverte.value
        assertEquals("Vitrine 2", ouverte?.nom)
        assertEquals("Costan", ouverte?.marque)
        assertEquals("le numéro de série reste à renseigner", "", ouverte?.numeroSerie)
        assertEquals("l'original est toujours là", 2, daoEquipements.contenu.size)
    }

    /**
     * L'appui long ouvre la sélection **avec la machine touchée cochée** : c'est
     * elle que le doigt désignait. Le bouton visible, lui, l'ouvre vide.
     */
    @Test
    fun `l'appui long ouvre la selection sur la machine touchee`() = runTest {
        val viewModel = creerViewModel()
        val vitrine = Equipement(id = "v1", clientId = "cl-1", nom = "Vitrine 1")

        viewModel.onSelectionnerDepuis(vitrine)

        assertEquals(SelectionParc("cl-1", setOf("v1")), viewModel.selection.value)

        viewModel.onSelectionner("cl-1")

        assertEquals(SelectionParc("cl-1"), viewModel.selection.value)
    }

    /**
     * La sélection ne sort pas du parc d'un client : une zone est un endroit d'un
     * site, et ranger d'un geste des machines de deux sites rangerait celles qu'on
     * ne voit plus. « Tout cocher » écarte donc les machines d'ailleurs, et une
     * case touchée chez un autre client ne coche rien.
     */
    @Test
    fun `la selection reste dans le parc d'un seul client`() = runTest {
        val viewModel = creerViewModel()
        val ici = (1..3).map { Equipement(id = "v$it", clientId = "cl-1", nom = "Vitrine $it") }
        val ailleurs = Equipement(id = "x1", clientId = "cl-2", nom = "Vitrine 1")

        viewModel.onSelectionner("cl-1")
        viewModel.onBasculerSelection(ailleurs)
        assertEquals(emptySet<String>(), viewModel.selection.value?.ids)

        viewModel.onSelectionnerTout(ici + ailleurs)
        assertEquals(setOf("v1", "v2", "v3"), viewModel.selection.value?.ids)

        viewModel.onBasculerSelection(ici[1])
        assertEquals(setOf("v1", "v3"), viewModel.selection.value?.ids)

        viewModel.onDeselectionner(listOf(ici[0]))
        assertEquals(setOf("v3"), viewModel.selection.value?.ids)
    }

    /**
     * Donner une zone range les seules machines cochées, nettoie la saisie, et
     * referme le mode : la sélection a servi, et la garder ouverte inviterait à
     * lui appliquer par mégarde le geste suivant.
     */
    @Test
    fun `donner une zone range la selection et referme le mode`() = runTest {
        val viewModel = creerViewModel()
        (1..3).forEach { rang ->
            daoEquipements.enregistrer(Equipement(id = "v$rang", clientId = "cl-1", nom = "Vitrine $rang"))
        }
        viewModel.onSelectionner("cl-1")
        viewModel.onBasculerSelection(daoEquipements.contenu.single { it.id == "v1" })
        viewModel.onBasculerSelection(daoEquipements.contenu.single { it.id == "v2" })

        viewModel.onDonnerZone("  Linéaire frais ")
        advanceUntilIdle()

        val zones = daoEquipements.contenu.associate { it.id to it.zone }
        assertEquals("Linéaire frais", zones["v1"])
        assertEquals("Linéaire frais", zones["v2"])
        assertEquals("", zones["v3"])
        assertNull("le mode sélection est refermé", viewModel.selection.value)
    }

    /** Rien de coché, rien à ranger : aucune écriture, et le mode reste ouvert. */
    @Test
    fun `une selection vide ne range rien et reste ouverte`() = runTest {
        val viewModel = creerViewModel()
        daoEquipements.enregistrer(Equipement(id = "v1", clientId = "cl-1", nom = "Vitrine 1", zone = "Toiture"))
        viewModel.onSelectionner("cl-1")

        viewModel.onDonnerZone("")
        advanceUntilIdle()

        assertEquals("Toiture", daoEquipements.contenu.single().zone)
        assertEquals(SelectionParc("cl-1"), viewModel.selection.value)
    }

    /**
     * Une unité se referme **sur son groupe** : on l'ouvre depuis la fiche du
     * groupe pour photographier « Salon » puis « Chambre », et revenir au carnet
     * entre les deux obligeait à retrouver le groupe dans tout le parc. Le groupe,
     * lui, se referme sur le carnet.
     */
    @Test
    fun `le retour d'une unite rouvre son groupe, celui du groupe le carnet`() = runTest {
        val viewModel = creerViewModel()
        val groupe = Equipement(id = "g-1", clientId = "cl-1", nom = "Groupe Daikin")
        val unite = Equipement(id = "u-1", clientId = "cl-1", parentId = "g-1", nom = "Salon")
        daoEquipements.enregistrer(groupe)
        daoEquipements.enregistrer(unite)
        viewModel.onOuvrir(groupe)
        viewModel.onOuvrir(unite)
        advanceUntilIdle()

        viewModel.onFermer()
        advanceUntilIdle()
        assertEquals("g-1", viewModel.ouverte.value?.id)

        viewModel.onFermer()
        advanceUntilIdle()
        assertNull("le groupe revient au carnet", viewModel.ouverte.value)
    }

    /**
     * Le pas de retour vient de l'unité et non du chemin : une unité ouverte sans
     * être passé par son groupe se referme quand même sur lui.
     */
    @Test
    fun `une unite ouverte directement se referme aussi sur son groupe`() = runTest {
        val viewModel = creerViewModel()
        daoEquipements.enregistrer(Equipement(id = "g-1", clientId = "cl-1", nom = "Groupe Daikin"))
        val unite = Equipement(id = "u-1", clientId = "cl-1", parentId = "g-1", nom = "Salon")
        daoEquipements.enregistrer(unite)
        viewModel.onOuvrir(unite)
        advanceUntilIdle()

        viewModel.onFermer()
        advanceUntilIdle()

        assertEquals("g-1", viewModel.ouverte.value?.id)
    }

    /** Une unité supprimée referme sur son groupe, comme un retour. */
    @Test
    fun `supprimer l'unite ouverte ramene a son groupe`() = runTest {
        val viewModel = creerViewModel()
        val groupe = Equipement(id = "g-1", clientId = "cl-1", nom = "Groupe Daikin")
        val unite = Equipement(id = "u-1", clientId = "cl-1", parentId = "g-1", nom = "Salon")
        daoEquipements.enregistrer(groupe)
        daoEquipements.enregistrer(unite)
        viewModel.onOuvrir(groupe)
        viewModel.onOuvrir(unite)
        advanceUntilIdle()

        viewModel.onSupprimerMachine(unite)
        viewModel.onConfirmerSuppression()
        advanceUntilIdle()

        assertEquals("g-1", viewModel.ouverte.value?.id)
        assertEquals(listOf("g-1"), daoEquipements.contenu.map { it.id })
    }

    /**
     * **Une machine créée s'ouvre**, comme une copie : on la crée pour lui donner
     * sa plaque et ses photos, et la laisser dans un parc de trois cents lignes
     * obligeait à la chercher.
     */
    @Test
    fun `une machine creee depuis le carnet s'ouvre aussitot`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onAjouterMachine("cl-1")
        viewModel.onValiderNom("Vitrine salle 2")
        advanceUntilIdle()

        assertEquals("Vitrine salle 2", viewModel.ouverte.value?.nom)

        viewModel.onFermer()
        advanceUntilIdle()
        assertNull("une machine créée se referme sur le carnet", viewModel.ouverte.value)
    }

    /**
     * Une unité, elle, laisse ouverte la fiche du groupe d'où on l'a créée : elle
     * y paraît dans la liste des unités, et un bi-split se pose en deux saisies
     * coup sur coup.
     */
    @Test
    fun `une unite creee laisse ouverte la fiche du groupe`() = runTest {
        val viewModel = creerViewModel()
        val groupe = Equipement(id = "g-1", clientId = "cl-1", nom = "Groupe Daikin")
        daoEquipements.enregistrer(groupe)
        viewModel.onOuvrir(groupe)
        advanceUntilIdle()

        viewModel.onAjouterUnite(groupe)
        viewModel.onValiderNom("Salon")
        advanceUntilIdle()

        assertEquals("g-1", viewModel.ouverte.value?.id)
        assertEquals(listOf("Salon"), viewModel.unitesOuvertes.value.map { it.nom })
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
            viewModel.groupes,
            viewModel.ouverte,
            viewModel.unitesOuvertes,
            viewModel.groupeOuvert,
            viewModel.photosOuvertes,
            viewModel.historique,
        ).forEach { flux -> backgroundScope.launch(ordonnanceur) { flux.collect { } } }
        return viewModel
    }
}
