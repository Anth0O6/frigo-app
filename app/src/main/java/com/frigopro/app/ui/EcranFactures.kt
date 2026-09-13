package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
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
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.FactureComplete
import com.frigopro.app.data.LigneFacture
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.StatutFacture
import com.frigopro.app.ui.composants.BarreActions
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.LocalStatuts
import java.time.LocalDate

/** Les deux faces de l'onglet : ce qu'on propose, et ce qu'on réclame. */
enum class VueFacturation(val libelle: String) {
    DEVIS("Devis"),
    FACTURES("Factures"),
}

/**
 * L'onglet « Devis », qui porte en réalité les deux documents commerciaux.
 *
 * Une bascule plutôt qu'un septième onglet, et ce n'est pas un pis-aller : la
 * barre en porte déjà six, un de plus que ce que Material recommande, et un
 * septième aurait réduit chaque cible à ce qu'un pouce ganté ne trouve plus. Les
 * deux documents se suivent d'ailleurs dans le temps — on chiffre, puis on
 * facture — et les mettre à deux endroits éloignés aurait séparé les deux moitiés
 * d'un même geste.
 *
 * Ouvrir une facture depuis ailleurs — le bouton « Facturer » d'une intervention
 * — bascule la vue tout seul : le ViewModel des factures est partagé, et une
 * facture ouverte est l'unique raison qu'a cet onglet de montrer les factures.
 */
