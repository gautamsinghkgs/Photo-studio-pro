package com.photostudio.pro.monetization.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.photostudio.pro.monetization.ads.AdPolicy
import com.photostudio.pro.monetization.ads.FlagSource

/**
 * Every tunable number in the app lives here, fetched from Firebase Remote
 * Config on the SPARK plan (free, unlimited for this use).
 *
 * This is the free, no-backend way to change ad frequency, paywall behaviour
 * and feature availability WITHOUT waiting for a 1-7 day Play review.
 * master-plan section 12.1.
 *
 * OWNER DECISION 2026-10-31: interstitials are ON at launch — one per export
 * cycle, shown AFTER the save completes, with the rails intact (daily cap,
 * 180 s gap, first-ad delay, banners off forever, pro = zero ads).
 *
 * ⚠️ NEVER enable Firebase Blaze. Cloud Functions and Cloud Storage are
 * Blaze-only and Firebase has no hard spending cap. Spark gives us
 * Analytics, Crashlytics and Remote Config, which is all we need.
 *
 * ⚠️ FIREBASE IS OPTIONAL (bug #38). Until the owner adds a real
 * google-services.json and un-comments the plugin in app/build.gradle.kts,
 * no Firebase resources exist in the build and [LocalFlags] serves these same
 * defaults from memory. The app must boot with or without Firebase.
 */
object RemoteFlagKeys {
    const val ADS_INTERSTITIAL_ENABLED    = "ads_interstitial_enabled"
    const val ADS_INTERSTITIAL_DELAY_MS   = "ads_interstitial_delay_ms"
    const val ADS_INTERSTITIAL_GAP_MS     = "ads_interstitial_gap_ms"
    const val ADS_INTERSTITIAL_DAILY_CAP  = "ads_interstitial_daily_cap"
    const val ADS_REWARDED_ENABLED        = "ads_rewarded_enabled"
    const val ADS_REWARDED_DAILY_CAP      = "ads_rewarded_daily_cap"
    const val ADS_NATIVE_ENABLED          = "ads_native_enabled"
    const val PAYWALL_VARIANT             = "paywall_variant"
    const val FREE_EXPORTS_PER_DAY        = "free_exports_per_day"
    const val FREE_BATCH_SIZE             = "free_batch_size"
    const val FREE_OCR_SCANS_PER_DAY      = "free_ocr_scans_per_day"
    const val FREE_ID_PHOTO_COUNTRIES     = "free_id_photo_countries"
    const val SSV_STRICT_MODE             = "ssv_strict_mode"   // Phase 2, always false today
    const val TOOL_ENABLED_PREFIX         = "tool_enabled_"
}

/**
 * SINGLE SOURCE OF TRUTH for the safe values. Both the Firebase Remote Config
 * defaults map ([RemoteFlags]) and the in-memory fallback ([LocalFlags]) read
 * from here, so the two can never drift apart. Bug #38.
 */
object FlagDefaults {
    const val INTERSTITIAL_ENABLED   = true     // ⭐ OWNER DECISION 2026-10-31: ON at launch
    const val FIRST_AD_DELAY_MS      = AdPolicy.DEFAULT_FIRST_AD_DELAY_MS
    const val INTERSTITIAL_GAP_MS    = AdPolicy.DEFAULT_INTERSTITIAL_GAP_MS
    const val INTERSTITIAL_DAILY_CAP = AdPolicy.DEFAULT_INTERSTITIAL_DAILY_CAP
    const val REWARDED_ENABLED       = true
    const val REWARDED_DAILY_CAP     = 8
    const val NATIVE_ENABLED         = false
    const val PAYWALL_VARIANT        = "soft_hard"
    const val FREE_EXPORTS_PER_DAY   = 3
    const val FREE_BATCH_SIZE        = 1
    const val FREE_OCR_SCANS_PER_DAY = 5
    const val FREE_ID_PHOTO_COUNTRIES = 10
    const val SSV_STRICT_MODE        = false

