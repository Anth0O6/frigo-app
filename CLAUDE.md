# FrigoPro

Application Android native destinée aux techniciens frigoristes en tournée.
Elle affiche les interventions d'une journée, se déplace d'un jour à l'autre ou
d'une semaine à l'autre, et permet de les créer, les modifier, les supprimer et
de suivre leur avancement, chacune rangée sous un type que le technicien nomme
lui-même dans l'onglet Réglages.

Chaque intervention s'ouvre sur **ce qui s'y est vraiment passé** : un
chronomètre qui se met en pause et reprend, les relevés frigorifiques (BP, HP,
surchauffe, sous-refroidissement), les mouvements de fluide consignés au
registre, les pièces posées, des photos avant/après, et un compte-rendu que le
client signe du doigt. Les relevés alimentent une **aide au dépannage** qui
propose des pistes — jamais un verdict — et le contrôle qui tranche chacune.

Un onglet Clients tient le carnet — adresse et téléphone compris — ainsi que le
parc de machines de chaque client : plaque signalétique, fluide et charge,
photos, échéance du contrôle d'étanchéité et historique. Un onglet Devis permet
de chiffrer sur place, **déplacement compris** : temps de trajet, kilomètres et
péages, au km, à l'heure ou les deux, avec un plancher et le choix de l'offrir. Depuis la tournée, appeler un client ou ouvrir
l'itinéraire tient en un geste. Les données sont persistées localement, et
exportables dans une archive de sauvegarde.

## Stack

