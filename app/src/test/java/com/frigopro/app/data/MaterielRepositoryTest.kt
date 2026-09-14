package com.frigopro.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le magasin.
 *
 * Les cas retenus sont ceux où une erreur coûte de l'argent sans se voir : une
 * marge annoncée dans la mauvaise unité, un stock qui part en négatif, une
 * alerte qui se déclenche sur un article qu'on ne tient pas.
 */
class MaterielRepositoryTest {

    private val dao = FauxMaterielDao()
    private val depot = MaterielRepository(dao)

    // — La marge, et les trois façons de la dire ————————————————————————

    /**
     * Le cas qui motive le type : 100 € acheté, 150 € vendu.
     *
     * 50 € de marge, 33 % de taux de marque, coefficient 1,5. Annoncer « 50 % »
     * serait ambigu — c'est le coefficient exprimé en pourcentage du prix
     * d'achat, ce que personne ne lit ainsi sur un compte de résultat.
     */
    @Test
    fun `la marge se dit de trois facons qui ne se confondent pas`() {
        val marge = Marge(prixAchat = 100.0, prixVente = 150.0)

        assertEquals(50.0, marge.brute!!, 0.001)
        assertEquals("le taux de marque se calcule sur le prix de vente", 33.3, marge.tauxDeMarque!!, 0.05)
        assertEquals("le coefficient se calcule sur le prix d'achat", 1.5, marge.coefficient!!, 0.001)
        assertFalse(marge.aPerte)
    }

    /**
     * Sans prix d'achat, rien n'est déduit.
     *
     * Un prix d'achat à zéro donnerait un coefficient infini ; le dire inconnu
     * vaut mieux. Même règle que le GWP d'un fluide hors catalogue.
     */
    @Test
    fun `sans prix d'achat, aucune marge n'est inventee`() {
        val marge = Marge(prixAchat = 0.0, prixVente = 150.0)

        assertFalse(marge.chiffrable)
        assertNull(marge.brute)
        assertNull(marge.tauxDeMarque)
        assertNull(marge.coefficient)
    }

    /** Vendre à perte se signale, et ne se corrige pas en silence. */
    @Test
    fun `vendre sous le prix d'achat se voit`() {
        val marge = Marge(prixAchat = 120.0, prixVente = 100.0)

        assertTrue(marge.aPerte)
        assertEquals(-20.0, marge.brute!!, 0.001)
    }

    // — Les deux stocks ————————————————————————————————————————————————

    /**
     * Le manque se déduit, il ne se stocke pas.
     *
     * Et il se juge **par endroit** : trois détendeurs à l'atelier ne dépannent
     * personne à 40 km de là, où le camion est à zéro.
     */
    @Test
    fun `le manque se juge endroit par endroit`() = runTest {
        val article = depot.enregistrerArticle(Article(id = "a-1", designation = "Détendeur"))
        depot.definirStock(article.id, LieuStock.ATELIER, quantite = 5.0, minimum = 2.0)
        depot.definirStock(article.id, LieuStock.CAMION, quantite = 0.0, minimum = 1.0)

        val entree = depot.magasin.first().single()

        assertEquals(listOf(LieuStock.CAMION), entree.aReapprovisionner)
        assertTrue(entree.enAlerte)
        assertEquals(5.0, entree.total, 0.001)
        assertEquals(1.0, entree.stocks.getValue(LieuStock.CAMION).manquant!!, 0.001)
    }

    /**
     * Un seuil à zéro veut dire « pas de seuil », et non « seuil à zéro ».
     *
     * Sans cela, tout article épuisé alerterait — y compris celui qu'on ne tient
     * délibérément pas en stock et qu'on commande à la demande. Une alerte qui
     * se déclenche toujours est une alerte qu'on cesse de lire.
     */
    @Test
    fun `un article sans seuil n'alerte jamais`() = runTest {
        val article = depot.enregistrerArticle(Article(id = "a-1", designation = "Compresseur"))
        depot.definirStock(article.id, LieuStock.ATELIER, quantite = 0.0, minimum = 0.0)

        val entree = depot.magasin.first().single()

        assertFalse(entree.enAlerte)
        assertFalse(entree.stocks.getValue(LieuStock.ATELIER).surveille)
    }

