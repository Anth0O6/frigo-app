package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Devis
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.Technicien
import com.frigopro.app.data.TechnicienRepository
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
 * Les trois vues de la tournée, sous une seule bascule.
 *
 * Elles ne sont pas trois écrans mais trois **distances de lecture** de la même
 * chose : « et maintenant ? », « et le reste de la journée ? », « et le reste de
 * la semaine ? ». Les avoir séparées en deux onglets — l'accueil et le planning
 * — était le point le plus déroutant de l'application : deux entrées de la barre
 * du bas montraient les mêmes interventions, sans que rien ne dise laquelle
 * regarder. Une bascule sous le titre est la réponse que le projet donne déjà
 * partout où un onglet porte plusieurs vues, les carnets et la facturation, et
 * les trois se comportent maintenant de la même façon.
 *
 * Le gain n'est pas seulement visuel : la barre du bas passe de six entrées à
 * cinq, c'est-à-dire au maximum que Material recommande, et les libellés
 * cessent d'être abrégés — « Auj. » ne voulait rien dire pour personne.
 */
enum class VueTournee(val libelle: String) {
    MAINTENANT("Maintenant"),
    JOUR("Jour"),
    SEMAINE("Semaine"),
}

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
    private val technicienRepository: TechnicienRepository,
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

    private val _vue = MutableStateFlow(VueTournee.MAINTENANT)

    /**
     * Laquelle des trois vues de la tournée est ouverte.
     *
     * Elle est tenue **ici** et non dans la coquille, à la différence du carnet,
     * parce que l'accueil et le planning partagent déjà ce ViewModel : la carte
     * de l'accueil n'a donc rien à transporter pour ouvrir la semaine, elle la
     * demande à l'objet qu'elle a sous la main.
     *
     * On rouvre sur « Maintenant », qui est la question qu'on se pose en sortant
     * le téléphone. Revenir sur la vue qu'on avait quittée aurait rouvert le
     * planning de jeudi prochain un mardi matin.
     */
    val vue: StateFlow<VueTournee> = _vue.asStateFlow()

    fun onVue(vue: VueTournee) {
        _vue.value = vue
    }

    /** Les interventions de la semaine où tombe la journée affichée. */
    val semaine: StateFlow<List<Intervention>> = _jour
        .flatMapLatest { interventionRepository.observerSemaine(lundiDe(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

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

    /**
     * Les techniciens, pour confier une tournée.
     *
     * La liste démarre vide, comme celle des types : l'application était celle
     * d'un homme seul, et n'a pas à supposer une équipe. Le choix n'apparaît donc
     * dans le formulaire que lorsqu'il y a quelqu'un à choisir.
     */
    val techniciens: StateFlow<List<Technicien>> = technicienRepository.techniciens
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    /** Inscrit un technicien au passage, comme le carnet inscrit un client. */
    fun onNouveauTechnicien(nom: String) {
        val etat = _formulaire.value ?: return
        viewModelScope.launch {
            val technicien = technicienRepository.trouverOuCreer(nom)
            _formulaire.value = etat.copy(
                technicienId = technicien.id,
                technicienNom = technicien.nom,
            )
        }
    }

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
     * Ouvre le formulaire pré-rempli depuis un devis accepté.
     *
     * Un devis accepté est du travail promis à une date qui reste à poser, et
     * c'était jusqu'ici le seul endroit du parcours où l'on ressaisissait à la
     * main ce que l'application savait déjà : le client, sa ville, la machine
     * visée. Rien n'est **enregistré** ici — le formulaire s'ouvre, et c'est
     * l'utilisateur qui valide : la date est la vraie question que pose un
     * chantier accepté, et la poser à sa place aurait planifié une intervention
     * un jour choisi par personne.
     *
     * La **ville** vient de la fiche du client et non du devis, qui ne la porte
     * pas : c'est elle qui rend l'intervention valide, et sans elle le
     * formulaire s'ouvrirait sur un champ vide qu'il faudrait remplir en
     * regardant ailleurs. `client` est en revanche pris sur le devis — c'est le
     * nom **recopié** au moment du chiffrage, celui que le client a vu sur le
     * document.
     *
     * L'objet du devis devient les **notes**, avec la référence devant. C'est
     * un point de départ, pas un compte-rendu : le technicien le réécrit sur
     * place, et il est là pour qu'on sache en arrivant ce qui a été vendu.
     *
     * Le devis n'est pas modifié : il reste accepté, et rien ne le marque
     * « planifié ». Un état de plus aurait demandé une colonne, une migration
     * et une règle pour le cas où l'intervention est supprimée ensuite — pour
     * une information que la tournée porte déjà.
     */
    fun onPlanifierDepuisDevis(devis: Devis, client: Client?) {
        _formulaire.value = EtatFormulaire(
            // La journée consultée, comme pour toute création : c'est celle que
            // l'utilisateur a sous les yeux, et le sélecteur est juste à côté.
            date = _jour.value,
            client = devis.clientNom.ifBlank { client?.nom.orEmpty() },
            ville = client?.ville.orEmpty(),
            clientId = devis.clientId,
            equipementId = devis.equipementId,
            equipementNom = devis.equipementNom,
            notes = listOf(devis.numero, devis.objet)
                .filter { it.isNotBlank() }
                .joinToString(" — "),
        )
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
            // Le donneur d'ordre entre au carnet par le même chemin : sans cela,
            // une sous-traitance tapée à la main laisserait un nom sur
            // l'intervention et rien dans le carnet, et la facture partirait sans
            // adresse.
            val donneurId = etat.clientFactureId ?: etat.clientFactureNom.trim()
                .takeIf { it.isNotBlank() }
                ?.let { clientRepository.trouverOuCreer(it, etat.ville).id }
            interventionRepository.enregistrer(
                etat.versIntervention().copy(clientId = clientId, clientFactureId = donneurId),
            )
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
                    conteneur.techniciens,
                )
            }
        }
    }
}
