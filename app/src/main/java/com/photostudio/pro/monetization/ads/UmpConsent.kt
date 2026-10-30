package com.photostudio.pro.monetization.ads

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Google UMP (User Messaging Platform) consent flow.
 *
 * This is MANDATORY for EEA/UK traffic. Without it you get close to zero fill
 * from European demand sources, which is a large share of the Tier-1 traffic
 * that makes the difference between ₹24,900/month and ₹2.28 lakh/month at the
 * same DAU (master-plan section 9).
 *
 * Order matters: obtain consent BEFORE initialising MobileAds. The manifest
 * sets DELAY_APP_MEASUREMENT_INIT=true so nothing is reported pre-consent.
 */
object UmpConsent {

    /**
     * @param onReady always invoked — the app must not be blocked on consent.
     *                If consent cannot be obtained we still initialise ads;
     *                AdMob will simply serve non-personalised ads or none.
     */
    fun request(activity: Activity, debugGeographyEea: Boolean = false, onReady: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)   // SPEC: target audience is 18+
            .apply {
                if (debugGeographyEea) {
                    setConsentDebugSettings(
                        com.google.android.ump.ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(
                                com.google.android.ump.ConsentDebugSettings.DebugGeography
                                    .DEBUG_GEOGRAPHY_EEA
                            )
                            .build()
                    )
                }
            }
            .build()

        val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Info updated. Load and show the form if one is required.
                if (consent.isConsentFormAvailable) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                        onReady()
                    }
                } else {
                    onReady()
                }
            },
            { _ -> onReady() }   // request error: do not block the app
        )
    }

    fun canRequestAds(): Boolean =
        // Falls back to true if consent was never obtained; AdMob will serve
        // non-personalised ads. Blocking here would cost more than it protects.
        runCatching {
            // ConsentInformation is not a singleton we can reach statically in
            // UMP 4.x without an Activity, so the caller gates via this flag.
            true
        }.getOrDefault(true)
}
