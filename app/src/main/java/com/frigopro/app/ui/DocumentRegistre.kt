package com.frigopro.app.ui

import com.frigopro.app.data.LigneRegistre
import com.frigopro.app.data.Parametres
import com.frigopro.app.data.Registre
import com.frigopro.app.data.SensFluide
import com.frigopro.app.data.TotalFluide
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ce que le registre des fluides imprimé dit.
 *
 * Le quatrième document de la chaîne, et le seul qui ne s'adresse pas à un
 * client : c'est la pièce qu'on pose sur la table quand un inspecteur demande
 * la traçabilité des fluides frigorigènes (règlement (UE) 517/2014, art. 6). Il
 * n'a **pas de destinataire** pour cette raison — un registre est tenu par
 * l'entreprise, pour elle-même, et lui inventer un destinataire aurait laissé
 * croire qu'il s'envoie à quelqu'un.
 *
 * Il emprunte la mise en page du compte-rendu — des blocs, pas un tableau
 * chiffré — parce qu'un registre ne parle pas d'argent non plus, et que le
 * groupement par fluide est exactement ce qu'un bloc sait faire : un titre, des
 * lignes, et une coupure qui reprend le titre quand la page est pleine.
 *
 * ## Ce qu'il ne fait pas
 *
 * **Il ne se signe pas.** Un registre n'est pas un engagement pris devant
 * quelqu'un, c'est une tenue de comptes ; y mettre un cadre de signature aurait
 * suggéré qu'il vaut attestation, ce qu'il n'est pas.
 *
 * **Il ne se tait jamais.** Une année sans mouvement produit quand même un
 * registre, qui dit qu'aucun fluide n'a bougé : « rien à montrer » et « rien ne
 * s'est passé » ne valent pas la même chose devant un contrôle, et seul le
 * second est une réponse.
 *
 * **Il n'invente aucun équivalent CO₂.** Un fluide hors catalogue n'a pas de
 * GWP, donc pas de tonnage, et la ligne le dit plutôt que d'afficher un zéro
 * qui placerait l'entreprise sous un seuil qu'elle n'a peut-être pas respecté.
 */
object DocumentRegistre {

    fun de(
        registre: Registre,
        parametres: Parametres,
        aujourdhui: LocalDate = LocalDate.now(),
    ): DocumentImprime = DocumentImprime(
        titre = "REGISTRE FLUIDES",
        // **Pas de numéro** : un registre n'en porte pas, c'est une tenue de
        // comptes et non une pièce numérotée comme une facture. L'année tient ce
        // rôle, et elle est dans les dates juste en dessous.
        numero = "",
        emetteur = emetteurDe(parametres, "Registre des fluides"),
        // Aucun destinataire : voir plus haut. La liste vide se traduit par une
        // colonne de droite qui ne porte que le titre et l'année.
        destinataire = emptyList(),
        dates = listOf(
            "Année ${registre.annee}",
            "Édité le ${aujourdhui.format(FORMAT_DATE_DOCUMENT)}",
        ),
        objet = "Mouvements de fluides frigorigènes",
        machine = "",
        blocs = blocs(registre),
        mentions = mentions(registre, parametres),
        logoFichier = parametres.logoFichier,
        // Le nom du fichier porte l'année, lui : « 2026.pdf » dans un dossier
        // de téléchargements ne dit rien, et c'est là qu'un inspecteur le
        // retrouvera six mois plus tard.
        nomSansNumero = "registre-fluides-${registre.annee}",
    )

    private fun blocs(registre: Registre): List<BlocImprime> = buildList {
        add(bilan(registre))
        // Un bloc par fluide : c'est le groupement qui répond à « combien de
        // R-404A cette année », et la pagination sait déjà reprendre un titre
        // quand un fluide déborde sur la page suivante.
        registre.totaux.forEach { total ->
            add(
                BlocImprime(
                    intitule = total.fluide,
                    lignes = registre.lignesDe(total.fluide).map(::ligne),
                ),
            )
        }
    }

    /**
     * Le bilan, en tête.
     *
     * Avant le détail et non après : c'est le chiffre qu'on demande en premier,
     * et le faire chercher au bout de quatre pages de mouvements aurait inversé
     * l'ordre de la conversation.
     */
    private fun bilan(registre: Registre): BlocImprime = BlocImprime(
        intitule = "Bilan de l'année ${registre.annee}",
        lignes = if (registre.vide) {
            listOf(LigneBloc("Aucun mouvement de fluide consigné", "0,00 kg"))
        } else {
            registre.totaux.flatMap { total ->
                buildList {
                    add(
                        LigneBloc(
                            intitule = total.fluide,
                            valeur = "+${Nombres.enMasse(total.ajoute)} / " +
                                "−${Nombres.enMasse(total.recupere)} kg",
                        ),
                    )
                    val tonnes = total.tonnesEqCo2Ajoutees
                    add(
                        LigneBloc(
                            intitule = "    équivalent CO₂ des charges ajoutées" +
                                (total.gwp?.let { " (GWP $it)" } ?: ""),
                            valeur = if (tonnes != null) {
                                "${Nombres.enTexte(tonnes)} t"
                            } else {
                                // Dit, et non deviné : ce chiffre sert à se
                                // situer sous un seuil réglementaire.
                                "GWP inconnu"
                            },
                        ),
                    )
                }
            }
        },
    )

    /**
     * Une ligne de mouvement.
     *
     * L'année est absente de la date parce qu'elle est dans le titre du
     * document : la répéter sur chaque ligne aurait mangé la place du nom de la
     * machine, qui est ce qu'on cherche quand on remonte un kilo.
     *
     * Le sens est écrit en toutes lettres et non porté par un signe : un « − »
     * devant un nombre se lit « moins » et non « récupéré », et le registre
     * emploie le vocabulaire du métier — on *ajoute* une charge, on *récupère*
     * du fluide.
     */
    private fun ligne(ligne: LigneRegistre): LigneBloc = LigneBloc(
        intitule = listOf(
            ligne.date.format(FORMAT_JOUR_REGISTRE),
            ligne.numero,
            ligne.client,
            ligne.machine,
        ).filter { it.isNotBlank() }.joinToString(" · "),
        valeur = "${Nombres.enMasse(ligne.masseKg)} kg " +
            when (ligne.sens) {
                SensFluide.AJOUT -> "ajouté"
                SensFluide.RECUPERATION -> "récupéré"
            },
    )

    private fun mentions(registre: Registre, parametres: Parametres): List<String> = buildList {
        add(
            "Registre des mouvements de fluides frigorigènes, tenu au titre de " +
                "l'article 6 du règlement (UE) n° 517/2014.",
        )
        add("Masses exprimées en kilogrammes de fluide.")
        if (parametres.attestation.isBlank()) {
            // Dit à l'utilisateur, pas à l'inspecteur : le numéro d'attestation
            // est la première chose qu'un contrôle rapproche du registre, et
            // l'export est le dernier moment pour s'apercevoir qu'il manque.
            add("(Attestation de capacité non renseignée dans les Réglages)")
        }
        if (registre.vide) {
            add("Aucun mouvement n'a été consigné sur cette période.")
        }
    }
}

/** « 14/05 » : l'année est dans le titre du registre. */
private val FORMAT_JOUR_REGISTRE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH)
