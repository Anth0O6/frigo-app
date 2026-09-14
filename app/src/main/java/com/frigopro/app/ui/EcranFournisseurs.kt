package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.Article
import com.frigopro.app.data.CategorieFournisseur
import com.frigopro.app.data.Fournisseur
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.theme.LocalStatuts

/**
 * Le carnet de fournisseurs : qui vend quoi, et qui l'on appelle en premier.
 *
 * ## Trois filtres, trois moments
 *
 * Ils ne se posent pas à la même heure de la journée : « qui vend des fluides ? »
 * quand on sait ce qu'il faut, « qui est-ce que j'appelle d'habitude ? » quand
 * on est pressé, « qui est près d'ici ? » quand on est déjà sur la route.
 *
 * La proximité est une **ville** et non une position GPS, et c'est une
 * décision : demander la permission de localisation pour trier une liste de
 * numéros serait disproportionné — l'application n'en a que deux, toutes deux
 * justifiées ailleurs. La ville est d'ailleurs la meilleure réponse, parce
 * qu'on cherche un fournisseur près du **chantier** et non près de l'endroit
 * d'où l'on consulte son téléphone.
 *
 * ## Ce que la carte propose
 *
 * Appeler, ouvrir l'itinéraire, ouvrir le catalogue — les trois gestes qui
 * ferment la question. Les deux premiers passent par les mêmes intentions
 * Android que pour un client ; le troisième ouvre le navigateur, parce qu'un
 * catalogue fournisseur n'a pas de format d'échange et que sa copie serait
 * fausse au premier changement de tarif.
 */
