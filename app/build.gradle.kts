import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// -Ppuki.profile: the build the baseline profile is generated on. A separate ".bench" app with the
// debug key, so generating it can't reach the real game, its progress or the release key
val makingProfile = project.hasProperty("puki.profile")
if (makingProfile) apply(plugin = "androidx.baselineprofile")

// The game's version. Bump it for each release; scripts/release.py reads it from here, and the
// game updates itself to a newer one found in the GitHub releases.
val pukiVersion = "0.7.0"

// The release key lives outside the repo, in ~/keys (puki-release.jks, and its password in
// puki-signing.properties as storePassword=...). Without it (CI, contributors) builds use the
// debug key, and scripts/release.py refuses to publish them.
val keysDir = File(System.getProperty("user.home"), "keys")
val signingProps = Properties().apply {
    File(keysDir, "puki-signing.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val hasReleaseKey = File(keysDir, "puki-release.jks").isFile && signingProps.getProperty("storePassword") != null

android {
    namespace = "io.github.tomsadeh.puki"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.tomsadeh.puki"
        minSdk = 26
        targetSdk = 36
        versionCode = pukiVersion.split(".").let { (a, b, c) -> a.toInt() * 10000 + b.toInt() * 100 + c.toInt() }
        versionName = pukiVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 64-bit ARM phones and tablets only (debug builds add the x86_64 emulator, below)
        ndk { abiFilters += listOf("arm64-v8a") }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseKey) create("puki") {
            storeFile = File(keysDir, "puki-release.jks")
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = "puki"
            keyPassword = signingProps.getProperty("storePassword")
        }
    }
    // Debug builds get the same key, so a test build installs over the real one without losing
    // the child's progress (Android refuses an update signed with a different key)
    val pukiSigning = if (hasReleaseKey) signingConfigs.getByName("puki") else signingConfigs.getByName("debug")

    buildTypes {
        debug {
            ndk { abiFilters += listOf("x86_64") }
            signingConfig = pukiSigning
            // -Ppuki.bench: a separate app beside the real one, for the on-device measurements in
            // androidTest (connectedGithubDebugAndroidTest), on ARM
            if (project.hasProperty("puki.bench")) {
                applicationIdSuffix = ".bench"
                ndk { abiFilters.clear(); abiFilters += listOf("arm64-v8a") }
            }
        }
        release {
            signingConfig = if (makingProfile) signingConfigs.getByName("debug") else pukiSigning
            if (makingProfile) applicationIdSuffix = ".bench"
            // Shrunk: a smaller download and faster start (keep rules for native code in proguard-rules.pro)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Two ways out: "github" (the APKs on the GitHub releases, which update themselves from there)
    // and "play" (Google Play, which does the updating: no updater and no install permission,
    // as Play requires; src/play/AndroidManifest.xml removes them)
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // The GitHub APK keeps the device brain's big libraries compressed (a smaller download,
        // unpacked on install); the Play build leaves them uncompressed (see androidComponents)
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// On Play, uncompressed native libraries: nothing is unpacked on install (about 46 MB less on the
// device), and Play compresses the download itself
androidComponents {
    onVariants(selector().withFlavor("store" to "play")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(false)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    if (makingProfile) add("baselineProfile", project(":baselineprofile"))
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")  // applies baseline-prof.txt on sideloads

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // QR scanner for pairing with the PC (ZXing: no Play services, no network)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Puki's brain on the device itself (optional, for high-end phones): Gemma through
    // LiteRT-LM, the voice and Hebrew pronunciation through ONNX Runtime
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")  // the real org.json (Android's is a stub in JVM tests)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
