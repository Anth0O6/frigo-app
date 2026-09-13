# Le relais d'itinéraire

Ce petit service existe pour que **l'utilisateur de FrigoPro n'ait rien à
installer**. Il tient la clé du service de calcul d'itinéraire, l'application
l'appelle, et personne d'autre n'a de compte à créer.

## Pourquoi un relais plutôt qu'une clé dans l'application

Trois raisons, dans l'ordre où elles se sont imposées :

1. **Demander sa clé à l'utilisateur ne marche pas.** Un technicien frigoriste
   ne créera pas un compte sur une console d'API pour saisir un kilométrage.
   Faire porter le coût de l'infrastructure à celui qui n'a rien demandé, c'est
   livrer une fonctionnalité que personne n'activera.
2. **Une clé embarquée dans l'APK s'extrait.** Et l'API Routes de Google — la
   seule qui chiffre les péages — ne sait même pas restreindre une clé à une
   application Android : seule l'adresse IP d'un serveur peut l'être. Un relais
   est donc littéralement la restriction que Google propose.
3. **Le fournisseur devient interchangeable.** L'application ne connaît que le
   contrat de ce relais : deux adresses entrent, une distance et une durée
   sortent. Changer de service, ou corriger la lecture d'une réponse, se fait en
   redéployant un fichier — sans publier de version ni attendre que quiconque
   mette à jour son téléphone.

## Ce qu'il coûte

Rien, aux volumes d'un artisan. Un technicien seul fait de l'ordre de 100 à 150
calculs par mois ; l'offre gratuite d'OpenRouteService en autorise 2 500 par
jour, et Cloudflare Workers 100 000 requêtes par jour. **Aucune carte bancaire
n'est demandée nulle part** : le pire qui puisse arriver est un quota épuisé, et
l'application dit alors de saisir les kilomètres à la main.

## Déploiement, une fois

```bash
# 1. Une clé OpenRouteService (gratuite, sans carte bancaire)
#    → https://openrouteservice.org/dev/#/signup

# 2. Depuis ce dossier
npx wrangler login
npx wrangler secret put CLE_ORS     # colle la clé ; elle ne touche jamais le dépôt
npx wrangler deploy
```

`wrangler deploy` affiche l'adresse du relais. Reportez-la dans
`Itineraire.RELAIS` (`app/src/main/java/com/frigopro/app/data/ServiceItineraire.kt`),
puis republiez l'application.

## Vérifier qu'il répond

```bash
curl -sS -X POST https://<votre-relais>.workers.dev \
  -H 'Content-Type: application/json' \
  -d '{"depart":"12 rue des Lilas, Lyon","arrivee":"Place Bellecour, Lyon"}'
```

Réponse attendue :

```json
{"version":1,"distanceKm":3.42,"dureeMinutes":11,"peages":0,"peagesConnus":false}
```

Si la forme des réponses d'OpenRouteService a changé depuis, c'est
`geocoder()` ou `itineraire()` dans `worker.js` qu'il faut reprendre — et
seulement lui. L'application continue de fonctionner pendant ce temps : elle
affiche l'échec et laisse saisir à la main.

## Ce qu'il ne fait pas

- **Il ne chiffre pas les péages.** OpenRouteService ne les rend pas. Le relais
  répond donc `peagesConnus: false` plutôt que zéro — « pas de péage » et « je
  n'en sais rien » ne valent pas la même chose sur un devis — et l'application
  le signale pour qu'on les saisisse. C'est le prix de l'absence de carte
  bancaire ; le jour où les péages automatiques comptent plus que cela, c'est ce
  fichier qui change, pas l'application.
- **Il ne journalise pas les adresses.** Elles désignent les clients d'un
  artisan : elles traversent le relais et n'y restent pas.
- **Il ne limite pas le débit lui-même.** L'adresse n'est pas secrète, et la
  protection tient au quota d'OpenRouteService. Pour aller plus loin, une règle
  de limitation par IP se pose dans le tableau de bord Cloudflare (Security →
  WAF → Rate limiting rules), sans toucher au code.
