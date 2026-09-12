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
import androidx.compose.material.icons.automirrored.filled.EventNote
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
 * Les six sections de l'application.
 *
 * L'accueil vient en premier parce qu'il répond à la question qu'on se pose en
 * sortant le téléphone — « et maintenant ? » — et le planning juste après, pour
 * la question suivante : « et le reste de la semaine ? ». Réglages ferme la
 * marche : c'est là que vit la sauvegarde, qu'on ouvre rarement mais dont
 * l'absence se paierait cher.
 *
 * **Outils** se range juste avant, et c'est une section d'une autre nature que les
 * cinq autres : elle ne regarde aucune donnée de l'application. Une réglette, un
 * convertisseur, une périodicité réglementaire se consultent sans client ni
 * intervention — au téléphone, devant une plaque, en préparant une tournée — et
 * les enfouir dans la fiche d'une intervention obligeait à en ouvrir une pour
 * convertir des psi.
 *
 * Six est **un de plus que ce que Material recommande**, et le libellé s'en
 * ressent : ils sont déjà abrégés au plus court lisible. La contrepartie est
 * assumée plutôt que contournée par un menu « plus » — un onglet derrière un menu
 * n'est pas un onglet, et celui-ci doit s'atteindre d'un pouce, gants aux mains.
 */
enum class Onglet(val libelle: String, val icone: ImageVector) {
    AUJOURDHUI("Auj.", Icons.Filled.Today),

    // La variante « AutoMirrored » se retourne dans une langue écrite de droite
    // à gauche, ce que `Icons.Filled` ne fait pas : c'est elle qu'il faut.
    TOURNEE("Planning", Icons.AutoMirrored.Filled.EventNote),
    DEVIS("Devis", Icons.Filled.RequestQuote),
    CLIENTS("Clients", Icons.Filled.Contacts),
    OUTILS("Outils", Icons.Filled.Straighten),
    REGLAGES("Réglages", Icons.Filled.Tune),
}

/**
 * Coquille de l'application : la section affichée et la barre qui en change.
 *
 * Pas de graphe de navigation. Avec six sections sans lien hiérarchique, une
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
    var onglet by rememberSaveable { mutableStateOf(Onglet.AUJOURDHUI) }

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
                Onglet.AUJOURDHUI -> AujourdhuiRoute(
                    onVoirPlanning = { onglet = Onglet.TOURNEE },
                    onVoirDevis = { onglet = Onglet.DEVIS },
                )

                Onglet.TOURNEE -> InterventionsRoute(
                    onAllerAuxDevis = { onglet = Onglet.DEVIS },
                )
                Onglet.CLIENTS -> ClientsRoute()
                Onglet.DEVIS -> DevisRoute()
                Onglet.OUTILS -> OutilsRoute()
                Onglet.REGLAGES -> ReglagesRoute()
            }
        }
        NavigationBar {
            Onglet.entries.forEach { cible ->
                NavigationBarItem(
                    selected = onglet == cible,
                    onClick = { onglet = cible },
                    icon = { Icon(imageVector = cible.icone, contentDescription = null) },
                    // `maxLines` explicite : à six onglets la place d'un libellé se
                    // compte, et un retour à la ligne décalerait toute la barre.
                    label = { Text(text = cible.libelle, maxLines = 1) },
                )
            }
        }
    }
}
