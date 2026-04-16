package dev.ori.core.billing

import android.app.Activity
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase

interface BillingClientLauncher {
    suspend fun queryProductDetails(skus: List<String>): List<ProductDetails>
    suspend fun launchPurchaseFlow(activity: Activity, details: ProductDetails): BillingPurchaseOutcome
    suspend fun queryPurchases(productType: String): List<Purchase>
    suspend fun acknowledgePurchase(token: String): BillingPurchaseOutcome
}
