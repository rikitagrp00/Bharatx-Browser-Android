/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx.monetization

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.fenix.R

data class BharatXSubscriptionState(
    val known: Boolean = false,
    val active: Boolean = false,
    val loading: Boolean = false,
    val price: String? = null,
    val currency: String? = null,
    val purchasable: Boolean = false,
    val message: Int? = null,
)

/** Entitlement comes from current Play purchases, never a button, a local flag or a fixed 30-day timer. */
object BharatXBilling {
    const val PRODUCT_ID = "bharatx_remove_ads_monthly"
    private const val ENTITLEMENT_FRESH_MS = 15 * 60 * 1000L
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(BharatXSubscriptionState())
    val state = mutableState.asStateFlow()
    private var client: BillingClient? = null
    private var connecting = false
    private var refreshing = false
    private var lastChecked = 0L
    private var packageName = ""
    private var queryGeneration = 0
    private val expiryRefresh = Runnable { refresh() }
    private val acknowledgements = mutableSetOf<String>()

    private fun ui(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    fun initialize(context: Context) {
        if (client != null) return
        packageName = context.packageName
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, _ ->
                ui {
                    when (result.responseCode) {
                        BillingClient.BillingResponseCode.OK, BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                        BillingClient.BillingResponseCode.USER_CANCELED -> update { it.copy(loading = false, message = R.string.bharatx_purchase_cancelled) }
                        else -> update { it.copy(loading = false, message = R.string.bharatx_billing_error) }
                    }
                }
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    fun isAdFree(): Boolean = mutableState.value.active && SystemClock.elapsedRealtime() - lastChecked < ENTITLEMENT_FRESH_MS

    fun canShowAds(): Boolean = mutableState.value.known && !mutableState.value.active

    private fun update(change: (BharatXSubscriptionState) -> BharatXSubscriptionState) {
        mutableState.value = change(mutableState.value)
    }

    fun refresh() = ui {
        val billing = client ?: return@ui
        if (connecting || refreshing) return@ui
        if (!isAdFree() && mutableState.value.active) update { it.copy(known = false, active = false) }
        update { it.copy(loading = true, message = null) }
        if (billing.isReady) {
            queryStore(billing)
        } else {
            connecting = true
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) = ui {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) queryStore(billing)
                    else update { it.copy(loading = false, purchasable = false, message = R.string.bharatx_billing_unavailable) }
                }

                override fun onBillingServiceDisconnected() = ui {
                    connecting = false
                    refreshing = false
                    update { it.copy(loading = false, purchasable = false, message = R.string.bharatx_billing_unavailable) }
                }
            })
        }
    }

    private fun queryStore(billing: BillingClient) {
        refreshing = true
        val generation = ++queryGeneration
        queryProduct(billing) { details, offer ->
            val phase = offer?.pricingPhases?.pricingPhaseList?.singleOrNull()
            update { it.copy(price = phase?.formattedPrice, currency = phase?.priceCurrencyCode, purchasable = details != null && offer != null) }
        }
        val query = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
            .includeSuspendedSubscriptions(false).build()
        billing.queryPurchasesAsync(query) { result, purchases ->
            ui {
                if (generation != queryGeneration) return@ui
                refreshing = false
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    update { it.copy(loading = false, message = R.string.bharatx_billing_error) }
                    return@ui
                }
                fun decision(purchase: Purchase) = BharatXPurchasePolicy.classify(purchase, packageName, PRODUCT_ID)
                val purchased = purchases.firstOrNull { decision(it) in setOf(BharatXPurchasePolicy.Decision.ACTIVE, BharatXPurchasePolicy.Decision.ACKNOWLEDGE) }
                if (purchased == null) {
                    lastChecked = SystemClock.elapsedRealtime()
                    update { it.copy(known = true, active = false, loading = false,
                        message = if (purchases.any { p -> decision(p) == BharatXPurchasePolicy.Decision.PENDING }) R.string.bharatx_purchase_pending else null) }
                } else if (purchased.isAcknowledged) {
                    activate()
                } else if (acknowledgements.add(purchased.purchaseToken)) {
                    // Pending purchases are never acknowledged or granted.
                    billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchased.purchaseToken).build()) { acknowledged ->
                        ui {
                            acknowledgements.remove(purchased.purchaseToken)
                            if (generation != queryGeneration) { refresh(); return@ui }
                            if (acknowledged.responseCode == BillingClient.BillingResponseCode.OK) activate()
                            else update { it.copy(loading = false, message = R.string.bharatx_billing_error) }
                        }
                    }
                } else {
                    update { it.copy(loading = false, message = R.string.bharatx_subscription_checking) }
                }
            }
        }
    }

    private fun activate() {
        lastChecked = SystemClock.elapsedRealtime()
        main.removeCallbacks(expiryRefresh)
        main.postDelayed(expiryRefresh, ENTITLEMENT_FRESH_MS + 1)
        update { it.copy(known = true, active = true, loading = false, message = R.string.bharatx_subscription_active) }
    }

    private fun queryProduct(billing: BillingClient, result: (ProductDetails?, ProductDetails.SubscriptionOfferDetails?) -> Unit) {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build(),
        )).build()
        billing.queryProductDetailsAsync(params) { response, products ->
            ui {
                val details = if (response.responseCode == BillingClient.BillingResponseCode.OK)
                    products.productDetailsList.firstOrNull { it.productId == PRODUCT_ID } else null
                // Choose the ordinary monthly base plan, without an undisclosed trial/intro price.
                val offer = details?.subscriptionOfferDetails?.firstOrNull { offer ->
                    offer.offerId == null && offer.pricingPhases.pricingPhaseList.singleOrNull()?.let { phase ->
                        phase.billingPeriod == "P1M" && phase.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
                    } == true
                }
                result(details, offer)
            }
        }
    }

    fun buy(activity: Activity) = ui {
        val billing = client ?: return@ui
        if (isAdFree() || mutableState.value.loading || activity.isFinishing || activity.isDestroyed) return@ui
        if (!billing.isReady) { refresh(); return@ui }
        update { it.copy(loading = true, message = null) }
        queryProduct(billing) { details, offer ->
            if (details == null || offer == null || activity.isFinishing || activity.isDestroyed) {
                update { it.copy(loading = false, purchasable = false, message = R.string.bharatx_billing_unavailable) }
            } else {
                val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build()
                val response = billing.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build())
                update { it.copy(loading = false, message = if (response.responseCode == BillingClient.BillingResponseCode.OK) null else R.string.bharatx_billing_error) }
            }
        }
    }

    fun manage(activity: Activity) {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions").buildUpon()
            .appendQueryParameter("sku", PRODUCT_ID).appendQueryParameter("package", activity.packageName).build()
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
        } catch (_: android.content.ActivityNotFoundException) {
            update { it.copy(message = R.string.bharatx_billing_unavailable) }
        }
    }
}
