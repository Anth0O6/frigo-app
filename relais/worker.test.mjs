/**
 * Les cas du relais, éprouvés contre un faux OpenRouteService.
 *
 * Le relais est le seul endroit du projet qui ne compile pas avec le reste, et
 * c'est aussi celui qu'on redéploie le plus vite — deux raisons de l'éprouver
 * plutôt que de le relire. Le faux service rend les formes documentées, et le
 * test vérifie surtout ce que le relais fait des formes **inattendues** : c'est
 * là qu'il se taira ou dira n'importe quoi.
 *
 *     node --test relais/
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import relais from "./worker.js";

/** Installe un faux `fetch` et rend la liste des URL appelées. */
function fausServices(reponses) {
    const appels = [];
    globalThis.fetch = async (url, options) => {
        appels.push(String(url));
        for (const [motif, repondre] of Object.entries(reponses)) {
            if (String(url).includes(motif)) {
                const r = repondre(options);
                return {
                    ok: r.statut === undefined || (r.statut >= 200 && r.statut < 300),
                    status: r.statut ?? 200,
                    json: async () => r.corps,
                };
            }
        }
        throw new Error(`URL non prévue par le test : ${url}`);
    };
    return appels;
}

const GEOCODAGE = () => ({
    corps: { features: [{ geometry: { coordinates: [4.8357, 45.764] } }] },
});

function demander(corps, env = { CLE_ORS: "cle-de-test" }) {
    const requete = new Request("https://relais.test", {
        method: "POST",
        body: JSON.stringify(corps),
    });
    return relais.fetch(requete, env);
}

test("un trajet trouvé rend des kilomètres et des minutes", async () => {
    fausServices({
        "/geocode/search": GEOCODAGE,
        "/v2/directions": () => ({
            corps: { routes: [{ summary: { distance: 24870, duration: 1320 } }] },
        }),
    });

    const r = await demander({ depart: "Lyon", arrivee: "Villeurbanne" });
    assert.equal(r.status, 200);
    const corps = await r.json();
    assert.equal(corps.distanceKm, 24.87);
    assert.equal(corps.dureeMinutes, 22);
    assert.equal(
        corps.peagesConnus,
        false,
        "OpenRouteService ne chiffre pas les péages : il faut le dire, pas rendre zéro",
    );
});

test("la forme GeoJSON est acceptée comme la forme JSON", async () => {
    fausServices({
        "/geocode/search": GEOCODAGE,
        "/v2/directions": () => ({
            corps: {
                features: [{ properties: { summary: { distance: 8200, duration: 600 } } }],
            },
        }),
    });

    const corps = await (await demander({ depart: "a", arrivee: "b" })).json();
    assert.equal(corps.distanceKm, 8.2);
    assert.equal(corps.dureeMinutes, 10);
});

test("une adresse introuvable ne devient pas un trajet de zéro kilomètre", async () => {
    fausServices({ "/geocode/search": () => ({ corps: { features: [] } }) });

    const r = await demander({ depart: "nulle part", arrivee: "Lyon" });
    assert.equal(r.status, 404);
    assert.equal((await r.json()).erreur, "ADRESSE_INTROUVABLE");
});

test("une route de zéro mètre est refusée plutôt que facturée", async () => {
    fausServices({
        "/geocode/search": GEOCODAGE,
        "/v2/directions": () => ({
            corps: { routes: [{ summary: { distance: 0, duration: 0 } }] },
        }),
    });

    const r = await demander({ depart: "a", arrivee: "a" });
    assert.equal(r.status, 404);
});

test("une réponse d'une forme inconnue échoue franchement", async () => {
    fausServices({
        "/geocode/search": GEOCODAGE,
        "/v2/directions": () => ({ corps: { quelque_chose_dautre: true } }),
    });

    const r = await demander({ depart: "a", arrivee: "b" });
    assert.equal(r.status, 404);
});

test("le quota épuisé se distingue d'une panne", async () => {
    fausServices({
        "/geocode/search": GEOCODAGE,
        "/v2/directions": () => ({ statut: 429, corps: {} }),
    });

    const r = await demander({ depart: "a", arrivee: "b" });
    assert.equal(r.status, 429);
    assert.equal((await r.json()).erreur, "QUOTA_EPUISE");
});

test("une clé refusée est une erreur du relais, pas de l'utilisateur", async () => {
    fausServices({
        "/geocode/search": GEOCODAGE,
        "/v2/directions": () => ({ statut: 401, corps: {} }),
    });

    const corps = await (await demander({ depart: "a", arrivee: "b" })).json();
    assert.equal(corps.erreur, "RELAIS_NON_CONFIGURE");
});

test("un relais sans clé le dit sans appeler personne", async () => {
    const appels = fausServices({});

    const r = await demander({ depart: "a", arrivee: "b" }, {});
    assert.equal((await r.json()).erreur, "RELAIS_NON_CONFIGURE");
    assert.equal(appels.length, 0, "rien ne doit sortir sans clé");
});

test("une adresse manquante est refusée avant tout appel", async () => {
    const appels = fausServices({});

    const r = await demander({ depart: "Lyon", arrivee: "   " });
    assert.equal(r.status, 400);
    assert.equal((await r.json()).erreur, "ADRESSE_INCOMPLETE");
    assert.equal(appels.length, 0);
});

test("une adresse démesurée est refusée avant tout appel", async () => {
    const appels = fausServices({});

    const r = await demander({ depart: "x".repeat(5000), arrivee: "Lyon" });
    assert.equal(r.status, 400);
    assert.equal(appels.length, 0, "on ne relaie pas n'importe quoi vers le service");
});

test("les adresses sont échappées dans l'URL de géocodage", async () => {
    const appels = fausServices({
        "/geocode/search": GEOCODAGE,
        "/v2/directions": () => ({
            corps: { routes: [{ summary: { distance: 1000, duration: 60 } }] },
        }),
    });

    await demander({ depart: "3 rue \"Neuve\", L'Épicerie & Cie", arrivee: "Lyon" });
    const geocodage = appels.find((u) => u.includes("/geocode/search"));
    assert.ok(!geocodage.includes('"'), "un guillemet nu casserait l'URL");
    assert.ok(geocodage.includes("%26"), "l'esperluette doit être échappée");
});

test("un GET ne passe pas", async () => {
    fausServices({});
    const r = await relais.fetch(
        new Request("https://relais.test", { method: "GET" }),
        { CLE_ORS: "cle" },
    );
    assert.equal(r.status, 405);
});

test("un corps illisible ne fait pas tomber le relais", async () => {
    fausServices({});
    const r = await relais.fetch(
        new Request("https://relais.test", { method: "POST", body: "pas du json" }),
        { CLE_ORS: "cle" },
    );
    assert.equal(r.status, 400);
    assert.equal((await r.json()).erreur, "REQUETE_ILLISIBLE");
});
