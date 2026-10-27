package com.frigopro.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Avoirs
import com.frigopro.app.data.CategoriePrestation
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.FactureComplete
import com.frigopro.app.data.LigneFacture
import com.frigopro.app.data.PenalitesDues
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.Prestation
import com.frigopro.app.data.RappelsFactures
import com.frigopro.app.data.StatutFacture
import com.frigopro.app.ui.composants.BarreActions
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampChiffre
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.LocalStatuts
import java.time.LocalDate

/**
 * Les deux faces de l'onglet : ce qu'on réclame, et ce qu'on propose.
 *
 * **Les factures d'abord**, et c'est une correction : l'onglet s'intitulait
 * « Factures » et s'ouvrait sur les devis, si bien que le premier écran vu sous
 * ce nom en montrait un autre. Ce sont les factures qu'on vient chercher le plus
 * souvent ici — une relance, un paiement à pointer —, le devis se créant le plus
 * souvent depuis une intervention ou un client.
 */
enum class VueFacturation(val libelle: String) {
    FACTURES("Factures"),
    DEVIS("Devis"),
}

/**
 * L'onglet « Factures », qui porte les deux documents commerciaux.
 *
 * Une bascule plutôt qu'un onglet de plus, et ce n'est pas un pis-aller : c'est
 * le motif que partagent les trois onglets à plusieurs vues, et un onglet
 * supplémentaire aurait réduit chaque cible à ce qu'un pouce ganté ne trouve
 * plus. Les deux documents se suivent d'ailleurs dans le temps — on chiffre, puis
 * on facture — et les mettre à deux endroits éloignés aurait séparé les deux
 * moitiés d'un même geste.
 *
 * La vue est tenue par la **coquille** et non ici : posée dans un
 * `rememberSaveable` de cette route, elle se perdait à chaque changement
 * d'onglet, et l'on revenait sur la vue par défaut après être allé voir une
 * intervention. C'est le motif de la vue de l'Atelier et de la page des Réglages.
 *
 * Ouvrir une facture depuis ailleurs — « Facturer » sur une intervention ou sur
 * un devis, une relance de l'accueil — bascule la vue tout seul : le ViewModel
 * des factures est partagé, et une facture ouverte est l'unique raison qu'a cet
 * onglet de montrer les factures plutôt que ce qu'on regardait.
 */
@Composable
fun FacturationRoute(
    vue: VueFacturation,
    onVue: (VueFacturation) -> Unit,
    modifier: Modifier = Modifier,
    /** Un devis accepté se planifie, et la journée est un autre onglet. */
    onAllerALaTournee: () -> Unit = {},
    facturesViewModel: FacturesViewModel = viewModel(factory = FacturesViewModel.Factory),
) {
    val factureOuverte by facturesViewModel.ouverte.collectAsStateWithLifecycle()

    LaunchedEffect(factureOuverte) {
        if (factureOuverte != null) onVue(VueFacturation.FACTURES)
    }

    when (vue) {
        VueFacturation.DEVIS -> DevisRoute(
            modifier = modifier,
            onVoirFactures = { onVue(VueFacturation.FACTURES) },
            onAllerALaTournee = onAllerALaTournee,
        )

        VueFacturation.FACTURES -> FacturesRoute(
            modifier = modifier,
            onVoirDevis = { onVue(VueFacturation.DEVIS) },
            viewModel = facturesViewModel,
        )
    }
}

