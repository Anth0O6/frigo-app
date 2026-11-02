package com.frigopro.app.data

/**
 * Le passage du HT au TTC et retour, et les sous-totaux d'un devis.
 *
 * **Le document reste en HT**, et c'est ce qui permet de tout le reste : un prix
 * tapé TTC est ramené au HT avant d'être écrit, si bien que la ligne, ses totaux
 * arrondis ligne à ligne, la facture qui la reprend et le PDF ne changent pas de
 * règle. Le TTC n'est qu'une **façon de saisir et de lire**, jamais une seconde
 * valeur stockée qui pourrait cesser de répondre à la première.
 */
object ChiffrageTtc {

    /**
     * Le HT d'un prix pensé TTC, arrondi au centime : 60 € TTC à 20 % font 50 € HT.
     *
     * L'arrondi peut décaler d'un centime le TTC que le devis recalculera — 10,01 €
     * TTC donnent 8,34 € HT, qui refont 10,01 €, mais un HT arrondi multiplié par
     * 1,2 peut retomber un centime à côté. C'est pourquoi l'écran montre le TTC
     * **recalculé** sous la saisie : c'est lui que le client lira.
     */
    fun versHt(ttc: Double, tauxTva: Double): Double = (ttc / (1.0 + tauxTva / 100.0)).auCentime()

    fun versTtc(ht: Double, tauxTva: Double): Double = (ht * (1.0 + tauxTva / 100.0)).auCentime()

    /**
     * Les sous-totaux d'un devis, par famille, dans l'ordre où ils se lisent : ce
     * qu'on pose, ce qu'on fait, ce qu'on parcourt. Une famille vide n'apparaît pas.
     *
     * La TVA de chaque famille est arrondie à part, si bien que leur somme peut
     * différer d'un centime de celle du devis, calculée sur le total : c'est le
     * total qui fait foi, et l'écran le dit.
     */
    fun sousTotaux(lignes: List<LigneDevis>, tauxTva: Double, assujettie: Boolean): List<SousTotal> =
        FamilleLigne.entries.mapNotNull { famille ->
            val siennes = lignes.filter { FamilleLigne.de(it) == famille }
            if (siennes.isEmpty()) return@mapNotNull null
            val ht = siennes.sumOf { it.montant.auCentime() }.auCentime()
            val tva = if (assujettie) (ht * tauxTva / 100.0).auCentime() else 0.0
            SousTotal(famille = famille, ht = ht, tva = tva, ttc = (ht + tva).auCentime())
        }
}

/**
 * La famille d'une ligne de devis, déduite de ce qu'elle porte et non stockée.
 *
 * Une ligne de **matériel** a un prix d'achat — c'est ce que posent le magasin et
 * un devis fournisseur repris —, une ligne de **déplacement** porte son marqueur,
 * et tout le reste est de la **main-d'œuvre ou une prestation**. La contrepartie
 * est assumée : du matériel saisi en ligne libre, sans prix d'achat, compte avec
 * les prestations — l'écran ne peut pas savoir ce qu'il n'a pas reçu.
 */
enum class FamilleLigne(val libelle: String) {
    MATERIEL("Matériel"),
    PRESTATIONS("Main-d'œuvre et prestations"),
    DEPLACEMENT("Déplacement"),
    ;

    companion object {
        fun de(ligne: LigneDevis): FamilleLigne = when {
            ligne.deplacement -> DEPLACEMENT
            ligne.prixAchat > 0.0 -> MATERIEL
            else -> PRESTATIONS
        }
    }
}

data class SousTotal(
    val famille: FamilleLigne,
    val ht: Double,
    val tva: Double,
    val ttc: Double,
)
