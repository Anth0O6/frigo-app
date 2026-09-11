package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Capture
import com.frigopro.app.data.CategoriePhoto
import kotlinx.coroutines.launch

/**
 * L'écran d'une intervention, avec son état.
 *
 * Comme la fiche machine, il s'ouvre **en plein onglet** plutôt qu'en feuille :
 * on y saisit des relevés, on y regarde des photos, on y fait signer. Une
 * feuille à mi-hauteur ne permet aucun des trois.
 *
 * Le retour système tient en un `BackHandler` à deux profondeurs — l'aide au
 * dépannage par-dessus l'intervention — ce qui ne justifie toujours pas un
 * graphe de navigation.
 */
@Composable
fun InterventionRoute(
    modifier: Modifier = Modifier,
    viewModel: InterventionViewModel,
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    val ecoule by viewModel.ecoule.collectAsStateWithLifecycle()
    val onglet by viewModel.onglet.collectAsStateWithLifecycle()
    val depannageOuvert by viewModel.depannageOuvert.collectAsStateWithLifecycle()
    val agrandie by viewModel.agrandie.collectAsStateWithLifecycle()
    val portee = rememberCoroutineScope()

    // La prise de vue quitte l'application : la catégorie visée et le fichier à
    // remplir doivent survivre à l'aller-retour.
    var capture by remember { mutableStateOf<Pair<CategoriePhoto, Capture>?>(null) }
    val appareilPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { _ ->
        capture?.let { (categorie, prise) -> viewModel.onCapture(categorie, prise.nom) }
        capture = null
    }

    var categorieGalerie by remember { mutableStateOf<CategoriePhoto?>(null) }
    val galerie = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { source ->
        val categorie = categorieGalerie
        if (source != null && categorie != null) viewModel.onPhotoChoisie(categorie, source)
        categorieGalerie = null
    }

    var signatureOuverte by remember { mutableStateOf(false) }

    val courant = etat ?: return

    BackHandler(enabled = true) {
        when {
            depannageOuvert -> viewModel.onFermerDepannage()
            else -> viewModel.onFermer()
        }
    }

    val diagnostic = courant.diagnostic
    if (depannageOuvert && diagnostic != null) {
        EcranDepannage(
            diagnostic = diagnostic,
            releve = courant.releve,
            fluide = courant.fluide,
            onFermer = viewModel::onFermerDepannage,
            modifier = modifier,
        )
        return
    }

    EcranIntervention(
        etat = courant,
        ecoule = ecoule,
        onglet = onglet,
        chargerPhoto = viewModel::charger,
        actions = ActionsIntervention(
            onFermer = viewModel::onFermer,
            onOnglet = viewModel::onOnglet,
            onBasculerChrono = viewModel::onBasculerChrono,
            onBp = viewModel::onBp,
            onHp = viewModel::onHp,
            onSurchauffe = viewModel::onSurchauffe,
            onSousRefroidissement = viewModel::onSousRefroidissement,
            onOuvrirDepannage = viewModel::onOuvrirDepannage,
            onMouvement = viewModel::onMouvement,
            onSupprimerMouvement = viewModel::onSupprimerMouvement,
            onAjouterPiece = viewModel::onAjouterPiece,
            onSupprimerPiece = viewModel::onSupprimerPiece,
            onPhotographier = { categorie ->
                val prise = viewModel.preparerCapture()
                capture = categorie to prise
                appareilPhoto.launch(prise.uri)
            },
            onChoisirImage = { categorie ->
                categorieGalerie = categorie
                galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onAgrandir = viewModel::onAgrandir,
            onTravaux = viewModel::onTravaux,
            onSigner = { signatureOuverte = true },
            onEffacerSignature = viewModel::onEffacerSignature,
            onCloturer = viewModel::onCloturer,
        ),
        modifier = modifier,
    )

    agrandie?.let { photo ->
        VisionneusePhoto(
            photo = photo,
            chargerPhoto = viewModel::charger,
            onSupprimer = viewModel::onSupprimerPhoto,
            onFermer = viewModel::onFermerAgrandissement,
        )
    }

    if (signatureOuverte) {
        DialogueSignature(
            onValider = { image ->
                // Ranger l'image puis noter son nom : l'ordre habituel est
                // inversé ici parce que, la signature étant fabriquée par
                // l'application, c'est le fichier qui doit exister en premier.
                portee.launch {
                    viewModel.rangerSignature(image)?.let(viewModel::onSignature)
                }
                signatureOuverte = false
            },
            onFermer = { signatureOuverte = false },
        )
    }
}
