package com.frigopro.app.ui.composants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frigopro.app.ui.Nombres
import com.frigopro.app.ui.theme.StyleChiffre

/**
 * Une case de relevé : son intitulé, sa valeur, son unité.
 *
 * La valeur s'édite **sur place**, sans boîte de dialogue. Un relevé se
 * remplit entre deux manipulations, une case à la fois, souvent d'une seule
 * main : chaque écran intermédiaire est un geste de trop.
 *
 * La saisie est retenue localement tant que le champ a le focus, et seule une
 * valeur lisible remonte à l'appelant. Sans cela, effacer « 11,2 » pour taper
 * « 9 » passerait par l'état intermédiaire « 11, » — illisible — qui
 * reviendrait aussitôt effacer ce que l'utilisateur est en train d'écrire.
 *
 * @param onValeur reçoit `null` quand la case est vidée, ce qui est une
 *   information : le technicien retire une mesure qu'il juge fausse.
 */
@Composable
fun ChampChiffre(
    libelle: String,
    valeur: Double?,
    unite: String,
    onValeur: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    couleur: Color = MaterialTheme.colorScheme.onSurface,
) {
    var saisie by remember(valeur) { mutableStateOf(Nombres.enTexte(valeur)) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                text = libelle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = saisie,
                    onValueChange = { texte ->
                        saisie = texte
                        onValeur(Nombres.versDecimal(texte))
                    },
                    modifier = Modifier.weight(1f, fill = false),
                    textStyle = StyleChiffre.copy(color = couleur),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    // `Decimal` fait apparaître la virgule sur le pavé, et rien
                    // d'autre : pas de lettres à écarter du doigt avec un gant.
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    decorationBox = { champ ->
                        Box {
                            if (saisie.isEmpty()) {
                                Text(
                                    text = "—",
                                    style = StyleChiffre,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            champ()
                        }
                    },
                )
                if (unite.isNotEmpty()) {
                    Text(
                        text = " $unite",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * Un champ de texte ordinaire, aux couleurs du thème.
 *
 * Enveloppe `OutlinedTextField` plutôt que de le reconfigurer sur chaque
 * écran : la douzaine de couleurs qu'il faut poser pour qu'il s'accorde au
 * fond sombre finirait sinon recopiée partout, et diverge à la première
 * retouche.
 */
@Composable
fun ChampTexte(
    libelle: String,
    valeur: String,
    onValeur: (String) -> Unit,
    modifier: Modifier = Modifier,
    lignes: Int = 1,
    clavier: KeyboardType = KeyboardType.Text,
) {
    androidx.compose.material3.OutlinedTextField(
        value = valeur,
        onValueChange = onValeur,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = libelle) },
        singleLine = lignes == 1,
        minLines = lignes,
        shape = MaterialTheme.shapes.medium,
        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = clavier),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** Une rangée de pastilles dont une seule est retenue : les onglets de la maquette. */
@Composable
fun <T> RangeePastilles(
    options: List<T>,
    retenue: T,
    libelle: (T) -> String,
    onChoisir: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val choisie = option == retenue
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (choisie) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                onClick = { onChoisir(option) },
            ) {
                Text(
                    text = libelle(option),
                    style = if (choisie) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (choisie) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * La barre de recherche d'une liste.
 *
 * Sans icône de validation ni bouton : la liste se filtre à la frappe, et un
 * geste de plus pour « lancer » la recherche serait un geste pour rien.
 */
@Composable
fun ChampRecherche(
    valeur: String,
    onValeur: (String) -> Unit,
    indication: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = valeur,
                onValueChange = onValeur,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { champ ->
                    Box {
                        if (valeur.isEmpty()) {
                            Text(
                                text = indication,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        champ()
                    }
                },
            )
        }
    }
}
