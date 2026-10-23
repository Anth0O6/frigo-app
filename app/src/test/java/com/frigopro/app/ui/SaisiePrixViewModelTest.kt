package com.frigopro.app.ui

import com.frigopro.app.data.Article
import com.frigopro.app.data.FauxMaterielDao
import com.frigopro.app.data.FauxParametresDao
import com.frigopro.app.data.FauxRangementPhotos
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.data.MaterielRepository
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * La saisie groupée des prix du magasin.
 *
 * Les cas retenus sont ceux où l'écran mentirait sans que rien ne le montre : un
 * prix de vente inventé faute de coefficient, une vente tapée à la main que le
 * calcul écraserait, un compteur qui annoncerait des modifications que personne
 * n'a faites, un fournisseur posé sans sa copie.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaisiePrixViewModelTest {

    private val daoMateriel = FauxMaterielDao()
    private val daoParametres = FauxParametresDao()

    private val goulotte = Article(id = "acc-01", designation = "Goulotte clim 60x45 — barre 2 m", unite = "barre")
    private val angle = Article(id = "acc-02", designation = "Goulotte clim 60x45 — angle plat")
    private val pompe = Article(id = "acc-03", designation = "Pompe de relevage")

    @After
    fun nettoyer() {
        Dispatchers.resetMain()
    }

    // — Le prix de vente proposé ————————————————————————————————————————

    /**
     * Le prix d'achat multiplié, **arrondi au centime** : 7,45 × 1,35 fait
     * 10,0575, et c'est 10,06 qui part sur un devis. Même calcul que la feuille
     * du matériel, sans quoi un article tarifé ici ne retomberait pas sur le
     * prix que le devis aurait proposé.
     */
    @Test
    fun `la vente se propose au coefficient, arrondie au centime`() = runTest {
        val viewModel = creerViewModel(coefficient = 1.35, goulotte)

        viewModel.onPrixAchat(goulotte, 7.45)

        val ligne = ligne(viewModel, goulotte.id)
        assertEquals(10.06, ligne.prixVente, 0.0001)
        assertEquals(OrigineVente.COEFFICIENT, ligne.origineVente)

        // Et un prix à trois décimales — du cuivre au mètre — retombe sur le centime.
        viewModel.onPrixAchat(goulotte, 3.333)
        assertEquals(5.0, ligne(viewModel, goulotte.id).prixVente, 0.0001)
    }

    /** Le calcul lui-même : rien sans coefficient, rien sans prix d'achat. */
    @Test
    fun `la proposition ne s'invente pas`() {
        assertEquals(10.06, propositionVente(7.45, 1.35)!!, 0.0001)
        assertNull("coefficient non réglé", propositionVente(7.45, 0.0))
        assertNull("prix d'achat inconnu", propositionVente(null, 1.35))
        assertNull("prix d'achat à zéro", propositionVente(0.0, 1.35))
    }

    /**
     * Un coefficient à zéro veut dire « non réglé » : le prix de vente n'est pas
     * proposé, il reste vide, et l'état le dit pour que l'écran mène aux Réglages.
     */
    @Test
    fun `sans coefficient, aucune vente n'est proposee`() = runTest {
        val viewModel = creerViewModel(coefficient = 0.0, goulotte)

        viewModel.onPrixAchat(goulotte, 7.45)
        viewModel.onEnregistrer()
        advanceUntilIdle()

        assertFalse(etatDe(viewModel).coefficientRegle)
        val article = daoMateriel.contenuArticles.single()
        assertEquals(7.45, article.prixAchat, 0.0001)
        assertEquals("rien d'inventé", 0.0, article.prixVente, 0.0001)
    }

    /**
     * Une vente tapée à la main fait foi : corriger ensuite le prix d'achat ne
     * l'écrase pas. Vider son champ rend la main au coefficient.
     */
    @Test
    fun `une vente tapee a la main n'est jamais ecrasee par le calcul`() = runTest {
        val viewModel = creerViewModel(coefficient = 1.8, goulotte)

        viewModel.onPrixAchat(goulotte, 10.0)
        viewModel.onPrixVente(goulotte, 19.9)
        viewModel.onPrixAchat(goulotte, 12.0)

        assertEquals(19.9, ligne(viewModel, goulotte.id).prixVente, 0.0001)
        assertEquals(OrigineVente.SAISIE, ligne(viewModel, goulotte.id).origineVente)

        viewModel.onPrixVente(goulotte, null)

        assertEquals("le champ vidé suit de nouveau l'achat", 21.6, ligne(viewModel, goulotte.id).prixVente, 0.0001)
    }

    /**
     * Le prix de vente déjà en base : gardé s'il a été décidé, suivi s'il n'était
     * que la proposition du coefficient — c'est ce qui fait suivre la hausse d'un
     * tarif sans retaper chaque vente.
     */
    @Test
    fun `une vente decidee sur la fiche est gardee, une vente proposee suit l'achat`() = runTest {
        val decidee = goulotte.copy(prixAchat = 10.0, prixVente = 20.0)
        val proposee = angle.copy(prixAchat = 10.0, prixVente = 18.0)
        val viewModel = creerViewModel(coefficient = 1.8, decidee, proposee)

        viewModel.onPrixAchat(decidee, 12.0)
        viewModel.onPrixAchat(proposee, 12.0)

        assertEquals(20.0, ligne(viewModel, decidee.id).prixVente, 0.0001)
        assertEquals(OrigineVente.FICHE, ligne(viewModel, decidee.id).origineVente)
        assertEquals(21.6, ligne(viewModel, proposee.id).prixVente, 0.0001)
    }

    // — Ce qui s'écrit ————————————————————————————————————————————————

    /**
     * Le compteur vaut zéro à l'ouverture, une valeur retapée à l'identique ne
     * compte pas, et seules les lignes réellement modifiées partent — en une
     * seule écriture.
     */
    @Test
    fun `seules les lignes modifiees s'ecrivent, en une fois`() = runTest {
        val dejaTarifee = pompe.copy(prixAchat = 89.0, prixVente = 160.0)
        val viewModel = creerViewModel(coefficient = 1.8, goulotte, angle, dejaTarifee)
        assertEquals("rien à enregistrer à l'ouverture", 0, etatDe(viewModel).aEnregistrer)

        viewModel.onPrixAchat(goulotte, 7.45)
        viewModel.onReference(angle, "GC-AP")
        // Touchée, puis remise à l'identique.
        viewModel.onPrixAchat(dejaTarifee, 90.0)
        viewModel.onPrixAchat(dejaTarifee, 89.0)
        assertEquals(2, etatDe(viewModel).aEnregistrer)

        val avant = daoMateriel.ecrituresArticles
        viewModel.onEnregistrer()
        advanceUntilIdle()

        assertEquals("une seule écriture", 1, daoMateriel.ecrituresArticles - avant)
        val parId = daoMateriel.contenuArticles.associateBy { it.id }
        assertEquals(13.41, parId.getValue(goulotte.id).prixVente, 0.0001)
        assertEquals("GC-AP", parId.getValue(angle.id).reference)
        assertEquals("la ligne inchangée garde sa date", Instant.EPOCH, parId.getValue(dejaTarifee.id).modifieLe)
        assertEquals("et la saisie est vidée de ce qui est écrit", 0, etatDe(viewModel).aEnregistrer)
    }

    /** Même nettoyage que la fiche : un prix négatif veut dire « pas renseigné ». */
    @Test
    fun `un prix negatif est ramene a zero`() = runTest {
        val viewModel = creerViewModel(coefficient = 0.0, goulotte)

        viewModel.onPrixAchat(goulotte, -7.0)
        viewModel.onPrixVente(goulotte, -3.0)
        viewModel.onReference(goulotte, "GC6045")
        viewModel.onEnregistrer()
        advanceUntilIdle()

        val article = daoMateriel.contenuArticles.single()
        assertEquals(0.0, article.prixAchat, 0.0001)
        assertEquals(0.0, article.prixVente, 0.0001)
        assertEquals("GC6045", article.reference)
    }

    /**
     * Le fournisseur en lot : le lien **et** sa copie sur chaque ligne cochée,
     * y compris celle que la recherche masque — on compose une sélection en
     * plusieurs recherches —, et rien sur les autres.
     */
    @Test
    fun `le fournisseur s'attribue en lot avec son lien et sa copie`() = runTest {
        daoMateriel.enregistrerFournisseur(Fournisseur(id = "four-gff", nom = "GFF"))
        val viewModel = creerViewModel(coefficient = 1.8, goulotte, angle, pompe)

        viewModel.onCocher(goulotte.id)
        viewModel.onCocher(pompe.id)
        viewModel.onRecherche("goulotte")
        assertEquals("la pompe cochée est masquée, mais comptée", 2, etatDe(viewModel).cochees)

        viewModel.onAttribuerFournisseur(Fournisseur(id = "four-gff", nom = "GFF"))
        viewModel.onEnregistrer()
        advanceUntilIdle()

        val parId = daoMateriel.contenuArticles.associateBy { it.id }
        listOf(goulotte.id, pompe.id).forEach { id ->
            assertEquals("four-gff", parId.getValue(id).fournisseurId)
            assertEquals("GFF", parId.getValue(id).fournisseurNom)
        }
        assertNull("la ligne non cochée n'a rien reçu", parId.getValue(angle.id).fournisseurId)
        assertEquals("la sélection est vidée après le lot", 0, etatDe(viewModel).cochees)
    }

    // — La liste ——————————————————————————————————————————————————————

    /**
     * « Sans prix » se juge sur la base : la ligne qu'on remplit ne disparaît
     * pas au premier chiffre, et elle quitte le filtre une fois enregistrée.
     */
    @Test
    fun `une ligne ne quitte le filtre sans prix qu'a l'enregistrement`() = runTest {
        val tarifee = pompe.copy(prixAchat = 89.0)
        val viewModel = creerViewModel(coefficient = 1.8, goulotte, tarifee)
        viewModel.onFiltre(FiltrePrix.SANS_PRIX)
        assertEquals(listOf(goulotte.id), etatDe(viewModel).lignes.map { it.article.id })

        viewModel.onPrixAchat(goulotte, 7.45)
        assertEquals(listOf(goulotte.id), etatDe(viewModel).lignes.map { it.article.id })

        viewModel.onEnregistrer()
        advanceUntilIdle()
        assertTrue(etatDe(viewModel).lignes.isEmpty())
        assertEquals(0, etatDe(viewModel).sansPrix)
    }

    /** Abandonner, c'est vider la saisie et fermer — rien n'est écrit. */
    @Test
    fun `abandonner ne laisse rien en base`() = runTest {
        val viewModel = creerViewModel(coefficient = 1.8, goulotte)
        viewModel.onOuvrir()

        viewModel.onPrixAchat(goulotte, 7.45)
        viewModel.onAbandonner()
        advanceUntilIdle()

        assertFalse(viewModel.ouverte.value)
        assertEquals(0, etatDe(viewModel).aEnregistrer)
        assertEquals(0.0, daoMateriel.contenuArticles.single().prixAchat, 0.0001)
    }

    /** L'état de l'écran, une fois que tout ce qui était en cours a abouti. */
    private fun TestScope.etatDe(viewModel: SaisiePrixViewModel): EtatSaisiePrix {
        advanceUntilIdle()
        return viewModel.etat.value
    }

    private fun TestScope.ligne(viewModel: SaisiePrixViewModel, id: String): LignePrix =
        etatDe(viewModel).lignes.single { it.article.id == id }

    private suspend fun TestScope.creerViewModel(
        coefficient: Double,
        vararg articles: Article,
    ): SaisiePrixViewModel {
        val ordonnanceur = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(ordonnanceur)
        daoParametres.enregistrer(Parametres(coefficientMateriel = coefficient))
        daoMateriel.enregistrerArticles(articles.toList())
        val viewModel = SaisiePrixViewModel(
            MaterielRepository(daoMateriel),
            ParametresRepository(daoParametres, FauxRangementPhotos()),
        )
        // `etat` est un `stateIn(WhileSubscribed)` : sans collecteur il resterait
        // à sa valeur initiale, et les assertions liraient une liste vide.
        listOf(viewModel.etat, viewModel.fournisseurs)
            .forEach { flux -> backgroundScope.launch(ordonnanceur) { flux.collect { } } }
        return viewModel
    }
}
