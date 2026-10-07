package com.frigopro.app.ui

import com.frigopro.app.data.AttestationMaintenance
import com.frigopro.app.data.LigneAttestation
import com.frigopro.app.data.Parametres
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ce que l'attestation d'entretien imprimée dit.
 *
 * Le cinquième document de la chaîne, et le second qui ne chiffre rien — il
 * emprunte donc la mise en page du compte-rendu, des blocs et pas un tableau
 * d'euros, comme le registre des fluides avant lui. Le groupement par machine
 * est exactement ce qu'un bloc sait faire : un titre, des lignes, et une coupure
 * qui reprend le titre quand la page est pleine.
 *
 * Il a, lui, **un destinataire** — c'est tout ce qui le sépare du registre : un
 * registre est tenu par l'entreprise pour elle-même, une attestation se remet au
 * client. C'est la pièce qu'un gérant demande en fin d'année, celle qu'une
 * enseigne réclame à son prestataire, et celle qu'une assurance regarde après un
 * sinistre.
 *
 * Trois règles, et chacune répare une façon de mentir :
 *
 * - **Il n'invente aucune visite.** Une machine suivie mais jamais visitée le dit
 *   en toutes lettres, et c'est précisément le cas où la tentation serait de
 *   taire la ligne. Un parc dont il manque une machine se lit « tout a été
 *   fait », ce qui est exactement le contraire de la vérité.
 * - **Il ne dit pas « conforme ».** Il rapporte des dates. Même règle que la
 *   checklist du compte-rendu : l'application n'a pas qualité à délivrer un
 *   certificat, et une mention de conformité sur un document remis au client
 *   engagerait l'entreprise bien au-delà de ce qu'elle a vu.
 * - **Il ne se signe pas.** Un compte-rendu se signe parce que le client
 *   reconnaît des travaux faits devant lui ; une attestation porte une année de
 *   visites dont il n'a vu que quelques-unes, et lui faire signer un récapitulatif
 *   qu'il ne peut pas vérifier serait lui faire attester le travail de
 *   l'entreprise. C'est l'entreprise qui l'émet, sous sa seule responsabilité.
 */
object DocumentAttestation {

    fun de(
        attestation: AttestationMaintenance,
        parametres: Parametres,
        aujourdhui: LocalDate = LocalDate.now(),
    ): DocumentImprime = DocumentImprime(
        titre = "ATTESTATION D'ENTRETIEN",
        // **Pas de numéro** : ce n'est pas une pièce comptable, et ouvrir une
        // sixième séquence continue à tenir pour un document qu'on réédite à la
        // demande aurait coûté plus qu'il ne rapporte. La période la désigne.
        numero = "",
        emetteur = emetteurDe(parametres, "Attestation d'entretien"),
        destinataire = buildList {
            add(attestation.clientNom)
            if (attestation.clientAdresse.isNotBlank()) add(attestation.clientAdresse)
        },
        dates = listOf(
            "Du ${attestation.debut.format(FORMAT_DATE_DOCUMENT)}",
            "au ${attestation.fin.format(FORMAT_DATE_DOCUMENT)}",
            "Éditée le ${aujourdhui.format(FORMAT_DATE_DOCUMENT)}",
        ),
        objet = "Maintenance préventive du parc frigorifique",
        machine = "",
        blocs = blocs(attestation),
        mentions = mentions(attestation),
        logoFichier = parametres.logoFichier,
        nomSansNumero = nomDeFichier(attestation),
    )

    private fun blocs(attestation: AttestationMaintenance): List<BlocImprime> = buildList {
        add(bilan(attestation))
        // Un bloc par machine, dans l'ordre où le document se parcourt : un
        // gérant cherche la sienne, et la pagination sait reprendre un titre
        // quand une machine à ronde journalière déborde sur la page suivante.
        attestation.machines.forEach { machine ->
            val siennes = attestation.lignes.filter { it.machineNom == machine }
            add(
                BlocImprime(
                    intitule = listOf(machine, siennes.first().machineDesignation)
                        .filter { it.isNotBlank() }
                        .joinToString(" — "),
                    lignes = siennes.flatMap(::lignesDeGamme),
                ),
            )
        }
    }

