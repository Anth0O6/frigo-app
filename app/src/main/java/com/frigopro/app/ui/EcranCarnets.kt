package com.frigopro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
 * Les trois carnets de l'onglet.
 *
 * ## Pourquoi trois carnets et pas trois onglets
 *
 * La barre en porte déjà six, un de plus que ce que Material recommande, et un
 * septième réduirait chaque cible à ce qu'un pouce ganté ne trouve plus (voir
 * `FrigoProApp`). La bascule est la réponse que le projet a déjà donnée pour la
 * facturation, et elle vaut ici pour la même raison.
 *
 * Ce qui les réunit sous un même onglet n'est pas un pis-aller : ce sont les
 * **carnets** de l'entreprise — pour qui je travaille, chez qui j'achète, et ce
 * que je transporte. Trois listes qu'on tient à jour et qu'on consulte, par
 * opposition aux cinq autres onglets qui montrent du temps (une tournée, une
 * semaine) ou des documents.
 *
 * Le magasin est au milieu **délibérément** : c'est celui qu'on ouvre le plus
 * souvent des trois — chaque matin, avant de charger.
 */
enum class VueCarnet(val libelle: String) {
    CLIENTS("Clients"),
    MAGASIN("Magasin"),
    FOURNISSEURS("Fournisseurs"),
}

/**
 * La coquille commune aux trois : le titre, la bascule, et le bouton d'ajout.
 *
 * Nommée une fois plutôt que recopiée trois fois, pour la raison que le projet
 * donne à tout son vocabulaire visuel : trois copies d'un `Scaffold` et de ses
 * marges divergent au premier ajustement, et c'est exactement ce qui arrive
 * quand chacun recopie un `Box`.
 */
@Composable
fun CadreCarnet(
    vue: VueCarnet,
    onVue: (VueCarnet) -> Unit,
    iconeAjout: ImageVector,
    descriptionAjout: String,
    onAjouter: () -> Unit,
    modifier: Modifier = Modifier,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas ;
        // l'y ajouter ici la compterait deux fois.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAjouter,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(imageVector = iconeAjout, contentDescription = descriptionAjout)
            }
        },
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            Text(
                text = vue.libelle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = MargeEcran, vertical = 12.dp),
            )
            RangeePastilles(
                options = VueCarnet.entries,
                retenue = vue,
                libelle = { it.libelle },
                onChoisir = onVue,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MargeEcran)
                    .padding(bottom = 14.dp),
            )
            contenu()
        }
    }
}

/**
 * L'onglet des carnets : celui qui est ouvert, et ce qu'il montre.
 *
 * La vue est tenue par la **coquille** et non ici, et c'est ce qui permet à
 * l'accueil d'ouvrir le magasin directement quand il manque quelque chose : un
 * état posé dans cette fonction aurait été hors d'atteinte, et il aurait fallu
 * transporter l'intention d'un onglet à l'autre. Elle survit ainsi à une
 * rotation par le `rememberSaveable` de la coquille, mais pas à la fermeture de
 * l'application : on rouvre sur les clients, qui est le carnet le plus consulté
 * sur la durée.
 */
@Composable
fun CarnetsRoute(
    vue: VueCarnet,
    onVue: (VueCarnet) -> Unit,
    modifier: Modifier = Modifier,
    materiel: MaterielViewModel = viewModel(factory = MaterielViewModel.Factory),
) {
    when (vue) {
        VueCarnet.CLIENTS -> ClientsRoute(vue = vue, onVue = onVue, modifier = modifier)
        VueCarnet.MAGASIN -> MagasinRoute(
            vue = vue,
            onVue = onVue,
            modifier = modifier,
            viewModel = materiel,
        )
        VueCarnet.FOURNISSEURS -> FournisseursRoute(
            vue = vue,
            onVue = onVue,
            modifier = modifier,
            viewModel = materiel,
        )
    }
}

/**
 * Le magasin : la liste, et l'article ouvert.
 *
 * Un article ouvert **remplace** la liste, comme un devis ou une machine
 * ailleurs : une seule profondeur, un `BackHandler`, et toujours pas de graphe
 * de navigation.
 */
@Composable
private fun MagasinRoute(
    vue: VueCarnet,
    onVue: (VueCarnet) -> Unit,
    modifier: Modifier,
    viewModel: MaterielViewModel,
) {
    val magasin by viewModel.magasin.collectAsStateWithLifecycle()
    val manquants by viewModel.aReapprovisionner.collectAsStateWithLifecycle()
    val valeur by viewModel.valeurStock.collectAsStateWithLifecycle()
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
        vue = vue,
        onVue = onVue,
        iconeAjout = Icons.Filled.Add,
        descriptionAjout = "Ajouter un article",
        onAjouter = viewModel::onNouvelArticle,
        modifier = modifier,
    ) {
        EcranMagasin(
            magasin = magasin,
            recherche = recherche,
            manquants = manquants.size,
            valeurStock = valeur,
            onRecherche = viewModel::onRecherche,
            onOuvrir = viewModel::onOuvrirArticle,
            onBouger = viewModel::onBouger,
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

/** Les fournisseurs : la liste filtrée, et la fiche ouverte. Même motif. */
@Composable
private fun FournisseursRoute(
    vue: VueCarnet,
    onVue: (VueCarnet) -> Unit,
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
        vue = vue,
        onVue = onVue,
        iconeAjout = Icons.Filled.PersonAdd,
        descriptionAjout = "Ajouter un fournisseur",
        onAjouter = viewModel::onNouveauFournisseur,
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
