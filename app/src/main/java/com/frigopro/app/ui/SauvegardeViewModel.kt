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
import com.frigopro.app.data.StockagePhotos
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
 *
 * Le dépôt ne connaît les photos que par leur nom ; c'est ici que les noms
 * deviennent des fichiers, et c'est pourquoi [photos] s'ajoute à [fichiers].
 */
class SauvegardeViewModel(
    private val sauvegardes: SauvegardeRepository,
    private val fichiers: FichiersExternes,
    private val photos: StockagePhotos,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    /** Compte rendu de la dernière opération, ou `null` s'il a été lu. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * La date dans le nom : on retrouve la plus récente sans l'ouvrir. Et
     * `.zip`, car la sauvegarde embarque désormais les photos.
     */
    fun nomFichierPropose(): String = "frigopro-sauvegarde-${LocalDate.now()}.zip"

    fun onExporterVers(destination: Uri) {
        viewModelScope.launch {
            val export = sauvegardes.exporter()
            val images = export.fichiersPhotos.map { photos.fichier(it) }
            _message.value = if (fichiers.ecrireArchive(destination, export.contenu, images)) {
                "Sauvegarde enregistrée : " + inventaire(
                    interventions = export.interventions,
                    clients = export.clients,
                    types = export.types,
                    equipements = export.equipements,
                    photos = images.size,
                ) + "."
            } else {
                "Impossible d'écrire dans ce fichier. Essayez un autre emplacement."
            }
        }
    }

    fun onRestaurerDepuis(source: Uri) {
        viewModelScope.launch {
            val contenu = fichiers.lireSauvegarde(source)
            if (contenu == null) {
                _message.value = "Impossible de lire ce fichier."
                return@launch
            }

            _message.value = when (val resultat = sauvegardes.restaurer(contenu)) {
                is ResultatRestauration.Reussie -> {
                    // Les images seulement maintenant : le fichier est accepté,
                    // donc plus rien ne viendra annuler ce qu'on écrit.
                    fichiers.extrairePhotos(source) { nom, flux -> photos.restaurer(nom, flux) }
                    "Restauration terminée : " + inventaire(
                        interventions = resultat.interventions,
                        clients = resultat.clients,
                        types = resultat.types,
                        equipements = resultat.equipements,
                        photos = resultat.photos,
                    ) + "."
                }

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

    /**
     * Ce qui est absent n'est pas mentionné : annoncer « 0 photo » attirerait
     * l'œil sur ce qui n'a aucune importance. Interventions et clients, eux,
     * sont toujours dits — même à zéro, c'est une information.
     */
    private fun inventaire(
        interventions: Int,
        clients: Int,
        types: Int,
        equipements: Int,
        photos: Int,
    ): String {
        val parties = buildList {
            add(compte(interventions, "intervention"))
            add(compte(clients, "client"))
            if (types > 0) add(compte(types, "type"))
            if (equipements > 0) add(compte(equipements, "machine"))
            if (photos > 0) add(compte(photos, "photo"))
        }

        return parties.dropLast(1).joinToString(", ") + " et " + parties.last()
    }

    private fun compte(nombre: Int, nom: String): String =
        if (nombre > 1) "$nombre ${nom}s" else "$nombre $nom"

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val conteneur = (application as FrigoProApplication).conteneur
                SauvegardeViewModel(conteneur.sauvegardes, conteneur.fichiers, conteneur.photos)
            }
        }
    }
}
