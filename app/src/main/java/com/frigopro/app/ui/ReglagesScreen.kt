package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.PalierPrestation
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.TypeIntervention
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.theme.FrigoProTheme

/**
 * Les pages des Réglages, et l'ordre dans lequel on les ouvre.
 *
 * L'onglet était **six sections déroulées l'une sous l'autre**, ce qui en faisait
 * le plus long écran de l'application : pour changer un taux horaire il fallait
 * faire défiler le technicien, le thème, l'entreprise et le déplacement, et le
 * bouton flottant posé par-dessus n'ajoutait qu'un type d'intervention. Un
 * sommaire répond mieux à la question qu'on se pose en ouvrant cet onglet, qui
 * n'est jamais « que peut-on régler ? » mais « où se règle *ceci* ? ».
 *
 * L'ordre est celui de ce qu'on remplit d'abord : qui je suis, quelle entreprise
 * je suis, ce que je facture, comment je me déplace, ce que je vends. Viennent
 * ensuite ce qu'on règle une fois (l'affichage, les types), puis les deux pages
 * qui ne règlent rien mais **sortent** quelque chose — la sauvegarde et le
 * registre. Elles ferment la marche parce qu'on les ouvre rarement, et non parce
 * qu'elles compteraient moins : la sauvegarde est le seul filet contre un
 * téléphone perdu.
 */
enum class PageReglages(val titre: String) {
    TECHNICIEN("Technicien"),
    ENTREPRISE("Mon entreprise"),
    TARIFS("Tarifs et paiement"),
    DEPLACEMENT("Déplacement"),
    CATALOGUE("Catalogue des prestations"),
    AFFICHAGE("Affichage"),
    TYPES("Types d'intervention"),
    SAUVEGARDE("Sauvegarde"),
    REGISTRE("Registre des fluides"),
}

