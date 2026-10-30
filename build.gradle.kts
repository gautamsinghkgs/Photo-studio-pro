// Top-level build file. Plugin versions come from gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.kotlin.android)       apply false
    alias(libs.plugins.kotlin.compose)       apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp)                  apply false
    // Firebase plugins: uncomment AFTER you add app/google-services.json (see YOUR-TURN.md step 5)
    // alias(libs.plugins.google.services)      apply false
    // alias(libs.plugins.firebase.crashlytics) apply false
}
