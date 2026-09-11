package com.frigopro.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.frigopro.app.R

/**
 * Les trois familles de la maquette, **embarquées** plutôt que téléchargées.
 *
 * L'application revendique de fonctionner hors ligne ; un fournisseur de
 * polices la ferait démarrer en Roboto au fond d'une chambre froide, là
 * précisément où elle sert. Le poids — moins d'un mégaoctet — est le prix de
 * cette garantie.
 */

/** Le texte courant. */
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

/** Les intitulés de section, en capitales espacées. */
val BarlowCondense = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

/**
 * Tout ce qui se lit comme un chiffre : heures, pressions, températures,
 * masses de fluide, montants, références.
 *
 * Une chasse fixe aligne les colonnes et, surtout, distingue le 0 du O et le 1
 * du l — ce qui n'est pas un détail quand on relève un numéro de série ou
 * qu'on compare 11,2 K à 1,12 K.
 */
val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

/**
 * Typographie Material 3 en Barlow.
 *
 * Les tailles suivent la maquette et sont franches : un écran consulté à bout
 * de bras, sur un toit, ne supporte pas la demi-mesure. Les corps de texte
 * partent de 15 sp là où Material propose 14.
 */
val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

/**
 * L'intitulé d'une section : condensé, en capitales, très espacé.
 *
 * Ce style n'a pas d'équivalent dans Material — `labelSmall` s'en approche
 * mais sert déjà aux libellés d'onglets — d'où cette constante à part.
 */
val StyleSection = TextStyle(
    fontFamily = BarlowCondense,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.7.sp,
)

/** Un chiffre qu'on lit d'un coup d'œil : durée, pression, montant. */
val StyleChiffre = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 21.sp,
    lineHeight = 26.sp,
)

/** Un chiffre de second plan : référence, horodatage, détail d'une ligne. */
val StyleChiffrePetit = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)