    /** Per-tool kill switches: a broken tool can be hidden REMOTELY in an
     *  incident, instantly, no Play review. They MUST all be present (a missing
     *  key reads as false and would hide the tool), and — bug #39 — they MUST
     *  all default to TRUE at launch. The old milestone-era `false` values (from
     *  when these tools did not exist yet) silently hid 5 of the 8 built tools
     *  on every fresh install, against onboarding's and the store listing's
     *  "eight tools" promise. `check_wiring.py` now errors on any false default
     *  for a built+listed tool, so the VALUE is guarded, not just the key. */
    val TOOL_ENABLED = mapOf(
        "bgremove" to true,
        "idphoto"  to true,
        "batch"    to true,
        "whitebg"  to true,
        "collage"  to true,
        "pdf"      to true,
        "ocr"      to true,
        "enhance"  to true,
    )
}

/**
 * The full flag surface the app reads. [RemoteFlags] (Firebase) and
 * [LocalFlags] (in-memory) both implement it; ServiceLocator chooses at
 * startup. Extends [FlagSource] so AdPolicy keeps seeing only its narrow slice.
 */
interface AppFlags : FlagSource {
    /** Call once from PhotoStudioApp.onCreate. Failures are non-fatal. */
    fun fetchAsync()
    val rewardedDailyCap: Int
    val paywallVariant: String
    val freeExportsPerDay: Int
    val freeBatchSize: Int
    val freeOcrScansPerDay: Int
    val freeIdPhotoCountries: Int
    /** Phase 2 only. Always false until an SSV endpoint exists. */
    val ssvStrictMode: Boolean
    fun isToolEnabled(toolId: String): Boolean
}

class RemoteFlags(private val config: FirebaseRemoteConfig) : AppFlags {

    init {
        // In-app defaults are the SAFE values. If fetch fails, the app still
        // behaves correctly — it just cannot be tuned live.
        config.setDefaultsAsync(
            buildMap<String, Any> {
                put(RemoteFlagKeys.ADS_INTERSTITIAL_ENABLED,   FlagDefaults.INTERSTITIAL_ENABLED)
                put(RemoteFlagKeys.ADS_INTERSTITIAL_DELAY_MS,  FlagDefaults.FIRST_AD_DELAY_MS)
                put(RemoteFlagKeys.ADS_INTERSTITIAL_GAP_MS,    FlagDefaults.INTERSTITIAL_GAP_MS)
                put(RemoteFlagKeys.ADS_INTERSTITIAL_DAILY_CAP, FlagDefaults.INTERSTITIAL_DAILY_CAP.toLong())
                put(RemoteFlagKeys.ADS_REWARDED_ENABLED,       FlagDefaults.REWARDED_ENABLED)
                put(RemoteFlagKeys.ADS_REWARDED_DAILY_CAP,     FlagDefaults.REWARDED_DAILY_CAP.toLong())
                put(RemoteFlagKeys.ADS_NATIVE_ENABLED,         FlagDefaults.NATIVE_ENABLED)
                put(RemoteFlagKeys.PAYWALL_VARIANT,            FlagDefaults.PAYWALL_VARIANT)
                put(RemoteFlagKeys.FREE_EXPORTS_PER_DAY,       FlagDefaults.FREE_EXPORTS_PER_DAY.toLong())
                put(RemoteFlagKeys.FREE_BATCH_SIZE,            FlagDefaults.FREE_BATCH_SIZE.toLong())
                put(RemoteFlagKeys.FREE_OCR_SCANS_PER_DAY,     FlagDefaults.FREE_OCR_SCANS_PER_DAY.toLong())
                put(RemoteFlagKeys.FREE_ID_PHOTO_COUNTRIES,    FlagDefaults.FREE_ID_PHOTO_COUNTRIES.toLong())
                put(RemoteFlagKeys.SSV_STRICT_MODE,            FlagDefaults.SSV_STRICT_MODE)
                FlagDefaults.TOOL_ENABLED.forEach { (tool, on) ->
                    put(RemoteFlagKeys.TOOL_ENABLED_PREFIX + tool, on)
                }
            }
        )
        config.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                // Minimum interval between fetches. Keep at 1h in production;
                // lower it only in a debug build while tuning.
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
        )
    }

    override fun fetchAsync() {
        config.fetchAndActivate()
            .addOnCompleteListener { /* ignored — defaults are already safe */ }
    }

    override val interstitialsEnabled: Boolean get() = config.getBoolean(RemoteFlagKeys.ADS_INTERSTITIAL_ENABLED)
    override val firstAdDelayMs: Long          get() = config.getLong(RemoteFlagKeys.ADS_INTERSTITIAL_DELAY_MS)
    override val interstitialGapMs: Long       get() = config.getLong(RemoteFlagKeys.ADS_INTERSTITIAL_GAP_MS)
    override val interstitialDailyCap: Int     get() = config.getLong(RemoteFlagKeys.ADS_INTERSTITIAL_DAILY_CAP).toInt()
    override val rewardedEnabled: Boolean      get() = config.getBoolean(RemoteFlagKeys.ADS_REWARDED_ENABLED)
    override val rewardedDailyCap: Int         get() = config.getLong(RemoteFlagKeys.ADS_REWARDED_DAILY_CAP).toInt()
    override val nativeEnabled: Boolean        get() = config.getBoolean(RemoteFlagKeys.ADS_NATIVE_ENABLED)
    override val paywallVariant: String        get() = config.getString(RemoteFlagKeys.PAYWALL_VARIANT)
    override val freeExportsPerDay: Int        get() = config.getLong(RemoteFlagKeys.FREE_EXPORTS_PER_DAY).toInt()
    override val freeBatchSize: Int            get() = config.getLong(RemoteFlagKeys.FREE_BATCH_SIZE).toInt()
    override val freeOcrScansPerDay: Int       get() = config.getLong(RemoteFlagKeys.FREE_OCR_SCANS_PER_DAY).toInt()
    override val freeIdPhotoCountries: Int     get() = config.getLong(RemoteFlagKeys.FREE_ID_PHOTO_COUNTRIES).toInt()
    override val ssvStrictMode: Boolean        get() = config.getBoolean(RemoteFlagKeys.SSV_STRICT_MODE)
    override fun isToolEnabled(toolId: String): Boolean =
        config.getBoolean(RemoteFlagKeys.TOOL_ENABLED_PREFIX + toolId)
}

