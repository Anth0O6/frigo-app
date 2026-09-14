package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    viewModel: InterventionViewModel,
    /**
     * Chiffrer depuis l'intervention. L'écran ne sait pas où va l'onglet Devis :
     * c'est la coquille qui le sait, et c'est elle qui bascule.
     */
    onCreerDevis: () -> Unit,
    /** Ouvrir le formulaire de l'intervention : la coquille seule le détient. */
    onModifierFiche: () -> Unit,
    modifier: Modifier = Modifier,
    facturesViewModel: FacturesViewModel = viewModel(factory = FacturesViewModel.Factory),
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    val ecoule by viewModel.ecoule.collectAsStateWithLifecycle()
    val onglet by viewModel.onglet.collectAsStateWithLifecycle()
    val depannageOuvert by viewModel.depannageOuvert.collectAsStateWithLifecycle()
    val agrandie by viewModel.agrandie.collectAsStateWithLifecycle()
    val fluidesVerifies by viewModel.fluidesVerifies.collectAsStateWithLifecycle()
    val magasin by viewModel.magasin.collectAsStateWithLifecycle()
    val documentPret by viewModel.documentPret.collectAsStateWithLifecycle()
    val echecExport by viewModel.echecExport.collectAsStateWithLifecycle()
    val contexte = LocalContext.current
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

    // Posés **avant** les retours anticipés : l'aide au dépannage recouvre
    // l'intervention, et un export lancé juste avant de l'ouvrir doit quand même
    // aboutir à une feuille de partage.
    LaunchedEffect(documentPret) {
        val fichier = documentPret ?: return@LaunchedEffect
        val intervention = etat?.intervention
        contexte.envoyerDocument(
            document = fichier,
            objet = listOf("Compte-rendu", intervention?.numero.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" "),
            corps = corpsDuCompteRendu(etat),
        )
        viewModel.onDocumentPartage()
    }

    if (echecExport) {
        AlertDialog(
            onDismissRequest = viewModel::onEchecVu,
            title = { Text(text = "Export impossible") },
            text = {
                Text(
                    text = "Le compte-rendu n'a pas pu être écrit. Il manque peut-être de la " +
                        "place sur le téléphone : le document se reconstruit à l'identique, " +
                        "rien de ce qui a été saisi n'est perdu.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::onEchecVu) { Text(text = "Fermer") } },
        )
    }

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
        fluidesVerifies = fluidesVerifies,
        magasin = magasin,
        actions = ActionsIntervention(
            onFermer = viewModel::onFermer,
            onOnglet = viewModel::onOnglet,
            onBasculerChrono = viewModel::onBasculerChrono,
            onPoserTemps = viewModel::onPoserTemps,
            onBp = viewModel::onBp,
            onHp = viewModel::onHp,
            onSurchauffe = viewModel::onSurchauffe,
            onSousRefroidissement = viewModel::onSousRefroidissement,
            onOuvrirDepannage = viewModel::onOuvrirDepannage,
            onModifierFiche = onModifierFiche,
            onVerifierFluide = viewModel::onVerifierFluide,
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
            onEnvoyerRapport = viewModel::onExporterRapport,
            onSigner = { signatureOuverte = true },
            onEffacerSignature = viewModel::onEffacerSignature,
            onCloturer = viewModel::onCloturer,
            onBasculerPoint = viewModel::onBasculerPoint,
            onCreerDevis = onCreerDevis,
            // Facturer crée la facture — ou rouvre celle qui existe déjà — et la
            // pose comme facture ouverte. La coquille bascule ensuite sur
            // l'onglet, qui la montre : le ViewModel des factures est partagé.
            onFacturer = {
                val courante = etat?.intervention
                if (courante != null) {
                    facturesViewModel.onFacturerIntervention(courante)
                    onCreerDevis()
                }
            },
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

/**
 * Le corps du message qui accompagne le compte-rendu.
 *
 * Court, et sans rien répéter de ce qui est dans le PDF : c'est un message, pas
 * un second document. Il nomme le client et la date, de quoi retrouver la pièce
 * jointe dans une boîte mail six mois plus tard.
 */
private fun corpsDuCompteRendu(etat: EtatIntervention?): String {
    val intervention = etat?.intervention ?: return "Compte-rendu d'intervention ci-joint."
    return buildString {
        append("Bonjour,\n\nVous trouverez ci-joint le compte-rendu de l'intervention")
        if (intervention.typeLibelle.isNotBlank()) append(" (${intervention.typeLibelle})")
        append(" du ${intervention.date.format(FORMAT_DATE_DOCUMENT)}")
        if (intervention.equipementNom.isNotBlank()) append(" sur ${intervention.equipementNom}")
        append(".\n\nCordialement,")
    }
}
