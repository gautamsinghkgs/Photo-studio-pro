package com.photostudio.pro.core.util

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.photostudio.pro.core.data.local.Prefs
import com.photostudio.pro.core.data.repository.PrefsUsageCounters
import com.photostudio.pro.feature.idphoto.CountryRepository
import com.photostudio.pro.monetization.ads.AdCoordinator
import com.photostudio.pro.monetization.ads.AdManager
import com.photostudio.pro.monetization.ads.AdPolicy
import com.photostudio.pro.monetization.billing.EntitlementManager
import com.photostudio.pro.monetization.config.RemoteFlags

/**
 * Deliberately a hand-written locator instead of Hilt or Koin.
 *
 * Reason: fewer build-time annotation processors means fewer ways for a first
 * build to fail, and this app has about eight singletons. SPEC.md C3 favours
 * simplicity; a DI framework here buys nothing and costs a KSP round plus a
 * version-compatibility matrix.
 */
object ServiceLocator {

    lateinit var appContext: Context; private set
    lateinit var prefs: Prefs; private set
    lateinit var usage: PrefsUsageCounters; private set
    lateinit var flags: RemoteFlags; private set
    lateinit var entitlements: EntitlementManager; private set
    lateinit var adPolicy: AdPolicy; private set
    lateinit var adManager: AdManager; private set
    lateinit var adCoordinator: AdCoordinator; private set
    lateinit var countries: CountryRepository; private set

    private var initialised = false

    @Synchronized
    fun init(context: Context) {
        if (initialised) return
        appContext = context.applicationContext
        prefs = Prefs(appContext)
        usage = PrefsUsageCounters(prefs)
        flags = RemoteFlags(FirebaseRemoteConfig.getInstance())
        entitlements = EntitlementManager(appContext)
        adPolicy = AdPolicy(entitlements, flags, usage)
        adManager = AdManager(appContext)
        adCoordinator = AdCoordinator(prefs, usage, adPolicy, adManager, entitlements)
        countries = CountryRepository(appContext)
        initialised = true
    }
}
