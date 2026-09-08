package com.frigopro.app

import android.content.Context
import com.frigopro.app.data.FrigoProDatabase
import com.frigopro.app.data.InterventionRepository

/**
 * Assemblage manuel des dépendances de l'application.
 *
 * Une poignée d'objets suffit pour l'instant ; introduire Hilt coûterait plus
 * cher en configuration que ce que cette classe fait gagner. Le jour où les
 * dépendances se multiplient, c'est ici que le remplacement se joue.
 */
class ConteneurApp(contexte: Context) {

    private val base: FrigoProDatabase by lazy { FrigoProDatabase.creer(contexte) }

    val interventions: InterventionRepository by lazy { InterventionRepository(base.interventionDao()) }
}