    /**
     * Un mouvement additionne, il n'écrase pas.
     *
     * C'est ce qui rend l'opération juste quand deux écrans l'appellent à
     * quelques secondes d'écart : poser une pièce chez un client retire une
     * unité à ce qu'il y avait, sans avoir à relire d'abord.
     */
    @Test
    fun `un mouvement s'ajoute a ce qu'il y avait`() = runTest {
        depot.enregistrerArticle(Article(id = "a-1", designation = "Filtre déshydrateur"))
        depot.definirStock("a-1", LieuStock.CAMION, quantite = 4.0, minimum = 2.0)

        depot.bouger("a-1", LieuStock.CAMION, delta = -1.0)
        depot.bouger("a-1", LieuStock.CAMION, delta = -1.0)

        val stock = dao.stock("a-1", LieuStock.CAMION)!!
        assertEquals(2.0, stock.quantite, 0.001)
        assertEquals("le seuil ne bouge pas avec la quantité", 2.0, stock.minimum, 0.001)
    }

    /** On ne doit pas de pièces à son propre magasin : le stock plancher à zéro. */
    @Test
    fun `un stock ne part jamais en negatif`() = runTest {
        depot.enregistrerArticle(Article(id = "a-1", designation = "Vanne"))
        depot.definirStock("a-1", LieuStock.CAMION, quantite = 1.0, minimum = 0.0)

        depot.bouger("a-1", LieuStock.CAMION, delta = -5.0)

        assertEquals(0.0, dao.stock("a-1", LieuStock.CAMION)!!.quantite, 0.001)
    }

    /** Le geste du matin : charger le camion depuis l'atelier. */
    @Test
    fun `un transfert deplace sans rien creer ni perdre`() = runTest {
        depot.enregistrerArticle(Article(id = "a-1", designation = "Détendeur"))
        depot.definirStock("a-1", LieuStock.ATELIER, quantite = 6.0, minimum = 0.0)

        depot.transferer("a-1", LieuStock.ATELIER, LieuStock.CAMION, quantite = 2.0)

        assertEquals(4.0, dao.stock("a-1", LieuStock.ATELIER)!!.quantite, 0.001)
        assertEquals(2.0, dao.stock("a-1", LieuStock.CAMION)!!.quantite, 0.001)
    }

    /**
     * On ne charge pas plus que ce qu'il y a.
     *
     * Charger quatre détendeurs quand il y en a trois laisserait l'atelier à
     * -1 : un compte que plus rien ne rattrape, et qui ferait partir en tournée
     * avec une pièce qui n'existe pas.
     */
    @Test
    fun `un transfert ne prend que ce qui est disponible`() = runTest {
        depot.enregistrerArticle(Article(id = "a-1", designation = "Détendeur"))
        depot.definirStock("a-1", LieuStock.ATELIER, quantite = 3.0, minimum = 0.0)

        depot.transferer("a-1", LieuStock.ATELIER, LieuStock.CAMION, quantite = 4.0)

        assertEquals(0.0, dao.stock("a-1", LieuStock.ATELIER)!!.quantite, 0.001)
        assertEquals("et rien n'est créé au passage", 3.0, dao.stock("a-1", LieuStock.CAMION)!!.quantite, 0.001)
    }

    /** La valeur du magasin se compte au prix d'achat : du stock n'est pas du chiffre d'affaires. */
    @Test
    fun `la valeur du stock se compte au prix d'achat`() = runTest {
        depot.enregistrerArticle(
            Article(id = "a-1", designation = "Détendeur", prixAchat = 40.0, prixVente = 90.0),
        )
        depot.definirStock("a-1", LieuStock.ATELIER, quantite = 3.0, minimum = 0.0)
        depot.definirStock("a-1", LieuStock.CAMION, quantite = 2.0, minimum = 0.0)

        val entree = depot.magasin.first().single()

        assertEquals(200.0, entree.valeurAchat, 0.001)
    }

    // — Les fournisseurs ————————————————————————————————————————————————

