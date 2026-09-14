package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès SQL aux factures et à leurs lignes.
 *
 * Réunis pour la même raison que le devis et ses lignes : supprimer l'un doit
 * emporter l'autre, et la moitié d'un document n'a aucun sens.
 *
 * Une différence de taille, pourtant, et elle est portée par le dépôt plutôt que
 * par le DAO : **une facture émise ne se supprime pas**. Le SQL sait le faire —
 * il faut bien pouvoir jeter un brouillon —, mais c'est
 * [FactureRepository.supprimer] qui refuse, parce que c'est là que la règle se
 * lit et s'éprouve.
 */
@Dao
abstract class FactureDao {

    /**
     * Les factures, de la plus récente à la plus ancienne.
     *
     * Triées sur [Facture.emiseLe] d'abord : c'est la date qui compte pour un
     * document comptable. Un brouillon n'en a pas encore, et `modifieLe` le
     * remonte alors là où on l'a laissé.
     */
    @Query("SELECT * FROM factures ORDER BY emiseLe DESC, modifieLe DESC")
    abstract fun observerToutes(): Flow<List<Facture>>

    @Query("SELECT * FROM factures WHERE clientId = :clientId ORDER BY emiseLe DESC")
    abstract fun observerDuClient(clientId: String): Flow<List<Facture>>

    @Query("SELECT * FROM factures WHERE id = :id")
    abstract fun observer(id: String): Flow<Facture?>

    /** La facture déjà établie pour cette intervention, s'il y en a une. */
    @Query("SELECT * FROM factures WHERE interventionId = :interventionId LIMIT 1")
    abstract fun observerDeLIntervention(interventionId: String): Flow<Facture?>

    /** Idem pour un devis : c'est ce qui empêche de facturer deux fois. */
    @Query("SELECT * FROM factures WHERE devisId = :devisId LIMIT 1")
    abstract suspend fun pourDevis(devisId: String): Facture?

    /**
     * Quel devis a produit quelle facture.
     *
     * Trois colonnes et pas la facture entière : ce qu'on en veut, c'est savoir
     * qu'un devis est facturé et sous quel numéro, pour le ranger et le dire.
     * Charger les factures pour n'en lire que cela ferait passer par la mémoire,
     * à chaque ouverture de l'onglet, tout ce que l'entreprise a jamais émis.
     */
    @Query("SELECT devisId, numero, statut FROM factures WHERE devisId IS NOT NULL")
    abstract fun observerDevisFactures(): Flow<List<DevisFacture>>

    @Query("SELECT * FROM factures WHERE interventionId = :interventionId LIMIT 1")
    abstract suspend fun pourIntervention(interventionId: String): Facture?

    @Query("SELECT * FROM factures")
    abstract suspend fun toutes(): List<Facture>

    /**
     * Les numéros déjà attribués.
     *
     * C'est ce dont [Numerotation.suivantAnnuel] a besoin, et rien d'autre :
     * charger les factures entières pour n'en lire que le numéro serait payer
     * cher une chaîne de treize caractères.
     */
    @Query("SELECT numero FROM factures WHERE numero != ''")
    abstract suspend fun numeros(): List<String>

    @Upsert
    abstract suspend fun enregistrer(facture: Facture)

    @Upsert
    abstract suspend fun enregistrerToutes(factures: List<Facture>)

    @Query("SELECT * FROM lignes_facture WHERE factureId = :factureId ORDER BY rang ASC")
    abstract fun observerLignes(factureId: String): Flow<List<LigneFacture>>

    @Query("SELECT * FROM lignes_facture WHERE factureId = :factureId ORDER BY rang ASC")
    abstract suspend fun lignesDe(factureId: String): List<LigneFacture>

    @Query("SELECT * FROM lignes_facture")
    abstract suspend fun toutesLesLignes(): List<LigneFacture>

    /**
     * Le montant hors taxes de chaque facture, en une requête.
     *
     * Même motif — et même `CASE` — que [DevisDao.observerTotaux] : les
     * compteurs de l'accueil et de l'onglet ont besoin du total de chacune, pas
     * du détail, et un `GROUP BY` les donne toutes d'un coup.
     */
    @Query(
        "SELECT factureId, SUM(CASE WHEN offerte = 0 THEN quantite * prixUnitaire ELSE 0 END) " +
            "AS montant FROM lignes_facture GROUP BY factureId",
    )
    abstract fun observerTotaux(): Flow<List<TotalFacture>>

    @Query("SELECT COALESCE(MAX(rang), -1) + 1 FROM lignes_facture WHERE factureId = :factureId")
    abstract suspend fun prochainRang(factureId: String): Int

    @Upsert
    abstract suspend fun enregistrerLigne(ligne: LigneFacture)

    @Upsert
    abstract suspend fun enregistrerLignes(lignes: List<LigneFacture>)

    @Query("DELETE FROM lignes_facture WHERE id = :id")
    abstract suspend fun effacerLigne(id: String)

    @Query("DELETE FROM lignes_facture WHERE factureId = :factureId")
    abstract suspend fun effacerLignesDe(factureId: String)

    @Query("DELETE FROM factures WHERE id = :id")
    abstract suspend fun effacer(id: String)

    /**
     * Supprime la facture et ses lignes d'un bloc.
     *
     * Par `@Transaction` pour la raison habituelle : une facture sans ses lignes
     * se verrait à l'écran, des lignes sans leur facture ne se verraient nulle
     * part et s'accumuleraient en silence.
     */
    @Transaction
    open suspend fun supprimerAvecLignes(factureId: String) {
        effacerLignesDe(factureId)
        effacer(factureId)
    }

    /** Pose une facture et toutes ses lignes en une fois : la création. */
    @Transaction
    open suspend fun creer(facture: Facture, lignes: List<LigneFacture>) {
        enregistrer(facture)
        enregistrerLignes(lignes)
    }
}
