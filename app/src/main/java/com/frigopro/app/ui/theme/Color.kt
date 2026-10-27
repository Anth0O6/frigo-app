package com.frigopro.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette de l'application : la direction « Instrument de mesure ».
 *
 * L'écran se lit comme un manomètre numérique — fond OLED noir, panneaux à
 * peine plus clairs, gros chiffres à chasse fixe. **Trois rôles de couleur**, et
 * trois luminances bien distinctes pour qu'un daltonien ne les confonde pas :
 * l'**ambre** pour ce qui est en cours et ce qui alerte, le **cyan** pour
 * l'action et le fait accompli, la **framboise** pour l'urgence. Ce qui est
 * seulement prévu reste **gris** : « à venir » n'appelle aucun geste, et une
 * liste de journée toute en couleur n'en signalerait plus aucune. Les noms historiques des constantes sont gardés — `Bleu` est l'action,
 * `AValider` l'ambre — pour ne pas réécrire chaque écran.
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
val Noir = Color(0xFF050607)

/** Carte ordinaire. */
val Carte = Color(0xFF101316)

/**
 * Carte d'une ligne déjà faite.
 *
 * Un ton *sous* la carte ordinaire, et non au-dessus : ce qui est terminé doit
 * s'effacer du regard, pas le retenir.
 */
val CarteEteinte = Color(0xFF0B0D0F)

/** Surface posée sur une carte : une puce, un champ, une vignette. */
val Relief = Color(0xFF1A1F23)

/** Filet de séparation, à peine visible et c'est voulu. */
val Filet = Color(0xFF262C31)

/** Trait discontinu : une zone à remplir, une ligne à ajouter. */
val Pointille = Color(0xFF39414A)

// — Textes ————————————————————————————————————————————————————————————————

val Texte = Color(0xFFF4F5F6)

/** Sous-titre, unité, légende. Le gris iOS à 55 %. */
val TexteSecondaire = Color(0xFF9AA3AB)

/** Intitulé de section, libellé d'onglet au repos. À 45 %. */
val TexteTertiaire = Color(0xFF6E777F)

/** Chevron, repère de liste — à la limite du lisible, et c'est le but. À 30 %. */
val TexteEteint = Color(0xFF4A5259)

// — Accent ————————————————————————————————————————————————————————————————

/** L'action : ce sur quoi on appuie pour que quelque chose arrive. */
val Bleu = Color(0xFF2ED3E6)

/** Bleu éclairci, pour un texte d'action sur un fond déjà bleuté. */
val BleuClair = Color(0xFF7DE6F2)

/** Texte posé sur un aplat de [Bleu]. */
val SurBleu = Color(0xFF00262B)

// — Les cinq statuts ——————————————————————————————————————————————————————

/**
 * L'urgence, et l'intervention en cours.
 *
 * Les deux partagent le rouge à dessein : ce qu'on est en train de faire et ce
 * qui ne peut pas attendre appellent le même geste — y aller maintenant.
 */
val Urgence = Color(0xFFFF3B5C)

/** Ce qui est prévu et se déroulera comme prévu. */
val Planifie = Color(0xFF9AA3AB)

/** Ce qui attend une décision : un devis à envoyer, une fiche à signer. */
val AValider = Color(0xFFFFB224)

/** Le chiffrage, qui n'est ni du travail fait ni du travail promis. */
val CouleurDevis = Color(0xFFA79BFF)

/** Ce qui est fait. */
val Termine = Color(0xFF2ED3E6)

/** Texte posé sur un aplat de [Termine] : très sombre, pour que ça se lise. */
val SurTermine = Color(0xFF00262B)

// — Clair, quand le technicien coupe le thème sombre ——————————————————————

/** Le gris de fond d'iOS en clair, et non un blanc pur qui éblouirait. */
val Jour = Color(0xFFF4F5F6)

val JourCarte = Color(0xFFFFFFFF)

val JourFilet = Color(0xFFD5DADF)

val JourTexte = Color(0xFF0B0D0F)

val JourTexteSecondaire = Color(0xFF4E5860)

/** Les teintes de statut assombries : sur blanc, les vives ne passent aucun seuil. */
val BleuSombre = Color(0xFF00788A)

val UrgenceSombre = Color(0xFFC8102E)

val AValiderSombre = Color(0xFF8F5A00)

val DevisSombre = Color(0xFF5B4BB5)

val TermineSombre = Color(0xFF00788A)
