plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing comes from the environment (CI secrets, or a local shell), never
// from the repository. Without it a release build is left unsigned.
val keystore: String? = System.getenv("FLOCKSWITCH_KEYSTORE")

// `-PplayStore` builds what goes to Google Play: no update check, no contact section, and
// no self-update — neither its code (src/github) nor the install permission.
val playStore = hasProperty("playStore")

android {
    namespace = "io.github.wwhalefe.flockswitch"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.wwhalefe.flockswitch"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.8.0"
        buildConfigField("boolean", "PLAY_STORE", playStore.toString())
    }

    sourceSets {
        // The GitHub build updates itself (src/github: code, plus a manifest that adds
        // REQUEST_INSTALL_PACKAGES and the installer's receiver); the Play build gets an
        // empty stand-in (src/play) and nothing added to the manifest.
        getByName("main").kotlin.directories.add(if (playStore) "src/play/java" else "src/github/java")
        if (!playStore) {
            getByName("debug").manifest.srcFile("src/github/AndroidManifest.xml")
            getByName("release").manifest.srcFile("src/github/AndroidManifest.xml")
        }
    }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("FLOCKSWITCH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FLOCKSWITCH_KEY_ALIAS")
                keyPassword = System.getenv("FLOCKSWITCH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
