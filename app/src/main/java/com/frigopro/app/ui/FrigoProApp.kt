package com.frigopro.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Les cinq sections de l'application.
 *
 * Elles étaient six, et c'était une de trop — Material n'en recommande pas plus
 * de cinq, et la contrepartie se lisait sur les libellés : « Auj. » ne voulait
 * rien dire pour personne, et l'onglet « Devis » ouvrait un écran intitulé
 * « Facturation ». Le sixième a disparu en réunissant **l'accueil et le
 * planning**, qui montraient les mêmes interventions sous deux entrées
 * différentes sans que rien ne dise laquelle regarder : ils sont maintenant deux
 * des trois vues de [VueTournee], sous la bascule que les carnets et la
 * facturation portent déjà.
 *
 * L'ordre reste celui des questions qu'on se pose : la tournée d'abord — « et
 * maintenant ? » —, ce qu'elle rapporte ensuite, puis les carnets qu'on tient.
 * **Outils** se range avant-dernier, et c'est une section d'une autre nature que
 * les quatre autres : elle ne regarde aucune donnée de l'application. Une
 * réglette, un convertisseur, une périodicité réglementaire se consultent sans
 * client ni intervention — au téléphone, devant une plaque —, et les enfouir
 * dans la fiche d'une intervention obligeait à en ouvrir une pour convertir des
 * psi. **Réglages** ferme la marche : c'est là que vivent la sauvegarde et le
 * registre des fluides, qu'on ouvre rarement mais dont l'absence se paierait
 * cher.
 *
 * Aucun libellé n'est abrégé, et aucun ne ment sur ce qu'il ouvre : c'est ce
 * que le cinquième onglet a payé.
 */
enum class Onglet(val libelle: String, val icone: ImageVector) {
    TOURNEE("Tournée", Icons.Filled.Today),
    FACTURES("Factures", Icons.Filled.RequestQuote),
    CARNETS("Carnets", Icons.Filled.Contacts),
    OUTILS("Outils", Icons.Filled.Straighten),
    REGLAGES("Réglages", Icons.Filled.Tune),
}

/**
 * Coquille de l'application : la section affichée et la barre qui en change.
 *
 * Pas de graphe de navigation. Avec cinq sections sans lien hiérarchique, une
 * variable d'état suffit, et `rememberSaveable` la fait survivre à une rotation
 * comme à la mise en arrière-plan. La bibliothèque de navigation aura son
 * intérêt le jour où il faudra une pile arrière — une fiche client ouverte en
 * pleine page plutôt qu'en feuille, par exemple — ou des liens profonds.
 *
 * **C'est la coquille qui pose les marges des barres système**, et elle seule :
 * la barre d'onglets porte celle du bas, et le contenu reçoit ici celle du haut
 * et des côtés. Les écrans qu'elle héberge passent donc
 * `contentWindowInsets = WindowInsets(0, 0, 0, 0)` à leur `Scaffold` — sans quoi
 * la marge serait comptée deux fois.
 *
 * La marge du haut vient de `safeDrawing` et non des seules barres système,
 * parce qu'elle doit aussi écarter la **découpe d'écran** : sur un téléphone à
 * appareil photo perforé, celui-ci déborde de la barre d'état et masquerait le
 * titre de l'écran.
 */
@Composable
fun FrigoProApp(modifier: Modifier = Modifier) {
    var onglet by rememberSaveable { mutableStateOf(Onglet.TOURNEE) }

    // Le carnet ouvert est tenu **ici** et non dans `CarnetsRoute`, parce que
    // l'accueil doit pouvoir désigner le magasin : « il manque trois articles »
    // n'est une réponse à « et maintenant ? » que si elle mène quelque part.
    // C'est le même motif que la facture ouverte depuis l'accueil, à ceci près
    // qu'un carnet n'est pas un `ViewModel` et ne peut donc pas être partagé
    // par `viewModel()`.
    var carnet by rememberSaveable { mutableStateOf(VueCarnet.CLIENTS) }

    // La page des Réglages est tenue ici pour la même raison que le carnet, et
    // c'est le bandeau de l'accueil qui l'exige : « une ligne de main-d'œuvre se
    // chiffre à 0 € » n'est une réponse à « et maintenant ? » que si elle mène à
    // la page qui le corrige, et un état posé dans `ReglagesRoute` aurait été
    // hors d'atteinte de l'accueil.
    var pageReglages by rememberSaveable { mutableStateOf<PageReglages?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            when (onglet) {
                Onglet.TOURNEE -> TourneeRoute(
                    onAllerAuxDevis = { onglet = Onglet.FACTURES },
                    onVoirMagasin = {
                        carnet = VueCarnet.MAGASIN
                        onglet = Onglet.CARNETS
                    },
                    onReglage = { page ->
                        pageReglages = page
                        onglet = Onglet.REGLAGES
                    },
                )

                Onglet.FACTURES -> FacturationRoute(
                    onAllerALaTournee = { onglet = Onglet.TOURNEE },
                )
                Onglet.CARNETS -> CarnetsRoute(vue = carnet, onVue = { carnet = it })
                Onglet.OUTILS -> OutilsRoute()
                Onglet.REGLAGES -> ReglagesRoute(
                    page = pageReglages,
                    onPage = { pageReglages = it },
                )
            }
        }
        NavigationBar {
            Onglet.entries.forEach { cible ->
                NavigationBarItem(
                    selected = onglet == cible,
                    onClick = { onglet = cible },
                    icon = { Icon(imageVector = cible.icone, contentDescription = null) },
                    // `maxLines` explicite : la place d'un libellé se compte
                    // encore à cinq, et un retour à la ligne décalerait toute
                    // la barre.
                    label = { Text(text = cible.libelle, maxLines = 1) },
                )
            }
        }
    }
}
