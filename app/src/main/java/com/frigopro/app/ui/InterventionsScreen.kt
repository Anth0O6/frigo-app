package com.frigopro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.data.enDuree
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.theme.Ambre
import com.frigopro.app.ui.theme.BleuFroid
import com.frigopro.app.ui.theme.FrigoProTheme
import com.frigopro.app.ui.theme.StyleChiffre
import com.frigopro.app.ui.theme.StyleChiffrePetit
import com.frigopro.app.ui.theme.StyleSection
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * La tournée du jour, avec son état.
 *
 * Elle héberge aussi l'écran d'une intervention : ouvrir une intervention
 * remplace la liste plutôt que de l'empiler, comme la fiche machine remplace
 * le carnet. Une seule profondeur, et donc toujours pas de graphe de
 * navigation.
 */
@Composable
fun InterventionsRoute(
    modifier: Modifier = Modifier,
    viewModel: InterventionsViewModel = viewModel(factory = InterventionsViewModel.Factory),
    detail: InterventionViewModel = viewModel(factory = InterventionViewModel.Factory),
) {
    val jour by viewModel.jour.collectAsStateWithLifecycle()
    val lignes by viewModel.lignes.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val types by viewModel.types.collectAsStateWithLifecycle()
    val machines by viewModel.machines.collectAsStateWithLifecycle()
    val formulaire by viewModel.formulaire.collectAsStateWithLifecycle()
    val ouverte by detail.ouverte.collectAsStateWithLifecycle()
    val semaineOuverte by viewModel.semaineOuverte.collectAsStateWithLifecycle()
    val semaine by viewModel.semaine.collectAsStateWithLifecycle()

    if (ouverte != null) {
        InterventionRoute(modifier = modifier, viewModel = detail)
        return
    }

    BackHandler(enabled = semaineOuverte) { viewModel.onFermerSemaine() }

    if (semaineOuverte) {
        EcranSemaine(
            lundi = lundiDe(jour),
            jourRetenu = jour,
            interventions = semaine,
            onJourRetenu = {
                viewModel.onJourChoisi(it)
                viewModel.onFermerSemaine()
            },
            onSemainePrecedente = viewModel::onSemainePrecedente,
            onSemaineSuivante = viewModel::onSemaineSuivante,
            onOuvrir = detail::onOuvrir,
            modifier = modifier,
        )
        return
    }

    InterventionsScreen(
        jour = jour,
        lignes = lignes,
        onJourPrecedent = viewModel::onJourPrecedent,
        onJourSuivant = viewModel::onJourSuivant,
        onJourChoisi = viewModel::onJourChoisi,
        onNouvelleIntervention = viewModel::onNouvelleIntervention,
        onOuvrirSemaine = viewModel::onOuvrirSemaine,
        onOuvrirIntervention = detail::onOuvrir,
        onModifierIntervention = viewModel::onModifierIntervention,
        onChangerStatut = viewModel::onChangerStatut,
        actions = { MenuSauvegarde() },
        modifier = modifier,
    )

    formulaire?.let { etat ->
        FormulaireIntervention(
            etat = etat,
            clients = clients,
            types = types,
            // Seules les machines du client choisi : celles des autres clients
            // n'ont rien à faire dans cette saisie.
            machines = machines.filter { it.clientId == etat.clientId },
            onEtatChange = viewModel::onFormulaireChange,
            onClientChoisi = viewModel::onClientChoisi,
            onTypeChoisi = viewModel::onTypeChoisi,
            onNouveauType = viewModel::onNouveauType,
            onMachineChoisie = viewModel::onMachineChoisie,
            onNouvelleMachine = viewModel::onNouvelleMachine,
            onValider = viewModel::onValiderFormulaire,
            onSupprimer = viewModel::onSupprimerIntervention,
            onFermer = viewModel::onFermerFormulaire,
        )
    }
}

