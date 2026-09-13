package com.frigopro.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.ModeDeplacement
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.PrestationRepository
import com.frigopro.app.data.TypeIntervention
import com.frigopro.app.data.TypeInterventionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Boîte de dialogue ouverte par l'écran Réglages.
 *
 * Un seul état plutôt que trois booléens : deux dialogues ne peuvent pas être
 * ouverts en même temps, et le dire au type supprime la question.
 */
sealed interface DialogueReglages {

    data object Creation : DialogueReglages

    data class Renommage(val type: TypeIntervention) : DialogueReglages

    data class Suppression(val type: TypeIntervention) : DialogueReglages
}

/**
 * Détient l'état de l'onglet « Réglages ».
 *
 * Il ne tient que les types d'intervention pour l'instant ; c'est ici que les
 * réglages suivants viendront se ranger.
 */
class ReglagesViewModel(
    private val typeRepository: TypeInterventionRepository,
    private val parametresRepository: ParametresRepository,
    private val prestationRepository: PrestationRepository,
) : ViewModel() {

    /** Les réglages, jamais `null` : voir [ParametresRepository]. */
    val parametres: StateFlow<Parametres> = parametresRepository.parametres
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = Parametres(),
        )

    fun onThemeSombre(actif: Boolean) = modifier { it.copy(themeSombre = actif) }

    fun onModeGants(actif: Boolean) = modifier { it.copy(modeGants = actif) }

    fun onChronoAuto(actif: Boolean) = modifier { it.copy(chronoAuto = actif) }

    fun onTechnicien(nom: String) = modifier { it.copy(technicien = nom) }

    fun onAttestation(mention: String) = modifier { it.copy(attestation = mention) }

    fun onTauxHoraire(taux: Double) = modifier { it.copy(tauxHoraire = taux) }

    fun onTauxTva(taux: Double) = modifier { it.copy(tauxTva = taux) }

    // — L'identité de l'entreprise : l'en-tête des documents —————————————————

    fun onEntreprise(nom: String) = modifier { it.copy(entreprise = nom) }

    fun onEntrepriseAdresse(adresse: String) = modifier { it.copy(entrepriseAdresse = adresse) }

    fun onEntrepriseTelephone(numero: String) = modifier { it.copy(entrepriseTelephone = numero) }

    fun onEntrepriseEmail(courriel: String) = modifier { it.copy(entrepriseEmail = courriel) }

    fun onEntrepriseSiret(siret: String) = modifier { it.copy(entrepriseSiret = siret) }

    /**
     * Le régime de TVA.
     *
     * Il ne recalcule aucun devis déjà établi : chacun en porte une copie posée à
     * sa création (voir `Devis.assujettiTva`). Le changer ici ne vaut que pour les
     * suivants — un devis envoyé en franchise en base doit rester tel qu'il était.
     */
    fun onAssujettiTva(assujetti: Boolean) = modifier { it.copy(assujettiTva = assujetti) }

    // — Le tarif de déplacement ————————————————————————————————————————————

    fun onAdresseDepart(adresse: String) = modifier { it.copy(adresseDepart = adresse) }

    fun onModeDeplacement(mode: ModeDeplacement) = modifier { it.copy(modeDeplacement = mode) }

    /**
     * Les prix du déplacement.
     *
     * Un prix négatif est ramené à zéro plutôt que refusé, comme ceux du
     * catalogue : à zéro la ligne se voit et appelle une correction, alors qu'un
     * tarif négatif produirait un devis qui paie le client.
     */
    fun onPrixKm(prix: Double?) = modifier { it.copy(prixKm = prix?.coerceAtLeast(0.0) ?: 0.0) }

    fun onPrixHeureTrajet(prix: Double?) =
        modifier { it.copy(prixHeureTrajet = prix?.coerceAtLeast(0.0) ?: 0.0) }

    fun onMinimumDeplacement(prix: Double?) =
        modifier { it.copy(minimumDeplacement = prix?.coerceAtLeast(0.0) ?: 0.0) }

    fun onRefacturerPeages(refacturer: Boolean) =
        modifier { it.copy(refacturerPeages = refacturer) }


    /**
     * Pose le logo choisi dans la galerie.
     *
     * Le dépôt s'occupe du fichier et de la ligne ensemble, et efface le logo
     * remplacé : sans cela, changer trois fois de logo laisserait trois images
     * orphelines dans le dossier, que plus aucun écran ne montrerait et qui
     * grossiraient chaque archive de sauvegarde.
     */
    fun onLogoChoisi(source: Uri) {
        viewModelScope.launch { parametresRepository.poserLogo(source) }
    }

    fun onRetirerLogo() {
        viewModelScope.launch { parametresRepository.retirerLogo() }
    }

    /** Décode le logo pour l'aperçu, comme une photo de machine. */
    suspend fun charger(nom: String, coteMax: Int) = parametresRepository.charger(nom, coteMax)

    /**
     * Le catalogue, et ses prix.
     *
     * Il est livré avec les intitulés du métier et sans les tarifs ; c'est donc
     * ici qu'ils se posent, et nulle part ailleurs.
     */
    val prestations: StateFlow<List<Prestation>> = prestationRepository.prestations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS), emptyList())

    /**
     * Pose ou corrige une prestation du catalogue.
     *
     * Un seul point d'entrée pour la création et la correction, parce que le dépôt
     * ne fait pas la différence : il remplace la ligne de même identifiant, et une
     * prestation neuve en porte un qui n'existe pas encore. C'est lui qui refuse un
     * prix négatif et un intitulé vide — une remise se saisit en réduisant le prix,
     * pas en inversant son signe.
     */
    fun onEnregistrerPrestation(prestation: Prestation) {
        viewModelScope.launch { prestationRepository.enregistrer(prestation) }
    }

    /**
     * Retire une prestation du catalogue.
     *
     * Sans effet sur les devis : une ligne de devis recopie l'intitulé et le prix
     * (voir [PrestationDao]), donc un devis déjà envoyé garde ce qu'il disait.
     */
    fun onSupprimerPrestation(prestation: Prestation) {
        viewModelScope.launch { prestationRepository.supprimer(prestation.id) }
    }

    private fun modifier(transformation: (Parametres) -> Parametres) {
        viewModelScope.launch { parametresRepository.modifier(transformation) }
    }


    val types: StateFlow<List<TypeIntervention>> = typeRepository.types
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptyList(),
        )

    private val _dialogue = MutableStateFlow<DialogueReglages?>(null)

    val dialogue: StateFlow<DialogueReglages?> = _dialogue.asStateFlow()

    fun onAjouterType() {
        _dialogue.value = DialogueReglages.Creation
    }

    fun onRenommerType(type: TypeIntervention) {
        _dialogue.value = DialogueReglages.Renommage(type)
    }

    fun onSupprimerType(type: TypeIntervention) {
        _dialogue.value = DialogueReglages.Suppression(type)
    }

    fun onFermerDialogue() {
        _dialogue.value = null
    }

    /**
     * Valide la saisie du dialogue ouvert : création ou renommage selon le cas.
     * Un renommage se répercute sur les interventions qui désignent le type.
     */
    fun onValiderIntitule(libelle: String) {
        if (libelle.isBlank()) return

        when (val ouvert = _dialogue.value) {
            DialogueReglages.Creation -> {
                _dialogue.value = null
                viewModelScope.launch { typeRepository.trouverOuCreer(libelle) }
            }

            is DialogueReglages.Renommage -> {
                _dialogue.value = null
                viewModelScope.launch {
                    typeRepository.enregistrer(ouvert.type.copy(libelle = libelle))
                }
            }

            else -> Unit
        }
    }

    fun onConfirmerSuppression() {
        val ouvert = _dialogue.value as? DialogueReglages.Suppression ?: return

        _dialogue.value = null
        viewModelScope.launch { typeRepository.supprimer(ouvert.type.id) }
    }

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                ReglagesViewModel(
                    conteneur.typesIntervention,
                    conteneur.parametres,
                    conteneur.prestations,
                )
            }
        }
    }
}
