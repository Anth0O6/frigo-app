package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToLong

/** Où en est un devis. */
enum class StatutDevis(val libelle: String) {
    BROUILLON("Brouillon"),
    ENVOYE("Envoyé"),
    ACCEPTE("Accepté"),
    REFUSE("Refusé"),
    ;

    /** Un devis accepté ou refusé ne se modifie plus : il fait foi. */
    val figé: Boolean get() = this == ACCEPTE || this == REFUSE
}

/**
 * Un devis : ce qu'on propose au client, et combien.
 *
 * Il naît souvent sur place, au moment où l'on constate qu'une réparation
 * dépasse le dépannage — le compresseur est mort, il faut le remplacer. C'est
 * pour cela qu'il vit dans l'application de terrain et non au bureau :
 * chiffré et envoyé avant d'être redescendu du toit, il est accepté le jour
 * même plutôt que la semaine suivante.
 *
 * Le nom du client est recopié, comme sur l'intervention et pour la même
 * raison : un devis de mars doit continuer d'afficher le client tel qu'il
 * s'appelait en mars.
 *
 * @param tauxTva taux de TVA en pourcentage, porté par le devis et non par un
 *   réglage global : il change avec la nature des travaux, et un devis déjà
 *   envoyé ne doit pas se recalculer parce qu'un réglage a bougé depuis.
 * @param valableJusquau date de fin de validité, mentionnée sur le document.
 */
@Entity(
    tableName = "devis",
    indices = [Index("clientId"), Index("equipementId")],
)
data class Devis(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val numero: String = "",
    val clientId: String? = null,
    val clientNom: String = "",
    val equipementId: String? = null,
    val equipementNom: String = "",
    val objet: String = "",
    val statut: StatutDevis = StatutDevis.BROUILLON,
    val tauxTva: Double = 20.0,
    val creeLe: LocalDate? = null,
    val valableJusquau: LocalDate? = null,
    val modifieLe: Instant = Instant.EPOCH,
)

/**
 * Une ligne de devis.
 *
 * @param quantite en nombre d'unités : des heures, des kilogrammes, des
 *   pièces. Un décimal, parce que « 6,2 kg » et « 1,5 h » sont des quantités
 *   ordinaires dans ce métier.
 * @param unite ce que compte [quantite] : `h`, `kg`, ou vide pour du dénombré.
 * @param rang position dans le devis. Stocké plutôt que déduit : l'ordre des
 *   lignes est un choix de rédaction, pas une conséquence de la saisie.
 */
@Entity(
    tableName = "lignes_devis",
    indices = [Index("devisId")],
)
data class LigneDevis(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val devisId: String,
    val designation: String,
    val quantite: Double = 1.0,
    val unite: String = "",
    val prixUnitaire: Double = 0.0,
    val rang: Int = 0,
) {

    /** Montant hors taxes de la ligne. */
    val montant: Double get() = quantite * prixUnitaire
}

/**
 * Un devis et ses lignes, tels que l'écran les manipule.
 *
 * Les totaux se calculent ici plutôt qu'en base : ils découlent des lignes, et
 * une valeur stockée finirait par les contredire après une modification. Ils
 * sont **arrondis au centime ligne à ligne puis sommés**, dans cet ordre :
 * sommer des montants non arrondis puis arrondir le total donne un résultat
 * qui ne retombe pas sur l'addition que le client refait à la main, et c'est
 * le genre d'écart d'un centime qui fait rappeler.
 */
data class DevisComplet(
    val devis: Devis,
    val lignes: List<LigneDevis> = emptyList(),
) {

    /** Total hors taxes. */
    val totalHt: Double get() = lignes.sumOf { it.montant.auCentime() }

    /** Montant de la TVA. */
    val tva: Double get() = (totalHt * devis.tauxTva / 100.0).auCentime()

    /** Total toutes taxes comprises. */
    val totalTtc: Double get() = (totalHt + tva).auCentime()
}

/** Arrondit au centime, la seule précision qui ait un sens sur une facture. */
fun Double.auCentime(): Double = (this * 100).roundToLong() / 100.0
