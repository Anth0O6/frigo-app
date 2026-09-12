package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Comment se facture un déplacement.
 *
 * Les trois cas existent réellement sur le terrain et ne se ramènent pas l'un à
 * l'autre : le kilomètre paie le véhicule, l'heure paie le chauffeur. Trente
 * kilomètres d'autoroute et trente kilomètres dans Paris coûtent le même
 * carburant et pas du tout le même temps, et c'est pourquoi [KM_ET_HEURE] n'est
 * pas une redondance mais le cas le plus juste des trois.
 */
enum class ModeDeplacement(val libelle: String) {
    KM("Au kilomètre"),
    HEURE("À l'heure"),
    KM_ET_HEURE("Au kilomètre et à l'heure"),
    ;

    val compteLesKm: Boolean get() = this != HEURE
    val compteLeTemps: Boolean get() = this != KM
}

/**
 * D'où viennent les chiffres d'un trajet.
 *
 * La distinction est portée en base et affichée, parce qu'elle change ce qu'on
 * peut en dire : un trajet [CALCULE] tient d'un service de routage interrogé à
 * une date, un trajet [SAISI] de ce que le technicien a lu sur son compteur.
 * Les confondre serait présenter une estimation comme un relevé.
 */
enum class OrigineTrajet(val libelle: String) {
    CALCULE("Calculé"),
    SAISI("Saisi à la main"),
}

/**
 * Le tarif de déplacement, tel que le calcul le reçoit.
 *
 * Ce n'est pas une entité : ces valeurs vivent sur la ligne unique de
 * [Parametres], et les rassembler ici n'a qu'un but — que le calcul soit une
 * fonction de ses arguments, éprouvable sans base ni téléphone.
 *
 * @param minimum prix plancher du déplacement. À zéro, il n'y a pas de
 *   plancher. Il s'applique au **trajet seul** : les péages sont des débours,
 *   avancés puis refacturés, et les fondre dans un minimum reviendrait à les
 *   faire disparaître sur les courtes distances.
 */
data class TarifDeplacement(
    val mode: ModeDeplacement = ModeDeplacement.KM,
    val prixKm: Double = 0.0,
    val prixHeure: Double = 0.0,
    val minimum: Double = 0.0,
    val refacturerPeages: Boolean = true,
) {

    /** Un tarif qu'on peut appliquer : sans prix, le calcul ne dirait rien. */
    val renseigne: Boolean
        get() = (!mode.compteLesKm || prixKm > 0.0) && (!mode.compteLeTemps || prixHeure > 0.0)
}

/**
 * Un déplacement chez un client, rattaché au devis qui le facture.
 *
 * **Les chiffres portés ici sont ceux de l'aller**, tels que le service de
 * routage les a rendus ou tels qu'on les a saisis ; [allerRetour] est une
 * décision de facturation, appliquée au moment du calcul. Stocker la distance
 * déjà doublée aurait figé cette décision : cocher la case après coup aurait
 * alors laissé un chiffre faux, sans que rien ne le signale.
 *
 * Le résultat d'un calcul est **stocké et non recalculé à l'affichage**. Ce
 * n'est pas une optimisation : un itinéraire est un fait daté venu de
 * l'extérieur, et un devis de mars doit rester chiffrable en février suivant,
 * sans réseau et sans que le prix ait bougé parce qu'une autoroute a ouvert.
 * C'est l'inverse des échéances F-Gas, qui se recalculent parce qu'elles
 * découlent de la date du dernier contrôle et de rien d'autre.
 *
 * @param peages le péage d'un **sens**, doublé avec [allerRetour] comme le
 *   reste : les barrières se paient dans les deux sens.
 * @param peagesConnus le service a répondu sur les péages. Un zéro ne suffit
 *   pas à le dire : « pas de péage sur ce trajet » et « je n'en sais rien » se
 *   ressemblent dans une colonne de chiffres et ne se ressemblent pas du tout
 *   sur un devis.
 * @param calculeLe quand le service a répondu. `null` pour un trajet saisi.
 */
@Entity(
    tableName = "trajets",
    indices = [Index("devisId")],
)
data class Trajet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val devisId: String,
    val depart: String = "",
    val arrivee: String = "",
    val distanceKm: Double = 0.0,
    val dureeMinutes: Int = 0,
    val peages: Double = 0.0,
    val peagesConnus: Boolean = false,
    val allerRetour: Boolean = false,
    /**
     * Le déplacement est **offert**.
     *
     * Même mécanique qu'une ligne de devis offerte, et pour la même raison : le
     * montant reste calculé et s'affiche barré. Remettre les chiffres à zéro
     * aurait effacé l'argument de vente et interdit de reprendre le geste sans
     * ressaisir l'adresse.
     */
    val offert: Boolean = false,
    val origine: OrigineTrajet = OrigineTrajet.SAISI,
    val calculeLe: Instant? = null,
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Le facteur appliqué à tout ce qui se compte : un aller, ou deux. */
    val facteur: Int get() = if (allerRetour) 2 else 1

    /** Y a-t-il de quoi facturer ? Un trajet vide ne produit aucune ligne. */
    val renseigne: Boolean get() = distanceKm > 0.0 || dureeMinutes > 0
}

/**
 * Ce que coûte un déplacement, poste par poste.
 *
 * Le détail est conservé et pas seulement le total, parce que c'est lui qui
 * s'affiche et qui s'imprime : « 24 km à 0,45 € » se discute avec un client,
 * « 10,80 € » ne se discute pas. [minimumApplique] est là pour la même raison —
 * un montant qui ne correspond pas à la distance a besoin de dire pourquoi.
 */
