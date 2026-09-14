package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.CategoriePhoto
import com.frigopro.app.data.LieuStock
import com.frigopro.app.data.PiecePosee
import com.frigopro.app.data.Photo
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.theme.AValider
import com.frigopro.app.ui.theme.LocalCibles
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.Termine
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * Une pièce qu'on pose, et d'où elle vient.
 *
 * Un objet plutôt que six paramètres positionnels : la moitié d'entre eux ne
 * sont renseignés que sur l'un des deux chemins, et une lambda à six arguments
 * dont trois valent zéro se relit mal et se réordonne à ses risques.
 *
 * [articleId] est **l'article du magasin**, ou `null` quand la pièce a été
 * saisie à la main : tout ce que la fiche du camion n'a pas ne s'invente pas —
 * un frigoriste passe chez un grossiste en cours de route, et lui interdire de
 * noter la pièce tant qu'elle n'est pas au magasin l'aurait fait renoncer à la
 * noter.
 */
data class PosePiece(
    val designation: String,
    val reference: String = "",
    val quantite: Double = 1.0,
    val articleId: String? = null,
    /** Le prix d'achat **du jour**, recopié sur la ligne. Zéro : inconnu. */
    val prixAchat: Double = 0.0,
    /** Retrancher la pièce du camion. Sans article, il n'y a rien à retrancher. */
    val sortirDuStock: Boolean = true,
)

/**
 * Le volet des pièces posées.
 *
 * La référence est séparée de la désignation parce qu'elles ne servent pas au
 * même moment : la désignation se lit sur le compte-rendu du client, la
 * référence se recopie dans une commande.
 *
 * Deux chemins pour poser : **prendre au camion**, qui est le cas normal et
 * emporte le prix d'achat, et **saisir à la main**, qui reste ouvert pour la
 * pièce achetée en route. Le premier est proposé en tête de la boîte plutôt
 * qu'en second recours : c'est celui qui remplit la marge, et un chemin qu'il
 * faut chercher est un chemin qu'on ne prend pas.
 */
@Composable
fun OngletPieces(
    etat: EtatIntervention,
    actions: ActionsIntervention,
    modifier: Modifier = Modifier,
    /** Ce que le camion transporte, prix d'achat compris. */
    magasin: List<ArticleEnStock> = emptyList(),
) {
    var saisieOuverte by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (etat.pieces.isEmpty()) {
            Encart(texte = "Aucune pièce posée. Celles que vous ajoutez ici se reportent sur le compte-rendu.")
        }
        etat.pieces.forEach { piece ->
            LignePiece(piece = piece, onSupprimer = actions.onSupprimerPiece)
        }
        BoutonContour(
            texte = "+ Ajouter une pièce",
            onClick = { saisieOuverte = true },
            modifier = Modifier.fillMaxWidth(),
        )
        EspaceVertical(24)
    }

    if (saisieOuverte) {
        DialoguePiece(
            magasin = magasin,
            onValider = { pose ->
                actions.onAjouterPiece(pose)
                saisieOuverte = false
            },
            onFermer = { saisieOuverte = false },
        )
    }
}

