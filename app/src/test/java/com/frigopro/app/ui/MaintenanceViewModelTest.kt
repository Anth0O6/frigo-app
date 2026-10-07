package com.frigopro.app.ui

import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxEquipementDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxMaintenanceDao
import com.frigopro.app.data.FauxParametresDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.FauxTechnicienDao
import com.frigopro.app.data.MaintenanceRepository
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.Periodicite
import com.frigopro.app.data.StatutEcheance
import com.frigopro.app.data.TechnicienRepository
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

/**
 * Le plan de maintenance, vu de l'écran.
 *
 * Le jour est **figé** dans ces tests : une échéance se compte en jours, et un
 * test qui lirait l'horloge réelle échouerait un matin sur trente sans qu'on
 * sache pourquoi — ce qui est la façon la plus sûre de le faire désactiver.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModelTest {

    private val daoInterventions = FauxInterventionDao()
    private val daoMaintenance = FauxMaintenanceDao()
    private val daoEquipements = FauxEquipementDao(daoInterventions)
    private val daoClients = FauxClientDao()
    private val daoTechniciens = FauxTechnicienDao(daoInterventions)
    private val daoParametres = FauxParametresDao()

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    /** Créer une gamme l'ouvre : on vient d'en créer une pour la garnir. */
    @Test
    fun `creer une gamme l'ouvre aussitot`() = runTest {
        val viewModel = creerViewModel()
        collecter(viewModel)

        viewModel.onCreerGamme("Visite mensuelle groupe froid", Periodicite.MENSUEL)
        advanceUntilIdle()

        val gamme = daoMaintenance.gammes.single()
        assertEquals("Visite mensuelle groupe froid", gamme.libelle)
        assertEquals(Periodicite.MENSUEL, gamme.periodicite)
        assertEquals(gamme.id, viewModel.gammeOuverte.value)
    }

    /** Une gamme sans intitulé n'est pas une gamme : elle ne s'écrit pas. */
    @Test
    fun `une gamme sans intitule ne s'ecrit pas`() = runTest {
        val viewModel = creerViewModel()
        collecter(viewModel)

        viewModel.onCreerGamme("   ", Periodicite.MENSUEL)
        advanceUntilIdle()

        assertTrue(daoMaintenance.gammes.isEmpty())
        assertNull(viewModel.gammeOuverte.value)
    }

    /**
     * **Le test qui justifie de ne stocker aucune occurrence.** Changer la cadence
     * d'une gamme ne régénère rien : la prochaine échéance de chaque machine se
     * recalcule au pas nouveau, à partir de sa dernière visite. Une table
     * d'occurrences aurait demandé de les refaire, et de décider du sort de celles
     * déjà cochées.
     */
    @Test
    fun `changer la cadence recalcule l'echeance sans rien regenerer`() = runTest {
        val viewModel = creerViewModel()
        collecter(viewModel)
        daoEquipements.enregistrer(MACHINE)
        viewModel.onCreerGamme("Visite", Periodicite.MENSUEL)
        advanceUntilIdle()
        val gamme = daoMaintenance.gammes.single()
        viewModel.onAffecter(MACHINE.id, gamme.id)
        advanceUntilIdle()
        viewModel.onConsigner(MACHINE, gamme, faitLe = LocalDate.of(2026, 6, 10))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 7, 10), viewModel.echeances.value.single().echeance)

        viewModel.onPeriodicite(gamme, Periodicite.TRIMESTRIEL)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 10), viewModel.echeances.value.single().echeance)
        // Rien n'a été engendré : une seule visite au journal, celle qu'on a faite.
        assertEquals(1, daoMaintenance.releves.size)
    }

    /**
     * Le geste qui rend la GMAO tenable sur un site de plusieurs centaines
     * d'équipements : les rattacher un à un demanderait une soirée, et la soirée
     * ne se prendrait pas.
     */
    @Test
    fun `affecter au parc rattache toutes les machines d'un coup`() = runTest {
        val viewModel = creerViewModel()
        collecter(viewModel)
        val parc = (1..12).map { rang ->
            MACHINE.copy(id = "eq-$rang", nom = "Meuble $rang")
        }
        parc.forEach { daoEquipements.enregistrer(it) }
        viewModel.onCreerGamme("Ronde du matin", Periodicite.JOURNALIER)
        advanceUntilIdle()
        val gamme = daoMaintenance.gammes.single()

        viewModel.onAffecterAuParc(parc, gamme.id)
        advanceUntilIdle()

        assertEquals(12, daoMaintenance.affectations.size)
        assertEquals(12, viewModel.echeances.value.size)
    }

    /**
     * Rattacher deux fois la même gamme est le geste le plus banal qui soit en
     * balayant une liste de cent machines. Il ne doit ni échouer — l'index unique
     * le refuserait — ni remettre le départ du plan à zéro.
     */
    @Test
    fun `rattacher deux fois ne double rien et ne remet pas le depart a zero`() = runTest {
        val viewModel = creerViewModel(jour = LocalDate.of(2026, 6, 1))
        collecter(viewModel)
        daoEquipements.enregistrer(MACHINE)
        viewModel.onCreerGamme("Visite", Periodicite.MENSUEL)
        advanceUntilIdle()
        val gamme = daoMaintenance.gammes.single()

        viewModel.onAffecter(MACHINE.id, gamme.id)
        advanceUntilIdle()
        val depart = daoMaintenance.affectations.single().depuisLe

        viewModel.onAffecter(MACHINE.id, gamme.id)
        advanceUntilIdle()

        assertEquals(1, daoMaintenance.affectations.size)
        assertEquals(depart, daoMaintenance.affectations.single().depuisLe)
    }

    /**
     * Consigner une visite déplace l'échéance d'une période, et la machine cesse
     * d'appeler une action. C'est la boucle entière de la GMAO en un test.
     */
    @Test
    fun `consigner une visite repousse l'echeance et eteint l'alerte`() = runTest {
        val jour = LocalDate.of(2026, 6, 1)
        val viewModel = creerViewModel(jour = jour)
        collecter(viewModel)
        daoEquipements.enregistrer(MACHINE)
        viewModel.onCreerGamme("Visite", Periodicite.MENSUEL)
        advanceUntilIdle()
        val gamme = daoMaintenance.gammes.single()
        // Un départ reculé : la visite était due le 1ᵉʳ mai, elle est en retard.
        viewModel.onAffecter(MACHINE.id, gamme.id)
        advanceUntilIdle()
        daoMaintenance.enregistrerAffectation(
            daoMaintenance.affectations.single().copy(depuisLe = LocalDate.of(2026, 4, 1)),
        )
        advanceUntilIdle()

        assertEquals(StatutEcheance.EN_RETARD, viewModel.echeances.value.single().statut(jour))

        viewModel.onConsigner(MACHINE, gamme, faitLe = jour)
        advanceUntilIdle()

        val echeance = viewModel.echeances.value.single()
        assertEquals(LocalDate.of(2026, 7, 1), echeance.echeance)
        assertEquals(StatutEcheance.A_VENIR, echeance.statut(jour))
    }

    /**
     * **La propriété la plus importante du modèle.** Supprimer une gamme emporte
     * ses points et ses affectations, et **garde ses visites** : une ligne du
     * journal est la preuve qu'une maintenance contractuelle a eu lieu, et
     * l'effacer reviendrait à perdre ce qu'un contrôle vient chercher.
     */
    @Test
    fun `supprimer une gamme garde les visites deja faites`() = runTest {
        val viewModel = creerViewModel()
        collecter(viewModel)
        daoEquipements.enregistrer(MACHINE)
        viewModel.onCreerGamme("Visite", Periodicite.MENSUEL)
        advanceUntilIdle()
        val gamme = daoMaintenance.gammes.single()
        viewModel.onAjouterPoint(gamme.id, "Nettoyer le condenseur")
        viewModel.onAffecter(MACHINE.id, gamme.id)
        viewModel.onConsigner(MACHINE, gamme, faitLe = LocalDate.of(2026, 6, 10))
        advanceUntilIdle()

        viewModel.onSupprimerGamme(gamme)
        advanceUntilIdle()

        assertTrue("la gamme est partie", daoMaintenance.gammes.isEmpty())
        assertTrue("ses points sont partis", daoMaintenance.points.isEmpty())
        assertTrue("ses affectations sont parties", daoMaintenance.affectations.isEmpty())
        val visite = daoMaintenance.releves.single()
        assertNull("le lien est coupé", visite.gammeId)
        assertEquals("l'intitulé reste lisible", "Visite", visite.gammeLibelle)
        assertEquals("Centrale négatif", visite.equipementNom)
        assertEquals(LocalDate.of(2026, 6, 10), visite.faitLe)
    }

    /**
     * Une échéance jamais visitée le dit, parce que c'est une information : une
     * machine qu'on vient de rattacher n'a pas été négligée.
     */
    @Test
    fun `une machine jamais visitee le dit`() = runTest {
        val viewModel = creerViewModel()
        collecter(viewModel)
        daoEquipements.enregistrer(MACHINE)
        viewModel.onCreerGamme("Visite", Periodicite.ANNUEL)
        advanceUntilIdle()
        viewModel.onAffecter(MACHINE.id, daoMaintenance.gammes.single().id)
        advanceUntilIdle()

        assertTrue(viewModel.echeances.value.single().jamaisVisitee)
    }

    /**
     * Les flux passent par `stateIn(WhileSubscribed)` : ils n'observent la base
     * que tant que quelqu'un écoute, et `.value` sur un flux que personne ne
     * collecte resterait à sa valeur initiale pour toujours.
     */
    private fun TestScope.collecter(viewModel: MaintenanceViewModel) {
        listOf(
            viewModel.gammes,
            viewModel.points,
            viewModel.affectations,
            viewModel.releves,
        ).forEach { flux ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flux.collect { } }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.echeances.collect { }
        }
    }

    private fun TestScope.creerViewModel(
        jour: LocalDate = LocalDate.of(2026, 6, 15),
    ): MaintenanceViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val stockage = FauxRangementPhotos()
        return MaintenanceViewModel(
            MaintenanceRepository(daoMaintenance),
            EquipementRepository(daoEquipements, stockage),
            ClientRepository(daoClients, stockage),
            TechnicienRepository(daoTechniciens),
            ParametresRepository(daoParametres, stockage),
        ) { jour }
    }

    private companion object {

        val MACHINE = Equipement(
            id = "eq-1",
            clientId = "cl-1",
            nom = "Centrale négatif",
            fluide = "R-449A",
            chargeKg = 42.0,
        )
    }
}
