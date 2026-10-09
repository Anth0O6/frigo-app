package com.frigopro.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.CategoriePhoto
import com.frigopro.app.data.Equipement
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.Photo
import com.frigopro.app.data.ReductionPhoto
import com.frigopro.app.data.Releve
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.theme.FrigoProTheme
import java.time.LocalDate
import java.time.LocalTime

/** Côté d'une vignette : assez grand pour reconnaître une plaque, assez petit pour en voir plusieurs. */
private val COTE_VIGNETTE = 104.dp

/**
 * La fiche d'une machine : ses photos, puis son historique.
 *
 * Les photos d'abord, parce que c'est ce qu'on vient chercher en arrivant sur
 * place — la référence à commander, l'endroit où la trouver. L'historique
 * ensuite, qui répond à la question d'après.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranEquipement(
    equipement: Equipement,
    /**
     * Les unités intérieures de ce groupe, vide pour un monosplit comme pour une
     * unité — la hiérarchie n'a qu'un niveau.
     */
    unites: List<Equipement>,
    /** Le groupe dont cette fiche dépend, quand c'est une unité. */
    groupe: Equipement?,
    photos: List<Photo>,
    historique: List<Intervention>,
    releves: List<Releve>,
    /** Ce que cette machine doit au calendrier, et ce qu'elle a reçu. */
    plan: PlanMachine,
    actionsPlan: ActionsPlanMachine,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    onPhotographier: (CategoriePhoto) -> Unit,
    onChoisirImage: (CategoriePhoto) -> Unit,
    onAgrandir: (Photo) -> Unit,
    onOuvrirUnite: (Equipement) -> Unit,
    onAjouterUnite: () -> Unit,
    onRenommer: () -> Unit,
    onModifierFiche: () -> Unit,
    onDupliquer: () -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La barre d'onglets, sous cet écran, pose déjà la marge du bas.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                // La coquille pose déjà la marge du haut : la laisser ici la
                // compterait deux fois.
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(text = equipement.nom, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val plaque = listOf(equipement.designation, equipement.numeroSerie)
                            .filter { it.isNotBlank() }
                            .joinToString(" · n° ")
                        // Le groupe passe devant la plaque : « Salon » tout seul ne
                        // se retrouve pas dans un parc de vingt machines, et c'est
                        // l'appareil dont elle dépend qui la situe.
                        // La zone passe devant tout : sur un site rangé, c'est
                        // elle qui dit où aller, et on ouvre une fiche depuis une
                        // liste où dix machines se ressemblent.
                        val sousTitre = listOfNotNull(
                            equipement.zone,
                            groupe?.let { "Unité de ${it.nom}" },
                            plaque,
                        )
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (sousTitre.isNotEmpty()) {
                            Text(
                                text = sousTitre,
                                style = com.frigopro.app.ui.theme.StyleChiffrePetit,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFermer) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Revenir au carnet",
                        )
                    }
                },
                actions = {
                    MenuMachine(
                        onRenommer = onRenommer,
                        onModifierFiche = onModifierFiche,
                        onDupliquer = onDupliquer,
                        onSupprimer = onSupprimer,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "identite") {
                IdentiteMachine(
                    equipement = equipement,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            // Avant les photos : savoir combien d'unités porte l'appareil change
            // ce qu'on monte sur le toit, et la fiche d'une unité se consulte
            // ensuite. Un monosplit n'affiche rien de plus qu'un bouton.
            if (!equipement.estUnite) {
                item(key = "unites") {
                    SectionUnites(
                        unites = unites,
                        onOuvrirUnite = onOuvrirUnite,
                        onAjouterUnite = onAjouterUnite,
                    )
                }
            }
            // Le plan avant les photos : pendant une ronde, « cette machine est
            // due » et le bouton qui l'éteint sont ce qu'on vient chercher, et
            // les photos servent à se repérer une fois. Il ne s'affiche que si
            // des gammes existent — voir [SectionPlanMachine].
            item(key = "plan") {
                SectionPlanMachine(
                    plan = plan,
                    actions = actionsPlan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (releves.isNotEmpty()) {
                item(key = "tendance") {
                    TendanceReleves(
                        releves = releves,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            // Seules les catégories de la machine : « avant » et « après »
            // appartiennent à une intervention et n'ont rien à faire ici.
            CategoriePhoto.deMachine.forEach { categorie ->
                item(key = categorie.name) {
                    SectionPhotos(
                        categorie = categorie,
                        photos = photos.filter { it.categorie == categorie },
                        chargerPhoto = chargerPhoto,
                        onPhotographier = { onPhotographier(categorie) },
                        onChoisirImage = { onChoisirImage(categorie) },
                        onAgrandir = onAgrandir,
                    )
                }
            }
            item(key = "historique") {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                TitreSection(texte = "Historique")
            }
            if (historique.isEmpty()) {
                item(key = "historique-vide") {
                    Text(
                        text = "Aucune intervention n'a encore été rattachée à cette machine.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            } else {
                items(items = historique, key = { it.id }) { intervention ->
                    LigneHistorique(intervention = intervention)
                }
            }
            // Les visites préventives **après** les interventions, et non
            // mêlées à elles : une intervention est un passage imprévu, une
            // visite l'exécution d'un contrat, et les confondre rendrait
            // illisible la question que chacune des deux listes répond.
            item(key = "visites") {
                HistoriqueVisites(
                    visites = plan.visites,
                    onRetirer = actionsPlan.onRetirerVisite,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Une catégorie de photos, et de quoi en ajouter.
 *
 * Les deux catégories sont toujours affichées, vides comprises : c'est ainsi
 * qu'on voit qu'il manque la photo de l'emplacement, ce qu'une liste qui cache
 * ses sections vides ne dirait pas.
 */
@Composable
private fun SectionPhotos(
    categorie: CategoriePhoto,
    photos: List<Photo>,
    chargerPhoto: suspend (String, Int) -> Bitmap?,
    onPhotographier: () -> Unit,
    onChoisirImage: () -> Unit,
    onAgrandir: (Photo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TitreSection(texte = categorie.libelle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = photos, key = { it.id }) { photo ->
                PhotoChargee(
                    fichier = photo.fichier,
                    coteMax = ReductionPhoto.COTE_VIGNETTE,
                    charger = chargerPhoto,
                    contentDescription = "${categorie.libelle}, voir en grand",
                    modifier = Modifier
                        .size(COTE_VIGNETTE)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAgrandir(photo) },
                )
            }
            item(key = "ajouter") {
                BoutonAjoutPhoto(
                    categorie = categorie,
                    onPhotographier = onPhotographier,
                    onChoisirImage = onChoisirImage,
                )
            }
        }
    }
}

/**
 * Tuile d'ajout : elle offre l'appareil photo *et* la galerie.
 *
 * L'appareil photo en premier, c'est le geste du terrain ; la galerie sert au
 * retour, pour une photo déjà prise avec l'appareil habituel du téléphone.
 */
@Composable
private fun BoutonAjoutPhoto(
    categorie: CategoriePhoto,
    onPhotographier: () -> Unit,
    onChoisirImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ouvert by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .size(COTE_VIGNETTE)
                .clickable { ouvert = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AddAPhoto,
                    contentDescription = "Ajouter une photo : ${categorie.libelle}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            DropdownMenuItem(
                text = { Text(text = "Photographier") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = null)
                },
                onClick = {
                    ouvert = false
                    onPhotographier()
                },
            )
            DropdownMenuItem(
                text = { Text(text = "Choisir une image") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null)
                },
                onClick = {
                    ouvert = false
                    onChoisirImage()
                },
            )
        }
    }
}

/** Une ligne d'historique : quand, quoi, et où cela en est. */
@Composable
private fun LigneHistorique(intervention: Intervention, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = libelleDateAvecAnnee(intervention.date),
                style = MaterialTheme.typography.titleSmall,
            )
            if (intervention.typeLibelle.isNotBlank()) {
                Text(
                    text = intervention.typeLibelle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = intervention.statut.libelle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * Les unités intérieures d'un groupe.
 *
 * La section paraît même vide, et c'est voulu : c'est ainsi qu'on apprend qu'on
 * peut en ajouter. L'application a d'abord été écrite pour le monosplit, où une
 * machine est une machine ; un bi-split est le même groupe avec deux unités, et
 * rien dans l'écran ne le disait.
 *
 * Chaque unité s'ouvre comme une fiche à part entière — ses photos, son
 * historique : c'est une unité précise qui fuit ou qui encrasse son filtre, pas
 * « l'installation », et le relevé comme la photo doivent pouvoir la désigner.
 */
@Composable
private fun SectionUnites(
    unites: List<Equipement>,
    onOuvrirUnite: (Equipement) -> Unit,
    onAjouterUnite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        TitreSection(
            texte = when (unites.size) {
                0 -> "Unités intérieures"
                1 -> "1 unité intérieure"
                else -> "${unites.size} unités intérieures"
            },
        )
        if (unites.isEmpty()) {
            Text(
                text = "Un monosplit n'en a qu'une, confondue avec le groupe. Pour un " +
                    "bi-split ou un multi-split, ajoutez-les ici : les prestations " +
                    "comptées par unité se chiffreront alors au bon nombre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        unites.forEach { unite ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOuvrirUnite(unite) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = unite.nom,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BoutonContour(
            texte = "+ Ajouter une unité",
            onClick = onAjouterUnite,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            couleur = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * Les quatre gestes d'une machine, derrière un seul bouton.
 *
 * Ils étaient **trois icônes côte à côte** sur la rangée du titre, et le compte
 * ne tenait plus : le titre y porte le nom de la machine *et* sa plaque en
 * sous-titre — « Groupe Daikin bi-split » puis « Bitzer 4FES-3Y · n° 4821007 » —,
 * les deux coupés à l'ellipse pendant que trois boutons carrés se partageaient
 * le tiers droit. C'est le défaut que la barre de la tournée avait déjà : un
 * libellé qui paie la place qu'on a donnée aux boutons, et c'est le libellé qui
 * compte — on ouvre une fiche pour savoir quelle machine on regarde.
 *
 * Un menu les rend **tous lisibles en entier**, là où une icône demandait de
 * deviner, et c'est ce qui a permis d'en ajouter un quatrième sans rien
 * reprendre. Ce n'est pas le « menu plus » que la barre d'onglets s'interdit :
 * une destination derrière un menu n'est pas une destination, mais une action
 * nommée derrière un menu reste une action — et aucune des quatre ne se fait
 * gants aux mains en pleine tournée.
 *
 * « Supprimer » est en dernier et séparé, pour la raison qui le met sur la fiche
 * ouverte plutôt qu'en appui long sur une carte : c'est le seul des quatre qui ne
 * se défait pas.
 */
@Composable
private fun MenuMachine(
    onRenommer: () -> Unit,
    onModifierFiche: () -> Unit,
    onDupliquer: () -> Unit,
    onSupprimer: () -> Unit,
) {
    var ouvert by remember { mutableStateOf(false) }

    IconButton(onClick = { ouvert = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "Actions sur la machine",
        )
    }
    DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
        DropdownMenuItem(
            text = { Text(text = "Renommer") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                ouvert = false
                onRenommer()
            },
        )
        DropdownMenuItem(
            text = { Text(text = "Zone et plaque") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Tune, contentDescription = null) },
            onClick = {
                ouvert = false
                onModifierFiche()
            },
        )
        DropdownMenuItem(
            text = { Text(text = "Dupliquer") },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
            },
            onClick = {
                ouvert = false
                onDupliquer()
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(text = "Supprimer") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                ouvert = false
                onSupprimer()
            },
        )
    }
}

@Composable
private fun TitreSection(texte: String, modifier: Modifier = Modifier) {
    Text(
        text = texte,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun EcranEquipementPreview() {
    FrigoProTheme {
        Surface {
            EcranEquipement(
                equipement = Equipement(id = "1", clientId = "c1", nom = "Groupe Daikin bi-split"),
                unites = listOf(
                    Equipement(id = "u1", clientId = "c1", parentId = "1", nom = "Salon"),
                    Equipement(id = "u2", clientId = "c1", parentId = "1", nom = "Chambre"),
                ),
                groupe = null,
                photos = emptyList(),
                historique = listOf(
                    Intervention(
                        id = "i1",
                        date = LocalDate.of(2026, 9, 8),
                        heure = LocalTime.of(9, 30),
                        client = "Boucherie Lemoine",
                        ville = "Rouen",
                        typeLibelle = "Fuite de fluide",
                        statut = StatutIntervention.TERMINEE,
                    ),
                ),
                releves = emptyList(),
                plan = PlanMachine(
                    echeances = emptyList(),
                    gammes = emptyList(),
                    visites = emptyList(),
                ),
                actionsPlan = ActionsPlanMachine({}, {}, {}, {}, {}),
                chargerPhoto = { _, _ -> null },
                onPhotographier = {},
                onChoisirImage = {},
                onAgrandir = {},
                onOuvrirUnite = {},
                onAjouterUnite = {},
                onRenommer = {},
                onModifierFiche = {},
                onDupliquer = {},
                onSupprimer = {},
                onFermer = {},
                modifier = Modifier.height(600.dp),
            )
        }
    }
}
