package com.photostudio.pro.feature.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photostudio.pro.R
import com.photostudio.pro.core.ui.NoticeCard
import com.photostudio.pro.core.util.ServiceLocator
import com.photostudio.pro.monetization.billing.EntitlementManager
import com.photostudio.pro.monetization.billing.PurchaseFlow
import androidx.compose.material3.LinearProgressIndicator
// LocalActivity comes from activity-compose, NOT from compose.ui.platform (bug #24).
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * SOFT-HARD HYBRID paywall.
 *
 * Hard paywalls convert far better at Day 35 (10.7% vs 2.1%) but a cold paywall
 * with no escape destroys the ad inventory in low-ARPU markets like India.
 * So: a prominent trial CTA, and a clearly visible "continue with the free
 * version" escape into the ad-supported tier. master-plan section 8.7.
 *
 * Trial must be >= 7 days: 7+ day trials convert 37-43% versus 25.5% for short
 * ones. This is configured in Play Console, not here — but the copy assumes 7.
 *
 * ⚠️ PLAY POLICY: the price, the billing period, the auto-renewal, and the
 * cancellation path must ALL be visible. [PaywallScreen] renders the legal
 * block from strings.xml -> paywall_terms. Do not remove it to make the
 * paywall "cleaner".
 */
@Composable
fun PaywallScreen(trigger: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val entitlements by ServiceLocator.entitlements.state.collectAsState()
    var selected by remember { mutableStateOf(EntitlementManager.PRODUCT_YEARLY) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val offers = remember { mutableStateListOf<PurchaseFlow.Offer>() }
    val activity = LocalActivity.current

    LaunchedEffect(trigger) {
        ServiceLocator.entitlements.markPurchaseIntent()
        com.photostudio.pro.analytics.Events.logPaywallShown(trigger, ServiceLocator.flags.paywallVariant)
        // Fetch LOCALISED prices from Google Play. Never hardcoded — a wrong
        // price on a paywall is a Play policy violation.
        PurchaseFlow.loadOffers { loaded ->
            offers.clear(); offers.addAll(loaded)
            // Default to the yearly anchor if it exists; it converts best.
            loaded.firstOrNull { it.productId == EntitlementManager.PRODUCT_YEARLY }
                ?.let { selected = it.productId }
                ?: loaded.firstOrNull()?.let { selected = it.productId }
        }
    }

    // Already Pro (e.g. a purchase completed while this screen was open): leave.
    LaunchedEffect(entitlements.isPro) { if (entitlements.isPro) onDismiss() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.paywall_title),
                 style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.paywall_subtitle),
                 color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(4.dp))
            listOf(
                stringResource(R.string.paywall_benefit_1),
                stringResource(R.string.paywall_benefit_2),
                stringResource(R.string.paywall_benefit_3),
                stringResource(R.string.paywall_benefit_4),
                stringResource(R.string.paywall_benefit_5),
                stringResource(R.string.paywall_benefit_6),
            ).forEach { Benefit(it) }

            Spacer(Modifier.height(6.dp))

            if (offers.isEmpty()) {
                // Offerings not configured in RevenueCat yet, or still loading.
                // Buttons stay DISABLED. Showing a placeholder price like
                // "$34.99" would be a wrong price, which is a policy violation.
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.paywall_loading_prices), fontSize = 12.sp,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            offers.forEach { o ->
                val isAnchor = o.productId == EntitlementManager.PRODUCT_YEARLY
                OptionCard(
                    id = o.productId,
                    title = titleFor(o.productId),
                    sub = subtitleFor(o),
                    price = o.priceFormatted,
                    period = o.period,
                    selected = selected == o.productId,
                    onClick = { selected = o.productId },
                    highlight = isAnchor,
                )
            }

            // Prices are read from the Play Store listing at runtime via
            // RevenueCat offerings, never hardcoded, so currency and tax are
            // always correct for the user's country. Until offerings are
            // configured the buttons stay disabled rather than showing a wrong
            // price — showing a wrong price is a Play policy violation.
            val chosen = offers.firstOrNull { it.productId == selected }
            Button(
                onClick = {
                    val a = activity ?: return@Button
                    val o = chosen ?: return@Button
                    busy = true; message = null
                    PurchaseFlow.purchase(a, o) { res ->
                        busy = false
                        when (res) {
                            // Success: the LaunchedEffect on isPro dismisses us.
                            is PurchaseFlow.Result.Success -> { }
                            // The user backed out of the Play sheet. This is NOT
                            // an error — no toast, no red text, nothing.
                            is PurchaseFlow.Result.UserCancelled -> {
                                com.photostudio.pro.analytics.Events
                                    .logPaywallDismissed("${trigger}_cancelled")
                            }
                            is PurchaseFlow.Result.AlreadyOwned -> {
                                message = "This purchase is already on your account. " +
                                          "Tap Restore below if Pro is not showing."
                            }
                            is PurchaseFlow.Result.Error -> {
                                message = "Could not complete the purchase (${res.code}). " +
                                          "You have not been charged."
                            }
                        }
                    }
                },
                // Disabled until real localised prices have loaded.
                enabled = !busy && chosen != null,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    when {
                        busy -> stringResource(R.string.paywall_opening_play)
                        chosen == null -> stringResource(R.string.paywall_loading)
                        chosen.hasTrial && !chosen.isOneTime ->
                            stringResource(R.string.paywall_start_trial, EntitlementManager.TRIAL_DAYS)
                        chosen.isOneTime -> stringResource(R.string.paywall_buy_lifetime, chosen.priceFormatted)
                        else -> stringResource(R.string.paywall_subscribe, chosen.priceFormatted)
                    },
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                )
            }
            message?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                     modifier = Modifier.fillMaxWidth())
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                // ⭐ THE ESCAPE HATCH. Removing this line is how apps destroy
                // their ad inventory in low-ARPU markets.
                Text(stringResource(R.string.paywall_continue_free), fontSize = 14.sp)
            }
            TextButton(onClick = {
                ServiceLocator.entitlements.restorePurchases { ok ->
                    if (!ok) com.photostudio.pro.analytics.Events.logPaywallDismissed("restore_failed")
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.paywall_restore), fontSize = 13.sp)
            }

            NoticeCard(stringResource(R.string.paywall_terms))
            Text(
                stringResource(R.string.paywall_prices_note),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun titleFor(productId: String) = when (productId) {
    EntitlementManager.PRODUCT_WEEKLY   -> stringResource(R.string.paywall_weekly)
    EntitlementManager.PRODUCT_YEARLY   -> stringResource(R.string.paywall_yearly)
    EntitlementManager.PRODUCT_LIFETIME -> stringResource(R.string.paywall_lifetime)
    else -> productId
}

@Composable
private fun subtitleFor(o: PurchaseFlow.Offer) = when {
    o.hasTrial && !o.isOneTime -> stringResource(R.string.paywall_offer_best, EntitlementManager.TRIAL_DAYS)
    o.isOneTime                -> stringResource(R.string.paywall_offer_once)
    else                       -> stringResource(R.string.paywall_offer_trial, EntitlementManager.TRIAL_DAYS)
}

/*
 The purchase flow now lives in monetization/billing/PurchaseFlow.kt — one file,
 isolated, so that if the RevenueCat API has shifted by the time you build, only
 that file needs fixing. The original note is kept because it still applies:

 Wire this up in YOUR-TURN.md step 8 using RevenueCat's official sample:
   https://www.revenuecat.com/docs/getting-started/entitlements

 The shape is:
   1. Purchases.sharedInstance.getOfferings { offerings, error -> ... }
   2. pick offerings.current.availablePackages by packageProduct.identifier
   3. read the LOCALISED price from package.storeProduct.price.formatted
      and render it on the OptionCards above (never hardcode "$34.99")
   4. Purchases.sharedInstance.purchase(PurchaseParams.Builder(activity, pkg).build())
   5. on success -> EntitlementManager.refresh() -> isPro flips -> onDismiss()
   6. on userCancelled -> do nothing, do not show an error

 Test with PLAY CONSOLE LICENCE TESTING only. Never make a real purchase while
 developing, and never test with a card you care about.
*/

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✓", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(start = 10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OptionCard(id: String, title: String, sub: String, price: String, period: String,
                       selected: Boolean, onClick: () -> Unit, highlight: Boolean) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface),
        border = if (highlight) androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(sub, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                // From storeProduct.price.formatted — localised by Google Play,
                // includes tax, correct for the user's country.
                Text(price, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(period, fontSize = 10.5.sp,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