/**
 * In-memory flags for builds with NO Firebase config — which is every build
 * today, because the google-services plugin is commented out in
 * app/build.gradle.kts until the owner adds a real google-services.json
 * (bug #38: the app used to die at startup when Firebase resources were
 * absent). Values are identical to the Remote Config defaults map because
 * both read [FlagDefaults]. When the owner enables Firebase, [RemoteFlags]
 * takes over with zero call-site changes.
 */
class LocalFlags : AppFlags {
    override fun fetchAsync() { /* nothing to fetch — memory is the source */ }
    override val interstitialsEnabled: Boolean  = FlagDefaults.INTERSTITIAL_ENABLED
    override val firstAdDelayMs: Long           = FlagDefaults.FIRST_AD_DELAY_MS
    override val interstitialGapMs: Long        = FlagDefaults.INTERSTITIAL_GAP_MS
    override val interstitialDailyCap: Int      = FlagDefaults.INTERSTITIAL_DAILY_CAP
    override val rewardedEnabled: Boolean       = FlagDefaults.REWARDED_ENABLED
    override val rewardedDailyCap: Int          = FlagDefaults.REWARDED_DAILY_CAP
    override val nativeEnabled: Boolean         = FlagDefaults.NATIVE_ENABLED
    override val paywallVariant: String         = FlagDefaults.PAYWALL_VARIANT
    override val freeExportsPerDay: Int         = FlagDefaults.FREE_EXPORTS_PER_DAY
    override val freeBatchSize: Int             = FlagDefaults.FREE_BATCH_SIZE
    override val freeOcrScansPerDay: Int        = FlagDefaults.FREE_OCR_SCANS_PER_DAY
    override val freeIdPhotoCountries: Int      = FlagDefaults.FREE_ID_PHOTO_COUNTRIES
    override val ssvStrictMode: Boolean         = FlagDefaults.SSV_STRICT_MODE
    override fun isToolEnabled(toolId: String): Boolean =
        FlagDefaults.TOOL_ENABLED[toolId] ?: false
}
