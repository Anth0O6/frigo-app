package com.frigopro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frigopro.app.data.CourbesSaturation
import com.frigopro.app.data.Fluides
import com.frigopro.app.data.arrondiCentieme
import com.frigopro.app.data.arrondiDixieme
import com.frigopro.app.ui.composants.BoutonContour
import com.frigopro.app.ui.composants.BoutonPlein
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.Puce
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffrePetit

/**
 * La réglette pression / température, en feuille.
 *
 * C'est l'outil qu'un frigoriste a dans sa poche sous forme de carton, et qu'il
 * consulte manifold en main : une pression d'un côté, une température de
 * l'autre. La version en verre a trois avantages sur le carton — elle porte tous
 * les fluides sans qu'on les cherche, elle interpole entre deux graduations, et
 * elle calcule la surchauffe au lieu de laisser faire une soustraction de tête,
 * là où l'on est le plus distrait.
 *
 * **Elle dit toujours ce qu'elle ne sait pas.** Une courbe non encore contrôlée
 * l'annonce en clair, et son écart ne peut pas être reporté dans le relevé : la
 * réglette peut montrer un chiffre sous avertissement, elle ne peut pas le
 * laisser entrer en silence dans les données d'une intervention qui partira
 * signée chez un client.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleReglette(
    /** Le fluide de la machine, s'il est connu. */
    fluideMachine: String,
    /** La BP déjà relevée, pour ouvrir sur la bonne pression. */
    bpRelevee: Double?,
    /** La HP déjà relevée. */
    hpRelevee: Double?,
    /** Les fluides dont l'utilisateur a contrôlé la courbe. */
    verifies: Set<String>,
    onVerifier: (String, Boolean) -> Unit,
    /** Reporter la pression du curseur en BP ou en HP du relevé. */
    onReporterPression: (CoteCircuit, Double) -> Unit,
    /** Reporter la surchauffe ou le sous-refroidissement calculé. */
    onReporterEcart: (CoteCircuit, Double) -> Unit,
    onFermer: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onFermer) {
        Text(
            text = "Réglette pression / température",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = MargeEcran),
        )
        CorpsReglette(
            fluideMachine = fluideMachine,
            bpRelevee = bpRelevee,
            hpRelevee = hpRelevee,
            verifies = verifies,
            onVerifier = onVerifier,
            onReporterPression = onReporterPression,
            onReporterEcart = onReporterEcart,
        )
    }
}

/**
 * Le corps de la réglette, sans la fenêtre qui le porte.
 *
 * Deux entrées y mènent, et elles ne se valent pas : depuis l'onglet **Outils**,
 * où l'on vient chercher une correspondance sans intervention ouverte, et depuis
 * l'onglet des **relevés** d'une intervention, manomètre en main, où l'on veut en
 * plus reporter ce qu'on lit. Une seule réglette pour les deux — en recopier une
 * seconde aurait garanti qu'elles divergent, et c'est la plus consultée des deux
 * qui aurait pris du retard.
 *
 * Les deux rappels de report sont **facultatifs** : depuis les Outils il n'y a
 * aucun relevé où reporter, et les boutons disparaissent plutôt que de rester là
 * sans effet.
 */
