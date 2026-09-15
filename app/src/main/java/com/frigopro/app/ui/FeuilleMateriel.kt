package com.frigopro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Article
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.auCentime
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampChiffre
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * D'où vient le prix de vente d'une ligne de matériel.
 *
 * Trois façons de répondre à la même question, et elles ne se valent pas :
 *
 * - **[FICHE]** — le prix de vente arrêté sur la fiche article. C'est celui
 *   qu'on a décidé une fois pour toutes sur ce qu'on pose chaque semaine.
 * - **[ACHAT]** — le prix d'achat tel quel : le matériel passe au coûtant. Ce
 *   n'est pas une erreur de saisie, c'est un geste commercial, et il vaut mieux
 *   qu'il soit nommé qu'obtenu en retapant un chiffre par-dessus l'autre.
 * - **[COEFFICIENT]** — le prix d'achat multiplié. C'est ce qu'un frigoriste
 *   applique de tête devant un client, et ce qui fait marcher les articles dont
 *   la fiche ne porte qu'un prix d'achat — le magasin sert de tarifaire autant
 *   que d'inventaire, et tout n'y a pas un prix de vente arrêté.
 *
 * Le prix reste **modifiable à la main** dans les trois cas : ce sont des points
 * de départ, pas des verrous. Une remise consentie sur place ne se demande pas
 * la permission.
 */
enum class PrixMateriel(val libelle: String) {
    FICHE("Prix de vente"),
    ACHAT("Au prix coûtant"),
    COEFFICIENT("Coefficient"),
}

/**
 * Le magasin, ouvert depuis un devis.
 *
 * Il montre **tout le magasin** et non ce qui est en stock, et c'est le point à
 * ne pas perdre : on chiffre précisément le matériel qu'on n'a pas encore
 * acheté. Ce qu'il y a au camion ou à l'atelier est affiché — c'est une
 * information utile quand on promet une date — mais ne filtre rien.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleMateriel(
    magasin: List<ArticleEnStock>,
    /** Le coefficient des Réglages ; `0` quand il n'est pas réglé. */
    coefficientParDefaut: Double,
    onChoisir: (Article, Double, Double) -> Unit,
    onFermer: () -> Unit,
) {
    var recherche by remember { mutableStateOf("") }
    var choisi by remember { mutableStateOf<Article?>(null) }

    val filtres = magasin.filter { it.article.correspondA(recherche) }

    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Matériel", style = MaterialTheme.typography.titleLarge)

            if (magasin.isEmpty()) {
                Encart(
                    texte = "Le magasin est vide. Les articles se créent dans " +
                        "l'onglet Carnets, avec leur référence et leurs deux prix — " +
                        "y compris ceux qu'on n'a pas en stock : le magasin sert de " +
                        "tarifaire autant que d'inventaire.",
                )
            } else {
                ChampRecherche(
                    valeur = recherche,
                    onValeur = { recherche = it },
                    indication = "Référence, désignation, fournisseur",
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = filtres, key = { it.article.id }) { enStock ->
                    LigneArticleDevis(
                        enStock = enStock,
                        onClick = { choisi = enStock.article },
                    )
                }
            }
        }
    }

    choisi?.let { article ->
        DialogueLigneMateriel(
            article = article,
            coefficientParDefaut = coefficientParDefaut,
            onValider = { quantite, prix ->
                onChoisir(article, quantite, prix)
                choisi = null
                onFermer()
            },
            onFermer = { choisi = null },
        )
    }
}

/** Ce qu'on lit sur un article avant de le poser sur un devis. */
@Composable
private fun LigneArticleDevis(enStock: ArticleEnStock, onClick: () -> Unit) {
    val article = enStock.article
    val statuts = LocalStatuts.current

    Carte(onClick = onClick) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.designation,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(article.reference, article.fournisseurNom)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = StyleChiffrePetit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // Le prix de vente s'il existe, l'achat sinon — et l'écran le
                    // dit, plutôt que de laisser croire à un prix de vente arrêté.
                    text = if (article.prixVente > 0.0) {
                        Nombres.enEuros(article.prixVente)
                    } else {
                        "achat ${Nombres.enEuros(article.prixAchat)}"
                    },
                    style = StyleChiffrePetit,
                )
                // Ce qu'on en a, sans filtrer : promettre une pose lundi n'est pas
                // la même chose selon qu'elle est au camion ou à commander.
                val total = enStock.total
                Text(
                    text = if (total > 0.0) {
                        "${Nombres.enTexte(total)} ${article.unite} en stock"
                    } else {
                        "à commander"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (total > 0.0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        statuts.aValider
                    },
                )
            }
        }
    }
}

