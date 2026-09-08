# FrigoPro

Application Android native destinée aux techniciens frigoristes en tournée.
Elle affiche les interventions d'une journée, se déplace d'un jour à l'autre,
et permet de les créer, les modifier et les supprimer. Les données sont
persistées localement.

## Stack

| Élément | Choix |
| --- | --- |
| Langage | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Persistance | Room (SQLite local), KSP pour la génération |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Build | Gradle (wrapper committé), AGP, catalogue de versions `gradle/libs.versions.toml` |
| JDK | 17 (source/target et `jvmTarget`) |
| Package | `com.frigopro.app` |

`minSdk 26` donne accès à `java.time` nativement : aucun *desugaring* n'est
nécessaire pour `LocalDate` et `LocalTime`.

## Structure

```
.
├── app/
│   ├── build.gradle.kts            # configuration du module applicatif
│   ├── schemas/                    # schémas Room exportés (migrations)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/frigopro/app/
│       │   ├── FrigoProApplication.kt  # porte le conteneur de dépendances
│       │   ├── ConteneurApp.kt         # assemblage base ← dépôt
│       │   ├── MainActivity.kt         # unique activité, héberge l'arbre Compose
│       │   ├── data/               # modèle, base et source de données
│       │   │   ├── Intervention.kt
│       │   │   ├── Convertisseurs.kt
│       │   │   ├── InterventionDao.kt
│       │   │   ├── FrigoProDatabase.kt
│       │   │   └── InterventionRepository.kt
│       │   └── ui/                 # écrans, ViewModels et thème
│       │       ├── Dates.kt            # formats et conversions de dates
│       │       ├── EtatFormulaire.kt
│       │       ├── FormulaireIntervention.kt
│       │       ├── SelecteurDate.kt
│       │       ├── InterventionsScreen.kt
│       │       ├── InterventionsViewModel.kt
│       │       └── theme/
│       └── res/                    # chaînes, couleurs, thème XML, icône
├── gradle/libs.versions.toml       # versions centralisées
├── gradle/wrapper/                 # wrapper committé (jar inclus)
└── .github/workflows/build.yml     # CI : assembleDebug + Release
```

## Architecture

Découpage en trois couches, sens de dépendance `ui → data` uniquement :

- **`data`** — `Intervention` est à la fois le modèle du domaine et l'entité
  Room ; les deux se confondent tant que le stockage épouse le domaine, et se
  sépareront le jour où ils divergeront. `Convertisseurs` traduit les types
  `java.time` en colonnes : dates et heures sont stockées en texte de largeur
  fixe, ce qui les rend **triables et comparables directement en SQL** — c'est
  ce sur quoi reposent le `WHERE date = :date` et le `ORDER BY heure` du DAO.
  `InterventionRepository` expose un `Flow` par journée et deux écritures
  (`enregistrer`, `supprimer`) ; Room réémet le `Flow` à chaque écriture, donc
  l'UI se remet à jour sans que personne n'ait à la prévenir.
- **`ui`** — `InterventionsViewModel` détient la journée consultée
  (`StateFlow<LocalDate>`) et en dérive la liste par `flatMapLatest` : changer
  la date suffit à recharger l'écran. Il détient aussi le formulaire ouvert
  (`StateFlow<EtatFormulaire?>`, `null` quand l'écran n'affiche que la liste).
  `EtatFormulaire` porte la saisie en cours ; son `id` vaut `null` en création
  et identifie la ligne éditée sinon, ce qui distingue « Ajouter » d'«
  Enregistrer ». Les écrans suivent le motif *state hoisting* :
  `InterventionsRoute` (avec état) enveloppe `InterventionsScreen` et
  `FormulaireIntervention` (sans état, testables et prévisualisables).
- **`ui.theme`** — thème Material 3 avec couleurs dynamiques (Material You) sur
  Android 12+, repli sur la palette « froid » définie dans `Color.kt`.

Les dépendances sont assemblées à la main dans `ConteneurApp`, porté par
`FrigoProApplication` et atteint par `InterventionsViewModel.Factory`. Une
poignée d'objets ne justifie pas encore Hilt.

