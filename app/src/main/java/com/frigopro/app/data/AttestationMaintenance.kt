package com.frigopro.app.data

import java.time.LocalDate

/**
 * Une ligne de l'attestation : une machine, une gamme, et ce qui a été fait.
 *
 * L'intitulé de la gamme et la périodicité sont **ceux du plan courant** et non
 * ceux recopiés sur les visites, à la différence du registre des fluides. La
 * raison tient à ce que le document prouve : une attestation dit « cette machine
 * suit la visite mensuelle, et voici les visites faites cette année ». Si la
 * cadence a changé en cours d'année, c'est la cadence actuelle qui décrit le
 * contrat en cours, et les dates au-dessous disent le reste. Les copies portées
 * par le journal servent, elles, à relire une visite dont la gamme a disparu —
 * cas où aucune ligne n'est produite ici, précisément parce qu'il n'y a plus de
 * plan à attester.
 *
 * @param attendues ce que la cadence demandait sur la période, compté depuis le
 *   départ du plan de *cette* machine : une armoire rattachée en septembre ne
 *   doit pas treize visites sur l'année.
 */
data class LigneAttestation(
    val machineNom: String,
    val machineDesignation: String,
    /** Où la machine se trouve, vide si le parc n'est pas rangé par zones. */
    val machineZone: String,
    val gammeLibelle: String,
    val periodicite: Periodicite,
    /** Les dates des visites consignées sur la période, chronologiques. */
    val visites: List<LocalDate>,
    val attendues: Int,
) {

    val realisation: RealisationGamme
        get() = RealisationGamme(attendues = attendues, faites = visites.size)
}

/**
 * L'attestation d'entretien d'un client : ce que son parc devait, et ce qu'il a
 * reçu.
 *
 * C'est le cinquième document de la chaîne, et il répond à une question qu'aucun
 * des quatre autres ne couvre. Le devis dit ce qu'on propose, la facture ce
 * qu'on réclame, le compte-rendu ce qui s'est passé lors d'**une** intervention,
 * le registre ce que les fluides ont fait. Celui-ci dit **qu'un contrat
 * d'entretien a été exécuté** — c'est la pièce qu'un gérant demande en fin
 * d'année, celle qu'une enseigne réclame à son prestataire, et celle qu'une
 * assurance regarde après un sinistre sur une installation.
 *
 * Trois règles, et chacune répare une façon de mentir :
 *
 * - **Il ne compte que ce qui est consigné.** Une visite faite et non saisie n'y
 *   est pas, et le document ne le compense pas : il ne peut pas attester ce que
 *   personne n'a noté. C'est la contrepartie assumée d'une saisie à la main.
 * - **Il annonce les deux nombres, pas seulement le taux.** « 23 faites sur 26
 *   attendues » se vérifie ligne par ligne au-dessous ; « 88 % » invite à croire
 *   à une précision que ce calcul n'a pas. Voir [RealisationGamme].
 * - **Il ne dit pas « conforme ».** Il rapporte des dates. C'est la même règle
 *   que pour la checklist du compte-rendu : l'application n'a pas qualité à
 *   délivrer un certificat, et une mention « installation conforme » sur un
 *   document signé engagerait l'entreprise bien au-delà de ce qu'elle a vu.
 *
 * Rien n'en est stocké : il se recompose à chaque export, comme le registre et
 * pour la même raison — une visite corrigée après coup doit changer le document
 * suivant, et une attestation figée en base aurait cessé d'être juste au premier
 * relevé retiré.
 */
data class AttestationMaintenance(
    val clientNom: String,
    val clientAdresse: String,
    val debut: LocalDate,
    val fin: LocalDate,
    /** Une ligne par couple (machine, gamme), par machine puis par gamme. */
    val lignes: List<LigneAttestation>,
) {

    val faites: Int get() = lignes.sumOf { it.visites.size }

    val attendues: Int get() = lignes.sumOf { it.attendues }

    /** Le bilan, tout le parc confondu. */
    val realisation: RealisationGamme
        get() = RealisationGamme(attendues = attendues, faites = faites)

    /**
     * Aucune machine de ce client ne suit de gamme.
     *
     * Distinct d'un parc suivi mais non visité : le premier veut dire « il n'y a
     * pas de contrat », le second « le contrat n'a pas été honoré », et les deux
     * ne s'écrivent pas de la même façon sur un document qui part chez le client.
     */
    val vide: Boolean get() = lignes.isEmpty()

    /** Les machines concernées, dans l'ordre du document. */
    val machines: List<String> get() = lignes.map { it.machineNom }.distinct()
}

/** Compose l'attestation d'un client. Pure, et éprouvée sans base. */
object SuiviMaintenance {

