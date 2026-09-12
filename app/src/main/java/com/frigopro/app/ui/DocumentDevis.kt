package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.Parametres
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Le devis tel qu'il s'imprime : tout le texte du document, et rien d'autre.
 *
 * Séparé du dessin pour la raison qui vaut partout dans ce projet — ce qui se
 * vérifie sans téléphone doit l'être — et ici la raison est plus forte
 * qu'ailleurs : **ce document part chez un client**. Une mention légale oubliée,
 * une TVA affichée par une entreprise qui n'y est pas assujettie, un total qui ne
 * correspond pas aux lignes au-dessus : ce sont des fautes qu'on découvre après
 * l'envoi, et dont l'entreprise répond.
 */
data class DocumentDevis(
    val titre: String,
    val numero: String,
    /** L'émetteur : l'entreprise, ou à défaut le technicien. */
    val emetteur: List<String>,
    /** Le destinataire : le client, son adresse si on l'a. */
    val destinataire: List<String>,
    val dates: List<String>,
    val objet: String,
    /** La machine concernée, quand le devis en désigne une. */
    val machine: String,
    val lignes: List<LigneImprimee>,
    val totaux: List<LigneTotalImprimee>,
    val mentions: List<String>,
    /** Le fichier du logo, `null` si l'entreprise n'en a pas posé. */
    val logoFichier: String?,
) {

    /** Le nom du fichier PDF, tel que le client le recevra. */
    val nomFichier: String
        get() = (numero.ifBlank { "devis" } + ".pdf").replace(Regex("[^A-Za-z0-9._-]"), "-")

    companion object {

        private val FORMAT_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

        /**
         * Assemble le document à partir de ce que la base porte.
         *
         * Les **mentions légales** sont assemblées ici et pas au dessin : ce sont
         * des obligations, pas des éléments de maquette, et les laisser au canevas
         * les aurait rendues faciles à perdre dans une retouche graphique.
         */
        fun de(
            devis: DevisComplet,
            parametres: Parametres,
            client: Client? = null,
            aujourdhui: LocalDate = LocalDate.now(),
        ): DocumentDevis {
            val document = devis.devis
            return DocumentDevis(
                titre = "DEVIS",
                numero = document.numero,
                emetteur = emetteur(parametres),
                destinataire = destinataire(document, client),
                dates = dates(document, aujourdhui),
                objet = document.objet,
                machine = document.equipementNom,
                lignes = devis.lignes.map { ligne ->
                    LigneImprimee(
                        designation = ligne.designation,
                        quantite = Nombres.enTexte(ligne.quantite) +
                            if (ligne.unite.isBlank()) "" else " ${ligne.unite}",
                        prixUnitaire = Nombres.enEuros(ligne.prixUnitaire),
                        // Une ligne offerte imprime son prix d'origine : le client
                        // doit lire ce qu'on lui a donné, sinon le geste n'est pas
                        // un argument de vente mais une ligne à zéro inexpliquée.
                        montant = if (ligne.offerte) {
                            "${Nombres.enEuros(ligne.montantAvantGeste)} — offert"
                        } else {
                            Nombres.enEuros(ligne.montant)
                        },
                        offerte = ligne.offerte,
                    )
                },
                totaux = totaux(devis),
                mentions = mentions(devis, parametres),
                logoFichier = parametres.logoFichier,
            )
        }

        /**
         * L'émetteur du devis.
         *
         * L'entreprise si elle est renseignée, sinon le technicien : un devis doit
         * dire de qui il vient, et une entreprise non saisie ne doit pas produire un
         * en-tête vide — c'est le cas normal d'un artisan qui n'a pas encore ouvert
         * les Réglages.
         */
        private fun emetteur(parametres: Parametres): List<String> = buildList {
            add(parametres.entreprise.ifBlank { parametres.technicien }.ifBlank { "Devis" })
            if (parametres.entrepriseAdresse.isNotBlank()) add(parametres.entrepriseAdresse)
            val contact = listOf(parametres.entrepriseTelephone, parametres.entrepriseEmail)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (contact.isNotBlank()) add(contact)
            if (parametres.entrepriseSiret.isNotBlank()) {
                add("SIRET ${parametres.entrepriseSiret}")
            }
            if (parametres.attestation.isNotBlank()) {
                // L'attestation de capacité se porte sur les documents : c'est ce
                // qui prouve le droit de manipuler des fluides frigorigènes, et un
                // client professionnel la demande.
                add("Attestation fluides ${parametres.attestation}")
            }
        }

        private fun destinataire(devis: Devis, client: Client?): List<String> = buildList {
            // Le nom recopié sur le devis, pas celui du carnet : un devis de mars
            // doit continuer d'afficher le client tel qu'il s'appelait en mars.
            val nom = devis.clientNom.ifBlank { client?.nom.orEmpty() }
            if (nom.isNotBlank()) add(nom)
            client?.adresse?.takeIf { it.isNotBlank() }?.let { add(it) }
            client?.ville?.takeIf { it.isNotBlank() }?.let { add(it) }
            client?.telephone?.takeIf { it.isNotBlank() }?.let { add(it) }
        }

        private fun dates(devis: Devis, aujourdhui: LocalDate): List<String> = buildList {
            add("Établi le ${(devis.creeLe ?: aujourdhui).format(FORMAT_DATE)}")
            devis.valableJusquau?.let { add("Valable jusqu'au ${it.format(FORMAT_DATE)}") }
        }

        /**
         * Le bloc des totaux.
         *
         * La TVA y figure **au taux du devis**, puis la remise en dessous quand elle
         * est offerte. L'escamoter en affichant un TTC égal au HT donnerait un
         * document faux : la taxe reste due, et c'est une remise commerciale qui la
         * prend en charge. La distinction n'est pas cosmétique, c'est celle que
         * l'administration attend.
         */
        private fun totaux(devis: DevisComplet): List<LigneTotalImprimee> = buildList {
            add(LigneTotalImprimee("Total HT", Nombres.enEuros(devis.totalHt)))
            if (devis.devis.assujettiTva) {
                add(
                    LigneTotalImprimee(
                        "TVA ${Nombres.enTexte(devis.devis.tauxTva)} %",
                        Nombres.enEuros(devis.tvaDue),
                    ),
                )
                if (devis.devis.tvaOfferte) {
                    add(
                        LigneTotalImprimee(
                            "Remise commerciale — TVA offerte",
                            "− ${Nombres.enEuros(devis.remiseTva)}",
                        ),
                    )
                }
            }
            add(LigneTotalImprimee("TOTAL À PAYER", Nombres.enEuros(devis.totalTtc), forte = true))
        }

        /**
         * Les mentions portées au pied du document.
         *
         * La franchise en base impose la mention de l'article 293 B du CGI, et son
         * absence est un manquement ; une entreprise assujettie n'a pas à la porter,
         * et l'y mettre serait tout aussi faux. C'est le **régime du devis** qui
         * tranche et non celui des réglages du jour : un devis établi il y a six
         * mois doit rester ce qu'il était.
         */
        private fun mentions(devis: DevisComplet, parametres: Parametres): List<String> = buildList {
            if (!devis.devis.assujettiTva) add(Parametres.MENTION_FRANCHISE)
            add("Devis gratuit et sans engagement. Bon pour accord, date et signature :")
            if (parametres.entrepriseSiret.isBlank()) {
                // Dit à l'utilisateur, pas au client : un devis sans SIRET
                // n'est pas conforme, et l'export est le dernier moment où on
                // peut encore s'en apercevoir.
                add("(SIRET non renseigné dans les Réglages)")
            }
        }
    }
}
