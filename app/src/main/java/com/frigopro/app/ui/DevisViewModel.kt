package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.CategoriePrestation
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.LigneDevis
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.PrestationRepository
import com.frigopro.app.data.StatutDevis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Les trois chiffres de l'en-tête des devis.
 *
 * « En cours » compte en TTC : c'est le montant que le client verrait, et donc
 * celui qui décide s'il faut relancer. Le hors taxes n'intéresse personne à ce
 * stade — il n'entre en jeu qu'une fois le devis accepté.
 */
data class CompteursDevis(
    val enAttente: Int = 0,
    val acceptes: Int = 0,
    val pipelineTtc: Double = 0.0,
) {

    companion object {

        fun de(devis: List<DevisChiffre>): CompteursDevis {
            val attente = devis.filter { it.enAttente }
            return CompteursDevis(
                enAttente = attente.size,
                acceptes = devis.count { it.devis.statut == StatutDevis.ACCEPTE },
                pipelineTtc = attente.sumOf { it.totalTtc },
            )
        }
    }
}

/**
 * L'onglet Devis : la liste, et le devis ouvert.
 *
 * Même motif que partout ailleurs — le devis ouvert est retenu par son
 * identifiant, si bien qu'une ligne ajoutée ou un changement de statut se
 * voient sans rien recopier.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevisViewModel(
    private val devis: DevisRepository,
    private val clients: ClientRepository,
    private val parametres: ParametresRepository,
    private val prestations: PrestationRepository,
) : ViewModel() {

    /**
     * Les devis avec leur montant.
     *
     * Le montant est la somme des lignes, recalculée par SQL (voir
     * [DevisRepository.devisChiffres]) : le recopier sur la ligne du devis
     * serait s'exposer à ce qu'il cesse d'être juste après une modification,
     * et un devis qui affiche un total faux est pire qu'un devis sans total.
     */
    val liste: StateFlow<List<DevisChiffre>> = devis.devisChiffres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    /**
     * Les compteurs de l'en-tête : ce qui attend, ce qui est gagné, et combien.
     *
     * « En cours » est le montant TTC de ce qui attend une réponse — c'est le
     * chiffre qui dit s'il faut relancer un client, et la raison d'être de
     * l'écran autant que la liste elle-même.
     */
    val compteurs: StateFlow<CompteursDevis> = devis.devisChiffres
        .map { CompteursDevis.de(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), CompteursDevis())

    /** Le catalogue, groupé par famille, tel que la feuille l'affiche. */
    val catalogue: StateFlow<Map<CategoriePrestation, List<Prestation>>> = prestations.parCategorie
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyMap())

    val carnet: StateFlow<List<Client>> = clients.clients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    val reglages: StateFlow<Parametres> = parametres.parametres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), Parametres())

    private val _ouvert = MutableStateFlow<String?>(null)

    val ouvert: StateFlow<String?> = _ouvert.asStateFlow()

    /** Le devis ouvert et ses lignes, d'où se calculent les totaux. */
    val complet: StateFlow<DevisComplet?> = _ouvert
        .flatMapLatest { id -> if (id == null) flowOf(null) else devis.observerComplet(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), null)

    /**
     * Ajoute une prestation du catalogue au devis ouvert.
     *
     * L'intitulé, l'unité et le prix sont **recopiés** sur la ligne, comme
     * partout ailleurs dans le projet : retirer une prestation du catalogue, ou
     * en changer le tarif, ne doit rien changer à un devis déjà envoyé.
     */
    fun onAjouterPrestation(prestation: Prestation) {
        onAjouterLigne(
            designation = prestation.designation,
            quantite = 1.0,
            unite = prestation.unite,
            prixUnitaire = prestation.prixUnitaire,
        )
    }

    fun onOuvrir(document: Devis) {
        _ouvert.value = document.id
    }

    fun onFermer() {
        _ouvert.value = null
    }

    /**
     * Crée un devis et l'ouvre aussitôt.
     *
     * Le taux de TVA vient des réglages au moment de la création, puis le
     * devis en garde sa copie : un devis déjà envoyé ne doit pas se recalculer
     * parce qu'un réglage a bougé depuis.
     */
    fun onNouveau(client: Client?) {
        viewModelScope.launch {
            val cree = devis.creer(
                client = client,
                tauxTva = reglages.value.tauxTva,
                assujettiTva = reglages.value.assujettiTva,
            )
            _ouvert.value = cree.id
        }
    }

    fun onObjet(texte: String) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch { devis.enregistrer(courant.copy(objet = texte)) }
    }

    fun onClient(client: Client) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch {
            devis.enregistrer(courant.copy(clientId = client.id, clientNom = client.nom))
        }
    }

    fun onStatut(statut: StatutDevis) {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch { devis.changerStatut(courant, statut) }
    }

    fun onAjouterLigne(designation: String, quantite: Double, unite: String, prixUnitaire: Double) {
        val id = _ouvert.value ?: return
        viewModelScope.launch { devis.ajouterLigne(id, designation, quantite, unite, prixUnitaire) }
    }

    fun onModifierLigne(ligne: LigneDevis) {
        viewModelScope.launch { devis.enregistrerLigne(ligne) }
    }

    /**
     * Offre une ligne, ou reprend le geste.
     *
     * C'est l'appui **simple** sur une ligne, là où l'appui long supprime. Avant,
     * l'appui simple supprimait : un contact involontaire faisait disparaître une
     * ligne sans un mot, et c'est exactement le genre de geste qu'on fait gants
     * aux mains. L'action fréquente prend l'appui simple, la destructrice l'appui
     * long — la convention du projet (voir `Carte`).
     */
    fun onOffrirLigne(ligne: LigneDevis) {
        viewModelScope.launch { devis.offrirLigne(ligne, !ligne.offerte) }
    }

    /** Offre la TVA, ou reprend le geste. Sans objet en franchise en base. */
    fun onOffrirTva() {
        val courant = complet.value?.devis ?: return
        viewModelScope.launch { devis.offrirTva(courant, !courant.tvaOfferte) }
    }

    /**
     * Inscrit une prestation au catalogue depuis le devis.
     *
     * Ajouter au catalogue sans quitter le chiffrage, c'est ce qui fait qu'on
     * l'enrichit vraiment : une pièce qu'il faut aller déclarer dans les Réglages
     * finit saisie en ligne libre, et le catalogue ne grossit jamais. La
     * prestation est posée **et** ajoutée au devis en cours, puisque c'est bien
     * pour lui qu'on la saisit.
     */
    fun onCreerPrestation(prestation: Prestation) {
        viewModelScope.launch {
            // La prestation enregistrée, et non celle reçue : le dépôt a nettoyé
            // l'intitulé et posé le rang, et c'est cette version-là que la ligne
            // du devis doit recopier.
            val creee = prestations.enregistrer(prestation) ?: return@launch
            if (_ouvert.value != null) onAjouterPrestation(creee)
        }
    }

    fun onSupprimerLigne(id: String) {
        viewModelScope.launch { devis.supprimerLigne(id) }
    }

    fun onSupprimer() {
        val id = _ouvert.value ?: return
        _ouvert.value = null
        viewModelScope.launch { devis.supprimer(id) }
    }

    companion object {

        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                DevisViewModel(
                    conteneur.devis,
                    conteneur.clients,
                    conteneur.parametres,
                    conteneur.prestations,
                )
            }
        }
    }
}
