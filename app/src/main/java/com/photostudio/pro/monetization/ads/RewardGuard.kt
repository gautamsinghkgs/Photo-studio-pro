package com.photostudio.pro.monetization.ads

/**
 * Abuse control WITHOUT a backend.
 *
 * There is no AdMob Server-Side Verification endpoint in this project, so we
 * cannot cryptographically prove that a rewarded video was actually watched.
 * A modified APK could invoke the onUserEarnedReward path directly.
 *
 * That risk is deliberately accepted (master-plan section 5.2) because:
 *  - SSV is optional per Google's own docs, and the Google Mobile Ads SDK has
 *    NO retry logic for the SSV callback. On patchy Indian mobile data an
 *    honest user who briefly loses connectivity mid-ad fires the client
 *    callback but never reaches a server, and would look fraudulent.
 *  - Test ads never invoke an SSV endpoint, so it cannot be tested during the
 *    mandatory closed-testing period.
 *  - The financial exposure is tiny at launch scale.
 *
 * Instead we LIMIT THE VALUE a forger can extract:
 *  1. A hard daily cap on reward GRANTS.
 *  2. Rewards are only ever small, temporary and replaceable.
 *  3. Pro, lifetime and trial extensions are NEVER grantable by watching an ad.
 *
 * Detection is analytics-only: compare AdMob rewarded impressions against the
 * `reward_granted` event count weekly. Ratio > 3.0 means build Phase 2 SSV.
 * SPEC.md section 8.
 */
object RewardGuard {

    /**
     * The ONLY reward kinds that exist. Adding one requires a spec change,
     * because each new kind is a new avenue for abuse.
     */
    enum class RewardKind(
        /** Human-readable label used in the unlock toast. */
        val labelKey: String,
        /** How long the grant lasts, in milliseconds. 0 = single use. */
        val durationMs: Long,
    ) {
        REMOVE_WATERMARK_ONCE("reward_watermark", 0L),
        TEMPLATE_24H("reward_template_24h", 24L * 60 * 60 * 1000),
        BATCH_PLUS_5("reward_batch_5", 0L),
        HD_CREDIT_3("reward_hd_3", 0L),
        FULL_RES_EXPORT_ONCE("reward_fullres", 0L),
        // ❌ NEVER add: PRO_1_MONTH, LIFETIME_UNLOCK, TRIAL_EXTEND, SUBSCRIPTION_*
    }

    const val DEFAULT_DAILY_CAP = 8

    /**
     * Pure, easily unit-tested cap check.
     *
     * A negative counter must FAIL CLOSED. `grantsToday < cap` alone would
     * return true for -1, and a negative value is reachable in practice:
     * DataStore corruption, a user rolling the device clock backwards to reset
     * the daily counters, or a rolled-over day key. Any of those would silently
     * hand out unlimited rewards — the exact abuse RewardGuard exists to stop.
     */
    fun canGrant(grantsToday: Int, cap: Int = DEFAULT_DAILY_CAP): Boolean =
        grantsToday in 0 until cap

    /**
     * Defence in depth: even if a caller constructs a bogus kind string, we
     * refuse anything that is not a known-small reward.
     */
    fun isGrantable(rawKind: String?): RewardKind? =
        rawKind?.let { r -> RewardKind.entries.firstOrNull { it.name == r } }
}
