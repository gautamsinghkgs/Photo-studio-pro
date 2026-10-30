# ============================================================================
# Photo Studio Pro — R8 / ProGuard rules
# ============================================================================

# ---- ML Kit ----
# ML Kit ships consumer ProGuard rules, but keep the model metadata and the
# Subject Segmentation entry points explicit because that artifact is BETA.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_subject_segment.** { *; }
-dontwarn com.google.mlkit.**

# ---- Google Mobile Ads + UMP ----
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.gms.ads.**

# ---- RevenueCat ----
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# ---- Firebase ----
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ---- PdfBox-Android (uses reflection heavily) ----
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn java.awt.**

# ---- pdfbox's OPTIONAL JPEG2000 codec (bug #33) ----
# com.tom_roush.pdfbox.filter.JPXFilter references com.gemalto.jp2.JP2Decoder /
# JP2Encoder. pdfbox-android treats JPX as an optional codec and does NOT bundle
# the Gemalto library, so R8's missing-class check fails minifyReleaseWithR8
# (CI run #7, the first release job ever to execute). Debug builds never run R8,
# which is why this never appeared in 30 green local rounds.
#
# This -dontwarn is honest, not a mask: the classes are genuinely absent at
# runtime too, and behaviour is unchanged — PdfViewModel wraps pdfToImages in
# runCatching (which catches Throwable), so a PDF page containing JPX-encoded
# images degrades to Ui.Failed("pdf_render_failed") instead of crashing.
-dontwarn com.gemalto.jp2.**

# ---- Kotlin serialization (countries.json) ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.photostudio.pro.** {
    *** Companion;
}
-keepclasseswithmembers class com.photostudio.pro.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.photostudio.pro.**$$serializer { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---- Keep line numbers for Crashlytics stack traces ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# material-icons-extended  (SIZE TRAP — read this)
# ---------------------------------------------------------------------------
# This library ships ~7,000 icon objects and is roughly 15 MB unshrunk. This app
# uses only 13 of them (see the Icons.Filled.* references across feature/).
#
# It is safe here ONLY because release has isMinifyEnabled = true AND
# isShrinkResources = true, which lets R8 discard every icon that is never
# referenced. If you ever turn minification off, or move to a debug build for a
# size measurement, the AAB will balloon by ~15 MB and blow the ~33 MB budget.
#
# VERIFY after your first release build:
#   1. Build -> Analyze APK -> look at classes.dex and res/ size
#   2. Search the dex for "androidx.compose.material.icons.filled" — you should
#      see roughly a dozen classes, not thousands.
# If you see thousands, minification is not running. Do not ship that build.
#
# Never add -keep rules for the icons package; that defeats the shrinker.
