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
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.Photo
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

    /** Inscrire une machine chez ce client. */
    data class Creation(val clientId: String) : DialogueEquipement

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
) : ViewModel() {

    /** Tout le parc, trié par nom. L'écran en tire les machines de chaque client. */
    val parc: StateFlow<List<Equipement>> = equipements.equipements
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
                is DialogueEquipement.Creation ->
                    equipements.trouverOuCreer(ouvert.clientId, nom)

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
                EquipementsViewModel(conteneur.equipements, conteneur.interventions)
            }
        }
    }
}
