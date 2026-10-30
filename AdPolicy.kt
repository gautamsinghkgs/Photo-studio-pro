package com.photostudio.pro.monetization.ads

import com.photostudio.pro.analytics.Events
import com.photostudio.pro.monetization.billing.EntitlementManager
import com.photostudio.pro.monetization.config.RemoteFlags

/**
 * ⭐⭐⭐ THE MOST IMPORTANT FILE IN THIS PROJECT ⭐⭐⭐
 *
 * This is the SINGLE SOURCE OF TRUTH for every ad-show decision in the app.
 * No feature screen, ViewModel or manager may call AdManager.show*() directly —
 * they must ask AdPolicy first and honour the Decision.
 *
 * Why this matters commercially (see master-plan section 8.2):
 *  - Showing a forced ad to a paying user is the #2 revenue killer. It causes
 *    refunds and 1-star reviews, because the user paid specifically to not
 *    see ads.
 *  - Entitlement / ad-SDK desync is the mechanism: the purchase succeeded but
 *    the ad layer did not hear about it. Routing every decision through this
 *    one class, reading a single StateFlow, is what prevents that.
 *
 * Design principles encoded below:
 *  1. A paying user NEVER sees a forced ad.
 *  2. A new user NEVER sees an ad in their first 90 seconds or first 2 days.
 *  3. Every ad lands on a natural break point (after a completed export/save).
 *  4. Every threshold is remotely tunable, because retention damage is only
 *     visible after launch and a Play review takes 1-7 days.
 *  5. Every denial is logged with a reason. When revenue looks wrong, this
 *     log is the first place you look.
 *
 * All defaults come from RemoteFlags, which falls back to the constants below
 * if Firebase has not fetched yet. SPEC.md section 7.
 */
