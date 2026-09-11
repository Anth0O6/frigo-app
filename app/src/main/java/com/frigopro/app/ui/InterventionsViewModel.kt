package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.TypeIntervention
import com.frigopro.app.data.TypeInterventionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Détient l'état de l'écran « Interventions ».
 *
 * L'UI observe [jour], [lignes], [clients], [types] et [formulaire], et remonte
 * les intentions utilisateur via les méthodes `on…`.
 */
class InterventionsViewModel(
    private val interventionRepository: InterventionRepository,
    private val clientRepository: ClientRepository,
    private val typeRepository: TypeInterventionRepository,
    private val equipementRepository: EquipementRepository,
) : ViewModel() {

    private val _jour = MutableStateFlow(LocalDate.now())

    /** Journée affichée. Changer sa valeur suffit à recharger la liste. */
    val jour: StateFlow<LocalDate> = _jour.asStateFlow()

    private val _frise = MutableStateFlow(true)

    /**
     * La journée en frise horaire plutôt qu'en liste.
     *
     * Les deux vues répondent à deux questions différentes : la liste dit ce qui
     * vient ensuite, la frise dit **où sont les trous** — et c'est dans les trous
     * qu'on case le client qui vient d'appeler. La frise est retenue par défaut
     * parce que la question de la journée en cours est aussi celle de la journée
     * qu'on est en train de remplir.
     */
    val frise: StateFlow<Boolean> = _frise.asStateFlow()

    fun onBasculerVue() {
        _frise.value = !_frise.value
    }

    private val _semaineOuverte = MutableStateFlow(false)

    /**
     * La vue semaine, par-dessus la tournée.
     *
     * Même onglet et non une section de plus : la journée et la semaine
     * répondent à deux questions du même métier — « et maintenant ? » et
     * « où puis-je caser jeudi ? » — et passer de l'une à l'autre ne doit pas
     * coûter un aller-retour par la barre du bas.
     */
    val semaineOuverte: StateFlow<Boolean> = _semaineOuverte.asStateFlow()

    /** Les interventions de la semaine où tombe la journée affichée. */
    val semaine: StateFlow<List<Intervention>> = _jour
        .flatMapLatest { interventionRepository.observerSemaine(lundiDe(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    fun onOuvrirSemaine() {
        _semaineOuverte.value = true
    }

    fun onFermerSemaine() {
        _semaineOuverte.value = false
    }

    /** Recule ou avance d'une semaine entière, en gardant le jour de la semaine. */
    fun onSemainePrecedente() {
        _jour.value = _jour.value.minusWeeks(1)
    }

    fun onSemaineSuivante() {
        _jour.value = _jour.value.plusWeeks(1)
    }

    /**
     * Carnet de clients : il fournit les suggestions du formulaire et les
     * coordonnées qu'affiche la tournée.
     */
    val clients: StateFlow<List<Client>> = clientRepository.clients
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    /**
     * Tournée de la journée consultée, chaque ligne accompagnée de la fiche du
     * client chez qui elle a lieu.
     *
     * Le rapprochement se fait ici plutôt que par une jointure SQL : les deux
     * flux sont déjà observés, et l'écran reçoit ainsi de quoi afficher
     * l'intervention *et* de quoi agir — appeler, se rendre sur place.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lignes: StateFlow<List<LigneTournee>> = combine(
        _jour.flatMapLatest { interventionRepository.observerJournee(it) },
        clients,
    ) { interventions, carnet ->
        val parIdentifiant = carnet.associateBy { client -> client.id }
        interventions.map { intervention ->
            LigneTournee(
                intervention = intervention,
                client = intervention.clientId?.let { id -> parIdentifiant[id] },
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    /** Types d'intervention proposés par le formulaire. Vide au premier lancement. */
    val types: StateFlow<List<TypeIntervention>> = typeRepository.types
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    /**
     * Tout le parc de machines. Le formulaire n'en montrera que celles du client
     * choisi : le filtrage se fait à l'affichage, le flux étant déjà observé.
     */
    val machines: StateFlow<List<Equipement>> = equipementRepository.equipements
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    private val _formulaire = MutableStateFlow<EtatFormulaire?>(null)

    /** Formulaire ouvert, ou `null` quand l'écran affiche seulement la liste. */
    val formulaire: StateFlow<EtatFormulaire?> = _formulaire.asStateFlow()

    fun onJourPrecedent() {
        _jour.update { it.minusDays(1) }
    }

    fun onJourSuivant() {
        _jour.update { it.plusDays(1) }
    }

    fun onJourChoisi(date: LocalDate) {
        _jour.value = date
    }

    /** La nouvelle intervention est proposée sur la journée consultée. */
    fun onNouvelleIntervention() {
        _formulaire.value = EtatFormulaire(date = _jour.value)
    }

    fun onModifierIntervention(intervention: Intervention) {
        _formulaire.value = EtatFormulaire.depuis(intervention)
    }

    /**
     * Fait avancer le statut depuis la liste, sans ouvrir le formulaire :
     * marquer une intervention terminée doit tenir en un geste, sur place.
     */
    fun onChangerStatut(intervention: Intervention) {
        viewModelScope.launch {
            interventionRepository.enregistrer(
                intervention.copy(statut = intervention.statut.suivant()),
            )
        }
    }

    fun onFormulaireChange(etat: EtatFormulaire) {
        _formulaire.value = etat
    }

    /**
     * Rattache l'intervention à un client du carnet et en reprend les
     * coordonnées. Changer de client oublie la machine : elle appartenait au
     * précédent, et n'a aucun sens chez celui-ci.
     */
    fun onClientChoisi(client: Client) {
        _formulaire.update { etat ->
            when {
                etat == null -> null
                etat.clientId == client.id ->
                    etat.copy(client = client.nom, ville = client.ville, clientId = client.id)

                else -> etat.copy(
                    client = client.nom,
                    ville = client.ville,
                    clientId = client.id,
                    equipementId = null,
                    equipementNom = "",
                )
            }
        }
    }

    /**
     * Choisit un type, ou l'enlève quand [type] vaut `null` : le type est
     * facultatif, et revenir en arrière doit être aussi simple que choisir.
     */
    fun onTypeChoisi(type: TypeIntervention?) {
        _formulaire.update { etat ->
            etat?.copy(typeId = type?.id, typeLibelle = type?.libelle ?: "")
        }
    }

    /**
     * Ajoute un type à la liste depuis le formulaire, et le choisit aussitôt :
     * rencontrer un type qui manque ne doit pas obliger à quitter sa saisie.
     */
    fun onNouveauType(libelle: String) {
        if (libelle.isBlank()) return

        viewModelScope.launch {
            val type = typeRepository.trouverOuCreer(libelle)
            _formulaire.update { etat -> etat?.copy(typeId = type.id, typeLibelle = type.libelle) }
        }
    }

    /** Choisit une machine, ou l'enlève quand [equipement] vaut `null`. */
    fun onMachineChoisie(equipement: Equipement?) {
        _formulaire.update { etat ->
            etat?.copy(equipementId = equipement?.id, equipementNom = equipement?.nom ?: "")
        }
    }

    /**
     * Inscrit une machine chez le client du formulaire et la choisit aussitôt :
     * tomber sur une machine non fichée ne doit pas obliger à quitter sa saisie.
     * Sans client du carnet, il n'y a pas de parc où l'inscrire.
     */
    fun onNouvelleMachine(nom: String) {
        val clientId = _formulaire.value?.clientId ?: return
        if (nom.isBlank()) return

        viewModelScope.launch {
            val machine = equipementRepository.trouverOuCreer(clientId, nom)
            _formulaire.update { etat ->
                etat?.copy(equipementId = machine.id, equipementNom = machine.nom)
            }
        }
    }

    fun onFermerFormulaire() {
        _formulaire.value = null
    }

    /**
     * Enregistre la saisie, puis se place sur la journée de l'intervention :
     * sans cela, une ligne datée d'un autre jour disparaîtrait sans un mot.
     * Une saisie incomplète laisse le formulaire ouvert.
     *
     * Un client absent du carnet y est inscrit au passage : c'est ainsi que le
     * carnet se remplit, sans écran de saisie dédié.
     */
    fun onValiderFormulaire() {
        val etat = _formulaire.value ?: return
        if (!etat.estValide) return

        _formulaire.value = null
        _jour.value = etat.date
        viewModelScope.launch {
            val clientId = etat.clientId
                ?: clientRepository.trouverOuCreer(etat.client, etat.ville).id
            interventionRepository.enregistrer(etat.versIntervention().copy(clientId = clientId))
        }
    }

    /** Supprime l'intervention en cours d'édition. Sans effet sur une création. */
    fun onSupprimerIntervention() {
        val id = _formulaire.value?.id ?: return

        _formulaire.value = null
        viewModelScope.launch { interventionRepository.supprimer(id) }
    }

    companion object {

        /**
         * Délai avant d'arrêter d'observer la base quand l'écran passe en
         * arrière-plan : assez long pour traverser une rotation sans relancer
         * la requête, assez court pour ne rien collecter inutilement.
         */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                InterventionsViewModel(
                    conteneur.interventions,
                    conteneur.clients,
                    conteneur.typesIntervention,
                    conteneur.equipements,
                )
            }
        }
    }
}
