import java.util.Properties

/** Secret de signature : variable d'environnement, sinon keystore.properties (non versionné), sinon null. */
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun secret(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(name)?.takeIf { it.isNotBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ivanherboch.chessduck"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ivanherboch.chessduck"
        minSdk = 26
        targetSdk = 35
        versionCode = 118
        versionName = "4.7.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        // Signature : d'abord les variables CHESSDUCK_* (environnement, ou keystore.properties non versionné), sinon la
        // clé de démonstration versionnée. Ce repli garde le certificat actuel tant que la rotation (Phase B, voir
        // docs/contexte-session.md) n'a pas eu lieu : les mises à jour s'installent sans désinstaller l'application.
        create("release") {
            storeFile = rootProject.file(secret("CHESSDUCK_STORE_FILE") ?: "keystore/chessduck-release.jks")
            storePassword = secret("CHESSDUCK_STORE_PASSWORD") ?: "chessduck"
            keyAlias = secret("CHESSDUCK_KEY_ALIAS") ?: "chessduck"
            keyPassword = secret("CHESSDUCK_KEY_PASSWORD") ?: "chessduck"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    packaging {
        // Stockfish est un exécutable lancé depuis nativeLibraryDir : il doit être extrait sur le disque.
        jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/libstockfish.so" }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Table de débogage des coroutines, inutile dans l'APK.
        resources.excludes += "DebugProbesKt.bin"
    }
    // La base embarquée (chessduck.db) est compressée dans l'APK : Room (createFromAsset) et syncPuzzles la lisent
    // en flux (assets.open), sans accès direct au fichier, ce qui divise sa place par plus de deux.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Par défaut, Roborazzi enregistre les captures (build/screenshots n'est pas versionné) ;
        // -Proborazzi.test.record=false active le mode vérification quand des captures de référence existent.
        val roborazziRecord = providers.gradleProperty("roborazzi.test.record").orElse("true").get()
        unitTests.all { it.systemProperty("roborazzi.test.record", roborazziRecord) }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

composeCompiler {
    // Types du moteur d'échecs et entités déclarés stables (voir compose-stability.conf).
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose-stability.conf"))
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.coroutines.test)
    // Icônes étendues : uniquement pour IconGeneratorTool (l'application embarque material-icons-core + DuckIcons).
    testImplementation(libs.compose.material.icons.extended)
}
