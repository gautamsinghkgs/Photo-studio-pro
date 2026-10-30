// Top-level build file. Plugin versions come from gradle/libs.versions.toml.
//
// ⚠️ AGP 9 BUILT-IN KOTLIN (bug #16). Since AGP 9.0 the Android plugin compiles
// Kotlin itself, so `org.jetbrains.kotlin.android` must NOT be applied — doing so
// fails the build with "The 'org.jetbrains.kotlin.android' plugin is no longer
// required for Kotlin support since AGP 9.0".
// AGP 9.1.1 pins its bundled Kotlin Gradle Plugin to 2.2.10. This project is
// written against Kotlin 2.3.21 and the Compose + serialization COMPILER plugins
// must match the Kotlin version, so KGP is raised the documented way:
//   developer.android.com/build/releases/agp-9-0-0-release-notes
//   #runtime-dependency-on-kotlin-gradle-plugin
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.kotlin.compose)       apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp)                  apply false
    // Firebase plugins: uncomment AFTER you add app/google-services.json (see YOUR-TURN.md step 5)
    // alias(libs.plugins.google.services)      apply false
    // alias(libs.plugins.firebase.crashlytics) apply false
}
