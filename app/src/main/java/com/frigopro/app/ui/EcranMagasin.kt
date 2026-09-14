package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Article
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.data.LieuStock
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampRecherche
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * L'inventaire : ce qu'on a, où, et ce qu'il faut racheter.
 *
 * ## Le manque passe devant
 *
 * Un inventaire se consulte, un manque se traite : ce qui est sous le seuil
 * remonte en tête de liste et porte une pastille. C'est la même décision que
 * les factures échues sur l'accueil — la seule chose de l'écran qui appelle une
 * action doit être la première qu'on voit.
 *
 * ## Deux colonnes, et c'est tout l'intérêt
 *
 * Chaque article montre l'atelier **et** le camion côte à côte, parce que la
 * question du terrain n'est pas « est-ce que j'en ai ? » mais « est-ce que j'en
 * ai *ici* ? ». Un total unique aurait répondu « oui » à quarante kilomètres du
 * seul endroit où la pièce se trouve.
 *
 * Les deux boutons `−` et `+` posent un **mouvement** et non une quantité : ils
 * additionnent à ce qu'il y avait, ce qui reste juste même si l'écran affiche
 * une valeur d'il y a une seconde. Corriger un compte — après un vrai
 * inventaire — se fait dans la fiche, qui écrit la quantité absolue.
 */
@Composable
fun EcranMagasin(
    magasin: List<ArticleEnStock>,
    recherche: String,
    manquants: Int,
    valeurStock: Double,
    onRecherche: (String) -> Unit,
    onOuvrir: (String) -> Unit,
    onBouger: (String, LieuStock, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statuts = LocalStatuts.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MargeEcran)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TuileChiffre(
                valeur = "${magasin.size}",
                libelle = "articles",
                modifier = Modifier.weight(1f),
            )
            TuileChiffre(
                valeur = "$manquants",
                libelle = "à racheter",
                modifier = Modifier.weight(1f),
                // Zéro manquant n'est pas une alerte : c'est le cas normal, et
                // le peindre en rouge apprendrait à ne plus regarder la couleur.
                couleur = if (manquants > 0) statuts.urgence else MaterialTheme.colorScheme.onSurface,
            )
            TuileChiffre(
                valeur = Nombres.enEurosCourt(valeurStock),
                libelle = "au prix d'achat",
                modifier = Modifier.weight(1f),
            )
        }

        ChampRecherche(
            valeur = recherche,
            onValeur = onRecherche,
            indication = "Désignation, référence, fournisseur…",
            modifier = Modifier.padding(horizontal = MargeEcran),
        )

        if (magasin.isEmpty()) {
            Encart(
                texte = if (recherche.isBlank()) {
                    "Aucun article. Le magasin sert à deux choses : savoir ce qu'on a dans " +
                        "le camion avant de partir, et ce qu'il faut racheter avant d'en " +
                        "manquer devant un client."
                } else {
                    "Aucun article ne correspond à « ${recherche.trim()} »."
                },
                modifier = Modifier.padding(MargeEcran),
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = MargeEcran,
                end = MargeEcran,
                top = 12.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = magasin, key = { it.article.id }) { entree ->
                CarteArticle(
                    entree = entree,
                    onClick = { onOuvrir(entree.article.id) },
                    onBouger = { lieu, delta -> onBouger(entree.article.id, lieu, delta) },
                )
            }
        }
    }
}

@Composable
private fun CarteArticle(
    entree: ArticleEnStock,
    onClick: () -> Unit,
    onBouger: (LieuStock, Double) -> Unit,
) {
    val statuts = LocalStatuts.current
    val article = entree.article

    Carte(liseré = if (entree.enAlerte) statuts.urgence else null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            .joinToString(" · ")
                            .ifBlank { "Sans référence" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Le coefficient plutôt que la marge en euros : c'est ce qu'un
                // frigoriste compare d'un article à l'autre pour tarifer.
                article.marge.coefficient?.let {
                    Puce(
                        texte = "×${Nombres.enTexte(it)}",
                        couleur = if (article.marge.aPerte) statuts.urgence else statuts.termine,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LieuStock.entries.forEach { lieu ->
                LigneStock(
                    lieu = lieu,
                    entree = entree,
                    onBouger = { delta -> onBouger(lieu, delta) },
                )
            }
        }
    }
}

/**
 * Une ligne de stock : où, combien, et les deux boutons du mouvement.
 *
 * Le seuil est écrit à côté de la quantité et non caché dans la fiche : c'est
 * lui qui donne son sens au chiffre — « 2 » ne dit rien, « 2 / mini 5 » dit tout.
 */
@Composable
private fun LigneStock(
    lieu: LieuStock,
    entree: ArticleEnStock,
    onBouger: (Double) -> Unit,
) {
    val statuts = LocalStatuts.current
    val stock = entree.stocks[lieu]
    val quantite = entree.quantite(lieu)
    val manque = stock?.sousSeuil == true

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = when (lieu) {
                LieuStock.ATELIER -> Icons.Filled.Warehouse
                LieuStock.CAMION -> Icons.Filled.LocalShipping
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = lieu.libelle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = buildString {
                append(Nombres.enTexte(quantite))
                append(' ')
                append(entree.article.unite)
                if (stock?.surveille == true) append("  ·  mini ${Nombres.enTexte(stock.minimum)}")
            },
            style = StyleChiffrePetit,
            color = if (manque) statuts.urgence else MaterialTheme.colorScheme.onSurface,
        )
        BoutonMouvement(Icons.Filled.Remove, "Retirer une unité") { onBouger(-1.0) }
        BoutonMouvement(Icons.Filled.Add, "Ajouter une unité") { onBouger(1.0) }
    }
}

/**
 * Le bouton d'un mouvement d'une unité.
 *
 * À la taille d'une cible tactile, gants compris : c'est le geste qu'on fait le
 * plus souvent dans cet écran, et souvent debout devant une armoire ouverte.
 */
@Composable
private fun BoutonMouvement(icone: ImageVector, description: String, onClick: () -> Unit) {
    val cote = LocalCibles.current.action
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(cote),
    ) {
        Icon(
            imageVector = icone,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(cote / 4),
        )
    }
}

