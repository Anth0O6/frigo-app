package com.frigopro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.initialesDe
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.time.LocalTime

/**
 * Un créneau posé sur la frise : l'intervention, et où la dessiner.
 *
 * Le calcul est séparé du dessin pour qu'il soit éprouvable sans Compose :
 * une intervention mal placée sur la frise est un bug d'arithmétique, pas
 * d'affichage.
 */
internal data class Creneau(
    val ligne: LigneTournee,
    /** Distance depuis le haut de la frise. */
    val haut: Dp,
    val hauteur: Dp,
)

/** L'amplitude horaire que la frise couvre, bornes incluses. */
internal data class Amplitude(val premiereHeure: Int, val derniereHeure: Int) {

    val heures: Int get() = derniereHeure - premiereHeure + 1
}

/**
 * La hauteur d'une heure sur la frise.
 *
 * 64 dp tient une douzaine d'heures sur un écran de téléphone tout en laissant
 * un créneau d'une demi-heure rester lisible — en dessous, le client et le type
 * d'intervention ne tiennent plus sur deux lignes.
 */
private val HauteurHeure = 64.dp

/** Un créneau ne descend jamais sous cette hauteur, même très court. */
private val HauteurMinimale = 40.dp

/** La colonne des heures, à gauche de la frise. */
private val LargeurHeures = 46.dp

/**
 * L'amplitude horaire à dessiner.
 *
 * Elle s'adapte à la journée plutôt que d'être figée de 7 h à 19 h : une
 * astreinte à 5 h du matin ou une fin de chantier à 21 h sortiraient d'une
 * frise fixe, et une intervention invisible sur le planning est pire qu'un
 * planning un peu plus long. Les bornes par défaut restent celles d'une
 * journée ordinaire, pour qu'un jour vide ou presque ne montre pas une frise
 * d'une heure.
 */
internal fun amplitudeDe(interventions: List<Intervention>): Amplitude {
    if (interventions.isEmpty()) return Amplitude(HEURE_DEBUT_PAR_DEFAUT, HEURE_FIN_PAR_DEFAUT)
    val debut = interventions.minOf { it.heure.hour }
    val fin = interventions.maxOf { intervention ->
        val heureFin = finDe(intervention)
        if (heureFin.minute > 0) heureFin.hour + 1 else heureFin.hour
    }
    return Amplitude(
        premiereHeure = minOf(debut, HEURE_DEBUT_PAR_DEFAUT),
        // Une intervention qui mord sur minuit est bornée à 23 h : la frise ne
        // passe pas au lendemain, qui a sa propre journée.
        derniereHeure = maxOf(minOf(fin, HEURE_MAXIMALE), HEURE_FIN_PAR_DEFAUT),
    )
}

private const val HEURE_DEBUT_PAR_DEFAUT = 7

private const val HEURE_FIN_PAR_DEFAUT = 18

/** La frise s'arrête à la journée : minuit appartient au lendemain. */
private const val HEURE_MAXIMALE = 23

/** L'heure de fin prévue, d'après l'heure de début et la durée. */
internal fun finDe(intervention: Intervention): LocalTime =
    intervention.heure.plusMinutes(intervention.dureeMin.toLong())

/** « 08:00 – 09:30 » : le créneau prévu, tel que la frise l'annonce. */
internal fun creneauDe(intervention: Intervention): String =
    "${intervention.heure.format(FORMAT_HEURE)} – ${finDe(intervention).format(FORMAT_HEURE)}"

/** Les créneaux d'une journée, placés sur la frise. */
internal fun creneauxDe(lignes: List<LigneTournee>, amplitude: Amplitude): List<Creneau> =
    lignes.map { ligne ->
        val depuisLeDebut = (ligne.intervention.heure.hour - amplitude.premiereHeure) * 60 +
            ligne.intervention.heure.minute
        Creneau(
            ligne = ligne,
            haut = HauteurHeure * (depuisLeDebut / 60f),
            hauteur = maxOf(HauteurHeure * (ligne.intervention.dureeMin / 60f), HauteurMinimale),
        )
    }

