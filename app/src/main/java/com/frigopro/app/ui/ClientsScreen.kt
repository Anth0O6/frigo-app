package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Capture
import com.frigopro.app.data.CategoriePhoto
import com.frigopro.app.data.Client
import com.frigopro.app.data.Devis
import com.frigopro.app.data.Facture
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.EcheanceMaintenance
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.GroupeClients
import com.frigopro.app.data.GroupeMachines
import com.frigopro.app.data.SuiviMaintenance
import com.frigopro.app.data.initialesDe
import com.frigopro.app.data.nomDeCopie
import com.frigopro.app.data.zonesDe
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.theme.FrigoProTheme
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * Point d'entrée de l'onglet, branché sur le [ClientsViewModel] et sur le
 * [EquipementsViewModel].
 *
 * La fiche d'une machine prend tout l'onglet au lieu de s'empiler dans une
 * feuille : on y regarde des photos, et une feuille à mi-hauteur n'est pas
 * faite pour cela. Le retour système la referme, ce qui tient ici en un
 * [BackHandler] — une seule profondeur à défaire ne justifie pas encore un
 * graphe de navigation.
 */
@Composable
fun ClientsRoute(
    /**
     * Les trois objets que la page d'un client ouvre **ailleurs** : c'est la
     * coquille qui change d'onglet, et le ViewModel partagé de l'onglet visé qui
     * reçoit l'objet — même mécanisme que l'accueil ouvrant une facture à
     * relancer, ou le Planning une intervention.
     */
    onOuvrirIntervention: (Intervention) -> Unit = {},
    onOuvrirFacture: (Facture) -> Unit = {},
    onOuvrirDevis: (Devis) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ClientsViewModel = viewModel(factory = ClientsViewModel.Factory),
    machines: EquipementsViewModel = viewModel(factory = EquipementsViewModel.Factory),
    /**
     * Le plan de maintenance préventive. `viewModel()` rend une seule
     * instance par classe, si bien que c'est **le même** que celui de
     * l'Entretien du Planning : une gamme rattachée ici se voit là sans que
     * rien ne transporte d'état, et une visite consignée ici éteint l'échéance
     * qui paraissait dans la liste. C'est le mécanisme qui fait déjà passer le
     * formulaire d'intervention d'un onglet à l'autre.
     */
    maintenance: MaintenanceViewModel = viewModel(factory = MaintenanceViewModel.Factory),
) {
    val carnet by viewModel.groupes.collectAsStateWithLifecycle()
    val fiche by viewModel.fiche.collectAsStateWithLifecycle()
    val dossier by viewModel.dossier.collectAsStateWithLifecycle()
    val recherche by viewModel.recherche.collectAsStateWithLifecycle()
    val deplies by viewModel.deplies.collectAsStateWithLifecycle()
    val parcsComplets by viewModel.parcsComplets.collectAsStateWithLifecycle()
    // La position dans la liste est tenue par la route, qui reste composée quand
    // une machine s'ouvre : revenir d'une fiche doit retrouver la carte d'où l'on
    // est parti, et non le haut d'un carnet de trois cents lignes.
    val etatListe = rememberLazyListState()
    val parc by machines.parc.collectAsStateWithLifecycle()
    val groupes by machines.groupes.collectAsStateWithLifecycle()
    val ouverte by machines.ouverte.collectAsStateWithLifecycle()
    val unitesOuvertes by machines.unitesOuvertes.collectAsStateWithLifecycle()
    val groupeOuvert by machines.groupeOuvert.collectAsStateWithLifecycle()
    val photos by machines.photosOuvertes.collectAsStateWithLifecycle()
    val historique by machines.historique.collectAsStateWithLifecycle()
    val relevesMachine by machines.relevesMachine.collectAsStateWithLifecycle()
    val echeances by maintenance.echeances.collectAsStateWithLifecycle()
    val gammes by maintenance.gammes.collectAsStateWithLifecycle()
    val pointsGammes by maintenance.points.collectAsStateWithLifecycle()
    val visites by maintenance.releves.collectAsStateWithLifecycle()
    val affectations by maintenance.affectations.collectAsStateWithLifecycle()
    val documentPret by maintenance.documentPret.collectAsStateWithLifecycle()
    val echecExport by maintenance.echecExport.collectAsStateWithLifecycle()
    val contexte = LocalContext.current
    var ficheOuverte by remember { mutableStateOf(false) }

    // L'échéance dont on vient de toucher « Fait », en attente de sa feuille.
    // Tenue par la route comme la feuille du formulaire d'intervention : elle se
    // superpose à la fiche, et la fiche n'a pas à savoir qu'elle existe.
    var visite by remember { mutableStateOf<EcheanceMaintenance?>(null) }

    // L'échéance dont on veut déplacer l'entrée au plan. Deux états distincts
    // plutôt qu'un seul avec un drapeau : on ne consigne pas et ne reporte pas en
    // même temps, et un drapeau aurait permis d'écrire l'état impossible.
    var depart by remember { mutableStateOf<EcheanceMaintenance?>(null) }

    // Le client dont on vient de demander la suppression, en attente de
    // confirmation. Tenu par la route et non par la feuille : c'est ici qu'on a
    // sous la main de quoi compter ce qui disparaît — le parc, les sites.
    var aSupprimer by remember { mutableStateOf<Client?>(null) }
    val dialogue by machines.dialogue.collectAsStateWithLifecycle()
    val agrandie by machines.agrandie.collectAsStateWithLifecycle()
    val selection by machines.selection.collectAsStateWithLifecycle()

    // La boîte ouverte sur la sélection, zone ou gamme. Affichage local, comme
    // l'ouverture de la fiche : la sélection elle-même est au ViewModel.
    var boiteSelection by remember { mutableStateOf<BoiteSelection?>(null) }

    // La prise de vue quitte l'application : la catégorie visée et le fichier à
    // remplir doivent donc survivre à l'aller-retour.
    var capture by remember { mutableStateOf<Pair<CategoriePhoto, Capture>?>(null) }
    val appareilPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { _ ->
        // L'issue annoncée par l'appareil photo n'est pas consultée : un fichier
        // vide — prise de vue abandonnée — est écarté par le dépôt, qui
        // n'enregistre alors aucune photo.
        capture?.let { (categorie, prise) -> machines.onCapture(categorie, prise.nom) }
        capture = null
    }

    var categorieGalerie by remember { mutableStateOf<CategoriePhoto?>(null) }
    val galerie = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { source ->
        val categorie = categorieGalerie
        if (source != null && categorie != null) machines.onPhotoChoisie(categorie, source)
        categorieGalerie = null
    }

    // Le partage s'ouvre dès que le PDF est écrit, puis le ViewModel oublie le
    // document : sans cet oubli, revenir sur l'onglet rouvrirait le sélecteur.
    // Même chaîne que le registre des fluides dans les Réglages.
    LaunchedEffect(documentPret) {
        val fichier = documentPret ?: return@LaunchedEffect
        contexte.envoyerDocument(
            document = fichier,
            objet = "Attestation d'entretien",
            corps = "Récapitulatif des visites de maintenance préventive ci-joint.",
        )
        maintenance.onDocumentPartage()
    }

    if (echecExport) {
        AlertDialog(
            onDismissRequest = maintenance::onEchecVu,
            title = { Text(text = "Export impossible") },
            text = {
                Text(
                    text = "L'attestation n'a pas pu être écrite. Il manque peut-être de la " +
                        "place sur le téléphone : rien de ce qui est consigné n'est perdu, " +
                        "et le document se refait à l'identique.",
                )
            },
            confirmButton = {
                TextButton(onClick = maintenance::onEchecVu) { Text(text = "Fermer") }
            },
        )
    }

    BackHandler(enabled = ouverte != null) { machines.onFermer() }

    // Le retour système défait la sélection avant de quitter l'onglet : c'est la
    // seule profondeur qu'elle ajoute, et un retour qui changerait d'onglet en
    // laissant douze cases cochées derrière lui les ferait retrouver plus tard,
    // prêtes à recevoir un geste qui ne leur était pas destiné.
    BackHandler(enabled = ouverte == null && selection != null) { machines.onQuitterSelection() }

    // La page d'un client se referme au retour — sur son enseigne s'il s'agit
    // d'un site. Ni machine ouverte ni sélection : chacune a son rappel, et les
    // conditions s'excluent plutôt que de compter sur l'ordre des rappels.
    BackHandler(enabled = ouverte == null && selection == null && dossier != null) {
        viewModel.onRetourClient()
    }

    // Les années ne sont proposées que si le parc de ce client suit vraiment une
    // gamme : un bouton qui ne produirait qu'un document blanc vaut moins que pas
    // de bouton, et c'est la même règle que la section de plan absente d'une
    // fiche machine tant qu'aucune gamme n'existe.
    fun attestationDe(client: Client): ActionsAttestation {
        val siennes = parc.filter { it.clientId == client.id }.map { it.id }.toSet()
        val suivi = affectations.any { it.equipementId in siennes }
        return ActionsAttestation(
            annees = if (suivi) SuiviMaintenance.anneesDe(setOf(client.id), parc, visites) else emptyList(),
            onExporter = { annee -> maintenance.onExporterAttestation(client, annee) },
        )
    }

    val machineOuverte = ouverte
    if (machineOuverte != null) {
        EcranEquipement(
            equipement = machineOuverte,
            unites = unitesOuvertes,
            groupe = groupeOuvert,
            photos = photos,
            historique = historique,
            releves = relevesMachine,
            // Filtré ici plutôt que par un flux de plus dans le ViewModel : les
            // trois listes sont déjà observées par cet écran, et un
            // `flatMapLatest` sur la machine ouverte aurait ajouté trois flux
            // pour une liste qui tient en mémoire.
            plan = PlanMachine(
                echeances = echeances.filter { it.equipement.id == machineOuverte.id },
                gammes = gammes,
                visites = visites.filter { it.equipementId == machineOuverte.id },
            ),
            actionsPlan = ActionsPlanMachine(
                onSuivre = { gammeId -> maintenance.onAffecter(machineOuverte.id, gammeId) },
                onNePlusSuivre = { gammeId -> maintenance.onRetirer(machineOuverte.id, gammeId) },
                onConsigner = { visite = it },
                onDepart = { depart = it },
                onRetirerVisite = maintenance::onRetirerVisite,
            ),
            chargerPhoto = machines::charger,
            onPhotographier = { categorie ->
                val prise = machines.preparerCapture()
                capture = categorie to prise
                appareilPhoto.launch(prise.uri)
            },
            onChoisirImage = { categorie ->
                categorieGalerie = categorie
                galerie.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onAgrandir = machines::onAgrandir,
            onOuvrirUnite = machines::onOuvrir,
            onAjouterUnite = { machines.onAjouterUnite(machineOuverte) },
            onRenommer = { machines.onRenommerMachine(machineOuverte) },
            onModifierFiche = { ficheOuverte = true },
            onDupliquer = { machines.onDupliquerMachine(machineOuverte) },
            onSupprimer = { machines.onSupprimerMachine(machineOuverte) },
            onFermer = machines::onFermer,
            onOuvrirIntervention = onOuvrirIntervention,
            modifier = modifier,
        )

        visite?.let { echeance ->
            FeuilleVisite(
                echeance = echeance,
                points = pointsGammes.filter { it.gammeId == echeance.gamme.id },
                onConsigner = { faitLe, notes ->
                    maintenance.onConsigner(echeance.equipement, echeance.gamme, faitLe, notes)
                    visite = null
                },
                onFermer = { visite = null },
            )
        }

        depart?.let { echeance ->
            SelecteurDate(
                date = echeance.depuisLe,
                onDateChoisie = { jour ->
                    maintenance.onReporterDepart(
                        equipementId = echeance.equipement.id,
                        gammeId = echeance.gamme.id,
                        depuisLe = jour,
                    )
                    depart = null
                },
                onFermer = { depart = null },
            )
        }

        if (ficheOuverte) {
            DialogueFicheMachine(
                equipement = machineOuverte,
                // Les zones de **tout le parc** et non du seul client : sur un
                // site, les zones sont celles du site, et un donneur d'ordre à
                // plusieurs magasins réemploie « réserve » et « vente » partout.
                zones = remember(parc) { zonesDe(parc) },
                onValider = {
                    machines.onEnregistrerFiche(it)
                    ficheOuverte = false
                },
                onFermer = { ficheOuverte = false },
            )
        }
    } else if (selection == null && dossier != null) {
        val ouvert = dossier!!
        val client = ouvert.client
        EcranClient(
            dossier = ouvert,
            machines = groupes.filter { it.groupe.clientId == client.id },
            attestation = attestationDe(client),
            actions = ActionsEcranClient(
                onFermer = viewModel::onRetourClient,
                onModifier = { viewModel.onOuvrirFiche(client) },
                onOuvrirFacture = onOuvrirFacture,
                onOuvrirIntervention = onOuvrirIntervention,
                onOuvrirDevis = onOuvrirDevis,
                onOuvrirMachine = machines::onOuvrir,
                onAjouterMachine = { machines.onAjouterMachine(client.id) },
                onSelectionner = { machines.onSelectionner(client.id) },
                onSelectionnerDepuis = machines::onSelectionnerDepuis,
                onOuvrirSite = { site -> viewModel.onOuvrirClient(site.id) },
                onNouveauSite = { viewModel.onNouveauSite(client) }.takeUnless { client.estSite },
            ),
            modifier = modifier,
        )
    } else {
        ClientsScreen(
            carnet = carnet,
            groupes = groupes,
            lecture = LectureCarnet(
                recherche = recherche,
                deplies = deplies,
                parcsComplets = parcsComplets,
            ),
            actionsLecture = ActionsLectureCarnet(
                onRecherche = viewModel::onRecherche,
                onBasculerParc = viewModel::onBasculerParc,
                onVoirToutLeParc = viewModel::onVoirToutLeParc,
            ),
            etatListe = etatListe,
            onNouveauClient = viewModel::onNouveauClient,
            onOuvrirClient = { client -> viewModel.onOuvrirClient(client.id) },
            onNouveauSite = viewModel::onNouveauSite,
            onOuvrirMachine = machines::onOuvrir,
            onAjouterMachine = machines::onAjouterMachine,
            modifier = modifier,
            selection = selection,
            actionsSelection = ActionsSelection(
                onSelectionner = machines::onSelectionner,
                onSelectionnerDepuis = machines::onSelectionnerDepuis,
                onBasculer = machines::onBasculerSelection,
                onTout = machines::onSelectionnerTout,
                onAucune = machines::onDeselectionner,
                onQuitter = machines::onQuitterSelection,
                onZone = { boiteSelection = BoiteSelection.ZONE },
                onGamme = { boiteSelection = BoiteSelection.GAMME }.takeIf { gammes.isNotEmpty() },
            ),
        )

        // Les machines cochées **et toujours là**, chez le seul client de la
        // sélection : c'est sur elles que les deux boîtes comptent et agissent.
        val cochees = selection
            ?.let { courante -> parc.filter { it.clientId == courante.clientId && it.id in courante.ids } }
            .orEmpty()
        if (cochees.isNotEmpty()) {
            when (boiteSelection) {
                BoiteSelection.ZONE -> DialogueZoneSelection(
                    machines = cochees,
                    // Tout le parc, pour la raison dite sur la fiche d'une machine.
                    zones = remember(parc) { zonesDe(parc) },
                    onValider = { zone ->
                        machines.onDonnerZone(zone)
                        boiteSelection = null
                    },
                    onFermer = { boiteSelection = null },
                )

                BoiteSelection.GAMME -> {
                    val ids = cochees.map { it.id }.toSet()
                    DialogueGammeSelection(
                        ids = ids,
                        gammes = gammes,
                        affectations = affectations,
                        onAffecter = { gammeId, depuisLe ->
                            maintenance.onAffecterSelection(ids, gammeId, depuisLe)
                            machines.onQuitterSelection()
                            boiteSelection = null
                        },
                        onRetirer = { gammeId ->
                            maintenance.onRetirerSelection(ids, gammeId)
                            machines.onQuitterSelection()
                            boiteSelection = null
                        },
                        onFermer = { boiteSelection = null },
                    )
                }

                null -> Unit
            }
        }
    }

    // La boîte d'édition se pose sur le carnet comme sur la page d'un client,
    // d'où « Modifier » l'ouvre ; jamais sur une machine ouverte.
    if (ouverte == null) {
        fiche?.let { etat ->
            FicheClient(
                etat = etat,
                onEtatChange = viewModel::onFicheChange,
                onEnregistrer = viewModel::onEnregistrerFiche,
                onFermer = viewModel::onFermerFiche,
                onSupprimer = { aSupprimer = etat.versClient() },
            )
        }
    }

    aSupprimer?.let { condamne ->
        ConfirmationSuppressionClient(
            client = condamne,
            machines = parc.count { it.clientId == condamne.id },
            sites = carnet.firstOrNull { it.donneur.id == condamne.id }?.nombreSites ?: 0,
            onConfirmer = {
                viewModel.onSupprimerClient(condamne)
                aSupprimer = null
            },
            onFermer = { aSupprimer = null },
        )
    }

    agrandie?.let { photo ->
        VisionneusePhoto(
            photo = photo,
            chargerPhoto = machines::charger,
            onSupprimer = machines::onSupprimerPhoto,
            onFermer = machines::onFermerAgrandissement,
        )
    }

    DialoguesMachine(dialogue = dialogue, parc = parc, viewModel = machines)
}

/**
 * Les boîtes de dialogue du parc. Le test du doublon se fait sur les machines
 * *du même client* : deux clients peuvent chacun avoir leur « vitrine salle 2 ».
 */
@Composable
private fun DialoguesMachine(
    dialogue: DialogueEquipement?,
    parc: List<Equipement>,
    viewModel: EquipementsViewModel,
) {
    val dejaPris = { clientId: String, nom: String, exclu: String? ->
        parc.any { it.clientId == clientId && it.nom.equals(nom, ignoreCase = true) && it.id != exclu }
    }

    when (dialogue) {
        // Une unité et un groupe se nomment de la même façon ; ce qui change est le
        // périmètre du doublon. Deux groupes homonymes chez un client sont une
        // confusion, deux unités « Salon » sous deux groupes différents ne le sont
        // pas — c'est même le cas ordinaire d'un immeuble.
        is DialogueEquipement.Creation -> DialogueIntitule(
            titre = dialogue.parent?.let { "Unité de ${it.nom}" } ?: "Nouvelle machine",
            libelleAction = "Ajouter",
            libelleChamp = dialogue.parent?.let { "Nom de l'unité" } ?: "Nom de la machine",
            messageConflit = dialogue.parent
                ?.let { "Ce groupe a déjà une unité de ce nom." }
                ?: MESSAGE_MACHINE_EXISTANTE,
            estDejaPris = { nom ->
                val parent = dialogue.parent
                if (parent == null) {
                    dejaPris(dialogue.clientId, nom, null)
                } else {
                    parc.any { it.parentId == parent.id && it.nom.equals(nom, ignoreCase = true) }
                }
            },
            onValider = viewModel::onValiderNom,
            onFermer = viewModel::onFermerDialogue,
        )

        // Le périmètre du doublon est celui de la machine copiée : chez le client
        // pour un groupe, dans le groupe pour une unité. C'est le même partage
        // que pour la création, et le réécrire ici l'aurait fait diverger.
        is DialogueEquipement.Duplication -> {
            val source = dialogue.equipement
            val parent = source.parentId
            val pris: (String) -> Boolean = { nom ->
                if (parent == null) {
                    dejaPris(source.clientId, nom, null)
                } else {
                    parc.any { it.parentId == parent && it.nom.equals(nom, ignoreCase = true) }
                }
            }
            DialogueIntitule(
                titre = if (parent == null) "Dupliquer la machine" else "Dupliquer l'unité",
                libelleAction = "Dupliquer",
                libelleChamp = if (parent == null) "Nom de la copie" else "Nom de l'unité",
                messageConflit = if (parent == null) {
                    MESSAGE_MACHINE_EXISTANTE
                } else {
                    "Ce groupe a déjà une unité de ce nom."
                },
                // Calculé une fois : `dejaPris` balaie le parc, et un parc de
                // trois cents machines le ferait à chaque recomposition.
                intituleInitial = remember(source.id, parc) { nomDeCopie(source.nom, pris) },
                estDejaPris = pris,
                onValider = viewModel::onValiderNom,
                onFermer = viewModel::onFermerDialogue,
            )
        }

        is DialogueEquipement.Renommage -> DialogueIntitule(
            titre = "Renommer la machine",
            libelleAction = "Enregistrer",
            libelleChamp = "Nom de la machine",
            messageConflit = MESSAGE_MACHINE_EXISTANTE,
            intituleInitial = dialogue.equipement.nom,
            estDejaPris = {
                dejaPris(dialogue.equipement.clientId, it, dialogue.equipement.id)
            },
            onValider = viewModel::onValiderNom,
            onFermer = viewModel::onFermerDialogue,
        )

        is DialogueEquipement.Suppression -> ConfirmationSuppressionMachine(
            equipement = dialogue.equipement,
            unites = parc.count { it.parentId == dialogue.equipement.id },
            onConfirmer = viewModel::onConfirmerSuppression,
            onFermer = viewModel::onFermerDialogue,
        )

        null -> Unit
    }
}

private const val MESSAGE_MACHINE_EXISTANTE = "Ce client a déjà une machine de ce nom."

/**
 * Supprimer une machine efface ses photos, et cela doit être dit : c'est la
 * seule chose qu'une suppression fait disparaître pour de bon. Les interventions
 * passées, elles, gardent son nom.
 */
@Composable
private fun ConfirmationSuppressionMachine(
    equipement: Equipement,
    unites: Int,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Supprimer « ${equipement.nom} » ?") },
        text = {
            Text(
                text = buildString {
                    // Ce qu'un groupe emporte doit être dit avant, pas découvert
                    // après : supprimer un bi-split efface deux fiches d'unité et
                    // leurs photos, et rien ne les ramènera.
                    if (unites > 0) {
                        append(
                            if (unites == 1) {
                                "Son unité intérieure sera supprimée avec lui. "
                            } else {
                                "Ses $unites unités intérieures seront supprimées avec lui. "
                            },
                        )
                    }
                    append(
                        "Ses photos seront effacées. Les interventions déjà faites sur " +
                            "cette machine gardent son nom.",
                    )
                },
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

/** Écran sans état : le carnet, trié alphabétiquement par le dépôt. */
/**
 * La confirmation de suppression d'un client.
 *
 * Elle **nomme ce qui disparaît et ce qui reste**, et les deux comptent autant.
 * Ce qui disparaît, parce qu'un parc de machines emporte des plaques
 * signalétiques photographiées sur un toit, qu'on ne reprendra pas. Ce qui
 * reste, parce que la crainte qui retient le doigt est celle d'effacer une année
 * de tournées — et ce n'est pas ce qui se passe : les interventions, devis et
 * factures gardent leur ligne, le nom recopié dessus, et perdent seulement le
 * lien. C'est la règle que le projet applique déjà au type d'intervention et à
 * la machine.
 *
 * Les comptes sont **dérivés au moment d'afficher** et non stockés : un parc
 * grossit, et un chiffre figé serait faux le lendemain.
 */
@Composable
private fun ConfirmationSuppressionClient(
    client: Client,
    machines: Int,
    sites: Int,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Supprimer ${client.nom} ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val emporte = buildList {
                    if (machines > 0) {
                        add(
                            if (machines == 1) {
                                "sa machine, ses photos et son historique d'étanchéité"
                            } else {
                                "ses $machines machines, leurs photos et leur historique d'étanchéité"
                            },
                        )
                    }
                    if (sites > 0) {
                        add(if (sites == 1) "son site, et tout ce qu'il porte" else "ses $sites sites, et tout ce qu'ils portent")
                    }
                }
                Text(
                    text = if (emporte.isEmpty()) {
                        "La fiche disparaît du carnet. C'est définitif."
                    } else {
                        "Disparaissent avec la fiche : ${emporte.joinToString(", ")}. C'est définitif."
                    },
                )
                Text(
                    text = "Les interventions, devis et factures de ce client sont " +
                        "conservés : ils gardent son nom, et perdent seulement le lien " +
                        "vers la fiche.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmer) {
                Text(text = "Supprimer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    /**
     * Le carnet **en groupes** : chaque donneur d'ordre, et ses sites.
     *
     * Les sites ne sont pas au premier rang : « Carrefour », « Carrefour
     * Part-Dieu » et « Carrefour Vaise » côte à côte feraient trois entrées pour
     * un client. Ils se posent sous le leur, en retrait, et ont sinon exactement
     * la même carte — un site a son adresse, son téléphone et son parc.
     */
    carnet: List<GroupeClients>,
    /**
     * Le parc **par groupe** : un bi-split est un appareil chez le client, pas
     * trois lignes. Compter ses unités comme des machines donnerait un parc faux,
     * et les mettrait au même rang que le groupe dont elles dépendent.
     */
    groupes: List<GroupeMachines>,
    /** La recherche et les parcs dépliés, tenus par [ClientsViewModel]. */
    lecture: LectureCarnet,
    actionsLecture: ActionsLectureCarnet,
    onNouveauClient: () -> Unit,
    onOuvrirClient: (Client) -> Unit,
    onNouveauSite: (Client) -> Unit,
    onOuvrirMachine: (Equipement) -> Unit,
    onAjouterMachine: (String) -> Unit,
    modifier: Modifier = Modifier,
    etatListe: LazyListState = rememberLazyListState(),
    /** La sélection en cours dans un parc, `null` hors du mode sélection. */
    selection: SelectionParc? = null,
    actionsSelection: ActionsSelection = ActionsSelection(),
) {
    val parClient = groupes.groupBy { it.groupe.clientId }
    val recherche = lecture.recherche
    // Le client dont on coche le parc. Introuvable — supprimé entre-temps —, le
    // carnet reparaît plutôt qu'un écran vide qu'aucun geste ne ferait quitter.
    val clientSelection = selection?.let { courante ->
        carnet.flatMap { it.tous }.firstOrNull { it.id == courante.clientId }
    }

    fun correspondant(client: Client) =
        correspondAuCarnet(client, parClient[client.id].orEmpty(), recherche)

    // La carte d'un client telle que la recherche la montre : dépliée sur les
    // seules machines trouvées, ou comme on l'avait laissée. Voir [parcTrouve].
    @Composable
    fun CarteClient(client: Client, carteModifier: Modifier, ajoutSite: (() -> Unit)?, nombreSites: Int) {
        val machines = parClient[client.id].orEmpty()
        val trouvees = parcTrouve(client, machines, recherche)
            ?.takeUnless { client.id in lecture.parcsComplets }
        ClientCard(
            client = client,
            machines = machines,
            trouvees = trouvees,
            deplie = trouvees != null || client.id in lecture.deplies,
            onBasculerParc = { actionsLecture.onBasculerParc(client.id) },
            onVoirToutLeParc = { actionsLecture.onVoirToutLeParc(client.id) },
            onClick = { onOuvrirClient(client) },
            onOuvrirMachine = onOuvrirMachine,
            onAjouterMachine = { onAjouterMachine(client.id) },
            onNouveauSite = ajoutSite,
            nombreSites = nombreSites,
            onSelectionner = { actionsSelection.onSelectionner(client.id) },
            onSelectionnerDepuis = actionsSelection.onSelectionnerDepuis,
            modifier = carteModifier,
        )
    }

    // Un groupe est retenu si le donneur d'ordre correspond — ses sites suivent
    // alors tous — ou si l'un de ses sites correspond, auquel cas seuls ceux-là
    // paraissent, sous leur donneur d'ordre qui les situe.
    val retenus = carnet.mapNotNull { groupe ->
        when {
            correspondant(groupe.donneur) -> groupe
            else -> groupe.sites.filter(::correspondant)
                .takeIf { it.isNotEmpty() }
                ?.let { groupe.copy(sites = it) }
        }
    }

    // Seul dans son onglet, donc sans bascule : voir [VueAtelier] pour ce qui
    // a séparé le carnet des clients du magasin et des fournisseurs.
    CadreCarnet(
        titre = "Clients",
        iconeAjout = Icons.Filled.PersonAdd,
        descriptionAjout = "Ajouter un client",
        onAjouter = onNouveauClient,
        modifier = modifier,
    ) {
        if (selection != null && clientSelection != null) {
            ParcEnSelection(
                client = clientSelection,
                machines = parClient[clientSelection.id].orEmpty(),
                selection = selection,
                actions = actionsSelection,
                modifier = Modifier.fillMaxSize(),
            )
            return@CadreCarnet
        }
        Column {
            ChampRecherche(
                valeur = recherche,
                onValeur = actionsLecture.onRecherche,
                indication = "Nom, ville, machine, zone…",
                modifier = Modifier.padding(horizontal = MargeEcran),
            )
            if (carnet.isEmpty()) {
                CarnetVide(modifier = Modifier.fillMaxSize())
            } else if (retenus.isEmpty()) {
                Encart(
                    texte = "Aucun client ne correspond à « ${recherche.trim()} ».",
                    modifier = Modifier.padding(MargeEcran),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = etatListe,
                    contentPadding = PaddingValues(
                        start = MargeEcran,
                        end = MargeEcran,
                        top = 12.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Groupé par initiale : dans un carnet qui grossit, c'est
                    // le repère qui évite de faire défiler à l'aveugle.
                    // L'initiale est celle du **donneur d'ordre** : un site se
                    // cherche sous son enseigne, pas sous sa propre lettre.
                    retenus.groupBy { initiale(it.donneur.nom) }.forEach { (lettre, groupe) ->
                        item(key = "lettre-$lettre") {
                            Text(
                                text = lettre,
                                style = StyleChiffrePetit,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                            )
                        }
                        groupe.forEach { entree ->
                            item(key = entree.donneur.id) {
                                CarteClient(
                                    client = entree.donneur,
                                    carteModifier = Modifier,
                                    ajoutSite = { onNouveauSite(entree.donneur) },
                                    nombreSites = entree.nombreSites,
                                )
                            }
                            items(items = entree.sites, key = { it.id }) { site ->
                                CarteClient(
                                    client = site,
                                    // Le retrait est ce qui dit le rattachement :
                                    // une carte pleine largeur au même rang que
                                    // son enseigne se lirait comme un client de
                                    // plus.
                                    carteModifier = Modifier.padding(start = RETRAIT_SITE),
                                    ajoutSite = null,
                                    nombreSites = 0,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ce que l'écran du carnet montre de sa propre lecture : la recherche, les parcs
 * dépliés, et ceux dont on a demandé tout le parc malgré la recherche.
 *
 * Passés en un objet, comme [PlanMachine] : l'écran portait déjà treize
 * paramètres. Tout vient de [ClientsViewModel], et c'est le point — voir
 * [ClientsViewModel.recherche].
 */
data class LectureCarnet(
    val recherche: String = "",
    val deplies: Set<String> = emptySet(),
    val parcsComplets: Set<String> = emptySet(),
)

/** Les trois gestes de [LectureCarnet]. */
data class ActionsLectureCarnet(
    val onRecherche: (String) -> Unit = {},
    val onBasculerParc: (String) -> Unit = {},
    val onVoirToutLeParc: (String) -> Unit = {},
)

/** L'initiale sous laquelle ranger un nom, accents repliés. */
private fun initiale(nom: String): String {
    val premiere = nom.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return java.text.Normalizer.normalize(premiere.toString(), java.text.Normalizer.Form.NFD)
        .first()
        .toString()
}

/**
 * Fiche résumée d'un client. Le corps ouvre sa page ([EcranClient]) ; les deux icônes
 * de droite appellent et ouvrent l'itinéraire sans passer par elle — depuis le
 * carnet comme depuis la tournée, agir doit tenir en un geste.
 *
 * Le parc est replié par défaut : un carnet de cinquante clients déployés
 * deviendrait illisible, et c'est le nom du client qu'on cherche d'abord.
 */
@Composable
fun ClientCard(
    client: Client,
    machines: List<GroupeMachines>,
    onClick: () -> Unit,
    onOuvrirMachine: (Equipement) -> Unit,
    onAjouterMachine: () -> Unit,
    modifier: Modifier = Modifier,
    /** `null` sur un site : on n'attache pas un site à un site. */
    onNouveauSite: (() -> Unit)? = null,
    nombreSites: Int = 0,
    /** Entre en sélection sur ce parc : voir [ParcEnSelection]. */
    onSelectionner: () -> Unit = {},
    onSelectionnerDepuis: (Equipement) -> Unit = {},
    /** Le parc est-il déplié. L'état vit dans [ClientsViewModel.deplies]. */
    deplie: Boolean = false,
    onBasculerParc: () -> Unit = {},
    /** Les seules machines que la recherche retient, `null` sinon : voir [parcTrouve]. */
    trouvees: List<GroupeMachines>? = null,
    onVoirToutLeParc: () -> Unit = {},
) {
    val contexte = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick)
                        .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PastilleInitiales(nom = client.nom)
                    Column {
                    Text(
                        text = client.nom,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sousTitreClient(client, machines),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    }
                }
                if (client.appelable) {
                    IconButton(onClick = { contexte.appeler(client.telephone) }) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = "Appeler ${client.nom}",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (client.localisable) {
                    IconButton(onClick = { contexte.ouvrirItineraire(client.adresseComplete) }) {
                        Icon(
                            imageVector = Icons.Filled.Directions,
                            contentDescription = "Itinéraire vers ${client.nom}",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            SectionMachines(
                client = client,
                machines = machines,
                trouvees = trouvees,
                deplie = deplie,
                onBasculerParc = onBasculerParc,
                onVoirToutLeParc = onVoirToutLeParc,
                onOuvrirMachine = onOuvrirMachine,
                onAjouterMachine = onAjouterMachine,
                onSelectionner = onSelectionner,
                onSelectionnerDepuis = onSelectionnerDepuis,
            )
            if (onNouveauSite != null) {
                LigneAjoutSite(nombre = nombreSites, onClick = onNouveauSite)
            }
        }
    }
}

/**
 * L'ajout d'un site, au bas de la carte d'un donneur d'ordre.
 *
 * Le compte est dit plutôt que déduit d'un coup d'œil aux cartes en retrait :
 * elles sont filtrées par la recherche, et « 4 sites » quand on en voit un seul
 * est l'information qui manquerait autrement.
 */
@Composable
private fun LigneAjoutSite(nombre: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = LocalCibles.current.action)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.AddLocationAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = when (nombre) {
                0 -> "Ajouter un site"
                1 -> "1 site · en ajouter un"
                else -> "$nombre sites · en ajouter un"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Le retrait d'une carte de site.
 *
 * C'est lui, et rien d'autre, qui dit le rattachement : une carte pleine
 * largeur au même rang que son enseigne se lirait comme un client de plus.
 */
private val RETRAIT_SITE = 20.dp

/**
 * Le parc d'un client, dépliable depuis sa carte.
 *
 * La sélection s'y ouvre de deux façons, et c'est délibéré : par **un bouton
 * visible** en tête du parc déplié, et par l'appui long sur une machine, qui la coche du
 * même coup. Le second double le premier sans le remplacer — un appui long ne
 * s'annonce pas, et il se perd avec des gants.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SectionMachines(
    client: Client,
    machines: List<GroupeMachines>,
    trouvees: List<GroupeMachines>?,
    deplie: Boolean,
    onBasculerParc: () -> Unit,
    onVoirToutLeParc: () -> Unit,
    onOuvrirMachine: (Equipement) -> Unit,
    onAjouterMachine: () -> Unit,
    onSelectionner: () -> Unit,
    onSelectionnerDepuis: (Equipement) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Faux sur la page du client, où le parc est toujours montré : il y est la
     * raison d'être de la section, et un en-tête qui le replierait n'y
     * cacherait que ce qu'on est venu voir.
     */
    repliable: Boolean = true,
) {
    // L'état déplié vient du ViewModel, par identifiant de client : la liste
    // recycle ses cartes, et il ne doit ni sauter d'un client à l'autre au
    // défilement, ni se perdre quand une machine s'ouvre puis se referme.
    //
    // Une recherche qui désigne des machines **déplie de force** et ne montre
    // qu'elles : l'en-tête ne replie alors plus rien — replier ce qu'on vient de
    // chercher n'a pas de sens —, et « voir tout le parc » rend la main.
    val filtre = trouvees != null
    val montrees = trouvees ?: machines

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !filtre && repliable, onClick = onBasculerParc)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Kitchen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (trouvees != null) {
                    libelleTrouvees(trouvees.size, machines.size)
                } else {
                    libelleParc(machines.size)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // En tête du parc et non à son pied : sur un site de trois cents
            // machines, un bouton sous la liste serait à trois cents lignes du
            // pouce. Déplié seulement — on sélectionne ce qu'on voit —, et jamais
            // sur un parc vide, où il n'y aurait rien à cocher.
            if (deplie && machines.isNotEmpty()) {
                TextButton(
                    onClick = onSelectionner,
                    modifier = Modifier.heightIn(min = LocalCibles.current.action),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckBox,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Sélectionner")
                }
            }
            if (!filtre && repliable) {
                Icon(
                    imageVector = if (deplie) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (deplie) "Replier le parc" else "Déplier le parc",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (deplie) {
            montrees.forEach { groupe ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOuvrirMachine(groupe.groupe) },
                            onLongClick = { onSelectionnerDepuis(groupe.groupe) },
                        )
                        .padding(start = 42.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = groupe.groupe.nom,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Dit ici et non sur la fiche seule : c'est depuis le
                        // carnet qu'on décide si l'on monte avec une ou trois
                        // cartouches de filtre.
                        if (groupe.unites.isNotEmpty()) {
                            Text(
                                text = groupe.resume,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trouvees != null) {
                TextButton(
                    onClick = onVoirToutLeParc,
                    modifier = Modifier
                        .padding(start = 30.dp)
                        .heightIn(min = LocalCibles.current.action),
                ) {
                    Text(text = "Voir tout le parc (${machines.size})")
                }
            }
            TextButton(
                onClick = onAjouterMachine,
                modifier = Modifier.padding(start = 30.dp, bottom = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Ajouter une machine")
            }
        }
    }
}

/** « 1 machine trouvée sur 40 », « 3 machines trouvées sur 40 ». */
private fun libelleTrouvees(trouvees: Int, total: Int): String =
    if (trouvees == 1) "1 machine trouvée sur $total" else "$trouvees machines trouvées sur $total"

/** « Aucune machine », « 1 machine », « 3 machines ». */
private fun libelleParc(nombre: Int): String = when (nombre) {
    0 -> "Aucune machine"
    1 -> "1 machine"
    else -> "$nombre machines"
}

/**
 * Le carnet se remplissant tout seul, un carnet vide signifie surtout qu'aucune
 * intervention n'a encore été saisie : le dire évite de chercher un bouton.
 */
@Composable
private fun CarnetVide(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "Aucun client pour l'instant.\n" +
                "Le carnet se remplit à mesure que vous saisissez des interventions.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientsScreenPreview() {
    FrigoProTheme {
        Surface {
            ClientsScreen(
                carnet = listOf(
                    GroupeClients(
                        Client(
                            id = "1",
                            nom = "Boucherie Lemoine",
                            ville = "Rouen",
                            adresse = "12 rue des Carmes",
                            telephone = "02 35 00 00 00",
                        ),
                    ),
                    GroupeClients(
                        donneur = Client(id = "2", nom = "Supérette Val-Fleuri", ville = "Elbeuf"),
                        sites = listOf(
                            Client(
                                id = "2a",
                                nom = "Val-Fleuri Centre",
                                ville = "Elbeuf",
                                adresse = "8 rue Henry",
                                parentId = "2",
                            ),
                        ),
                    ),
                    GroupeClients(
                        Client(
                            id = "3",
                            nom = "Traiteur Delaunay",
                            ville = "Barentin",
                            adresse = "5 place de la Gare",
                        ),
                    ),
                ),
                groupes = listOf(
                    GroupeMachines(Equipement(id = "e1", clientId = "1", nom = "Vitrine salle 2")),
                    GroupeMachines(
                        groupe = Equipement(id = "e2", clientId = "1", nom = "Groupe Daikin bi-split"),
                        unites = listOf(
                            Equipement(id = "u1", clientId = "1", parentId = "e2", nom = "Salon"),
                            Equipement(id = "u2", clientId = "1", parentId = "e2", nom = "Chambre"),
                        ),
                    ),
                ),
                lecture = LectureCarnet(deplies = setOf("1")),
                actionsLecture = ActionsLectureCarnet(),
                onNouveauClient = {},
                onOuvrirClient = {},
                onNouveauSite = {},
                onOuvrirMachine = {},
                onAjouterMachine = {},
            )
        }
    }
}


/**
 * La pastille d'initiales d'un client.
 *
 * Elle ne remplace pas le nom, elle l'ancre : dans une liste qu'on parcourt du
 * pouce, une forme colorée se retrouve plus vite qu'une ligne de texte.
 */
@Composable
private fun PastilleInitiales(nom: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initialesDe(nom),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** « Vitry · 3 machines », ou l'adresse quand le parc est vide. */
private fun sousTitreClient(client: Client, machines: List<GroupeMachines>): String {
    val lieu = client.ville.ifBlank { client.adresseComplete }
    val parc = when (machines.size) {
        0 -> ""
        1 -> "1 machine"
        else -> "${machines.size} machines"
    }
    return listOf(lieu, parc).filter { it.isNotBlank() }.joinToString(" · ")
}
