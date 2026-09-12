package com.frigopro.app.ui.composants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frigopro.app.ui.theme.AValider
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.StyleChiffre
import com.frigopro.app.ui.theme.StyleChiffrePetit
import com.frigopro.app.ui.theme.StyleSection

/**
 * Le vocabulaire visuel commun aux douze écrans.
 *
 * La maquette répète partout les mêmes formes : une carte arrondie, un
 * intitulé de section en capitales condensées, une tuile portant un chiffre,
 * une barre d'actions en bas. Les nommer une fois évite qu'elles divergent
 * écran par écran, ce qui est exactement ce qui arrive quand chacun recopie un
 * `Box` et ses marges.
 */

/** Marge latérale de tous les écrans. */
val MargeEcran = 20.dp

/**
 * Une carte : le conteneur de base.
 *
 * @param relief carte surélevée (en-tête, élément retenu) plutôt qu'ordinaire.
 * @param liseré couleur d'un filet vertical à gauche, qui signale l'état d'une
 *   ligne — l'ambre d'une intervention en cours, par exemple.
 */
@Composable
fun Carte(
    modifier: Modifier = Modifier,
    relief: Boolean = false,
    liseré: Color? = null,
    contour: Boolean = false,
    forme: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    /**
     * L'appui long. Il porte ici l'action secondaire — modifier la fiche d'une
     * intervention quand l'appui simple l'ouvre — parce qu'une carte de liste
     * n'a pas la place d'un second bouton sans cesser d'être balayable du
     * pouce.
     */
    onLongClick: (() -> Unit)? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val fond = if (relief) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    @OptIn(ExperimentalFoundationApi::class)
    val cliquable = when {
        onClick != null && onLongClick != null -> Modifier.combinedClickable(
            role = Role.Button,
            onClick = onClick,
            onLongClick = onLongClick,
        )

        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = forme,
        color = fond,
        border = if (contour) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        // `IntrinsicSize.Min` donne à la Row la hauteur de son contenu, ce qui
        // permet au liseré de la remplir : sans cela il n'aurait aucune
        // hauteur propre et disparaîtrait.
        Row(modifier = cliquable.height(IntrinsicSize.Min)) {
            if (liseré != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(liseré),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = contenu,
            )
        }
    }
}

/** L'intitulé d'une section : capitales condensées, très espacées. */
@Composable
fun IntituleSection(
    texte: String,
    modifier: Modifier = Modifier,
    couleur: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = texte.uppercase(),
        style = StyleSection,
        color = couleur,
        modifier = modifier,
    )
}

/** Une section : son intitulé, puis son contenu. */
@Composable
fun Section(
    intitule: String,
    modifier: Modifier = Modifier,
    couleurIntitule: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    espacement: Dp = 10.dp,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IntituleSection(texte = intitule, couleur = couleurIntitule)
        Column(verticalArrangement = Arrangement.spacedBy(espacement), content = contenu)
    }
}

/**
 * Une tuile portant un chiffre et ce qu'il désigne : « 2/6 terminées »,
 * « 14,8 bar », « 3 machines ».
 */
@Composable
fun TuileChiffre(
    valeur: String,
    libelle: String,
    modifier: Modifier = Modifier,
    unite: String? = null,
    couleur: Color = MaterialTheme.colorScheme.onSurface,
    contour: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (contour) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = libelle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = valeur, style = StyleChiffre, color = couleur)
                if (unite != null) {
                    Text(
                        text = " $unite",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/** Une puce : un mot posé sur un fond arrondi. */
@Composable
fun Puce(
    texte: String,
    modifier: Modifier = Modifier,
    couleur: Color = MaterialTheme.colorScheme.secondary,
    fond: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    chiffre: Boolean = false,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraSmall, color = fond) {
        Text(
            text = texte,
            style = if (chiffre) StyleChiffrePetit else MaterialTheme.typography.labelMedium,
            color = couleur,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

/** Un bouton plein : l'action principale d'un écran. */
@Composable
fun BoutonPlein(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    couleur: Color = MaterialTheme.colorScheme.primary,
    surCouleur: Color = MaterialTheme.colorScheme.onPrimary,
    actif: Boolean = true,
) {
    val fond = if (actif) couleur else MaterialTheme.colorScheme.surfaceContainerHighest
    val encre = if (actif) surCouleur else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.height(LocalCibles.current.action),
        shape = MaterialTheme.shapes.medium,
        color = fond,
        onClick = onClick,
        enabled = actif,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = texte, style = MaterialTheme.typography.labelLarge, color = encre)
        }
    }
}

/** Un bouton cerné : l'action secondaire, qui ne doit pas attirer l'œil. */
@Composable
fun BoutonContour(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    couleur: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.height(LocalCibles.current.action),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, couleur),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = texte, style = MaterialTheme.typography.labelLarge, color = couleur)
        }
    }
}

/** Un bouton discret sur fond de surface : « Comparer », « PDF », « Aperçu ». */
@Composable
fun BoutonDiscret(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(LocalCibles.current.action),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = texte,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Un bouton carré portant une icône : retour, appeler, itinéraire. */
@Composable
fun BoutonCarre(
    icone: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    couleur: Color = MaterialTheme.colorScheme.secondary,
    fond: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val cote = LocalCibles.current.carre
    Surface(
        modifier = modifier.size(cote),
        shape = RoundedCornerShape(cote / 3.6f),
        color = fond,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icone, contentDescription = description, tint = couleur)
        }
    }
}

/**
 * Un encart d'information ou d'alerte : le fond très dilué de sa couleur, un
 * filet de la même, et un texte qui tient sur deux ou trois lignes.
 */
@Composable
fun Encart(
    texte: String,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.secondary,
    alerte: Boolean = false,
    onClick: (() -> Unit)? = null,
    complement: String? = null,
) {
    val teinte = if (alerte) AValider else accent
    val cliquable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (alerte) teinte.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (alerte) BorderStroke(1.dp, teinte.copy(alpha = 0.35f)) else null,
    ) {
        Row(
            modifier = cliquable.padding(horizontal = 15.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (icone != null) {
                Icon(
                    imageVector = icone,
                    contentDescription = null,
                    tint = teinte,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = texte,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (complement != null) {
                    Text(
                        text = complement,
                        style = MaterialTheme.typography.labelMedium,
                        color = teinte,
                    )
                }
            }
        }
    }
}

/**
 * La barre d'actions du bas : un fond qui la détache du contenu, un filet au
 * ras, et la marge de la barre système.
 */
@Composable
fun BarreActions(
    modifier: Modifier = Modifier,
    contenu: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargeEcran, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = contenu,
            )
        }
    }
}

/** Un emplacement d'image : le gris bleuté de la maquette, et son intitulé. */
@Composable
fun EmplacementImage(
    modifier: Modifier = Modifier,
    legende: String? = null,
    forme: Shape = MaterialTheme.shapes.medium,
) {
    Box(
        modifier = modifier
            .clip(forme)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (legende != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Text(
                    text = legende,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}
