package com.frigopro.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
 * Le minutage de l'écran de démarrage, et la chorégraphie de son animation.
 *
 * Séparés du composable parce qu'ils décident de quelque chose et se vérifient
 * sans écran. Une chorégraphie est une suite de fenêtres dans un temps commun,
 * et la seule façon de se tromper est de la laisser déborder du temps
 * disponible : l'écran partirait alors au milieu d'un mouvement.
 */
object Demarrage {

    /**
     * Le temps minimal pendant lequel l'écran reste affiché.
     *
     * Il valait 550 ms, ce qu'il fallait pour qu'un téléphone rapide ne fasse
     * pas *clignoter* le logo et rien de plus. Il vaut deux secondes de plus
     * parce que l'animation a besoin de se dérouler : l'anneau met à lui seul
     * près de deux secondes à faire le tour, et une animation coupée en son
     * milieu est pire que pas d'animation du tout.
     *
     * Le coût est réel et assumé : ce sont deux secondes et demie à chaque
     * démarrage **à froid** — au premier lancement de la journée, et chaque fois
     * qu'Android a repris la mémoire de l'application. Revenir sur l'application
     * restée en arrière-plan ne recrée pas l'activité et ne rejoue donc rien.
     *
     * Ce n'est qu'un **plancher** : voir [attenteRestante]. Un démarrage lent
     * n'ajoute rien par-dessus.
     */
    val MINIMUM: Duration = 2550.milliseconds

    /** La durée du fondu qui efface l'écran et découvre l'application. */
    val FONDU: Duration = 400.milliseconds

    /** Une fenêtre de la chorégraphie : quand elle commence, combien elle dure. */
    data class Phase(val depart: Duration, val duree: Duration) {
        val fin: Duration get() = depart + duree
    }

    /** Le logo paraît : il monte de 90 % à sa taille pleine en se révélant. */
    val LOGO = Phase(Duration.ZERO, 720.milliseconds)

    /**
     * L'anneau fait le tour du logo, **du froid vers le chaud**.
     *
     * Il part du bas et tourne dans le sens des aiguilles, ce qui le fait passer
     * par la gauche — le bleu, le flocon — avant d'arriver à droite, sur
     * l'orange et la flamme. C'est le sens de lecture de la jauge du logo, et
     * c'est aussi le métier : on part du froid.
     */
    val ANNEAU = Phase(300.milliseconds, 1850.milliseconds)

    /** Le nom se lève sous le logo. */
    val NOM = Phase(820.milliseconds, 580.milliseconds)

    /** Le numéro de build, en dernier et à peine. */
    val VERSION = Phase(1400.milliseconds, 500.milliseconds)

    /** Toute la chorégraphie, pour ce qui doit la regarder d'ensemble. */
    val CHOREGRAPHIE: List<Phase> = listOf(LOGO, ANNEAU, NOM, VERSION)

    /**
     * Où en est une phase, de 0 à 1, à cet instant de la chorégraphie.
     *
     * Borné aux deux bouts : avant sa fenêtre une phase n'a pas commencé, après
     * elle est finie et le reste — c'est ce qui permet à l'écran de s'attarder
     * au-delà de la chorégraphie sans que rien ne reparte en arrière.
     */
    fun avancement(ecoule: Duration, phase: Phase): Float =
        ((ecoule - phase.depart) / phase.duree).toFloat().coerceIn(0f, 1f)

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
 * L'écran de démarrage : le logo, l'anneau qui en fait le tour, et le nom.
 *
 * **Pourquoi il existe alors qu'Android en affiche déjà un.** Celui du système
 * ne sait montrer qu'une icône — jamais de texte, jamais d'animation à soi —, et
 * il s'efface dès que la première image de Compose est prête, c'est-à-dire
 * *avant* que les réglages soient lus. Or le thème en dépend : sans cet écran,
 * un technicien qui a coupé le thème sombre voyait l'application s'ouvrir en
 * sombre puis basculer en clair sous ses yeux. L'écran couvre exactement cet
 * intervalle, et se retire par un fondu au moment où le thème définitif
 * s'applique — si bien que la bascule se fait derrière lui.
 *
 * Il reprend le fond et l'emblème de l'écran de démarrage du système
 * (`values-v31/themes.xml`) : les deux se succèdent sans qu'on voie la couture,
 * et l'animation ne démarre qu'une fois le relais pris.
 *
 * Toute la chorégraphie est lue par une **seule horloge** — une animation de 0 à
 * 1 sur [Demarrage.MINIMUM] — dont chaque élément découpe sa fenêtre. Quatre
 * animations indépendantes auraient dérivé les unes des autres sur un téléphone
 * qui saute des images ; ici elles ne peuvent pas, elles lisent le même temps.
 * Et ce temps n'est lu que dans des lambdas de dessin (`graphicsLayer`,
 * `Canvas`) : l'arbre n'est pas recomposé soixante fois par seconde, il est
 * seulement redessiné.
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

    // L'horloge de la chorégraphie. Linéaire à dessein : les assouplissements
    // appartiennent à chaque mouvement, pas au temps qui les porte.
    val horloge = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        horloge.animateTo(1f, tween(MINIMUM_MS, easing = LinearEasing))
    }

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
        // En sortant il s'écarte légèrement vers l'avant : on ne referme pas une
        // image, on entre dans l'application.
        exit = fadeOut(tween(FONDU_MS)) + scaleOut(tween(FONDU_MS), targetScale = 1.06f),
    ) {
        ContenuDemarrage(
            ecoule = { Demarrage.MINIMUM * horloge.value.toDouble() },
            version = version,
        )
    }
}

