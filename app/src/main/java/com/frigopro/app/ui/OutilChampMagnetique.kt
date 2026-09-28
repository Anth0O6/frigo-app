package com.frigopro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.AllureChamp
import com.frigopro.app.data.ChampMagnetique
import com.frigopro.app.data.LectureChamp
import com.frigopro.app.data.NiveauChamp
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffre
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * L'outil « Champ magnétique » : ce que le magnétomètre sait dire, et rien de
 * plus.
 *
 * Il répond à quatre questions du terrain : cette bobine de solénoïde est-elle
 * excitée, ce contacteur a-t-il collé, où est passé l'aimant de ce contact de
 * porte, et ce câble débite-t-il. Les quatre se posent capot fermé, et y répondre
 * épargne de sortir le multimètre ou d'ouvrir une armoire sous tension.
 *
 * **Il ne donne pas le sens de rotation d'un moteur**, et il le dit lui-même.
 * Voir [ChampMagnetique] pour la raison — elle tient à la cadence du capteur, et
 * aucun réglage n'y changerait quoi que ce soit. Le dire *dans* l'outil plutôt
 * que nulle part suit la règle du projet sur les messages d'échec : constater une
 * impossibilité sans ouvrir de porte ne vaut rien, donc celui-ci dit du même
 * souffle comment un sens de rotation se contrôle pour de vrai.
 */
@Composable
fun OutilChampMagnetique(modifier: Modifier = Modifier) {
    val etat = rememberChampMagnetique()
    val lecture = etat.lecture

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            etat.capteurAbsent -> Encart(
                texte = "Cet appareil n'a pas de magnétomètre. L'outil ne peut rien mesurer " +
                    "ici — c'est le capteur qui manque, pas un réglage.",
                icone = Icons.Filled.Warning,
                alerte = true,
            )

            lecture == null -> Encart(
                texte = "Mesure en cours… Tenez le téléphone immobile une seconde.",
            )

            else -> {
                CarteMesureChamp(lecture = lecture)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoutonPlein(
                        texte = if (lecture.ambiantReleve) "Reprendre l'ambiant" else "Relever l'ambiant",
                        onClick = etat.onReleverAmbiant,
                        modifier = Modifier.weight(1.4f),
                    )
                    if (lecture.ambiantReleve) {
                        BoutonContour(
                            texte = "Oublier",
                            onClick = etat.onOublierAmbiant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    text = "Relevez l'ambiant à l'écart de ce que vous cherchez, puis " +
                        "approchez : dans une armoire, la tôle décale le champ terrestre de " +
                        "plusieurs dizaines de microteslas, et un champ faible s'y perdrait.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionCeQuIlRepere()
                SectionSensDeRotation()
                EspaceVertical(24)
            }
        }
    }
}