@Composable
fun FacturationRoute(
    modifier: Modifier = Modifier,
    facturesViewModel: FacturesViewModel = viewModel(factory = FacturesViewModel.Factory),
) {
    var vue by rememberSaveable { mutableStateOf(VueFacturation.DEVIS) }
    val factureOuverte by facturesViewModel.ouverte.collectAsStateWithLifecycle()

    LaunchedEffect(factureOuverte) {
        if (factureOuverte != null) vue = VueFacturation.FACTURES
    }

    when (vue) {
        VueFacturation.DEVIS -> DevisRoute(
            modifier = modifier,
            onVoirFactures = { vue = VueFacturation.FACTURES },
        )

        VueFacturation.FACTURES -> FacturesRoute(
            modifier = modifier,
            onVoirDevis = { vue = VueFacturation.DEVIS },
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
    val contexte = LocalContext.current

    // Le partage s'ouvre dès que le PDF est écrit, puis le ViewModel oublie le
    // document : sans cet oubli, revenir sur l'onglet rouvrirait le sélecteur.
    LaunchedEffect(documentPret) {
        val fichier = documentPret ?: return@LaunchedEffect
        val ouverte = complete?.facture
        contexte.envoyerDocument(
            document = fichier,
            objet = listOf("Facture", ouverte?.numero.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" "),
            corps = corpsDeLaFacture(ouverte),
        )
        viewModel.onDocumentPartage()
        // Envoyer une facture en retard, c'est la relancer : le noter ici évite
        // qu'elle remonte le lendemain dans la liste des relances à faire.
        if (ouverte != null && ouverte.enRetard(LocalDate.now())) viewModel.onRelancee(ouverte)
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

    BackHandler(enabled = complete != null) { viewModel.onFermer() }

    val ouverte = complete
    if (ouverte != null) {
        EcranFacture(
            facture = ouverte,
            onEmettre = viewModel::onEmettre,
            onEnvoyer = viewModel::onExporterPdf,
            onPayee = viewModel::onPayee,
            onImpayee = viewModel::onImpayee,
            onAnnuler = viewModel::onAnnuler,
            onSupprimer = viewModel::onSupprimer,
            onSupprimerLigne = viewModel::onSupprimerLigne,
            onFermer = viewModel::onFermer,
            modifier = modifier,
        )
    } else {
        ListeFactures(
            factures = liste,
            compteurs = compteurs,
            onOuvrir = viewModel::onOuvrir,
            onVoirDevis = onVoirDevis,
            modifier = modifier,
        )
    }
}

/** La liste des factures, de la plus récente à la plus ancienne. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeFactures(
    factures: List<FactureChiffree>,
    compteurs: CompteursFactures,
    onOuvrir: (Facture) -> Unit,
    onVoirDevis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statuts = LocalStatuts.current
    val aujourdhui = remember { LocalDate.now() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            Text(
                text = "Facturation",
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
                if (factures.isEmpty()) {
                    item {
                        Encart(
                            texte = "Aucune facture. Une intervention terminée se facture " +
                                "depuis sa fiche, et un devis accepté depuis le devis : les " +
                                "lignes se construisent alors toutes seules.",
                        )
                    }
                }
                items(factures, key = { it.facture.id }) { chiffree ->
                    CarteFacture(
                        chiffree = chiffree,
                        aujourdhui = aujourdhui,
                        onClick = { onOuvrir(chiffree.facture) },
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
) {
    val facture = chiffree.facture
    val retard = facture.joursDeRetard(aujourdhui)

    Carte(onClick = onClick, liseré = couleurStatutFacture(facture, aujourdhui)) {
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
            Text(
                text = Nombres.enEuros(chiffree.totalTtc),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Puce(
                texte = facture.statut.libelle,
                couleur = couleurStatutFacture(facture, aujourdhui),
            )
            if (retard != null) {
                Puce(
                    texte = "En retard de $retard j",
                    couleur = LocalStatuts.current.urgence,
                )
            } else if (facture.echeanceLe != null && facture.statut.attendPaiement) {
                Puce(texte = "Échéance ${jourCourt(facture.echeanceLe!!)}")
            }
        }
    }
}

/**
 * La couleur d'une facture.
 *
 * Le retard l'emporte sur le statut, et c'est voulu : « émise » et « émise depuis
 * quarante jours » ne demandent pas le même geste.
 */
@Composable
private fun couleurStatutFacture(facture: Facture, aujourdhui: LocalDate) =
    when {
        facture.enRetard(aujourdhui) -> LocalStatuts.current.urgence
        facture.statut == StatutFacture.PAYEE -> LocalStatuts.current.termine
        facture.statut == StatutFacture.ANNULEE -> MaterialTheme.colorScheme.onSurfaceVariant
        facture.statut == StatutFacture.EMISE -> LocalStatuts.current.aValider
        else -> LocalStatuts.current.planifie
    }

/**
 * Une facture ouverte.
 *
 * Les actions du bas changent avec le statut, et c'est le cœur de l'écran : un
 * brouillon s'émet, une facture émise s'envoie et s'encaisse, une facture payée
 * ne se réclame plus. Proposer les quatre en permanence aurait mis « Émettre » à
 * côté d'une facture déjà partie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranFacture(
    facture: FactureComplete,
    onEmettre: () -> Unit,
    onEnvoyer: () -> Unit,
    onPayee: () -> Unit,
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ActionsFacture(
                facture = document,
                onEmettre = { confirmation = ConfirmationFacture.EMETTRE },
                onEnvoyer = onEnvoyer,
                onPayee = onPayee,
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
                        text = document.numero.ifBlank { "Brouillon" },
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

            val retard = document.joursDeRetard(aujourdhui)
            if (retard != null) {
                Encart(
                    texte = "Échue depuis $retard jour${if (retard > 1) "s" else ""}. " +
                        "Les pénalités courent depuis le lendemain de l'échéance, et la " +
                        "facture les annonce : les réclamer ne demande pas de rappel " +
                        "préalable.",
                    alerte = true,
                )
            }

            Section(intitule = "Document") {
                LigneInfoFacture("Statut", document.statut.libelle)
                if (document.objet.isNotBlank()) LigneInfoFacture("Objet", document.objet)
                document.emiseLe?.let { LigneInfoFacture("Émise le", jourCourt(it)) }
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
                        modifiable = !document.statut.figee,
                        onSupprimer = { onSupprimerLigne(ligne) },
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
                LigneInfoFacture("Total à payer", Nombres.enEuros(facture.totalTtc), forte = true)
            }
            EspaceVertical(24)
        }
    }

    confirmation?.let { quoi ->
        DialogueConfirmationFacture(
            quoi = quoi,
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
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    val (titre, texte, bouton) = when (quoi) {
        ConfirmationFacture.EMETTRE -> Triple(
            "Émettre la facture",
            "Elle prend son numéro, sa date et son échéance, et ne se modifie plus. " +
                "Le numéro est définitivement consommé : une facture émise ne se " +
                "supprime pas, elle s'annule.",
            "Émettre",
        )

        ConfirmationFacture.ANNULER -> Triple(
            "Annuler la facture",
            "Elle garde son numéro — l'effacer creuserait un trou dans la " +
                "numérotation — et cesse d'être réclamée.",
            "Annuler la facture",
        )

        ConfirmationFacture.SUPPRIMER -> Triple(
            "Supprimer le brouillon",
            "Ses lignes disparaissent avec lui. Aucun numéro n'a été attribué : " +
                "rien ne manquera dans la séquence.",
            "Supprimer",
        )
    }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = titre) },
        text = { Text(text = texte) },
        confirmButton = { TextButton(onClick = onConfirmer) { Text(text = bouton) } },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Revenir") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun ActionsFacture(
    facture: Facture,
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
                BoutonContour(texte = "Payée", onClick = onPayee)
                BoutonPlein(
                    texte = "Envoyer",
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
        if (facture.statut == StatutFacture.EMISE) {
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

/** Le message qui accompagne le PDF, prêt à être complété. */
private fun corpsDeLaFacture(facture: Facture?): String {
    if (facture == null) return "Veuillez trouver ci-joint notre facture."
    val echeance = facture.echeanceLe?.let { " Règlement attendu pour le ${jourCourt(it)}." }.orEmpty()
    return "Bonjour,\n\nVeuillez trouver ci-joint la facture ${facture.numero}.$echeance" +
        "\n\nCordialement."
}