@Composable
private fun ContenuDemarrage(ecoule: () -> Duration, version: String) {
    // Les couleurs sont fixes et non prises au thème : celui-ci bascule pendant
    // le fondu, et un nom qui virerait au noir en sortant serait exactement ce
    // que l'écran est chargé de masquer.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.nuit)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ECART_NOM),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnneauDemarrage(ecoule)
                Image(
                    painter = painterResource(R.drawable.logo_demarrage),
                    contentDescription = null,
                    modifier = Modifier
                        .size(COTE_LOGO)
                        .graphicsLayer {
                            val p = FastOutSlowInEasing.transform(
                                Demarrage.avancement(ecoule(), Demarrage.LOGO),
                            )
                            alpha = p
                            scaleX = 0.90f + 0.10f * p
                            scaleY = scaleX
                        },
                )
            }

            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.graphicsLayer {
                    val p = FastOutSlowInEasing.transform(
                        Demarrage.avancement(ecoule(), Demarrage.NOM),
                    )
                    alpha = p
                    translationY = (1f - p) * MONTEE_NOM.toPx()
                },
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
                .padding(bottom = 44.dp)
                .graphicsLayer {
                    alpha = OPACITE_VERSION *
                        Demarrage.avancement(ecoule(), Demarrage.VERSION)
                },
            color = Color.White,
            style = StyleChiffrePetit,
        )
    }
}

/**
 * L'anneau qui fait le tour du logo, du froid vers le chaud.
 *
 * Il est **dessiné** et non embarqué en image, et ce n'est pas une coquetterie :
 * un dégradé tracé au pixel près reste net à toutes les densités, et la teinte
 * de chaque côté est celle relevée sur l'emblème lui-même — le bleu du flocon à
 * gauche, l'orange de la flamme à droite. Le dégradé est **horizontal** plutôt
 * que circulaire, si bien que chaque côté de l'anneau porte la couleur du côté
 * du logo qu'il longe, et qu'aucune couture n'apparaît là où le tour se referme.
 */
@Composable
private fun AnneauDemarrage(ecoule: () -> Duration) {
    Canvas(modifier = Modifier.size(DIAMETRE_ANNEAU)) {
        val p = FastOutSlowInEasing.transform(
            Demarrage.avancement(ecoule(), Demarrage.ANNEAU),
        )
        if (p <= 0f) return@Canvas

        val trait = TRAIT_ANNEAU.toPx()
        drawArc(
            brush = Brush.linearGradient(
                colors = listOf(FROID, CHAUD),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
            ),
            // 90° est le bas ; le balayage est horaire, donc il passe par la
            // gauche — le froid — avant d'atteindre la droite.
            startAngle = 90f,
            sweepAngle = 360f * p,
            useCenter = false,
            topLeft = Offset(trait / 2f, trait / 2f),
            size = Size(size.width - trait, size.height - trait),
            style = Stroke(width = trait, cap = StrokeCap.Round),
        )
    }
}

/**
 * Le côté du logo.
 *
 * L'emblème n'en occupe que la largeur : la source lui laisse en haut et en bas
 * une marge d'environ un sixième, héritée de l'icône de lanceur qu'elle sert
 * aussi.
 */
private val COTE_LOGO = 168.dp

/**
 * Le diamètre de l'anneau.
 *
 * L'emblème tient dans un cercle qui fait un peu plus de la moitié du côté de
 * son carré, soit 85 dp ici ; l'anneau passe treize dp au large, ce qui le
 * sépare du dessin sans l'en éloigner.
 */
private val DIAMETRE_ANNEAU = 196.dp

private val TRAIT_ANNEAU = 3.dp

/** L'écart entre l'anneau et le nom. Il se compte depuis l'anneau, pas le logo. */
private val ECART_NOM = 26.dp

/** De combien le nom monte en se révélant. */
private val MONTEE_NOM = 14.dp

private const val OPACITE_VERSION = 0.35f

/** Le bleu du flocon et l'orange de la flamme, relevés sur l'emblème. */
private val FROID = Color(0xFF18B4FC)

private val CHAUD = Color(0xFFFC5400)

private val MINIMUM_MS = Demarrage.MINIMUM.inWholeMilliseconds.toInt()

private val FONDU_MS = Demarrage.FONDU.inWholeMilliseconds.toInt()

@Preview(showBackground = true, backgroundColor = 0xFF081520)
@Composable
private fun ApercuDemarrage() {
    FrigoProTheme(sombre = true) {
        ContenuDemarrage(ecoule = { Demarrage.MINIMUM }, version = "0.2.0 (38)")
    }
}
