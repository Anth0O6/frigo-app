package com.frigopro.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

/**
 * Ce qu'un service de routage a répondu.
 *
 * Un résultat scellé plutôt qu'une exception, et ce n'est pas une préférence de
 * style : l'écran doit dire au technicien *ce qui* a échoué, parce que la suite
 * n'est pas la même — une clé refusée se corrige dans les Réglages, une absence
 * de réseau s'attend, une adresse introuvable se réécrit. Une exception aurait
 * donné un seul message pour cinq situations.
 */
sealed interface ResultatItineraire {

    /**
     * Un itinéraire, **pour un sens**.
     *
     * @param peagesConnus le service s'est prononcé sur les péages. Distinct
     *   d'un péage nul : voir [Trajet.peagesConnus].
     */
    data class Trouve(
        val distanceKm: Double,
        val dureeMinutes: Int,
        val peages: Double = 0.0,
        val peagesConnus: Boolean = false,
    ) : ResultatItineraire

    data class Echec(val raison: RaisonEchec) : ResultatItineraire
}

/**
 * Pourquoi le calcul n'a rien rendu.
 *
 * Le message est porté par l'énumération et non construit par l'écran : il est
 * lu par quelqu'un sur un toit qui doit décider s'il ressaisit une adresse ou
 * s'il tape ses kilomètres à la main, et il vaut mieux une phrase juste qu'un
 * code d'erreur.
 */
enum class RaisonEchec(val message: String) {
    PAS_DE_CLE("Aucune clé d'itinéraire dans les Réglages : saisissez les kilomètres à la main."),
    ADRESSE_INCOMPLETE("Il faut une adresse de départ et une adresse d'arrivée."),
    ADRESSE_INTROUVABLE("Aucune route trouvée entre ces deux adresses. Vérifiez-les, ou saisissez à la main."),
    CLE_REFUSEE("Le service a refusé la clé. Vérifiez-la dans les Réglages, et que l'API Routes y est activée."),
    QUOTA_EPUISE("Le quota du service est épuisé. Réessayez plus tard, ou saisissez à la main."),
    PAS_DE_RESEAU("Pas de réseau. Saisissez les kilomètres à la main : le devis se chiffre quand même."),
    SERVICE_INDISPONIBLE("Le service d'itinéraire n'a pas répondu."),
    REPONSE_ILLISIBLE("La réponse du service n'a pas pu être lue."),
}

/**
 * Un service capable de calculer un itinéraire.
 *
 * L'interface existe pour que tout ce qui l'utilise — le ViewModel, l'écran —
 * s'éprouve sans réseau ni clé, exactement comme [RangementPhotos] permet
 * d'éprouver le rangement des photos sans Android. Elle rend un **aller** : le
 * doublement est une décision de facturation, prise dans [CalculDeplacement].
 */
fun interface ServiceItineraire {

    suspend fun itineraire(depart: String, arrivee: String): ResultatItineraire
}

/**
 * La lecture d'une réponse de l'API Routes de Google.
 *
 * Séparée du transport, et c'est tout l'intérêt : le protocole HTTP ne
 * s'éprouve pas sur la JVM sans monter un serveur, mais l'analyse d'une réponse
 * s'éprouve avec une chaîne de caractères. C'est là que sont les pièges, et ils
 * sont réels : les entiers de plus de 32 bits arrivent **en texte** dans le JSON
 * de protobuf (`"units": "8"`), la durée est une chaîne suffixée (`"1320s"`), et
 * le prix d'un péage se lit en deux morceaux — des unités et des milliardièmes.
 */
object AnalyseItineraireGoogle {

    private val json = Json { ignoreUnknownKeys = true }

    /** L'hôte et le chemin de l'appel, nommés ici pour être vérifiables. */
    const val URL = "https://routes.googleapis.com/directions/v2:computeRoutes"

    /**
     * Les champs demandés, sans lesquels la réponse revient vide.
     *
     * L'API Routes **exige** un masque de champs — c'est elle qui refuse
     * l'appel sinon — et il sert aussi à ne pas payer pour ce qu'on ne lit pas.
     */
    const val CHAMPS = "routes.distanceMeters,routes.duration,routes.travelAdvisory.tollInfo"

    /**
     * Le corps de la requête.
     *
     * `TRAFFIC_UNAWARE` est un choix : la durée est alors celle d'une route
     * libre, sans le trafic du moment. C'est **voulu** pour un devis, qui doit
     * annoncer le même prix si on le rouvre à 18 h un vendredi, et cela évite au
     * passage la tranche tarifaire la plus chère du service.
     *
     * `emissionType` n'est pas décoratif : sans information sur le véhicule, le
     * service ne se prononce pas sur les péages de certains pays.
     */
    fun corpsRequete(depart: String, arrivee: String): String = buildJsonObject {
        putJsonObject("origin") { put("address", depart.trim()) }
        putJsonObject("destination") { put("address", arrivee.trim()) }
        put("travelMode", "DRIVE")
        put("routingPreference", "TRAFFIC_UNAWARE")
        putJsonArray("extraComputations") { add("TOLLS") }
        putJsonObject("routeModifiers") {
            putJsonObject("vehicleInfo") { put("emissionType", "GASOLINE") }
        }
        put("languageCode", "fr-FR")
        put("units", "METRIC")
    }.toString()

