package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.EtatEtancheite
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.StatutIntervention
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * Un réglage que l'application attend, et ce qu'il empêche tant qu'il manque.
 *
 * Plusieurs valeurs des Réglages arrivent à zéro ou vides sur une installation neuve,
 * **à dessein** : un tarif horaire inventé partirait chez un vrai client sans que
 * personne ne l'ait relu, et le projet préfère partout une valeur absente à une
 * valeur devinée. Mais rien ne le disait. Un devis sortait donc à 0 €, la marge
 * d'une intervention se déclarait « non chiffrable », et l'écran ne nommait ni la
 * cause ni l'endroit où la corriger — il fallait avoir lu le code pour savoir
 * qu'un réglage manquait.
 *
 * Cette liste est ce qui manquait, et elle est **dérivée, jamais stockée** : elle
 * se relit dans les réglages courants à chaque affichage, comme le retard d'une
 * facture ou une échéance F-Gas, et un booléen « configuration terminée » en base
 * serait faux le jour où l'on remet un taux à zéro.
 *
 * Chacune nomme la conséquence plutôt que le réglage, parce que c'est la
 * conséquence qui décide si l'on s'en occupe maintenant : « le matériel se
 * propose au prix coûtant » se juge tout de suite, « coefficientMateriel = 0 »
 * ne se juge pas du tout. Et chacune porte la page où elle se règle : une alerte
 * qui ne mène nulle part n'est pas une réponse à « et maintenant ? ».
 */
enum class ReglageManquant(
    val intitule: String,
    val consequence: String,
    val page: PageReglages,
) {
    ENTREPRISE(
        intitule = "Nom de l'entreprise",
        consequence = "Vos devis et factures partent sans raison sociale.",
        page = PageReglages.ENTREPRISE,
    ),

    /**
     * L'adresse et le SIRET sont des **mentions obligatoires** d'une facture, au
     * même titre que la raison sociale : une facture qui part sans eux est un
     * manquement dont l'entreprise répond, et rien ne le disait — la page
     * « Mon entreprise » l'annonçait seulement à qui l'ouvrait.
     */
    ADRESSE_ENTREPRISE(
        intitule = "Adresse de l'entreprise",
        consequence = "Vos factures partent sans l'adresse du vendeur, une mention obligatoire.",
        page = PageReglages.ENTREPRISE,
    ),
    SIRET(
        intitule = "SIRET",
        consequence = "Vos factures partent sans numéro SIRET, une mention obligatoire.",
        page = PageReglages.ENTREPRISE,
    ),
    TAUX_HORAIRE(
        intitule = "Taux horaire facturé",
        consequence = "Une ligne de main-d'œuvre se chiffre à 0 €.",
        page = PageReglages.TARIFS,
    ),
    COUT_HORAIRE(
        intitule = "Coût horaire interne",
        consequence = "La marge d'une intervention reste incalculable.",
        page = PageReglages.TARIFS,
    ),
    COEFFICIENT_MATERIEL(
        intitule = "Coefficient matériel",
        consequence = "Le matériel sans prix de vente se propose au prix coûtant.",
        page = PageReglages.TARIFS,
    ),
    ;

    companion object {

        /**
         * Ce qui manque, dans l'ordre où cela se règle.
         *
         * L'ordre n'est pas celui de l'enum par hasard : l'identité de
         * l'entreprise d'abord — raison sociale, adresse, SIRET — parce qu'elle
         * paraît sur tout ce qui sort, les deux taux ensuite parce qu'ils
         * chiffrent, le coefficient en dernier parce que son absence a un
         * comportement défendable — proposer au coûtant — là où les autres
         * produisent un document incomplet ou un calcul muet.
         *
         * Le **régime de TVA** n'y est pas, et ce n'est pas un oubli : ses deux
         * valeurs sont légitimes, si bien que rien en base ne distingue « assujetti
         * » choisi de « assujetti » jamais regardé, et une colonne pour le dire
         * aurait demandé une migration. Il est donc mis sous les yeux là où le taux
         * se saisit, dans un même bloc « TVA » des tarifs.
         */
        fun de(parametres: Parametres): List<ReglageManquant> = buildList {
            if (parametres.entreprise.isBlank()) add(ENTREPRISE)
            if (parametres.entrepriseAdresse.isBlank()) add(ADRESSE_ENTREPRISE)
            if (parametres.entrepriseSiret.isBlank()) add(SIRET)
            if (parametres.tauxHoraire <= 0.0) add(TAUX_HORAIRE)
            if (parametres.coutHoraireInterne <= 0.0) add(COUT_HORAIRE)
            if (parametres.coefficientMateriel <= 0.0) add(COEFFICIENT_MATERIEL)
        }
    }
}

