import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is configured from an untracked keystore.properties at the
// repository root, so no credential ever lands in version control:
//
//   storeFile=/absolute/path/to/nyaya-release.jks
//   storePassword=...
//   keyAlias=nyaya
//   keyPassword=...
//
// When the file is absent the release build simply stays unsigned, which keeps
// `./gradlew assembleRelease` working for CI and for contributors who do not
// hold the signing key.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.bitchat.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "in.nyaya.ai"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 40
        versionName = "1.10.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                // Include x86_64 for emulator support during development
                abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86")
            }
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // APK splits for GitHub releases - creates arm64, x86_64, and universal APKs
    // AAB for Play Store handles architecture distribution automatically
    // Auto-detects: splits enabled for assemble tasks, disabled for bundle tasks
    // Works in Android Studio GUI and CLI without needing extra properties
    val enableSplits = gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("assemble", ignoreCase = true) &&
        !taskName.contains("bundle", ignoreCase = true)
    }

    splits {
        abi {
            isEnable = enableSplits
            reset()
            include("arm64-v8a", "x86_64", "armeabi-v7a", "x86")
            isUniversalApk = true  // For F-Droid and fallback
        }
    }

    compileOptions {
        // Java 11: JDK 21 warns that source/target 8 is obsolete, and minSdk 26
        // supports the Java 11 language level through desugaring without any
        // extra core-library desugaring configuration.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests {
            // Robolectric needs the merged assets/resources on the unit-test
            // classpath so the Nyaya tests can index the real bundled
            // knowledge base from src/main/assets/nyaya_kb.
            isIncludeAndroidResources = true
            all { test ->
                // Robolectric's native runtime ships binaries for linux-x86_64,
                // mac-x86_64, mac-aarch64 and windows-x86_64 only. On an aarch64
                // Linux host (ARM cloud builders, Raspberry-Pi-class dev boxes) it
                // aborts with "native runtime is not supported on Linux (aarch64)"
                // and every Robolectric test fails before its body runs. Falling
                // back to the legacy graphics/SQLite implementations keeps the
                // suite runnable there. Left untouched on every other platform so
                // CI (ubuntu-latest, x86_64) keeps testing the native path.
                val arch = System.getProperty("os.arch").orEmpty()
                val isLinuxArm64 = System.getProperty("os.name").orEmpty().startsWith("Linux") &&
                    (arch == "aarch64" || arch == "arm64")
                if (isLinuxArm64) {
                    test.systemProperty("robolectric.graphicsMode", "LEGACY")
                    test.systemProperty("robolectric.sqliteMode", "LEGACY")
                }
            }
        }
    }
}

// Kotlin JVM target, set through the current compilerOptions DSL. The old
// android.kotlinOptions.jvmTarget property is deprecated in Kotlin 2.x.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    // Core Android dependencies    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    
    // Lifecycle
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.lifecycle.process)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Permissions
    implementation(libs.accompanist.permissions)

    // QR
    implementation(libs.zxing.core)
    implementation(libs.mlkit.barcode.scanning)

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    
    // Cryptography
    implementation(libs.bundles.cryptography)
    
    // JSON
    implementation(libs.gson)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Bluetooth
    implementation(libs.nordic.ble)

    // WebSocket
    implementation(libs.okhttp)

    // Nyaya AI Lawyer — on-device LLM inference for Gemma 4 `.litertlm` bundles
    // via Google's LiteRT-LM runtime (runs fully offline on the phone).
    // Replaces MediaPipe tasks-genai: Gemma 4 is published for LiteRT-LM, and
    // that runtime applies each model's own chat template, so the app never has
    // to hand-assemble Gemma turn markers.
    // Note: ships native code for arm64-v8a and x86_64 only.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.14.0")

    // Arti (Tor in Rust) Android bridge - custom build from latest source
    // Built with rustls, 16KB page size support, and onio//un service client
    // Native libraries are in src/tor/jniLibs/ (extracted from arti-custom.aar)
    // Only included in tor flavor to reduce APK size for standard builds
    // Note: AAR is kept in libs/ for reference, but libraries loaded from jniLibs/

    // Google Play Services Location
    implementation(libs.gms.location)

    // Security preferences
    implementation(libs.androidx.security.crypto)
    
    // EXIF orientation handling for images
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    
    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.bundles.compose.testing)
    // Needed by the instrumentation tests that exercise AndroidKeyStore-backed
    // crypto, which cannot run on the JVM under Robolectric.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
