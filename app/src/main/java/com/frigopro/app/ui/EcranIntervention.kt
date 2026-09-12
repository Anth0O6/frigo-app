package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.CategoriePhoto
import com.frigopro.app.data.Photo
import com.frigopro.app.data.PointChecklist
import com.frigopro.app.data.SensFluide
import com.frigopro.app.data.enChrono
import com.frigopro.app.ui.composants.BarreActions
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.theme.AValider
import com.frigopro.app.ui.theme.Urgence
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.time.Duration

/**
 * Tout ce que l'écran d'une intervention sait faire.
 *
 * Les rappels sont réunis dans un objet plutôt qu'énumérés en paramètres :
 * il y en a une quinzaine, et une signature de quinze lambdas ne se relit pas,
 * ne se réordonne pas sans risque et rend chaque aperçu illisible.
 */
data class ActionsIntervention(
    val onFermer: () -> Unit = {},
    val onOnglet: (OngletIntervention) -> Unit = {},
    val onBasculerChrono: () -> Unit = {},
    val onBp: (Double?) -> Unit = {},
    val onHp: (Double?) -> Unit = {},
    val onSurchauffe: (Double?) -> Unit = {},
    val onSousRefroidissement: (Double?) -> Unit = {},
    val onOuvrirDepannage: () -> Unit = {},
    /** Marque la courbe d'un fluide vérifiée, ou retire la marque. */
    val onVerifierFluide: (String, Boolean) -> Unit = { _, _ -> },
    val onMouvement: (SensFluide, Double, String) -> Unit = { _, _, _ -> },
    val onSupprimerMouvement: (String) -> Unit = {},
    val onAjouterPiece: (String, String, Double) -> Unit = { _, _, _ -> },
    val onSupprimerPiece: (String) -> Unit = {},
    val onPhotographier: (CategoriePhoto) -> Unit = {},
    val onChoisirImage: (CategoriePhoto) -> Unit = {},
    val onAgrandir: (Photo) -> Unit = {},
    val onTravaux: (String) -> Unit = {},
    val onSigner: () -> Unit = {},
    val onEffacerSignature: () -> Unit = {},
    val onCloturer: () -> Unit = {},
    val onBasculerPoint: (PointChecklist) -> Unit = {},
    val onCreerDevis: () -> Unit = {},
)

/**
 * L'écran d'une intervention : ce qui s'est vraiment passé sur place.
 *
 * Quatre volets, dans l'ordre où le travail se fait : on relève, on pose des
 * pièces, on photographie, on rend compte. Le chronomètre, lui, est visible
 * depuis les quatre — il tourne pendant tout ce temps, et c'est la valeur
 * qu'on oublie le plus facilement d'arrêter.
 */
@Composable
fun EcranIntervention(
    etat: EtatIntervention,
    ecoule: Duration,
    onglet: OngletIntervention,
    actions: ActionsIntervention,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    /** Les fluides dont la courbe de saturation a été contrôlée. */
    fluidesVerifies: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { EnTeteIntervention(etat = etat, ecoule = ecoule, actions = actions) },
        bottomBar = { BarreClotureIntervention(etat = etat, actions = actions) },
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            RangeePastilles(
                options = OngletIntervention.entries,
                retenue = onglet,
                libelle = { it.libelle },
                onChoisir = actions.onOnglet,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MargeEcran, vertical = 12.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                when (onglet) {
                    OngletIntervention.FICHE -> OngletFiche(etat = etat, actions = actions)
                    OngletIntervention.RELEVES -> OngletReleves(
                        etat = etat,
                        ecoule = ecoule,
                        actions = actions,
                        fluidesVerifies = fluidesVerifies,
                    )
                    OngletIntervention.PIECES -> OngletPieces(etat = etat, actions = actions)
                    OngletIntervention.PHOTOS -> OngletPhotos(
                        etat = etat,
                        actions = actions,
                        chargerPhoto = chargerPhoto,
                    )

                    OngletIntervention.RAPPORT -> OngletRapport(
                        etat = etat,
                        ecoule = ecoule,
                        actions = actions,
                        chargerPhoto = chargerPhoto,
                    )
                }
            }
        }
    }
}

/**
 * L'en-tête : où l'on est, sur quelle machine, et depuis combien de temps.
 *
 * Le chronomètre y est en ambre quand il tourne : c'est la couleur de
 * « en cours » dans toute l'application, et il faut qu'un coup d'œil suffise
 * à voir qu'on est en train de compter du temps.
 */
@Composable
private fun EnTeteIntervention(
    etat: EtatIntervention,
    ecoule: Duration,
    actions: ActionsIntervention,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = MargeEcran, end = MargeEcran, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoutonCarre(
                    icone = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Revenir à la tournée",
                    onClick = actions.onFermer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = etat.intervention.client,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sousTitre = listOf(
                        etat.intervention.equipementNom,
                        etat.equipement?.designation.orEmpty(),
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sousTitre.isNotEmpty()) {
                        Text(
                            text = sousTitre,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!etat.intervention.chrono.vierge) {
                    val enMarche = etat.intervention.chrono.enMarche
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (enMarche) {
                            Urgence.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Text(
                            text = ecoule.enChrono(),
                            style = StyleChiffrePetit,
                            color = if (enMarche) Urgence else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * La barre du bas : le chronomètre, et la clôture.
 *
 * Le bouton de gauche porte l'action la plus fréquente du terrain — lancer ou
 * arrêter le temps — et celui de droite l'action terminale. Une intervention
 * déjà close n'offre plus que de rouvrir son compte-rendu : la reclôturer
 * n'aurait aucun sens, et lui réattribuerait un numéro.
 */
@Composable
private fun BarreClotureIntervention(etat: EtatIntervention, actions: ActionsIntervention) {
    val chrono = etat.intervention.chrono
    BarreActions {
        BoutonPlein(
            texte = if (chrono.enMarche) "Pause" else if (chrono.vierge) "Démarrer" else "Reprendre",
            onClick = actions.onBasculerChrono,
            modifier = Modifier.weight(1f),
            couleur = if (chrono.enMarche) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.primary
            },
            surCouleur = if (chrono.enMarche) Urgence else MaterialTheme.colorScheme.onPrimary,
        )
        BoutonPlein(
            texte = when {
                etat.intervention.numero.isNotEmpty() -> "Compte-rendu"
                // Le reste à cocher est dit sur le bouton plutôt que laissé à
                // découvrir : la checklist porte des obligations, et s'en
                // apercevoir après avoir quitté le site ne sert plus à rien.
                // Rien n'est pour autant bloqué — une intervention peut
                // légitimement se clore sans que tout s'applique, et
                // l'application n'a pas à en juger.
                !etat.checklistFinie && etat.checklist.isNotEmpty() ->
                    "Clôturer · ${etat.checklist.size - etat.pointsFaits} à cocher"

                else -> "Clôturer"
            },
            onClick = if (etat.intervention.numero.isEmpty()) {
                actions.onCloturer
            } else {
                { actions.onOnglet(OngletIntervention.RAPPORT) }
            },
            modifier = Modifier.weight(1.4f),
        )
    }
}

/** Un espace vertical, pour aérer une colonne sans multiplier les `Spacer`. */
@Composable
internal fun EspaceVertical(hauteur: Int = 14) {
    Spacer(modifier = Modifier.height(hauteur.dp))
}