| Élément | Choix |
| --- | --- |
| Langage | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Persistance | Room (SQLite local), KSP pour la génération |
| Polices | Barlow et IBM Plex Mono, **embarquées** (`res/font`) |
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
│       │   │   ├── Chrono.kt              # le temps passé, et son arithmétique
│       │   │   ├── Fluide.kt              # GWP, classe ISO 817, périodicité 517/2014
│       │   │   ├── Conversions.kt         # huit familles d'unités, affines
│       │   │   ├── PuissanceEchangee.kt   # débit × ρ × cp × Δt, dans les trois sens
│       │   │   ├── CourbesSaturation.kt   # bulle et rosée, les dix-sept fluides
│       │   │   ├── VerificationFluide.kt  # « j'ai contrôlé cette courbe »
│       │   │   ├── Depannage.kt           # les pistes déduites des relevés
│       │   │   ├── Releve.kt              # relevés, fluide, pièces posées
│       │   │   ├── Devis.kt               # devis, lignes et totaux
│       │   │   ├── Deplacement.kt         # tarif, trajet, et ce qu'il facture
│       │   │   ├── ServiceItineraire.kt   # le contrat, et la lecture de la réponse
│       │   │   ├── ItineraireRelais.kt    # la seule classe qui ouvre une connexion
│       │   │   ├── Parametres.kt          # les réglages, en une seule ligne
│       │   │   ├── Prestation.kt          # technicien, checklist, catalogue
│       │   │   ├── CatalogueDao.kt
│       │   │   ├── CatalogueRepository.kt
│       │   │   ├── Initiales.kt           # « KB », une seule fois pour quatre écrans
│       │   │   ├── Numerotation.kt        # INT-2605-018, DEV-2605-007
│       │   │   ├── SuiviDao.kt
│       │   │   ├── SuiviRepository.kt
│       │   │   ├── DevisDao.kt
│       │   │   ├── DevisRepository.kt
│       │   │   ├── EquipementDao.kt
│       │   │   ├── EquipementRepository.kt
│       │   │   ├── RangementPhotos.kt     # ce que le dépôt attend du stockage
│       │   │   ├── StockagePhotos.kt      # les images, dans files/photos/
│       │   │   ├── StockageDocuments.kt   # les PDF à envoyer, dans le cache
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
│       │       ├── FrigoProApp.kt      # coquille : les six onglets
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
│       │       ├── EcranIntervention.kt # les quatre volets d'une intervention
│       │       ├── OngletReleves.kt     # chrono, relevés frigorifiques, fluide
│       │       ├── OngletPieces.kt      # pièces posées, photos avant/après
│       │       ├── OngletRapport.kt     # compte-rendu, et tracé de la signature
│       │       ├── InterventionViewModel.kt
│       │       ├── InterventionRoute.kt
│       │       ├── EcranDepannage.kt    # les pistes, et le contrôle qui tranche
│       │       ├── DialogueSignature.kt # signer au doigt, puis rasteriser
│       │       ├── EcranSemaine.kt      # le planning des cinq jours ouvrés
│       │       ├── FriseHoraire.kt      # le temps en hauteur, les créneaux dessus
│       │       ├── EcranAujourdhui.kt   # l'accueil : « et maintenant ? »
│       │       ├── AujourdhuiViewModel.kt
│       │       ├── AujourdhuiRoute.kt
│       │       ├── OngletFiche.kt       # créneau, adresse, machine, checklist
│       │       ├── CouleurStatut.kt     # deux dimensions réduites à une couleur
│       │       ├── DevisScreen.kt
│       │       ├── SectionDeplacement.kt  # le déplacement dans un devis
│       │       ├── DevisViewModel.kt
│       │       ├── DocumentDevis.kt     # ce que le devis imprimé dit
│       │       ├── MiseEnPageDevis.kt   # l'arithmétique de la page A4
│       │       ├── PdfDevis.kt          # le seul à connaître Canvas
│       │       ├── EtatReglette.kt      # pression ↔ température, et le côté
│       │       ├── FeuilleReglette.kt   # la réglette, curseur et avertissement
│       │       ├── EcranOutils.kt       # l'onglet Outils, et le cadre d'un outil
│       │       ├── OutilsCalculs.kt     # convertisseur, bilan, F-Gas, fiche fluide
│       │       ├── OutilsViewModel.kt
│       │       ├── FicheMachine.kt      # plaque, fluide, étanchéité, tendance
│       │       ├── SectionsReglages.kt  # technicien, thème, gants, tarifs
│       │       ├── Nombres.kt           # virgule décimale à la saisie
│       │       ├── composants/          # le vocabulaire visuel commun
│       │       ├── EcranEquipement.kt  # photos et historique d'une machine
│       │       ├── EquipementsViewModel.kt
│       │       ├── PhotoChargee.kt     # décodage d'une image à la demande
│       │       ├── VisionneusePhoto.kt # une photo en plein écran
│       │       ├── DialogueIntitule.kt # saisie d'un intitulé ou d'un nom
│       │       ├── DialoguePrestation.kt # créer, retarifer, retirer
│       │       ├── ReglagesScreen.kt
│       │       ├── ReglagesViewModel.kt
│       │       ├── MenuSauvegarde.kt
│       │       ├── SauvegardeViewModel.kt
│       │       └── theme/
│       └── res/                    # chaînes, couleurs, thème XML, icône,
│                                   # chemins du FileProvider (xml/)
├── design/                         # le logo source et le script qui en tire
│                                   # les icônes (voir « Icône »)
├── relais/                         # le relais d'itinéraire : il tient la clé
│                                   # pour que l'utilisateur n'ait rien à faire
├── gradle/libs.versions.toml       # versions centralisées
├── gradle/wrapper/                 # wrapper committé (jar inclus)
├── .github/workflows/build.yml     # CI : tests, APK et Release (sur main)
└── .github/workflows/checks.yml    # CI : tests et compilation (sur une branche)
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
  bloc — et, depuis le multi-split, faire le même travail pour chacune de ses
  unités.
  Une machine peut porter des **unités intérieures** : un bi-split est un groupe
  extérieur et deux unités, et non trois machines au même rang. Le lien est un
  `parentId` sur la même table plutôt qu'une table à part : une unité est une
  machine — elle se nomme, se photographie, porte un historique — et lui donner sa
  propre table aurait doublé les quatre écrans qui la montrent. `GroupeMachines`
  réunit un groupe et ses unités, et **le compte d'unités est dérivé, jamais
  stocké** : un champ `nombreUnites` aurait dérivé à la première unité arrivée
  autrement que par l'écran qui l'incrémente — une restauration de sauvegarde, par
  exemple. La hiérarchie n'a **qu'un seul niveau**, et c'est une décision de l'UI
  et non une limite du modèle : `parentId` autoriserait un arbre de profondeur
  quelconque, qui demanderait un écran sachant le parcourir pour un besoin qui
  n'existe pas — un split se branche sur un groupe, pas sur un autre split.
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
  `Technicien` porte le même couple lien / copie que le type et la machine, et
  pour la même raison : un renommage suit les tournées passées, une suppression
  coupe le lien et laisse le nom — une tournée de mars doit continuer de dire qui
  l'a faite. `PointChecklist` est **recopié** sur chaque intervention plutôt que
  référencé : le modèle peut changer sans réécrire ce qu'on avait demandé de
  vérifier aux interventions passées. Trois de ses quatre points par défaut sont
  des obligations réglementaires, et c'est pour cela qu'ils sont là plutôt que
  laissés à la mémoire ; la liste est posée à l'**ouverture** d'une intervention
  et non à sa création, pour qu'une intervention saisie la semaine dernière la
  reçoive aussi.
  `Prestation` est le catalogue d'où se chiffrent les devis. Il est **livré avec
  ses intitulés et sans ses prix** : « recharge R-449A » est le vocabulaire d'un
  métier, un tarif horaire celui d'une entreprise, et un prix inventé partirait
  chez un vrai client sans que personne ne l'ait relu — une ligne à zéro euro se
  voit et appelle une correction. Deux chemins le posent et **partagent le même
  SQL** (`SQL_CATALOGUE_INITIAL`) : `MIGRATION_7_8` pour un téléphone déjà garni
  de tournées, le `onCreate` de Room pour une installation neuve. Les laisser
  diverger reviendrait à livrer deux applications différentes selon l'ancienneté
  du téléphone. Une prestation peut se compter **par unité intérieure** : poser un
  bi-split double la main-d'œuvre sans doubler le forfait de déplacement, et les
  deux cas cohabitent donc dans le catalogue. La case ne fait que **pré-remplir**
  la quantité de la ligne de devis, qui reste modifiable : une deuxième unité au
  même étage ne coûte pas le même temps qu'une deuxième trois étages plus haut, et
  proposer est utile là où imposer serait faux. Son `@ColumnInfo(defaultValue)`
  est le seul indispensable du projet — voir [Prestation] pour ce qu'il a coûté de
  l'oublier.
  Le montant d'un devis n'est pas recopié sur sa ligne : il est la somme de ses
  lignes, et le recopier serait s'exposer à ce qu'il cesse d'être juste après une
  modification. `DevisDao.observerTotaux` les calcule tous en un `GROUP BY`,
  d'où sortent `DevisChiffre` et les compteurs de l'accueil comme de l'onglet — et
  son `CASE WHEN offerte = 0` est ce qui en écarte les lignes offertes.
  **Offrir la TVA est une remise commerciale égale à son montant, pas un taux
  ramené à zéro.** La distinction n'est pas cosmétique : la taxe reste due, et un
  document annonçant « TVA 0 % » alors qu'on y est assujetti serait faux — c'est
  l'entreprise qui en répondrait. Le devis porte donc la TVA à son taux, puis la
  remise en dessous. Une **ligne offerte garde son prix** pour la même raison,
  barré à l'écran comme au PDF : une remise qu'on ne voit pas n'est pas un
  argument de vente, et remettre le prix à zéro aurait interdit de reprendre le
  geste sans ressaisir. `assujettiTva` est **recopié** sur chaque devis comme
  `tauxTva`, et pour la même raison : franchir le seuil de la franchise en base ne
  doit pas faire apparaître de la TVA sur un devis envoyé il y a six mois.
  `Parametres.MENTION_FRANCHISE` est nommée plutôt que recopiée — elle paraît à
  l'écran et sur le PDF, et les laisser diverger ferait partir chez un client une
  formule qui n'est pas celle du code général des impôts.
  **La facturation du déplacement** est le seul endroit de l'application qui
  sorte du téléphone. `Trajet` porte les chiffres d'un **aller** — distance, durée,
  péage — et `allerRetour` est une décision de facturation appliquée au calcul, non
  une distance déjà doublée : stocker le double aurait figé ce choix, et cocher la
  case après coup aurait laissé un chiffre faux sans que rien ne le signale. Le
  résultat d'un calcul est en revanche **stocké et jamais recalculé** : un
  itinéraire est un fait daté venu de l'extérieur, et un devis de mars doit rester
  chiffrable en février suivant, sans réseau et sans que le prix ait bougé parce
  qu'une autoroute a ouvert. C'est l'inverse exact des échéances F-Gas, qui se
  recalculent parce qu'elles ne découlent que de la date du dernier contrôle.
  `peagesConnus` est séparé du montant, et ce n'est pas un raffinement : « pas de
  péage sur ce trajet » et « je n'en sais rien » valent tous deux zéro dans une
  colonne de chiffres et ne valent pas la même chose sur un devis.
  Le déplacement **devient des lignes de devis ordinaires** (`LignesDeplacement`)
  plutôt qu'un second chemin vers le total, et c'est la décision centrale : tout ce
  qui existait fonctionne alors sans retouche — le `GROUP BY` des totaux, la TVA,
  la pagination du PDF, et « offrir » qui est déjà porté par `LigneDevis.offerte`
  et s'affiche barré. Un total parallèle aurait demandé de toucher sept endroits,
  chacun avec ses tests, pour dire la même chose. La contrepartie est assumée :
  `Trajet` garde les données d'entrée, les lignes en sont l'expression commerciale,
  et le marqueur `LigneDevis.deplacement` permet de refaire *les siennes* sans
  toucher à celles qu'on a saisies — une retouche à la main sur une ligne de
  déplacement est donc perdue au recalcul suivant, et c'est pourquoi le recalcul ne
  se déclenche jamais tout seul.
  **Les quantités sont arrondies avant de servir**, et c'est le point le plus
  subtil de tout le calcul : la quantité s'imprime sur le devis à deux décimales,
  si bien qu'un montant calculé sur la valeur exacte ne retomberait pas sur la
  ligne que le client a sous les yeux. Trente-cinq minutes aller-retour font
  1,1666… h ; la ligne annonce « 1,17 h » et doit valoir 1,17 × le tarif, sinon
  elle se contredit toute seule. On facture douze secondes de plus, ce qui est sans
  conséquence, là où un devis qui ne s'additionne pas fait rappeler. Le **plancher**
  ne s'applique qu'au trajet et jamais aux péages, qui sont des débours : les
  fondre dedans les ferait disparaître sur les courtes distances — celles,
  justement, où le plancher joue. Il produit une ligne de **forfait** et non un
  tarif kilométrique déduit : annoncer « 3 km à 8,33 € » aurait inventé un tarif
  pour justifier le montant, et un tarif qu'un client peut opposer au trajet
  suivant.
  `CourbesSaturation` porte les courbes bulle / rosée de dix-sept fluides, écrites
  **en clair** dans un format relisible contre une réglette de poche : ces valeurs
  doivent pouvoir être contrôlées par quelqu'un qui n'écrit pas de code. Elles
  vivent dans le code comme les GWP — ce sont des constantes physiques — mais
  **qui les a vérifiées est une donnée de l'utilisateur** et va en base
  (`VerificationFluide`) : c'est lui qui engage sa responsabilité en réglant un
  détendeur dessus. Rien n'est extrapolé hors de la plage saisie, et la courbe du
  CO₂ s'arrête au point critique : au-delà il n'y a plus de saturation, et un
  chiffre inventé serait le plus nuisible là précisément.
  `ClasseSecurite` porte la classification ISO 817 des fluides du catalogue. Elle
  commande la façon de travailler : l'inflammabilité décide des outils, du brasage
  et de la charge admise dans un local, la toxicité de la ventilation. Le libellé
  porte le **sens** et pas seulement le code, parce que « A2L » ne dit rien à qui
  n'a pas la table en tête — et un code faux passerait alors inaperçu là où une
  mention « faiblement inflammable » sur un R-410A sauterait aux yeux. Comme le
  GWP, rien n'est deviné : un fluide inconnu n'a pas de classe, le dire
  ininflammable sans rien en savoir étant le genre de supposition qui met le feu à
  un local technique. `PeriodiciteControle.pour` modélise enfin le **détecteur de
  fuite fixe**, qui double les intervalles (art. 4 § 3) ; son défaut est *sans*
  détecteur, et ce défaut est un choix — annoncer un contrôle trop tôt fait perdre
  une heure, trop tard expose à une sanction. Doubler une absence d'obligation ne
  veut rien dire : une machine non soumise le reste.
  `Conversions` décrit chaque unité comme une **fonction affine** de la référence
  de sa famille. Porter un décalage pour toutes — alors qu'il ne sert qu'aux
  températures — évite deux codes de conversion dont un seul serait éprouvé. La
  distinction qui compte est celle entre une **température** et un **écart de
  température** : 10 °C valent 50 °F, mais un écart de 10 K vaut 18 °F, et les
  confondre se paie sur une surchauffe. Ce sont deux familles séparées, et la
  conversion de l'une vers l'autre est refusée. Les facteurs sont les valeurs
  exactes des définitions, pas des arrondis, qu'un recopiage de proche en proche
  ferait dériver. `PuissanceEchangee` porte `P = débit × ρ × cp × Δt` dans ses
  **trois sens**, parce que les trois se posent sur le terrain ; un seul chemin
  aurait laissé passer une division inversée dans les deux autres. Les valeurs des
  caloporteurs sont données à une condition de référence, dite dans le modèle et
  reprise à l'écran : la masse volumique de l'air varie de près de moitié entre une
  chambre froide et une toiture en août, et le résultat est un **ordre de grandeur
  juste**, pas un relevé de réception.
  `initialesDe` est partagée : quatre écrans la dérivaient chacun à sa façon, et
  elles divergeaient déjà — « L'Épicerie du coin » donnait « L » sur l'un et
  « LÉ » sur l'autre.
  `ClientRepository` tient le carnet. Son tri passe par un `Collator` français
  plutôt que par SQL : `COLLATE NOCASE` ne replie pas les accents et rejetterait
  « Élise » après « Zoé ». `trouverOuCreer` est ce qui remplit le carnet — une
  intervention chez un client inconnu l'y inscrit au passage, sans écran dédié.
