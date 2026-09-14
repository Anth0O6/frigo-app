package com.frigopro.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Le contrat SQL du magasin, reproduit en mémoire.
 *
 * Deux comportements sont **recopiés à la main** plutôt qu'empruntés au
 * domaine, et c'est délibéré : l'unicité `(articleId, lieu)`, portée en base par
 * un index unique, et le détachement des articles quand un fournisseur
 * disparaît. Les laisser au hasard ferait passer des tests que la vraie base
 * refuserait.
 */
class FauxMaterielDao : MaterielDao() {

    private val lesFournisseurs = MutableStateFlow<List<Fournisseur>>(emptyList())

    private val lesArticles = MutableStateFlow<List<Article>>(emptyList())

    private val lesStocks = MutableStateFlow<List<Stock>>(emptyList())

    val contenuFournisseurs: List<Fournisseur> get() = lesFournisseurs.value

    val contenuArticles: List<Article> get() = lesArticles.value

    val contenuStocks: List<Stock> get() = lesStocks.value

    override fun observerFournisseurs(): Flow<List<Fournisseur>> = lesFournisseurs

    override suspend fun tousLesFournisseurs(): List<Fournisseur> = lesFournisseurs.value

    override suspend fun fournisseur(id: String): Fournisseur? =
        lesFournisseurs.value.firstOrNull { it.id == id }

    override suspend fun enregistrerFournisseur(fournisseur: Fournisseur) {
        lesFournisseurs.update { liste -> liste.filterNot { it.id == fournisseur.id } + fournisseur }
    }

    override suspend fun enregistrerFournisseurs(fournisseurs: List<Fournisseur>) {
        val identifiants = fournisseurs.map { it.id }.toSet()
        lesFournisseurs.update { liste -> liste.filterNot { it.id in identifiants } + fournisseurs }
    }

    override suspend fun detacherArticlesDu(id: String) {
        lesArticles.update { liste ->
            liste.map { if (it.fournisseurId == id) it.copy(fournisseurId = null) else it }
        }
    }

    override suspend fun effacerFournisseur(id: String) {
        lesFournisseurs.update { liste -> liste.filterNot { it.id == id } }
    }

    override suspend fun majNomFournisseur(id: String, nom: String) {
        lesFournisseurs.update { liste ->
            liste.map { if (it.id == id) it.copy(nom = nom) else it }
        }
    }

    override suspend fun majNomSurArticles(id: String, nom: String) {
        lesArticles.update { liste ->
            liste.map { if (it.fournisseurId == id) it.copy(fournisseurNom = nom) else it }
        }
    }

    override fun observerArticles(): Flow<List<Article>> = lesArticles

    override suspend fun tousLesArticles(): List<Article> = lesArticles.value

    override fun observerArticle(id: String): Flow<Article?> =
        MutableStateFlow(lesArticles.value.firstOrNull { it.id == id })

    override suspend fun enregistrerArticle(article: Article) {
        lesArticles.update { liste -> liste.filterNot { it.id == article.id } + article }
    }

    override suspend fun enregistrerArticles(articles: List<Article>) {
        val identifiants = articles.map { it.id }.toSet()
        lesArticles.update { liste -> liste.filterNot { it.id in identifiants } + articles }
    }

    override suspend fun effacerStocksDe(articleId: String) {
        lesStocks.update { liste -> liste.filterNot { it.articleId == articleId } }
    }

    override suspend fun effacerArticle(id: String) {
        lesArticles.update { liste -> liste.filterNot { it.id == id } }
    }

    override fun observerStocks(): Flow<List<Stock>> = lesStocks

    override suspend fun tousLesStocks(): List<Stock> = lesStocks.value

    override suspend fun stock(articleId: String, lieu: LieuStock): Stock? =
        lesStocks.value.firstOrNull { it.articleId == articleId && it.lieu == lieu }

    override suspend fun enregistrerStock(stock: Stock) {
        // L'index unique `(articleId, lieu)` de la vraie base : une ligne au
        // plus par article et par endroit, quel que soit l'identifiant.
        lesStocks.update { liste ->
            liste.filterNot {
                it.id == stock.id || (it.articleId == stock.articleId && it.lieu == stock.lieu)
            } + stock
        }
    }

    override suspend fun enregistrerStocks(stocks: List<Stock>) {
        stocks.forEach { stock ->
            lesStocks.update { liste ->
                liste.filterNot {
                    it.id == stock.id || (it.articleId == stock.articleId && it.lieu == stock.lieu)
                } + stock
            }
        }
    }
}
