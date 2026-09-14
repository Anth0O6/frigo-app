package com.frigopro.app.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * Une ligne du registre : un mouvement, remis dans son contexte.
 *
 * Un mouvement seul ne dit rien à un contrôle — « 3 kg de R-449A » sans date,
 * sans machine et sans client n'est pas une trace, c'est un chiffre. La ligne
 * réunit donc ce que la table des mouvements ne porte pas, en le **recopiant**
 * plutôt qu'en gardant des liens : le registre est une photographie à une date,
 * et un client renommé l'an prochain ne doit pas réécrire ce qu'on a présenté.
 */
data class LigneRegistre(
    val date: LocalDate,
    val numero: String,
    val client: String,
    val machine: String,
    val fluide: String,
    val sens: SensFluide,
    val masseKg: Double,
    val technicien: String,
)

/**
 * Ce qu'un fluide a vu passer sur la période.
 *
 * [net] peut être **négatif**, et c'est une information : plus de fluide
 * récupéré qu'ajouté, c'est un démantèlement ou une reprise de charge. Le
 * ramener à zéro aurait effacé précisément ce qu'un contrôle cherche à voir.
 *
 * L'équivalent CO₂ porte sur ce qui a été **ajouté**, parce que c'est ce qui a
 * été mis en circulation ; le récupéré est ce qui en sort, et l'additionner aux
 * deux reviendrait à compter deux fois le même kilo. Il vaut `null` pour un
 * fluide hors catalogue — jamais zéro : même règle que le GWP inconnu, et elle
 * compte double ici, le chiffre servant à se situer sous un seuil réglementaire.
 */
data class TotalFluide(
    val fluide: String,
    val ajoute: Double,
    val recupere: Double,
) {

    val net: Double get() = (ajoute - recupere).arrondiCentieme()

    val gwp: Int? get() = Fluides.gwp(fluide)

    val tonnesEqCo2Ajoutees: Double?
        get() = if (ajoute > 0.0) Fluides.tonnesEquivalentCo2(fluide, ajoute) else null
}

/**
 * Le registre d'une année : les lignes, et ce qu'elles totalisent.
 *
 * Les lignes sont groupées **par fluide** et chronologiques à l'intérieur de
 * chaque groupe, et ce n'est pas l'ordre qu'on attendrait d'un journal. C'est
 * un choix : un contrôle pose deux questions, « quelles quantités de tel fluide
 * avez-vous manipulées » et « d'où vient ce kilo-là ». Le groupement répond à la
 * première d'un coup d'œil, et la seconde reste entière puisque chaque ligne
 * nomme sa date, son intervention, son client et sa machine. L'ordre purement
 * chronologique aurait obligé à additionner à la main, sur un document dont
 * c'est l'usage principal.
 */
data class Registre(
    val annee: Int,
    val lignes: List<LigneRegistre>,
    val totaux: List<TotalFluide>,
) {

    val vide: Boolean get() = lignes.isEmpty()

    /** Les lignes d'un fluide, dans l'ordre où elles s'impriment. */
    fun lignesDe(fluide: String): List<LigneRegistre> = lignes.filter { it.fluide == fluide }
}

/**
 * Le registre des mouvements de fluide, tel qu'un contrôle le demande.
 *
 * La traçabilité des fluides frigorigènes est une obligation (règlement (UE)
 * 517/2014, art. 6) : la table se remplit à chaque mouvement depuis le début,
 * et il ne manquait que la **sortie**. Rien n'est stocké ici — le registre se
 * recompose à chaque export, et un document figé en base aurait cessé d'être
 * juste au premier mouvement corrigé.
 */
object RegistreFluides {

