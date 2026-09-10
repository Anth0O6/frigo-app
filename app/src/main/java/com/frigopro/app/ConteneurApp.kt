package com.frigopro.app

import android.content.Context
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.FichiersExternes
import com.frigopro.app.data.FrigoProDatabase
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.SauvegardeRepository
import com.frigopro.app.data.TypeInterventionRepository

/**
 * Assemblage manuel des dépendances de l'application.
 *
 * Une poignée d'objets suffit pour l'instant ; introduire Hilt coûterait plus
 * cher en configuration que ce que cette classe fait gagner. Le jour où les
 * dépendances se multiplient, c'est ici que le remplacement se joue.
 */
class ConteneurApp(private val contexte: Context) {

    private val base: FrigoProDatabase by lazy { FrigoProDatabase.creer(contexte) }

    val interventions: InterventionRepository by lazy { InterventionRepository(base.interventionDao()) }

    val clients: ClientRepository by lazy { ClientRepository(base.clientDao()) }

    val typesIntervention: TypeInterventionRepository by lazy {
        TypeInterventionRepository(base.typeInterventionDao())
    }

    val sauvegardes: SauvegardeRepository by lazy {
        SauvegardeRepository(base.interventionDao(), base.clientDao(), base.typeInterventionDao())
    }

    val fichiers: FichiersExternes by lazy {
        FichiersExternes(contexte.applicationContext.contentResolver)
    }
}
