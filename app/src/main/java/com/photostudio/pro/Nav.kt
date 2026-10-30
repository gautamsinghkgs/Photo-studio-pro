package com.photostudio.pro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.photostudio.pro.feature.batch.BatchScreen
import com.photostudio.pro.feature.bgremover.BgRemoverScreen
import com.photostudio.pro.feature.home.HomeScreen
import com.photostudio.pro.feature.idphoto.IdPhotoScreen
import com.photostudio.pro.feature.collage.CollageScreen
import com.photostudio.pro.feature.enhance.EnhanceScreen
import com.photostudio.pro.feature.ocr.OcrScreen
import com.photostudio.pro.feature.pdf.PdfScreen
import com.photostudio.pro.feature.whitebg.WhiteBgScreen
import com.photostudio.pro.feature.onboarding.OnboardingScreen
import com.photostudio.pro.core.util.ServiceLocator
import com.photostudio.pro.feature.paywall.PaywallScreen
import com.photostudio.pro.feature.settings.SettingsScreen

/**
 * Tool ids double as Remote Config kill-switch keys:
 *   tool_enabled_<id>  ->  RemoteFlags.isToolEnabled(id)
 * A broken tool can be hidden instantly without shipping an update.
 */
object Routes {
    const val HOME      = "home"
    const val BGREMOVE  = "bgremove"
    const val IDPHOTO   = "idphoto"
    const val BATCH     = "batch"
    const val PAYWALL   = "paywall/{trigger}"
    const val SETTINGS  = "settings"
    const val ONBOARDING = "onboarding"

    fun paywall(trigger: String) = "paywall/$trigger"

    // All eight tools are implemented. `feature/later/LaterMilestoneScreen.kt` is
    // kept for FUTURE milestones (M13+): it shows an honest "which milestone this
    // lands in" state rather than a dead button or a fake paywall. Do not route to
    // it for a tool that exists.
    const val WHITEBG   = "whitebg"
    const val COLLAGE   = "collage"
    const val PDF       = "pdf"
    const val OCR       = "ocr"
    const val ENHANCE   = "enhance"
}

@Composable
fun PhotoStudioNav(adsReady: Boolean, nav: NavHostController = rememberNavController()) {

    /**
     * Onboarding gate. This is not cosmetic: Prefs.onboardingDone() drives
     * AdPolicy's `never_during_onboarding` check through AdCoordinator, so
     * until this screen runs and calls setOnboardingDone(), NO ad can ever be
     * shown. See OnboardingScreen's class doc.
     *
     * Resolved asynchronously because DataStore reads are suspend calls; we
     * render nothing until the first-launch question is answered, which avoids
     * flashing Home to a new user and avoids a back-stack entry they can
     * return to after finishing onboarding.
     */
    var gate by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { gate = ServiceLocator.prefs.onboardingDone() }

    val start = when (gate) {
        null -> return          // still reading DataStore; render nothing for one frame
        true -> Routes.HOME
        false -> Routes.ONBOARDING
    }

    NavHost(navController = nav, startDestination = start) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                // popUpTo(ONBOARDING){inclusive=true} so Back cannot return here
                onFinish = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onSeePaywall = { nav.navigate(Routes.paywall("onboarding")) },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(adsReady = adsReady, onNavigate = { nav.navigate(it) })
        }

        composable(Routes.BGREMOVE) {
            BgRemoverScreen(adsReady = adsReady, onBack = { nav.popBackStack() },
                            onGoPro = { nav.navigate(Routes.paywall("bgremove_export")) })
        }

        composable(Routes.IDPHOTO) {
            IdPhotoScreen(adsReady = adsReady, onBack = { nav.popBackStack() },
                          onGoPro = { nav.navigate(Routes.paywall("idphoto_country")) })
        }

        composable(Routes.BATCH) {
            BatchScreen(adsReady = adsReady, onBack = { nav.popBackStack() },
                        onGoPro = { nav.navigate(Routes.paywall("batch_limit")) })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() },
                           onGoPro = { nav.navigate(Routes.paywall("settings")) })
        }

        composable(Routes.PAYWALL) { entry ->
            val trigger = entry.arguments?.getString("trigger") ?: "generic"
            PaywallScreen(trigger = trigger, onDismiss = { nav.popBackStack() })
        }

        // ---- M6-M10: all eight tools are now real ----
        composable(Routes.WHITEBG) {
            WhiteBgScreen(adsReady = adsReady, onBack = { nav.popBackStack() },
                          onGoPro = { nav.navigate(Routes.paywall("whitebg_export")) })
        }
        composable(Routes.COLLAGE) {
            CollageScreen(adsReady = adsReady, onBack = { nav.popBackStack() },
                          onGoPro = { nav.navigate(Routes.paywall("collage_template")) })
        }
        composable(Routes.PDF) {
            PdfScreen(adsReady = adsReady, onBack = { nav.popBackStack() },
                      onGoPro = { nav.navigate(Routes.paywall("pdf_limit")) })
        }
        composable(Routes.OCR) {
            OcrScreen(adsReady = adsReady, onBack = { nav.popBackStack() },
                      onGoPro = { nav.navigate(Routes.paywall("ocr_limit")) })
        }
        composable(Routes.ENHANCE) {
            EnhanceScreen(adsReady = adsReady, onBack = { nav.popBackStack() },
                          onGoPro = { nav.navigate(Routes.paywall("enhance_export")) })
        }
    }
}
