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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Devis
import com.frigopro.app.data.DevisComplet
import com.frigopro.app.data.LigneDevis
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
import com.frigopro.app.ui.theme.Ambre
import com.frigopro.app.ui.theme.BleuFroid
import com.frigopro.app.ui.theme.Cyan
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
            onSupprimerLigne = viewModel::onSupprimerLigne,
            onSupprimer = viewModel::onSupprimer,
            onFermer = viewModel::onFermer,
            modifier = modifier,
        )
    } else {
        ListeDevis(
            devis = liste,
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
    devis: List<Devis>,
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
                items(items = devis, key = { it.id }) { document ->
                    LigneDevisListe(devis = document, onClick = { onOuvrir(document) })
                }
            }
        }
    }
}

@Composable
private fun LigneDevisListe(devis: Devis, onClick: () -> Unit) {
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
            PuceStatut(devis.statut)
        }
    }
}

@Composable
private fun PuceStatut(statut: StatutDevis) {
    val couleur = when (statut) {
        StatutDevis.BROUILLON -> BleuFroid
        StatutDevis.ENVOYE -> Ambre
        StatutDevis.ACCEPTE -> Cyan
        StatutDevis.REFUSE -> MaterialTheme.colorScheme.onSurfaceVariant
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
    onSupprimerLigne: (String) -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var saisieOuverte by remember { mutableStateOf(false) }
    var clientOuvert by remember { mutableStateOf(false) }

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
                LigneDuDevis(ligne = ligne, onSupprimer = { onSupprimerLigne(ligne.id) })
            }

            BoutonContour(
                texte = "+ Ajouter une ligne",
                onClick = { saisieOuverte = true },
                modifier = Modifier.fillMaxWidth(),
                couleur = MaterialTheme.colorScheme.secondary,
            )

            CarteTotaux(devis = devis)

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

@Composable
private fun LigneDuDevis(ligne: LigneDevis, onSupprimer: () -> Unit) {
    Carte(contour = true, onClick = onSupprimer) {
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
            Text(text = Nombres.enEuros(ligne.montant), style = StyleChiffrePetit)
        }
    }
}

@Composable
private fun CarteTotaux(devis: DevisComplet) {
    Carte(relief = true) {
        LigneTotal("Total HT", Nombres.enEuros(devis.totalHt))
        LigneTotal("TVA ${Nombres.enTexte(devis.devis.tauxTva)} %", Nombres.enEuros(devis.tva))
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
    }
}

@Composable
private fun LigneTotal(intitule: String, valeur: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = intitule,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valeur,
            style = StyleChiffrePetit,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
