package com.frigopro.app

import android.app.Application

/** Porte le [ConteneurApp], construit paresseusement au premier accès. */
class FrigoProApplication : Application() {

    val conteneur: ConteneurApp by lazy { ConteneurApp(this) }
}
