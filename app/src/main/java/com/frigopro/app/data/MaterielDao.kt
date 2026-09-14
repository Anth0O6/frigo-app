package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès SQL au magasin : fournisseurs, articles et stocks.
 *
 * Les trois sont réunis dans un seul DAO parce que supprimer un article doit
 * emporter ses lignes de stock, et qu'un `@Transaction` ne peut pas s'écrire
 * entre deux DAO — même raison que pour les factures et leurs lignes.
 *
 * Aucun `ORDER BY` sur les noms : le tri alphabétique se fait côté Kotlin, avec
 * un `Collator` français, `COLLATE NOCASE` de SQLite ne repliant pas les
 * accents. Même règle que le carnet de clients.
 */
@Dao
abstract class MaterielDao {

    // — Les fournisseurs ————————————————————————————————————————————————

    @Query("SELECT * FROM fournisseurs")
    abstract fun observerFournisseurs(): Flow<List<Fournisseur>>

    @Query("SELECT * FROM fournisseurs")
    abstract suspend fun tousLesFournisseurs(): List<Fournisseur>

    @Query("SELECT * FROM fournisseurs WHERE id = :id")
    abstract suspend fun fournisseur(id: String): Fournisseur?

    @Upsert
    abstract suspend fun enregistrerFournisseur(fournisseur: Fournisseur)

    @Upsert
    abstract suspend fun enregistrerFournisseurs(fournisseurs: List<Fournisseur>)

    /**
     * Supprimer un fournisseur **coupe le lien et garde la copie** sur ses
     * articles, comme pour un type d'intervention ou une machine : un article
     * doit continuer de dire d'où il venait, même si la fiche a disparu.
     */
    @Transaction
    open suspend fun supprimerFournisseur(id: String) {
        detacherArticlesDu(id)
        effacerFournisseur(id)
    }

    @Query("UPDATE articles SET fournisseurId = NULL WHERE fournisseurId = :id")
    abstract suspend fun detacherArticlesDu(id: String)

    @Query("DELETE FROM fournisseurs WHERE id = :id")
    abstract suspend fun effacerFournisseur(id: String)

    /**
     * Renommer un fournisseur suit ses articles, par la copie.
     *
     * Les deux écritures sont dans la même transaction : un fournisseur renommé
     * sans ses articles laisserait la base en désaccord avec elle-même.
     */
    @Transaction
    open suspend fun renommerFournisseur(id: String, nom: String) {
        majNomFournisseur(id, nom)
        majNomSurArticles(id, nom)
    }

    @Query("UPDATE fournisseurs SET nom = :nom WHERE id = :id")
    abstract suspend fun majNomFournisseur(id: String, nom: String)

    @Query("UPDATE articles SET fournisseurNom = :nom WHERE fournisseurId = :id")
    abstract suspend fun majNomSurArticles(id: String, nom: String)

    // — Les articles ————————————————————————————————————————————————————

    @Query("SELECT * FROM articles")
    abstract fun observerArticles(): Flow<List<Article>>

    @Query("SELECT * FROM articles")
    abstract suspend fun tousLesArticles(): List<Article>

    @Query("SELECT * FROM articles WHERE id = :id")
    abstract fun observerArticle(id: String): Flow<Article?>

    @Upsert
    abstract suspend fun enregistrerArticle(article: Article)

    @Upsert
    abstract suspend fun enregistrerArticles(articles: List<Article>)

    /** Un article effacé emporte ses lignes de stock : elles n'ont plus d'objet. */
    @Transaction
    open suspend fun supprimerArticle(id: String) {
        effacerStocksDe(id)
        effacerArticle(id)
    }

    @Query("DELETE FROM stocks WHERE articleId = :articleId")
    abstract suspend fun effacerStocksDe(articleId: String)

    @Query("DELETE FROM articles WHERE id = :id")
    abstract suspend fun effacerArticle(id: String)

    // — Les stocks ——————————————————————————————————————————————————————

    @Query("SELECT * FROM stocks")
    abstract fun observerStocks(): Flow<List<Stock>>

    @Query("SELECT * FROM stocks")
    abstract suspend fun tousLesStocks(): List<Stock>

    /**
     * La ligne d'un article à un endroit, s'il y en a une.
     *
     * `null` et « quantité zéro » sont la même chose pour le technicien, mais
     * pas pour l'écriture : c'est ce qui distingue une ligne à créer d'une ligne
     * à mettre à jour, et c'est la seule raison pour laquelle cette requête
     * existe.
     */
    @Query("SELECT * FROM stocks WHERE articleId = :articleId AND lieu = :lieu LIMIT 1")
    abstract suspend fun stock(articleId: String, lieu: LieuStock): Stock?

    @Upsert
    abstract suspend fun enregistrerStock(stock: Stock)

    @Upsert
    abstract suspend fun enregistrerStocks(stocks: List<Stock>)
}