    /**
     * Les années où quelque chose a bougé, la plus récente d'abord.
     *
     * Dérivées et jamais listées en dur : proposer « 2024, 2025, 2026 » sur une
     * application installée hier aurait donné deux registres vides à ouvrir
     * avant d'atteindre le seul qui existe.
     */
    fun annees(
        mouvements: List<MouvementFluide>,
        interventions: List<Intervention>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Int> {
        val parIntervention = interventions.associateBy { it.id }
        return mouvements
            .map { dateDe(it, parIntervention[it.interventionId], zone).year }
            .distinct()
            .sortedDescending()
    }

    fun pour(
        annee: Int,
        mouvements: List<MouvementFluide>,
        interventions: List<Intervention>,
        clients: List<Client> = emptyList(),
        equipements: List<Equipement> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Registre {
        val parIntervention = interventions.associateBy { it.id }
        val parClient = clients.associateBy { it.id }
        val parEquipement = equipements.associateBy { it.id }

        val lignes = mouvements
            .mapNotNull { mouvement ->
                val intervention = parIntervention[mouvement.interventionId]
                val date = dateDe(mouvement, intervention, zone)
                if (date.year != annee) return@mapNotNull null
                LigneRegistre(
                    date = date,
                    numero = intervention?.numero.orEmpty(),
                    client = client(intervention, parClient),
                    machine = machine(mouvement, intervention, parEquipement),
                    fluide = Fluides.afficher(mouvement.fluide),
                    sens = mouvement.sens,
                    masseKg = mouvement.masseKg,
                    technicien = intervention?.technicienNom.orEmpty(),
                )
            }
            // Par fluide, puis par date : voir [Registre] pour ce que ce
            // groupement répond, et ce qu'un ordre chronologique aurait coûté.
            .sortedWith(compareBy({ it.fluide }, { it.date }, { it.numero }))

        return Registre(annee = annee, lignes = lignes, totaux = totaux(lignes))
    }

    private fun totaux(lignes: List<LigneRegistre>): List<TotalFluide> =
        lignes.groupBy { it.fluide }
            .map { (fluide, dedans) ->
                TotalFluide(
                    fluide = fluide,
                    ajoute = dedans.filter { it.sens == SensFluide.AJOUT }
                        .sumOf { it.masseKg }
                        .arrondiCentieme(),
                    recupere = dedans.filter { it.sens == SensFluide.RECUPERATION }
                        .sumOf { it.masseKg }
                        .arrondiCentieme(),
                )
            }
            .sortedBy { it.fluide }

    /**
     * La date d'une ligne : celle de l'**intervention**, et non de la saisie.
     *
     * `MouvementFluide.le` est posé par le dépôt au moment d'écrire, ce qui peut
     * tomber le lendemain quand on consigne sa tournée le soir — ou des semaines
     * plus tard sur une reprise. Un contrôle rapproche le registre des fiches
     * d'intervention, et deux dates qui ne se répondent pas sont exactement ce
     * qui fait ouvrir le dossier en grand.
     *
     * L'horodatage de saisie reste le **recours** : un mouvement dont
     * l'intervention a disparu — une sauvegarde restaurée à moitié — garde une
     * date plutôt que de sortir du registre. Une ligne qui s'efface parce qu'un
     * lien est cassé est plus grave qu'une ligne imprécise : les kilos, eux, ont
     * bougé.
     */
    private fun dateDe(
        mouvement: MouvementFluide,
        intervention: Intervention?,
        zone: ZoneId,
    ): LocalDate = intervention?.date ?: mouvement.le.atZone(zone).toLocalDate()

    private fun client(intervention: Intervention?, carnet: Map<String, Client>): String {
        if (intervention == null) return ""
        return intervention.clientId?.let { carnet[it]?.nom }.orEmpty()
            .ifBlank { intervention.client }
    }

    private fun machine(
        mouvement: MouvementFluide,
        intervention: Intervention?,
        parc: Map<String, Equipement>,
    ): String {
        val designee = mouvement.equipementId ?: intervention?.equipementId
        return designee?.let { parc[it]?.nom }.orEmpty()
            .ifBlank { intervention?.equipementNom.orEmpty() }
    }
}
