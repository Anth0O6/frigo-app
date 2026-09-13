package com.frigopro.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Les réglages de l'application, en une seule ligne.
 *
 * En base plutôt qu'en `DataStore`, pour une raison précise : la sauvegarde
 * est une archive qu'on restaure sur un téléphone neuf, et un technicien qui
 * retrouve ses clients mais pas son taux horaire ni son attestation fluides
 * considérera — à juste titre — que la restauration a échoué. Ce qui est
 * sauvegardé doit l'être entièrement, et ce qui vit dans la base l'est
 * gratuitement.
 *
 * La table n'a qu'une ligne, d'identifiant fixe [UNIQUE]. Une table à une
 * ligne est un peu curieuse, mais elle évite un second mécanisme de
 * persistance, une seconde migration à écrire et un second format à
 * sauvegarder.
 *
 * @param themeSombre `true` par défaut. L'application se lit en chambre
 *   froide, sur un toit et dans un local technique ; un fond blanc y éblouit.
 * @param modeGants agrandit les cibles tactiles. Voir `Cibles` dans le thème.
 * @param chronoAuto démarre le chronomètre dès qu'une intervention passe en
 *   cours, sans attendre un second geste. Désactivé par défaut : démarrer un
 *   chronomètre à l'insu du technicien fausserait le temps facturé, et il vaut
 *   mieux un chrono oublié qu'un chrono faux.
 * @param tauxHoraire taux horaire hors taxes de la main-d'œuvre, repris par
 *   défaut sur une ligne de devis.
 * @param tauxTva taux de TVA proposé à la création d'un devis. Le devis en
 *   garde ensuite sa propre copie.
 * @param attestation mention de l'attestation de capacité fluides, qui figure
 *   sur les comptes-rendus.
 */
@Entity(tableName = "parametres")
data class Parametres(
    @PrimaryKey val id: Int = UNIQUE,
    val technicien: String = "",
    val attestation: String = "",
    val themeSombre: Boolean = true,
    val modeGants: Boolean = false,
    val chronoAuto: Boolean = false,
    val tauxHoraire: Double = 0.0,
    val tauxTva: Double = 20.0,
    /**
     * L'entreprise est assujettie à la TVA.
     *
     * À `false` — franchise en base — les documents ne portent aucune TVA et
     * affichent la mention de l'article 293 B du CGI. Ce n'est pas un taux à
     * zéro : c'est l'absence de TVA, et l'omettre sur une facture est une
     * irrégularité.
     */
    val assujettiTva: Boolean = true,
    /**
     * L'en-tête des documents : ce qui s'imprime en haut du devis.
     *
     * Rien n'est obligatoire pour un devis interne, mais un devis sans raison
     * sociale ni coordonnées n'est pas un devis qu'on envoie — les champs
     * vides sont simplement omis du document plutôt que remplacés par un
     * placeholder.
     */
    val entreprise: String = "",
    val entrepriseAdresse: String = "",
    val entrepriseTelephone: String = "",
    val entrepriseEmail: String = "",
    val entrepriseSiret: String = "",
    /**
     * Le logo, rangé comme une photo : la base n'en porte que le nom.
     *
     * Même raison que pour les photos de machines (voir [StockagePhotos]) : un
     * chemin absolu changerait d'une installation à l'autre, et une sauvegarde
     * restaurée sur un autre téléphone doit retrouver son logo.
     */
    val logoFichier: String? = null,
    /**
     * L'adresse d'où l'on part : le dépôt, l'atelier, le domicile.
     *
     * C'est le point de départ proposé pour tout nouveau trajet. Il reste
     * modifiable trajet par trajet — une tournée ne repart pas toujours du
     * dépôt, et le deuxième client de la journée se rejoint depuis le premier.
     */
    val adresseDepart: String = "",
    val modeDeplacement: ModeDeplacement = ModeDeplacement.KM,
    val prixKm: Double = 0.0,
    /**
     * Le prix d'une heure de **trajet**, distinct de [tauxHoraire].
     *
     * Les séparer n'est pas une subtilité comptable : conduire n'est pas
     * intervenir, et beaucoup facturent la route moins cher que la main-d'œuvre
     * — voire au tarif d'un aide. Les confondre aurait interdit ce choix.
     */
    val prixHeureTrajet: Double = 0.0,
    val minimumDeplacement: Double = 0.0,
    val refacturerPeages: Boolean = true,
    val derniereSauvegardeLe: Instant? = null,
    val modifieLe: Instant = Instant.EPOCH,
) {

    /** Les initiales du technicien, telles que les affiche la pastille des Réglages. */
    val initiales: String get() = if (technicien.isBlank()) "" else initialesDe(technicien)

    /**
     * Y a-t-il de quoi en-têter un document ?
     *
     * Le PDF reste produisible sans — on exporte alors un devis sans en-tête,
     * ce qui vaut mieux que pas de devis du tout — mais l'écran le signale.
     */
    val entreprisePresentable: Boolean get() = entreprise.isNotBlank()

    /** La mention qui remplace la TVA en franchise en base. */
    val mentionTva: String get() = if (assujettiTva) "" else MENTION_FRANCHISE

    /**
     * Le tarif de déplacement, rassemblé pour le calcul.
     *
     * Les champs sont à plat en base — une table à une ligne n'a pas besoin
     * d'être structurée — mais le calcul, lui, reçoit un objet : c'est ce qui
     * permet de l'éprouver sans base et sans réglages.
     */
    val tarifDeplacement: TarifDeplacement
        get() = TarifDeplacement(
            mode = modeDeplacement,
            prixKm = prixKm,
            prixHeure = prixHeureTrajet,
            minimum = minimumDeplacement,
            refacturerPeages = refacturerPeages,
        )

    /**
     * Le calcul automatique est possible.
     *
     * Ce n'est **plus un réglage** : la clé du service vit dans le relais et non
     * sur le téléphone, si bien que l'utilisateur n'a rien à saisir pour en
     * disposer. La propriété reste ici parce que l'écran a besoin de savoir quoi
     * proposer, mais elle ne dépend que de l'application elle-même.
     */
    val itineraireDisponible: Boolean get() = Itineraire.configure

    companion object {

        /** La ligne unique. */
        const val UNIQUE = 1

        /**
         * La mention obligatoire en franchise en base de TVA.
         *
         * Nommée plutôt que recopiée, parce qu'elle paraît à deux endroits —
         * l'écran du devis et le PDF — et que les laisser diverger ferait partir
         * chez un client une formule qui n'est pas celle du code général des
         * impôts. L'omettre est un manquement ; l'écrire autrement aussi.
         */
        const val MENTION_FRANCHISE = "TVA non applicable, art. 293 B du CGI"
    }
}
