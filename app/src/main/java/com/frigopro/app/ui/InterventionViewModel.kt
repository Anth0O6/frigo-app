package com.frigopro.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.Capture
import com.frigopro.app.data.CategoriePhoto
import com.frigopro.app.data.Client
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.Depannage
import com.frigopro.app.data.Diagnostic
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.MouvementFluide
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.Photo
import com.frigopro.app.data.PointChecklist
import com.frigopro.app.data.PiecePosee
import com.frigopro.app.data.Releve
import com.frigopro.app.data.SensFluide
import com.frigopro.app.data.SuiviRepository
import com.frigopro.app.data.VerificationFluideRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** Les quatre volets de l'écran d'une intervention. */
enum class OngletIntervention(val libelle: String) {
    /**
     * Ce qu'on lit en arrivant : le créneau, l'adresse, la machine, et la liste
     * à cocher. Il vient en premier parce que c'est l'ordre du terrain — on
     * regarde où l'on est et ce qu'on a à vérifier avant de sortir le manomètre.
     */
    FICHE("Fiche"),
    RELEVES("Relevés"),
    PIECES("Pièces"),
    PHOTOS("Photos"),
    RAPPORT("Rapport"),
}

/**
 * Tout ce que l'écran d'une intervention montre, réuni.
 *
 * Un seul objet plutôt que huit flux séparés : l'écran a besoin de la plupart
 * de ces valeurs en même temps — le fluide de la machine pour intituler un
 * mouvement, le relevé pour le diagnostic, le chrono pour le titre — et les
 * recomposer une par une ferait clignoter l'affichage à chaque écriture.
 */
data class EtatIntervention(
    val intervention: Intervention,
    val client: Client? = null,
    val equipement: Equipement? = null,
    val releve: Releve? = null,
    val mouvements: List<MouvementFluide> = emptyList(),
    val pieces: List<PiecePosee> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val checklist: List<PointChecklist> = emptyList(),
    val parametres: Parametres = Parametres(),
) {

    /** Combien de points sont cochés, et sur combien. */
    val pointsFaits: Int get() = checklist.count { it.fait }

    /**
     * La checklist est-elle finie ?
     *
     * Une checklist vide n'est pas « finie » : elle n'est pas encore posée, et
     * annoncer 0/0 comme un succès dirait le contraire de ce qui est vrai.
     */
    val checklistFinie: Boolean get() = checklist.isNotEmpty() && pointsFaits == checklist.size

    /** Ce que les relevés suggèrent, ou `null` s'ils n'en disent pas assez. */
    val diagnostic: Diagnostic? get() = releve?.let(Depannage::analyser)

    /** Fluide de la machine, ou celui déjà employé dans un mouvement. */
    val fluide: String
        get() = equipement?.fluide?.takeIf { it.isNotBlank() }
            ?: mouvements.firstOrNull()?.fluide.orEmpty()

    val ajoute: Double get() = mouvements.filter { it.sens == SensFluide.AJOUT }.sumOf { it.masseKg }

    val recupere: Double
        get() = mouvements.filter { it.sens == SensFluide.RECUPERATION }.sumOf { it.masseKg }

    val photosAvant: List<Photo> get() = photos.filter { it.categorie == CategoriePhoto.AVANT }

    val photosApres: List<Photo> get() = photos.filter { it.categorie == CategoriePhoto.APRES }
}

