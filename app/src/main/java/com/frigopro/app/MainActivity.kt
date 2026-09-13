package com.frigopro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.frigopro.app.data.Parametres
import com.frigopro.app.ui.EcranDemarrage
import com.frigopro.app.ui.FrigoProApp
import com.frigopro.app.ui.theme.FrigoProTheme

/** Unique activité de l'application : héberge l'arbre Compose. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val parametres = (application as FrigoProApplication).conteneur.parametres.parametres

        setContent {
            // `null` tant que la base n'a rien rendu, et c'est toute la
            // différence avec une valeur par défaut : le thème dépend de ces
            // réglages, et les supposer revenait à ouvrir l'application en
            // sombre puis à la repeindre en clair sous les yeux du technicien
            // qui avait choisi l'inverse. L'écran de démarrage couvre cet
            // intervalle.
            val reglages by parametres.collectAsState(initial = null)

            // Le thème définitif n'est appliqué qu'une fois l'écran de démarrage
            // parti — au début de son fondu, qui le découvre alors au lieu de le
            // précéder.
            var demarre by rememberSaveable { mutableStateOf(false) }
            val effectifs = if (demarre) (reglages ?: Parametres()) else Parametres()

            FrigoProTheme(sombre = effectifs.themeSombre, modeGants = effectifs.modeGants) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        FrigoProApp()
                    }
                    EcranDemarrage(
                        pret = reglages != null,
                        version = BuildConfig.VERSION_NAME,
                        onEfface = { demarre = true },
                    )
                }
            }
        }
    }
}