/** La mesure : le dépassement en grand, la jauge, et ce qu'on en dit. */
@Composable
private fun CarteMesureChamp(lecture: LectureChamp) {
    val statuts = LocalStatuts.current
    val teinte = when (lecture.niveau) {
        NiveauChamp.AUCUN -> MaterialTheme.colorScheme.onSurfaceVariant
        NiveauChamp.SATURE -> statuts.aValider
        else -> MaterialTheme.colorScheme.primary
    }

    Carte(relief = true, liseré = teinte) {
        Text(
            text = lecture.niveau.libelle,
            style = MaterialTheme.typography.titleMedium,
            color = teinte,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                // Le **dépassement** et non le total, parce que c'est lui qui
                // répond à la question : le champ terrestre est là de toute
                // façon, et l'afficher en grand ferait lire « 52 µT » devant une
                // bobine morte comme devant rien du tout.
                text = signe(lecture.exces),
                style = StyleChiffre,
                color = teinte,
            )
            Text(
                text = " µT au-delà de l'ambiant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
            )
        }
        JaugeChamp(exces = lecture.exces, teinte = teinte)
        Text(
            text = lecture.interpretation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TuileChiffre(
                valeur = "${lecture.amplitude.roundToInt()}",
                libelle = "µT mesurés",
                modifier = Modifier.weight(1f),
            )
            TuileChiffre(
                valeur = "${(lecture.amplitude - lecture.exces).roundToInt()}",
                libelle = if (lecture.ambiantReleve) "ambiant relevé" else "ambiant supposé",
                modifier = Modifier.weight(1f),
            )
            TuileChiffre(
                valeur = when (lecture.allure) {
                    AllureChamp.CONSTANTE -> "="
                    AllureChamp.FLUCTUANTE -> "∼"
                    AllureChamp.INDETERMINEE -> "—"
                },
                libelle = when (lecture.allure) {
                    AllureChamp.CONSTANTE -> "constant"
                    AllureChamp.FLUCTUANTE -> "alternatif ?"
                    AllureChamp.INDETERMINEE -> "indéterminé"
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * La jauge du dépassement, en échelle **logarithmique**.
 *
 * Ce n'est pas un raffinement : ce qu'on cherche va de huit microteslas — la
 * limite du bruit d'une main — à plusieurs milliers au contact d'un aimant, soit
 * plus de deux décades. Une échelle linéaire aurait collé au ras du zéro tout ce
 * qui se passe en dessous de cent, c'est-à-dire exactement la plage où l'on
 * balaie une armoire à la recherche d'un maximum.
 *
 * La valeur absolue du dépassement : un champ qui **s'oppose** à l'ambiant le
 * fait baisser, et un aimant présenté dans l'autre sens laisserait sinon la
 * jauge à zéro.
 */
@Composable
private fun JaugeChamp(exces: Double, teinte: Color) {
    val part = partDeJauge(abs(exces))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(part)
                .fillMaxHeight()
                .background(teinte),
        )
    }
}

/** Zéro au seuil de détection, un au seuil du champ fort. Voir [JaugeChamp]. */
internal fun partDeJauge(exces: Double): Float {
    if (exces <= ChampMagnetique.SEUIL_DETECTION) return 0f
    val decades = ln(ChampMagnetique.SEUIL_FORT / ChampMagnetique.SEUIL_DETECTION)
    val part = ln(exces / ChampMagnetique.SEUIL_DETECTION) / decades
    return part.coerceIn(0.0, 1.0).toFloat()
}

/** « +340 », « −12 » : le signe compte, un champ peut s'opposer à l'ambiant. */
private fun signe(valeur: Double): String {
    val arrondi = valeur.roundToInt()
    return if (arrondi > 0) "+$arrondi" else "$arrondi"
}

/** Ce qu'il repère, et ce qu'il repère mal — les deux comptent autant. */
@Composable
private fun SectionCeQuIlRepere() {
    Section(intitule = "Ce qu'il repère", espacement = 8.dp) {
        LigneRepere(
            quoi = "Une bobine alimentée",
            comment = "Vanne solénoïde, vanne 4 voies, bobine de contacteur : au contact " +
                "du corps de la bobine, le champ est net et il fluctue.",
        )
        LigneRepere(
            quoi = "Un contacteur collé",
            comment = "À travers le capot : la bobine excitée se distingue de la même " +
                "bobine au repos, ce qui dit si l'ordre est arrivé.",
        )
        LigneRepere(
            quoi = "Un aimant",
            comment = "Contact de porte, flotteur, contrôleur de débit à lame souple. " +
                "Champ fort et parfaitement constant — c'est la signature la plus nette " +
                "des quatre, et le capteur sature souvent au contact.",
        )
        LigneRepere(
            quoi = "Un câble qui débite",
            comment = "Mal, et il faut le savoir : un câble à deux conducteurs annule " +
                "presque tout son champ, l'aller et le retour s'opposant. Il se repère " +
                "sur un conducteur seul, ou sur une forte charge. Un circuit au repos ne " +
                "se voit pas du tout — ce n'est donc jamais une preuve d'absence de " +
                "tension.",
        )
    }
}

@Composable
private fun LigneRepere(quoi: String, comment: String) {
    Carte {
        Text(text = quoi, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = comment,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Ce que l'outil ne fait pas, et ce qu'il faut faire à la place.
 *
 * Elle est dans l'outil et non dans une fiche à part, parce que c'est là que la
 * question se pose : quelqu'un qui ouvre un détecteur de champ magnétique devant
 * une pompe cherche souvent le sens de rotation, et le laisser repartir avec
 * « ça ne marche pas » serait la moitié d'une réponse.
 *
 * L'ordre est celui du risque, et non celui de la fréquence : le compresseur
 * d'abord, parce que c'est le seul des deux où se tromper coûte la machine.
 */
@Composable
private fun SectionSensDeRotation() {
    Section(intitule = "Le sens de rotation", espacement = 8.dp) {
        Encart(
            texte = "Cet outil ne le donne pas, et aucune version ne le donnera. Le champ " +
                "d'un moteur tri fait cinquante tours par seconde ; le magnétomètre d'un " +
                "téléphone mesure cinquante à cent fois par seconde, et le sens est perdu " +
                "avant d'arriver au logiciel. Les applications qui montrent un disque qui " +
                "tourne animent du bruit.",
            icone = Icons.Filled.Warning,
            alerte = true,
        )
        LigneRepere(
            quoi = "Compresseur scroll ou à vis",
            comment = "Ne jamais essayer en démarrant : à l'envers, il n'établit aucune " +
                "pression et se détruit en quelques secondes. Le sens se contrôle avant le " +
                "premier démarrage, au contrôleur d'ordre des phases sur " +
                "l'alimentation, ou en respectant le repérage relevé au démontage.",
        )
        LigneRepere(
            quoi = "Pompe ou ventilateur",
            comment = "Un démarrage bref ne casse rien : la flèche est moulée sur le corps, " +
                "et on regarde tourner l'accouplement ou l'hélice. À l'envers, une pompe " +
                "centrifuge refoule quand même — beaucoup moins, d'où l'intérêt de " +
                "comparer la pression aux deux branchements plutôt que de se fier au bruit.",
        )
        LigneRepere(
            quoi = "Ce qu'il faut dans la camionnette",
            comment = "Un contrôleur d'ordre des phases se raccorde sur les trois phases et " +
                "tranche en une seconde. C'est l'appareil que les indicateurs à disque " +
                "imitent — et c'est le raccordement qui fait le travail, jamais la " +
                "proximité.",
        )
    }
}
