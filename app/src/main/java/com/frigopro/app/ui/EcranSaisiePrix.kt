package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Article
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.ui.composants.BarreActions
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonDiscret
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampChiffre
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.LocalStatuts

/**
 * Remplir les prix du magasin d'un trait, un tarif fournisseur à la main.
 *
 * ## Une ligne par article, et tout ce qu'un tarif apporte dessus
 *
 * La référence, le prix d'achat et le prix de vente sont **en place**, sans
 * boîte de dialogue : on recopie un tarif ligne après ligne, et chaque écran
 * intermédiaire serait un geste de plus multiplié par trente-huit. Le
 * fournisseur, lui, se donne **en lot** — on coche, on choisit —, parce qu'un
 * tarif vient d'un seul fournisseur et que le choisir trente-huit fois serait
 * trente-huit occasions de se tromper.
 *
 * ## Ce qui attend un prix passe devant
 *
 * Les articles sans prix d'achat remontent en tête, et une pastille ne montre
 * qu'eux : c'est la liste qu'on vient remplir. Les deux se jugent sur la base et
 * non sur la saisie, pour qu'une ligne ne saute pas de sous le doigt au premier
 * chiffre (voir [FiltrePrix]).
 *
 * ## Rien ne s'écrit avant « Enregistrer », et rien ne se perd sans le dire
 *
 * Le retour système, avec des prix non enregistrés, **demande** : enregistrer,
 * abandonner, ou reprendre. Ni l'enregistrement implicite — il aurait écrit un
 * tarif à moitié relu, et c'est ce tarif qui part ensuite sur un devis — ni
 * l'abandon silencieux, qui perdrait une demi-heure de saisie sur un geste qu'on
 * fait souvent par réflexe. La question n'est posée que s'il y a quelque chose à
 * perdre : une saisie remise à l'identique ferme sans rien demander.
 */
