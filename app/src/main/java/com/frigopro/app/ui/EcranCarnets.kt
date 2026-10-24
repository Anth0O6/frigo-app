package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.RangeePastilles

/**
 * Les trois vues de l'onglet Atelier : ce que je transporte, chez qui je
 * l'achète, et de quoi calculer sur place.
 *
 * ## Pourquoi ces trois-là ensemble
 *
 * L'onglet s'appelait **Carnets** et réunissait les clients, le magasin et les
 * fournisseurs, au motif que ce sont trois listes qu'on tient à jour. Le motif
 * était juste et le regroupement ne l'était pas : on n'ouvre jamais le carnet des
 * clients pour la même raison que le magasin. Le premier sert la tournée et la
 * facture — c'est *pour qui* je travaille —, les deux autres servent l'atelier —
 * c'est *avec quoi*. Les clients ont donc leur onglet, et l'onglet **Outils**,
 * qui ne regardait aucune donnée de l'application, est venu prendre la troisième
 * place : une réglette ou un convertisseur sont l'outillage de l'atelier au même
 * titre que le stock du camion, et la barre du bas y gagnait un onglet pour le
 * planning.
 *
 * Trois pastilles et pas une de plus : c'est le compte qui tient sur un écran de
 * 320 points sans défiler, et une pastille rognée est une destination qu'on ne
 * voit pas (voir `RangeePastilles`).
 *
 * Le magasin est **en tête** et c'est la vue par défaut : c'est celle qu'on ouvre
 * le plus souvent des trois — chaque matin, avant de charger —, et celle vers
 * laquelle l'accueil mène quand il manque quelque chose.
 */
enum class VueAtelier(val libelle: String) {
    MAGASIN("Magasin"),
    FOURNISSEURS("Fournisseurs"),
    OUTILS("Outils"),
}

/**
 * La bascule des trois vues de l'Atelier.
 *
 * Nommée une fois parce que trois écrans la posent — le magasin, les
 * fournisseurs, la liste des outils —, et que trois copies d'une même bascule
 * divergent au premier ajustement.
 */
@Composable
fun BasculeAtelier(vue: VueAtelier, onVue: (VueAtelier) -> Unit) {
    RangeePastilles(
        options = VueAtelier.entries,
        retenue = vue,
        libelle = { it.libelle },
        onChoisir = onVue,
    )
}

/**
 * La coquille commune aux listes qu'on tient : le titre, la bascule s'il y en a
 * une, et le bouton d'ajout s'il y a quelque chose à ajouter.
 *
 * Nommée une fois plutôt que recopiée, pour la raison que le projet donne à tout
 * son vocabulaire visuel : quatre copies d'un `Scaffold` et de ses marges
 * divergent au premier ajustement, et c'est exactement ce qui arrive quand chacun
 * recopie un `Box`. Le carnet des clients y passe **sans bascule** — il est seul
 * dans son onglet —, la liste des outils **sans bouton d'ajout** — on n'y ajoute
 * rien.
 *
 * Le titre nomme la vue ouverte et non l'onglet, comme partout : « Atelier »
 * au-dessus d'une bascule « Magasin · Fournisseurs · Outils » répéterait le nom
 * du groupe et laisserait l'écran sans dire lequel des trois on regarde.
 */
@Composable
fun CadreCarnet(
    titre: String,
    modifier: Modifier = Modifier,
    /** L'icône du bouton flottant, `null` quand il n'y a rien à ajouter. */
    iconeAjout: ImageVector? = null,
    descriptionAjout: String = "",
    onAjouter: () -> Unit = {},
    /** La bascule de l'onglet, posée sous le titre ; `null` pour une vue seule. */
    bascule: (@Composable () -> Unit)? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas ;
        // l'y ajouter ici la compterait deux fois.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (iconeAjout != null) {
                FloatingActionButton(
                    onClick = onAjouter,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(imageVector = iconeAjout, contentDescription = descriptionAjout)
                }
            }
        },
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            Text(
                text = titre,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = MargeEcran, vertical = 12.dp),
            )
            if (bascule != null) {
                // Sans défilement : trois pastilles tiennent, et une rangée qui
                // glisse cacherait la dernière destination sans le dire.
                Column(
                    modifier = Modifier
                        .padding(horizontal = MargeEcran)
                        .padding(bottom = 14.dp),
                ) { bascule() }
            }
            contenu()
        }
    }
}

