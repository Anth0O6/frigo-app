package com.frigopro.app

import android.app.Application
import com.frigopro.app.data.RappelsFactures

/** Porte le [ConteneurApp], construit paresseusement au premier accès. */
class FrigoProApplication : Application() {

    val conteneur: ConteneurApp by lazy { ConteneurApp(this) }

    override fun onCreate() {
        super.onCreate()
        // Le canal et le travail quotidien, posés ici parce que c'est le seul
        // endroit qui s'exécute même quand l'application est ouverte par une
        // notification. Ni l'un ni l'autre ne touche la base : le conteneur
        // reste paresseux, et le démarrage n'est pas ralenti.
        RappelsFactures.poserLeCanal(this)
        RappelsFactures.planifier(this)
    }
}
