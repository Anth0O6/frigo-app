package com.frigopro.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * Ce qu'un service de routage a répondu.
 *
 * Un résultat scellé plutôt qu'une exception, et ce n'est pas une préférence de
 * style : l'écran doit dire au technicien *ce qui* a échoué, parce que la suite
 * n'est pas la même — une absence de réseau s'attend, une adresse introuvable se
 * réécrit, un quota épuisé se contourne en saisissant à la main. Une exception
 * aurait donné un seul message pour cinq situations.
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
 *
 * Aucun de ces messages ne demande à l'utilisateur d'aller configurer quoi que
 * ce soit : c'est tout l'objet du relais. [RELAIS_INDISPONIBLE] est le seul qui
 * parle d'un réglage, et il s'adresse à qui a construit l'application.
 */
enum class RaisonEchec(val message: String) {
    ADRESSE_INCOMPLETE("Il faut une adresse de départ et une adresse d'arrivée."),
    ADRESSE_INTROUVABLE(
        "Aucune route trouvée entre ces deux adresses. Vérifiez-les, ou saisissez à la main.",
    ),
    QUOTA_EPUISE("Le service d'itinéraire est saturé pour le moment. Saisissez à la main."),
    PAS_DE_RESEAU("Pas de réseau. Saisissez les kilomètres à la main : le devis se chiffre quand même."),
    SERVICE_INDISPONIBLE("Le service d'itinéraire n'a pas répondu. Saisissez à la main."),
    RELAIS_INDISPONIBLE("Le calcul automatique n'est pas disponible dans cette version."),
    REPONSE_ILLISIBLE("La réponse du service n'a pas pu être lue. Saisissez à la main."),
}

/**
 * Un service capable de calculer un itinéraire.
 *
 * L'interface existe pour que tout ce qui l'utilise — le ViewModel, l'écran —
 * s'éprouve sans réseau, exactement comme [RangementPhotos] permet d'éprouver le
 * rangement des photos sans Android. Elle rend un **aller** : le doublement est
 * une décision de facturation, prise dans [CalculDeplacement].
 */
fun interface ServiceItineraire {

    suspend fun itineraire(depart: String, arrivee: String): ResultatItineraire
}

/**
 * Le relais d'itinéraire, et la lecture de ce qu'il répond.
 *
 * **L'application ne parle à aucun service de cartographie**, seulement à ce
 * relais (voir `relais/` à la racine du dépôt). C'est ce qui permet à
 * l'utilisateur de n'avoir strictement rien à installer : la clé du service vit
 * dans le relais, pas ici. Lui demander la sienne était la première version de
 * cette fonctionnalité, et c'était une erreur — un technicien ne crée pas un
 * compte sur une console d'API pour saisir un kilométrage, si bien que le calcul
 * automatique n'aurait jamais été activé par personne.
 *
 * Deux conséquences qui valent d'être sues :
 *
 * - **Le contrat est étroit et stable** : deux adresses entrent, une distance et
 *   une durée sortent. Changer de fournisseur de cartographie, ou corriger la
 *   lecture de sa réponse, se fait en redéployant le relais — sans publier de
 *   version ni attendre que quiconque mette à jour son téléphone.
 * - **Les adresses des clients traversent le relais.** Il ne les journalise pas,
 *   et c'est une propriété à préserver si on le réécrit.
 */
object Itineraire {

    /**
     * L'adresse du relais, posée à la construction de l'application.
     *
     * Vide tant qu'aucun relais n'est déployé, et l'application le supporte : le
     * calcul automatique est alors simplement absent de l'écran, et la saisie à
     * la main — qui reste le chemin de secours en toute circonstance — est le
     * seul chemin. Voir `relais/README.md` pour la poser.
     *
     * Ce n'est pas un secret : l'adresse d'un relais se lit dans le trafic de
     * n'importe quelle application. Ce qui est secret, la clé du service, n'est
     * pas ici et n'y sera jamais.
     */
    const val RELAIS: String = ""

