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
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.StatutIntervention
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Ce que fait la pastille de statut d'une ligne — et le bouton « Terminer » de
 * la carte en cours —, selon où en est l'intervention.
 *
 * La pastille faisait tourner le statut en boucle — planifié, en cours,
 * terminé, puis de nouveau planifié — en écrivant le statut seul. C'était un
 * second chemin vers « en cours », qui ne lançait pas le chronomètre, et un
 * second chemin vers « terminé », qui ne l'arrêtait pas et ne numérotait pas le
 * compte-rendu ; « Facturer » apparaissait pourtant dès le statut terminé, sur
 * une intervention sans référence et au temps encore courant. Et un appui de
 * trop rouvrait une intervention close.
 *
 * Il n'y a plus qu'**un chemin par transition**. Commencer passe par le
 * chronomètre ([InterventionRepository.basculerChrono]), comme « Démarrer » sur
 * l'accueil : un seul endroit décide que le travail a commencé. Terminer ne se
 * fait que par la **clôture**, qui arrête le temps et attribue la référence, et
 * la pastille y **mène** — elle ouvre l'intervention sur le volet Rapport, où
 * l'on fait signer puis où l'on clôture — au lieu de la faire à moitié. Une
 * intervention terminée ouvre son compte-rendu : elle ne se rouvre plus d'un
 * doigt qui glisse.
 */
enum class GesteStatut {
    /** Lancer le chronomètre, ce qui met l'intervention en cours. */
    DEMARRER,

    /** Ouvrir le volet Rapport pour clôturer. */
    CLOTURER,

    /** Ouvrir le compte-rendu d'une intervention déjà close. */
    VOIR_RAPPORT,
}

/** Voir [GesteStatut]. `A_VALIDER` mène à la clôture comme `EN_COURS`. */
fun gesteStatut(statut: StatutIntervention): GesteStatut = when (statut) {
    StatutIntervention.PLANIFIEE -> GesteStatut.DEMARRER
    StatutIntervention.EN_COURS -> GesteStatut.CLOTURER
    StatutIntervention.A_VALIDER -> GesteStatut.CLOTURER
    StatutIntervention.TERMINEE -> GesteStatut.VOIR_RAPPORT
}

/**
 * Détient l'état de la journée : celle qu'« Aujourd'hui » affiche, la semaine
 * que le Planning montre autour d'elle, la recherche, et la feuille de saisie.
 *
 * L'UI observe [jour], [lignes], [clients], [types] et [formulaire], et remonte
 * les intentions utilisateur via les méthodes `on…`. Il est partagé par
 * `viewModel()` entre la coquille — qui pose la feuille de saisie —,
 * « Aujourd'hui », le Planning et la vue Devis, qui y planifie un devis accepté.
 */
