package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.initialesDe
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.theme.FrigoProTheme
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
    modifier: Modifier = Modifier,
    viewModel: ClientsViewModel = viewModel(factory = ClientsViewModel.Factory),
    machines: EquipementsViewModel = viewModel(factory = EquipementsViewModel.Factory),
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val fiche by viewModel.fiche.collectAsStateWithLifecycle()
    val parc by machines.parc.collectAsStateWithLifecycle()
    val ouverte by machines.ouverte.collectAsStateWithLifecycle()
    val photos by machines.photosOuvertes.collectAsStateWithLifecycle()
    val historique by machines.historique.collectAsStateWithLifecycle()
    val relevesMachine by machines.relevesMachine.collectAsStateWithLifecycle()
    var ficheOuverte by remember { mutableStateOf(false) }
    val dialogue by machines.dialogue.collectAsStateWithLifecycle()
    val agrandie by machines.agrandie.collectAsStateWithLifecycle()

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

    BackHandler(enabled = ouverte != null) { machines.onFermer() }

    val machineOuverte = ouverte
    if (machineOuverte != null) {
        EcranEquipement(
            equipement = machineOuverte,
            photos = photos,
            historique = historique,
            releves = relevesMachine,
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
            onRenommer = { machines.onRenommerMachine(machineOuverte) },
            onModifierFiche = { ficheOuverte = true },
            onSupprimer = { machines.onSupprimerMachine(machineOuverte) },
            onFermer = machines::onFermer,
            modifier = modifier,
        )

        if (ficheOuverte) {
            DialogueFicheMachine(
                equipement = machineOuverte,
                onValider = {
                    machines.onEnregistrerFiche(it)
                    ficheOuverte = false
                },
                onFermer = { ficheOuverte = false },
            )
        }
    } else {
        ClientsScreen(
            clients = clients,
            parc = parc,
            onNouveauClient = viewModel::onNouveauClient,
            onOuvrirFiche = viewModel::onOuvrirFiche,
            onOuvrirMachine = machines::onOuvrir,
            onAjouterMachine = machines::onAjouterMachine,
            modifier = modifier,
        )

        fiche?.let { etat ->
            FicheClient(
                etat = etat,
                onEtatChange = viewModel::onFicheChange,
                onEnregistrer = viewModel::onEnregistrerFiche,
                onFermer = viewModel::onFermerFiche,
            )
        }
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
        is DialogueEquipement.Creation -> DialogueIntitule(
            titre = "Nouvelle machine",
            libelleAction = "Ajouter",
            libelleChamp = "Nom de la machine",
            messageConflit = MESSAGE_MACHINE_EXISTANTE,
            estDejaPris = { dejaPris(dialogue.clientId, it, null) },
            onValider = viewModel::onValiderNom,
            onFermer = viewModel::onFermerDialogue,
        )

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
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Supprimer « ${equipement.nom} » ?") },
        text = {
            Text(
                text = "Ses photos seront effacées. Les interventions déjà faites sur " +
                    "cette machine gardent son nom.",
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    clients: List<Client>,
    parc: List<Equipement>,
    onNouveauClient: () -> Unit,
    onOuvrirFiche: (Client) -> Unit,
    onOuvrirMachine: (Equipement) -> Unit,
    onAjouterMachine: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parClient = parc.groupBy { it.clientId }
    var recherche by rememberSaveable { mutableStateOf("") }
    val retenus = clients.filter { correspond(it, parClient[it.id].orEmpty(), recherche) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas ;
        // l'y ajouter ici la compterait deux fois.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNouveauClient,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = "Ajouter un client",
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = "Clients",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = MargeEcran, vertical = 12.dp),
            )
            ChampRecherche(
                valeur = recherche,
                onValeur = { recherche = it },
                indication = "Nom, ville, machine…",
                modifier = Modifier.padding(horizontal = MargeEcran),
            )
            if (clients.isEmpty()) {
                CarnetVide(modifier = Modifier.fillMaxSize())
            } else if (retenus.isEmpty()) {
                Encart(
                    texte = "Aucun client ne correspond à « ${recherche.trim()} ».",
                    modifier = Modifier.padding(MargeEcran),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                    retenus.groupBy { initiale(it.nom) }.forEach { (lettre, groupe) ->
                        item(key = "lettre-$lettre") {
                            Text(
                                text = lettre,
                                style = StyleChiffrePetit,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                            )
                        }
                        items(items = groupe, key = { it.id }) { client ->
                            ClientCard(
                                client = client,
                                machines = parClient[client.id].orEmpty(),
                                onClick = { onOuvrirFiche(client) },
                                onOuvrirMachine = onOuvrirMachine,
                                onAjouterMachine = { onAjouterMachine(client.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * La recherche du carnet.
 *
 * Elle porte aussi sur les machines : un technicien se souvient souvent de
 * « la vitrine Costan » sans retrouver le nom du commerce.
 */
private fun correspond(client: Client, machines: List<Equipement>, recherche: String): Boolean {
    val cherche = recherche.trim().lowercase()
    if (cherche.isEmpty()) return true
    return client.nom.lowercase().contains(cherche) ||
        client.ville.lowercase().contains(cherche) ||
        machines.any { machine ->
            machine.nom.lowercase().contains(cherche) ||
                machine.designation.lowercase().contains(cherche)
        }
}

/** L'initiale sous laquelle ranger un nom, accents repliés. */
private fun initiale(nom: String): String {
    val premiere = nom.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return java.text.Normalizer.normalize(premiere.toString(), java.text.Normalizer.Form.NFD)
        .first()
        .toString()
}

/**
 * Fiche résumée d'un client. Le corps ouvre la fiche complète ; les deux icônes
 * de droite appellent et ouvrent l'itinéraire sans passer par elle — depuis le
 * carnet comme depuis la tournée, agir doit tenir en un geste.
 *
 * Le parc est replié par défaut : un carnet de cinquante clients déployés
 * deviendrait illisible, et c'est le nom du client qu'on cherche d'abord.
 */
@Composable
fun ClientCard(
    client: Client,
    machines: List<Equipement>,
    onClick: () -> Unit,
    onOuvrirMachine: (Equipement) -> Unit,
    onAjouterMachine: () -> Unit,
    modifier: Modifier = Modifier,
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
                onOuvrirMachine = onOuvrirMachine,
                onAjouterMachine = onAjouterMachine,
            )
        }
    }
}

/** Le parc d'un client, dépliable depuis sa carte. */
@Composable
private fun SectionMachines(
    client: Client,
    machines: List<Equipement>,
    onOuvrirMachine: (Equipement) -> Unit,
    onAjouterMachine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Clé sur l'identifiant du client : la liste recycle ses cartes, et sans
    // cela l'état déplié sauterait d'un client à l'autre au défilement.
    var deplie by rememberSaveable(client.id) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { deplie = !deplie }
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
                text = libelleParc(machines.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (deplie) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (deplie) "Replier le parc" else "Déplier le parc",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (deplie) {
            machines.forEach { machine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOuvrirMachine(machine) }
                        .padding(start = 42.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = machine.nom,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                clients = listOf(
                    Client(
                        id = "1",
                        nom = "Boucherie Lemoine",
                        ville = "Rouen",
                        adresse = "12 rue des Carmes",
                        telephone = "02 35 00 00 00",
                    ),
                    Client(id = "2", nom = "Supérette Val-Fleuri", ville = "Elbeuf"),
                    Client(
                        id = "3",
                        nom = "Traiteur Delaunay",
                        ville = "Barentin",
                        adresse = "5 place de la Gare",
                    ),
                ),
                parc = listOf(
                    Equipement(id = "e1", clientId = "1", nom = "Vitrine salle 2"),
                    Equipement(id = "e2", clientId = "1", nom = "Chambre froide positive"),
                ),
                onNouveauClient = {},
                onOuvrirFiche = {},
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
private fun sousTitreClient(client: Client, machines: List<Equipement>): String {
    val lieu = client.ville.ifBlank { client.adresseComplete }
    val parc = when (machines.size) {
        0 -> ""
        1 -> "1 machine"
        else -> "${machines.size} machines"
    }
    return listOf(lieu, parc).filter { it.isNotBlank() }.joinToString(" · ")
}
