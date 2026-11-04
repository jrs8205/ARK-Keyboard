import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

// Julkaisuavaimen tiedot luetaan gitin ulkopuolisesta tiedostosta;
// ilman sitä release-build jää allekirjoittamatta (esim. CI).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "org.jarsi.ark"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.jarsi.ark.nappaimisto"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "0.21.0"

        ndk {
            // Vain puhelinten arkkitehtuurit: ML Kitin käännöskirjaston
            // x86-versiot ovat emulaattoreita varten ja veivät APK:sta
            // yli puolet (~35 Mt).
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Asetussivu on Composella; näppäimistö itse on View-pohjainen.
    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Julkaisu-APK saa versiollisen nimen (esim. ARK-nappaimisto-v0.11.0.apk),
    // jotta GitHub-releasen lataus on tunnistettava. Debug-nimi säilyy
    // ennallaan asennusskriptejä varten.
    applicationVariants.all {
        if (buildType.name == "release") {
            val apkName = "ark-nappaimisto-v$versionName.apk"
            outputs.all {
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                    .outputFileName = apkName
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Material 3 Expressive -rajapinnat ovat 1.5-alphassa kokeellisia; opt-in
        // koko moduulille, ettei jokaiseen tiedostoon tule samaa merkintää.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    // Emojisivut ja ensiasennuksen esittely pyyhkäistävinä.
    implementation(libs.androidx.viewpager2)
    // Täyttöpalvelun inline-ehdotusten chipityyli (Android 11+).
    implementation(libs.androidx.autofill)
    // Asetusten käynnistyskuva samanlaisena kaikilla Android-versioilla.
    implementation(libs.androidx.core.splashscreen)
    // Paikallinen käännös: mallit ladataan kerran, käännös tapahtuu laitteella.
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    // Sanelun suoratoisto: WebSocket OpenAI:n realtime-transkriptioon.
    implementation(libs.okhttp)
    // Asetussivu: Compose ja Material 3 Expressive.
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    // Yksikkötesteissä android.jarin org.json on pelkkä tynkä; oikea
    // toteutus tuodaan testien luokkapolulle.
    testImplementation(libs.json)
}
