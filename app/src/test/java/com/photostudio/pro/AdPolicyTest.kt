package com.photostudio.pro

import com.photostudio.pro.monetization.ads.AdPolicy
import com.photostudio.pro.monetization.ads.EntitlementSource
import com.photostudio.pro.monetization.ads.FlagSource
import com.photostudio.pro.monetization.ads.UsageCounters
import com.photostudio.pro.monetization.billing.EntitlementManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AdPolicy is the most important file in the project: it is the only thing that
 * decides whether a user sees a forced ad. Every denial reason below is asserted
 * literally, because those strings are what you will read in Firebase Analytics
 * (`ad_decision`) when revenue looks wrong after launch.
 *
 * These tests exercise the REAL production source, not a copy.
 */
class AdPolicyTest {

    private fun ctx(
        appOpen: Boolean = false,
        onboarding: Boolean = false,
        breakPoint: Boolean = true,
        days: Int = 10,
        sessionMs: Long = 120_000,
        lastInterstitialMs: Long = Long.MAX_VALUE,
    ) = AdPolicy.AdContext(
        isAppOpen = appOpen,
        isOnboarding = onboarding,
        isNaturalBreakPoint = breakPoint,
        daysSinceInstall = days,
        msSinceSessionStart = sessionMs,
        msSinceLastInterstitial = lastInterstitialMs,
    )

    /** The one case where an interstitial should be allowed. */
    @Test fun allowed_whenAllGatesPass() {
        val d = AdPolicy.canShowInterstitialPure(
            isPro = false, isInTrial = false, hasPurchaseIntent = false,
            interstitialsEnabled = true, ctx = ctx())
        assertTrue("expected allowed, got ${d.reason}", d.allowed)
        assertEquals("allowed", d.reason)
    }

    /**
     * ⭐ The #2 revenue killer: a paying user seeing a forced ad. This causes
     * refunds and 1-star reviews. Asserted first and asserted loudly.
     */
    @Test fun blocked_forProUser() {
        val d = AdPolicy.canShowInterstitialPure(
            isPro = true, isInTrial = false, hasPurchaseIntent = false,
            interstitialsEnabled = true, ctx = ctx())
        assertFalse(d.allowed); assertEquals("user_is_pro", d.reason)
    }

    @Test fun blocked_duringTrial() {
        val d = AdPolicy.canShowInterstitialPure(
            isPro = false, isInTrial = true, hasPurchaseIntent = false,
            interstitialsEnabled = true, ctx = ctx())
        assertFalse(d.allowed); assertEquals("user_in_trial", d.reason)
    }

    /**
     * A user mid-purchase-consideration must never be interrupted with a
     * full-screen ad — that is how you lose the sale.
     */
    @Test fun blocked_whenPurchaseIntentDetected() {
        val d = AdPolicy.canShowInterstitialPure(
            isPro = false, isInTrial = false, hasPurchaseIntent = true,
            interstitialsEnabled = true, ctx = ctx())
        assertFalse(d.allowed); assertEquals("purchase_intent_detected", d.reason)
    }

    /**
     * The kill switch must be evaluated FIRST, above even the Pro check, so a
     * remote "stop all ads" always works regardless of local entitlement state.
     */
    @Test fun killSwitchTakesPrecedenceOverEverything() {
        val d = AdPolicy.canShowInterstitialPure(
            isPro = true, isInTrial = true, hasPurchaseIntent = true,
            interstitialsEnabled = false, ctx = ctx(appOpen = true))
        assertFalse(d.allowed); assertEquals("remote_kill_switch", d.reason)
    }

    /** Interstitials at launch are OFF by default (RemoteFlags), so this must hold. */
    @Test fun blocked_byDefaultBecauseInterstitialsShipDisabled() {
        val d = AdPolicy.canShowInterstitialPure(
            isPro = false, isInTrial = false, hasPurchaseIntent = false,
            interstitialsEnabled = false, ctx = ctx())
        assertFalse(d.allowed); assertEquals("remote_kill_switch", d.reason)
    }

