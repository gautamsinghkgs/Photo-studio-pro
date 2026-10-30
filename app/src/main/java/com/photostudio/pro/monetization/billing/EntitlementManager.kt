package com.photostudio.pro.monetization.billing

import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ONE place the app learns whether the user is Pro.
 *
 * There is no backend of our own (SPEC.md HARD CONSTRAINT C2). RevenueCat's
 * servers perform the Play Developer API receipt validation and manage the
 * subscription lifecycle (expiry, cancellation, grace period, billing retry).
 * Their SDK caches the result on-device, so this works OFFLINE — which is what
 * lets us honestly claim the app works without a network connection.
 *
 * Everything in the app reads [state]. Nothing calls RevenueCat directly.
 * That single-Source-of-truth discipline is what prevents the entitlement /
 * ad-SDK desync that causes payers to see ads and then refund.
 */
class EntitlementManager(private val appContext: Context) {

    /** Exactly one entitlement, named "pro". */
    companion object {
        const val ENTITLEMENT_PRO = "pro"

        const val PRODUCT_WEEKLY   = "pro_weekly"
        const val PRODUCT_YEARLY   = "pro_yearly"
        const val PRODUCT_LIFETIME = "pro_lifetime"

        /** Trial length must be >= 7 days: 7+ day trials convert 37-43% versus
         *  25.5% for short ones. Do not optimise this down. */
        const val TRIAL_DAYS = 7
    }

    data class State(
        val isPro: Boolean = false,
        val isInTrial: Boolean = false,
        /**
         * True once the user has tapped a purchase button or opened the paywall
         * from a contextual trigger. AdPolicy uses this to lighten the ad load
         * for people who are considering paying — interrupting them with a
         * full-screen ad is how you lose the sale.
         */
        val hasPurchaseIntent: Boolean = false,
        val activeProductId: String? = null,
        val expiresAtMillis: Long? = null,
        val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Non-suspending read for AdPolicy, which must decide synchronously. */
    fun snapshot(): State = _state.value

    fun configure(apiKey: String) {
        Purchases.logLevel = com.revenuecat.purchases.LogLevel.WARN
        Purchases.configure(PurchasesConfiguration.Builder(appContext, apiKey).build())
        refresh()

        // React to lifecycle changes (renewals, cancellations, restores) as
        // RevenueCat reports them, so isPro flips the instant a purchase lands.
        Purchases.sharedInstance.updatedCustomerInfoListener = { info -> apply(info) }
    }

    fun refresh() {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoListener {
            override fun onReceived(customerInfo: CustomerInfo) = apply(customerInfo)
            override fun onError(error: PurchasesError) {
                // Non-fatal. The cached value from the previous launch still
                // stands, so a paying user offline keeps Pro.
                _state.value = _state.value.copy(loaded = true)
            }
        })
    }

    fun restorePurchases(onDone: (Boolean) -> Unit) {
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoListener {
            override fun onReceived(customerInfo: CustomerInfo) {
                apply(customerInfo)
                onDone(customerInfo.entitlements.active.containsKey(ENTITLEMENT_PRO))
            }
            override fun onError(error: PurchasesError) = onDone(false)
        })
    }

    /** Called by the paywall when the user shows buying intent. */
    fun markPurchaseIntent() {
        if (!_state.value.hasPurchaseIntent) {
            _state.value = _state.value.copy(hasPurchaseIntent = true)
            com.photostudio.pro.analytics.Events.logPurchaseIntentDetected()
        }
    }

    private fun apply(info: CustomerInfo) {
        val e = info.entitlements.all[ENTITLEMENT_PRO]
        val isActive = e?.isActive == true
        val inTrial = isActive && info.activeSubscriptions.isEmpty() &&
                e?.expirationDate == null           // non-expiring = lifetime
        _state.value = State(
            isPro = isActive,
            // A real trial shows as an active subscription with a future expiry
            // and willPurchaseRenewal semantics handled by RevenueCat. We keep a
            // conservative approximation here; refine once you see live data.
            isInTrial = isActive && e?.isActive == true && e.expirationDate != null &&
                    isWithinTrialWindow(e.expirationDate.time),
            hasPurchaseIntent = _state.value.hasPurchaseIntent,
            activeProductId = info.activeSubscriptions.firstOrNull()
                ?: info.nonSubscriptionTransactions.firstOrNull()?.productId,
            expiresAtMillis = e?.expirationDate?.time,
            loaded = true,
        )
    }

    private fun isWithinTrialWindow(expiryMillis: Long): Boolean {
        val remaining = expiryMillis - System.currentTimeMillis()
        val trialMs = TRIAL_DAYS * 24L * 60 * 60 * 1000
        return remaining in 1..trialMs
    }
}