/**
 * L'onglet Atelier : la vue ouverte, et ce qu'elle montre.
 *
 * La vue est tenue par la **coquille** et non ici, et c'est ce qui permet à
 * l'accueil d'ouvrir le magasin directement quand il manque quelque chose : un
 * état posé dans cette fonction aurait été hors d'atteinte, et il aurait fallu
 * transporter l'intention d'un onglet à l'autre. Elle survit ainsi au changement
 * d'onglet et à une rotation, mais pas à la fermeture de l'application : on
 * rouvre sur le magasin.
 */
@Composable
fun AtelierRoute(
    vue: VueAtelier,
    onVue: (VueAtelier) -> Unit,
    modifier: Modifier = Modifier,
    /** Mène à une page des Réglages : le coefficient, depuis la saisie des prix. */
    onReglage: (PageReglages) -> Unit = {},
    materiel: MaterielViewModel = viewModel(factory = MaterielViewModel.Factory),
    saisiePrix: SaisiePrixViewModel = viewModel(factory = SaisiePrixViewModel.Factory),
) {
    val bascule: @Composable () -> Unit = { BasculeAtelier(vue = vue, onVue = onVue) }
    when (vue) {
        VueAtelier.MAGASIN -> MagasinRoute(
            bascule = bascule,
            modifier = modifier,
            viewModel = materiel,
            saisiePrix = saisiePrix,
            onReglage = onReglage,
        )
        VueAtelier.FOURNISSEURS -> FournisseursRoute(
            bascule = bascule,
            modifier = modifier,
            viewModel = materiel,
        )
        VueAtelier.OUTILS -> OutilsRoute(bascule = bascule, modifier = modifier)
    }
}

/**
 * Le magasin : la liste, l'article ouvert, et la saisie groupée des prix.
 *
 * Un article ouvert **remplace** la liste, comme un devis ou une machine
 * ailleurs : une seule profondeur, un `BackHandler`, et toujours pas de graphe
 * de navigation. La saisie des prix la remplace de la même façon.
 */
