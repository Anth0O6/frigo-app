package com.frigopro.app.ui

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
import androidx.compose.material3.MaterialTheme
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
import com.frigopro.app.data.Caloporteur
import com.frigopro.app.data.Conversions
import com.frigopro.app.data.CourbesSaturation
import com.frigopro.app.data.FamilleUnite
import com.frigopro.app.data.Fluides
import com.frigopro.app.data.PeriodiciteControle
import com.frigopro.app.data.PuissanceEchangee
import com.frigopro.app.data.Unite
import com.frigopro.app.data.arrondiDixieme
import com.frigopro.app.ui.composants.Carte
import com.frigopro.app.ui.composants.ChampTexte
import com.frigopro.app.ui.composants.Encart
import com.frigopro.app.ui.composants.MargeEcran
import com.frigopro.app.ui.composants.RangeePastilles
import com.frigopro.app.ui.composants.Section
import com.frigopro.app.ui.composants.TuileChiffre
import com.frigopro.app.ui.theme.LocalStatuts
import com.frigopro.app.ui.theme.StyleChiffre
import com.frigopro.app.ui.theme.StyleChiffrePetit

/** La colonne commune aux outils : défilante, à la marge de l'écran. */
@Composable
private fun ColonneOutil(contenu: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MargeEcran),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        contenu()
        EspaceVertical(24)
    }
}

/**
 * Le convertisseur d'unités.
 *
 * La famille se choisit d'abord, et les deux unités ensuite : c'est l'ordre de la
 * question qu'on se pose — « j'ai des psi, je veux des bars » suppose qu'on sait
 * déjà parler de pression. Présenter les quarante unités à plat aurait obligé à
 * chercher « psi » parmi des kilogrammes et des pieds par minute.
 *
 * Le résultat s'affiche en gros et la saisie reste au-dessus : on convertit en
 * tapant, et voir le chiffre changer sous le doigt est ce qui fait qu'on se fie à
 * l'outil.
 */