- **`ui`** — `FrigoProApp` est la coquille : six onglets, `Aujourd'hui`,
  `Planning`, `Devis`, `Clients`, `Outils` et `Réglages`, et la barre qui en
  change. **Six est un de plus que ce que Material recommande**, et les libellés
  sont déjà abrégés au plus court lisible ; la contrepartie est assumée plutôt que
  contournée par un menu « plus » — un onglet derrière un menu n'est pas un onglet,
  et celui-ci doit s'atteindre d'un pouce, gants aux mains.
  L'accueil vient en tête parce qu'il répond à la question qu'on se pose en
  sortant le téléphone — « et maintenant ? » — et le planning juste après, pour
  la suivante : « et le reste de la semaine ? ». `EcranAujourdhui` met en avant
  l'intervention en cours, ou à défaut la prochaine, et ne navigue **jamais**
  dans le temps : donner deux façons de changer de date conduirait à se demander
  laquelle des deux on regarde. Les échéances F-Gas qu'il annonce ne sont
  stockées nulle part — elles se recalculent (`EtatEtancheite`), une échéance en
  base étant fausse le lendemain d'un contrôle.
  Le planning offre deux vues de la même journée, et c'est délibéré : la liste
  dit ce qui vient ensuite, `FriseHoraire` dit **où sont les trous** — la seule
  question qui compte quand un client demande à être dépanné aujourd'hui. Les
  créneaux y sont posés en décalage absolu et non empilés, si bien que deux
  interventions qui se chevauchent se chevauchent à l'écran : un chevauchement
  est une erreur de planification, et il faut qu'elle se voie. Son amplitude
  horaire s'adapte à la journée au lieu d'être figée — une astreinte à 5 h
  sortirait d'une frise fixe, et une intervention invisible sur le planning est
  pire qu'un planning plus long.
  `CouleurStatut` réduit à une seule couleur les deux dimensions que le modèle
  garde séparées : l'avancement et l'urgence. Le modèle les sépare parce qu'une
  urgence reste une urgence une fois terminée ; l'écran n'a qu'une pastille à
  peindre. **Pas de graphe de
  navigation** : des sections sans lien hiérarchique se passent d'une pile
  arrière, et une variable
  `rememberSaveable` suffit. Une intervention, une machine ou un devis ouverts
  *remplacent* leur liste plutôt que de s'empiler dessus, ce qui garde une seule
  profondeur et un simple `BackHandler`. La bibliothèque de navigation s'imposera le jour
  d'une vraie destination à empiler ou d'un lien profond. Corollaire à ne pas
  perdre de vue : **c'est la coquille qui pose les marges des barres système**,
  et elle seule — la barre d'onglets porte celle du bas, et `FrigoProApp` pose
  celle du haut et des côtés. Les écrans qu'elle héberge passent donc
  `contentWindowInsets = WindowInsets(0, 0, 0, 0)` à leur `Scaffold`, et les deux
  `TopAppBar` restantes reçoivent le même `windowInsets`, sans quoi la marge
  serait comptée deux fois. La marge du haut vient de `safeDrawing` et non des
  seules barres système : sur un téléphone à appareil photo perforé, la découpe
  déborde de la barre d'état et masquerait le titre de l'écran. Les boîtes plein
  écran (`VisionneusePhoto`, `DialogueSignature`) sont des **fenêtres à part**,
  que la marge de la coquille n'atteint pas : elles la posent elles-mêmes.
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
  Réglages — la liste des types, et les prix du catalogue, qui ne se saisissent
  que là : le catalogue est livré sans tarifs, et un catalogue qu'on ne peut pas
  tarifer ne sert à rien — et n'expose qu'un seul
  `StateFlow<DialogueReglages?>` plutôt que trois booléens : deux boîtes de
  dialogue ne peuvent pas être ouvertes en même temps, et le dire au type
  supprime la question. `DialogueIntitule` est partagée par les types, les
  machines et les unités : nommer, renommer et refuser un doublon se font de la
  même façon, et des boîtes jumelles finiraient par diverger — seuls le
  vocabulaire et le test du doublon sont des paramètres. Ce dernier n'est pas le
  même pour une unité : deux groupes homonymes chez un client sont une confusion,
  deux unités « Salon » sous deux groupes différents ne le sont pas — c'est le cas
  ordinaire d'un immeuble. `DialoguePrestation` suit le même motif et sert les deux
  chemins par où le catalogue s'enrichit : les Réglages, et la feuille du devis.
  Le second compte autant que le premier — une pièce qu'il faudrait aller déclarer
  dans les Réglages finit saisie en ligne libre, et le catalogue ne grossit jamais.
  `FeuilleReglette` et `EtatReglette` sont la réglette pression / température, et
  `CoteCircuit` en est la pièce centrale : il **choisit la colonne**, rosée à
  l'aspiration pour la surchauffe, bulle au refoulement pour le
  sous-refroidissement. Les confondre sur un R-448A donne un écart faux de tout le
  glissement — près de 5 K, dans le sens qui fait croire à une surchauffe
  suffisante quand elle ne l'est pas, et c'est du liquide qui arrive au
  compresseur. Les pressions sont en **bar relatifs**, comme sur un manomètre, et
  l'unité est dite à l'écran plutôt que supposée : l'écart avec l'absolu fait
  1,013 bar, soit environ 7 K sur un R-410A en basse pression. Le garde-fou est
  `reportable` : **aucun écart calculé sur une courbe non vérifiée n'entre dans un
  relevé**. La réglette peut l'afficher — l'avertissement est sous les yeux de
  celui qui le lit — mais une fois dans le relevé le chiffre devient un fait, qui
  part dans le compte-rendu signé et nourrit l'aide au dépannage sans que rien ne
  dise plus d'où il venait. Reporter la *pression*, elle, ne demande rien : c'est
  celle qu'on a lue au manomètre. La plage du curseur vient de la courbe et est
  l'**intersection** des deux colonnes : sur un mélange la rosée descend plus bas
  que la bulle et monte moins haut, si bien qu'une plage prise aux extrêmes
  afficherait « hors plage » aux deux bouts. `CorpsReglette` est le corps commun
  aux **deux entrées** — l'onglet Outils et l'onglet des relevés d'une
  intervention — et la feuille n'est plus qu'une coquille autour de lui. Les deux
  ne se valent pas, et c'est le seul écart : les rappels de report sont
  **facultatifs**, parce que depuis les Outils il n'y a aucun relevé où écrire et
  qu'un bouton sans effet vaut moins que pas de bouton.
  `EcranOutils` tient l'onglet **Outils**, d'une autre nature que les cinq
  autres : il ne regarde **aucune donnée de l'application**. Ce sont des outils de
  métier — réglette, convertisseur, bilan de puissance, périodicité réglementaire,
  fiche fluide — qu'on consulte sans client ni intervention ouverte, et rien n'y
  est persisté : un convertisseur n'a pas d'état à conserver, et lui donner un
  historique aurait créé quelque chose à sauvegarder, restaurer et migrer pour
  rien. Un outil ouvert *remplace* la liste, comme un devis ou une machine
  ailleurs : une seule profondeur, un `BackHandler`, toujours pas de graphe de
  navigation. Le **convertisseur** demande la famille avant les unités, parce que
  c'est l'ordre de la question — « j'ai des psi, je veux des bars » suppose qu'on
  sait déjà parler de pression —, et deux encarts veillent sur les deux pièges :
  une pression convertie ne dit pas si elle est relative ou absolue, et une
  température n'est pas un écart de température. Le **bilan** ne demande que les
  deux grandeurs connues des trois : les laisser toutes ouvertes aurait posé la
  question de celle qui gagne. La **fiche fluide** met la classe de sécurité avant
  le GWP, et c'est volontaire : le GWP décide d'une paperasse, la classe décide de
  la façon de travailler et de ce qui peut prendre feu.
  `SectionDeplacement` est le déplacement dans la feuille d'un devis, posée juste
  avant les totaux : un déplacement se lit après ce qu'on est venu faire, et avant
  l'addition. Elle tient la **saisie en cours** en état local et n'écrit qu'à un
  geste explicite — enregistrer refait les lignes du devis, et sauver à chaque
  caractère aurait réécrit trois lignes de facture à chaque frappe. Les trois
  chiffres restent saisissables **après** un calcul : c'est le compteur du véhicule
  qui fait foi devant un client, pas une estimation. Un recalcul conserve
  l'aller-retour et le geste commercial, sinon chaque appel du service aurait
  décoché l'un et repris l'autre — le genre de perte qu'on ne remarque qu'en
  relisant le total. Le tarif, lui, n'est pas modifiable là : il se règle une fois
  dans les Réglages, et le proposer sur chaque devis aurait invité à facturer chaque
  client différemment sans s'en souvenir. Les rappels des deux écrans concernés sont
  **regroupés en objets** (`EtatDeplacement`, `ActionsDeplacement`,
  `ActionsTarifDeplacement`) plutôt qu'ajoutés un à un : `EcranDevis` en portait
  déjà dix-huit et `ReglagesScreen` vingt-cinq.
  Appeler et ouvrir un itinéraire passent par des intentions Android
  (`ActionsExternes.kt`) : `ACTION_DIAL` plutôt que `ACTION_CALL`, pour n'avoir
  pas à demander la permission d'appeler, et le schéma `geo:` pour laisser
  l'utilisateur choisir sa cartographie ; `envoyerDocument` ouvre un sélecteur
  plutôt qu'une application désignée, un devis partant tantôt par courriel à un
  syndic, tantôt par message à un restaurateur qui ne lit pas ses mails. Il détient aussi le formulaire ouvert
  (`StateFlow<EtatFormulaire?>`, `null` quand l'écran n'affiche que la liste).
  `EtatFormulaire` porte la saisie en cours ; son `id` vaut `null` en création
  et identifie la ligne éditée sinon, ce qui distingue « Ajouter » d'«
  Enregistrer ». Il porte aussi `origine`, l'intervention **telle qu'elle
  était**, et `versIntervention` écrit par-dessus elle. Ce n'est pas un détail :
  le formulaire n'affiche qu'une partie d'une intervention — le reste s'est passé
  sur place — et reconstruire la ligne à neuf remettait le chronomètre à zéro,
  effaçait le numéro attribué et la signature du client dès qu'on corrigeait une
  heure mal saisie. Tout nouveau champ qui ne passe pas par le formulaire doit
  donc traverser par `copy`, et non être réécrit. Les écrans suivent le motif *state hoisting* :
  `InterventionsRoute` (avec état) enveloppe `InterventionsScreen` et
  `FormulaireIntervention` (sans état, testables et prévisualisables).
  **Le formulaire s'ouvre depuis les quatre endroits où l'on voit une
  intervention** — la liste du jour, la carte de celle en cours, la frise de la
  semaine et la fiche ouverte —, ce qui a demandé de rendre `InterventionsRoute`
  capable de le superposer à n'importe laquelle de ses trois vues plutôt que de
  sortir par un `return` avant de l'atteindre. Une heure mal saisie se corrige là
  où elle se voit : l'ouvrir depuis la seule liste du jour obligeait à en sortir
  d'abord, et depuis la fiche c'était impossible. La feuille elle-même est un
  composable à part, `FeuilleFormulaireIntervention`, parce que l'accueil l'ouvre
  aussi : la recopier dans les deux routes aurait fait diverger cinq flux au
  premier champ ajouté. Les deux passent le **même** `InterventionsViewModel` —
  `viewModel()` rend une seule instance par classe —, si bien qu'une saisie
  commencée dans un onglet se retrouve intacte dans l'autre. L'appui long y mène partout, et
  un bouton visible là où la place le permet — un geste qui ne se voit pas n'est
  pas une fonctionnalité. **Supprimer passe par une confirmation** qui nomme ce
  qui disparaît avec la ligne : le temps chronométré, la signature du client, le
  numéro attribué. Ce qui s'est passé sur place ne se retrouve pas, et l'annuler
  par `Snackbar` aurait demandé de garder la ligne en attente quelque part.
