package com.photostudio.pro

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.photostudio.pro.analytics.Events
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Initialisation order matters:
 *  1. ServiceLocator (pure local objects, no network)
 *  2. Events sink -> Firebase Analytics
 *  3. RemoteFlags.fetchAsync() (async, defaults are already safe)
 *  4. EntitlementManager.configure() (reads cached entitlements first, so a
 *     paying user is Pro before any UI renders)
 *  5. Ads: UMP consent is obtained in MainActivity BEFORE MobileAds.initialize,
 *     because the manifest sets DELAY_APP_MEASUREMENT_INIT=true.
 *
 * ⚠️ There is no backend to initialise. No Cloud Functions, no Workers client,
 * no Retrofit instance. SPEC.md HARD CONSTRAINT C2.
 */
class PhotoStudioApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        ServiceLocator.init(this)

        val analytics = runCatching { FirebaseAnalytics.getInstance(this) }.getOrNull()
        Events.sink = { name, params ->
            val bundle = android.os.Bundle()
            params.forEach { (k, v) ->
                when (v) {
                    is Boolean -> bundle.putBoolean(k, v)
                    is Int     -> bundle.putInt(k, v)
                    is Long    -> bundle.putLong(k, v)
                    is String  -> bundle.putString(k, v)
                    null       -> { /* never log nulls */ }
                    else       -> bundle.putString(k, v.toString())
                }
            }
            analytics?.logEvent(name, bundle)
        }

        appScope.launch {
            ServiceLocator.flags.fetchAsync()
            // Load the cached counters so AdPolicy can decide synchronously from
            // the first frame onward.
            ServiceLocator.usage.refresh()
            ServiceLocator.prefs.ensureInstallTimestamp()
            ServiceLocator.prefs.markSessionStart()
        }

        // RevenueCat's public SDK key. This is NOT a secret — RevenueCat
        // documents that the public key is safe to embed in the client. There is
        // still no private key anywhere in this project.
        ServiceLocator.entitlements.configure(BuildConfig.REVENUECAT_PUBLIC_KEY)

        // PdfBox-Android needs its resources loaded once.
        runCatching { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext) }
    }
}
