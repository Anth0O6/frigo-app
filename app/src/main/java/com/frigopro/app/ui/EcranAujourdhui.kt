package com.frigopro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.Client
import com.frigopro.app.data.Devis
import com.frigopro.app.data.EcheanceMaintenance
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutEcheance
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.IntituleSection
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.theme.FrigoProTheme
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit
import com.frigopro.app.ui.theme.StyleSection
import java.time.LocalDate
import java.time.LocalTime

/**
 * La recherche d'« Aujourd'hui » : ouverte ou non, ce qu'on a tapé, et ce
 * qu'elle trouve dans **toute** la tournée.
 *
 * Tant qu'elle est fermée, l'écran est la journée. Ouverte, il devient la liste
 * des interventions qui correspondent, groupées par date : retrouver
 * « Carrefour en mars » demandait sinon de remonter jour par jour.
 */
data class RechercheJournee(
    val ouverte: Boolean = false,
    val saisie: String = "",
    val trouvees: List<LigneTournee> = emptyList(),
    /** Ce qu'elle trouve hors des interventions : voir [ResultatsGlobaux]. */
    val ailleurs: ResultatsGlobaux = ResultatsGlobaux(),
)

/**
 * Les gestes de la recherche, en un objet : voir [RechercheJournee]. Chaque
 * objet trouvé s'ouvre dans son onglet — c'est la coquille qui bascule.
 */
data class ActionsRecherche(
    val onOuvrir: () -> Unit = {},
    val onSaisie: (String) -> Unit = {},
    val onFermer: () -> Unit = {},
    val onOuvrirClient: (Client) -> Unit = {},
    val onOuvrirMachine: (Equipement) -> Unit = {},
    val onOuvrirFacture: (Facture) -> Unit = {},
    val onOuvrirDevis: (Devis) -> Unit = {},
    val onOuvrirArticle: (ArticleEnStock) -> Unit = {},
)

/**
 * Ce qui attend, réuni sous « À traiter » : les factures à relancer, les visites
 * dues, les articles à racheter, les échéances F-Gas et les réglages qui
 * manquent.
 *
 * Aucune de ces listes n'est stockée — un impayé, un manque, une visite due et
 * une échéance se déduisent tous d'un seuil et d'une date, et un booléen en base
 * serait faux le lendemain. Toutes sont **plafonnées à trois lignes** à
 * l'affichage : l'accueil alerte, les onglets tiennent le détail.
 */
data class ATraiter(
    /**
     * Les factures échues qu'il est temps de relancer. Une facture de six
     * semaines est une réponse à « et maintenant ? » au même titre qu'une
     * intervention en cours.
     */
    val impayees: List<FactureChiffree> = emptyList(),
    /**
     * Les visites préventives en retard ou dues dans leur préavis : le seul
     * travail du métier que **personne ne vient demander**. Une ronde du matin
     * oubliée ne se manifeste jamais — elle se découvre à la fin du contrat,
     * quand le client compte les visites.
     */
    val visites: List<EcheanceMaintenance> = emptyList(),
    /**
     * Ce qu'il faut racheter, atelier et camion confondus : un manque découvert
     * sur un toit coûte la journée.
     */
    val manquants: List<ArticleEnStock> = emptyList(),
    val echeances: List<EcheanceFgas> = emptyList(),
    val reglages: List<ReglageManquant> = emptyList(),
) {

    val vide: Boolean
        get() = impayees.isEmpty() && visites.isEmpty() && manquants.isEmpty() &&
            echeances.isEmpty() && reglages.isEmpty()
}

/**
 * Où mène chaque ligne de « À traiter ».
 *
 * **Chaque ligne ouvre l'objet lui-même**, et c'est ce que ce bloc a corrigé :
 * une visite due menait à la liste du préventif, où il fallait la retrouver ; un
 * article manquant menait au magasin, où il fallait le chercher ; une échéance
 * F-Gas ne menait nulle part. Une alerte qui demande encore de chercher n'est
 * qu'à moitié une réponse à « et maintenant ? ». Les « N autres » mènent, eux, à
 * la liste entière, là où elle se tient.
 */
data class ActionsATraiter(
    val onOuvrirFacture: (Facture) -> Unit = {},
    val onVoirFactures: () -> Unit = {},
    val onOuvrirVisite: (EcheanceMaintenance) -> Unit = {},
    val onVoirEntretien: () -> Unit = {},
    val onOuvrirArticle: (ArticleEnStock) -> Unit = {},
    val onVoirMagasin: () -> Unit = {},
    val onOuvrirMachine: (Equipement) -> Unit = {},
    val onReglage: (PageReglages) -> Unit = {},
)