    /** Un relais est déployé : l'écran peut proposer le calcul. */
    val configure: Boolean get() = RELAIS.isNotBlank()
}

/**
 * La lecture d'une réponse du relais.
 *
 * Séparée du transport, et c'est tout l'intérêt : le protocole HTTP ne s'éprouve
 * pas sur la JVM sans monter un serveur, l'analyse d'une réponse s'éprouve avec
 * une chaîne de caractères.
 *
 * La forme est bien plus simple que celle d'un service de cartographie, et c'est
 * voulu : les pièges — entiers rendus en texte, durées suffixées, prix en deux
 * morceaux — sont absorbés par le relais, qui est éprouvé de son côté
 * (`relais/worker.test.mjs`). Ce qui reste ici tient en quatre nombres.
 */
object AnalyseItineraireRelais {

    private val json = Json { ignoreUnknownKeys = true }

    /** Le corps envoyé au relais. */
    fun corpsRequete(depart: String, arrivee: String): String = buildJsonObject {
        put("depart", depart.trim())
        put("arrivee", arrivee.trim())
    }.toString()

    /**
     * Traduit une réponse en résultat.
     *
     * Le code HTTP suffit pour les échecs que le relais nomme lui-même ; le
     * champ `erreur` est plus précis et l'emporte quand il est là.
     */
    fun lire(codeHttp: Int, corps: String): ResultatItineraire {
        val racine = runCatching { json.parseToJsonElement(corps).jsonObject }.getOrNull()

        racine?.texte("erreur")?.let { code ->
            return ResultatItineraire.Echec(
                when (code) {
                    "ADRESSE_INCOMPLETE" -> RaisonEchec.ADRESSE_INCOMPLETE
                    "ADRESSE_INTROUVABLE" -> RaisonEchec.ADRESSE_INTROUVABLE
                    "QUOTA_EPUISE" -> RaisonEchec.QUOTA_EPUISE
                    // Un relais mal configuré est une panne d'exploitation, pas
                    // une erreur de l'utilisateur : il n'a rien à corriger.
                    "RELAIS_NON_CONFIGURE" -> RaisonEchec.RELAIS_INDISPONIBLE
                    else -> RaisonEchec.SERVICE_INDISPONIBLE
                },
            )
        }

        if (codeHttp !in 200..299) {
            return ResultatItineraire.Echec(
                when (codeHttp) {
                    429 -> RaisonEchec.QUOTA_EPUISE
                    404 -> RaisonEchec.ADRESSE_INTROUVABLE
                    else -> RaisonEchec.SERVICE_INDISPONIBLE
                },
            )
        }
        if (racine == null) return ResultatItineraire.Echec(RaisonEchec.REPONSE_ILLISIBLE)

        val km = racine.nombre("distanceKm")
        val minutes = racine.nombre("dureeMinutes")
        if (km == null || minutes == null) {
            return ResultatItineraire.Echec(RaisonEchec.REPONSE_ILLISIBLE)
        }
        // Un trajet nul n'est pas un trajet : le relais le refuse déjà, et le
        // redire ici évite qu'une version plus ancienne du relais fasse facturer
        // un déplacement qui n'a pas eu lieu.
        if (km <= 0.0) return ResultatItineraire.Echec(RaisonEchec.ADRESSE_INTROUVABLE)

        val peages = racine.nombre("peages") ?: 0.0
        return ResultatItineraire.Trouve(
            distanceKm = km.auCentime(),
            dureeMinutes = minutes.roundToInt(),
            peages = peages.auCentime(),
            peagesConnus = racine.booleen("peagesConnus") == true,
        )
    }

    private fun JsonObject.nombre(cle: String): Double? =
        runCatching { this[cle]?.jsonPrimitive?.content?.toDoubleOrNull() }.getOrNull()

    private fun JsonObject.texte(cle: String): String? =
        runCatching { this[cle]?.jsonPrimitive?.content }.getOrNull()

    private fun JsonObject.booleen(cle: String): Boolean? =
        runCatching { this[cle]?.jsonPrimitive?.content?.toBooleanStrictOrNull() }.getOrNull()
}