/**
 * La fiche d'un article : ses deux prix, sa marge, et le compte exact.
 *
 * C'est ici, et non sur la carte, qu'on écrit une quantité **absolue** : c'est
 * le vrai inventaire, celui qu'on fait armoire ouverte, et il écrase ce que les
 * mouvements avaient accumulé. Les deux gestes cohabitent parce qu'ils ne
 * disent pas la même chose — l'un compte, l'autre corrige.
 */
@Composable
fun FicheArticle(
    entree: ArticleEnStock,
    fournisseurs: List<Fournisseur>,
    onModifier: (Article) -> Unit,
    onDefinirStock: (LieuStock, Double, Double) -> Unit,
    onTransferer: (LieuStock, LieuStock, Double) -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statuts = LocalStatuts.current
    val article = entree.article
    var confirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = article.designation,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            BoutonContour(texte = "Fermer", onClick = onFermer)
        }

        Section(intitule = "Prix et marge") {
            ChampTexte(
                libelle = "Prix d'achat HT",
                valeur = Nombres.enTexte(article.prixAchat),
                onValeur = { saisie ->
                    Nombres.versDecimal(saisie)?.let { onModifier(article.copy(prixAchat = it)) }
                },
                clavier = KeyboardType.Decimal,
            )
            ChampTexte(
                libelle = "Prix de vente HT",
                valeur = Nombres.enTexte(article.prixVente),
                onValeur = { saisie ->
                    Nombres.versDecimal(saisie)?.let { onModifier(article.copy(prixVente = it)) }
                },
                clavier = KeyboardType.Decimal,
            )
            SectionMarge(entree = entree)
        }

        Section(intitule = "Identification") {
            ChampTexte(
                libelle = "Désignation",
                valeur = article.designation,
                onValeur = { onModifier(article.copy(designation = it)) },
            )
            ChampTexte(
                libelle = "Référence fournisseur",
                valeur = article.reference,
                onValeur = { onModifier(article.copy(reference = it)) },
            )
            ChampTexte(
                libelle = "Unité",
                valeur = article.unite,
                onValeur = { onModifier(article.copy(unite = it)) },
            )
            ChoixFournisseur(
                retenu = article.fournisseurId,
                fournisseurs = fournisseurs,
                onChoisir = { choisi ->
                    onModifier(
                        article.copy(
                            fournisseurId = choisi?.id,
                            // La copie suit le lien : c'est elle qui dira d'où
                            // venait la pièce si la fiche disparaît un jour.
                            fournisseurNom = choisi?.nom ?: article.fournisseurNom,
                        ),
                    )
                },
            )
        }

        LieuStock.entries.forEach { lieu ->
            SectionInventaire(
                lieu = lieu,
                entree = entree,
                onDefinir = { quantite, minimum -> onDefinirStock(lieu, quantite, minimum) },
            )
        }

        SectionTransfert(entree = entree, onTransferer = onTransferer)

        BoutonContour(
            texte = "Supprimer cet article",
            onClick = { confirmation = true },
            modifier = Modifier.fillMaxWidth(),
            couleur = statuts.urgence,
        )
        Spacer(modifier = Modifier.padding(bottom = 24.dp))
    }

    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text(text = "Supprimer « ${article.designation} » ?") },
            text = {
                Text(
                    text = "Les quantités de l'atelier et du camion disparaissent avec " +
                        "l'article. Les interventions où la pièce a été posée gardent ce " +
                        "qu'elles disaient : elles ne désignent pas cette fiche.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = false
                        onSupprimer()
                    },
                ) {
                    Text(text = "Supprimer", color = statuts.urgence)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = false }) { Text(text = "Annuler") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/**
 * La marge, dite de ses trois façons.
 *
 * Les trois côte à côte, et jamais une seule : « 50 % » n'est ni vrai ni faux
 * sur un article acheté 100 et vendu 150, c'est ambigu. Voir `Marge`.
 */
@Composable
private fun SectionMarge(entree: ArticleEnStock) {
    val statuts = LocalStatuts.current
    val marge = entree.article.marge

    if (!marge.chiffrable) {
        Encart(
            texte = "Renseignez les deux prix pour connaître la marge. Sans prix d'achat " +
                "elle n'est pas nulle : elle est inconnue, et l'inventer reviendrait à " +
                "tarifer à l'aveugle.",
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TuileChiffre(
            valeur = Nombres.enEuros(marge.brute!!),
            libelle = "marge brute",
            modifier = Modifier.weight(1f),
            couleur = if (marge.aPerte) statuts.urgence else statuts.termine,
        )
        TuileChiffre(
            valeur = "${Nombres.enTexte(marge.tauxDeMarque!!)} %",
            libelle = "taux de marque",
            modifier = Modifier.weight(1f),
        )
        TuileChiffre(
            valeur = "×${Nombres.enTexte(marge.coefficient!!)}",
            libelle = "coefficient",
            modifier = Modifier.weight(1f),
        )
    }
    if (marge.aPerte) {
        Encart(
            texte = "Cet article est vendu sous son prix d'achat.",
            icone = Icons.Filled.Warning,
            alerte = true,
        )
    }
}

/** Le compte exact d'un endroit, et son seuil. */
@Composable
private fun SectionInventaire(
    lieu: LieuStock,
    entree: ArticleEnStock,
    onDefinir: (Double, Double) -> Unit,
) {
    val stock = entree.stocks[lieu]
    val quantite = entree.quantite(lieu)
    val minimum = stock?.minimum ?: 0.0

    Section(intitule = lieu.libelle) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChampTexte(
                libelle = "Quantité",
                valeur = Nombres.enTexte(quantite),
                onValeur = { saisie ->
                    Nombres.versDecimal(saisie)?.let { onDefinir(it, minimum) }
                },
                clavier = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            ChampTexte(
                libelle = "Seuil",
                valeur = Nombres.enTexte(minimum),
                onValeur = { saisie ->
                    Nombres.versDecimal(saisie)?.let { onDefinir(quantite, it) }
                },
                clavier = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (stock?.surveille == true) {
                "Alerte sous ${Nombres.enTexte(minimum)} ${entree.article.unite}."
            } else {
                "Seuil à zéro : aucune alerte. C'est le réglage d'un article qu'on " +
                    "commande à la demande plutôt que de tenir en stock."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Charger le camion, ou le décharger.
 *
 * Le geste du matin, et il a sa section parce qu'il ne se fait pas en corrigeant
 * deux quantités : un transfert conserve le total, deux corrections
 * indépendantes peuvent le perdre sans qu'on s'en aperçoive.
 */
@Composable
private fun SectionTransfert(
    entree: ArticleEnStock,
    onTransferer: (LieuStock, LieuStock, Double) -> Unit,
) {
    var quantite by remember { mutableStateOf("1") }
    val combien = Nombres.versDecimal(quantite) ?: 0.0

    Section(intitule = "Transférer") {
        ChampTexte(
            libelle = "Quantité",
            valeur = quantite,
            onValeur = { quantite = it },
            clavier = KeyboardType.Decimal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoutonPlein(
                texte = "Atelier → camion",
                onClick = { onTransferer(LieuStock.ATELIER, LieuStock.CAMION, combien) },
                modifier = Modifier.weight(1f),
                actif = combien > 0.0 && entree.quantite(LieuStock.ATELIER) > 0.0,
            )
            BoutonContour(
                texte = "Camion → atelier",
                onClick = { onTransferer(LieuStock.CAMION, LieuStock.ATELIER, combien) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Un transfert ne prend jamais plus que ce qu'il y a à l'endroit de " +
                "départ : partir avec une pièce qui n'existe pas se découvre chez le client.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Le fournisseur d'un article, choisi dans le carnet. */
@Composable
private fun ChoixFournisseur(
    retenu: String?,
    fournisseurs: List<Fournisseur>,
    onChoisir: (Fournisseur?) -> Unit,
) {
    var ouvert by remember { mutableStateOf(false) }
    val actuel = fournisseurs.firstOrNull { it.id == retenu }

    Surface(
        onClick = { ouvert = true },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalCibles.current.action),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Fournisseur",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = actuel?.nom ?: "Aucun",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (ouvert) {
        AlertDialog(
            onDismissRequest = { ouvert = false },
            title = { Text(text = "Fournisseur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (fournisseurs.isEmpty()) {
                        Text(
                            text = "Le carnet de fournisseurs est vide. Il se remplit dans " +
                                "l'onglet voisin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    fournisseurs.forEach { candidat ->
                        LigneChoix(
                            texte = candidat.nom,
                            retenu = candidat.id == retenu,
                            onClick = {
                                onChoisir(candidat)
                                ouvert = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { ouvert = false }) { Text(text = "Fermer") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onChoisir(null)
                        ouvert = false
                    },
                ) {
                    Text(text = "Détacher")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun LigneChoix(texte: String, retenu: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalCibles.current.action),
        shape = MaterialTheme.shapes.small,
        color = if (retenu) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Text(
            text = texte,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
