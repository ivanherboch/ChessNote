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
        versionCode = 42
        versionName = "2.5.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        // Clé de démonstration versionnée pour produire un APK installable directement.
        create("release") {
            storeFile = rootProject.file("keystore/chessduck-release.jks")
            storePassword = "chessduck"
            keyAlias = "chessduck"
            keyPassword = "chessduck"
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
    }
    androidResources { noCompress += "db" }
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
