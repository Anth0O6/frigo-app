package com.frigopro.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette de l'application, reprise de la maquette.
 *
 * Elle est **neutre et sombre** — un noir profond, des cartes gris anthracite —
 * pour que la seule couleur d'un écran soit celle qui porte une information.
 * C'est l'inverse d'un habillage : ici, voir de la couleur veut toujours dire
 * quelque chose, et cinq teintes suffisent à dire quoi (voir [CouleursStatut]).
 *
 * Le fond est presque noir plutôt que gris foncé : sur les dalles OLED des
 * téléphones récents, un pixel noir est un pixel éteint, et l'application
 * s'allume vingt fois par jour dans une camionnette.
 *
 * Les valeurs sont figées et non dérivées de Material You. Laisser le fond
 * d'écran du téléphone repeindre un rouge d'urgence en vert effacerait une
 * information.
 */

// — Fonds, du plus profond au plus clair ——————————————————————————————————

/** Fond de l'application. Noir, pour la dalle OLED. */
val Noir = Color(0xFF0A0A0B)

/** Carte ordinaire. */
val Carte = Color(0xFF1C1C1E)

/**
 * Carte d'une ligne déjà faite.
 *
 * Un ton *sous* la carte ordinaire, et non au-dessus : ce qui est terminé doit
 * s'effacer du regard, pas le retenir.
 */
val CarteEteinte = Color(0xFF141416)

/** Surface posée sur une carte : une puce, un champ, une vignette. */
val Relief = Color(0xFF2C2C2E)

/** Filet de séparation, à peine visible et c'est voulu. */
val Filet = Color(0xFF3A3A3C)

/** Trait discontinu : une zone à remplir, une ligne à ajouter. */
val Pointille = Color(0xFF48484A)

// — Textes ————————————————————————————————————————————————————————————————

val Texte = Color(0xFFFFFFFF)

/** Sous-titre, unité, légende. Le gris iOS à 55 %. */
val TexteSecondaire = Color(0x8CEBEBF5)

/** Intitulé de section, libellé d'onglet au repos. À 45 %. */
val TexteTertiaire = Color(0x73EBEBF5)

/** Chevron, repère de liste — à la limite du lisible, et c'est le but. À 30 %. */
val TexteEteint = Color(0x4DEBEBF5)

// — Accent ————————————————————————————————————————————————————————————————

/** L'action : ce sur quoi on appuie pour que quelque chose arrive. */
val Bleu = Color(0xFF0F6FD6)

/** Bleu éclairci, pour un texte d'action sur un fond déjà bleuté. */
val BleuClair = Color(0xFF4A9BFF)

/** Texte posé sur un aplat de [Bleu]. */
val SurBleu = Color(0xFFFFFFFF)

// — Les cinq statuts ——————————————————————————————————————————————————————

/**
 * L'urgence, et l'intervention en cours.
 *
 * Les deux partagent le rouge à dessein : ce qu'on est en train de faire et ce
 * qui ne peut pas attendre appellent le même geste — y aller maintenant.
 */
val Urgence = Color(0xFFFF453A)

/** Ce qui est prévu et se déroulera comme prévu. */
val Planifie = Color(0xFF0F6FD6)

/** Ce qui attend une décision : un devis à envoyer, une fiche à signer. */
val AValider = Color(0xFFFF9F0A)

/** Le chiffrage, qui n'est ni du travail fait ni du travail promis. */
val CouleurDevis = Color(0xFFBF5AF2)

/** Ce qui est fait. */
val Termine = Color(0xFF30D158)

/** Texte posé sur un aplat de [Termine] : très sombre, pour que ça se lise. */
val SurTermine = Color(0xFF04240D)

// — Clair, quand le technicien coupe le thème sombre ——————————————————————

/** Le gris de fond d'iOS en clair, et non un blanc pur qui éblouirait. */
val Jour = Color(0xFFF2F2F7)

val JourCarte = Color(0xFFFFFFFF)

val JourFilet = Color(0xFFD1D1D6)

val JourTexte = Color(0xFF000000)

val JourTexteSecondaire = Color(0x993C3C43)

/** Les teintes de statut assombries : sur blanc, les vives ne passent aucun seuil. */
val BleuSombre = Color(0xFF0A58AC)

val UrgenceSombre = Color(0xFFC9251C)

val AValiderSombre = Color(0xFF8A5200)

val DevisSombre = Color(0xFF7B2FA8)

val TermineSombre = Color(0xFF1B7A34)
