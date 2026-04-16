package dev.ori.core.billing

import android.app.Activity
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase

/**
 * Hand-written fake for [BillingClientLauncher]. Seed the `next*` fields
 * before calling the corresponding method — the fake returns the seeded
 * value and resets nothing, so the same value is returned on repeated calls
 * unless you change it.
 */
class FakeBillingClientLauncher : BillingClientLauncher {

    var nextProductDetails: List<ProductDetails> = emptyList()
    var nextPurchaseOutcome: BillingPurchaseOutcome = BillingPurchaseOutcome.Success
    var nextPurchases: List<Purchase> = emptyList()
    var nextAcknowledgeOutcome: BillingPurchaseOutcome = BillingPurchaseOutcome.Success

    /** Tracks every SKU list passed to [queryProductDetails]. */
    val queriedSkus: MutableList<List<String>> = mutableListOf()

    /** Tracks every token passed to [acknowledgePurchase]. */
    val acknowledgedTokens: MutableList<String> = mutableListOf()

    override suspend fun queryProductDetails(skus: List<String>): List<ProductDetails> {
        queriedSkus += skus
        return nextProductDetails
    }

    override suspend fun launchPurchaseFlow(
        activity: Activity,
        details: ProductDetails,
    ): BillingPurchaseOutcome = nextPurchaseOutcome

    override suspend fun queryPurchases(productType: String): List<Purchase> = nextPurchases

    override suspend fun acknowledgePurchase(token: String): BillingPurchaseOutcome {
        acknowledgedTokens += token
        return nextAcknowledgeOutcome
    }
}