    @Test fun neverOnAppOpen() {
        val d = AdPolicy.canShowInterstitialPure(
            false, false, false, true, ctx(appOpen = true))
        assertFalse(d.allowed); assertEquals("never_on_app_open", d.reason)
    }

    @Test fun neverDuringOnboarding() {
        val d = AdPolicy.canShowInterstitialPure(
            false, false, false, true, ctx(onboarding = true))
        assertFalse(d.allowed); assertEquals("never_during_onboarding", d.reason)
    }

    /** Ads must land on a break point, never mid-flow. */
    @Test fun notAtBreakPoint() {
        val d = AdPolicy.canShowInterstitialPure(
            false, false, false, true, ctx(breakPoint = false))
        assertFalse(d.allowed); assertEquals("not_a_break_point", d.reason)
    }

    /** Zero full-screen ads on Day 0-2: protects D1/D7 retention, the North Star inputs. */
    @Test fun suppressedDay0AndDay1() {
        for (day in 0..1) {
            val d = AdPolicy.canShowInterstitialPure(false, false, false, true, ctx(days = day))
            assertFalse("day $day should be suppressed", d.allowed)
            assertEquals("day0_2_suppression", d.reason)
        }
    }

    @Test fun allowedOnDay2() {
        val d = AdPolicy.canShowInterstitialPure(false, false, false, true, ctx(days = 2))
        assertTrue(d.allowed); assertEquals("allowed", d.reason)
    }

    /**
     * First-ad delay: a 60-90s delay lifts D1 retention ~5-8% with no revenue
     * loss, because that first session is the trial window.
     */
    @Test fun firstAdDelayIs90Seconds() {
        assertEquals(90_000L, AdPolicy.DEFAULT_FIRST_AD_DELAY_MS)
        val tooEarly = AdPolicy.canShowInterstitialPure(
            false, false, false, true, ctx(sessionMs = 89_999))
        assertFalse(tooEarly.allowed); assertEquals("first_ad_delay", tooEarly.reason)

        val onTime = AdPolicy.canShowInterstitialPure(
            false, false, false, true, ctx(sessionMs = 90_000))
        assertTrue(onTime.allowed)
    }

    /** Industry floor is 180-240s between full-screen ads. */
    @Test fun interstitialGapIs180Seconds() {
        assertEquals(180_000L, AdPolicy.DEFAULT_INTERSTITIAL_GAP_MS)
        val tooSoon = AdPolicy.canShowInterstitialPure(
            false, false, false, true, ctx(lastInterstitialMs = 179_999))
        assertFalse(tooSoon.allowed); assertEquals("gap_not_elapsed", tooSoon.reason)

        val fine = AdPolicy.canShowInterstitialPure(
            false, false, false, true, ctx(lastInterstitialMs = 180_000))
        assertTrue(fine.allowed)
    }

    @Test fun dailyCapIsThree() {
        assertEquals(3, AdPolicy.DEFAULT_INTERSTITIAL_DAILY_CAP)
    }

    /**
     * ⭐ Banners are PERMANENTLY disabled. India banner eCPM is $0.08-0.40 and
     * an accidental click near a bottom nav bar is a real AdMob account-ban
     * risk. There is deliberately NO flag that turns this on, so this test
     * cannot be satisfied by flipping config — it fails if anyone adds a flag.
     */
    @Test fun bannersArePermanentlyDisabled() {
        val policy = AdPolicy(FakeEntitlements(isPro = false), FakeFlags(), FakeUsage(0, 0, 0, 0))
        val d = policy.canShowBanner(ctx())
        assertFalse(d.allowed); assertEquals("banners_permanently_disabled", d.reason)
    }

    @Test fun appOpenDisabledUntilDay90() {
        val policy = AdPolicy(FakeEntitlements(false), FakeFlags(), FakeUsage(0, 0, 0, 0))
        val d = policy.canShowAppOpen(ctx(days = 400))
        assertFalse(d.allowed); assertEquals("app_open_disabled_until_day90", d.reason)
    }