    /**
     * Le bilan, en tête.
     *
     * Avant le détail, pour la raison qui met celui du registre en tête : c'est
     * le chiffre qu'on demande en premier, et le faire chercher au bout de quatre
     * pages aurait inversé l'ordre de la conversation. **Les deux nombres, puis
     * le taux** — « 23 sur 26 » se vérifie ligne à ligne au-dessous, « 88 % »
     * invite à croire à une précision que ce calcul n'a pas.
     */
    private fun bilan(attestation: AttestationMaintenance): BlocImprime = BlocImprime(
        intitule = "Bilan de la période",
        lignes = if (attestation.vide) {
            listOf(
                LigneBloc(
                    "Aucune machine de ce parc ne suit de gamme de maintenance",
                    "",
                ),
            )
        } else {
            buildList {
                add(
                    LigneBloc(
                        intitule = "Machines suivies",
                        valeur = "${attestation.machines.size}",
                    ),
                )
                add(
                    LigneBloc(
                        intitule = "Visites consignées sur la période",
                        valeur = "${attestation.faites} / ${attestation.attendues} attendues",
                    ),
                )
                val taux = attestation.realisation.tauxPlafonne
                add(
                    LigneBloc(
                        intitule = "Taux de réalisation",
                        // Dit, et non deviné : une période où le plan ne
                        // demandait rien n'a pas un taux de zéro, elle n'en a
                        // pas. Même règle que l'équivalent CO₂ d'un fluide hors
                        // catalogue.
                        valeur = taux?.let { "${Math.round(it * 100)} %" }
                            ?: "aucune visite attendue sur la période",
                    ),
                )
            }
        },
    )

    /**
     * Une gamme d'une machine : le compte, puis les dates.
     *
     * Les dates sont **toutes** là, enveloppées sur plusieurs lignes au besoin :
     * c'est ce qui fait la valeur du document — un client qui conteste pointe une
     * date, et un simple « 12 visites » ne lui répondrait pas. L'enveloppement
     * passe par [MiseEnPageRapport.envelopper], le même budget de caractères que
     * les travaux d'un compte-rendu, parce que la largeur utile est la même.
     *
     * Une machine suivie et **jamais visitée** le dit, plutôt que de n'avoir
     * aucune ligne de dates : une ligne absente se lit « rien à signaler », ce
     * qui n'est pas « rien n'a été fait ». Même règle que le relevé non pris du
     * compte-rendu, dans l'autre sens.
     */
    private fun lignesDeGamme(ligne: LigneAttestation): List<LigneBloc> = buildList {
        add(
            LigneBloc(
                intitule = "${ligne.gammeLibelle} · ${ligne.periodicite.libelle.lowercase()}",
                valeur = "${ligne.visites.size} / ${ligne.attendues}",
            ),
        )
        if (ligne.visites.isEmpty()) {
            add(LigneBloc(intitule = "    aucune visite consignée sur la période"))
        } else {
            val dates = ligne.visites.joinToString(" · ") { it.format(FORMAT_JOUR_ATTESTATION) }
            MiseEnPageRapport.envelopper(dates, CARACTERES_DATES)
                .forEach { add(LigneBloc(intitule = "    $it")) }
        }
    }

    private fun mentions(attestation: AttestationMaintenance): List<String> = buildList {
        add(
            "Cette attestation récapitule les visites de maintenance préventive " +
                "consignées sur la période. Elle rapporte des dates et ne vaut pas " +
                "certificat de conformité des installations.",
        )
        add(
            "Les quantités attendues sont calculées à partir de la cadence de chaque " +
                "gamme et de la date d'entrée de chaque machine au plan.",
        )
        if (attestation.vide) {
            add("Aucune gamme de maintenance n'est rattachée au parc de ce client.")
        } else if (attestation.faites == 0) {
            // La distinction est le point : « pas de contrat » et « contrat non
            // honoré » ne s'écrivent pas de la même façon sur un document qui
            // part chez le client.
            add("Aucune visite n'a été consignée sur cette période.")
        }
    }

    /**
     * « attestation-entretien-boucherie-lemoine-2026 » : le nom du client et
     * l'année, parce que c'est ainsi qu'on le retrouve dans un dossier de
     * téléchargements six mois plus tard. `DocumentImprime.nomFichier` se charge
     * d'en retirer ce qui n'est pas un caractère de nom de fichier.
     */
    private fun nomDeFichier(attestation: AttestationMaintenance): String {
        val client = attestation.clientNom.lowercase().ifBlank { "client" }
        val periode = if (attestation.debut.year == attestation.fin.year) {
            "${attestation.fin.year}"
        } else {
            "${attestation.debut.year}-${attestation.fin.year}"
        }
        return "attestation-entretien-$client-$periode"
    }
}

/** « 14/05/26 » : l'année abrégée, pour en faire tenir douze sur une ligne. */
private val FORMAT_JOUR_ATTESTATION: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yy", Locale.FRENCH)

/**
 * Le budget d'une ligne de dates, retranché de l'indentation qui les décale sous
 * leur gamme. Sous-estimer fait perdre un peu de place à droite ; surestimer
 * ferait déborder le texte hors de la page, où il ne se voit pas du tout.
 */
private const val CARACTERES_DATES = MiseEnPageRapport.CARACTERES_PAR_LIGNE - 6
