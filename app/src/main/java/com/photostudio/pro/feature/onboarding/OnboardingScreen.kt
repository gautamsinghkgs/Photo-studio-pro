package com.photostudio.pro.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photostudio.pro.R
import com.photostudio.pro.core.ui.PrimaryAction
import com.photostudio.pro.core.util.ServiceLocator
import kotlinx.coroutines.launch

/**
 * First-run onboarding: 3 honest slides, then the paywall, then into the app.
 *
 * ⚠️ WHY THIS FILE MATTERS MORE THAN IT LOOKS
 *
 * `Prefs.onboardingDone()` gates EVERY ad decision — AdCoordinator passes
 * `isOnboarding = !prefs.onboardingDone()` into AdPolicy, and AdPolicy returns
 * `never_during_onboarding` for any ad while that is true. This screen is the
 * ONLY caller of `setOnboardingDone()`. Before it existed there was no caller
 * at all, which meant the flag stayed false forever and **no ad could ever
 * show, on any device, for any user** — a total silent revenue loss that would
 * have looked like "AdMob isn't filling".
 *
 * The flag is also set on the skip path, so a user who skips still gets ads.
 *
 * Structure follows master-plan 8.7: soft-hard hybrid. The paywall appears
 * AFTER value has been communicated, and always with a visible free escape.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, onSeePaywall: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { SLIDES.size })

    /** Marks onboarding complete exactly once, whichever path the user takes. */
    fun complete() {
        scope.launch {
            ServiceLocator.prefs.setOnboardingDone()
            onFinish()
        }
    }

    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.height(32.dp))

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val s = SLIDES[page]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(s.emoji, fontSize = 64.sp)
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(s.title), style = MaterialTheme.typography.headlineSmall,
                         fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(s.body), style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         textAlign = TextAlign.Center, lineHeight = 22.sp)
                }
            }

            // Page dots
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                repeat(SLIDES.size) { i ->
                    Box(Modifier.padding(4.dp).size(if (pager.currentPage == i) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant))
                }
            }

            Spacer(Modifier.height(20.dp))

            if (pager.currentPage < SLIDES.size - 1) {
                PrimaryAction(stringResource(R.string.onboarding_next)) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            } else {
                // Last slide: show the paywall. The escape hatch inside the
                // paywall is what keeps the ad-supported tier populated.
                PrimaryAction(stringResource(R.string.onboarding_see_pro)) { onSeePaywall() }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { complete() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.paywall_continue_free))
                }
            }

            // Skip must ALSO mark onboarding done, or a skipping user is stuck
            // in the "never show ads" state forever.
            if (pager.currentPage < SLIDES.size - 1) {
                TextButton(onClick = { complete() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_skip), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Deliberately only three slides. Every extra slide costs installs: the
 * paywall/first-value moment must arrive inside the first minute, which is
 * also the window in which AdPolicy suppresses ads (first_ad_delay = 90s).
 *
 * Slide copy states what the app actually does. Nothing here may claim
 * "100% offline" — see SPEC.md and master-plan 6.4 for why.
 */
private data class Slide(val emoji: String, val title: Int, val body: Int)

private val SLIDES = listOf(
    Slide("✂️", R.string.onb_1_title, R.string.onb_1_body),
    Slide("🔒", R.string.onb_2_title, R.string.onb_2_body),
    Slide("♾️", R.string.onb_3_title, R.string.onb_3_body),
)
