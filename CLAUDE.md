# FrigoPro

Application Android native destinée aux techniciens frigoristes en tournée.
La v0 affiche la liste des interventions du jour et permet d'en ajouter une.

## Stack

| Élément | Choix |
| --- | --- |
| Langage | Kotlin |
| UI | Jetpack Compose + Material 3 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Build | Gradle (wrapper committé), AGP, catalogue de versions `gradle/libs.versions.toml` |
| JDK | 17 (source/target et `jvmTarget`) |
| Package | `com.frigopro.app` |

## Structure

```
.
├── app/
│   ├── build.gradle.kts            # configuration du module applicatif
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/frigopro/app/
│       │   ├── MainActivity.kt     # unique activité, héberge l'arbre Compose
│       │   ├── data/               # modèle + source de données
│       │   │   ├── Intervention.kt
│       │   │   └── InterventionRepository.kt
│       │   └── ui/                 # écrans, ViewModels et thème
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

- **`data`** — `Intervention` (heure, client, ville, type de panne) et
  `TypePanne`. `InterventionRepository` détient l'état dans un
  `MutableStateFlow` : **tout est en mémoire en v0**, rien n'est persisté ; les
  données sont factices et repartent de zéro à chaque lancement. L'API du dépôt
  est cependant celle qu'aurait une implémentation Room ou réseau, pour que le
  passage à la persistance ne touche ni le ViewModel ni l'UI.
- **`ui`** — `InterventionsViewModel` expose un `StateFlow<List<Intervention>>`
  et reçoit les intentions utilisateur (`onAjouterIntervention`). L'écran suit
  le motif *state hoisting* : `InterventionsRoute` (avec état, branché sur le
  ViewModel) enveloppe `InterventionsScreen` (sans état, testable et
  prévisualisable).
- **`ui.theme`** — thème Material 3 avec couleurs dynamiques (Material You) sur
  Android 12+, repli sur la palette « froid » définie dans `Color.kt`.

Un seul écran, pas de navigation : ajouter une destination impliquera
d'introduire un graphe de navigation et de déplacer `InterventionsRoute`
derrière celui-ci.

## Conventions

- **Nommage** : code du domaine en français (`Intervention`, `TypePanne`,
  `ajouterIntervention`) pour coller au vocabulaire métier ; les API Android et
  Compose gardent évidemment leurs noms d'origine.
- **Composables** : `PascalCase`, un `Modifier` en premier paramètre optionnel,
  paramètres d'état avant les lambdas de rappel, `@Preview` privé en fin de
  fichier.
- **État** : exposé en `StateFlow` et collecté avec
  `collectAsStateWithLifecycle()`. Pas d'état mutable dans les composables
  au-delà de l'affichage local.
- **Dépendances** : toujours passer par `gradle/libs.versions.toml`, jamais de
  coordonnées en dur dans un `build.gradle.kts`. Compose est géré par la BOM.
- **Chaînes** : `strings.xml` pour tout texte destiné à l'utilisateur dès qu'une
  localisation sera nécessaire ; la v0 tolère les libellés en dur dans les
  composables.
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

- Persistance des interventions (Room) derrière `InterventionRepository`.
- Formulaire de création plutôt que l'ajout d'un exemple par le bouton flottant.
- Écran de détail d'une intervention + navigation.
- Tests unitaires du dépôt et tests d'UI Compose.
