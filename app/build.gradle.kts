import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // NO kotlin.android here — AGP 9 has built-in Kotlin (see the root build file).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // Uncomment AFTER adding app/google-services.json (YOUR-TURN.md step 5)
    // alias(libs.plugins.google.services)
    // alias(libs.plugins.firebase.crashlytics)
}

// Kotlin 2.3 removed the old `kotlinOptions { jvmTarget = "17" }` DSL — it is now a
// hard compile ERROR, not a warning (workflow run #2, app/build.gradle.kts:87).
// The replacement is the compilerOptions DSL on the top-level kotlin extension,
// which is also the AGP 9 form (built-in Kotlin registers that extension).
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.photostudio.pro"
    // ⚠️ 37, not 36 — bug #16. Compose 1.12.1 / core 1.19.1 / navigation 2.10.2 /
    // lifecycle 2.11.0 all declare minCompileSdk=37 in their AAR
    // metadata. compileSdk only decides WHICH APIs you may call; targetSdk (below)
    // is what Play enforces, and that stays 36 per SPEC.md C3.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.photostudio.pro"
        minSdk = 24                        // Subject Segmentation REQUIRES API 24+
        targetSdk = 36                     // SPEC.md C3
        versionCode = 1
        versionName = "1.0.0"

        // ⚠️ HARD CONSTRAINT C1: there is NO API key in this project.
        // No cloud AI, no cloud inference, no backend. See SPEC.md section 0.
        //
        // The two values below are PUBLIC identifiers, not secrets:
        //  - RevenueCat public SDK key (safe to embed; RevenueCat documents this)
        //  - AdMob app id (declared in the manifest, not here)
        // Replace both with your own from YOUR-TURN.md steps 6 and 7.
        buildConfigField("String", "REVENUECAT_PUBLIC_KEY",
            "\"goog_public placeholder_replace_me\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Release signing is configured via keystore.properties, never hardcoded.
        // Create it when you reach YOUR-TURN.md step 12.
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            val p = Properties().apply { propsFile.inputStream().use { load(it) } }
            create("release") {
                storeFile = file(p["storeFile"] as String)
                storePassword = p["storePassword"] as String
                keyAlias = p["keyAlias"] as String
                keyPassword = p["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            // AdMob TEST MODE is driven by BuildConfig.DEBUG — never a manual toggle.
            // SPEC.md section 13: clicking your own live ads = permanent AdMob ban.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    // ⭐ CRITICAL — SPEC.md section 7.3 (the architecture trap).
    // ML Kit models are native .so libraries compiled for 4 ABIs. Without these
    // splits a universal APK would carry ~4x the model weight. AAB is mandatory
    // for new Play apps anyway, so this costs nothing.
    bundle {
        language.enableSplit = true
        density.enableSplit = true
        abi.enableSplit = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
        )
        // PdfBox-Android ships native libs; keep them uncompressed for 16 KB alignment.
        jniLibs.useLegacyPackaging = false
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    // ---- Core / Compose ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- Local storage ----
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)

    // ---- ML Kit ----
    // Subject Segmentation: UNBUNDLED ONLY (Play Services downloads the model).
    // The manifest declares com.google.mlkit.vision.DEPENDENCIES=subject_segment
    // so Play Services fetches it automatically right after install.
    implementation(libs.mlkit.subject.segmentation)
    // Face Detection + Text Recognition: BUNDLED (truly offline from first run).
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.devanagari)

    // ---- Ads + consent ----
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    // ---- Billing ----
    implementation(libs.revenuecat.purchases)   // umbrella artifact — see bug #17 in libs.versions.toml

    // ---- Firebase (SPARK PLAN ONLY — SPEC.md C2. Never enable Blaze.) ----
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    // Crashlytics needs the gradle plugin too; enable together in YOUR-TURN.md step 5
    // implementation(libs.firebase.crashlytics)

    // ---- Third party ----
    // Coil deliberately NOT here — see libs.versions.toml, bug #29 (unused dep).
    implementation(libs.pdfbox.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // ---- Test ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