@Composable
fun FacturesRoute(
    modifier: Modifier = Modifier,
    onVoirDevis: () -> Unit = {},
    viewModel: FacturesViewModel = viewModel(factory = FacturesViewModel.Factory),
) {
    val liste by viewModel.liste.collectAsStateWithLifecycle()
    val complete by viewModel.complete.collectAsStateWithLifecycle()
    val compteurs by viewModel.compteurs.collectAsStateWithLifecycle()
    val documentPret by viewModel.documentPret.collectAsStateWithLifecycle()
    val echecExport by viewModel.echecExport.collectAsStateWithLifecycle()
    val solde by viewModel.solde.collectAsStateWithLifecycle()
    val avoirRefuse by viewModel.avoirRefuse.collectAsStateWithLifecycle()
    val catalogue by viewModel.catalogue.collectAsStateWithLifecycle()
    val manques by viewModel.manques.collectAsStateWithLifecycle()
    val benefices by viewModel.benefices.collectAsStateWithLifecycle()
    val baseBenefice by viewModel.baseBenefice.collectAsStateWithLifecycle()
    val contexte = LocalContext.current

    // La permission de notifier n'est demandée qu'ici, et jamais au démarrage :
    // une permission réclamée avant d'avoir montré à quoi elle sert se refuse.
    // Et l'application marche sans — les impayés paraissent de toute façon sur
    // l'accueil ; la notification ne fait que les porter au-dehors.
    var rappelsActifs by remember { mutableStateOf(RappelsFactures.notificationsAutorisees(contexte)) }
    val demandeRappels = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { accordee -> rappelsActifs = accordee }

    // Le partage s'ouvre dès que le PDF est écrit, puis le ViewModel oublie le
    // document : sans cet oubli, revenir sur l'onglet rouvrirait le sélecteur.
    LaunchedEffect(documentPret) {
        val fichier = documentPret ?: return@LaunchedEffect
        val ouverte = complete?.facture
        // Le retard est celui du solde : une facture que ses avoirs couvrent ne
        // se relance plus, même si on la renvoie.
        val chiffree = solde?.takeIf { it.chiffree.facture.id == ouverte?.id }?.chiffree
        val enRetard = chiffree?.enRetard(LocalDate.now()) == true
        contexte.envoyerDocument(
            document = fichier,
            objet = listOf(
                when {
                    ouverte?.estAvoir == true -> "Avoir"
                    enRetard -> "Relance — facture"
                    else -> "Facture"
                },
                ouverte?.numero.orEmpty(),
            )
                .filter { it.isNotBlank() }
                .joinToString(" "),
            corps = if (enRetard && chiffree != null) {
                corpsDeLaRelance(chiffree)
            } else {
                corpsDeLaFacture(ouverte)
            },
        )
        viewModel.onDocumentPartage()
        // Envoyer une facture en retard, c'est la relancer : le noter ici évite
        // qu'elle remonte le lendemain dans la liste des relances à faire.
        if (ouverte != null && enRetard) viewModel.onRelancee(ouverte)
    }

    if (avoirRefuse) {
        AlertDialog(
            onDismissRequest = viewModel::onRefusVu,
            title = { Text(text = "Avoir refusé") },
            text = {
                Text(
                    text = "L'avoir dépasserait ce qu'il reste à créditer sur la facture, " +
                        "ou la facture ne se crédite plus — annulée, ou déjà couverte par " +
                        "un autre avoir. Aucun numéro n'a été consommé.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::onRefusVu) { Text(text = "Fermer") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
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

    // Le brouillon dont on vient de demander la suppression, en attente de
    // confirmation. Seule la liste passe par là : la facture ouverte a déjà sa
    // propre confirmation, qui sert aussi à émettre et à annuler.
    var aSupprimer by remember { mutableStateOf<Facture?>(null) }

    BackHandler(enabled = complete != null) { viewModel.onFermer() }

    val ouverte = complete
    if (ouverte != null) {
        EcranFacture(
            facture = ouverte,
            // Le solde n'est retenu que s'il est bien celui de la facture
            // affichée : entre deux ouvertures, l'un des deux flux peut avoir
            // une émission d'avance sur l'autre.
            solde = solde?.takeIf { it.chiffree.facture.id == ouverte.facture.id },
            onFaireAvoir = viewModel::onFaireAvoir,
            onOuvrir = viewModel::onOuvrir,
            catalogue = catalogue,
            manques = manques,
            onObjet = viewModel::onObjet,
            onAjouterLigne = viewModel::onAjouterLigne,
            onAjouterPrestation = viewModel::onAjouterPrestation,
            onEmettre = viewModel::onEmettre,
            onEnvoyer = viewModel::onExporterPdf,
            onPayee = { le -> viewModel.onPayee(le) },
            onImpayee = viewModel::onImpayee,
            onAnnuler = viewModel::onAnnuler,
            onSupprimer = { viewModel.onSupprimer(ouverte.facture) },
            onSupprimerLigne = viewModel::onSupprimerLigne,
            onFermer = viewModel::onFermer,
            modifier = modifier,
        )
    } else {
        ListeFactures(
            factures = liste,
            compteurs = compteurs,
            onOuvrir = viewModel::onOuvrir,
            onSupprimer = { aSupprimer = it },
            onVoirDevis = onVoirDevis,
            rappelsActifs = rappelsActifs,
            benefice = {
                CarteBenefice(
                    benefices = benefices,
                    base = baseBenefice,
                    onBase = viewModel::onBaseBenefice,
                )
            },
            onAutoriserRappels = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    demandeRappels.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            modifier = modifier,
        )
    }

    aSupprimer?.let { condamne ->
        ConfirmationSuppressionFacture(
            facture = condamne,
            onConfirmer = {
                viewModel.onSupprimer(condamne)
                aSupprimer = null
            },
            onFermer = { aSupprimer = null },
        )
    }
}

/**
 * La confirmation de suppression d'un brouillon, depuis la liste.
 *
 * Elle ne s'ouvre que sur un brouillon : une facture numérotée ne se supprime
 * pas, elle s'annule en gardant son numéro, et cela se fait depuis la facture
 * elle-même — un trou dans la séquence est ce qu'un contrôle cherche en premier.
 */
@Composable
private fun ConfirmationSuppressionFacture(
    facture: Facture,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Supprimer ce brouillon ?") },
        text = {
            Text(
                text = buildString {
                    append(facture.objet.ifBlank { "Ce brouillon" })
                    if (facture.clientNom.isNotBlank()) append(" — ${facture.clientNom}")
                    append(" disparaît avec ses lignes. C'est définitif.")
                    append(" Il n'a pris aucun numéro : la séquence des factures")
                    append(" n'en gardera pas de trou.")
                },
            )
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

/** La liste des factures, de la plus récente à la plus ancienne. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeFactures(
    factures: List<FactureChiffree>,
    compteurs: CompteursFactures,
    onOuvrir: (Facture) -> Unit,
    /** L'appui long sur un brouillon. Sans effet sur une facture numérotée. */
    onSupprimer: (Facture) -> Unit = {},
    onVoirDevis: () -> Unit,
    /** Le rappel du matin est autorisé : voir [RappelsFactures]. */
    rappelsActifs: Boolean = true,
    onAutoriserRappels: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** La carte du bénéfice, posée par la route : elle tient ses propres flux. */
    benefice: @Composable () -> Unit = {},
) {
    val statuts = LocalStatuts.current
    val aujourdhui = remember { LocalDate.now() }

    // La recherche ne survit pas à la sortie de l'onglet : on cherche une
    // facture, on l'ouvre, et on ne veut pas retrouver la liste filtrée ensuite.
    var recherche by rememberSaveable { mutableStateOf("") }
    val trouvees = factures.filter { it.correspondA(recherche) }
    val cherche = recherche.isNotBlank()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            Text(
                // Le titre nomme la vue ouverte et non l'onglet, comme celui
                // de l'Atelier : « Facturation » au-dessus d'une bascule
                // « Factures · Devis » répétait le nom du groupe et laissait
                // l'écran sans dire lequel des deux on regarde.
                text = VueFacturation.FACTURES.libelle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = MargeEcran, vertical = 12.dp),
            )
            RangeePastilles(
                options = VueFacturation.entries,
                retenue = VueFacturation.FACTURES,
                libelle = { it.libelle },
                onChoisir = { if (it == VueFacturation.DEVIS) onVoirDevis() },
                modifier = Modifier.padding(horizontal = MargeEcran),
            )
            ChampRecherche(
                valeur = recherche,
                onValeur = { recherche = it },
                indication = "Client, objet, numéro…",
                modifier = Modifier
                    .padding(horizontal = MargeEcran)
                    .padding(top = 14.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargeEcran)
                    .padding(top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TuileChiffre(
                    valeur = "${compteurs.enAttente}",
                    libelle = "en attente",
                    modifier = Modifier.weight(1f),
                    couleur = statuts.aValider,
                )
                TuileChiffre(
                    valeur = "${compteurs.enRetard}",
                    libelle = "en retard",
                    modifier = Modifier.weight(1f),
                    couleur = statuts.urgence,
                )
                TuileChiffre(
                    valeur = Nombres.enEurosCourt(compteurs.montantEnAttente),
                    libelle = "à encaisser",
                    modifier = Modifier.weight(1f),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = MargeEcran,
                    end = MargeEcran,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Proposé seulement quand il y a de quoi rappeler : demander
                // l'autorisation de notifier des factures à quelqu'un qui n'en a
                // aucune, c'est se la faire refuser une fois pour toutes.
                if (!rappelsActifs && compteurs.enAttente > 0) {
                    item {
                        Encart(
                            texte = "Être prévenu le matin quand une facture dépasse son " +
                                "échéance. Les impayés resteront de toute façon sur l'accueil.",
                            complement = "Autoriser les rappels",
                            onClick = onAutoriserRappels,
                        )
                    }
                }
                // Le bénéfice en tête, sauf pendant une recherche : on cherche alors
                // une facture, et la carte repousserait les résultats sous le pouce.
                if (!cherche && factures.isNotEmpty()) {
                    item(key = "benefice") { benefice() }
                }
                if (cherche && trouvees.isEmpty()) {
                    // « Rien ne correspond » et « aucune facture » n'appellent
                    // pas le même geste : l'un fait effacer un mot, l'autre fait
                    // aller chercher une intervention terminée.
                    item {
                        Encart(texte = "Aucune facture ne correspond à « ${recherche.trim()} ».")
                    }
                } else if (factures.isEmpty()) {
                    item {
                        Encart(
                            texte = "Aucune facture. Une intervention terminée se facture " +
                                "depuis sa fiche, et un devis accepté depuis le devis : les " +
                                "lignes se construisent alors toutes seules.",
                        )
                    }
                }
                items(trouvees, key = { it.facture.id }) { chiffree ->
                    CarteFacture(
                        chiffree = chiffree,
                        aujourdhui = aujourdhui,
                        onClick = { onOuvrir(chiffree.facture) },
                        onSupprimer = { onSupprimer(chiffree.facture) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CarteFacture(
    chiffree: FactureChiffree,
    aujourdhui: LocalDate,
    onClick: () -> Unit,
    onSupprimer: (() -> Unit)? = null,
) {
    val facture = chiffree.facture
    // Le retard est celui du solde : une facture que ses avoirs couvrent n'est
    // plus en retard de rien.
    val retard = facture.joursDeRetard(aujourdhui)?.takeIf { chiffree.enRetard(aujourdhui) }

    Carte(
        onClick = onClick,
        // L'appui long n'existe que sur un brouillon : une facture numérotée ne
        // se supprime pas, et un geste qui ne ferait rien vaut moins que pas de
        // geste. La convention reste celle du projet — appui simple pour ouvrir,
        // appui long pour détruire.
        onLongClick = onSupprimer.takeIf { !facture.numerotee },
        liseré = couleurStatutFacture(chiffree, aujourdhui),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = facture.clientNom.ifBlank { "Client non renseigné" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = listOf(facture.numero, facture.objet)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Un avoir se lit au milieu des factures, et c'est là que son sens
            // doit se voir : précédé d'un « − », comme la remise de TVA offerte.
            // Le document, lui, porte un montant positif sous le titre « Avoir ».
            Text(
                text = if (facture.estAvoir) {
                    "− ${Nombres.enEuros(chiffree.totalTtc)}"
                } else {
                    Nombres.enEuros(chiffree.totalTtc)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (facture.estAvoir) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (facture.estAvoir) {
                Puce(texte = facture.nature.libelle, couleur = MaterialTheme.colorScheme.secondary)
            }
            Puce(
                texte = facture.statut.libelle,
                couleur = couleurStatutFacture(chiffree, aujourdhui),
            )
            if (facture.estAvoir && facture.factureOrigineNumero.isNotBlank()) {
                Puce(texte = "Sur ${facture.factureOrigineNumero}")
            } else if (chiffree.couverte) {
                Puce(texte = "Couverte par avoir", couleur = MaterialTheme.colorScheme.secondary)
            } else if (chiffree.creditee) {
                Puce(texte = "Avoir − ${Nombres.enEuros(chiffree.avoirsTtc)}")
            }
            if (retard != null) {
                Puce(
                    texte = "En retard de $retard j",
                    couleur = LocalStatuts.current.urgence,
                )
            } else if (facture.echeanceLe != null && chiffree.attendPaiement) {
                Puce(texte = "Échéance ${jourCourt(facture.echeanceLe!!)}")
            }
        }
    }
}

/**
 * La couleur d'une facture.
 *
 * Le retard l'emporte sur le statut, et c'est voulu : « émise » et « émise depuis
 * quarante jours » ne demandent pas le même geste. Une facture que ses avoirs
 * couvrent prend la couleur du fait accompli, comme une facture payée : il n'y
 * a plus rien à en attendre.
 */
@Composable
private fun couleurStatutFacture(chiffree: FactureChiffree, aujourdhui: LocalDate) =
    when {
        chiffree.enRetard(aujourdhui) -> LocalStatuts.current.urgence
        chiffree.couverte -> LocalStatuts.current.termine
        chiffree.facture.estAvoir && chiffree.facture.statut == StatutFacture.EMISE ->
            MaterialTheme.colorScheme.secondary
        chiffree.facture.statut == StatutFacture.PAYEE -> LocalStatuts.current.termine
        chiffree.facture.statut == StatutFacture.ANNULEE -> MaterialTheme.colorScheme.onSurfaceVariant
        chiffree.facture.statut == StatutFacture.EMISE -> LocalStatuts.current.aValider
        else -> LocalStatuts.current.planifie
    }

/**
 * Une facture ouverte.
 *
 * Les actions du bas changent avec le statut, et c'est le cœur de l'écran : un
 * brouillon s'émet, une facture émise s'envoie et s'encaisse, une facture payée
 * ne se réclame plus. Proposer les quatre en permanence aurait mis « Émettre » à
 * côté d'une facture déjà partie.
 *
 * Un avoir s'ouvre ici aussi, et la même règle le sert : un brouillon d'avoir
 * s'émet ou se supprime, un avoir émis s'envoie ou s'annule — il ne se « paie »
 * pas. « Faire un avoir » n'est pas dans la barre du bas, qui en porte déjà
 * trois sur une facture émise : il est dans la section des avoirs, à côté de ce
 * qui a déjà été crédité, qui est ce qu'on veut lire avant de créditer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranFacture(
    facture: FactureComplete,
    /** La facture imputée de ses avoirs ; `null` le temps du premier calcul. */
    solde: SoldeFacture?,
    onFaireAvoir: (Map<String, Double>?) -> Unit,
    /** Ouvre un autre document : la facture d'un avoir, ou l'avoir d'une facture. */
    onOuvrir: (String) -> Unit,
    /** Le catalogue, d'où se complète un brouillon. */
    catalogue: Map<CategoriePrestation, List<Prestation>> = emptyMap(),
    /**
     * Ce qui manque à l'en-tête de l'entreprise : la confirmation d'émission le
     * nomme avant le geste irréversible — voir [manquesEmetteur].
     */
    manques: List<String> = emptyList(),
    onObjet: (String) -> Unit = {},
    onAjouterLigne: (String, Double, String, Double) -> Unit = { _, _, _, _ -> },
    onAjouterPrestation: (Prestation) -> Unit = {},
    onEmettre: () -> Unit,
    onEnvoyer: () -> Unit,
    /** Payée le jour donné : aujourd'hui par défaut, à confirmer dans la boîte. */
    onPayee: (LocalDate) -> Unit,
    onImpayee: () -> Unit,
    onAnnuler: () -> Unit,
    onSupprimer: () -> Unit,
    onSupprimerLigne: (LigneFacture) -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = facture.facture
    val aujourdhui = remember { LocalDate.now() }
    var confirmation by remember { mutableStateOf<ConfirmationFacture?>(null) }
    var saisieAvoir by remember { mutableStateOf(false) }
    var catalogueOuvert by remember { mutableStateOf(false) }
    var saisieLigne by remember { mutableStateOf(false) }
    var paiementOuvert by remember { mutableStateOf(false) }
    val brouillon = !document.statut.figee
    // Le retard du solde, et non de la seule échéance : une facture couverte
    // par ses avoirs ne se relance plus.
    val enRetard = solde?.chiffree?.enRetard(aujourdhui) == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ActionsFacture(
                facture = document,
                enRetard = enRetard,
                // Une facture créditée ne s'annule plus : voir `FactureRepository.annuler`.
                annulable = solde?.chiffree?.creditee != true,
                onEmettre = { confirmation = ConfirmationFacture.EMETTRE },
                onEnvoyer = onEnvoyer,
                onPayee = { paiementOuvert = true },
                onImpayee = onImpayee,
                onAnnuler = { confirmation = ConfirmationFacture.ANNULER },
                onSupprimer = { confirmation = ConfirmationFacture.SUPPRIMER },
            )
        },
    ) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.numero.ifBlank {
                            if (document.estAvoir) "Brouillon d'avoir" else "Brouillon"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = document.clientNom.ifBlank { "Client non renseigné" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BoutonContour(texte = "Fermer", onClick = onFermer)
            }

            // Les pénalités courent sur ce qui reste dû, et plus du tout sur une
            // facture que ses avoirs couvrent : voir [PenalitesDues.de].
            val chiffree = solde?.chiffree
            if (chiffree != null && chiffree.enRetard(aujourdhui)) {
                PenalitesDues.de(document, chiffree.resteTtc, aujourdhui)?.let { dues ->
                    SectionPenalites(dues)
                }
            }

            Section(intitule = "Document") {
                if (document.estAvoir) {
                    LigneInfoFacture("Nature", document.nature.libelle)
                    LigneOrigineAvoir(
                        avoir = document,
                        onOuvrir = document.factureOrigineId?.let { id -> { onOuvrir(id) } },
                    )
                }
                LigneInfoFacture("Statut", document.statut.libelle)
                // L'objet se retouche tant que la facture est un brouillon, et
                // seulement alors : passé l'émission, plus rien ne bouge.
                if (brouillon) {
                    ChampTexte(libelle = "Objet", valeur = document.objet, onValeur = onObjet)
                } else if (document.objet.isNotBlank()) {
                    LigneInfoFacture("Objet", document.objet)
                }
                document.emiseLe?.let {
                    LigneInfoFacture(if (document.estAvoir) "Émis le" else "Émise le", jourCourt(it))
                }
                document.echeanceLe?.let { LigneInfoFacture("Échéance", jourCourt(it)) }
                document.payeeLe?.let { LigneInfoFacture("Payée le", jourCourt(it)) }
                document.relanceeLe?.let { LigneInfoFacture("Relancée le", jourCourt(it)) }
            }

            Section(intitule = "Lignes") {
                if (facture.lignes.isEmpty()) {
                    Text(
                        text = "Aucune ligne.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                facture.lignes.forEach { ligne ->
                    LigneFactureAffichee(
                        ligne = ligne,
                        modifiable = brouillon,
                        onSupprimer = { onSupprimerLigne(ligne) },
                    )
                }
                // Un brouillon se complète comme un devis — le catalogue, puis la
                // ligne libre. Un brouillon d'avoir non : il ne crédite que ce qui
                // a été facturé, et une ligne de plus n'y aurait pas de sens.
                if (brouillon && !document.estAvoir) {
                    BoutonContour(
                        texte = "Ajouter depuis le catalogue",
                        onClick = { catalogueOuvert = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BoutonContour(
                        texte = "+ Ligne libre",
                        onClick = { saisieLigne = true },
                        modifier = Modifier.fillMaxWidth(),
                        couleur = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Section(intitule = "Totaux") {
                LigneInfoFacture("Total HT", Nombres.enEuros(facture.totalHt))
                if (document.assujettiTva) {
                    LigneInfoFacture(
                        "TVA ${Nombres.enTexte(document.tauxTva)} %",
                        Nombres.enEuros(facture.tvaDue),
                    )
                    if (document.tvaOfferte) {
                        LigneInfoFacture(
                            "TVA offerte",
                            "− ${Nombres.enEuros(facture.remiseTva)}",
                        )
                    }
                } else {
                    Text(
                        text = Parametres.MENTION_FRANCHISE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LigneInfoFacture(
                    if (document.estAvoir) "Total de l'avoir" else "Total à payer",
                    Nombres.enEuros(facture.totalTtc),
                    forte = true,
                )
            }

            if (solde != null && (document.creditable || solde.avoirs.isNotEmpty())) {
                SectionAvoirs(
                    solde = solde,
                    creditable = document.creditable,
                    onFaireAvoir = { saisieAvoir = true },
                    onOuvrir = onOuvrir,
                )
            }
            EspaceVertical(24)
        }
    }

    if (saisieAvoir && solde != null) {
        DialogueAvoir(
            facture = facture,
            dejaCrediteHt = solde.dejaCrediteHt,
            onConfirmer = { quantites ->
                onFaireAvoir(quantites)
                saisieAvoir = false
            },
            onFermer = { saisieAvoir = false },
        )
    }

    if (catalogueOuvert) {
        FeuilleCatalogue(
            catalogue = catalogue,
            dejaAuDevis = facture.lignes.map { it.designation }.toSet(),
            onChoisir = onAjouterPrestation,
            // Une facture puise dans le catalogue sans l'enrichir : la prestation
            // nouvelle se crée depuis un devis ou dans les Réglages.
            onCreer = null,
            unitesVisees = 1,
            onFermer = { catalogueOuvert = false },
        )
    }

    if (saisieLigne) {
        DialogueLigneDevis(
            titre = "Ligne de facture",
            onValider = { designation, quantite, unite, prix ->
                onAjouterLigne(designation, quantite, unite, prix)
                saisieLigne = false
            },
            onFermer = { saisieLigne = false },
            // Le choix HT ou TTC, ouvert en HT : l'écran des factures ne lit pas le
            // réglage de saisie, et une facture se complète rarement à la main.
            tauxTva = document.tauxTva.takeIf { document.assujettiTva },
        )
    }

    if (paiementOuvert) {
        DialoguePaiement(
            onConfirmer = { le ->
                onPayee(le)
                paiementOuvert = false
            },
            onFermer = { paiementOuvert = false },
        )
    }

    confirmation?.let { quoi ->
        DialogueConfirmationFacture(
            quoi = quoi,
            avoir = document.estAvoir,
            manques = manques,
            onConfirmer = {
                when (quoi) {
                    ConfirmationFacture.EMETTRE -> onEmettre()
                    ConfirmationFacture.ANNULER -> onAnnuler()
                    ConfirmationFacture.SUPPRIMER -> onSupprimer()
                }
                confirmation = null
            },
            onFermer = { confirmation = null },
        )
    }
}

private enum class ConfirmationFacture { EMETTRE, ANNULER, SUPPRIMER }

@Composable
private fun DialogueConfirmationFacture(
    quoi: ConfirmationFacture,
    /** Le document est un avoir : les mêmes gestes, mais pas les mêmes mots. */
    avoir: Boolean,
    /**
     * Ce qui manque à l'en-tête de l'entreprise. Nommé à l'émission, et à elle
     * seule : c'est le dernier moment où l'on peut encore aller le renseigner,
     * puisqu'une facture émise ne se retouche plus. Le choix reste ouvert — rien
     * n'est bloqué, et rien n'est plus imprimé chez le client à la place.
     */
    manques: List<String> = emptyList(),
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    val (titre, texte, bouton) = when {
        avoir && quoi == ConfirmationFacture.EMETTRE -> Triple(
            "Émettre l'avoir",
            "Il prend son numéro dans la séquence des avoirs et ne se modifie plus. " +
                "Ce qu'il crédite vient aussitôt en déduction de la facture d'origine.",
            "Émettre",
        )

        avoir && quoi == ConfirmationFacture.ANNULER -> Triple(
            "Annuler l'avoir",
            "Il garde son numéro, et cesse de venir en déduction de la facture : " +
                "ce qu'il créditait redevient dû.",
            "Annuler l'avoir",
        )

        avoir && quoi == ConfirmationFacture.SUPPRIMER -> Triple(
            "Supprimer le brouillon d'avoir",
            "Ses lignes disparaissent avec lui. Aucun numéro n'a été attribué : " +
                "rien ne manquera dans la séquence des avoirs.",
            "Supprimer",
        )

        quoi == ConfirmationFacture.EMETTRE -> Triple(
            "Émettre la facture",
            "Elle prend son numéro, sa date et son échéance, et ne se modifie plus. " +
                "Le numéro est définitivement consommé : une facture émise ne se " +
                "supprime pas, elle s'annule.",
            "Émettre",
        )

        quoi == ConfirmationFacture.ANNULER -> Triple(
            "Annuler la facture",
            "Elle garde son numéro — l'effacer creuserait un trou dans la " +
                "numérotation — et cesse d'être réclamée.",
            "Annuler la facture",
        )

        else -> Triple(
            "Supprimer le brouillon",
            "Ses lignes disparaissent avec lui. Aucun numéro n'a été attribué : " +
                "rien ne manquera dans la séquence.",
            "Supprimer",
        )
    }

    val avertissement = manques.takeIf { quoi == ConfirmationFacture.EMETTRE && it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = titre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = texte)
                if (avertissement != null) {
                    Text(
                        text = "Il manque à l'en-tête de l'entreprise " +
                            avertissement.joinToString(", ") + ". Le document partira " +
                            "sans, et ne pourra plus être complété : ces mentions se " +
                            "renseignent dans les Réglages, avant d'émettre.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalStatuts.current.urgence,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmer) {
                Text(text = if (avertissement != null) "$bouton quand même" else bouton)
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Revenir") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * Pointer une facture payée, **à la date du règlement**.
 *
 * Aujourd'hui par défaut, parce que c'est le cas courant — on pointe en recevant
 * le chèque —, et modifiable, parce qu'on pointe aussi ses virements après coup,
 * relevé de banque en main. Dater du jour de la saisie aurait fait mentir le
 * « payée le » imprimé sur la facture renvoyée.
 */
@Composable
private fun DialoguePaiement(
    onConfirmer: (LocalDate) -> Unit,
    onFermer: () -> Unit,
) {
    var le by remember { mutableStateOf(LocalDate.now()) }
    var calendrierOuvert by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Facture payée") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LigneInfoFacture("Réglée le", jourCourt(le), forte = true)
                TextButton(onClick = { calendrierOuvert = true }) { Text(text = "Changer la date") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirmer(le) }) { Text(text = "Payée") } },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Revenir") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    if (calendrierOuvert) {
        SelecteurDate(
            date = le,
            onDateChoisie = {
                le = it
                calendrierOuvert = false
            },
            onFermer = { calendrierOuvert = false },
        )
    }
}

/**
 * Ce qu'un retard a fait courir, et ce qu'on peut en réclamer.
 *
 * Elle répond à la question qu'on se pose devant une facture échue — « je
 * réclame combien ? » — et à celle qu'on ne se pose pas : **on ne retouche pas
 * la facture**. Une facture émise ne bouge plus, c'est ce qui tient toute la
 * numérotation ; les pénalités sont dues *en plus*, de plein droit, sans rappel
 * préalable. Le montant grandit chaque jour, donc rien n'en est stocké.
 *
 * Sans taux convenu, les intérêts ne sont pas chiffrés. Ce n'est pas une lacune
 * de l'écran : le taux légal est celui de la BCE majoré de dix points, il varie
 * dans le temps et n'est pas dans le téléphone. Annoncer un chiffre inventé
 * serait le réclamer à un vrai client.
 */
@Composable
private fun SectionPenalites(dues: PenalitesDues) {
    val jours = dues.joursDeRetard
    Section(intitule = "Retard de paiement") {
        Text(
            text = "Échue depuis $jours jour${if (jours > 1) "s" else ""}.",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalStatuts.current.urgence,
        )
        val interets = dues.interets
        if (interets != null && dues.tauxAnnuel != null) {
            LigneInfoFacture(
                "Intérêts (${Nombres.enTexte(dues.tauxAnnuel)} % l'an)",
                Nombres.enEuros(interets),
            )
        } else {
            Text(
                text = "Aucun taux de pénalités n'est fixé dans les Réglages. Le taux légal " +
                    "s'applique alors de plein droit — celui de la BCE majoré de dix points —, " +
                    "mais il change dans le temps et n'est pas connu de l'application : les " +
                    "intérêts ne sont donc pas chiffrés ici.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LigneInfoFacture(
            "Indemnité de recouvrement",
            Nombres.enEuros(dues.indemnite),
        )
        dues.total?.let { LigneInfoFacture("Dû en plus de la facture", Nombres.enEuros(it)) }
        Text(
            text = "À réclamer en plus du montant facturé : une facture émise ne se retouche " +
                "pas, et les pénalités n'ont pas à y figurer pour être exigibles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * La facture qu'un avoir corrige, et le geste qui l'ouvre.
 *
 * Le numéro et la date viennent de la copie posée sur l'avoir : ils se lisent
 * même si la facture n'est plus là pour être ouverte.
 */
@Composable
private fun LigneOrigineAvoir(avoir: Facture, onOuvrir: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sur la facture",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = listOfNotNull(
                    avoir.factureOrigineNumero.ifBlank { null },
                    avoir.factureOrigineEmiseLe?.let { "du ${jourCourt(it)}" },
                ).joinToString(" ").ifBlank { "Référence non renseignée" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (onOuvrir != null) {
            TextButton(onClick = onOuvrir) { Text(text = "Ouvrir") }
        }
    }
}

/**
 * Ce que les avoirs ont fait d'une facture, et le geste qui en ajoute un.
 *
 * Elle dit **de combien** la facture a été créditée et **ce qu'il en reste**,
 * parce que c'est la question qu'on se pose devant une facture corrigée — la
 * facture elle-même ne bouge pas, et ses totaux au-dessus restent ceux du
 * document parti. Rien n'est stocké : le reste se dérive des avoirs émis, comme
 * le retard de l'échéance.
 *
 * Une facture payée puis créditée ne « reste » rien devoir : c'est l'entreprise
 * qui doit, et l'écran le dit plutôt que d'afficher un reste à zéro qui se lirait
 * comme une affaire close.
 */
@Composable
private fun SectionAvoirs(
    solde: SoldeFacture,
    creditable: Boolean,
    onFaireAvoir: () -> Unit,
    onOuvrir: (String) -> Unit,
) {
    val chiffree = solde.chiffree
    Section(intitule = "Avoirs") {
        if (solde.avoirs.isEmpty()) {
            Text(
                text = "Aucun avoir. Une facture émise ne se retouche pas : si elle est " +
                    "fausse, c'est un avoir qui la corrige, en tout ou en partie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        solde.avoirs.forEach { avoir ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = avoir.facture.numero.ifBlank { "Brouillon d'avoir" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = avoir.facture.statut.libelle +
                            (avoir.facture.emiseLe?.let { " le ${jourCourt(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "− ${Nombres.enEuros(avoir.totalTtc)}",
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (avoir.facture.deduit) null else TextDecoration.LineThrough,
                )
                TextButton(onClick = { onOuvrir(avoir.facture.id) }) { Text(text = "Ouvrir") }
            }
        }
        if (chiffree.creditee) {
            LigneInfoFacture("Crédité par avoir", "− ${Nombres.enEuros(chiffree.avoirsTtc)}")
            if (chiffree.facture.statut == StatutFacture.PAYEE) {
                Text(
                    text = "La facture était réglée : ce qui a été crédité se rembourse au " +
                        "client, ou s'impute sur une prochaine facture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LigneInfoFacture("Reste dû", Nombres.enEuros(chiffree.resteTtc), forte = true)
            }
        }
        // Proposé tant qu'il reste quelque chose à créditer, brouillons compris :
        // c'est ce que compte le dépôt, et un geste qu'il refuserait à coup sûr
        // vaut moins que pas de geste.
        if (creditable && solde.dejaCrediteHt + DEMI_CENTIME < chiffree.totalHt) {
            BoutonContour(
                texte = "Faire un avoir",
                onClick = onFaireAvoir,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Ce que l'avoir va créditer, ligne par ligne.
 *
 * Toutes les quantités sont pré-remplies à celles de la facture : c'est l'**avoir
 * total**, le cas le plus fréquent — une facture partie au mauvais client, ou en
 * double —, et il tient en un appui. L'avoir **partiel** est la même boîte, des
 * quantités baissées ou mises à zéro : un détendeur repris, une heure facturée de
 * trop. Le prix, lui, ne se saisit pas : un avoir crédite ce qui a été facturé, au
 * prix où ça l'a été, et un prix libre en ferait une remise déguisée.
 *
 * La saisie vit en état local, comme celle du déplacement : rien n'est écrit
 * avant le geste qui confirme, et ce qui est écrit alors n'est qu'un brouillon.
 * Le total se recalcule à chaque frappe, par [Avoirs.lignes] et les totaux
 * partagés — ceux-là mêmes que le dépôt appliquera.
 */
@Composable
private fun DialogueAvoir(
    facture: FactureComplete,
    dejaCrediteHt: Double,
    onConfirmer: (Map<String, Double>?) -> Unit,
    onFermer: () -> Unit,
) {
    var quantites by remember {
        mutableStateOf(facture.lignes.associate { it.id to it.quantite })
    }
    val prevues = Avoirs.lignes(APERCU_AVOIR, facture.lignes, quantites)
    val avoirHt = prevues?.let { FactureComplete(facture.facture, it).totalHt } ?: 0.0
    val avoirTtc = prevues?.let { FactureComplete(facture.facture, it).totalTtc } ?: 0.0
    val possible = prevues != null &&
        avoirHt >= DEMI_CENTIME &&
        !Avoirs.depasse(facture.totalHt, listOf(dejaCrediteHt), avoirHt)
    val total = facture.lignes.all { quantites[it.id] == it.quantite }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Faire un avoir") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "La quantité à créditer, ligne par ligne. Tout laisser tel quel " +
                        "fait l'avoir total ; une ligne à zéro n'y entre pas. L'avoir naît " +
                        "brouillon, et ne prend son numéro qu'à l'émission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dejaCrediteHt > 0.0) {
                    Text(
                        text = "Déjà crédité, brouillons compris : " +
                            "${Nombres.enEuros(dejaCrediteHt)} HT sur " +
                            "${Nombres.enEuros(facture.totalHt)} HT.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                facture.lignes.forEach { ligne ->
                    ChampChiffre(
                        libelle = "${ligne.designation} — facturé " +
                            Nombres.enTexte(ligne.quantite) +
                            " × ${Nombres.enEuros(ligne.prixUnitaire)}",
                        valeur = quantites[ligne.id],
                        unite = ligne.unite,
                        onValeur = { saisie -> quantites = quantites + (ligne.id to (saisie ?: 0.0)) },
                    )
                }
                LigneInfoFacture(
                    if (total) "Avoir total" else "Avoir partiel",
                    Nombres.enEuros(avoirTtc),
                    forte = true,
                )
                if (!possible) {
                    Text(
                        text = when {
                            prevues == null ->
                                "Une quantité dépasse celle facturée, ou aucune ligne n'est créditée."
                            avoirHt < DEMI_CENTIME -> "Cet avoir ne crédite rien."
                            else -> "L'avoir dépasserait ce qu'il reste à créditer sur la facture."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmer(if (total) null else quantites) },
                enabled = possible,
            ) { Text(text = "Créer le brouillon") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Revenir") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** Sous ce montant, il ne reste rien à créditer : moins d'un centime. */
private const val DEMI_CENTIME = 0.005

/**
 * L'identifiant des lignes d'aperçu de [DialogueAvoir], qui ne sont jamais
 * écrites : le dépôt refait les siennes, sous l'identifiant de l'avoir.
 */
private const val APERCU_AVOIR = "apercu"

@Composable
private fun ActionsFacture(
    facture: Facture,
    /**
     * Le solde est en retard : « Envoyer » devient « Relancer », et le message
     * qui part le dit — reste dû et échéance dépassée. C'était le même geste sous
     * un nom qui ne disait pas ce qu'il faisait.
     */
    enRetard: Boolean = false,
    /** Faux sur une facture qui a reçu un avoir émis : elle se crédite, elle ne s'annule plus. */
    annulable: Boolean = true,
    onEmettre: () -> Unit,
    onEnvoyer: () -> Unit,
    onPayee: () -> Unit,
    onImpayee: () -> Unit,
    onAnnuler: () -> Unit,
    onSupprimer: () -> Unit,
) {
    BarreActions {
        when (facture.statut) {
            StatutFacture.BROUILLON -> {
                BoutonContour(
                    texte = "Supprimer",
                    onClick = onSupprimer,
                    couleur = LocalStatuts.current.urgence,
                )
                BoutonPlein(
                    texte = "Émettre",
                    onClick = onEmettre,
                    modifier = Modifier.weight(1f),
                )
            }

            StatutFacture.EMISE -> {
                // Un avoir ne se règle pas : c'est l'entreprise qui doit.
                if (!facture.estAvoir) BoutonContour(texte = "Payée", onClick = onPayee)
                BoutonPlein(
                    texte = if (enRetard && !facture.estAvoir) "Relancer" else "Envoyer",
                    onClick = onEnvoyer,
                    modifier = Modifier.weight(1f),
                )
            }

            StatutFacture.PAYEE -> {
                BoutonContour(texte = "Impayée", onClick = onImpayee)
                BoutonPlein(
                    texte = "Renvoyer",
                    onClick = onEnvoyer,
                    modifier = Modifier.weight(1f),
                )
            }

            StatutFacture.ANNULEE -> {
                BoutonContour(
                    texte = "Renvoyer",
                    onClick = onEnvoyer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (facture.statut == StatutFacture.EMISE && annulable) {
            BoutonContour(
                texte = "Annuler",
                onClick = onAnnuler,
                couleur = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LigneInfoFacture(intitule: String, valeur: String, forte: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = intitule,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valeur,
            style = if (forte) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

@Composable
private fun LigneFactureAffichee(
    ligne: LigneFacture,
    modifiable: Boolean,
    onSupprimer: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = ligne.designation, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = Nombres.enTexte(ligne.quantite) +
                    (if (ligne.unite.isBlank()) "" else " ${ligne.unite}") +
                    " × ${Nombres.enEuros(ligne.prixUnitaire)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Une ligne offerte garde son prix, barré : un geste commercial qu'on ne
        // voit pas n'est pas un geste commercial.
        Text(
            text = Nombres.enEuros(ligne.montantAvantGeste),
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (ligne.offerte) TextDecoration.LineThrough else null,
            color = if (ligne.offerte) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (modifiable) {
            TextButton(onClick = onSupprimer) { Text(text = "Retirer") }
        }
    }
}

/**
 * Le message d'une relance : le numéro, l'échéance dépassée, et **le reste dû** —
 * pas le total, puisqu'un avoir a pu en créditer une partie, et réclamer à un
 * client une somme qu'on lui a créditée est la façon la plus sûre de le perdre.
 *
 * Les pénalités n'y sont pas chiffrées : elles sont dues de plein droit et la
 * facture les annonce déjà ; les ajouter d'office à un premier rappel durcirait
 * le ton d'un message qu'on relit de toute façon avant de l'envoyer.
 */
private fun corpsDeLaRelance(chiffree: FactureChiffree): String {
    val facture = chiffree.facture
    val echeance = facture.echeanceLe?.let { " arrivée à échéance le ${jourCourt(it)}" }.orEmpty()
    return "Bonjour,\n\nSauf erreur de notre part, la facture ${facture.numero}$echeance " +
        "reste à régler pour un montant de ${Nombres.enEuros(chiffree.resteTtc)}. " +
        "Vous la trouverez ci-jointe.\n\nSi le règlement est déjà parti, merci de ne pas " +
        "tenir compte de ce message.\n\nCordialement."
}

/** Le message qui accompagne le PDF, prêt à être complété. */
private fun corpsDeLaFacture(facture: Facture?): String {
    if (facture == null) return "Veuillez trouver ci-joint notre facture."
    if (facture.estAvoir) {
        val origine = facture.factureOrigineNumero.takeIf { it.isNotBlank() }
            ?.let { " sur la facture $it" }
            .orEmpty()
        return "Bonjour,\n\nVeuillez trouver ci-joint l'avoir ${facture.numero}$origine." +
            "\n\nCordialement."
    }
    val echeance = facture.echeanceLe?.let { " Règlement attendu pour le ${jourCourt(it)}." }.orEmpty()
    return "Bonjour,\n\nVeuillez trouver ci-joint la facture ${facture.numero}.$echeance" +
        "\n\nCordialement."
}
