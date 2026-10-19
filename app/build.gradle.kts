plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.matheo.sonnet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.matheo.sonnet"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signée avec la clé debug pour pouvoir l'installer directement.
            // Remplace par une vraie signingConfig pour une distribution publique.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Les API "unstable" de Media3 sont annotées @OptIn dans le code ;
    // on évite juste qu'un avertissement lint bloque un build release.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // WebView (interface Jellyfin web + Bridge Hub)
    implementation("androidx.webkit:webkit:1.11.0")

    // Lecteur natif : ExoPlayer (direct play 4K/HDR/HEVC/AV1, HLS pour le transcodage)
    val media3 = "1.9.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")
    // Décodeurs audio logiciels FFmpeg (AC3, E-AC3, DTS, TrueHD, ALAC…) — fournis par le projet Jellyfin
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    // Pliables : posture tabletop / livre (Galaxy Z Fold)
    implementation("androidx.window:window:1.3.0")

    // Affiches et vignettes
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Réglages persistants
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
