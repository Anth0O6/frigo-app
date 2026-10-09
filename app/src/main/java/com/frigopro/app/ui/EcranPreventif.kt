package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.EcheanceMaintenance
import com.frigopro.app.data.StatutEcheance
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.IntituleSection
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.LocalStatuts
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/**
 * Le Préventif : ce que le calendrier doit.
 *
 * Il répond à une question que les trois autres vues de la tournée ne posent
 * pas — elles montrent des interventions **posées sur une date**, celle-ci montre
 * du travail **dû**. Voir [VueTournee.PREVENTIF].
 *
 * **Groupé par état et non par date**, et c'est la décision de l'écran : sur un
 * site de plusieurs centaines d'équipements, une liste chronologique noierait les
 * trois visites en retard sous les quatre cents à venir. Les trois groupes
 * répondent dans l'ordre où l'on se les pose : qu'est-ce que j'ai laissé passer,
 * qu'est-ce qui tombe, et qu'est-ce qui vient.
 *
 * **Ce qui est à venir est replié**, pour la même raison que les devis facturés en
 * bas de leur liste : c'est la seule partie qui n'appelle aucune action, et la
 * laisser déroulée ferait défiler quatre cents lignes avant d'atteindre quoi que
 * ce soit d'utile.
 */
@Composable
fun EcranPreventif(
    echeances: List<EcheanceMaintenance>,
    vue: VueTournee,
    onVue: (VueTournee) -> Unit,
    onOuvrir: (EcheanceMaintenance) -> Unit,
    modifier: Modifier = Modifier,
    aujourdhui: LocalDate = LocalDate.now(),
    /** Aucune gamme n'existe encore : l'écran dit où elles se créent. */
    onVoirReglages: () -> Unit = {},
) {
    var recherche by rememberSaveable { mutableStateOf("") }
    var aVenirDeplie by rememberSaveable { mutableStateOf(false) }

    val trouvees = echeances.filter { it.correspondA(recherche) }
    val parStatut = trouvees.groupBy { it.statut(aujourdhui) }
    val enRetard = parStatut[StatutEcheance.EN_RETARD].orEmpty()
    val aFaire = parStatut[StatutEcheance.A_FAIRE].orEmpty()
    val aVenir = parStatut[StatutEcheance.A_VENIR].orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { marges ->
        Column(modifier = Modifier.padding(marges)) {
            Text(
                text = VueTournee.PREVENTIF.libelle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = MargeEcran, vertical = 12.dp),
            )
            BasculeTournee(
                vue = vue,
                onVue = onVue,
                modifier = Modifier
                    .padding(horizontal = MargeEcran)
                    .padding(bottom = 14.dp),
            )

            if (echeances.isEmpty()) {
                Encart(
                    texte = "Aucun plan de maintenance. Créez une gamme dans les Réglages — " +
                        "une liste de points et une cadence —, puis affectez-la à un parc : " +
                        "ce qu'elle doit apparaîtra ici.",
                    complement = "Ouvrir les Réglages",
                    onClick = onVoirReglages,
                    modifier = Modifier.padding(horizontal = MargeEcran),
                )
                return@Column
            }

            // Les trois chiffres portent sur **tout** le plan et non sur ce qui est
            // filtré : « 4 en retard » est un état du site, pas une propriété de la
            // recherche en cours, et le faire tomber à zéro pendant qu'on cherche
            // donnerait un chiffre faux au moment où on le lit le moins.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MargeEcran)
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val statuts = LocalStatuts.current
                val tout = echeances.groupBy { it.statut(aujourdhui) }
                TuileChiffre(
                    valeur = "${tout[StatutEcheance.EN_RETARD]?.size ?: 0}",
                    libelle = "en retard",
                    modifier = Modifier.weight(1f),
                    couleur = statuts.urgence,
                )
                TuileChiffre(
                    valeur = "${tout[StatutEcheance.A_FAIRE]?.size ?: 0}",
                    libelle = "à faire",
                    modifier = Modifier.weight(1f),
                    couleur = statuts.aValider,
                )
                TuileChiffre(
                    valeur = "${tout[StatutEcheance.A_VENIR]?.size ?: 0}",
                    libelle = "à venir",
                    modifier = Modifier.weight(1f),
                )
            }

            ChampRecherche(
                valeur = recherche,
                onValeur = { recherche = it },
                indication = "Machine, zone, client, gamme…",
                modifier = Modifier
                    .padding(horizontal = MargeEcran)
                    .padding(bottom = 14.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MargeEcran,
                    end = MargeEcran,
                    top = 4.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (recherche.isNotBlank() && trouvees.isEmpty()) {
                    item {
                        Encart(texte = "Rien ne correspond à « ${recherche.trim()} ».")
                    }
                }

                groupe(
                    intitule = "En retard",
                    echeances = enRetard,
                    aujourdhui = aujourdhui,
                    onOuvrir = onOuvrir,
                )
                groupe(
                    intitule = "À faire",
                    echeances = aFaire,
                    aujourdhui = aujourdhui,
                    onOuvrir = onOuvrir,
                )

                if (aVenir.isNotEmpty()) {
                    item(key = "entete-a-venir") {
                        Carte(onClick = { aVenirDeplie = !aVenirDeplie }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "À venir",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Puce(
                                    texte = if (aVenirDeplie) {
                                        "replier"
                                    } else {
                                        "${aVenir.size} visite${pluriel(aVenir.size)}"
                                    },
                                )
                            }
                        }
                    }
                    if (aVenirDeplie) {
                        items(items = aVenir, key = { cle(it) }) { echeance ->
                            LigneEcheanceMaintenance(
                                echeance = echeance,
                                aujourdhui = aujourdhui,
                                onClick = { onOuvrir(echeance) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Un groupe de la liste : son intitulé, puis ses lignes — **sous-groupées par
 * zone** dès qu'une machine en porte une.
 *
 * Le statut reste le premier niveau, et c'est délibéré : il dit ce qui est
 * urgent, et c'est la question de l'écran. La zone est le second parce qu'elle
 * dit *l'ordre dans lequel marcher*, ce qui ne devient une question qu'à partir
 * d'un certain nombre — une ronde journalière sur cinquante machines donne un
 * seul groupe de statut et cinquante lignes, qu'on ne parcourt pas. Les
 * sous-titres y découpent le trajet des pieds.
 *
 * L'inverse — zone d'abord, statut en couleur — a été écarté : sur un parc de
 * trois cents machines à cadences mêlées, il aurait noyé les quatre en retard
 * au milieu de trois cents à venir, et c'est le retard qu'on ouvre l'écran pour
 * voir.
 *
 * **Rien ne change tant qu'aucune machine n'a de zone** : un seul sous-groupe
 * sans nom n'ajouterait qu'une ligne de titre inutile, et c'est le même partage
 * que la section de plan absente d'une fiche machine tant qu'aucune gamme
 * n'existe. Les machines **non rangées** ont, elles, leur sous-groupe nommé
 * « Sans zone » plutôt que de passer en tête sans titre : une machine qu'on ne
 * voit nulle part est une machine qu'on n'entretient pas.
 */
private fun LazyListScope.groupe(
    intitule: String,
    echeances: List<EcheanceMaintenance>,
    aujourdhui: LocalDate,
    onOuvrir: (EcheanceMaintenance) -> Unit,
) {
    if (echeances.isEmpty()) return
    item(key = "entete-$intitule") { IntituleSection(texte = "$intitule · ${echeances.size}") }

    val parZone = echeances.groupBy { it.equipement.zone.trim() }
    if (parZone.size <= 1) {
        items(items = echeances, key = { cle(it) }) { echeance ->
            LigneEcheanceMaintenance(
                echeance = echeance,
                aujourdhui = aujourdhui,
                onClick = { onOuvrir(echeance) },
            )
        }
        return
    }

    // Les zones nommées d'abord, triées ; les non rangées en dernier — elles
    // appellent un rangement, pas un détour en tête de liste.
    val ordre = parZone.keys.filter { it.isNotEmpty() }.sortedWith(collateurZones) +
        listOf("").filter { it in parZone }
    ordre.forEach { zone ->
        val siennes = parZone.getValue(zone)
        item(key = "zone-$intitule-$zone") {
            SousTitreZone(
                zone = zone.ifEmpty { "Sans zone" },
                nombre = siennes.size,
            )
        }
        items(items = siennes, key = { cle(it) }) { echeance ->
            LigneEcheanceMaintenance(
                echeance = echeance,
                aujourdhui = aujourdhui,
                onClick = { onOuvrir(echeance) },
            )
        }
    }
}

/**
 * Le sous-titre d'une zone, et son compte.
 *
 * Le compte y est parce que c'est lui qu'on lit : « Toiture · 3 » dit qu'on
 * monte une fois pour trois machines, ce qui change l'ordre dans lequel on
 * décide de faire sa matinée.
 */
@Composable
private fun SousTitreZone(zone: String, nombre: Int) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = zone,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Puce(texte = "$nombre", chiffre = true)
    }
}

/** Même tri que `zonesDe` : « Étuve » n'est pas après « Zone 2 ». */
private val collateurZones: Comparator<String> =
    Collator.getInstance(Locale.FRENCH).let { collateur ->
        Comparator { a, b -> collateur.compare(a, b) }
    }

/**
 * La clé d'une ligne : le couple machine / gamme.
 *
 * Ni l'un ni l'autre seul ne suffit — une machine suit plusieurs gammes, et une
 * gamme couvre plusieurs machines. Une clé en doublon ferait sauter la liste à
 * chaque écriture.
 */
private fun cle(echeance: EcheanceMaintenance): String =
    "${echeance.equipement.id}-${echeance.gamme.id}"

@Composable
private fun LigneEcheanceMaintenance(
    echeance: EcheanceMaintenance,
    aujourdhui: LocalDate,
    onClick: () -> Unit,
) {
    val statuts = LocalStatuts.current
    val statut = echeance.statut(aujourdhui)
    val teinte: Color = when (statut) {
        StatutEcheance.EN_RETARD -> statuts.urgence
        StatutEcheance.A_FAIRE -> statuts.aValider
        StatutEcheance.A_VENIR -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Carte(onClick = onClick, liseré = teinte.takeIf { statut.appelleUneAction }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = echeance.equipement.nom,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(echeance.clientNom, echeance.gamme.libelle)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Puce(texte = echeance.gamme.periodicite.libelle)
                Text(
                    text = libelleEcheance(echeance, aujourdhui),
                    style = MaterialTheme.typography.bodySmall,
                    color = teinte,
                )
            }
        }
    }
}

/**
 * Ce que la ligne dit de sa date.
 *
 * Un retard se compte en jours parce que c'est ainsi qu'on le juge — « en retard
 * de 12 j » se mesure, « échéance le 2 juin » demande de compter de tête. Une
 * machine **jamais visitée** le dit : elle n'a pas été négligée, elle vient
 * d'entrer au plan, et les deux ne s'annoncent pas de la même façon.
 */
internal fun libelleEcheance(echeance: EcheanceMaintenance, aujourdhui: LocalDate): String {
    val retard = echeance.joursDeRetard(aujourdhui)
    return when {
        retard > 0 -> "en retard de $retard j"
        echeance.echeance == aujourdhui -> "aujourd'hui"
        echeance.jamaisVisitee -> "jamais faite · ${jourCourt(echeance.echeance)}"
        else -> jourCourt(echeance.echeance)
    }
}
