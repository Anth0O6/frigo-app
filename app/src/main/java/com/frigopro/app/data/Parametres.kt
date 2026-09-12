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
    val mentionTva: String
        get() = if (assujettiTva) "" else "TVA non applicable, art. 293 B du CGI"

    companion object {

        /** La ligne unique. */
        const val UNIQUE = 1
    }
}