/**
 * La quantité et le prix, avant de poser la ligne.
 *
 * Les trois origines de prix sont des **pastilles** et non trois boutons qui
 * enregistrent : on bascule, on regarde le montant, on ajuste. Le champ reste
 * modifiable — voir [PrixMateriel] pour pourquoi aucun des trois n'est un
 * verrou.
 */
@Composable
private fun DialogueLigneMateriel(
    article: Article,
    coefficientParDefaut: Double,
    onValider: (Double, Double) -> Unit,
    onFermer: () -> Unit,
) {
    val coefficient = coefficientParDefaut.takeIf { it > 0.0 }

    // L'origine retenue à l'ouverture : la fiche quand elle porte un prix, le
    // coefficient à défaut, et le prix coûtant quand il n'y a ni l'un ni l'autre
    // — ce dernier cas se voit et appelle une correction, là où un prix inventé
    // serait parti chez un vrai client.
    val origineInitiale = when {
        article.prixVente > 0.0 -> PrixMateriel.FICHE
        coefficient != null -> PrixMateriel.COEFFICIENT
        else -> PrixMateriel.ACHAT
    }

    var origine by remember { mutableStateOf(origineInitiale) }
    var quantite by remember { mutableStateOf<Double?>(1.0) }
    var prix by remember { mutableStateOf<Double?>(prixPour(article, origineInitiale, coefficient)) }

    val quantiteRetenue = quantite ?: 0.0
    val prixRetenu = prix ?: 0.0

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = article.designation) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RangeePastilles(
                    options = PrixMateriel.entries.filter {
                        // La fiche n'est proposée que si elle porte un prix, le
                        // coefficient que s'il est réglé : une pastille qui pose
                        // zéro euro n'est pas un choix.
                        when (it) {
                            PrixMateriel.FICHE -> article.prixVente > 0.0
                            PrixMateriel.COEFFICIENT -> coefficient != null
                            PrixMateriel.ACHAT -> true
                        }
                    },
                    retenue = origine,
                    libelle = { it.libelle },
                    onChoisir = {
                        origine = it
                        prix = prixPour(article, it, coefficient)
                    },
                )

                if (coefficient == null) {
                    Encart(
                        texte = "Aucun coefficient réglé : il se pose une fois dans " +
                            "les Réglages, et sert alors à chiffrer tout article sans " +
                            "prix de vente.",
                    )
                }

                ChampChiffre(
                    libelle = "Quantité",
                    valeur = quantite,
                    unite = article.unite,
                    onValeur = { quantite = it },
                )
                ChampChiffre(
                    libelle = "Prix unitaire HT",
                    valeur = prix,
                    unite = "€",
                    // Le champ fait foi : retoucher le prix à la main l'emporte
                    // sur la pastille, qui n'est qu'un point de départ.
                    onValeur = { prix = it },
                )

                // Ce que la ligne rapporte, dit avant de la poser. Jamais sur le
                // PDF — c'est le chiffre du technicien, pas celui du client.
                val marge = ((prixRetenu - article.prixAchat) * quantiteRetenue).auCentime()
                Text(
                    text = when {
                        article.prixAchat <= 0.0 ->
                            "Prix d'achat inconnu : la marge de cette ligne ne se calcule pas."
                        else ->
                            "Total ${Nombres.enEuros((prixRetenu * quantiteRetenue).auCentime())}" +
                                " — marge ${Nombres.enEuros(marge)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onValider(quantiteRetenue, prixRetenu) },
                enabled = quantiteRetenue > 0.0,
            ) {
                Text(text = "Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** Le prix que propose une origine donnée. */
private fun prixPour(article: Article, origine: PrixMateriel, coefficient: Double?): Double =
    when (origine) {
        PrixMateriel.FICHE -> article.prixVente
        PrixMateriel.ACHAT -> article.prixAchat
        PrixMateriel.COEFFICIENT -> (article.prixAchat * (coefficient ?: 1.0)).auCentime()
    }

/** La recherche du magasin : référence, désignation, fournisseur. */
private fun Article.correspondA(recherche: String): Boolean {
    val cherche = recherche.trim()
    if (cherche.isEmpty()) return true
    return listOf(reference, designation, fournisseurNom)
        .any { it.contains(cherche, ignoreCase = true) }
}