La saisie se fait dans une `ModalBottomSheet` plutôt que sur une destination
dédiée : un seul écran, pas de navigation. Ajouter une vraie destination
impliquera d'introduire un graphe de navigation et de déplacer
`InterventionsRoute` derrière celui-ci.

### Deux choix faits pour une synchronisation future

L'application est locale et le restera tant qu'un seul technicien l'utilise.
Deux décisions anticipent néanmoins l'arrivée d'un serveur, parce qu'elles
seraient coûteuses à rattraper après coup :

- **Les identifiants sont des UUID**, pas un compteur auto-incrémenté. Deux
  appareils travaillant hors réseau doivent pouvoir créer des interventions
  sans se donner le même identifiant.
- **Chaque ligne porte `modifieLe`**, posé par le dépôt à chaque écriture.
  Inutilisé aujourd'hui, c'est ce qui permettra de départager deux versions
  concurrentes d'une même intervention.

Rien d'autre n'anticipe le réseau : ni comptes, ni notion d'entreprise, ni
état de synchronisation. Ces éléments arriveront avec le besoin réel.

### Migrations

`exportSchema` est actif et `room.schemaLocation` pointe sur `app/schemas`.
Toute évolution de `Intervention` doit s'accompagner d'une migration et du
schéma régénéré, **committé** : sans lui, aucune migration ne peut être écrite
ni testée, et une mise à jour effacerait les tournées déjà saisies. Le fichier
est produit à la compilation ; il faut donc penser à l'ajouter au dépôt après
un `assembleDebug` local.

## Conventions

- **Nommage** : code du domaine en français (`Intervention`, `TypePanne`,
  `enregistrer`, `EtatFormulaire`) pour coller au vocabulaire métier ; les API
  Android et Compose gardent évidemment leurs noms d'origine.
- **Composables** : `PascalCase`, un `Modifier` en premier paramètre optionnel,
  paramètres d'état avant les lambdas de rappel, `@Preview` privé en fin de
  fichier.
- **État** : exposé en `StateFlow` et collecté avec
  `collectAsStateWithLifecycle()`. Pas d'état mutable dans les composables
  au-delà de l'affichage local (ouverture d'une boîte de dialogue, par exemple).
- **Dates** : `Dates.kt` centralise formats et conversions. Le sélecteur
  Material 3 raisonne en **millisecondes UTC** — y passer par le fuseau local
  décale la date d'un jour selon l'heure qu'il est.
- **Dépendances** : toujours passer par `gradle/libs.versions.toml`, jamais de
  coordonnées en dur dans un `build.gradle.kts`. Compose est géré par la BOM.
- **Chaînes** : `strings.xml` pour tout texte destiné à l'utilisateur dès qu'une
  localisation sera nécessaire ; les libellés en dur restent tolérés tant que
  l'application n'existe qu'en français.
- **Formatage** : style officiel Kotlin (`kotlin.code.style=official`),
  indentation 4 espaces, virgule finale sur les listes multi-lignes.

## Build

```bash
./gradlew assembleDebug     # APK debug : app/build/outputs/apk/debug/app-debug.apk
./gradlew lint              # analyse statique Android
```

Le SDK Android est requis (`ANDROID_HOME`, ou `sdk.dir` dans `local.properties`,
fichier non versionné).

## Intégration continue

`.github/workflows/build.yml` s'exécute à chaque push sur `main` et sur
déclenchement manuel (`workflow_dispatch`) : checkout, JDK Temurin 17,
`gradle/actions/setup-gradle`, `./gradlew assembleDebug`, puis publication de
`app-debug.apk` dans une GitHub Release taguée `build-<numéro de run>`
(`permissions: contents: write`).

## Pistes pour la suite

- Suivi d'intervention : statut (à faire / en cours / terminée), notes, photos.
- Compte-rendu client exportable, éventuellement signé.
- Confirmation avant suppression (ou annulation par `Snackbar`).
- Tests unitaires du dépôt (Room en mémoire) et tests d'UI Compose.
- Synchronisation serveur, le jour où plusieurs techniciens partagent un planning.
