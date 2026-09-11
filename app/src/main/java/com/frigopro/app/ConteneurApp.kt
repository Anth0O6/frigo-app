package com.frigopro.app

import android.content.Context
import com.frigopro.app.data.ClientRepository
import com.frigopro.app.data.DevisRepository
import com.frigopro.app.data.EquipementRepository
import com.frigopro.app.data.FichiersExternes
import com.frigopro.app.data.FrigoProDatabase
import com.frigopro.app.data.InterventionRepository
import com.frigopro.app.data.ParametresRepository
import com.frigopro.app.data.SauvegardeRepository
import com.frigopro.app.data.StockagePhotos
import com.frigopro.app.data.SuiviRepository
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

    private val stockagePhotos: StockagePhotos by lazy { StockagePhotos(contexte.applicationContext) }

    val equipements: EquipementRepository by lazy {
        EquipementRepository(base.equipementDao(), stockagePhotos)
    }

    /** Ce qui s'est passé sur place : relevés, fluide, pièces, photos avant/après. */
    val suivi: SuiviRepository by lazy { SuiviRepository(base.suiviDao(), stockagePhotos) }

    val devis: DevisRepository by lazy { DevisRepository(base.devisDao()) }

    val parametres: ParametresRepository by lazy { ParametresRepository(base.parametresDao()) }

    val sauvegardes: SauvegardeRepository by lazy {
        SauvegardeRepository(
            base.interventionDao(),
            base.clientDao(),
            base.typeInterventionDao(),
            base.equipementDao(),
            base.suiviDao(),
            base.devisDao(),
            base.parametresDao(),
        )
    }

    /**
     * Le stockage des images, dont la sauvegarde a besoin directement : elle
     * embarque des fichiers, que le dépôt ne connaît que par leur nom.
     */
    val photos: StockagePhotos get() = stockagePhotos

    val fichiers: FichiersExternes by lazy {
        FichiersExternes(contexte.applicationContext.contentResolver)
    }
}
