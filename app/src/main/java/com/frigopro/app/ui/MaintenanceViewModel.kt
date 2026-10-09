package com.frigopro.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.AffectationGamme
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.EcheanceMaintenance
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.GammeMaintenance
import com.frigopro.app.data.MaintenanceRepository
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.Periodicite
import com.frigopro.app.data.PlanMaintenance
import com.frigopro.app.data.PointGamme
import com.frigopro.app.data.RealisationGamme
import com.frigopro.app.data.ReleveGamme
import com.frigopro.app.data.SuiviMaintenance
import com.frigopro.app.data.Technicien
import com.frigopro.app.data.TechnicienRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Le plan de maintenance préventive : les gammes qu'on tient, et ce que le
 * calendrier doit.
 *
 * Il observe cinq flux et n'en garde aucun calcul : les échéances se recomposent
 * par [PlanMaintenance.echeances], qui est une fonction pure. C'est ce qui permet
 * de changer la périodicité d'une gamme sans rien régénérer — l'écran suivant la
 * recalcule, et aucune table d'occurrences n'a besoin d'être rattrapée.
 */
class MaintenanceViewModel(
    private val maintenance: MaintenanceRepository,
    private val equipements: EquipementRepository,
    private val clients: ClientRepository,
    private val techniciens: TechnicienRepository,
    private val parametres: ParametresRepository,
    /** Même interface, et même raison, que dans [DevisViewModel]. */
    private val pdf: ProducteurPdf,
    /**
     * Le jour courant, relu à chaque collecte plutôt que figé à la construction :
     * le ViewModel survit à une mise en arrière-plan, et l'application rouverte le
     * lendemain matin doit compter le retard du lendemain. Même raison que dans
     * [AujourdhuiViewModel].
     */
    private val aujourdhui: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val gammes: StateFlow<List<GammeMaintenance>> = maintenance.gammes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    val points: StateFlow<List<PointGamme>> = maintenance.points
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    val affectations: StateFlow<List<AffectationGamme>> = maintenance.affectations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    val releves: StateFlow<List<ReleveGamme>> = maintenance.releves
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    /**
     * Toutes les échéances du parc, la plus urgente d'abord.
     *
     * Cinq flux croisés en mémoire plutôt qu'une jointure SQL, pour la raison de
     * `LigneTournee` : ils sont déjà observés, et le calcul d'échéance est en
     * Kotlin. Sur un site de plusieurs centaines d'équipements cela fait quelques
     * milliers de comparaisons à chaque écriture, ce qui est sans conséquence — là
     * où une vue SQL aurait demandé d'y porter aussi l'arithmétique des mois.
     */
    val echeances: StateFlow<List<EcheanceMaintenance>> = combine(
        equipements.equipements,
        maintenance.gammes,
        maintenance.affectations,
        maintenance.releves,
        clients.clients,
    ) { parc, lesGammes, lesAffectations, lesReleves, carnet ->
        val noms = carnet.associate { it.id to it.nom }
        PlanMaintenance.echeances(
            equipements = parc,
            gammes = lesGammes,
            affectations = lesAffectations,
            releves = lesReleves,
            nomDuClient = { id -> noms[id].orEmpty() },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
        emptyList(),
    )

    /**
     * Ce qui appelle une action : en retard, puis dû dans le préavis de sa cadence.
     *
     * C'est ce que l'**accueil** annonce, plafonné à trois lignes comme les
     * factures échues et les articles en manque : une visite préventive est le
     * seul travail du métier que personne ne vient demander, et l'accueil est
     * donc le seul endroit où elle peut se rappeler d'elle-même.
     *
     * Le jour est relu à chaque émission et non capturé une fois : l'application
     * rouverte le lendemain matin doit compter le retard du lendemain. Le flux
     * ne réémet cependant que sur une écriture, si bien qu'une application
     * laissée ouverte toute la nuit montre l'état de la veille — c'est sans
     * conséquence ici, puisque la liste ne fait que **manquer** une visite qui
     * vient de tomber, et le Préventif la recalcule à l'ouverture.
     */
    val aFaire: StateFlow<List<EcheanceMaintenance>> = echeances
        .map { liste -> liste.filter { it.statut(aujourdhui()).appelleUneAction } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            emptyList(),
        )

    /**
     * Le taux de réalisation de chaque gamme sur les douze derniers mois, par
     * identifiant de gamme.
     *
     * **Un flux et non une fonction qui lirait `.value`**, et la distinction est
     * celle qui avait fait partir des factures sans logo : le compte demande que
     * trois flux soient collectés, et `.value` sur un flux que personne ne
     * collecte reste à sa valeur initiale pour toujours. L'écran des Réglages
     * n'observait pas le journal des visites — le taux y aurait affiché zéro
     * visite faite sur toutes les gammes, ce qui est le genre de chiffre faux
     * qu'on croit. En flux, la dépendance est portée par le type et l'écran ne
     * peut plus l'oublier.
     *
     * Douze mois glissants, et rien n'en est stocké : un taux en base aurait
     * cessé d'être juste le lendemain. Même règle que le retard d'une facture et
     * que les échéances F-Gas.
     */
    val realisations: StateFlow<Map<String, RealisationGamme>> = combine(
        maintenance.gammes,
        maintenance.affectations,
        maintenance.releves,
    ) { lesGammes, lesAffectations, lesReleves ->
        val fin = aujourdhui()
        lesGammes.associate { gamme ->
            gamme.id to PlanMaintenance.realisation(
                gamme = gamme,
                affectations = lesAffectations,
                releves = lesReleves,
                debut = fin.minusYears(1),
                fin = fin,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
        emptyMap(),
    )

    // — Les gammes et leurs points ———————————————————————————————————————————

    private val _gammeOuverte = MutableStateFlow<String?>(null)

    /**
     * La gamme en cours d'édition, **retenue par son identifiant** et non par sa
     * valeur : ce qu'affiche la boîte vient alors toujours de la base, si bien
     * qu'un renommage s'y voit sans rien recopier et qu'une suppression la referme
     * d'elle-même. Même motif que la fiche machine ouverte.
     */
    val gammeOuverte: StateFlow<String?> = _gammeOuverte.asStateFlow()

    fun onOuvrirGamme(gammeId: String?) {
        _gammeOuverte.value = gammeId
    }

    fun onFermerGamme() {
        _gammeOuverte.value = null
    }

    /** Crée une gamme et l'ouvre : on vient d'en créer une pour la garnir. */
    fun onCreerGamme(libelle: String, periodicite: Periodicite) {
        val gamme = GammeMaintenance(
            libelle = libelle,
            periodicite = periodicite,
            rang = gammes.value.size,
        )
        viewModelScope.launch {
            if (maintenance.enregistrerGamme(gamme)) _gammeOuverte.value = gamme.id
        }
    }

    fun onRenommerGamme(gamme: GammeMaintenance, libelle: String) {
        viewModelScope.launch { maintenance.enregistrerGamme(gamme.copy(libelle = libelle)) }
    }

    /**
     * Change la cadence d'une gamme.
     *
     * Rien n'est régénéré, et c'est tout l'intérêt de ne stocker aucune
     * occurrence : la prochaine échéance de chaque machine se recalcule au pas
     * nouveau, à partir de sa dernière visite. Les visites déjà faites gardent la
     * cadence qu'elles portaient — elles ont été faites sous l'ancien contrat.
     */
    fun onPeriodicite(gamme: GammeMaintenance, periodicite: Periodicite) {
        viewModelScope.launch { maintenance.enregistrerGamme(gamme.copy(periodicite = periodicite)) }
    }

    fun onSupprimerGamme(gamme: GammeMaintenance) {
        if (_gammeOuverte.value == gamme.id) _gammeOuverte.value = null
        viewModelScope.launch { maintenance.supprimerGamme(gamme.id) }
    }

    fun onAjouterPoint(gammeId: String, libelle: String) {
        val siens = points.value.count { it.gammeId == gammeId }
        viewModelScope.launch {
            maintenance.enregistrerPoint(
                PointGamme(gammeId = gammeId, libelle = libelle, rang = siens),
            )
        }
    }

    fun onSupprimerPoint(point: PointGamme) {
        viewModelScope.launch { maintenance.supprimerPoint(point.id) }
    }

    // — Le plan d'une machine ————————————————————————————————————————————————

    fun onAffecter(equipementId: String, gammeId: String) {
        viewModelScope.launch { maintenance.affecter(equipementId, gammeId, aujourdhui()) }
    }

    fun onRetirer(equipementId: String, gammeId: String) {
        viewModelScope.launch { maintenance.retirer(equipementId, gammeId) }
    }

    /**
     * Rattache une gamme à **tout un parc** d'un seul geste.
     *
     * C'est le geste qui rend la GMAO tenable sur un site de plusieurs centaines
     * d'équipements : les rattacher un à un demanderait une soirée, et la soirée
     * ne se prendrait pas. Les unités intérieures sont incluses — un split suivi
     * sans ses unités ne voudrait rien dire.
     */
    fun onAffecterAuParc(equipements: List<Equipement>, gammeId: String) {
        val jour = aujourdhui()
        viewModelScope.launch {
            equipements.forEach { machine -> maintenance.affecter(machine.id, gammeId, jour) }
        }
    }

    /**
     * Consigne une visite faite.
     *
     * Le technicien des Réglages est recopié sur la ligne faute de mieux : c'est
     * le seul nom que l'application connaisse avec certitude, et une visite sans
     * auteur perdrait la moitié de sa valeur de preuve.
     */
    fun onConsigner(
        equipement: Equipement,
        gamme: GammeMaintenance,
        faitLe: LocalDate? = null,
        notes: String = "",
    ) {
        viewModelScope.launch {
            val nom = parametres.lire().technicien
            val connus = techniciens.techniciens.first()
            val auteur = connus.firstOrNull { it.nom == nom }
                ?: nom.takeIf { it.isNotBlank() }?.let { Technicien(nom = it) }
            maintenance.consigner(
                equipement = equipement,
                gamme = gamme,
                faitLe = faitLe ?: aujourdhui(),
                technicien = auteur,
                notes = notes,
            )
        }
    }

    fun onRetirerVisite(releve: ReleveGamme) {
        viewModelScope.launch { maintenance.retirerReleve(releve.id) }
    }

    // — L'attestation d'entretien ————————————————————————————————————————————

    private val _documentPret = MutableStateFlow<Uri?>(null)

    /** Le PDF écrit, tant que l'écran ne l'a pas partagé. Même motif qu'ailleurs. */
    val documentPret: StateFlow<Uri?> = _documentPret.asStateFlow()

    private val _echecExport = MutableStateFlow(false)

    val echecExport: StateFlow<Boolean> = _echecExport.asStateFlow()

    /**
     * Sort l'attestation d'entretien d'un client, sur une année.
     *
     * Les quatre listes sont prises **au moment de l'export** et non observées
     * en permanence, comme pour le registre des fluides : une attestation est une
     * photographie datée, et l'écran n'a rien à afficher d'elles entre deux
     * exports.
     *
     * **La fin de la période s'arrête à aujourd'hui**, et c'est le point le plus
     * facile à rater de tout le document : un contrat mensuel sur l'année 2026
     * demande douze visites, mais au 7 octobre il n'en a pu recevoir que neuf.
     * Compter jusqu'au 31 décembre aurait affiché « 9 sur 12 », soit 75 %, sur un
     * contrat parfaitement honoré — et ce chiffre-là part chez le client. C'est
     * la même erreur que de facturer une quantité non arrondie : elle ne se voit
     * qu'une fois le document envoyé.
     */
    fun onExporterAttestation(client: Client, annee: Int) {
        viewModelScope.launch {
            val jour = aujourdhui()
            val attestation = SuiviMaintenance.attestation(
                clientNom = client.nom,
                clientAdresse = client.adresseComplete,
                // Le seul client, sans ses sites : chaque site est une adresse
                // distincte avec son propre parc, et c'est le gérant de *ce*
                // magasin qui demande l'attestation de son magasin.
                clientIds = setOf(client.id),
                equipements = equipements.equipements.first(),
                gammes = maintenance.gammes.first(),
                affectations = maintenance.affectations.first(),
                releves = maintenance.releves.first(),
                debut = LocalDate.of(annee, 1, 1),
                fin = minOf(LocalDate.of(annee, 12, 31), jour),
            )
            val document = DocumentAttestation.de(attestation, parametres.lire(), jour)
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

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                MaintenanceViewModel(
                    conteneur.maintenance,
                    conteneur.equipements,
                    conteneur.clients,
                    conteneur.techniciens,
                    conteneur.parametres,
                    ProducteurPdfAndroid(conteneur.documents, conteneur.photos),
                )
            }
        }
    }
}