/**
 * « Aujourd'hui » : la journée, et ce qui attend.
 *
 * L'écran réunit ce qui était deux vues de la Tournée, **Maintenant** et
 * **Jour**, et c'était le même défaut que l'ancien couple accueil / planning :
 * la « suite de la journée » de l'une était la liste de l'autre, si bien que les
 * mêmes interventions paraissaient sous deux pastilles sans que rien ne dise
 * laquelle regarder. Il ne reste qu'un écran, qui répond dans l'ordre aux
 * questions qu'on se pose en sortant le téléphone : chez qui je suis — la carte
 * du haut, avec « Démarrer » —, ce qui suit — la journée —, et ce qui m'attend
 * par ailleurs — « À traiter ».
 *
 * Il **navigue dans le temps**, à la différence de l'ancien accueil : la date et
 * ses flèches sont en tête, et c'était le prix de la fusion — la vue Jour les
 * portait. La carte du haut ne paraît que **sur aujourd'hui** : « Démarrer » sur
 * une intervention de jeudi prochain lancerait un chronomètre qui n'a rien à
 * mesurer. « À traiter », lui, reste en bas quel que soit le jour regardé, parce
 * qu'une facture échue ne l'est pas moins un autre jour.
 *
 * Pas de bascule : la frise est une **préférence d'affichage** de la journée et
 * non une destination, et elle se choisit par un bouton à côté du titre de la
 * journée. La semaine et l'entretien sont l'onglet Planning.
 */
