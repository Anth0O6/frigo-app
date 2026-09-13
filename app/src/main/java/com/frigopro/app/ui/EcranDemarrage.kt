package com.frigopro.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frigopro.app.R
import com.frigopro.app.ui.theme.Barlow
import com.frigopro.app.ui.theme.FrigoProTheme
import com.frigopro.app.ui.theme.StyleChiffrePetit
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Les règles de durée de l'écran de démarrage.
 *
 * Séparées du composable parce qu'elles décident de quelque chose et se
 * vérifient sans écran : un écran de démarrage mal minuté est soit un
 * clignotement, soit de l'attente ajoutée à un technicien qui en a déjà.
 */
object Demarrage {

    /**
     * Le temps minimal pendant lequel le logo reste affiché.
     *
     * Sans minimum, un démarrage rapide ferait clignoter l'écran — apparu et
     * disparu en deux images, ce qui se remarque plus qu'un écran franc. Avec un
     * minimum trop long, on fait attendre pour rien quelqu'un qui ouvre
     * l'application vingt fois par jour dans une camionnette. Un peu plus d'un
     * demi-seconde est le compromis : assez pour que l'œil pose la marque,
     * jamais assez pour agacer.
     */
    val MINIMUM: Duration = 550.milliseconds

    /** La durée du fondu qui efface le logo et découvre l'application. */
    val FONDU: Duration = 320.milliseconds

    /**
     * Combien de temps l'écran de démarrage doit-il encore rester ?
     *
     * `null` tant que l'application n'est pas prête : on ne sait pas encore
     * quand, et il n'y a rien à programmer. Le jour où elle l'est, il ne reste
     * que ce qui manque au [MINIMUM] — un démarrage lent n'ajoute donc **jamais**
     * d'attente, il l'a déjà consommée.
     */
    fun attenteRestante(ecoule: Duration, pret: Boolean): Duration? =
        if (!pret) null else (MINIMUM - ecoule).coerceAtLeast(Duration.ZERO)
}

/**
 * L'écran de démarrage : le logo, le nom, et rien d'autre.
 *
 * **Pourquoi il existe alors qu'Android en affiche déjà un.** Celui du système
 * ne sait montrer qu'une icône — jamais de texte —, et il s'efface dès que la
 * première image de Compose est prête, c'est-à-dire *avant* que les réglages
 * soient lus. Or le thème en dépend : sans cet écran, un technicien qui a coupé
 * le thème sombre voyait l'application s'ouvrir en sombre puis basculer en clair
 * sous ses yeux. L'écran couvre exactement cet intervalle, et se retire par un
 * fondu au moment où le thème définitif s'applique — si bien que la bascule se
 * fait derrière lui.
 *
 * Il reprend le fond et l'emblème de l'écran de démarrage du système
 * (`values-v31/themes.xml`) : les deux se succèdent sans qu'on voie la couture,
 * et le nom vient s'écrire sous un logo qui, lui, n'a pas bougé.
 *
 * @param pret les réglages sont lus : l'application peut se montrer.
 * @param version ce qui s'affiche en bas. Une APK de test s'identifie ainsi sans
 *   aller la chercher dans les paramètres du téléphone.
 * @param onEfface appelé au début du fondu, et non à sa fin : c'est à ce
 *   moment-là que le thème définitif doit s'appliquer, pour que le fondu le
 *   découvre au lieu de le précéder.
 */
@Composable
fun EcranDemarrage(
    pret: Boolean,
    version: String,
    modifier: Modifier = Modifier,
    onEfface: () -> Unit = {},
) {
    // `rememberSaveable` : une rotation ne doit pas rejouer le démarrage.
    var visible by rememberSaveable { mutableStateOf(true) }
    val depuis = remember { TimeSource.Monotonic.markNow() }
    val efface by rememberUpdatedState(onEfface)

    LaunchedEffect(pret) {
        val attente = Demarrage.attenteRestante(depuis.elapsedNow(), pret) ?: return@LaunchedEffect
        delay(attente)
        visible = false
        efface()
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // Pas d'entrée : l'écran est là dès la première image, il ne s'annonce
        // pas. Le faire apparaître en fondu par-dessus celui du système aurait
        // fabriqué le clignotement qu'on cherche à éviter.
        enter = EnterTransition.None,
        exit = fadeOut(tween(FONDU_MS)),
    ) {
        ContenuDemarrage(version)
    }
}

@Composable
private fun ContenuDemarrage(version: String) {
    // Les couleurs sont fixes et non prises au thème : celui-ci bascule pendant
    // le fondu, et un nom qui vire au noir sur le fond nuit en sortant serait
    // exactement ce que l'écran est chargé de masquer.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.nuit)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.logo_demarrage),
                contentDescription = null,
                modifier = Modifier.size(COTE_LOGO),
            )
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontFamily = Barlow,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = version,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp),
            color = Color.White.copy(alpha = 0.35f),
            style = StyleChiffrePetit,
        )
    }
}

/**
 * Le côté du logo.
 *
 * L'emblème n'en occupe que la largeur : la source lui laisse en haut et en bas
 * une marge d'environ un sixième, héritée de l'icône de lanceur qu'elle sert
 * aussi. C'est elle qui fait l'essentiel de l'espace entre le logo et le nom,
 * d'où les 4 dp seulement ajoutés en dessous.
 */
private val COTE_LOGO = 168.dp

private val FONDU_MS = Demarrage.FONDU.inWholeMilliseconds.toInt()

@Preview(showBackground = true, backgroundColor = 0xFF081520)
@Composable
private fun ApercuDemarrage() {
    FrigoProTheme(sombre = true) {
        ContenuDemarrage(version = "0.2.0 (36)")
    }
}
