package com.photostudio.pro.monetization.ads

import com.photostudio.pro.BuildConfig

/**
 * Ad unit ids.
 *
 * ⚠️ DEBUG builds use Google's official SAMPLE ad units. They render real
 * looking ads but generate no revenue and, critically, cannot cause an
 * accidental-click policy violation while you and your testers are clicking
 * around. This switch is driven by BuildConfig.DEBUG so it can never be
 * forgotten. SPEC.md section 13.
 *
 * TODO(you): replace the release ids with your own from
 * AdMob → Apps → your app → Ad units. YOUR-TURN.md step 7.
 */
object AdUnitIds {

    /** Google's official sample INTERSTITIAL id. */
    private const val SAMPLE_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    /** Google's official sample REWARDED id. */
    private const val SAMPLE_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    /** Google's official sample NATIVE id. */
    private const val SAMPLE_NATIVE = "ca-app-pub-3940256099942544/2247696110"

    val interstitial: String = if (BuildConfig.DEBUG) SAMPLE_INTERSTITIAL else "ca-app-pub-0000000000000000/0000000000"
    val rewarded: String     = if (BuildConfig.DEBUG) SAMPLE_REWARDED     else "ca-app-pub-0000000000000000/0000000000"
    val native: String       = if (BuildConfig.DEBUG) SAMPLE_NATIVE       else "ca-app-pub-0000000000000000/0000000000"
}
