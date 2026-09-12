package com.frigopro.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/**
 * L'API Routes de Google, la seule classe du projet à ouvrir une connexion.
 *
 * Elle est **la seule** raison de la permission `INTERNET`, et c'est pourquoi
 * elle est isolée ici : le reste de l'application continue de fonctionner
 * entièrement hors ligne, et ce fichier est l'endroit où vérifier ce qui sort du
 * téléphone — deux adresses et une clé, vers un seul hôte, et rien d'autre.
 *
 * Pas de bibliothèque HTTP : un appel, un POST, un JSON. `HttpURLConnection`
 * suffit, là où Retrofit ou OkHttp auraient ajouté une dépendance et son
 * transitif pour une requête. Le jour où il y en aura cinq, la question se
 * reposera.
 *
 * La clé est lue **à chaque appel** plutôt que retenue à la construction : elle
 * se saisit dans les Réglages, et un service qui garderait l'ancienne obligerait
 * à redémarrer l'application après l'avoir corrigée.
 *
 * @param delaiMs délai d'attente, à la connexion comme à la lecture. Court à
 *   dessein : au bord d'une route avec une barre de réseau, une réponse qui
 *   tarde vaut moins qu'un échec franc qui rend la main pour saisir à la main.
 */
class ItineraireGoogle(
    private val cle: suspend () -> String,
    private val ordonnanceur: CoroutineDispatcher = Dispatchers.IO,
    private val delaiMs: Int = 15_000,
) : ServiceItineraire {

    override suspend fun itineraire(depart: String, arrivee: String): ResultatItineraire {
        if (depart.isBlank() || arrivee.isBlank()) {
            return ResultatItineraire.Echec(RaisonEchec.ADRESSE_INCOMPLETE)
        }
        val cleApi = cle().trim()
        if (cleApi.isEmpty()) return ResultatItineraire.Echec(RaisonEchec.PAS_DE_CLE)

        return withContext(ordonnanceur) {
            var connexion: HttpURLConnection? = null
            try {
                connexion = (URL(AnalyseItineraireGoogle.URL).openConnection() as HttpURLConnection)
                    .apply {
                        requestMethod = "POST"
                        connectTimeout = delaiMs
                        readTimeout = delaiMs
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("X-Goog-Api-Key", cleApi)
                        setRequestProperty("X-Goog-FieldMask", AnalyseItineraireGoogle.CHAMPS)
                    }
                connexion.outputStream.use { flux ->
                    flux.write(
                        AnalyseItineraireGoogle.corpsRequete(depart, arrivee)
                            .toByteArray(Charsets.UTF_8),
                    )
                }

                val code = connexion.responseCode
                // Un code d'erreur met le corps dans `errorStream` et laisse
                // `inputStream` jeter : c'est là que se trouve le `status` de
                // l'API, celui qui distingue une clé refusée d'un quota épuisé.
                val corps = (if (code in 200..299) connexion.inputStream else connexion.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                AnalyseItineraireGoogle.lire(code, corps)
            } catch (_: UnknownHostException) {
                // Pas de DNS : c'est la forme que prend l'absence de réseau.
                ResultatItineraire.Echec(RaisonEchec.PAS_DE_RESEAU)
            } catch (_: IOException) {
                ResultatItineraire.Echec(RaisonEchec.PAS_DE_RESEAU)
            } finally {
                connexion?.disconnect()
            }
        }
    }
}
