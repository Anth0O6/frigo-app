package com.frigopro.app.ui

import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxEquipementDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxSuiviDao
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.SuiviRepository
import com.frigopro.app.data.FauxTypeInterventionDao
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.TypeIntervention
import com.frigopro.app.data.CategoriePrestation
import com.frigopro.app.data.FauxParametresDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.FauxPrestationDao
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.PrestationRepository
import com.frigopro.app.data.TypeInterventionRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ReglagesViewModelTest {

    private val daoInterventions = FauxInterventionDao()
    private val daoTypes = FauxTypeInterventionDao(daoInterventions)
    private val daoSuivi = FauxSuiviDao(daoInterventions)

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ajouter un type l'inscrit dans la liste`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onAjouterType()
        assertEquals(DialogueReglages.Creation, viewModel.dialogue.value)

        viewModel.onValiderIntitule("Mise en service")
        advanceUntilIdle()

        assertNull("le dialogue doit se refermer", viewModel.dialogue.value)
        assertEquals("Mise en service", daoTypes.contenu.single().libelle)
    }

    @Test
    fun `un intitule vide ne cree rien et laisse le dialogue ouvert`() = runTest {
        val viewModel = creerViewModel()
        viewModel.onAjouterType()

        viewModel.onValiderIntitule("   ")
        advanceUntilIdle()

        assertEquals(DialogueReglages.Creation, viewModel.dialogue.value)
        assertTrue(daoTypes.contenu.isEmpty())
    }

    /** Le cœur de la demande : la correction doit remonter jusqu'aux tournées passées. */
    @Test
    fun `renommer un type corrige les interventions qui l'utilisent`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Entretien anuel")
        daoTypes.enregistrer(type)
        daoInterventions.enregistrer(intervention("i1", "t1", "Entretien anuel"))

        viewModel.onRenommerType(type)
        viewModel.onValiderIntitule("Entretien annuel")
        advanceUntilIdle()

        assertEquals("Entretien annuel", daoTypes.contenu.single().libelle)
        assertEquals("Entretien annuel", daoInterventions.contenu.single().typeLibelle)
    }

    @Test
    fun `supprimer un type demande confirmation avant d'agir`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Dépannage")
        daoTypes.enregistrer(type)

        viewModel.onSupprimerType(type)
        advanceUntilIdle()

        assertEquals(DialogueReglages.Suppression(type), viewModel.dialogue.value)
        assertEquals("rien ne doit être supprimé avant confirmation", 1, daoTypes.contenu.size)

        viewModel.onConfirmerSuppression()
        advanceUntilIdle()

        assertTrue(daoTypes.contenu.isEmpty())
    }

    @Test
    fun `fermer le dialogue de suppression ne supprime rien`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Dépannage")
        daoTypes.enregistrer(type)

        viewModel.onSupprimerType(type)
        viewModel.onFermerDialogue()
        advanceUntilIdle()

        assertNull(viewModel.dialogue.value)
        assertEquals(1, daoTypes.contenu.size)
    }

    /** Supprimer un type ne doit rien retirer d'une tournée déjà saisie. */
    @Test
    fun `supprimer un type laisse son intitule sur les interventions`() = runTest {
        val viewModel = creerViewModel()
        val type = TypeIntervention(id = "t1", libelle = "Dépannage")
        daoTypes.enregistrer(type)
        daoInterventions.enregistrer(intervention("i1", "t1", "Dépannage"))

        viewModel.onSupprimerType(type)
        viewModel.onConfirmerSuppression()
        advanceUntilIdle()

        val restee = daoInterventions.contenu.single()
        assertEquals("Dépannage", restee.typeLibelle)
        assertNull(restee.typeId)
    }

    /** Un dialogue fermé ne doit pas pouvoir écrire : la validation n'a plus de cible. */
    @Test
    fun `valider sans dialogue ouvert n'ecrit rien`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onValiderIntitule("Fantôme")
        advanceUntilIdle()

        assertTrue(daoTypes.contenu.isEmpty())
    }

    private fun intervention(id: String, typeId: String?, typeLibelle: String) = Intervention(
        id = id,
        date = LocalDate.of(2026, 9, 10),
        heure = LocalTime.of(9, 0),
        client = "Client",
        ville = "Rouen",
        typeId = typeId,
        typeLibelle = typeLibelle,
    )

    private val daoPrestations = FauxPrestationDao()
    private val daoParametres = FauxParametresDao()

    /** Même raison que dans [InterventionsViewModelTest]. */
    private fun TestScope.creerViewModel(): ReglagesViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return ReglagesViewModel(
            TypeInterventionRepository(daoTypes),
            ParametresRepository(daoParametres, FauxRangementPhotos()),
            PrestationRepository(daoPrestations),
            SuiviRepository(daoSuivi, FauxRangementPhotos()),
            InterventionRepository(daoInterventions),
            ClientRepository(FauxClientDao(), FauxRangementPhotos()),
            EquipementRepository(FauxEquipementDao(daoInterventions), FauxRangementPhotos()),
            // Ce que le registre imprimé dit s'éprouve dans `DocumentRegistreTest` ;
            // l'écrire demanderait un `Canvas`.
            ProducteurPdf { null },
        )
    }

    /**
     * Le catalogue est livré sans prix : si les Réglages ne permettent pas de
     * les poser, il ne sert à rien. Ce test tient le seul chemin qui les pose.
     */
    @Test
    fun `renseigner un prix le garde, et l'unite avec`() = runTest {
        val prestation = Prestation(
            designation = "Recharge R-449A",
            categorie = CategoriePrestation.FLUIDE,
            rang = 9,
        )
        daoPrestations.enregistrer(prestation)
        val viewModel = creerViewModel()

        viewModel.onEnregistrerPrestation(prestation.copy(prixUnitaire = 38.0, unite = "kg"))
        advanceUntilIdle()

        val enregistree = daoPrestations.contenu.single()
        assertEquals(38.0, enregistree.prixUnitaire, 0.001)
        assertEquals("kg", enregistree.unite)
        assertTrue("elle cesse d'être « à renseigner »", enregistree.tarifee)
        assertEquals("son rang ne bouge pas", 9, enregistree.rang)
    }

    /**
     * Le catalogue livré couvre le métier, pas une entreprise : ce qu'on pose
     * trois fois par mois et qui n'y figure pas doit pouvoir s'y ajouter, sinon
     * il finit ressaisi en ligne libre à chaque devis.
     */
    @Test
    fun `une prestation saisie a la main entre au catalogue`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onEnregistrerPrestation(
            Prestation(
                designation = "  Vanne 3 voies DN25  ",
                categorie = CategoriePrestation.PIECES,
                prixUnitaire = 112.5,
                unite = "u",
                parUnite = true,
            ),
        )
        advanceUntilIdle()

        val creee = daoPrestations.contenu.single()
        assertEquals("Vanne 3 voies DN25", creee.designation)
        assertEquals(CategoriePrestation.PIECES, creee.categorie)
        assertEquals(112.5, creee.prixUnitaire, 0.001)
        assertTrue("elle se compte par unité intérieure", creee.parUnite)
        assertTrue("elle prend un rang, et se place en fin de liste", creee.rang > 0)
    }

    /**
     * Retirer une prestation ne touche pas aux devis : leurs lignes en ont
     * recopié l'intitulé et le prix. Le test ne vérifie ici que le catalogue, le
     * reste étant garanti par le modèle.
     */
    @Test
    fun `une prestation retiree quitte le catalogue`() = runTest {
        val prestation = Prestation(designation = "Forfait obsolète", categorie = CategoriePrestation.DEPANNAGE)
        daoPrestations.enregistrer(prestation)
        val viewModel = creerViewModel()

        viewModel.onSupprimerPrestation(prestation)
        advanceUntilIdle()

        assertTrue(daoPrestations.contenu.isEmpty())
    }

    /**
     * Un prix négatif n'a pas de sens : une remise se saisit en baissant le
     * prix, pas en inversant le signe, sinon un total devient faux sans qu'on
     * le voie. Le dépôt le ramène à zéro, donc à « à renseigner ».
     */
    @Test
    fun `un prix negatif est ramene a zero`() = runTest {
        val prestation = Prestation(designation = "Déplacement", categorie = CategoriePrestation.DEPANNAGE)
        daoPrestations.enregistrer(prestation)
        val viewModel = creerViewModel()

        viewModel.onEnregistrerPrestation(prestation.copy(prixUnitaire = -45.0, unite = "forfait"))
        advanceUntilIdle()

        assertEquals(0.0, daoPrestations.contenu.single().prixUnitaire, 0.001)
    }

    /**
     * Le coût horaire interne était en base depuis la migration 16, la
     * sauvegarde l'emportait et `RentabiliteIntervention` le lisait — mais aucun
     * écran ne le saisissait, si bien que la fiche d'une intervention invitait à
     * le renseigner « dans les Réglages », où il n'y avait rien à renseigner. La
     * marge d'une intervention était donc **impossible à obtenir**. Ce test tient
     * le chemin qui manquait.
     */
    @Test
    fun `le cout horaire interne se renseigne enfin`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onCoutHoraireInterne(28.5)
        advanceUntilIdle()

        assertEquals(28.5, reglages().coutHoraireInterne, 0.001)
    }

    /**
     * Zéro veut dire « non renseigné », et un coût négatif ne veut rien dire :
     * il donnerait une marge supérieure à la recette, donc un chiffre flatteur et
     * faux — exactement ce que le calcul refuse de produire quand il se tait.
     */
    @Test
    fun `un cout horaire negatif est ramene a zero`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onCoutHoraireInterne(-12.0)
        advanceUntilIdle()

        assertEquals(0.0, reglages().coutHoraireInterne, 0.001)
    }

    /**
     * Le taux facturé et le coût interne ne doivent jamais se confondre : entre
     * les deux il y a le salaire chargé, et c'est tout l'écart qu'on mesure. Les
     * intervertir donnerait une marge négative sur une intervention rentable.
     */
    @Test
    fun `le taux facture et le cout interne ne se marchent pas dessus`() = runTest {
        val viewModel = creerViewModel()

        viewModel.onTauxHoraire(65.0)
        viewModel.onCoutHoraireInterne(32.0)
        advanceUntilIdle()

        val parametres = reglages()
        assertEquals(65.0, parametres.tauxHoraire, 0.001)
        assertEquals(32.0, parametres.coutHoraireInterne, 0.001)
    }

    /** La ligne unique des réglages, telle que le dépôt vient de l'écrire. */
    private fun reglages() =
        requireNotNull(daoParametres.contenu) { "les réglages doivent avoir été écrits" }
}
