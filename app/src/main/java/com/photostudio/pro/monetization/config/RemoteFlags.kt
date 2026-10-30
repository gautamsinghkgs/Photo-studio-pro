package com.photostudio.pro.monetization.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.photostudio.pro.monetization.ads.AdPolicy

/**
 * Every tunable number in the app lives here, fetched from Firebase Remote
 * Config on the SPARK plan (free, unlimited for this use).
 *
 * This is the free, no-backend way to change ad frequency, paywall behaviour
 * and feature availability WITHOUT waiting for a 1-7 day Play review.
 * master-plan section 12.1.
 *
 * ⚠️ LAUNCH DEFAULT: interstitials are OFF. They are switched on remotely
 * around Day 30, only if D7 retention is holding above 15% and crash-free
 * sessions above 99.5%. This is a business decision, not a code decision —
 * but the code must make it a one-line remote change.
 *
 * ⚠️ NEVER enable Firebase Blaze. Cloud Functions and Cloud Storage are
 * Blaze-only and Firebase has no hard spending cap. Spark gives us
 * Analytics, Crashlytics and Remote Config, which is all we need.
 */
object RemoteFlagKeys {
    const val ADS_INTERSTITIAL_ENABLED    = "ads_interstitial_enabled"
    const val ADS_INTERSTITIAL_DELAY_MS   = "ads_interstitial_delay_ms"
    const val ADS_INTERSTITIAL_GAP_MS     = "ads_interstitial_gap_ms"
    const val ADS_INTERSTITIAL_DAILY_CAP  = "ads_interstitial_daily_cap"
    const val ADS_REWARDED_ENABLED        = "ads_rewarded_enabled"
    const val ADS_REWARDED_DAILY_CAP      = "ads_rewarded_daily_cap"
    const val ADS_NATIVE_ENABLED          = "ads_native_enabled"
    const val PAYWALL_AFTER_SCREENS       = "paywall_after_screens"
    const val PAYWALL_VARIANT             = "paywall_variant"
    const val FREE_EXPORTS_PER_DAY        = "free_exports_per_day"
    const val FREE_BATCH_SIZE             = "free_batch_size"
    const val FREE_OCR_SCANS_PER_DAY      = "free_ocr_scans_per_day"
    const val FREE_ID_PHOTO_COUNTRIES     = "free_id_photo_countries"
    const val SSV_STRICT_MODE             = "ssv_strict_mode"   // Phase 2, always false today
    const val TOOL_ENABLED_PREFIX         = "tool_enabled_"
}

class RemoteFlags(private val config: FirebaseRemoteConfig) {

