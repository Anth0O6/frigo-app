package com.frigopro.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.FactureComplete
import com.frigopro.app.data.FactureRepository
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.LigneFacture
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.SuiviRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Les compteurs de l'en-tête : ce qui attend un règlement, et ce qui traîne.
 *
 * `enRetard` est un sous-ensemble d'`enAttente` et non une catégorie à côté :
 * une facture en retard attend toujours son règlement. Les séparer aurait fait
 * un total faux.
 */
data class CompteursFactures(
    val enAttente: Int = 0,
    val montantEnAttente: Double = 0.0,
    val enRetard: Int = 0,
    val montantEnRetard: Double = 0.0,
)

/**
 * Les factures : la liste, celle qu'on regarde, et ce qu'on en fait.
 *
 * Il porte aussi **l'émission depuis une intervention**, appelée depuis l'écran
 * d'intervention et non d'ici. `viewModel()` rendant une seule instance par
 * classe pour une même activité, les deux écrans parlent au même objet : la
 * facture créée là-bas est immédiatement celle qu'on ouvre ici.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FacturesViewModel(
    private val factures: FactureRepository,
    private val suivi: SuiviRepository,
    private val clients: ClientRepository,
    private val parametres: ParametresRepository,
    private val pdf: ProducteurPdf,
) : ViewModel() {

    val reglages: StateFlow<Parametres> = parametres.parametres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_MS), Parametres())

    val carnet: StateFlow<List<Client>> = clients.clients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_MS), emptyList())

    val liste: StateFlow<List<FactureChiffree>> = factures.facturesChiffrees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_MS), emptyList())

    /**
     * Le jour, tenu par le ViewModel plutôt que lu à chaque affichage.
     *
     * « En retard » se déduit de l'échéance et du jour, et rien n'est stocké —
     * même règle que les échéances F-Gas de l'accueil. Le tenir ici rend le
     * calcul reproductible, et la journée qui tourne n'est pas un événement
     * qu'un écran doive suivre à la seconde.
     */
    private val _aujourdhui = MutableStateFlow(LocalDate.now())

    val compteurs: StateFlow<CompteursFactures> =
        combine(liste, _aujourdhui) { toutes, jour ->
            val enAttente = toutes.filter { it.facture.statut.attendPaiement }
            val enRetard = enAttente.filter { it.facture.enRetard(jour) }
            CompteursFactures(
                enAttente = enAttente.size,
                montantEnAttente = enAttente.sumOf { it.totalTtc },
                enRetard = enRetard.size,
                montantEnRetard = enRetard.sumOf { it.totalTtc },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_MS), CompteursFactures())

    private val _ouverte = MutableStateFlow<String?>(null)

    /** La facture ouverte, ou `null` quand l'écran montre la liste. */
    val ouverte: StateFlow<String?> = _ouverte.asStateFlow()

    /**
     * Retenue par son identifiant et non par sa valeur : ce qu'affiche l'écran
     * vient alors toujours de la base, si bien qu'un encaissement s'y voit sans
     * rien recopier et qu'une suppression le referme d'elle-même.
     */
    val complete: StateFlow<FactureComplete?> = _ouverte
        .flatMapLatest { id -> if (id == null) flowOf(null) else factures.observerComplete(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_MS), null)

    private val _documentPret = MutableStateFlow<Uri?>(null)

    val documentPret: StateFlow<Uri?> = _documentPret.asStateFlow()

    private val _echecExport = MutableStateFlow(false)

    val echecExport: StateFlow<Boolean> = _echecExport.asStateFlow()

    // — La liste ————————————————————————————————————————————————————————

    fun onOuvrir(facture: Facture) {
        _aujourdhui.value = LocalDate.now()
        _ouverte.value = facture.id
    }

    fun onOuvrir(id: String) {
        _aujourdhui.value = LocalDate.now()
        _ouverte.value = id
    }

    fun onFermer() {
        _ouverte.value = null
    }

    // — Le cycle de vie d'une facture ————————————————————————————————

    /**
     * Émet la facture : numéro, date et échéance.
     *
     * Irréversible par construction — le numéro est consommé — d'où la
     * confirmation à l'écran. C'est aussi ce qui la rend envoyable : une
     * facture sans numéro n'en est pas une.
     */
    fun onEmettre() {
        val courante = complete.value?.facture ?: return
        viewModelScope.launch {
            factures.emettre(courante, reglages.value)
        }
    }

    fun onPayee() {
        val courante = complete.value?.facture ?: return
        viewModelScope.launch { factures.marquerPayee(courante) }
    }

    fun onImpayee() {
        val courante = complete.value?.facture ?: return
        viewModelScope.launch { factures.marquerImpayee(courante) }
    }

    fun onAnnuler() {
        val courante = complete.value?.facture ?: return
        viewModelScope.launch { factures.annuler(courante) }
    }

    /** Ne fait rien sur une facture numérotée : c'est le dépôt qui refuse. */
    fun onSupprimer() {
        val courante = complete.value?.facture ?: return
        viewModelScope.launch {
            if (factures.supprimer(courante)) _ouverte.value = null
        }
    }

    fun onObjet(texte: String) {
        val courante = complete.value?.facture ?: return
        viewModelScope.launch { factures.enregistrer(courante.copy(objet = texte)) }
    }

    fun onAjouterLigne(designation: String, quantite: Double, unite: String, prix: Double) {
        val courante = complete.value?.facture ?: return
        viewModelScope.launch { factures.ajouterLigne(courante, designation, quantite, unite, prix) }
    }

    fun onSupprimerLigne(ligne: LigneFacture) {
        val courante = complete.value?.facture ?: return
        viewModelScope.launch { factures.supprimerLigne(courante, ligne.id) }
    }

    // — L'envoi ————————————————————————————————————————————————————————

    /**
     * Produit le PDF et l'annonce à l'écran, qui ouvre le sélecteur.
     *
     * Une facture non émise n'est pas exportable : elle n'a pas de numéro, et un
     * document sans numéro qui part chez un client est une facture irrégulière.
     * L'écran ne propose donc le bouton qu'après émission, et le ViewModel le
     * revérifie — les deux gardes valent mieux qu'une.
     */
    fun onExporterPdf() {
        val courante = complete.value ?: return
        if (!courante.facture.numerotee) return
        viewModelScope.launch {
            val document = DocumentFacture.de(
                facture = courante,
                parametres = reglages.value,
                client = carnet.value.firstOrNull { it.id == courante.facture.clientId },
            )
            val produit = pdf.produire(document)
            if (produit != null) _documentPret.value = produit else _echecExport.value = true
        }
    }

    /** L'écran a ouvert le partage : le document n'a plus à être annoncé. */
    fun onDocumentPartage() {
        _documentPret.value = null
    }

    fun onEchecVu() {
        _echecExport.value = false
    }

    /**
     * Note qu'une relance est partie.
     *
     * Appelé après l'ouverture du sélecteur et non avant : on ne sait pas si le
     * message a été envoyé, mais on sait que l'utilisateur a fait le geste. Le
     * contraire — noter la relance avant — aurait fait disparaître de la liste
     * une facture qu'on a finalement renoncé à relancer.
     */
    fun onRelancee(facture: Facture) {
        viewModelScope.launch { factures.marquerRelancee(facture) }
    }

    // — La création ————————————————————————————————————————————————————

    /**
     * La facture d'une intervention terminée, créée et **ouverte**.
     *
     * Appelée depuis l'écran d'intervention. Elle ne crée rien si l'intervention
     * a déjà sa facture : c'est le dépôt qui le garantit, et ce qui s'ouvre alors
     * est celle qui existe.
     */
    fun onFacturerIntervention(intervention: Intervention) {
        viewModelScope.launch {
            val facture = factures.creerDepuisIntervention(
                intervention = intervention,
                pieces = suivi.observerPieces(intervention.id).first(),
                mouvements = suivi.observerMouvements(intervention.id).first(),
                parametres = parametres.lire(),
                // Celui qui paie, et non celui chez qui on est allé : c'est son
                // adresse qui doit figurer sur la facture.
                client = carnet.value.firstOrNull {
                    it.id == (intervention.clientFactureId ?: intervention.clientId)
                },
            )
            _ouverte.value = facture.id
        }
    }

    /** La facture d'un devis accepté : ses lignes, à l'identique. */
    fun onFacturerDevis(devis: DevisComplet) {
        viewModelScope.launch {
            val facture = factures.creerDepuisDevis(
                devis = devis,
                parametres = parametres.lire(),
                client = carnet.value.firstOrNull { it.id == devis.devis.clientId },
            )
            _ouverte.value = facture.id
        }
    }

    /** Les factures qu'il est temps de relancer, pour l'accueil. */
    val aRelancer: StateFlow<List<FactureChiffree>> =
        combine(liste, _aujourdhui) { toutes, jour ->
            toutes.filter { it.facture.aRelancer(jour) }.sortedBy { it.facture.echeanceLe }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_MS), emptyList())

    companion object {

        private const val TEMPS_ARRET_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                FacturesViewModel(
                    conteneur.factures,
                    conteneur.suivi,
                    conteneur.clients,
                    conteneur.parametres,
                    ProducteurPdfAndroid(conteneur.documents, conteneur.photos),
                )
            }
        }
    }
}
