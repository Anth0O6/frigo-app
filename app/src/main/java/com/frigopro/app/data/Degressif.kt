package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Le prix d'une prestation **à partir d'un certain rang d'unité**.
 *
 * Le besoin est celui du multi-split, et il se dit en une phrase : entretenir
 * un split coûte 100 €, la deuxième unité 80, la troisième peut-être 70. Ce
 * n'est pas une remise sur le total, c'est un prix par unité qui décroît — le
 * déplacement et la mise en route sont déjà payés par la première.
 *
 * ## Un prix par rang, et non une tranche
 *
 * Deux façons de lire « dégressif » existent, et elles ne donnent pas le même
 * montant :
 *
 * - **par tranche** : trois unités, donc tout à 70 € — soit 210 € ;
 * - **par rang**, celle retenue : 100 + 80 + 70 — soit 250 €.
 *
 * La seconde est celle du métier, et c'est aussi la seule qui ne fasse jamais
 * *baisser* la facture quand on ajoute une unité. Avec des tranches, passer de
 * deux unités (100 + 80 = 180) à trois (3 × 70 = 210) va bien dans le bon sens,
 * mais un jeu de paliers mal réglé peut inverser la marche — et personne ne le
 * remarque avant qu'un client ne le calcule.
 *
 * ## Le rang 1 n'est pas un palier
 *
 * Il est porté par [Prestation.prixUnitaire], qui existait déjà. Un palier
 * `aPartirDe = 1` aurait dupliqué cette valeur, et les deux auraient divergé au
 * premier changement de tarif dans les Réglages.
 */
@Entity(
    tableName = "paliers_prestation",
    indices = [Index("prestationId")],
)
data class PalierPrestation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val prestationId: String,
    /** Le rang d'unité à partir duquel ce prix s'applique. Toujours ≥ 2. */
    val aPartirDe: Int,
    val prixUnitaire: Double = 0.0,
    val modifieLe: Instant = Instant.EPOCH,
)

/**
 * Une tranche d'unités au même prix, telle qu'elle paraît sur le devis.
 *
 * @param depuis rang de la première unité de la tranche, inclus.
 * @param jusqua rang de la dernière, inclus.
 */
data class TrancheDegressive(
    val depuis: Int,
    val jusqua: Int,
    val prixUnitaire: Double,
) {

    val nombre: Int get() = jusqua - depuis + 1

    val montant: Double get() = (nombre * prixUnitaire).auCentime()

    /**
     * Ce que la ligne dit sur le devis.
     *
     * La première tranche ne porte pas de mention — c'est la prestation
     * elle-même. Les suivantes disent **de quel rang à quel rang**, parce que
     * « unité supplémentaire » sur deux lignes différentes ne se distingue pas,
     * et qu'un client qui relit veut retrouver son compte.
     */
    fun mention(): String? = when {
        // La première tranche est la prestation elle-même : rien à préciser.
        depuis == 1 -> null
        // Les tranches suivantes commencent toujours au rang 2 ou plus, d'où le
        // « e » sans cas particulier pour « 1re ».
        nombre == 1 -> "${depuis}e unité"
        else -> "unités $depuis à $jusqua"
    }
}

/**
 * Le tarif d'une prestation et sa dégressivité.
 *
 * Séparé de l'entité et sans dépendance Android, pour que les tests puissent
 * l'exercer — même motif que [FriseHoraire] et [ReductionPhoto].
 */
data class TarifDegressif(
    /** Le prix du rang 1, celui de la prestation. */
    val base: Double,
    val paliers: List<PalierPrestation> = emptyList(),
) {

    /**
     * Les paliers utilisables : rang ≥ 2, triés, un seul par rang.
     *
     * Un doublon de rang n'est pas une erreur de saisie à refuser mais une
     * ambiguïté à trancher, et c'est **le dernier enregistré qui gagne** : c'est
     * celui que l'utilisateur vient de poser.
     */
    private val retenus: List<PalierPrestation>
        get() = paliers.filter { it.aPartirDe >= 2 }
            .groupBy { it.aPartirDe }
            .map { (_, doublons) -> doublons.maxBy { it.modifieLe } }
            .sortedBy { it.aPartirDe }

    /** Il y a quelque chose à dégresser. */
    val degressif: Boolean get() = retenus.isNotEmpty()

    /** Le prix de la n-ième unité. */
    fun prixDuRang(rang: Int): Double {
        if (rang <= 1) return base
        return retenus.lastOrNull { it.aPartirDe <= rang }?.prixUnitaire ?: base
    }

    /** Ce que coûtent `quantite` unités, rang par rang. */
    fun total(quantite: Int): Double =
        (1..quantite.coerceAtLeast(0)).sumOf { prixDuRang(it) }.auCentime()

    /**
     * Le découpage en tranches, tel qu'il paraît sur le devis.
     *
     * Les rangs consécutifs au même prix sont **regroupés** : sans cela, poser
     * un six-split produirait six lignes dont cinq identiques, et un devis qu'on
     * ne relit plus.
     */
    fun tranches(quantite: Int): List<TrancheDegressive> {
        val n = quantite.coerceAtLeast(0)
        if (n == 0) return emptyList()

        val tranches = mutableListOf<TrancheDegressive>()
        var debut = 1
        for (rang in 1..n) {
            val suivantDiffere = rang == n || prixDuRang(rang + 1) != prixDuRang(rang)
            if (suivantDiffere) {
                tranches += TrancheDegressive(debut, rang, prixDuRang(rang))
                debut = rang + 1
            }
        }
        return tranches
    }
}

/**
 * Ce qu'une prestation dégressive devient sur un devis.
 *
 * **Des lignes de devis ordinaires**, et c'est la même décision que pour le
 * déplacement : tout ce qui existe fonctionne alors sans retouche — le
 * `GROUP BY` des totaux, la TVA, la pagination du PDF, et « offrir » qui est
 * déjà porté par `LigneDevis.offerte`. Un second chemin vers le total aurait
 * demandé de toucher sept endroits pour dire la même chose.
 *
 * Et c'est aussi ce qui rend la dégression **visible pour le client** : il lit
 * « 1 u à 100 € » puis « unités 2 à 3 à 80 € » et voit le geste. Un montant
 * unique moyenné l'aurait caché, ce qui est exactement ce que le projet refuse
 * pour une ligne offerte — une remise qu'on ne voit pas n'est pas un argument de
 * vente.
 */
object LignesDegressives {

    fun pour(
        devisId: String,
        prestation: Prestation,
        tarif: TarifDegressif,
        quantite: Int,
        rangDepart: Int = 0,
    ): List<LigneDevis> {
        val tranches = tarif.tranches(quantite)
        return tranches.mapIndexed { index, tranche ->
            val mention = tranche.mention()
            LigneDevis(
                devisId = devisId,
                designation = listOfNotNull(prestation.designation, mention)
                    .joinToString(" — "),
                quantite = tranche.nombre.toDouble(),
                unite = prestation.unite,
                prixUnitaire = tranche.prixUnitaire,
                rang = rangDepart + index,
            )
        }
    }
}