    init {
        // In-app defaults are the SAFE values. If fetch fails, the app still
        // behaves correctly — it just cannot be tuned live.
        config.setDefaultsAsync(
            mapOf(
                RemoteFlagKeys.ADS_INTERSTITIAL_ENABLED   to false,   // ⭐ OFF at launch
                RemoteFlagKeys.ADS_INTERSTITIAL_DELAY_MS  to AdPolicy.DEFAULT_FIRST_AD_DELAY_MS,
                RemoteFlagKeys.ADS_INTERSTITIAL_GAP_MS    to AdPolicy.DEFAULT_INTERSTITIAL_GAP_MS,
                RemoteFlagKeys.ADS_INTERSTITIAL_DAILY_CAP to AdPolicy.DEFAULT_INTERSTITIAL_DAILY_CAP.toLong(),
                RemoteFlagKeys.ADS_REWARDED_ENABLED       to true,
                RemoteFlagKeys.ADS_REWARDED_DAILY_CAP     to 8L,
                RemoteFlagKeys.ADS_NATIVE_ENABLED         to false,
                RemoteFlagKeys.PAYWALL_AFTER_SCREONS      to 3L,
                RemoteFlagKeys.PAYWALL_VARIANT            to "soft_hard",
                RemoteFlagKeys.FREE_EXPORTS_PER_DAY       to 3L,
                RemoteFlagKeys.FREE_BATCH_SIZE            to 1L,
                RemoteFlagKeys.FREE_OCR_SCANS_PER_DAY     to 5L,
                RemoteFlagKeys.FREE_ID_PHOTO_COUNTRIES    to 10L,
                RemoteFlagKeys.SSV_STRICT_MODE            to false,
                // Per-tool kill switches. They MUST be in the defaults map:
                // getBoolean() returns false for a key that was never set, which
                // would hide every tool on a fresh install before the first fetch.
                (RemoteFlagKeys.TOOL_ENABLED_PREFIX + "bgremove") to true,
                (RemoteFlagKeys.TOOL_ENABLED_PREFIX + "idphoto")  to true,
                (RemoteFlagKeys.TOOL_ENABLED_PREFIX + "batch")    to true,
                (RemoteFlagKeys.TOOL_ENABLED_PREFIX + "whitebg")  to false,
                (RemoteFlagKeys.TOOL_ENABLED_PREFIX + "collage")  to false,
                (RemoteFlagKeys.TOOL_ENABLED_PREFIX + "pdf")      to false,
                (RemoteFlagKeys.TOOL_ENABLED_PREFIX + "ocr")      to false,
                (RemoteFlagKeys.TOOL_ENABLED_PREFIX + "enhance")  to false,
            )
        )
        config.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                // Minimum interval between fetches. Keep at 1h in production;
                // lower it only in a debug build while tuning.
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
        )
    }

    /** Call once from PhotoStudioApp.onCreate. Failures are non-fatal. */
    fun fetchAsync() {
        config.fetchAndActivate()
            .addOnCompleteListener { /* ignored — defaults are already safe */ }
    }

    val interstitialsEnabled: Boolean get() = config.getBoolean(RemoteFlagKeys.ADS_INTERSTITIAL_ENABLED)
    val firstAdDelayMs: Long          get() = config.getLong(RemoteFlagKeys.ADS_INTERSTITIAL_DELAY_MS)
    val interstitialGapMs: Long       get() = config.getLong(RemoteFlagKeys.ADS_INTERSTITIAL_GAP_MS)
    val interstitialDailyCap: Int     get() = config.getLong(RemoteFlagKeys.ADS_INTERSTITIAL_DAILY_CAP).toInt()
    val rewardedEnabled: Boolean      get() = config.getBoolean(RemoteFlagKeys.ADS_REWARDED_ENABLED)
    val rewardedDailyCap: Int         get() = config.getLong(RemoteFlagKeys.ADS_REWARDED_DAILY_CAP).toInt()
    val nativeEnabled: Boolean        get() = config.getBoolean(RemoteFlagKeys.ADS_NATIVE_ENABLED)
    val paywallAfterScreens: Int      get() = config.getLong(RemoteFlagKeys.PAYWALL_AFTER_SCREONS).toInt()
    val paywallVariant: String        get() = config.getString(RemoteFlagKeys.PAYWALL_VARIANT)
    val freeExportsPerDay: Int        get() = config.getLong(RemoteFlagKeys.FREE_EXPORTS_PER_DAY).toInt()
    val freeBatchSize: Int            get() = config.getLong(RemoteFlagKeys.FREE_BATCH_SIZE).toInt()
    val freeOcrScansPerDay: Int       get() = config.getLong(RemoteFlagKeys.FREE_OCR_SCANS_PER_DAY).toInt()
    val freeIdPhotoCountries: Int     get() = config.getLong(RemoteFlagKeys.FREE_ID_PHOTO_COUNTRIES).toInt()

    /** Phase 2 only. Always false until an SSV endpoint exists. */
    val ssvStrictMode: Boolean        get() = config.getBoolean(RemoteFlagKeys.SSV_STRICT_MODE)

    /** Per-tool kill switch. A broken tool can be hidden instantly, no update. */
    fun isToolEnabled(toolId: String): Boolean =
        config.getBoolean(RemoteFlagKeys.TOOL_ENABLED_PREFIX + toolId)
}
