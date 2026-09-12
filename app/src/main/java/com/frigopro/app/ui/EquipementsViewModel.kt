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
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.GroupeMachines
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.Photo
import com.frigopro.app.data.Releve
import com.frigopro.app.data.SuiviRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ce qu'une boîte de dialogue du parc demande. Une seule à la fois, d'où le type unique. */
sealed interface DialogueEquipement {

    /**
     * Inscrire une machine chez ce client, ou une unité intérieure sous un
     * groupe quand [parent] est donné.
     *
     * Le même cas sert les deux : nommer et refuser un doublon se font de la même
     * façon, seul le vocabulaire change. Ce qui diffère est le périmètre du
     * doublon — chez le client pour un groupe, dans le groupe pour une unité —, et
     * c'est l'écran qui le tranche.
     */
    data class Creation(val clientId: String, val parent: Equipement? = null) : DialogueEquipement

    data class Renommage(val equipement: Equipement) : DialogueEquipement

    data class Suppression(val equipement: Equipement) : DialogueEquipement
}

/**
 * Détient le parc de machines : la liste sous chaque client, la fiche ouverte,
 * ses photos et son historique.
 *
 * La fiche ouverte est retenue **par son identifiant** et non par sa valeur :
 * la machine affichée est alors toujours celle de la base, si bien qu'un
 * renommage se voit dans le titre sans rien recopier, et qu'une suppression
 * referme l'écran d'elle-même.
 */