@Composable
private fun MagasinRoute(
    bascule: @Composable () -> Unit,
    modifier: Modifier,
    viewModel: MaterielViewModel,
    saisiePrix: SaisiePrixViewModel,
    onReglage: (PageReglages) -> Unit,
) {
    val saisieOuverte by saisiePrix.ouverte.collectAsStateWithLifecycle()
    if (saisieOuverte) {
        SaisiePrixRoute(
            viewModel = saisiePrix,
            onReglage = onReglage,
            modifier = modifier,
        )
        return
    }

    val magasin by viewModel.magasin.collectAsStateWithLifecycle()
    val manquants by viewModel.aReapprovisionner.collectAsStateWithLifecycle()
    val valeur by viewModel.valeurStock.collectAsStateWithLifecycle()
    val sansPrix by viewModel.sansPrix.collectAsStateWithLifecycle()
    val recherche by viewModel.recherche.collectAsStateWithLifecycle()
    val ouvert by viewModel.articleOuvert.collectAsStateWithLifecycle()
    val fournisseurs by viewModel.tousLesFournisseurs.collectAsStateWithLifecycle()
    val brouillon by viewModel.brouillonArticle.collectAsStateWithLifecycle()

    BackHandler(enabled = ouvert != null) { viewModel.onFermerArticle() }

    val article = ouvert
    if (article != null) {
        FicheArticle(
            entree = article,
            fournisseurs = fournisseurs,
            onModifier = { viewModel.onBrouillonArticle(it); viewModel.onEnregistrerArticle() },
            onDefinirStock = { lieu, quantite, minimum ->
                viewModel.onDefinirStock(article.article.id, lieu, quantite, minimum)
            },
            onTransferer = { depuis, vers, combien ->
                viewModel.onTransferer(article.article.id, depuis, vers, combien)
            },
            onSupprimer = { viewModel.onSupprimerArticle(article.article.id) },
            onFermer = viewModel::onFermerArticle,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    CadreCarnet(
        titre = VueAtelier.MAGASIN.libelle,
        iconeAjout = Icons.Filled.Add,
        descriptionAjout = "Ajouter un article",
        onAjouter = viewModel::onNouvelArticle,
        bascule = bascule,
        modifier = modifier,
    ) {
        EcranMagasin(
            magasin = magasin,
            recherche = recherche,
            manquants = manquants.size,
            valeurStock = valeur,
            sansPrix = sansPrix,
            onRecherche = viewModel::onRecherche,
            onOuvrir = viewModel::onOuvrirArticle,
            onBouger = viewModel::onBouger,
            onSaisirPrix = saisiePrix::onOuvrir,
        )
    }

    brouillon?.let {
        FeuilleNouvelArticle(
            brouillon = it,
            onBrouillon = viewModel::onBrouillonArticle,
            onEnregistrer = viewModel::onEnregistrerArticle,
            onAnnuler = viewModel::onAnnulerBrouillonArticle,
        )
    }
}

/**
 * La saisie groupée des prix, branchée sur son `ViewModel`.
 *
 * Ses flux ne sont collectés que tant qu'elle est ouverte : la liste qu'elle
 * compose recalcule chaque ligne du magasin à chaque frappe, et ce travail n'a
 * pas à tourner derrière l'inventaire.
 */
@Composable
private fun SaisiePrixRoute(
    viewModel: SaisiePrixViewModel,
    onReglage: (PageReglages) -> Unit,
    modifier: Modifier,
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    val fournisseurs by viewModel.fournisseurs.collectAsStateWithLifecycle()

    EcranSaisiePrix(
        etat = etat,
        fournisseurs = fournisseurs,
        onRecherche = viewModel::onRecherche,
        onFiltre = viewModel::onFiltre,
        onReference = viewModel::onReference,
        onPrixAchat = viewModel::onPrixAchat,
        onPrixVente = viewModel::onPrixVente,
        onCocher = viewModel::onCocher,
        onCocherTout = viewModel::onCocherTout,
        onAttribuerFournisseur = viewModel::onAttribuerFournisseur,
        onEnregistrer = viewModel::onEnregistrer,
        onEnregistrerEtFermer = viewModel::onEnregistrerEtFermer,
        onAbandonner = viewModel::onAbandonner,
        onReglerCoefficient = { onReglage(ReglageManquant.COEFFICIENT_MATERIEL.page) },
        modifier = modifier.fillMaxSize(),
    )
}

/** Les fournisseurs : la liste filtrée, et la fiche ouverte. Même motif. */
@Composable
private fun FournisseursRoute(
    bascule: @Composable () -> Unit,
    modifier: Modifier,
    viewModel: MaterielViewModel,
) {
    val fournisseurs by viewModel.fournisseurs.collectAsStateWithLifecycle()
    val tous by viewModel.tousLesFournisseurs.collectAsStateWithLifecycle()
    val filtres by viewModel.filtres.collectAsStateWithLifecycle()
    val ouvert by viewModel.fournisseurOuvert.collectAsStateWithLifecycle()
    val brouillon by viewModel.brouillonFournisseur.collectAsStateWithLifecycle()

    BackHandler(enabled = ouvert != null) { viewModel.onFermerFournisseur() }

    val fournisseur = ouvert
    if (fournisseur != null) {
        FicheFournisseur(
            fournisseur = fournisseur,
            onModifier = {
                viewModel.onBrouillonFournisseur(it)
                viewModel.onEnregistrerFournisseur()
            },
            onSupprimer = { viewModel.onSupprimerFournisseur(fournisseur.id) },
            onFermer = viewModel::onFermerFournisseur,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    CadreCarnet(
        titre = VueAtelier.FOURNISSEURS.libelle,
        iconeAjout = Icons.Filled.PersonAdd,
        descriptionAjout = "Ajouter un fournisseur",
        onAjouter = viewModel::onNouveauFournisseur,
        bascule = bascule,
        modifier = modifier,
    ) {
        EcranFournisseurs(
            fournisseurs = fournisseurs,
            filtres = filtres,
            total = tous.size,
            onFiltres = viewModel::onFiltres,
            onOuvrir = viewModel::onOuvrirFournisseur,
        )
    }

    brouillon?.let {
        FeuilleNouveauFournisseur(
            brouillon = it,
            onBrouillon = viewModel::onBrouillonFournisseur,
            onEnregistrer = viewModel::onEnregistrerFournisseur,
            onAnnuler = viewModel::onAnnulerBrouillonFournisseur,
        )
    }
}
