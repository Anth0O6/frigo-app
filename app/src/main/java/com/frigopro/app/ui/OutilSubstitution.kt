package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.AmpleurSubstitution
import com.frigopro.app.data.EcartFluides
import com.frigopro.app.data.Fluides
import com.frigopro.app.data.Substitution
import com.frigopro.app.data.Substitutions
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * Par quoi remplacer un fluide, et ce que la conversion demande.
 *
 * ## Ce que l'écran répond, et ce qu'il refuse de répondre
 *
 * La question du terrain est double — « par quoi ? » et surtout « pourquoi
 * celui-là plutôt que l'autre ? » —, et c'est la seconde qui manque partout
 * ailleurs : un tableau de correspondances donne une liste de codes, sans dire
 * ce qui les sépare. Chaque piste porte donc ce qu'elle **change** : le GWP, la
 * classe de sécurité, le glissement, l'huile.
 *
 * Ce qu'il refuse de répondre : si *cette* machine l'accepte. Un tableau ne
 * connaît ni le compresseur, ni son année, ni sa garantie, et ce sont ces
 * trois-là qui décident. L'avertissement est donc en **tête** et non en pied de
 * page — un renvoi au constructeur qu'on lit après avoir choisi ne sert plus à
 * rien.
 *
 * ## L'ordre de lecture
 *
 * L'inflammabilité passe avant le GWP, comme sur la fiche fluide et pour la même
 * raison : le GWP décide d'une paperasse, la classe décide de la façon de
 * travailler et de ce qui peut prendre feu.
 */
@Composable
fun OutilSubstitution() {
    var fluide by remember { mutableStateOf("") }

    val pistes = Substitutions.pour(fluide)
    val nom = Fluides.normaliser(fluide)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChampFluide(
            valeur = fluide,
            onValeur = { fluide = it },
            modifier = Modifier.fillMaxWidth(),
            libelle = "Fluide en place",
        )

        if (fluide.isBlank()) {
            Encart(
                texte = "Choisissez le fluide actuellement dans la machine pour voir par " +
                    "quoi il se remplace, et ce que chaque conversion demande.",
            )
            return@Column
        }

        // L'avertissement d'abord : lu après avoir choisi, il ne sert plus à rien.
        Encart(texte = Substitutions.AVERTISSEMENT, icone = Icons.Filled.Warning, alerte = true)

        if (pistes.isEmpty()) {
            Encart(
                texte = "Aucune piste de remplacement n'est connue pour " +
                    "« ${Fluides.afficher(nom)} ». Rien n'est rapproché par ressemblance : " +
                    "un fluide proposé au hasard, c'est un compresseur qui le paie.",
                icone = Icons.Filled.Warning,
                alerte = true,
            )
            return@Column
        }

        Text(
            text = if (pistes.size == 1) "1 piste" else "${pistes.size} pistes",
            style = StyleChiffrePetit,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        pistes.forEach { piste ->
            CartePiste(piste = piste, ecart = Substitutions.ecart(piste))
        }
    }
}

/**
 * Une piste : le fluide, ce qu'il change, et ce qu'il demande.
 *
 * Les trois avertissements — inflammabilité, glissement, vidange — sont des
 * **puces** et non du texte courant : ce sont les trois choses qu'on compare
 * d'une piste à l'autre, et les comparer demande de les trouver au même endroit
 * sur chaque carte.
 */
@Composable
private fun CartePiste(piste: Substitution, ecart: EcartFluides) {
    val statuts = LocalStatuts.current

    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = libelleFluide(piste.remplacant),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ecart.classeRemplacant?.let {
                        Text(
                            text = it.resume,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.inflammable) statuts.urgence else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                GwpCompare(ecart = ecart)
            }

            // Les trois marqueurs, dans l'ordre de ce qu'ils coûtent : la façon
            // de travailler, puis la façon de charger, puis le chantier.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ecart.devientInflammable) {
                    PuceAlerte("Devient A2L", Icons.Filled.LocalFireDepartment, statuts.urgence)
                }
                if (ecart.apporteDuGlissement) {
                    PuceAlerte("Glissement", Icons.Filled.Waves, statuts.aValider)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = piste.ampleur.libelle,
                style = MaterialTheme.typography.titleSmall,
                color = when (piste.ampleur) {
                    AmpleurSubstitution.SANS_VIDANGE -> statuts.termine
                    AmpleurSubstitution.VIDANGE_POE -> statuts.aValider
                },
            )
            Text(
                text = piste.ampleur.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            piste.remarques.forEach { remarque ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = remarque,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Le glissement chiffré en dernier : c'est un détail de mise en
            // œuvre, utile une fois la piste retenue, pas au moment de choisir.
            ecart.glissementRemplacantK?.takeIf { it >= 1.0 }?.let { glissement ->
                Text(
                    text = "Glissement d'environ ${Nombres.enTexte(glissement)} K " +
                        "à basse pression : charge en phase liquide, et pas de " +
                        "complément après fuite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Le GWP avant et après, et de combien il baisse.
 *
 * La hausse est affichée comme telle plutôt que tue : une conversion de
 * prolongation — un R-22 vers un R-422D — alourdit le bilan, et le cacher
 * laisserait croire que toute conversion est un progrès.
 */
@Composable
private fun GwpCompare(ecart: EcartFluides) {
    val statuts = LocalStatuts.current
    val apres = ecart.gwpRemplacant ?: return
    val baisse = ecart.baisseGwp

    Column(horizontalAlignment = Alignment.End) {
        Text(text = "GWP $apres", style = StyleChiffrePetit)
        if (baisse != null) {
            Text(
                text = if (baisse >= 0.0) {
                    "− ${Nombres.enTexte(baisse)} %"
                } else {
                    "+ ${Nombres.enTexte(-baisse)} %"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (baisse >= 0.0) statuts.termine else statuts.urgence,
            )
        }
    }
}

@Composable
private fun PuceAlerte(texte: String, icone: ImageVector, couleur: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = couleur,
            modifier = Modifier.size(14.dp),
        )
        Puce(texte = texte, couleur = couleur, fond = couleur.copy(alpha = 0.16f))
    }
}
