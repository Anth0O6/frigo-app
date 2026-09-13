/**
 * Le relais d'itinéraire de FrigoPro.
 *
 * Il existe pour une seule raison : **l'utilisateur ne doit rien avoir à
 * installer**. Un technicien frigoriste ne créera pas un compte sur une console
 * d'API pour saisir un kilométrage, et lui demander sa propre clé revenait à
 * faire porter le coût de l'infrastructure par celui qui n'a rien demandé.
 *
 * La clé vit donc ici, en secret de déploiement (`CLE_ORS`), et jamais dans
 * l'application — une clé embarquée dans une APK s'en extrait, et l'API Routes
 * de Google ne sait même pas restreindre une clé à une application Android :
 * seule l'adresse IP d'un serveur peut l'être, ce qui est exactement ce qu'un
 * relais apporte.
 *
 * Le contrat rendu à l'application est **volontairement plus étroit** que celui
 * d'OpenRouteService : deux adresses entrent, une distance et une durée
 * sortent. C'est ce qui permet de changer de fournisseur — ou de corriger une
 * lecture de réponse — en redéployant ce fichier, sans publier une version de
 * l'application ni attendre que quiconque la mette à jour.
 *
 * **Les adresses ne sont jamais journalisées.** Elles désignent des clients
 * d'un artisan ; elles traversent ce relais et n'y restent pas.
 */

/** Ce que l'application sait lire. Voir `AnalyseItineraireRelais` côté Android. */
const VERSION_CONTRAT = 1;

const ORS = "https://api.openrouteservice.org";

/** Au-delà, ce n'est plus une adresse : on refuse avant d'appeler le service. */
const LONGUEUR_ADRESSE_MAX = 300;

export default {
    async fetch(requete, env) {
        if (requete.method === "OPTIONS") {
            return new Response(null, { status: 204, headers: enTetes() });
        }
        if (requete.method !== "POST") {
            return erreur(405, "METHODE");
        }
        if (!env.CLE_ORS) {
            // Le relais est déployé mais pas configuré : c'est une erreur
            // d'exploitation, pas une erreur de l'utilisateur, et elle se dit
            // autrement — voir le README.
            return erreur(500, "RELAIS_NON_CONFIGURE");
        }

        let corps;
        try {
            corps = await requete.json();
        } catch {
            return erreur(400, "REQUETE_ILLISIBLE");
        }

        const depart = texteCourt(corps && corps.depart);
        const arrivee = texteCourt(corps && corps.arrivee);
        if (!depart || !arrivee) return erreur(400, "ADRESSE_INCOMPLETE");

        try {
            const [origine, destination] = await Promise.all([
                geocoder(depart, env.CLE_ORS),
                geocoder(arrivee, env.CLE_ORS),
            ]);
            if (!origine || !destination) return erreur(404, "ADRESSE_INTROUVABLE");

            const trajet = await itineraire(origine, destination, env.CLE_ORS);
            if (!trajet) return erreur(404, "ADRESSE_INTROUVABLE");

            return reponse(200, {
                version: VERSION_CONTRAT,
                distanceKm: arrondi(trajet.metres / 1000),
                dureeMinutes: Math.round(trajet.secondes / 60),
                // OpenRouteService ne chiffre pas les péages. On le **dit**
                // plutôt que de rendre zéro : « pas de péage » et « je n'en sais
                // rien » ne valent pas la même chose sur un devis, et
                // l'application affiche un avertissement là-dessus.
                peages: 0,
                peagesConnus: false,
            });
        } catch (e) {
            if (e && e.statut === 429) return erreur(429, "QUOTA_EPUISE");
            if (e && e.statut === 401) return erreur(502, "RELAIS_NON_CONFIGURE");
            return erreur(502, "SERVICE_INDISPONIBLE");
        }
    },
};

/**
 * Une adresse en coordonnées.
 *
 * La lecture est **tolérante** : ce relais a été écrit sans pouvoir interroger
 * le service, et la forme exacte d'une réponse se corrige ici en trente
 * secondes. Les deux formes connues de Pelias sont acceptées.
 */
async function geocoder(adresse, cle) {
    const url = `${ORS}/geocode/search?text=${encodeURIComponent(adresse)}&size=1`;
    const r = await appeler(url, { headers: { Authorization: cle } });
    const premier = r && r.features && r.features[0];
    const coord = premier && premier.geometry && premier.geometry.coordinates;
    if (!Array.isArray(coord) || coord.length < 2) return null;
    const [lon, lat] = coord;
    if (typeof lon !== "number" || typeof lat !== "number") return null;
    return [lon, lat];
}

/** La route entre deux points, en mètres et en secondes. */
async function itineraire(origine, destination, cle) {
    const r = await appeler(`${ORS}/v2/directions/driving-car`, {
        method: "POST",
        headers: {
            Authorization: cle,
            "Content-Type": "application/json",
            Accept: "application/json",
        },
        body: JSON.stringify({ coordinates: [origine, destination] }),
    });

    // Deux formes selon le suffixe demandé et la version : `routes[]` en JSON,
    // `features[].properties` en GeoJSON. On accepte les deux plutôt que de
    // parier sur l'une.
    const resume =
        (r && r.routes && r.routes[0] && r.routes[0].summary) ||
        (r && r.features && r.features[0] && r.features[0].properties &&
            r.features[0].properties.summary);
    if (!resume) return null;

    const metres = Number(resume.distance);
    const secondes = Number(resume.duration);
    if (!isFinite(metres) || !isFinite(secondes)) return null;
    // Une route de zéro mètre n'est pas une route : c'est deux adresses
    // géocodées au même endroit, et la facturer serait facturer un déplacement
    // qui n'a pas eu lieu.
    if (metres <= 0) return null;
    return { metres, secondes };
}

async function appeler(url, options) {
    const r = await fetch(url, options);
    if (!r.ok) {
        const erreur = new Error(`service ${r.status}`);
        erreur.statut = r.status;
        throw erreur;
    }
    return r.json();
}

function texteCourt(valeur) {
    if (typeof valeur !== "string") return "";
    const propre = valeur.trim();
    return propre.length > LONGUEUR_ADRESSE_MAX ? "" : propre;
}

/** Au décamètre : la précision d'un itinéraire ne va pas au-delà. */
function arrondi(km) {
    return Math.round(km * 100) / 100;
}

function enTetes() {
    return { "Content-Type": "application/json; charset=utf-8" };
}

function reponse(statut, objet) {
    return new Response(JSON.stringify(objet), { status: statut, headers: enTetes() });
}

function erreur(statut, code) {
    return reponse(statut, { version: VERSION_CONTRAT, erreur: code });
}
