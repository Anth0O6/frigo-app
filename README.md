# FrigoPro

Application Android native (Kotlin / Jetpack Compose / Material 3) pour les
techniciens frigoristes : la v0 affiche les interventions du jour et permet
d'en ajouter une.

## Compiler

```bash
./gradlew assembleDebug
```

L'APK est produit dans `app/build/outputs/apk/debug/app-debug.apk`.

Chaque push sur `main` (ou un déclenchement manuel du workflow *Build debug
APK*) compile l'application et publie cet APK dans une GitHub Release taguée
`build-<numéro de run>`.

Voir [CLAUDE.md](CLAUDE.md) pour l'architecture et les conventions du projet.
