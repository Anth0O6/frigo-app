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

/** Les quatre sections de l'application. */
enum class Onglet(val libelle: String, val icone: ImageVector) {
    // La variante « AutoMirrored » se retourne dans une langue écrite de droite
    // à gauche, ce que `Icons.Filled` ne fait pas : c'est elle qu'il faut.
    TOURNEE("Tournée", Icons.AutoMirrored.Filled.EventNote),
    CLIENTS("Clients", Icons.Filled.Contacts),
    DEVIS("Devis", Icons.Filled.RequestQuote),
    REGLAGES("Réglages", Icons.Filled.Tune),
}

/**
 * Coquille de l'application : la section affichée et la barre qui en change.
 *
 * Pas de graphe de navigation. Avec trois sections sans lien hiérarchique, une
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
                Onglet.TOURNEE -> InterventionsRoute()
                Onglet.CLIENTS -> ClientsRoute()
                Onglet.DEVIS -> DevisRoute()
                Onglet.REGLAGES -> ReglagesRoute()
            }
        }
        NavigationBar {
            Onglet.entries.forEach { cible ->
                NavigationBarItem(
                    selected = onglet == cible,
                    onClick = { onglet = cible },
                    icon = { Icon(imageVector = cible.icone, contentDescription = null) },
                    label = { Text(text = cible.libelle) },
                )
            }
        }
    }
}