- **`ui.theme`** — thème Material 3 **sombre par défaut**, fidèle à la maquette.
  Les couleurs dynamiques (Material You) ont été retirées : elles se justifiaient
  tant que l'application n'avait pas d'identité propre, mais maintenant qu'une
  couleur *signifie* quelque chose — l'ambre est l'intervention en cours et
  l'alerte, le cyan est l'action et le fait accompli — laisser le fond d'écran du
  téléphone les repeindre reviendrait à effacer une information. Les polices sont
  **embarquées** plutôt que téléchargées : l'application revendique de fonctionner
  hors ligne, et un fournisseur de polices la ferait démarrer en Roboto au fond
  d'une chambre froide. `LocalCibles` porte la taille des cibles tactiles, que le
  **mode gants** fait passer de 56 à 68 dp.
- **`ui.composants`** — le vocabulaire visuel commun aux écrans : `Carte`,
  `Section`, `TuileChiffre`, `Puce`, `BoutonPlein`, `BoutonCarre`, `Encart`,
  `ChampChiffre`, `ChampRecherche`. La maquette répète partout les mêmes formes ;
  les nommer une fois évite qu'elles divergent écran par écran, ce qui est
  exactement ce qui arrive quand chacun recopie un `Box` et ses marges.
  **Un champ de saisie possède son texte tant qu'il a le focus**, et ne le reprend
  de l'état que lorsqu'il l'a perdu. Ce n'est pas une optimisation : un champ qui
  ne reçoit qu'une `String` laisse Compose replacer le curseur au début à chaque
  aller-retour par le ViewModel, si bien que la deuxième lettre s'insérait devant
  la première — « intervention » tapé donnait « nterventioni ». Les champs
  tiennent donc un `TextFieldValue`, qui porte la position du curseur avec le
  texte, et `onFocusChanged` dit lequel des deux fait autorité. La resynchro-
  nisation hors focus reste nécessaire : c'est ce qui fait voir un intitulé
  nettoyé par le dépôt, ou le champ vidé après enregistrement.

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