/**
 * La frise horaire d'une journée : le temps en hauteur, les créneaux dessus.
 *
 * C'est la vue qui répond à « où puis-je caser ce client ? », là où la liste
 * répond à « qu'est-ce qui vient ensuite ? ». Les deux existent parce que ce
 * sont deux questions différentes, et que la réponse à la première se lit dans
 * les **trous** — qu'une liste ne montre pas.
 *
 * Les créneaux sont posés en décalage absolu dans un `Box` plutôt qu'empilés :
 * c'est leur heure qui décide de leur position, pas leur rang dans la liste,
 * et deux interventions qui se chevauchent doivent se chevaucher à l'écran.
 * C'est même le principal intérêt de la vue : un chevauchement est une erreur
 * de planification, et il faut qu'elle se voie.
 */
@Composable
internal fun FriseHoraire(
    lignes: List<LigneTournee>,
    maintenant: LocalTime?,
    onOuvrir: (Intervention) -> Unit,
    modifier: Modifier = Modifier,
) {
    val amplitude = amplitudeDe(lignes.map { it.intervention })
    val creneaux = creneauxDe(lignes, amplitude)
    Box(modifier = modifier.height(HauteurHeure * amplitude.heures)) {
        // Les graduations d'abord : tout le reste se lit par-dessus.
        repeat(amplitude.heures) { rang ->
            val heure = amplitude.premiereHeure + rang
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = HauteurHeure * rang),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "%02d:00".format(heure),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(LargeurHeures)
                        .offset(y = (-6).dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }

        // L'heure qu'il est, en trait plein : c'est ce qui dit d'un coup d'œil
        // si l'on est en avance ou en retard sur la journée. Elle n'est dessinée
        // que sur le jour courant, la question n'ayant pas de sens ailleurs.
        if (maintenant != null) {
            val minutes = (maintenant.hour - amplitude.premiereHeure) * 60 + maintenant.minute
            if (minutes >= 0 && minutes <= amplitude.heures * 60) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = HauteurHeure * (minutes / 60f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = maintenant.format(FORMAT_HEURE),
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.width(LargeurHeures),
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.extraSmall),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.5.dp)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
        }

        creneaux.forEach { creneau ->
            CarteCreneau(
                creneau = creneau,
                onOuvrir = { onOuvrir(creneau.ligne.intervention) },
                modifier = Modifier
                    .offset(x = LargeurHeures, y = creneau.haut)
                    .padding(end = 2.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * Un créneau sur la frise.
 *
 * Les deux lignes de texte sont coupées plutôt que réduites : un créneau d'une
 * demi-heure est physiquement petit, et laisser le texte décider de la hauteur
 * mentirait sur la durée — or c'est la seule chose que la frise promette.
 */
@Composable
private fun CarteCreneau(creneau: Creneau, onOuvrir: () -> Unit, modifier: Modifier = Modifier) {
    val intervention = creneau.ligne.intervention
    val couleur = couleurStatut(intervention)
    Carte(
        modifier = modifier.height(creneau.hauteur),
        liseré = couleur,
        forme = MaterialTheme.shapes.medium,
        onClick = onOuvrir,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = intervention.client,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = creneauDe(intervention) + sousTitre(intervention),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Les initiales du technicien, quand il y en a un : c'est ce qui
            // distingue « ma journée » de « la journée de l'équipe ».
            if (intervention.technicienNom.isNotBlank()) {
                Puce(texte = initialesDe(intervention.technicienNom), couleur = couleur, chiffre = true)
            }
        }
    }
}

private fun sousTitre(intervention: Intervention): String =
    intervention.typeLibelle.ifBlank { intervention.ville }.let { if (it.isBlank()) "" else " · $it" }