@Composable
fun EcranSaisiePrix(
    etat: EtatSaisiePrix,
    fournisseurs: List<Fournisseur>,
    lecture: LectureDevis?,
    articles: List<Article>,
    onRecherche: (String) -> Unit,
    onFiltre: (FiltrePrix) -> Unit,
    onReference: (Article, String) -> Unit,
    onPrixAchat: (Article, Double?) -> Unit,
    onPrixVente: (Article, Double?) -> Unit,
    onCocher: (String) -> Unit,
    onCocherTout: (List<Article>, Boolean) -> Unit,
    onAttribuerFournisseur: (Fournisseur?) -> Unit,
    onEnregistrer: () -> Unit,
    onEnregistrerEtFermer: () -> Unit,
    onAbandonner: () -> Unit,
    onReglerCoefficient: () -> Unit,
    onLireDevis: (String) -> Unit,
    onScanner: () -> Unit,
    onRapprocher: (LigneARapprocher, Article) -> Unit,
    onCreerArticle: (LigneARapprocher) -> Unit,
    onIgnorer: (LigneARapprocher) -> Unit,
    onFermerLecture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statuts = LocalStatuts.current
    var choixArticle by remember { mutableStateOf<LigneARapprocher?>(null) }
    // Le sélecteur du système, et non une permission de stockage : PDF reçu par
    // courriel ou photo de l'écran de l'agence, l'un comme l'autre.
    val choisirDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onLireDevis(it.toString()) }
    }
    var confirmerFermeture by remember { mutableStateOf(false) }
    var choixFournisseur by remember { mutableStateOf(false) }

    val fermer: () -> Unit = {
        if (etat.aEnregistrer > 0) {
            confirmerFermeture = true
        } else {
            onAbandonner()
        }
    }
    BackHandler(onBack = fermer)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MargeEcran, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Saisir les prix",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            BoutonContour(texte = "Fermer", onClick = fermer)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = MargeEcran,
                end = MargeEcran,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "coefficient") {
                if (etat.coefficientRegle) {
                    Text(
                        text = "Vente proposée : achat × ${Nombres.enTexte(etat.coefficient)}, " +
                            "arrondi au centime. Une vente tapée à la main fait foi ; vider " +
                            "son champ rend la main au coefficient.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // La conséquence d'abord, le réglage ensuite : « la vente se
                    // tape ligne par ligne » se juge tout de suite, et le bandeau
                    // mène à la page qui la corrige — même règle que l'accueil.
                    Encart(
                        texte = "Aucun coefficient matériel : le prix de vente ne se propose " +
                            "pas, il se tape ligne par ligne — ou reste vide.",
                        icone = Icons.Filled.Warning,
                        alerte = true,
                        onClick = onReglerCoefficient,
                        complement = "Régler le coefficient",
                    )
                }
            }

            // Le scan d'abord : le devis papier tendu au comptoir de l'agence est
            // le cas qui ne laisse pas le temps de passer par la galerie. Le
            // fichier reste pour le PDF reçu par courriel, qui se lit le mieux.
            item(key = "lire") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoutonPlein(
                        texte = "Scanner un devis",
                        onClick = onScanner,
                        modifier = Modifier.weight(1f),
                    )
                    BoutonContour(
                        texte = "Ouvrir un fichier",
                        onClick = { choisirDocument.launch(arrayOf("application/pdf", "image/*")) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (lecture != null) {
                item(key = "lecture") {
                    BilanLecture(lecture = lecture, onFermer = onFermerLecture)
                }
                items(items = lecture.aRapprocher, key = { "lu-${it.rang}" }) { ligne ->
                    LigneLue(
                        ligne = ligne,
                        onRapprocher = { onRapprocher(ligne, it) },
                        onChoisir = { choixArticle = ligne },
                        onCreer = { onCreerArticle(ligne) },
                        onIgnorer = { onIgnorer(ligne) },
                    )
                }
            }

            item(key = "recherche") {
                ChampRecherche(
                    valeur = etat.recherche,
                    onValeur = onRecherche,
                    indication = "Désignation, référence, fournisseur…",
                )
            }

            item(key = "filtres") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RangeePastilles(
                        options = FiltrePrix.entries,
                        retenue = etat.filtre,
                        libelle = {
                            when (it) {
                                FiltrePrix.TOUS -> it.libelle
                                FiltrePrix.SANS_PRIX -> "${it.libelle} · ${etat.sansPrix}"
                                FiltrePrix.A_ENREGISTRER -> "${it.libelle} · ${etat.aEnregistrer}"
                            }
                        },
                        onChoisir = onFiltre,
                        modifier = Modifier.weight(1f),
                    )
                    val visibles = etat.lignes.map { it.article }
                    val toutCoche = etat.lignes.isNotEmpty() && etat.lignes.all { it.cochee }
                    if (visibles.isNotEmpty()) {
                        BoutonDiscret(
                            texte = if (toutCoche) "Décocher" else "Tout cocher",
                            onClick = { onCocherTout(visibles, !toutCoche) },
                            // Une largeur fixe : le bouton ne porte pas de marge
                            // intérieure, et son libellé change d'un geste à l'autre.
                            modifier = Modifier.width(116.dp),
                        )
                    }
                }
            }

            if (etat.lignes.isEmpty()) {
                item(key = "vide") {
                    Encart(
                        texte = when {
                            etat.recherche.isNotBlank() ->
                                "Aucun article ne correspond à « ${etat.recherche.trim()} »."
                            etat.filtre == FiltrePrix.SANS_PRIX ->
                                "Tous les articles ont un prix d'achat."
                            etat.filtre == FiltrePrix.A_ENREGISTRER ->
                                "Rien à enregistrer pour l'instant."
                            else -> "Le magasin est vide."
                        },
                    )
                }
            }

            items(items = etat.lignes, key = { it.article.id }) { ligne ->
                LigneSaisiePrix(
                    ligne = ligne,
                    coefficientRegle = etat.coefficientRegle,
                    onCocher = { onCocher(ligne.article.id) },
                    onReference = { onReference(ligne.article, it) },
                    onPrixAchat = { onPrixAchat(ligne.article, it) },
                    onPrixVente = { onPrixVente(ligne.article, it) },
                )
            }
        }

        BarreActions {
            if (etat.cochees > 0) {
                BoutonContour(
                    texte = "Fournisseur · ${etat.cochees}",
                    onClick = { choixFournisseur = true },
                    modifier = Modifier.weight(1f),
                )
            }
            BoutonPlein(
                texte = if (etat.aEnregistrer > 0) {
                    "Enregistrer · ${etat.aEnregistrer}"
                } else {
                    "Rien à enregistrer"
                },
                onClick = onEnregistrer,
                modifier = Modifier.weight(1f),
                actif = etat.aEnregistrer > 0,
            )
        }
    }

    choixArticle?.let { ligne ->
        DialogueChoixArticle(
            ligne = ligne,
            articles = articles,
            onChoisir = { article ->
                choixArticle = null
                onRapprocher(ligne, article)
            },
            onAnnuler = { choixArticle = null },
        )
    }

    if (confirmerFermeture) {
        AlertDialog(
            onDismissRequest = { confirmerFermeture = false },
            title = { Text(text = "Prix non enregistrés") },
            text = {
                Text(
                    text = "${etat.aEnregistrer} article${if (etat.aEnregistrer > 1) "s" else ""} " +
                        "modifié${if (etat.aEnregistrer > 1) "s" else ""} depuis le dernier " +
                        "enregistrement. Rien n'est encore écrit au magasin.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmerFermeture = false
                        onEnregistrerEtFermer()
                    },
                ) {
                    Text(text = "Enregistrer")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            confirmerFermeture = false
                            onAbandonner()
                        },
                    ) {
                        Text(text = "Abandonner", color = statuts.urgence)
                    }
                    TextButton(onClick = { confirmerFermeture = false }) {
                        Text(text = "Reprendre")
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (choixFournisseur) {
        DialogueFournisseurLot(
            cochees = etat.cochees,
            fournisseurs = fournisseurs,
            onChoisir = { choisi ->
                choixFournisseur = false
                onAttribuerFournisseur(choisi)
            },
            onFermer = { choixFournisseur = false },
        )
    }
}

/**
 * Une ligne de la saisie : la case, la désignation, et les trois champs.
 *
 * La ligne qui sera écrite porte un liseré : c'est ce qui permet de relire un
 * tarif avant de l'enregistrer, et de voir d'un coup d'œil qu'une ligne touchée
 * par erreur va partir.
 */
@Composable
private fun LigneSaisiePrix(
    ligne: LignePrix,
    coefficientRegle: Boolean,
    onCocher: () -> Unit,
    onReference: (String) -> Unit,
    onPrixAchat: (Double?) -> Unit,
    onPrixVente: (Double?) -> Unit,
) {
    val statuts = LocalStatuts.current
    val article = ligne.article
    val saisie = ligne.saisie

    Carte(liseré = if (ligne.modifiee) statuts.aValider else null) {
        // Toute la rangée coche, et non la seule case : même règle que la
        // checklist d'une intervention, qu'on coche avec des gants.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalCibles.current.case)
                .clickable(onClick = onCocher),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaseACocher(cochee = ligne.cochee)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.designation,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(
                        article.unite,
                        saisie.fournisseurNom.ifBlank { "sans fournisseur" },
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        ChampTexte(
            libelle = "Référence",
            valeur = saisie.reference,
            onValeur = onReference,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChampChiffre(
                libelle = "Achat HT / ${article.unite}",
                valeur = saisie.prixAchat,
                unite = "€",
                onValeur = onPrixAchat,
                modifier = Modifier.weight(1f),
            )
            ChampChiffre(
                libelle = "Vente HT / ${article.unite}",
                // Zéro est « pas de prix » : le champ le montre vide plutôt
                // que d'afficher un « 0 » qui se lirait comme un prix.
                valeur = ligne.prixVente.takeIf { it > 0.0 },
                unite = "€",
                onValeur = onPrixVente,
                modifier = Modifier.weight(1f),
            )
        }

        val achat = saisie.prixAchat ?: 0.0
        val aPerte = achat > 0.0 && ligne.prixVente > 0.0 && ligne.prixVente < achat
        val origine = when (ligne.origineVente) {
            OrigineVente.SAISIE -> "Vente tapée à la main : aucun calcul n'y touchera."
            OrigineVente.FICHE -> "Vente de la fiche, gardée telle quelle."
            OrigineVente.COEFFICIENT -> "Vente proposée par le coefficient : elle suit l'achat."
            OrigineVente.AUCUNE ->
                if (coefficientRegle) "La vente se proposera dès qu'un prix d'achat sera saisi." else null
        }
        if (aPerte) {
            Text(
                text = "Vendu sous son prix d'achat.",
                style = MaterialTheme.typography.bodySmall,
                color = statuts.urgence,
            )
        } else if (origine != null) {
            Text(
                text = origine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** La case d'une ligne, à la taille de celle de la checklist — gants compris. */
@Composable
private fun CaseACocher(cochee: Boolean) {
    Surface(
        modifier = Modifier.size(LocalCibles.current.case),
        shape = MaterialTheme.shapes.small,
        color = if (cochee) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (cochee) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Cochée",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Le fournisseur des lignes cochées.
 *
 * « Détacher » coupe le lien et garde le nom, comme sur la fiche d'un article :
 * un article doit continuer de dire d'où il venait.
 */
@Composable
private fun DialogueFournisseurLot(
    cochees: Int,
    fournisseurs: List<Fournisseur>,
    onChoisir: (Fournisseur?) -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Fournisseur de $cochees article${if (cochees > 1) "s" else ""}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (fournisseurs.isEmpty()) {
                    Text(
                        text = "Le carnet de fournisseurs est vide. Il se remplit dans la " +
                            "vue voisine, « Fournisseurs ».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                fournisseurs.forEach { candidat ->
                    LigneChoix(
                        texte = candidat.nom,
                        retenu = false,
                        onClick = { onChoisir(candidat) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFermer) { Text(text = "Annuler") }
        },
        dismissButton = {
            TextButton(onClick = { onChoisir(null) }) { Text(text = "Détacher") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * Ce que la lecture a donné, en une phrase : combien de lignes, combien déjà
 * reportées, et ce qui reste à rapprocher. Un échec dit pourquoi et ce qui
 * marche mieux — jamais seulement « erreur ».
 */
@Composable
private fun BilanLecture(lecture: LectureDevis, onFermer: () -> Unit) {
    when {
        lecture.enCours -> Encart(texte = "Lecture du document…", icone = Icons.Filled.DocumentScanner)
        lecture.echec != null -> Encart(
            texte = lecture.echec,
            icone = Icons.Filled.Warning,
            alerte = true,
            onClick = onFermer,
            complement = "Fermer",
        )
        else -> {
            val origine = buildString {
                append("Devis")
                lecture.fournisseur?.let { append(" ").append(it.nom) }
                lecture.date?.let { append(" du ").append(it.format(FORMAT_DATE_DEVIS)) }
            }
            val reste = lecture.aRapprocher.size
            Encart(
                texte = "$origine : ${lecture.lues} ligne${if (lecture.lues > 1) "s" else ""} lue" +
                    "${if (lecture.lues > 1) "s" else ""}, ${lecture.reportees} reportée" +
                    "${if (lecture.reportees > 1) "s" else ""} dans la saisie" +
                    (if (reste > 0) ", $reste à rapprocher d'un article." else ".") +
                    " Rien n'est écrit avant « Enregistrer » : relisez, puis enregistrez.",
                icone = Icons.Filled.DocumentScanner,
                onClick = onFermer,
                complement = "Fermer le bilan",
            )
        }
    }
}

/**
 * Une ligne lue qu'aucune référence du magasin ne désigne.
 *
 * La suggestion est un bouton et non un choix fait d'office : deux goulottes
 * 80x60 se ressemblent assez pour qu'un rapprochement automatique se trompe, et
 * un prix d'angle posé sur une barre partirait ensuite sur un devis.
 */
@Composable
private fun LigneLue(
    ligne: LigneARapprocher,
    onRapprocher: (Article) -> Unit,
    onChoisir: () -> Unit,
    onCreer: () -> Unit,
    onIgnorer: () -> Unit,
) {
    Carte(contour = true) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = ligne.ligne.designation, style = MaterialTheme.typography.titleSmall)
            Text(
                text = buildString {
                    if (ligne.ligne.reference.isNotBlank()) append("Réf. ${ligne.ligne.reference} · ")
                    append("${Nombres.enTexte(ligne.ligne.prixNet)} € HT")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ligne.suggestion?.let { suggestion ->
                BoutonPlein(
                    texte = "→ ${suggestion.designation}",
                    onClick = { onRapprocher(suggestion) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonContour(texte = "Autre article", onClick = onChoisir, modifier = Modifier.weight(1f))
                BoutonContour(texte = "Créer", onClick = onCreer, modifier = Modifier.weight(1f))
                BoutonDiscret(texte = "Ignorer", onClick = onIgnorer, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Choisir à la main l'article d'une ligne lue, dans tout le magasin. */
@Composable
private fun DialogueChoixArticle(
    ligne: LigneARapprocher,
    articles: List<Article>,
    onChoisir: (Article) -> Unit,
    onAnnuler: () -> Unit,
) {
    var recherche by remember { mutableStateOf("") }
    val trouves = articles.filter { Recherche.correspond(recherche, it.designation, it.reference) }
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(text = ligne.ligne.designation) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampRecherche(valeur = recherche, onValeur = { recherche = it }, indication = "Article du magasin…")
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(items = trouves, key = { it.id }) { article ->
                        Text(
                            text = article.designation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoisir(article) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onAnnuler) { Text(text = "Annuler") } },
    )
}

private val FORMAT_DATE_DEVIS = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