**`SchemaCommitteTest` vérifie que le schéma committé est le bon**, et il a fallu
s'y reprendre : le même contrôle écrit en intégration continue était *incapable
d'échouer*. Le schéma n'est réécrit que si KSP retraite réellement les sources ;
or Gradle croit la tâche à jour — `app/schemas` n'est pas déclaré parmi ses
sorties — et même forcée par `--rerun-tasks`, KSP garde son propre état
incrémental et conclut en neuf secondes qu'il n'a rien à faire. Le contrôle
comparait donc le fichier committé **à lui-même**, et il est passé au vert sur un
schéma dont l'empreinte d'identité était le texte « à remplacer ».

Le test, lui, ne dépend d'aucun cache : il crée une vraie base par Robolectric et
compare l'empreinte que Room y inscrit — celle qu'il a compilée depuis les
entités — à celle du fichier. L'empreinte est la seule valeur d'un schéma qui ne
se déduise pas du précédent, et c'est elle dont `MigrationTest` a besoin : fausse,
elle ne se serait vue qu'en écrivant la migration *suivante*.

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

`MIGRATION_6_7` est la plus lourde du projet : elle apporte le suivi
d'intervention, les devis et les réglages — six tables — et **reconstruit la
table des photos**. Cette reconstruction est sa seule partie délicate : une
photo pouvant désormais appartenir à une intervention, son `equipementId` doit
devenir nullable, et SQLite ne sait pas relâcher un `NOT NULL` par
`ALTER TABLE`. Elle insère aussi la ligne unique de `parametres` : sans elle,
chaque écran devrait traiter le cas « pas encore de réglages ».