@Composable
fun EcranFournisseurs(
    fournisseurs: List<Fournisseur>,
    filtres: FiltresFournisseurs,
    total: Int,
    onFiltres: (FiltresFournisseurs) -> Unit,
    onOuvrir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BarreFiltres(
            filtres = filtres,
            onFiltres = onFiltres,
            modifier = Modifier.padding(horizontal = MargeEcran),
        )

        ChampTexte(
            libelle = "Près de (ville)",
            valeur = filtres.ville,
            onValeur = { onFiltres(filtres.copy(ville = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MargeEcran)
                .padding(top = 10.dp),
        )

        if (total == 0) {
            Encart(
                texte = "Aucun fournisseur. Le carnet sert le jour où il manque une pièce : " +
                    "un numéro, une adresse, et le lien du catalogue en ligne.",
                modifier = Modifier.padding(MargeEcran),
            )
            return@Column
        }

        if (fournisseurs.isEmpty()) {
            Encart(
                texte = "Aucun fournisseur ne correspond aux filtres.",
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
            items(items = fournisseurs, key = { it.id }) { fournisseur ->
                CarteFournisseur(
                    fournisseur = fournisseur,
                    onClick = { onOuvrir(fournisseur.id) },
                )
            }
        }
    }
}

/**
 * Les deux filtres qui se touchent : la famille, et les préférés.
 *
 * En pastilles horizontales défilantes, comme partout ailleurs. « Tous » est
 * une pastille et non l'absence de sélection : sans elle, on ne saurait pas
 * comment revenir en arrière.
 */
@Composable
private fun BarreFiltres(
    filtres: FiltresFournisseurs,
    onFiltres: (FiltresFournisseurs) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statuts = LocalStatuts.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PastilleFiltre(
            texte = "Préférés",
            retenue = filtres.preferesSeulement,
            couleurRetenue = statuts.termine,
            onClick = { onFiltres(filtres.copy(preferesSeulement = !filtres.preferesSeulement)) },
        )
        PastilleFiltre(
            texte = "Tous",
            retenue = filtres.categorie == null,
            onClick = { onFiltres(filtres.copy(categorie = null)) },
        )
        CategorieFournisseur.entries.forEach { famille ->
            PastilleFiltre(
                texte = famille.libelle,
                retenue = filtres.categorie == famille,
                onClick = {
                    onFiltres(
                        filtres.copy(
                            categorie = if (filtres.categorie == famille) null else famille,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun PastilleFiltre(
    texte: String,
    retenue: Boolean,
    onClick: () -> Unit,
    couleurRetenue: Color = MaterialTheme.colorScheme.primary,
) {
    Puce(
        texte = texte,
        modifier = Modifier.clickable(onClick = onClick),
        couleur = if (retenue) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fond = if (retenue) couleurRetenue else MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Composable
private fun CarteFournisseur(fournisseur: Fournisseur, onClick: () -> Unit) {
    val contexte = LocalContext.current
    val statuts = LocalStatuts.current

    Carte {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (fournisseur.prefere) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Fournisseur préféré",
                            tint = statuts.termine,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = fournisseur.nom,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = listOf(fournisseur.categorie.libelle, fournisseur.ville)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (fournisseur.appelable) {
                IconButton(onClick = { contexte.appeler(fournisseur.telephone) }) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = "Appeler ${fournisseur.nom}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (fournisseur.localisable) {
                IconButton(onClick = { contexte.ouvrirItineraire(fournisseur.adresseComplete) }) {
                    Icon(
                        imageVector = Icons.Filled.Directions,
                        contentDescription = "Itinéraire vers ${fournisseur.nom}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (fournisseur.consultable) {
                IconButton(onClick = { contexte.ouvrirLien(fournisseur.siteCatalogue) }) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = "Catalogue de ${fournisseur.nom}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** La fiche d'un fournisseur : ses coordonnées, sa famille, son catalogue. */
@Composable
fun FicheFournisseur(
    fournisseur: Fournisseur,
    onModifier: (Fournisseur) -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statuts = LocalStatuts.current
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
                text = fournisseur.nom.ifBlank { "Nouveau fournisseur" },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            BoutonContour(texte = "Fermer", onClick = onFermer)
        }

        Section(intitule = "Identité") {
            ChampTexte(
                libelle = "Nom",
                valeur = fournisseur.nom,
                onValeur = { onModifier(fournisseur.copy(nom = it)) },
            )
            ChoixCategorie(
                retenue = fournisseur.categorie,
                onChoisir = { onModifier(fournisseur.copy(categorie = it)) },
            )
            PastilleFiltre(
                texte = if (fournisseur.prefere) "★ Appelé en premier" else "Marquer comme préféré",
                retenue = fournisseur.prefere,
                couleurRetenue = statuts.termine,
                onClick = { onModifier(fournisseur.copy(prefere = !fournisseur.prefere)) },
            )
        }

        Section(intitule = "Coordonnées") {
            ChampTexte(
                libelle = "Téléphone",
                valeur = fournisseur.telephone,
                onValeur = { onModifier(fournisseur.copy(telephone = it)) },
                clavier = KeyboardType.Phone,
            )
            ChampTexte(
                libelle = "Courriel",
                valeur = fournisseur.email,
                onValeur = { onModifier(fournisseur.copy(email = it)) },
                clavier = KeyboardType.Email,
            )
            ChampTexte(
                libelle = "Adresse",
                valeur = fournisseur.adresse,
                onValeur = { onModifier(fournisseur.copy(adresse = it)) },
            )
            ChampTexte(
                libelle = "Ville",
                valeur = fournisseur.ville,
                onValeur = { onModifier(fournisseur.copy(ville = it)) },
            )
        }

        Section(intitule = "Catalogue en ligne") {
            ChampTexte(
                libelle = "Adresse du site",
                valeur = fournisseur.siteCatalogue,
                onValeur = { onModifier(fournisseur.copy(siteCatalogue = it)) },
            )
            Text(
                text = "L'application ouvre le lien, elle n'en copie pas le contenu : un " +
                    "catalogue change sans prévenir, et une copie serait fausse au premier " +
                    "changement de tarif — le genre de faux qui part ensuite sur un devis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(intitule = "Notes") {
            ChampTexte(
                libelle = "Ce qu'il faut se rappeler",
                valeur = fournisseur.notes,
                onValeur = { onModifier(fournisseur.copy(notes = it)) },
                lignes = 3,
            )
        }

        BoutonContour(
            texte = "Supprimer ce fournisseur",
            onClick = { confirmation = true },
            modifier = Modifier.fillMaxWidth(),
            couleur = statuts.urgence,
        )
        Spacer(modifier = Modifier.padding(bottom = 24.dp))
    }

    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text(text = "Supprimer « ${fournisseur.nom} » ?") },
            text = {
                Text(
                    text = "Les articles qu'il fournit gardent son nom : ils doivent " +
                        "continuer de dire chez qui les racheter, même sans la fiche. " +
                        "Seul le lien est coupé.",
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

@Composable
private fun ChoixCategorie(
    retenue: CategorieFournisseur,
    onChoisir: (CategorieFournisseur) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategorieFournisseur.entries.forEach { famille ->
            PastilleFiltre(
                texte = famille.libelle,
                retenue = famille == retenue,
                onClick = { onChoisir(famille) },
            )
        }
    }
}

/** La feuille de création d'un fournisseur : le nom suffit, le reste s'ajoute après. */
@Composable
fun FeuilleNouveauFournisseur(
    brouillon: Fournisseur,
    onBrouillon: (Fournisseur) -> Unit,
    onEnregistrer: () -> Unit,
    onAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(text = "Nouveau fournisseur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChampTexte(
                    libelle = "Nom",
                    valeur = brouillon.nom,
                    onValeur = { onBrouillon(brouillon.copy(nom = it)) },
                )
                ChampTexte(
                    libelle = "Téléphone",
                    valeur = brouillon.telephone,
                    onValeur = { onBrouillon(brouillon.copy(telephone = it)) },
                    clavier = KeyboardType.Phone,
                )
                ChoixCategorie(
                    retenue = brouillon.categorie,
                    onChoisir = { onBrouillon(brouillon.copy(categorie = it)) },
                )
                Text(
                    text = "L'adresse et le catalogue s'ajoutent ensuite, sur la fiche : " +
                        "un fournisseur s'inscrit souvent au téléphone, en pleine panne.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onEnregistrer, enabled = brouillon.nom.isNotBlank()) {
                Text(text = "Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) { Text(text = "Annuler") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** La feuille de création d'un article. Même motif que celle du fournisseur. */
@Composable
fun FeuilleNouvelArticle(
    brouillon: Article,
    onBrouillon: (Article) -> Unit,
    onEnregistrer: () -> Unit,
    onAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(text = "Nouvel article") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChampTexte(
                    libelle = "Désignation",
                    valeur = brouillon.designation,
                    onValeur = { onBrouillon(brouillon.copy(designation = it)) },
                )
                ChampTexte(
                    libelle = "Référence fournisseur",
                    valeur = brouillon.reference,
                    onValeur = { onBrouillon(brouillon.copy(reference = it)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChampTexte(
                        libelle = "Prix d'achat HT",
                        valeur = Nombres.enTexte(brouillon.prixAchat),
                        onValeur = { saisie ->
                            Nombres.versDecimal(saisie)
                                ?.let { onBrouillon(brouillon.copy(prixAchat = it)) }
                        },
                        clavier = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    ChampTexte(
                        libelle = "Prix de vente HT",
                        valeur = Nombres.enTexte(brouillon.prixVente),
                        onValeur = { saisie ->
                            Nombres.versDecimal(saisie)
                                ?.let { onBrouillon(brouillon.copy(prixVente = it)) }
                        },
                        clavier = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "Les quantités et les seuils se posent ensuite, sur la fiche.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onEnregistrer, enabled = brouillon.designation.isNotBlank()) {
                Text(text = "Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) { Text(text = "Annuler") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
