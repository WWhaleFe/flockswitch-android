plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing comes from the environment (CI secrets, or a local shell), never
// from the repository. Without it a release build is left unsigned.
val keystore: String? = System.getenv("FLOCKSWITCH_KEYSTORE")

// `-PplayStore` builds what goes to Google Play: no update check, no contact section, and
// no self-update: neither its code (src/github) nor the install permission.
val playStore = hasProperty("playStore")

android {
    namespace = "io.github.wwhalefe.flockswitch"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.wwhalefe.flockswitch"
        minSdk = 26
        // Target 37 requires the ACCESS_LOCAL_NETWORK runtime permission.
        targetSdk = 36
        versionCode = 19
        versionName = "2.4.0"
        buildConfigField("boolean", "PLAY_STORE", playStore.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        // The GitHub build updates itself (src/github: code, plus a manifest that adds
        // REQUEST_INSTALL_PACKAGES and the installer's receiver); the Play build gets an
        // empty stand-in (src/play) and an empty manifest: INTERNET stays (src/main) for the
        // local-network receiver, but nothing in it goes to the internet.
        getByName("main").kotlin.directories.add(if (playStore) "src/play/java" else "src/github/java")
        val buildManifest = if (playStore) "src/play/AndroidManifest.xml" else "src/github/AndroidManifest.xml"
        getByName("debug").manifest.srcFile(buildManifest)
        getByName("release").manifest.srcFile(buildManifest)
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

    // The screenshot test for the store listing (src/androidTest).
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Builds the shared Rust core (../netcore through flocknet-jni) into jniLibs with
// cargo-ndk. Where cargo-ndk is missing, the jniLibs already there are used.
val rustPath = listOf(
    "${System.getProperty("user.home")}/.cargo/bin",
    "${System.getProperty("user.home")}/.rustup/toolchains/stable-aarch64-apple-darwin/bin",
    "/opt/homebrew/bin",
    System.getenv("PATH") ?: "",
).joinToString(":")
val buildRustLibs by tasks.registering(Exec::class) {
    workingDir(rootProject.file("flocknet-jni"))
    environment("PATH", rustPath)
    environment("ANDROID_NDK_HOME", System.getenv("ANDROID_NDK_HOME") ?: "/opt/homebrew/share/android-commandlinetools/ndk/28.2.13676358")
    commandLine("sh", "-c", "cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release")
    onlyIf {
        rustPath.split(":").any { File(it, "cargo-ndk").canExecute() }.also {
            if (!it) logger.warn("cargo-ndk unavailable; using existing jniLibs")
        }
    }
}
tasks.named("preBuild") { dependsOn(buildRustLibs) }