`MIGRATION_7_8` apporte les techniciens, la durée d'une intervention, la
checklist et le catalogue. Tout y est ajout sauf un renommage de valeur :
`A_FAIRE` devient `PLANIFIEE`, parce que « planifié » se dit d'un créneau posé
sur une frise horaire et « à faire » d'une case de liste — c'est le même état. La
durée arrive à une heure par défaut : c'est une supposition assumée, sans durée
le planning ne saurait pas quelle hauteur donner à un créneau.

`MIGRATION_9_10` apporte la facturation du déplacement : la table des trajets, le
marqueur des lignes qu'ils produisent, et le tarif sur la ligne unique des
réglages. Tout y est ajout, donc rien à reconstruire. Les défauts disent quelque
chose : les prix arrivent à **zéro** comme le catalogue et pour la même raison —
un tarif kilométrique inventé partirait chez un vrai client sans que personne ne
l'ait relu — et `refacturerPeages` à `1`, parce que qui a avancé un péage
s'attend à le récupérer et que l'oubli coûte de l'argent là où l'inverse se
remarque à la lecture du devis.

`MIGRATION_10_11` retire la clé d'itinéraire des réglages : elle n'a plus d'objet
depuis que le calcul passe par le relais. C'est une colonne qui **disparaît**,
donc une table reconstruite — peu coûteux ici, `parametres` n'ayant qu'une ligne.
La laisser en place aurait laissé en base un champ de *credential* que plus rien
ne lit ni n'efface, et une clé oubliée dans une colonne morte est une clé qui
traîne.