class EquipementsViewModel(
    private val equipements: EquipementRepository,
    private val interventions: InterventionRepository,
    private val suivi: SuiviRepository,
) : ViewModel() {

    /** Tout le parc, trié par nom. L'écran en tire les machines de chaque client. */
    val parc: StateFlow<List<Equipement>> = equipements.equipements
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    /**
     * Le parc rangé par groupe : c'est ce que le carnet montre.
     *
     * Un bi-split est **un** appareil chez le client et non trois lignes : le
     * compter comme trois machines donnerait un parc faux, et mettrait les unités
     * au même rang que le groupe dont elles dépendent.
     */
    val groupes: StateFlow<List<GroupeMachines>> = equipements.parGroupe
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    private val _ouverte = MutableStateFlow<String?>(null)

    /** Machine dont la fiche est ouverte, ou `null` quand l'écran montre le carnet. */
    val ouverte: StateFlow<Equipement?> = combine(parc, _ouverte) { liste, id ->
        liste.firstOrNull { it.id == id }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = null,
        )

    /**
     * Les unités intérieures de la machine ouverte, vide si elle n'en a pas — ou
     * si c'est elle-même une unité : la hiérarchie est **à un seul niveau**, une
     * unité ne porte pas d'unité. Le modèle l'autoriserait (`parentId` est libre),
     * et c'est ici qu'on s'interdit de s'en servir : un arbre de profondeur
     * quelconque demanderait un écran qui sache le parcourir, pour un besoin qui
     * n'existe pas — un split se branche sur un groupe, pas sur un autre split.
     */
    val unitesOuvertes: StateFlow<List<Equipement>> = combine(parc, _ouverte) { liste, id ->
        val ouverte = liste.firstOrNull { it.id == id }
        if (ouverte == null || ouverte.estUnite) emptyList() else liste.filter { it.parentId == id }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    /**
     * Le groupe de la machine ouverte, quand celle-ci est une unité.
     *
     * La fiche d'une unité doit dire de quoi elle dépend : « Salon » tout seul ne
     * se retrouve pas dans un parc de vingt machines, et on ouvre une unité pour
     * la photographier sans perdre le fil de l'appareil auquel elle appartient.
     */
    val groupeOuvert: StateFlow<Equipement?> = combine(parc, _ouverte) { liste, id ->
        val parent = liste.firstOrNull { it.id == id }?.parentId
        parent?.let { identifiant -> liste.firstOrNull { it.id == identifiant } }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val photosOuvertes: StateFlow<List<Photo>> = _ouverte
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else equipements.observerPhotos(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    /** « Qu'a-t-on déjà fait sur celle-ci ? », du plus récent au plus ancien. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val historique: StateFlow<List<Intervention>> = _ouverte
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else interventions.observerParEquipement(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    /**
     * Les relevés déjà pris sur cette machine, toutes visites confondues.
     *
     * C'est ce qui trace la tendance de la fiche : une surchauffe qui monte
     * visite après visite dit quelque chose qu'aucun relevé isolé ne dit.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val relevesMachine: StateFlow<List<Releve>> = _ouverte
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else suivi.observerRelevesMachine(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    private val _dialogue = MutableStateFlow<DialogueEquipement?>(null)

    val dialogue: StateFlow<DialogueEquipement?> = _dialogue.asStateFlow()

    private val _agrandie = MutableStateFlow<Photo?>(null)

    /** Photo affichée en plein écran, ou `null`. */
    val agrandie: StateFlow<Photo?> = _agrandie.asStateFlow()

    fun onOuvrir(equipement: Equipement) {
        _ouverte.value = equipement.id
    }

    fun onFermer() {
        _ouverte.value = null
    }

    fun onAjouterMachine(clientId: String) {
        _dialogue.value = DialogueEquipement.Creation(clientId)
    }

    /**
     * Ajoute une unité intérieure au groupe ouvert.
     *
     * Poser un bi-split, c'est un groupe extérieur et deux unités : chacune se
     * nomme — « Salon », « Chambre » — et se photographie pour elle-même, parce
     * que c'est une unité précise qui fuit ou qui encrasse son filtre, pas
     * « l'installation ».
     */
    fun onAjouterUnite(groupe: Equipement) {
        _dialogue.value = DialogueEquipement.Creation(groupe.clientId, parent = groupe)
    }

    fun onRenommerMachine(equipement: Equipement) {
        _dialogue.value = DialogueEquipement.Renommage(equipement)
    }

    fun onSupprimerMachine(equipement: Equipement) {
        _dialogue.value = DialogueEquipement.Suppression(equipement)
    }

    fun onFermerDialogue() {
        _dialogue.value = null
    }

    /** Valide la saisie de la boîte ouverte, création ou renommage. */
    fun onValiderNom(nom: String) {
        val ouvert = _dialogue.value ?: return
        if (nom.isBlank()) return

        _dialogue.value = null
        viewModelScope.launch {
            when (ouvert) {
                is DialogueEquipement.Creation -> {
                    val parent = ouvert.parent
                    if (parent == null) {
                        equipements.trouverOuCreer(ouvert.clientId, nom)
                    } else {
                        equipements.ajouterUnite(parent, nom)
                    }
                }

                is DialogueEquipement.Renommage ->
                    equipements.enregistrer(ouvert.equipement.copy(nom = nom))

                is DialogueEquipement.Suppression -> Unit
            }
        }
    }

    /**
     * Supprime la machine et referme sa fiche. Les interventions passées gardent
     * son nom : c'est le dépôt qui s'en charge.
     */
    fun onConfirmerSuppression() {
        val ouvert = _dialogue.value as? DialogueEquipement.Suppression ?: return

        _dialogue.value = null
        if (_ouverte.value == ouvert.equipement.id) _ouverte.value = null
        viewModelScope.launch { equipements.supprimer(ouvert.equipement.id) }
    }

    /**
     * Enregistre la plaque signalétique et le fluide.
     *
     * Passe par le même chemin qu'un renommage : le nom peut avoir changé au
     * passage, et il doit alors suivre les interventions passées.
     */
    fun onEnregistrerFiche(equipement: Equipement) {
        viewModelScope.launch { equipements.enregistrer(equipement) }
    }

    /** Le fichier que l'application d'appareil photo va remplir. */
    fun preparerCapture(): Capture = equipements.preparerCapture()

    /**
     * Range la photo qui vient d'être prise. Une prise de vue abandonnée ne
     * laisse rien derrière elle : le dépôt efface le fichier vide.
     */
    fun onCapture(categorie: CategoriePhoto, nom: String) {
        val equipementId = _ouverte.value ?: return
        viewModelScope.launch { equipements.ajouterCapture(equipementId, categorie, nom) }
    }

    fun onPhotoChoisie(categorie: CategoriePhoto, source: Uri) {
        val equipementId = _ouverte.value ?: return
        viewModelScope.launch { equipements.ajouterDepuisGalerie(equipementId, categorie, source) }
    }

    fun onAgrandir(photo: Photo) {
        _agrandie.value = photo
    }

    fun onFermerAgrandissement() {
        _agrandie.value = null
    }

    fun onSupprimerPhoto(photo: Photo) {
        if (_agrandie.value?.id == photo.id) _agrandie.value = null
        viewModelScope.launch { equipements.supprimerPhoto(photo) }
    }

    /**
     * Décode une image à la taille demandée. Exposé comme fonction plutôt que
     * comme flux : chaque vignette charge la sienne, quand elle s'affiche.
     */
    suspend fun charger(nom: String, coteMax: Int): Bitmap? = equipements.charger(nom, coteMax)

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                EquipementsViewModel(
                    conteneur.equipements,
                    conteneur.interventions,
                    conteneur.suivi,
                )
            }
        }
    }
}
