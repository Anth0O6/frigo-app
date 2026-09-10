plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// L'alias n'est pas un secret : il désigne une clé, il ne l'ouvre pas.
val aliasCle = "frigopro"

// La clé de signature vient de l'environnement, jamais du dépôt : celui-ci est
// public. La CI la reconstitue depuis un secret ; en local ces variables sont
// absentes et l'APK sort non signée, donc bonne à vérifier une compilation et
// à rien d'autre.
val magasinCles = System.getenv("FRIGOPRO_KEYSTORE")?.let { file(it) }
val motDePasseCles = System.getenv("FRIGOPRO_KEYSTORE_PASSWORD")
val signatureDisponible = magasinCles?.exists() == true && !motDePasseCles.isNullOrBlank()

// Android refuse d'installer une version dont le code est inférieur à celui déjà
// posé sur l'appareil : il doit croître à chaque publication. La CI y injecte son
// numéro de run. En local il vaut 1, donc une APK construite à la main ne
// s'installera pas par-dessus une APK de la CI — c'est voulu, les deux ne sont
// pas signées par la même clé de toute façon.
val numeroBuild = System.getenv("FRIGOPRO_VERSION_CODE")?.toIntOrNull() ?: 1

android {
    namespace = "com.frigopro.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.frigopro.app"
        minSdk = 26
        targetSdk = 36
        versionCode = numeroBuild
        versionName = "0.2.0 ($numeroBuild)"
    }

    signingConfigs {
        if (signatureDisponible) {
            create("release") {
                storeFile = magasinCles
                storePassword = motDePasseCles
                keyAlias = aliasCle
                // PKCS12 n'accepte qu'un seul mot de passe pour le magasin et la clé.
                keyPassword = motDePasseCles
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // `null` en l'absence de clé : Gradle produit alors une APK
            // « unsigned », que la CI refusera de publier faute de la trouver.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true

        // Sans cela, un `testDebugUnitTest` vert ne dit pas ce qu'il a exécuté.
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