class InterventionsViewModel(
    private val interventionRepository: InterventionRepository,
    private val clientRepository: ClientRepository,
    private val typeRepository: TypeInterventionRepository,
    private val equipementRepository: EquipementRepository,
    private val technicienRepository: TechnicienRepository,
    /**
     * Les réglages, dont ce ViewModel ne lit qu'une chose : `chronoAuto`, au
     * moment d'enregistrer une intervention passée « en cours » à la main.
     * Lus par une fonction `suspend` et non tenus en `StateFlow` : personne ici
     * ne collecterait ce flux, et `.value` resterait à sa valeur initiale.
     */
    private val parametresRepository: ParametresRepository,
) : ViewModel() {

    private val _jour = MutableStateFlow(LocalDate.now())

    /** Journée affichée. Changer sa valeur suffit à recharger la liste. */
    val jour: StateFlow<LocalDate> = _jour.asStateFlow()

    private val _frise = MutableStateFlow(false)

    /**
     * La journée en frise horaire plutôt qu'en liste.
     *
     * Les deux vues répondent à deux questions différentes : la liste dit ce qui
     * vient ensuite, la frise dit **où sont les trous** — et c'est dans les trous
     * qu'on case le client qui vient d'appeler. La **liste** est retenue par
     * défaut depuis que la journée est l'accueil : on ouvre l'application pour
     * savoir chez qui l'on va, et la frise reste à un appui pour qui cherche un
     * créneau. C'est une préférence d'affichage et non une navigation, d'où un
     * bouton à côté plutôt qu'une pastille de plus.
     */
    val frise: StateFlow<Boolean> = _frise.asStateFlow()

    fun onBasculerVue() {
        _frise.value = !_frise.value
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

    private val _recherche = MutableStateFlow("")

    /** Ce qu'on cherche dans toute la tournée, et non dans la seule journée. */
    val recherche: StateFlow<String> = _recherche.asStateFlow()

    fun onRecherche(saisie: String) {
        _recherche.value = saisie
    }

    private val _rechercheOuverte = MutableStateFlow(false)

    /**
     * Le champ de recherche est ouvert, et l'écran montre les résultats au lieu
     * de la journée.
     *
     * Distinct de [recherche] parce qu'un champ ouvert et vide n'est pas la
     * journée : la loupe de l'en-tête l'ouvre avant qu'on ait tapé quoi que ce
     * soit, et refermer la journée sous le doigt à la première lettre — puis la
     * rouvrir à la dernière effacée — ferait sauter l'écran deux fois. Le flux
     * lourd, lui, ne dépend que du texte : ouvert et vide, il n'observe rien.
     */
    val rechercheOuverte: StateFlow<Boolean> = _rechercheOuverte.asStateFlow()

    fun onOuvrirRecherche() {
        _rechercheOuverte.value = true
    }

    /**
     * Referme la recherche **et l'efface** : la laisser courir ferait retrouver
     * une journée filtrée à la prochaine loupe, sans que rien, à ce moment-là,
     * ne dise qu'on cherche encore. C'est aussi ce qui arrête le flux de toutes
     * les interventions.
     */
    fun onFermerRecherche() {
        _rechercheOuverte.value = false
        _recherche.value = ""
    }

    /**
     * Ramène « Aujourd'hui » à son début : la recherche refermée, et la date
     * remise à aujourd'hui. C'est ce que fait toucher l'onglet déjà ouvert — le
     * geste de qui s'est perdu dans le calendrier et veut retrouver sa journée.
     */
    fun onRevenirAAujourdhui() {
        onFermerRecherche()
        _jour.value = LocalDate.now()
    }

    /**
     * Les interventions qui correspondent à la recherche, **toutes dates
     * confondues**, les plus récentes d'abord.
     *
     * C'est ce qui manquait pour retrouver « l'intervention chez Carrefour en
     * mars » : il fallait remonter jour par jour, ou passer par la fiche d'une
     * machine en espérant que l'intervention y était rattachée. Une application
     * qui garde le temps passé, les relevés et la signature d'un client doit
     * pouvoir les ressortir autrement qu'en connaissant la date.
     *
     * Le flux de **toutes** les interventions est le plus lourd du projet, et il
     * ne coule donc que tant qu'on cherche : `flatMapLatest` sur la saisie le
     * remplace par une liste vide dès qu'elle est effacée. Sans cela l'onglet le
     * plus ouvert de l'application observerait la table entière en permanence,
     * pour un écran qui ne montre qu'une journée.
     *
     * Les résultats sont **antichronologiques**, à l'inverse de la journée : on
     * cherche presque toujours quelque chose de récent, et la première
     * intervention d'un client de longue date n'est pas celle qu'on veut voir en
     * tête.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trouvees: StateFlow<List<LigneTournee>> = _recherche
        .flatMapLatest { saisie ->
            if (saisie.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(interventionRepository.observerToutes(), clients) { toutes, carnet ->
                    val parIdentifiant = carnet.associateBy { client -> client.id }
                    toutes
                        .map { intervention ->
                            LigneTournee(
                                intervention = intervention,
                                client = intervention.clientId?.let { parIdentifiant[it] },
                            )
                        }
                        .filter { ligne -> ligne.correspondA(saisie) }
                        .sortedWith(
                            compareByDescending<LigneTournee> { it.intervention.date }
                                .thenByDescending { it.intervention.heure },
                        )
                }
            }
        }
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
     * Commence le travail depuis la pastille d'une ligne, sans rien ouvrir.
     *
     * Par le chronomètre et non par le statut seul : voir [GesteStatut]. Le
     * statut suit le chronomètre ([InterventionRepository.basculerChrono]),
     * exactement comme « Démarrer » sur la carte du haut d'« Aujourd'hui ».
     *
     * Un chronomètre qui tourne déjà n'est pas mis en pause : `basculerChrono`
     * est une bascule, et le doigt qui insiste sur une pastille ne veut jamais
     * arrêter ce qu'il vient de lancer. Seule une intervention **planifiée** se
     * démarre ici : les autres ont leur geste, qui ouvre l'intervention.
     */
    fun onDemarrer(intervention: Intervention) {
        if (gesteStatut(intervention.statut) != GesteStatut.DEMARRER) return
        if (intervention.chrono.enMarche) return
        viewModelScope.launch { interventionRepository.basculerChrono(intervention) }
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
     * carnet se remplit, sans écran de saisie dédié. L'adresse et le téléphone
     * saisis complètent sa fiche **là où elle est vide** — voir
     * [ClientRepository.completer].
     *
     * Une intervention que la feuille fait passer « en cours » lance le
     * chronomètre si le réglage `chronoAuto` le demande : c'est le sens que son
     * KDoc lui donne — démarrer le temps dès que l'intervention passe en cours,
     * sans second geste —, et c'est le seul endroit où le statut passe encore en
     * cours **sans** le chronomètre. Désactivé, ce qui est le défaut, le statut
     * change seul : un chrono oublié vaut mieux qu'un chrono faux.
     */
    fun onValiderFormulaire() {
        val etat = _formulaire.value ?: return
        if (!etat.estValide) return

        _formulaire.value = null
        _jour.value = etat.date
        viewModelScope.launch {
            val clientId = etat.clientId
                ?: clientRepository.trouverOuCreer(etat.client, etat.ville).id
            if (etat.adresse.isNotBlank() || etat.telephone.isNotBlank()) {
                clientRepository.completer(clientId, etat.adresse, etat.telephone)
            }
            // Le donneur d'ordre entre au carnet par le même chemin : sans cela,
            // une sous-traitance tapée à la main laisserait un nom sur
            // l'intervention et rien dans le carnet, et la facture partirait sans
            // adresse.
            val donneurId = etat.clientFactureId ?: etat.clientFactureNom.trim()
                .takeIf { it.isNotBlank() }
                ?.let { clientRepository.trouverOuCreer(it, etat.ville).id }
            val aEcrire = etat.versIntervention().copy(clientId = clientId, clientFactureId = donneurId)
            val passeEnCours = aEcrire.statut == StatutIntervention.EN_COURS &&
                etat.origine?.statut != StatutIntervention.EN_COURS
            if (passeEnCours && !aEcrire.chrono.enMarche && parametresRepository.lire().chronoAuto) {
                // `basculerChrono` enregistre lui-même : une seule écriture.
                interventionRepository.basculerChrono(aEcrire)
            } else {
                interventionRepository.enregistrer(aEcrire)
            }
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
                    conteneur.parametres,
                )
            }
        }
    }
}