/** Point d'entrée de l'onglet, branché sur le [ReglagesViewModel]. */
@Composable
fun ReglagesRoute(
    modifier: Modifier = Modifier,
    viewModel: ReglagesViewModel = viewModel(factory = ReglagesViewModel.Factory),
) {
    val types by viewModel.types.collectAsStateWithLifecycle()
    val dialogue by viewModel.dialogue.collectAsStateWithLifecycle()
    val parametres by viewModel.parametres.collectAsStateWithLifecycle()
    val prestations by viewModel.prestations.collectAsStateWithLifecycle()
    val paliers by viewModel.paliers.collectAsStateWithLifecycle()
    val anneesRegistre by viewModel.anneesRegistre.collectAsStateWithLifecycle()
    val documentPret by viewModel.documentPret.collectAsStateWithLifecycle()
    val echecExport by viewModel.echecExport.collectAsStateWithLifecycle()
    val contexte = LocalContext.current

    // Le logo vient de la galerie : aucune permission, le sélecteur du système
    // ne nous donne accès qu'à l'image désignée. Même chemin que les photos de
    // machines.
    val galerie = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { source -> source?.let(viewModel::onLogoChoisi) }

    // Le partage s'ouvre dès que le PDF est écrit, puis le ViewModel oublie le
    // document : sans cet oubli, revenir sur l'onglet rouvrirait le sélecteur.
    LaunchedEffect(documentPret) {
        val fichier = documentPret ?: return@LaunchedEffect
        contexte.envoyerDocument(
            document = fichier,
            objet = "Registre des fluides",
            corps = "Registre des mouvements de fluides frigorigènes ci-joint.",
        )
        viewModel.onDocumentPartage()
    }

    if (echecExport) {
        AlertDialog(
            onDismissRequest = viewModel::onEchecVu,
            title = { Text(text = "Export impossible") },
            text = {
                Text(
                    text = "Le registre n'a pas pu être écrit. Il manque peut-être de la " +
                        "place sur le téléphone : rien de ce qui est consigné n'est perdu, " +
                        "et le document se refait à l'identique.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::onEchecVu) { Text(text = "Fermer") } },
        )
    }

    ReglagesScreen(
        types = types,
        parametres = parametres,
        onAjouterType = viewModel::onAjouterType,
        onRenommerType = viewModel::onRenommerType,
        onSupprimerType = viewModel::onSupprimerType,
        onThemeSombre = viewModel::onThemeSombre,
        onModeGants = viewModel::onModeGants,
        onChronoAuto = viewModel::onChronoAuto,
        onTechnicien = viewModel::onTechnicien,
        onAttestation = viewModel::onAttestation,
        onTauxHoraire = viewModel::onTauxHoraire,
        onCoutHoraireInterne = viewModel::onCoutHoraireInterne,
        onTauxTva = viewModel::onTauxTva,
        onDelaiPaiement = viewModel::onDelaiPaiement,
        onTauxPenalites = viewModel::onTauxPenalites,
        onCoefficientMateriel = viewModel::onCoefficientMateriel,
        onEntreprise = viewModel::onEntreprise,
        onEntrepriseAdresse = viewModel::onEntrepriseAdresse,
        onEntrepriseTelephone = viewModel::onEntrepriseTelephone,
        onEntrepriseEmail = viewModel::onEntrepriseEmail,
        onEntrepriseSiret = viewModel::onEntrepriseSiret,
        onAssujettiTva = viewModel::onAssujettiTva,
        onChoisirLogo = {
            galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRetirerLogo = viewModel::onRetirerLogo,
        chargerPhoto = viewModel::charger,
        prestations = prestations,
        onEnregistrerPrestation = viewModel::onEnregistrerPrestation,
        onSupprimerPrestation = viewModel::onSupprimerPrestation,
        paliers = paliers,
        onDefinirPalier = viewModel::onDefinirPalier,
        onSupprimerPalier = viewModel::onSupprimerPalier,
        tarifDeplacement = ActionsTarifDeplacement(
            onAdresseDepart = viewModel::onAdresseDepart,
            onMode = viewModel::onModeDeplacement,
            onPrixKm = viewModel::onPrixKm,
            onPrixHeure = viewModel::onPrixHeureTrajet,
            onMinimum = viewModel::onMinimumDeplacement,
            onRefacturerPeages = viewModel::onRefacturerPeages,
        ),
        registre = ActionsRegistre(
            annees = anneesRegistre,
            onExporter = viewModel::onExporterRegistre,
        ),
        // La sauvegarde tient ses propres lanceurs et son propre ViewModel :
        // l'écran l'héberge sans la connaître, exactement comme la barre de la
        // tournée le faisait avant elle.
        sauvegarde = { SectionSauvegarde() },
        modifier = modifier,
    )

    // Un intitulé déjà porté par un *autre* type est refusé ; renommer un type
    // en lui-même doit rester possible, ne serait-ce que pour corriger la casse.
    val dejaPris = { intitule: String, exclu: String? ->
        types.any { it.libelle.equals(intitule, ignoreCase = true) && it.id != exclu }
    }

    when (val ouvert = dialogue) {
        DialogueReglages.Creation -> DialogueIntitule(
            titre = "Nouveau type d'intervention",
            libelleAction = "Ajouter",
            messageConflit = MESSAGE_TYPE_EXISTANT,
            estDejaPris = { dejaPris(it, null) },
            onValider = viewModel::onValiderIntitule,
            onFermer = viewModel::onFermerDialogue,
        )

        is DialogueReglages.Renommage -> DialogueIntitule(
            titre = "Renommer le type",
            libelleAction = "Enregistrer",
            messageConflit = MESSAGE_TYPE_EXISTANT,
            intituleInitial = ouvert.type.libelle,
            estDejaPris = { dejaPris(it, ouvert.type.id) },
            onValider = viewModel::onValiderIntitule,
            onFermer = viewModel::onFermerDialogue,
        )

        is DialogueReglages.Suppression -> ConfirmationSuppression(
            type = ouvert.type,
            onConfirmer = viewModel::onConfirmerSuppression,
            onFermer = viewModel::onFermerDialogue,
        )

        null -> Unit
    }
}

private const val MESSAGE_TYPE_EXISTANT = "Ce type existe déjà."

/** Écran sans état : pour l'instant, la seule liste des types d'intervention. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReglagesScreen(
    types: List<TypeIntervention>,
    parametres: Parametres,
    onAjouterType: () -> Unit,
    onRenommerType: (TypeIntervention) -> Unit,
    onSupprimerType: (TypeIntervention) -> Unit,
    onThemeSombre: (Boolean) -> Unit,
    onModeGants: (Boolean) -> Unit,
    onChronoAuto: (Boolean) -> Unit,
    onTechnicien: (String) -> Unit,
    onAttestation: (String) -> Unit,
    onTauxHoraire: (Double) -> Unit,
    onCoutHoraireInterne: (Double) -> Unit,
    onTauxTva: (Double) -> Unit,
    onDelaiPaiement: (Int) -> Unit,
    onTauxPenalites: (Double) -> Unit,
    onCoefficientMateriel: (Double) -> Unit,
    onEntreprise: (String) -> Unit,
    onEntrepriseAdresse: (String) -> Unit,
    onEntrepriseTelephone: (String) -> Unit,
    onEntrepriseEmail: (String) -> Unit,
    onEntrepriseSiret: (String) -> Unit,
    onAssujettiTva: (Boolean) -> Unit,
    onChoisirLogo: () -> Unit,
    onRetirerLogo: () -> Unit,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    prestations: List<Prestation>,
    paliers: Map<String, List<PalierPrestation>>,
    onDefinirPalier: (String, Int, Double) -> Unit,
    onSupprimerPalier: (String) -> Unit,
    onEnregistrerPrestation: (Prestation) -> Unit,
    onSupprimerPrestation: (Prestation) -> Unit,
    /**
     * Le tarif de déplacement, en un objet.
     *
     * Sept rappels de plus auraient porté cette signature à trente-deux
     * paramètres ; les regrouper est le même geste que pour la section du
     * déplacement dans un devis.
     */
    tarifDeplacement: ActionsTarifDeplacement,
    /** Le registre des fluides : les années disponibles, et l'export. */
    registre: ActionsRegistre = ActionsRegistre(),
    /**
     * La page de sauvegarde, posée par la route.
     *
     * Un emplacement plutôt qu'une liste de rappels : la sauvegarde ouvre deux
     * sélecteurs de fichiers du système et tient son propre ViewModel, ce que
     * cet écran n'a pas à savoir. C'est le motif que la barre de la tournée
     * employait déjà pour le même composant.
     */
    sauvegarde: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableStateOf<PageReglages?>(null) }

    // Une seule profondeur à défaire : la page ouverte remplace le sommaire, et
    // le retour système la referme. Toujours pas de graphe de navigation — c'est
    // le motif du devis ouvert, de la machine ouverte et de l'outil ouvert.
    BackHandler(enabled = page != null) { page = null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets pose déjà la marge du bas ; voir `FrigoProApp`.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                // Voir `FrigoProApp` : la coquille pose la marge du haut.
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(text = page?.titre ?: "Réglages", maxLines = 1) },
                navigationIcon = {
                    // Un bouton de retour visible **en plus** du geste système :
                    // un geste qui ne se voit pas n'est pas une fonctionnalité,
                    // et c'est la règle que le projet suit partout.
                    if (page != null) {
                        IconButton(onClick = { page = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Revenir aux réglages",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            // Le bouton flottant n'existe que là où il ajoute quelque chose. Il
            // était auparavant posé sur les six sections à la fois alors qu'il
            // n'ajoutait qu'un type d'intervention, c'est-à-dire l'avant-dernière
            // chose de la page : une action flottante qui ne désigne pas ce qu'on
            // regarde est une invitation à se tromper.
            if (page == PageReglages.TYPES) {
                FloatingActionButton(onClick = onAjouterType) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Ajouter un type d'intervention",
                    )
                }
            }
        },
    ) { marges ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(marges),
            contentPadding = PaddingValues(
                start = MargeEcran,
                end = MargeEcran,
                top = 12.dp,
                bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                null -> sommaire(
                    parametres = parametres,
                    prestations = prestations,
                    types = types,
                    onPage = { page = it },
                )

                PageReglages.TECHNICIEN -> item {
                    SectionTechnicien(
                        parametres = parametres,
                        onTechnicien = onTechnicien,
                        onAttestation = onAttestation,
                    )
                }

                PageReglages.ENTREPRISE -> item {
                    SectionEntreprise(
                        parametres = parametres,
                        chargerPhoto = chargerPhoto,
                        onEntreprise = onEntreprise,
                        onAdresse = onEntrepriseAdresse,
                        onTelephone = onEntrepriseTelephone,
                        onEmail = onEntrepriseEmail,
                        onSiret = onEntrepriseSiret,
                        onAssujettiTva = onAssujettiTva,
                        onChoisirLogo = onChoisirLogo,
                        onRetirerLogo = onRetirerLogo,
                    )
                }

                PageReglages.TARIFS -> item {
                    SectionTarifs(
                        parametres = parametres,
                        onTauxHoraire = onTauxHoraire,
                        onCoutHoraireInterne = onCoutHoraireInterne,
                        onTauxTva = onTauxTva,
                        onDelaiPaiement = onDelaiPaiement,
                        onTauxPenalites = onTauxPenalites,
                        onCoefficientMateriel = onCoefficientMateriel,
                    )
                }

                PageReglages.DEPLACEMENT -> item {
                    SectionDeplacementReglages(
                        parametres = parametres,
                        actions = tarifDeplacement,
                    )
                }

                PageReglages.CATALOGUE -> item {
                    SectionCatalogue(
                        prestations = prestations,
                        onEnregistrer = onEnregistrerPrestation,
                        onSupprimer = onSupprimerPrestation,
                        paliers = paliers,
                        onDefinirPalier = onDefinirPalier,
                        onSupprimerPalier = onSupprimerPalier,
                    )
                }

                PageReglages.TYPES -> {
                    item {
                        Text(
                            text = if (types.isEmpty()) {
                                "Aucun type pour l'instant. Ajoutez-en ici, ou au moment " +
                                    "de saisir une intervention : le type est facultatif, " +
                                    "et la liste démarre vide parce que « fuite de fluide » " +
                                    "est le vocabulaire d'un métier et non celui d'une " +
                                    "entreprise."
                            } else {
                                "Renommer un type met à jour toutes les interventions " +
                                    "qui l'utilisent, y compris les anciennes. Le supprimer " +
                                    "ne perd rien : elles gardent leur intitulé."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(items = types, key = { it.id }) { type ->
                        LigneType(
                            type = type,
                            onRenommer = { onRenommerType(type) },
                            onSupprimer = { onSupprimerType(type) },
                        )
                    }
                }

                PageReglages.AFFICHAGE -> item {
                    SectionAffichage(
                        parametres = parametres,
                        onThemeSombre = onThemeSombre,
                        onModeGants = onModeGants,
                        onChronoAuto = onChronoAuto,
                    )
                }

                PageReglages.SAUVEGARDE -> item { sauvegarde() }

                PageReglages.REGISTRE -> item { SectionRegistre(actions = registre) }
            }
        }
    }
}

/**
 * Le sommaire : une ligne par page, et **ce que la page contient aujourd'hui**.
 *
 * Le résumé est ce qui distingue un sommaire d'un menu, et c'est lui qui fait le
 * travail : « 0,00 € HT · coût interne non réglé » dit d'un coup d'œil qu'il y a
 * là quelque chose à faire, là où « Tarifs et paiement › » aurait demandé
 * d'ouvrir les sept pages l'une après l'autre pour s'en assurer. Rien n'y est
 * stocké — chaque résumé se lit dans les réglages courants, et un compteur en
 * base serait faux au premier tarif saisi.
 */
private fun LazyListScope.sommaire(
    parametres: Parametres,
    prestations: List<Prestation>,
    types: List<TypeIntervention>,
    onPage: (PageReglages) -> Unit,
) {
    val tarifes = prestations.count { it.prixUnitaire > 0.0 }
    val resumes = mapOf(
        PageReglages.TECHNICIEN to listOfNotNull(
            parametres.technicien.ifBlank { "Nom non renseigné" },
            parametres.attestation.ifBlank { null }?.let { "attestation $it" },
        ).joinToString(" · "),
        PageReglages.ENTREPRISE to listOfNotNull(
            parametres.entreprise.ifBlank { "Raison sociale non renseignée" },
            if (parametres.assujettiTva) null else "franchise en base",
            if (parametres.logoFichier == null) "sans logo" else null,
        ).joinToString(" · "),
        PageReglages.TARIFS to listOfNotNull(
            "${Nombres.enEuros(parametres.tauxHoraire)} HT l'heure",
            if (parametres.coutHoraireInterne > 0.0) null else "coût interne non réglé",
            "TVA ${Nombres.enTexte(parametres.tauxTva)} %",
            "${parametres.delaiPaiementJours} j",
        ).joinToString(" · "),
        PageReglages.DEPLACEMENT to listOfNotNull(
            parametres.adresseDepart.ifBlank { "Adresse de départ non renseignée" },
            if (parametres.prixKm > 0.0 || parametres.prixHeureTrajet > 0.0) {
                null
            } else {
                "tarif non réglé"
            },
        ).joinToString(" · "),
        PageReglages.CATALOGUE to when {
            prestations.isEmpty() -> "Catalogue vide"
            tarifes == 0 -> "${prestations.size} prestations, aucune tarifée"
            tarifes < prestations.size ->
                "${prestations.size} prestations, ${prestations.size - tarifes} sans prix"

            else -> "${prestations.size} prestations, toutes tarifées"
        },
        PageReglages.TYPES to when (types.size) {
            0 -> "Aucun type"
            1 -> "1 type"
            else -> "${types.size} types"
        },
        PageReglages.AFFICHAGE to listOfNotNull(
            if (parametres.themeSombre) "Sombre" else "Clair",
            if (parametres.modeGants) "gants" else null,
            if (parametres.chronoAuto) "chrono automatique" else null,
        ).joinToString(" · "),
        PageReglages.SAUVEGARDE to "Exporter ou restaurer une archive",
        PageReglages.REGISTRE to "Règlement (UE) 517/2014",
    )

    items(items = PageReglages.entries, key = { it.name }) { cible ->
        Carte(onClick = { onPage(cible) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = cible.titre, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = resumes[cible].orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LigneType(
    type: TypeIntervention,
    onRenommer: () -> Unit,
    onSupprimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = type.libelle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRenommer) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Renommer ${type.libelle}",
                )
            }
            IconButton(onClick = onSupprimer) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Supprimer ${type.libelle}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Supprimer un type ne perd aucune donnée : le dire évite d'hésiter devant le
 * bouton, et évite surtout de croire qu'on va effacer ses tournées.
 */
@Composable
private fun ConfirmationSuppression(
    type: TypeIntervention,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Supprimer « ${type.libelle} » ?") },
        text = {
            Text(
                text = "Le type quitte la liste. Les interventions qui l'utilisaient " +
                    "gardent leur intitulé : rien n'est perdu.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmer) { Text(text = "Supprimer") }
        },
        dismissButton = {
            TextButton(onClick = onFermer) { Text(text = "Annuler") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ReglagesScreenPreview() {
    FrigoProTheme {
        Surface {
            ReglagesScreen(
                types = listOf(
                    TypeIntervention(id = "t1", libelle = "Entretien annuel"),
                    TypeIntervention(id = "t2", libelle = "Fuite de fluide"),
                    TypeIntervention(id = "t3", libelle = "Mise en service"),
                ),
                parametres = Parametres(technicien = "Anthony O."),
                onAjouterType = {},
                onRenommerType = {},
                onSupprimerType = {},
                onThemeSombre = {},
                onModeGants = {},
                onChronoAuto = {},
                onTechnicien = {},
                onAttestation = {},
                onTauxHoraire = {},
                onCoutHoraireInterne = {},
                onTauxTva = {},
                onDelaiPaiement = {},
                onTauxPenalites = {},
                onCoefficientMateriel = {},
                prestations = emptyList(),
                paliers = emptyMap(),
                onDefinirPalier = { _, _, _ -> },
                onSupprimerPalier = {},
                onEnregistrerPrestation = {},
                onSupprimerPrestation = {},
                onEntreprise = {},
                onEntrepriseAdresse = {},
                onEntrepriseTelephone = {},
                onEntrepriseEmail = {},
                onEntrepriseSiret = {},
                onAssujettiTva = {},
                onChoisirLogo = {},
                onRetirerLogo = {},
                chargerPhoto = { _, _ -> null },
                tarifDeplacement = ActionsTarifDeplacement(),
            )
        }
    }
}
