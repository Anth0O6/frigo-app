package com.frigopro.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette « froid industriel », reprise telle quelle de la maquette.
 *
 * Les valeurs sont figées et non dérivées de Material You : l'application est
 * un outil de terrain qu'on lit au soleil, gants aux mains, et dont les
 * couleurs *signifient* quelque chose — l'ambre est l'intervention en cours et
 * l'alerte, le cyan est l'action et le fait accompli. Laisser le fond d'écran
 * du téléphone les redéfinir brouillerait cette lecture.
 */

// — Sombre, le mode par défaut —————————————————————————————————————————————

/** Fond de l'application, le plus profond. */
val Nuit = Color(0xFF081520)

/** Carte ordinaire, et barre d'onglets. */
val NuitCarte = Color(0xFF0C2030)

/** Surface surélevée : en-têtes, carte de l'intervention en cours. */
val NuitSurface = Color(0xFF0F2734)

/** Pastille, puce, bouton secondaire. */
val NuitPuce = Color(0xFF173141)

/** Emplacement d'une image en attente de chargement. */
val NuitImage = Color(0xFF122C3A)

/** Filet de séparation. */
val NuitFilet = Color(0xFF17303F)

/** Filet plus marqué, sous un total par exemple. */
val NuitFiletFort = Color(0xFF1C3A4A)

/** Trait discontinu : zone à remplir, ligne à ajouter. */
val NuitPointille = Color(0xFF2A4A5C)

/** Texte courant sur fond sombre. */
val NuitTexte = Color(0xFFE6EFF3)

/** Texte secondaire : sous-titres, unités, légendes. */
val NuitTexteFaible = Color(0xFF8FA6B4)

/** Chevrons et repères de liste, à la limite du lisible et c'est voulu. */
val NuitTexteTresFaible = Color(0xFF4E6C7E)

/** Onglet non retenu dans la barre du bas. */
val NuitOngletInactif = Color(0xFF6D8695)

// — Accents, communs aux deux modes ————————————————————————————————————————

/** L'action, et ce qui est terminé. */
val Cyan = Color(0xFF22D3C5)

/** Texte posé sur [Cyan] : presque noir, pour que le contraste tienne. */
val SurCyan = Color(0xFF062229)

/** Cyan éclairci, pour un tracé fin sur fond sombre. */
val CyanClair = Color(0xFF7FE3DA)

/** L'intervention en cours, et l'alerte. */
val Ambre = Color(0xFFFFB343)

/** Texte d'une alerte développée, sur fond ambre très dilué. */
val AmbreTexte = Color(0xFFF6DFB8)

/** Bleu froid : l'information qui n'appelle pas d'action. */
val BleuFroid = Color(0xFF9CC9E3)

// — Clair, quand le technicien coupe le thème sombre ——————————————————————

/** Fond clair : le gris bleuté de la maquette, pas un blanc pur. */
val Jour = Color(0xFFEDF1F4)

val JourSurface = Color(0xFFFFFFFF)

val JourCarte = Color(0xFFF4F7F9)

val JourFilet = Color(0xFFD2DBE1)

val JourTexte = Color(0xFF0B2A3D)

val JourTexteFaible = Color(0xFF5C6B76)

/**
 * Cyan assombri pour le mode clair : [Cyan] sur blanc ne passe aucun seuil de
 * contraste. C'est la teinte des liens de la maquette.
 */
val CyanSombre = Color(0xFF0E7C74)

/** Ambre assombri, même raison. */
val AmbreSombre = Color(0xFF8A5200)

/** Bleu froid assombri, même raison. */
val BleuFroidSombre = Color(0xFF2C5F7E)
