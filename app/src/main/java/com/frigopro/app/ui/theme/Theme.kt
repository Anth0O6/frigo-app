package com.frigopro.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val SchemaSombre = darkColorScheme(
    primary = Bleu,
    onPrimary = SurBleu,
    primaryContainer = Bleu.copy(alpha = 0.20f),
    onPrimaryContainer = BleuClair,
    secondary = TexteSecondaire,
    onSecondary = Noir,
    secondaryContainer = Relief,
    onSecondaryContainer = Texte,
    tertiary = AValider,
    onTertiary = Color(0xFF2A1800),
    tertiaryContainer = AValider.copy(alpha = 0.16f),
    onTertiaryContainer = AValider,
    background = Noir,
    onBackground = Texte,
    surface = Noir,
    onSurface = Texte,
    surfaceVariant = Relief,
    onSurfaceVariant = TexteSecondaire,
    surfaceContainerLowest = Noir,
    surfaceContainerLow = CarteEteinte,
    surfaceContainer = Carte,
    surfaceContainerHigh = Relief,
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Filet,
    outlineVariant = Color(0xFF2C2C2E),
    error = Urgence,
    onError = Color(0xFF3A0A05),
)

private val SchemaClair = lightColorScheme(
    primary = BleuSombre,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FA),
    onPrimaryContainer = Color(0xFF06305F),
    secondary = JourTexteSecondaire,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = JourTexte,
    tertiary = AValiderSombre,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6C2),
    onTertiaryContainer = Color(0xFF3A2400),
    background = Jour,
    onBackground = JourTexte,
    surface = Jour,
    onSurface = JourTexte,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = JourTexteSecondaire,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = JourCarte,
    surfaceContainerHigh = Color(0xFFEFEFF4),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = JourFilet,
    outlineVariant = Color(0xFFE5E5EA),
    error = UrgenceSombre,
    onError = Color.White,
)

/**
 * Les cinq teintes de statut, et rien d'autre en couleur sur un écran.
 *
 * Elles vivent dans le thème et non dans les écrans parce qu'elles changent
 * avec le mode clair : les teintes vives de la maquette ne passent aucun seuil
 * de contraste sur blanc, et une pastille illisible ne signale plus rien.
 */
class Statuts(
    val urgence: Color,
    val planifie: Color,
    val aValider: Color,
    val devis: Color,
    val termine: Color,
)

private val StatutsSombres = Statuts(
    urgence = Urgence,
    planifie = Planifie,
    aValider = AValider,
    devis = CouleurDevis,
    termine = Termine,
)

private val StatutsClairs = Statuts(
    urgence = UrgenceSombre,
    planifie = BleuSombre,
    aValider = AValiderSombre,
    devis = DevisSombre,
    termine = TermineSombre,
)

/** Les couleurs de statut en vigueur. Voir [Statuts]. */
val LocalStatuts = staticCompositionLocalOf { StatutsSombres }

/**
 * Coins arrondis de la maquette : généreux, jamais circulaires.
 *
 * `large` est le rayon d'une carte de liste (18 dp) et `extraLarge` celui de la
 * carte héros (24 dp), qui est plus grande et doit le rester à l'œil.
 */
private val Formes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Hauteur des cibles tactiles, et ce que le **mode gants** en fait.
 *
 * Un frigoriste travaille ganté, souvent d'une seule main, parfois sur une
 * échelle. Les 48 dp de Material sont un minimum pensé pour un pouce nu :
 * la maquette pose 56 dp, et le mode gants monte à 68 dp pour les commandes
 * qui décident de quelque chose.
 */
class Cibles(
    /** Bouton principal, ligne de liste que l'on touche. */
    val action: Dp,
    /** Bouton carré d'une barre : appeler, itinéraire, retour. */
    val carre: Dp,
    /**
     * Case à cocher de la checklist.
     *
     * Elle grandit aussi avec les gants : c'est l'une des commandes qu'on touche
     * le plus souvent sur un toit, et la seule dont l'oubli se lit plus tard sur
     * un document réglementaire.
     */
    val case: Dp,
)

private val CiblesNormales = Cibles(action = 56.dp, carre = 52.dp, case = 26.dp)

private val CiblesGantees = Cibles(action = 68.dp, carre = 64.dp, case = 34.dp)

/** Les cibles tactiles en vigueur. */
val LocalCibles = staticCompositionLocalOf { CiblesNormales }

/**
 * Thème de l'application : **sombre par défaut**, et fidèle à la maquette.
 *
 * Les couleurs dynamiques (Material You) ont été retirées à dessein. Sur un
 * écran où le rouge veut dire « urgence » et le vert « fait », laisser le fond
 * d'écran du téléphone les redistribuer reviendrait à effacer une information.
 *
 * @param sombre `null` pour suivre le réglage du système, sinon le choix
 *   explicite du technicien — l'interrupteur « Thème sombre » des Réglages.
 * @param modeGants agrandit les cibles tactiles.
 */
@Composable
fun FrigoProTheme(
    sombre: Boolean? = null,
    modeGants: Boolean = false,
    content: @Composable () -> Unit,
) {
    val enSombre = sombre ?: isSystemInDarkTheme()
    val schema = if (enSombre) SchemaSombre else SchemaClair

    // Les barres système empruntent le fond de l'application, et leurs icônes
    // doivent virer au clair sur fond sombre — sans quoi l'heure disparaît.
    val vue = LocalView.current
    if (!vue.isInEditMode) {
        val contexte = LocalContext.current
        SideEffect {
            val fenetre = (contexte as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(fenetre, vue).apply {
                isAppearanceLightStatusBars = !enSombre
                isAppearanceLightNavigationBars = !enSombre
            }
        }
    }

    CompositionLocalProvider(
        LocalCibles provides if (modeGants) CiblesGantees else CiblesNormales,
        LocalStatuts provides if (enSombre) StatutsSombres else StatutsClairs,
    ) {
        MaterialTheme(
            colorScheme = schema,
            typography = Typography,
            shapes = Formes,
            content = content,
        )
    }
}
