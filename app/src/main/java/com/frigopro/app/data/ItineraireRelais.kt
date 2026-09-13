package com.frigopro.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/**
 * Le relais d'itinéraire, et la seule classe du projet à ouvrir une connexion.
 *
 * Elle est **la seule** raison de la permission `INTERNET`, et c'est pourquoi
 * elle est isolée ici : le reste de l'application fonctionne entièrement hors
 * ligne, et ce fichier est l'endroit où vérifier ce qui sort du téléphone — deux
 * adresses, vers un seul hôte, et rien d'autre. Aucune clé ne part d'ici : elle
 * vit dans le relais (voir `relais/`), et c'est ce qui permet à l'utilisateur de
 * n'avoir rien à installer.
 *
 * Pas de bibliothèque HTTP : un appel, un POST, un JSON. `HttpURLConnection`
 * suffit là où Retrofit ou OkHttp auraient ajouté une dépendance et son
 * transitif pour une requête.
 *
 * @param delaiMs délai d'attente, à la connexion comme à la lecture. Court à
 *   dessein : au bord d'une route avec une barre de réseau, une réponse qui
 *   tarde vaut moins qu'un échec franc qui rend la main pour saisir à la main.
 */
class ItineraireRelais(
    private val adresseRelais: String = Itineraire.RELAIS,
    private val ordonnanceur: CoroutineDispatcher = Dispatchers.IO,
    private val delaiMs: Int = 15_000,
) : ServiceItineraire {

    override suspend fun itineraire(depart: String, arrivee: String): ResultatItineraire {
        if (depart.isBlank() || arrivee.isBlank()) {
            return ResultatItineraire.Echec(RaisonEchec.ADRESSE_INCOMPLETE)
        }
        if (adresseRelais.isBlank()) {
            return ResultatItineraire.Echec(RaisonEchec.RELAIS_INDISPONIBLE)
        }

        return withContext(ordonnanceur) {
            var connexion: HttpURLConnection? = null
            try {
                connexion = (URL(adresseRelais).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = delaiMs
                    readTimeout = delaiMs
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connexion.outputStream.use { flux ->
                    flux.write(
                        AnalyseItineraireRelais.corpsRequete(depart, arrivee)
                            .toByteArray(Charsets.UTF_8),
                    )
                }

                val code = connexion.responseCode
                // Un code d'erreur met le corps dans `errorStream` et laisse
                // `inputStream` jeter : c'est là que se trouve le champ `erreur`
                // du relais, celui qui distingue un quota épuisé d'une panne.
                val corps = (if (code in 200..299) connexion.inputStream else connexion.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                AnalyseItineraireRelais.lire(code, corps)
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
