package com.frigopro.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.frigopro.app.FrigoProApplication
import com.frigopro.app.data.FichiersExternes
import com.frigopro.app.data.ResultatRestauration
import com.frigopro.app.data.SauvegardeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Sauvegarde et restauration, déclenchées depuis la barre de la tournée.
 *
 * L'issue est rendue en clair dans [message] : une sauvegarde dont on ne sait
 * pas si elle a réussi ne rassure personne.
 */
class SauvegardeViewModel(
    private val sauvegardes: SauvegardeRepository,
    private val fichiers: FichiersExternes,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    /** Compte rendu de la dernière opération, ou `null` s'il a été lu. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** La date dans le nom : on retrouve la plus récente sans l'ouvrir. */
    fun nomFichierPropose(): String = "frigopro-sauvegarde-${LocalDate.now()}.json"

    fun onExporterVers(destination: Uri) {
        viewModelScope.launch {
            val export = sauvegardes.exporter()
            _message.value = if (fichiers.ecrire(destination, export.contenu)) {
                "Sauvegarde enregistrée : ${compte(export.interventions, "intervention")} " +
                    "et ${compte(export.clients, "client")}."
            } else {
                "Impossible d'écrire dans ce fichier. Essayez un autre emplacement."
            }
        }
    }

    fun onRestaurerDepuis(source: Uri) {
        viewModelScope.launch {
            val contenu = fichiers.lire(source)
            if (contenu == null) {
                _message.value = "Impossible de lire ce fichier."
                return@launch
            }

            _message.value = when (val resultat = sauvegardes.restaurer(contenu)) {
                is ResultatRestauration.Reussie ->
                    "Restauration terminée : ${compte(resultat.interventions, "intervention")} " +
                        "et ${compte(resultat.clients, "client")}."

                is ResultatRestauration.TropRecente ->
                    "Cette sauvegarde vient d'une version plus récente de FrigoPro. " +
                        "Mettez l'application à jour avant de la restaurer."

                ResultatRestauration.Illisible ->
                    "Ce fichier n'est pas une sauvegarde FrigoPro utilisable. Rien n'a été modifié."
            }
        }
    }

    fun onMessageLu() {
        _message.value = null
    }

    private fun compte(nombre: Int, nom: String): String =
        if (nombre > 1) "$nombre ${nom}s" else "$nombre $nom"

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                SauvegardeViewModel(conteneur.sauvegardes, conteneur.fichiers)
            }
        }
    }
}