data class MontantDeplacement(
    val distanceFacturee: Double,
    val heuresFacturees: Double,
    val montantKm: Double,
    val montantHeure: Double,
    val montantTrajet: Double,
    val minimumApplique: Boolean,
    val peages: Double,
    val total: Double,
) {

    /** Le total avant geste commercial : c'est ce qui se barre sur le document. */
    val totalAvantGeste: Double get() = (montantTrajet + peages).auCentime()
}

/**
 * L'arithmétique du déplacement.
 *
 * Les montants sont **arrondis poste par poste puis sommés**, dans cet ordre et
 * pour la raison déjà donnée dans [DevisComplet] : sommer des montants non
 * arrondis puis arrondir le total donne un résultat qui ne retombe pas sur
 * l'addition refaite à la main, et c'est l'écart d'un centime qui fait
 * rappeler.
 *
 * **Les quantités sont arrondies avant de servir**, et c'est le même souci
 * poussé d'un cran : la quantité est imprimée sur le devis à deux décimales, si
 * bien qu'un montant calculé sur la valeur exacte ne retomberait pas sur la
 * ligne que le client a sous les yeux. Trente-cinq minutes aller-retour font
 * 1,1666… h ; la ligne annonce « 1,17 h » et doit valoir 1,17 × le tarif, sinon
 * elle se contredit toute seule. On facture donc douze secondes de plus, ce qui
 * est sans conséquence, là où un devis qui ne s'additionne pas fait rappeler.
 */
object CalculDeplacement {

    fun montant(trajet: Trajet, tarif: TarifDeplacement): MontantDeplacement {
        val facteur = trajet.facteur
        val distance = (trajet.distanceKm * facteur).auCentime()
        val heures = (trajet.dureeMinutes.toDouble() * facteur / 60.0).auCentime()

        val montantKm = if (tarif.mode.compteLesKm) (distance * tarif.prixKm).auCentime() else 0.0
        val montantHeure = if (tarif.mode.compteLeTemps) (heures * tarif.prixHeure).auCentime() else 0.0
        val brut = (montantKm + montantHeure).auCentime()

        // Le plancher ne s'applique qu'au trajet, jamais aux péages : voir
        // [TarifDeplacement.minimum].
        val montantTrajet = maxOf(brut, tarif.minimum).auCentime()
        val peages = if (tarif.refacturerPeages) (trajet.peages * facteur).auCentime() else 0.0

        return MontantDeplacement(
            distanceFacturee = distance,
            heuresFacturees = heures,
            montantKm = montantKm,
            montantHeure = montantHeure,
            montantTrajet = montantTrajet,
            minimumApplique = montantTrajet > brut,
            peages = peages,
            total = if (trajet.offert) 0.0 else (montantTrajet + peages).auCentime(),
        )
    }
}

/**
 * Le déplacement, traduit en lignes de devis.
 *
 * C'est le choix de conception central de la facturation du déplacement : le
 * trajet ne s'ajoute pas au total par un chemin parallèle, il **devient des
 * lignes ordinaires**. Tout ce qui existe déjà fonctionne alors sans retouche —
 * le `GROUP BY` des totaux, la TVA, la pagination du PDF, et « offrir » qui est
 * déjà porté par [LigneDevis.offerte] et s'affiche barré. Un second mécanisme de
 * total aurait demandé de toucher sept endroits, chacun avec ses tests, pour
 * dire la même chose.
 *
 * La contrepartie est assumée : [Trajet] garde les données d'entrée — les
 * adresses, la distance, la durée — et les lignes en sont l'expression
 * commerciale, refaite quand le trajet change. Une retouche à la main sur une
 * ligne de déplacement est donc perdue au recalcul suivant, et c'est pourquoi le
 * recalcul ne se déclenche jamais tout seul.
 */
object LignesDeplacement {

    fun pour(
        trajet: Trajet,
        tarif: TarifDeplacement,
        rangDepart: Int = 0,
    ): List<LigneDevis> {
        if (!trajet.renseigne) return emptyList()
        val calcul = CalculDeplacement.montant(trajet, tarif)
        val suffixe = if (trajet.allerRetour) " (aller-retour)" else ""
        val lignes = mutableListOf<LigneDevis>()

        fun ajouter(designation: String, quantite: Double, unite: String, prix: Double) {
            lignes += LigneDevis(
                devisId = trajet.devisId,
                designation = designation,
                quantite = quantite,
                unite = unite,
                prixUnitaire = prix,
                offerte = trajet.offert,
                deplacement = true,
                rang = rangDepart + lignes.size,
            )
        }

        if (calcul.minimumApplique) {
            // Le plancher ne se dit pas en kilomètres : l'annoncer « 3 km à 8,33 € »
            // serait un tarif inventé pour justifier le montant. Une ligne de
            // forfait dit ce qui est, et le détail du trajet reste sur l'écran.
            ajouter("Déplacement, forfait minimum$suffixe", 1.0, "forfait", calcul.montantTrajet)
        } else {
            if (calcul.montantKm > 0.0) {
                ajouter("Déplacement$suffixe", calcul.distanceFacturee, "km", tarif.prixKm)
            }
            if (calcul.montantHeure > 0.0) {
                ajouter("Temps de trajet$suffixe", calcul.heuresFacturees, "h", tarif.prixHeure)
            }
        }

        // Les péages sont des débours : une ligne à part, parce qu'ils ne sont
        // pas une prestation et qu'un client les reconnaît pour les avoir payés
        // lui-même sur la même autoroute.
        if (calcul.peages > 0.0) {
            ajouter("Péages$suffixe", 1.0, "forfait", calcul.peages)
        }
        return lignes
    }
}
