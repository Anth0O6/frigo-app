# FrigoPro

Application Android native destinée aux techniciens frigoristes en tournée.
Elle affiche les interventions d'une journée, se déplace d'un jour à l'autre,
et permet de les créer, les modifier, les supprimer et de suivre leur
avancement, chacune rangée sous un type que le technicien nomme lui-même dans
l'onglet Réglages. Un onglet Clients tient le carnet — adresse et téléphone
compris — ainsi que le parc de machines de chaque client : leur plaque
signalétique et leur emplacement en photos, et l'historique de ce qu'on a déjà
fait sur chacune. Depuis la tournée, appeler un client ou ouvrir l'itinéraire
tient en un geste. Les données sont persistées localement, et exportables dans
une archive de sauvegarde.

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
│   ├── src/test/java/com/frigopro/app/  # tests JVM (voir « Tests »)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/frigopro/app/
│       │   ├── FrigoProApplication.kt  # porte le conteneur de dépendances
│       │   ├── ConteneurApp.kt         # assemblage base ← dépôt
│       │   ├── MainActivity.kt         # unique activité, héberge l'arbre Compose
│       │   ├── data/               # modèle, base et source de données
│       │   │   ├── Intervention.kt
│       │   │   ├── Client.kt
│       │   │   ├── TypeIntervention.kt
│       │   │   ├── TypeInterventionDao.kt
│       │   │   ├── TypeInterventionRepository.kt
│       │   │   ├── Equipement.kt
│       │   │   ├── Photo.kt
│       │   │   ├── EquipementDao.kt
│       │   │   ├── EquipementRepository.kt
│       │   │   ├── RangementPhotos.kt     # ce que le dépôt attend du stockage
│       │   │   ├── StockagePhotos.kt      # les images, dans files/photos/
│       │   │   ├── ReductionPhoto.kt      # arithmétique de la réduction
│       │   │   ├── Sauvegarde.kt          # format du fichier de sauvegarde
│       │   │   ├── SauvegardeRepository.kt
│       │   │   ├── ArchiveSauvegarde.kt   # l'archive : le json et les images
│       │   │   ├── FichiersExternes.kt    # fichiers désignés par l'utilisateur
│       │   │   ├── Convertisseurs.kt
│       │   │   ├── Migrations.kt
│       │   │   ├── InterventionDao.kt
│       │   │   ├── ClientDao.kt
│       │   │   ├── FrigoProDatabase.kt
│       │   │   ├── InterventionRepository.kt
│       │   │   └── ClientRepository.kt
│       │   └── ui/                 # écrans, ViewModels et thème
│       │       ├── FrigoProApp.kt      # coquille : les trois onglets
│       │       ├── Dates.kt            # formats et conversions de dates
│       │       ├── ActionsExternes.kt  # appel et itinéraire (intentions Android)
│       │       ├── LigneTournee.kt     # intervention + fiche de son client
│       │       ├── EtatFormulaire.kt
│       │       ├── FormulaireIntervention.kt
│       │       ├── SelecteurDate.kt
│       │       ├── InterventionsScreen.kt
│       │       ├── InterventionsViewModel.kt
│       │       ├── EtatFicheClient.kt
│       │       ├── FicheClient.kt
│       │       ├── ClientsScreen.kt
│       │       ├── ClientsViewModel.kt
│       │       ├── EcranEquipement.kt  # photos et historique d'une machine
│       │       ├── EquipementsViewModel.kt
│       │       ├── PhotoChargee.kt     # décodage d'une image à la demande
│       │       ├── VisionneusePhoto.kt # une photo en plein écran
│       │       ├── DialogueIntitule.kt # saisie d'un intitulé ou d'un nom
│       │       ├── ReglagesScreen.kt
│       │       ├── ReglagesViewModel.kt
│       │       ├── MenuSauvegarde.kt
│       │       ├── SauvegardeViewModel.kt
│       │       └── theme/
│       └── res/                    # chaînes, couleurs, thème XML, icône,
│                                   # chemins du FileProvider (xml/)
├── gradle/libs.versions.toml       # versions centralisées
├── gradle/wrapper/                 # wrapper committé (jar inclus)
└── .github/workflows/build.yml     # CI : tests, APK et Release
```

## Architecture

Découpage en trois couches, sens de dépendance `ui → data` uniquement :

- **`data`** — `Intervention` (date, heure, client, ville, type, statut, notes)
  et `Client` (nom, ville) sont à la fois modèles du domaine et
  entités Room ; les deux se confondent tant que le stockage épouse le domaine, et se
  sépareront le jour où ils divergeront. `Convertisseurs` traduit les types
  `java.time` en colonnes : dates et heures sont stockées en texte de largeur
  fixe, ce qui les rend **triables et comparables directement en SQL** — c'est
  ce sur quoi reposent le `WHERE date = :date` et le `ORDER BY heure` du DAO.
  `InterventionRepository` expose un `Flow` par journée et deux écritures
  (`enregistrer`, `supprimer`) ; Room réémet le `Flow` à chaque écriture, donc
  l'UI se remet à jour sans que personne n'ait à la prévenir.
  `Client` porte aussi l'adresse et le téléphone, qui peuvent rester vides —
  c'est le cas normal, le carnet se remplissant depuis les interventions où
  seuls le nom et la ville sont demandés ; `appelable` et `localisable` disent à
  l'écran ce qu'il peut proposer.
  `TypeIntervention` est la liste des types, **vide au premier lancement** :
  « fuite de fluide » et « entretien préventif » sont le vocabulaire d'un métier,
  pas celui d'une entreprise. L'intervention en porte à la fois le lien
  (`typeId`) et une copie de l'intitulé (`typeLibelle`), et c'est ce doublon qui
  réconcilie deux exigences contradictoires : le lien permet de répercuter un
  renommage sur les tournées passées, la copie garantit qu'une intervention
  affiche toujours quelque chose — celle d'avant la liste, qui ne peut désigner
  aucun type, comme celle dont le type a été supprimé depuis. Le type est
  **facultatif** : l'exiger alors que la liste démarre vide interdirait la
  première saisie. `TypeInterventionDao` est la seule classe à écrire dans deux
  tables, par `@Transaction` : un type renommé sans ses interventions, ou
  l'inverse, laisserait la base incohérente.
  `Equipement` est le parc d'un client, et `Photo` ce qu'on en a photographié.
  La fiche d'une machine ne porte **qu'un nom d'usage** : marque, modèle et
  numéro de série sont écrits sur la plaque signalétique, et la photographier
  vaut mieux que les retaper sur un toit. La contrepartie est assumée — un
  numéro de série ne se cherche pas en texte, il se lit sur la photo — et ces
  champs s'ajouteront par une migration le jour où commander une pièce depuis
  l'application aura un sens. L'intervention porte `equipementId` et
  `equipementNom`, même couple lien / copie que pour le type et pour les mêmes
  raisons. `EquipementDao` touche trois tables par `@Transaction` : supprimer une
  machine doit effacer ses photos, détacher ses interventions et disparaître d'un
  bloc.
  Les images ne vont pas en base : SQLite n'est pas un entrepôt de fichiers, et
  une photo dans une colonne alourdirait chaque lecture de la ligne. Elles vivent
  dans `files/photos/`, la base ne portant que leur nom — un chemin absolu
  changerait d'une installation à l'autre, et une sauvegarde restaurée sur un
  autre téléphone doit retrouver ses images. Toute photo entrante est **réduite**
  (`ReductionPhoto`, grand côté à 2048) : l'original de l'appareil photo ne sert
  à rien et se paierait dans l'archive de sauvegarde. `EquipementRepository` est
  le seul endroit où la base et les fichiers avancent ensemble, et toujours dans
  le même ordre — la ligne d'abord, le fichier ensuite : une ligne sans fichier se
  voit à l'écran, un fichier sans ligne ne se voit nulle part. `RangementPhotos`
  est l'interface qui permet d'éprouver cette coordination sans Android ;
  `StockagePhotos` en est la seule implémentation, et la seule à connaître
  `BitmapFactory`, l'EXIF et le `FileProvider`.
  `ClientRepository` tient le carnet. Son tri passe par un `Collator` français
  plutôt que par SQL : `COLLATE NOCASE` ne replie pas les accents et rejetterait
  « Élise » après « Zoé ». `trouverOuCreer` est ce qui remplit le carnet — une
  intervention chez un client inconnu l'y inscrit au passage, sans écran dédié.
- **`ui`** — `FrigoProApp` est la coquille : trois onglets, `Tournée`,
  `Clients` et `Réglages`, et la barre qui en change. **Pas de graphe de
  navigation** : des sections sans lien hiérarchique se passent d'une pile
  arrière, et une variable
  `rememberSaveable` suffit. La bibliothèque de navigation s'imposera le jour
  d'une vraie destination à empiler ou d'un lien profond. Corollaire à ne pas
  perdre de vue : la barre d'onglets pose elle-même la marge de la barre système
  du bas, donc les écrans qu'elle surmonte passent `contentWindowInsets =
  WindowInsets(0, 0, 0, 0)` à leur `Scaffold`, sans quoi la marge serait comptée
  deux fois.
  `InterventionsViewModel` détient la journée consultée
  (`StateFlow<LocalDate>`) et en dérive la liste par `flatMapLatest` : changer
  la date suffit à recharger l'écran. Il la rapproche du carnet par `combine`
  pour produire des `LigneTournee` — l'intervention *et* la fiche du client chez
  qui elle a lieu, `null` pour une ligne d'avant le carnet. Le rapprochement se
  fait là plutôt que par une jointure SQL : les deux flux sont déjà observés, et
  l'écran reçoit de quoi afficher comme de quoi agir. `ClientsViewModel` tient
  l'onglet Clients sur le même modèle, `EtatFicheClient` jouant pour la fiche le
  rôle d'`EtatFormulaire` pour l'intervention. `EquipementsViewModel` tient le
  parc : la carte d'un client se déplie sur ses machines, et une machine s'ouvre
  en plein onglet — on y regarde des photos, ce qu'une feuille à mi-hauteur ne
  permet pas. Il retient la fiche ouverte **par son identifiant** et non par sa
  valeur : ce qu'affiche l'écran vient alors toujours de la base, si bien qu'un
  renommage s'y voit sans rien recopier et qu'une suppression le referme
  d'elle-même. Le retour système tient en un `BackHandler` : une seule profondeur
  à défaire ne justifie toujours pas un graphe de navigation. `PhotoChargee`
  décode une image à la taille demandée, sans bibliothèque de chargement : les
  fichiers sont locaux, peu nombreux et déjà réduits, et ce qu'une bibliothèque
  apporterait — cache réseau, préchargement — ne servirait à rien ici.
  `ReglagesViewModel` tient l'onglet
  Réglages — la liste des types pour l'instant — et n'expose qu'un seul
  `StateFlow<DialogueReglages?>` plutôt que trois booléens : deux boîtes de
  dialogue ne peuvent pas être ouvertes en même temps, et le dire au type
  supprime la question. `DialogueIntitule` est partagée par les types et
  les machines : nommer, renommer et refuser un doublon se font de la même façon,
  et des boîtes jumelles finiraient par diverger — seuls le vocabulaire et le
  test du doublon sont des paramètres.
  Appeler et ouvrir un itinéraire passent par des intentions Android
  (`ActionsExternes.kt`) : `ACTION_DIAL` plutôt que `ACTION_CALL`, pour n'avoir
  pas à demander la permission d'appeler, et le schéma `geo:` pour laisser
  l'utilisateur choisir sa cartographie. Il détient aussi le formulaire ouvert
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
Toute évolution de `Intervention` doit s'accompagner d'une migration dans
`Migrations.kt`, du schéma régénéré **committé**, et d'un cas dans
`MigrationTest` : sans le schéma, aucune migration ne peut être écrite ni
vérifiée, et une mise à jour effacerait les tournées déjà saisies.

Le schéma est produit à la compilation. Chaque build en publie une copie en
artefact `room-schemas`, d'où il se récupère sans construire le projet
localement.

Une colonne qui **disparaît** impose de reconstruire la table : `DROP COLUMN`
n'existe dans SQLite que depuis la version 3.35, absente des appareils couverts
par `minSdk 26`. C'est le cas de `MIGRATION_4_5`, qui en profite pour traduire
les anciennes constantes en intitulés. Attention à reposer les index : ils
suivent la table détruite, et Room refuse d'ouvrir une base dont le schéma ne
correspond plus — `MigrationTest` le vérifie explicitement.

Une table qui **arrive** est plus simple : `MIGRATION_5_6` crée `equipements` et
`photos` et ajoute deux colonnes aux interventions, sans rien reconstruire. Ses
index sont aussi obligatoires que les tables, Room validant le schéma entier à
l'ouverture. Les fichiers image, eux, ne sont pas du ressort d'une migration.

`MigrationTest` recrée une base telle que la version précédente l'écrivait —
empreinte d'identité comprise — puis l'ouvre par `FrigoProDatabase.creer` : la
migration est ainsi vérifiée dans les conditions réelles, y compris son
enregistrement auprès du constructeur, qu'un simple test de SQL laisserait
passer.

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

## Tests

Tout tourne sur la JVM, sans émulateur, donc en CI avant la construction de
l'APK : un test rouge bloque la publication.

| Cible | Ce qui est couvert |
| --- | --- |
| `DatesTest` | La conversion vers le sélecteur Material 3 ne doit pas dériver d'un jour selon le fuseau |
| `EtatFormulaireTest` | Validation de la saisie, distinction création/édition par l'`id` |
| `InterventionRepositoryTest` | Nettoyage des saisies, horodatage, filtre et tri par journée |
| `InterventionsViewModelTest` | Navigation entre les jours, cycle de statut, formulaire retenu sur saisie incomplète, rapprochement avec le carnet |
| `ClientTest` | Ce qui rend un client appelable ou localisable, et son adresse complète |
| `ClientRepositoryTest` | Tri français du carnet, absence de doublon à la casse près, nettoyage des coordonnées |
| `TypeInterventionRepositoryTest` | Tri français, absence de doublon, propagation d'un renommage, suppression qui laisse l'intitulé |
| `EtatFicheClientTest` | Validation de la fiche, identifiant stable d'une création |
| `ClientsViewModelTest` | Ouverture et enregistrement d'une fiche, saisie incomplète refusée |
| `ReglagesViewModelTest` | Création, renommage propagé, suppression confirmée qui laisse l'intitulé |
| `SauvegardeRepositoryTest` | Aller-retour export/restauration sans perte, refus d'un fichier douteux, relecture d'un fichier du format 1 |
| `EquipementRepositoryTest` | Tri français, parcs distincts entre clients, renommage propagé, suppression qui emporte les fichiers |
| `ReductionPhotoTest` | L'arithmétique de la réduction : une photo ne doit pas finir deux fois trop petite |
| `ArchiveSauvegardeTest` | Aller-retour dans l'archive, JSON relu seul, ancien fichier texte reconnu |
| `EquipementsViewModelTest` | Ouverture d'une fiche, renommage vu aussitôt, suppression qui referme, photos et historique |
| `MigrationTest` | Une base d'une version antérieure se migre sans perdre ses tournées, index reposés |

Les dépôts et les ViewModels s'exercent sur des faux DAO — `FauxInterventionDao`,
`FauxClientDao`, `FauxTypeInterventionDao`, `FauxEquipementDao` — qui reproduisent
le contrat SQL des vrais, et sur `FauxRangementPhotos`, une liste de noms de
fichiers qui tient lieu de stockage d'images. Seul `MigrationTest` a besoin d'un
vrai SQLite, fourni par Robolectric. Rien ne décode d'image : ce qui se vérifie
sans téléphone est isolé dans `ReductionPhoto`.

## Build

```bash
./gradlew testDebugUnitTest # tests unitaires
./gradlew assembleDebug     # APK debug : app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # APK publiée ; non signée sans les variables de signature
./gradlew lint              # analyse statique Android
```

Le SDK Android est requis (`ANDROID_HOME`, ou `sdk.dir` dans `local.properties`,
fichier non versionné).

## Sauvegarde des données

Les données ne vivent que sur le téléphone. La sauvegarde automatique d'Android
fait le minimum, mais ne se restaure qu'à la réinstallation et suppose un compte
Google : le menu de la tournée offre donc un export explicite.

La sauvegarde est une **archive zip** (`ArchiveSauvegarde`) : `sauvegarde.json`
à la racine, les images dans `photos/`. Depuis que les machines portent des
photos, un export de texte seul serait un piège — on croirait tout avoir sauvé,
et un téléphone perdu emporterait les plaques signalétiques. Le zip est un
format ordinaire, ouvrable sur n'importe quel ordinateur, où le JSON reste
lisible à l'œil. Le JSON y est écrit **en premier**, ce qui permet de le relire
seul : le fichier est validé avant que la moindre image ne soit écrite sur le
téléphone, et les photos ne sont extraites qu'ensuite, par une seconde lecture
du même fichier.

Le JSON, lui, a un **format volontairement distinct du schéma
Room** : le schéma suit les besoins de l'application et change à chaque
migration, tandis qu'un fichier de sauvegarde doit rester lisible par les
versions suivantes. `FORMAT_COURANT` se numérote donc à part, les champs
facultatifs portent une valeur par défaut, et une sauvegarde écrite par une
version plus récente est refusée plutôt que devinée.

Le format 3 ajoute le parc de machines et leurs photos. Le format 2 avait ajouté
la liste des types. Un fichier du format 1 ou 2 reste lisible — et, s'il est du
JSON en clair, reconnu comme tel : la signature `PK` distingue une archive d'un
ancien export, et rien n'oblige l'utilisateur à savoir lequel il a sous la main.
Pour le format 1 :
`InterventionSauvegarde` conserve l'ancien champ `typePanne` en lecture seule et
en déduit l'intitulé français, avec les mêmes correspondances que
`MIGRATION_4_5` — une sauvegarde d'alors et une base d'alors doivent donner le
même résultat. Tout champ retiré d'un format doit être conservé ainsi, sinon une
sauvegarde devient irrécupérable sans qu'on s'en aperçoive.

Trois décisions à connaître :

- **Les écritures passent par les DAO, pas par les dépôts.** Ceux-ci horodatent
  chaque écriture, ce qui effacerait le `modifieLe` transporté par le fichier —
  or c'est précisément ce qui départagera deux versions d'une même ligne.
- **La restauration fusionne, elle ne remplace pas.** Chaque ligne écrase celle
  qui porte le même identifiant et laisse les autres en place ; rien n'est donc
  jamais supprimé par une restauration, et les identifiants étant des UUID, deux
  lignes réellement distinctes ne peuvent se confondre. Restaurer deux fois le
  même fichier ne crée aucun doublon.
- **Une valeur illisible fait refuser le fichier entier**, avant toute écriture :
  une tournée restaurée à moitié serait pire qu'une restauration refusée. Un
  *intitulé* de type inconnu ne compte pas : il est libre par nature, au
  contraire d'un statut ou d'une catégorie de photo, qui sont des valeurs fixes
  de l'application. Un **nom de fichier** de photo qui n'en est pas un non plus :
  une archive nommant une image `../databases/frigopro.db` chercherait à faire
  écrire ailleurs que dans le dossier des photos, et `StockagePhotos.nomSur` est
  le seul rempart contre cela — le fichier vient de l'extérieur, son contenu
  aussi.

L'accès aux fichiers passe par le sélecteur du système
(`ActivityResultContracts.CreateDocument` / `OpenDocument`), d'où l'absence de
toute permission de stockage. Le filtre de lecture est `*/*` à dessein : selon
l'endroit où la sauvegarde a été rangée, le système lui attribue parfois un
autre type, et un filtre strict la rendrait invisible dans le sélecteur — c'est
aussi ce qui laisse restaurer un ancien export `.json`.

Les photos entrent par l'appareil photo, via un `FileProvider` qui lui ouvre le
dossier `files/photos/` et rien d'autre, ou par le sélecteur d'images du système.
Aucune permission dans les deux cas : ni caméra — l'application ne photographie
pas elle-même, elle délègue —, ni stockage.

## Signature et mises à jour

Android n'installe une mise à jour que si elle porte **la même signature** que
l'application déjà présente ; sinon il répond « Application non installée ». Une
APK construite sans clé déclarée est signée par la clé de debug de la machine
qui la construit — donc par une clé différente à chaque runner de CI. C'est
pourquoi le projet a sa propre clé, stable, et sans laquelle chaque nouvelle
version imposerait une désinstallation, c'est-à-dire la perte des tournées
saisies.

Cette clé ne peut pas vivre dans le dépôt, qui est public : elle est stockée en
secrets GitHub (`FRIGOPRO_KEYSTORE_BASE64`, `FRIGOPRO_KEYSTORE_PASSWORD`) et
reconstituée par la CI dans un répertoire temporaire du runner. Le
`build.gradle.kts` la lit dans l'environnement (`FRIGOPRO_KEYSTORE`,
`FRIGOPRO_KEYSTORE_PASSWORD`) ; en son absence — build local, fork — l'APK sort
non signée, ce qui permet de vérifier une compilation mais rien d'installer.
`*.keystore` et `*.jks` sont ignorés par git.

`versionCode` vient du numéro de run de la CI (`FRIGOPRO_VERSION_CODE`) : Android
refuse d'installer une version dont le code est inférieur à celui déjà posé, il
doit donc croître à chaque publication. Il vaut 1 en local, et `versionName`
affiche ce numéro (`0.2.0 (17)`) pour identifier une build depuis les
paramètres du téléphone.

**La clé est irremplaçable** : la perdre, c'est ne plus pouvoir mettre à jour
l'application sans une désinstallation chez chaque utilisateur. C'est aussi
elle qui signera les versions publiées sur le Play Store.

## Intégration continue

`.github/workflows/build.yml` s'exécute à chaque push sur `main` et sur
déclenchement manuel (`workflow_dispatch`) : checkout, JDK Temurin 17,
`gradle/actions/setup-gradle`, `./gradlew testDebugUnitTest`, restitution de la
clé de signature, `./gradlew assembleRelease`, vérification de la signature par
`apksigner verify --print-certs`, publication du schéma Room en artefact, puis
publication de `app-release.apk` dans une GitHub Release taguée
`build-<numéro de run>` (`permissions: contents: write`).

Deux filets protègent la signature : sans clé, Gradle nomme sa sortie
`app-release-unsigned.apk` et l'étape de publication ne trouve plus son fichier ;
et `apksigner verify` échoue de son côté. L'empreinte du certificat est imprimée
dans le journal du build, où elle doit rester identique d'une build à l'autre.

## Pistes pour la suite

Dans l'ordre souhaité par l'utilisateur, « ce qui s'est vraiment passé sur
place » venant en tête :

- Le temps passé : heure d'arrivée, heure de départ, durée réelle.
- Les pièces et le fluide utilisés. Le fluide frigorigène a ses obligations de
  traçabilité.
- Photos avant / après, prises depuis l'intervention — par opposition aux photos
  de la machine, qui décrivent un état durable et vivent sur sa fiche.
- Marque, modèle et numéro de série en champs sur la fiche machine, le jour où
  commander une pièce depuis l'application aura un sens : aujourd'hui la photo de
  la plaque les porte, au prix de ne pas être cherchables.
- Compte-rendu client exportable, éventuellement signé.
- Suppression d'un client, qui devra décider du sort du `clientId` des
  interventions passées — les types et les machines montrent une façon de le
  faire : couper le lien, garder la copie — et du sort de son parc, qui n'a lui
  aucune existence sans client.
- Confirmation avant suppression d'une intervention (ou annulation par
  `Snackbar`).
- Tests d'UI Compose.
- Synchronisation serveur, le jour où plusieurs techniciens partagent un planning.