class AdPolicy(
    // Seams, not concrete classes (bug #28). AdPolicy is the most unit-tested file
    // in the project and it must stay testable on a plain JVM with no Android
    // runtime, no Firebase and no DataStore. `UsageCounters` already worked this
    // way; entitlements and flags now do too.
    private val entitlements: EntitlementSource,
    private val flags: FlagSource,
    private val usage: UsageCounters,
) {

    /** Snapshot of everything AdPolicy needs to decide. Built by the caller. */
    data class AdContext(
        val isAppOpen: Boolean = false,
        val isOnboarding: Boolean = false,
        /** True ONLY after an export/save has completed. Never mid-flow. */
        val isNaturalBreakPoint: Boolean = false,
        val daysSinceInstall: Int = 0,
        val msSinceSessionStart: Long = 0,
        val msSinceLastInterstitial: Long = Long.MAX_VALUE,
    )

    data class Decision(val allowed: Boolean, val reason: String)

    // ---- Thresholds. RemoteFlags wins; these are the cold-start fallbacks. ----
    companion object {
        const val DEFAULT_FIRST_AD_DELAY_MS = 90_000L   // 60-90s lift D1 ~5-8%, no revenue loss
        const val DEFAULT_INTERSTITIAL_GAP_MS = 180_000L // industry floor 180-240s
        const val DEFAULT_INTERSTITIAL_DAILY_CAP = 3     // max 3-4/user/day
        const val DEFAULT_ONBOARDING_SUPPRESS_DAYS = 2   // zero full-screen ads Day 0-2

        fun canShowInterstitialPure(
            isPro: Boolean,
            isInTrial: Boolean,
            hasPurchaseIntent: Boolean,
            interstitialsEnabled: Boolean,
            ctx: AdContext,
            firstAdDelayMs: Long = DEFAULT_FIRST_AD_DELAY_MS,
            gapMs: Long = DEFAULT_INTERSTITIAL_GAP_MS,
            suppressDays: Int = DEFAULT_ONBOARDING_SUPPRESS_DAYS,
        ): Decision {
            // Kill switch first: if remote config says off, nothing else matters.
            if (!interstitialsEnabled)             return Decision(false, "remote_kill_switch")
            if (isPro)                             return Decision(false, "user_is_pro")
            if (isInTrial)                         return Decision(false, "user_in_trial")
            if (hasPurchaseIntent)                 return Decision(false, "purchase_intent_detected")
            if (ctx.isAppOpen)                     return Decision(false, "never_on_app_open")
            if (ctx.isOnboarding)                  return Decision(false, "never_during_onboarding")
            if (!ctx.isNaturalBreakPoint)          return Decision(false, "not_a_break_point")
            if (ctx.daysSinceInstall < suppressDays) return Decision(false, "day0_2_suppression")
            if (ctx.msSinceSessionStart < firstAdDelayMs) return Decision(false, "first_ad_delay")
            if (ctx.msSinceLastInterstitial < gapMs)      return Decision(false, "gap_not_elapsed")
            return Decision(true, "allowed")
        }
    }

    /**
     * Interstitials are shown ONLY after a successful export/save.
     * The daily cap is applied HERE rather than in the pure function because it
     * needs the persisted counter. `canShowInterstitialPure` deliberately does NOT
     * take a `dailyCap` argument: it used to accept one and ignore it, which meant
     * a caller passing `dailyCap = 0` expected ads off and silently got ads. An
     * unused parameter is a lie in the signature — this project has been bitten by
     * that exact shape before (LayoutMath.floodWhiten accepted `high` and never
     * used it, so the hysteresis its own docstring described did not exist).
     */
    fun canShowInterstitial(ctx: AdContext): Decision {
        val e = entitlements.snapshot()
        val d = canShowInterstitialPure(
            isPro = e.isPro,
            isInTrial = e.isInTrial,
            hasPurchaseIntent = e.hasPurchaseIntent,
            interstitialsEnabled = flags.interstitialsEnabled,
            ctx = ctx,
            firstAdDelayMs = flags.firstAdDelayMs,
            gapMs = flags.interstitialGapMs,
        )
        if (!d.allowed) return log(d)
        if (usage.interstitialsToday >= flags.interstitialDailyCap) return log(Decision(false, "daily_cap"))
        return log(d)
    }

    /**
     * Rewarded ads are USER-INITIATED, so they are never "forced" and Pro users
     * may still watch one if they choose to (e.g. to unlock a template they do
     * not want to pay for). The cap lives in RewardGuard on the GRANT side, not
     * here, because the thing we want to limit is the value extracted — not the
     * number of videos watched.
     */
    fun canShowRewarded(ctx: AdContext): Decision {
        if (!flags.rewardedEnabled) return log(Decision(false, "remote_kill_switch"))
        if (ctx.isAppOpen)          return log(Decision(false, "never_on_app_open"))
        if (ctx.isOnboarding)       return log(Decision(false, "never_during_onboarding"))
        return log(Decision(true, "allowed"))
    }

    /** Native ads in the template/tool gallery. Suppressed for payers and Day 0-1. */
    fun canShowNative(ctx: AdContext): Decision {
        val e = entitlements.snapshot()
        if (!flags.nativeEnabled)   return log(Decision(false, "remote_kill_switch"))
        if (e.isPro || e.isInTrial) return log(Decision(false, "user_is_pro"))
        if (ctx.isAppOpen)          return log(Decision(false, "never_on_app_open"))
        if (ctx.daysSinceInstall < DEFAULT_ONBOARDING_SUPPRESS_DAYS)
            return log(Decision(false, "day0_2_suppression"))
        return log(Decision(true, "allowed"))
    }

    /**
     * BANNER ADS ARE PERMANENTLY DISABLED.
     * India banner eCPM is roughly $0.08-0.40, the format degrades UX in a tool
     * app, and accidental clicks near a bottom navigation bar are a real AdMob
     * account-ban risk. There is no configuration flag that turns this on.
     * SPEC.md section 7 / master-plan section 8.3.
     */
    fun canShowBanner(@Suppress("UNUSED_PARAMETER") ctx: AdContext): Decision =
        Decision(false, "banners_permanently_disabled")

    /**
     * APP-OPEN ADS ARE DISABLED AT LAUNCH.
     * A full-screen ad on launch is the top uninstall trigger. Revisit only
     * after Day 90 and only if blended ARPDAU justifies it.
     */
    fun canShowAppOpen(@Suppress("UNUSED_PARAMETER") ctx: AdContext): Decision =
        Decision(false, "app_open_disabled_until_day90")

    private fun log(d: Decision): Decision {
        Events.logAdDecision(d.allowed, d.reason)
        return d
    }
}

/**
 * Daily counters read by AdPolicy. Backed by DataStore/Room in production;
 * an in-memory implementation exists for tests.
 */
interface UsageCounters {
    val interstitialsToday: Int
    val rewardedGrantsToday: Int
    val exportsToday: Int
    val ocrScansToday: Int
}

/**
 * The ONLY slice of [EntitlementManager] that AdPolicy is allowed to see.
 *
 * A synchronous snapshot, because an ad decision cannot suspend: the ad SDK asks
 * "may I show this now?" on the main thread and an answer that arrives later is
 * an ad shown to a paying user.
 */
interface EntitlementSource {
    fun snapshot(): EntitlementManager.State
}

/**
 * The ONLY slice of [RemoteFlags] that AdPolicy is allowed to see.
 *
 * Deliberately narrow: if someone adds a flag here, they are declaring that ad
 * policy depends on it, and AdPolicyTest will not compile until they cover it.
 * That is the point — every one of these numbers can change the revenue curve.
 */
interface FlagSource {
    val interstitialsEnabled: Boolean
    val firstAdDelayMs: Long
    val interstitialGapMs: Long
    val interstitialDailyCap: Int
    val rewardedEnabled: Boolean
    val nativeEnabled: Boolean
}