@Composable
fun CorpsReglette(
    fluideMachine: String,
    bpRelevee: Double?,
    hpRelevee: Double?,
    verifies: Set<String>,
    onVerifier: (String, Boolean) -> Unit,
    onReporterPression: ((CoteCircuit, Double) -> Unit)? = null,
    onReporterEcart: ((CoteCircuit, Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var fluide by remember { mutableStateOf(EtatReglette.fluideInitial(fluideMachine)) }
    var cote by remember { mutableStateOf(CoteCircuit.ASPIRATION) }
    var pression by remember {
        mutableStateOf(EtatReglette.pressionInitiale(fluide, bpRelevee))
    }
    var temperatureLigne by remember { mutableStateOf("") }

    val etat = EtatReglette(
        fluide = fluide,
        pressionRelativeBar = pression,
        cote = cote,
        temperatureLigneC = Nombres.versDecimal(temperatureLigne),
        verifie = fluide in verifies,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChoixFluide(
            retenu = fluide,
            verifies = verifies,
            onChoisir = { choisi ->
                fluide = choisi
                // La pression est ramenée dans la plage du nouveau fluide :
                // 60 bar ont un sens sur du CO₂ et aucun sur du R-600a, et un
                // curseur qui resterait hors plage n'afficherait plus rien.
                pression = EtatReglette.pressionInitiale(
                    choisi,
                    pression.takeIf { EtatReglette.plagePressionRelative(choisi)?.contains(it) == true },
                )
            },
        )

        if (!etat.couvert) {
            Encart(
                texte = "La courbe de ce fluide n'est pas saisie. Aucune valeur n'est " +
                    "déduite : une température plausible tirée d'un fluide voisin serait " +
                    "fausse, et rien ne le dirait.",
                icone = Icons.Filled.Warning,
                alerte = true,
            )
            return@Column
        }

        BanniereVerification(
            fluide = fluide,
            verifie = etat.verifie,
            onVerifier = { onVerifier(fluide, it) },
        )

        RangeePastilles(
            options = CoteCircuit.entries,
            retenue = cote,
            libelle = { it.libelle },
            onChoisir = { choisi ->
                cote = choisi
                // Chaque côté s'ouvre sur sa pression relevée : passer de
                // l'aspiration au refoulement sans changer la pression
                // afficherait une température de condensation à 2 bar.
                val relevee = when (choisi) {
                    CoteCircuit.ASPIRATION -> bpRelevee
                    CoteCircuit.REFOULEMENT -> hpRelevee
                }
                if (relevee != null) pression = EtatReglette.pressionInitiale(fluide, relevee)
            },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )

        CurseurPression(
            fluide = fluide,
            pression = pression,
            onPression = { pression = it },
        )

        CartesSaturation(etat = etat)

        Section(intitule = etat.cote.intituleEcart) {
            ChampTexte(
                libelle = "Température relevée sur la tuyauterie (°C)",
                valeur = temperatureLigne,
                onValeur = { temperatureLigne = it },
                clavier = KeyboardType.Decimal,
            )
            val ecart = etat.ecartK
            if (ecart == null) {
                Text(
                    text = "Saisissez la température lue au contact du tube pour obtenir " +
                        "${etat.cote.intituleEcart.lowercase()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TuileChiffre(
                    valeur = "${Nombres.enTexte(ecart)} K",
                    libelle = etat.cote.intituleEcart.lowercase(),
                    modifier = Modifier.fillMaxWidth(),
                    couleur = teinteEcart(
                        valeur = ecart,
                        normalBas = if (cote == CoteCircuit.ASPIRATION) 3.0 else 2.0,
                        normalHaut = 8.0,
                    ),
                )
                // Le report n'existe que depuis une intervention : consultée
                // depuis les Outils, la réglette n'a aucun relevé où écrire, et un
                // bouton sans effet vaut moins que pas de bouton.
                if (onReporterEcart != null) {
                    BoutonPlein(
                        texte = "Reporter dans le relevé",
                        onClick = { onReporterEcart(cote, ecart) },
                        modifier = Modifier.fillMaxWidth(),
                        actif = etat.reportable,
                    )
                    if (!etat.reportable) {
                        Text(
                            text = "Le report attend que la courbe soit marquée vérifiée : " +
                                "une fois dans le relevé, ce chiffre part dans le compte-rendu " +
                                "signé et nourrit l'aide au dépannage, sans que rien ne dise " +
                                "plus d'où il venait.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Reporter la pression, elle, ne demande aucune vérification : c'est
        // celle qu'on a lue au manomètre, pas une valeur déduite d'une courbe.
        if (onReporterPression != null) {
            BoutonContour(
                texte = "Reporter ${Nombres.enTexte(pression.arrondiCentieme())} bar en " +
                    if (cote == CoteCircuit.ASPIRATION) "BP" else "HP",
                onClick = { onReporterPression(cote, pression.arrondiCentieme()) },
                modifier = Modifier.fillMaxWidth(),
                couleur = MaterialTheme.colorScheme.secondary,
            )
        }
        EspaceVertical(24)
    }
}

/**
 * Le choix du fluide.
 *
 * Les fluides vérifiés portent une coche : c'est la seule façon de savoir d'un
 * coup d'œil lesquels ont déjà été contrôlés, et lesquels restent à faire un soir
 * au bureau, la table constructeur à côté.
 */
@Composable
private fun ChoixFluide(
    retenu: String,
    verifies: Set<String>,
    onChoisir: (String) -> Unit,
) {
    val vert = LocalStatuts.current.termine
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CourbesSaturation.fluidesCouverts.forEach { candidat ->
            val choisi = candidat == retenu
            Puce(
                texte = Fluides.afficher(candidat) + if (candidat in verifies) " ✓" else "",
                modifier = Modifier.clickable { onChoisir(candidat) },
                couleur = when {
                    choisi -> MaterialTheme.colorScheme.onPrimary
                    candidat in verifies -> vert
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fond = if (choisi) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
        }
    }
}

/**
 * L'avertissement, et l'interrupteur qui le lève.
 *
 * C'est l'utilisateur qui engage sa responsabilité en réglant un détendeur sur
 * ces chiffres : c'est donc à lui de dire quand il y consent, après avoir comparé
 * la courbe à sa réglette ou à la table du constructeur. Le marquage se retire
 * aussi bien qu'il se pose — quelqu'un qui s'aperçoit qu'il a coché trop vite
 * doit pouvoir revenir en arrière, sinon il n'osera plus cocher du tout.
 */
@Composable
private fun BanniereVerification(
    fluide: String,
    verifie: Boolean,
    onVerifier: (Boolean) -> Unit,
) {
    val vert = LocalStatuts.current.termine
    Carte(relief = true, liseré = if (verifie) vert else MaterialTheme.colorScheme.error) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (verifie) "Courbe vérifiée" else "Courbe non vérifiée",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (verifie) vert else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = if (verifie) {
                        "Vous avez contrôlé cette courbe. Le report dans le relevé est ouvert."
                    } else {
                        "Comparez-la à votre réglette ou à la table du constructeur avant de " +
                            "vous y fier : un prix faux se rattrape, une surchauffe fausse casse " +
                            "un compresseur."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = verifie,
                onCheckedChange = onVerifier,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = vert,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }
    }
}

/**
 * Le curseur, et la saisie au clavier à côté.
 *
 * Les deux, parce qu'ils ne servent pas au même moment : le curseur pour balayer
 * et voir la température suivre — c'est ce qu'on fait avec un carton qu'on fait
 * glisser —, le clavier pour poser exactement les 6,4 bar qu'affiche le
 * manomètre. Un curseur seul ne permet pas de viser un dixième de bar avec un
 * gant.
 */
@Composable
private fun CurseurPression(
    fluide: String,
    pression: Double,
    onPression: (Double) -> Unit,
) {
    val plage = EtatReglette.plagePressionRelative(fluide) ?: return
    var saisie by remember(fluide) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Pression relative",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // « rel. » est dit, et ce n'est pas un détail de présentation :
                // l'écart avec l'absolu fait environ 7 K sur un R-410A en basse
                // pression, et c'est l'erreur la plus facile à commettre.
                text = "${Nombres.enTexte(pression.arrondiCentieme())} bar rel.",
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = pression.toFloat(),
            onValueChange = {
                saisie = null
                onPression(it.toDouble().arrondiCentieme())
            },
            valueRange = plage.start.toFloat()..plage.endInclusive.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        ChampTexte(
            libelle = "ou saisir la pression (bar rel.)",
            valeur = saisie ?: Nombres.enTexte(pression.arrondiCentieme()),
            onValeur = { texte ->
                saisie = texte
                Nombres.versDecimal(texte)
                    ?.coerceIn(plage.start, plage.endInclusive)
                    ?.let(onPression)
            },
            clavier = KeyboardType.Decimal,
        )
    }
}

/**
 * Les deux températures de saturation.
 *
 * Bulle et rosée sont montrées **toutes les deux**, même sur un corps pur où
 * elles se confondent : les voir ensemble est ce qui apprend ce qu'est un
 * glissement, et une colonne qui disparaîtrait selon le fluide rendrait la
 * réglette plus difficile à lire, pas plus simple. Celle qui compte du côté
 * choisi est mise en avant.
 */
@Composable
private fun CartesSaturation(etat: EtatReglette) {
    val lecture = etat.lecture
    if (lecture == null) {
        Encart(
            texte = "Hors de la plage saisie pour ce fluide. Rien n'est extrapolé : " +
                "au-delà de la table, l'écart grandit avec la distance, et c'est " +
                "justement aux extrêmes qu'on consulte une réglette.",
            icone = Icons.Filled.Warning,
            alerte = true,
        )
        return
    }

    val aspiration = etat.cote == CoteCircuit.ASPIRATION
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val enAvant = MaterialTheme.colorScheme.primary
        val enRetrait = MaterialTheme.colorScheme.onSurfaceVariant
        TuileChiffre(
            valeur = "${Nombres.enTexte(lecture.temperatureRoseeC)} °C",
            libelle = "rosée (vapeur)",
            modifier = Modifier.weight(1f),
            couleur = if (aspiration) enAvant else enRetrait,
        )
        TuileChiffre(
            valeur = "${Nombres.enTexte(lecture.temperatureBulleC)} °C",
            libelle = "bulle (liquide)",
            modifier = Modifier.weight(1f),
            couleur = if (aspiration) enRetrait else enAvant,
        )
    }
    if (etat.glissementNotable) {
        Encart(
            texte = "Glissement de ${Nombres.enTexte(lecture.glissementK.arrondiDixieme())} K : " +
                "la surchauffe se calcule sur la rosée, le sous-refroidissement sur la " +
                "bulle. Les confondre fausse le réglage de tout le glissement.",
        )
    }
}