    /**
     * Les préférés d'abord, puis l'ordre français.
     *
     * Quand on cherche un numéro à 7 h du matin, ce qu'on veut est que le bon
     * soit en haut — pas qu'il soit joliment marqué au milieu de douze autres.
     */
    @Test
    fun `les preferes remontent, et le reste suit l'ordre francais`() = runTest {
        depot.enregistrerFournisseur(Fournisseur(id = "f-1", nom = "Zéolithe Diffusion"))
        depot.enregistrerFournisseur(Fournisseur(id = "f-2", nom = "Économiseur SA"))
        depot.enregistrerFournisseur(Fournisseur(id = "f-3", nom = "Airtech", prefere = true))

        val liste = depot.fournisseurs.first()

        assertEquals(
            listOf("Airtech", "Économiseur SA", "Zéolithe Diffusion"),
            liste.map { it.nom },
        )
    }

    /** Un renommage suit les articles, par la copie. */
    @Test
    fun `renommer un fournisseur suit ses articles`() = runTest {
        depot.enregistrerFournisseur(Fournisseur(id = "f-1", nom = "Airtech"))
        depot.enregistrerArticle(
            Article(id = "a-1", designation = "Détendeur", fournisseurId = "f-1", fournisseurNom = "Airtech"),
        )

        depot.renommerFournisseur("f-1", "Airtech Froid")

        assertEquals("Airtech Froid", dao.contenuArticles.single().fournisseurNom)
    }

    /**
     * Une suppression coupe le lien et **laisse le nom**.
     *
     * Même règle que pour un type d'intervention ou une machine : un article
     * doit continuer de dire d'où il venait, sans quoi on ne sait plus chez qui
     * le racheter.
     */
    @Test
    fun `supprimer un fournisseur laisse le nom sur ses articles`() = runTest {
        depot.enregistrerFournisseur(Fournisseur(id = "f-1", nom = "Airtech"))
        depot.enregistrerArticle(
            Article(id = "a-1", designation = "Détendeur", fournisseurId = "f-1", fournisseurNom = "Airtech"),
        )

        depot.supprimerFournisseur("f-1")

        val article = dao.contenuArticles.single()
        assertNull(article.fournisseurId)
        assertEquals("Airtech", article.fournisseurNom)
    }

    /** Un article effacé emporte ses stocks : ils n'ont plus d'objet. */
    @Test
    fun `supprimer un article emporte ses lignes de stock`() = runTest {
        depot.enregistrerArticle(Article(id = "a-1", designation = "Détendeur"))
        depot.definirStock("a-1", LieuStock.ATELIER, quantite = 3.0, minimum = 1.0)

        depot.supprimerArticle("a-1")

        assertTrue(dao.contenuStocks.isEmpty())
    }

    /**
     * Une adresse tapée sans protocole ne s'ouvre pas.
     *
     * « www.fournisseur.fr » n'est pas une adresse qu'une intention Android
     * ouvre : il lui faut un schéma. Le corriger à la saisie vaut mieux qu'un
     * bouton qui ne fait rien.
     */
    @Test
    fun `l'adresse d'un catalogue recoit son protocole`() = runTest {
        val sansProtocole = depot.enregistrerFournisseur(
            Fournisseur(id = "f-1", nom = "Airtech", siteCatalogue = " www.airtech.fr "),
        )
        val avecProtocole = depot.enregistrerFournisseur(
            Fournisseur(id = "f-2", nom = "Froid Diffusion", siteCatalogue = "http://froid.fr"),
        )
        val sansRien = depot.enregistrerFournisseur(Fournisseur(id = "f-3", nom = "Sans site"))

        assertEquals("https://www.airtech.fr", sansProtocole.siteCatalogue)
        assertEquals("celui qui en a un le garde", "http://froid.fr", avecProtocole.siteCatalogue)
        assertEquals("et rien ne s'invente sur un champ vide", "", sansRien.siteCatalogue)
        assertFalse(sansRien.consultable)
    }

    /** Un prix négatif n'a pas de sens et fausserait toute marge qui en découle. */
    @Test
    fun `un prix negatif est ramene a zero`() = runTest {
        val article = depot.enregistrerArticle(
            Article(id = "a-1", designation = "Détendeur", prixAchat = -40.0, prixVente = 90.0),
        )

        assertEquals(0.0, article.prixAchat, 0.001)
        assertFalse("et la marge redevient incalculable", article.marge.chiffrable)
    }
}
