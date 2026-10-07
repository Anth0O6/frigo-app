package com.frigopro.app.ui

import com.frigopro.app.data.Client
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
    private val daoEquipements = FauxEquipementDao(daoInterventions, daoMaintenance)
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
     * Retirer une gamme **d'une machine** — le geste de sa fiche — n'est pas
     * supprimer la gamme : elle continue de courir sur le reste du parc, et les
     * visites déjà faites sur celle-ci restent au journal, lien intact.
     *
     * C'est la distinction que la fiche doit tenir : une armoire sortie du
     * contrat d'entretien cesse d'être réclamée, elle ne cesse pas d'avoir été
     * entretenue.
     */
    @Test
    fun `retirer une gamme d'une machine laisse ses visites et l'autre machine`() = runTest {
        val viewModel = creerViewModel()
        collecter(viewModel)
        val autre = MACHINE.copy(id = "eq-2", nom = "Chambre froide")
        daoEquipements.enregistrer(MACHINE)
        daoEquipements.enregistrer(autre)
        viewModel.onCreerGamme("Visite", Periodicite.MENSUEL)
        advanceUntilIdle()
        val gamme = daoMaintenance.gammes.single()
        viewModel.onAffecter(MACHINE.id, gamme.id)
        viewModel.onAffecter(autre.id, gamme.id)
        viewModel.onConsigner(MACHINE, gamme, faitLe = LocalDate.of(2026, 6, 10))
        advanceUntilIdle()

        viewModel.onRetirer(MACHINE.id, gamme.id)
        advanceUntilIdle()

        assertEquals("la gamme reste", 1, daoMaintenance.gammes.size)
        assertEquals(
            "seule l'autre machine la suit encore",
            listOf(autre.id),
            daoMaintenance.affectations.map { it.equipementId },
        )
        val visite = daoMaintenance.releves.single()
        assertEquals("la visite reste au journal", MACHINE.id, visite.equipementId)
        assertEquals("et garde son lien de gamme", gamme.id, visite.gammeId)
        assertEquals(
            "plus rien n'est réclamé à la machine retirée",
            listOf(autre.id),
            viewModel.echeances.value.map { it.equipement.id },
        )
    }

    /**
     * Une visite cochée sur la mauvaise machine se retire, et l'échéance revient
     * où elle était.
     *
     * C'est ce qui distingue le journal des visites d'une facture émise : il
     * n'ouvre aucune séquence numérotée, et une case touchée par mégarde
     * fausserait l'échéance **et** le taux de réalisation. Vivre avec serait pire
     * que de pouvoir l'effacer.
     */
    @Test
    fun `retirer une visite consignee par erreur ramene l'echeance`() = runTest {
        // La visite est datée de cinq jours avant la saisie — on consigne sa
        // tournée le dimanche —, sans quoi elle tomberait sur le départ du plan
        // et le test ne prouverait rien de la date.
        val jour = LocalDate.of(2026, 6, 25)
        val viewModel = creerViewModel(jour = jour)
        collecter(viewModel)
        daoEquipements.enregistrer(MACHINE)
        viewModel.onCreerGamme("Visite", Periodicite.MENSUEL)
        advanceUntilIdle()
        val gamme = daoMaintenance.gammes.single()
        viewModel.onAffecter(MACHINE.id, gamme.id)
        advanceUntilIdle()
        val attendue = viewModel.echeances.value.single().echeance
        assertEquals(LocalDate.of(2026, 7, 25), attendue)

        viewModel.onConsigner(MACHINE, gamme, faitLe = LocalDate.of(2026, 6, 20))
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 7, 20), viewModel.echeances.value.single().echeance)

        viewModel.onRetirerVisite(daoMaintenance.releves.single())
        advanceUntilIdle()

        assertTrue("le journal est vide", daoMaintenance.releves.isEmpty())
        val echeance = viewModel.echeances.value.single()
        assertEquals("l'échéance est revenue où elle était", attendue, echeance.echeance)
        assertTrue("la machine redevient jamais visitée", echeance.jamaisVisitee)
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
     * Le taux de réalisation d'une gamme, sur douze mois glissants.
     *
     * Il est exposé en **flux** et non par une fonction qui lirait `.value` :
     * trois flux doivent être collectés pour que le compte soit juste, et
     * `.value` sur un flux que personne ne collecte reste à sa valeur initiale
     * pour toujours. L'écran des Réglages n'observait pas le journal des visites,
     * et le taux y aurait affiché zéro visite faite sur toutes les gammes — le
     * genre de chiffre faux qu'on croit. Le flux porte la dépendance dans son
     * type, et l'écran ne peut plus l'oublier.
     */
    @Test
    fun `le taux de realisation compte les visites des douze derniers mois`() = runTest {
        val jour = LocalDate.of(2026, 6, 15)
        val viewModel = creerViewModel(jour = jour)
        collecter(viewModel)
        daoEquipements.enregistrer(MACHINE)
        viewModel.onCreerGamme("Visite mensuelle", Periodicite.MENSUEL)
        advanceUntilIdle()
        val gamme = daoMaintenance.gammes.single()
        viewModel.onAffecter(MACHINE.id, gamme.id)
        advanceUntilIdle()
        daoMaintenance.enregistrerAffectation(
            daoMaintenance.affectations.single().copy(depuisLe = LocalDate.of(2026, 1, 1)),
        )
        // Cinq attendues — février à juin — et trois faites.
        listOf(2, 3, 4).forEach { mois ->
            viewModel.onConsigner(MACHINE, gamme, faitLe = LocalDate.of(2026, mois, 10))
        }
        // Et une hors fenêtre, qui ne doit pas compter.
        viewModel.onConsigner(MACHINE, gamme, faitLe = LocalDate.of(2025, 2, 10))
        advanceUntilIdle()

        val taux = viewModel.realisations.value[gamme.id]!!
        assertEquals(5, taux.attendues)
        assertEquals(3, taux.faites)
    }

    /**
     * **L'attestation s'arrête à aujourd'hui**, et c'est le point le plus facile
     * à rater de tout le document.
     *
     * Un contrat mensuel sur l'année 2026 demande douze visites ; au 15 juin il
     * n'en a pu recevoir que cinq. Compter jusqu'au 31 décembre aurait annoncé
     * « 5 sur 12 » — 42 % — sur un contrat parfaitement honoré, et ce chiffre-là
     * part chez le client. C'est la même erreur que de facturer une quantité non
     * arrondie : elle ne se voit qu'une fois le document envoyé.
     */
    @Test
    fun `l'attestation d'une annee en cours s'arrete a aujourd'hui`() = runTest {
        val jour = LocalDate.of(2026, 6, 15)
        val viewModel = creerViewModel(jour = jour)
        collecter(viewModel)
        val client = Client(id = "cl-1", nom = "Boucherie Morel", ville = "Lyon")
        daoClients.enregistrer(client)
        daoEquipements.enregistrer(MACHINE)
        viewModel.onCreerGamme("Visite mensuelle", Periodicite.MENSUEL)
        advanceUntilIdle()
        val gamme = daoMaintenance.gammes.single()
        viewModel.onAffecter(MACHINE.id, gamme.id)
        advanceUntilIdle()
        daoMaintenance.enregistrerAffectation(
            daoMaintenance.affectations.single().copy(depuisLe = LocalDate.of(2026, 1, 1)),
        )
        // Cinq visites, une par mois de février à juin : le contrat est tenu.
        (2..6).forEach { mois ->
            viewModel.onConsigner(MACHINE, gamme, faitLe = LocalDate.of(2026, mois, 10))
        }
        advanceUntilIdle()

        viewModel.onExporterAttestation(client, 2026)
        advanceUntilIdle()

        val document = imprime!!
        assertEquals("ATTESTATION D'ENTRETIEN", document.titre)
        assertTrue("la période s'arrête au jour courant", "au 15/06/2026" in document.dates)
        val bilan = document.blocs.first().lignes.map { "${it.intitule} ${it.valeur}" }
        assertTrue(
            "cinq attendues et non douze : $bilan",
            bilan.any { it.contains("5 / 5 attendues") },
        )
        assertTrue(bilan.any { it.contains("100 %") })
        // Le faux producteur rend `null` : l'échec est annoncé, et aucun
        // document n'est proposé au partage.
        assertTrue(viewModel.echecExport.value)
        assertNull(viewModel.documentPret.value)
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
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.realisations.collect { }
        }
    }

    /** Le dernier document que l'export a produit, pour l'éprouver sans téléphone. */
    private var imprime: DocumentImprime? = null

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
            // Le PDF ne se dessine pas sans téléphone : le faux retient le
            // document et rend `null`, ce qui fait prendre au ViewModel la
            // branche d'échec. C'est le texte qui s'éprouve ici, pas le dessin.
            ProducteurPdf { document ->
                imprime = document
                null
            },
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
