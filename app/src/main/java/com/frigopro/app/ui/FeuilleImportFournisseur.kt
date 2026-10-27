package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Marge
import com.frigopro.app.data.auCentime
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.CaseACocher
import com.frigopro.app.ui.composants.ChampChiffre
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * Les gestes de la feuille d'import, regroupés pour la même raison que
 * [ActionsDeplacement] : `EcranDevis` porte déjà plus de vingt rappels.
 */
data class ActionsImportFournisseur(
    val onScanner: () -> Unit = {},
    val onOuvrirFichier: () -> Unit = {},
    val onMarge: (ChoixMarge) -> Unit = {},
    val onCoefficient: (Double?) -> Unit = {},
    val onMettreAJourPrix: (Boolean) -> Unit = {},
    val onCreerArticles: (Boolean) -> Unit = {},
    val onRetenir: (Int, Boolean) -> Unit = { _, _ -> },
    val onQuantite: (Int, Double?) -> Unit = { _, _ -> },
    val onCoefficientLigne: (Int, Double?) -> Unit = { _, _ -> },
    val onValider: () -> Unit = {},
    val onFermer: () -> Unit = {},
)

/**
 * Un devis fournisseur repris sur un devis client : ce qui a été lu, la question
 * de la marge, et chaque ligne avec sa quantité et son coefficient.
 *
 * La question **marger ou non** vient avant les lignes, et rien ne s'ajoute tant
 * qu'on n'y a pas répondu (voir [ChoixMarge]). La marge se dit en
 * **coefficient**, comme dans les Réglages — c'est le chiffre qu'on applique —,
 * et le taux de marque est écrit à côté : « 30 % de marge » ne dit pas lequel
 * des deux, et l'écart se paie en euros (voir [Marge]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleImportFournisseur(
    etat: ImportFournisseur,
    actions: ActionsImportFournisseur,
) {
    // Ouverte en grand d'emblée : on y relit une liste, et une feuille à
    // mi-hauteur cacherait le bouton d'ajout sous les lignes.
    ModalBottomSheet(
        onDismissRequest = actions.onFermer,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "titre") {
                Text(text = "Devis fournisseur", style = MaterialTheme.typography.titleLarge)
            }

            when {
                etat.enCours -> item(key = "encours") {
                    Encart(texte = "Lecture du document…", icone = Icons.Filled.DocumentScanner)
                }
                etat.echec != null -> item(key = "echec") {
                    Encart(
                        texte = etat.echec,
                        icone = Icons.Filled.Warning,
                        alerte = true,
                        onClick = actions.onFermer,
                        complement = "Fermer",
                    )
                }
                else -> {
                    item(key = "bilan") { BilanImport(etat) }
                    item(key = "marge") { ChoixDeMarge(etat, actions) }
                    if (etat.reconnues.isNotEmpty()) {
                        item(key = "prix") {
                            LigneACocher(
                                cochee = etat.mettreAJourPrix,
                                texte = "Mettre à jour le prix d'achat de " +
                                    "${etat.reconnues.size} article${if (etat.reconnues.size > 1) "s" else ""}" +
                                    " du magasin",
                                onBasculer = { actions.onMettreAJourPrix(!etat.mettreAJourPrix) },
                            )
                        }
                    }
                    if (etat.horsMagasin.isNotEmpty()) {
                        item(key = "creer") {
                            val n = etat.horsMagasin.size
                            LigneACocher(
                                cochee = etat.creerArticles,
                                texte = "Créer la fiche de $n article${if (n > 1) "s" else ""} " +
                                    "hors magasin, au prix d'achat lu",
                                onBasculer = { actions.onCreerArticles(!etat.creerArticles) },
                            )
                        }
                    }
                    items(items = etat.lignes, key = { "import-${it.rang}" }) { ligne ->
                        CarteLigneImport(
                            etat = etat,
                            ligne = ligne,
                            actions = actions,
                        )
                    }
                    item(key = "valider") { PiedImport(etat, actions) }
                }
            }
        }
    }
}

@Composable
private fun BilanImport(etat: ImportFournisseur) {
    val origine = buildString {
        append("Devis")
        etat.fournisseur?.let { append(" ").append(it.nom) }
        etat.date?.let { append(" du ").append(it.format(FORMAT_DATE_COURTE)) }
    }
    val lues = etat.lignes.size
    val connues = etat.reconnues.size
    Encart(
        texte = "$origine : $lues ligne${if (lues > 1) "s" else ""} lue${if (lues > 1) "s" else ""}, " +
            "$connues au magasin par leur référence. Relisez les quantités et les prix : " +
            "rien n'est écrit avant « Ajouter au devis ».",
        icone = Icons.Filled.DocumentScanner,
    )
}

/** La question posée avant tout : marger ou non, et de combien. */
@Composable
private fun ChoixDeMarge(etat: ImportFournisseur, actions: ActionsImportFournisseur) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Marger ce matériel ?", style = MaterialTheme.typography.titleMedium)
        RangeePastilles(
            options = ChoixMarge.entries.toList<ChoixMarge?>(),
            retenue = etat.marge,
            libelle = { it?.libelle.orEmpty() },
            onChoisir = { it?.let(actions.onMarge) },
        )
        if (etat.marge == ChoixMarge.MARGER) {
            ChampChiffre(
                libelle = "Coefficient appliqué au prix d'achat",
                valeur = etat.coefficient,
                unite = "×",
                onValeur = actions.onCoefficient,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = equivalentDe(etat.coefficient)
                    ?: "Aucun coefficient réglé : tapez-le, il vaudra pour toutes les lignes " +
                    "sans coefficient propre.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Une ligne lue : à cocher, sa quantité, son coefficient, et ce qu'elle donne. */
@Composable
private fun CarteLigneImport(
    etat: ImportFournisseur,
    ligne: LigneImport,
    actions: ActionsImportFournisseur,
) {
    val statuts = LocalStatuts.current
    val article = ligne.article
    val prix = etat.prixVente(ligne)

    Carte(liseré = if (ligne.retenue) statuts.aValider else null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LigneACocher(
                cochee = ligne.retenue,
                texte = ligne.designation,
                onBasculer = { actions.onRetenir(ligne.rang, !ligne.retenue) },
            )
            Text(
                text = buildString {
                    append("Achat ").append(Nombres.enEuros(ligne.lue.prixNet))
                    when {
                        article == null && ligne.retenue && etat.creerArticles ->
                            append(" · fiche créée à l'ajout")
                        article == null -> append(" · hors magasin")
                        article.prixAchat > 0.0 && article.prixAchat.auCentime() != ligne.lue.prixNet.auCentime() ->
                            append(" · fiche : ").append(Nombres.enEuros(article.prixAchat))
                        else -> append(" · au magasin")
                    }
                },
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ligne.retenue) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChampChiffre(
                        libelle = "Quantité",
                        valeur = ligne.quantite,
                        unite = ligne.unite,
                        onValeur = { actions.onQuantite(ligne.rang, it) },
                        modifier = Modifier.weight(1f),
                    )
                    // Vide, il suit le choix général : c'est ce que dit
                    // l'indication, et vider le champ y revient.
                    ChampChiffre(
                        libelle = if (ligne.coefficient == null) "Coefficient (général)" else "Coefficient",
                        valeur = ligne.coefficient ?: etat.coefficientDe(ligne),
                        unite = "×",
                        onValeur = { actions.onCoefficientLigne(ligne.rang, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = when {
                        prix == null -> "Prix de vente : choisissez la marge."
                        else -> {
                            val total = (prix * (ligne.quantite ?: 0.0)).auCentime()
                            val marge = ((prix - ligne.lue.prixNet) * (ligne.quantite ?: 0.0)).auCentime()
                            "Vente ${Nombres.enEuros(prix)} l'unité — total ${Nombres.enEuros(total)}" +
                                ", marge ${Nombres.enEuros(marge)}"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (prix != null && prix < ligne.lue.prixNet) {
                        statuts.urgence
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun PiedImport(etat: ImportFournisseur, actions: ActionsImportFournisseur) {
    Column(
        modifier = Modifier.padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val manque = etat.manque
        if (manque != null) {
            Text(
                text = manque,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val total = etat.versLignes().sumOf { (it.prixUnitaire * it.quantite).auCentime() }
            Text(
                text = "Total HT ajouté : ${Nombres.enEuros(total.auCentime())}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        val n = etat.retenues.size
        BoutonPlein(
            texte = "Ajouter $n ligne${if (n > 1) "s" else ""} au devis",
            onClick = actions.onValider,
            actif = manque == null,
            modifier = Modifier.fillMaxWidth(),
        )
        BoutonContour(texte = "Annuler", onClick = actions.onFermer, modifier = Modifier.fillMaxWidth())
    }
}

/** Toute la rangée coche, et non la seule case : gants aux mains. */
@Composable
private fun LigneACocher(cochee: Boolean, texte: String, onBasculer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalCibles.current.case)
            .clickable(onClick = onBasculer),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CaseACocher(cochee = cochee)
        Text(
            text = texte,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * « × 1,30 — taux de marque 23,1 % » : le coefficient et ce qu'il veut dire,
 * calculés par [Marge] sur un achat de 100 € pour n'en recopier aucune formule.
 */
private fun equivalentDe(coefficient: Double?): String? {
    val c = coefficient?.takeIf { it > 0.0 } ?: return null
    val taux = Marge(prixAchat = 100.0, prixVente = 100.0 * c).tauxDeMarque ?: return null
    return "Coefficient × ${Nombres.enTexte(c)}, soit un taux de marque de " +
        "${Nombres.enTexte(taux)} % (part de la marge dans le prix de vente)."
}
