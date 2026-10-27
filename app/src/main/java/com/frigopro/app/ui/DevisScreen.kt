package com.frigopro.app.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Article
import com.frigopro.app.data.ChiffrageTtc
import com.frigopro.app.data.auCentime
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.CategoriePrestation
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.DevisFacture
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.GroupeMachines
import com.frigopro.app.data.LigneDevis
import com.frigopro.app.data.PalierPrestation
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.StatutDevis
import com.frigopro.app.data.StatutFacture
import com.frigopro.app.data.TarifDegressif
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.CaseACocher
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffre
import com.frigopro.app.ui.theme.StyleChiffrePetit

/** La vue Devis de l'onglet Factures, avec son état. */
@Composable
fun DevisRoute(
    modifier: Modifier = Modifier,
    /** Passer aux factures : la bascule de l'onglet, tenue par la coquille. */
    onVoirFactures: () -> Unit = {},
    /** Passer à « Aujourd'hui » : un autre onglet, donc la coquille seule sait le faire. */
    onAllerALaTournee: () -> Unit = {},
    viewModel: DevisViewModel = viewModel(factory = DevisViewModel.Factory),
    facturesViewModel: FacturesViewModel = viewModel(factory = FacturesViewModel.Factory),
    /**
     * Celui de la journée, et c'est tout le mécanisme : `viewModel()` rend une
     * seule instance par classe, si bien que le formulaire ouvert d'ici est
     * exactement celui que la coquille dessine déjà. Rien à transporter d'un
     * onglet à l'autre — le même motif que la facture créée depuis un devis.
     */
    tournee: InterventionsViewModel = viewModel(factory = InterventionsViewModel.Factory),
) {
    val liste by viewModel.liste.collectAsStateWithLifecycle()
    val carnet by viewModel.carnet.collectAsStateWithLifecycle()
    val complet by viewModel.complet.collectAsStateWithLifecycle()
    val compteurs by viewModel.compteurs.collectAsStateWithLifecycle()
    val catalogue by viewModel.catalogue.collectAsStateWithLifecycle()
    val magasin by viewModel.magasin.collectAsStateWithLifecycle()
    val unitesVisees by viewModel.unitesVisees.collectAsStateWithLifecycle()
    val reglages by viewModel.reglages.collectAsStateWithLifecycle()
    val documentPret by viewModel.documentPret.collectAsStateWithLifecycle()
    val echecExport by viewModel.echecExport.collectAsStateWithLifecycle()
    val trajet by viewModel.trajet.collectAsStateWithLifecycle()
    val calculEnCours by viewModel.calculEnCours.collectAsStateWithLifecycle()
    val echecItineraire by viewModel.echecItineraire.collectAsStateWithLifecycle()
    val parc by viewModel.parc.collectAsStateWithLifecycle()
    val paliers by viewModel.paliers.collectAsStateWithLifecycle()
    val factureDuDevis by viewModel.facture.collectAsStateWithLifecycle()
    val importFournisseur by viewModel.importFournisseur.collectAsStateWithLifecycle()
    val contexte = LocalContext.current

    // Un devis fournisseur, scanné ou choisi : les deux chemins de la saisie des
    // prix. Le nom du scan survit à l'aller-retour vers l'appareil photo.
    var scan by rememberSaveable { mutableStateOf<String?>(null) }
    val appareilPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { pris ->
        scan?.let { viewModel.onScanFournisseur(it, pris) }
        scan = null
    }
    val choisirDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onLireFournisseur(it.toString()) }
    }

    // Le devis dont on vient de demander la suppression, en attente de
    // confirmation. Tenu par la route et non par chaque écran : la liste et
    // la feuille ouverte suppriment toutes deux, et deux boîtes jumelles
    // auraient fini par ne plus dire la même chose.
    var aSupprimer by remember { mutableStateOf<Devis?>(null) }

    // Le partage s'ouvre dès que le PDF est écrit, puis le ViewModel oublie le
    // document : sans cet oubli, revenir sur l'onglet rouvrirait le sélecteur.
    LaunchedEffect(documentPret) {
        val fichier = documentPret ?: return@LaunchedEffect
        val ouvert = complet
        contexte.envoyerDocument(
            document = fichier,
            objet = listOf("Devis", ouvert?.devis?.numero.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" "),
            corps = corpsDuMessage(ouvert),
        )
        viewModel.onDocumentPartage()
    }

    if (echecExport) {
        AlertDialog(
            onDismissRequest = viewModel::onEchecVu,
            title = { Text(text = "Export impossible") },
            text = {
                Text(
                    text = "Le PDF n'a pas pu être écrit. Il manque peut-être de la place " +
                        "sur le téléphone : le document se reconstruit à l'identique, " +
                        "rien n'est perdu.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::onEchecVu) { Text(text = "Fermer") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    BackHandler(enabled = complet != null) { viewModel.onFermer() }

    val ouvert = complet
    if (ouvert != null) {
        EcranDevis(
            devis = ouvert,
            clients = carnet,
            // La facture n'est retenue que si elle est bien celle du devis
            // affiché : les deux flux ne répondent pas au même instant.
            facture = factureDuDevis?.takeIf { it.devisId == ouvert.devis.id },
            onObjet = viewModel::onObjet,
            onClient = viewModel::onClient,
            parc = parc,
            onMachine = viewModel::onMachine,
            onStatut = viewModel::onStatut,
            onAjouterLigne = { designation, quantite, unite, prix ->
                viewModel.onAjouterLigne(designation, quantite, unite, prix)
            },
            onAjouterPrestation = viewModel::onAjouterPrestation,
            paliers = paliers,
            magasin = magasin,
            onAjouterArticle = viewModel::onAjouterArticle,
            coefficientMateriel = reglages.coefficientMateriel,
            onCreerPrestation = viewModel::onCreerPrestation,
            catalogue = catalogue,
            unitesVisees = unitesVisees,
            onModifierLigne = viewModel::onModifierLigne,
            onOffrirTva = viewModel::onOffrirTva,
            onExporterPdf = viewModel::onExporterPdf,
            // Facturer bascule l'onglet tout seul : le ViewModel des factures
            // est partagé, et la facture créée devient celle qu'il montre.
            onFacturer = { facturesViewModel.onFacturerDevis(ouvert) },
            // Pré-remplir puis basculer : la feuille de saisie est posée par la
            // coquille, et c'est sur « Aujourd'hui » que la date se choisit —
            // devant la journée, flèches et sélecteur à portée.
            onPlanifier = {
                tournee.onPlanifierDepuisDevis(
                    devis = ouvert.devis,
                    client = carnet.firstOrNull { it.id == ouvert.devis.clientId },
                )
                onAllerALaTournee()
            },
            manques = manquesEmetteur(reglages),
            onSupprimerLigne = viewModel::onSupprimerLigne,
            deplacement = EtatDeplacement(
                trajet = trajet,
                tarif = reglages.tarifDeplacement,
                calculEnCours = calculEnCours,
                echec = echecItineraire,
                itineraireDisponible = reglages.itineraireDisponible,
            ),
            actionsDeplacement = ActionsDeplacement(
                trajetPropose = viewModel::trajetPropose,
                onEnregistrer = viewModel::onEnregistrerTrajet,
                onCalculer = viewModel::onCalculerTrajet,
                onOffrir = viewModel::onOffrirDeplacement,
                onRetirer = viewModel::onRetirerDeplacement,
                onOublierEchec = viewModel::onOublierEchec,
            ),
            importFournisseur = importFournisseur,
            affichage = AffichageChiffrage(
                saisieTtc = reglages.saisieTtc,
                ttcParLigne = reglages.ttcParLigne,
                sousTotaux = reglages.sousTotaux,
                onTtcParLigne = viewModel::onTtcParLigne,
                onSousTotaux = viewModel::onSousTotaux,
            ),
            actionsImport = ActionsImportFournisseur(
                onScanner = {
                    val prise = viewModel.preparerScan()
                    scan = prise.nom
                    try {
                        appareilPhoto.launch(prise.uri)
                    } catch (_: ActivityNotFoundException) {
                        // Pas d'appareil photo : le fichier préparé ne servira pas.
                        viewModel.onScanFournisseur(prise.nom, pris = false)
                        scan = null
                    }
                },
                onOuvrirFichier = { choisirDocument.launch(arrayOf("application/pdf", "image/*")) },
                onMarge = viewModel::onMargeImport,
                onCoefficient = viewModel::onCoefficientImport,
                onMettreAJourPrix = viewModel::onMettreAJourPrixImport,
                onCreerArticles = viewModel::onCreerArticlesImport,
                onAchatTtc = viewModel::onAchatTtcImport,
                onTauxAchat = viewModel::onTauxAchatImport,
                onRetenir = viewModel::onRetenirLigneImport,
                onQuantite = viewModel::onQuantiteImport,
                onCoefficientLigne = viewModel::onCoefficientLigneImport,
                onValider = viewModel::onValiderImport,
                onFermer = viewModel::onFermerImport,
            ),
            onSupprimer = { aSupprimer = ouvert.devis },
            onFermer = viewModel::onFermer,
            modifier = modifier,
        )
    } else {
        ListeDevis(
            devis = liste,
            compteurs = compteurs,
            onOuvrir = viewModel::onOuvrir,
            onSupprimer = { aSupprimer = it },
            onNouveau = { viewModel.onNouveau(null) },
            onVoirFactures = onVoirFactures,
            modifier = modifier,
        )
    }

    aSupprimer?.let { condamne ->
        ConfirmationSuppressionDevis(
            devis = condamne,
            onConfirmer = {
                viewModel.onSupprimer(condamne.id)
                aSupprimer = null
            },
            onFermer = { aSupprimer = null },
        )
    }
}

/**
 * La confirmation de suppression d'un devis.
 *
 * Elle **nomme ce qui disparaît**, comme celle d'une intervention : les lignes
 * chiffrées, et le trajet si un itinéraire a été calculé — un chiffre venu de
 * l'extérieur, un jour donné, qui ne se retrouve pas en le redemandant. Le
 * bouton supprimait jusqu'ici sans rien demander, et une fausse manœuvre sur un
 * devis de quinze lignes se payait en les ressaisissant toutes.
 *
 * Elle ne propose pas d'annuler après coup : garder la ligne en attente quelque
 * part aurait demandé de décider ce qu'il advient de son numéro, et un devis à
 * moitié supprimé est pire qu'une question posée avant.
 */
@Composable
private fun ConfirmationSuppressionDevis(
    devis: Devis,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Supprimer ce devis ?") },
        text = {
            Text(
                text = buildString {
                    append(devis.objet.ifBlank { "Ce devis" })
                    if (devis.numero.isNotBlank()) append(" (${devis.numero})")
                    append(" disparaît avec ses lignes chiffrées")
                    append(" et le trajet calculé pour le déplacement.")
                    append(" C'est définitif.")
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmer) {
                Text(
                    text = "Supprimer",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * La liste des devis, du plus récent au plus ancien.
 *
 * Ceux qui ont produit une facture sont **repliés en bas**, sous un en-tête qui
 * les compte. Ils ne sont ni supprimés ni filtrés au loin : un devis facturé
 * reste la pièce qui dit ce que le client a accepté, et c'est celle qu'on
 * ressort quand il conteste la facture. Mais c'est une affaire close, et la
 * laisser au milieu de ce qui attend encore une réponse noie la seule question
 * à laquelle cet écran doit répondre d'un coup d'œil.
 *
 * Repliés plutôt qu'ailleurs, et c'est la même décision que partout dans ce
 * projet : une destination de plus aurait demandé un graphe de navigation pour
 * une liste qu'on ouvre trois fois par an.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeDevis(
    devis: DevisRanges,
    compteurs: CompteursDevis,
    onOuvrir: (Devis) -> Unit,
    /**
     * L'appui long sur une ligne. La liste est le seul endroit d'où l'on peut
     * effacer un devis créé par erreur sans avoir à l'ouvrir d'abord, et c'est
     * exactement le geste que réclame une fausse manœuvre : le devis de trop se
     * voit dans la liste, pas dans sa propre feuille.
     */
    onSupprimer: (Devis) -> Unit = {},
    onNouveau: () -> Unit,
    onVoirFactures: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Replié par défaut, et l'état ne survit pas à la sortie de l'onglet : c'est
    // un coup d'œil qu'on donne, pas une vue qu'on adopte.
    var facturésDepliés by rememberSaveable { mutableStateOf(false) }

    // La recherche, elle, ne survit pas non plus : on cherche un devis, on
    // l'ouvre, et on ne veut pas retrouver la liste filtrée en revenant.
    var recherche by rememberSaveable { mutableStateOf("") }
    val enCours = devis.enCours.filter { it.correspondA(recherche) }
    val facturés = devis.facturés.filter { it.chiffre.correspondA(recherche) }
    val cherche = recherche.isNotBlank()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNouveau,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Nouveau devis")
            }
        },
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            Text(
                // Le titre nomme la vue ouverte et non l'onglet, comme celui
                // de l'Atelier : « Facturation » au-dessus d'une bascule
                // « Factures · Devis » répétait le nom du groupe et laissait
                // l'écran sans dire lequel des deux on regarde.
                text = VueFacturation.DEVIS.libelle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = MargeEcran, vertical = 12.dp),
            )
            RangeePastilles(
                options = VueFacturation.entries,
                retenue = VueFacturation.DEVIS,
                libelle = { it.libelle },
                onChoisir = { if (it == VueFacturation.FACTURES) onVoirFactures() },
                modifier = Modifier
                    .padding(horizontal = MargeEcran)
                    .padding(bottom = 14.dp),
            )
            ChampRecherche(
                valeur = recherche,
                onValeur = { recherche = it },
                indication = "Client, objet, numéro…",
                modifier = Modifier
                    .padding(horizontal = MargeEcran)
                    .padding(bottom = 14.dp),
            )
            // Les compteurs portent sur **tout** et non sur ce qui est filtré :
            // « 3 en attente » est un état de l'entreprise, pas une propriété de
            // la recherche en cours, et le faire tomber à 1 pendant qu'on cherche
            // aurait donné un chiffre faux au moment où on le lit le moins.
            CompteursEnTete(compteurs = compteurs)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = MargeEcran,
                    end = MargeEcran,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (cherche && enCours.isEmpty() && facturés.isEmpty()) {
                    // Dire que la recherche n'a rien donné, et non qu'il n'y a
                    // aucun devis : les deux phrases n'appellent pas le même
                    // geste — l'une fait effacer un mot, l'autre en créer un.
                    item {
                        Encart(texte = "Aucun devis ne correspond à « ${recherche.trim()} ».")
                    }
                } else if (devis.enCours.isEmpty() && devis.facturés.isEmpty()) {
                    item {
                        Encart(
                            texte = "Aucun devis. Un devis chiffré sur place et envoyé avant " +
                                "d'être redescendu du toit est accepté le jour même plutôt que " +
                                "la semaine suivante.",
                        )
                    }
                } else if (!cherche && devis.enCours.isEmpty()) {
                    item {
                        Encart(texte = "Tous les devis sont facturés.")
                    }
                }
                items(items = enCours, key = { it.devis.id }) { document ->
                    LigneDevisListe(
                        chiffre = document,
                        onClick = { onOuvrir(document.devis) },
                        onSupprimer = { onSupprimer(document.devis) },
                    )
                }
                if (facturés.isNotEmpty()) {
                    item(key = "entete-factures") {
                        EnTeteFacturés(
                            nombre = facturés.size,
                            // Une recherche déplie les facturés d'elle-même : on
                            // cherche un devis, pas une catégorie, et le trouver
                            // replié sous un en-tête ferait conclure qu'il n'est
                            // pas là.
                            deplié = facturésDepliés || cherche,
                            onBasculer = { facturésDepliés = !facturésDepliés },
                        )
                    }
                    if (facturésDepliés || cherche) {
                        items(items = facturés, key = { it.chiffre.devis.id }) { facturé ->
                            LigneDevisListe(
                                chiffre = facturé.chiffre,
                                onClick = { onOuvrir(facturé.chiffre.devis) },
                                // Pas d'appui long ici : un devis facturé est la
                                // pièce qui dit ce que le client a accepté, et
                                // l'effacer laisserait une facture sans son devis.
                                facture = facturé.facture,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Les trois chiffres de l'en-tête.
 *
 * « En cours » est le seul des trois qui demande une action : c'est le montant
 * qui attend une réponse, donc celui qui dit s'il faut relancer un client.
 */
@Composable
private fun CompteursEnTete(compteurs: CompteursDevis) {
    val statuts = LocalStatuts.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MargeEcran)
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TuileChiffre(
            valeur = "${compteurs.enAttente}",
            libelle = "en attente",
            modifier = Modifier.weight(1f),
            couleur = statuts.aValider,
        )
        TuileChiffre(
            valeur = "${compteurs.acceptes}",
            libelle = "acceptés",
            modifier = Modifier.weight(1f),
            couleur = statuts.termine,
        )
        TuileChiffre(
            valeur = Nombres.enEurosCourt(compteurs.pipelineTtc),
            libelle = "en cours",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LigneDevisListe(
    chiffre: DevisChiffre,
    onClick: () -> Unit,
    onSupprimer: (() -> Unit)? = null,
    facture: DevisFacture? = null,
) {
    val devis = chiffre.devis
    // L'appui simple ouvre, l'appui long supprime : la convention du projet,
    // celle des lignes d'un devis et des interventions de la tournée.
    Carte(onClick = onClick, onLongClick = onSupprimer) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = devis.objet.ifBlank { "Devis sans objet" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(devis.numero, devis.clientNom)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = StyleChiffrePetit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                // Le montant TTC : c'est celui que le client lit, et le seul
                // qu'on compare d'un devis à l'autre.
                Text(text = Nombres.enEuros(chiffre.totalTtc), style = StyleChiffrePetit)
                // Sur un devis facturé, le numéro de la facture remplace le
                // statut du devis : « Accepté » ne dit plus rien d'utile une
                // fois la facture partie, et le numéro, lui, permet de la
                // retrouver dans l'autre vue.
                if (facture != null) {
                    PuceFacture(facture)
                } else {
                    PuceStatut(devis.statut)
                }
            }
        }
    }
}

/**
 * L'en-tête qui replie les devis facturés.
 *
 * Il porte le compte, parce que c'est la seule information qu'on lit sans
 * déplier — « sept devis facturés » dit que l'année avance ; une flèche seule ne
 * dirait rien.
 */
@Composable
private fun EnTeteFacturés(nombre: Int, deplié: Boolean, onBasculer: () -> Unit) {
    Surface(
        onClick = onBasculer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalCibles.current.action),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (nombre == 1) "1 devis facturé" else "$nombre devis facturés",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (deplié) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = if (deplié) "Replier" else "Déplier",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Le numéro de la facture qu'un devis a produite, et son sort. */
@Composable
private fun PuceFacture(facture: DevisFacture) {
    val statuts = LocalStatuts.current
    val couleur = when (facture.statut) {
        StatutFacture.PAYEE -> statuts.termine
        StatutFacture.ANNULEE -> statuts.urgence
        else -> statuts.planifie
    }
    val texte = if (facture.statut == StatutFacture.BROUILLON) {
        "FACTURE EN BROUILLON"
    } else {
        facture.numero
    }
    Puce(texte = texte, couleur = couleur, fond = couleur.copy(alpha = 0.16f))
}

@Composable
private fun PuceStatut(statut: StatutDevis) {
    val statuts = LocalStatuts.current
    val couleur = when (statut) {
        // Un brouillon n'est pas un état, c'est l'absence d'état : il reste gris.
        StatutDevis.BROUILLON -> MaterialTheme.colorScheme.onSurfaceVariant
        StatutDevis.ENVOYE -> statuts.planifie
        StatutDevis.ACCEPTE -> statuts.termine
        StatutDevis.REFUSE -> statuts.urgence
    }
    Puce(texte = statut.libelle.uppercase(), couleur = couleur, fond = couleur.copy(alpha = 0.16f))
}

/**
 * Un devis : son objet, ses lignes, ses totaux.
 *
 * Les totaux sont en bas et non en haut, contre l'habitude des tableurs : on
 * lit un devis en ajoutant des lignes, et le montant est la conclusion de ce
 * qu'on vient d'écrire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranDevis(
    devis: DevisComplet,
    clients: List<com.frigopro.app.data.Client>,
    /**
     * La facture que le devis a produite, s'il en a produit une : elle le **fige**.
     * Ses lignes, son en-tête, son statut et sa suppression ne se proposent plus —
     * la facture a repris ses lignes, et c'est la pièce qui dit ce que le client a
     * accepté. Le dépôt le refuse de toute façon ; l'écran ne propose pas un geste
     * qui ne ferait rien.
     */
    facture: DevisFacture? = null,
    onObjet: (String) -> Unit,
    onClient: (com.frigopro.app.data.Client) -> Unit,
    /** Le parc du client du devis, d'où se choisit la machine visée. */
    parc: List<GroupeMachines> = emptyList(),
    onMachine: (Equipement?) -> Unit = {},
    onStatut: (StatutDevis) -> Unit,
    onAjouterLigne: (String, Double, String, Double) -> Unit,
    /** Une prestation, et la quantité confirmée — `null` pour la proposition. */
    onAjouterPrestation: (Prestation, Int?) -> Unit,
    /** Les paliers dégressifs du catalogue, par prestation. */
    paliers: Map<String, List<PalierPrestation>> = emptyMap(),
    /** Le magasin, d'où se chiffre le matériel. Tout le magasin, stock ou non. */
    magasin: List<ArticleEnStock> = emptyList(),
    onAjouterArticle: (Article, Double, Double) -> Unit = { _, _, _ -> },
    /** Le coefficient des Réglages ; `0` quand il n'est pas réglé. */
    coefficientMateriel: Double = 0.0,
    onCreerPrestation: (Prestation) -> Unit,
    catalogue: Map<CategoriePrestation, List<Prestation>>,
    /** Le nombre d'unités intérieures de la machine visée : 1 pour un monosplit. */
    unitesVisees: Int,
    /** Une ligne retouchée dans sa boîte : le seul chemin qui modifie une ligne. */
    onModifierLigne: (LigneDevis) -> Unit,
    onOffrirTva: () -> Unit,
    onExporterPdf: () -> Unit,
    /**
     * Transformer le devis en facture.
     *
     * Proposé une fois le devis **accepté** seulement : facturer ce qui n'a pas
     * été accepté serait réclamer de l'argent pour un chiffrage que personne n'a
     * approuvé.
     */
    onFacturer: () -> Unit,
    /**
     * Planifier le chantier. N'a de sens que sur un devis **accepté** : avant, il
     * n'y a rien à poser sur un planning, et proposer le bouton sur un brouillon
     * aurait fait planifier du travail que le client n'a pas commandé.
     */
    onPlanifier: () -> Unit,
    /**
     * Ce qui manque à l'en-tête de l'entreprise — voir [manquesEmetteur]. Dit ici,
     * avant l'export, et plus jamais imprimé sur le document du client.
     */
    manques: List<String> = emptyList(),
    /** Retire une ligne, après la confirmation de sa boîte. */
    onSupprimerLigne: (LigneDevis) -> Unit,
    /** Le déplacement facturé, et ce qu'on peut en faire. */
    deplacement: EtatDeplacement,
    actionsDeplacement: ActionsDeplacement,
    /** Un devis fournisseur lu et pas encore ajouté ; `null` quand rien n'est en cours. */
    importFournisseur: ImportFournisseur? = null,
    /** Saisie et lecture en HT ou en TTC, telles que réglées et changeables ici. */
    affichage: AffichageChiffrage = AffichageChiffrage(),
    actionsImport: ActionsImportFournisseur = ActionsImportFournisseur(),
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fige = facture != null
    // Le taux du document et non celui des Réglages : un devis garde le régime et
    // le taux de sa création. `null` en franchise, où il n'y a pas de TTC à part.
    val tauxDocument = devis.devis.tauxTva.takeIf { devis.devis.assujettiTva }
    var saisieOuverte by remember { mutableStateOf(false) }
    var clientOuvert by remember { mutableStateOf(false) }
    var machineOuverte by remember { mutableStateOf(false) }
    var catalogueOuvert by remember { mutableStateOf(false) }
    var ligneOuverte by remember { mutableStateOf<LigneDevis?>(null) }
    // La prestation dégressive dont on fait confirmer le nombre d'unités.
    var degressiveOuverte by remember { mutableStateOf<Prestation?>(null) }

    var magasinOuvert by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargeEcran, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoutonCarre(
                    icone = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Revenir aux devis",
                    onClick = onFermer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Devis", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${devis.devis.numero} · ${devis.devis.statut.libelle.lowercase()}",
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (facture != null) {
                Encart(
                    texte = "Ce devis a produit la facture " +
                        facture.numero.ifBlank { "en brouillon" } +
                        " : il ne se modifie plus et ne se supprime plus. C'est la pièce " +
                        "qui dit ce que le client a accepté ; une correction se fait sur " +
                        "la facture, ou par un avoir.",
                )
            }

            if (fige) {
                LigneLecture(intitule = "OBJET", valeur = devis.devis.objet.ifBlank { "Sans objet" })
            } else {
                ChampTexte(libelle = "Objet", valeur = devis.devis.objet, onValeur = onObjet)
            }

            Carte(onClick = if (fige) null else ({ clientOuvert = true })) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CLIENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = devis.devis.clientNom.ifBlank { "Aucun client choisi" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (!fige) Text(text = "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // La machine, sous le client et seulement une fois le client choisi :
            // on chiffre le parc de quelqu'un. C'est ce qui fait proposer le
            // nombre d'unités d'un bi-split — sans machine, tout compte pour une.
            if (devis.devis.clientId != null || devis.devis.equipementNom.isNotBlank()) {
                val peutChoisir = !fige && (parc.isNotEmpty() || devis.devis.equipementId != null)
                Carte(onClick = if (peutChoisir) ({ machineOuverte = true }) else null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MACHINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = devis.devis.equipementNom.ifBlank {
                                    if (parc.isEmpty()) "Aucune machine au parc de ce client" else "Aucune machine visée"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (unitesVisees > 1) {
                                Text(
                                    text = "$unitesVisees unités intérieures",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (peutChoisir) Text(text = "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            devis.lignes.forEach { ligne ->
                LigneDuDevis(
                    ligne = ligne,
                    onClick = if (fige) null else ({ ligneOuverte = ligne }),
                    tauxTtc = tauxDocument?.takeIf { affichage.ttcParLigne },
                )
            }
            if (devis.lignes.isNotEmpty() && !fige) {
                Text(
                    text = "Toucher une ligne pour la modifier, l'offrir ou la retirer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!fige) {
                // Le catalogue d'abord, la saisie libre ensuite : c'est l'ordre des
                // fréquences. Un devis se construit à 90 % de prestations connues,
                // et taper un intitulé gants aux mains sur un capot de camionnette
                // est ce qu'on veut avoir à faire le moins souvent possible.
                BoutonPlein(
                    texte = "Ajouter depuis le catalogue",
                    onClick = { catalogueOuvert = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Le matériel entre les deux : moins fréquent que la main-d'œuvre,
                // beaucoup plus que la ligne tapée à la main. C'était le chemin
                // manquant — le magasin tenait les références et les deux prix, et
                // aucun devis ne pouvait y puiser.
                BoutonContour(
                    texte = "Ajouter du matériel",
                    onClick = { magasinOuvert = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Un devis fournisseur repris d'un trait : ses lignes, ses
                // quantités, et la marge qu'on choisit d'y mettre. Le scan
                // d'abord, pour le papier tendu au comptoir de l'agence.
                Text(
                    text = "Reprendre un devis fournisseur",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoutonContour(
                        texte = "Scanner",
                        onClick = actionsImport.onScanner,
                        modifier = Modifier.weight(1f),
                    )
                    BoutonContour(
                        texte = "Ouvrir un fichier",
                        onClick = actionsImport.onOuvrirFichier,
                        modifier = Modifier.weight(1f),
                    )
                }
                BoutonContour(
                    texte = "+ Ligne libre",
                    onClick = { saisieOuverte = true },
                    modifier = Modifier.fillMaxWidth(),
                    couleur = MaterialTheme.colorScheme.secondary,
                )

                // Le déplacement refait des lignes : sur un devis figé, ses lignes
                // restent lisibles au-dessus, mais sa saisie n'a plus d'objet.
                SectionDeplacement(etat = deplacement, actions = actionsDeplacement)
            }

            CarteTotaux(
                devis = devis,
                onOffrirTva = if (fige) null else onOffrirTva,
                affichage = affichage,
            )

            if (!fige) {
                RangeePastilles(
                    options = StatutDevis.entries,
                    retenue = devis.devis.statut,
                    libelle = { it.libelle },
                    onChoisir = onStatut,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (manques.isNotEmpty()) {
                // Dit ici, avant l'export, et plus sur le document : une note
                // adressée à soi-même n'a pas à partir chez le client.
                Encart(
                    texte = "Le document partira sans " + manques.joinToString(", ") +
                        ". L'en-tête de l'entreprise — raison sociale, adresse, SIRET, " +
                        "logo — se renseigne dans les Réglages.",
                )
            }

            // Exporter, et non « envoyer » : le devis part par le sélecteur du
            // système, et le statut reste celui que l'utilisateur posera lui-même
            // quand il saura que le client l'a reçu.
            BoutonPlein(
                texte = "Exporter en PDF et envoyer",
                onClick = onExporterPdf,
                modifier = Modifier.fillMaxWidth(),
            )
            if (devis.devis.statut == StatutDevis.ACCEPTE) {
                // Planifier avant de facturer, et c'est l'ordre du métier : un
                // chantier accepté se pose d'abord sur un planning, la facture
                // ne vient qu'après l'avoir fait. Les deux boutons dans l'autre
                // sens auraient mis en avant ce qu'on réclame avant ce qu'on
                // doit encore aller faire.
                BoutonContour(
                    texte = "Planifier l'intervention",
                    onClick = onPlanifier,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Sur un devis déjà facturé, le même geste rouvre la facture
                // vivante, ou en établit une nouvelle si la précédente a été
                // annulée ou entièrement créditée : c'est le dépôt qui tranche.
                BoutonContour(
                    texte = when {
                        facture == null -> "Établir la facture"
                        facture.statut == StatutFacture.ANNULEE -> "Établir une nouvelle facture"
                        else -> "Ouvrir la facture"
                    },
                    onClick = onFacturer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!fige) {
                BoutonContour(
                    texte = "Supprimer ce devis",
                    onClick = onSupprimer,
                    modifier = Modifier.fillMaxWidth(),
                    couleur = MaterialTheme.colorScheme.error,
                )
            }
            EspaceVertical(24)
        }
    }

    if (saisieOuverte) {
        DialogueLigneDevis(
            onValider = { designation, quantite, unite, prix ->
                onAjouterLigne(designation, quantite, unite, prix)
                saisieOuverte = false
            },
            onFermer = { saisieOuverte = false },
            tauxTva = tauxDocument,
            saisieTtc = affichage.saisieTtc,
        )
    }

    ligneOuverte?.let { ligne ->
        DialogueModifierLigne(
            ligne = ligne,
            onEnregistrer = {
                onModifierLigne(it)
                ligneOuverte = null
            },
            onSupprimer = {
                onSupprimerLigne(ligne)
                ligneOuverte = null
            },
            onFermer = { ligneOuverte = null },
            tauxTva = tauxDocument,
            saisieTtc = affichage.saisieTtc,
        )
    }

    importFournisseur?.let { etat ->
        FeuilleImportFournisseur(etat = etat, actions = actionsImport)
    }

    if (magasinOuvert) {
        FeuilleMateriel(
            magasin = magasin,
            coefficientParDefaut = coefficientMateriel,
            onChoisir = onAjouterArticle,
            onFermer = { magasinOuvert = false },
        )
    }

    if (catalogueOuvert) {
        FeuilleCatalogue(
            catalogue = catalogue,
            dejaAuDevis = devis.lignes.map { it.designation }.toSet(),
            onChoisir = { prestation ->
                // Une prestation dégressive fait confirmer son nombre d'unités :
                // c'est lui qui découpe les tranches, et une ligne posée ne se
                // redécoupe pas. Les autres arrivent à la quantité proposée.
                val tarif = TarifDegressif(prestation.prixUnitaire, paliers[prestation.id].orEmpty())
                if (prestation.parUnite && tarif.degressif) {
                    degressiveOuverte = prestation
                } else {
                    onAjouterPrestation(prestation, null)
                }
            },
            onCreer = onCreerPrestation,
            unitesVisees = unitesVisees,
            onFermer = { catalogueOuvert = false },
        )
    }

    degressiveOuverte?.let { prestation ->
        DialogueQuantiteDegressive(
            prestation = prestation,
            tarif = TarifDegressif(prestation.prixUnitaire, paliers[prestation.id].orEmpty()),
            proposee = unitesVisees,
            onValider = { quantite ->
                onAjouterPrestation(prestation, quantite)
                degressiveOuverte = null
            },
            onFermer = { degressiveOuverte = null },
        )
    }

    if (clientOuvert) {
        DialogueChoixClient(
            clients = clients,
            onChoisir = {
                onClient(it)
                clientOuvert = false
            },
            onFermer = { clientOuvert = false },
        )
    }

    if (machineOuverte) {
        DialogueChoixMachine(
            parc = parc,
            retenue = devis.devis.equipementId,
            onChoisir = {
                onMachine(it)
                machineOuverte = false
            },
            onFermer = { machineOuverte = false },
        )
    }
}

/** Une valeur qu'on lit sans pouvoir la changer : l'objet d'un devis figé. */
@Composable
private fun LigneLecture(intitule: String, valeur: String) {
    Column {
        Text(
            text = intitule,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = valeur, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Une ligne du devis.
 *
 * **Le toucher ouvre la ligne**, et c'est tout ce qu'il fait. Il l'offrait, et
 * l'appui long la supprimait sans rien demander : deux gestes cachés, dont l'un
 * accordait un geste commercial d'un contact involontaire — gants aux mains, sur
 * un capot, c'est le geste le plus facile à faire par erreur. Offrir et retirer
 * se font désormais dans la boîte de la ligne, où ils se voient et où le retrait
 * se confirme.
 *
 * Une ligne offerte garde son prix, barré, et annonce « offert ». Le client doit
 * lire ce qu'on lui a donné : une remise invisible n'est pas un argument.
 */
@Composable
private fun LigneDuDevis(
    ligne: LigneDevis,
    /** `null` sur un devis figé : la ligne se lit, elle ne s'ouvre plus. */
    onClick: (() -> Unit)?,
    /** Le taux auquel montrer aussi le TTC de la ligne ; `null` pour le HT seul. */
    tauxTtc: Double? = null,
) {
    val vert = LocalStatuts.current.termine
    Carte(contour = true, onClick = onClick) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = ligne.designation, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${Nombres.enTexte(ligne.quantite)} ${ligne.unite} × " +
                        Nombres.enEuros(ligne.prixUnitaire),
                    style = StyleChiffrePetit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ligne.offerte) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Nombres.enEuros(ligne.montantAvantGeste),
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                    Puce(texte = "OFFERT", couleur = vert, fond = vert.copy(alpha = 0.18f))
                }
            } else if (tauxTtc != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "${Nombres.enEuros(ligne.montant)} HT", style = StyleChiffrePetit)
                    Text(
                        text = "${Nombres.enEuros(ChiffrageTtc.versTtc(ligne.montant.auCentime(), tauxTtc))} TTC",
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(text = Nombres.enEuros(ligne.montant), style = StyleChiffrePetit)
            }
        }
    }
}

/**
 * La boîte d'une ligne : la modifier, l'offrir, la retirer.
 *
 * La saisie vit en état local et n'est écrite qu'à « Enregistrer » : une ligne
 * de devis réécrite à chaque frappe ferait passer le total par des montants que
 * personne n'a voulus. Ce que la boîte ne montre pas — le prix d'achat, le
 * marqueur du déplacement — traverse par `copy` : le perdre fausserait la marge,
 * ou le recalcul du trajet.
 *
 * **Retirer se confirme**, et la confirmation nomme la ligne : c'était un appui
 * long sans question, et une ligne chiffrée disparue se ressaisit de mémoire.
 */
@Composable
private fun DialogueModifierLigne(
    ligne: LigneDevis,
    onEnregistrer: (LigneDevis) -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    tauxTva: Double? = null,
    saisieTtc: Boolean = false,
) {
    // Ouverte en TTC, la boîte montre le prix TTC de la ligne : c'est la valeur
    // qu'on y pense, et la retoucher sans y toucher la réécrit au même HT.
    val ouvreEnTtc = tauxTva != null && saisieTtc
    var designation by remember(ligne.id) { mutableStateOf(ligne.designation) }
    var quantite by remember(ligne.id) { mutableStateOf(Nombres.enTexte(ligne.quantite)) }
    var unite by remember(ligne.id) { mutableStateOf(ligne.unite) }
    var prix by remember(ligne.id) {
        mutableStateOf(
            Nombres.enTexte(
                if (ouvreEnTtc && tauxTva != null) {
                    ChiffrageTtc.versTtc(ligne.prixUnitaire, tauxTva)
                } else {
                    ligne.prixUnitaire
                },
            ),
        )
    }
    var enTtc by remember(ligne.id) { mutableStateOf(ouvreEnTtc) }
    var offerte by remember(ligne.id) { mutableStateOf(ligne.offerte) }
    var confirmerRetrait by remember(ligne.id) { mutableStateOf(false) }

    val quantiteLue = Nombres.versDecimal(quantite)
    // Le prix retenu est toujours le HT : c'est lui que porte la ligne. Une
    // valeur retapée en TTC à l'identique ne déplace donc le HT que si l'arrondi
    // l'impose — et le TTC recalculé, dit sous le champ, le montre.
    val prixLu = prixHtSaisi(prix, enTtc, tauxTva).let { lu ->
        if (enTtc && tauxTva != null && lu != null &&
            ChiffrageTtc.versTtc(ligne.prixUnitaire, tauxTva) == Nombres.versDecimal(prix)
        ) {
            ligne.prixUnitaire
        } else {
            lu
        }
    }
    val valide = designation.isNotBlank() &&
        quantiteLue != null && quantiteLue > 0.0 &&
        prixLu != null && prixLu >= 0.0

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Modifier la ligne") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChampTexte(libelle = "Désignation", valeur = designation, onValeur = { designation = it })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChampTexte(
                        libelle = "Quantité",
                        valeur = quantite,
                        onValeur = { quantite = it },
                        clavier = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    ChampTexte(
                        libelle = "Unité",
                        valeur = unite,
                        onValeur = { unite = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                ChampPrixLigne(
                    prix = prix,
                    onPrix = { prix = it },
                    enTtc = enTtc,
                    onEnTtc = {
                        prix = basculerPrix(prix, versTtc = it, tauxTva = tauxTva)
                        enTtc = it
                    },
                    tauxTva = tauxTva,
                )
                // Offrir est un geste commercial : il se coche, en toutes lettres,
                // et se lit sur le devis en prix barré.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = offerte, onCheckedChange = { offerte = it })
                    Text(
                        text = "Ligne offerte — le prix reste affiché, barré",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (ligne.deplacement) {
                    Text(
                        text = "Ligne produite par le déplacement : elle sera refaite au " +
                            "prochain enregistrement du trajet, et cette retouche perdue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { confirmerRetrait = true }) {
                    Text(text = "Retirer cette ligne", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onEnregistrer(
                        ligne.copy(
                            designation = designation,
                            quantite = quantiteLue ?: ligne.quantite,
                            unite = unite,
                            prixUnitaire = prixLu ?: ligne.prixUnitaire,
                            offerte = offerte,
                        ),
                    )
                },
                enabled = valide,
            ) { Text(text = "Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    if (confirmerRetrait) {
        AlertDialog(
            onDismissRequest = { confirmerRetrait = false },
            title = { Text(text = "Retirer la ligne ?") },
            text = {
                Text(
                    text = "« ${ligne.designation} » " +
                        "(${Nombres.enEuros(ligne.montantAvantGeste)} HT) disparaît du devis.",
                )
            },
            confirmButton = {
                TextButton(onClick = onSupprimer) {
                    Text(text = "Retirer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmerRetrait = false }) { Text(text = "Garder") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/**
 * Combien d'unités, pour une prestation dégressive.
 *
 * Le nombre est **proposé** — celui des unités intérieures de la machine du
 * devis — et se change ici, avant que les lignes soient posées : c'est lui qui
 * découpe les tranches (« 1 u à 100 € », « 2e unité à 80 € »), et une ligne
 * posée ne se redécoupe pas. Les tranches et le total se lisent pendant la
 * saisie, comme dans [DialoguePaliers] : des prix unitaires se lisent mal, leur
 * somme se lit tout de suite.
 */
@Composable
private fun DialogueQuantiteDegressive(
    prestation: Prestation,
    tarif: TarifDegressif,
    proposee: Int,
    onValider: (Int) -> Unit,
    onFermer: () -> Unit,
) {
    var saisie by remember(prestation.id) { mutableStateOf(proposee.coerceAtLeast(1).toString()) }
    val nombre = saisie.trim().toIntOrNull()?.takeIf { it in 1..MAXIMUM_UNITES }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = prestation.designation) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Prix dégressif par unité. Le nombre proposé est celui des unités " +
                        "de la machine du devis ; il se change ici, avant de poser les lignes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChampTexte(
                    libelle = "Nombre d'unités",
                    valeur = saisie,
                    onValeur = { saisie = it },
                    clavier = KeyboardType.Number,
                )
                if (nombre != null) {
                    tarif.tranches(nombre).forEach { tranche ->
                        Text(
                            text = listOfNotNull(
                                tranche.mention() ?: "1re unité",
                                "${tranche.nombre} × ${Nombres.enEuros(tranche.prixUnitaire)}",
                            ).joinToString(" — ") + " = " + Nombres.enEuros(tranche.montant),
                            style = StyleChiffrePetit,
                        )
                    }
                    Text(
                        text = "Total ${Nombres.enEuros(tarif.total(nombre))} HT",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { nombre?.let(onValider) }, enabled = nombre != null) {
                Text(text = "Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** Au-delà, ce n'est plus un multi-split mais une faute de frappe. */
private const val MAXIMUM_UNITES = 99

/**
 * La machine que vise le devis, dans le parc de son client.
 *
 * Les groupes, et leurs unités en retrait : viser le groupe compte toutes ses
 * unités, viser une unité n'en compte qu'une — on chiffre alors ce qu'on fait sur
 * celle-là. « Aucune » rend le devis à une unité, ce qui est juste pour un devis
 * qui ne porte sur aucune machine en particulier.
 */
@Composable
private fun DialogueChoixMachine(
    parc: List<GroupeMachines>,
    retenue: String?,
    onChoisir: (Equipement?) -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Machine du devis") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "aucune") {
                    Carte(onClick = { onChoisir(null) }, relief = retenue == null) {
                        Text(text = "Aucune machine", style = MaterialTheme.typography.titleSmall)
                    }
                }
                parc.forEach { groupe ->
                    item(key = groupe.groupe.id) {
                        Carte(
                            onClick = { onChoisir(groupe.groupe) },
                            relief = retenue == groupe.groupe.id,
                        ) {
                            Text(text = groupe.groupe.nom, style = MaterialTheme.typography.titleSmall)
                            if (groupe.unites.isNotEmpty()) {
                                Text(
                                    text = "${groupe.unites.size} unités intérieures",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    groupe.unites.forEach { unite ->
                        item(key = unite.id) {
                            Carte(
                                onClick = { onChoisir(unite) },
                                relief = retenue == unite.id,
                                modifier = Modifier.padding(start = 20.dp),
                            ) {
                                Text(text = unite.nom, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text(text = "Fermer") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun CarteTotaux(
    devis: DevisComplet,
    /** `null` sur un devis figé : le geste ne se propose plus. */
    onOffrirTva: (() -> Unit)?,
    affichage: AffichageChiffrage = AffichageChiffrage(),
) {
    val statuts = LocalStatuts.current
    val assujetti = devis.devis.assujettiTva
    Carte(relief = true) {
        // Les sous-totaux viennent avant le total qu'ils composent, et chacun dit
        // son HT et son TTC : c'est la lecture « matériel à tant, main-d'œuvre à
        // tant » qu'on fait au client. Chaque TVA s'arrondit à part, et le total
        // en dessous fait foi — le dire évite de chercher le centime.
        if (affichage.sousTotaux && devis.lignes.isNotEmpty()) {
            val sous = ChiffrageTtc.sousTotaux(devis.lignes, devis.devis.tauxTva, assujetti)
            sous.forEach { st ->
                LigneTotal(
                    intitule = st.famille.libelle,
                    valeur = if (assujetti) {
                        "${Nombres.enEuros(st.ht)} HT · ${Nombres.enEuros(st.ttc)} TTC"
                    } else {
                        Nombres.enEuros(st.ht)
                    },
                )
            }
            if (assujetti && sous.size > 1) {
                Text(
                    text = "TVA arrondie par famille : le total ci-dessous fait foi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        LigneTotal("Total HT", Nombres.enEuros(devis.totalHt))

        if (devis.devis.assujettiTva) {
            LigneTotal("TVA ${Nombres.enTexte(devis.devis.tauxTva)} %", Nombres.enEuros(devis.tvaDue))
            // La remise apparaît sous la TVA et non à sa place : le document doit
            // montrer que la taxe est due et qu'elle a été prise en charge. Une
            // TVA escamotée serait un document faux.
            if (devis.devis.tvaOfferte) {
                LigneTotal(
                    intitule = "Remise commerciale — TVA offerte",
                    valeur = "− ${Nombres.enEuros(devis.remiseTva)}",
                    couleur = statuts.termine,
                )
            }
        } else {
            // Franchise en base : pas de TVA, et la mention est obligatoire.
            Text(
                // La constante, et non la phrase recopiée : elle paraît aussi sur le
                // PDF, et deux formulations divergentes enverraient chez un client
                // une mention qui n'est pas celle du code général des impôts.
                text = Parametres.MENTION_FRANCHISE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = "Total TTC", style = MaterialTheme.typography.titleSmall)
            Text(
                text = Nombres.enEuros(devis.totalTtc),
                style = StyleChiffre,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Ce que le matériel rapporte, pour le technicien et jamais pour le
        // client : le prix d'achat ne sort sur aucun document, et un test le
        // vérifie. La main-d'œuvre n'y est pas — elle n'a pas de prix d'achat
        // mais un coût horaire interne, qui est une autre grandeur.
        devis.margeMateriel?.let { marge ->
            val brute = marge.brute
            val marque = marge.tauxDeMarque
            Text(
                text = buildString {
                    append("Marge matériel : ")
                    append(Nombres.enEuros(brute ?: 0.0))
                    if (marque != null) append(" (${Nombres.enTexte(marque, 0)} % de marque)")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if ((brute ?: 0.0) >= 0.0) statuts.termine else statuts.urgence,
            )
        }

        // Ce que le geste a coûté, pour le technicien et non pour le client :
        // c'est le chiffre qui se regarde avant d'envoyer, pas après.
        if (devis.gestesCommerciaux > 0) {
            Text(
                text = "Gestes commerciaux : ${Nombres.enEuros(devis.gestesCommerciaux)}",
                style = MaterialTheme.typography.bodySmall,
                color = statuts.termine,
            )
        }

        // Les deux lectures se changent ici, sur le devis qu'on regarde, et le
        // choix devient le réglage : c'est le même interrupteur que dans les
        // Réglages, et non une seconde préférence qui pourrait le contredire.
        if (devis.lignes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (assujetti) {
                    CaseLecture(
                        texte = "TTC par ligne",
                        cochee = affichage.ttcParLigne,
                        onBasculer = { affichage.onTtcParLigne(!affichage.ttcParLigne) },
                    )
                }
                CaseLecture(
                    texte = "Sous-totaux",
                    cochee = affichage.sousTotaux,
                    onBasculer = { affichage.onSousTotaux(!affichage.sousTotaux) },
                )
            }
        }

        // Le geste n'est proposé qu'à une entreprise assujettie : en franchise en
        // base il n'y a pas de TVA à offrir, et un bouton qui n'agirait sur rien
        // ferait croire à une remise accordée.
        if (devis.devis.assujettiTva && onOffrirTva != null) {
            BoutonContour(
                texte = if (devis.devis.tvaOfferte) "Reprendre la TVA offerte" else "Offrir la TVA",
                onClick = onOffrirTva,
                modifier = Modifier.fillMaxWidth(),
                couleur = if (devis.devis.tvaOfferte) {
                    MaterialTheme.colorScheme.error
                } else {
                    statuts.termine
                },
            )
        }
    }
}

@Composable
private fun LigneTotal(
    intitule: String,
    valeur: String,
    couleur: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = intitule,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = valeur, style = StyleChiffrePetit, color = couleur)
    }
}

/**
 * Une ligne saisie à la main. Partagée par le devis et le brouillon de facture :
 * les deux documents se complètent de la même façon, et deux boîtes jumelles
 * auraient fini par ne plus valider la même chose.
 */
@Composable
internal fun DialogueLigneDevis(
    onValider: (String, Double, String, Double) -> Unit,
    onFermer: () -> Unit,
    titre: String = "Ligne de devis",
    /** Le taux du document, `null` en franchise : pas de TTC à proposer. */
    tauxTva: Double? = null,
    /** La saisie s'ouvre en TTC — le réglage, que la boîte permet de changer. */
    saisieTtc: Boolean = false,
) {
    var designation by remember { mutableStateOf("") }
    var quantite by remember { mutableStateOf("1") }
    var unite by remember { mutableStateOf("") }
    var prix by remember { mutableStateOf("") }
    var enTtc by remember { mutableStateOf(tauxTva != null && saisieTtc) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = titre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChampTexte(libelle = "Désignation", valeur = designation, onValeur = { designation = it })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChampTexte(
                        libelle = "Quantité",
                        valeur = quantite,
                        onValeur = { quantite = it },
                        clavier = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    ChampTexte(
                        libelle = "Unité",
                        valeur = unite,
                        onValeur = { unite = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                ChampPrixLigne(
                    prix = prix,
                    onPrix = { prix = it },
                    enTtc = enTtc,
                    onEnTtc = {
                        prix = basculerPrix(prix, versTtc = it, tauxTva = tauxTva)
                        enTtc = it
                    },
                    tauxTva = tauxTva,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValider(
                        designation,
                        Nombres.versDecimal(quantite) ?: 1.0,
                        unite,
                        prixHtSaisi(prix, enTtc, tauxTva) ?: 0.0,
                    )
                },
                enabled = designation.isNotBlank(),
            ) {
                Text(text = "Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun DialogueChoixClient(
    clients: List<com.frigopro.app.data.Client>,
    onChoisir: (com.frigopro.app.data.Client) -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Client du devis") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = clients, key = { it.id }) { client ->
                    Carte(onClick = { onChoisir(client) }) {
                        Text(text = client.nom, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = client.ville,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text(text = "Fermer") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * Le catalogue de prestations, en feuille.
 *
 * Elle reste **ouverte** après un choix : un devis porte rarement une seule
 * ligne, et la refermer à chaque ajout obligerait à la rouvrir trois fois pour
 * un déplacement, une main d'œuvre et une recharge. Ce qui est déjà au devis
 * porte une coche — non pour l'interdire (une prestation peut légitimement
 * figurer deux fois) mais pour qu'on sache où l'on en est.
 *
 * Les familles sont des onglets plutôt qu'une longue liste : vingt et une
 * lignes ne se parcourent pas d'un pouce sur une échelle, et un frigoriste qui
 * cherche une recharge sait qu'il cherche dans « Fluide ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeuilleCatalogue(
    catalogue: Map<CategoriePrestation, List<Prestation>>,
    dejaAuDevis: Set<String>,
    onChoisir: (Prestation) -> Unit,
    /**
     * Inscrire une prestation au catalogue depuis la feuille. `null` là où le
     * document n'a pas ce chemin — le brouillon de facture, qui puise dans le
     * catalogue sans l'enrichir : le bouton n'y paraît pas.
     */
    onCreer: ((Prestation) -> Unit)?,
    unitesVisees: Int,
    onFermer: () -> Unit,
) {
    // `null` vaut « toutes les familles » : c'est ce qu'on veut en ouvrant,
    // quand on ne sait pas encore sous quel rayon ranger ce qu'on cherche.
    var famille by remember { mutableStateOf<CategoriePrestation?>(null) }
    var creationOuverte by remember { mutableStateOf(false) }
    val statuts = LocalStatuts.current

    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Catalogue", style = MaterialTheme.typography.titleLarge)

            // Dit avant de choisir, et non découvert après : une quantité qui
            // s'affiche à 3 sans explication se lit comme une erreur de saisie.
            if (unitesVisees > 1) {
                Encart(
                    texte = "$unitesVisees unités intérieures : les prestations comptées " +
                        "par unité arriveront avec cette quantité, modifiable ensuite.",
                )
            }

            if (catalogue.isEmpty()) {
                Encart(
                    texte = "Le catalogue est vide. Il est livré avec les intitulés du métier " +
                        "mais sans les prix : un tarif est celui d'une entreprise, pas d'un " +
                        "métier, et il se renseigne dans les Réglages.",
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PuceFamille(texte = "Tous", retenue = famille == null, onClick = { famille = null })
                CategoriePrestation.entries.forEach { candidate ->
                    PuceFamille(
                        texte = candidate.libelle,
                        retenue = famille == candidate,
                        onClick = { famille = candidate },
                    )
                }
            }

            val montrees = catalogue
                .filterKeys { famille == null || it == famille }
                .entries
                .sortedBy { it.key.ordinal }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = HauteurCatalogue)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                montrees.forEach { (categorie, prestations) ->
                    prestations.forEach { prestation ->
                        LignePrestation(
                            prestation = prestation,
                            categorie = categorie,
                            deja = prestation.designation in dejaAuDevis,
                            couleurDeja = statuts.termine,
                            onChoisir = { onChoisir(prestation) },
                        )
                    }
                }
                EspaceVertical(24)
            }

            // Créer la prestation **ici**, sans quitter le chiffrage : une pièce
            // qu'il faudrait aller déclarer dans les Réglages finit saisie en ligne
            // libre, et le catalogue ne grossit jamais. Elle y entre et rejoint le
            // devis du même geste, puisque c'est bien pour lui qu'on la saisit.
            if (onCreer != null) {
                BoutonContour(
                    texte = "+ Nouvelle prestation au catalogue",
                    onClick = { creationOuverte = true },
                    modifier = Modifier.fillMaxWidth(),
                    couleur = MaterialTheme.colorScheme.secondary,
                )
            }
            EspaceVertical(16)
        }
    }

    if (creationOuverte && onCreer != null) {
        DialoguePrestation(
            prestation = null,
            onValider = {
                onCreer(it)
                creationOuverte = false
            },
            onFermer = { creationOuverte = false },
        )
    }
}

/** La hauteur de la liste du catalogue : de quoi en voir six sans noyer l'écran. */
private val HauteurCatalogue = 420.dp

@Composable
private fun PuceFamille(texte: String, retenue: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (retenue) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        onClick = onClick,
    ) {
        Text(
            text = texte,
            style = MaterialTheme.typography.labelMedium,
            color = if (retenue) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            maxLines = 1,
        )
    }
}

/**
 * Une prestation du catalogue.
 *
 * Le prix à zéro est dit en clair — « prix à renseigner » — plutôt qu'affiché
 * « 0 € » : un zéro se lit comme une gratuité, ce qui est faux, tandis qu'une
 * mention appelle la correction. C'est la contrepartie assumée d'un catalogue
 * livré sans tarifs.
 */
@Composable
private fun LignePrestation(
    prestation: Prestation,
    categorie: CategoriePrestation,
    deja: Boolean,
    couleurDeja: Color,
    onChoisir: () -> Unit,
) {
    // « par unité » se lit sur la ligne du catalogue et pas seulement dans sa
    // boîte d'édition : c'est ce qui explique la quantité pré-remplie.
    val detail = buildString {
        append(categorie.libelle)
        append(" · ")
        if (prestation.tarifee) {
            append(Nombres.enEuros(prestation.prixUnitaire))
            if (prestation.unite.isNotBlank()) append(" / ${prestation.unite}")
        } else {
            append("prix à renseigner")
        }
        if (prestation.parUnite) append(" · par unité")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onChoisir,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prestation.designation,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = StyleChiffrePetit,
                    color = if (prestation.tarifee) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Puce(
                texte = if (deja) "✓" else "+",
                couleur = if (deja) couleurDeja else MaterialTheme.colorScheme.primary,
                fond = if (deja) {
                    couleurDeja.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                },
            )
        }
    }
}

/**
 * Le corps du courriel qui porte le devis.
 *
 * Court et neutre : il est relu dans la messagerie avant d'être envoyé, et une
 * formule trop longue se fait effacer. Il nomme le devis, parce qu'un courriel
 * avec une pièce jointe et aucun texte finit parfois en indésirable.
 */
/**
 * Comment le devis se saisit et se lit : les réglages « HT ou TTC », et les
 * deux gestes qui en changent la lecture depuis le devis ouvert.
 */
data class AffichageChiffrage(
    val saisieTtc: Boolean = false,
    val ttcParLigne: Boolean = false,
    val sousTotaux: Boolean = false,
    val onTtcParLigne: (Boolean) -> Unit = {},
    val onSousTotaux: (Boolean) -> Unit = {},
)

/** Une lecture qui se coche, toute la rangée sensible : gants aux mains. */
@Composable
private fun CaseLecture(texte: String, cochee: Boolean, onBasculer: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = LocalCibles.current.case)
            .clickable(onClick = onBasculer),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CaseACocher(cochee = cochee)
        Text(text = texte, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Le prix d'une ligne, saisi HT ou TTC.
 *
 * Le choix ne se propose que sur un document assujetti — [tauxTva] `null` en
 * franchise, où HT et TTC ne font qu'un. En TTC, le champ dit dessous le HT qui
 * sera écrit **et le TTC qu'il redonne**, qui peut s'écarter d'un centime de la
 * saisie : c'est ce second chiffre que le client lira (voir [ChiffrageTtc]).
 */
@Composable
internal fun ChampPrixLigne(
    prix: String,
    onPrix: (String) -> Unit,
    enTtc: Boolean,
    onEnTtc: (Boolean) -> Unit,
    tauxTva: Double?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tauxTva != null) {
            RangeePastilles(
                options = listOf(false, true),
                retenue = enTtc,
                libelle = { if (it) "Prix TTC" else "Prix HT" },
                onChoisir = onEnTtc,
            )
        }
        ChampTexte(
            libelle = if (enTtc) "Prix unitaire TTC" else "Prix unitaire HT",
            valeur = prix,
            onValeur = onPrix,
            clavier = KeyboardType.Decimal,
        )
        val ht = prixHtSaisi(prix, enTtc, tauxTva)
        if (tauxTva != null && ht != null) {
            Text(
                text = if (enTtc) {
                    "Soit ${Nombres.enEuros(ht)} HT, qui font " +
                        "${Nombres.enEuros(ChiffrageTtc.versTtc(ht, tauxTva))} TTC à " +
                        "${Nombres.enTexte(tauxTva)} %."
                } else {
                    "Soit ${Nombres.enEuros(ChiffrageTtc.versTtc(ht, tauxTva))} TTC à " +
                        "${Nombres.enTexte(tauxTva)} %."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Le HT d'une saisie de prix, `null` si elle ne se lit pas. */
internal fun prixHtSaisi(texte: String, enTtc: Boolean, tauxTva: Double?): Double? {
    val lu = Nombres.versDecimal(texte) ?: return null
    return if (enTtc && tauxTva != null) ChiffrageTtc.versHt(lu, tauxTva) else lu
}

/** Le champ réécrit dans l'autre sens quand on change de pastille : 50 HT devient 60 TTC. */
internal fun basculerPrix(texte: String, versTtc: Boolean, tauxTva: Double?): String {
    val lu = Nombres.versDecimal(texte) ?: return texte
    if (tauxTva == null) return texte
    return Nombres.enTexte(if (versTtc) ChiffrageTtc.versTtc(lu, tauxTva) else ChiffrageTtc.versHt(lu, tauxTva))
}

private fun corpsDuMessage(devis: DevisComplet?): String {
    val numero = devis?.devis?.numero.orEmpty()
    val objet = devis?.devis?.objet.orEmpty()
    return buildString {
        append("Bonjour,\n\n")
        append("Veuillez trouver ci-joint notre devis")
        if (numero.isNotBlank()) append(" $numero")
        if (objet.isNotBlank()) append(" concernant $objet")
        append(".\n\nCordialement,")
    }
}