    /**
     * Traduit une réponse en résultat.
     *
     * Un code 200 avec `routes` vide **n'est pas un succès** : c'est ainsi que
     * l'API dit qu'elle n'a pas trouvé de route, et le prendre pour un trajet de
     * zéro kilomètre aurait facturé un déplacement nul sans rien signaler.
     */
    fun lire(codeHttp: Int, corps: String): ResultatItineraire {
        when (codeHttp) {
            401, 403 -> return ResultatItineraire.Echec(RaisonEchec.CLE_REFUSEE)
            429 -> return ResultatItineraire.Echec(RaisonEchec.QUOTA_EPUISE)
            in 500..599 -> return ResultatItineraire.Echec(RaisonEchec.SERVICE_INDISPONIBLE)
        }

        val racine = runCatching { json.parseToJsonElement(corps).jsonObject }.getOrNull()
            ?: return ResultatItineraire.Echec(RaisonEchec.REPONSE_ILLISIBLE)

        // Une erreur structurée peut accompagner un code que l'on n'a pas su
        // classer : `status` est plus précis que le code HTTP.
        racine["error"]?.let { erreur ->
            val statut = runCatching {
                erreur.jsonObject["status"]?.jsonPrimitive?.content
            }.getOrNull()
            return ResultatItineraire.Echec(
                when (statut) {
                    "PERMISSION_DENIED", "UNAUTHENTICATED" -> RaisonEchec.CLE_REFUSEE
                    "RESOURCE_EXHAUSTED" -> RaisonEchec.QUOTA_EPUISE
                    "NOT_FOUND" -> RaisonEchec.ADRESSE_INTROUVABLE
                    else -> RaisonEchec.SERVICE_INDISPONIBLE
                },
            )
        }
        if (codeHttp !in 200..299) return ResultatItineraire.Echec(RaisonEchec.SERVICE_INDISPONIBLE)

        val route = runCatching { racine["routes"]?.jsonArray?.firstOrNull()?.jsonObject }
            .getOrNull() ?: return ResultatItineraire.Echec(RaisonEchec.ADRESSE_INTROUVABLE)

        val metres = route.nombre("distanceMeters")
            ?: return ResultatItineraire.Echec(RaisonEchec.REPONSE_ILLISIBLE)
        val secondes = route.texte("duration")?.let(::secondesDe)
            ?: return ResultatItineraire.Echec(RaisonEchec.REPONSE_ILLISIBLE)

        val peage = peageDe(route)
        return ResultatItineraire.Trouve(
            distanceKm = (metres / 1000.0).auCentime(),
            dureeMinutes = (secondes / 60.0).roundToInt(),
            peages = peage ?: 0.0,
            peagesConnus = peage != null,
        )
    }

    /**
     * Le péage d'un sens, ou `null` si le service ne s'est pas prononcé.
     *
     * **Une devise autre que l'euro rend le péage inconnu** plutôt que repris
     * tel quel. Le trajet ne porte qu'un nombre, et l'ajouter à un devis en
     * euros y aurait glissé des francs suisses au taux de un pour un — une
     * erreur silencieuse et dans le mauvais sens, puisqu'elle sous-facture.
     */
    private fun peageDe(route: JsonObject): Double? {
        val prix = runCatching {
            route["travelAdvisory"]?.jsonObject
                ?.get("tollInfo")?.jsonObject
                ?.get("estimatedPrice")?.jsonArray
        }.getOrNull() ?: return null
        // `tollInfo` présent mais sans prix : le service a regardé et n'a rien
        // trouvé à payer. C'est un zéro connu, et non une absence de réponse.
        if (prix.isEmpty()) return 0.0

        var total = 0.0
        for (element in prix) {
            val montant = runCatching { element.jsonObject }.getOrNull() ?: return null
            if (montant.texte("currencyCode")?.uppercase() != "EUR") return null
            val unites = montant.nombre("units") ?: 0.0
            val nanos = montant.nombre("nanos") ?: 0.0
            total += unites + nanos / 1_000_000_000.0
        }
        return total.auCentime()
    }

    /** `"1320s"` fait 1320 secondes ; une durée sans son `s` reste lisible. */
    private fun secondesDe(duree: String): Double? =
        duree.trim().removeSuffix("s").toDoubleOrNull()

    /**
     * Un nombre, qu'il arrive en nombre ou **en texte**.
     *
     * Le JSON de protobuf écrit les entiers de 64 bits entre guillemets, si bien
     * que `units` arrive en texte là où `nanos` arrive en nombre. Les lire de la
     * même façon évite d'avoir à se souvenir lequel est lequel.
     */
    private fun JsonObject.nombre(cle: String): Double? =
        runCatching { this[cle]?.jsonPrimitive?.content?.toDoubleOrNull() }.getOrNull()

    private fun JsonObject.texte(cle: String): String? =
        runCatching { this[cle]?.jsonPrimitive?.content }.getOrNull()
}
