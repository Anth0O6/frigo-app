package com.frigopro.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.ArticleEnStock
import com.frigopro.app.data.Client
import com.frigopro.app.data.Facture
import com.frigopro.app.data.FactureChiffree
import com.frigopro.app.data.Intervention
import com.frigopro.app.data.StatutIntervention
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.FrigoProTheme
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit
import java.time.LocalDate
import java.time.LocalTime

/**
 * L'écran d'accueil : ce qu'il faut savoir en ouvrant l'application.
 *
 * Il répond à une seule question — « et maintenant ? » — et tout y est
 * subordonné : l'intervention en cours occupe le haut, en grand, avec de quoi
 * l'ouvrir ou s'y rendre sans rien chercher ; le reste de la journée suit en
 * lignes serrées ; les chiffres et les échéances ferment la marche.
 *
 * Aucune navigation dans le temps ici. L'accueil montre *aujourd'hui*, et le
 * Planning montre les autres jours : donner deux façons de changer de date
 * conduirait à se demander laquelle des deux on regarde.
 */
@Composable
fun EcranAujourdhui(
    etat: EtatAujourdhui,
    /** Laquelle des trois vues de la tournée est ouverte : celle-ci, ici. */
    vue: VueTournee,
    onVue: (VueTournee) -> Unit,
    onOuvrir: (Intervention) -> Unit,
    onItineraire: (Client) -> Unit,
    onVoirDevis: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Les factures échues qu'il est temps de relancer.
     *
     * Elles paraissent ici et non seulement dans leur onglet, pour la raison qui
     * a fait poser les échéances F-Gas au même endroit : l'accueil répond à
     * « et maintenant ? », et une facture de six semaines est une réponse à
     * cette question. Rien n'est stocké — « en retard » se déduit de l'échéance
     * et du jour, et un booléen en base serait faux le lendemain.
     */
    impayees: List<FactureChiffree> = emptyList(),
    onOuvrirFacture: (Facture) -> Unit = {},
    /**
     * Ce qu'il faut racheter, atelier et camion confondus.
     *
     * Même raison que les factures échues, et c'est la dernière liste du projet
     * à rejoindre l'accueil : un article sous son seuil répond à « et
     * maintenant ? » aussi franchement qu'un impayé — il décide de ce qu'on
     * charge avant de partir, et un manque découvert sur un toit coûte la
     * journée. Le manque est dérivé du seuil et de la quantité, jamais stocké.
     */
    manquants: List<ArticleEnStock> = emptyList(),
    onVoirMagasin: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // La coquille pose déjà les marges des barres système.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MargeEcran),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            EnTeteAccueil(etat = etat)

            // La bascule des trois vues, juste sous le titre : exactement où
            // les carnets et la facturation posent la leur. Elle remplace le
            // bouton « Planning » qui était en bas de l'écran — un bouton qui
            // changeait d'onglet, là où ces trois vues sont maintenant le même.
            // Sans marge propre : la colonne de l'écran pose déjà la marge des
            // côtés et espace ses enfants.
            BasculeTournee(vue = vue, onVue = onVue)

            if (etat.enAvant != null) {
                CarteEnAvant(
                    ligne = etat.enAvant,
                    onOuvrir = { onOuvrir(etat.enAvant.intervention) },
                    onItineraire = { etat.enAvant.client?.let(onItineraire) },
                )
            } else {
                Carte {
                    Text(
                        text = if (etat.journeeVide) "Aucune intervention aujourd'hui" else "Journée terminée",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (etat.journeeVide) {
                            "Le planning est libre. De quoi rattraper une échéance, ou souffler."
                        } else {
                            "Tout ce qui était prévu est fait."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TuileChiffre(
                    valeur = "${etat.nombreDuJour}",
                    libelle = "aujourd'hui",
                    modifier = Modifier.weight(1f),
                )
                TuileChiffre(
                    valeur = "${etat.devisEnAttente}",
                    libelle = "devis en attente",
                    modifier = Modifier.weight(1f),
                    couleur = LocalStatuts.current.aValider,
                )
                TuileChiffre(
                    valeur = Nombres.enEurosCourt(etat.chiffreDuMois),
                    libelle = "acceptés ce mois",
                    modifier = Modifier.weight(1f),
                )
            }

            if (etat.suite.isNotEmpty()) {
                Section(intitule = "Suite de la journée", espacement = 8.dp) {
                    etat.suite.forEach { ligne ->
                        LigneSuite(ligne = ligne, onOuvrir = { onOuvrir(ligne.intervention) })
                    }
                }
            }

            if (impayees.isNotEmpty()) {
                Section(intitule = "À relancer", espacement = 8.dp) {
                    impayees.forEach { chiffree ->
                        LigneImpayee(
                            chiffree = chiffree,
                            onClick = { onOuvrirFacture(chiffree.facture) },
                        )
                    }
                }
            }

            if (manquants.isNotEmpty()) {
                Section(intitule = "À racheter", espacement = 8.dp) {
                    manquants.take(MANQUANTS_MONTRES).forEach { entree ->
                        LigneManquant(entree = entree, onClick = onVoirMagasin)
                    }
                    // Le reste est compté plutôt que déroulé : l'accueil alerte,
                    // le magasin tient l'inventaire. Une liste de trente lignes
                    // ici ne serait plus lue — même règle que les échéances.
                    val reste = manquants.size - MANQUANTS_MONTRES
                    if (reste > 0) {
                        BoutonContour(
                            texte = "$reste autre${if (reste > 1) "s" else ""} au magasin",
                            onClick = onVoirMagasin,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (etat.echeances.isNotEmpty()) {
                Section(intitule = "Échéances F-Gas", espacement = 8.dp) {
                    etat.echeances.forEach { echeance -> LigneEcheance(echeance = echeance) }
                }
            }

            // Le seul raccourci qui reste : les trois vues de la tournée sont
            // dans la bascule du haut, et la facturation est un autre onglet.
            BoutonContour(
                texte = "Devis et factures",
                onClick = onVoirDevis,
                modifier = Modifier.fillMaxWidth(),
            )
            EspaceVertical(24)
        }
    }
}

/** « Bonjour Karim », la date du jour, et la pastille des initiales. */
@Composable
private fun EnTeteAccueil(etat: EtatAujourdhui) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = libelleDate(etat.jour),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (etat.technicien.isBlank()) "Bonjour" else "Bonjour ${etat.technicien}",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Les initiales viennent des réglages ; tant que le nom n'y est pas,
        // il n'y a rien à afficher et la pastille disparaît plutôt que de
        // montrer un point d'interrogation.
        if (etat.initiales.isNotBlank()) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = etat.initiales,
                        style = StyleChiffrePetit,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * La carte du haut : l'intervention en cours, ou la prochaine.
 *
 * C'est la seule carte de l'application à porter deux boutons pleins. Elle les
 * mérite : ouvrir la fiche et lancer l'itinéraire sont les deux gestes du
 * matin, et les faire chercher dans une liste coûterait plus cher que la place
 * qu'ils prennent.
 */
@Composable
private fun CarteEnAvant(ligne: LigneTournee, onOuvrir: () -> Unit, onItineraire: () -> Unit) {
    val intervention = ligne.intervention
    val couleur = couleurStatut(intervention)
    Carte(relief = true, liseré = couleur, onClick = onOuvrir) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Puce(
                texte = libelleStatut(intervention),
                couleur = couleur,
                fond = couleur.copy(alpha = 0.16f),
            )
            Text(
                text = creneau(intervention),
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = intervention.client,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sousTitre = listOf(intervention.typeLibelle, intervention.equipementNom)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (sousTitre.isNotEmpty()) {
            Text(
                text = sousTitre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val adresse = ligne.client?.adresseComplete.orEmpty().ifBlank { intervention.ville }
        if (adresse.isNotBlank()) {
            Text(
                text = adresse,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BoutonPlein(texte = "Ouvrir la fiche", onClick = onOuvrir, modifier = Modifier.weight(1.4f))
            if (ligne.localisable) {
                BoutonContour(texte = "Itinéraire", onClick = onItineraire, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Une ligne de la suite du jour : l'heure, le client, et ce qu'on y fait. */
@Composable
private fun LigneSuite(ligne: LigneTournee, onOuvrir: () -> Unit) {
    val intervention = ligne.intervention
    Carte(onClick = onOuvrir, forme = MaterialTheme.shapes.medium) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = intervention.heure.format(FORMAT_HEURE),
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 30.dp)
                    .background(couleurStatut(intervention), MaterialTheme.shapes.extraSmall),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = intervention.client,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = intervention.typeLibelle.ifBlank { intervention.ville },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Une échéance de contrôle d'étanchéité.
 *
 * Le retard est dit en rouge et en toutes lettres : c'est le seul élément de
 * l'accueil qui expose à une sanction, et une date seule ne se compare pas
 * d'un coup d'œil à celle du jour.
 */
/**
 * Une facture échue.
 *
 * Elle dit **depuis combien de jours**, et c'est le chiffre qui décide : une
 * facture de trois jours s'oublie, une de six semaines se réclame. Toucher la
 * ligne ouvre la facture, d'où elle se renvoie — et l'envoi vaut relance.
 */
@Composable
private fun LigneImpayee(chiffree: FactureChiffree, onClick: () -> Unit) {
    val facture = chiffree.facture
    val retard = facture.joursDeRetard(LocalDate.now()) ?: 0L

    Carte(onClick = onClick, relief = true, liseré = LocalStatuts.current.urgence) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = facture.clientNom.ifBlank { "Client non renseigné" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(
                        facture.numero,
                        "échue depuis $retard jour${if (retard > 1) "s" else ""}",
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalStatuts.current.urgence,
                )
            }
            Text(
                text = Nombres.enEuros(chiffree.totalTtc),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/**
 * Un article sous son seuil.
 *
 * Il dit **où** il manque, et c'est tout l'intérêt de tenir deux stocks : « 1 u
 * au camion, mini 3 » n'appelle pas le même geste que le même manque à
 * l'atelier — l'un se recharge le soir, l'autre se commande. Toucher la ligne
 * ouvre le magasin, où le mouvement se pose.
 */
@Composable
private fun LigneManquant(entree: ArticleEnStock, onClick: () -> Unit) {
    val urgence = LocalStatuts.current.urgence
    Carte(onClick = onClick, contour = true, forme = MaterialTheme.shapes.medium) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 30.dp)
                    .background(urgence, MaterialTheme.shapes.extraSmall),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entree.article.designation,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Le même vocabulaire que le magasin : « 1 u · mini 3 ».
                    // Le seuil donne son sens au chiffre, et le chiffre seul ne
                    // dirait pas de combien on est court.
                    text = entree.aReapprovisionner.joinToString("  ·  ") { lieu ->
                        val stock = entree.stocks[lieu]
                        buildString {
                            append(lieu.libelle)
                            append(" : ")
                            append(Nombres.enTexte(entree.quantite(lieu)))
                            append(' ')
                            append(entree.article.unite)
                            if (stock != null) append(" · mini ${Nombres.enTexte(stock.minimum)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = urgence,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entree.article.fournisseurNom.isNotBlank()) {
                Text(
                    text = entree.article.fournisseurNom,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LigneEcheance(echeance: EcheanceFgas) {
    val couleur = if (echeance.etat.enRetard) {
        LocalStatuts.current.urgence
    } else {
        LocalStatuts.current.aValider
    }
    Carte(contour = true, forme = MaterialTheme.shapes.medium) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = couleur.copy(alpha = 0.16f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${echeance.echeance.dayOfMonth}",
                        style = StyleChiffrePetit,
                        color = couleur,
                    )
                    Text(
                        text = moisCourt(echeance.echeance),
                        style = MaterialTheme.typography.labelSmall,
                        color = couleur,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = echeance.clientNom.ifBlank { echeance.equipement.nom },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(if (echeance.etat.enRetard) "En retard" else "Contrôle d'étanchéité")
                        append(" · ")
                        append(echeance.equipement.nom)
                        if (echeance.equipement.fluide.isNotBlank()) {
                            append(" · ")
                            append(echeance.equipement.fluide)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (echeance.etat.enRetard) couleur else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Combien d'articles manquants l'accueil montre.
 *
 * Trois, comme les échéances, et pour la même raison : le but est d'alerter, pas
 * de tenir l'inventaire. Le magasin porte le détail, et le compte du reste y
 * mène.
 */
private const val MANQUANTS_MONTRES = 3

/** « 08:00 → 09:30 », d'après l'heure de début et la durée prévue. */
private fun creneau(intervention: Intervention): String {
    val debut = intervention.heure
    val fin = debut.plusMinutes(intervention.dureeMin.toLong())
    return "${debut.format(FORMAT_HEURE)} → ${fin.format(FORMAT_HEURE)}"
}

@Preview(showBackground = true)
@Composable
private fun ApercuAujourdhui() {
    val client = Client(nom = "Boulangerie Martin", ville = "Lyon 2e", adresse = "12 rue des Halles")
    val intervention = Intervention(
        date = LocalDate.now(),
        heure = LocalTime.of(8, 0),
        client = client.nom,
        ville = client.ville,
        clientId = client.id,
        typeLibelle = "Dépannage chambre froide",
        statut = StatutIntervention.EN_COURS,
        urgente = true,
        dureeMin = 90,
    )
    FrigoProTheme {
        EcranAujourdhui(
            etat = EtatAujourdhui(
                technicien = "Karim",
                initiales = "K",
                enAvant = LigneTournee(intervention, client),
                suite = emptyList(),
                nombreDuJour = 1,
                devisEnAttente = 2,
                chiffreDuMois = 12400.0,
            ),
            vue = VueTournee.MAINTENANT,
            onVue = {},
            onOuvrir = {},
            onItineraire = {},
            onVoirDevis = {},
        )
    }
}
