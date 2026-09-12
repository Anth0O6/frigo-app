package com.frigopro.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès SQL aux devis et à leurs lignes.
 *
 * Même raison que partout ailleurs de les réunir : supprimer un devis doit
 * emporter ses lignes, et un devis sans lignes ou des lignes sans devis n'ont
 * aucun sens.
 */
@Dao
abstract class DevisDao {

    /** Les devis, du plus récent au plus ancien. */
    @Query("SELECT * FROM devis ORDER BY creeLe DESC, modifieLe DESC")
    abstract fun observerTous(): Flow<List<Devis>>

    @Query("SELECT * FROM devis WHERE clientId = :clientId ORDER BY creeLe DESC")
    abstract fun observerDuClient(clientId: String): Flow<List<Devis>>

    @Query("SELECT * FROM devis WHERE id = :id")
    abstract fun observer(id: String): Flow<Devis?>

    @Query("SELECT * FROM devis")
    abstract suspend fun tous(): List<Devis>

    @Upsert
    abstract suspend fun enregistrer(devis: Devis)

    @Upsert
    abstract suspend fun enregistrerTous(devis: List<Devis>)

    @Query("SELECT * FROM lignes_devis WHERE devisId = :devisId ORDER BY rang ASC")
    abstract fun observerLignes(devisId: String): Flow<List<LigneDevis>>

    @Query("SELECT * FROM lignes_devis")
    abstract suspend fun toutesLesLignes(): List<LigneDevis>

    /**
     * Le montant hors taxes de chaque devis, en une requête.
     *
     * Les compteurs de l'accueil et de l'onglet Devis — « en attente »,
     * « acceptés », « en cours » — ont tous besoin du total d'un devis. Les
     * calculer en relisant les lignes devis par devis multiplierait les
     * requêtes par le nombre de devis ; un `GROUP BY` les donne toutes d'un
     * coup, et c'est le genre de somme que SQLite fait mieux que Kotlin.
     *
     * Un devis sans ligne n'apparaît pas dans le résultat, ce qui est exact :
     * son total est zéro, et l'appelant le traite comme absent.
     *
     * Le `CASE` écarte les lignes offertes. Sans lui, la liste annoncerait un
     * montant que le client ne paiera pas — et l'écart serait invisible, puisque
     * le détail du devis, lui, compterait juste.
     */
    @Query(
        "SELECT devisId, SUM(CASE WHEN offerte = 0 THEN quantite * prixUnitaire ELSE 0 END) " +
            "AS montant FROM lignes_devis GROUP BY devisId",
    )
    abstract fun observerTotaux(): Flow<List<TotalDevis>>

    /** Le rang libre suivant, pour poser une ligne à la fin. */
    @Query("SELECT COALESCE(MAX(rang), -1) + 1 FROM lignes_devis WHERE devisId = :devisId")
    abstract suspend fun prochainRang(devisId: String): Int

    @Upsert
    abstract suspend fun enregistrerLigne(ligne: LigneDevis)

    @Upsert
    abstract suspend fun enregistrerLignes(lignes: List<LigneDevis>)

    @Query("DELETE FROM lignes_devis WHERE id = :id")
    abstract suspend fun effacerLigne(id: String)

    @Query("DELETE FROM lignes_devis WHERE devisId = :devisId")
    abstract suspend fun effacerLignesDe(devisId: String)

    @Query("DELETE FROM devis WHERE id = :id")
    abstract suspend fun effacer(id: String)

    // — Le déplacement facturé —

    /**
     * Le trajet d'un devis, s'il en a un.
     *
     * Un devis n'en porte qu'un : c'est un aller chez un client, pas une
     * tournée. Le jour où un devis couvrirait plusieurs sites, la table les
     * accepterait déjà — `devisId` n'est pas unique — et seul l'écran aurait à
     * apprendre à les montrer.
     */
    @Query("SELECT * FROM trajets WHERE devisId = :devisId LIMIT 1")
    abstract fun observerTrajet(devisId: String): Flow<Trajet?>

    @Query("SELECT * FROM trajets")
    abstract suspend fun tousLesTrajets(): List<Trajet>

    @Upsert
    abstract suspend fun enregistrerTrajet(trajet: Trajet)

    @Upsert
    abstract suspend fun enregistrerTrajets(trajets: List<Trajet>)

    @Query("DELETE FROM trajets WHERE devisId = :devisId")
    abstract suspend fun effacerTrajetDe(devisId: String)

    @Query("DELETE FROM lignes_devis WHERE devisId = :devisId AND deplacement = 1")
    abstract suspend fun effacerLignesDeplacementDe(devisId: String)

    /**
     * Pose le trajet et refait *ses* lignes, d'un bloc.
     *
     * Les deux écritures ne peuvent pas se séparer : un trajet enregistré sans
     * ses lignes ne serait pas facturé, des lignes sans leur trajet ne seraient
     * plus modifiables. Le marqueur `deplacement` est ce qui permet de remplacer
     * les unes sans toucher aux autres — voir [LigneDevis.deplacement].
     *
     * Les lignes du déplacement sont **reposées à la fin** du devis, et c'est
     * voulu : un déplacement se lit en bas d'un devis, après ce qu'on est venu
     * faire.
     */
    @Transaction
    open suspend fun enregistrerDeplacement(trajet: Trajet, lignes: List<LigneDevis>) {
        effacerTrajetDe(trajet.devisId)
        effacerLignesDeplacementDe(trajet.devisId)
        enregistrerTrajet(trajet)
        if (lignes.isNotEmpty()) {
            val rang = prochainRang(trajet.devisId)
            enregistrerLignes(lignes.mapIndexed { index, ligne -> ligne.copy(rang = rang + index) })
        }
    }

    /** Retire le déplacement d'un devis : le trajet et les lignes qu'il portait. */
    @Transaction
    open suspend fun supprimerDeplacement(devisId: String) {
        effacerTrajetDe(devisId)
        effacerLignesDeplacementDe(devisId)
    }

    @Transaction
    open suspend fun supprimer(id: String) {
        effacerLignesDe(id)
        effacerTrajetDe(id)
        effacer(id)
    }
}

/** Accès à la ligne unique des réglages. */
@Dao
interface ParametresDao {

    @Query("SELECT * FROM parametres WHERE id = ${Parametres.UNIQUE}")
    fun observer(): Flow<Parametres?>

    @Query("SELECT * FROM parametres WHERE id = ${Parametres.UNIQUE}")
    suspend fun lire(): Parametres?

    @Upsert
    suspend fun enregistrer(parametres: Parametres)
}
