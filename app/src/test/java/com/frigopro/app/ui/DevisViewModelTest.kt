package com.frigopro.app.ui

import com.frigopro.app.data.Article
import com.frigopro.app.data.CategoriePrestation
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureRepository
import com.frigopro.app.data.FauxClientDao
import com.frigopro.app.data.FauxDevisDao
import com.frigopro.app.data.FauxEquipementDao
import com.frigopro.app.data.FauxFactureDao
import com.frigopro.app.data.FauxInterventionDao
import com.frigopro.app.data.FauxParametresDao
import com.frigopro.app.data.FauxPrestationDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.LigneDevis
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.PrestationRepository
import com.frigopro.app.data.RaisonEchec
import com.frigopro.app.data.ResultatItineraire
import com.frigopro.app.data.ServiceItineraire
import com.frigopro.app.data.StatutFacture
import com.frigopro.app.data.TarifDeplacement
import com.frigopro.app.data.ModeDeplacement
import com.frigopro.app.data.OrigineTrajet
import com.frigopro.app.data.Trajet
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
 * L'onglet Devis : le geste commercial, et la quantité que le multi-split
 * pré-remplit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevisViewModelTest {

    private val daoDevis = FauxDevisDao()
    private val daoFactures = FauxFactureDao()
    private val daoClients = FauxClientDao()
    private val daoParametres = FauxParametresDao()
    private val daoPrestations = FauxPrestationDao()
    private val daoInterventions = FauxInterventionDao()
    private val daoEquipements = FauxEquipementDao(daoInterventions)
    private val stockage = FauxRangementPhotos()

    private val mai = LocalDate.of(2026, 5, 14)

    /** Ce que le faux service d'itinéraire répondra. */
    private var itineraire: ResultatItineraire =
        ResultatItineraire.Echec(RaisonEchec.SERVICE_INDISPONIBLE)

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    /**
     * Un devis facturé quitte la liste principale, et **seulement** celle-là.
     *
     * Deux choses à tenir d'un coup, et la seconde est la plus facile à perdre :
     * il sort de ce qui attend une réponse, et il reste consultable. Un filtre
     * qui l'aurait effacé pour de bon aurait emporté la pièce qui dit ce que le
     * client avait accepté — celle qu'on ressort quand il conteste la facture.
     */
    @Test
    fun `un devis facture quitte la liste sans disparaitre`() = runTest {
        val depot = DevisRepository(daoDevis)
        val viewModel = creerViewModel()
        val chiffré = depot.creer(client = null, equipement = null, aujourdhui = mai)
        val enAttente = depot.creer(client = null, equipement = null, aujourdhui = mai)
        advanceUntilIdle()

        assertEquals(2, viewModel.liste.value.enCours.size)

        daoFactures.enregistrer(
            Facture(
                id = "f-1",
                numero = "FAC-2026-0001",
                devisId = chiffré.id,
                statut = StatutFacture.EMISE,
            ),
        )
        advanceUntilIdle()

        assertEquals(
            "seul le devis non facturé reste en tête",
            listOf(enAttente.id),
            viewModel.liste.value.enCours.map { it.devis.id },
        )
        val facturés = viewModel.liste.value.facturés
        assertEquals("l'autre est rangé, pas supprimé", 1, facturés.size)
        assertEquals(chiffré.id, facturés.single().chiffre.devis.id)
        assertEquals(
            "et il dit sous quel numéro il est parti",
            "FAC-2026-0001",
            facturés.single().facture.numero,
        )
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

    // — Le déplacement facturé —

    private val tarif = Parametres(
        modeDeplacement = ModeDeplacement.KM_ET_HEURE,
        prixKm = 0.45,
        prixHeureTrajet = 35.0,
    )

    /**
     * Un itinéraire calculé devient des lignes de devis, et le total du devis les
     * compte. C'est tout l'intérêt d'être passé par des lignes ordinaires plutôt
     * que par un second chemin vers le total.
     */
    @Test
    fun `un itineraire calcule devient des lignes de devis`() = runTest {
        daoParametres.enregistrer(tarif)
        itineraire = ResultatItineraire.Trouve(
            distanceKm = 12.0,
            dureeMinutes = 18,
            peages = 0.0,
            peagesConnus = true,
        )
        val viewModel = creerViewModel()
        advanceUntilIdle()
        viewModel.onNouveau(null)
        advanceUntilIdle()

        val devisId = daoDevis.contenu.single().id
        viewModel.onCalculerTrajet(
            Trajet(devisId = devisId, depart = "Lyon", arrivee = "Villeurbanne", allerRetour = true),
        )
        advanceUntilIdle()

        val trajet = daoDevis.contenuTrajets.single()
        assertEquals("les chiffres stockes sont ceux d'un aller", 12.0, trajet.distanceKm, 0.001)
        assertEquals(OrigineTrajet.CALCULE, trajet.origine)
        assertTrue("le calcul est date", trajet.calculeLe != null)

        val lignes = daoDevis.contenuLignes
        assertEquals(listOf("km", "h"), lignes.map { it.unite })
        assertEquals("24 km factures en aller-retour", 24.0, lignes.first().quantite, 0.001)
        assertEquals(31.80, lignes.sumOf { it.montant }, 0.001)
    }

    /**
     * Recalculer ne doit pas décocher l'aller-retour ni reprendre un geste
     * commercial : c'est le genre de perte qu'on ne remarque qu'en relisant le
     * total.
     */
    @Test
    fun `un recalcul garde l'aller-retour et le geste commercial`() = runTest {
        daoParametres.enregistrer(tarif)
        itineraire = ResultatItineraire.Trouve(distanceKm = 30.0, dureeMinutes = 40)
        val viewModel = creerViewModel()
        advanceUntilIdle()
        viewModel.onNouveau(null)
        advanceUntilIdle()
        val devisId = daoDevis.contenu.single().id

        viewModel.onEnregistrerTrajet(
            Trajet(
                devisId = devisId,
                depart = "Lyon",
                arrivee = "Vienne",
                distanceKm = 10.0,
                dureeMinutes = 15,
                allerRetour = true,
                offert = true,
            ),
        )
        advanceUntilIdle()

        viewModel.onCalculerTrajet(daoDevis.contenuTrajets.single())
        advanceUntilIdle()

        val trajet = daoDevis.contenuTrajets.single()
        assertEquals("la distance vient du service", 30.0, trajet.distanceKm, 0.001)
        assertTrue("l'aller-retour survit au recalcul", trajet.allerRetour)
        assertTrue("le geste commercial aussi", trajet.offert)
        assertTrue("et les lignes restent offertes", daoDevis.contenuLignes.all { it.offerte })
    }

    /**
     * Un échec ne casse rien et se dit : l'écran doit pouvoir renvoyer à la saisie
     * à la main, qui est le chemin normal sans clé ni réseau.
     */
    @Test
    fun `un echec de calcul est rapporte sans rien ecrire`() = runTest {
        daoParametres.enregistrer(tarif)
        itineraire = ResultatItineraire.Echec(RaisonEchec.PAS_DE_RESEAU)
        val viewModel = creerViewModel()
        advanceUntilIdle()
        viewModel.onNouveau(null)
        advanceUntilIdle()
        val devisId = daoDevis.contenu.single().id

        viewModel.onCalculerTrajet(Trajet(devisId = devisId, depart = "Lyon", arrivee = "Nulle part"))
        advanceUntilIdle()

        assertEquals(RaisonEchec.PAS_DE_RESEAU, viewModel.echecItineraire.value)
        assertTrue("aucun trajet n'est pose", daoDevis.contenuTrajets.isEmpty())
        assertTrue("et aucune ligne", daoDevis.contenuLignes.isEmpty())
        assertTrue("le bouton est rendu", !viewModel.calculEnCours.value)
    }

    /**
     * Recalculer remplace les lignes du déplacement **sans toucher** à celles
     * qu'on a saisies : c'est ce que garantit le marqueur `deplacement`.
     */
    @Test
    fun `un recalcul n'emporte pas les lignes saisies a la main`() = runTest {
        daoParametres.enregistrer(tarif)
        itineraire = ResultatItineraire.Trouve(distanceKm = 12.0, dureeMinutes = 18)
        val viewModel = creerViewModel()
        advanceUntilIdle()
        viewModel.onNouveau(null)
        advanceUntilIdle()
        val devisId = daoDevis.contenu.single().id

        viewModel.onAjouterLigne("Compresseur Copeland", 1.0, "unité", 980.0)
        advanceUntilIdle()
        viewModel.onCalculerTrajet(Trajet(devisId = devisId, depart = "Lyon", arrivee = "Vienne"))
        advanceUntilIdle()
        viewModel.onCalculerTrajet(daoDevis.contenuTrajets.single())
        advanceUntilIdle()

        val saisies = daoDevis.contenuLignes.filterNot { it.deplacement }
        assertEquals(1, saisies.size)
        assertEquals("Compresseur Copeland", saisies.single().designation)
        assertEquals(
            "deux calculs ne laissent pas deux jeux de lignes",
            2,
            daoDevis.contenuLignes.count { it.deplacement },
        )
    }

    /** Retirer le déplacement emporte le trajet et ses lignes, et rien d'autre. */
    @Test
    fun `retirer le deplacement laisse le reste du devis`() = runTest {
        daoParametres.enregistrer(tarif)
        val viewModel = creerViewModel()
        advanceUntilIdle()
        viewModel.onNouveau(null)
        advanceUntilIdle()
        val devisId = daoDevis.contenu.single().id
        viewModel.onAjouterLigne("Compresseur Copeland", 1.0, "unité", 980.0)
        viewModel.onEnregistrerTrajet(
            Trajet(devisId = devisId, depart = "Lyon", arrivee = "Vienne", distanceKm = 12.0),
        )
        advanceUntilIdle()

        viewModel.onRetirerDeplacement()
        advanceUntilIdle()

        assertTrue(daoDevis.contenuTrajets.isEmpty())
        assertEquals(1, daoDevis.contenuLignes.size)
        assertEquals("Compresseur Copeland", daoDevis.contenuLignes.single().designation)
    }

    /**
     * Sans tarif, le déplacement ne produit pas de lignes à zéro euro : un devis
     * annonçant « Déplacement — 0,00 € » passerait pour un geste commercial.
     */
    @Test
    fun `sans tarif le deplacement ne facture rien`() = runTest {
        val viewModel = creerViewModel()
        advanceUntilIdle()
        viewModel.onNouveau(null)
        advanceUntilIdle()
        val devisId = daoDevis.contenu.single().id

        viewModel.onEnregistrerTrajet(
            Trajet(devisId = devisId, depart = "Lyon", arrivee = "Vienne", distanceKm = 12.0),
        )
        advanceUntilIdle()

        assertTrue("le trajet est retenu", daoDevis.contenuTrajets.isNotEmpty())
        assertEquals(
            "mais aucune ligne a zero euro",
            0.0,
            daoDevis.contenuLignes.sumOf { it.montant },
            0.001,
        )
    }

    // — Le matériel, et ce qu'il rapporte ————————————————————————————————

    /**
     * Le matériel du magasin se pose sur un devis, avec son coût.
     *
     * C'était le trou : le magasin tenait les références et les deux prix, et
     * aucun devis ne pouvait y puiser — chiffrer la pose d'une clim revenait à
     * retaper le split à la main, ou à ne chiffrer que la main-d'œuvre.
     */
    @Test
    fun `un article du magasin devient une ligne qui porte son cout`() = runTest {
        val depot = DevisRepository(daoDevis)
        val viewModel = creerViewModel()
        val ouvert = depot.creer(client = null, equipement = null, aujourdhui = mai)
        viewModel.onOuvrir(ouvert)
        advanceUntilIdle()

        viewModel.onAjouterArticle(split, quantite = 2.0, prixVente = 890.0)
        advanceUntilIdle()

        val ligne = daoDevis.contenuLignes.single()
        // La référence entre dans l'intitulé : c'est ce qu'un client compare à un
        // devis concurrent, et ce qu'on dicte au fournisseur en commandant.
        assertEquals("Split mural 3,5 kW (SPL-35)", ligne.designation)
        assertEquals(2.0, ligne.quantite, 0.001)
        assertEquals(890.0, ligne.prixUnitaire, 0.001)
        assertEquals("le coût est recopié depuis la fiche", 640.0, ligne.prixAchat, 0.001)
    }

    /** Le prix de vente est décidé par l'appelant : au coûtant, c'est un geste. */
    @Test
    fun `le materiel peut se poser au prix coutant`() = runTest {
        val depot = DevisRepository(daoDevis)
        val viewModel = creerViewModel()
        val ouvert = depot.creer(client = null, equipement = null, aujourdhui = mai)
        viewModel.onOuvrir(ouvert)
        advanceUntilIdle()

        viewModel.onAjouterArticle(split, quantite = 1.0, prixVente = split.prixAchat)
        advanceUntilIdle()

        val ligne = daoDevis.contenuLignes.single()
        assertEquals(640.0, ligne.prixUnitaire, 0.001)
        // Le coût reste porté : la marge est nulle, et c'est une information —
        // pas un coût inconnu.
        assertEquals(640.0, ligne.prixAchat, 0.001)
        assertEquals(0.0, DevisComplet(ouvert, listOf(ligne)).margeMateriel?.brute ?: -1.0, 0.001)
    }

    /**
     * La marge ne porte que sur le matériel, et se tait quand il n'y en a pas.
     *
     * Un devis de pure main-d'œuvre n'a pas une marge de 100 % : il a un coût
     * horaire interne, qui est une autre grandeur et qui vit ailleurs. Annoncer
     * un bénéfice égal à la recette serait le genre de chiffre juste par
     * accident qu'on ne distingue pas d'un vrai.
     */
    @Test
    fun `la marge materiel ignore la main-d'oeuvre et se tait sans cout connu`() {
        val devis = Devis(id = "d-1", numero = "DEV-2605-001")

        val sansMateriel = DevisComplet(
            devis,
            listOf(LigneDevis(devisId = "d-1", designation = "Main-d'œuvre", quantite = 4.0, prixUnitaire = 68.0)),
        )
        assertNull("aucun coût connu : le calcul se tait", sansMateriel.margeMateriel)

        val mixte = DevisComplet(
            devis,
            listOf(
                LigneDevis(devisId = "d-1", designation = "Main-d'œuvre", quantite = 4.0, prixUnitaire = 68.0),
                LigneDevis(
                    devisId = "d-1",
                    designation = "Split",
                    quantite = 2.0,
                    prixUnitaire = 890.0,
                    prixAchat = 640.0,
                ),
            ),
        )
        // 2 × (890 − 640) = 500, et les 272 € de main-d'œuvre n'y sont pour rien.
        assertEquals(500.0, mixte.margeMateriel?.brute ?: 0.0, 0.001)
    }

    /** Une ligne offerte ne rapporte rien : son coût est déjà un geste commercial. */
    @Test
    fun `une ligne de materiel offerte sort du calcul de marge`() {
        val devis = Devis(id = "d-1", numero = "DEV-2605-001")
        val complet = DevisComplet(
            devis,
            listOf(
                LigneDevis(
                    devisId = "d-1",
                    designation = "Split",
                    quantite = 1.0,
                    prixUnitaire = 890.0,
                    prixAchat = 640.0,
                    offerte = true,
                ),
            ),
        )

        assertNull(complet.margeMateriel)
    }

    private val split = Article(
        id = "a-1",
        reference = "SPL-35",
        designation = "Split mural 3,5 kW",
        prixAchat = 640.0,
        prixVente = 890.0,
        unite = "u",
    )

    /** Même raison que dans [InterventionsViewModelTest] pour `Dispatchers.Main`. */
    private fun TestScope.creerViewModel(): DevisViewModel {
        val ordonnanceur = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(ordonnanceur)
        val viewModel = DevisViewModel(
            DevisRepository(daoDevis),
            ClientRepository(daoClients, stockage),
            ParametresRepository(daoParametres, stockage),
            PrestationRepository(daoPrestations),
            EquipementRepository(daoEquipements, stockage),
            FactureRepository(daoFactures),
            // Le PDF ne se dessine pas sans Android : le producteur rend `null`, ce
            // qui est le chemin d'échec. Ce que le document dit et où tombent ses
            // lignes est vérifié par [DocumentDevisTest] et [MiseEnPageDevisTest].
            ProducteurPdf { null },
            // Le réseau ne se joint pas depuis un test : le faux service rend ce
            // que la classe du test a posé dans [itineraire].
            ServiceItineraire { _, _ -> itineraire },
        )
        listOf(
            viewModel.liste,
            viewModel.compteurs,
            viewModel.catalogue,
            viewModel.carnet,
            viewModel.reglages,
            viewModel.complet,
            viewModel.unitesVisees,
            // `trajet` est un `stateIn(WhileSubscribed)` : sans collecteur il
            // resterait à `null`, et les actions qui le lisent — offrir, retirer —
            // ne verraient jamais le trajet enregistré.
            viewModel.trajet,
        ).forEach { flux -> backgroundScope.launch(ordonnanceur) { flux.collect { } } }
        return viewModel
    }
}
