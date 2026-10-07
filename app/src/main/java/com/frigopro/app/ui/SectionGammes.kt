package com.frigopro.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.GammeMaintenance
import com.frigopro.app.data.Periodicite
import com.frigopro.app.data.PointGamme
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.RangeePastilles

/**
 * Les gammes de maintenance, dans les Réglages.
 *
 * Elles y sont et non dans la tournée pour la raison qui y range le catalogue de
 * prestations et la liste des types d'intervention : une gamme se décrit une fois
 * et se consulte des centaines de fois. L'écran qui **l'applique** est ailleurs —
 * la vue Préventif de la tournée, et la fiche d'une machine.
 *
 * L'édition passe par une **boîte de dialogue** et non par une page dans la page :
 * l'onglet Réglages tient déjà une seule profondeur, et un troisième niveau y
 * aurait demandé une pile arrière que le projet n'a pas. C'est le motif de
 * `DialoguePrestation`, qui édite une prestation du catalogue depuis cette même
 * liste.
 */
@Composable
fun SectionGammes(
    gammes: List<GammeMaintenance>,
    points: List<PointGamme>,
    /** Combien de machines suivent chaque gamme, par identifiant de gamme. */
    machinesParGamme: Map<String, Int>,
    gammeOuverte: String?,
    actions: ActionsGammes,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (gammes.isEmpty()) {
            Encart(
                texte = "Aucune gamme. Une gamme est une liste de points et une cadence — " +
                    "« visite mensuelle groupe froid », « ronde du matin ». Elle se décrit " +
                    "une fois et s'affecte ensuite à autant de machines qu'il faut.",
            )
        } else {
            Text(
                text = "Changer la cadence d'une gamme ne réécrit rien : la prochaine " +
                    "échéance de chaque machine se recalcule au pas nouveau, et les visites " +
                    "déjà faites gardent la cadence sous laquelle elles l'ont été.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        gammes.forEach { gamme ->
            LigneGamme(
                gamme = gamme,
                points = points.count { it.gammeId == gamme.id },
                machines = machinesParGamme[gamme.id] ?: 0,
                onOuvrir = { actions.onOuvrir(gamme.id) },
            )
        }

        BoutonPlein(
            texte = "Nouvelle gamme",
            onClick = { actions.onOuvrir(NOUVELLE_GAMME) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    when (gammeOuverte) {
        null -> Unit
        NOUVELLE_GAMME -> DialogueNouvelleGamme(
            onCreer = actions.onCreer,
            onFermer = actions.onFermer,
        )

        else -> gammes.firstOrNull { it.id == gammeOuverte }?.let { gamme ->
            DialogueGamme(
                gamme = gamme,
                points = points.filter { it.gammeId == gamme.id }.sortedBy { it.rang },
                machines = machinesParGamme[gamme.id] ?: 0,
                actions = actions,
            )
        }
    }
}

/**
 * Les rappels de l'écran des gammes, en un objet.
 *
 * Sept rappels portés un à un auraient allongé d'autant la signature de
 * `ReglagesScreen`, qui en comptait déjà trente : c'est le geste déjà fait pour le
 * tarif de déplacement et pour le registre.
 */
data class ActionsGammes(
    val onOuvrir: (String) -> Unit = {},
    val onFermer: () -> Unit = {},
    val onCreer: (String, Periodicite) -> Unit = { _, _ -> },
    val onRenommer: (GammeMaintenance, String) -> Unit = { _, _ -> },
    val onPeriodicite: (GammeMaintenance, Periodicite) -> Unit = { _, _ -> },
    val onSupprimer: (GammeMaintenance) -> Unit = {},
    val onAjouterPoint: (String, String) -> Unit = { _, _ -> },
    val onSupprimerPoint: (PointGamme) -> Unit = {},
)

/** L'identifiant factice qui ouvre la boîte de création. */
internal const val NOUVELLE_GAMME = "nouvelle"

@Composable
private fun LigneGamme(
    gamme: GammeMaintenance,
    points: Int,
    machines: Int,
    onOuvrir: () -> Unit,
) {
    Carte(onClick = onOuvrir) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = gamme.libelle, style = MaterialTheme.typography.bodyLarge)
                Text(
                    // Les deux comptes disent d'un coup d'œil si la gamme est
                    // prête : une gamme sans point ne vérifie rien, une gamme sans
                    // machine ne réclame rien.
                    text = listOf(
                        if (points == 0) "aucun point" else "$points point${pluriel(points)}",
                        if (machines == 0) {
                            "aucune machine"
                        } else {
                            "$machines machine${pluriel(machines)}"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Puce(texte = gamme.periodicite.libelle)
        }
    }
}

internal fun pluriel(nombre: Int): String = if (nombre > 1) "s" else ""

/**
 * Créer une gamme : son intitulé et sa cadence, et rien de plus.
 *
 * Les points se posent **ensuite**, dans la boîte d'édition qui s'ouvre d'elle-même
 * sur la gamme créée : demander les deux d'un coup aurait fait une boîte longue
 * pour un geste qu'on fait debout.
 */
@Composable
private fun DialogueNouvelleGamme(
    onCreer: (String, Periodicite) -> Unit,
    onFermer: () -> Unit,
) {
    var libelle by remember { mutableStateOf("") }
    var periodicite by remember { mutableStateOf(Periodicite.MENSUEL) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Nouvelle gamme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ChampTexte(
                    libelle = "Intitulé",
                    valeur = libelle,
                    onValeur = { libelle = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Cadence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PastillesPeriodicite(retenue = periodicite, onChoisir = { periodicite = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreer(libelle, periodicite) },
                enabled = libelle.isNotBlank(),
            ) {
                Text(text = "Créer")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** Une gamme ouverte : son intitulé, sa cadence, ses points. */
@Composable
private fun DialogueGamme(
    gamme: GammeMaintenance,
    points: List<PointGamme>,
    machines: Int,
    actions: ActionsGammes,
) {
    var nouveauPoint by remember { mutableStateOf("") }
    var aSupprimer by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = actions.onFermer,
        title = { Text(text = gamme.libelle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ChampTexte(
                    libelle = "Intitulé",
                    valeur = gamme.libelle,
                    onValeur = { actions.onRenommer(gamme, it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PastillesPeriodicite(
                    retenue = gamme.periodicite,
                    onChoisir = { actions.onPeriodicite(gamme, it) },
                )

                Text(
                    text = if (points.isEmpty()) {
                        "Aucun point. Une gamme sans point ne vérifie rien : ajoutez ce qu'il " +
                            "faut contrôler, dans l'ordre où on le fait."
                    } else {
                        "Ces points sont recopiés sur chaque visite au moment où elle se fait, " +
                            "si bien qu'en retirer un ne touche pas aux visites passées."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                points.forEach { point ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = point.libelle,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { actions.onSupprimerPoint(point) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Retirer ${point.libelle}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChampTexte(
                        libelle = "Nouveau point",
                        valeur = nouveauPoint,
                        onValeur = { nouveauPoint = it },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            actions.onAjouterPoint(gamme.id, nouveauPoint)
                            nouveauPoint = ""
                        },
                        enabled = nouveauPoint.isNotBlank(),
                    ) {
                        Text(text = "Ajouter")
                    }
                }

                TextButton(onClick = { aSupprimer = true }) {
                    Text(
                        text = "Supprimer cette gamme",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = actions.onFermer) { Text(text = "Fermer") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    if (aSupprimer) {
        ConfirmationSuppressionGamme(
            gamme = gamme,
            machines = machines,
            onConfirmer = {
                aSupprimer = false
                actions.onSupprimer(gamme)
            },
            onFermer = { aSupprimer = false },
        )
    }
}

/**
 * La confirmation nomme ce qui part **et ce qui reste**, le second comptant
 * autant : la crainte qui retient le doigt est celle d'effacer la preuve d'une
 * année de visites, et ce n'est pas ce qui se produit.
 */
@Composable
private fun ConfirmationSuppressionGamme(
    gamme: GammeMaintenance,
    machines: Int,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Supprimer « ${gamme.libelle} » ?") },
        text = {
            Text(
                text = buildString {
                    append("Ses points partent, et ")
                    append(
                        if (machines == 0) {
                            "aucune machine ne la suit"
                        } else {
                            "$machines machine${pluriel(machines)} cesse" +
                                "${if (machines > 1) "nt" else ""} de la suivre"
                        },
                    )
                    append(". Les visites déjà faites sont gardées : elles gardent leur ")
                    append("intitulé et leur date, et restent la preuve que la maintenance ")
                    append("a eu lieu.")
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirmer) { Text(text = "Supprimer") } },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
    )
}

/**
 * Les six cadences, en pastilles qui défilent.
 *
 * Six libellés font près de six cents points de large : la rangée défile, et c'est
 * le seul endroit du projet où un défilement horizontal est assumé plutôt que
 * corrigé — six choix ne tiennent pas, et les abréger donnerait « Trim. » et
 * « Sem. », qui se confondent avec « Semaine ».
 */
@Composable
private fun PastillesPeriodicite(
    retenue: Periodicite,
    onChoisir: (Periodicite) -> Unit,
) {
    RangeePastilles(
        options = Periodicite.entries,
        retenue = retenue,
        libelle = { it.libelle },
        onChoisir = onChoisir,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    )
}