    /** The daily cap needs the persisted counter, so it lives in the class not the pure fn. */
    @Test fun dailyCapEnforcedAtClassLevel() {
        val flags = FakeFlags(interstitialsEnabled = true)
        val policy = AdPolicy(FakeEntitlements(false), flags, FakeUsage(interstitialsToday = 3))
        assertFalse(policy.canShowInterstitial(ctx()).allowed)
        assertEquals("daily_cap", policy.canShowInterstitial(ctx()).reason)

        val ok = AdPolicy(FakeEntitlements(false), flags, FakeUsage(interstitialsToday = 2))
        assertTrue(ok.canShowInterstitial(ctx()).allowed)
    }

    /** End-to-end: a Pro user gets no interstitial even with every flag on and cap unused. */
    @Test fun proUserNeverSeesInterstitialEvenWithAllFlagsOn() {
        val policy = AdPolicy(
            FakeEntitlements(isPro = true),
            FakeFlags(interstitialsEnabled = true),
            FakeUsage(interstitialsToday = 0))
        val d = policy.canShowInterstitial(ctx())
        assertFalse(d.allowed); assertEquals("user_is_pro", d.reason)
    }

    /**
     * Rewarded ads are user-initiated, so a Pro user MAY still watch one (e.g.
     * to unlock a template rather than pay). The limit on rewarded is the VALUE
     * granted, enforced by RewardGuard — not the impression count.
     */
    @Test fun rewardedIsAllowedForProUserBecauseItIsUserInitiated() {
        val policy = AdPolicy(FakeEntitlements(isPro = true), FakeFlags(), FakeUsage(0, 0, 0, 0))
        val d = policy.canShowRewarded(ctx())
        assertTrue("rewarded must stay available to Pro users, got ${d.reason}", d.allowed)
    }

    @Test fun rewardedSuppressedDuringOnboarding() {
        val policy = AdPolicy(FakeEntitlements(false), FakeFlags(), FakeUsage(0, 0, 0, 0))
        assertFalse(policy.canShowRewarded(ctx(onboarding = true)).allowed)
    }

    @Test fun nativeSuppressedForPayersAndNewUsers() {
        val pro = AdPolicy(FakeEntitlements(isPro = true), FakeFlags(nativeEnabled = true), FakeUsage(0, 0, 0, 0))
        assertEquals("user_is_pro", pro.canShowNative(ctx()).reason)

        val fresh = AdPolicy(FakeEntitlements(false), FakeFlags(nativeEnabled = true), FakeUsage(0, 0, 0, 0))
        assertEquals("day0_2_suppression", fresh.canShowNative(ctx(days = 0)).reason)
        assertTrue(fresh.canShowNative(ctx(days = 5)).allowed)
    }

    // ---- Fakes. Kept hand-written: no Mockito on a solo zero-budget project. ----

    // These fakes implement the SEAMS (EntitlementSource / FlagSource), not the
    // real classes: EntitlementManager needs a Context and RemoteFlags needs a
    // FirebaseRemoteConfig, and neither exists on a plain JVM. Both are final, so
    // subclassing them never compiled — the unit tests had literally never been
    // through a real compile before 2026-10-30 (bug #28).
    private class FakeEntitlements(
        isPro: Boolean, isInTrial: Boolean = false, intent: Boolean = false,
    ) : EntitlementSource {
        private val s = EntitlementManager.State(isPro, isInTrial, intent)
        override fun snapshot(): EntitlementManager.State = s
    }

    private class FakeFlags(
        override val interstitialsEnabled: Boolean = false,
        override val firstAdDelayMs: Long = AdPolicy.DEFAULT_FIRST_AD_DELAY_MS,
        override val interstitialGapMs: Long = AdPolicy.DEFAULT_INTERSTITIAL_GAP_MS,
        override val interstitialDailyCap: Int = AdPolicy.DEFAULT_INTERSTITIAL_DAILY_CAP,
        override val rewardedEnabled: Boolean = true,
        override val nativeEnabled: Boolean = false,
    ) : FlagSource

    private class FakeUsage(
        override val interstitialsToday: Int = 0,
        override val rewardedGrantsToday: Int = 0,
        override val exportsToday: Int = 0,
        override val ocrScansToday: Int = 0,
    ) : UsageCounters
}
