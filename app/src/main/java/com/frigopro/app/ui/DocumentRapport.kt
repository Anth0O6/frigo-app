package com.frigopro.app.ui

import com.frigopro.app.data.Client
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.PointChecklist
import com.frigopro.app.data.Releve
import com.frigopro.app.data.enDuree
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ce que le compte-rendu imprimé dit.
 *
 * C'est le troisième document de la chaîne, après le devis et la facture, et il
 * en réutilise tout ce qui est commun : l'en-tête, le pied, le nom du fichier,
 * le producteur de PDF. Il en diffère sur un point, et un seul : **il ne chiffre
 * rien**. Un devis et une facture sont des tableaux à quatre colonnes dont trois
 * portent des euros ; un compte-rendu est une suite de blocs — ce qu'on a relevé,
 * ce qu'on a mis, ce qu'on a posé, ce qu'on a fait. Les forcer dans le tableau du
 * devis aurait imprimé trois colonnes vides sur un document qui ne parle pas
 * d'argent, et les faire vivre dans une seconde chaîne d'impression aurait fait
 * diverger les deux en-têtes au premier ajustement de maquette.
 *
 * ## Ce qu'il ne fait pas, et pourquoi
 *
 * **Il n'invente rien.** Un relevé non pris n'apparaît pas, une checklist non
 * posée non plus, et le bloc disparaît entièrement plutôt que d'annoncer des
 * tirets : une case vide sur un document signé se lit comme « rien à signaler »,
 * ce qui n'est pas la même chose que « pas mesuré ». Même règle que le GWP d'un
 * fluide hors catalogue.
 *
 * **Il n'affiche aucun prix, ni aucune marge.** Ce que l'intervention a coûté
 * regarde l'entreprise et personne d'autre ; c'est la facture qui porte ce que le
 * client doit. Un compte-rendu qui laisserait filtrer un coût d'achat serait le
 * genre de fuite qu'on ne remarque qu'une fois le document parti.
 *
 * **Il ne dit pas « conforme ».** Les points cochés sont rapportés tels quels,
 * et un point non fait se voit : c'est un relevé de ce qui a été vérifié, pas un
 * certificat, et l'application n'a pas qualité à en délivrer un.
 */
object DocumentRapport {

    /**
     * La mention que le client signe.
     *
     * Elle porte sur les **travaux décrits**, pas sur un montant : un
     * compte-rendu n'engage aucun paiement, et lui faire dire « bon pour
     * accord » — la formule du devis — ferait signer autre chose que ce qui est
     * écrit au-dessus.
     */
    const val MENTION_SIGNATURE =
        "Le client reconnaît que les travaux décrits ci-dessus ont été réalisés."

    fun de(
        etat: EtatIntervention,
        ecoule: Duration,
        aujourdhui: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): DocumentImprime {
        val intervention = etat.intervention
        return DocumentImprime(
            titre = "COMPTE-RENDU",
            numero = intervention.numero,
            emetteur = emetteurDe(etat.parametres, "Compte-rendu"),
            destinataire = destinataire(intervention, etat.client),
            dates = dates(intervention, ecoule, aujourdhui, zone),
            objet = intervention.typeLibelle,
            machine = machine(etat),
            blocs = blocs(etat, ecoule, zone),
            mentions = mentions(etat, aujourdhui),
            signatureFichier = intervention.signatureFichier,
            mentionSignature = MENTION_SIGNATURE,
            logoFichier = etat.parametres.logoFichier,
            // Le numéro n'est attribué qu'à la clôture, et le compte-rendu
            // s'exporte avant : un technicien qui fait signer sur place n'a pas
            // encore clos l'intervention. Le fichier a donc besoin d'un nom.
            nomSansNumero = "compte-rendu",
        )
    }

