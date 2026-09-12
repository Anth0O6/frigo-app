package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.CategoriePrestation
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisChiffre
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.LigneDevis
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.StatutDevis
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffre
import com.frigopro.app.ui.theme.StyleChiffrePetit

/** L'onglet Devis, avec son état. */
@Composable
fun DevisRoute(
    modifier: Modifier = Modifier,
    viewModel: DevisViewModel = viewModel(factory = DevisViewModel.Factory),
) {
    val liste by viewModel.liste.collectAsStateWithLifecycle()
    val carnet by viewModel.carnet.collectAsStateWithLifecycle()
    val complet by viewModel.complet.collectAsStateWithLifecycle()
    val compteurs by viewModel.compteurs.collectAsStateWithLifecycle()
    val catalogue by viewModel.catalogue.collectAsStateWithLifecycle()
    val unitesVisees by viewModel.unitesVisees.collectAsStateWithLifecycle()

    BackHandler(enabled = complet != null) { viewModel.onFermer() }

    val ouvert = complet
    if (ouvert != null) {
        EcranDevis(
            devis = ouvert,
            clients = carnet,
            onObjet = viewModel::onObjet,
            onClient = viewModel::onClient,
            onStatut = viewModel::onStatut,
            onAjouterLigne = viewModel::onAjouterLigne,
            onAjouterPrestation = viewModel::onAjouterPrestation,
            onCreerPrestation = viewModel::onCreerPrestation,
            catalogue = catalogue,
            unitesVisees = unitesVisees,
            onOffrirLigne = viewModel::onOffrirLigne,
            onOffrirTva = viewModel::onOffrirTva,
            onSupprimerLigne = viewModel::onSupprimerLigne,
            onSupprimer = viewModel::onSupprimer,
            onFermer = viewModel::onFermer,
            modifier = modifier,
        )
    } else {
        ListeDevis(
            devis = liste,
            compteurs = compteurs,
            onOuvrir = viewModel::onOuvrir,
            onNouveau = { viewModel.onNouveau(null) },
            modifier = modifier,
        )
    }
}

/** La liste des devis, du plus récent au plus ancien. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeDevis(
    devis: List<DevisChiffre>,
    compteurs: CompteursDevis,
    onOuvrir: (Devis) -> Unit,
    onNouveau: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = "Devis",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = MargeEcran, vertical = 12.dp),
            )
            CompteursEnTete(compteurs = compteurs)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = MargeEcran,
                    end = MargeEcran,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (devis.isEmpty()) {
                    item {
                        Encart(
                            texte = "Aucun devis. Un devis chiffré sur place et envoyé avant " +
                                "d'être redescendu du toit est accepté le jour même plutôt que " +
                                "la semaine suivante.",
                        )
                    }
                }
                items(items = devis, key = { it.devis.id }) { document ->
                    LigneDevisListe(
                        chiffre = document,
                        onClick = { onOuvrir(document.devis) },
                    )
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
private fun LigneDevisListe(chiffre: DevisChiffre, onClick: () -> Unit) {
    val devis = chiffre.devis
    Carte(onClick = onClick) {
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
                PuceStatut(devis.statut)
            }
        }
    }
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
    onObjet: (String) -> Unit,
    onClient: (com.frigopro.app.data.Client) -> Unit,
    onStatut: (StatutDevis) -> Unit,
    onAjouterLigne: (String, Double, String, Double) -> Unit,
    onAjouterPrestation: (Prestation) -> Unit,
    onCreerPrestation: (Prestation) -> Unit,
    catalogue: Map<CategoriePrestation, List<Prestation>>,
    /** Le nombre d'unités intérieures de la machine visée : 1 pour un monosplit. */
    unitesVisees: Int,
    onOffrirLigne: (LigneDevis) -> Unit,
    onOffrirTva: () -> Unit,
    onSupprimerLigne: (String) -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var saisieOuverte by remember { mutableStateOf(false) }
    var clientOuvert by remember { mutableStateOf(false) }
    var catalogueOuvert by remember { mutableStateOf(false) }

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
            ChampTexte(libelle = "Objet", valeur = devis.devis.objet, onValeur = onObjet)

            Carte(onClick = { clientOuvert = true }) {
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
                    Text(text = "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            devis.lignes.forEach { ligne ->
                LigneDuDevis(
                    ligne = ligne,
                    onOffrir = { onOffrirLigne(ligne) },
                    onSupprimer = { onSupprimerLigne(ligne.id) },
                )
            }
            if (devis.lignes.isNotEmpty()) {
                Text(
                    text = "Appui sur une ligne : l'offrir ou reprendre le geste. " +
                        "Appui long : la supprimer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Le catalogue d'abord, la saisie libre ensuite : c'est l'ordre des
            // fréquences. Un devis se construit à 90 % de prestations connues,
            // et taper un intitulé gants aux mains sur un capot de camionnette
            // est ce qu'on veut avoir à faire le moins souvent possible.
            BoutonPlein(
                texte = "Ajouter depuis le catalogue",
                onClick = { catalogueOuvert = true },
                modifier = Modifier.fillMaxWidth(),
            )
            BoutonContour(
                texte = "+ Ligne libre",
                onClick = { saisieOuverte = true },
                modifier = Modifier.fillMaxWidth(),
                couleur = MaterialTheme.colorScheme.secondary,
            )

            CarteTotaux(devis = devis, onOffrirTva = onOffrirTva)

            RangeePastilles(
                options = StatutDevis.entries,
                retenue = devis.devis.statut,
                libelle = { it.libelle },
                onChoisir = onStatut,
                modifier = Modifier.fillMaxWidth(),
            )

            BoutonContour(
                texte = "Supprimer ce devis",
                onClick = onSupprimer,
                modifier = Modifier.fillMaxWidth(),
                couleur = MaterialTheme.colorScheme.error,
            )
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
        )
    }

    if (catalogueOuvert) {
        FeuilleCatalogue(
            catalogue = catalogue,
            dejaAuDevis = devis.lignes.map { it.designation }.toSet(),
            onChoisir = onAjouterPrestation,
            onCreer = onCreerPrestation,
            unitesVisees = unitesVisees,
            onFermer = { catalogueOuvert = false },
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
}

/**
 * Une ligne du devis.
 *
 * **Appui simple : offrir ou reprendre. Appui long : supprimer.** C'était
 * l'inverse — l'appui simple supprimait — et un contact involontaire faisait
 * disparaître une ligne sans un mot. Gants aux mains, sur un capot, c'est le
 * geste le plus facile à faire par erreur ; il porte donc l'action fréquente, et
 * la destruction demande une intention.
 *
 * Une ligne offerte garde son prix, barré, et annonce « offert ». Le client doit
 * lire ce qu'on lui a donné : une remise invisible n'est pas un argument.
 */
@Composable
private fun LigneDuDevis(
    ligne: LigneDevis,
    onOffrir: () -> Unit,
    onSupprimer: () -> Unit,
) {
    val vert = LocalStatuts.current.termine
    Carte(contour = true, onClick = onOffrir, onLongClick = onSupprimer) {
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
            } else {
                Text(text = Nombres.enEuros(ligne.montant), style = StyleChiffrePetit)
            }
        }
    }
}