/**
 * L'écran d'une intervention : le chronomètre, les relevés, le fluide, les
 * pièces, les photos et le compte-rendu.
 *
 * Comme [EquipementsViewModel], il retient l'intervention ouverte **par son
 * identifiant** : ce qu'affiche l'écran vient alors toujours de la base, si
 * bien qu'un changement de statut ou une clôture s'y voient sans rien
 * recopier, et qu'une suppression referme l'écran d'elle-même.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InterventionViewModel(
    private val interventions: InterventionRepository,
    private val suivi: SuiviRepository,
    private val equipements: EquipementRepository,
    private val clients: ClientRepository,
    private val parametres: ParametresRepository,
    private val verifications: VerificationFluideRepository,
) : ViewModel() {

    private val _ouverte = MutableStateFlow<String?>(null)

    /** Intervention ouverte, ou `null` quand l'écran montre la tournée. */
    val ouverte: StateFlow<String?> = _ouverte.asStateFlow()

    private val _onglet = MutableStateFlow(OngletIntervention.RELEVES)

    val onglet: StateFlow<OngletIntervention> = _onglet.asStateFlow()

    private val _depannageOuvert = MutableStateFlow(false)

    /** L'aide au dépannage, ouverte par-dessus le reste. */
    val depannageOuvert: StateFlow<Boolean> = _depannageOuvert.asStateFlow()

    private val _agrandie = MutableStateFlow<Photo?>(null)

    val agrandie: StateFlow<Photo?> = _agrandie.asStateFlow()

    /**
     * Les fluides dont l'utilisateur a contrôlé la courbe de saturation.
     *
     * Exposé à part plutôt que porté par [EtatIntervention] : l'état est assemblé
     * par deux `combine` déjà saturés — cinq flux est le maximum des surcharges
     * typées —, et surtout cette donnée ne dépend pas de l'intervention ouverte.
     * Une vérification vaut pour toutes.
     */
    val fluidesVerifies: StateFlow<Set<String>> = verifications.verifies
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptySet(),
        )

    /**
     * L'état complet de l'intervention ouverte.
     *
     * `flatMapLatest` sur l'identifiant : changer d'intervention rebranche
     * tous les flux d'un coup, et n'en observer aucun quand l'écran est fermé.
     */
    val etat: StateFlow<EtatIntervention?> = _ouverte
        .flatMapLatest { id -> if (id == null) flowOf(null) else observerTout(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = null,
        )

    /**
     * Le temps écoulé, réémis chaque seconde tant que le chronomètre tourne.
     *
     * La cadence est portée ici plutôt que dans l'écran : c'est une propriété
     * de la donnée — un chrono en marche *change* — et non un effet
     * d'affichage. À l'arrêt, aucun réveil : la boucle s'arrête d'elle-même.
     */
    val ecoule: StateFlow<Duration> = etat
        .flatMapLatest { courant ->
            val chrono = courant?.intervention?.chrono ?: return@flatMapLatest flowOf(Duration.ZERO)
            if (!chrono.enMarche) {
                flowOf(chrono.ecoulee(Instant.now()))
            } else {
                flow {
                    while (true) {
                        emit(chrono.ecoulee(Instant.now()))
                        delay(CADENCE_CHRONO_MS)
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = Duration.ZERO,
        )

    fun onOuvrir(intervention: Intervention) {
        _ouverte.value = intervention.id
        _onglet.value = OngletIntervention.FICHE
        // La checklist est posée à l'ouverture plutôt qu'à la création de
        // l'intervention : une intervention saisie la semaine dernière doit la
        // recevoir aussi, et le dépôt ne la pose qu'une fois.
        viewModelScope.launch { suivi.preparerChecklist(intervention.id) }
    }

    fun onBasculerPoint(point: PointChecklist) {
        viewModelScope.launch { suivi.basculerPoint(point) }
    }

    fun onFermer() {
        _ouverte.value = null
        _depannageOuvert.value = false
    }

    fun onOnglet(cible: OngletIntervention) {
        _onglet.value = cible
    }

    /**
     * Marque la courbe d'un fluide vérifiée, ou retire la marque.
     *
     * Le nom du technicien est recopié sur la vérification : s'en tenir à une date
     * laisserait la question « qui a contrôlé ça ? » sans réponse le jour où deux
     * personnes se partagent l'application, et une vérification s'assume.
     */
    fun onVerifierFluide(fluide: String, verifie: Boolean) {
        viewModelScope.launch {
            verifications.basculer(fluide, verifie, par = parametres.lire().technicien)
        }
    }

    fun onOuvrirDepannage() {
        _depannageOuvert.value = true
    }

    fun onFermerDepannage() {
        _depannageOuvert.value = false
    }

    // — Chronomètre ————————————————————————————————————————————————————————

    /** Démarre ou met en pause. Voir [InterventionRepository.basculerChrono]. */
    fun onBasculerChrono() {
        val courante = etat.value?.intervention ?: return
        viewModelScope.launch { interventions.basculerChrono(courante) }
    }

    /** Clôt l'intervention : chrono arrêté, statut terminé, numéro attribué. */
    fun onCloturer() {
        val courante = etat.value?.intervention ?: return
        viewModelScope.launch {
            interventions.cloturer(courante)
            _onglet.value = OngletIntervention.RAPPORT
        }
    }

    // — Relevés ————————————————————————————————————————————————————————————

    /**
     * Enregistre une grandeur du relevé.
     *
     * Le relevé est créé à la première valeur saisie et effacé quand la
     * dernière est effacée : le technicien n'a jamais à le « créer », il
     * remplit des cases.
     */
    fun onReleve(transformation: (Releve) -> Releve) {
        val courant = etat.value ?: return
        val base = courant.releve ?: Releve(
            interventionId = courant.intervention.id,
            equipementId = courant.intervention.equipementId,
        )
        viewModelScope.launch { suivi.enregistrerReleve(transformation(base)) }
    }

    fun onBp(valeur: Double?) = onReleve { it.copy(bpBar = valeur) }

    fun onHp(valeur: Double?) = onReleve { it.copy(hpBar = valeur) }

    fun onSurchauffe(valeur: Double?) = onReleve { it.copy(surchauffeK = valeur) }

    fun onSousRefroidissement(valeur: Double?) = onReleve { it.copy(sousRefroidissementK = valeur) }

    // — Fluide —————————————————————————————————————————————————————————————

    /** Consigne un mouvement de fluide au registre. */
    fun onMouvement(sens: SensFluide, masseKg: Double, fluide: String) {
        val courant = etat.value ?: return
        viewModelScope.launch {
            suivi.enregistrerMouvement(
                MouvementFluide(
                    interventionId = courant.intervention.id,
                    equipementId = courant.intervention.equipementId,
                    fluide = fluide,
                    sens = sens,
                    masseKg = masseKg,
                ),
            )
        }
    }

    fun onSupprimerMouvement(id: String) {
        viewModelScope.launch { suivi.supprimerMouvement(id) }
    }

    // — Pièces —————————————————————————————————————————————————————————————

    fun onAjouterPiece(designation: String, reference: String, quantite: Double) {
        val courant = etat.value ?: return
        viewModelScope.launch {
            suivi.enregistrerPiece(
                PiecePosee(
                    interventionId = courant.intervention.id,
                    designation = designation,
                    reference = reference,
                    quantite = quantite,
                ),
            )
        }
    }

    fun onSupprimerPiece(id: String) {
        viewModelScope.launch { suivi.supprimerPiece(id) }
    }

    // — Photos —————————————————————————————————————————————————————————————

    fun preparerCapture(): Capture = suivi.preparerCapture()

    fun onCapture(categorie: CategoriePhoto, nom: String) {
        val id = _ouverte.value ?: return
        viewModelScope.launch { suivi.ajouterCapture(id, categorie, nom) }
    }

    fun onPhotoChoisie(categorie: CategoriePhoto, source: Uri) {
        val id = _ouverte.value ?: return
        viewModelScope.launch { suivi.ajouterDepuisGalerie(id, categorie, source) }
    }

    fun onAgrandir(photo: Photo) {
        _agrandie.value = photo
    }

    fun onFermerAgrandissement() {
        _agrandie.value = null
    }

    fun onSupprimerPhoto(photo: Photo) {
        if (_agrandie.value?.id == photo.id) _agrandie.value = null
        viewModelScope.launch { suivi.supprimerPhoto(photo) }
    }

    suspend fun charger(nom: String, coteMax: Int): Bitmap? = suivi.charger(nom, coteMax)

    // — Compte-rendu ———————————————————————————————————————————————————————

    /** Les travaux réalisés, qui sont les notes de l'intervention. */
    fun onTravaux(texte: String) {
        val courante = etat.value?.intervention ?: return
        viewModelScope.launch { interventions.enregistrer(courante.copy(notes = texte)) }
    }

    /**
     * Attache la signature du client, déjà rangée comme une image.
     *
     * L'horodatage est posé ici : c'est lui qui fait de la signature une
     * acceptation datée plutôt qu'un simple dessin.
     */
    fun onSignature(fichier: String) {
        val courante = etat.value?.intervention ?: return
        viewModelScope.launch {
            interventions.enregistrer(
                courante.copy(signatureFichier = fichier, signeeLe = Instant.now()),
            )
        }
    }

    /**
     * Range l'image de la signature et rend son nom de fichier.
     *
     * `suspend` plutôt qu'un lancement dans le `viewModelScope` : l'appelant a
     * besoin du nom pour l'attacher ensuite, et un rappel asynchrone ferait
     * courir le risque d'attacher un fichier qui n'a pas fini d'être écrit.
     */
    suspend fun rangerSignature(image: Bitmap): String? = suivi.rangerImage(image)

    fun onEffacerSignature() {
        val courante = etat.value?.intervention ?: return
        viewModelScope.launch {
            interventions.enregistrer(courante.copy(signatureFichier = null, signeeLe = null))
        }
    }

    /**
     * Réunit les neuf flux de l'intervention.
     *
     * `combine` accepte cinq flux au plus par surcharge typée ; au-delà il
     * faut passer par la variante à tableau, dont le résultat n'est plus
     * typé. Deux `combine` imbriqués gardent le typage et se lisent mieux.
     */
    private fun observerTout(id: String): Flow<EtatIntervention?> {
        val base = combine(
            interventions.observer(id),
            suivi.observerReleves(id),
            suivi.observerMouvements(id),
            suivi.observerPieces(id),
            suivi.observerPhotos(id),
        ) { intervention, releves, mouvements, pieces, photos ->
            intervention?.let {
                EtatIntervention(
                    intervention = it,
                    // Le dernier relevé : l'écran en montre un seul, celui
                    // qu'on est en train de remplir.
                    releve = releves.lastOrNull(),
                    mouvements = mouvements,
                    pieces = pieces,
                    photos = photos,
                )
            }
        }
        return combine(
            base,
            equipements.equipements,
            clients.clients,
            parametres.parametres,
            suivi.observerChecklist(id),
        ) { etat, parc, carnet, reglages, points ->
            etat?.copy(
                equipement = parc.firstOrNull { it.id == etat.intervention.equipementId },
                client = carnet.firstOrNull { it.id == etat.intervention.clientId },
                parametres = reglages,
                checklist = points,
            )
        }
    }

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        /** Une seconde : c'est la précision qu'affiche le chronomètre. */
        private const val CADENCE_CHRONO_MS = 1_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                InterventionViewModel(
                    conteneur.interventions,
                    conteneur.suivi,
                    conteneur.equipements,
                    conteneur.clients,
                    conteneur.parametres,
                    conteneur.verificationsFluide,
                )
            }
        }
    }
}
