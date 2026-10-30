package com.photostudio.pro.monetization.billing

import android.app.Activity
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.purchaseWith
import com.photostudio.pro.analytics.Events
import com.photostudio.pro.core.util.ServiceLocator

/**
 * The RevenueCat purchase flow, in ONE place.
 *
 * ⚠️ WHY THIS IS A SEPARATE FILE RATHER THAN INLINE IN THE PAYWALL
 *
 * Play Billing is the single most error-prone area of a subscription app, and the
 * area where AI-generated code is least reliable — the RevenueCat API surface has
 * changed shape several times (callbacks, then `awaitX` coroutines, then `*With`
 * scope extensions) and training data mixes all of them. Keeping it isolated means:
 *
 *   • If the API has moved by the time you build, only THIS file needs fixing.
 *   • Nothing else in the app touches `Purchases.sharedInstance` except
 *     EntitlementManager, so a mistake cannot leak into the tools.
 *   • It is the one file you should read against the official docs before
 *     shipping: https://www.revenuecat.com/docs/getting-started/entitlements
 *
 * THE THREE RULES THIS FILE ENFORCES
 *
 * 1. **Never hardcode a price.** Prices come from `storeProduct.price.formatted`,
 *    which Google Play localises per country and includes tax. A wrong price on a
 *    paywall is a Play policy violation, not just a UX bug.
 * 2. **A cancelled purchase is not an error.** Showing "purchase failed" when the
 *    user simply backed out of the Play sheet generates support emails and bad
 *    reviews. `userCancelled` is handled as its own outcome.
 * 3. **The entitlement is read back from RevenueCat, never assumed.** After a
 *    successful purchase we refresh and let `EntitlementManager.state` drive the
 *    UI, so there is exactly one source of truth for "is this user Pro" — which is
 *    what stops the ad/entitlement desync that shows paying users ads.
 */
object PurchaseFlow {

    /** One purchasable option, with its LOCALISED price already formatted. */
    data class Offer(
        val productId: String,
        val rcPackage: Package,
        /** e.g. "₹999", "$34.99" — already localised by Google Play. */
        val priceFormatted: String,
        /** e.g. "per year", "per week". Empty for one-time products. */
        val period: String,
        val isOneTime: Boolean,
        /** True when this product carries an introductory free trial. */
        val hasTrial: Boolean,
        /** Raw price in micros, for sorting/anchoring logic. */
        val priceMicros: Long,
    )

    sealed interface Result {
        /** Purchase succeeded and the entitlement is now active. */
        data object Success : Result
        /** User backed out of the Play sheet. NOT an error — do not show a toast. */
        data object UserCancelled : Result
        /** Already owned / pending from an earlier attempt. */
        data object AlreadyOwned : Result
        data class Error(val message: String, val code: String) : Result
    }

    /**
     * Load the current offering and map it to the three product ids the app uses.
     *
     * Returns an empty list if offerings are not configured yet in the RevenueCat
     * dashboard — in that case the paywall keeps its buttons DISABLED rather than
     * showing a placeholder price, because a wrong price is a policy violation.
     */
    fun loadOffers(onResult: (List<Offer>) -> Unit) {
        runCatching {
            Purchases.sharedInstance.getOfferingsWith(
                onError = { e ->
                    Events.logMlKitError("offerings_fetch", e.code.name)
                    onResult(emptyList())
                },
                onSuccess = { offerings ->
                    onResult(mapOffering(offerings.current))
                },
            )
        }.onFailure {
            // RevenueCat not configured, or the SDK key is still the placeholder.
            onResult(emptyList())
        }
    }

    private fun mapOffering(offering: Offering?): List<Offer> {
        val pkgs = offering?.availablePackages ?: return emptyList()
        return pkgs.mapNotNull { p ->
            // RevenueCat 6.5.2: `Package.product` (javap-verified). There is no
            // `storeProduct` property on Package — that name exists on the older
            // PurchasesError/store types and training data mixes them up (bug #27).
            val sp = p.product
            val id = sp.id
            // Only surface the three products this app actually defines. An
            // unexpected id means someone edited the dashboard without editing the
            // app — better to hide it than to sell something the app cannot
            // describe.
            if (id !in setOf(EntitlementManager.PRODUCT_WEEKLY,
                             EntitlementManager.PRODUCT_YEARLY,
                             EntitlementManager.PRODUCT_LIFETIME)) return@mapNotNull null
            Offer(
                productId = id,
                rcPackage = p,
                priceFormatted = sp.price.formatted,
                period = periodLabel(id),
                isOneTime = id == EntitlementManager.PRODUCT_LIFETIME,
                hasTrial = runCatching {
                    sp.subscriptionOptions?.defaultOffer?.freePhase != null
                }.getOrDefault(false),
                // StoreProduct has no priceAmountMicros; the raw value lives on
                // Price.amountMicros (javap-verified, bug #27).
                priceMicros = sp.price.amountMicros,
            )
        }.sortedByDescending { it.priceMicros }   // anchor first: yearly/lifetime above weekly
    }

    private fun periodLabel(productId: String) = when (productId) {
        EntitlementManager.PRODUCT_WEEKLY   -> "per week"
        EntitlementManager.PRODUCT_YEARLY   -> "per year"
        EntitlementManager.PRODUCT_LIFETIME -> "one time"
        else -> ""
    }

    /**
     * Launch the Play billing sheet for one offer.
     *
     * On success we refresh EntitlementManager so `isPro` flips from the same
     * StateFlow every other screen already observes.
     */
    fun purchase(activity: Activity, offer: Offer, onResult: (Result) -> Unit) {
        runCatching {
            Purchases.sharedInstance.purchaseWith(
                PurchaseParams.Builder(activity, offer.rcPackage).build(),
                // 6.5.2 signature: onError: (PurchasesError, userCancelled: Boolean) -> Unit.
                // A one-parameter lambda here is a compile error; the second arg is
                // real information (a cancel is not a failure) but PurchaseFlow maps
                // both to Result.UserCancelled/Error via mapError, so ignore it.
                onError = { e, _ -> onResult(mapError(e)) },
                onSuccess = { _, customerInfo ->
                    val active = customerInfo.entitlements[EntitlementManager.ENTITLEMENT_PRO]?.isActive == true
                    ServiceLocator.entitlements.refresh()
                    if (active) {
                        Events.logTrialStarted(offer.productId)
                        onResult(Result.Success)
                    } else {
                        // Purchased but not entitled: almost always a pending
                        // transaction (Play can defer a purchase for approval).
                        onResult(Result.AlreadyOwned)
                    }
                },
            )
        }.onFailure { t ->
            onResult(Result.Error(t.message ?: "unknown", t.javaClass.simpleName))
        }
    }

    private fun mapError(e: PurchasesError): Result = when (e.code) {
        PurchasesErrorCode.PurchaseCancelledError        -> Result.UserCancelled
        // The 6.5.2 enum member is OperationAlreadyInProgressError; there is no
        // PurchaseInProgressError (javap-verified against the AAR, bug #27).
        PurchasesErrorCode.OperationAlreadyInProgressError -> Result.AlreadyOwned
        PurchasesErrorCode.ProductAlreadyPurchasedError  -> Result.AlreadyOwned
        else -> Result.Error(e.message ?: e.code.name, e.code.name)
    }

    fun restore(onResult: (Boolean) -> Unit) {
        ServiceLocator.entitlements.restorePurchases(onResult)
    }
}
