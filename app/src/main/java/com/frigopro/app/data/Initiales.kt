package com.frigopro.app.data

/**
 * « KB » pour « Karim Benali », « BM » pour « Boucherie Martel ».
 *
 * Quatre endroits en avaient besoin — la pastille d'un technicien sur le
 * planning, celle des Réglages, l'avatar d'un client, le créneau d'une frise —
 * et chacun l'avait réécrit à sa façon. Ils divergeaient déjà : l'un coupait sur
 * l'apostrophe, l'autre non, si bien que « L'Épicerie du coin » ne donnait pas
 * les mêmes lettres selon l'écran. Une seule fonction supprime la question.
 *
 * Deux lettres au plus : c'est ce qui tient dans une pastille, et au-delà on ne
 * lit plus des initiales mais un mot. « ? » quand il n'y a rien à abréger —
 * une pastille vide ressemblerait à un défaut d'affichage.
 */
fun initialesDe(nom: String): String = nom
    .split(' ', '-', '\'', '’')
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString("")
    .ifEmpty { "?" }
