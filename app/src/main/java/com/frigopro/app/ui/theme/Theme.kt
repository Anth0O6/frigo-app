package com.frigopro.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
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
    primary = Cyan,
    onPrimary = SurCyan,
    primaryContainer = NuitPuce,
    onPrimaryContainer = Cyan,
    secondary = BleuFroid,
    onSecondary = Nuit,
    secondaryContainer = NuitPuce,
    onSecondaryContainer = BleuFroid,
    tertiary = Ambre,
    onTertiary = Color(0xFF2A1800),
    tertiaryContainer = Color(0xFF3A2A10),
    onTertiaryContainer = Ambre,
    background = Nuit,
    onBackground = NuitTexte,
    surface = Nuit,
    onSurface = NuitTexte,
    surfaceVariant = NuitPuce,
    onSurfaceVariant = NuitTexteFaible,
    surfaceContainerLowest = Nuit,
    surfaceContainerLow = NuitCarte,
    surfaceContainer = NuitCarte,
    surfaceContainerHigh = NuitSurface,
    surfaceContainerHighest = NuitPuce,
    outline = NuitFilet,
    outlineVariant = NuitFiletFort,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0A0A),
)

private val SchemaClair = lightColorScheme(
    primary = CyanSombre,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEFEB),
    onPrimaryContainer = Color(0xFF06322E),
    secondary = BleuFroidSombre,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEAF3),
    onSecondaryContainer = Color(0xFF0B2A3D),
    tertiary = AmbreSombre,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6C2),
    onTertiaryContainer = Color(0xFF3A2400),
    background = Jour,
    onBackground = JourTexte,
    surface = Jour,
    onSurface = JourTexte,
    surfaceVariant = JourCarte,
    onSurfaceVariant = JourTexteFaible,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = JourSurface,
    surfaceContainer = JourCarte,
    surfaceContainerHigh = JourSurface,
    surfaceContainerHighest = Color(0xFFE7EDF1),
    outline = JourFilet,
    outlineVariant = Color(0xFFE2E8ED),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/** Coins arrondis de la maquette : rien d'anguleux, rien de circulaire. */
private val Formes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
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
)

private val CiblesNormales = Cibles(action = 56.dp, carre = 52.dp)

private val CiblesGantees = Cibles(action = 68.dp, carre = 64.dp)

/**
 * Les cibles tactiles en vigueur. Fournies par le thème plutôt que lues dans
 * les réglages à chaque écran : c'est une propriété de l'apparence, au même
 * titre qu'une couleur.
 */
val LocalCibles = staticCompositionLocalOf { CiblesNormales }

/**
 * Thème de l'application : **sombre par défaut**, et fidèle à la maquette.
 *
 * Les couleurs dynamiques (Material You) ont été retirées à dessein. Elles
 * étaient justifiées tant que l'application n'avait pas d'identité propre ;
 * maintenant qu'un ambre signale l'intervention en cours et un cyan l'action,
 * laisser le fond d'écran du téléphone les repeindre reviendrait à effacer une
 * information.
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
    ) {
        MaterialTheme(
            colorScheme = schema,
            typography = Typography,
            shapes = Formes,
            content = content,
        )
    }
}