    /**
     * L'attestation du parc d'un client sur une période.
     *
     * Le **parc du seul client** : les sites d'un donneur d'ordre ne sont pas
     * inclus, et c'est volontaire — chacun est une adresse distincte avec son
     * propre parc, et un gérant de magasin demande l'attestation de *son*
     * magasin. L'appelant qui veut l'enseigne entière passe la liste des
     * identifiants ; c'est pour cela que [clientIds] est un ensemble et non un
     * identifiant unique.
     *
     * Les visites retenues sont celles de la période **et** rattachées à une
     * gamme que la machine suit encore : une visite dont le plan a disparu reste
     * au journal pour l'historique, mais il n'y a plus de contrat à attester, et
     * la compter ici aurait gonflé un taux dont le dénominateur n'existe plus.
     */
    fun attestation(
        clientNom: String,
        clientAdresse: String,
        clientIds: Set<String>,
        equipements: List<Equipement>,
        gammes: List<GammeMaintenance>,
        affectations: List<AffectationGamme>,
        releves: List<ReleveGamme>,
        debut: LocalDate,
        fin: LocalDate,
    ): AttestationMaintenance {
        val parc = equipements.filter { it.clientId in clientIds }.associateBy { it.id }
        val parGamme = gammes.associateBy { it.id }
        // Les visites de la période, par couple (machine, gamme), chronologiques.
        val visites = HashMap<Pair<String, String>, MutableList<LocalDate>>()
        releves.forEach { releve ->
            val equipementId = releve.equipementId ?: return@forEach
            val gammeId = releve.gammeId ?: return@forEach
            if (equipementId !in parc) return@forEach
            if (releve.faitLe.isBefore(debut) || releve.faitLe.isAfter(fin)) return@forEach
            visites.getOrPut(equipementId to gammeId) { mutableListOf() }.add(releve.faitLe)
        }

        val lignes = affectations
            .mapNotNull { affectation ->
                val machine = parc[affectation.equipementId] ?: return@mapNotNull null
                val gamme = parGamme[affectation.gammeId] ?: return@mapNotNull null
                LigneAttestation(
                    machineNom = machine.nom,
                    machineDesignation = machine.designation,
                    machineZone = machine.zone,
                    gammeLibelle = gamme.libelle,
                    periodicite = gamme.periodicite,
                    visites = visites[machine.id to gamme.id]?.sorted().orEmpty(),
                    attendues = Maintenance.occurrencesAttendues(
                        depuisLe = affectation.depuisLe,
                        debut = debut,
                        fin = fin,
                        periodicite = gamme.periodicite,
                    ),
                )
            }
            // **Par zone, puis par machine, puis par cadence.** Le document se
            // lit machine par machine — c'est ainsi qu'un gérant le parcourt, en
            // cherchant la sienne —, et la zone vient devant parce que c'est
            // ainsi qu'il connaît son site : il demande ce qui a été fait « en
            // chambre froide », pas ce qui a été fait sur « VIT-02 ». Les
            // machines **non rangées** passent en dernier, comme à l'écran : une
            // zone vide en tête aurait ouvert le document sur un bloc sans nom.
            // Les gammes d'une même machine se suivent de la plus fréquente à la
            // plus rare, qui est l'ordre du rang des périodicités.
            .sortedWith(
                compareBy(
                    { it.machineZone.isEmpty() },
                    { it.machineZone.lowercase() },
                    { it.machineNom.lowercase() },
                    { it.periodicite.ordinal },
                    { it.gammeLibelle.lowercase() },
                ),
            )

        return AttestationMaintenance(
            clientNom = clientNom,
            clientAdresse = clientAdresse,
            debut = debut,
            fin = fin,
            lignes = lignes,
        )
    }

    /**
     * Les années sur lesquelles ce client a quelque chose à attester.
     *
     * **Dérivées des visites**, comme les années du registre le sont des
     * mouvements : offrir trois années dont deux sont vides ferait ouvrir deux
     * attestations blanches. L'année courante y est toujours, parce qu'une
     * attestation demandée en cours d'année est le cas ordinaire — on la remet
     * avec la facture de décembre.
     */
    fun anneesDe(
        clientIds: Set<String>,
        equipements: List<Equipement>,
        releves: List<ReleveGamme>,
        aujourdhui: LocalDate = LocalDate.now(),
    ): List<Int> {
        val parc = equipements.filter { it.clientId in clientIds }.map { it.id }.toSet()
        val annees = releves
            .filter { it.equipementId in parc }
            .map { it.faitLe.year }
            .toMutableSet()
        annees += aujourdhui.year
        return annees.sortedDescending()
    }
}