@Composable
fun OutilConvertisseur() {
    var famille by remember { mutableStateOf(FamilleUnite.PRESSION) }
    var de by remember { mutableStateOf(Unite.PSI) }
    var vers by remember { mutableStateOf(Unite.BAR) }
    var saisie by remember { mutableStateOf("") }

    val unites = Conversions.unites(famille)
    val valeur = Nombres.versDecimal(saisie)
    val resultat = valeur?.let { Conversions.convertir(it, de, vers) }

    ColonneOutil {
        RangeePastilles(
            options = FamilleUnite.entries,
            retenue = famille,
            libelle = { it.libelle },
            onChoisir = { choisie ->
                famille = choisie
                // Les deux unités doivent rester de la famille : garder « psi » en
                // passant aux masses donnerait une conversion refusée, donc un
                // écran vide sans explication.
                val candidates = Conversions.unites(choisie)
                de = candidates.first()
                vers = candidates.getOrElse(1) { candidates.first() }
            },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )

        ChampTexte(
            libelle = "Valeur en ${de.symbole}",
            valeur = saisie,
            onValeur = { saisie = it },
            clavier = KeyboardType.Decimal,
        )

        Section(intitule = "De") {
            RangeePastilles(
                options = unites,
                retenue = de,
                libelle = { it.symbole },
                onChoisir = { de = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }

        Section(intitule = "Vers") {
            RangeePastilles(
                options = unites,
                retenue = vers,
                libelle = { it.symbole },
                onChoisir = { vers = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }

        Carte(relief = true) {
            Text(
                text = if (resultat == null) "—" else "${Nombres.enTexte(resultat, decimales = 4)} ${vers.symbole}",
                style = StyleChiffre,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (valeur == null) {
                    "Saisissez une valeur."
                } else {
                    "${Nombres.enTexte(valeur, decimales = 4)} ${de.symbole}"
                },
                style = StyleChiffrePetit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (famille == FamilleUnite.PRESSION) {
            Encart(
                texte = "Ces pressions sont des valeurs brutes : le convertisseur ne dit pas " +
                    "si elles sont relatives ou absolues. L'écart est de 1,013 bar, soit " +
                    "environ 7 K de température de saturation sur un R-410A en basse pression.",
            )
        }
        if (famille == FamilleUnite.TEMPERATURE) {
            Encart(
                texte = "Il s'agit d'une température, pas d'un écart : 10 °C valent 50 °F, " +
                    "mais un écart de 10 K vaut 18 °F. Pour une surchauffe, prenez " +
                    "« Écart de température ».",
                icone = Icons.Filled.Warning,
                alerte = true,
            )
        }
    }
}

/** Ce que le bilan cherche : l'une des trois grandeurs, les deux autres étant connues. */
private enum class Inconnue(val libelle: String) {
    PUISSANCE("Puissance"),
    ECART("Écart"),
    DEBIT("Débit"),
}

/**
 * Le bilan d'un circuit secondaire.
 *
 * Les trois sens de la même formule, parce que les trois se posent : on mesure
 * pour contrôler une puissance, on cherche le Δt à attendre d'une puissance de
 * plaque, on dimensionne un débit. Un outil qui n'offrirait que le premier sens
 * laisserait faire les deux divisions de tête.
 */
@Composable
fun OutilBilanPuissance() {
    var caloporteur by remember { mutableStateOf(Caloporteur.EAU) }
    var cherchee by remember { mutableStateOf(Inconnue.PUISSANCE) }
    var debit by remember { mutableStateOf("") }
    var ecart by remember { mutableStateOf("") }
    var puissance by remember { mutableStateOf("") }

    ColonneOutil {
        RangeePastilles(
            options = Caloporteur.entries,
            retenue = caloporteur,
            libelle = { it.libelle },
            onChoisir = { caloporteur = it },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )

        Section(intitule = "Ce que je cherche") {
            RangeePastilles(
                options = Inconnue.entries,
                retenue = cherchee,
                libelle = { it.libelle },
                onChoisir = { cherchee = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Seules les deux grandeurs connues sont demandées : laisser les trois
        // champs ouverts aurait posé la question de celui qui gagne.
        if (cherchee != Inconnue.DEBIT) {
            ChampTexte(
                libelle = "Débit (m³/h)",
                valeur = debit,
                onValeur = { debit = it },
                clavier = KeyboardType.Decimal,
            )
        }
        if (cherchee != Inconnue.ECART) {
            ChampTexte(
                libelle = "Écart de température (K)",
                valeur = ecart,
                onValeur = { ecart = it },
                clavier = KeyboardType.Decimal,
            )
        }
        if (cherchee != Inconnue.PUISSANCE) {
            ChampTexte(
                libelle = "Puissance (kW)",
                valeur = puissance,
                onValeur = { puissance = it },
                clavier = KeyboardType.Decimal,
            )
        }

        val lu = Triple(
            Nombres.versDecimal(debit),
            Nombres.versDecimal(ecart),
            Nombres.versDecimal(puissance),
        )
        val resultat = when (cherchee) {
            Inconnue.PUISSANCE -> PuissanceEchangee.kilowatts(lu.first, lu.second, caloporteur)
            Inconnue.ECART -> PuissanceEchangee.ecartAttenduK(lu.third, lu.first, caloporteur)
            Inconnue.DEBIT -> PuissanceEchangee.debitAttenduM3ParHeure(lu.third, lu.second, caloporteur)
        }
        val unite = when (cherchee) {
            Inconnue.PUISSANCE -> "kW"
            Inconnue.ECART -> "K"
            Inconnue.DEBIT -> "m³/h"
        }

        TuileChiffre(
            valeur = if (resultat == null) "—" else "${Nombres.enTexte(resultat)} $unite",
            libelle = cherchee.libelle.lowercase(),
            modifier = Modifier.fillMaxWidth(),
            couleur = MaterialTheme.colorScheme.primary,
        )

        // La condition de référence est dite, et ce n'est pas une formalité : la
        // masse volumique de l'air varie de près de moitié entre une chambre froide
        // et une toiture en août, et le résultat est un ordre de grandeur juste —
        // de quoi dire si un échangeur rend ce qu'il doit rendre, pas un relevé de
        // réception.
        Encart(
            texte = "${caloporteur.libelle} ${caloporteur.reference} : " +
                "${Nombres.enTexte(caloporteur.masseVolumique)} kg/m³, " +
                "${Nombres.enTexte(caloporteur.chaleurMassique)} kJ/(kg·K). " +
                "Ces valeurs varient avec la température : le résultat est un ordre de " +
                "grandeur, pas un relevé de réception.",
        )
    }
}

/**
 * Le contrôle d'étanchéité : équivalent CO₂ et périodicité.
 *
 * Le même calcul que celui de la fiche machine, mais **sans machine** : la
 * question se pose au téléphone, avant de se déplacer, ou devant une installation
 * qui n'est pas encore au carnet. L'y enfermer obligeait à créer une fiche pour
 * répondre à « est-ce que ça doit être contrôlé ? ».
 */
@Composable
fun OutilControleEtancheite() {
    var fluide by remember { mutableStateOf("") }
    var charge by remember { mutableStateOf("") }
    var detecteur by remember { mutableStateOf(false) }

    val statuts = LocalStatuts.current
    val gwp = Fluides.gwp(fluide)
    val chargeKg = Nombres.versDecimal(charge)
    val tonnes = chargeKg?.let { Fluides.tonnesEquivalentCo2(fluide, it) }
    val periodicite = tonnes?.let { PeriodiciteControle.pour(it, detecteurFixe = detecteur) }

    ColonneOutil {
        ChampTexte(
            libelle = "Fluide",
            valeur = fluide,
            onValeur = { fluide = it },
        )
        ChampTexte(
            libelle = "Charge (kg)",
            valeur = charge,
            onValeur = { charge = it },
            clavier = KeyboardType.Decimal,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Détecteur de fuite fixe", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Il double les intervalles (art. 4 § 3 du règlement 517/2014).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = detecteur,
                onCheckedChange = { detecteur = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }

        if (fluide.isNotBlank() && gwp == null) {
            Encart(
                texte = "Fluide inconnu au catalogue : aucun GWP n'est deviné, donc aucune " +
                    "périodicité annoncée. Un chiffre plausible et faux ferait manquer un " +
                    "contrôle obligatoire.",
                icone = Icons.Filled.Warning,
                alerte = true,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TuileChiffre(
                valeur = gwp?.toString() ?: "—",
                libelle = "GWP (AR4)",
                modifier = Modifier.weight(1f),
            )
            TuileChiffre(
                valeur = tonnes?.let { Nombres.enTexte(it.arrondiDixieme()) } ?: "—",
                libelle = "t éq. CO₂",
                modifier = Modifier.weight(1f),
                couleur = MaterialTheme.colorScheme.primary,
            )
        }

        TuileChiffre(
            valeur = periodicite?.libelle ?: "—",
            libelle = "contrôle d'étanchéité",
            modifier = Modifier.fillMaxWidth(),
            couleur = when (periodicite) {
                null, PeriodiciteControle.AUCUNE -> MaterialTheme.colorScheme.onSurface
                else -> statuts.aValider
            },
        )

        if (periodicite == PeriodiciteControle.AUCUNE && tonnes != null) {
            Encart(
                texte = "Sous le seuil de 5 tonnes équivalent CO₂ : aucune obligation de " +
                    "contrôle périodique. Ce n'est pas une dispense de bonne pratique.",
            )
        }
    }
}

/**
 * La fiche d'un fluide : ce qu'il faut en savoir avant d'y toucher.
 *
 * Le GWP pour la réglementation, la **classe de sécurité** pour la façon de
 * travailler, le glissement pour la lecture des pressions. Les trois sont sur le
 * même écran parce qu'on se les demande au même moment : en ouvrant une
 * installation qu'on ne connaît pas.
 */
@Composable
fun OutilFicheFluide(verifies: Set<String>) {
    var fluide by remember { mutableStateOf("") }

    val statuts = LocalStatuts.current
    val nom = Fluides.normaliser(fluide)
    val gwp = Fluides.gwp(fluide)
    val classe = Fluides.classeSecurite(fluide)
    val points = CourbesSaturation.points(fluide)

    ColonneOutil {
        ChampTexte(
            libelle = "Fluide",
            valeur = fluide,
            onValeur = { fluide = it },
        )

        if (fluide.isBlank()) {
            Encart(
                texte = "Saisissez un fluide : « R410A », « R-448A », « r32 » — la graphie " +
                    "n'a pas d'importance.",
            )
            return@ColonneOutil
        }

        if (gwp == null && classe == null) {
            Encart(
                texte = "« ${Fluides.afficher(nom)} » n'est pas au catalogue. Rien n'en est " +
                    "déduit : ni GWP, ni classe de sécurité. Le rapprocher d'un fluide " +
                    "« voisin » reviendrait à en inventer les propriétés.",
                icone = Icons.Filled.Warning,
                alerte = true,
            )
            return@ColonneOutil
        }

        Text(text = Fluides.afficher(nom), style = StyleChiffre)

        if (classe != null) {
            // La classe avant le GWP : le GWP décide d'une paperasse, la classe
            // décide de la façon de travailler et de ce qui peut prendre feu.
            Carte(
                relief = true,
                liseré = when {
                    classe.toxique -> MaterialTheme.colorScheme.error
                    classe.inflammable -> statuts.aValider
                    else -> statuts.termine
                },
            ) {
                Text(text = classe.code, style = StyleChiffre)
                Text(text = classe.toxicite, style = MaterialTheme.typography.bodyMedium)
                Text(text = classe.inflammabilite, style = MaterialTheme.typography.bodyMedium)
                if (classe.inflammable || classe.toxique) {
                    Text(
                        text = "Classification ISO 817. Un fluide inflammable impose des " +
                            "précautions de brasage, de ventilation et une charge maximale " +
                            "selon le volume du local.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TuileChiffre(
                valeur = gwp?.toString() ?: "—",
                libelle = "GWP (AR4)",
                modifier = Modifier.weight(1f),
            )
            TuileChiffre(
                valeur = gwp?.let { Nombres.enTexte((it / 1000.0).arrondiDixieme()) } ?: "—",
                libelle = "t éq. CO₂ par tonne",
                modifier = Modifier.weight(1f),
            )
        }

        Section(intitule = "Courbe de saturation") {
            if (points.isEmpty()) {
                Encart(
                    texte = "Aucune courbe saisie pour ce fluide : la réglette ne peut pas " +
                        "l'afficher, et n'approxime pas par un voisin.",
                )
            } else {
                val premier = points.first()
                val dernier = points.last()
                // Le glissement se lit **au milieu de la plage**, pas à ses bornes :
                // il se resserre aux extrêmes, et le donner à −40 °C laisserait
                // croire à un mélange plus sage qu'il ne l'est en fonctionnement.
                val milieu = points[points.size / 2]
                val glissement = CourbesSaturation.temperatureA(nom, milieu.bulleBarAbs)?.glissementK
                if (glissement != null) {
                    TuileChiffre(
                        valeur = "${Nombres.enTexte(glissement.arrondiDixieme())} K",
                        libelle = "glissement vers ${Nombres.enTexte(milieu.temperatureC)} °C",
                        modifier = Modifier.fillMaxWidth(),
                        couleur = if (glissement >= 1.0) statuts.aValider else MaterialTheme.colorScheme.onSurface,
                    )
                    if (glissement >= 1.0) {
                        Text(
                            text = "Mélange à glissement : la surchauffe se calcule sur la " +
                                "rosée, le sous-refroidissement sur la bulle. Les confondre " +
                                "fausse le réglage de tout le glissement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "Plage saisie : de ${Nombres.enTexte(premier.temperatureC)} °C " +
                        "à ${Nombres.enTexte(dernier.temperatureC)} °C.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Encart(
                    texte = if (nom in verifies) {
                        "Courbe vérifiée : le report d'une surchauffe dans un relevé est ouvert."
                    } else {
                        "Courbe non vérifiée. Elle s'affiche, mais aucun écart calculé dessus " +
                            "ne peut entrer dans un relevé — cochez-la dans la réglette après " +
                            "l'avoir comparée à une table constructeur."
                    },
                    icone = if (nom in verifies) null else Icons.Filled.Warning,
                    alerte = nom !in verifies,
                )
            }
        }
    }
}
