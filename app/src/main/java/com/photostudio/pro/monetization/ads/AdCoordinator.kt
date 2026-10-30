package com.photostudio.pro.monetization.ads

import android.app.Activity
import com.photostudio.pro.core.data.local.Prefs
import com.photostudio.pro.core.data.repository.PrefsUsageCounters
import com.photostudio.pro.monetization.billing.EntitlementManager

/**
 * The ONLY correct way to show an interstitial.
 *
 * Every export/save flow calls [afterExportCompleted]. This class:
 *   1. builds a truthful AdContext from persisted counters and timestamps,
 *   2. asks AdPolicy (never decides itself),
 *   3. shows the ad only if the Decision is allowed,
 *   4. ALWAYS calls onFinished, so the user's flow continues whether an ad
 *      showed or not.
 *
 * Nothing else in the app may touch AdManager.showInterstitial directly.
 * SPEC.md architectural rule #1 — routing every decision through AdPolicy is
 * what stops a paying user from seeing an ad after a desync, which is the #2
 * revenue killer and a direct cause of refunds.
 */
class AdCoordinator(
    private val prefs: Prefs,
    private val usage: PrefsUsageCounters,
    private val policy: AdPolicy,
    private val manager: AdManager,
    private val entitlements: EntitlementManager,
) {

    /**
     * @param isOnboarding true while the first-run onboarding flow is active.
     *        Full-screen ads are suppressed for the whole of Day 0-2 regardless,
     *        but this also catches the case where the user is still onboarding
     *        days after install.
     */
    suspend fun afterExportCompleted(
        activity: Activity,
        isOnboarding: Boolean = false,
        onFinished: () -> Unit,
    ) {
        // Refresh the counters first: a stale cache would let a second
        // interstitial through inside the same gap window.
        usage.refresh()
        val ctx = AdPolicy.AdContext(
            isAppOpen = false,
            isOnboarding = isOnboarding || !prefs.onboardingDone(),
            isNaturalBreakPoint = true,                 // we are literally after an export
            daysSinceInstall = prefs.daysSinceInstall(),
            msSinceSessionStart = prefs.msSinceSessionStart(),
            msSinceLastInterstitial = prefs.msSinceLastInterstitial(),
        )

        val decision = policy.canShowInterstitial(ctx)
        if (!decision.allowed) { onFinished(); return }

        manager.showInterstitial(activity, decision) {
            // Count it only after it actually showed, so a failed show does not
            // consume the user's daily allowance.
            prefs.noteInterstitialShown()
            usage.refresh()
            onFinished()
        }
    }

    /**
     * Rewarded flow with RewardGuard enforced on the GRANT side.
     *
     * There is no SSV endpoint (SPEC.md section 8 / master-plan 5.2), so the
     * reward comes from the client callback. We therefore limit the VALUE a
     * forger can extract: a hard daily cap, and only small temporary rewards.
     */
    suspend fun requestRewarded(
        activity: Activity,
        kind: RewardGuard.RewardKind,
        onGranted: (RewardGuard.RewardKind) -> Unit,
        onDenied: (String) -> Unit,
        onError: () -> Unit,
    ) {
        usage.refresh()
        if (!RewardGuard.canGrant(usage.rewardedGrantsToday, /* cap read from flags */ capFromFlags())) {
            onDenied("daily_cap")
            com.photostudio.pro.analytics.Events.logRewardDenied("daily_cap")
            return
        }

        val ctx = AdPolicy.AdContext(
            isOnboarding = !prefs.onboardingDone(),
            isNaturalBreakPoint = true,
            daysSinceInstall = prefs.daysSinceInstall(),
            msSinceSessionStart = prefs.msSinceSessionStart(),
            msSinceLastInterstitial = prefs.msSinceLastInterstitial(),
        )
        val decision = policy.canShowRewarded(ctx)
        if (!decision.allowed) { onDenied(decision.reason); return }

        manager.showRewarded(
            activity = activity,
            decision = decision,
            onEarned = {
                prefs.noteRewardGranted()
                com.photostudio.pro.analytics.Events.logRewardGranted(kind.name)
                onGranted(kind)
            },
            onCancelled = { onDenied("user_cancelled") },
            onError = { onError() },
        )
    }

    private fun capFromFlags(): Int = runCatching {
        com.photostudio.pro.core.util.ServiceLocator.flags.rewardedDailyCap
    }.getOrDefault(RewardGuard.DEFAULT_DAILY_CAP)
}
