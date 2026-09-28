package com.frigopro.app.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.frigopro.app.data.ChampMagnetique
import com.frigopro.app.data.FenetreChamp
import com.frigopro.app.data.LectureChamp
import com.frigopro.app.data.MesureChamp
import kotlinx.coroutines.delay

/**
 * Ce que l'écran du champ magnétique reçoit.
 *
 * `lecture` vaut `null` la première fraction de seconde, le temps que la fenêtre
 * se remplisse — et l'écran le dit plutôt que d'afficher un zéro qui passerait
 * pour une mesure.
 */
data class EtatChamp(
    val lecture: LectureChamp? = null,
    /** Aucun magnétomètre sur cet appareil : l'outil ne peut rien faire. */
    val capteurAbsent: Boolean = false,
    val onReleverAmbiant: () -> Unit = {},
    val onOublierAmbiant: () -> Unit = {},
)

/**
 * La seule fonction de l'application qui parle au magnétomètre.
 *
 * Même motif qu'`ItineraireRelais`, seule classe à ouvrir une connexion, et que
 * `StockagePhotos`, seule à connaître `BitmapFactory` : le code qui touche la
 * plateforme est rassemblé en un endroit, et l'arithmétique qui décide de
 * quelque chose (`ChampMagnetique`) reste éprouvable sans téléphone.
 *
 * **Aucune permission n'est demandée**, et c'est une propriété du magnétomètre
 * et non un tour de passe-passe : Android ne protège ni la boussole ni les
 * capteurs de mouvement. L'application en reste donc à ses deux permissions.
 *
 * Trois décisions valent d'être écrites :
 *
 * - **Le capteur n'est écouté que tant que l'outil est ouvert.** Le
 *   `DisposableEffect` le libère en quittant l'écran, sans quoi il continuerait
 *   de réveiller le processeur cinquante fois par seconde dans la poche de
 *   quelqu'un.
 * - **L'affichage n'est pas rafraîchi à la cadence du capteur.** Les mesures
 *   s'accumulent dans un tampon ordinaire, et une boucle publie la fenêtre
 *   toutes les [PERIODE_PUBLICATION_MS]. Recomposer cinquante fois par seconde
 *   pour un chiffre qu'on lit à l'œil serait le genre de gaspillage que l'écran
 *   de démarrage évite déjà en ne redessinant que ses lambdas de dessin.
 * - **Tout se passe sur le fil principal**, grâce au `Handler` passé à
 *   `registerListener` : le tampon n'a alors besoin d'aucune synchronisation, là
 *   où le fil par défaut du capteur en aurait imposé une.
 */
@Composable
fun rememberChampMagnetique(): EtatChamp {
    val contexte = LocalContext.current
    val gestionnaire = remember(contexte) {
        contexte.getSystemService(SensorManager::class.java)
    }
    val capteur = remember(gestionnaire) {
        gestionnaire?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    // Le tampon n'est pas un état Compose : il change cinquante fois par seconde
    // et rien ne doit se recomposer pour autant. Seule la fenêtre publiée l'est.
    val tampon = remember { ArrayDeque<MesureChamp>() }
    var fenetre by remember { mutableStateOf<FenetreChamp?>(null) }
    var reference by remember { mutableStateOf<FenetreChamp?>(null) }

    // Pas de magnétomètre — un téléphone d'entrée de gamme, ou une tablette : le
    // dire franchement plutôt que d'attendre indéfiniment une première mesure.
    if (capteur == null) return EtatChamp(capteurAbsent = true)

    DisposableEffect(capteur) {
        val ecouteur = object : SensorEventListener {
            override fun onSensorChanged(evenement: SensorEvent) {
                val valeurs = evenement.values ?: return
                if (valeurs.size < 3) return
                tampon.addLast(
                    MesureChamp(
                        x = valeurs[0].toDouble(),
                        y = valeurs[1].toDouble(),
                        z = valeurs[2].toDouble(),
                    ),
                )
                while (tampon.size > ECHANTILLONS_FENETRE) tampon.removeFirst()
            }

            override fun onAccuracyChanged(capteur: Sensor?, precision: Int) = Unit
        }
        gestionnaire?.registerListener(
            ecouteur,
            capteur,
            // La cadence la plus rapide qu'on puisse demander sans réclamer
            // l'impossible : environ cinquante mesures par seconde, ce qui est le
            // plafond d'un magnétomètre de téléphone.
            SensorManager.SENSOR_DELAY_GAME,
            Handler(Looper.getMainLooper()),
        )
        onDispose {
            gestionnaire?.unregisterListener(ecouteur)
            tampon.clear()
        }
    }

    LaunchedEffect(capteur) {
        while (true) {
            fenetre = FenetreChamp.de(tampon.toList())
            delay(PERIODE_PUBLICATION_MS)
        }
    }

    val courante = fenetre
    return EtatChamp(
        lecture = courante?.let {
            ChampMagnetique.lire(
                fenetre = it,
                reference = reference,
                porteeCapteur = capteur.maximumRange.toDouble(),
            )
        },
        onReleverAmbiant = { reference = fenetre },
        onOublierAmbiant = { reference = null },
    )
}

/**
 * Une seconde de mesures, à la cadence d'un magnétomètre.
 *
 * Assez pour que l'écart-type veuille dire quelque chose, assez court pour que
 * la valeur suive la main qui balaie une armoire à la recherche du maximum.
 */
private const val ECHANTILLONS_FENETRE = 64

/** Sept rafraîchissements par seconde : l'œil suit, le processeur respire. */
private const val PERIODE_PUBLICATION_MS = 150L