/**
 * Ce que l'accueil ajoute à la journée, réuni.
 *
 * La journée elle-même — les interventions de la date affichée — vient de
 * l'[InterventionsViewModel], qui tient la date : l'accueil et la vue « Jour »
 * ont été réunis, et deux sources pour les mêmes lignes auraient fini par ne
 * pas montrer la même chose. Il ne reste ici que ce qui ne dépend d'aucune
 * date choisie : qui est au volant, les échéances F-Gas et les réglages qui
 * manquent.
 *
 * Un seul objet plutôt que plusieurs `StateFlow` : l'écran les lirait tous
 * ensemble de toute façon, et un état partiellement recomposé afficherait un
 * instant une liste qui ne va pas avec celle d'à côté.
 *
 * Les **tuiles commerciales** — devis en attente, chiffre du mois — n'y sont
 * plus. Elles répondaient à « où en est l'entreprise ? », qui n'est pas la
 * question de l'accueil, et l'onglet Factures porte déjà ses compteurs.
 */
data class EtatAujourdhui(
    val jour: LocalDate = LocalDate.now(),
    val technicien: String = "",
    val echeances: List<EcheanceFgas> = emptyList(),
    /**
     * Les réglages qu'il reste à poser. Vide dès qu'ils le sont tous, et le
     * bandeau disparaît alors de lui-même : une alerte qu'on ne peut pas
     * faire taire en la réglant est une alerte qu'on cesse de lire.
     */
    val reglagesManquants: List<ReglageManquant> = emptyList(),
)

/**
 * Ce qu'« Aujourd'hui » annonce en plus de la journée : voir [EtatAujourdhui].
 *
 * Il ne possède aucune date consultée : la date affichée est celle de
 * l'[InterventionsViewModel], que les flèches de l'en-tête déplacent. Ce qu'il
 * annonce vaut pour aujourd'hui quel que soit le jour regardé — une échéance
 * F-Gas ne recule pas parce qu'on consulte jeudi prochain.
 */
class AujourdhuiViewModel(
    clientRepository: ClientRepository,
    equipementRepository: EquipementRepository,
    parametresRepository: ParametresRepository,
    /**
     * Le jour courant, relu à chaque collecte plutôt que figé à la
     * construction : le ViewModel survit à une mise en arrière-plan, et
     * l'application rouverte le lendemain matin doit afficher le lendemain.
     */
    private val aujourdhui: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val etat: StateFlow<EtatAujourdhui> = combine(
        clientRepository.clients,
        equipementRepository.equipements,
        parametresRepository.parametres,
    ) { carnet, machines, parametres ->
        assembler(carnet, machines, parametres, aujourdhui())
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
         * L'intervention mise en avant : celle en cours, ou à défaut la prochaine
         * qui reste à faire. `null` quand la journée est vide ou déjà finie — et
         * c'est alors une information en soi, que l'écran dit en clair.
         *
         * Une fonction pure sur la journée plutôt qu'un champ de l'état : la
         * journée est celle de l'[InterventionsViewModel], et l'écran ne la met
         * en avant que lorsqu'il montre aujourd'hui.
         */
        internal fun enAvantDe(lignes: List<LigneTournee>): LigneTournee? =
            lignes.firstOrNull { it.intervention.statut == StatutIntervention.EN_COURS }
                ?: lignes.firstOrNull { !it.intervention.statut.close }

        /**
         * Assemble l'état. Séparé du `combine` et sans dépendance Android, pour
         * qu'un test puisse l'exercer directement.
         */
        internal fun assembler(
            carnet: List<Client>,
            machines: List<Equipement>,
            parametres: Parametres,
            jour: LocalDate,
        ): EtatAujourdhui {
            val parIdentifiant = carnet.associateBy { it.id }
            return EtatAujourdhui(
                jour = jour,
                technicien = parametres.technicien,
                echeances = echeancesProches(machines, parIdentifiant, jour),
                reglagesManquants = ReglageManquant.de(parametres),
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
                // Par la machine entière, détecteur compris : la même machine ne
                // doit pas être due à deux dates selon l'écran qui la montre.
                .map { machine -> machine to EtatEtancheite.de(machine, jour) }
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
                    clientRepository = conteneur.clients,
                    equipementRepository = conteneur.equipements,
                    parametresRepository = conteneur.parametres,
                )
            }
        }
    }
}