**Un renommage de valeur a un jumeau côté sauvegarde.** `A_FAIRE` vit encore dans
tous les fichiers déjà exportés, et un statut inconnu fait refuser le fichier
entier — à dessein. `STATUTS_HISTORIQUES`, dans `Sauvegarde.kt`, est donc aussi
obligatoire que la migration : sans lui, la mise à jour rendrait illisibles
toutes les sauvegardes existantes, et rien ne le signalerait avant le jour où
quelqu'un essaie de restaurer. Même règle que pour `typePanne` — un fichier
d'alors et une base d'alors doivent donner le même résultat.

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
| `NombresTest` | Le montant abrégé des tuiles, et la forme longue exacte au centime |
| `EtatFormulaireTest` | Validation de la saisie, distinction création/édition par l'`id`, édition qui n'efface ni chrono ni numéro ni signature |
| `InterventionRepositoryTest` | Nettoyage des saisies, horodatage, filtre et tri par journée |
| `InterventionsViewModelTest` | Navigation entre les jours, cycle de statut, formulaire retenu sur saisie incomplète, rapprochement avec le carnet, technicien inscrit au passage |
| `ClientTest` | Ce qui rend un client appelable ou localisable, et son adresse complète |
| `ClientRepositoryTest` | Tri français du carnet, absence de doublon à la casse près, nettoyage des coordonnées |
| `TypeInterventionRepositoryTest` | Tri français, absence de doublon, propagation d'un renommage, suppression qui laisse l'intitulé |
| `EtatFicheClientTest` | Validation de la fiche, identifiant stable d'une création |
| `ClientsViewModelTest` | Ouverture et enregistrement d'une fiche, saisie incomplète refusée |
| `ReglagesViewModelTest` | Création, renommage propagé, suppression confirmée qui laisse l'intitulé, prix du catalogue renseigné et prix négatif refusé |
| `SauvegardeRepositoryTest` | Aller-retour export/restauration sans perte, refus d'un fichier douteux, relecture d'un fichier du format 1, statut retiré depuis qui reste lisible |
| `EquipementRepositoryTest` | Tri français, parcs distincts entre clients, renommage propagé, suppression qui emporte les fichiers |
| `ReductionPhotoTest` | L'arithmétique de la réduction : une photo ne doit pas finir deux fois trop petite |
| `ArchiveSauvegardeTest` | Aller-retour dans l'archive, JSON relu seul, ancien fichier texte reconnu |
| `EquipementsViewModelTest` | Ouverture d'une fiche, renommage vu aussitôt, suppression qui referme, photos et historique |
| `ChronoTest` | Reprise après pause, heure d'arrivée jamais réécrite, horloge qui recule |
| `FluideTest` | GWP, équivalent CO₂, périodicité 517/2014, détecteur de fuite qui double les intervalles, classe de sécurité jamais devinée, silence quand la charge est inconnue |
| `DepannageTest` | Le croisement surchauffe / sous-refroidissement, et le silence d'un relevé muet |
| `NumerotationTest` | Le rang repart au mois, et une suppression ne réattribue pas un numéro |
| `SuiviRepositoryTest` | Relevé vide effacé, masse ramenée au positif, suppression qui emporte tout |
| `DevisRepositoryTest` | Numérotation, totaux arrondis ligne à ligne, montant de chaque devis, ce qui compte comme « en attente », lignes emportées avec le devis |
| `ParametresRepositoryTest` | Valeurs par défaut sans ligne en base, ligne unique, initiales |
| `InterventionViewModelTest` | Chrono qui met « en cours », clôture qui numérote une seule fois, relevé créé à la première valeur, checklist posée à l'ouverture et non reposée ensuite |
| `InitialesTest` | « KB », « LÉ » : deux lettres au plus, apostrophe comprise |
| `FriseHoraireTest` | L'arithmétique du planning : amplitude adaptée, créneau à son heure, chevauchement visible |
| `ConversionsTest` | Les repères du métier (1 bar = 14,5 psi, 0 °C = 32 °F), la distinction température / écart, et l'aller-retour de toute paire d'unités |
| `PuissanceEchangeeTest` | Les deux règles de pouce (1 m³/h d'eau sur 5 K ≈ 5,8 kW), et les trois sens de la formule qui se retrouvent |
| `CourbesSaturationTest` | Cohérence interne des courbes : pression croissante, bulle jamais sous la rosée, corps purs sans glissement, rien d'extrapolé |
| `EtatRegletteTest` | La bonne colonne de chaque côté du circuit, et le report fermé tant que la courbe n'est pas vérifiée |
| `DevisViewModelTest` | Quantité pré-remplie par unité, régime recopié, prestation créée depuis le devis, ligne offerte puis reprise, itinéraire devenu lignes, recalcul qui garde l'aller-retour et n'emporte pas les lignes saisies, échec rapporté sans rien écrire |
| `MiseEnPageDevisTest` | Pagination du PDF : rien de perdu, totaux jamais coupés, « Page 2 / 3 » juste, tableau au-dessus du pied |
| `DocumentDevisTest` | Ce que le devis imprimé dit : en-tête, mentions légales, TVA offerte en remise, nom de fichier assaini |
| `DeplacementTest` | Ce qui double en aller-retour, la ligne qui retombe sur sa propre quantité, le plancher qui ne mange pas les péages, le forfait plutôt qu'un tarif inventé |
| `AnalyseItineraireRelaisTest` | La lecture d'une réponse du relais : un trajet nul qui est un échec et non un déplacement gratuit, chaque échec qui garde son sens, et aucun message qui renvoie à une configuration |
| `SchemaCommitteTest` | Le schéma committé porte l'empreinte que Room compile depuis les entités |
| `MigrationTest` | Une base d'une version antérieure se migre sans perdre ses tournées, index reposés |

Les dépôts et les ViewModels s'exercent sur des faux DAO — `FauxInterventionDao`,
`FauxClientDao`, `FauxTypeInterventionDao`, `FauxEquipementDao`, `FauxSuiviDao`,
`FauxDevisDao`, `FauxParametresDao`, `FauxTechnicienDao`, `FauxPrestationDao`,
`FauxVerificationFluideDao` — qui reproduisent le contrat SQL des vrais, et sur `FauxRangementPhotos`, une liste de noms de
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

Le format 7 ajoute la facturation du déplacement : le trajet de chaque devis, le
marqueur des lignes qu'il a produites, et le tarif kilométrique. Un trajet est
sauvegardé parce que ce n'est **pas un chiffre qu'on retrouve** : un itinéraire a
été calculé un jour donné, par un service qui ne rendra pas forcément le même
résultat six mois plus tard, et si le devis est parti chez le client c'est *ce*
chiffre-là qui fait foi. `origine` et `calculeLe` partent avec, pour la même
raison : « calculé le 12 mars » et « saisi à la main » ne se valent pas devant
quelqu'un qui discute la note, et une origine inconnue fait refuser le fichier au
même titre qu'un statut.

**Aucune sauvegarde ne transporte de secret**, et un test le vérifie. Le format 7
avait d'abord exclu une clé d'API des réglages exportés ; depuis que la clé vit
dans le relais, il n'y en a plus du tout à exclure. Le test reste, pour que la
propriété ne se reperde pas le jour où un champ de ce genre reviendrait.

Le format 6 avait ajouté les unités intérieures (`parentId`), la prestation comptée par
unité, la ligne offerte et le régime de TVA du devis, l'en-tête d'entreprise avec
son logo, et les vérifications de courbe. **Le logo part dans l'archive comme une
photo** : un technicien qui restaure sur un téléphone neuf et retrouve ses clients
mais plus son logo conclurait, à juste titre, que la restauration a échoué. Les
vérifications y entrent aussi, et pour une raison plus sérieuse : sans elles, une
restauration rendrait toutes les courbes « non vérifiées » et refermerait le
report dans le relevé, si bien qu'on recocherait dix-sept fluides sans les avoir
contrôlés à nouveau — exactement ce que le dispositif cherche à éviter.

Le format 5 ajoute les techniciens, la durée d'une intervention, la checklist et
le catalogue. Il traduit aussi `A_FAIRE` en `PLANIFIEE` à la lecture, par
`STATUTS_HISTORIQUES` : c'est ce qui garde lisibles les sauvegardes d'avant le
renommage (voir « Migrations »).

Le format 4 ajoute ce qui s'est passé sur place — temps chronométré, relevés,
mouvements de fluide, pièces posées, photos avant/après — ainsi que les devis et
les réglages. Les réglages y entrent pour une raison précise : un technicien qui
restaure sur un téléphone neuf et retrouve ses clients mais pas son taux horaire
ni son attestation fluides considérera, à juste titre, que la restauration a
échoué. Les **signatures** sont des images comme les autres et partent dans
l'archive : les oublier rendrait des comptes-rendus non signés, ce qui vide le
document de sa valeur.

Le format 3 avait ajouté le parc de machines et leurs photos. Le format 2 avait
ajouté la liste des types. Un fichier du format 1 ou 2 reste lisible — et, s'il est du
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

## Le réseau, et le relais

L'application a longtemps revendiqué de fonctionner entièrement hors ligne, et
cela reste vrai de tout sauf d'une chose : le **calcul d'itinéraire**. Aucune API
Android ne calcule une route hors ligne. D'où la première — et unique —
permission de l'application, `INTERNET`, qui est une permission dite *normale* :
elle se déclare et ne se demande pas à l'utilisateur.

### Pourquoi un relais, et pas une clé

La première version demandait sa clé d'API à l'utilisateur. C'était une erreur de
conception, et elle vaut d'être écrite ici pour ne pas être refaite :

1. **Personne ne l'aurait fait.** Un technicien frigoriste ne crée pas un compte
   sur une console d'API pour saisir un kilométrage. La fonctionnalité existait et
   n'aurait jamais été activée — ce qui revient à faire porter le coût de
   l'infrastructure à celui qui n'a rien demandé.
2. **Embarquer la clé dans l'APK n'était pas une option non plus.** Elle s'en
   extrait, et l'API Routes de Google ne sait pas restreindre une clé à une
   application Android : seule l'adresse IP d'un serveur peut l'être. Un relais
   est donc littéralement la restriction que Google propose.

La clé vit donc dans `relais/` (un Worker Cloudflare), et l'application ne parle
qu'à lui. **Le contrat est volontairement étroit** — deux adresses entrent, une
distance et une durée sortent — et c'est ce qui en fait plus qu'un tour de passe-
passe : le fournisseur de cartographie devient interchangeable, et corriger la
lecture d'une réponse se fait en redéployant un fichier, sans publier de version
ni attendre que quiconque mette à jour son téléphone. Cela a servi immédiatement :
le relais a été écrit sans pouvoir interroger le service, il accepte donc les deux
formes de réponse connues et s'éprouve contre un faux service
(`relais/worker.test.mjs`).

Derrière le relais, OpenRouteService : gratuit, sans carte bancaire. La
contrepartie est nette et dite à l'écran — **il ne chiffre pas les péages**, qui
restent à saisir. Le relais répond alors « je ne sais pas » plutôt que zéro, parce
que les deux ne valent pas la même chose sur un devis.

### Trois règles

- **Une seule classe ouvre une connexion**, `ItineraireRelais`, et c'est là qu'on
  vérifie ce qui sort du téléphone : deux adresses, vers un seul hôte. Pas de
  bibliothèque HTTP — un appel, un POST, un JSON.
- **La saisie à la main reste le chemin de secours**, et non un mode dégradé : un
  technicien au fond d'une chambre froide n'a pas de réseau, et un devis doit se
  chiffrer quand même. Aucun message d'échec ne renvoie l'utilisateur vers une
  configuration, et un test le vérifie.
- **Les adresses des clients traversent le relais.** Il ne les journalise pas, et
  c'est une propriété à préserver si on le réécrit.

`RaisonEchec` porte un message par situation plutôt qu'un seul, parce que la
suite n'est pas la même : une adresse se réécrit, une absence de réseau se
contourne en tapant ses kilomètres.

## Icône

Le logo vit dans `design/logo-frigopro.png`, et `design/genere-icones.py` en tire
tout ce que `res/` contient : c'est le script qui est la source, pas les PNG. Les
constantes en tête (la boîte de l'emblème dans l'image, le centre et le rayon de
l'anneau, les deux seuils d'alpha) sont **mesurées sur ce fichier précis** — un
logo redessiné demande de les reprendre, et c'est pour cela qu'il est versionné.

Une icône adaptative se dessine sur **108 dp dont seuls les 72 dp centraux sont
garantis visibles** : le lanceur rogne le reste en cercle, en carré arrondi ou en
goutte selon le téléphone. L'emblème occupe donc 66 % du canevas (`PART_EMBLEME`),
et le fond est un dégradé radial vectoriel (`drawable/ic_launcher_background.xml`)
plutôt qu'une couleur plate : c'est lui qui donne la profondeur du logo, et il se
laisse rogner sans perdre son centre. Les PNG de `mipmap-*` ne servent qu'au
**plan avant** et aux lanceurs d'avant Android 8 ; `mipmap-anydpi-v26` l'emporte
sur tous les appareils couverts par `minSdk 26`.

Le seuil d'alpha bas vaut 72 et non zéro, et ce n'est pas un réglage cosmétique :
le logo est posé sur une carte dont le fond plafonne à une luminance de 64, si
bien qu'un seuil plus bas la gardait à 30 % d'opacité — un rectangle visible
derrière l'emblème, une fois l'icône sur un fond clair.

Le flocon vectoriel d'origine reste sous le nom `ic_launcher_monochrome.xml` : il
sert de **silhouette** aux icônes thématisées d'Android 13, qui demandent une
forme d'une seule couleur là où le logo en a cinq.

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

Deux workflows, et la séparation est volontaire : celui de publication signe et
publie une Release, ce qui ne doit pas arriver pour éprouver une branche en
cours. `.github/workflows/checks.yml` ne fait donc que ce qui doit passer avant
une fusion — compiler et lancer les tests — et ne produit rien. Il résume un
échec aux lignes `e:` du compilateur, imprimées en dernière étape : la trace
Gradle qui les suit fait plusieurs centaines de lignes et n'apprend rien.

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

- **Export PDF du compte-rendu**, sur le modèle du devis : la mise en page et le
  partage sont écrits (`MiseEnPageDevis`, `PdfDevis`, `Context.envoyerDocument`),
  il reste à décrire le document — relevés, travaux, signature du client.
- **Export du registre des fluides.** La table existe et se remplit à chaque
  mouvement ; il manque la sortie exigible lors d'un contrôle.
- **Optimisation des trajets** depuis la vue semaine. Le calcul d'itinéraire est
  désormais là (`ServiceItineraire`) ; il manque l'ordonnancement d'une journée,
  qui est un problème d'un autre ordre — et la question de ce qu'il coûte en appels
  au service, puisqu'ils sont facturés.
- **Déployer le relais, et l'éprouver contre le vrai service.** Tant qu'aucun
  relais n'est déployé, `Itineraire.RELAIS` est vide et le calcul automatique est
  simplement absent de l'écran — la saisie à la main reste le chemin normal. Le
  relais lui-même est éprouvé contre un faux service, mais aucun appel réel n'a
  encore été passé : un écart de forme se verrait alors en échec franc plutôt
  qu'en chiffre faux, ce qui est le bon sens de l'échec, mais ce n'est pas une
  vérification.
- **Les péages automatiques**, le jour où ils compteront plus que l'absence de
  carte bancaire. C'est le relais qui changerait, pas l'application : elle sait
  déjà lire un péage chiffré et le dire connu.
- **Contrôler les courbes de saturation livrées**, fluide par fluide, contre une
  table constructeur, puis cocher chacune dans la réglette. Tant que ce n'est pas
  fait, elle affiche l'avertissement et refuse de reporter un écart dans un
  relevé : c'est voulu, mais ce n'est pas un état d'arrivée.
- **Facturation** : un devis accepté devient une facture. Le régime de TVA,
  l'en-tête d'entreprise et la mise en page du PDF sont déjà là ; il manque la
  numérotation séquentielle **sans trou**, qu'une facture exige et qu'un devis
  n'exige pas — `Numerotation` repart au mois et tolère un numéro abandonné, ce
  qui ne conviendra pas.
- **Détecteur de fuite fixe sur la fiche machine.** `PeriodiciteControle` sait
  désormais doubler les intervalles, et l'outil F-Gas pose la question ; il reste à
  porter le champ sur `Equipement` — par une migration — pour que l'accueil et la
  fiche machine en tiennent compte au lieu de retenir toujours la périodicité la
  plus exigeante.
- Plus d'un niveau de machines, si un jour un cas l'exige : `parentId` le
  permettrait, l'écran s'y refuse délibérément (voir « Architecture »).
- Plusieurs relevés horodatés par intervention : la table les accepte déjà
  (`releveLe`), l'écran n'en montre qu'un.
- Suppression d'un client, qui devra décider du sort du `clientId` des
  interventions passées — les types et les machines montrent une façon de le
  faire : couper le lien, garder la copie — et du sort de son parc, qui n'a lui
  aucune existence sans client.
- Tests d'UI Compose.
- Synchronisation serveur, le jour où plusieurs techniciens partagent un planning.
