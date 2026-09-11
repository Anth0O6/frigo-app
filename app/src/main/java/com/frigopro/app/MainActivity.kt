package com.frigopro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.frigopro.app.data.Parametres
import com.frigopro.app.ui.FrigoProApp
import com.frigopro.app.ui.theme.FrigoProTheme

/** Unique activité de l'application : héberge l'arbre Compose. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val parametres = (application as FrigoProApplication).conteneur.parametres.parametres

        setContent {
            // Sombre par défaut, et non « selon le système » : l'application
            // se lit en chambre froide et sur un toit, où un fond blanc
            // éblouit. Les Réglages laissent en revenir, et le mode gants
            // agrandit les cibles — deux réglages qui changent l'apparence, et
            // qui doivent donc être lus ici, au-dessus de tout l'arbre.
            val reglages by parametres.collectAsState(initial = Parametres())
            FrigoProTheme(sombre = reglages.themeSombre, modeGants = reglages.modeGants) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FrigoProApp()
                }
            }
        }
    }
}