@Composable
private fun CarteTotaux(devis: DevisComplet, onOffrirTva: () -> Unit) {
    val statuts = LocalStatuts.current
    Carte(relief = true) {
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
                text = "TVA non applicable, art. 293 B du CGI",
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

        // Ce que le geste a coûté, pour le technicien et non pour le client :
        // c'est le chiffre qui se regarde avant d'envoyer, pas après.
        if (devis.gestesCommerciaux > 0) {
            Text(
                text = "Gestes commerciaux : ${Nombres.enEuros(devis.gestesCommerciaux)}",
                style = MaterialTheme.typography.bodySmall,
                color = statuts.termine,
            )
        }

        // Le geste n'est proposé qu'à une entreprise assujettie : en franchise en
        // base il n'y a pas de TVA à offrir, et un bouton qui n'agirait sur rien
        // ferait croire à une remise accordée.
        if (devis.devis.assujettiTva) {
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

@Composable
private fun DialogueLigneDevis(
    onValider: (String, Double, String, Double) -> Unit,
    onFermer: () -> Unit,
) {
    var designation by remember { mutableStateOf("") }
    var quantite by remember { mutableStateOf("1") }
    var unite by remember { mutableStateOf("") }
    var prix by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Ligne de devis") },
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
                ChampTexte(
                    libelle = "Prix unitaire HT",
                    valeur = prix,
                    onValeur = { prix = it },
                    clavier = KeyboardType.Decimal,
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
                        Nombres.versDecimal(prix) ?: 0.0,
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
private fun FeuilleCatalogue(
    catalogue: Map<CategoriePrestation, List<Prestation>>,
    dejaAuDevis: Set<String>,
    onChoisir: (Prestation) -> Unit,
    onCreer: (Prestation) -> Unit,
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
            BoutonContour(
                texte = "+ Nouvelle prestation au catalogue",
                onClick = { creationOuverte = true },
                modifier = Modifier.fillMaxWidth(),
                couleur = MaterialTheme.colorScheme.secondary,
            )
            EspaceVertical(16)
        }
    }

    if (creationOuverte) {
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
