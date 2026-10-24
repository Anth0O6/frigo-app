package com.frigopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.VerificationFluideRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * La vue Outils de l'Atelier.
 *
 * Il ne tient presque rien, et c'est la nature de la vue : un convertisseur
 * d'unités ou un bilan de puissance n'a **pas d'état à conserver**. On y entre une
 * valeur, on lit le résultat, on referme — rien là-dedans ne mérite d'aller en
 * base, et y mettre un historique de conversions aurait donné quelque chose à
 * sauvegarder, à restaurer et à migrer pour rien.
 *
 * Le seul état durable est celui des **vérifications de courbe**, qui existait
 * déjà : c'est une donnée de l'utilisateur, pas un brouillon de calcul.
 */
class OutilsViewModel(
    private val verifications: VerificationFluideRepository,
    private val parametres: ParametresRepository,
) : ViewModel() {

    private val _ouvert = MutableStateFlow<Outil?>(null)

    /**
     * L'outil ouvert, `null` pour la liste.
     *
     * Tenu ici et non dans la composition : un état de composition se perd au
     * changement d'onglet, et la réglette réglée sur un R-448A se refermait dès
     * qu'on allait chercher un prix dans les factures. Ce n'est pas un état de
     * calcul — rien n'en est persisté —, seulement l'endroit où l'on était.
     */
    val ouvert: StateFlow<Outil?> = _ouvert.asStateFlow()

    fun onOuvrir(outil: Outil) {
        _ouvert.value = outil
    }

    fun onFermer() {
        _ouvert.value = null
    }

    /** Les fluides dont l'utilisateur a contrôlé la courbe de saturation. */
    val fluidesVerifies: StateFlow<Set<String>> = verifications.verifies
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TEMPS_ARRET_COLLECTE_MS),
            initialValue = emptySet(),
        )

    /**
     * Marque la courbe d'un fluide vérifiée, ou retire la marque.
     *
     * Le même geste qu'au sein d'une intervention, et le même effet : une
     * vérification vaut pour toutes les machines, puisqu'elle porte sur une
     * constante physique et non sur un appareil.
     */
    fun onVerifierFluide(fluide: String, verifie: Boolean) {
        viewModelScope.launch {
            verifications.basculer(fluide, verifie, par = parametres.lire().technicien)
        }
    }

    companion object {

        /** Même raison que dans [InterventionsViewModel]. */
        private const val TEMPS_ARRET_COLLECTE_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                OutilsViewModel(
                    conteneur.verificationsFluide,
                    conteneur.parametres,
                )
            }
        }
    }
}
