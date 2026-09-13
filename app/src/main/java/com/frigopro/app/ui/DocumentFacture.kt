package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureComplete
import com.frigopro.app.data.Parametres
import java.time.LocalDate

/**
 * Ce que la facture imprimée dit.
 *
 * Le document réutilise toute la chaîne du devis — [DocumentImprime], la
 * pagination, le dessin — et n'en diffère que par trois choses : le titre, les
 * dates, et les **mentions**. Ce sont les mentions qui comptent : sur un devis
 * elles sont commerciales, sur une facture elles sont obligatoires, et leur
 * absence est un manquement dont l'entreprise répond.
 *
 * Quatre sont portées ici, et aucune n'est décorative :
 *
 * - **La date d'échéance.** C'est l'objet de la demande : le client doit lire
 *   jusqu'à quand il a pour payer, et c'est de cette date que part la relance.
 * - **Les pénalités de retard.** Elles sont dues de plein droit dès le
 *   lendemain de l'échéance, sans rappel — mais encore faut-il que la facture
 *   les ait annoncées. Quand aucun taux n'a été fixé, le document renvoie au
 *   taux légal plutôt que d'imprimer « 0 % », qui serait faux et vaudrait
 *   renonciation.
 * - **L'indemnité forfaitaire de recouvrement**, quarante euros, fixée par
 *   l'article D. 441-5 du code de commerce.
 * - **L'absence d'escompte.** Faute de le dire, on s'expose à ce qu'un client
 *   en retienne un pour paiement anticipé.
 */
object DocumentFacture {

    fun de(
        facture: FactureComplete,
        parametres: Parametres,
        client: Client? = null,
        aujourdhui: LocalDate = LocalDate.now(),
    ): DocumentImprime {
        val document = facture.facture
        return DocumentImprime(
            titre = "FACTURE",
            numero = document.numero,
            emetteur = emetteurDe(parametres, "Facture"),
            destinataire = destinataire(document, client),
            dates = dates(document, aujourdhui),
            objet = document.objet,
            machine = document.equipementNom,
            lignes = facture.lignes.map { ligne ->
                LigneImprimee(
                    designation = ligne.designation,
                    quantite = Nombres.enTexte(ligne.quantite) +
                        if (ligne.unite.isBlank()) "" else " ${ligne.unite}",
                    prixUnitaire = Nombres.enEuros(ligne.prixUnitaire),
                    montant = if (ligne.offerte) {
                        "${Nombres.enEuros(ligne.montantAvantGeste)} — offert"
                    } else {
                        Nombres.enEuros(ligne.montant)
                    },
                    offerte = ligne.offerte,
                )
            },
            totaux = totaux(facture),
            mentions = mentions(document, parametres),
            logoFichier = parametres.logoFichier,
            // Une facture n'est exportable qu'émise, donc numérotée : ce nom de
            // repli ne devrait jamais servir, et il existe pour que l'absence de
            // numéro ne produise pas un fichier nommé « .pdf ».
            nomSansNumero = "facture",
        )
    }

    /**
     * Le destinataire.
     *
     * L'adresse vient de la **facture** et non du carnet : elle y a été recopiée
     * à la création, et c'est celle à laquelle le document a été envoyé. Qu'un
     * client déménage ne doit pas réécrire une facture de mars. Le carnet ne sert
     * qu'à compléter ce que la facture ne porte pas — le téléphone.
     */
    private fun destinataire(facture: Facture, client: Client?): List<String> = buildList {
        val nom = facture.clientNom.ifBlank { client?.nom.orEmpty() }
        if (nom.isNotBlank()) add(nom)
        val adresse = facture.clientAdresse.ifBlank { client?.adresseComplete.orEmpty() }
        if (adresse.isNotBlank()) add(adresse)
        client?.telephone?.takeIf { it.isNotBlank() }?.let { add(it) }
    }

    private fun dates(facture: Facture, aujourdhui: LocalDate): List<String> = buildList {
        add("Émise le ${(facture.emiseLe ?: aujourdhui).format(FORMAT_DATE_DOCUMENT)}")
        facture.echeanceLe?.let { add("Échéance le ${it.format(FORMAT_DATE_DOCUMENT)}") }
        facture.payeeLe?.let { add("Payée le ${it.format(FORMAT_DATE_DOCUMENT)}") }
    }

    /** Même bloc que sur le devis : la TVA au taux, puis la remise en dessous. */
    private fun totaux(facture: FactureComplete): List<LigneTotalImprimee> = buildList {
        add(LigneTotalImprimee("Total HT", Nombres.enEuros(facture.totalHt)))
        if (facture.facture.assujettiTva) {
            add(
                LigneTotalImprimee(
                    "TVA ${Nombres.enTexte(facture.facture.tauxTva)} %",
                    Nombres.enEuros(facture.tvaDue),
                ),
            )
            if (facture.facture.tvaOfferte) {
                add(
                    LigneTotalImprimee(
                        "Remise commerciale — TVA offerte",
                        "− ${Nombres.enEuros(facture.remiseTva)}",
                    ),
                )
            }
        }
        add(LigneTotalImprimee("TOTAL À PAYER", Nombres.enEuros(facture.totalTtc), forte = true))
    }

    private fun mentions(facture: Facture, parametres: Parametres): List<String> = buildList {
        if (!facture.assujettiTva) add(Parametres.MENTION_FRANCHISE)

        facture.echeanceLe?.let {
            add("Paiement à échéance du ${it.format(FORMAT_DATE_DOCUMENT)}.")
        }

        // Le taux est celui recopié sur la facture, pas celui des réglages du
        // jour : c'est ce que le document annonçait quand il est parti.
        add(
            if (facture.tauxPenalites > 0.0) {
                "En cas de retard de paiement, pénalités au taux annuel de " +
                    "${Nombres.enTexte(facture.tauxPenalites)} %, exigibles sans rappel."
            } else {
                "En cas de retard de paiement, pénalités au taux d'intérêt appliqué par la " +
                    "Banque centrale européenne à son opération de refinancement la plus " +
                    "récente, majoré de 10 points, exigibles sans rappel."
            },
        )
        add(
            "Indemnité forfaitaire pour frais de recouvrement : " +
                "${Nombres.enEuros(Facture.INDEMNITE_RECOUVREMENT)} " +
                "(art. D. 441-5 du code de commerce).",
        )
        add("Pas d'escompte pour paiement anticipé.")

        if (parametres.entrepriseSiret.isBlank()) {
            // Dit à l'utilisateur, pas au client : une facture sans SIRET n'est
            // pas conforme, et l'export est le dernier moment où l'on peut
            // encore s'en apercevoir.
            add("(SIRET non renseigné dans les Réglages)")
        }
    }
}
