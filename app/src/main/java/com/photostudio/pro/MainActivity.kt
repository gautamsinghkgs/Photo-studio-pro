package com.photostudio.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.photostudio.pro.core.ui.PhotoStudioTheme
import com.photostudio.pro.core.util.ServiceLocator
import com.photostudio.pro.monetization.ads.UmpConsent

/**
 * Single-activity Compose host.
 *
 * UMP consent is requested BEFORE any ad initialisation, and the app is never
 * blocked on it — if consent cannot be obtained we proceed and AdMob serves
 * non-personalised ads or nothing. Blocking here would cost more revenue than
 * the compliance it buys.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            PhotoStudioTheme {
                var adsReady by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    UmpConsent.request(
                        activity = this@MainActivity,
                        // Set true in a DEBUG build on a non-EEA network to see the
                        // consent form while developing. Never true in release.
                        debugGeographyEea = BuildConfig.DEBUG,
                    ) {
                        ServiceLocator.adManager.initialise()
                        adsReady = true
                    }
                }
                PhotoStudioNav(adsReady = adsReady)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Entitlements can change while we were backgrounded (a subscription
        // renewed, or the user cancelled in Play settings).
        ServiceLocator.entitlements.refresh()
    }
}
