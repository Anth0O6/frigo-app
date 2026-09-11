package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Cause
import com.frigopro.app.data.Diagnostic
import com.frigopro.app.data.Fluides
import com.frigopro.app.data.Releve
import com.frigopro.app.ui.composants.BoutonCarre
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.theme.BleuFroid
import com.frigopro.app.ui.theme.Cyan
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * L'aide au dépannage : ce que les relevés suggèrent, et quoi vérifier.
 *
 * L'écran affiche d'abord le relevé qui a servi — on doit pouvoir vérifier sur
 * quoi l'application se fonde avant de la croire — puis les causes, numérotées
 * par ordre de probabilité, chacune avec le contrôle qui tranche.
 *
 * La première cause est mise en avant par sa couleur, les autres non : c'est
 * par là qu'il faut commencer, pas la seule chose à regarder.
 */
@Composable
fun EcranDepannage(
    diagnostic: Diagnostic,
    releve: Releve?,
    fluide: String,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargeEcran, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoutonCarre(
                    icone = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Revenir à l'intervention",
                    onClick = onFermer,
                )
                Column {
                    Text(text = "Aide au dépannage", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "À partir de vos relevés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Carte(relief = true) {
                Text(
                    text = "SYMPTÔME RETENU",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = diagnostic.symptome.libelle,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (releve != null) {
                    Text(
                        text = resumeReleve(releve, fluide),
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            diagnostic.causes.forEachIndexed { index, cause ->
                LigneCause(rang = index + 1, cause = cause, premiere = index == 0)
            }

            Encart(
                texte = "Ce sont des pistes, pas un verdict : chacune se confirme ou " +
                    "s'écarte par le contrôle indiqué. Une pièce changée sans contrôle " +
                    "est une pièce changée au hasard.",
            )
            EspaceVertical(24)
        }
    }
}

@Composable
private fun LigneCause(rang: Int, cause: Cause, premiere: Boolean) {
    val accent = if (premiere) Cyan else BleuFroid
    Carte(contour = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = accent.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "$rang", style = StyleChiffrePetit, color = accent)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = cause.intitule, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = cause.controle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** « BP 2,4 · HP 14,8 · SH 11,2 K · SC 4,0 K · R452A ». */
private fun resumeReleve(releve: Releve, fluide: String): String = buildList {
    releve.bpBar?.let { add("BP ${Nombres.enTexte(it)}") }
    releve.hpBar?.let { add("HP ${Nombres.enTexte(it)}") }
    releve.surchauffeK?.let { add("SH ${Nombres.enTexte(it)} K") }
    releve.sousRefroidissementK?.let { add("SC ${Nombres.enTexte(it)} K") }
    if (fluide.isNotBlank()) {
        val gwp = Fluides.gwp(fluide)
        add(if (gwp != null) "$fluide" else fluide)
    }
}.joinToString(" · ")