@Composable
private fun LignePiece(piece: PiecePosee, onSupprimer: (String) -> Unit) {
    Carte(contour = true, onClick = { onSupprimer(piece.id) }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = piece.designation, style = MaterialTheme.typography.titleSmall)
                if (piece.reference.isNotBlank()) {
                    Text(
                        text = piece.reference,
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "×${Nombres.enTexte(piece.quantite)}",
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/**
 * La boîte de saisie d'une pièce.
 *
 * Le choix au camion **pré-remplit** la désignation, la référence et le prix
 * d'achat, et laisse les trois modifiables : une référence se corrige, et une
 * pièce prise au camion peut être posée sous un autre nom sur le compte-rendu
 * d'un client qui ne lit pas les codes fabricant. Proposer vaut mieux
 * qu'imposer, comme la quantité pré-remplie d'un multi-split.
 *
 * Le stock disponible est dit à côté de chaque article, et un article que le
 * camion n'a plus reste **choisissable** : il arrive qu'on en pose une prise à
 * l'atelier le matin sans avoir noté le transfert, et refuser la saisie
 * obligerait à corriger l'inventaire avant de pouvoir rendre compte du travail.
 * La quantité descend alors le camion à zéro, jamais en dessous.
 */
@Composable
private fun DialoguePiece(
    magasin: List<ArticleEnStock>,
    onValider: (PosePiece) -> Unit,
    onFermer: () -> Unit,
) {
    var designation by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var quantite by remember { mutableStateOf("1") }
    var choisi by remember { mutableStateOf<ArticleEnStock?>(null) }
    var sortirDuStock by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(text = "Pièce posée") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (magasin.isNotEmpty()) {
                    ChoixArticle(
                        choisi = choisi,
                        magasin = magasin,
                        onChoisir = { entree ->
                            choisi = entree
                            if (entree != null) {
                                designation = entree.article.designation
                                reference = entree.article.reference
                            }
                        },
                    )
                }

                ChampTexte(libelle = "Désignation", valeur = designation, onValeur = { designation = it })
                ChampTexte(libelle = "Référence", valeur = reference, onValeur = { reference = it })
                ChampTexte(
                    libelle = "Quantité",
                    valeur = quantite,
                    onValeur = { quantite = it },
                    clavier = KeyboardType.Decimal,
                )

                val article = choisi
                if (article != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Sortir du camion",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = sortirDuStock, onCheckedChange = { sortirDuStock = it })
                    }
                    // Le prix d'achat est annoncé plutôt que saisi : il vient de
                    // la fiche de l'article, et le retaper ici aurait créé une
                    // deuxième vérité. Zéro se dit en clair — une marge se
                    // calcule sur un prix connu, et un blanc ne se distingue
                    // pas d'un article donné.
                    Encart(
                        texte = if (article.article.prixAchat > 0.0) {
                            "Prix d'achat repris de la fiche : " +
                                "${Nombres.enEuros(article.article.prixAchat)} / ${article.article.unite}. " +
                                "C'est celui du jour, et il reste sur la ligne."
                        } else {
                            "Cet article n'a pas de prix d'achat : la pièce ne comptera pas " +
                                "dans le coût de l'intervention tant qu'il n'est pas renseigné au magasin."
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValider(
                        PosePiece(
                            designation = designation,
                            reference = reference,
                            quantite = Nombres.versDecimal(quantite) ?: 1.0,
                            articleId = choisi?.article?.id,
                            prixAchat = choisi?.article?.prixAchat ?: 0.0,
                            sortirDuStock = sortirDuStock,
                        ),
                    )
                },
                enabled = designation.isNotBlank(),
            ) {
                Text(text = "Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text(text = "Annuler") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** Le camion, déroulé : ce qu'il transporte, et combien il en reste. */
@Composable
private fun ChoixArticle(
    choisi: ArticleEnStock?,
    magasin: List<ArticleEnStock>,
    onChoisir: (ArticleEnStock?) -> Unit,
) {
    var ouvert by remember { mutableStateOf(false) }

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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Au camion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = choisi?.article?.designation ?: "Saisie libre",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (ouvert) {
        AlertDialog(
            onDismissRequest = { ouvert = false },
            title = { Text(text = "Prendre au camion") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Ce que le camion a encore d'abord : c'est ce qu'on peut
                    // poser maintenant. Le reste suit, sans disparaître.
                    magasin
                        .sortedWith(
                            compareByDescending<ArticleEnStock> { it.quantite(LieuStock.CAMION) > 0.0 }
                                .thenBy { it.article.designation },
                        )
                        .forEach { entree ->
                            LigneArticle(
                                entree = entree,
                                retenu = entree.article.id == choisi?.article?.id,
                                onClick = {
                                    onChoisir(entree)
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
                    Text(text = "Saisie libre")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun LigneArticle(entree: ArticleEnStock, retenu: Boolean, onClick: () -> Unit) {
    val auCamion = entree.quantite(LieuStock.CAMION)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalCibles.current.action),
        shape = MaterialTheme.shapes.small,
        color = if (retenu) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entree.article.designation,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entree.article.reference.isNotBlank()) {
                    Text(
                        text = entree.article.reference,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = "${Nombres.enTexte(auCamion)} ${entree.article.unite}",
                style = StyleChiffrePetit,
                color = if (auCamion > 0.0) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    LocalStatuts.current.urgence
                },
            )
        }
    }
}

/**
 * Le volet des photos avant / après.
 *
 * Les deux catégories sont séparées et colorées comme partout ailleurs —
 * l'ambre pour l'état constaté, le cyan pour l'état rendu. C'est la paire qui
 * vaut preuve auprès du client, et la séparer visuellement évite la question
 * « laquelle est laquelle » six mois plus tard.
 */
@Composable
fun OngletPhotos(
    etat: EtatIntervention,
    actions: ActionsIntervention,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        GrillePhotos(
            intitule = "Avant",
            accent = AValider,
            photos = etat.photosAvant,
            categorie = CategoriePhoto.AVANT,
            actions = actions,
            chargerPhoto = chargerPhoto,
        )
        GrillePhotos(
            intitule = "Après",
            accent = Termine,
            photos = etat.photosApres,
            categorie = CategoriePhoto.APRES,
            actions = actions,
            chargerPhoto = chargerPhoto,
        )
        Encart(
            texte = "Chaque photo est horodatée et rattachée à l'intervention. " +
                "Les photos durables de la machine — plaque, emplacement — vivent sur sa fiche.",
        )
        EspaceVertical(24)
    }
}

/**
 * Une catégorie de photos : deux colonnes, et les deux façons d'en ajouter.
 *
 * Deux colonnes et non trois : une vignette doit rester assez grande pour
 * qu'on reconnaisse un évaporateur givré sans l'ouvrir.
 */
@Composable
private fun GrillePhotos(
    intitule: String,
    accent: androidx.compose.ui.graphics.Color,
    photos: List<Photo>,
    categorie: CategoriePhoto,
    actions: ActionsIntervention,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
) {
    Section(intitule = intitule, couleurIntitule = accent) {
        photos.chunked(2).forEach { paire ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                paire.forEach { photo ->
                    VignettePhoto(
                        photo = photo,
                        chargerPhoto = chargerPhoto,
                        onClick = { actions.onAgrandir(photo) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
                // Une seule photo sur la rangée : garder la moitié libre pour
                // que la vignette ne s'étire pas sur toute la largeur.
                if (paire.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoutonContour(
                texte = "Photographier",
                onClick = { actions.onPhotographier(categorie) },
                modifier = Modifier.weight(1f),
                couleur = accent,
            )
            BoutonContour(
                texte = "Choisir",
                onClick = { actions.onChoisirImage(categorie) },
                modifier = Modifier.weight(1f),
                couleur = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** Une vignette, qui décode son image à la taille où elle s'affiche. */
@Composable
internal fun VignettePhoto(
    photo: Photo,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coteMax: Int = 512,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        PhotoChargee(
            fichier = photo.fichier,
            coteMax = coteMax,
            charger = chargerPhoto,
            contentDescription = photo.legende.ifBlank { photo.categorie.libelle },
        )
    }
}