@Composable
fun EcranAujourdhui(
    etat: EtatAujourdhui,
    jour: LocalDate,
    lignes: List<LigneTournee>,
    onJourPrecedent: () -> Unit,
    onJourSuivant: () -> Unit,
    onJourChoisi: (LocalDate) -> Unit,
    onNouvelleIntervention: () -> Unit,
    onOuvrirIntervention: (Intervention) -> Unit,
    onModifierIntervention: (Intervention) -> Unit,
    /**
     * La pastille d'une ligne, et « Terminer » sur une carte en cours. Ce n'est
     * plus un cycle de statuts : voir [GesteStatut] pour ce que chacun fait.
     */
    onChangerStatut: (Intervention) -> Unit,
    /**
     * Démarrer le travail en un appui, depuis la carte du haut : le chronomètre
     * part, l'intervention passe en cours, et sa fiche s'ouvre. Voir
     * `InterventionViewModel.onDemarrer`.
     */
    onDemarrer: (Intervention) -> Unit,
    onItineraire: (Client) -> Unit,
    /** La journée en frise horaire plutôt qu'en liste. */
    frise: Boolean,
    onBasculerVue: () -> Unit,
    /** La roue crantée : les Réglages, en plein écran, par la coquille. */
    onOuvrirReglages: () -> Unit,
    modifier: Modifier = Modifier,
    recherche: RechercheJournee = RechercheJournee(),
    actionsRecherche: ActionsRecherche = ActionsRecherche(),
    aTraiter: ATraiter = ATraiter(),
    actionsATraiter: ActionsATraiter = ActionsATraiter(),
) {
    var selecteurOuvert by remember { mutableStateOf(false) }
    val aujourdhui = LocalDate.now()
    val estAujourdhui = jour == aujourdhui
    // La carte du haut n'existe que sur aujourd'hui, et ce qu'elle montre sort
    // de la liste : la même intervention deux fois l'une sous l'autre est
    // exactement le doublon que la fusion a supprimé.
    val enAvant = if (estAujourdhui) AujourdhuiViewModel.enAvantDe(lignes) else null
    val reste = lignes.filter { it !== enAvant }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La coquille pose déjà les marges des barres système.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // Le bouton + qui manquait à « Maintenant » : on y était le plus
            // souvent, et c'est là qu'arrive l'appel d'un client à caser.
            if (!recherche.ouverte) {
                FloatingActionButton(
                    onClick = onNouvelleIntervention,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Ajouter une intervention")
                }
            }
        },
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            BarreAujourdhui(
                technicien = etat.technicien,
                recherche = recherche,
                actions = actionsRecherche,
                onOuvrirReglages = onOuvrirReglages,
            )
            if (recherche.ouverte) {
                ResultatsRecherche(
                    recherche = recherche,
                    actions = actionsRecherche,
                    onOuvrir = onOuvrirIntervention,
                    onModifier = onModifierIntervention,
                    onChangerStatut = onChangerStatut,
                )
                return@Column
            }

            EnTeteJour(
                jour = jour,
                onJourPrecedent = onJourPrecedent,
                onJourSuivant = onJourSuivant,
                onOuvrirSelecteur = { selecteurOuvert = true },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Pas de marge de côté sur la colonne : les pastilles de la
                // semaine et le bandeau posent la leur, et chaque autre élément
                // la reçoit de [aLaMarge].
                contentPadding = PaddingValues(top = 0.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "semaine") { PastillesSemaine(jour = jour, onJourChoisi = onJourChoisi) }
                item(key = "bandeau") { BandeauJournee(lignes = lignes) }

                if (estAujourdhui) {
                    item(key = "en-avant") {
                        Box(modifier = aLaMarge) {
                            if (enAvant != null) {
                                CarteEnAvant(
                                    ligne = enAvant,
                                    onOuvrir = { onOuvrirIntervention(enAvant.intervention) },
                                    onDemarrer = { onDemarrer(enAvant.intervention) },
                                    onItineraire = { enAvant.client?.let(onItineraire) },
                                )
                            } else {
                                CarteJourneeLibre(journeeVide = lignes.isEmpty())
                            }
                        }
                    }
                }

                if (lignes.isNotEmpty()) {
                    item(key = "titre-journee") {
                        TitreJournee(
                            texte = if (enAvant != null && !frise) "Suite de la journée" else "La journée",
                            frise = frise,
                            onBasculerVue = onBasculerVue,
                        )
                    }
                }

                when {
                    // Hors d'aujourd'hui, une journée vide se dit ici ; sur
                    // aujourd'hui, la carte du haut le dit déjà.
                    lignes.isEmpty() && !estAujourdhui -> item(key = "vide") { JourneeVide() }

                    lignes.isEmpty() -> Unit

                    frise -> item(key = "frise") {
                        Box(modifier = aLaMarge) {
                            FriseHoraire(
                                lignes = lignes,
                                // Le trait de l'heure courante n'a de sens que
                                // sur aujourd'hui : ailleurs il désignerait un
                                // instant qui n'appartient pas à la journée.
                                maintenant = if (estAujourdhui) LocalTime.now() else null,
                                onOuvrir = onOuvrirIntervention,
                            )
                        }
                    }

                    reste.isEmpty() -> item(key = "rien-d-autre") {
                        Text(
                            text = "Rien d'autre de prévu aujourd'hui.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = aLaMarge,
                        )
                    }

                    else -> items(items = reste, key = { it.intervention.id }) { ligne ->
                        Box(modifier = aLaMarge) {
                            if (ligne.intervention.statut == StatutIntervention.EN_COURS) {
                                CarteEnCours(
                                    ligne = ligne,
                                    onOuvrir = { onOuvrirIntervention(ligne.intervention) },
                                    onModifier = { onModifierIntervention(ligne.intervention) },
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

                blocATraiter(contenu = aTraiter, actions = actionsATraiter, aujourdhui = aujourdhui)
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

/** La marge de côté d'un élément de la colonne d'« Aujourd'hui ». */
private val aLaMarge: Modifier = Modifier.padding(horizontal = MargeEcran)

/**
 * La rangée du haut : qui est au volant, la loupe et la roue crantée.
 *
 * Elle est **séparée de la date**, et c'est une leçon déjà payée : la barre de la
 * vue Jour avait porté quatre boutons carrés sur la rangée du titre, et en mode
 * gants « Aujourd'hui » s'affichait « Auj… ». Deux boutons par rangée, et chaque
 * titre se lit en entier — la date avec ses deux flèches dessous, la salutation
 * avec la loupe et les Réglages ici.
 *
 * La **roue crantée** est la seule entrée des Réglages : ils ont quitté la barre
 * du bas, qui avait besoin de la place pour le Planning, et c'est l'endroit où
 * les applications les rangent — on ne les ouvre que rarement, mais on sait où
 * les chercher.
 */
@Composable
private fun BarreAujourdhui(
    technicien: String,
    recherche: RechercheJournee,
    actions: ActionsRecherche,
    onOuvrirReglages: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    // La loupe ouvre un champ où l'on écrit aussitôt : un champ qu'il faut
    // encore toucher est un geste de trop. `runCatching` parce qu'un champ pas
    // encore posé refuserait le focus — on n'en perd alors que la commodité.
    LaunchedEffect(recherche.ouverte) {
        if (recherche.ouverte) runCatching { focus.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MargeEcran, end = MargeEcran, top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (recherche.ouverte) {
            ChampRecherche(
                valeur = recherche.saisie,
                onValeur = actions.onSaisie,
                indication = "Client, ville, machine, numéro…",
                modifier = Modifier.weight(1f),
                focus = focus,
            )
            BoutonCarre(
                icone = Icons.Filled.Close,
                description = "Fermer la recherche",
                onClick = actions.onFermer,
            )
        } else {
            Text(
                text = if (technicien.isBlank()) "Bonjour" else "Bonjour $technicien",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BoutonCarre(
                icone = Icons.Filled.Search,
                description = "Chercher une intervention",
                onClick = actions.onOuvrir,
            )
            BoutonCarre(
                icone = Icons.Filled.Settings,
                description = "Réglages",
                onClick = onOuvrirReglages,
            )
        }
    }
}

/**
 * La date affichée, et les deux flèches qui en changent.
 *
 * Le titre ouvre le sélecteur de date, et **le dit** : une icône de calendrier le
 * signale. Un texte cliquable dont rien n'annonce qu'il l'est n'est pas une
 * fonctionnalité — c'est la même règle qui fait doubler tout appui long par un
 * bouton visible.
 */
@Composable
private fun EnTeteJour(
    jour: LocalDate,
    onJourPrecedent: () -> Unit,
    onJourSuivant: () -> Unit,
    onOuvrirSelecteur: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MargeEcran, end = MargeEcran, bottom = 14.dp),
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // `headlineMedium` et non `displaySmall` : « Aujourd'hui »
                    // ne tient pas en 36 sp à côté de deux boutons et d'une
                    // icône sur un téléphone étroit, et un titre tronqué ne se
                    // rattrape par aucun réglage.
                    text = titreJour(jour),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.EditCalendar,
                    contentDescription = "Choisir une date",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
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
    }
}

/**
 * Le titre de la journée, et le choix frise / liste à côté.
 *
 * Frise ou liste est une **préférence d'affichage**, pas une navigation : elle
 * est donc à côté du titre et non dans une bascule — une pastille de plus aurait
 * laissé croire à une destination de plus.
 */
@Composable
private fun TitreJournee(texte: String, frise: Boolean, onBasculerVue: () -> Unit) {
    Row(
        modifier = aLaMarge.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IntituleSection(texte = texte, modifier = Modifier.weight(1f))
        BoutonCarre(
            icone = if (frise) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.Schedule,
            description = if (frise) "Voir en liste" else "Voir en frise",
            onClick = onBasculerVue,
        )
    }
}

/** Aujourd'hui sans intervention à mettre en avant : vide, ou déjà faite. */
@Composable
private fun CarteJourneeLibre(journeeVide: Boolean) {
    Carte {
        Text(
            text = if (journeeVide) "Aucune intervention aujourd'hui" else "Journée terminée",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (journeeVide) {
                "Le planning est libre. De quoi rattraper une échéance, ou souffler."
            } else {
                "Tout ce qui était prévu est fait."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Ce que la recherche trouve, **toutes dates confondues**.
 *
 * La semaine, la date et la frise disparaissent pendant ce temps : les
 * résultats viennent de toutes les dates, et une pastille « mercredi » retenue
 * au-dessus d'une intervention de mars ferait croire que l'une désigne l'autre.
 */
@Composable
private fun ResultatsRecherche(
    recherche: RechercheJournee,
    actions: ActionsRecherche,
    onOuvrir: (Intervention) -> Unit,
    onModifier: (Intervention) -> Unit,
    onChangerStatut: (Intervention) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MargeEcran,
            end = MargeEcran,
            top = 4.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            recherche.saisie.isBlank() -> item {
                Encart(
                    texte = "Un seul champ pour tout : un client, une ville, une machine, une " +
                        "zone, un numéro de facture ou de devis, un article — toutes dates " +
                        "confondues.",
                )
            }

            recherche.trouvees.isEmpty() && recherche.ailleurs.vide -> item {
                Encart(
                    texte = "Rien ne correspond à « ${recherche.saisie.trim()} ».",
                )
            }
        }
        blocAilleurs(recherche.ailleurs, actions)
        if (recherche.trouvees.isNotEmpty() && !recherche.ailleurs.vide) {
            item(key = "titre-interventions") { IntituleSection(texte = "Interventions") }
        }
        // `groupBy` garde l'ordre d'arrivée, et la liste vient déjà
        // antichronologique : les dates se suivent donc de la plus récente à la
        // plus ancienne sans être triées deux fois.
        recherche.trouvees.groupBy { it.intervention.date }.forEach { (date, duJour) ->
            item(key = "date-$date") {
                IntituleSection(texte = libelleDateAvecAnnee(date))
            }
            items(items = duJour, key = { it.intervention.id }) { ligne ->
                LigneCompacte(
                    ligne = ligne,
                    onOuvrir = { onOuvrir(ligne.intervention) },
                    onModifier = { onModifier(ligne.intervention) },
                    onChangerStatut = { onChangerStatut(ligne.intervention) },
                )
            }
        }
    }
}

/**
 * « À traiter », en fin de colonne.
 *
 * L'ordre suit l'urgence et non la gravité : les relances d'abord, parce qu'un
 * impayé fait courir des pénalités chaque jour ; les visites ensuite, dont le
 * préavis va de zéro à trente jours ; les achats, qui décident de ce qu'on charge
 * ce soir ; les échéances F-Gas, une obligation légale mais à des mois ; et les
 * réglages en dernier, qui ne bougent pas d'un jour à l'autre.
 */
private fun LazyListScope.blocATraiter(
    contenu: ATraiter,
    actions: ActionsATraiter,
    aujourdhui: LocalDate,
) {
    item(key = "a-traiter") {
        IntituleSection(
            texte = "À traiter",
            modifier = aLaMarge.padding(top = 12.dp),
        )
    }
    if (contenu.vide) {
        item(key = "a-traiter-vide") {
            Encart(
                texte = "Rien qui attende : aucune facture à relancer, aucune visite due, " +
                    "rien à racheter, aucune échéance F-Gas en vue.",
                modifier = aLaMarge,
            )
        }
        return
    }

    if (contenu.impayees.isNotEmpty()) {
        item(key = "a-relancer") {
            Section(intitule = "À relancer", espacement = 8.dp, modifier = aLaMarge) {
                contenu.impayees.take(IMPAYEES_MONTREES).forEach { chiffree ->
                    LigneImpayee(
                        chiffree = chiffree,
                        onClick = { actions.onOuvrirFacture(chiffree.facture) },
                    )
                }
                val reste = contenu.impayees.size - IMPAYEES_MONTREES
                if (reste > 0) {
                    BoutonContour(
                        texte = "$reste autre${pluriel(reste)} à relancer",
                        onClick = actions.onVoirFactures,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (contenu.visites.isNotEmpty()) {
        item(key = "visites") {
            Section(intitule = "Visites à faire", espacement = 8.dp, modifier = aLaMarge) {
                contenu.visites.take(PREVENTIF_MONTRE).forEach { echeance ->
                    LignePreventif(
                        echeance = echeance,
                        aujourdhui = aujourdhui,
                        onClick = { actions.onOuvrirVisite(echeance) },
                    )
                }
                // Sur un site de plusieurs centaines de machines une ronde
                // journalière en pose des dizaines : le reste se compte, et
                // l'Entretien du Planning tient la liste.
                val reste = contenu.visites.size - PREVENTIF_MONTRE
                if (reste > 0) {
                    BoutonContour(
                        texte = "$reste autre${pluriel(reste)} à l'entretien",
                        onClick = actions.onVoirEntretien,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (contenu.manquants.isNotEmpty()) {
        item(key = "a-racheter") {
            Section(intitule = "À racheter", espacement = 8.dp, modifier = aLaMarge) {
                contenu.manquants.take(MANQUANTS_MONTRES).forEach { entree ->
                    LigneManquant(entree = entree, onClick = { actions.onOuvrirArticle(entree) })
                }
                val reste = contenu.manquants.size - MANQUANTS_MONTRES
                if (reste > 0) {
                    BoutonContour(
                        texte = "$reste autre${pluriel(reste)} au magasin",
                        onClick = actions.onVoirMagasin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (contenu.echeances.isNotEmpty()) {
        item(key = "fgas") {
            Section(intitule = "Échéances F-Gas", espacement = 8.dp, modifier = aLaMarge) {
                contenu.echeances.forEach { echeance ->
                    LigneEcheance(
                        echeance = echeance,
                        onClick = { actions.onOuvrirMachine(echeance.equipement) },
                    )
                }
            }
        }
    }

    if (contenu.reglages.isNotEmpty()) {
        item(key = "reglages") {
            Box(modifier = aLaMarge) {
                CarteReglagesManquants(manquants = contenu.reglages, onReglage = actions.onReglage)
            }
        }
    }
}

/**
 * Ce qu'il reste à régler, et ce que cela empêche.
 *
 * Elle se lit sous « À traiter » et non dans les Réglages, et c'est tout l'intérêt :
 * un réglage qui manque ne se découvre pas en ouvrant l'écran des réglages — on
 * n'y va justement pas — mais en voyant un devis sortir à 0 €. L'accueil répond
 * à « et maintenant ? », et « vos factures partent sans raison sociale » est une
 * réponse à cette question, au même titre qu'un impayé de six semaines.
 *
 * Chaque ligne mène à la page qui la règle. Elle disparaît d'elle-même quand tout
 * est posé : rien à fermer, rien à faire taire — un bandeau qu'on écarte d'un
 * geste serait écarté une fois pour toutes, et la question reviendrait le jour
 * du premier devis.
 */
@Composable
private fun CarteReglagesManquants(
    manquants: List<ReglageManquant>,
    onReglage: (PageReglages) -> Unit,
) {
    Carte(liseré = LocalStatuts.current.aValider) {
        Text(
            text = if (manquants.size == 1) "Un réglage à poser" else "${manquants.size} réglages à poser",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "L'application ne devine aucun tarif : une valeur inventée partirait " +
                "chez un vrai client sans que personne ne l'ait relue. Voici ce qu'elle " +
                "attend, et ce que chacun débloque.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        manquants.forEach { manquant ->
            Carte(relief = true, onClick = { onReglage(manquant.page) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = manquant.intitule,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = manquant.consequence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

/**
 * La carte du haut : l'intervention en cours, ou la prochaine.
 *
 * C'est la seule carte de l'application à porter deux boutons pleins. Elle les
 * mérite : ouvrir la fiche et lancer l'itinéraire sont les deux gestes du
 * matin, et les faire chercher dans une liste coûterait plus cher que la place
 * qu'ils prennent.
 */
@Composable
private fun CarteEnAvant(
    ligne: LigneTournee,
    onOuvrir: () -> Unit,
    onDemarrer: () -> Unit,
    onItineraire: () -> Unit,
) {
    val intervention = ligne.intervention
    val couleur = couleurStatut(intervention)
    Carte(relief = true, liseré = couleur, onClick = onOuvrir) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Puce(
                texte = libelleStatut(intervention),
                couleur = couleur,
                fond = couleur.copy(alpha = 0.16f),
            )
            Text(
                text = creneau(intervention),
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = intervention.client,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sousTitre = listOf(intervention.typeLibelle, intervention.equipementNom)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (sousTitre.isNotEmpty()) {
            Text(
                text = sousTitre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val adresse = ligne.client?.adresseComplete.orEmpty().ifBlank { intervention.ville }
        if (adresse.isNotBlank()) {
            Text(
                text = adresse,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Le geste le plus fréquent de la journée, en premier et en plein : on
        // se gare, on sort le téléphone, on démarre. Il demandait trois appuis —
        // ouvrir la fiche, trouver le chronomètre dans le volet des relevés, le
        // lancer — dont le premier n'apprenait rien à personne.
        //
        // Il disparaît dès que le temps tourne, et « Ouvrir la fiche » reprend
        // alors la place : un bouton « Démarrer » sur une intervention en cours
        // n'aurait su que la mettre en pause, ce que personne ne vient faire ici.
        val aDemarrer = !ligne.intervention.chrono.enMarche &&
            ligne.intervention.statut != StatutIntervention.TERMINEE
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (aDemarrer) {
                BoutonPlein(
                    texte = "Démarrer",
                    onClick = onDemarrer,
                    modifier = Modifier.weight(1.4f),
                )
            } else {
                BoutonPlein(
                    texte = "Ouvrir la fiche",
                    onClick = onOuvrir,
                    modifier = Modifier.weight(1.4f),
                )
            }
            if (ligne.localisable) {
                BoutonContour(
                    texte = "Itinéraire",
                    onClick = onItineraire,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // La fiche reste à un appui quand « Démarrer » occupe le bouton plein :
        // la carte entière est cliquable, et ce second chemin le dit.
        if (aDemarrer) {
            BoutonContour(
                texte = "Ouvrir la fiche sans démarrer",
                onClick = onOuvrir,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Une facture échue.
 *
 * Elle dit **depuis combien de jours**, et c'est le chiffre qui décide : une
 * facture de trois jours s'oublie, une de six semaines se réclame. Toucher la
 * ligne ouvre la facture, d'où elle se renvoie — et l'envoi vaut relance.
 */
@Composable
private fun LigneImpayee(chiffree: FactureChiffree, onClick: () -> Unit) {
    val facture = chiffree.facture
    val retard = facture.joursDeRetard(LocalDate.now()) ?: 0L

    Carte(onClick = onClick, relief = true, liseré = LocalStatuts.current.urgence) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = facture.clientNom.ifBlank { "Client non renseigné" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(
                        facture.numero,
                        "échue depuis $retard jour${if (retard > 1) "s" else ""}",
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalStatuts.current.urgence,
                )
            }
            // Ce qui reste dû et non le total : une facture qu'un avoir a corrigée
            // en partie se réclame pour son reste.
            Text(
                text = Nombres.enEuros(chiffree.resteTtc),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/**
 * Une visite due, sur l'accueil.
 *
 * Toucher la ligne ouvre **sa feuille de visite**, sur place : la visite se
 * consigne d'ici, sans passer par la liste de l'Entretien où il fallait la
 * retrouver.
 *
 * Elle nomme la machine **et son client**, là où la ligne de l'Entretien peut se
 * contenter de la machine : on y est déjà dans le contexte d'un site, tandis que
 * l'accueil mélange tout ce qui attend. Le liseré prend la couleur du statut,
 * comme l'article en manque prend celle de l'urgence — l'ambre d'un préavis et le
 * rouge d'un retard ne disent pas la même chose, et c'est le seul signal qui les
 * sépare d'un coup d'œil.
 */
@Composable
private fun LignePreventif(
    echeance: EcheanceMaintenance,
    aujourdhui: LocalDate,
    onClick: () -> Unit,
) {
    val statuts = LocalStatuts.current
    val teinte = if (echeance.statut(aujourdhui) == StatutEcheance.EN_RETARD) {
        statuts.urgence
    } else {
        statuts.aValider
    }

    Carte(onClick = onClick, contour = true, forme = MaterialTheme.shapes.medium) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 30.dp)
                    .background(teinte, MaterialTheme.shapes.extraSmall),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = echeance.equipement.nom,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // La zone en premier : sur un site unique le nom du client se
                    // répète d'une ligne à l'autre et n'apprend rien, alors que la
                    // zone dit où aller. Trois lignes au plus, donc la place est
                    // comptée et l'ordre décide de ce qui survit à l'ellipse.
                    text = listOf(
                        echeance.equipement.zone,
                        echeance.clientNom,
                        echeance.gamme.libelle,
                    )
                        .filter { it.isNotBlank() }
                        .joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = libelleEcheance(echeance, aujourdhui),
                style = MaterialTheme.typography.bodySmall,
                color = teinte,
            )
        }
    }
}

/**
 * Un article sous son seuil.
 *
 * Il dit **où** il manque, et c'est tout l'intérêt de tenir deux stocks : « 1 u
 * au camion, mini 3 » n'appelle pas le même geste que le même manque à
 * l'atelier — l'un se recharge le soir, l'autre se commande. Toucher la ligne
 * ouvre **la fiche de l'article** au magasin, où le stock se corrige et le
 * transfert se pose.
 */
@Composable
private fun LigneManquant(entree: ArticleEnStock, onClick: () -> Unit) {
    val urgence = LocalStatuts.current.urgence
    Carte(onClick = onClick, contour = true, forme = MaterialTheme.shapes.medium) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 30.dp)
                    .background(urgence, MaterialTheme.shapes.extraSmall),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entree.article.designation,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Le même vocabulaire que le magasin : « 1 u · mini 3 ».
                    // Le seuil donne son sens au chiffre, et le chiffre seul ne
                    // dirait pas de combien on est court.
                    text = entree.aReapprovisionner.joinToString("  ·  ") { lieu ->
                        val stock = entree.stocks[lieu]
                        buildString {
                            append(lieu.libelle)
                            append(" : ")
                            append(Nombres.enTexte(entree.quantite(lieu)))
                            append(' ')
                            append(entree.article.unite)
                            if (stock != null) append(" · mini ${Nombres.enTexte(stock.minimum)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = urgence,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entree.article.fournisseurNom.isNotBlank()) {
                Text(
                    text = entree.article.fournisseurNom,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Une échéance de contrôle d'étanchéité.
 *
 * Le retard est dit en rouge et en toutes lettres : c'est le seul élément de
 * l'accueil qui expose à une sanction, et une date seule ne se compare pas
 * d'un coup d'œil à celle du jour.
 *
 * Toucher la ligne ouvre **la fiche de la machine**, dans l'onglet Clients :
 * c'est là que se lisent le fluide, la charge, le dernier contrôle et ce qui a
 * doublé la périodicité, et là qu'on note le contrôle une fois fait. La ligne ne
 * menait nulle part, et une échéance qu'on ne peut que regarder s'oublie.
 */
@Composable
private fun LigneEcheance(echeance: EcheanceFgas, onClick: () -> Unit) {
    val couleur = if (echeance.etat.enRetard) {
        LocalStatuts.current.urgence
    } else {
        LocalStatuts.current.aValider
    }
    Carte(onClick = onClick, contour = true, forme = MaterialTheme.shapes.medium) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = couleur.copy(alpha = 0.16f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${echeance.echeance.dayOfMonth}",
                        style = StyleChiffrePetit,
                        color = couleur,
                    )
                    Text(
                        text = moisCourt(echeance.echeance),
                        style = MaterialTheme.typography.labelSmall,
                        color = couleur,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = echeance.clientNom.ifBlank { echeance.equipement.nom },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(if (echeance.etat.enRetard) "En retard" else "Contrôle d'étanchéité")
                        append(" · ")
                        append(echeance.equipement.nom)
                        if (echeance.equipement.fluide.isNotBlank()) {
                            append(" · ")
                            append(echeance.equipement.fluide)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (echeance.etat.enRetard) couleur else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Combien d'articles manquants l'accueil montre.
 *
 * Trois, comme les échéances, et pour la même raison : le but est d'alerter, pas
 * de tenir l'inventaire. Le magasin porte le détail, et le compte du reste y
 * mène.
 */
private const val MANQUANTS_MONTRES = 3

/** Même plafond que les manquants, et pour la même raison. */
private const val IMPAYEES_MONTREES = 3

/** Même plafond que les manquants, et pour la même raison. */
private const val PREVENTIF_MONTRE = 3

/** « 08:00 → 09:30 », d'après l'heure de début et la durée prévue. */
private fun creneau(intervention: Intervention): String {
    val debut = intervention.heure
    val fin = debut.plusMinutes(intervention.dureeMin.toLong())
    return "${debut.format(FORMAT_HEURE)} → ${fin.format(FORMAT_HEURE)}"
}

@Preview(showBackground = true)
@Composable
private fun ApercuAujourdhui() {
    val client = Client(nom = "Boulangerie Martin", ville = "Lyon 2e", adresse = "12 rue des Halles")
    val intervention = Intervention(
        id = "1",
        date = LocalDate.now(),
        heure = LocalTime.of(8, 0),
        client = client.nom,
        ville = client.ville,
        clientId = client.id,
        typeLibelle = "Dépannage chambre froide",
        statut = StatutIntervention.EN_COURS,
        urgente = true,
        dureeMin = 90,
    )
    val suivante = Intervention(
        id = "2",
        date = LocalDate.now(),
        heure = LocalTime.of(10, 30),
        client = "SCI Le Vallon",
        ville = "Ivry",
        typeLibelle = "Entretien PAC",
    )
    FrigoProTheme {
        EcranAujourdhui(
            etat = EtatAujourdhui(technicien = "Karim"),
            jour = LocalDate.now(),
            lignes = listOf(LigneTournee(intervention, client), LigneTournee(suivante, null)),
            onJourPrecedent = {},
            onJourSuivant = {},
            onJourChoisi = {},
            onNouvelleIntervention = {},
            onOuvrirIntervention = {},
            onModifierIntervention = {},
            onChangerStatut = {},
            onDemarrer = {},
            onItineraire = {},
            frise = false,
            onBasculerVue = {},
            onOuvrirReglages = {},
            aTraiter = ATraiter(reglages = listOf(ReglageManquant.TAUX_HORAIRE)),
        )
    }
}

/**
 * Les clients, machines, factures, devis et articles trouvés, **avant** les
 * interventions : on cherche plus souvent une fiche qu'un passage, et la liste
 * des interventions peut être longue. Chaque famille dit son compte complet
 * quand le plafond l'a coupée — l'onglet tient la suite.
 */
private fun LazyListScope.blocAilleurs(resultats: ResultatsGlobaux, actions: ActionsRecherche) {
    fun titre(famille: FamilleTrouvee, affiches: Int) {
        val total = resultats.totaux[famille] ?: affiches
        item(key = "titre-${famille.name}") {
            IntituleSection(
                texte = if (total > affiches) "${famille.libelle} · $affiches sur $total" else famille.libelle,
            )
        }
    }
    if (resultats.clients.isNotEmpty()) {
        titre(FamilleTrouvee.CLIENTS, resultats.clients.size)
        items(items = resultats.clients, key = { "cl-${it.id}" }) { client ->
            LigneTrouvee(client.nom, client.adresseComplete) { actions.onOuvrirClient(client) }
        }
    }
    if (resultats.machines.isNotEmpty()) {
        titre(FamilleTrouvee.MACHINES, resultats.machines.size)
        items(items = resultats.machines, key = { "eq-${it.equipement.id}" }) { trouvee ->
            LigneTrouvee(
                trouvee.equipement.nom,
                listOf(trouvee.clientNom, trouvee.equipement.zone).filter { it.isNotBlank() }.joinToString(" · "),
            ) { actions.onOuvrirMachine(trouvee.equipement) }
        }
    }
    if (resultats.factures.isNotEmpty()) {
        titre(FamilleTrouvee.FACTURES, resultats.factures.size)
        items(items = resultats.factures, key = { "fa-${it.facture.id}" }) { chiffree ->
            val facture = chiffree.facture
            LigneTrouvee(
                facture.numero.ifBlank { "Brouillon" },
                "${facture.clientNom} · ${facture.statut.libelle}",
            ) { actions.onOuvrirFacture(facture) }
        }
    }
    if (resultats.devis.isNotEmpty()) {
        titre(FamilleTrouvee.DEVIS, resultats.devis.size)
        items(items = resultats.devis, key = { "de-${it.devis.id}" }) { chiffre ->
            val devis = chiffre.devis
            LigneTrouvee(
                devis.numero,
                listOf(devis.clientNom, devis.objet).filter { it.isNotBlank() }.joinToString(" · "),
            ) { actions.onOuvrirDevis(devis) }
        }
    }
    if (resultats.articles.isNotEmpty()) {
        titre(FamilleTrouvee.ARTICLES, resultats.articles.size)
        items(items = resultats.articles, key = { "ar-${it.article.id}" }) { entree ->
            LigneTrouvee(
                entree.article.designation,
                listOf(entree.article.reference, entree.article.fournisseurNom)
                    .filter { it.isNotBlank() }.joinToString(" · "),
            ) { actions.onOuvrirArticle(entree) }
        }
    }
}

/** Une ligne de résultat : ce qu'on cherchait, et de quoi la reconnaître. */
@Composable
private fun LigneTrouvee(titre: String, detail: String, onClick: () -> Unit) {
    Carte(onClick = onClick) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = titre, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
