package com.photostudio.pro.monetization.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.photostudio.pro.BuildConfig
import com.photostudio.pro.analytics.Events

/**
 * Ad load/show lifecycle. This class NEVER decides whether an ad may show —
 * that is AdPolicy's job. Callers must hold an allowed Decision before calling
 * showInterstitial(). SPEC.md architectural rule #1.
 */
class AdManager(private val appContext: Context) {

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false

    fun initialise() {
        // ⭐ TEST MODE IS DRIVEN BY BuildConfig.DEBUG, NEVER BY A MANUAL TOGGLE.
        // Clicking your own live ads — or asking testers to — is a PERMANENT
        // AdMob account ban. Keeping the whole closed-testing period in test
        // mode is non-negotiable. SPEC.md section 13.
        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(TEST_DEVICE_ID_PLACEHOLDER))
                    .build()
            )
        }
        MobileAds.initialize(appContext) { }
        preloadInterstitial()
        preloadRewarded()
    }

    /**
     * TODO(you): put your real device id here so YOUR device also gets test ads
     * in debug builds. Logcat prints it the first time you request an ad:
     *   "To get test ads on this device, set RequestConfiguration... testDeviceIds"
     * Only add ids for devices you physically own. Never ship a release build
     * with test ids affecting real users (BuildConfig.DEBUG already guarantees
     * this).
     */
    private val TEST_DEVICE_ID_PLACEHOLDER = "YOUR_DEBUG_DEVICE_ID_FROM_LOGCAT"

    // ---------------- Interstitial ----------------

    fun preloadInterstitial() {
        if (interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            appContext,
            AdUnitIds.interstitial,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad; loadingInterstitial = false
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    interstitial = null; loadingInterstitial = false
                    Events.logAdFailed("interstitial", e.code)
                }
            }
        )
    }

    /**
     * @param decision MUST be an allowed Decision from AdPolicy.canShowInterstitial().
     * @param onDismissed always called, whether an ad showed or not, so the
     *                    caller can continue the user's flow either way.
     */
    fun showInterstitial(activity: Activity, decision: AdPolicy.Decision, onDismissed: () -> Unit) {
        if (!decision.allowed) { onDismissed(); return }          // honour the policy
        val ad = interstitial
        if (ad == null) { onDismissed(); preloadInterstitial(); return }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                onDismissed()
                preloadInterstitial()
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                interstitial = null
                Events.logAdFailed("interstitial_show", e.code)
                onDismissed()
                preloadInterstitial()
            }
            override fun onAdShowedFullScreenContent() {
                Events.logAdShown("interstitial", "after_export")
            }
        }
        ad.show(activity)
    }

    // ---------------- Rewarded ----------------

    fun preloadRewarded() {
        if (rewarded != null || loadingRewarded) return
        loadingRewarded = true

        // SSV options are set so that Phase 2 can add a verification endpoint
        // WITHOUT an app update to the ad-request side. Today there is no
        // endpoint configured in the AdMob dashboard, so nothing is called.
        // The userId is a random local UUID — no personal data (SPEC.md 11).
        val ssv = ServerSideVerificationOptions.Builder()
            .setUserId(LocalIdentity.anonymousId(appContext))
            .build()

        RewardedAd.load(
            appContext,
            AdUnitIds.rewarded,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.setServerSideVerificationOptions(ssv)
                    rewarded = ad; loadingRewarded = false
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    rewarded = null; loadingRewarded = false
                    Events.logAdFailed("rewarded", e.code)
                }
            }
        )
    }

    /**
     * ⭐ FAIL-OPEN REWARD LOGIC.
     *
     * The reward is granted from the CLIENT callback (onUserEarnedReward),
     * because there is no SSV endpoint. If the ad is dismissed without the
     * reward firing, we do NOT grant — that is the one honest denial.
     *
     * The abuse cap is enforced on the GRANT side by RewardGuard, not here.
     */
    fun showRewarded(
        activity: Activity,
        decision: AdPolicy.Decision,
        onEarned: () -> Unit,
        onCancelled: () -> Unit,
        onError: () -> Unit,
    ) {
        if (!decision.allowed) { onCancelled(); return }
        val ad = rewarded
        if (ad == null) { onError(); preloadRewarded(); return }

        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                if (!earned) onCancelled()
                preloadRewarded()
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                rewarded = null
                Events.logAdFailed("rewarded_show", e.code)
                onError()
                preloadRewarded()
            }
            override fun onAdShowedFullScreenContent() {
                Events.logAdShown("rewarded", "user_initiated")
            }
        }
        ad.show(activity) { item: RewardItem ->
            earned = true
            onEarned()
        }
    }
}