/**
 * La tournée d'une journée.
 *
 * Trois choses la structurent, dans cet ordre : où l'on en est (le bandeau de
 * chiffres), ce qui vient maintenant (l'intervention en cours, dépliée), et ce
 * qui suit (les autres, en une ligne chacune). Un technicien consulte cet
 * écran vingt fois par jour et n'y cherche jamais qu'une chose : la suivante.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterventionsScreen(
    jour: LocalDate,
    lignes: List<LigneTournee>,
    onJourPrecedent: () -> Unit,
    onJourSuivant: () -> Unit,
    onJourChoisi: (LocalDate) -> Unit,
    onNouvelleIntervention: () -> Unit,
    onOuvrirSemaine: () -> Unit,
    onOuvrirIntervention: (Intervention) -> Unit,
    onModifierIntervention: (Intervention) -> Unit,
    onChangerStatut: (Intervention) -> Unit,
    modifier: Modifier = Modifier,
    /** Posé dans la barre du haut : la sauvegarde s'y branche sans que l'écran la connaisse. */
    actions: @Composable RowScope.() -> Unit = {},
) {
    var selecteurOuvert by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNouvelleIntervention,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Ajouter une intervention")
            }
        },
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            EnTeteTournee(
                jour = jour,
                onJourPrecedent = onJourPrecedent,
                onJourSuivant = onJourSuivant,
                onOuvrirSelecteur = { selecteurOuvert = true },
                onOuvrirSemaine = onOuvrirSemaine,
                actions = actions,
            )
            BandeauJournee(lignes = lignes)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MargeEcran,
                    end = MargeEcran,
                    top = 4.dp,
                    // De quoi faire passer la dernière carte au-dessus du bouton.
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (lignes.isEmpty()) {
                    item { JourneeVide() }
                }
                items(items = lignes, key = { it.intervention.id }) { ligne ->
                    if (ligne.intervention.statut == StatutIntervention.EN_COURS) {
                        CarteEnCours(
                            ligne = ligne,
                            onOuvrir = { onOuvrirIntervention(ligne.intervention) },
                            onChangerStatut = { onChangerStatut(ligne.intervention) },
                        )
                    } else {
                        LigneCompacte(
                            ligne = ligne,
                            onOuvrir = { onOuvrirIntervention(ligne.intervention) },
                            onModifier = { onModifierIntervention(ligne.intervention) },
                            onChangerStatut = { onChangerStatut(ligne.intervention) },
                        )
                    }
                }
            }
        }
    }

    if (selecteurOuvert) {
        SelecteurDate(
            date = jour,
            onDateChoisie = {
                onJourChoisi(it)
                selecteurOuvert = false
            },
            onFermer = { selecteurOuvert = false },
        )
    }
}

@Composable
private fun EnTeteTournee(
    jour: LocalDate,
    onJourPrecedent: () -> Unit,
    onJourSuivant: () -> Unit,
    onOuvrirSelecteur: () -> Unit,
    onOuvrirSemaine: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MargeEcran, end = MargeEcran, top = 8.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOuvrirSelecteur),
        ) {
            Text(
                text = libelleDate(jour).uppercase(),
                style = StyleSection,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = titreJour(jour),
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BoutonCarre(
            icone = Icons.AutoMirrored.Filled.ArrowBack,
            description = "Jour précédent",
            onClick = onJourPrecedent,
        )
        BoutonCarre(
            icone = Icons.AutoMirrored.Filled.ArrowForward,
            description = "Jour suivant",
            onClick = onJourSuivant,
        )
        BoutonCarre(
            icone = Icons.Filled.CalendarMonth,
            description = "Voir la semaine",
            onClick = onOuvrirSemaine,
        )
        actions()
    }
}

/**
 * Le bandeau de chiffres : ce qui est fait, le temps saisi, les urgences.
 *
 * Le temps saisi est la somme des chronomètres de la journée. Il ne sert pas
 * qu'à informer : c'est lui qui fait remarquer qu'on a oublié d'arrêter un
 * chrono, ou qu'une journée à rallonge n'a été facturée qu'à moitié.
 */
@Composable
private fun BandeauJournee(lignes: List<LigneTournee>) {
    if (lignes.isEmpty()) return

    val terminees = lignes.count { it.intervention.statut == StatutIntervention.TERMINEE }
    val cumul = lignes.fold(Duration.ZERO) { total, ligne ->
        total.plus(ligne.intervention.chrono.ecoulee(java.time.Instant.now()))
    }
    val urgences = lignes.count { it.intervention.urgente }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MargeEcran)
            .padding(bottom = 14.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChiffreJournee(
                valeur = "$terminees/${lignes.size}",
                libelle = "terminées",
                couleur = MaterialTheme.colorScheme.primary,
            )
            SeparateurVertical()
            ChiffreJournee(valeur = cumul.enDuree(), libelle = "temps saisi")
            if (urgences > 0) {
                SeparateurVertical()
                ChiffreJournee(valeur = "$urgences", libelle = "urgence", couleur = Ambre)
            }
        }
    }
}