    /**
     * Le destinataire : celui chez qui on est allé.
     *
     * Et non celui à qui la facture part. C'est la différence avec la facture, et
     * elle compte en sous-traitance : le compte-rendu se signe **sur place**, par
     * la personne qui a vu les travaux, pas par le donneur d'ordre qui paie et
     * qui n'était pas là.
     */
    private fun destinataire(intervention: Intervention, client: Client?): List<String> = buildList {
        val nom = client?.nom.orEmpty().ifBlank { intervention.client }
        if (nom.isNotBlank()) add(nom)
        val adresse = client?.adresseComplete.orEmpty().ifBlank { intervention.ville }
        if (adresse.isNotBlank()) add(adresse)
        client?.telephone?.takeIf { it.isNotBlank() }?.let { add(it) }
    }

    private fun dates(
        intervention: Intervention,
        ecoule: Duration,
        aujourdhui: LocalDate,
        zone: ZoneId,
    ): List<String> = buildList {
        add("Intervention du ${intervention.date.format(FORMAT_DATE_DOCUMENT)}")
        // L'heure d'arrivée est celle du chronomètre quand il a tourné, et le
        // créneau prévu sinon : ce qui s'est passé prime sur ce qui était prévu,
        // mais un créneau reste plus utile qu'un blanc.
        val arrivee = intervention.chrono.arriveeLe?.atZone(zone)?.toLocalTime()
        add("Arrivée ${(arrivee ?: intervention.heure).format(FORMAT_HEURE)}")
        if (!ecoule.isZero) add("Temps passé ${ecoule.enDuree()}")
        if (intervention.date != aujourdhui) {
            add("Établi le ${aujourdhui.format(FORMAT_DATE_DOCUMENT)}")
        }
    }

    /** La machine, et son fluide : les deux se lisent ensemble sur un rapport. */
    private fun machine(etat: EtatIntervention): String = listOf(
        etat.intervention.equipementNom.ifBlank { etat.equipement?.nom.orEmpty() },
        etat.fluide,
    ).filter { it.isNotBlank() }.joinToString(" — ")

    private fun blocs(etat: EtatIntervention, ecoule: Duration, zone: ZoneId): List<BlocImprime> =
        listOf(
            blocIntervention(etat, ecoule, zone),
            blocReleves(etat.releve),
            blocFluide(etat),
            blocPieces(etat),
            blocChecklist(etat.checklist),
            blocTravaux(etat.intervention.notes),
        ).filter { it.lignes.isNotEmpty() }

    private fun blocIntervention(
        etat: EtatIntervention,
        ecoule: Duration,
        zone: ZoneId,
    ): BlocImprime {
        val intervention = etat.intervention
        return BlocImprime(
            intitule = "L'intervention",
            lignes = buildList {
                if (intervention.typeLibelle.isNotBlank()) {
                    add(LigneBloc("Nature", intervention.typeLibelle))
                }
                if (intervention.technicienNom.isNotBlank()) {
                    add(LigneBloc("Technicien", intervention.technicienNom))
                }
                val arrivee = intervention.chrono.arriveeLe?.atZone(zone)?.toLocalTime()
                if (arrivee != null) add(LigneBloc("Arrivée", arrivee.format(FORMAT_HEURE)))
                if (!ecoule.isZero) add(LigneBloc("Temps passé", ecoule.enDuree()))
                // La sous-traitance est dite au client : il a le droit de savoir
                // qui facture, et de qui l'entreprise qu'il a appelée répond.
                if (intervention.sousTraitance) {
                    add(LigneBloc("Pour le compte de", intervention.clientFactureNom))
                }
            },
        )
    }

    /**
     * Les relevés, et seulement ceux qui ont été pris.
     *
     * Les unités sont écrites en toutes lettres à côté de chaque valeur, et les
     * pressions dites **relatives** comme sur un manomètre : un « 4,2 » nu sur un
     * document d'archive ne se relit pas trois ans plus tard, et l'écart entre
     * relatif et absolu vaut 1,013 bar — près de 7 K sur un R-410A en basse
     * pression.
     */
    private fun blocReleves(releve: Releve?): BlocImprime = BlocImprime(
        intitule = "Relevés frigorifiques",
        lignes = buildList {
            if (releve == null) return@buildList
            releve.bpBar?.let { add(LigneBloc("Basse pression", "${Nombres.enTexte(it)} bar rel.")) }
            releve.hpBar?.let { add(LigneBloc("Haute pression", "${Nombres.enTexte(it)} bar rel.")) }
            releve.surchauffeK?.let { add(LigneBloc("Surchauffe", "${Nombres.enTexte(it)} K")) }
            releve.sousRefroidissementK?.let {
                add(LigneBloc("Sous-refroidissement", "${Nombres.enTexte(it)} K"))
            }
        },
    )

