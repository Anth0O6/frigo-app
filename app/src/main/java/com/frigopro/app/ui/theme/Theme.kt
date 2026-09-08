package com.frigopro.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BleuFroid80,
    secondary = BleuFroidGris80,
    tertiary = Cyan80,
)

private val LightColorScheme = lightColorScheme(
    primary = BleuFroid40,
    secondary = BleuFroidGris40,
    tertiary = Cyan40,
)

/**
 * Thème Material 3 de l'application.
 *
 * Les couleurs dynamiques (Material You) sont utilisées quand l'appareil les
 * expose, avec repli sur la palette maison sinon.
 */
@Composable
fun FrigoProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