@Composable
private fun ChiffreJournee(
    valeur: String,
    libelle: String,
    couleur: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(text = valeur, style = StyleChiffre, color = couleur)
        Text(
            text = libelle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeparateurVertical() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * L'intervention en cours, dépliée.
 *
 * C'est la seule carte de la liste qui porte des boutons : celle qu'on est en
 * train de faire est la seule sur laquelle on agit sans réfléchir — terminer,
 * appeler, y aller. Les autres se contentent d'attendre.
 */
@Composable
private fun CarteEnCours(
    ligne: LigneTournee,
    onOuvrir: () -> Unit,
    onChangerStatut: () -> Unit,
) {
    val contexte = LocalContext.current
    val intervention = ligne.intervention
    val ecoule = intervention.chrono.ecoulee(java.time.Instant.now())

    Carte(relief = true, liseré = Ambre, onClick = onOuvrir) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = intervention.heure.format(FORMAT_HEURE),
                style = StyleChiffrePetit,
                color = Ambre,
            )
            Puce(
                texte = if (intervention.chrono.vierge) {
                    "EN COURS"
                } else {
                    "EN COURS · ${ecoule.enDuree()}"
                },
                couleur = Ambre,
                fond = Ambre.copy(alpha = 0.16f),
            )
        }
        Column {
            Text(
                text = intervention.client,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sousTitre(intervention),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (intervention.typeLibelle.isNotBlank()) {
                Puce(texte = intervention.typeLibelle, couleur = BleuFroid)
            }
            if (intervention.equipementNom.isNotBlank()) {
                Puce(texte = intervention.equipementNom, couleur = BleuFroid)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoutonPlein(
                texte = "Terminer",
                onClick = onChangerStatut,
                modifier = Modifier.weight(1f),
            )
            if (ligne.appelable) {
                BoutonCarre(
                    icone = Icons.Filled.Call,
                    description = "Appeler ${intervention.client}",
                    onClick = { ligne.client?.telephone?.let(contexte::appeler) },
                    fond = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            if (ligne.localisable) {
                BoutonCarre(
                    icone = Icons.Filled.Directions,
                    description = "Itinéraire vers ${intervention.client}",
                    onClick = { ligne.client?.adresseComplete?.let(contexte::ouvrirItineraire) },
                    fond = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

/**
 * Une intervention qui attend, ou qui est faite.
 *
 * Une ligne, une heure, un nom : de quoi balayer la journée du pouce. La
 * pastille de droite change le statut sans ouvrir quoi que ce soit — c'est le
 * geste qu'on fait en remontant dans la camionnette.
 */
@Composable
private fun LigneCompacte(
    ligne: LigneTournee,
    onOuvrir: () -> Unit,
    onModifier: () -> Unit,
    onChangerStatut: () -> Unit,
) {
    val intervention = ligne.intervention
    val terminee = intervention.statut == StatutIntervention.TERMINEE

    Carte(onClick = onOuvrir, onLongClick = onModifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = intervention.heure.format(FORMAT_HEURE),
                style = StyleChiffrePetit,
                color = if (terminee) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = intervention.client,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (terminee) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sousTitre(intervention),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (intervention.urgente && !terminee) {
                Puce(texte = "URGENCE", couleur = Ambre, fond = Ambre.copy(alpha = 0.16f))
            }
            PastilleStatut(terminee = terminee, onClick = onChangerStatut)
        }
    }
}

/** Le rond qu'on touche pour avancer : vide à faire, coché terminé. */
@Composable
private fun PastilleStatut(terminee: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = androidx.compose.ui.graphics.Color.Transparent,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (terminee) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Rouvrir l'intervention",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Surface(
                    modifier = Modifier.size(14.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {}
            }
        }
    }
}

@Composable
private fun JourneeVide() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Aucune intervention",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Le bouton + en ajoute une.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** « Chambre froide positive · Vitry-sur-Seine », selon ce qui est connu. */
private fun sousTitre(intervention: Intervention): String = listOf(
    intervention.equipementNom.ifBlank { intervention.typeLibelle },
    intervention.ville,
).filter { it.isNotBlank() }.joinToString(" · ")

@Preview
@Composable
private fun ApercuTournee() {
    FrigoProTheme(sombre = true) {
        InterventionsScreen(
            jour = LocalDate.of(2026, 5, 14),
            lignes = listOf(
                LigneTournee(
                    intervention = Intervention(
                        id = "1",
                        date = LocalDate.of(2026, 5, 14),
                        heure = LocalTime.of(8, 0),
                        client = "Boucherie Martel",
                        ville = "Vitry-sur-Seine",
                        typeLibelle = "Dépannage",
                        equipementNom = "Chambre froide positive",
                        statut = StatutIntervention.EN_COURS,
                        urgente = true,
                    ),
                    client = null,
                ),
                LigneTournee(
                    intervention = Intervention(
                        id = "2",
                        date = LocalDate.of(2026, 5, 14),
                        heure = LocalTime.of(10, 30),
                        client = "SCI Le Vallon",
                        ville = "Ivry",
                        typeLibelle = "Entretien PAC",
                    ),
                    client = null,
                ),
            ),
            onJourPrecedent = {},
            onJourSuivant = {},
            onJourChoisi = {},
            onNouvelleIntervention = {},
            onOuvrirSemaine = {},
            onOuvrirIntervention = {},
            onModifierIntervention = {},
            onChangerStatut = {},
        )
    }
}
