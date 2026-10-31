/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.fenix.bharatx.monetization

import com.android.billingclient.api.Purchase

/** Classifies purchases returned by Play Billing. This is not a server-side receipt verifier. */
object BharatXPurchasePolicy {
    enum class Decision { NONE, PENDING, ACKNOWLEDGE, ACTIVE }

    fun classify(purchase: Purchase, appPackage: String, productId: String): Decision {
        if (purchase.packageName != appPackage || productId !in purchase.products ||
            purchase.isSuspended || purchase.purchaseToken.isBlank()) return Decision.NONE
        return when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> Decision.PENDING
            Purchase.PurchaseState.PURCHASED -> if (purchase.isAcknowledged) Decision.ACTIVE else Decision.ACKNOWLEDGE
            else -> Decision.NONE
        }
    }
}