    /**
     * Le fluide, mouvement par mouvement.
     *
     * Chaque ligne est détaillée plutôt que résumée en un total, parce que c'est
     * ce que le registre demande : « 2 kg ajoutés » et « 3 kg ajoutés puis 1 kg
     * récupéré » ne racontent pas la même intervention, et c'est la seconde qu'un
     * contrôle veut lire. Les totaux suivent quand même, pour la lecture.
     */
    private fun blocFluide(etat: EtatIntervention): BlocImprime = BlocImprime(
        intitule = "Fluide frigorigène",
        lignes = buildList {
            etat.mouvements.forEach { mouvement ->
                add(
                    LigneBloc(
                        intitule = listOf(mouvement.sens.libelle, mouvement.fluide)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        valeur = "${Nombres.enMasse(mouvement.masseKg)} kg",
                    ),
                )
            }
            if (etat.mouvements.size > 1) {
                if (etat.ajoute > 0.0) {
                    add(LigneBloc("Total ajouté", "${Nombres.enMasse(etat.ajoute)} kg"))
                }
                if (etat.recupere > 0.0) {
                    add(LigneBloc("Total récupéré", "${Nombres.enMasse(etat.recupere)} kg"))
                }
            }
        },
    )

    /**
     * Les pièces posées, sans leur prix.
     *
     * La référence est écrite à côté de la désignation : c'est ce qu'un autre
     * technicien recherchera le jour où la même pièce lâche, et le compte-rendu
     * est alors la seule trace qu'il ait.
     */
    private fun blocPieces(etat: EtatIntervention): BlocImprime = BlocImprime(
        intitule = "Pièces posées",
        lignes = etat.pieces.map { piece ->
            LigneBloc(
                intitule = listOf(piece.designation, piece.reference)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                valeur = "× ${Nombres.enTexte(piece.quantite)}",
            )
        },
    )

    /** Les points vérifiés, et ceux qui ne l'ont pas été — dits comme tels. */
    private fun blocChecklist(checklist: List<PointChecklist>): BlocImprime = BlocImprime(
        intitule = "Points vérifiés",
        lignes = checklist.map { point ->
            LigneBloc(point.libelle, if (point.fait) "Vérifié" else "Non fait")
        },
    )

    private fun blocTravaux(notes: String): BlocImprime = BlocImprime(
        intitule = "Travaux réalisés",
        lignes = MiseEnPageRapport.envelopper(notes).map { LigneBloc(it) },
    )

    /**
     * Les mentions.
     *
     * Aucune n'est obligatoire — ce n'est pas une facture —, mais deux ont une
     * portée réelle. Le renvoi au **registre des fluides** rappelle où la trace
     * réglementaire est tenue, et l'absence de numéro est dite en clair : un
     * compte-rendu signé sans référence est difficile à rapprocher de sa facture,
     * et mieux vaut que le technicien le voie avant de le faire signer.
     */
    private fun mentions(etat: EtatIntervention, aujourdhui: LocalDate): List<String> = buildList {
        if (etat.mouvements.isNotEmpty()) {
            add(
                "Les mouvements de fluide ci-dessus sont consignés au registre prévu par le " +
                    "règlement (UE) 517/2014.",
            )
        }
        if (etat.parametres.attestation.isBlank() && etat.mouvements.isNotEmpty()) {
            // Dit à l'utilisateur, pas au client : manipuler du fluide sans
            // attestation renseignée sur le document est un manque qui se voit
            // au contrôle, et l'export est le dernier moment pour s'en aviser.
            add("(Attestation de capacité non renseignée dans les Réglages)")
        }
        if (etat.intervention.numero.isBlank()) {
            add("(Référence attribuée à la clôture de l'intervention)")
        }
        add("Établi le ${aujourdhui.format(FORMAT_DATE_DOCUMENT)}.")
    }
}
