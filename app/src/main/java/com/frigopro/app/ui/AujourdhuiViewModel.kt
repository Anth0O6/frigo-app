package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.EtatEtancheite
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.StatutDevis
import com.frigopro.app.data.StatutIntervention
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Une échéance de contrôle d'étanchéité, telle que l'accueil l'annonce.
 *
 * Elle ne vit dans aucune table : c'est un calcul (voir [EtatEtancheite]) posé
 * sur une machine, son fluide et sa charge. La recalculer à chaque affichage est
 * ce qui garantit qu'elle ne se périme pas — une échéance stockée serait fausse
 * le lendemain d'un contrôle.
 */
data class EcheanceFgas(
    val equipement: Equipement,
    val clientNom: String,
    val etat: EtatEtancheite,
) {

    /** La date existe forcément : une machine sans échéance n'entre pas ici. */
    val echeance: LocalDate get() = etat.echeance!!
}

/**
 * Tout ce que l'écran d'accueil affiche, réuni.
 *
 * Un seul objet plutôt que huit `StateFlow` : l'écran les lirait tous ensemble
 * de toute façon, et un état partiellement recomposé afficherait un instant des
 * chiffres qui ne vont pas avec la liste d'à côté.
 */
data class EtatAujourdhui(
    val jour: LocalDate = LocalDate.now(),
    val technicien: String = "",
    val initiales: String = "",
    /**
     * L'intervention mise en avant : celle en cours, ou à défaut la prochaine
     * qui reste à faire. `null` quand la journée est vide ou déjà finie — et
     * c'est alors une information en soi, que l'écran dit en clair.
     */
    val enAvant: LigneTournee? = null,
    /** Le reste de la journée, l'intervention mise en avant exclue. */
    val suite: List<LigneTournee> = emptyList(),
    val nombreDuJour: Int = 0,
    val devisEnAttente: Int = 0,
    /** Le montant TTC des devis qui attendent une réponse. */
    val pipelineTtc: Double = 0.0,
    /** Le chiffre d'affaires du mois : les devis acceptés, TTC. */
    val chiffreDuMois: Double = 0.0,
    val echeances: List<EcheanceFgas> = emptyList(),
) {

    val journeeVide: Boolean get() = nombreDuJour == 0
}

/**
 * L'écran d'accueil : ce qu'il faut savoir en ouvrant l'application.
 *
 * Il ne possède rien en propre — ni date consultée, ni formulaire — et c'est
 * voulu : l'accueil montre *aujourd'hui*, jamais un autre jour. Se déplacer dans
 * le temps est le travail du Planning, et donner deux façons de le faire
 * conduirait à se demander laquelle des deux on regarde.
 */
class AujourdhuiViewModel(
    interventionRepository: InterventionRepository,
    clientRepository: ClientRepository,
    devisRepository: DevisRepository,
    equipementRepository: EquipementRepository,
    parametresRepository: ParametresRepository,
    /**
     * Le jour courant, relu à chaque collecte plutôt que figé à la
     * construction : le ViewModel survit à une mise en arrière-plan, et
     * l'application rouverte le lendemain matin doit afficher le lendemain.
     */
    private val aujourdhui: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tournee = flowOf(Unit)
        .flatMapLatest { interventionRepository.observerJournee(aujourdhui()) }

    val etat: StateFlow<EtatAujourdhui> = combine(
        tournee,
        clientRepository.clients,
        devisRepository.devisChiffres,
        equipementRepository.equipements,
        parametresRepository.parametres,
    ) { interventions, carnet, devis, machines, parametres ->
        assembler(interventions, carnet, devis, machines, parametres, aujourdhui())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
        initialValue = EtatAujourdhui(),
    )

    companion object {

        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        /**
         * Combien d'échéances l'accueil montre.
         *
         * Le but est d'alerter, pas de tenir le registre : une liste de trente
         * lignes ne serait plus lue, et la fiche machine porte le détail.
         */
        private const val ECHEANCES_MONTREES = 3

        /**
         * Dans combien de temps une échéance commence à compter.
         *
         * Deux mois, parce qu'un contrôle se case dans une tournée : l'annoncer
         * la semaine où il tombe obligerait à décaler un autre client.
         */
        private const val HORIZON_ECHEANCES_MOIS = 2L

        /**
         * Assemble l'état. Séparé du `combine` et sans dépendance Android, pour
         * qu'un test puisse l'exercer directement.
         */
        internal fun assembler(
            interventions: List<Intervention>,
            carnet: List<Client>,
            devis: List<DevisChiffre>,
            machines: List<Equipement>,
            parametres: Parametres,
            jour: LocalDate,
        ): EtatAujourdhui {
            val parIdentifiant = carnet.associateBy { it.id }
            val lignes = interventions.map { intervention ->
                LigneTournee(intervention, intervention.clientId?.let { parIdentifiant[it] })
            }
            val enAvant = lignes.firstOrNull { it.intervention.statut == StatutIntervention.EN_COURS }
                ?: lignes.firstOrNull { !it.intervention.statut.close }
            val enAttente = devis.filter { it.enAttente }
            val debutDuMois = jour.withDayOfMonth(1)
            return EtatAujourdhui(
                jour = jour,
                technicien = parametres.technicien,
                initiales = parametres.initiales,
                enAvant = enAvant,
                suite = lignes.filter { it !== enAvant },
                nombreDuJour = lignes.size,
                devisEnAttente = enAttente.size,
                pipelineTtc = enAttente.sumOf { it.totalTtc },
                chiffreDuMois = devis
                    .filter { it.devis.statut == StatutDevis.ACCEPTE }
                    .filter { chiffre ->
                        val creeLe = chiffre.devis.creeLe
                        creeLe != null && !creeLe.isBefore(debutDuMois) && !creeLe.isAfter(jour)
                    }
                    .sumOf { it.totalTtc },
                echeances = echeancesProches(machines, parIdentifiant, jour),
            )
        }

        /**
         * Les contrôles d'étanchéité qui approchent, les plus urgents d'abord.
         *
         * Un contrôle **en retard** passe devant, et reste dans la liste aussi
         * longtemps qu'il n'est pas fait : c'est le seul élément de l'accueil
         * qui expose à une sanction, et le faire disparaître au bout d'un mois
         * serait exactement le mauvais service à rendre.
         */
        private fun echeancesProches(
            machines: List<Equipement>,
            carnet: Map<String, Client>,
            jour: LocalDate,
        ): List<EcheanceFgas> {
            val horizon = jour.plusMonths(HORIZON_ECHEANCES_MOIS)
            return machines
                .map { machine ->
                    machine to EtatEtancheite.calculer(
                        fluide = machine.fluide,
                        chargeKg = machine.chargeKg,
                        dernierControle = machine.dernierControleLe,
                        aujourdhui = jour,
                    )
                }
                .filter { (_, etat) -> etat.echeance != null && !etat.echeance.isAfter(horizon) }
                .sortedBy { (_, etat) -> etat.echeance }
                .take(ECHEANCES_MONTREES)
                .map { (machine, etat) ->
                    EcheanceFgas(
                        equipement = machine,
                        clientNom = carnet[machine.clientId]?.nom.orEmpty(),
                        etat = etat,
                    )
                }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                AujourdhuiViewModel(
                    interventionRepository = conteneur.interventions,
                    clientRepository = conteneur.clients,
                    devisRepository = conteneur.devis,
                    equipementRepository = conteneur.equipements,
                    parametresRepository = conteneur.parametres,
                )
            }
        }
    }
}
